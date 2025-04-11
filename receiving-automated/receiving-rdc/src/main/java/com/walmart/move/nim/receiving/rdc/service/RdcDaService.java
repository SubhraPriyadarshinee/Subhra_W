package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.EventType.OFFLINE_RECEIVING;
import static com.walmart.move.nim.receiving.core.common.InstructionUtils.checkIfProblemTagPresent;
import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.*;
import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.X_BLOCK_ITEM_HANDLING_CODES;
import static com.walmart.move.nim.receiving.rdc.constants.ReceivingMethod.SLOTTING;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeGetLpnsRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.core.model.move.MoveType;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.constants.ReceivingMethod;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.repositories.OutboxEvent;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
public class RdcDaService {
  private static final Logger logger = LoggerFactory.getLogger(RdcDaService.class);

  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private PrintJobService printJobService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private Gson gson;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private ContainerService containerService;
  @Autowired private LabelDataService labelDataService;
  @Autowired private ReceiptService receiptService;
  @Autowired private RdcLpnUtils rdcLpnUtils;
  @Autowired private RdcLabelGenerationService rdcLabelGenerationService;
  @Autowired private LabelDownloadEventService labelDownloadEventService;

  @Resource(name = ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
  private LPNCacheService lpnCacheService;

  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired private RdcSlottingUtils rdcSlottingUtils;
  @Autowired private RdcAsyncUtils rdcAsyncUtils;
  @Autowired private RdcSSTKLabelGenerationUtils rdcSSTKLabelGenerationUtils;

  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.INTERNAL,
      executionFlow = "createInstructionForDACaseReceiving")
  @Transactional(rollbackFor = ReceivingBadDataException.class)
  @InjectTenantFilter
  public InstructionResponse createInstructionForDACaseReceiving(
      InstructionRequest instructionRequest, Long totalReceivedQty, HttpHeaders httpHeaders)
      throws ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    int qtyToBeReceived = RdcConstants.RDC_DA_CASE_RECEIVE_QTY;
    boolean isDaQtyReceiving =
        !RdcUtils.isWorkStationAndScanToPrintReceivingModeEnabled(
            instructionRequest.getFeatureType());
    logger.info("Create Instruction for DA item, UPC: {}", instructionRequest.getUpcNumber());

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    // Check if any problem tag exists => throw exception
    if (rdcInstructionUtils.isProblemTagValidationApplicable(
        instructionRequest.getDeliveryDocuments())) {
      checkIfProblemTagPresent(
          instructionRequest.getUpcNumber(),
          instructionRequest.getDeliveryDocuments().get(0),
          appConfig.getProblemTagTypesList());
    }
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    rdcReceivingUtils.overrideItemProperties(deliveryDocument);

    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    rdcReceivingUtils.isPoAndPoLineInReceivableStatus(deliveryDocumentLine);

    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .setTotalReceivedQty(totalReceivedQty.intValue());

