package com.walmart.move.nim.receiving.acc.service;

import com.google.gson.Gson;
import com.walmart.atlas.argus.metrics.annotations.CaptureMethodMetric;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.constants.LocationType;
import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseOrderInfo;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.ExceptionContainerHandlerFactory;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EMPTY_STRING;

public class LPNReceivingService {
  private static final Logger LOGGER = LoggerFactory.getLogger(LPNReceivingService.class);
  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  @Autowired protected ContainerPersisterService containerPersisterService;
  @Autowired protected InstructionHelperService instructionHelperService;

  @Autowired protected InstructionPersisterService instructionPersisterService;

  @Autowired private DeliveryDocumentHelper deliveryDocumentHelper;

  @Resource(name = ReceivingConstants.RETRYABLE_FDE_SERVICE)
  protected FdeService fdeService;

  @Autowired private UserLocationService userLocationService;

  @Autowired private DCFinService dcFinService;

  @Autowired private AsyncPersister asyncPersister;

  @Autowired protected LabelDataService labelDataService;

  @Autowired protected ContainerService containerService;

  @Resource(name = ReceivingConstants.ACC_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService deliveryMetaDataService;

  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private Gson gson;

  @Autowired protected ExceptionContainerHandlerFactory exceptionContainerHandlerFactory;

  /**
   * This method will receive a case by trackingId. TenantContext should be set before calling this
   * method
   *
   * @param lpn
   * @param deliveryNumber
   * @param location
   */
  @Timed(
      name = "ReceiveByLpnTimed",
      level1 = "uwms-receiving",
      level2 = "LPNReceivingService",
      level3 = "receiveByLPN")
  @Counted(
      name = "ReceiveByLpnHitCount",
      level1 = "uwms-receiving",
      level2 = "LPNReceivingService",
      level3 = "receiveByLPN")
  @ExceptionCounted(
      name = "ReceiveByLpnExceptionCount",
      level1 = "uwms-receiving",
      level2 = "LPNReceivingService",
      level3 = "receiveByLPN")
  @CaptureMethodMetric
  public void receiveByLPN(String lpn, Long deliveryNumber, String location)
      throws ReceivingException {
    LOGGER.info(
        "LPNReceivingService: Processing lpn={}, delivery={},location={}",
        lpn,
        deliveryNumber,
        location);

    Container existingContainer = containerPersisterService.getContainerDetails(lpn);
    // create Http headers from tenant context
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.DEFAULT_USER);

    if (Objects.nonNull(existingContainer)) {
      if (!tenantSpecificConfigReader.isFeatureFlagEnabled(
          ACCConstants.ENABLE_DUPLICATE_LPN_VERIFICATION)) {
        LOGGER.error(
            "LPNReceivingService: Ignoring ACL Verification message:container {} already received.",
            lpn);
      } else {
        String originDcNumber =
            Optional.of(existingContainer)
                .map(Container::getFacility)
                .map(x -> x.get(ReceivingConstants.BU_NUMBER))
                .orElse(null);
        if (Objects.nonNull(existingContainer.getContainerMiscInfo())) {
          existingContainer
              .getContainerMiscInfo()
              .put(ReceivingConstants.ORIGIN_DC_NBR, originDcNumber);
        } else {
          existingContainer.setContainerMiscInfo(
              Collections.singletonMap(ReceivingConstants.ORIGIN_DC_NBR, originDcNumber));
        }
        LOGGER.info("LPNReceivingService: Republishing container {} already received.", lpn);
        publishContainerInfoToDownstream(lpn, httpHeaders, existingContainer);
      }
      return;
    }

    // Get PO, POL from label_data
    PurchaseOrderInfo purchaseOrderInfo = getPurchaseOrderInfoFromLabelData(lpn, deliveryNumber);
    LOGGER.info(
        "Fetched purchase order info for LPN {} and delivery {}. PO Info {}",
        lpn,
        deliveryNumber,
        purchaseOrderInfo.toString());
    PossibleUPC possibleUPC =
        JacksonParser.convertJsonToObject(purchaseOrderInfo.getPossibleUPC(), PossibleUPC.class);

    // Get PO/POL details from GDM(Could be multi PO/POL)
    List<DeliveryDocument> deliveryDocuments =
        fetchDeliveryDocumentsFromGDM(
            purchaseOrderInfo, possibleUPC, deliveryNumber, lpn, httpHeaders);

    String deliveryStatus = deliveryDocuments.get(0).getDeliveryStatus().name();
    String deliveryLegacyStatus = deliveryDocuments.get(0).getDeliveryLegacyStatus();
    // check if delivery is closed, then call reopen
    instructionHelperService.reopenDeliveryIfNeeded(
        deliveryNumber, deliveryStatus, httpHeaders, deliveryLegacyStatus);

    Pair<DeliveryDocument, Boolean> selectedDeliveryDocAndIsReceivable =
        selectDeliveryDocAndCheckIfReceivable(
            deliveryDocuments, purchaseOrderInfo, possibleUPC.getOrderableGTIN());
    DeliveryDocument deliveryDocument = selectedDeliveryDocAndIsReceivable.getKey();

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL)) {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      try {
        InstructionUtils.validateItemXBlocked(deliveryDocumentLine);
      } catch (ReceivingBadDataException exc) {
        LOGGER.error(
            "LPNReceivingService: X Blocked item found in selected po line. PO: {} POL: {} HandlingCode: {} Item: {}",
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            deliveryDocumentLine.getHandlingCode(),
            deliveryDocumentLine.getItemNbr());
        exceptionContainerHandlerFactory
            .exceptionContainerHandler(ContainerException.XBLOCK)
            .publishExceptionDivertToSorter(lpn, SorterExceptionReason.XBLOCK, new Date());
        throw exc;
      }
    }

    // validate receivedQty
    if (!selectedDeliveryDocAndIsReceivable.getValue()) {
      LOGGER.error(
          "LPNReceivingService: Received maximum allowable quantity for this item, publish overage as exception container {}",
          lpn);
      // publish & save exception container to inventory
      publishExceptionContainer(
          lpn, deliveryNumber, location, deliveryDocument, ContainerException.OVERAGE, httpHeaders);
      return;
    }

    // Create InstructionRequest POJO and use that for building OF payload
    InstructionRequest instructionRequest =
        getInstructionRequest(
            lpn,
            deliveryNumber,
            location,
            deliveryDocument,
            deliveryDocuments.get(0).getDeliveryStatus().name());

    // Create OF request payload
    FdeCreateContainerRequest fdeCreateContainerRequest =
        InstructionUtils.prepareFdeCreateContainerRequestForOnConveyor(
            httpHeaders, instructionRequest, ReceivingConstants.DEFAULT_USER);

    // call OF
    String instructionResponse = null;
    try {
      instructionResponse = fdeService.receive(fdeCreateContainerRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      handleFdeServiceError(
          receivingException, lpn, deliveryNumber, location, deliveryDocument, httpHeaders);
      return;
    }

    // populate instruction from OF response
    Instruction instruction =
        getInstruction(httpHeaders, deliveryDocument, instructionRequest, instructionResponse);

    // find user & locationType for a location
    Pair<String, LocationType> userLocTypePair = getUserWorkingAtLocation(location);
    String userId = userLocTypePair.getKey();
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, userId);
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, ACCConstants.DEFAULT_SECURITY_ID);
    instruction.setCreateUserId(userId);

    // publish instruction created to WFM
    instructionHelperService.publishInstruction(
        instruction, null, null, null, InstructionStatus.CREATED, httpHeaders);

    // channel type
    String purRefType =
        fdeCreateContainerRequest.getContainer().getContents().get(0).getPurchaseRefType();

    // create updateInstruction  request
    UpdateInstructionRequest instructionUpdateRequest =
        InstructionUtils.getInstructionUpdateRequestForOnConveyor(
            location, deliveryNumber, purRefType, deliveryDocument);

    String activityName = getActivityNameForWFM(userLocTypePair.getValue());

    // publish updated instruction to WFM
    publishUpdateInstructionToWFM(httpHeaders, instruction, instructionUpdateRequest, activityName);

    // save container, receipts and instruction
    // onConveyor has to be true for ACL receiving
    Pair<Container, Instruction> containersAndSavedInstruction =
        instructionPersisterService.createContainersReceiptsAndSaveInstruction(
            instructionUpdateRequest, userId, instruction, Boolean.TRUE);

    Container consolidatedContainer = containersAndSavedInstruction.getKey();
    Set<Container> childContainerList = new HashSet<>();
    consolidatedContainer.setChildContainers(childContainerList);

    publishContainerInfoToDownstream(lpn, httpHeaders, consolidatedContainer);

    instruction = containersAndSavedInstruction.getValue();

    // Publishing instruction. Instruction will be published all the time.
    instructionHelperService.publishInstruction(
        instruction, null, null, consolidatedContainer, InstructionStatus.COMPLETED, httpHeaders);
    LOGGER.info(
        "LPNReceivingService: Processed lpn={}, delivery={},location={}",
        lpn,
        deliveryNumber,
        location);
  }

  private void handleFdeServiceError(
      ReceivingException receivingException,
      String lpn,
      Long deliveryNumber,
      String location,
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.error("LPNReceivingService: OF call failed for lpn {}", lpn);
    Optional<InstructionError> instructionErrorOptional =
        InstructionUtils.getFdeInstructionError(receivingException);
    if (instructionErrorOptional.isPresent()) {
      InstructionError instructionError = instructionErrorOptional.get();
      switch (instructionError) {
        case NO_ALLOCATION:
          LOGGER.error(
              "LPNReceivingService:No allocation found, publishing exception container for lpn: {}",
              lpn);
          publishExceptionContainer(
              lpn,
              deliveryNumber,
              location,
              deliveryDocument,
              ContainerException.NO_ALLOCATION_FOUND,
              httpHeaders);
          return;

        case INVALID_ALLOCATION:
          if (tenantSpecificConfigReader.isFeatureFlagEnabled(
              ACCConstants.ENABLE_INVALID_ALLOCATIONS_EXCEPTION_CONTAINER_PUBLISH)) {
            LOGGER.error(
                "LPNReceivingService:Invalid Allocations found, publishing exception container for lpn: {}",
                lpn);
            publishExceptionContainer(
                lpn,
                deliveryNumber,
                location,
                deliveryDocument,
                ContainerException.NO_ALLOCATION_FOUND,
                httpHeaders);
            return;
          } else throw receivingException;

        case CHANNEL_FLIP:
          LOGGER.error(
              "LPNReceivingService:Channel flip applied, publishing exception container for lpn: {}",
              lpn);
          publishExceptionContainer(
              lpn,
              deliveryNumber,
              location,
              deliveryDocument,
              ContainerException.CHANNEL_FLIP,
              httpHeaders);
          return;

        default:
          // In case the error is a known FDE error, but not handled above
          // consider a generic error, and publish exception container based on flag
          if (tenantSpecificConfigReader.isFeatureFlagEnabled(
              ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH)) {
            LOGGER.error(
                "LPNReceivingService: Known InstructionError {} in FDE Call {}, publishing exception container for lpn: {}",
                instructionError,
                receivingException,
                lpn);
            publishExceptionContainer(
                lpn,
                deliveryNumber,
                location,
                deliveryDocument,
                ContainerException.NO_ALLOCATION_FOUND,
                httpHeaders);
          } else throw receivingException;
      }
    } else {
      // In case no instructionError could be parsed out from the response
      // Still consider it a "generic error" and publish exception container based on flag
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH)) {
        LOGGER.error(
            "LPNReceivingService: Exception in FDE Call {}, publishing exception container for lpn: {}",
            receivingException,
            lpn);
        publishExceptionContainer(
            lpn,
            deliveryNumber,
            location,
            deliveryDocument,
            ContainerException.NO_ALLOCATION_FOUND,
            httpHeaders);
      } else throw receivingException;
    }
  }

  protected List<DeliveryDocument> getDeliveryDocuments(
      PurchaseOrderInfo purchaseOrderInfo, PossibleUPC possibleUPC, HttpHeaders httpHeaders)
      throws ReceivingException {
    DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments;
    String deliveryDocumentSearchUPC;
    try {
      deliveryDocumentSearchUPC = possibleUPC.getOrderableGTIN();
      deliveryDocuments =
          deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
              purchaseOrderInfo.getDeliveryNumber(), possibleUPC.getOrderableGTIN(), httpHeaders);
    } catch (ReceivingDataNotFoundException exc) {
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ACCConstants.ENABLE_FALLBACK_PO_SEARCH_LPN_RECEIVING)) {
        deliveryDocumentSearchUPC = possibleUPC.getConsumableGTIN();
        LOGGER.info(
            "LPNReceivingService: "
                + "PO Search for delivery: {} orderableGTIN: {} Failed. "
                + "Entering fallback PO Search with consumableGTIN: {}",
            purchaseOrderInfo.getDeliveryNumber(),
            possibleUPC.getOrderableGTIN(),
            possibleUPC.getConsumableGTIN());
        deliveryDocuments =
            deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
                purchaseOrderInfo.getDeliveryNumber(),
                possibleUPC.getConsumableGTIN(),
                httpHeaders);
      } else throw exc;
    }

    if (CollectionUtils.isEmpty(deliveryDocuments)
        || CollectionUtils.isEmpty(deliveryDocuments.get(0).getDeliveryDocumentLines())) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.PO_LINE_NOT_FOUND,
          String.format(
              ReceivingException.PO_POLINE_NOT_FOUND,
              deliveryDocumentSearchUPC,
              purchaseOrderInfo.getDeliveryNumber()));
    }
    return deliveryDocuments;
  }

  protected void publishContainerInfoToDownstream(
      String lpn, HttpHeaders httpHeaders, Container consolidatedContainer)
      throws ReceivingException {

    // Publish to inventory
    instructionHelperService.publishConsolidatedContainer(
        consolidatedContainer, httpHeaders, Boolean.TRUE);

    // Post receipts to DCFin
    dcFinService.postReceiptsToDCFin(consolidatedContainer, httpHeaders, true);

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.ENABLE_STORE_LABEL_SORTER_DIVERT)) {
      tenantSpecificConfigReader
          .getConfiguredInstance(
              String.valueOf(TenantContext.getFacilityNum()),
              ReceivingConstants.SORTER_PUBLISHER,
              SorterPublisher.class)
          .publishStoreLabel(consolidatedContainer);
    }
  }

  protected void publishUpdateInstructionToWFM(
      HttpHeaders httpHeaders,
      Instruction instruction,
      UpdateInstructionRequest instructionUpdateRequest,
      String activityName) {
    PublishInstructionSummary publishInstructionSummary =
        instructionHelperService.prepareInstructionMessage(
            instruction, instructionUpdateRequest, 1, null, InstructionStatus.UPDATED, httpHeaders);
    publishInstructionSummary.setActivityName(activityName);
    publishInstructionSummary.setVnpkQty(1);
    publishInstructionSummary.setWhpkQty(1);
    instructionHelperService.publishInstruction(httpHeaders, publishInstructionSummary);
  }

  private String getActivityNameForWFM(LocationType locationType) {
    if (Objects.nonNull(locationType) && locationType.equals(LocationType.FLR_LINE)) {
      return ACCConstants.FLR_LINE;
    }
    return ACCConstants.ACL_DOOR;
  }

  private Pair<String, LocationType> getUserWorkingAtLocation(String location) {
    String userId = ReceivingConstants.DEFAULT_USER;
    LocationType locationType = LocationType.ONLINE;
    UserLocation userLocation = userLocationService.getLastUserAtLocation(location);
    if (Objects.nonNull(userLocation)) {
      userId =
          !StringUtils.isEmpty(userLocation.getUserId())
              ? userLocation.getUserId().split("\\.s")[0]
              : userId;
      locationType =
          Objects.nonNull(userLocation.getLocationType())
              ? userLocation.getLocationType()
              : locationType;
    } else {
      List<String> roboDepalFloorlineList =
          Arrays.stream(
                  tenantSpecificConfigReader
                      .getCcmValue(
                          TenantContext.getFacilityNum(),
                          ReceivingConstants.ROBO_DEPAL_PARENT_FLOORLINES,
                          EMPTY_STRING)
                      .split(","))
              .filter(s -> !StringUtils.trimAllWhitespace(s).isEmpty())
              .collect(Collectors.toList());
      for (String roboDepalFloorline : roboDepalFloorlineList) {
        if (location.startsWith(roboDepalFloorline)) {
          locationType = LocationType.FLR_LINE;
          break;
        }
      }
    }
    LOGGER.info(
        "LPNReceivingService:Returning user {} at location {}:{}", userId, location, locationType);
    return new Pair<>(userId, locationType);
  }

  protected Instruction getInstruction(
      HttpHeaders httpHeaders,
      DeliveryDocument deliveryDocument,
      InstructionRequest instructionRequest,
      String instructionResponse) {
    Instruction instruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            deliveryDocument,
            InstructionUtils.mapHttpHeaderToInstruction(
                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));
    FdeCreateContainerResponse fdeCreateContainerResponse =
        JacksonParser.convertJsonToObject(instructionResponse, FdeCreateContainerResponse.class);
    instruction =
        InstructionUtils.processInstructionResponse(
            instruction, instructionRequest, fdeCreateContainerResponse);
    return instruction;
  }

  private DeliveryDocument filterByPOPOL(
      List<DeliveryDocument> deliveryDocuments, PurchaseOrderInfo purchaseOrderInfo) {

    List<DeliveryDocument> selectedDeliveryDocuments =
        InstructionUtils.filterDeliveryDocumentByPOPOL(
            deliveryDocuments,
            purchaseOrderInfo.getPurchaseReferenceNumber(),
            purchaseOrderInfo.getPurchaseReferenceLineNumber());

    if (!checkIfAnyPoLineIsSelected(selectedDeliveryDocuments)) {
      Long deliveryNumber = purchaseOrderInfo.getDeliveryNumber();
      LOGGER.error(
          "No valid po line found in delivery: {} to receive this container.", deliveryNumber);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.AUTO_SELECT_PO_POLINE_FAILED,
          String.format(
              ExceptionDescriptionConstants.AUTO_SELECT_PO_POLINE_FAILED_ERROR_MSG, deliveryNumber),
          deliveryNumber);
    }
    return selectedDeliveryDocuments.get(0);
  }

  private PurchaseOrderInfo getPurchaseOrderInfoFromLabelData(String lpn, Long deliveryNumber) {
    PurchaseOrderInfo purchaseOrderInfo =
        labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNumber, lpn);
    if (Objects.isNull(purchaseOrderInfo)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
          String.format(ACCConstants.PO_POL_METADATA_NOT_FOUND_ERROR_MSG, deliveryNumber, lpn));
    }
    return purchaseOrderInfo;
  }

  protected InstructionRequest getInstructionRequest(
      String lpn,
      Long deliveryNumber,
      String location,
      DeliveryDocument deliveryDocument,
      String deliveryStatus) {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setUpcNumber(deliveryDocument.getDeliveryDocumentLines().get(0).getGtin());
    instructionRequest.setDeliveryNumber(deliveryNumber.toString());
    instructionRequest.setDeliveryStatus(deliveryStatus);
    instructionRequest.setDoorNumber(location);
    instructionRequest.setMessageId(lpn);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));
    return instructionRequest;
  }

  protected void publishExceptionContainer(
      String trackingId,
      Long deliveryNumber,
      String locationId,
      DeliveryDocument deliveryDocument,
      ContainerException containerException,
      HttpHeaders httpHeaders) {

    Container container =
        prepareExceptionContainer(
            trackingId, locationId, deliveryNumber, deliveryDocument, containerException);
    container = containerPersisterService.saveContainer(container);

    // Publish to downstream
    exceptionContainerHandlerFactory
        .exceptionContainerHandler(containerException)
        .publishException(container);
  }

  protected Container prepareExceptionContainer(
      String trackingId,
      String locationId,
      Long deliveryNumber,
      DeliveryDocument deliveryDocument,
      ContainerException containerException) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    // create containerItem
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(trackingId);
    containerItem.setPurchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    containerItem.setPurchaseReferenceLineNumber(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    containerItem.setInboundChannelMethod(deliveryDocumentLine.getPurchaseRefType());
    containerItem.setTotalPurchaseReferenceQty(deliveryDocumentLine.getTotalOrderQty());
    containerItem.setPurchaseCompanyId(Integer.parseInt(deliveryDocument.getPurchaseCompanyId()));
    containerItem.setDeptNumber(Integer.parseInt(deliveryDocument.getDeptNumber()));
    containerItem.setItemNumber(deliveryDocumentLine.getItemNbr());
    containerItem.setGtin(deliveryDocumentLine.getGtin());
    containerItem.setVnpkQty(deliveryDocumentLine.getVendorPack());
    containerItem.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    containerItem.setVnpkWgtQty(deliveryDocumentLine.getWeight());
    containerItem.setVnpkWgtUom(deliveryDocumentLine.getWeightUom());
    containerItem.setVnpkcbqty(deliveryDocumentLine.getCube());
    containerItem.setVnpkcbuomcd(deliveryDocumentLine.getCubeUom());
    containerItem.setDescription(deliveryDocumentLine.getDescription());
    containerItem.setSecondaryDescription(deliveryDocumentLine.getSecondaryDescription());
    containerItem.setActualTi(deliveryDocumentLine.getPalletTie());
    containerItem.setActualHi(deliveryDocumentLine.getPalletHigh());
    containerItem.setVendorPackCost(deliveryDocumentLine.getVendorPackCost().doubleValue());
    containerItem.setWhpkSell(deliveryDocumentLine.getWarehousePackSell().doubleValue());
    containerItem.setQuantity(deliveryDocumentLine.getVendorPack());
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

    ContainerUtils.setAttributesForImports(
        deliveryDocument.getPoDCNumber(),
        deliveryDocument.getPoDcCountry(),
        deliveryDocument.getImportInd(),
        containerItem);

    String baseDivCode =
        deliveryDocument.getBaseDivisionCode() != null
            ? deliveryDocument.getBaseDivisionCode()
            : ReceivingConstants.BASE_DIVISION_CODE;
    containerItem.setBaseDivisionCode(baseDivCode);
    String fRG =
        deliveryDocument.getFinancialReportingGroup() != null
            ? deliveryDocument.getFinancialReportingGroup()
            : TenantContext.getFacilityCountryCode();
    containerItem.setFinancialReportingGroupCode(fRG);

    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);

    // create container
    Container container = new Container();
    container.setTrackingId(trackingId);
    container.setMessageId(trackingId);
    container.setLocation(locationId);
    container.setDeliveryNumber(deliveryNumber);

    Map<String, String> facility = new HashMap<>();
    facility.put(ReceivingConstants.COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    facility.put(ReceivingConstants.BU_NUMBER, TenantContext.getFacilityNum().toString());

    container.setFacility(facility);
    container.setContainerType(ContainerType.PALLET.name());
    container.setCtrShippable(Boolean.FALSE);
    container.setCtrReusable(Boolean.FALSE);
    container.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.name());
    container.setIsConveyable(deliveryDocumentLine.getIsConveyable());
    container.setOnConveyor(Boolean.TRUE);
    container.setContainerException(containerException.getText());
    container.setContainerItems(containerItems);
    container.setCreateTs(new Date());
    container.setCreateUser(ReceivingConstants.DEFAULT_USER);
    container.setLastChangedTs(new Date());
    container.setLastChangedUser(ReceivingConstants.DEFAULT_USER);
    container.setPublishTs(new Date());

    return container;
  }

  protected Pair<DeliveryDocument, Boolean> selectDeliveryDocAndCheckIfReceivable(
      List<DeliveryDocument> deliveryDocuments, PurchaseOrderInfo purchaseOrderInfo, String UPC)
      throws ReceivingException {
    boolean isReceivable = false;
    DeliveryDocument deliveryDocument;
    DeliveryDocumentLine deliveryDocumentLine;
    long totalReceivedQty = 0;

    List<DeliveryDocument> activeDeliveryDocuments =
        tenantSpecificConfigReader.isFeatureFlagEnabled(
                ACCConstants.ENABLE_FILTER_CANCELLED_PO_FOR_ACL)
            ? InstructionUtils.filterCancelledPoPoLine(deliveryDocuments)
            : deliveryDocuments;

    if (accManagedConfig.isMultiPOAutoSelectEnabled()
        && InstructionUtils.isMultiPoPol(activeDeliveryDocuments)) {
      LOGGER.info(
          "Triggered auto selection of PO/POL for label data record with delivery {} and PO {} and POL {}",
          purchaseOrderInfo.getDeliveryNumber(),
          purchaseOrderInfo.getPurchaseReferenceNumber(),
          purchaseOrderInfo.getPurchaseReferenceLineNumber());
      Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine;
      if (!CollectionUtils.isEmpty(activeDeliveryDocuments)
          && (Objects.isNull(activeDeliveryDocuments.stream().findAny().get().getImportInd())
              || !activeDeliveryDocuments.stream().findAny().get().getImportInd())) {
        autoSelectDocumentAndDocumentLine =
            instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
                activeDeliveryDocuments, 1, EMPTY_STRING);
      } else {
        autoSelectDocumentAndDocumentLine =
            instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobin(
                activeDeliveryDocuments, 1);
      }
      if (Objects.nonNull(autoSelectDocumentAndDocumentLine)) {
        deliveryDocument = autoSelectDocumentAndDocumentLine.getKey();
        totalReceivedQty = autoSelectDocumentAndDocumentLine.getValue();
        isReceivable = true;
        LOGGER.info(
            "Selected PO {} and POL {} and current received qty is {}",
            deliveryDocument.getPurchaseReferenceNumber(),
            deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber(),
            totalReceivedQty);
      } else {
        // Get the default PO/POL for creating exception container
        deliveryDocument = filterByPOPOL(activeDeliveryDocuments, purchaseOrderInfo);
        LOGGER.info(
            "No PO/POL eligible for receiving. Selected PO {} and POL {} for creating exception container",
            deliveryDocument.getPurchaseReferenceNumber(),
            deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber());
      }
    } else {
      Pair<Integer, Long> receivedQtyDetails = null;
      // Single PO/POL case
      deliveryDocument = filterByPOPOL(activeDeliveryDocuments, purchaseOrderInfo);
      // Get Received Qty
      receivedQtyDetails = getReceivedQtyDetails(deliveryDocument);
      // receivable if total received quantity is less than max ordered quantity
      totalReceivedQty = receivedQtyDetails.getValue();
      isReceivable = totalReceivedQty < receivedQtyDetails.getKey();
      LOGGER.info(
          "Single PO/POL case.Going to receive against PO {} and POL {}. Received Qty {}",
          deliveryDocument.getPurchaseReferenceNumber(),
          deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber(),
          totalReceivedQty);
    }
    deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    // update deliveryDoc
    deliveryDocumentHelper.updateCommonFieldsInDeliveryDocLine(deliveryDocumentLine);
    // set openQty
    // (open qty based on total order qty and not allowable overage)
    // in case of import PO and import fbq flag enabled, it will take line fbq instead of orderQty
    Integer totalOrderQty =
        ReceivingUtils.computeEffectiveTotalQty(
            deliveryDocumentLine, deliveryDocument.getImportInd(), tenantSpecificConfigReader);

    deliveryDocumentLine.setOpenQty(totalOrderQty - (int) totalReceivedQty);
    return new Pair<>(deliveryDocument, isReceivable);
  }

  /**
   * In case of import PO and FBQ Flag Enabled Check received qty based on current delivery (This
   * will be in case of single PO POL)
   *
   * @param deliveryDocument
   * @return
   */
  private Pair<Integer, Long> getReceivedQtyDetails(DeliveryDocument deliveryDocument) {
    Boolean importPoLineFbqEnabled =
        ReceivingUtils.isImportPoLineFbqEnabled(
            deliveryDocument.getImportInd(), tenantSpecificConfigReader);
    Pair<Integer, Long> receivedQtyDetails =
        importPoLineFbqEnabled
            ? instructionHelperService.getReceivedQtyDetailsByDeliveryNumber(
                deliveryDocument.getDeliveryNumber(),
                null,
                deliveryDocument.getDeliveryDocumentLines().get(0))
            : instructionHelperService.getReceivedQtyDetails(
                null, deliveryDocument.getDeliveryDocumentLines().get(0));
    if (importPoLineFbqEnabled) {
      Long totalReceivedQty = receivedQtyDetails.getValue();
      // This includes allowable overage
      Integer effectiveTotalQty =
          ReceivingUtils.computeEffectiveMaxReceiveQty(
              deliveryDocument.getDeliveryDocumentLines().get(0),
              deliveryDocument.getImportInd(),
              tenantSpecificConfigReader);
      receivedQtyDetails = new Pair<>(effectiveTotalQty, totalReceivedQty);
    }
    return receivedQtyDetails;
  }

  protected List<DeliveryDocument> fetchDeliveryDocumentsFromGDM(
      PurchaseOrderInfo purchaseOrderInfo,
      PossibleUPC possibleUPC,
      Long deliveryNumber,
      String lpn,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    try {
      return getDeliveryDocuments(purchaseOrderInfo, possibleUPC, httpHeaders);
    } catch (ReceivingDataNotFoundException exc) {
      // publish exception divert to sorter for no delivery documents found
      LOGGER.error(
          "LPNReceivingService: Delivery docs not found from GDM for delivery: {} possibleUPC: {}",
          deliveryNumber,
          possibleUPC);
      exceptionContainerHandlerFactory
          .exceptionContainerHandler(ContainerException.NO_DELIVERY_DOC)
          .publishExceptionDivertToSorter(lpn, SorterExceptionReason.NO_DELIVERY_DOC, new Date());
      throw exc;
    }
  }

  private boolean checkIfAnyPoLineIsSelected(List<DeliveryDocument> deliveryDocumentList) {
    return CollectionUtils.emptyIfNull(deliveryDocumentList)
        .stream()
        .anyMatch(po -> CollectionUtils.isNotEmpty(po.getDeliveryDocumentLines()));
  }
}
