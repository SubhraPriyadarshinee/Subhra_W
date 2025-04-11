package com.walmart.move.nim.receiving.core.helper;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.computeEffectiveTotalQty;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TRUE_STRING;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.RangeErrorResponse;
import com.walmart.move.nim.receiving.core.model.docktag.DockTagInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.model.inventory.*;
import com.walmart.move.nim.receiving.core.model.inventory.Item;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.DockTagPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.core.service.LabelServiceImpl;
import com.walmart.move.nim.receiving.core.service.OverrideInfo;
import com.walmart.move.nim.receiving.core.service.PrintJobService;
import com.walmart.move.nim.receiving.core.service.PrintingAndLabellingService;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.WitronDeliveryMetaDataService;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
public class InstructionHelperService {

  private static final Logger LOGGER = LoggerFactory.getLogger(InstructionHelperService.class);

  @Autowired protected TenantSpecificConfigReader configUtils;

  @Autowired private JmsPublisher jmsPublisher;

  @Autowired protected ContainerService containerService;

  @Autowired protected ReceiptService receiptService;

  @Autowired protected PrintJobService printJobService;

  @Autowired private LabelServiceImpl labelServiceImpl;

  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = ReceivingConstants.WITRON_DELIVERY_METADATA_SERVICE)
  protected WitronDeliveryMetaDataService witronDeliveryMetaDataService;

  protected InstructionError instructionError;

  @Resource(name = ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
  private LPNCacheService lpnCacheService;

  @Autowired protected InstructionPersisterService instructionPersisterService;
  @Autowired private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private DockTagPersisterService dockTagPersisterService;
  @Autowired protected GDMRestApiClient gdmRestApiClient;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Autowired private PrintingAndLabellingService printingAndLabellingService;
  @Autowired protected InventoryRestApiClient inventoryRestApiClient;

  /**
   * Prepare and returns create single item dock tag instruction
   *
   * @return instruction
   */
  public Instruction getDockTagInstruction(
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders,
      String moveToLocation,
      DockTagType dockTagType) {
    String dockTagId = lpnCacheService.getLPNBasedOnTenant(httpHeaders);
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put("toLocation", moveToLocation);
    moveTreeMap.put(
        "correlationID", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    moveTreeMap.put("containerTag", dockTagId);
    moveTreeMap.put("lastChangedOn", new Date());
    moveTreeMap.put("lastChangedBy", httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    Instruction instruction = new Instruction();
    instruction.setInstructionCode(ReceivingConstants.DOCK_TAG);
    instruction.setInstructionMsg(ReceivingConstants.DOCKTAG_INSTRUCTION_MESSAGE);
    instruction.setProviderId(ReceivingConstants.RECEIVING_PROVIDER_ID);
    instruction.setContainer(
        getDockTagContainer(
            instructionRequest.getDoorNumber(),
            instructionRequest.getDeliveryNumber(),
            dockTagId,
            httpHeaders,
            dockTagType));
    if (!StringUtils.isEmpty(instructionRequest.getMessageId())) {
      instruction.setMessageId(instructionRequest.getMessageId());
    } else {
      instruction.setMessageId(dockTagId);
    }
    instruction.setDockTagId(dockTagId);
    instruction.setActivityName(ReceivingConstants.DOCK_TAG);
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setDeliveryNumber(Long.parseLong(instructionRequest.getDeliveryNumber()));
    instruction.setPurchaseReferenceNumber("");
    instruction.setPurchaseReferenceLineNumber(null);
    instruction.setCreateUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    instruction.setMove(moveTreeMap);
    return instruction;
  }

  public ContainerDetails getDockTagContainer(
      String doorNumber,
      String deliveryNumber,
      String dockTagId,
      HttpHeaders httpHeaders,
      DockTagType dockTagType) {
    List<Map<String, Object>> labelDataList = new ArrayList<>();

    Map<String, Object> doorMap = new HashMap<>();
    doorMap.put("key", "DOOR");
    doorMap.put("value", doorNumber);
    labelDataList.add(doorMap);

    Map<String, Object> dateMap = new HashMap<>();
    dateMap.put("key", "DATE");
    dateMap.put("value", new SimpleDateFormat("MM/dd/yy").format(new Date()));
    labelDataList.add(dateMap);

    Map<String, Object> lpnMap = new HashMap<>();
    lpnMap.put("key", "LPN");
    lpnMap.put("value", dockTagId);
    labelDataList.add(lpnMap);

    Map<String, Object> userIdMap = new HashMap<>();
    userIdMap.put("key", "FULLUSERID");
    userIdMap.put("value", httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    labelDataList.add(userIdMap);

    Map<String, Object> deliveryMap = new HashMap<>();
    deliveryMap.put("key", "DELIVERYNBR");
    deliveryMap.put("value", deliveryNumber);
    labelDataList.add(deliveryMap);

    Map<String, Object> dockTagTypeMap = new HashMap<>();
    dockTagTypeMap.put("key", "DOCKTAGTYPE");
    dockTagTypeMap.put("value", dockTagType.getText());
    labelDataList.add(dockTagTypeMap);

    Map<String, Object> containerLabel = getContainerLabel(dockTagId, httpHeaders, labelDataList);

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setTrackingId(dockTagId);
    containerDetails.setCtrType(ContainerType.PALLET.name());
    containerDetails.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.name());
    containerDetails.setCtrReusable(Boolean.FALSE);
    containerDetails.setCtrShippable(Boolean.FALSE);
    containerDetails.setCtrLabel(containerLabel);
    return containerDetails;
  }

  public Map<String, Object> getContainerLabel(
      String labelIdentifier, HttpHeaders httpHeaders, List<Map<String, Object>> labelDataList) {

    if (configUtils.isPrintingAndroidComponentEnabled()) {
      return InstructionUtils.getContainerLabelWithNewPrintingFmt(
          labelIdentifier, httpHeaders, labelDataList);
    }
    return InstructionUtils.getContainerLabelWithOldPrintingFmt(labelIdentifier, labelDataList);
  }

  /**
   * Calculate current received quantity and has validation around it
   *
   * @param problemTagId
   * @param deliveryDocument
   * @param deliveryNumber
   * @param isKotlinEnabled
   * @param isScanToPrintFlow
   * @return
   * @throws ReceivingException
   */
  public Pair<Integer, Long> getReceivedQtyDetailsAndValidate(
      String problemTagId,
      DeliveryDocument deliveryDocument,
      String deliveryNumber,
      boolean isKotlinEnabled,
      boolean isScanToPrintFlow)
      throws ReceivingException {
    Pair<Integer, Long> result =
        getMaxReceiveAndTotalReceivedQty(problemTagId, deliveryNumber, deliveryDocument);
    Integer maxReceiveQty = result.getKey();
    Long totalReceivedQty = result.getValue();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    validateReceivedQty(
        problemTagId,
        deliveryDocument,
        deliveryNumber,
        isKotlinEnabled,
        isScanToPrintFlow,
        maxReceiveQty,
        totalReceivedQty,
        deliveryDocumentLine);

    return new Pair<>(maxReceiveQty, totalReceivedQty);
  }

  private void validateReceivedQty(
      String problemTagId,
      DeliveryDocument deliveryDocument,
      String deliveryNumber,
      boolean isKotlinEnabled,
      boolean isScanToPrintFlow,
      Integer maxReceiveQty,
      Long totalReceivedQty,
      DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    if (totalReceivedQty >= maxReceiveQty) {
      if (configUtils.getConfiguredFeatureFlag(
              getFacilityNum().toString(), GROCERY_PROBLEM_RECEIVE_FEATURE, false)
          && isNotBlank(problemTagId)) {
        LOGGER.info(
            "suppress error overage alert for problemTagId={} totalReceivedQty={} >= maxReceiveQty={}",
            problemTagId,
            totalReceivedQty,
            maxReceiveQty);
      } else {
        boolean isOverrideOverage =
            isManagerOverrideIgnoreOverage(
                deliveryNumber,
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber());

        if (!isOverrideOverage) {
          if (isKotlinEnabled && StringUtils.isEmpty(problemTagId) && !isScanToPrintFlow) {
            LOGGER.info(
                "Suppress overage alert error for PO:{} POL:{} and delivery: {}",
                deliveryDocument.getPurchaseReferenceNumber(),
                deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber(),
                deliveryNumber);
          } else {
            instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OVERAGE_ERROR);
            LOGGER.error(instructionError.getErrorMessage());
            ErrorResponse errorResponse =
                ErrorResponse.builder()
                    .errorCode(instructionError.getErrorCode())
                    .errorMessage(instructionError.getErrorMessage())
                    .errorKey(ExceptionCodes.OVERAGE_ERROR)
                    .build();
            RangeErrorResponse rangeErrorResponse =
                RangeErrorResponse.rangeErrorBuilder()
                    .rcvdqtytilldate(Math.toIntExact(totalReceivedQty))
                    .deliveryDocument(deliveryDocument)
                    .maxReceiveQty(maxReceiveQty)
                    .errorResponse(errorResponse)
                    .build();
            throw ReceivingException.builder()
                .errorResponse(rangeErrorResponse)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .build();
          }
        }
      }
    }
  }

  /**
   * Received qty based on FBQ
   *
   * @param problemTagId
   * @param deliveryNumber
   * @param deliveryDocument
   * @return
   */
  private Pair<Integer, Long> getMaxReceiveAndTotalReceivedQty(
      String problemTagId, String deliveryNumber, DeliveryDocument deliveryDocument) {
    Pair<Integer, Long> receivedQtyDetails;
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    // In case of INTL receive until fbq flag enabled
    // OR if import flag enabled and po is import PO
    // use receive until fbq logic
    boolean enableReceiveTillFbq =
        configUtils.isFeatureFlagEnabled(ReceivingConstants.ALLOW_RCV_UNTIL_FBQ)
            || ReceivingUtils.isImportPoLineFbqEnabled(
                deliveryDocument.getImportInd(), configUtils);

    if (enableReceiveTillFbq) {
      receivedQtyDetails =
          getReceivedQtyDetailsByDeliveryNumber(
              Long.parseLong(deliveryNumber), problemTagId, deliveryDocumentLine);
    } else {
      receivedQtyDetails = getReceivedQtyDetails(problemTagId, deliveryDocumentLine);
    }
    int maxReceiveQty = receivedQtyDetails.getKey();
    long totalReceivedQty = receivedQtyDetails.getValue();
    if (enableReceiveTillFbq)
      maxReceiveQty = Optional.ofNullable(deliveryDocumentLine.getFreightBillQty()).orElse(0);

    Pair<Integer, Long> result = new Pair<>(maxReceiveQty, totalReceivedQty);
    return result;
  }

  public Pair<Integer, Long> getReceivedQtyDetailsInEaAndValidate(
      String problemTagId, DeliveryDocument deliveryDocument, String deliveryNumber)
      throws ReceivingException {

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Pair<Integer, Long> receivedQtyDetails =
        getReceivedQtyDetailsInEach(problemTagId, deliveryDocumentLine);

    int maxReceiveQty = receivedQtyDetails.getKey();
    long totalReceivedQty = receivedQtyDetails.getValue();

    if (totalReceivedQty >= maxReceiveQty) {
      boolean isOverrideOverage =
          isManagerOverrideIgnoreOverage(
              deliveryNumber,
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());

      if (!isOverrideOverage) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OVERAGE_ERROR);
        LOGGER.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            (int) totalReceivedQty,
            maxReceiveQty,
            deliveryDocument);
      }
    }

    return new Pair<>(maxReceiveQty, totalReceivedQty);
  }

  /**
   * Fetches current received quantity and max received quantity
   *
   * @param problemTagId problem tag id
   * @param deliveryDocumentLine delivery document with one deliveryDocumentLine
   * @return Pair of maxReceiveQty, totalReceivedQty
   */
  public Pair<Integer, Long> getReceivedQtyDetails(
      String problemTagId, DeliveryDocumentLine deliveryDocumentLine) {
    int maxReceiveQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();

    long totalReceivedQty;

    TenantContext.get().setAtlasRcvGetRcvdQtyStart(System.currentTimeMillis());
    if (!StringUtils.isEmpty(problemTagId)) {
      totalReceivedQty = receiptService.getReceivedQtyByProblemId(problemTagId);
    } else {
      totalReceivedQty =
          receiptService.getReceivedQtyByPoAndPoLine(
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
    }
    TenantContext.get().setAtlasRcvGetRcvdQtyEnd(System.currentTimeMillis());

    return new Pair<>(maxReceiveQty, totalReceivedQty);
  }

  public Pair<Integer, Long> getReceivedQtyDetailsByDeliveryNumber(
      Long deliveryNumber, String problemTagId, DeliveryDocumentLine deliveryDocumentLine) {
    int maxReceiveQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();

    long totalReceivedQty;

    TenantContext.get().setAtlasRcvGetRcvdQtyStart(System.currentTimeMillis());
    if (!StringUtils.isEmpty(problemTagId)) {
      totalReceivedQty = receiptService.getReceivedQtyByProblemId(problemTagId);
    } else {
      totalReceivedQty =
          receiptService.receivedQtyByDeliveryPoAndPoLine(
              deliveryNumber,
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
    }
    TenantContext.get().setAtlasRcvGetRcvdQtyEnd(System.currentTimeMillis());

    return new Pair<>(maxReceiveQty, totalReceivedQty);
  }

  public Pair<Integer, Long> getReceivedQtyDetailsInEach(
      String problemTagId, DeliveryDocumentLine deliveryDocumentLine) {
    int maxReceiveQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();

    long totalReceivedQty;

    TenantContext.get().setAtlasRcvGetRcvdQtyStart(System.currentTimeMillis());
    if (!StringUtils.isEmpty(problemTagId)) {
      totalReceivedQty = receiptService.getReceivedQtyByProblemIdInEach(problemTagId);
    } else {
      totalReceivedQty =
          receiptService.getReceivedQtyByPoAndPoLineInEach(
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
    }
    TenantContext.get().setAtlasRcvGetRcvdQtyEnd(System.currentTimeMillis());

    return new Pair<>(maxReceiveQty, totalReceivedQty);
  }

  /**
   * Check if manager override for expiry is enabled
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param firstExpiryFirstOut
   * @param purchaseReferenceLineNumber
   * @return true if ignore else false
   */
  public boolean isManagerOverrideIgnoreExpiry(
      String deliveryNumber,
      String purchaseReferenceNumber,
      Boolean firstExpiryFirstOut,
      Integer purchaseReferenceLineNumber) {
    boolean isOverrideExpiry = false;
    if (configUtils.isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE)) {
      LOGGER.info(
          "FEFO = {}, deliveryNumber = {}, PO = {}, POLINE = {}",
          firstExpiryFirstOut,
          deliveryNumber,
          purchaseReferenceNumber,
          purchaseReferenceLineNumber);

      if (firstExpiryFirstOut) {
        isOverrideExpiry =
            witronDeliveryMetaDataService.isManagerOverride(
                deliveryNumber,
                purchaseReferenceNumber,
                purchaseReferenceLineNumber,
                ReceivingConstants.IGNORE_EXPIRY);
      }
    }
    LOGGER.info(
        "isOverrideExpiry = {}, for FEFO = {}, deliveryNumber = {}, PO = {}, POLINE = {}",
        isOverrideExpiry,
        firstExpiryFirstOut,
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber);

    return isOverrideExpiry;
  }

  /**
   * Check if manager override for overage is enabled
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return true if ignore else false
   */
  public boolean isManagerOverrideIgnoreOverage(
      String deliveryNumber, String purchaseReferenceNumber, int purchaseReferenceLineNumber) {
    boolean isOverrideOverage = false;
    if (configUtils.isFeatureFlagEnabled(ReceivingConstants.MANAGER_OVERRIDE_FEATURE)) {
      LOGGER.info(
          "deliveryNumber = {}, PO = {}, POLINE = {}",
          deliveryNumber,
          purchaseReferenceNumber,
          purchaseReferenceLineNumber);

      isOverrideOverage =
          witronDeliveryMetaDataService.isManagerOverride(
              deliveryNumber,
              purchaseReferenceNumber,
              purchaseReferenceLineNumber,
              ReceivingConstants.IGNORE_OVERAGE);
    }
    LOGGER.info(
        "isOverrideOverage = {}, deliveryNumber = {}, PO = {}, POLINE = {}",
        isOverrideOverage,
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber);

    return isOverrideOverage;
  }

  public OverrideInfo getOverrideInfo(
      String deliveryNumber, String purchaseReferenceNumber, int purchaseReferenceLineNumber) {
    return new OverrideInfo(
        witronDeliveryMetaDataService.getPurchaseReferenceLineMeta(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber));
  }

  /**
   * @param instruction instruction
   * @param updateInstructionRequest update instruction request
   * @param quantity quantity
   * @param container container
   * @param instructionStatus instruction status
   * @param httpHeaders http headers
   * @return PublishInstructionSummary
   */
  public PublishInstructionSummary prepareInstructionMessage(
      Instruction instruction,
      UpdateInstructionRequest updateInstructionRequest,
      Integer quantity,
      Container container,
      InstructionStatus instructionStatus,
      HttpHeaders httpHeaders) {
    PublishInstructionSummary.UserInfo userInfo =
        new PublishInstructionSummary.UserInfo(
            httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY),
            httpHeaders.getFirst(ReceivingConstants.SECURITY_HEADER_KEY));
    PublishInstructionSummary publishInstructionSummary = new PublishInstructionSummary();

    publishInstructionSummary.setMessageId(instruction.getMessageId());
    publishInstructionSummary.setInstructionCode(instruction.getInstructionCode());
    publishInstructionSummary.setInstructionMsg(instruction.getInstructionMsg());
    publishInstructionSummary.setActivityName(instruction.getActivityName());
    publishInstructionSummary.setPrintChildContainerLabels(
        instruction.getPrintChildContainerLabels());
    publishInstructionSummary.setInstructionExecutionTS(new Date());
    publishInstructionSummary.setUserInfo(userInfo);

    if (container != null && appConfig.isPublishContainerDetailsInInstruction()) {
      publishInstructionSummary.setContainer(container);
    }

    // Check and set warehouseAreaCode is exists in container Items
    if (Objects.nonNull(container) && !CollectionUtils.isEmpty(container.getContainerItems()))
      publishInstructionSummary.setWarehouseAreaCode(
          container.getContainerItems().get(0).getWarehouseAreaCode());

    switch (instructionStatus) {
      case CREATED:
        publishInstructionSummary.setInstructionStatus(
            InstructionStatus.CREATED.getInstructionStatus());
        break;
      case UPDATED:
        publishInstructionSummary.setInstructionStatus(
            InstructionStatus.UPDATED.getInstructionStatus());
        publishInstructionSummary.setUpdatedQty(quantity);
        publishInstructionSummary.setUpdatedQtyUOM(ReceivingConstants.Uom.VNPK);
        publishInstructionSummary.setVnpkQty(
            updateInstructionRequest.getDeliveryDocumentLines().get(0).getVnpkQty());
        publishInstructionSummary.setWhpkQty(
            updateInstructionRequest.getDeliveryDocumentLines().get(0).getWhpkQty());
        // RCV will send userRole to WTD in update Instruction
        if (Objects.nonNull(updateInstructionRequest.getUserRole()))
          publishInstructionSummary.setUserRole(updateInstructionRequest.getUserRole());
        if (Objects.nonNull(updateInstructionRequest.getCreateUser()))
          publishInstructionSummary.setCreateUser(updateInstructionRequest.getCreateUser());
        if (Objects.nonNull(updateInstructionRequest.getEventType()))
          publishInstructionSummary.setEventType(updateInstructionRequest.getEventType());

        break;
      case COMPLETED:
        publishInstructionSummary.setInstructionStatus(
            InstructionStatus.COMPLETED.getInstructionStatus());
        break;
      default:
        break;
    }
    return publishInstructionSummary;
  }

  /**
   * Publishes Instruction to WFT
   *
   * @param httpHeaders http headers
   * @param publishInstructionSummary instruction message to be published
   */
  public void publishInstruction(
      HttpHeaders httpHeaders, PublishInstructionSummary publishInstructionSummary) {
    MessagePublisher messagePublisher =
        configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class);
    messagePublisher.publish(
        publishInstructionSummary, ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders));
  }

  /**
   * Publishes update instruction message to WFT
   *
   * @param httpHeaders http headers
   * @param instruction instruction
   * @param instructionUpdateRequest instruction update request
   * @param activityName activity name
   */
  public void publishUpdateInstructionToWFM(
      HttpHeaders httpHeaders,
      Instruction instruction,
      UpdateInstructionRequest instructionUpdateRequest,
      String activityName) {
    PublishInstructionSummary publishInstructionSummary =
        prepareInstructionMessage(
            instruction, instructionUpdateRequest, 1, null, InstructionStatus.UPDATED, httpHeaders);
    publishInstructionSummary.setActivityName(activityName);
    publishInstructionSummary.setVnpkQty(1);
    publishInstructionSummary.setWhpkQty(1);
    publishInstruction(httpHeaders, publishInstructionSummary);
  }

  /**
   * This method is used to publish instruction to work force management. Instruction is published
   * in three scenario, when instruction is either created or updated or completed.
   *
   * @param instruction instruction
   * @param updateInstructionRequest update instruction request
   * @param quantity quantity
   * @param container container
   * @param instructionStatus instruction status
   * @param httpHeaders http headers
   */
  public void publishInstruction(
      Instruction instruction,
      UpdateInstructionRequest updateInstructionRequest,
      Integer quantity,
      Container container,
      InstructionStatus instructionStatus,
      HttpHeaders httpHeaders) {
    PublishInstructionSummary publishInstructionSummary =
        prepareInstructionMessage(
            instruction,
            updateInstructionRequest,
            quantity,
            container,
            instructionStatus,
            httpHeaders);

    publishInstruction(httpHeaders, publishInstructionSummary);
  }

  /**
   * Publish conatiner
   *
   * @param consolidatedContainer consolidated container
   * @param httpHeaders headers
   * @param putToRetry if needs to be retried
   */
  public void publishConsolidatedContainer(
      Container consolidatedContainer, HttpHeaders httpHeaders, boolean putToRetry) {
    Map<String, Object> headersToSend = getForwardableHeadersWithRequestOriginator(httpHeaders);
    headersToSend.put(ReceivingConstants.IDEM_POTENCY_KEY, consolidatedContainer.getTrackingId());
    containerService.publishContainer(consolidatedContainer, headersToSend, putToRetry);
  }

  /**
   * Addition params for printing
   *
   * @param instruction
   * @param rotateDate rotate date
   * @return map of additional attributes
   * @throws ReceivingException throws checked Receiving Exception
   */
  public List<Map<String, Object>> getAdditionalParam(Instruction instruction, String rotateDate)
      throws ReceivingException {
    List<Map<String, Object>> additionalAttributeList = new ArrayList<>();
    int qty = instruction.getReceivedQuantity();
    // Prepare new key/value pair for total case quantity received on pallet

    Map<String, Object> quantityMap = new HashMap<>();
    quantityMap.put("key", "QTY");

    if (instruction.getReceivedQuantity() == -1) quantityMap.put("value", "NA");
    else quantityMap.put("value", qty);

    additionalAttributeList.add(quantityMap);

    if (configUtils.isShowRotateDateOnPrintLabelEnabled(getFacilityNum())
        && !StringUtils.isEmpty(rotateDate)) {
      Map<String, Object> rotateDateMap = new HashMap<>();
      rotateDateMap.put("key", "ROTATEDATE");
      rotateDateMap.put("value", rotateDate);
      additionalAttributeList.add(rotateDateMap);
    }

    // if PO's are non-national,then we need add few properties which are required for label
    if (instruction.getActivityName().equalsIgnoreCase(PurchaseReferenceType.POCON.toString())
        || instruction.getActivityName().equalsIgnoreCase(PurchaseReferenceType.DSDC.toString())) {
      String originalChannel = instruction.getOriginalChannel();
      if (!Objects.isNull(originalChannel)) {
        Map<String, Object> originalPoType = new HashMap<>();
        originalPoType.put("key", "CHANNELMETHOD");
        originalPoType.put("value", instruction.getOriginalChannel());
        additionalAttributeList.add(originalPoType);
      }
    }

    return additionalAttributeList;
  }

  /**
   * Mutate the print job with addition attributes
   *
   * @param instructionFromDB instruction from DB
   * @param rotateDate rotate date
   * @return print job with additional attributes
   * @throws ReceivingException throws checked Receiving Exception
   */
  private Map<String, Object> getPrintJobWithAdditionalAttributes(
      Instruction instructionFromDB, String rotateDate) throws ReceivingException {
    // Add quantity to the pallet label
    Map<String, Object> printJob = instructionFromDB.getContainer().getCtrLabel();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printRequest.get("data");
    labelData.addAll(getAdditionalParam(instructionFromDB, rotateDate));
    printRequest.put("data", labelData);
    printRequests.set(0, printRequest);
    printJob.put("printRequests", printRequests);
    return printJob;
  }

  /**
   * Mutate the print job with addition attributes for old peint response
   *
   * @param instructionFromDB instruction from DB
   * @param rotateDate rotate date
   * @return old print job map
   * @throws ReceivingException throws checked Receiving Exception
   */
  private List<Map<String, Object>> getOldPrintJobWithAdditionalAttributes(
      Instruction instructionFromDB, String rotateDate) throws ReceivingException {
    // Add quantity to the pallet label
    Map<String, Object> printJob = instructionFromDB.getContainer().getCtrLabel();
    List<Map<String, Object>> labelData = (List<Map<String, Object>>) printJob.get("labelData");

    //    labelData = InstructionUtils.addOriginalPoTypeToLabel(instructionFromDB, labelData);
    labelData.addAll(getAdditionalParam(instructionFromDB, rotateDate));
    printJob.put("labelData", labelData);
    printJob.put("data", labelData);
    return Arrays.asList(printJob);
  }

  /**
   * Prepares instruction response from instruction, container and printer id
   *
   * @param instruction instruction
   * @param consolidatedContainer consolidated container
   * @param printerId printer id
   * @param printerName
   * @return instruction response for client
   * @throws ReceivingException throws checked Receiving Exception
   */
  public InstructionResponse prepareInstructionResponse(
      Instruction instruction,
      Container consolidatedContainer,
      Integer printerId,
      String printerName)
      throws ReceivingException {
    List<Map<String, Object>> oldPrintJob = null;
    Map<String, Object> printJob = null;

    String rotateDate =
        (Boolean.TRUE.equals(instruction.getFirstExpiryFirstOut())
                && !Objects.isNull(
                    consolidatedContainer.getContainerItems().get(0).getRotateDate()))
            ? new SimpleDateFormat("MM/dd/yy")
                .format(consolidatedContainer.getContainerItems().get(0).getRotateDate())
            : "-";
    if (!configUtils.isPrintingAndroidComponentEnabled()) {
      oldPrintJob = getOldPrintJobWithAdditionalAttributes(instruction, rotateDate);
    } else {
      if (configUtils.isPoConfirmationFlagEnabled(getFacilityNum())) {
        final String dcTimeZone = configUtils.getDCTimeZone(getFacilityNum());
        printJob =
            InstructionUtils.getPrintJobWithWitronAttributes(
                instruction, rotateDate, instruction.getCompleteUserId(), printerName, dcTimeZone);
      } else {
        printJob = getPrintJobWithAdditionalAttributes(instruction, rotateDate);
      }
    }

    if (!configUtils.isPrintingAndroidComponentEnabled()) {
      return new InstructionResponseImplOld(null, null, instruction, oldPrintJob);
    }
    return new InstructionResponseImplNew(null, null, instruction, printJob);
  }

  public Container getConsolidatedContainerAndPublishContainer(
      Container parentContainer, HttpHeaders httpHeaders, boolean putToRetry)
      throws ReceivingException {
    Container consolidatedContainer = containerService.getContainerIncludingChild(parentContainer);
    publishConsolidatedContainer(consolidatedContainer, httpHeaders, putToRetry);
    return consolidatedContainer;
  }

  /**
   * Get received qty map by PO/POL
   *
   * @param deliveryDocuments list of delivery document
   * @return map of received qty by po/pol
   */
  public Map<String, Long> getReceivedQtyMapByPOPOL(
      List<DeliveryDocument> deliveryDocuments, String uom) {
    List<String> purchaseReferenceNumbers = new ArrayList<>();
    Set<Integer> purchaseReferenceLineNumbers = new HashSet<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      purchaseReferenceNumbers.add(deliveryDocument.getPurchaseReferenceNumber());
      for (DeliveryDocumentLine deliveryDocumentLine :
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())) {
        purchaseReferenceLineNumbers.add(deliveryDocumentLine.getPurchaseReferenceLineNumber());
      }
    }
    List<ReceiptSummaryQtyByPoAndPoLineResponse> qtyByPoAndPoLineList;

    if (ReceivingConstants.Uom.EACHES.equalsIgnoreCase(uom)) {

      // TODO dirty-received-qty-fix use fbq
      if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(deliveryDocuments)
          && org.apache.commons.collections4.CollectionUtils.isNotEmpty(
              deliveryDocuments.get(0).getDeliveryDocumentLines())
          && Boolean.TRUE.equals(
              isImportPoLineFbqEnabled(deliveryDocuments.get(0).getImportInd(), configUtils))) {

        qtyByPoAndPoLineList =
            receiptService.receivedQtyInEAByDeliveryPoAndPoLineList(
                deliveryDocuments.get(0).getDeliveryNumber(),
                purchaseReferenceNumbers,
                purchaseReferenceLineNumbers);
      } else {
        qtyByPoAndPoLineList =
            receiptService.receivedQtyInEAByPoAndPoLineList(
                purchaseReferenceNumbers, purchaseReferenceLineNumbers);
      }
    } else {
      // TODO dirty-received-qty-fix use fbq
      if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(deliveryDocuments)
          && org.apache.commons.collections4.CollectionUtils.isNotEmpty(
              deliveryDocuments.get(0).getDeliveryDocumentLines())
          && Boolean.TRUE.equals(
              isImportPoLineFbqEnabled(deliveryDocuments.get(0).getImportInd(), configUtils))) {

        qtyByPoAndPoLineList =
            receiptService.receivedQtyInVNPKByDeliveryPoAndPoLineList(
                deliveryDocuments.get(0).getDeliveryNumber(),
                purchaseReferenceNumbers,
                purchaseReferenceLineNumbers);
      } else {
        qtyByPoAndPoLineList =
            receiptService.receivedQtyInVNPKByPoAndPoLineList(
                purchaseReferenceNumbers, purchaseReferenceLineNumbers);
      }
    }
    Map<String, Long> receivedQtyByPoAndPoLineMap = new HashMap<>();
    for (ReceiptSummaryQtyByPoAndPoLineResponse qtyByPoAndPoLine : qtyByPoAndPoLineList) {
      String key =
          qtyByPoAndPoLine.getPurchaseReferenceNumber()
              + ReceivingConstants.DELIM_DASH
              + qtyByPoAndPoLine.getPurchaseReferenceLineNumber();
      receivedQtyByPoAndPoLineMap.put(key, qtyByPoAndPoLine.getReceivedQty());
    }
    return receivedQtyByPoAndPoLineMap;
  }

  public ReceiptsAggregator getReceivedQtyByPoPol(List<DeliveryDocument> deliveryDocuments) {
    List<String> poNumberList = new ArrayList<>();
    Set<Integer> poLineNumberSet = new HashSet<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      poNumberList.add(deliveryDocument.getPurchaseReferenceNumber());
      for (DeliveryDocumentLine deliveryDocumentLine :
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())) {
        poLineNumberSet.add(deliveryDocumentLine.getPurchaseReferenceLineNumber());
      }
    }
    List<ReceiptSummaryEachesResponse> poLineReceipts =
        receiptService.receivedQtyByPoAndPoLineList(poNumberList, poLineNumberSet);
    ReceiptsAggregator receiptsAggregator = ReceiptsAggregator.fromPOLReceipts(poLineReceipts);
    return receiptsAggregator;
  }

  /**
   * Auto select PO/POL based on must arrive by data
   *
   * @param deliveryDocuments list of delivery documents
   * @param qtyToReceive quantity to be received
   * @return selected PO POL if any otherwise null
   */
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      executionFlow = "MULTI-PO-Line-Select")
  public Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineMABD(
      List<DeliveryDocument> deliveryDocuments, int qtyToReceive, String uom) {
    if (configUtils.isFeatureFlagEnabled(IS_RECEIPT_AGGREGATOR_CHECK_ENABLED)) {
      ReceiptsAggregator receiptsAggregator = getReceivedQtyByPoPol(deliveryDocuments);
      LOGGER.info("Fetched received qty by list of PO and POL {}", receiptsAggregator);
      return autoSelectDocumentAndDocumentLineMABDGivenReceivedQty(
          deliveryDocuments, qtyToReceive, receiptsAggregator, uom);
    } else {
      Map<String, Long> receivedQtyByPoAndPoLineMap =
          getReceivedQtyMapByPOPOL(deliveryDocuments, uom);
      LOGGER.info("Fetched received qty by list of PO and POL {}", receivedQtyByPoAndPoLineMap);
      return autoSelectDocumentAndDocumentLineMABDGivenReceivedQty(
          deliveryDocuments, qtyToReceive, receivedQtyByPoAndPoLineMap, uom);
    }
  }

  public Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineMABDGivenReceivedQty(
      List<DeliveryDocument> deliveryDocuments,
      int qtyToReceive,
      Map<String, Long> receivedQtyByPoAndPoLineMap,
      String uom) {
    DeliveryDocument selectedPOAgainstAllowedOverages = null;
    DeliveryDocument selectedDeliveryDocument = null;
    DeliveryDocumentLine selectedPOLAgainstAllowedOverages = null;
    DeliveryDocumentLine selectedDeliveryDocumentLine = null;
    Long totalReceivedQty = 0L;
    Long totalReceivedQtyAgainstAllowedOverages = 0L;
    deliveryDocuments =
        deliveryDocuments
            .stream()
            .sorted(
                Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                    .thenComparing(DeliveryDocument::getPurchaseReferenceNumber))
            .collect(Collectors.toList());
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      List<DeliveryDocumentLine> deliveryDocumentLines =
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines());
      deliveryDocumentLines =
          deliveryDocumentLines
              .stream()
              .sorted(Comparator.comparing(DeliveryDocumentLine::getPurchaseReferenceLineNumber))
              .collect(Collectors.toList());
      for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocumentLines) {
        String key =
            deliveryDocument.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        totalReceivedQty = receivedQtyByPoAndPoLineMap.getOrDefault(key, 0L);
        // TODO dirty-received-qty-fix use flag to use line fbq instead of order qty

        Integer totalOrderQty =
            computeEffectiveTotalQty(
                deliveryDocumentLine, deliveryDocument.getImportInd(), configUtils);
        if (ReceivingConstants.Uom.EACHES.equalsIgnoreCase(uom)) {
          totalOrderQty =
              ReceivingUtils.conversionToEaches(
                  totalOrderQty,
                  deliveryDocumentLine.getQtyUOM(),
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack());
        }
        if (totalReceivedQty + qtyToReceive <= totalOrderQty) {
          selectedDeliveryDocument = deliveryDocument;
          selectedDeliveryDocumentLine = deliveryDocumentLine;
          break;
        } else if (Objects.isNull(selectedPOAgainstAllowedOverages)) {
          if (totalReceivedQty + qtyToReceive
              // TODO dirty-received-qty-fix use flag to use line fbq instead of order qty
              // TODO dirty-received-qty-fix doubt - replaced with totalOrderQty from
              // deliveryDocumentLine.getTotalOrderQty() - any issue?
              <= totalOrderQty + deliveryDocumentLine.getOverageQtyLimit()) {
            selectedPOAgainstAllowedOverages = deliveryDocument;
            selectedPOLAgainstAllowedOverages = deliveryDocumentLine;
            totalReceivedQtyAgainstAllowedOverages = totalReceivedQty;
          }
        }
      }
      if (Objects.nonNull(selectedDeliveryDocument)) break;
    }
    if (Objects.nonNull(selectedDeliveryDocument)) {
      LOGGER.info(
          "Selected PO {} and POL {} against ordered qty",
          selectedDeliveryDocument.getPurchaseReferenceNumber(),
          selectedDeliveryDocumentLine.getPurchaseReferenceLineNumber());
      selectedDeliveryDocument.getDeliveryDocumentLines().clear();
      selectedDeliveryDocument.getDeliveryDocumentLines().add(selectedDeliveryDocumentLine);
      return new Pair<>(selectedDeliveryDocument, totalReceivedQty);
    } else if (Objects.nonNull(selectedPOAgainstAllowedOverages)) {
      LOGGER.info(
          "Selected PO {} and POL {} against allowed ovg qty",
          selectedPOAgainstAllowedOverages.getPurchaseReferenceNumber(),
          selectedPOLAgainstAllowedOverages.getPurchaseReferenceLineNumber());
      selectedPOAgainstAllowedOverages.getDeliveryDocumentLines().clear();
      selectedPOAgainstAllowedOverages
          .getDeliveryDocumentLines()
          .add(selectedPOLAgainstAllowedOverages);
      return new Pair<>(selectedPOAgainstAllowedOverages, totalReceivedQtyAgainstAllowedOverages);
    }
    return null;
  }

  public Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineMABDGivenReceivedQty(
      List<DeliveryDocument> deliveryDocuments,
      int qtyToReceive,
      ReceiptsAggregator receiptsAggregator,
      String uom) {
    DeliveryDocument selectedPOAgainstAllowedOverages = null;
    DeliveryDocument selectedDeliveryDocument = null;
    DeliveryDocumentLine selectedPOLAgainstAllowedOverages = null;
    DeliveryDocumentLine selectedDeliveryDocumentLine = null;
    Long totalReceivedQty = 0L;
    Long totalReceivedQtyAgainstAllowedOverages = 0L;
    deliveryDocuments =
        deliveryDocuments
            .stream()
            .sorted(
                Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                    .thenComparing(DeliveryDocument::getPurchaseReferenceNumber))
            .collect(Collectors.toList());
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      List<DeliveryDocumentLine> deliveryDocumentLines =
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines());
      deliveryDocumentLines =
          deliveryDocumentLines
              .stream()
              .sorted(Comparator.comparing(DeliveryDocumentLine::getPurchaseReferenceLineNumber))
              .collect(Collectors.toList());
      for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocumentLines) {
        String key =
            deliveryDocument.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        totalReceivedQty =
            receiptsAggregator.getByPoPolInZA(
                deliveryDocument.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack());
        totalReceivedQty = totalReceivedQty == null ? 0 : totalReceivedQty;
        // TODO dirty-received-qty-fix use flag to use line fbq instead of order qty

        Integer totalOrderQty =
            computeEffectiveTotalQty(
                deliveryDocumentLine, deliveryDocument.getImportInd(), configUtils);
        if (ReceivingConstants.Uom.EACHES.equalsIgnoreCase(uom)) {
          totalOrderQty =
              ReceivingUtils.conversionToEaches(
                  totalOrderQty,
                  deliveryDocumentLine.getQtyUOM(),
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack());
        }
        if (totalReceivedQty + qtyToReceive <= totalOrderQty) {
          selectedDeliveryDocument = deliveryDocument;
          selectedDeliveryDocumentLine = deliveryDocumentLine;
          break;
        } else if (Objects.isNull(selectedPOAgainstAllowedOverages)) {
          if (totalReceivedQty + qtyToReceive
              // TODO dirty-received-qty-fix use flag to use line fbq instead of order qty
              // TODO dirty-received-qty-fix doubt - replaced with totalOrderQty from
              // deliveryDocumentLine.getTotalOrderQty() - any issue?
              <= totalOrderQty + deliveryDocumentLine.getOverageQtyLimit()) {
            selectedPOAgainstAllowedOverages = deliveryDocument;
            selectedPOLAgainstAllowedOverages = deliveryDocumentLine;
            totalReceivedQtyAgainstAllowedOverages = totalReceivedQty;
          }
        }
      }
      if (Objects.nonNull(selectedDeliveryDocument)) break;
    }
    if (Objects.nonNull(selectedDeliveryDocument)) {
      LOGGER.info(
          "Selected PO {} and POL {} against ordered qty",
          selectedDeliveryDocument.getPurchaseReferenceNumber(),
          selectedDeliveryDocumentLine.getPurchaseReferenceLineNumber());
      selectedDeliveryDocument.getDeliveryDocumentLines().clear();
      selectedDeliveryDocument.getDeliveryDocumentLines().add(selectedDeliveryDocumentLine);
      return new Pair<>(selectedDeliveryDocument, totalReceivedQty);
    } else if (Objects.nonNull(selectedPOAgainstAllowedOverages)) {
      LOGGER.info(
          "Selected PO {} and POL {} against allowed ovg qty",
          selectedPOAgainstAllowedOverages.getPurchaseReferenceNumber(),
          selectedPOLAgainstAllowedOverages.getPurchaseReferenceLineNumber());
      selectedPOAgainstAllowedOverages.getDeliveryDocumentLines().clear();
      selectedPOAgainstAllowedOverages
          .getDeliveryDocumentLines()
          .add(selectedPOLAgainstAllowedOverages);
      return new Pair<>(selectedPOAgainstAllowedOverages, totalReceivedQtyAgainstAllowedOverages);
    }
    return null;
  }

  public ReceiptsAggregator getReceivedQtyByDeliveryPoPol(
      List<DeliveryDocument> deliveryDocuments) {
    List<String> poNumberList = new ArrayList<>();
    Set<Integer> poLineNumberSet = new HashSet<>();
    List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts = new ArrayList<>();
    List<ReceiptSummaryEachesResponse> poLineReceipts = new ArrayList<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      poNumberList.add(deliveryDocument.getPurchaseReferenceNumber());
      for (DeliveryDocumentLine deliveryDocumentLine :
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())) {
        poLineNumberSet.add(deliveryDocumentLine.getPurchaseReferenceLineNumber());
      }
    }
    if (!CollectionUtils.isEmpty(deliveryDocuments)) {
      Long deliveryNumber = deliveryDocuments.get(0).getDeliveryNumber();
      deliveryPoLineReceipts =
          receiptService.receivedQtyByPoAndPoLinesAndDelivery(
              deliveryNumber, poNumberList, poLineNumberSet);
      poLineReceipts = receiptService.receivedQtyByPoAndPoLineList(poNumberList, poLineNumberSet);
    }
    return ReceiptsAggregator.fromPOLandDPOLReceipts(poLineReceipts, deliveryPoLineReceipts);
  }

  /**
   * Auto select PO/POL based on round robin basis
   *
   * @param deliveryDocuments list of delivery documents
   * @param qtyToReceive quantity to be received
   * @return selected PO POL if any otherwise null
   */
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      executionFlow = "MULTI-PO-Line-Select-Round-Robin")
  public Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineRoundRobin(
      List<DeliveryDocument> deliveryDocuments, int qtyToReceive) {
    if (configUtils.isFeatureFlagEnabled(IS_RECEIPT_AGGREGATOR_CHECK_ENABLED)) {
      ReceiptsAggregator receiptsAggregator = getReceivedQtyByDeliveryPoPol(deliveryDocuments);
      LOGGER.info("Fetched received qty by list of PO and POL {}", receiptsAggregator);
      return autoSelectDocumentAndDocumentLineRoundRobinGivenReceivedQty(
          deliveryDocuments, qtyToReceive, receiptsAggregator);
    } else {
      Map<String, Long> receivedQtyByPoAndPoLineMap =
          getReceivedQtyMapByPOPOL(deliveryDocuments, ReceivingConstants.EMPTY_STRING);
      LOGGER.info("Fetched received qty by list of PO and POL {}", receivedQtyByPoAndPoLineMap);
      return autoSelectDocumentAndDocumentLineRoundRobinGivenReceivedQty(
          deliveryDocuments, qtyToReceive, receivedQtyByPoAndPoLineMap);
    }
  }

  public Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineRoundRobinGivenReceivedQty(
      List<DeliveryDocument> deliveryDocuments,
      int qtyToReceive,
      Map<String, Long> receivedQtyByPoAndPoLineMap) {

    if (!appConfig.isRoundRobinPOSelectEnabled()) {
      return autoSelectDocumentAndDocumentLineMABDGivenReceivedQty(
          deliveryDocuments, qtyToReceive, receivedQtyByPoAndPoLineMap, EMPTY_STRING);
    }

    DeliveryDocument selectedPOAgainstAllowedOverages = null;
    DeliveryDocument selectedDeliveryDocument = null;
    DeliveryDocument selectedPOAgainstAllowedOveragesMABD = null;
    DeliveryDocument selectedDeliveryDocumentMABD = null;
    DeliveryDocumentLine selectedPOLAgainstAllowedOverages = null;
    DeliveryDocumentLine selectedDeliveryDocumentLine = null;
    DeliveryDocumentLine selectedPOLAgainstAllowedOveragesMABD = null;
    DeliveryDocumentLine selectedDeliveryDocumentLineMABD = null;

    long totalOrderedQuantity = 0L;
    long totalAllowedQuantity = 0L;
    Long totalPOLineReceivedQty = 0L;
    long totalPOLineReceivedQtyAgainstAllowedOverages = 0L;
    long receivedQtyAgainstOrderedExhaustedPOLines = 0L;
    long receivedQtyAgainstAllowedExhaustedPOLines = 0L;

    // Removing PO-Lines which are exhausted and calculating total quantity for the leftover
    // PO-Lines
    Set<String> orderedExhaustedPOLines = new HashSet<>();
    Set<String> allowedExhaustedPOLines = new HashSet<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())) {
        String key =
            deliveryDocument.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        totalPOLineReceivedQty = receivedQtyByPoAndPoLineMap.get(key);
        totalPOLineReceivedQty = totalPOLineReceivedQty == null ? 0 : totalPOLineReceivedQty;

        Integer effectiveTotalOrderQty =
            ReceivingUtils.computeEffectiveTotalQty(
                deliveryDocumentLine, deliveryDocument.getImportInd(), configUtils);

        if (effectiveTotalOrderQty <= totalPOLineReceivedQty) {
          orderedExhaustedPOLines.add(key);
          receivedQtyAgainstOrderedExhaustedPOLines += totalPOLineReceivedQty;
        }
        totalOrderedQuantity += effectiveTotalOrderQty;

        if (effectiveTotalOrderQty + deliveryDocumentLine.getOverageQtyLimit()
            <= totalPOLineReceivedQty) {
          allowedExhaustedPOLines.add(key);
          receivedQtyAgainstAllowedExhaustedPOLines +=
              totalPOLineReceivedQty - deliveryDocumentLine.getTotalOrderQty();
        }

        totalAllowedQuantity += deliveryDocumentLine.getOverageQtyLimit();
      }
    }

    // Sorting documents on MABD
    deliveryDocuments =
        deliveryDocuments
            .stream()
            .sorted(
                Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                    .thenComparing(DeliveryDocument::getPurchaseReferenceNumber))
            .collect(Collectors.toList());

    // Find PO specific adjusted batch size
    long totalReceivedQuantity =
        receivedQtyByPoAndPoLineMap.values().stream().mapToLong(v -> v).sum();
    long totalReceivedQuantityAgainstOverage = totalReceivedQuantity - totalOrderedQuantity;
    long batchSize =
        configUtils.getDCSpecificPODistributionBatchSize(TenantContext.getFacilityNum());
    if (batchSize == 0) {
      batchSize = totalOrderedQuantity == 0 ? totalAllowedQuantity : totalOrderedQuantity;
    }
    Pair<Long, Long> batchSizePair =
        getAdjustedBatchSize(
            deliveryDocuments,
            batchSize,
            orderedExhaustedPOLines,
            allowedExhaustedPOLines,
            totalOrderedQuantity,
            totalAllowedQuantity);
    long orderedQtyBatchSize = batchSizePair.getKey();
    long allowedQtyBatchSize = batchSizePair.getValue();

    // Nothing left to receive: Overage scenario
    if (orderedQtyBatchSize == 0 && allowedQtyBatchSize == 0) {
      return null;
    }

    long currentPositionForOrdered =
        orderedQtyBatchSize > 0
            ? (totalReceivedQuantity - receivedQtyAgainstOrderedExhaustedPOLines)
                % orderedQtyBatchSize
            : Long.MAX_VALUE;
    long currentPositionForAllowed =
        allowedQtyBatchSize > 0
            ? (totalReceivedQuantityAgainstOverage - receivedQtyAgainstAllowedExhaustedPOLines)
                % allowedQtyBatchSize
            : Long.MAX_VALUE;

    // Finding the PO to select and if we can receive the quantity without going over the
    // ordered/allowed quantity
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      List<DeliveryDocumentLine> deliveryDocumentLines =
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines());
      deliveryDocumentLines =
          deliveryDocumentLines
              .stream()
              .sorted(Comparator.comparing(DeliveryDocumentLine::getPurchaseReferenceLineNumber))
              .collect(Collectors.toList());
      for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocumentLines) {
        String key =
            deliveryDocument.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        totalPOLineReceivedQty = receivedQtyByPoAndPoLineMap.get(key);
        totalPOLineReceivedQty = totalPOLineReceivedQty == null ? 0 : totalPOLineReceivedQty;

        if (!orderedExhaustedPOLines.contains(key)) {
          Integer effectiveTotalOrderQty =
              ReceivingUtils.computeEffectiveTotalQty(
                  deliveryDocumentLine, deliveryDocument.getImportInd(), configUtils);
          long lastPositionForPOLineOrder =
              (long) Math.ceil((double) effectiveTotalOrderQty * batchSize / totalOrderedQuantity);
          if (currentPositionForOrdered + qtyToReceive <= lastPositionForPOLineOrder) {
            selectedDeliveryDocument = deliveryDocument;
            selectedDeliveryDocumentLine = deliveryDocumentLine;
            break;
          } else {
            currentPositionForOrdered -= lastPositionForPOLineOrder;
            currentPositionForOrdered =
                currentPositionForOrdered < 0 ? 0 : currentPositionForOrdered;
          }
        }

        if (!allowedExhaustedPOLines.contains(key)) {
          long lastPositionForPOLineAllowed =
              (long)
                  Math.ceil(
                      (double) deliveryDocumentLine.getOverageQtyLimit()
                          * batchSize
                          / totalAllowedQuantity);
          if (Objects.isNull(selectedPOAgainstAllowedOverages)) {
            if (currentPositionForAllowed + qtyToReceive <= lastPositionForPOLineAllowed) {
              selectedPOAgainstAllowedOverages = deliveryDocument;
              selectedPOLAgainstAllowedOverages = deliveryDocumentLine;
              totalPOLineReceivedQtyAgainstAllowedOverages = totalPOLineReceivedQty;
            } else {
              currentPositionForAllowed -= lastPositionForPOLineAllowed;
              currentPositionForAllowed =
                  currentPositionForAllowed < 0 ? 0 : currentPositionForAllowed;
            }
          }
        }

        // Fallback MABD logic for receiving quantity which batch wise distribution doesn't support
        if (Objects.isNull(selectedDeliveryDocumentMABD)) {
          Integer effectiveOrderQty =
              computeEffectiveTotalQty(
                  deliveryDocumentLine, deliveryDocument.getImportInd(), configUtils);
          if (totalPOLineReceivedQty + qtyToReceive <= effectiveOrderQty) {
            selectedDeliveryDocumentMABD = deliveryDocument;
            selectedDeliveryDocumentLineMABD = deliveryDocumentLine;
          } else if (Objects.isNull(selectedPOAgainstAllowedOveragesMABD)
              && (totalPOLineReceivedQty + qtyToReceive
                  <= effectiveOrderQty + deliveryDocumentLine.getOverageQtyLimit())) {
            selectedPOAgainstAllowedOveragesMABD = deliveryDocument;
            selectedPOLAgainstAllowedOveragesMABD = deliveryDocumentLine;
            totalPOLineReceivedQtyAgainstAllowedOverages = totalPOLineReceivedQty;
          }
        }
      }
      if (Objects.nonNull(selectedDeliveryDocument)) break;
    }

    if (Objects.nonNull(selectedDeliveryDocument)) {
      LOGGER.info(
          "Selected PO {} and POL {} against ordered qty",
          selectedDeliveryDocument.getPurchaseReferenceNumber(),
          selectedDeliveryDocumentLine.getPurchaseReferenceLineNumber());
      selectedDeliveryDocument.getDeliveryDocumentLines().clear();
      selectedDeliveryDocument.getDeliveryDocumentLines().add(selectedDeliveryDocumentLine);
      return new Pair<>(selectedDeliveryDocument, totalPOLineReceivedQty);
    } else if (Objects.nonNull(selectedPOAgainstAllowedOverages)) {
      LOGGER.info(
          "Selected PO {} and POL {} against allowed ovg qty",
          selectedPOAgainstAllowedOverages.getPurchaseReferenceNumber(),
          selectedPOLAgainstAllowedOverages.getPurchaseReferenceLineNumber());
      selectedPOAgainstAllowedOverages.getDeliveryDocumentLines().clear();
      selectedPOAgainstAllowedOverages
          .getDeliveryDocumentLines()
          .add(selectedPOLAgainstAllowedOverages);
      return new Pair<>(
          selectedPOAgainstAllowedOverages, totalPOLineReceivedQtyAgainstAllowedOverages);
    } else if (Objects.nonNull(selectedDeliveryDocumentMABD)) {
      LOGGER.info(
          "Selected PO {} and POL {} against ordered qty",
          selectedDeliveryDocumentMABD.getPurchaseReferenceNumber(),
          selectedDeliveryDocumentLineMABD.getPurchaseReferenceLineNumber());
      selectedDeliveryDocumentMABD.getDeliveryDocumentLines().clear();
      selectedDeliveryDocumentMABD.getDeliveryDocumentLines().add(selectedDeliveryDocumentLineMABD);
      return new Pair<>(selectedDeliveryDocumentMABD, totalPOLineReceivedQty);
    } else if (Objects.nonNull(selectedPOAgainstAllowedOveragesMABD)) {
      LOGGER.info(
          "Selected PO {} and POL {} against allowed ovg qty",
          selectedPOAgainstAllowedOveragesMABD.getPurchaseReferenceNumber(),
          selectedPOLAgainstAllowedOveragesMABD.getPurchaseReferenceLineNumber());
      selectedPOAgainstAllowedOveragesMABD.getDeliveryDocumentLines().clear();
      selectedPOAgainstAllowedOveragesMABD
          .getDeliveryDocumentLines()
          .add(selectedPOLAgainstAllowedOveragesMABD);
      return new Pair<>(
          selectedPOAgainstAllowedOveragesMABD, totalPOLineReceivedQtyAgainstAllowedOverages);
    }
    return null;
  }

  public Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLineRoundRobinGivenReceivedQty(
      List<DeliveryDocument> deliveryDocuments,
      int qtyToReceive,
      ReceiptsAggregator receiptsAggregator) {

    if (!appConfig.isRoundRobinPOSelectEnabled()) {
      return autoSelectDocumentAndDocumentLineMABD(deliveryDocuments, qtyToReceive, EMPTY_STRING);
    }

    DeliveryDocument selectedPOAgainstAllowedOverages = null;
    DeliveryDocument selectedDeliveryDocument = null;
    DeliveryDocument selectedPOAgainstAllowedOveragesMABD = null;
    DeliveryDocument selectedDeliveryDocumentMABD = null;
    DeliveryDocumentLine selectedPOLAgainstAllowedOverages = null;
    DeliveryDocumentLine selectedDeliveryDocumentLine = null;
    DeliveryDocumentLine selectedPOLAgainstAllowedOveragesMABD = null;
    DeliveryDocumentLine selectedDeliveryDocumentLineMABD = null;

    long totalOrderedQuantity = 0L;
    long totalAllowedQuantity = 0L;
    Long totalPOLineReceivedQty = 0L;
    long totalPOLineReceivedQtyAgainstAllowedOverages = 0L;
    long receivedQtyAgainstOrderedExhaustedPOLines = 0L;
    long receivedQtyAgainstAllowedExhaustedPOLines = 0L;

    // Removing PO-Lines which are exhausted and calculating total quantity for the leftover
    // PO-Lines
    Set<String> orderedExhaustedPOLines = new HashSet<>();
    Set<String> allowedExhaustedPOLines = new HashSet<>();
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())) {
        String key =
            deliveryDocument.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        totalPOLineReceivedQty =
            receiptsAggregator.getByDeliveryPoLineInZA(
                deliveryDocument.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack());
        totalPOLineReceivedQty = totalPOLineReceivedQty == null ? 0 : totalPOLineReceivedQty;

        Integer effectiveTotalOrderQty =
            ReceivingUtils.computeEffectiveTotalQty(
                deliveryDocumentLine, deliveryDocument.getImportInd(), configUtils);

        if (effectiveTotalOrderQty <= totalPOLineReceivedQty) {
          orderedExhaustedPOLines.add(key);
          receivedQtyAgainstOrderedExhaustedPOLines += totalPOLineReceivedQty;
        }
        totalOrderedQuantity += effectiveTotalOrderQty;

        if (effectiveTotalOrderQty + deliveryDocumentLine.getOverageQtyLimit()
            <= totalPOLineReceivedQty) {
          allowedExhaustedPOLines.add(key);
          receivedQtyAgainstAllowedExhaustedPOLines +=
              totalPOLineReceivedQty - deliveryDocumentLine.getTotalOrderQty();
        }

        totalAllowedQuantity += deliveryDocumentLine.getOverageQtyLimit();
      }
    }

    // Sorting documents on MABD
    deliveryDocuments =
        deliveryDocuments
            .stream()
            .sorted(
                Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                    .thenComparing(DeliveryDocument::getPurchaseReferenceNumber))
            .collect(Collectors.toList());

    // Find PO specific adjusted batch size
    long totalReceivedQuantity =
        deliveryDocuments
            .stream()
            .flatMap(
                deliveryDocument ->
                    ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())
                        .stream()
                        .map(
                            deliveryDocumentLine ->
                                new AbstractMap.SimpleEntry<>(
                                    deliveryDocument, deliveryDocumentLine)))
            .mapToLong(
                entry -> {
                  DeliveryDocument deliveryDocument = entry.getKey();
                  DeliveryDocumentLine deliveryDocumentLine = entry.getValue();

                  String purchaseReferenceNumber = deliveryDocument.getPurchaseReferenceNumber();
                  int purchaseReferenceLineNumber =
                      deliveryDocumentLine.getPurchaseReferenceLineNumber();

                  return receiptsAggregator.getByDeliveryPoLineInZA(
                      purchaseReferenceNumber,
                      purchaseReferenceLineNumber,
                      deliveryDocumentLine.getVendorPack(),
                      deliveryDocumentLine.getWarehousePack());
                })
            .sum();
    long totalReceivedQuantityAgainstOverage = totalReceivedQuantity - totalOrderedQuantity;
    long batchSize =
        configUtils.getDCSpecificPODistributionBatchSize(TenantContext.getFacilityNum());
    if (batchSize == 0) {
      batchSize = totalOrderedQuantity == 0 ? totalAllowedQuantity : totalOrderedQuantity;
    }
    Pair<Long, Long> batchSizePair =
        getAdjustedBatchSize(
            deliveryDocuments,
            batchSize,
            orderedExhaustedPOLines,
            allowedExhaustedPOLines,
            totalOrderedQuantity,
            totalAllowedQuantity);
    long orderedQtyBatchSize = batchSizePair.getKey();
    long allowedQtyBatchSize = batchSizePair.getValue();

    // Nothing left to receive: Overage scenario
    if (orderedQtyBatchSize == 0 && allowedQtyBatchSize == 0) {
      return null;
    }

    long currentPositionForOrdered =
        orderedQtyBatchSize > 0
            ? (totalReceivedQuantity - receivedQtyAgainstOrderedExhaustedPOLines)
                % orderedQtyBatchSize
            : Long.MAX_VALUE;
    long currentPositionForAllowed =
        allowedQtyBatchSize > 0
            ? (totalReceivedQuantityAgainstOverage - receivedQtyAgainstAllowedExhaustedPOLines)
                % allowedQtyBatchSize
            : Long.MAX_VALUE;

    // Finding the PO to select and if we can receive the quantity without going over the
    // ordered/allowed quantity
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      List<DeliveryDocumentLine> deliveryDocumentLines =
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines());
      deliveryDocumentLines =
          deliveryDocumentLines
              .stream()
              .sorted(Comparator.comparing(DeliveryDocumentLine::getPurchaseReferenceLineNumber))
              .collect(Collectors.toList());
      for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocumentLines) {
        String key =
            deliveryDocument.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        totalPOLineReceivedQty =
            receiptsAggregator.getByDeliveryPoLineInZA(
                deliveryDocument.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack());
        totalPOLineReceivedQty = totalPOLineReceivedQty == null ? 0 : totalPOLineReceivedQty;

        if (!orderedExhaustedPOLines.contains(key)) {
          Integer effectiveTotalOrderQty =
              ReceivingUtils.computeEffectiveTotalQty(
                  deliveryDocumentLine, deliveryDocument.getImportInd(), configUtils);
          long lastPositionForPOLineOrder =
              (long) Math.ceil((double) effectiveTotalOrderQty * batchSize / totalOrderedQuantity);
          if (currentPositionForOrdered + qtyToReceive <= lastPositionForPOLineOrder) {
            selectedDeliveryDocument = deliveryDocument;
            selectedDeliveryDocumentLine = deliveryDocumentLine;
            break;
          } else {
            currentPositionForOrdered -= lastPositionForPOLineOrder;
            currentPositionForOrdered =
                currentPositionForOrdered < 0 ? 0 : currentPositionForOrdered;
          }
        }

        if (!allowedExhaustedPOLines.contains(key)) {
          long lastPositionForPOLineAllowed =
              (long)
                  Math.ceil(
                      (double) deliveryDocumentLine.getOverageQtyLimit()
                          * batchSize
                          / totalAllowedQuantity);
          if (Objects.isNull(selectedPOAgainstAllowedOverages)) {
            if (currentPositionForAllowed + qtyToReceive <= lastPositionForPOLineAllowed) {
              selectedPOAgainstAllowedOverages = deliveryDocument;
              selectedPOLAgainstAllowedOverages = deliveryDocumentLine;
              totalPOLineReceivedQtyAgainstAllowedOverages = totalPOLineReceivedQty;
            } else {
              currentPositionForAllowed -= lastPositionForPOLineAllowed;
              currentPositionForAllowed =
                  currentPositionForAllowed < 0 ? 0 : currentPositionForAllowed;
            }
          }
        }

        // Fallback MABD logic for receiving quantity which batch wise distribution doesn't support
        if (Objects.isNull(selectedDeliveryDocumentMABD)) {
          Integer effectiveOrderQty =
              computeEffectiveTotalQty(
                  deliveryDocumentLine, deliveryDocument.getImportInd(), configUtils);
          if (totalPOLineReceivedQty + qtyToReceive <= effectiveOrderQty) {
            selectedDeliveryDocumentMABD = deliveryDocument;
            selectedDeliveryDocumentLineMABD = deliveryDocumentLine;
          } else if (Objects.isNull(selectedPOAgainstAllowedOveragesMABD)
              && (totalPOLineReceivedQty + qtyToReceive
                  <= effectiveOrderQty + deliveryDocumentLine.getOverageQtyLimit())) {
            selectedPOAgainstAllowedOveragesMABD = deliveryDocument;
            selectedPOLAgainstAllowedOveragesMABD = deliveryDocumentLine;
            totalPOLineReceivedQtyAgainstAllowedOverages = totalPOLineReceivedQty;
          }
        }
      }
      if (Objects.nonNull(selectedDeliveryDocument)) break;
    }

    if (Objects.nonNull(selectedDeliveryDocument)) {
      LOGGER.info(
          "Selected PO {} and POL {} against ordered qty",
          selectedDeliveryDocument.getPurchaseReferenceNumber(),
          selectedDeliveryDocumentLine.getPurchaseReferenceLineNumber());
      selectedDeliveryDocument.getDeliveryDocumentLines().clear();
      selectedDeliveryDocument.getDeliveryDocumentLines().add(selectedDeliveryDocumentLine);
      return new Pair<>(selectedDeliveryDocument, totalPOLineReceivedQty);
    } else if (Objects.nonNull(selectedPOAgainstAllowedOverages)) {
      LOGGER.info(
          "Selected PO {} and POL {} against allowed ovg qty",
          selectedPOAgainstAllowedOverages.getPurchaseReferenceNumber(),
          selectedPOLAgainstAllowedOverages.getPurchaseReferenceLineNumber());
      selectedPOAgainstAllowedOverages.getDeliveryDocumentLines().clear();
      selectedPOAgainstAllowedOverages
          .getDeliveryDocumentLines()
          .add(selectedPOLAgainstAllowedOverages);
      return new Pair<>(
          selectedPOAgainstAllowedOverages, totalPOLineReceivedQtyAgainstAllowedOverages);
    } else if (Objects.nonNull(selectedDeliveryDocumentMABD)) {
      LOGGER.info(
          "Selected PO {} and POL {} against ordered qty",
          selectedDeliveryDocumentMABD.getPurchaseReferenceNumber(),
          selectedDeliveryDocumentLineMABD.getPurchaseReferenceLineNumber());
      selectedDeliveryDocumentMABD.getDeliveryDocumentLines().clear();
      selectedDeliveryDocumentMABD.getDeliveryDocumentLines().add(selectedDeliveryDocumentLineMABD);
      return new Pair<>(selectedDeliveryDocumentMABD, totalPOLineReceivedQty);
    } else if (Objects.nonNull(selectedPOAgainstAllowedOveragesMABD)) {
      LOGGER.info(
          "Selected PO {} and POL {} against allowed ovg qty",
          selectedPOAgainstAllowedOveragesMABD.getPurchaseReferenceNumber(),
          selectedPOLAgainstAllowedOveragesMABD.getPurchaseReferenceLineNumber());
      selectedPOAgainstAllowedOveragesMABD.getDeliveryDocumentLines().clear();
      selectedPOAgainstAllowedOveragesMABD
          .getDeliveryDocumentLines()
          .add(selectedPOLAgainstAllowedOveragesMABD);
      return new Pair<>(
          selectedPOAgainstAllowedOveragesMABD, totalPOLineReceivedQtyAgainstAllowedOverages);
    }
    return null;
  }

  /**
   * Method which adjusts the batch size for integral values when deciding how much one PO would get
   *
   * <p>Eg. Order Quantity for PO1 = 10; PO2 = 10; PO3 = 10 Batch size = 20 Since 20 can't be
   * divided equally among 3 POs, the below method would adjust the batch size to 21, so that each
   * PO would get 7, 7, 7 in a batch
   *
   * @param deliveryDocuments
   * @param totalOrderedQuantity
   * @param totalAllowedQuantity
   * @return
   */
  private Pair<Long, Long> getAdjustedBatchSize(
      List<DeliveryDocument> deliveryDocuments,
      long batchSize,
      Set<String> orderedExhaustedPOLines,
      Set<String> allowedExhaustedPOLines,
      long totalOrderedQuantity,
      long totalAllowedQuantity) {
    long adjustedOrderedBatchSize = 0;
    long adjustedAllowedBatchSize = 0;
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())) {
        String key =
            deliveryDocument.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();

        if (!orderedExhaustedPOLines.contains(key)) {
          double effectiveQty =
              (double)
                  computeEffectiveTotalQty(
                      deliveryDocumentLine, deliveryDocument.getImportInd(), configUtils);
          adjustedOrderedBatchSize =
              totalOrderedQuantity == 0
                  ? Long.MAX_VALUE
                  : adjustedOrderedBatchSize
                      + (long) Math.ceil(effectiveQty * batchSize / totalOrderedQuantity);
        }

        if (!allowedExhaustedPOLines.contains(key)) {
          adjustedAllowedBatchSize =
              totalAllowedQuantity == 0
                  ? Long.MAX_VALUE
                  : adjustedAllowedBatchSize
                      + (long)
                          Math.ceil(
                              (double) deliveryDocumentLine.getOverageQtyLimit()
                                  * batchSize
                                  / totalAllowedQuantity);
        }
      }
    }
    return new Pair<>(adjustedOrderedBatchSize, adjustedAllowedBatchSize);
  }

  public boolean checkIfListContainsAnyPendingInstruction(List<InstructionDetails> instructions) {
    for (InstructionDetails instruction : instructions) {
      if (instruction.getReceivedQuantity() > 0) return true;
    }
    return false;
  }

  @Transactional
  @InjectTenantFilter
  public void transferInstructions(List<Long> instructionIds, String userId) {
    instructionPersisterService.updateLastChangeUserIdAndLastChangeTs(instructionIds, userId);
  }

  @Transactional(
      rollbackFor = {
        ReceivingException.class,
        ReceivingBadDataException.class,
        ReceivingInternalException.class
      })
  @InjectTenantFilter
  public Map<String, Object> receiveInstructionAndCompleteProblemTag(
      Instruction instruction,
      UpdateInstructionRequest updateInstructionRequest,
      Integer qtyToBeReceived,
      HttpHeaders httpHeaders,
      boolean isReceiveAsCorrection,
      Map<String, Object> caseLabelsInfo)
      throws ReceivingException {
    LOGGER.info(
        "Enter receiveInstructionAndCompleteProblemTag with instructionId: {}",
        instruction.getId());
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    // Create CONTAINER_ITEM/CONTAINER/RECEIPT
    instructionPersisterService.createContainersAndPrintJobs(
        updateInstructionRequest,
        httpHeaders,
        userId,
        instruction,
        qtyToBeReceived,
        instruction.getReceivedQuantity(),
        instruction.getChildContainers());

    // Create PRINTJOB and mark CONTAINER/INSTRUCTION as completed
    instruction.setLastChangeUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());

    if (configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_UPDATE_CONTAINER_LABEL_ENABLED)) {
      instruction =
          updateContainerLabel(
              updateInstructionRequest.getDeliveryDocumentLines().get(0).getRotateDate(),
              instruction,
              caseLabelsInfo);
    }

    Map<String, Object> containerAndInstructionMap =
        instructionPersisterService.completeAndCreatePrintJob(httpHeaders, instruction);

    // Prepare the payload with OSDR details for any pallet received after PO close
    if (isReceiveAsCorrection) {
      LOGGER.info(
          "Prepare the finalizePORequestBody for PO: {}", instruction.getPurchaseReferenceNumber());
      FinalizePORequestBody finalizePORequestBody =
          finalizePORequestBodyBuilder.buildFrom(
              instruction.getDeliveryNumber(),
              instruction.getPurchaseReferenceNumber(),
              getForwardablHeaderWithTenantData(httpHeaders));

      containerAndInstructionMap.put(ReceivingConstants.OSDR_PAYLOAD, finalizePORequestBody);
    }

    // Notify received QTY to FIXIT
    if (StringUtils.isNotBlank(instruction.getProblemTagId())) {
      LOGGER.info("Invoke completeProblem() for problemTagId: {}", instruction.getProblemTagId());
      configUtils
          .getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.PROBLEM_SERVICE,
              ProblemService.class)
          .completeProblem(instruction);
    }

    return containerAndInstructionMap;
  }

  @Transactional(
      rollbackFor = {
        ReceivingException.class,
        ReceivingBadDataException.class,
        ReceivingInternalException.class
      })
  @InjectTenantFilter
  public Map<String, Object> receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
      Instruction instruction,
      UpdateInstructionRequest updateInstructionRequest,
      Integer qtyToBeReceived,
      HttpHeaders httpHeaders,
      boolean isReceiveAsCorrection,
      boolean isAutomatedDc,
      boolean isOneAtlasConverted)
      throws ReceivingException {
    Map<String, Object> responseMap =
        receiveInstructionAndCompleteProblemTag(
            instruction,
            updateInstructionRequest,
            qtyToBeReceived,
            httpHeaders,
            isReceiveAsCorrection,
            null);
    Container consolidatedContainer = (Container) responseMap.get(CONTAINER);
    final ContainerItem containerItem = consolidatedContainer.getContainerItems().get(0);
    final String poNbr = instruction.getPurchaseReferenceNumber();
    final Integer poLineNbr = instruction.getPurchaseReferenceLineNumber();

    if (!isReceiveAsCorrection
        && (isAutomatedDc || isOneAtlasConverted)
        && ReceivingUtils.isTransferMerchandiseFromOssToMain(
            containerItem.getContainerItemMiscInfo())) {
      httpHeaders.set(IDEM_POTENCY_KEY, consolidatedContainer.getTrackingId());
      httpHeaders.add(IGNORE_INVENTORY, TRUE_STRING);
      inventoryRestApiClient.postInventoryOssReceiving(
          getInventoryOssReceivingRequest(consolidatedContainer, poNbr, poLineNbr), httpHeaders);
    }
    return responseMap;
  }

  @Transactional(
      rollbackFor = {
        Exception.class,
        ReceivingException.class,
        ReceivingBadDataException.class,
        ReceivingInternalException.class
      })
  @InjectTenantFilter
  public Map<String, Object> completeInstructionAndCreateContainerAndReceiptAndReject(
      PoLine poLineReq,
      Instruction instruction,
      UpdateInstructionRequest updateInstructionRequest,
      HttpHeaders httpHeaders,
      List<String> trackingIds)
      throws ReceivingException {
    String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);
    final Long deliveryNumber = instruction.getDeliveryNumber();
    final String poNum = instruction.getPurchaseReferenceNumber();
    final Integer lineNum = instruction.getPurchaseReferenceLineNumber();
    LOGGER.info(
        "Creating 1.Containers, 2.Receipts for delivery={}, poNum={}, lineNum={} user={}",
        deliveryNumber,
        poNum,
        lineNum,
        userId);
    instructionPersisterService.updateInstructionAndCreateContainerAndReceipt(
        poLineReq,
        updateInstructionRequest,
        httpHeaders,
        instruction,
        instruction.getReceivedQuantity(),
        trackingIds);
    LOGGER.info(
        "created 1.Containers 2.Receipts,Reject for delivery={}, poNum={}, lineNum={} user={}",
        deliveryNumber,
        poNum,
        lineNum,
        userId);
    instruction.setLastChangeUserId(userId);
    final Date ts = new Date();
    instruction.setLastChangeTs(ts);
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(ts);
    final Map<String, Object> completedMap =
        instructionPersisterService.completeInstructionAndContainer(httpHeaders, instruction);
    LOGGER.info(
        "completed Container,Instruction for delivery={}, poNum={}, lineNum={} user={}",
        deliveryNumber,
        poNum,
        lineNum,
        userId);
    return completedMap;
  }

  public Instruction updateContainerLabel(
      Date rotateDateDt, Instruction instruction, Map<String, Object> caseLabelsInfo)
      throws ReceivingException {

    String rotateDate =
        (nonNull(rotateDateDt)) ? new SimpleDateFormat("MM/dd/yy").format(rotateDateDt) : "-";
    Map<String, Object> printJob =
        ReceivingUtils.getPrintJobWithAdditionalAttributes(
            instruction, rotateDate, labelServiceImpl, configUtils);

    if (!CollectionUtils.isEmpty(caseLabelsInfo)) {
      List<Object> casePrintLabels =
          (List<Object>) caseLabelsInfo.get(ReceivingConstants.PRINT_REQUEST_KEY);
      List<Object> palletPrintLabel =
          (List<Object>) printJob.get(ReceivingConstants.PRINT_REQUEST_KEY);
      casePrintLabels.addAll(palletPrintLabel);
      printJob.put(ReceivingConstants.PRINT_REQUEST_KEY, casePrintLabels);
    }
    instruction.getContainer().setCtrLabel(printJob);
    return instruction;
  }

  @Transactional(
      rollbackFor = {
        ReceivingException.class,
        ReceivingBadDataException.class,
        ReceivingInternalException.class
      })
  @InjectTenantFilter
  public Map<String, Object> receiveInstructionAndCompleteProblemTagPOConCC(
      Instruction instruction,
      UpdateInstructionRequest updateInstructionRequest,
      Integer qtyToBeReceived,
      HttpHeaders httpHeaders,
      List<ContainerDetails> childContainers,
      Map<String, Object> caseLabelsInfo)
      throws ReceivingException {
    LOGGER.info(
        "Enter receiveInstructionAndCompleteProblemTag with instructionId: {}",
        instruction.getId());
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    Integer receivedQuantity = instruction.getReceivedQuantity();
    containerService.processCreateContainers(instruction, updateInstructionRequest, httpHeaders);

    if (!CollectionUtils.isEmpty(childContainers)) {
      List<String> childCtrTrackingIdInfo =
          containerService.getCreatedChildContainerTrackingIds(
              childContainers, instruction.getReceivedQuantity(), qtyToBeReceived);
      Labels labels = instruction.getLabels();
      childCtrTrackingIdInfo.forEach(
          trackingId -> {
            labels.getAvailableLabels().remove(trackingId);
            labels.getUsedLabels().add(trackingId);
          });
      instruction.setLabels(labels);
      // check why getPrintChildContainerLabels is not validated for true
      if (instruction.getPrintChildContainerLabels() != null) {
        // Persisting print job
        Set<String> printJobLpnSet = new HashSet<>();
        printJobLpnSet.addAll(childCtrTrackingIdInfo);
        printJobService.createPrintJob(
            instruction.getDeliveryNumber(),
            instruction.getId(),
            printJobLpnSet,
            httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).get(0));
      }
    }

    Receipt receiptForDB =
        receiptService.createAndCompleteReceiptsFromInstructionForPOCON(
            updateInstructionRequest, userId);
    receivedQuantity += qtyToBeReceived;

    instruction.setReceivedQuantity(receivedQuantity);

    // Create PRINTJOB and mark CONTAINER/INSTRUCTION as completed
    instruction.setLastChangeUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());
    Date rotateDate = updateInstructionRequest.getDeliveryDocumentLines().get(0).getRotateDate();
    instruction = updateContainerLabel(rotateDate, instruction, caseLabelsInfo);
    Map<String, Object> containerAndInstructionMap =
        instructionPersisterService.completeAndCreatePrintJobForPoCon(
            httpHeaders, instruction, receiptForDB);

    containerAndInstructionMap.put("instruction", instruction);
    // Notify received QTY to FIXIT
    if (StringUtils.isNotBlank(instruction.getProblemTagId())) {
      LOGGER.info("Invoke completeProblem() for problemTagId: {}", instruction.getProblemTagId());
      configUtils
          .getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.PROBLEM_SERVICE,
              ProblemService.class)
          .completeProblem(instruction);
    }

    return containerAndInstructionMap;
  }

  public void reopenDeliveryIfNeeded(
      Long deliveryNumber,
      String deliveryStatus,
      HttpHeaders httpHeaders,
      String deliveryLegacyStatus)
      throws ReceivingException {
    if (ReceivingUtils.needToCallReopen(deliveryStatus, deliveryLegacyStatus)) {

      // call Reopen delivery
      LOGGER.info(
          "InstructionHelperService : Delivery status for delivery {} is {}:{}. Calling delivery reopen.",
          deliveryNumber,
          deliveryStatus,
          deliveryLegacyStatus);
      deliveryService.reOpenDelivery(deliveryNumber, httpHeaders);

      // persist in table only if REOPEN is successful, so that scheduler can trigger an auto
      // complete.
      configUtils
          .getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DELIVERY_METADATA_SERVICE,
              DeliveryMetaDataService.class)
          .findAndUpdateDeliveryStatus(deliveryNumber.toString(), DeliveryStatus.SYS_REO);
    }
  }

  public void createPrintRequestsForChildLabels(
      Instruction instruction, List<Map<String, Object>> printRequestList) {
    Map<String, Object> childPrintLabelData =
        instructionPersisterService.getPrintlabeldata(
            instruction, instruction.getReceivedQuantity(), 0, instruction.getChildContainers());
    if (!CollectionUtils.isEmpty(childPrintLabelData)) {
      printRequestList.addAll(
          (List<Map<String, Object>>)
              childPrintLabelData.get(ReceivingConstants.PRINT_REQUEST_KEY));
    }
  }

  public void createPrintRequestsForParentLabels(
      Instruction instruction, List<Map<String, Object>> printRequestList) {
    if (instruction.getCompleteTs() != null) {
      Map<String, Object> parentPrintLabelData = null;
      try {
        parentPrintLabelData = getPrintJobWithAdditionalAttributes(instruction, null);
      } catch (ReceivingException e) {
        LOGGER.error(
            "postLabels: Error while preparing pallet label for instruction id {}",
            instruction.getId());
      }
      if (!CollectionUtils.isEmpty(parentPrintLabelData)
          && parentPrintLabelData.containsKey(ReceivingConstants.PRINT_REQUEST_KEY)) {
        printRequestList.addAll(
            (List<Map<String, Object>>)
                parentPrintLabelData.get(ReceivingConstants.PRINT_REQUEST_KEY));
      }
    }
  }

  public InventoryOssReceivingRequest getInventoryOssReceivingRequest(
      Container consolidatedContainer, String poNbr, Integer poLineNbr) {
    ContainerItem containerItem = consolidatedContainer.getContainerItems().get(0);
    Map<String, String> containerItemMiscInfo = containerItem.getContainerItemMiscInfo();
    InventoryOssReceivingRequest inventoryOssReceivingRequest = new InventoryOssReceivingRequest();
    Transfer transfer = new Transfer();
    Source source = new Source();
    SourceItem sourceItem = new SourceItem();
    ArrayList<ItemIdentifier> itemIdentifierList = new ArrayList<>();
    ItemIdentifier itemIdentifierItemNumber = new ItemIdentifier();
    ItemIdentifier itemIdentifierBaseCode = new ItemIdentifier();
    ItemIdentifier itemIdentifierFinanceGroup = new ItemIdentifier();
    TargetContainer targetContainer = new TargetContainer();
    com.walmart.move.nim.receiving.core.model.inventory.Item item = new Item();
    List<ContainerTag> tags = new ArrayList<>();
    ContainerTag toBeAuditTag = new ContainerTag(CONTAINER_TO_BE_AUDITED, CONTAINER_SET);
    tags.add(toBeAuditTag);
    Map<String, String> destination = consolidatedContainer.getDestination();
    if (MapUtils.isNotEmpty(destination) && PRIME.equalsIgnoreCase(destination.get(SLOT_TYPE))) {
      // Set PRIME Putaway if slotType is prime
      ContainerTag primeTag = new ContainerTag(PUTAWAY_TO_PRIME, CONTAINER_SET);
      tags.add(primeTag);
    }
    ContainerTagOss containerTagOss = new ContainerTagOss();
    containerTagOss.setTags(tags);
    targetContainer.setContainerTag(containerTagOss);
    ContainerIdentifier containerIdentifier = new ContainerIdentifier();
    containerIdentifier.setIdentifierType(ORG_UNIT_ID);
    containerIdentifier.setIdentifierValue(containerItemMiscInfo.get(FROM_SUBCENTER));
    ArrayList<ContainerIdentifier> containerIdentifierList = new ArrayList<>();
    containerIdentifierList.add(containerIdentifier);
    source.setCtnrIdentifiers(containerIdentifierList);

    itemIdentifierItemNumber.setIdentifierType(INVENTORY_V2_ITEM_NUMBER);
    itemIdentifierItemNumber.setIdentifierValue(containerItem.getItemNumber().toString());
    itemIdentifierList.add(itemIdentifierItemNumber);
    itemIdentifierBaseCode.setIdentifierType(BASE_DIV_CODE);
    itemIdentifierBaseCode.setIdentifierValue(containerItem.getBaseDivisionCode());
    itemIdentifierList.add(itemIdentifierBaseCode);
    itemIdentifierFinanceGroup.setIdentifierType(FINANCIAL_REPORTING_GROUP);
    itemIdentifierFinanceGroup.setIdentifierValue(containerItem.getFinancialReportingGroupCode());
    itemIdentifierList.add(itemIdentifierFinanceGroup);

    sourceItem.setQuantity(containerItem.getQuantity());
    sourceItem.setItemIdentifiers(itemIdentifierList);
    source.setItems(Arrays.asList(sourceItem));
    transfer.setSource(source);
    targetContainer.setTrackingId(consolidatedContainer.getTrackingId());
    targetContainer.setLocationName(consolidatedContainer.getLocation());
    targetContainer.setContainerStatus(consolidatedContainer.getInventoryStatus());
    targetContainer.setCreateDate(consolidatedContainer.getCreateTs());
    targetContainer.setCreateUserid(consolidatedContainer.getCreateUser());
    targetContainer.setDestLocationId(consolidatedContainer.getFacilityNum());
    targetContainer.setContainerType(PALLET_CAMEL_CASE);
    targetContainer.setOrgUnitId(consolidatedContainer.getSubcenterId());
    targetContainer.setShippable(consolidatedContainer.getCtrShippable());
    targetContainer.setReusable(consolidatedContainer.getCtrReusable());
    targetContainer.setDeliveryNumber(consolidatedContainer.getDeliveryNumber().toString());
    targetContainer.setChannelType(containerItem.getInboundChannelMethod());
    targetContainer.setSourceSys(ATLAS);
    targetContainer.setConveyable(consolidatedContainer.getIsConveyable());
    targetContainer.setOriginSystem(RECEIVING.toUpperCase());
    targetContainer.setOriginDcNumber(consolidatedContainer.getFacilityNum());
    targetContainer.setOriginCountryCode(consolidatedContainer.getFacilityCountryCode());

    item.setItemNumber(containerItem.getItemNumber().toString());
    item.setUpcNumber(containerItem.getGtin());
    item.setBaseDivisionCode(containerItem.getBaseDivisionCode());
    item.setFinancialReportingGroup(containerItem.getFinancialReportingGroupCode());
    item.setPurchaseCompanyId(containerItem.getPurchaseCompanyId());
    item.setVendorPkRatio(containerItem.getVnpkQty());
    item.setWarehousePkRatio(containerItem.getWhpkQty());

    item.setChannelType(containerItem.getInboundChannelMethod());
    item.setInboundChannelType(containerItem.getInboundChannelMethod());

    item.setAvailableToSellQty(containerItem.getQuantity());

    item.setQtyUOM(containerItem.getQuantityUOM());
    item.setWarehousePackSell(containerItem.getWhpkSell());
    item.setDescription(containerItem.getDescription());
    item.setSecondaryDescription(containerItem.getSecondaryDescription());
    item.setVnpkWeightQty(containerItem.getVnpkWgtQty());
    item.setVnpkWeightUOM(containerItem.getVnpkWgtUom());
    item.setVnpkCubeQty(
        nonNull(containerItem.getVnpkcbqty())
            ? containerItem.getVnpkcbqty().toString()
            : EMPTY_STRING);
    item.setVnpkCubeUOM(containerItem.getVnpkcbuomcd());
    item.setPromoBuyInd(containerItem.getPromoBuyInd());
    item.setWeightFormatType(containerItem.getWeightFormatTypeCode());
    item.setWarehouseAreaCode(containerItem.getWarehouseAreaCode());
    item.setRotateDate(containerItem.getRotateDate());
    item.setPoTypeCode(containerItem.getPoTypeCode());
    item.setProfiledWarehouseArea(containerItem.getProfiledWarehouseArea());
    item.setWarehouseRotationTypeCode(containerItem.getWarehouseRotationTypeCode());
    item.setDeptNumber(containerItem.getDeptNumber());
    item.setReceivedDate(consolidatedContainer.getCreateTs());
    Set<com.walmart.move.nim.receiving.core.model.inventory.Receipt> receiptSet = new HashSet<>();
    Set<Reference> reference = new HashSet<>();
    com.walmart.move.nim.receiving.core.model.inventory.Receipt receipt =
        new com.walmart.move.nim.receiving.core.model.inventory.Receipt();
    receipt.setDeliveryNumber(consolidatedContainer.getDeliveryNumber());
    receiptSet.add(receipt);
    reference.add(receipt);
    Set<com.walmart.move.nim.receiving.core.model.inventory.PurchaseOrder> purchaseOrderSet =
        new HashSet<>();
    PurchaseOrder purchaseOrder = new PurchaseOrder();
    purchaseOrder.setPoNumber(poNbr);
    purchaseOrder.setPoLineNumber(nonNull(poLineNbr) ? poLineNbr.toString() : EMPTY_STRING);
    purchaseOrderSet.add(purchaseOrder);
    reference.add(purchaseOrder);
    item.setReferences(reference);
    item.setVendorNumber(containerItem.getVendorNumber());
    item.setTotalQty(containerItem.getQuantity());
    targetContainer.setItems(Arrays.asList(item));
    transfer.setTargetContainer(targetContainer);
    inventoryOssReceivingRequest.setTransfer(transfer);

    return inventoryOssReceivingRequest;
  }
  // for wfs flow, see: WFSInstructionHelperService.java

  public boolean validateFloorLineDocktag(Instruction instruction) {
    String trackingId = instruction.getContainer().getTrackingId();
    DockTag dockTag = dockTagPersisterService.getDockTagByDockTagId(trackingId);
    return Objects.nonNull(dockTag) && DockTagType.FLOOR_LINE.equals(dockTag.getDockTagType());
  }

  public void createAndPublishDockTagInfo(
      Instruction instruction, CompleteInstructionRequest instructionRequest) {
    try {
      LOGGER.info("Creating dockTagInfo payload for dock tag: {}", instruction.getDockTagId());
      DockTagInfo dockTagInfo = createDockTagInfoPayload(instruction, instructionRequest);
      publishDockTagInfoPayload(dockTagInfo);
    } catch (ReceivingInternalException e) {
      LOGGER.warn(
          "Error while creating dockTagInfo payload for dock tag: {}, Error: {}",
          instruction.getDockTagId(),
          e.getStackTrace());
    }
  }

  public DockTagInfo createDockTagInfoPayload(
      Instruction instruction, CompleteInstructionRequest instructionRequest) {
    Long deliveryNumber = instruction.getDeliveryNumber();
    String trackingId = instruction.getContainer().getTrackingId();
    DockTagInfo dockTagInfo = new DockTagInfo();
    dockTagInfo.setTrackingId(trackingId);
    dockTagInfo.setDeliveryNumber(deliveryNumber);
    dockTagInfo.setContainerType(instruction.getContainer().getCtrType());
    if (Objects.nonNull(instructionRequest)) {
      dockTagInfo.setSkuIndicator(instructionRequest.getSkuIndicator());
      dockTagInfo.setLocation(instructionRequest.getDoorNumber());
    }
    if (isEmpty(dockTagInfo.getSkuIndicator())) {
      dockTagInfo.setSkuIndicator(ReceivingConstants.DEFAULT_SKU_INDICATOR);
    }
    if (isEmpty(dockTagInfo.getLocation())) {
      Container container = containerPersisterService.getContainerDetails(trackingId);
      if (Objects.isNull(container)) {
        String errorDescription =
            String.format(ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_ERROR_MSG, trackingId);
        LOGGER.warn(errorDescription);
        throw new ReceivingDataNotFoundException(
            ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription);
      }
      dockTagInfo.setLocation(container.getLocation());
    }
    try {
      HttpHeaders headers = ReceivingUtils.getHeaderForGDMV3API();
      Delivery delivery = gdmRestApiClient.getDeliveryWithDeliveryResponse(deliveryNumber, headers);
      if (Objects.nonNull(delivery) && Objects.nonNull(delivery.getPriority())) {
        dockTagInfo.setPriority(delivery.getPriority());
      } else {
        dockTagInfo.setPriority(ReceivingConstants.DEFAULT_PRIORITY);
      }
    } catch (GDMRestApiClientException e) {
      LOGGER.error(
          "Failed to fetch delivery details for delivery: {} while creating DockTagInfo payload",
          deliveryNumber);
      throw new ReceivingInternalException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          "Failed to fetch delivery details for delivery: " + deliveryNumber);
    }
    LOGGER.info("DockTag info payload: {} created for DockTag Id: {}", dockTagInfo, trackingId);
    return dockTagInfo;
  }

  public Integer getDeliveryPriority(Long deliveryNumber) {
    Integer priority = ReceivingConstants.DEFAULT_DELIVERY_PRIORITY;
    try {
      HttpHeaders headers = ReceivingUtils.getHeaderForGDMV3API();
      Delivery delivery = gdmRestApiClient.getDeliveryWithDeliveryResponse(deliveryNumber, headers);
      if (Objects.nonNull(delivery) && Objects.nonNull(delivery.getPriority())) {
        priority = delivery.getPriority();
      }
    } catch (GDMRestApiClientException e) {
      LOGGER.warn(
          "Failed to fetch delivery details for delivery: {} while creating DockTagInfo payload. Error: {}",
          deliveryNumber,
          e.getStackTrace());
    }
    return priority;
  }

  public void publishDockTagInfoPayload(DockTagInfo message) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum().toString());
    headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    headers.put(ReceivingConstants.EVENT_TYPE, ReceivingConstants.DOCK_TAG_CREATED_EVENT);

    MessagePublisher<MessageData> dockTagPublisher =
        configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DOCKTAG_INFO_PUBLISHER,
            MessagePublisher.class);
    dockTagPublisher.publish(message, headers);
  }
}