    Boolean maxOverageReceived =
        rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocument, totalReceivedQty, qtyToBeReceived);
    // if overage exists return overage alert instruction
    if (maxOverageReceived) {
      Instruction instruction =
          rdcInstructionUtils.getOverageAlertInstruction(instructionRequest, httpHeaders);
      instructionResponse.setInstruction(instruction);
      populateReceiveQtyDetailsInDeliveryDocument(
          deliveryDocument, totalReceivedQty.intValue(), isDaQtyReceiving);
      instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
      return instructionResponse;
    }
    // Block users if trying to receive unsupported pack and handlingCodes
    validateDaAtlasNonSupportedHandlingCode(
        deliveryDocument.getDeliveryNumber(), deliveryDocumentLine);

    if (!isDaQtyReceiving) {
      populateReceiveQtyDetailsInDeliveryDocument(
          deliveryDocument, totalReceivedQty.intValue(), false);
      instructionResponse =
          rdcReceivingUtils.validateRtsPutItems(
              deliveryDocument, instructionRequest, instructionResponse, httpHeaders);
      if (!CollectionUtils.isEmpty(instructionResponse.getDeliveryDocuments())) {
        return instructionResponse;
      }
    }

    instructionResponse =
        rdcReceivingUtils.validateBreakPackItems(
            deliveryDocument, instructionRequest, instructionResponse);
    if (!CollectionUtils.isEmpty(instructionResponse.getDeliveryDocuments())) {
      return instructionResponse;
    }

    instructionResponse =
        rdcReceivingUtils.checkIfVendorComplianceRequired(
            instructionRequest, deliveryDocument, instructionResponse);
    if (!CollectionUtils.isEmpty(instructionResponse.getDeliveryDocuments())) {
      return instructionResponse;
    }

    // Create instruction for DA Qty Receiving
    if (isDaQtyReceiving) {
      Instruction daQtyReceivingInstruction =
          createInstruction(
              instructionRequest,
              deliveryDocument,
              httpHeaders,
              deliveryDocumentLine,
              Boolean.FALSE);
      if (StringUtils.isNotBlank(instructionRequest.getProblemTagId())) {
        rdcReceivingUtils.populateProblemReceivedQtyDetails(
            deliveryDocument, daQtyReceivingInstruction);
      } else {
        populateReceiveQtyDetailsInDeliveryDocument(
            deliveryDocument, totalReceivedQty.intValue(), isDaQtyReceiving);
      }
      daQtyReceivingInstruction.setDeliveryDocument(gson.toJson(deliveryDocument));
      instructionResponse.setInstruction(daQtyReceivingInstruction);
      instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
      return instructionResponse;
    }

    instructionResponse =
        rdcReceivingUtils.checkIfNonConveyableItem(
            deliveryDocument, instructionRequest, instructionResponse);
    if (!CollectionUtils.isEmpty(instructionResponse.getDeliveryDocuments())) {
      return instructionResponse;
    }

    instructionResponse =
        receiveContainers(deliveryDocument, httpHeaders, instructionRequest, qtyToBeReceived, null);
    TenantContext.get().setDaCaseReceivingEnd(System.currentTimeMillis());
    calculateOverallDaReceivingLatencySummary();
    if (instructionRequest.isSyncPrintEnabled() && !isDaQtyReceiving) {
      enrichSyncPrintingResponse(
          instructionResponse, instructionRequest.isSyncPrintEnabled(), deliveryDocument);
    }
    return instructionResponse;
  }

  private void calculateOverallDaReceivingLatencySummary() {
    logger.info(
        "LatencyCheck: Total time taken for receiving a DA case in WorkStation daCaseReceivingStart ts={} time & daCaseReceivingEnd={} milliSeconds, correlationId={},"
            + "Intermediate systems latency, fetchItemConfigServiceStart ts={}, fetchItemConfigServiceEnd ts={} milliseconds; "
            + "fetchGdmGetDeliveryDocumentStart ts={}, fetchGdmGetDeliveryDocumentEnd ts={} milliseconds; "
            + "fetchLpnsFromHawkeyeStart ts={}, fetchLpnsFromHawkeyeEnd ts={} milliseconds; fetchLabelDataByPoAndItemDBCallStart ts={} , "
            + "fetchLabelDataByPoAndItemDBCallEnd ts={} milliseconds; fetchLabelDataByPoAndItemAndDesStoreNbrDBCallStart ts={} , "
            + "fetchLabelDataByPoAndItemAndDesStoreNbrDBCallEnd ts={} milliseconds; fetchLabelDataByLpnsDBCallStart ts={} , "
            + "fetchLabelDataByLpnsDBCallEnd ts={} milliseconds; persistContainersDBUpdatesStart ts={}, persistContainersDBUpdatesEnd ts={} milliseconds; "
            + "postReceivingUpdatesByKafkaStart ts={}, postReceivingUpdatesByKafkaEnd ts={} milliseconds ; postReceivingUpdatesByOutBoxStart ts={}, "
            + "postReceivingUpdatesByOutBoxEnd ts={} milliseconds; fetchSlotFromSmartSlottingStart ts={}, fetchSlotFromSmartSlottingEnd ts={} milliseconds ,"
            + "receiveContainersInRdsStart ts={}, receiveContainersInRdsEnd ts={} milliseconds ",
        TenantContext.get().getDaCaseReceivingStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getDaCaseReceivingStart(),
            TenantContext.get().getDaCaseReceivingEnd()),
        TenantContext.getCorrelationId(),
        TenantContext.get().getFetchItemConfigServiceCallStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchItemConfigServiceCallStart(),
            TenantContext.get().getFetchItemConfigServiceCallEnd()),
        TenantContext.get().getAtlasRcvGdmGetDocLineStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvGdmGetDocLineStart(),
            TenantContext.get().getAtlasRcvGdmGetDocLineEnd()),
        TenantContext.get().getFetchLpnsFromHawkeyeStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchLpnsFromHawkeyeStart(),
            TenantContext.get().getFetchLpnsFromHawkeyeEnd()),
        TenantContext.get().getFetchLabelDataByPoAndItemNumberDBCallStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchLabelDataByPoAndItemNumberDBCallStart(),
            TenantContext.get().getFetchLabelDataByPoAndItemNumberDBCallEnd()),
        TenantContext.get().getFetchLabelDataByPoAndItemNumberAndDestStoreNumberDBCallStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchLabelDataByPoAndItemNumberAndDestStoreNumberDBCallStart(),
            TenantContext.get().getFetchLabelDataByPoAndItemNumberAndDestStoreNumberDBCallEnd()),
        TenantContext.get().getFetchLabelDataListByGettingLpnsFromHawkeyeDBCallStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchLabelDataListByGettingLpnsFromHawkeyeDBCallStart(),
            TenantContext.get().getFetchLabelDataListByGettingLpnsFromHawkeyeDBCallEnd()),
        TenantContext.get().getDaCaseReceivingDataPersistStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getDaCaseReceivingDataPersistStart(),
            TenantContext.get().getDaCaseReceivingDataPersistEnd()),
        TenantContext.get().getPostReceivingUpdatesInKafkaStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getPostReceivingUpdatesInKafkaStart(),
            TenantContext.get().getPostReceivingUpdatesInKafkaEnd()),
        TenantContext.get().getPersistOutboxEventsForDAStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getPersistOutboxEventsForDAStart(),
            TenantContext.get().getPersistOutboxEventsForDAEnd()),
        TenantContext.get().getFetchSlotFromSmartSlottingStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchSlotFromSmartSlottingStart(),
            TenantContext.get().getFetchSlotFromSmartSlottingEnd()),
        TenantContext.get().getReceiveContainersInRdsStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getReceiveContainersInRdsStart(),
            TenantContext.get().getReceiveContainersInRdsEnd()));
  }

  private void enrichSyncPrintingResponse(
      InstructionResponse instructionResponse,
      boolean syncPrintEnabled,
      DeliveryDocument deliveryDocument) {
    boolean isSyncPrintEnabled =
        syncPrintEnabled
            && !isprintingAsyncBlockedHandlingCodes(deliveryDocument, syncPrintEnabled);
    if (instructionResponse instanceof InstructionResponseImplNew) {
      ((InstructionResponseImplNew) instructionResponse)
          .getPrintJob()
          .put("syncPrintRequired", isSyncPrintEnabled);
    }
  }

  private Boolean isprintingAsyncBlockedHandlingCodes(
      DeliveryDocument deliveryDocument, boolean syncPrintEnabled) {
    String itemHandlingCode =
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getItemPackAndHandlingCode();
    boolean isDaPrintingBlockingHandlingCodes =
        !CollectionUtils.isEmpty(rdcManagedConfig.getAtlasDaPrintingAsyncBlockedHandlingCodes())
            && rdcManagedConfig
                .getAtlasDaPrintingAsyncBlockedHandlingCodes()
                .contains(itemHandlingCode);
    return syncPrintEnabled && isDaPrintingBlockingHandlingCodes;
  }

  /**
   * @param deliveryDocument
   * @param totalReceivedQty
   * @param isDaQtyReceiving
   * @return
   */
  private void populateReceiveQtyDetailsInDeliveryDocument(
      DeliveryDocument deliveryDocument, Integer totalReceivedQty, boolean isDaQtyReceiving) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Integer openQty = deliveryDocumentLine.getTotalOrderQty() - totalReceivedQty;
    Integer maxReceiveQuantity =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();

    deliveryDocument.getDeliveryDocumentLines().get(0).setOpenQty(openQty);
    deliveryDocument.getDeliveryDocumentLines().get(0).setMaxReceiveQty(maxReceiveQuantity);
    deliveryDocument.getDeliveryDocumentLines().get(0).setTotalReceivedQty(totalReceivedQty);

    /* in case of qty receiving upon Scan UPC we need to display allowable overages qty when we already received
    upto totalOrderQty. For WorkStation/ScanToPrint we display the overage banner experience once we
    crossed totalOrderQty
    */
    boolean maxAllowedOverageQtyIncluded =
        isDaQtyReceiving && (totalReceivedQty >= deliveryDocumentLine.getTotalOrderQty())
            ? Boolean.TRUE
            : (totalReceivedQty > deliveryDocumentLine.getTotalOrderQty())
                ? Boolean.TRUE
                : Boolean.FALSE;

    /* if Non Con RTS PUT item scanned, set maxAllowedOverageQtyIncluded as true as user scans each time
    manually to receive N qty as master container & Client needs to show allowable overage details  */
    if (maxAllowedOverageQtyIncluded
        || deliveryDocumentLine
                .getAdditionalInfo()
                .getHandlingCode()
                .equals(RdcConstants.NON_CON_RTS_PUT_HANDLING_CODE)
            && !(RdcUtils.isBreakPackNonConRtsPutItem(deliveryDocumentLine))) {
      logger.info(
          "Received all of the order quantity for PO:{} and POL:{}, setting MaxAllowedOverageQtyIncluded flag to true",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      deliveryDocument.getDeliveryDocumentLines().get(0).setMaxAllowedOverageQtyIncluded(true);
    }
  }

  /**
   * @param deliveryDocument
   * @param httpHeaders
   * @param instructionRequest
   * @param receiveQty
   * @param receiveInstructionRequest
   * @return
   */
  public InstructionResponse receiveContainers(
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders,
      InstructionRequest instructionRequest,
      int receiveQty,
      ReceiveInstructionRequest receiveInstructionRequest) {
    boolean rollbackForException = false;
    boolean areLpnsUsed = false;
    List<String> labelTrackingIds = null;
    List<Container> containers = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();
    List<Receipt> receipts = new ArrayList<>();
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    List<LabelData> labelDataList = null;
    List<ContainerItem> matchedContainerItems = null;
    List<Container> matchedContainers = null;
    List<ReceivedContainer> parentReceivedContainers = new ArrayList<>();
    List<ContainerItem> existingContainerItemsList = new ArrayList<>();
    Set<Container> existingContainersSet = new HashSet<>();
    Collection<OutboxEvent> outboxEvents = new ArrayList<>();
    Map<String, Object> printLabelData = null;
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    boolean isAtlasConvertedItem = deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem();
    boolean isPalletPullByStore =
        Objects.nonNull(receiveInstructionRequest)
            && Objects.nonNull(receiveInstructionRequest.getStoreNumber());
    boolean isDaQtyReceiving =
        Objects.nonNull(receiveInstructionRequest) ? Boolean.TRUE : Boolean.FALSE;

    boolean isAtlasDaSlottingRequest =
        isAtlasConvertedItem && isSlottingPayLoad(receiveInstructionRequest);
    boolean isLessThanCase =
        Objects.nonNull(receiveInstructionRequest)
            && Boolean.TRUE.equals(receiveInstructionRequest.getIsLessThanCase());
    SlotDetails slotDetails =
        Objects.nonNull(receiveInstructionRequest)
            ? receiveInstructionRequest.getSlotDetails()
            : null;
    validateQuantityReceivingNotAllowed(
        isDaQtyReceiving,
        receiveQty,
        isLessThanCase,
        isAtlasDaSlottingRequest,
        deliveryDocumentLine);

    validateSlottingEligibilityByPackTypeHandlingCode(
        deliveryDocumentLine, isPalletPullByStore, isAtlasDaSlottingRequest);

    if (isAtlasDaSlottingRequest) {
      if (isAutomationSlottingForDAConveyableItem(slotDetails)) {
        if (isAutomationSlottingEligibleForDAConveyableItem(deliveryDocumentLine)) {
          logger.info(
              "Automation DA slotting selected for itemNumber:{}, deliveryNumber:{}",
              deliveryDocumentLine.getItemNbr(),
              deliveryDocument.getDeliveryNumber());
        } else {
          throw new ReceivingBadDataException(
              ExceptionCodes.ATLAS_DA_AUTOMATION_SLOTTING_NOT_ALLOWED,
              ReceivingException.ATLAS_DA_AUTOMATION_SLOTTING_NOT_ALLOWED);
        }
      } else if (isDaConventionalSlottingForConveyableItem(
          slotDetails, deliveryDocumentLine.getAdditionalInfo().getHandlingCode())) {
        logger.info(
            "Conventional DA slotting selected for itemNumber:{}, deliveryNumber:{}",
            deliveryDocumentLine.getItemNbr(),
            deliveryDocument.getDeliveryNumber());
      } else {
        blockSlottingForUnSupportedhandlingCodes(deliveryDocument);
      }
      isAtlasConvertedItem = deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem();
    }

    try {
      validateDaAtlasNonSupportedHandlingCode(
          deliveryDocument.getDeliveryNumber(), deliveryDocumentLine);
      int containerCount =
          rdcReceivingUtils.getContainersCountToBeReceived(
              deliveryDocumentLine, receiveQty, receiveInstructionRequest, isPalletPullByStore);

      // CP VOICEPUT & NONCON_RTSPUT generates 1 label with N qty
      int caseReceiveQty =
          (containerCount == RdcConstants.CONTAINER_COUNT_ONE)
              ? receiveQty
              : RdcConstants.RDC_DA_CASE_RECEIVE_QTY;

      if (!isAtlasConvertedItem) {
        logger.info(
            "Item:{} is non Atlas item, invoke RDS to receive container for UPC: {}",
            deliveryDocumentLine.getItemNbr(),
            instructionRequest.getUpcNumber());
        rdcReceivingUtils.blockRdsReceivingForNonAtlasItem(deliveryDocumentLine);
        receivedContainers =
            rdcReceivingUtils.receiveContainers(
                containerCount,
                instructionRequest,
                deliveryDocumentLine,
                httpHeaders,
                receiveInstructionRequest);
        if (!CollectionUtils.isEmpty(receivedContainers)) {
          labelTrackingIds =
              receivedContainers
                  .stream()
                  .map(ReceivedContainer::getLabelTrackingId)
                  .collect(Collectors.toList());
          parentReceivedContainers = receivedContainers;
        }

        // process DA Pallet containers for Non Atlas items
        if (isDaQtyReceiving && Objects.nonNull(slotDetails)) {
          return processDAPalleContainers(
              instructionRequest,
              receiveInstructionRequest,
              receivedContainers,
              deliveryDocument,
              httpHeaders);
        }

      } else {
        logger.info(
            "Item:{} is atlas converted, fetching instruction for UPC:{}",
            deliveryDocumentLine.getItemNbr(),
            instructionRequest.getUpcNumber());

        receiveQty =
            RdcUtils.getReceiveQtyForSlottingRequest(receiveInstructionRequest, receiveQty);
        int labelCount =
            getReceiveQtyByContainerCount(
                containerCount, receiveQty, deliveryDocumentLine, isLessThanCase);

        // fetch N lpns from label data table based on labelCount
        labelDataList =
            fetchLabelInstruction(deliveryDocument, labelCount, receiveInstructionRequest);

        /* this is to roll back hawkeye LPNs if any exception occurred here after */
        areLpnsUsed = true;

        validateLabelCount(labelCount, labelDataList, deliveryDocumentLine.getItemNbr());

        // build receive containers from label data
        receivedContainers =
            transformLabelDataByReceivingMethods(
                receivedContainers,
                isPalletPullByStore,
                receiveInstructionRequest,
                labelDataList,
                deliveryDocument,
                receiveQty,
                httpHeaders);

        validateProDate(deliveryDocument);

        // parent containers needed only for generating labels/printjob references
        parentReceivedContainers =
            receivedContainers
                .stream()
                .filter(
                    receivedContainer ->
                        StringUtils.isBlank(receivedContainer.getParentTrackingId()))
                .collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(parentReceivedContainers)) {
          labelTrackingIds =
              parentReceivedContainers
                  .stream()
                  .map(ReceivedContainer::getLabelTrackingId)
                  .collect(Collectors.toList());
        }
      }

      /*validate duplicate carton tags for Case labels. For DA Non Atlas items the chance of getting duplicate labels/carton tags
      are very high (2 days once). In case of Atlas items there's a chance of getting child inner picks (Break Pack PUT labels)
      as smart labels which can conflict with existing smart labels already available in container & container item table.*/

      existingContainerItemsList =
          containerPersisterService.findAllItemByTrackingId(labelTrackingIds);
      existingContainersSet = containerService.getContainerListByTrackingIdList(labelTrackingIds);

      Instruction instruction =
          createInstruction(
              instructionRequest,
              deliveryDocument,
              httpHeaders,
              deliveryDocumentLine,
              isDaQtyReceiving);

      PrintJob printJob =
          printJobService.createPrintJob(
              Long.valueOf(instructionRequest.getDeliveryNumber()),
              instruction.getId(),
              new HashSet<>(labelTrackingIds),
              userId);

      String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());

      // pallet label for Atlas DA Slotting
      if (isAtlasDaSlottingRequest) {
        Map<String, Integer> trackingIdQuantityMap = new HashMap<>();
        Map<ReceivedContainer, PrintJob> receivedContainerPrintJobMap = new HashMap<>();
        String palletTrackingId = parentReceivedContainers.get(0).getLabelTrackingId();
        trackingIdQuantityMap.put(palletTrackingId, receiveQty);
        receivedContainerPrintJobMap.put(parentReceivedContainers.get(0), printJob);
        LabelFormat labelFormat = rdcReceivingUtils.getLabelFormatForPallet(deliveryDocumentLine);
        printLabelData =
            LabelGenerator.generateDAPalletLabels(
                deliveryDocumentLine,
                trackingIdQuantityMap,
                parentReceivedContainers,
                receivedContainerPrintJobMap,
                httpHeaders,
                ReceivingUtils.getDCDateTime(dcTimeZone),
                labelFormat);
      } else {
        // Case labels for DA items
        printLabelData =
            LabelGenerator.generateDACaseLabel(
                deliveryDocumentLine,
                caseReceiveQty,
                parentReceivedContainers,
                printJob.getId(),
                httpHeaders,
                ReceivingUtils.getDCDateTime(dcTimeZone),
                tenantSpecificConfigReader.isFeatureFlagEnabled(
                    ReceivingConstants.MFC_INDICATOR_FEATURE_FLAG, getFacilityNum()));
      }

      for (ReceivedContainer receivedContainer : receivedContainers) {
        /* for Atlas converted items we rely on packQty from OP Label Instruction response in Eaches as we have parent & child containers
        to be persisted with the corresponding received qty */
        caseReceiveQty = isAtlasConvertedItem ? receivedContainer.getPack() : caseReceiveQty;
        if (!CollectionUtils.isEmpty(existingContainerItemsList)) {
          matchedContainerItems =
              existingContainerItemsList
                  .stream()
                  .filter(
                      item -> item.getTrackingId().equals(receivedContainer.getLabelTrackingId()))
                  .collect(Collectors.toList());
        }
        ContainerItem containerItem =
            !CollectionUtils.isEmpty(matchedContainerItems) ? matchedContainerItems.get(0) : null;
        containerItem =
            rdcContainerUtils.buildContainerItemDetails(
                receivedContainer.getLabelTrackingId(),
                deliveryDocument,
                caseReceiveQty,
                containerItem,
                receivedContainer.getStoreAlignment(),
                receivedContainer.getDistributions(),
                receivedContainer.getDestType());

        if (!CollectionUtils.isEmpty(existingContainersSet)) {
          matchedContainers =
              existingContainersSet
                  .stream()
                  .filter(
                      container ->
                          container.getTrackingId().equals(receivedContainer.getLabelTrackingId()))
                  .collect(Collectors.toList());
        }
        Container container =
            !CollectionUtils.isEmpty(matchedContainers) ? matchedContainers.get(0) : null;
        container =
            rdcContainerUtils.buildContainer(
                instructionRequest.getDoorNumber(),
                instruction.getId(),
                Long.valueOf(instructionRequest.getDeliveryNumber()),
                instructionRequest.getMessageId(),
                deliveryDocument,
                userId,
                receivedContainer,
                container,
                receiveInstructionRequest);
        containers.add(container);
        containerItems.add(containerItem);
      }

      // build receipts
      if (isAtlasConvertedItem) {
        receipts.addAll(
            receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
                deliveryDocument,
                instructionRequest.getDoorNumber(),
                instruction.getProblemTagId(),
                userId,
                receiveQty,
                isLessThanCase));
      }

      Integer totalReceivedQty =
          deliveryDocument.getDeliveryDocumentLines().get(0).getTotalReceivedQty() + receiveQty;
      populateReceiveQtyDetailsInDeliveryDocument(
          deliveryDocument, totalReceivedQty, isDaQtyReceiving);
      instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
      updateInstruction(instruction, receivedContainers.get(0), userId, receiveQty, printLabelData);
      if (!CollectionUtils.isEmpty(labelDataList)) {
        labelDataList.forEach(
            labelData -> labelData.setStatus(LabelInstructionStatus.COMPLETE.name()));
      }

      completeProblemTag(instruction);

      if (isAtlasDaSlottingRequest) {
        if (!CollectionUtils.isEmpty(parentReceivedContainers)
            && !CollectionUtils.isEmpty(parentReceivedContainers.get(0).getDestinations())
            && Objects.nonNull(
                parentReceivedContainers.get(0).getDestinations().get(0).getSlot())) {
          LinkedTreeMap<String, Object> moveTreeMap =
              rdcInstructionUtils.moveDetailsForInstruction(
                  receiveInstructionRequest,
                  parentReceivedContainers.get(0).getLabelTrackingId(),
                  parentReceivedContainers.get(0).getDestinations().get(0).getSlot(),
                  httpHeaders);
          instruction.setMove(moveTreeMap);
          instruction.setContainer(
              rdcContainerUtils.getContainerDetails(
                  parentReceivedContainers.get(0).getLabelTrackingId(),
                  printLabelData,
                  ContainerType.PALLET,
                  RdcConstants.OUTBOUND_CHANNEL_METHOD_CROSSDOCK));
        }
      }

      TenantContext.get().setDaCaseReceivingDataPersistStart(System.currentTimeMillis());
      rdcReceivingUtils.persistReceivedContainerDetails(
          Collections.singletonList(instruction),
          containers,
          containerItems,
          receipts,
          labelDataList);
      TenantContext.get().setDaCaseReceivingDataPersistEnd(System.currentTimeMillis());

      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED,
          false)) {
        TenantContext.get().setPersistOutboxEventsForDAStart(System.currentTimeMillis());
        if (isAtlasConvertedItem) {
          outboxEvents =
              rdcReceivingUtils.buildOutboxEvents(
                  receivedContainers, httpHeaders, instruction, deliveryDocument);
        } else {
          // Non Atlas items only need WFT integration
          outboxEvents =
              rdcReceivingUtils.buildOutboxEventForWFT(
                  instruction, deliveryDocumentLine, receiveQty, httpHeaders, isLessThanCase);
        }
        rdcReceivingUtils.persistOutboxEvents(outboxEvents);
        TenantContext.get().setPersistOutboxEventsForDAEnd(System.currentTimeMillis());
      } else {
        TenantContext.get().setPostReceivingUpdatesInKafkaStart(System.currentTimeMillis());
        rdcReceivingUtils.postReceivingUpdates(
            instruction,
            deliveryDocument,
            receiveQty,
            httpHeaders,
            isAtlasConvertedItem,
            receivedContainers,
            isLessThanCase);
        TenantContext.get().setPostReceivingUpdatesInKafkaEnd(System.currentTimeMillis());
      }

      if (isAtlasDaSlottingRequest) {
        // Publish move message to MM
        if (Objects.nonNull(instruction.getMove())) {
          TenantContext.get().setReceiveInstrPublishMoveCallStart(System.currentTimeMillis());
          rdcContainerUtils.publishMove(
              receiveInstructionRequest.getDoorNumber(),
              receiveQty,
              instruction.getMove(),
              httpHeaders);
          TenantContext.get().setReceiveInstrPublishMoveCallEnd(System.currentTimeMillis());
        }
      }

      return new InstructionResponseImplNew(
          instructionRequest.getDeliveryStatus(),
          Collections.singletonList(deliveryDocument),
          instruction,
          printLabelData);

    } catch (ReceivingBadDataException rbde) {
      rollbackForException = true;
      throw rbde;
    } catch (CompletionException cmple) {
      rollbackForException = true;
      String errorMessage =
          Objects.nonNull(cmple.getCause()) ? cmple.getCause().getMessage() : cmple.getMessage();
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_RECEIVE_INSTRUCTION_ERROR,
          ReceivingException.DA_RECEIVE_INSTRUCTION_ERROR_MSG,
          errorMessage);
    } catch (Exception e) {
      rollbackForException = true;
      logger.error(
          "{} {}",
          ReceivingException.DA_RECEIVE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVE_INSTRUCTION_INTERNAL_ERROR,
          ReceivingException.DA_RECEIVE_INSTRUCTION_ERROR_MSG,
          e);
    } finally {
      if (rollbackForException
          && !isAtlasConvertedItem
          && !CollectionUtils.isEmpty(labelTrackingIds)) {
        logger.info(
            "Error while receiving DA container, invoking nimRDS service to backout label: {}",
            labelTrackingIds.get(0));
        nimRdsService.backoutDALabels(labelTrackingIds, httpHeaders);
      }
      if (rollbackForException
          && isAtlasConvertedItem
          && areLpnsUsed
          && !CollectionUtils.isEmpty(labelDataList)
          && isHawkeyeIntegrationEnabledForManualReceiving()
          && appConfig
              .getValidItemPackTypeHandlingCodeCombinations()
              .contains(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode())) {
        rdcAsyncUtils.labelUpdateToHawkeye(httpHeaders, labelDataList);
      }
      // publish label status void to Hawkeye
      updateLabelStatusVoidToHawkeye(
          labelDataList, isAtlasConvertedItem, httpHeaders, deliveryDocumentLine);
    }
  }

  private void blockSlottingForUnSupportedhandlingCodes(DeliveryDocument deliveryDocument) {
    String itemHandlingCode =
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getItemPackAndHandlingCode();
    boolean isDaNonConSlottingEnabledHandlingCode =
        !CollectionUtils.isEmpty(rdcManagedConfig.getAtlasDaNonConPackAndHandlingCodes())
            ? rdcManagedConfig.getAtlasDaNonConPackAndHandlingCodes().contains(itemHandlingCode)
            : DA_NON_CON_SLOTTING_HANDLING_CODES.contains(itemHandlingCode);
    if (isDaNonConSlottingEnabledHandlingCode) {
      logger.info(
          "DA slotting not allowed for selected itemNumber:{}, handlingCode:{} deliveryNumber:{}",
          deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr(),
          deliveryDocument
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo()
              .getHandlingCode()
              .toString(),
          deliveryDocument.getDeliveryNumber());
    } else {
      throw new ReceivingBadDataException(
          String.format(ExceptionCodes.ATLAS_DA_SLOTTING_NOT_SUPPORTED_HANDLING_CODES),
          String.format(
              ReceivingException.ATLAS_DA_SLOTTING_NOT_SUPPORTED_HANDLING_CODES,
              deliveryDocument
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getAdditionalInfo()
                  .getHandlingCode()
                  .toString(),
              deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr().toString()),
          deliveryDocument
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo()
              .getHandlingCode()
              .toString(),
          deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr().toString());
    }
  }

  /**
   * This method build Received Container from Label Data based on the receiving methods. The
   * possible receiving methods are Slotting, Less Than a Case, Pallet Pull & Non Con RTS PUT & UPC
   * receiving (Case Pack, Break Pack (PUT)
   *
   * @param receivedContainers
   * @param receiveInstructionRequest
   * @param labelDataList
   * @param deliveryDocument
   * @param isPalletPullByStore
   * @param receiveQty
   * @param httpHeaders
   */
  private List<ReceivedContainer> transformLabelDataByReceivingMethods(
      List<ReceivedContainer> receivedContainers,
      boolean isPalletPullByStore,
      ReceiveInstructionRequest receiveInstructionRequest,
      List<LabelData> labelDataList,
      DeliveryDocument deliveryDocument,
      int receiveQty,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    ReceivingMethod receivingMethod =
        getReceivingMethod(deliveryDocument, receiveInstructionRequest);
    switch (receivingMethod) {
      case SLOTTING:
        buildContainersForSlotting(
            receiveInstructionRequest,
            labelDataList,
            deliveryDocument,
            receivedContainers,
            httpHeaders);
        break;
      case LESS_THAN_CASE:
        receivedContainers =
            new ArrayList<>(
                buildReceivedContainersForLessThanACase(
                    receiveInstructionRequest, labelDataList, deliveryDocument, receiveQty));
        break;
      case PALLET_PULL:
        receivedContainers =
            buildContainersForPalletPullByStore(
                receiveInstructionRequest,
                labelDataList,
                deliveryDocument,
                receivedContainers,
                httpHeaders,
                isPalletPullByStore);
        break;
      case NON_CON_RTS_PUT:
        receivedContainers =
            buildReceivedContainerForNonConRtsPut(
                receiveInstructionRequest,
                labelDataList,
                deliveryDocument,
                receivedContainers,
                httpHeaders);
        break;
      default:
        receivedContainers =
            new ArrayList<>(
                transformLabelData(
                    receiveInstructionRequest,
                    labelDataList,
                    deliveryDocument,
                    isPalletPullByStore));
        break;
    }

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
        false)) {
      // Based on handling code validating correct LPN format
      validateLabelFormatByHandlingCodes(receivedContainers, deliveryDocument);
    }
    return receivedContainers;
  }

  /**
   * This method checks if DA slotting for Atlas Conveyable items (CI,CC,CE,BC,BM) enabled or not.
   * If the flag is enabled then we do not override the atlasConvertedItem field. If the flag is not
   * enabled, then validate item handling code with the conveyable handling codes mapped in
   * rdcManagedConfig (C,I,J,M) & override atlasConvertedItem flag as false. Once we set this flag
   * as false then this item will be received through legacy DA slotting (RDS). We will support this
   * feature until we support Slotting or DA Conveyable handling codes
   *
   * @param deliveryDocumentLine
   * @param deliveryDocument
   */
  private void validateDaSlottingSupportedForAtlasConveyableItems(
      DeliveryDocumentLine deliveryDocumentLine, DeliveryDocument deliveryDocument) {
    logger.info(
        "Slotting request received for Atlas item:{}, purchaseReferenceNumber:{}, purchaseReferenceLineNumber:{},"
            + "deliveryNumber:{}",
        deliveryDocumentLine.getItemNbr(),
        deliveryDocumentLine.getPurchaseReferenceNumber(),
        deliveryDocumentLine.getPurchaseReferenceLineNumber(),
        deliveryDocument.getDeliveryNumber());
    String itemHandlingCode = deliveryDocumentLine.getAdditionalInfo().getHandlingCode();
    if (isConveyableItemHandlingCode(itemHandlingCode)) {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          RdcConstants.IS_DA_SLOTTING_BLOCKED_FOR_ATLAS_CONVEYABLE_ITEMS,
          false)) {
        /* block user upon slotting conveyable (C,I,J,E) DA freights. User can slot only the Non Conveyable items*/
        logger.info(
            "Atlas Da slotting not supported for Conveyable items hence blocking user upon slotting DA conveyable items for itemNumber:{}, "
                + "purchaseReferenceNumber:{}, purchaseReferenceLineNumber:{},"
                + "deliveryNumber:{}, itemHandlingCode:{}",
            deliveryDocumentLine.getItemNbr(),
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            deliveryDocument.getDeliveryNumber(),
            itemHandlingCode);
        throw new ReceivingBadDataException(
            String.format(ExceptionCodes.ATLAS_DA_SLOTTING_NOT_ALLOWED_FOR_CONVEYABLE_ITEMS),
            String.format(
                ReceivingException.ATLAS_DA_SLOTTING_NOT_ALLOWED_FOR_CONVEYABLE_ITEMS,
                deliveryDocumentLine.getItemNbr().toString()),
            deliveryDocumentLine.getItemNbr().toString());
      } else {
        deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(false);
        deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
        logger.info(
            "Atlas Da slotting not supported for Conveyable items hence updating atlasConvertedItem as false for itemNumber:{}, "
                + "purchaseReferenceNumber:{}, purchaseReferenceLineNumber:{},"
                + "deliveryNumber:{}, itemHandlingCode:{}",
            deliveryDocumentLine.getItemNbr(),
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            deliveryDocument.getDeliveryNumber(),
            itemHandlingCode);
      }
    }
  }

  /**
   * @param itemHandlingCode
   * @return
   */
  private boolean isConveyableItemHandlingCode(String itemHandlingCode) {
    return !CollectionUtils.isEmpty(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting())
        && rdcManagedConfig
            .getAtlasConveyableHandlingCodesForDaSlotting()
            .contains(itemHandlingCode);
  }

  /**
   * @param receiveInstructionRequest
   * @return
   */
  private boolean isSlottingPayLoad(ReceiveInstructionRequest receiveInstructionRequest) {
    return Objects.nonNull(receiveInstructionRequest)
        && Objects.nonNull(receiveInstructionRequest.getSlotDetails());
  }

  /**
   * In case of Break Pack Convey Picks we need to print N labels based on the container Count
   *
   * @param containerCount
   * @param receiveQty
   * @return
   */
  private int getReceiveQtyByContainerCount(
      int containerCount,
      int receiveQty,
      DeliveryDocumentLine deliveryDocumentLine,
      Boolean isLessThanCase) {
    // IsLessThanCase, if BM label count=6, if BC label count=1
    String itemPackAndHandlingCode =
        deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode();
    if (isLessThanCase) {
      if (StringUtils.equalsIgnoreCase(
          itemPackAndHandlingCode, RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE)) {
        return receiveQty;
      } else if (StringUtils.equalsIgnoreCase(
          itemPackAndHandlingCode, DA_BREAK_PACK_CONVEYABLE_ITEM_HANDLING_CODE)) {
        return RdcConstants.CONTAINER_COUNT_ONE;
      }
    }
    return (containerCount == RdcConstants.CONTAINER_COUNT_ONE) ? receiveQty : containerCount;
  }

  /** @param deliveryDocument */
  private void validateProDate(DeliveryDocument deliveryDocument) {
    if (Objects.isNull(deliveryDocument.getProDate())) {
      deliveryDocument.setProDate(new Date());
    }
  }

  /** @param instruction */
  private void completeProblemTag(Instruction instruction) throws ReceivingException {
    if (StringUtils.isNotBlank(instruction.getProblemTagId())) {
      logger.info("Invoking completeProblem() for problemTagId: {}", instruction.getProblemTagId());
      tenantSpecificConfigReader
          .getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.PROBLEM_SERVICE,
              ProblemService.class)
          .completeProblem(instruction);
    }
  }

  /**
   * In case of automation enabled, invoke Hawkeye API to get the LPNs byu passing the
   * DeliveryDocument and use the LPNS to get labelData from labelData table.
   *
   * @param deliveryDocument
   * @param caseReceiveQty
   * @param receiveInstructionRequest
   * @return
   */
  private List<LabelData> fetchLabelInstruction(
      DeliveryDocument deliveryDocument,
      Integer caseReceiveQty,
      ReceiveInstructionRequest receiveInstructionRequest) {
    List<LabelData> labelDataList;
    boolean isPalletPullByStore = false;
    Integer palletPullByStoreNumber = null;
    if (Objects.nonNull(receiveInstructionRequest)) {
      palletPullByStoreNumber = receiveInstructionRequest.getStoreNumber();
      isPalletPullByStore = Objects.nonNull(palletPullByStoreNumber);
    }
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    if (isHawkeyeIntegrationEnabledForManualReceiving()
        && appConfig
            .getValidItemPackTypeHandlingCodeCombinations()
            .contains(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode())) {
      HawkeyeGetLpnsRequest hawkeyeGetLpnsRequest =
          HawkeyeGetLpnsRequest.builder()
              .deliveryNumber(String.valueOf(deliveryDocument.getDeliveryNumber()))
              .itemNumber(Math.toIntExact(deliveryDocumentLine.getItemNbr()))
              .quantity(caseReceiveQty)
              .storeNumber(palletPullByStoreNumber)
              .build();
      TenantContext.get().setFetchLpnsFromHawkeyeStart(System.currentTimeMillis());
      List<String> lpnList = null;
      HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
      try {
        lpnList =
            hawkeyeRestApiClient
                .getLpnsFromHawkeye(hawkeyeGetLpnsRequest, httpHeaders)
                .orElse(Collections.emptyList());
      } catch (ReceivingBadDataException exception) {
        if (exception.getMessage().contains(ReceivingConstants.HAWKEYE_NO_DELIVERY_OR_ITEM_FOUND)) {
          republishLabelsToHawkeyeForNoDeliveryItemFoundError(deliveryDocument, httpHeaders);
        } else {
          throw exception;
        }
      }
      TenantContext.get().setFetchLpnsFromHawkeyeEnd(System.currentTimeMillis());
      TenantContext.get()
          .setFetchLabelDataListByGettingLpnsFromHawkeyeDBCallStart(System.currentTimeMillis());
      labelDataList = labelDataService.findByTrackingIdIn(lpnList);
      TenantContext.get()
          .setFetchLabelDataListByGettingLpnsFromHawkeyeDBCallEnd(System.currentTimeMillis());
      validateLabelStatusForHawkeyeLpns(labelDataList, deliveryDocument.getDeliveryNumber());
    } else {
      if (isPalletPullByStore) {
        TenantContext.get()
            .setFetchLabelDataByPoAndItemNumberAndDestStoreNumberDBCallStart(
                System.currentTimeMillis());
        labelDataList =
            labelDataService.fetchLabelDataByPoAndItemNumberAndStoreNumber(
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getItemNbr(),
                palletPullByStoreNumber,
                LabelInstructionStatus.AVAILABLE.name(),
                caseReceiveQty,
                TenantContext.getFacilityNum(),
                TenantContext.getFacilityCountryCode());
        TenantContext.get()
            .setFetchLabelDataByPoAndItemNumberAndDestStoreNumberDBCallEnd(
                System.currentTimeMillis());
      } else {
        TenantContext.get()
            .setFetchLabelDataByPoAndItemNumberDBCallStart(System.currentTimeMillis());
        labelDataList =
            labelDataService.fetchLabelDataByPoAndItemNumber(
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getItemNbr(),
                LabelInstructionStatus.AVAILABLE.name(),
                caseReceiveQty,
                TenantContext.getFacilityNum(),
                TenantContext.getFacilityCountryCode());
        TenantContext.get().setFetchLabelDataByPoAndItemNumberDBCallEnd(System.currentTimeMillis());
      }
    }
    if (CollectionUtils.isEmpty(labelDataList)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.NO_ALLOCATIONS_FOR_DA_FREIGHT,
          ReceivingException.NO_ALLOCATIONS_FOR_DA_FREIGHT);
    }
    return labelDataList;
  }

  /**
   * This method is to fetch available labels and republish labels to Hawkeye if not empty.
   *
   * @param deliveryDocument
   * @param httpHeaders
   */
  public void republishLabelsToHawkeyeForNoDeliveryItemFoundError(
      DeliveryDocument deliveryDocument, HttpHeaders httpHeaders) {
    Long itemNumber = deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr();
    Long deliveryNumber = deliveryDocument.getDeliveryNumber();

    List<LabelDownloadEvent> labelDownloadEventList =
        labelDownloadEventService.findByDeliveryNumberAndItemNumber(deliveryNumber, itemNumber);
    Set<String> purchaseReferenceNumberSet =
        labelDownloadEventList
            .stream()
            .filter(
                labelDownloadEvent ->
                    !rdcSSTKLabelGenerationUtils.isSSTKLabelDownloadEvent(labelDownloadEvent))
            .map(LabelDownloadEvent::getPurchaseReferenceNumber)
            .collect(Collectors.toSet());
    List<LabelData> labelDataList =
        labelDataService.findByPurchaseReferenceNumberInAndItemNumberAndStatus(
            purchaseReferenceNumberSet, itemNumber, LabelInstructionStatus.AVAILABLE.name());

    if (!CollectionUtils.isEmpty(labelDataList)) {
      rdcLabelGenerationService.processAndPublishLabelDataAsync(
          deliveryNumber, itemNumber, purchaseReferenceNumberSet, labelDataList, httpHeaders);
      throw new ReceivingBadDataException(
          ExceptionCodes.HAWKEYE_FETCH_LPNS_FAILED,
          ReceivingConstants.HAWKEYE_LABEL_GENERATION_IN_PROGRESS_RETRY_AGAIN);
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.HAWKEYE_FETCH_LPNS_FAILED, ReceivingConstants.HAWKEYE_LABELS_UNAVAILABLE);
    }
  }

  /**
   * This method validates the label status and if there are any Labels with non Available status
   * (COMPLETE/CANCELLED) then will throw an exception as the LPNs provided from hawkeye was not
   * with Available staus in Receiving.
   *
   * @param labelDataList
   * @param deliveryNumber
   */
  private void validateLabelStatusForHawkeyeLpns(
      List<LabelData> labelDataList, Long deliveryNumber) {
    labelDataList =
        labelDataList
            .stream()
            .filter(
                labelData ->
                    StringUtils.isNotBlank(labelData.getStatus())
                        && !labelData.getStatus().equals(LabelInstructionStatus.AVAILABLE.name()))
            .collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(labelDataList)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_LABEL_STATUS_FOR_HAWKEYE_LPNS,
          String.format(
              ReceivingException.INVALID_LABEL_STATUS_FOR_HAWKEYE_LPNS,
              deliveryNumber,
              labelDataList.get(0).getItemNumber()),
          deliveryNumber,
          labelDataList.get(0).getItemNumber());
    }
  }

  /**
   * This method validates if the requested labels count is matched against the labels returned from
   * label data list.
   *
   * @param labelCountRequested
   * @param labelDataList
   * @param itemNumber
   */
  private void validateLabelCount(
      int labelCountRequested, List<LabelData> labelDataList, Long itemNumber) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_ATLAS_DA_LABEL_COUNT_VALIDATION_ENABLED,
        false)) {
      boolean isLabelCountMatched = labelCountRequested == labelDataList.size();
      if (!isLabelCountMatched) {
        logger.info(
            "Label count is not matched. Number of labels requested is:{} but labels returned from labelData is:{}",
            labelCountRequested,
            labelDataList.size());
        throw new ReceivingBadDataException(
            ExceptionCodes.LABEL_COUNT_NOT_MATCHED,
            String.format(ReceivingException.LABEL_COUNT_NOT_MATCHED, itemNumber.toString()),
            itemNumber.toString());
      }
    }
  }

  private boolean isHawkeyeIntegrationEnabledForManualReceiving() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false)
        && tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false);
  }

  private boolean isVoidLabelsForCasePackSymIneligible() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_VOID_LABELS_FOR_CASE_PACK_SYM_INELIGIBLE_ENABLED,
        false);
  }

  /**
   * @param receiveInstructionRequest
   * @param labelDataList
   * @param deliveryDocument
   * @param isPalletPullByStore
   * @return
   */
  public List<ReceivedContainer> transformLabelData(
      ReceiveInstructionRequest receiveInstructionRequest,
      List<LabelData> labelDataList,
      DeliveryDocument deliveryDocument,
      boolean isPalletPullByStore) {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    SlotDetails slotDetails =
        Objects.nonNull(receiveInstructionRequest)
            ? receiveInstructionRequest.getSlotDetails()
            : null;
    boolean isAutomationSlottingForDAConveyableItem =
        isAutomationSlottingForDAConveyableItem(slotDetails);
    Map<String, DeliveryDocumentLine> deliveryDocumentLineMap = new HashMap<>();

    if (Objects.nonNull(deliveryDocument.getEventType())
        && EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
      deliveryDocumentLineMap =
          deliveryDocument
              .getDeliveryDocumentLines()
              .stream()
              .collect(
                  Collectors.toMap(
                      deliveryDocumentLine ->
                          deliveryDocumentLine.getChildTrackingId() != null
                              ? deliveryDocumentLine.getChildTrackingId()
                              : deliveryDocumentLine.getTrackingId(),
                      Function.identity()));
    }
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    for (LabelData labelData : labelDataList) {
      DeliveryDocumentLine documentLine;
      List<InstructionDownloadChildContainerDTO> childLabelDataContainers =
          labelData.getAllocation().getChildContainers();
      boolean isBreakPackPutContainer = !CollectionUtils.isEmpty(childLabelDataContainers);
      if (isBreakPackPutContainer) {
        if (EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
          documentLine = deliveryDocumentLineMap.get(deliveryDocumentLine.getChildTrackingId());
        } else {
          documentLine = deliveryDocumentLine;
        }
        // build break pack child containers for PUT
        String parentTrackingId = labelData.getTrackingId();
        List<ReceivedContainer> childReceivedContainers = new ArrayList<>();
        childLabelDataContainers.forEach(
            childContainer -> {
              ReceivedContainer childReceivedContainer =
                  buildReceivedContainer(
                      labelData,
                      deliveryDocument,
                      documentLine,
                      childContainer.getTrackingId(),
                      parentTrackingId,
                      childContainer.getDistributions(),
                      childContainer.getCtrDestination(),
                      isAutomationSlottingForDAConveyableItem,
                      isPalletPullByStore,
                      false);
              childReceivedContainers.add(childReceivedContainer);
            });

        Integer receivedQtyForParentContainerInEaches =
            childReceivedContainers
                .stream()
                .map(ReceivedContainer::getPack)
                .reduce(0, Integer::sum);
        receivedContainers.addAll(childReceivedContainers);

        // build parent container for PUT
        ReceivedContainer parentReceivedContainer =
            buildParentContainerForPutLabels(
                parentTrackingId,
                deliveryDocument,
                receivedQtyForParentContainerInEaches,
                childReceivedContainers);
        if (Objects.nonNull(parentReceivedContainer)) {
          receivedContainers.add(parentReceivedContainer);
        }
      } else {
        if (EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
          documentLine = deliveryDocumentLineMap.get(deliveryDocumentLine.getTrackingId());
        } else {
          documentLine = deliveryDocumentLine;
        }
        // build case pack shipping/routing containers
        ReceivedContainer receivedContainer =
            buildReceivedContainer(
                labelData,
                deliveryDocument,
                documentLine,
                labelData.getTrackingId(),
                null,
                labelData.getAllocation().getContainer().getDistributions(),
                labelData.getAllocation().getContainer().getFinalDestination(),
                isAutomationSlottingForDAConveyableItem,
                isPalletPullByStore,
                false);
        receivedContainers.add(receivedContainer);
      }
    }
    return receivedContainers;
  }

  /**
   * This method build Receive Containers for Less Than a case feature, applicable only for Break
   * Pack items. In case of BC(Break pack Conveyable), we have child containers in label_data table.
   * If Less than a case choosen for BC then we need to fetch the first N qty (receiveQty entered by
   * user in Receiving Client) from the child containers & return list of N containers to treat them
   * as R8000 Case pack containers. In case of BM(Break pack Convey Picks), BN(Break pack Non Con)
   * we have LPNs/containers in Label_data at WHPK level so we would receive N containers from
   * Label_data table directly. [No child containers exist for Break pack Convey Picks & Non Con
   * features]
   *
   * @param receiveInstructionRequest
   * @param labelDataList
   * @param deliveryDocument
   * @param receiveQty
   * @return
   */
  public List<ReceivedContainer> buildReceivedContainersForLessThanACase(
      ReceiveInstructionRequest receiveInstructionRequest,
      List<LabelData> labelDataList,
      DeliveryDocument deliveryDocument,
      int receiveQty) {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    SlotDetails slotDetails =
        Objects.nonNull(receiveInstructionRequest)
            ? receiveInstructionRequest.getSlotDetails()
            : null;
    boolean isAutomationSlottingForDAConveyableItem =
        isAutomationSlottingSelectedForDAConveyableItem(slotDetails);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    boolean isLabelDataReQueryWithFacilityForBCConveyable =
        isLabelDataReQueryWithFacilityForBCConveyable(deliveryDocument, labelDataList);
    if (isLabelDataReQueryWithFacilityForBCConveyable) {
      List<LabelData> labelDataListByFacility =
          labelDataService.fetchLabelDataByPoAndItemNumberAndStoreNumber(
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getItemNbr(),
              TenantContext.getFacilityNum(),
              LabelInstructionStatus.AVAILABLE.name(),
              RdcConstants.CONTAINER_COUNT_ONE,
              TenantContext.getFacilityNum(),
              TenantContext.getFacilityCountryCode());
      if (CollectionUtils.isEmpty(labelDataListByFacility)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.NO_ALLOCATIONS_FOR_DA_FREIGHT,
            ReceivingException.NO_ALLOCATIONS_FOR_DA_FREIGHT);
      }
      labelDataList.clear();
      labelDataList.addAll(labelDataListByFacility);
    }
    for (LabelData labelData : labelDataList) {
      if (Objects.nonNull(labelData.getAllocation())
          && !CollectionUtils.isEmpty(labelData.getAllocation().getChildContainers())) {
        List<InstructionDownloadChildContainerDTO> childLabelDataContainers =
            labelData.getAllocation().getChildContainers().subList(0, receiveQty);
        // Break pack Conveyable (BC)
        childLabelDataContainers.forEach(
            childContainer -> {
              ReceivedContainer childReceivedContainer =
                  buildReceivedContainer(
                      labelData,
                      deliveryDocument,
                      deliveryDocumentLine,
                      childContainer.getTrackingId(),
                      null,
                      childContainer.getDistributions(),
                      childContainer.getCtrDestination(),
                      isAutomationSlottingForDAConveyableItem,
                      false,
                      false);
              receivedContainers.add(childReceivedContainer);
            });
      } else {
        // Break pack Convey Picks (BM) , Break Pack Non Con (BN)
        List<InstructionDownloadDistributionsDTO> downloadDistributionsDTOS =
            labelData.getAllocation().getContainer().getDistributions();
        receivedContainers.add(
            buildReceivedContainer(
                labelData,
                deliveryDocument,
                deliveryDocumentLine,
                labelData.getTrackingId(),
                null,
                downloadDistributionsDTOS,
                labelData.getAllocation().getContainer().getFinalDestination(),
                isAutomationSlottingForDAConveyableItem,
                false,
                false));
      }
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
   * @param isAutomationSlottingForDAConveyableItem
   * @param isPalletPullByStore
   * @param isDaSlotting
   * @return
   */
  protected ReceivedContainer buildReceivedContainer(
      LabelData labelData,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      String labelTrackingId,
      String parentLabelTrackingId,
      List<InstructionDownloadDistributionsDTO> distributions,
      Facility facility,
      boolean isAutomationSlottingForDAConveyableItem,
      boolean isPalletPullByStore,
      boolean isDaSlotting) {
    boolean isChildContainer = false;
    String fulfillmentMethod = null;
    ReceivedContainer receivedContainer = new ReceivedContainer();
    String itemHandlingCode = deliveryDocumentLine.getAdditionalInfo().getHandlingCode();
    if (Objects.nonNull(parentLabelTrackingId)) {
      // child containers
      receivedContainer.setParentTrackingId(parentLabelTrackingId);
      setFulfillmentMethodInReceivedContainer(
          isAutomationSlottingForDAConveyableItem,
          isPalletPullByStore,
          isDaSlotting,
          receivedContainer);
      isChildContainer = true;
    } else {
      // case pack or warehouse pack containers
      receivedContainer.setFulfillmentMethod(FulfillmentMethodType.CASE_PACK_RECEIVING.getType());
      receivedContainer.setSorterDivertRequired(Boolean.TRUE);
    }
    RdcUtils.buildCommonReceivedContainerDetails(
        labelTrackingId, receivedContainer, deliveryDocument);
    InstructionDownloadItemDTO instructionDownloadItemDTO = distributions.get(0).getItem();

    // ToDo: refactor once the distributions info contract is cleaned up on orders end
    receivedContainer.setAisle(instructionDownloadItemDTO.getAisle());
    if (StringUtils.isNotBlank(instructionDownloadItemDTO.getPrintBatch())) {
      receivedContainer.setBatch(Integer.parseInt(instructionDownloadItemDTO.getPrintBatch()));
    }
    if (StringUtils.isNotBlank(instructionDownloadItemDTO.getPickBatch())) {
      receivedContainer.setPickBatch(Integer.parseInt(instructionDownloadItemDTO.getPickBatch()));
    }
    String storeZone =
        StringUtils.isNotBlank(instructionDownloadItemDTO.getStoreZone())
            ? instructionDownloadItemDTO.getStoreZone()
            : instructionDownloadItemDTO.getZone();
    receivedContainer.setStorezone(storeZone);

    receivedContainer.setShippingLane(instructionDownloadItemDTO.getShipLaneNumber());
    receivedContainer.setDivision(instructionDownloadItemDTO.getDivisionNumber());
    receivedContainer.setStoreAlignment(instructionDownloadItemDTO.getStoreAlignment());
    String labelPackTypeHandlingCode = getLabelPackTypeAndHandlingCode(instructionDownloadItemDTO);
    if (StringUtils.isNotBlank(labelPackTypeHandlingCode)) {
      receivedContainer.setLabelPackTypeHandlingCode(labelPackTypeHandlingCode);
    }

    receivedContainer.setPack(distributions.get(0).getAllocQty());
    /** added for offline flow (Possible values : XDK1, XDK2) */
    if (OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
      receivedContainer.setPalletId(deliveryDocument.getPalletId());
      setLabelTypeForOffline(labelData, deliveryDocument, isChildContainer, receivedContainer);
    } else {
      buildLabelType(
          isChildContainer,
          isPalletPullByStore,
          isDaSlotting,
          receivedContainer,
          itemHandlingCode,
          isAutomationSlottingForDAConveyableItem);
    }

    // ToDo: Store Aisle locations
    // receivedContainer.setPri_loc();
    // receivedContainer.setSec_loc();
    // receivedContainer.setTer_loc();

    receivedContainer.setRoutingLabel(
        isRoutingLabel(
            deliveryDocumentLine.getAdditionalInfo().getHandlingCode(),
            receivedContainer.getLabelType(),
            receivedContainer.getStoreAlignment()));
    prepareDestination(
        isChildContainer,
        deliveryDocumentLine,
        labelData,
        receivedContainer,
        facility,
        isAutomationSlottingForDAConveyableItem);
    receivedContainer.setDistributions(prepareDistributions(distributions, facility.getBuNumber()));
    if (Objects.nonNull(labelData.getAsnNumber())) {
      receivedContainer.setAsnNumber(labelData.getAsnNumber());
    }
    if (Objects.nonNull(facility.getDestType())) {
      receivedContainer.setDestType(facility.getDestType());
    }
    return receivedContainer;
  }

  /**
   * Set Fulfillment Method
   *
   * @param isAutomationSlottingForDAConveyableItem
   * @param isPalletPullByStore
   * @param isDaSlotting
   * @param receivedContainer
   */
  private static void setFulfillmentMethodInReceivedContainer(
      boolean isAutomationSlottingForDAConveyableItem,
      boolean isPalletPullByStore,
      boolean isDaSlotting,
      ReceivedContainer receivedContainer) {
    String fulfillmentMethod;
    if (isDaSlotting && !isAutomationSlottingForDAConveyableItem) {
      fulfillmentMethod = FulfillmentMethodType.DA_PALLET_SLOTTING.getType();
    } else if (isDaSlotting && isAutomationSlottingForDAConveyableItem) {
      fulfillmentMethod = FulfillmentMethodType.DA_AUTOMATION_PALLET_SLOTTING.getType();
    } else if (isPalletPullByStore) {
      fulfillmentMethod = FulfillmentMethodType.PALLET_PULL_RECEIVING_CHILD.getType();
    } else {
      fulfillmentMethod = FulfillmentMethodType.BREAK_PACK_PUT_RECEIVING.getType();
    }
    receivedContainer.setFulfillmentMethod(fulfillmentMethod);
  }

  /**
   * Set LabelType, ChannelMethod, SorterDivertRequired, InventoryLabelType for Offline receiving
   *
   * @param labelData
   * @param deliveryDocument
   * @param isChildContainer
   * @param receivedContainer
   */
  private void setLabelTypeForOffline(
      LabelData labelData,
      DeliveryDocument deliveryDocument,
      boolean isChildContainer,
      ReceivedContainer receivedContainer) {
    /**
     * if received containers is called for container having child, set label as XDK1 always else,
     * take value from label data itself - for WPM
     *
     * <p>For eg: WPM REPACK: all inner picks will be XDK1 WPM CP, CC/IMPORTS CP, CC/IMPORTS BP: all
     * will get it from labelData
     */
    if (deliveryDocument.getOriginFacilityNum() != null
        && (rdcManagedConfig
                .getWpmSites()
                .contains(deliveryDocument.getOriginFacilityNum().toString())
            || rdcManagedConfig
                .getRdc2rdcSites()
                .contains(deliveryDocument.getOriginFacilityNum().toString()))
        && isChildContainer) {
      receivedContainer.setLabelType(ReceivingConstants.XDK1);
    } else {
      receivedContainer.setLabelType(labelData.getLabel());
    }
    receivedContainer.setChannelMethod(labelData.getChannelMethod());
    receivedContainer.setSorterDivertRequired(true);
    receivedContainer.setInventoryLabelType(
        ReceivingConstants.XDK1.equalsIgnoreCase(deliveryDocument.getLabelType())
            ? InventoryLabelType.XDK1
            : InventoryLabelType.XDK2);
    logger.info(
        "Inventory label type is '{}' for trackingId '{}' ",
        receivedContainer.getInventoryLabelType(),
        receivedContainer.getParentTrackingId());
  }

  private String getLabelPackTypeAndHandlingCode(
      InstructionDownloadItemDTO instructionDownloadItemDTO) {
    String itemPackTypAndHandlingCode = null;
    String handlingMethodCode = null;
    if (StringUtils.isNotBlank(instructionDownloadItemDTO.getPackType())
        && StringUtils.isNotBlank(instructionDownloadItemDTO.getItemHandlingCode())) {
      String packType =
          DA_LABEL_INSTRUCTION_PACK_TYPE_MAP.get(instructionDownloadItemDTO.getPackType());
      /*in case of X block handling codes from orders,we can default to Conveyable (CC, BC) as the label generation
      is same as Conveyable handling method code*/
      if (Arrays.asList(X_BLOCK_ITEM_HANDLING_CODES)
          .contains(instructionDownloadItemDTO.getItemHandlingCode())) {
        handlingMethodCode = CONVEYABLE_HANDLING_CODE;
      } else if (!CollectionUtils.isEmpty(rdcManagedConfig.getAtlasDaNonSupportedHandlingCodes())
          && rdcManagedConfig
              .getAtlasDaNonSupportedHandlingCodes()
              .contains(instructionDownloadItemDTO.getItemHandlingCode())) {
        /*Override non-supported handling Code to Non-Conveyable as N*/
        handlingMethodCode = ATLAS_DA_NON_CONVEYABLE_HANDLING_CODE;
      } else {
        handlingMethodCode = instructionDownloadItemDTO.getItemHandlingCode();
      }
      itemPackTypAndHandlingCode = StringUtils.join(packType, handlingMethodCode);
    }
    return itemPackTypAndHandlingCode;
  }

  /**
   * @param labelTrackingId
   * @param receivedContainer
   * @param deliveryDocument
   */
  private void buildCommonReceivedContainerDetails(
      String labelTrackingId,
      ReceivedContainer receivedContainer,
      DeliveryDocument deliveryDocument) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    receivedContainer.setLabelTrackingId(labelTrackingId);
    receivedContainer.setPoNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    receivedContainer.setPoLine(deliveryDocumentLine.getPurchaseReferenceLineNumber());
    receivedContainer.setPocode(deliveryDocument.getPoTypeCode());
    receivedContainer.setPoevent(deliveryDocumentLine.getEvent());
    String departmentNumber =
        StringUtils.isNotBlank(deliveryDocumentLine.getDepartment())
            ? deliveryDocumentLine.getDepartment()
            : StringUtils.isNotBlank(deliveryDocument.getDeptNumber())
                ? deliveryDocument.getDeptNumber()
                : StringUtils.EMPTY;
    if (StringUtils.isNotBlank(departmentNumber)) {
      receivedContainer.setDepartment(Integer.parseInt(deliveryDocumentLine.getDepartment()));
    }
  }

  /**
   * This method prepares destination information about the StoreZone / dcZone & slot details for
   * the containers. if its child container then the slot would be PUT, else this is Shipping /
   * Routing containers
   *
   * @param isChildContainer
   * @param deliveryDocumentLine
   * @param labelData
   * @param receivedContainer
   * @param facility
   */
  private void prepareDestination(
      boolean isChildContainer,
      DeliveryDocumentLine deliveryDocumentLine,
      LabelData labelData,
      ReceivedContainer receivedContainer,
      Facility facility,
      boolean isAutomationSlottingForDAConveyableItem) {
    List<Destination> destinations = new ArrayList<>();
    if (isChildContainer) {
      String dcZone = null;
      if (facility instanceof InstructionDownloadCtrDestinationDTO) {
        InstructionDownloadCtrDestinationDTO destinationDTO =
            (InstructionDownloadCtrDestinationDTO) facility;
        if (Objects.nonNull(destinationDTO)) {
          dcZone = destinationDTO.getDcZone();
        }
      } else {
        dcZone =
            labelData
                .getAllocation()
                .getContainer()
                .getDistributions()
                .get(0)
                .getItem()
                .getDcZone();
      }
      destinations.add(
          buildDestination(
              deliveryDocumentLine,
              dcZone,
              facility.getBuNumber(),
              receivedContainer.getLabelType(),
              isAutomationSlottingForDAConveyableItem,
              false));
    } else {
      destinations.add(
          buildDestination(
              deliveryDocumentLine,
              receivedContainer.getStorezone(),
              facility.getBuNumber(),
              receivedContainer.getLabelType(),
              isAutomationSlottingForDAConveyableItem,
              false));
    }
    receivedContainer.setDestinations(destinations);
  }

  /**
   * This method determines labelType for the container. if pallet pull by store then its DA Full
   * case for child containers. If its PUT child containers then its Break Pack inner picks else its
   * Shipping / Routing labels / Shipping
   *
   * @param isChildContainer
   * @param isPalletPullByStore
   * @param isDaSlotting
   * @param receivedContainer
   * @param handlingCode
   * @param isAutomationSlottingForDAConveyableItem
   */
  private void buildLabelType(
      boolean isChildContainer,
      boolean isPalletPullByStore,
      boolean isDaSlotting,
      ReceivedContainer receivedContainer,
      String handlingCode,
      boolean isAutomationSlottingForDAConveyableItem) {
    boolean isNonConItemHandlingCode = isNonConveyableItem(handlingCode);
    String labelType = null;
    InventoryLabelType inventoryLabelType = null;
    if (isDaSlotting && !isNonConItemHandlingCode && isAutomationSlottingForDAConveyableItem) {
      labelType = InventoryLabelType.DA_CON_AUTOMATION_SLOTTING.getType();
      inventoryLabelType = InventoryLabelType.DA_CON_AUTOMATION_SLOTTING;
    } else if (isDaSlotting
        && isNonConItemHandlingCode
        && !isAutomationSlottingForDAConveyableItem) {
      labelType = InventoryLabelType.DA_NON_CON_SLOTTING.getType();
      inventoryLabelType = InventoryLabelType.DA_NON_CON_SLOTTING;
    } else if (isDaSlotting
        && !isNonConItemHandlingCode
        && !isAutomationSlottingForDAConveyableItem) {
      labelType = InventoryLabelType.DA_CON_SLOTTING.getType();
      inventoryLabelType = InventoryLabelType.DA_CON_SLOTTING;
    } else if (isPalletPullByStore) {
      labelType = InventoryLabelType.R8000_DA_FULL_CASE.getType();
      inventoryLabelType = InventoryLabelType.R8000_DA_FULL_CASE;
    } else if (isChildContainer) {
      labelType = InventoryLabelType.DA_BREAK_PACK_INNER_PICK.getType();
      inventoryLabelType = InventoryLabelType.DA_BREAK_PACK_INNER_PICK;
    } else {
      labelType = InventoryLabelType.R8000_DA_FULL_CASE.getType();
      inventoryLabelType = InventoryLabelType.R8000_DA_FULL_CASE;
    }
    receivedContainer.setLabelType(labelType);
    receivedContainer.setInventoryLabelType(inventoryLabelType);
  }

  /**
   * @param deliveryDocumentLine
   * @param dcZone
   * @param storeNumber
   * @param labelType
   * @param isAutomationSlottingForDAConveyableItem
   * @param isPalletPullByStoreNumber
   * @return
   */
  private Destination buildDestination(
      DeliveryDocumentLine deliveryDocumentLine,
      String dcZone,
      String storeNumber,
      String labelType,
      boolean isAutomationSlottingForDAConveyableItem,
      boolean isPalletPullByStoreNumber) {
    Destination destination = new Destination();
    String handlingCode = deliveryDocumentLine.getAdditionalInfo().getHandlingCode();
    boolean isNonConItemHandlingCode = isNonConveyableItem(handlingCode);
    if (labelType.equals(InventoryLabelType.DA_BREAK_PACK_INNER_PICK.getType())
        || labelType.equals(InventoryLabelType.DA_BREAK_PACK_PUT_INDUCT.getType())) {
      if (Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())) {
        if (deliveryDocumentLine
            .getAdditionalInfo()
            .getPackTypeCode()
            .equals(RdcConstants.CASE_PACK_TYPE_CODE)) {
          destination.setSlot(RdcConstants.DA_P0900_SLOT);
        } else if (deliveryDocumentLine
            .getAdditionalInfo()
            .getPackTypeCode()
            .equals(RdcConstants.BREAK_PACK_TYPE_CODE)) {
          destination.setSlot(RdcConstants.DA_P1001_SLOT);
        }
      }
    } else if (isPalletPullByStoreNumber || isNonConItemHandlingCode) {
      destination.setSlot(RdcConstants.DA_R8001_SLOT);
    } else if (RdcConstants.OFFLINE_LABEL_TYPE.contains(labelType)) {
      destination.setSlot(RdcConstants.OFFLINE_SLOT);
    } else if (isAutomationSlottingForDAConveyableItem) {
      destination.setSlot(
          tenantSpecificConfigReader.getCcmValue(
              TenantContext.getFacilityNum(),
              ReceivingConstants.AUTOMATION_AUTO_SLOT_FOR_DA_CONVEYABLE_ITEM,
              EMPTY_STRING));
    } else {
      destination.setSlot(RdcConstants.DA_R8000_SLOT);
    }

    if (StringUtils.isNotBlank(dcZone) && NumberUtils.isParsable(dcZone)) {
      destination.setZone(dcZone);
    }
    if (Objects.nonNull(storeNumber)) {
      destination.setStore(storeNumber);
    }
    return destination;
  }

  /**
   * @param handlingCode
   * @return
   */
  private boolean isNonConveyableItem(String handlingCode) {
    return !CollectionUtils.isEmpty(
            rdcManagedConfig.getAtlasNonConveyableHandlingCodesForDaSlotting())
        ? rdcManagedConfig.getAtlasNonConveyableHandlingCodesForDaSlotting().contains(handlingCode)
        : RdcConstants.ATLAS_DA_NON_CON_HANDLING_CODES.contains(handlingCode);
  }
  /**
   * @param instructionRequest
   * @param receiveInstructionRequest
   * @param receivedContainers
   * @param deliveryDocument
   * @param httpHeaders
   * @return
   */
  private InstructionResponse processDAPalleContainers(
      InstructionRequest instructionRequest,
      ReceiveInstructionRequest receiveInstructionRequest,
      List<ReceivedContainer> receivedContainers,
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    Map<String, Integer> trackingIdQuantityMap = new HashMap<>();
    Map<String, Instruction> trackingIdInstructionMap = new HashMap<>();
    Map<ReceivedContainer, PrintJob> receivedContainerPrintJobMap = new HashMap<>();
    List<Instruction> updatedInstructions = new ArrayList<>();
    List<Container> containers = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();
    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    for (int i = 0; i < receivedContainers.size(); i++) {
      trackingIdQuantityMap.put(
          receivedContainers.get(i).getLabelTrackingId(),
          Objects.nonNull(receiveInstructionRequest.getPalletQuantities())
              ? receiveInstructionRequest.getPalletQuantities().get(i).getQuantity()
              : receiveInstructionRequest.getQuantity());
    }
    for (ReceivedContainer receivedContainer : receivedContainers) {
      Instruction instruction =
          createInstruction(
              instructionRequest,
              deliveryDocument,
              httpHeaders,
              deliveryDocumentLine,
              Boolean.TRUE);
      LinkedTreeMap<String, Object> moveTreeMap =
          rdcInstructionUtils.moveDetailsForInstruction(
              instructionRequest.getDoorNumber(), deliveryDocument, httpHeaders);
      instruction.setMove(moveTreeMap);
      trackingIdInstructionMap.put(receivedContainer.getLabelTrackingId(), instruction);
      PrintJob printJob =
          printJobService.createPrintJob(
              receiveInstructionRequest.getDeliveryNumber(),
              instruction.getId(),
              Collections.singleton((receivedContainer.getLabelTrackingId())),
              userId);
      receivedContainerPrintJobMap.put(receivedContainer, printJob);
    }
    LabelFormat labelFormat = rdcReceivingUtils.getLabelFormatForPallet(deliveryDocumentLine);
    Map<String, Object> printLabelData =
        LabelGenerator.generateDAPalletLabels(
            deliveryDocumentLine,
            trackingIdQuantityMap,
            receivedContainers,
            receivedContainerPrintJobMap,
            httpHeaders,
            ReceivingUtils.getDCDateTime(dcTimeZone),
            labelFormat);

    for (ReceivedContainer receivedContainer : receivedContainers) {
      Instruction instruction =
          trackingIdInstructionMap.get(receivedContainer.getLabelTrackingId());
      Container container = null;
      int quantity = trackingIdQuantityMap.get(receivedContainer.getLabelTrackingId());

      ContainerItem containerItem =
          rdcContainerUtils
              .buildContainerItem(
                  receivedContainer.getLabelTrackingId(),
                  deliveryDocument,
                  quantity,
                  receivedContainer.getDestType())
              .get(0);

      container =
          rdcContainerUtils.buildContainer(
              receiveInstructionRequest.getDoorNumber(),
              instruction.getId(),
              receiveInstructionRequest.getDeliveryNumber(),
              instructionRequest.getMessageId(),
              deliveryDocument,
              userId,
              receivedContainer,
              container,
              receiveInstructionRequest);

      containers.add(container);
      containerItems.add(containerItem);

      instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
      updateInstruction(instruction, receivedContainer, userId, quantity, printLabelData);
      updatedInstructions.add(instruction);
    }

    rdcReceivingUtils.persistReceivedContainerDetails(
        updatedInstructions,
        containers,
        containerItems,
        Collections.emptyList(),
        Collections.emptyList());

    // Complete problem tag if one exists for slotting a pallet
    if (StringUtils.isNotBlank(instructionRequest.getProblemTagId())) {
      logger.info(
          "Invoking completeProblem() for problemTagId: {}", instructionRequest.getProblemTagId());
      tenantSpecificConfigReader
          .getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.PROBLEM_SERVICE,
              ProblemService.class)
          .completeProblem(updatedInstructions.get(0));
    }

    for (ReceivedContainer receivedContainer : receivedContainers) {
      Instruction instruction =
          trackingIdInstructionMap.get(receivedContainer.getLabelTrackingId());
      int quantity = trackingIdQuantityMap.get(receivedContainer.getLabelTrackingId());
      TenantContext.get().setDaCaseReceivingPublishWFTCallStart(System.currentTimeMillis());
      rdcReceivingUtils.publishInstruction(
          instruction, deliveryDocumentLine, quantity, httpHeaders, false);
      TenantContext.get().setDaCaseReceivingPublishWFTCallEnd(System.currentTimeMillis());
    }
    Instruction instruction = updatedInstructions.isEmpty() ? null : updatedInstructions.get(0);
    return new InstructionResponseImplNew(
        instructionRequest.getDeliveryStatus(),
        Collections.singletonList(deliveryDocument),
        instruction,
        printLabelData);
  }

  private void updateInstruction(
      Instruction instruction,
      ReceivedContainer receivedContainer,
      String userId,
      Integer receivedQty,
      Map<String, Object> printLabelData) {
    LinkedTreeMap<String, Object> moveTreeMap = instruction.getMove();
    if (Objects.nonNull(moveTreeMap) && !moveTreeMap.isEmpty()) {
      moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_BY, userId);
      moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_ON, new Date());
      moveTreeMap.put(
          ReceivingConstants.MOVE_TO_LOCATION,
          receivedContainer.getDestinations().get(0).getSlot());
      moveTreeMap.put(
          ReceivingConstants.MOVE_CONTAINER_TAG, receivedContainer.getLabelTrackingId());
      MoveType moveType =
          MoveType.builder()
              .code(rdcManagedConfig.getMoveTypeCode())
              .desc(rdcManagedConfig.getMoveTypeDesc())
              .build();
      moveTreeMap.put(ReceivingConstants.MOVE_TYPE, moveType);
    }
    instruction.setMove(moveTreeMap);
    instruction.setReceivedQuantity(receivedQty);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());
    instruction.setContainer(
        rdcContainerUtils.getContainerDetails(
            receivedContainer.getLabelTrackingId(),
            printLabelData,
            ContainerType.CASE,
            RdcConstants.OUTBOUND_CHANNEL_METHOD_CROSSDOCK));
    instruction.setCreateUserId(userId);
    instruction.setCreateTs(new Date());
  }

  /**
   * Create instruction for DA Case Receiving. Do not persist instruction in DB for DA Qty
   * Receiving. Persist instruction in DB for WorkStation Receiving
   *
   * @param instructionRequest
   * @param deliveryDocument
   * @param httpHeaders
   * @param deliveryDocumentLine
   * @param persistInstruction
   * @return
   */
  private Instruction createInstruction(
      InstructionRequest instructionRequest,
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders,
      DeliveryDocumentLine deliveryDocumentLine,
      Boolean persistInstruction) {
    Instruction instruction =
        rdcReceivingUtils.populateInstructionFields(
            instructionRequest, deliveryDocumentLine, httpHeaders, deliveryDocument);

    // Received Qty details
    if (!RdcUtils.isWorkStationAndScanToPrintReceivingModeEnabled(
        instructionRequest.getFeatureType())) {
      Integer projectedReceiveQty = null;
      Integer daQtyReceiveMaxLimit = rdcManagedConfig.getDaQtyReceiveMaxLimit();
      if (StringUtils.isNotBlank(instructionRequest.getProblemTagId())) {
        instruction.setProblemTagId(instructionRequest.getProblemTagId());
        Integer openQty =
            Objects.nonNull(deliveryDocumentLine.getOpenQty())
                ? deliveryDocumentLine.getOpenQty()
                : 0;
        projectedReceiveQty = Math.min(openQty, daQtyReceiveMaxLimit);
        logger.info(
            "Projected Receive quantity for problemTagId:{} is {}",
            instruction.getProblemTagId(),
            projectedReceiveQty);
      } else {
        Integer totalReceivedQty =
            deliveryDocument.getDeliveryDocumentLines().get(0).getTotalReceivedQty();
        Integer maxReceiveQuantity =
            deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
        Integer quantityCanBeReceived = maxReceiveQuantity - totalReceivedQty;
        projectedReceiveQty = Math.min(quantityCanBeReceived, daQtyReceiveMaxLimit);
      }
      instruction.setProjectedReceiveQty(projectedReceiveQty);
      instruction.setInstructionMsg(RdcInstructionType.DA_QTY_RECEIVING.getInstructionMsg());
      instruction.setInstructionCode(RdcInstructionType.DA_QTY_RECEIVING.getInstructionCode());
      // Persist instruction only for scan to print and work station receiving capabilities
      if (!persistInstruction) {
        return instruction;
      }
    } else {
      if (StringUtils.isNotBlank(instructionRequest.getProblemTagId())) {
        instruction.setProblemTagId(instructionRequest.getProblemTagId());
      }
      instruction.setProjectedReceiveQty(RdcConstants.RDC_DA_CASE_RECEIVE_QTY);
      instruction.setInstructionMsg(
          RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionMsg());
      instruction.setInstructionCode(
          RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());
    }

    return instructionPersisterService.saveInstruction(instruction);
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

  private List<Distribution> prepareParentDistributions(
      List<Distribution> childContainerDistributions) {
    List<Distribution> distributions = new ArrayList<>();
    Distribution distribution = new Distribution();
    distribution.setDestNbr(TenantContext.getFacilityNum());
    distribution.setItem(childContainerDistributions.get(0).getItem());
    distributions.add(distribution);
    return distributions;
  }

  private List<Distribution> prepareParentDistributionsForPalletPull(
      ReceivedContainer childReceivedContainer, String storeNumber) {
    List<Distribution> distributions = new ArrayList<>();
    Distribution distribution = new Distribution();
    distribution.setDestNbr(Integer.parseInt(storeNumber));
    if (!CollectionUtils.isEmpty(childReceivedContainer.getDistributions())) {
      distribution.setItem(childReceivedContainer.getDistributions().get(0).getItem());
    }
    distributions.add(distribution);
    return distributions;
  }

  /**
   * This method checks for RTS Put Item handling code and returns a boolean
   *
   * @param deliveryDocumentLine
   */
  private boolean isRtsPutFlowEnabled(DeliveryDocumentLine deliveryDocumentLine) {
    String itemPackAndHandlingCode =
        deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode();
    return DA_CASE_PACK_NONCON_RTS_PUT_ITEM_HANDLING_CODE.equalsIgnoreCase(itemPackAndHandlingCode);
  }

  /**
   * This method provides received containers for Rts Put flow. Channel type header will be added in
   * the header to differentiate DA/SSTK to generate 25 or 18 digit LPNs respectively.
   *
   * @param receiveInstructionRequest
   * @param labelDataList
   * @param deliveryDocument
   * @param receivedContainers
   * @param httpHeaders
   */
  private List<ReceivedContainer> buildReceivedContainerForNonConRtsPut(
      ReceiveInstructionRequest receiveInstructionRequest,
      List<LabelData> labelDataList,
      DeliveryDocument deliveryDocument,
      List<ReceivedContainer> receivedContainers,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    String rtsPutLabelTrackingId =
        lpnCacheService.getLPNSBasedOnTenant(RdcConstants.CONTAINER_COUNT_ONE, httpHeaders).get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    // build child containers
    for (LabelData labelData : labelDataList) {
      ReceivedContainer childContainer =
          buildReceivedContainer(
              labelData,
              deliveryDocument,
              deliveryDocumentLine,
              labelData.getTrackingId(),
              rtsPutLabelTrackingId,
              labelData.getAllocation().getContainer().getDistributions(),
              labelData.getAllocation().getContainer().getFinalDestination(),
              false,
              false,
              false);
      receivedContainers.add(childContainer);
    }

    Integer receivedQtyForParentContainerInEaches =
        receivedContainers.stream().map(ReceivedContainer::getPack).reduce(0, Integer::sum);
    // build parent container
    ReceivedContainer parentReceivedContainer =
        buildParentContainerForPutLabels(
            rtsPutLabelTrackingId,
            deliveryDocument,
            receivedQtyForParentContainerInEaches,
            receivedContainers);
    if (Objects.nonNull(parentReceivedContainer)) {
      receivedContainers.add(parentReceivedContainer);
    }

    return receivedContainers;
  }

  /**
   * This method prepares parent & child containers for the Pallet Pull by selected store number.
   * Parent container is generated by LPN Generator.
   *
   * @param receiveInstructionRequest
   * @param labelDataList
   * @param deliveryDocument
   * @param receivedContainers
   * @param httpHeaders
   */
  private List<ReceivedContainer> buildContainersForPalletPullByStore(
      ReceiveInstructionRequest receiveInstructionRequest,
      List<LabelData> labelDataList,
      DeliveryDocument deliveryDocument,
      List<ReceivedContainer> receivedContainers,
      HttpHeaders httpHeaders,
      boolean isPalletPullByStore)
      throws ReceivingException {

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    String palletPullParentTrackingId =
        lpnCacheService.getLPNSBasedOnTenant(RdcConstants.CONTAINER_COUNT_ONE, httpHeaders).get(0);

    // build child containers
    for (LabelData labelData : labelDataList) {
      ReceivedContainer childContainer =
          buildReceivedContainer(
              labelData,
              deliveryDocument,
              deliveryDocumentLine,
              labelData.getTrackingId(),
              palletPullParentTrackingId,
              labelData.getAllocation().getContainer().getDistributions(),
              labelData.getAllocation().getContainer().getFinalDestination(),
              false,
              isPalletPullByStore,
              false);
      receivedContainers.add(childContainer);
    }

    // build parent container
    buildParentContainerForPalletPullByStore(
        receivedContainers, palletPullParentTrackingId, deliveryDocument);

    return receivedContainers;
  }

  /**
   * This method prepares parent & child containers for the Pallet Pull by selected store number.
   * Parent container is generated by LPN Generator.
   *
   * @param labelDataList
   * @param deliveryDocument
   * @param receivedContainers
   * @param httpHeaders
   */
  private void buildContainersForSlotting(
      ReceiveInstructionRequest receiveInstructionRequest,
      List<LabelData> labelDataList,
      DeliveryDocument deliveryDocument,
      List<ReceivedContainer> receivedContainers,
      HttpHeaders httpHeaders) {
    SlotDetails slotDetails =
        Objects.nonNull(receiveInstructionRequest)
            ? receiveInstructionRequest.getSlotDetails()
            : null;
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    boolean isAutomationSlottingForDAConveyableItem =
        isAutomationSlottingForDAConveyableItem(slotDetails);
    boolean isConventionalSlottingForDAConveyableItems =
        isDaConventionalSlottingForConveyableItem(
            slotDetails, deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
        false)) {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
          false)) {
        // validate lpn label format
        if (isConventionalSlottingForDAConveyableItems || isAutomationSlottingForDAConveyableItem) {
          validateLpnLabels(labelDataList);
        } else {
          // validate smart label format
          validateSmartLabelsForSlotting(labelDataList);
        }
      }
      String slottingPalletLabel =
          rdcLpnUtils.getLPNs(RdcConstants.CONTAINER_COUNT_ONE, httpHeaders).get(0);
      // build child containers
      for (LabelData labelData : labelDataList) {
        ReceivedContainer childContainer =
            buildReceivedContainer(
                labelData,
                deliveryDocument,
                deliveryDocumentLine,
                labelData.getTrackingId(),
                slottingPalletLabel,
                labelData.getAllocation().getContainer().getDistributions(),
                labelData.getAllocation().getContainer().getFinalDestination(),
                isAutomationSlottingForDAConveyableItem,
                false,
                true);
        receivedContainers.add(childContainer);
      }

      // build parent container
      buildParentContainerForSlotting(
          receiveInstructionRequest,
          receivedContainers,
          slottingPalletLabel,
          deliveryDocument,
          httpHeaders);
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.SMART_SLOTTING_NOT_ENABLED_FOR_ATLAS_DA_ITEMS,
          ReceivingException.SMART_SLOTTING_NOT_ENABLED_FOR_ATLAS_DA_ITEMS);
    }
  }

  /**
   * @param childReceivedContainers
   * @param parentTrackingId
   * @param deliveryDocument
   * @param receivedQtyForParentContainerInEaches
   */
  public ReceivedContainer buildParentContainerForPutLabels(
      String parentTrackingId,
      DeliveryDocument deliveryDocument,
      Integer receivedQtyForParentContainerInEaches,
      List<ReceivedContainer> childReceivedContainers) {
    // build parent container
    ReceivedContainer parentReceivedContainer = null;
    if (!CollectionUtils.isEmpty(childReceivedContainers)) {
      List<Destination> destinations = new ArrayList<>();
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      parentReceivedContainer = new ReceivedContainer();
      buildCommonReceivedContainerDetails(
          parentTrackingId, parentReceivedContainer, deliveryDocument);

      /** added for offline flow (Possible values : XDK1, XDK2) */
      if (OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
        parentReceivedContainer.setPalletId(deliveryDocument.getPalletId());
        parentReceivedContainer.setLabelType(deliveryDocument.getLabelType());
        parentReceivedContainer.setAsnNumber(deliveryDocument.getAsnNumber());
        parentReceivedContainer.setInventoryLabelType(
            ReceivingConstants.XDK1.equalsIgnoreCase(deliveryDocument.getLabelType())
                ? InventoryLabelType.XDK1
                : InventoryLabelType.XDK2);
        logger.info(
            "Inventory label type is '{}' for parentTrackingId '{}' ",
            parentReceivedContainer.getInventoryLabelType(),
            parentTrackingId);
      } else {
        parentReceivedContainer.setLabelType(InventoryLabelType.DA_BREAK_PACK_PUT_INDUCT.getType());
        parentReceivedContainer.setInventoryLabelType(InventoryLabelType.DA_BREAK_PACK_PUT_INDUCT);
      }
      parentReceivedContainer.setFulfillmentMethod(
          FulfillmentMethodType.BREAK_PACK_PUT_RECEIVING.getType());
      parentReceivedContainer.setPack(receivedQtyForParentContainerInEaches);
      parentReceivedContainer.setSorterDivertRequired(true);

      /** added for offline flow - WPM scenario */
      if (OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())
          && ReceivingConstants.REPACK.equals(deliveryDocument.getCtrType())
          && !CollectionUtils.isEmpty(childReceivedContainers.get(0).getDestinations())) {
        destinations.add(
            buildDestination(
                deliveryDocumentLine,
                null,
                childReceivedContainers.get(0).getDestinations().get(0).getStore(),
                parentReceivedContainer.getLabelType(),
                false,
                false));
      } else {
        destinations.add(
            buildDestination(
                deliveryDocumentLine,
                null,
                TenantContext.getFacilityNum().toString(),
                parentReceivedContainer.getLabelType(),
                false,
                false));
      }
      parentReceivedContainer.setDestinations(destinations);
      List<Destination> dcZoneDestinations =
          childReceivedContainers
              .stream()
              .map(childContainer -> childContainer.getDestinations().get(0))
              .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(dcZoneDestinations)) {
        String dcZoneRange = RdcUtils.getZoneRange(dcZoneDestinations);
        parentReceivedContainer.setDcZoneRange(dcZoneRange);
      }
      parentReceivedContainer.setDistributions(
          prepareParentDistributions(childReceivedContainers.get(0).getDistributions()));
    }
    return parentReceivedContainer;
  }

  /**
   * @param childContainers
   * @param parentTrackingId
   * @param deliveryDocument
   */
  private void buildParentContainerForPalletPullByStore(
      List<ReceivedContainer> childContainers,
      String parentTrackingId,
      DeliveryDocument deliveryDocument) {
    // build parent container
    if (!CollectionUtils.isEmpty(childContainers)) {
      List<Destination> destinations = new ArrayList<>();
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      ReceivedContainer parentReceivedContainer = new ReceivedContainer();
      buildCommonReceivedContainerDetails(
          parentTrackingId, parentReceivedContainer, deliveryDocument);
      parentReceivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
      parentReceivedContainer.setInventoryLabelType(InventoryLabelType.R8000_DA_FULL_CASE);
      parentReceivedContainer.setPalletPullByStore(true);
      parentReceivedContainer.setFulfillmentMethod(
          FulfillmentMethodType.PALLET_PULL_RECEIVING_PARENT.getType());
      Integer totalReceiveQtyInEaches =
          childContainers.stream().map(ReceivedContainer::getPack).reduce(0, Integer::sum);
      parentReceivedContainer.setPack(totalReceiveQtyInEaches);

      String storeNumber = childContainers.get(0).getDestinations().get(0).getStore();
      destinations.add(
          buildDestination(
              deliveryDocumentLine,
              null,
              storeNumber,
              parentReceivedContainer.getLabelType(),
              false,
              true));
      parentReceivedContainer.setDestinations(destinations);
      parentReceivedContainer.setDistributions(
          prepareParentDistributionsForPalletPull(childContainers.get(0), storeNumber));
      parentReceivedContainer.setDivision(childContainers.get(0).getDivision());
      parentReceivedContainer.setStorezone(childContainers.get(0).getStorezone());
      parentReceivedContainer.setAisle(childContainers.get(0).getAisle());
      parentReceivedContainer.setRoutingLabel(false);
      parentReceivedContainer.setShippingLane(childContainers.get(0).getShippingLane());
      parentReceivedContainer.setBatch(childContainers.get(0).getBatch());
      childContainers.add(parentReceivedContainer);
    }
  }

  /**
   * @param receiveInstructionRequest
   * @param childContainers
   * @param parentTrackingId
   * @param deliveryDocument
   * @param httpHeaders
   */
  private void buildParentContainerForSlotting(
      ReceiveInstructionRequest receiveInstructionRequest,
      List<ReceivedContainer> childContainers,
      String parentTrackingId,
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders) {

    SlotDetails slotDetails =
        Objects.nonNull(receiveInstructionRequest)
            ? receiveInstructionRequest.getSlotDetails()
            : null;
    String slot = null;
    ContainerTag containerTag = null;
    int slotSize = 0;
    if (Objects.nonNull(receiveInstructionRequest.getDeliveryDocuments())) {
      if (Objects.nonNull(deliveryDocument.getDeliveryDocumentLines())) {
        receiveInstructionRequest.setDeliveryDocumentLines(
            deliveryDocument.getDeliveryDocumentLines());
      }
    }
    if (isAutomationSlottingForDAConveyableItem(slotDetails)) {
      if (StringUtils.isNotBlank(slotDetails.getSlotType())
          && StringUtils.equalsIgnoreCase(slotDetails.getSlotType(), SLOT_TYPE_AUTOMATION)) {
        slot =
            tenantSpecificConfigReader.getCcmValue(
                TenantContext.getFacilityNum(),
                ReceivingConstants.AUTOMATION_AUTO_SLOT_FOR_DA_CONVEYABLE_ITEM,
                EMPTY_STRING);
      } else {
        slot = slotDetails.getSlot();
      }
      containerTag = new ContainerTag();
      containerTag.setTag(CONTAINER_TAG_DA_CON_AUTOMATION_PALLET_SLOTTING);
      containerTag.setAction(CONTAINER_SET);
    } else {
      TenantContext.get().setFetchSlotFromSmartSlottingStart(System.currentTimeMillis());
      SlottingPalletResponse slottingPalletResponse =
          rdcSlottingUtils.receiveContainers(
              receiveInstructionRequest, parentTrackingId, httpHeaders, null);
      TenantContext.get().setFetchSlotFromSmartSlottingEnd(System.currentTimeMillis());
      if (Objects.nonNull(slottingPalletResponse)
          && !CollectionUtils.isEmpty(slottingPalletResponse.getLocations())) {
        slot = slottingPalletResponse.getLocations().get(0).getLocation();
        slotSize = (int) slottingPalletResponse.getLocations().get(0).getLocationSize();
      }
    }

    // build parent container
    if (!CollectionUtils.isEmpty(childContainers)) {
      String itemHandlingCode =
          deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().getHandlingCode();
      List<Destination> destinations = new ArrayList<>();
      ReceivedContainer parentReceivedContainer = new ReceivedContainer();
      buildCommonReceivedContainerDetails(
          parentTrackingId, parentReceivedContainer, deliveryDocument);
      String labelType = null;
      InventoryLabelType inventoryLabelType;
      if (!isNonConveyableItem(itemHandlingCode)
          && isAutomationSlottingForDAConveyableItem(slotDetails)) {
        labelType = InventoryLabelType.DA_CON_AUTOMATION_SLOTTING.getType();
        inventoryLabelType = InventoryLabelType.DA_CON_AUTOMATION_SLOTTING;
        parentReceivedContainer.setFulfillmentMethod(
            FulfillmentMethodType.DA_AUTOMATION_PALLET_SLOTTING.getType());
      } else if (isNonConveyableItem(itemHandlingCode)
          && !isAutomationSlottingForDAConveyableItem(slotDetails)) {
        labelType = InventoryLabelType.DA_NON_CON_SLOTTING.getType();
        inventoryLabelType = InventoryLabelType.DA_NON_CON_SLOTTING;
        parentReceivedContainer.setFulfillmentMethod(
            FulfillmentMethodType.DA_PALLET_SLOTTING.getType());
      } else {
        labelType = InventoryLabelType.DA_CON_SLOTTING.getType();
        inventoryLabelType = InventoryLabelType.DA_CON_SLOTTING;
        parentReceivedContainer.setFulfillmentMethod(
            FulfillmentMethodType.DA_PALLET_SLOTTING.getType());
      }
      if (Objects.nonNull(containerTag)) {
        parentReceivedContainer.setContainerTags(Collections.singletonList(containerTag));
      }
      parentReceivedContainer.setLabelType(labelType);
      parentReceivedContainer.setInventoryLabelType(inventoryLabelType);
      Integer totalReceiveQtyInEaches =
          childContainers.stream().map(ReceivedContainer::getPack).reduce(0, Integer::sum);
      parentReceivedContainer.setPack(totalReceiveQtyInEaches);

      Destination destination = new Destination();
      destination.setSlot(slot);
      destination.setSlot_size(slotSize);
      destinations.add(destination);
      parentReceivedContainer.setDestinations(destinations);
      if (isAutomationSlottingForDAConveyableItem(slotDetails)) {
        parentReceivedContainer.setRoutingLabel(true);
      } else {
        parentReceivedContainer.setRoutingLabel(false);
      }
      childContainers.add(parentReceivedContainer);
    }
  }

  /**
   * This method will validate if the given labelType/ handlingCode / storeAlignment is eligible to
   * generate putaway requests to Hawkeye. AsrsAlignment could be SYM2, SYM2_5. Label type is
   * eligible only for DA Full Case (R8000) & the item handling codes should be in C, I , J. The
   * following combinations can have Routing label with R8000 labels (CC, CI, CJ, BC)
   *
   * @param handlingCode
   * @param labelType
   * @param storeAlignment
   * @return
   */
  private boolean isRoutingLabel(String handlingCode, String labelType, String storeAlignment) {
    List<String> asrsAlignmentValues = appConfig.getValidSymAsrsAlignmentValues();
    List<String> symEligibleHandlingCodesForRoutingLabel =
        rdcManagedConfig.getSymEligibleHandlingCodesForRoutingLabel();
    return Objects.nonNull(labelType)
        && Objects.nonNull(storeAlignment)
        && !CollectionUtils.isEmpty(asrsAlignmentValues)
        && !CollectionUtils.isEmpty(symEligibleHandlingCodesForRoutingLabel)
        && labelType.equals(InventoryLabelType.R8000_DA_FULL_CASE.getType())
        && asrsAlignmentValues.contains(storeAlignment)
        && symEligibleHandlingCodesForRoutingLabel.contains(handlingCode);
  }

  /**
   * Filter ReceivingMethod
   *
   * @param deliveryDocument
   * @param receiveInstructionRequest
   * @return
   */
  private ReceivingMethod getReceivingMethod(
      DeliveryDocument deliveryDocument, ReceiveInstructionRequest receiveInstructionRequest) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    boolean isAtlasConvertedItem = deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem();
    boolean isAtlasDaSlottingRequest =
        isAtlasConvertedItem && isSlottingPayLoad(receiveInstructionRequest);
    boolean isLessThanCase =
        Objects.nonNull(receiveInstructionRequest)
            && Boolean.TRUE.equals(receiveInstructionRequest.getIsLessThanCase());
    boolean isPalletPullByStore =
        Objects.nonNull(receiveInstructionRequest)
            && Objects.nonNull(receiveInstructionRequest.getStoreNumber());
    boolean isNonConRtsPut = isRtsPutFlowEnabled(deliveryDocumentLine);
    ReceivingMethod receivingMethod;
    if (isAtlasDaSlottingRequest) {
      receivingMethod = SLOTTING;
    } else if (isLessThanCase) {
      receivingMethod = ReceivingMethod.LESS_THAN_CASE;
    } else if (isPalletPullByStore) {
      receivingMethod = ReceivingMethod.PALLET_PULL;
    } else if (isNonConRtsPut) {
      receivingMethod = ReceivingMethod.NON_CON_RTS_PUT;
    } else {
      receivingMethod = ReceivingMethod.DEFAULT;
    }
    return receivingMethod;
  }

  /**
   * Validate child container exist or not for BC Conveyable
   *
   * @param deliveryDocument
   * @param labelDataList
   * @return
   */
  private boolean isLabelDataReQueryWithFacilityForBCConveyable(
      DeliveryDocument deliveryDocument, List<LabelData> labelDataList) {
    boolean isChildContainersExist = false;
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    String itemPackAndHandlingCode =
        deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode();
    if (StringUtils.equalsIgnoreCase(
        itemPackAndHandlingCode, DA_BREAK_PACK_CONVEYABLE_ITEM_HANDLING_CODE)) {
      isChildContainersExist =
          !CollectionUtils.isEmpty(labelDataList)
              && labelDataList.size() == 1
              && CollectionUtils.isEmpty(labelDataList.get(0).getAllocation().getChildContainers());
      if (isChildContainersExist) {
        logger.info("Re querying label data with facility number");
      }
    }
    return isChildContainersExist;
  }

  /**
   * Publish label status void to Hawkeye for SYM ineligible handling codes
   *
   * @param labelDataList
   * @param isAtlasConvertedItem
   * @param httpHeaders
   * @param deliveryDocumentLine
   */
  public void updateLabelStatusVoidToHawkeye(
      List<LabelData> labelDataList,
      boolean isAtlasConvertedItem,
      HttpHeaders httpHeaders,
      DeliveryDocumentLine deliveryDocumentLine) {
    if (isVoidLabelsForCasePackSymIneligible()
        && isAtlasConvertedItem
        && isHawkeyeIntegrationEnabledForManualReceiving()
        && (rdcManagedConfig
            .getCasePackSymIneligibleHandlingCodes()
            .contains(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode()))) {
      List<String> trackingIdList =
          labelDataList.stream().map(LabelData::getTrackingId).collect(Collectors.toList());
      rdcAsyncUtils.updateLabelStatusVoidToHawkeye(trackingIdList, httpHeaders);
    }
  }

  /**
   * Validate lpn formats for different handling codes
   *
   * @param receivedContainers
   * @param deliveryDocument
   * @return
   */
  private void validateLabelFormatByHandlingCodes(
      List<ReceivedContainer> receivedContainers, DeliveryDocument deliveryDocument) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    String itemPackAndHandlingCode =
        deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode();
    List<ReceivedContainer> parentReceivedContainers =
        receivedContainers
            .stream()
            .filter(
                receivedContainer -> StringUtils.isBlank(receivedContainer.getParentTrackingId()))
            .collect(Collectors.toList());
    List<ReceivedContainer> childReceivedContainers =
        receivedContainers
            .stream()
            .filter(
                receivedContainer ->
                    StringUtils.isNotBlank(receivedContainer.getParentTrackingId()))
            .collect(Collectors.toList());

    switch (itemPackAndHandlingCode) {
      case DA_CASE_PACK_NONCON_RTS_PUT_ITEM_HANDLING_CODE:
        if (CollectionUtils.isEmpty(parentReceivedContainers)
            || CollectionUtils.isEmpty(childReceivedContainers)) {
          logger.error(
              String.format(
                  RdcConstants.INVALID_CONTAINER_INFO, deliveryDocumentLine.getItemNbr()));
          throw new ReceivingBadDataException(
              String.format(ExceptionCodes.INVALID_LBL_FORMAT),
              String.format(
                  RdcConstants.INVALID_CONTAINER_INFO,
                  deliveryDocumentLine.getItemNbr().toString()),
              deliveryDocumentLine.getItemNbr().toString());
        }
        validateSmartLabelFormatForContainers(
            childReceivedContainers, deliveryDocumentLine.getItemNbr());
        break;
      case DA_CASE_PACK_NON_CON_ITEM_HANDLING_CODE:
      case DA_CASE_PACK_NON_CON_VOICE_PICK_ITEM_HANDLING_CODE:
      case DA_BREAK_PACK_NON_CON_VOICE_PICK_ITEM_HANDLING_CODE:
      case DA_BREAK_PACK_NON_CON_ITEM_HANDLING_CODE:
        validateSmartLabelFormatForContainers(
            childReceivedContainers, deliveryDocumentLine.getItemNbr());
        break;
      case DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE:
        validateLpnFormatForContainers(receivedContainers, deliveryDocumentLine.getItemNbr());
        break;
    }
    validateLabelFormatByPackAndHandlingCode(
        itemPackAndHandlingCode,
        deliveryDocumentLine,
        parentReceivedContainers,
        childReceivedContainers);
  }

  /**
   * This method validates the item packAndHandlin code combinations matches against with the pack
   * And Handling codes being used upon label generation on the orders end. This is to make sure we
   * use the right labels generated against the item labels generated during label generation and
   * receiving. The combinations interested are CB, BC, BM, CN, BN as they have their unique label
   * formats (LPN vs Smart Label)
   *
   * @param itemPackAndHandlingCode
   * @param deliveryDocumentLine
   * @param parentReceivedContainers
   * @param childReceivedContainers
   */
  private void validateLabelFormatByPackAndHandlingCode(
      String itemPackAndHandlingCode,
      DeliveryDocumentLine deliveryDocumentLine,
      List<ReceivedContainer> parentReceivedContainers,
      List<ReceivedContainer> childReceivedContainers) {
    boolean isInterchangeableHandlingCode = false;
    if (!CollectionUtils.isEmpty(
            rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        && rdcManagedConfig
            .getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes()
            .contains(itemPackAndHandlingCode)) {
      // DA conveyable interchangeable itemPackAndHandlingCodes validation
      isInterchangeableHandlingCode =
          validateLabelFormatForInterchangeableHandlingCode(
              itemPackAndHandlingCode,
              parentReceivedContainers,
              childReceivedContainers,
              rdcManagedConfig.getAtlasDaBreakPackInterchangeableHandlingCodes());
      if (isInterchangeableHandlingCode) {
        return;
      }
      // DA non-conveyable interchangeable itemPackAndHandlingCodes validation
      isInterchangeableHandlingCode =
          validateLabelFormatForInterchangeableHandlingCode(
              itemPackAndHandlingCode,
              parentReceivedContainers,
              childReceivedContainers,
              rdcManagedConfig.getAtlasDaNonConInterchangeableHandlingCodes());
      if (isInterchangeableHandlingCode) {
        return;
      }
      parentReceivedContainers =
          parentReceivedContainers
              .stream()
              .filter(
                  parentReceivedContainer ->
                      StringUtils.isNotBlank(parentReceivedContainer.getLabelPackTypeHandlingCode())
                          && parentReceivedContainer
                              .getLabelPackTypeHandlingCode()
                              .equals(itemPackAndHandlingCode))
              .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(childReceivedContainers)) {
        childReceivedContainers =
            childReceivedContainers
                .stream()
                .filter(
                    chilsReceivedContainer ->
                        StringUtils.isNotBlank(
                                chilsReceivedContainer.getLabelPackTypeHandlingCode())
                            && chilsReceivedContainer
                                .getLabelPackTypeHandlingCode()
                                .equals(itemPackAndHandlingCode))
                .collect(Collectors.toList());
      }
      boolean isValidLabelType =
          !CollectionUtils.isEmpty(parentReceivedContainers)
              || !CollectionUtils.isEmpty(childReceivedContainers);
      if (!isValidLabelType) {
        throw new ReceivingBadDataException(
            String.format(ExceptionCodes.INVALID_LBL_FORMAT),
            String.format(RdcConstants.INVALID_CONTAINER_INFO, deliveryDocumentLine.getItemNbr()),
            deliveryDocumentLine.getItemNbr().toString());
      }
    }
  }

  /**
   * This method validates the item packAnd Handling code combinations matches against with the pack
   * And Handling codes being used upon label generation on the orders end for CV, CN, CL, BC, BJ,
   * BI. For these combinations we are allowing user to receive even if they are changed to one
   * another in item management.
   *
   * @param itemPackAndHandlingCode
   * @param parentReceivedContainers
   * @param childReceivedContainers
   */
  private boolean validateLabelFormatForInterchangeableHandlingCode(
      String itemPackAndHandlingCode,
      List<ReceivedContainer> parentReceivedContainers,
      List<ReceivedContainer> childReceivedContainers,
      List<String> atlasDaInterchangeableHandlingCodes) {
    List<ReceivedContainer> filteredChildReceivedContainers = new ArrayList<>();
    if (!CollectionUtils.isEmpty(atlasDaInterchangeableHandlingCodes)
        && atlasDaInterchangeableHandlingCodes.contains(itemPackAndHandlingCode)) {
      List<ReceivedContainer> filteredParentReceivedContainers =
          parentReceivedContainers
              .stream()
              .filter(
                  parentReceivedContainer ->
                      StringUtils.isNotBlank(parentReceivedContainer.getLabelPackTypeHandlingCode())
                          && atlasDaInterchangeableHandlingCodes.contains(
                              parentReceivedContainer.getLabelPackTypeHandlingCode())
                          && !parentReceivedContainer
                              .getLabelPackTypeHandlingCode()
                              .equals(itemPackAndHandlingCode))
              .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(childReceivedContainers)) {
        filteredChildReceivedContainers =
            childReceivedContainers
                .stream()
                .filter(
                    childReceivedContainer ->
                        StringUtils.isNotBlank(
                                childReceivedContainer.getLabelPackTypeHandlingCode())
                            && atlasDaInterchangeableHandlingCodes.contains(
                                childReceivedContainer.getLabelPackTypeHandlingCode())
                            && !childReceivedContainer
                                .getLabelPackTypeHandlingCode()
                                .equals(itemPackAndHandlingCode))
                .collect(Collectors.toList());
      }
      if (!CollectionUtils.isEmpty(filteredParentReceivedContainers)
          || !CollectionUtils.isEmpty(filteredChildReceivedContainers)) {
        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }

  /**
   * Validate smart lpn formats
   *
   * @param receivedContainers
   * @param itemNumber
   * @return
   */
  private void validateSmartLabelFormatForContainers(
      List<ReceivedContainer> receivedContainers, Long itemNumber) {
    List<String> labelTrackingIds =
        receivedContainers
            .stream()
            .map(ReceivedContainer::getLabelTrackingId)
            .collect(Collectors.toList());
    labelTrackingIds.forEach(
        trackingId -> {
          boolean isValidLabelFormat = ReceivingUtils.isValidSmartLabelFormat(trackingId);
          if (!isValidLabelFormat) {
            logger.error(
                "label={} is invalid for pattern={}", trackingId, SMART_LABEL_REGEX_PATTERN);
            throw new ReceivingBadDataException(
                String.format(ExceptionCodes.INVALID_LBL_FORMAT),
                String.format(RdcConstants.INVALID_CONTAINER_INFO, itemNumber.toString()),
                itemNumber.toString());
          }
        });
  }

  /**
   * Validate smart labels for slotting
   *
   * @param labelDataList
   * @return
   */
  private void validateSmartLabelsForSlotting(List<LabelData> labelDataList) {
    for (LabelData labelData : labelDataList) {
      boolean isValidLabelFormat =
          ReceivingUtils.isValidSmartLabelFormat(labelData.getTrackingId());
      if (!isValidLabelFormat) {
        logger.error(
            "label={} is invalid for pattern={}",
            labelData.getTrackingId(),
            SMART_LABEL_REGEX_PATTERN);
        throw new ReceivingBadDataException(
            String.format(ExceptionCodes.INVALID_LBL_FORMAT),
            String.format(
                RdcConstants.INVALID_CONTAINER_INFO, labelData.getItemNumber().toString()),
            labelData.getItemNumber().toString());
      }
    }
  }

  /**
   * Validate lpn labels
   *
   * @param labelDataList
   * @return
   */
  private void validateLpnLabels(List<LabelData> labelDataList) {
    for (LabelData labelData : labelDataList) {
      boolean isValidLabelFormat = ReceivingUtils.isValidLpn(labelData.getTrackingId());
      if (!isValidLabelFormat) {
        logger.error(
            "label={} is invalid for pattern={}",
            labelData.getTrackingId(),
            ATLAS_LPN_REGEX_PATTERN);
        throw new ReceivingBadDataException(
            String.format(ExceptionCodes.INVALID_LBL_FORMAT),
            String.format(
                RdcConstants.INVALID_CONTAINER_INFO, labelData.getItemNumber().toString()),
            labelData.getItemNumber().toString());
      }
    }
  }

  /**
   * Validate LPN labels for received containers
   *
   * @param receivedContainers
   * @param itemNumber
   * @return
   */
  private void validateLpnFormatForContainers(
      List<ReceivedContainer> receivedContainers, Long itemNumber) {
    List<String> labelTrackingIds =
        receivedContainers
            .stream()
            .map(ReceivedContainer::getLabelTrackingId)
            .collect(Collectors.toList());
    labelTrackingIds.forEach(
        trackingId -> {
          boolean isValidLpn = ReceivingUtils.isValidLpn(trackingId);
          if (!isValidLpn) {
            logger.error(
                "lpn={} is invalid for pattern={}",
                trackingId,
                ReceivingConstants.ATLAS_LPN_REGEX_PATTERN);
            throw new ReceivingBadDataException(
                String.format(ExceptionCodes.INVALID_LBL_FORMAT),
                String.format(RdcConstants.INVALID_CONTAINER_INFO, itemNumber.toString()),
                itemNumber.toString());
          }
        });
  }

  /**
   * This method checks if DA automation slotting is enabled and selected
   *
   * @param slotDetails
   * @return
   */
  private boolean isAutomationSlottingForDAConveyableItem(SlotDetails slotDetails) {
    return isAutomationSlottingEnabledForDAConveyableItem()
        && isAutomationSlottingSelectedForDAConveyableItem(slotDetails);
  }

  /**
   * This method checks if DA automation slotting is enabled and selected
   *
   * @param slotDetails
   * @param handlingCode
   * @return
   */
  private boolean isDaConventionalSlottingForConveyableItem(
      SlotDetails slotDetails, String handlingCode) {
    return isDaConventionalSlottingEnabledForConveyableItem()
        && isDaSlottingSelectedForConveyableItem(slotDetails, handlingCode);
  }

  /**
   * This method checks if DA automation slotting is enabled
   *
   * @return
   */
  private boolean isAutomationSlottingEnabledForDAConveyableItem() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_DA_AUTOMATION_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
        false);
  }

  /**
   * This method checks if DA automation slotting is enabled
   *
   * @return
   */
  private boolean isDaConventionalSlottingEnabledForConveyableItem() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_DA_CONVENTIONAL_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
        false);
  }

  /**
   * This method checks if DA automation slotting is selected
   *
   * @param slotDetails
   * @return
   */
  private boolean isAutomationSlottingSelectedForDAConveyableItem(SlotDetails slotDetails) {
    if (Objects.nonNull(slotDetails)
        && StringUtils.isNotBlank(slotDetails.getStockType())
        && StringUtils.equalsIgnoreCase(slotDetails.getStockType(), STOCK_TYPE_CONVEYABLE)
        && StringUtils.isNotBlank(slotDetails.getSlotType())
        && StringUtils.equalsIgnoreCase(slotDetails.getSlotType(), SLOT_TYPE_AUTOMATION)) {
      return true;
    }

    if (!CollectionUtils.isEmpty(
            tenantSpecificConfigReader.allowedAutomationManuallyEnteredSlotsForDAConveyableItem(
                TenantContext.getFacilityNum()))
        && Objects.nonNull(slotDetails)
        && StringUtils.isNotBlank(slotDetails.getSlot())
        && tenantSpecificConfigReader
            .allowedAutomationManuallyEnteredSlotsForDAConveyableItem(
                TenantContext.getFacilityNum())
            .contains(slotDetails.getSlot().toUpperCase())) {
      return true;
    }
    return false;
  }

  /**
   * This method checks if DA slotting is selected for Conveyable Stock type & Conventional Slot
   * type
   *
   * @param slotDetails
   * @param handlingCode
   * @return
   */
  private boolean isDaSlottingSelectedForConveyableItem(
      SlotDetails slotDetails, String handlingCode) {
    // Auto slotting
    if (Objects.nonNull(slotDetails)
        && Objects.nonNull(slotDetails.getSlotSize())
        && StringUtils.isNotBlank(slotDetails.getStockType())
        && StringUtils.equalsIgnoreCase(slotDetails.getStockType(), STOCK_TYPE_CONVEYABLE)
        && StringUtils.isNotBlank(slotDetails.getSlotType())
        && StringUtils.equalsIgnoreCase(slotDetails.getSlotType(), SLOT_TYPE_CONVENTIONAL)) {
      if (!isConveyableItemHandlingCode(handlingCode)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.ATLAS_DA_CONVENTIONAL_SLOTTING_NOT_ALLOWED_FOR_NON_CON_ITEMS,
            ReceivingException.ATLAS_DA_CONVENTIONAL_SLOTTING_NOT_ALLOWED_FOR_NON_CON_ITEMS);
      }
      return true;
    }
    // Manual slotting
    return Objects.nonNull(slotDetails)
        && Objects.nonNull(slotDetails.getSlot())
        && isConveyableItemHandlingCode(handlingCode);
  }

  /**
   * This method checks if DA automation slotting is eligible DA automation slotting is eligible if
   * pack type is Case pack and Item handling Code is either C, I or J.
   *
   * @param deliveryDocumentLine
   * @return
   */
  private boolean isAutomationSlottingEligibleForDAConveyableItem(
      DeliveryDocumentLine deliveryDocumentLine) {
    String packTypeCode = deliveryDocumentLine.getAdditionalInfo().getPackTypeCode();
    String itemHandlingCode = deliveryDocumentLine.getAdditionalInfo().getHandlingCode();
    if (StringUtils.isBlank(packTypeCode)
        || !RdcConstants.CASE_PACK_TYPE_CODE.equalsIgnoreCase(packTypeCode)) {
      return false;
    }
    if (StringUtils.isBlank(itemHandlingCode)
        || CollectionUtils.isEmpty(
            rdcManagedConfig.getAtlasConveyableHandlingCodesForDaAutomationSlotting())
        || !rdcManagedConfig
            .getAtlasConveyableHandlingCodesForDaAutomationSlotting()
            .contains(itemHandlingCode.toUpperCase())) {
      return false;
    }
    return true;
  }
  /**
   * Block user to receive certain non-supported handling codes in Legacy receiving
   *
   * @param deliveryDocumentLine
   * @return
   */
  private void validateDaAtlasNonSupportedHandlingCode(
      Long deliveryNumber, DeliveryDocumentLine deliveryDocumentLine) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_DA_ATLAS_NON_SUPPORTED_HANDLING_CODES_BLOCKED,
        false)) {
      String itemPackAndHandlingCode =
          deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode();
      boolean isAtlasDaNonSupportedPackAndHandlingCode =
          !CollectionUtils.isEmpty(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
              && !rdcManagedConfig
                  .getDaAtlasItemEnabledPackHandlingCode()
                  .contains(itemPackAndHandlingCode);
      if (isAtlasDaNonSupportedPackAndHandlingCode) {
        /* block user upon receiving not supported item pack handlingCodes */
        logger.info(
            "Atlas DA receiving is not supported for item: {} with pack and handling code: {}, hence blocking user to receive PO:{} and POL:{} on delivery:{}",
            deliveryDocumentLine.getItemNbr(),
            itemPackAndHandlingCode,
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            deliveryNumber);
        throw new ReceivingBadDataException(
            String.format(ExceptionCodes.GLS_RCV_ITEM_IS_NOT_ATLAS_SUPPORTED),
            String.format(
                ReceivingException.GLS_RCV_ITEM_IS_NOT_ATLAS_SUPPORTED,
                deliveryDocumentLine.getItemNbr(),
                itemPackAndHandlingCode),
            String.valueOf(deliveryDocumentLine.getItemNbr()),
            itemPackAndHandlingCode);
      }
    }
  }

  /**
   * Validate quantity receiving not allowed for BM or BN
   *
   * @param isDaQtyReceiving
   * @param receiveQty
   * @param isLessThanCase
   * @param isAtlasDaSlottingRequest
   * @param deliveryDocumentLine
   */
  private void validateQuantityReceivingNotAllowed(
      boolean isDaQtyReceiving,
      int receiveQty,
      boolean isLessThanCase,
      boolean isAtlasDaSlottingRequest,
      DeliveryDocumentLine deliveryDocumentLine) {
    boolean isBreakPackConveyPicksOrNonConveyable =
        RdcUtils.isBreakPackConveyPicks(deliveryDocumentLine)
            || RdcUtils.isBreakPackNonConveyable(deliveryDocumentLine);
    if (isDaQtyReceiving
        && receiveQty > 1
        && !isLessThanCase
        && !isAtlasDaSlottingRequest
        && isBreakPackConveyPicksOrNonConveyable) {
      String daQuantityReceivingNotAllowedMsg =
          String.format(
              ReceivingException.DA_QUANTITY_RECEIVING_NOT_ALLOWED,
              deliveryDocumentLine.getItemNbr());
      throw new ReceivingBadDataException(
          ExceptionCodes.DA_QUANTITY_RECEIVING_NOT_ALLOWED, daQuantityReceivingNotAllowedMsg);
    }
  }

  /**
   * If the item is CL, Pallet pull ot slotting is blocked and exception wil be thrown
   *
   * @param deliveryDocumentLine
   * @param isPalletPullByStore
   * @param isAtlasDaSlottingRequest
   */
  private void validateSlottingEligibilityByPackTypeHandlingCode(
      DeliveryDocumentLine deliveryDocumentLine,
      boolean isPalletPullByStore,
      boolean isAtlasDaSlottingRequest) {
    boolean isValidRequest =
        RdcConstants.CASE_PACK_TYPE_CODE.equalsIgnoreCase(
                deliveryDocumentLine.getAdditionalInfo().getPackTypeCode())
            && RdcConstants.PALLET_RECEIVING_HANDLING_CODE.equalsIgnoreCase(
                deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    if ((isPalletPullByStore || isAtlasDaSlottingRequest) && isValidRequest) {
      logger.info(
          "DA slotting or pallet pull operation is not supported for item number {} with packTypeHandlingCode {}",
          deliveryDocumentLine.getItemNbr(),
          deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode());
      throw new ReceivingBadDataException(
          ExceptionCodes.ATLAS_DA_SLOTTING_AND_PALLET_PULL_NOT_ALLOWED_FOR_PACK_HANDLING_CODES,
          String.format(
              ReceivingException
                  .ATLAS_DA_SLOTTING_AND_PALLET_PULL_NOT_ALLOWED_FOR_PACK_HANDLING_CODES,
              deliveryDocumentLine.getItemNbr().toString(),
              deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode()));
    }
  }
}
