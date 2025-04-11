package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.BREAK_PACK_TYPE_CODE;
import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.CASE_PACK_TYPE_CODE;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeGetLpnsRequest;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeItemUpdateRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadChildContainerDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadContainerDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadDistributionsDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadItemDTO;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcVerificationMessage;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.service.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class RdcAutoReceivingUtils {
  @Autowired private RdcDeliveryService rdcDeliveryService;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private RdcInstructionHelper rdcInstructionHelper;
  @Autowired private ContainerService containerService;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired private LabelDataService labelDataService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private LabelDownloadEventService labelDownloadEventService;
  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired private Gson gson;
  @Autowired private RdcDeliveryMetaDataService rdcDeliveryMetaDataService;
  @ManagedConfiguration private AppConfig appConfig;
  private static final Logger logger = LoggerFactory.getLogger(RdcAutoReceivingUtils.class);

  /**
   * Creates Containers/Container items/Receipts and persists in DB
   *
   * @param autoReceiveRequest
   * @param deliveryDocument
   * @param labelTrackingId
   * @param userId
   * @param receivedContainer
   * @param instructionId
   */
  public void buildContainerItemAndContainerForDA(
      AutoReceiveRequest autoReceiveRequest,
      DeliveryDocument deliveryDocument,
      String labelTrackingId,
      String userId,
      ReceivedContainer receivedContainer,
      Long instructionId,
      String inventoryStatus) {
    Container container = null;
    logger.info(
        "Build containers and receipts for deliveryNumber:{}, LPN:{}",
        autoReceiveRequest.getDeliveryNumber(),
        autoReceiveRequest.getLpn());
    ContainerItem containerItem =
        rdcContainerUtils.buildContainerItemDetails(
            labelTrackingId,
            deliveryDocument,
            receivedContainer.getPack(), // Qty in Eaches for DA. derived from Orders AllocQty.
            null,
            receivedContainer.getStoreAlignment(),
            receivedContainer.getDistributions(),
            receivedContainer.getDestType());
    container =
        rdcContainerUtils.buildContainer(
            autoReceiveRequest.getDoorNumber(),
            instructionId,
            autoReceiveRequest.getDeliveryNumber(),
            autoReceiveRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            container,
            null);

    container.setInventoryStatus(inventoryStatus);
    container.setContainerItems(Collections.singletonList(containerItem));
    containerPersisterService.saveContainer(container);
  }

  /**
   * @param labelTrackingId
   * @param slotId
   * @return
   */
  public ReceivedContainer getReceivedContainerInfo(String labelTrackingId, String slotId) {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(labelTrackingId);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot(slotId);
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    // This needs to be revisited for SSTK - SSTK has Breakpack and casepack so quantity will be
    // different as vpnk and wpnk
    receivedContainer.setPack(RdcConstants.QTY_TO_RECEIVE);
    return receivedContainer;
  }

  /**
   * @param autoReceiveRequest
   * @param deliveryDocument
   * @return
   */
  public ReceiveInstructionRequest buildReceiveInstructionRequest(
      AutoReceiveRequest autoReceiveRequest, DeliveryDocument deliveryDocument) {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber(autoReceiveRequest.getDoorNumber());
    receiveInstructionRequest.setDeliveryNumber(autoReceiveRequest.getDeliveryNumber());
    receiveInstructionRequest.setDeliveryDocumentLines(deliveryDocument.getDeliveryDocumentLines());
    receiveInstructionRequest.setQuantity(autoReceiveRequest.getQuantity());
    return receiveInstructionRequest;
  }

  /**
   * @param labelTrackingId
   * @throws ReceivingBadDataException
   */
  public void isLpnAlreadyReceived(String labelTrackingId) {
    Container container = containerService.findByTrackingId(labelTrackingId);
    if (Objects.nonNull(container)) {
      logger.info(String.format(ReceivingException.LPN_ALREADY_RECEIVED, labelTrackingId));
      throw new ReceivingBadDataException(
          ExceptionCodes.LPN_ALREADY_RECEIVED,
          String.format(ReceivingException.LPN_ALREADY_RECEIVED, labelTrackingId),
          labelTrackingId);
    }
  }

  public void updateCatalogInHawkeye(
      ItemCatalogUpdateRequest itemCatalogUpdateRequest, HttpHeaders httpHeaders) {
    List<LabelData> labelDataList =
        labelDataService.findByItemNumber(itemCatalogUpdateRequest.getItemNumber());
    try {
      if (Objects.nonNull(labelDataList)) {
        HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest =
            getItemUpdateRequest(itemCatalogUpdateRequest);
        hawkeyeRestApiClient.sendItemUpdateToHawkeye(hawkeyeItemUpdateRequest, httpHeaders);
      }
    } catch (ReceivingBadDataException | ReceivingInternalException e) {
      logger.error(
          "Item update failed for request with error code {} and description {}",
          e.getErrorCode(),
          e.getDescription());
    }
  }

  private HawkeyeItemUpdateRequest getItemUpdateRequest(
      ItemCatalogUpdateRequest itemCatalogUpdateRequest) {
    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest = new HawkeyeItemUpdateRequest();
    hawkeyeItemUpdateRequest.setItemNumber(
        String.valueOf(itemCatalogUpdateRequest.getItemNumber()));
    if (Objects.nonNull(itemCatalogUpdateRequest.getDeliveryNumber())) {
      hawkeyeItemUpdateRequest.setGroupNumber(itemCatalogUpdateRequest.getDeliveryNumber());
    }
    hawkeyeItemUpdateRequest.setCatalogGTIN(itemCatalogUpdateRequest.getNewItemUPC());
    return hawkeyeItemUpdateRequest;
  }

  /**
   * Creates instruction for Automation Receiving. Persist instruction in DB
   *
   * @param autoReceiveRequest
   * @param deliveryDocument
   * @param httpHeaders
   * @return
   */
  public Instruction createInstruction(
      AutoReceiveRequest autoReceiveRequest,
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders) {
    Instruction instruction = new Instruction();
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    instruction.setCreateUserId(userId);
    instruction.setCreateTs(new Date());
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    instruction.setPurchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    instruction.setPurchaseReferenceLineNumber(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    String itemDescription =
        Objects.nonNull(deliveryDocumentLine.getDescription())
            ? deliveryDocumentLine.getDescription()
            : deliveryDocumentLine.getSecondaryDescription();
    instruction.setItemDescription(itemDescription);
    instruction.setMessageId(autoReceiveRequest.getMessageId());
    instruction.setGtin(deliveryDocumentLine.getCaseUpc());
    instruction.setPoDcNumber(deliveryDocument.getPoDCNumber());
    instruction.setLastChangeUserId(userId);
    instruction.setDeliveryNumber(autoReceiveRequest.getDeliveryNumber());

    instruction.setPrintChildContainerLabels(false);
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setProjectedReceiveQty(autoReceiveRequest.getQuantity());

    instruction.setActivityName(WFTInstruction.ACL.getActivityName());
    instruction.setInstructionMsg(WFTInstruction.ACL.getMessage());
    instruction.setInstructionCode(WFTInstruction.ACL.getCode());

    instruction.setReceivedQuantity(autoReceiveRequest.getQuantity());
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    return instructionPersisterService.saveInstruction(instruction);
  }

  /**
   * Updates instruction for Automation Receiving. Updates instruction in DB
   *
   * @param instruction
   * @param receivedContainer
   * @param receivedQuantity
   * @param printLabelData
   * @param userId
   * @param isDAItem
   */
  public void updateInstruction(
      Instruction instruction,
      ReceivedContainer receivedContainer,
      Integer receivedQuantity,
      Map<String, Object> printLabelData,
      String userId,
      boolean isDAItem) {

    if (isDAItem) {
      instruction.setContainer(
          rdcContainerUtils.getContainerDetails(
              receivedContainer.getLabelTrackingId(),
              printLabelData,
              ContainerType.CASE,
              RdcConstants.OUTBOUND_CHANNEL_METHOD_CROSSDOCK));
      if (receivedContainer.isSorterDivertRequired()) {
        instruction.getContainer().setInventoryStatus(InventoryStatus.PICKED.name());
      } else {
        instruction.getContainer().setInventoryStatus(InventoryStatus.ALLOCATED.name());
      }
    } else {
      instruction.setContainer(
          rdcContainerUtils.getContainerDetails(
              receivedContainer.getLabelTrackingId(),
              printLabelData,
              ContainerType.PALLET,
              RdcConstants.OUTBOUND_CHANNEL_METHOD_SSTKU));
      instruction.getContainer().setInventoryStatus(InventoryStatus.AVAILABLE.name());
    }
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantity(receivedQuantity);
    instruction.setLastChangeUserId(userId);
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());
    instructionPersisterService.saveInstruction(instruction);
  }

  /**
   * @param labelDataList
   * @param deliveryDocument
   * @return
   */
  public List<ReceivedContainer> transformLabelData(
      List<LabelData> labelDataList, DeliveryDocument deliveryDocument) {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    for (LabelData labelData : labelDataList) {
      ReceivedContainer parentContainer =
          buildReceivedContainer(
              labelData,
              deliveryDocument,
              deliveryDocumentLine,
              labelData.getTrackingId(),
              null,
              labelData.getAllocation().getContainer().getDistributions(),
              labelData.getAllocation().getContainer().getFinalDestination());
      receivedContainers.add(parentContainer);

      List<InstructionDownloadChildContainerDTO> childContainers =
          labelData.getAllocation().getChildContainers();
      Optional.ofNullable(childContainers)
          .orElse(Collections.emptyList())
          .forEach(
              childContainer ->
                  receivedContainers.add(
                      buildReceivedContainer(
                          labelData,
                          deliveryDocument,
                          deliveryDocumentLine,
                          childContainer.getTrackingId(),
                          labelData.getTrackingId(),
                          childContainer.getDistributions(),
                          childContainer.getCtrDestination())));
    }
    return receivedContainers;
  }

  /**
   * Preparation of ReceivedContainer
   *
   * @param labelData
   * @param deliveryDocument
   * @param deliveryDocumentLine
   * @param labelTrackingId
   * @param parentLabelTrackingId
   * @param distributions
   * @param facility
   * @return
   */
  private ReceivedContainer buildReceivedContainer(
      LabelData labelData,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      String labelTrackingId,
      String parentLabelTrackingId,
      List<InstructionDownloadDistributionsDTO> distributions,
      Facility facility) {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setPoNumber(labelData.getPurchaseReferenceNumber());
    receivedContainer.setPoLine(labelData.getPurchaseReferenceLineNumber());
    receivedContainer.setPocode(deliveryDocument.getPoTypeCode());
    receivedContainer.setPoevent(deliveryDocumentLine.getEvent());
    String deptNumber =
        StringUtils.isNotBlank(deliveryDocument.getDeptNumber())
            ? deliveryDocument.getDeptNumber()
            : StringUtils.EMPTY;
    String departmentNumber =
        StringUtils.isNotBlank(deliveryDocumentLine.getDepartment())
            ? deliveryDocumentLine.getDepartment()
            : deptNumber;
    if (StringUtils.isNotBlank(departmentNumber)) {
      receivedContainer.setDepartment(Integer.parseInt(deliveryDocumentLine.getDepartment()));
    }
    receivedContainer.setLabelTrackingId(labelTrackingId);
    receivedContainer.setParentTrackingId(parentLabelTrackingId);
    receivedContainer.setDistributions(prepareDistributions(distributions, facility.getBuNumber()));

    InstructionDownloadContainerDTO instructionDownloadContainerDTO =
        labelData.getAllocation().getContainer();
    InstructionDownloadItemDTO instructionDownloadItemDTO =
        instructionDownloadContainerDTO.getDistributions().get(0).getItem();
    receivedContainer.setAisle(instructionDownloadItemDTO.getAisle());
    if (StringUtils.isNotBlank(instructionDownloadItemDTO.getPrintBatch())) {
      receivedContainer.setBatch(Integer.parseInt(instructionDownloadItemDTO.getPrintBatch()));
    }
    receivedContainer.setDivision(instructionDownloadItemDTO.getDivisionNumber());
    receivedContainer.setStoreAlignment(instructionDownloadItemDTO.getStoreAlignment());
    receivedContainer.setStorezone(instructionDownloadItemDTO.getZone());
    receivedContainer.setShippingLane(instructionDownloadItemDTO.getShipLaneNumber());
    receivedContainer.setPack(distributions.get(0).getAllocQty());

    // ToDo: Store Aisle locations
    Destination destination = new Destination();
    destination.setStore(facility.getBuNumber());

    // ToDo: virtual slot info needed for inventory
    destination.setSlot(RdcConstants.DA_R8000_SLOT);

    receivedContainer.setDestinations(Collections.singletonList(destination));
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    // TODO: fulfillmentType needs to be revisited for BreakPack and DSDC
    receivedContainer.setFulfillmentMethod(FulfillmentMethodType.FLIB_CASEPACK.getType());
    receivedContainer.setDestType(
        ReceivingConstants.MFC.equalsIgnoreCase(facility.getDestType())
            ? ReceivingConstants.MFC
            : ReceivingConstants.LABEL_TYPE_STORE);
    return receivedContainer;
  }

  /**
   * Preparing distributions
   *
   * @param instructionDownloadDistributionsDTOS
   * @return
   */
  private List<Distribution> prepareDistributions(
      List<InstructionDownloadDistributionsDTO> instructionDownloadDistributionsDTOS,
      String buNumber) {
    return instructionDownloadDistributionsDTOS
        .stream()
        .map(
            instructionDownloadDistribution -> {
              Distribution distribution = new Distribution();
              distribution.setItem(
                  ReceivingUtils.convertJsonToMap(
                      gson.toJson(instructionDownloadDistribution.getItem())));
              distribution.setOrderId(instructionDownloadDistribution.getOrderId());
              if (StringUtils.isNotBlank(buNumber)) {
                distribution.setDestNbr(Integer.parseInt(buNumber));
              }
              return distribution;
            })
        .collect(Collectors.toList());
  }

  /** @param deliveryDocument */
  public void validateProDate(DeliveryDocument deliveryDocument) {
    if (Objects.isNull(deliveryDocument.getProDate())) {
      deliveryDocument.setProDate(new Date());
    }
  }

  /**
   * * This method sets the location details for WFT posting in httpHeaders. If Hawkeye provides the
   * locationId from Verification message, we will use that locationId Until then, we will pick the
   * location from deliveryMetaData for door Number
   *
   * @param autoReceiveRequest
   * @param httpHeaders
   */
  public void setLocationHeaders(AutoReceiveRequest autoReceiveRequest, HttpHeaders httpHeaders) {
    String doorNumber = null;
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_HAWKEYE_LOCATION_DETAILS_ENABLED,
        false)) {
      if (StringUtils.isNotBlank(autoReceiveRequest.getFeatureType())
          && autoReceiveRequest.getFeatureType().equals(RdcConstants.EXCEPTION_HANDLING)) {
        doorNumber = autoReceiveRequest.getDoorNumber();
      } else {
        Optional<DeliveryMetaData> deliveryMetaData =
            rdcDeliveryMetaDataService.findByDeliveryNumber(
                autoReceiveRequest.getDeliveryNumber().toString());
        if (deliveryMetaData.isPresent()) {
          doorNumber = deliveryMetaData.get().getDoorNumber();
        }
      }
    } else {
      // TODO pick from verification message
    }
    if (Objects.nonNull(doorNumber)) {
      httpHeaders.add(
          RdcConstants.WFT_LOCATION_TYPE,
          ReceivingConstants.DOOR + ReceivingConstants.DELIM_DASH + doorNumber);
      autoReceiveRequest.setDoorNumber(doorNumber);
    }
  }

  public void buildLabelType(
      ReceivedContainer receivedContainer,
      AutoReceiveRequest autoReceiveRequest,
      DeliveryDocument deliveryDocument) {
    if (Objects.nonNull(autoReceiveRequest.getFeatureType())
        && autoReceiveRequest.getFeatureType().equals(RdcConstants.EXCEPTION_HANDLING)
        && (!(appConfig
            .getValidItemPackTypeHandlingCodeCombinations()
            .contains(
                deliveryDocument
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getAdditionalInfo()
                    .getItemPackAndHandlingCode())))) {
      autoReceiveRequest.setFlibEligible(Boolean.FALSE);
      receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    }
  }

  /**
   * * If feature type is exception handling and the request doesn't have the lpn, then if item has
   * valid pack type and handling code this method fetches the lpn from Hawkeye and then fetches the
   * label data based on the lpn else, it fetches label data based on PO and item number.In case of
   * auto receiving during verification event or when we have lpn in request for exception receiving
   * (HOST_LATE, FLIB INELIGIBLE, 25 digit unreceived lpn), we fetch label data based on the lpn. If
   * Label data is not available for receiving we throw a ReceivingBadDataException
   *
   * @param deliveryDocumentList
   * @param autoReceiveRequest
   */
  public LabelData fetchLabelData(
      List<DeliveryDocument> deliveryDocumentList,
      AutoReceiveRequest autoReceiveRequest,
      boolean isExceptionReceiving) {
    if (isExceptionReceiving && Objects.isNull(autoReceiveRequest.getLpn())) {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
      if (appConfig
          .getValidItemPackTypeHandlingCodeCombinations()
          .contains(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode())) {
        HawkeyeGetLpnsRequest hawkeyeGetLpnsRequest =
            HawkeyeGetLpnsRequest.builder()
                .deliveryNumber(String.valueOf(deliveryDocumentList.get(0).getDeliveryNumber()))
                .itemNumber(Math.toIntExact(deliveryDocumentLine.getItemNbr()))
                .quantity(autoReceiveRequest.getQuantity())
                .build();
        try {
          List<String> lpnList =
              hawkeyeRestApiClient
                  .getLpnsFromHawkeye(hawkeyeGetLpnsRequest, ReceivingUtils.getHeaders())
                  .orElse(Collections.emptyList());
          if (CollectionUtils.isEmpty(lpnList)) {
            throw new ReceivingBadDataException(
                ExceptionCodes.NO_ALLOCATIONS_FOR_DA_FREIGHT,
                ReceivingException.NO_ALLOCATIONS_FOR_DA_FREIGHT);
          }
          autoReceiveRequest.setLpn(lpnList.get(0));
        } catch (ReceivingBadDataException e) {
          return fetchLabelDataFromDB(deliveryDocumentLine, autoReceiveRequest.getQuantity());
        }
      } else {
        return fetchLabelDataFromDB(deliveryDocumentLine, autoReceiveRequest.getQuantity());
      }
    }
    isLpnAlreadyReceived(autoReceiveRequest.getLpn());
    LabelData labelData = getLabelData(autoReceiveRequest.getLpn());
    if (Objects.isNull(labelData)
        || StringUtils.equalsAny(
            labelData.getStatus(),
            LabelInstructionStatus.CANCELLED.name(),
            LabelInstructionStatus.COMPLETE.name())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.NO_ALLOCATIONS_FOR_DA_FREIGHT,
          ReceivingException.NO_ALLOCATIONS_FOR_DA_FREIGHT);
    }
    return labelData;
  }

  private LabelData fetchLabelDataFromDB(DeliveryDocumentLine deliveryDocumentLine, int quantity) {
    List<LabelData> labelDataList =
        labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            quantity,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());
    if (CollectionUtils.isEmpty(labelDataList)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.NO_ALLOCATIONS_FOR_DA_FREIGHT,
          ReceivingException.NO_ALLOCATIONS_FOR_DA_FREIGHT);
    }
    return labelDataList.get(0);
  }

  public AutoReceiveRequest buildAutoReceiveRequest(RdcVerificationMessage rdcVerificationMessage) {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setLpn(rdcVerificationMessage.getLpn());
    autoReceiveRequest.setDeliveryNumber(Long.valueOf(rdcVerificationMessage.getDeliveryNumber()));
    autoReceiveRequest.setQuantity(RdcConstants.RDC_AUTO_RECEIVE_QTY);
    autoReceiveRequest.setFlibEligible(Boolean.TRUE);
    // TODO: Update doorNumber if we get the value in verification message
    return autoReceiveRequest;
  }

  public LabelData getLabelData(String lpn) {
    LabelData labelData = labelDataService.findByTrackingId(lpn);
    if (Objects.nonNull(labelData)) {
      LabelInstructionStatus labelInstructionStatus =
          LabelInstructionStatus.valueOf(labelData.getStatus());
      logger.info(
          "Label data found for delivery {}, lpn {} has {} status",
          labelData.getDeliveryNumber(),
          lpn,
          labelInstructionStatus);
      return labelData;
    } else {
      logger.info("Label data not found for lpn {}, legacy lpn or exception lpn", lpn);
      return null;
    }
  }

  /**
   * This method gets the deliveryDocuments from GDM and updates additional item details
   *
   * @param autoReceiveRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public List<DeliveryDocument> getGdmDeliveryDocuments(
      AutoReceiveRequest autoReceiveRequest, HttpHeaders httpHeaders) throws ReceivingException {
    List<DeliveryDocument> gdmDeliveryDocumentList =
        rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            autoReceiveRequest.getDeliveryNumber().toString(),
            autoReceiveRequest.getPurchaseReferenceNumber(),
            autoReceiveRequest.getPurchaseReferenceLineNumber(),
            httpHeaders);

    DeliveryDocumentLine deliveryDocumentLine =
        gdmDeliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);

    if (Objects.isNull(deliveryDocumentLine.getPackType())) {
      boolean isBreakPackItem =
          RdcUtils.isBreakPackItem(
              deliveryDocumentLine.getVendorPack(), deliveryDocumentLine.getWarehousePack());
      deliveryDocumentLine.setPackType(
          isBreakPackItem ? BREAK_PACK_TYPE_CODE : CASE_PACK_TYPE_CODE);
    }
    if (Objects.nonNull(
        gdmDeliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo())) {
      gdmDeliveryDocumentList
          .get(0)
          .getDeliveryDocumentLines()
          .get(0)
          .getAdditionalInfo()
          .setAtlasConvertedItem(true);
      gdmDeliveryDocumentList
          .get(0)
          .getDeliveryDocumentLines()
          .get(0)
          .getAdditionalInfo()
          .setPackTypeCode(deliveryDocumentLine.getPackType());
    } else {
      ItemData itemData = new ItemData();
      itemData.setAtlasConvertedItem(true);
      itemData.setPackTypeCode(deliveryDocumentLine.getPackType());
      gdmDeliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    }
    rdcReceivingUtils.overridePackTypeCodeForBreakPackItem(deliveryDocumentLine);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
        false)) {
      rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(gdmDeliveryDocumentList);
    } else {
      nimRdsService.updateAdditionalItemDetails(gdmDeliveryDocumentList, httpHeaders);
    }
    return gdmDeliveryDocumentList;
  }

  /**
   * Validates GDM delivery documents
   *
   * @param gdmDeliveryDocumentList
   * @param autoReceiveRequest
   * @param httpHeaders
   * @throws ReceivingException
   */
  public void validateDeliveryDocuments(
      List<DeliveryDocument> gdmDeliveryDocumentList,
      AutoReceiveRequest autoReceiveRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber(autoReceiveRequest.getDeliveryNumber().toString());
    instructionRequest.setUpcNumber(
        gdmDeliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).getCaseUpc());
    DeliveryDocument deliveryDocument = gdmDeliveryDocumentList.get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
        gdmDeliveryDocumentList, instructionRequest);
    rdcInstructionUtils.validatePoLineIsCancelledOrClosedOrRejected(deliveryDocumentLine);
    rdcReceivingUtils.validateOverage(
        gdmDeliveryDocumentList, autoReceiveRequest.getQuantity(), httpHeaders, false);
  }

  public ReceivedContainer buildReceivedContainerForSSTK(
      String labelTrackingId, DeliveryDocumentLine deliveryDocumentLine) {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    Destination destination = new Destination();
    receivedContainer.setLabelTrackingId(labelTrackingId);
    receivedContainer.setPoNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    receivedContainer.setPoLine(deliveryDocumentLine.getPurchaseReferenceLineNumber());
    destination.setSlot(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    destination.setSlot_size(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize());
    receivedContainer.setDestinations(Collections.singletonList(destination));
    return receivedContainer;
  }

  public void buildContainerAndContainerItemForSSTK(
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      AutoReceiveRequest autoReceiveRequest,
      String userId,
      String labelTrackingId,
      String slotId) {
    UpdateInstructionRequest updateInstructionRequest =
        buildUpdateInstructionRequest(autoReceiveRequest);
    List<ContainerItem> containerItems =
        rdcContainerUtils.buildContainerItem(
            labelTrackingId, deliveryDocument, autoReceiveRequest.getQuantity(), null);
    Container container =
        rdcContainerUtils.buildContainer(
            instruction,
            updateInstructionRequest,
            deliveryDocument,
            userId,
            labelTrackingId,
            slotId,
            null);
    container.setContainerItems(containerItems);
    container.setContainerType(ContainerType.CASE.getText());
    containerPersisterService.saveContainer(container);
  }

  public UpdateInstructionRequest buildUpdateInstructionRequest(
      AutoReceiveRequest autoReceiveRequest) {
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDoorNumber(autoReceiveRequest.getDoorNumber());
    updateInstructionRequest.setDeliveryNumber(autoReceiveRequest.getDeliveryNumber());
    return updateInstructionRequest;
  }
}
