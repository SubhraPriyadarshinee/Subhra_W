package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.message.publisher.JMSSorterPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaAthenaPublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcVerificationMessage;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.CommonLabelDetails;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import com.walmart.platform.repositories.OutboxEvent;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RdcAutoReceiveService {

  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private ReceiptService receiptService;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private RdcInstructionHelper rdcInstructionHelper;
  @Autowired private RdcSlottingUtils rdcSlottingUtils;
  @Autowired private ContainerService containerService;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired private LabelDataService labelDataService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private PrintJobService printJobService;
  @Autowired private InstructionService instructionService;
  @Autowired private LabelDownloadEventService labelDownloadEventService;
  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired private Gson gson;
  @Autowired private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Autowired private RdcDeliveryMetaDataService rdcDeliveryMetaDataService;
  @Autowired private LocationService locationService;
  @Autowired private KafkaAthenaPublisher kafkaAthenaPublisher;
  @Autowired private JMSSorterPublisher jmsSorterPublisher;
  @Autowired private ItemUpdateUtils itemUpdateUtils;
  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private RdcAutoReceivingUtils rdcAutoReceivingUtils;

  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;

  private static final Logger logger = LoggerFactory.getLogger(RdcAutoReceiveService.class);

  /**
   * This method will take care of publishing post receipts for Automation either to Outbox or Kafka
   *
   * @param instruction
   * @param deliveryDocument
   * @param autoReceiveRequest
   * @param httpHeaders
   * @param receivedContainers
   * @param isDAContainer
   * @throws ReceivingException
   */
  private void postReceiveUpdatesForAutomation(
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      AutoReceiveRequest autoReceiveRequest,
      HttpHeaders httpHeaders,
      List<ReceivedContainer> receivedContainers,
      Boolean isDAContainer)
      throws ReceivingException {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_AUTOMATION_OUTBOX_PATTERN_ENABLED,
        false)) {
      logger.info(
          "Publishing automation receipts outbox Events for lpn : {}",
          receivedContainers.get(0).getLabelTrackingId());
      TenantContext.get().setPersistOutboxEventsForDAStart(System.currentTimeMillis());
      Collection<OutboxEvent> outboxEvents =
          rdcReceivingUtils.automationBuildOutboxEvents(
              receivedContainers, httpHeaders, instruction, deliveryDocument, isDAContainer);
      rdcReceivingUtils.persistOutboxEvents(outboxEvents);
      TenantContext.get().setPersistOutboxEventsForDAEnd(System.currentTimeMillis());
    } else {
      logger.info(
          "Publishing automation post receipts kafka messages for lpn : {}",
          receivedContainers.get(0).getLabelTrackingId());
      postReceivingUpdates(
          instruction,
          deliveryDocument,
          autoReceiveRequest,
          httpHeaders,
          Boolean.TRUE,
          receivedContainers,
          isDAContainer);
    }
  }
  /**
   * This method fetches(in case of receiving during verification event) and validates delivery
   * documents and fetches label data for auto receiving either DA,SSTK and DSDC lpns
   *
   * @param autoReceiveRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.INTERNAL,
      executionFlow = "autoReceiveContainerLpns")
  public InstructionResponse autoReceiveContainerLpns(
      AutoReceiveRequest autoReceiveRequest, HttpHeaders httpHeaders) throws ReceivingException {
    TenantContext.get().setAtlasAutoReceiveStartTs(System.currentTimeMillis());
    InstructionResponseImplNew instructionResponse = new InstructionResponseImplNew();
    List<DeliveryDocument> gdmDeliveryDocumentList = new ArrayList<>();
    boolean isExceptionReceiving =
        StringUtils.equals(autoReceiveRequest.getFeatureType(), RdcConstants.EXCEPTION_HANDLING);
    logger.info(
        "Auto Case receive request for Atlas item for Lpn:{}, deliveryNumber:{}",
        autoReceiveRequest.getLpn(),
        autoReceiveRequest.getDeliveryNumber());
    if (isExceptionReceiving) {
      gdmDeliveryDocumentList = autoReceiveRequest.getDeliveryDocuments();
    }
    rdcAutoReceivingUtils.setLocationHeaders(autoReceiveRequest, httpHeaders);
    LabelData labelData =
        rdcAutoReceivingUtils.fetchLabelData(
            gdmDeliveryDocumentList, autoReceiveRequest, isExceptionReceiving);

    String lpn = labelData.getTrackingId();

    if (Objects.isNull(autoReceiveRequest.getMessageId())) {
      String messageId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
      autoReceiveRequest.setMessageId(messageId);
    }
    if (!isExceptionReceiving) {
      // LabelData has PO and POLine
      autoReceiveRequest.setPurchaseReferenceNumber(labelData.getPurchaseReferenceNumber());
      autoReceiveRequest.setPurchaseReferenceLineNumber(labelData.getPurchaseReferenceLineNumber());
      gdmDeliveryDocumentList =
          rdcAutoReceivingUtils.getGdmDeliveryDocuments(autoReceiveRequest, httpHeaders);
      rdcAutoReceivingUtils.validateDeliveryDocuments(
          gdmDeliveryDocumentList, autoReceiveRequest, httpHeaders);
    }
    DeliveryDocumentLine deliveryDocumentLine =
        gdmDeliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setGtin(deliveryDocumentLine.getCaseUpc());
    instructionResponse.setDeliveryDocuments(gdmDeliveryDocumentList);
    instructionService.publishWorkingIfNeeded(instructionResponse, httpHeaders);

    boolean isDSDCItem =
        RdcConstants.DSDC_CHANNEL_METHODS_FOR_RDC.equals(deliveryDocumentLine.getPurchaseRefType());
    if (isDSDCItem) {
      // TODO: Receive DSDC item
      logger.info("Auto receive DSDC item with lpn :{}", lpn);
      return instructionResponse;
    }

    boolean isDAItem =
        ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType());
    if (isDAItem) {
      autoReceiveDALpn(autoReceiveRequest, instructionResponse, labelData, httpHeaders);
    } else {
      autoReceiveSSTKLpn(autoReceiveRequest, instructionResponse, labelData, httpHeaders);
    }

    TenantContext.get().setAtlasAutoReceiveEndTs(System.currentTimeMillis());
    logger.info(
        "LatencyCheck: Total time taken for Flib Auto receiving started ts={} time & completed within={} milliSeconds, correlationId={}",
        TenantContext.get().getAtlasAutoReceiveStartTs(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasAutoReceiveStartTs(),
            TenantContext.get().getAtlasAutoReceiveEndTs()),
        TenantContext.getCorrelationId());
    return instructionResponse;
  }

  /**
   * This method is to auto receive DA item, the method constructs instruction, container, container
   * item and receipts and persist them and updates the instruction response.
   *
   * @param autoReceiveRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  private void autoReceiveDALpn(
      AutoReceiveRequest autoReceiveRequest,
      InstructionResponseImplNew instructionResponse,
      LabelData labelData,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    DeliveryDocument deliveryDocument = instructionResponse.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    ReceivedContainer receivedContainer = null;
    List<ReceivedContainer> receivedContainers = null;
    Instruction instruction =
        rdcAutoReceivingUtils.createInstruction(
            autoReceiveRequest, instructionResponse.getDeliveryDocuments().get(0), httpHeaders);
    String inventoryStatus = null;

    receivedContainers =
        rdcAutoReceivingUtils.transformLabelData(
            Collections.singletonList(labelData), deliveryDocument);
    receivedContainer = receivedContainers.get(0);
    rdcAutoReceivingUtils.validateProDate(deliveryDocument);
    rdcAutoReceivingUtils.buildLabelType(receivedContainer, autoReceiveRequest, deliveryDocument);

    /**
     * Automation containers are all routing labels, irrespective of the storeAlignment, including
     * exception receiving containers(except for flib ineligible)
     */
    receivedContainer.setRoutingLabel(autoReceiveRequest.isFlibEligible());

    /**
     * if AutoReceivedContainer is false then we send putAway request, We have to send putaway
     * request only if we receive a flib eligible case from exception receiving. In case of
     * automation receiving or flib ineligible item, this value should be true.
     */
    if (!autoReceiveRequest.isFlibEligible()
        || !Objects.equals(autoReceiveRequest.getFeatureType(), RdcConstants.EXCEPTION_HANDLING)) {
      receivedContainer.setAutoReceivedContainer(Boolean.TRUE);
    } else {
      receivedContainer.setAutoReceivedContainer(Boolean.FALSE);
    }

    /**
     * Change inventory status to ALLOCATED for atlas DA flib eligible item. Fulfillmethod for flib
     * Ineligible item should be 'RECEIVING'. In case of Flib Ineligible, we get shipping labels and
     * shipping labels should be diverted to sorter
     */
    if (autoReceiveRequest.isFlibEligible()) {
      inventoryStatus = InventoryStatus.ALLOCATED.name();
      receivedContainer.setSorterDivertRequired(Boolean.FALSE);
    } else {
      receivedContainer.setFulfillmentMethod(FulfillmentMethodType.CASE_PACK_RECEIVING.getType());
      inventoryStatus = InventoryStatus.PICKED.name();
      receivedContainer.setSorterDivertRequired(Boolean.TRUE);
    }
    rdcAutoReceivingUtils.buildContainerItemAndContainerForDA(
        autoReceiveRequest,
        deliveryDocument,
        labelData.getTrackingId(),
        userId,
        receivedContainer,
        instruction.getId(),
        inventoryStatus);

    PrintJob printJob =
        printJobService.createPrintJob(
            autoReceiveRequest.getDeliveryNumber(),
            instruction.getId(),
            new HashSet<>(Collections.singleton(labelData.getTrackingId())),
            userId);
    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());

    Map<String, Object> printLabelData =
        LabelGenerator.generateDACaseLabel(
            deliveryDocumentLine,
            autoReceiveRequest.getQuantity(),
            Collections.singletonList(receivedContainer),
            printJob.getId(),
            httpHeaders,
            ReceivingUtils.getDCDateTime(dcTimeZone),
            tenantSpecificConfigReader.isFeatureFlagEnabled(
                ReceivingConstants.MFC_INDICATOR_FEATURE_FLAG, getFacilityNum()));
    rdcAutoReceivingUtils.updateInstruction(
        instruction,
        receivedContainer,
        autoReceiveRequest.getQuantity(),
        printLabelData,
        userId,
        true);
    instructionResponse.setInstruction(instruction);
    instructionResponse.setPrintJob(printLabelData);

    List<Receipt> receipts =
        receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            deliveryDocument,
            autoReceiveRequest.getDoorNumber(),
            instruction.getProblemTagId(),
            userId,
            autoReceiveRequest.getQuantity());
    receiptService.saveAll(receipts);

    // Set status as COMPLETE in LabelData
    labelData.setStatus(LabelInstructionStatus.COMPLETE.toString());
    labelDataService.save(labelData);
    postReceiveUpdatesForAutomation(
        instruction,
        deliveryDocument,
        autoReceiveRequest,
        httpHeaders,
        receivedContainers,
        Boolean.TRUE);
  }

  /**
   * This method is triggered during a verification event, the lpn from the message is received with
   * the auto receiving flow in this method.
   *
   * @param rdcVerificationMessage
   * @param httpHeaders
   */
  @InjectTenantFilter
  @Transactional
  public void autoReceiveOnVerificationEvent(
      RdcVerificationMessage rdcVerificationMessage, HttpHeaders httpHeaders)
      throws ReceivingException {
    if (rdcVerificationMessage
        .getMessageType()
        .equals(ReceivingConstants.RDC_MESSAGE_TYPE_NORMAL)) {
      AutoReceiveRequest autoReceiveRequest =
          rdcAutoReceivingUtils.buildAutoReceiveRequest(rdcVerificationMessage);
      InstructionResponse instructionResponse =
          autoReceiveContainerLpns(autoReceiveRequest, httpHeaders);
      logger.debug("Instruction Response from Automation Receiving: {}", instructionResponse);
    }
  }

  /**
   * This method is to auto receive SSTK item, the method constructs instruction, container,
   * container item and receipts and persist them and updates the instruction response.
   *
   * @param autoReceiveRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  private void autoReceiveSSTKLpn(
      AutoReceiveRequest autoReceiveRequest,
      InstructionResponseImplNew instructionResponse,
      LabelData labelData,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    DeliveryDocument deliveryDocument = instructionResponse.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    String lpn = labelData.getTrackingId();
    Instruction instruction =
        rdcAutoReceivingUtils.createInstruction(
            autoReceiveRequest, instructionResponse.getDeliveryDocuments().get(0), httpHeaders);
    RdcUtils.populateSlotInfoInDeliveryDocumentFromLabelData(labelData, deliveryDocumentLine);

    rdcAutoReceivingUtils.validateProDate(deliveryDocument);
    ReceivedContainer receivedContainer =
        rdcAutoReceivingUtils.buildReceivedContainerForSSTK(lpn, deliveryDocumentLine);

    rdcAutoReceivingUtils.buildContainerAndContainerItemForSSTK(
        instruction,
        deliveryDocument,
        autoReceiveRequest,
        userId,
        lpn,
        deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());

    PrintJob printJob =
        printJobService.createPrintJob(
            autoReceiveRequest.getDeliveryNumber(),
            instruction.getId(),
            new HashSet<>(Collections.singleton(lpn)),
            userId);
    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    TenantContext.get().setReceiveInstrCreateLabelCallStart(System.currentTimeMillis());
    CommonLabelDetails commonLabelDetails =
        CommonLabelDetails.builder()
            .labelTrackingId(lpn)
            .slot(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot())
            .slotSize(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize())
            .receiver(receivedContainer.getReceiver())
            .build();
    LabelFormat labelFormat = rdcReceivingUtils.getLabelFormatForPallet(deliveryDocumentLine);
    Map<String, Object> printLabelData =
        LabelGenerator.generatePalletLabels(
            deliveryDocumentLine,
            autoReceiveRequest.getQuantity(),
            commonLabelDetails,
            printJob.getId(),
            httpHeaders,
            ReceivingUtils.getDCDateTime(dcTimeZone),
            false,
            deliveryDocument.getDeliveryNumber(),
            labelFormat);
    TenantContext.get().setReceiveInstrCreateLabelCallEnd(System.currentTimeMillis());

    TenantContext.get().setUpdateInstrStart(System.currentTimeMillis());
    rdcAutoReceivingUtils.updateInstruction(
        instruction,
        receivedContainer,
        autoReceiveRequest.getQuantity(),
        printLabelData,
        userId,
        false);
    TenantContext.get().setUpdateInstrEnd(System.currentTimeMillis());

    instructionResponse.setInstruction(instruction);
    instructionResponse.setPrintJob(printLabelData);

    List<Receipt> receipts =
        receiptService.createReceiptsFromInstructionWithOsdrMasterUpdate(
            deliveryDocument,
            autoReceiveRequest.getDoorNumber(),
            autoReceiveRequest.getQuantity(),
            instruction.getProblemTagId(),
            userId);
    receiptService.saveAll(receipts);

    // Set status as COMPLETE in LabelData
    labelData.setStatus(LabelInstructionStatus.COMPLETE.toString());
    labelDataService.save(labelData);
    postReceiveUpdatesForAutomation(
        instruction,
        deliveryDocument,
        autoReceiveRequest,
        httpHeaders,
        Collections.singletonList(receivedContainer),
        Boolean.FALSE);
  }

  /**
   * Publishes instruction to WFT, containers to Inventory and receipts to dcFin
   *
   * @param lpn
   * @param deliveryDocument
   * @param httpHeaders
   * @param instruction
   * @throws ReceivingException
   */
  private void postReceivingUpdates(
      String lpn,
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders,
      Instruction instruction)
      throws ReceivingException {
    // TODO: If needed, get the location headers to publish to WFT
    rdcInstructionHelper.publishInstruction(
        instruction, httpHeaders, lpn, deliveryDocument.getDeliveryDocumentLines().get(0));
    Container container = containerPersisterService.getConsolidatedContainerForPublish(lpn);
    rdcContainerUtils.publishContainersToInventory(container);
    rdcContainerUtils.postReceiptsToDcFin(
        container, deliveryDocument.getPurchaseReferenceLegacyType());
  }
  /**
   * This method publishes receipts/purchases for Automation Receiving to below downstreams
   * Inventory (Receipts Topic) DcFin (Kafka / API - With Kafka Flag - IS_DCFIN_ENABLED_ON_KAFKA)
   * WFT () EI - For DA(only) Sorter - Not Needed, This case will not go in Conventional Sorter
   * Putaway - Not Needed, LabelRequest/LabelResponse is considered as Putaway
   *
   * @param instruction
   * @param deliveryDocument
   * @param autoReceiveRequest
   * @param httpHeaders
   * @param isAtlasConvertedItem
   * @param receivedContainers
   * @param isDaContainer
   */
  public void postReceivingUpdates(
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      AutoReceiveRequest autoReceiveRequest,
      HttpHeaders httpHeaders,
      boolean isAtlasConvertedItem,
      List<ReceivedContainer> receivedContainers,
      Boolean isDaContainer)
      throws ReceivingException {

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    if (isAtlasConvertedItem) {
      for (ReceivedContainer receivedContainer : receivedContainers) {
        if (StringUtils.isBlank(receivedContainer.getParentTrackingId())) {
          Container consolidatedContainer =
              containerPersisterService.getConsolidatedContainerForPublish(
                  receivedContainer.getLabelTrackingId());

          // publish consolidated containers to DcFin
          postContainersToDCFin(consolidatedContainer, deliveryDocument, isDaContainer);
          // publish to Sorter when we have shipping label
          publishSorterEvent(receivedContainer, isDaContainer, consolidatedContainer);

          // publish consolidated or parent containers to Inventory
          TenantContext.get().setReceiveInstrPublishReceiptsCallStart(System.currentTimeMillis());
          rdcContainerUtils.publishContainersToInventory(consolidatedContainer);
          TenantContext.get().setReceiveInstrPublishReceiptsCallEnd(System.currentTimeMillis());

          // publish putaway message - Not needed for Automation

          // publish consolidated or parent containers to EI
          publishContainerToEI(consolidatedContainer, isDaContainer);

          if (Boolean.TRUE.equals(isDaContainer)
              && StringUtils.equals(
                  autoReceiveRequest.getFeatureType(), RdcConstants.EXCEPTION_HANDLING)
              && autoReceiveRequest.isFlibEligible()) {
            TenantContext.get()
                .setReceiveInstrPublishSymPutawayCallStart(System.currentTimeMillis());
            symboticPutawayPublishHelper.publishPutawayAddMessage(
                receivedContainer, deliveryDocument, instruction, SymFreightType.DA, httpHeaders);
            TenantContext.get().setReceiveInstrPublishSymPutawayCallEnd(System.currentTimeMillis());
          }
        }
      }
    }

    TenantContext.get().setDaCaseReceivingPublishWFTCallStart(System.currentTimeMillis());
    rdcReceivingUtils.publishInstruction(
        instruction, deliveryDocumentLine, autoReceiveRequest.getQuantity(), httpHeaders, false);
    TenantContext.get().setDaCaseReceivingPublishWFTCallEnd(System.currentTimeMillis());
  }

  private void postContainersToDCFin(
      Container consolidatedContainer, DeliveryDocument deliveryDocument, Boolean isDaContainer) {
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_ENABLED_ON_KAFKA,
            false)
        || (!isDaContainer
            && !(tenantSpecificConfigReader.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                RdcConstants.IS_SSTK_DCFIN_ENABLED_ON_KAFKA,
                false)))) {
      TenantContext.get().setReceiveInstrPostDcFinReceiptsCallStart(System.currentTimeMillis());
      logger.info(
          "Consolidated containers posting to DcFin for trackingId:{}",
          consolidatedContainer.getTrackingId());
      rdcContainerUtils.postReceiptsToDcFin(
          consolidatedContainer, deliveryDocument.getPurchaseReferenceLegacyType());
      TenantContext.get().setReceiveInstrPostDcFinReceiptsCallEnd(System.currentTimeMillis());
    }
  }

  private void publishSorterEvent(
      ReceivedContainer receivedContainer, Boolean isDaContainer, Container consolidatedContainer) {
    if (Boolean.TRUE.equals(isDaContainer) && receivedContainer.isSorterDivertRequired()) {
      String labelType =
          rdcReceivingUtils.getLabelTypeForSorterDivert(receivedContainer, consolidatedContainer);
      if (Objects.nonNull(consolidatedContainer.getDestination())
          && Objects.nonNull(
              consolidatedContainer.getDestination().get(ReceivingConstants.BU_NUMBER))) {
        // publish sorter divert message to Athena
        TenantContext.get().setDaCaseReceivingSorterPublishStart(System.currentTimeMillis());
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false)) {
          kafkaAthenaPublisher.publishLabelToSorter(consolidatedContainer, labelType);
        } else {
          jmsSorterPublisher.publishLabelToSorter(consolidatedContainer, labelType);
        }
        TenantContext.get().setDaCaseReceivingAthenaPublishEnd(System.currentTimeMillis());
      }
    }
  }

  private void publishContainerToEI(Container consolidatedContainer, Boolean isDaContainer) {
    // Routing Label - EI_DC_RECEIVING_EVENT
    // Shipping Label - EI_DC_RECEIVING_AND_PICK_EVENTS
    if (Boolean.TRUE.equals(isDaContainer)
        && tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false)) {
      TenantContext.get().setPublishEICallStart(System.currentTimeMillis());
      String[] eiEvents =
          consolidatedContainer.getInventoryStatus().equals(InventoryStatus.PICKED.name())
              ? ReceivingConstants.EI_DC_RECEIVING_AND_PICK_EVENTS
              : ReceivingConstants.EI_DC_RECEIVING_EVENT;
      rdcContainerUtils.publishContainerToEI(consolidatedContainer, eiEvents);
      TenantContext.get().setPublishEICallEnd(System.currentTimeMillis());
    }
  }
}
