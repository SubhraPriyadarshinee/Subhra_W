package com.walmart.move.nim.receiving.rdc.service;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateMessage;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadMessageDTO;
import com.walmart.move.nim.receiving.core.model.label.LabelDataMiscInfo;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.model.label.acl.ACLLabelDataTO;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcSlotUpdateMessage;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelDownloadEventStatus;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RdcLabelGenerationService {
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private NimRdsService nimRdsService;

  @Value("${label.data.publish.batch.size:1000}")
  private int labelDataBatchSize;

  @Resource(name = RdcConstants.RDC_INSTRUCTION_SERVICE)
  private RdcInstructionService rdcInstructionService;

  @Autowired private LabelDataService labelDataService;
  @ManagedConfiguration AppConfig appConfig;
  @Autowired private RdcItemValidator rdcItemValidator;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired private ItemConfigApiClient itemConfigApiClient;
  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired private SlottingServiceImpl slottingService;

  @Autowired private RdcLabelGenerationUtils rdcLabelGenerationUtils;

  @Autowired private RdcSSTKLabelGenerationUtils rdcSSTKLabelGenerationUtils;

  @Autowired private LabelDownloadEventService labelDownloadEventService;

  @Resource(name = ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
  private LPNCacheService lpnCacheService;

  @Autowired private Gson gson;

  @Resource(name = ReceivingConstants.DELIVERY_EVENT_PERSISTER_SERVICE)
  @Autowired
  DeliveryEventPersisterService deliveryEventPersisterService;

  @Autowired RdcSSTKInstructionUtils rdcSSTKInstructionUtils;
  @Autowired RdcSSTKItemValidator rdcSSTKItemValidator;

  @Autowired private RdcAsyncUtils rdcAsyncUtils;
  @Autowired private LabelSequenceService labelSequenceService;

  private static final Logger logger = LoggerFactory.getLogger(RdcLabelGenerationService.class);

  public List<DeliveryDocument> fetchDeliveryDocumentsByPOAndItemNumber(
      String deliveryNumber,
      Integer itemNumber,
      String purchaseReferenceNumber,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments =
        deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            deliveryNumber, itemNumber, httpHeaders);
    // Filter delivery documents with PO
    deliveryDocuments =
        deliveryDocuments
            .stream()
            .filter(
                deliveryDocument ->
                    purchaseReferenceNumber.equals(deliveryDocument.getPurchaseReferenceNumber()))
            .collect(Collectors.toList());
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
        false)) {
      rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(deliveryDocuments);
    } else {
      nimRdsService.updateAdditionalItemDetails(deliveryDocuments, httpHeaders);
    }
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
        false)) {
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId(
          httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
      deliveryDocuments.forEach(
          deliveryDocument -> {
            if (rdcInstructionUtils.isSSTKDocument(deliveryDocument)) {
              rdcInstructionUtils.populatePrimeSlotFromSmartSlotting(
                  deliveryDocument, httpHeaders, instructionRequest);
            }
          });
    }
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .forEach(
            deliveryDocumentLine ->
                deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true));
    rdcReceivingUtils.overridePackTypeCodeForBreakPackItem(
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    return deliveryDocuments;
  }

  /**
   * This method validates Duplicate Lpns, processes and publishes the labelData to HE
   *
   * @param instructionDownloadMessageDTO
   * @param labelDataList
   */
  public void processLabelsForAutomation(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO, List<LabelData> labelDataList) {
    TenantContext.get().setLabelDownloadStart(System.currentTimeMillis());

    if (CollectionUtils.isNotEmpty(labelDataList)) {
      // filter dsdc labels
      List<LabelData> dsdcLabelDataList =
          labelDataList
              .stream()
              .filter(labelData -> StringUtils.isNotBlank(labelData.getSscc()))
              .collect(Collectors.toList());
      if (CollectionUtils.isNotEmpty(dsdcLabelDataList)) {
        logger.info(
            "Label processing for DSDC is not supported in Automation for deliveryNumber:{}",
            dsdcLabelDataList.get(0).getDeliveryNumber());
        return;
      }
    }

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false)
        && rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(
            instructionDownloadMessageDTO.getDeliveryNumber(),
            instructionDownloadMessageDTO.getItemNumber())) {
      if (Boolean.TRUE.equals(isExistingLabelDownloadEvent(instructionDownloadMessageDTO))) {
        logger.error(
            "LabelDownloadEvent already processed for the delivery {}, item {}, purchaseReferenceNumber {} with the blobUri : {}",
            instructionDownloadMessageDTO.getDeliveryNumber(),
            instructionDownloadMessageDTO.getItemNumber(),
            instructionDownloadMessageDTO.getPoNumber(),
            instructionDownloadMessageDTO.getBlobStorage().get(0).getBlobUri());
        return;
      }
      boolean isAtlasSendingUniqueLpnToHawkeyeEnabled =
          tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              RdcConstants.IS_ATLAS_SENDING_UNIQUE_LPN_TO_HAWKEYE_ENABLED,
              false);
      if (!isAtlasSendingUniqueLpnToHawkeyeEnabled) {
        TenantContext.get()
            .setLabelDataFetchWithPoAndPoLineAndItemNumberAndStatusStart(
                System.currentTimeMillis());
        labelDataList =
            labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
                instructionDownloadMessageDTO.getPoNumber().trim(),
                instructionDownloadMessageDTO.getItemNumber(),
                LabelInstructionStatus.AVAILABLE.name());
        TenantContext.get()
            .setLabelDataFetchWithPoAndPoLineAndItemNumberAndStatusEnd(System.currentTimeMillis());
      }
      if (CollectionUtils.isEmpty(labelDataList)) {
        logger.info(
            "Labels already sent to Hawkeye for delivery:{}, purchaseReferenceNumber:{} and itemNumber:{}",
            instructionDownloadMessageDTO.getDeliveryNumber(),
            instructionDownloadMessageDTO.getPoNumber().trim(),
            instructionDownloadMessageDTO.getItemNumber());
        saveLabelDownloadEvent(
            instructionDownloadMessageDTO, LabelDownloadEventStatus.IGNORE_NO_LABELS.name());
        return;
      }
      LabelDownloadEvent labelDownloadEvent =
          saveLabelDownloadEvent(
              instructionDownloadMessageDTO, LabelDownloadEventStatus.IN_PROGRESS.name());
      processAndPublishLabelData(instructionDownloadMessageDTO, labelDataList, labelDownloadEvent);
    }
    TenantContext.get().setLabelDownloadEnd(System.currentTimeMillis());
    TenantContext.get().setLabelDownloadEventIncludingAutomationEnd(System.currentTimeMillis());
    calculateAndLogElapsedTimeSummary4labelDownload();
  }

  /**
   * This method validates Duplicate Lpns, processes and publishes the labelData to HE in Async mode
   *
   * @param instructionDownloadMessageDTO
   * @param labelDataList
   */
  @Async
  public void processLabelsForAutomationAsync(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO, List<LabelData> labelDataList) {
    logger.info("Process Labels for Automation by Async enabled");
    if (Objects.nonNull(instructionDownloadMessageDTO)
        && Objects.nonNull(instructionDownloadMessageDTO.getHttpHeaders())) {
      // Setting tenant context as it is an async function
      TenantContext.setFacilityNum(
          Integer.valueOf(
              Objects.requireNonNull(
                      instructionDownloadMessageDTO
                          .getHttpHeaders()
                          .get(ReceivingConstants.TENENT_FACLITYNUM))
                  .get(0)));
      TenantContext.setFacilityCountryCode(
          Objects.requireNonNull(
                  instructionDownloadMessageDTO
                      .getHttpHeaders()
                      .get(ReceivingConstants.TENENT_COUNTRY_CODE))
              .get(0));
      processLabelsForAutomation(instructionDownloadMessageDTO, labelDataList);
    }
  }

  /**
   * This method processes labelData, constructs label payload and publishes to HE
   *
   * @param instructionDownloadMessageDTO
   * @param labelDataList
   */
  public void processAndPublishLabelData(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO,
      List<LabelData> labelDataList,
      LabelDownloadEvent labelDownloadEvent) {
    String poNumber = instructionDownloadMessageDTO.getPoNumber().trim();
    try {
      if (CollectionUtils.isNotEmpty(labelDataList)) {
        logger.info(
            "Processing start Label Download Information for Delivery {}, Item Number {}, PoNumber {}, poLineNumber {}",
            instructionDownloadMessageDTO.getDeliveryNumber(),
            instructionDownloadMessageDTO.getItemNumber(),
            poNumber,
            instructionDownloadMessageDTO.getPurchaseReferenceLineNumber());

        TenantContext.get()
            .setGdmDeliveryInfoByDeliveryItemNumberCallStart(System.currentTimeMillis());
        List<DeliveryDocument> deliveryDocumentList =
            fetchDeliveryDocumentsByPOAndItemNumber(
                instructionDownloadMessageDTO.getDeliveryNumber().toString(),
                Math.toIntExact(instructionDownloadMessageDTO.getItemNumber()),
                poNumber,
                instructionDownloadMessageDTO.getHttpHeaders());
        TenantContext.get()
            .setGdmDeliveryInfoByDeliveryItemNumberCallEnd(System.currentTimeMillis());
        RejectReason rejectReason =
            rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());

        if (CollectionUtils.isNotEmpty(deliveryDocumentList)) {
          TenantContext.get().setLabelConstructAndPublishToHawkeyeStart(System.currentTimeMillis());
          for (List<LabelData> partitionedLabelData :
              Lists.partition(labelDataList, labelDataBatchSize)) {
            ACLLabelDataTO aclLabelDataTO =
                rdcLabelGenerationUtils.buildLabelDownloadForHawkeye(
                    deliveryDocumentList.get(0), partitionedLabelData, rejectReason);
            aclLabelDataTO.setDeliveryNumber(aclLabelDataTO.getGroupNumber());
            instructionDownloadMessageDTO
                .getHttpHeaders()
                .add(
                    ReceivingConstants.DELIVERY_NUMBER,
                    String.valueOf(instructionDownloadMessageDTO.getDeliveryNumber()));
            publishACLLabelData(aclLabelDataTO, instructionDownloadMessageDTO.getHttpHeaders());
          }
          TenantContext.get().setLabelConstructAndPublishToHawkeyeEnd(System.currentTimeMillis());

          if (Objects.nonNull(labelDownloadEvent)) {
            TenantContext.get().setLabelDownloadEventPersistStart(System.currentTimeMillis());
            updateRejectReasonAndStatusInLabelDownloadEvent(labelDownloadEvent, rejectReason);
            TenantContext.get().setLabelDownloadEventPersistEnd(System.currentTimeMillis());
          }

          if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              RdcConstants.IS_UPDATE_POSSIBLE_UPC_ENABLED,
              false)) {
            TenantContext.get().setLabelDataPossibleUpcPersistStart(System.currentTimeMillis());
            updatePossibleUpcInLabelData(labelDataList, deliveryDocumentList);
            TenantContext.get().setLabelDataPossibleUpcPersistEnd(System.currentTimeMillis());
          }
          logger.info(
              "Processing Completed Label Download Information for Delivery {}, Item Number {}, PoNumber {}, poLineNumber {}",
              instructionDownloadMessageDTO.getDeliveryNumber(),
              instructionDownloadMessageDTO.getItemNumber(),
              poNumber,
              instructionDownloadMessageDTO.getPurchaseReferenceLineNumber());
        }
      }
    } catch (Exception exception) {
      logger.error(
          "Exception :{} occurred while processing and publishing labelData for delivery: {}, PO: {} and item: {}",
          exception,
          instructionDownloadMessageDTO.getDeliveryNumber(),
          poNumber,
          instructionDownloadMessageDTO.getItemNumber());
      ReceivingException receivingException = (ReceivingException) exception;
      if (Objects.nonNull(receivingException.getErrorResponse())
          && receivingException
              .getErrorResponse()
              .getErrorKey()
              .equals(ExceptionCodes.PO_LINE_NOT_FOUND)
          && Objects.nonNull(labelDownloadEvent)) {
        labelDownloadEvent.setStatus(LabelDownloadEventStatus.IGNORE_NO_LABELS.name());
        labelDownloadEventService.saveAll(Collections.singletonList(labelDownloadEvent));
      }
    }
  }

  /**
   * This method makes a call to Kafka Hawkeye Publisher for posting labels
   *
   * @param aclLabelDataTO
   * @param messageHeaders
   */
  @TimeTracing(component = AppComponent.RDC, type = Type.MESSAGE, externalCall = true)
  @Timed(
      name = "publishACLLabelDataTimed",
      level1 = "uwms-receiving",
      level2 = "RdcLabelGenerationService",
      level3 = "publishACLLabelData")
  public void publishACLLabelData(
      com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData aclLabelDataTO,
      HttpHeaders messageHeaders) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_AUTOMATION_OUTBOX_PATTERN_ENABLED,
        false)) {
      rdcReceivingUtils.publishAutomationOutboxRdcLabelData(messageHeaders, aclLabelDataTO);
    } else {
      String aclLabelDataTOJSON = JacksonParser.writeValueAsString(aclLabelDataTO);
      MessagePublisher<com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData>
          labelDataPublisher;
      labelDataPublisher =
          tenantSpecificConfigReader.getConfiguredInstance(
              String.valueOf(TenantContext.getFacilityNum()),
              ReceivingConstants.LABEL_DATA_PUBLISHER,
              MessagePublisher.class);
      labelDataPublisher.publish(
          aclLabelDataTO,
          ReceivingUtils.getHawkeyePublishHeaders(
              Long.valueOf(aclLabelDataTO.getDeliveryNumber()), messageHeaders));
      logger.info("Successfully published labels to Hawkeye for data: {}", aclLabelDataTOJSON);
    }
  }

  /**
   * Checks for existing labelDownloadEvents with same Delivery, Item, PO Number, BlobUri and in
   * PROCESSED status.
   *
   * @param instructionDownloadMessageDTO
   * @return
   */
  public Boolean isExistingLabelDownloadEvent(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO) {

    String blobUri;
    if (CollectionUtils.isNotEmpty(instructionDownloadMessageDTO.getBlobStorage())) {
      blobUri = instructionDownloadMessageDTO.getBlobStorage().get(0).getBlobUri();
    } else {
      return false;
    }

    List<LabelDownloadEvent> labelDownloadEventList =
        labelDownloadEventService
            .findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumberAndStatus(
                instructionDownloadMessageDTO.getDeliveryNumber(),
                instructionDownloadMessageDTO.getPoNumber(),
                instructionDownloadMessageDTO.getItemNumber(),
                LabelDownloadEventStatus.PROCESSED.toString());
    Optional<LabelDownloadEvent> matchedLabelDownloadEvent =
        labelDownloadEventList
            .stream()
            .filter(
                labelDownloadEvent -> {
                  String messagePayload = labelDownloadEvent.getMessagePayload();
                  if (Objects.nonNull(messagePayload)) {
                    InstructionDownloadMessageDTO
                        instructionDownloadMessageDTOFromLabelDownloadEvent;
                    try {
                      instructionDownloadMessageDTOFromLabelDownloadEvent =
                          gson.fromJson(messagePayload, InstructionDownloadMessageDTO.class);
                    } catch (Exception e) {
                      return false;
                    }
                    if (Objects.nonNull(instructionDownloadMessageDTOFromLabelDownloadEvent)) {
                      String blobUriFromLabelDownloadEvent;
                      if (CollectionUtils.isNotEmpty(
                          instructionDownloadMessageDTOFromLabelDownloadEvent.getBlobStorage())) {
                        blobUriFromLabelDownloadEvent =
                            instructionDownloadMessageDTOFromLabelDownloadEvent
                                .getBlobStorage()
                                .get(0)
                                .getBlobUri();
                      } else {
                        return false;
                      }
                      return StringUtils.equals(blobUri, blobUriFromLabelDownloadEvent);
                    }
                  }
                  return false;
                })
            .findAny();
    return matchedLabelDownloadEvent.isPresent();
  }

  /**
   * Republishing Labels to Hawkeye when item is changed to conveyable or sym eligible
   *
   * @param poToDeliveryMap
   * @param itemUpdateMessage
   * @param isSSTKLabelType
   */
  public void republishLabelsToHawkeye(
      Map<String, Set<Long>> poToDeliveryMap,
      ItemUpdateMessage itemUpdateMessage,
      boolean isSSTKLabelType) {
    Long itemNumber = Long.valueOf(itemUpdateMessage.getItemNumber());
    List<LabelDownloadEvent> labelDownloadEventList =
        saveLabelDownloadEventsForItemUpdate(
            poToDeliveryMap,
            LabelDownloadEventStatus.IN_PROGRESS.name(),
            itemUpdateMessage,
            isSSTKLabelType);
    poToDeliveryMap.forEach(
        (poNumber, deliveryNumberSet) -> {
          List<LabelData> labelDataList = fetchLabelDataForPOAndItem(poNumber, itemNumber);
          if (CollectionUtils.isEmpty(labelDataList)) {
            logger.info(
                "Labels not found for purchaseReferenceNumber:{} and itemNumber:{}",
                poNumber,
                itemNumber);
            return;
          }
          List<LabelData> finalLabelDataList =
              RdcUtils.filterLabelDataWith25DigitLpns(labelDataList);
          if (CollectionUtils.isEmpty(finalLabelDataList)) {
            logger.info(
                "Smart labels found for purchaseReferenceNumber:{} and itemNumber:{} hence not processing the labels.",
                poNumber,
                itemNumber);
            saveLabelDownloadEventWithIgnoreItemUpdateStatus(poNumber, labelDownloadEventList);
            return;
          }
          deliveryNumberSet.forEach(
              deliveryNumber -> {
                List<LabelDownloadEvent> labelDownloadEvents =
                    labelDownloadEventList
                        .stream()
                        .filter(
                            labelDownloadEvent ->
                                labelDownloadEvent.getDeliveryNumber().equals(deliveryNumber)
                                    && labelDownloadEvent
                                        .getPurchaseReferenceNumber()
                                        .equals(poNumber))
                        .collect(Collectors.toList());
                InstructionDownloadMessageDTO instructionDownloadMessageDTO =
                    createInstructionDownloadMessageDTO(
                        deliveryNumber, poNumber, itemUpdateMessage);
                if (isSSTKLabelType) {
                  publishSSTKLabels(
                      poNumber, deliveryNumber, labelDownloadEvents, finalLabelDataList);
                } else {
                  processAndPublishLabelData(
                      instructionDownloadMessageDTO,
                      finalLabelDataList,
                      labelDownloadEvents.get(0));
                }
              });
        });
  }

  /**
   * Update LabelDownloadEven with IGNORE_ITEM_UPDATE status
   *
   * @param poNumber
   * @param labelDownloadEventList
   */
  private void saveLabelDownloadEventWithIgnoreItemUpdateStatus(
      String poNumber, List<LabelDownloadEvent> labelDownloadEventList) {
    List<LabelDownloadEvent> labelDownloadEventsToUpdate =
        labelDownloadEventList
            .stream()
            .filter(
                labelDownloadEvent ->
                    labelDownloadEvent.getPurchaseReferenceNumber().equals(poNumber))
            .map(
                labelDownloadEvent -> {
                  labelDownloadEvent.setStatus(LabelDownloadEventStatus.IGNORE_ITEM_UPDATE.name());
                  return labelDownloadEvent;
                })
            .collect(Collectors.toList());
    labelDownloadEventService.saveAll(labelDownloadEventsToUpdate);
  }

  /**
   * This method is to fetch deliveryDocuments using poNumber and deliveryNumber and build
   * aggregatedLabelDataPOLineMap and publish SSTK LabelData
   *
   * @param poNumber
   * @param deliveryNumber
   * @param labelDownloadEvents
   * @param labelDataList
   */
  private void publishSSTKLabels(
      String poNumber,
      Long deliveryNumber,
      List<LabelDownloadEvent> labelDownloadEvents,
      List<LabelData> labelDataList) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber(String.valueOf(deliveryNumber))
            .poNumber(poNumber)
            .build();
    DeliveryDetails deliveryDetails =
        rdcSSTKLabelGenerationUtils.fetchDeliveryDetails(
            getDeliveryDetailsUrl(deliveryUpdateMessage), deliveryNumber);
    if (Objects.isNull(deliveryDetails)) {
      logger.info("Failed to fetch delivery: {} details", deliveryNumber);
      return;
    }
    try {
      List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments =
          deliveryDetails.getDeliveryDocuments();
      Map<DeliveryDocumentLine, List<LabelData>> aggregatedLabelDataPOLineMap = new HashMap<>();
      com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine deliveryDocumentLine =
          getDeliveryDocumentLineMatchingItemNumber(
              deliveryDocuments, labelDataList.get(0).getItemNumber());
      if (Objects.nonNull(deliveryDocumentLine)) {
        aggregatedLabelDataPOLineMap.put(deliveryDocumentLine, labelDataList);
        processAndPublishSSTKLabelData(
            aggregatedLabelDataPOLineMap,
            labelDownloadEvents,
            deliveryDocuments.get(0),
            httpHeaders);
      }
    } catch (Exception e) {
      logger.error(
          "Error occurred while processing and publishing labels for delivery: {}, PO: {}, Item : {}",
          deliveryNumber,
          poNumber,
          labelDataList.get(0).getItemNumber());
    }
  }

  /**
   * This method is to get DeliveryDocumentLine of with matching ItemNumber
   *
   * @param deliveryDocuments
   * @param itemNumber
   * @return
   */
  private com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine
      getDeliveryDocumentLineMatchingItemNumber(
          List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments,
          Long itemNumber) {
    for (com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument :
        deliveryDocuments) {
      for (com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine
          deliveryDocumentLine : deliveryDocument.getDeliveryDocumentLines()) {
        if (itemNumber.equals(deliveryDocumentLine.getItemNbr())) {
          return deliveryDocumentLine;
        }
      }
    }
    return null;
  }

  public List<LabelDownloadEvent> fetchAllLabelDownloadEvents(
      Map<String, Set<Long>> poToDeliveryMap,
      ItemUpdateMessage itemUpdateMessage,
      String status,
      boolean isVoidLpnRequired,
      boolean isSSTKLabelType) {
    List<LabelDownloadEvent> labelDownloadEventList = new ArrayList<>();
    LabelDownloadEventMiscInfo labelDownloadEventMiscInfo = new LabelDownloadEventMiscInfo();
    labelDownloadEventMiscInfo.setVoidLpnToHawkeyeRequired(isVoidLpnRequired);
    if (isSSTKLabelType) {
      labelDownloadEventMiscInfo.setLabelType(ReceivingConstants.PURCHASE_REF_TYPE_SSTK);
    }
    String miscInfo = gson.toJson(labelDownloadEventMiscInfo);
    poToDeliveryMap.forEach(
        (poNumber, deliveryNumberSet) -> {
          deliveryNumberSet.forEach(
              deliveryNumber -> {
                labelDownloadEventList.add(
                    transformLabelDownloadEventEntity(
                        itemUpdateMessage, deliveryNumber, poNumber, status, miscInfo));
              });
        });
    return labelDownloadEventList;
  }

  private List<LabelData> fetchLabelDataForPOAndItem(String poNumber, Long itemNumber) {
    return labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
        poNumber, itemNumber, LabelInstructionStatus.AVAILABLE.name());
  }

  private InstructionDownloadMessageDTO createInstructionDownloadMessageDTO(
      Long deliveryNumber, String poNumber, ItemUpdateMessage itemUpdateMessage) {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new InstructionDownloadMessageDTO();
    instructionDownloadMessageDTO.setDeliveryNumber(deliveryNumber);
    instructionDownloadMessageDTO.setItemNumber(Long.valueOf(itemUpdateMessage.getItemNumber()));
    instructionDownloadMessageDTO.setHttpHeaders(itemUpdateMessage.getHttpHeaders());
    instructionDownloadMessageDTO.setPoNumber(poNumber);
    return instructionDownloadMessageDTO;
  }

  public List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
      fetchAndFilterSSTKDeliveryDocuments(
          DeliveryDetails deliveryDetails, HttpHeaders httpHeaders) {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> filteredSSTKDocuments =
        deliveryDetails
            .getDeliveryDocuments()
            .stream()
            .filter(doc -> rdcInstructionUtils.isSSTKDocument(doc))
            .collect(Collectors.toList());
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_ATLAS_CONVERSION_ENABLED_FOR_ALL_SSTK_ITEMS,
        false)) {
      rdcInstructionUtils.validateAndAddAsAtlasConvertedItemsV2(filteredSSTKDocuments, httpHeaders);
    }
    return filteredSSTKDocuments
        .stream()
        .map(
            deliveryDocument -> {
              deliveryDocument
                  .getDeliveryDocumentLines()
                  .forEach(
                      deliveryDocumentLine -> {
                        ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
                        if (Objects.isNull(itemData)) {
                          itemData = new ItemData();
                        }
                        itemData.setAtlasConvertedItem(true);
                        deliveryDocumentLine.setAdditionalInfo(itemData);
                      });
              return deliveryDocument;
            })
        .collect(Collectors.toList());
  }

  /**
   * Generates labels for SSTK PO's and persists data in labelData and labelDownloadEvent. This
   * method will be called for DOOR_ASSIGN event
   *
   * @param deliveryDocuments
   * @param deliveryUpdateMessage
   * @param isUpdateEvent
   * @param rdcSlotUpdateMessage
   * @throws ReceivingException
   */
  public void generateAndPublishSSTKLabels(
      List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments,
      DeliveryUpdateMessage deliveryUpdateMessage,
      Boolean isUpdateEvent,
      RdcSlotUpdateMessage rdcSlotUpdateMessage)
      throws ReceivingException {
    List<LabelData> labelDataPerLine;
    HttpHeaders httpHeaders = deliveryUpdateMessage.getHttpHeaders();
    rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(deliveryDocuments);
    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      logger.info("No delivery documents to proceed, returning");
      return;
    }
    // Get Prime Slot details from Smart-Slotting only in case of delivery update
    TenantContext.get().setSmartSlottingPrimeSlotDetailsStart(System.currentTimeMillis());
    if (Objects.isNull(rdcSlotUpdateMessage)) {
      getPrimeSlotDetails(deliveryDocuments, httpHeaders);
    }
    TenantContext.get().setSmartSlottingPrimeSlotDetailsEnd(System.currentTimeMillis());

    for (com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument :
        deliveryDocuments) {
      List<LabelDownloadEvent> labelDownloadEventList = new ArrayList<>();
      Map<DeliveryDocumentLine, List<LabelData>> aggregatedLabelDataPOLineMap = new HashMap<>();
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        RejectReason rejectReason = rdcSSTKItemValidator.validateItem(deliveryDocumentLine);
        if (Objects.isNull(rejectReason)
            || !((rejectReason.equals(RejectReason.RDC_SSTK))
                || rejectReason.equals(RejectReason.BREAKOUT))) {
          if (Boolean.TRUE.equals(isUpdateEvent)) {
            labelDataPerLine = generateLabelsForSSTKPOLineQuantityChange(deliveryDocument);
          } else {
            labelDataPerLine =
                fetchOrGenerateLabelsPerPOAndPOLine(deliveryDocumentLine, deliveryDocument);
          }
          if (CollectionUtils.isNotEmpty(labelDataPerLine)) {
            // Adding the POLine and labelDataList to map only when labelDataList is not empty
            aggregatedLabelDataPOLineMap.put(deliveryDocumentLine, labelDataPerLine);
          }
        } else {
          aggregatedLabelDataPOLineMap.put(deliveryDocumentLine, Collections.emptyList());
        }
        LabelDownloadEvent labelDownloadEvent =
            createLabelDownloadEvent(
                deliveryDocument.getDeliveryNumber(),
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getItemNbr(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                rejectReason,
                deliveryUpdateMessage,
                rdcSlotUpdateMessage);
        if (Objects.nonNull(labelDownloadEvent)) {
          labelDownloadEventList.add(labelDownloadEvent);
        }
      }
      if (CollectionUtils.isNotEmpty(labelDownloadEventList)) {
        labelDownloadEventService.saveAll(labelDownloadEventList);
      }
      processAndPublishSSTKLabelData(
          aggregatedLabelDataPOLineMap, labelDownloadEventList, deliveryDocument, httpHeaders);
    }
  }

  private void processAndPublishSSTKLabelData(
      Map<DeliveryDocumentLine, List<LabelData>> aggregatedLabelDataPOLineMap,
      List<LabelDownloadEvent> labelDownloadEventList,
      com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders) {
    for (Map.Entry<DeliveryDocumentLine, List<LabelData>> poLineLabelData :
        aggregatedLabelDataPOLineMap.entrySet()) {
      List<LabelData> labelDataList = poLineLabelData.getValue();
      DeliveryDocumentLine deliveryDocumentLine = poLineLabelData.getKey();
      Long deliveryNumber = deliveryDocument.getDeliveryNumber();
      deliveryDocumentLine.setPurchaseReferenceLegacyType(
          Integer.valueOf(deliveryDocument.getPurchaseReferenceLegacyType()));
      try {
        List<LabelDownloadEvent> labelDownloadEvents =
            labelDownloadEventList
                .stream()
                .parallel()
                .filter(
                    event -> {
                      LabelDownloadEventMiscInfo labelDownloadEventMiscInfo =
                          gson.fromJson(event.getMiscInfo(), LabelDownloadEventMiscInfo.class);
                      return event
                              .getPurchaseReferenceNumber()
                              .equals(deliveryDocumentLine.getPurchaseReferenceNumber())
                          && event.getItemNumber().equals(deliveryDocumentLine.getItemNbr())
                          && (Objects.isNull(
                                  labelDownloadEventMiscInfo.getPurchaseReferenceLineNumber())
                              || labelDownloadEventMiscInfo
                                  .getPurchaseReferenceLineNumber()
                                  .equals(deliveryDocumentLine.getPurchaseReferenceLineNumber()));
                    })
                .collect(Collectors.toList());
        logger.info(
            "Started processing for label publish to Hawkeye for Delivery {}, Item Number {}, PoNumber {}, poLineNumber {}",
            deliveryNumber,
            deliveryDocumentLine.getItemNbr(),
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
        if (CollectionUtils.isEmpty(labelDataList)) {
          TenantContext.get().setLabelConstructAndPublishToHawkeyeStart(System.currentTimeMillis());
          buildPayloadAndPublishLabelsToHawkeye(
              deliveryDocumentLine,
              labelDataList,
              labelDownloadEvents.get(0).getRejectReason(),
              httpHeaders,
              deliveryDocument.getDeliveryNumber());
          TenantContext.get().setLabelConstructAndPublishToHawkeyeEnd(System.currentTimeMillis());
        } else {
          TenantContext.get().setLabelConstructAndPublishToHawkeyeStart(System.currentTimeMillis());
          for (List<LabelData> partitionedLabelData :
              Lists.partition(labelDataList, labelDataBatchSize)) {
            buildPayloadAndPublishLabelsToHawkeye(
                deliveryDocumentLine,
                partitionedLabelData,
                labelDownloadEvents.get(0).getRejectReason(),
                httpHeaders,
                deliveryDocument.getDeliveryNumber());
          }
          TenantContext.get().setLabelConstructAndPublishToHawkeyeEnd(System.currentTimeMillis());
        }
        if (CollectionUtils.isNotEmpty(labelDownloadEvents)) {
          TenantContext.get().setLabelDownloadEventPersistStart(System.currentTimeMillis());
          updateStatusInLabelDownloadEvent(labelDownloadEvents.get(0));
          TenantContext.get().setLabelDownloadEventPersistEnd(System.currentTimeMillis());
        }
        logger.info(
            "Processing Completed Label Download Information for Delivery {}, Item Number {}, PoNumber {}, poLineNumber {}",
            deliveryNumber,
            deliveryDocumentLine.getItemNbr(),
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
      } catch (Exception exception) {
        logger.error(
            "Exception :{} occurred while processing and publishing labelData for delivery: {}, PO: {} and item: {}",
            exception,
            deliveryNumber,
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr());
        throw exception;
      }
    }
  }

  /**
   * If labelData exists then validates duplicate lpns and returns labelDataList, else generates
   * labels per PO/PO line and persists the labelData
   *
   * @param deliveryDocumentLine
   * @param deliveryDocument
   * @return labelDataList
   * @throws ReceivingException
   */
  private List<LabelData> fetchOrGenerateLabelsPerPOAndPOLine(
      DeliveryDocumentLine deliveryDocumentLine,
      com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument)
      throws ReceivingException {
    List<LabelData> labelDataList =
        labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name());
    if (CollectionUtils.isNotEmpty(labelDataList)) {
      logger.info(
          "Lpns already generated for PO : {} and Item : {}",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getItemNbr());
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          RdcConstants.IS_ATLAS_SENDING_UNIQUE_LPN_TO_HAWKEYE_ENABLED,
          false)) {
        return labelDataList;
      }
      return labelDataList;
    } else {
      int generatedOrderQty = 0;
      int generatedOverageQty = 0;
      labelDataList =
          generateAndPersistLabels(
              deliveryDocument.getPurchaseReferenceMustArriveByDate(),
              deliveryDocument.getDeliveryNumber(),
              deliveryDocumentLine,
              generatedOrderQty,
              generatedOverageQty);
    }
    return labelDataList;
  }

  /**
   * Generates labels for order qty and overage qty, and persists labelData
   *
   * @param purchaseReferenceMustArriveByDate
   * @param deliveryNumber
   * @param deliveryDocumentLine
   * @param generatedOrderQty
   * @param generatedOverageQty
   * @return labelDataList
   * @throws ReceivingException
   */
  private List<LabelData> generateAndPersistLabels(
      Date purchaseReferenceMustArriveByDate,
      Long deliveryNumber,
      DeliveryDocumentLine deliveryDocumentLine,
      int generatedOrderQty,
      int generatedOverageQty)
      throws ReceivingException {
    List<LabelData> labelDataList = new ArrayList<>();
    // Generate labels for Order qty
    if (Objects.nonNull(deliveryDocumentLine.getExpectedQty())
        && deliveryDocumentLine.getExpectedQty() > 0) {
      List<LabelData> labelDataListForOrderQty =
          generateLpnsAndLabelData(
              purchaseReferenceMustArriveByDate,
              deliveryNumber,
              deliveryDocumentLine,
              generatedOrderQty,
              deliveryDocumentLine.getExpectedQty(),
              LabelType.ORDERED);
      if (CollectionUtils.isNotEmpty(labelDataListForOrderQty)) {
        labelDataList.addAll(labelDataListForOrderQty);
      }
    }
    // Generate labels for Overage qty
    if (Objects.nonNull(deliveryDocumentLine.getOverageQtyLimit())
        && deliveryDocumentLine.getOverageQtyLimit() > 0) {
      List<LabelData> labelDataListForOverageQty =
          generateLpnsAndLabelData(
              purchaseReferenceMustArriveByDate,
              deliveryNumber,
              deliveryDocumentLine,
              generatedOverageQty,
              deliveryDocumentLine.getOverageQtyLimit(),
              LabelType.OVERAGE);
      if (CollectionUtils.isNotEmpty(labelDataListForOverageQty)) {
        labelDataList.addAll(labelDataListForOverageQty);
      }
    }
    // Persists labelData
    if (CollectionUtils.isNotEmpty(labelDataList)) {
      labelDataService.saveAllAndFlush(labelDataList);
    }
    return labelDataList;
  }

  /**
   * Calculates lpnsCount, generates lpns and labelData for that count
   *
   * @param purchaseReferenceMustArriveByDate
   * @param deliveryNumber
   * @param deliveryDocumentLine
   * @param generatedQty
   * @param quantity
   * @param labelType
   * @return labelDataList
   * @throws ReceivingException
   */
  private List<LabelData> generateLpnsAndLabelData(
      Date purchaseReferenceMustArriveByDate,
      Long deliveryNumber,
      DeliveryDocumentLine deliveryDocumentLine,
      int generatedQty,
      Integer quantity,
      LabelType labelType)
      throws ReceivingException {
    Integer quantityInVnpks =
        ReceivingUtils.conversionToVendorPack(
            quantity,
            deliveryDocumentLine.getExpectedQtyUOM(),
            deliveryDocumentLine.getVnpkQty(),
            deliveryDocumentLine.getWhpkQty());
    int noOfLpnsToGenerate = quantityInVnpks - generatedQty;
    List<String> lpns = getLpns(noOfLpnsToGenerate, deliveryDocumentLine.getItemNbr());
    if (CollectionUtils.isEmpty(lpns)) {
      logger.error(
          "Failed to generate lpns for PO : {} and Item : {}",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getItemNbr());
      return Collections.emptyList();
    }
    LabelSequence labelSequence =
        labelSequenceService.findByMABDPOLineNumberItemNumberLabelType(
            purchaseReferenceMustArriveByDate,
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            deliveryDocumentLine.getItemNbr(),
            labelType);
    Long startingLabelSeqNbr =
        getStartingLabelSeqNbr(
            purchaseReferenceMustArriveByDate,
            deliveryDocumentLine,
            labelType,
            labelSequence,
            noOfLpnsToGenerate);
    long purchaseReferenceMustArriveByDateSeq = startingLabelSeqNbr + generatedQty;
    return createLabelData(
        deliveryNumber,
        deliveryDocumentLine,
        lpns,
        purchaseReferenceMustArriveByDateSeq,
        labelType);
  }

  /**
   * This method is to get the startingLabelSeqNbr. If the labelSequence is Empty it generates the
   * startingLabelSeqNbr or else assigns the nextSequenceNo to startingLabelSeqNbr from
   * labelSequence and updates the nextSequenceNo with startingLabelSeqNbr + noOfLpnsToGenerate.
   *
   * @param purchaseReferenceMustArriveByDate
   * @param deliveryDocumentLine
   * @param labelType
   * @param labelSequence
   * @param noOfLpnsToGenerate
   * @return
   */
  private Long getStartingLabelSeqNbr(
      Date purchaseReferenceMustArriveByDate,
      DeliveryDocumentLine deliveryDocumentLine,
      LabelType labelType,
      LabelSequence labelSequence,
      int noOfLpnsToGenerate) {
    Long startingLabelSeqNbr;
    if (ObjectUtils.isEmpty(labelSequence)) {
      startingLabelSeqNbr =
          generateLabelSequenceNbr(
              purchaseReferenceMustArriveByDate,
              deliveryDocumentLine.getPurchaseReferenceLineNumber(),
              labelType);
      buildAndSaveLabelSequence(
          purchaseReferenceMustArriveByDate,
          deliveryDocumentLine,
          startingLabelSeqNbr,
          labelType,
          noOfLpnsToGenerate);
    } else {
      startingLabelSeqNbr = labelSequence.getNextSequenceNo();
      labelSequence.setNextSequenceNo(startingLabelSeqNbr + noOfLpnsToGenerate);
      labelSequenceService.save(labelSequence);
    }
    return startingLabelSeqNbr;
  }

  /**
   * This method is to build LabelSequence object and persist in DB
   *
   * @param purchaseReferenceMustArriveByDate
   * @param deliveryDocumentLine
   * @param startingSerialNbr
   * @param labelType
   * @param noOfLpnsToGenerate
   * @return
   */
  private void buildAndSaveLabelSequence(
      Date purchaseReferenceMustArriveByDate,
      DeliveryDocumentLine deliveryDocumentLine,
      Long startingSerialNbr,
      LabelType labelType,
      int noOfLpnsToGenerate) {
    long seqNumber = startingSerialNbr + noOfLpnsToGenerate;
    LabelSequence labelSequence =
        LabelSequence.builder()
            .nextSequenceNo(seqNumber)
            .mustArriveBeforeDate(purchaseReferenceMustArriveByDate)
            .purchaseReferenceLineNumber(deliveryDocumentLine.getPurchaseReferenceLineNumber())
            .itemNumber(deliveryDocumentLine.getItemNbr())
            .labelType(labelType)
            .build();
    labelSequenceService.save(labelSequence);
  }

  /**
   * This method is to get delta ORDERED/OVERAGE LPNS and send these to updateLabelStatusToCancelled
   * to mark the AVAILABLE Labels as CANCELLED in LabelData and send VOID to Hawkeye
   *
   * @param labelDataList
   * @param labelType
   * @param qtyChange
   */
  private void processLabelsToUpdateLabelStatus(
      List<LabelData> labelDataList, LabelType labelType, int qtyChange, HttpHeaders httpHeaders) {
    List<LabelData> voidLabelData =
        labelDataList
            .stream()
            .filter(
                labelData ->
                    labelType.name().equalsIgnoreCase(labelData.getLabelType().name())
                        && LabelInstructionStatus.AVAILABLE
                            .name()
                            .equalsIgnoreCase(labelData.getStatus()))
            .sorted(Comparator.comparingLong(LabelData::getLabelSequenceNbr).reversed())
            .collect(Collectors.toList())
            .subList(0, Math.min(labelDataList.size(), qtyChange));
    updateLabelStatusToCancelled(
        voidLabelData, httpHeaders, ReceivingConstants.PURCHASE_REF_TYPE_SSTK);
  }

  /**
   * Generates Label Sequence Number as - MABD + POL(4 digits) + serial number.
   *
   * @param purchaseReferenceMustArriveByDate
   * @param purchaseReferenceLineNumber
   * @param labelType
   * @return purchaseReferenceMustArriveByDateSeq
   */
  private Long generateLabelSequenceNbr(
      Date purchaseReferenceMustArriveByDate,
      Integer purchaseReferenceLineNumber,
      LabelType labelType) {
    SimpleDateFormat simpleDateFormat =
        new SimpleDateFormat(RdcConstants.MABD_DATE_FORMAT_FOR_SSTK_LABEL_SEQUENCE_NBR);
    String purchaseReferenceMustArriveByDateSeq =
        simpleDateFormat.format(purchaseReferenceMustArriveByDate);
    String poLineNo =
        String.format(
                RdcConstants.POL_FORMAT_SPECIFIER_FOR_SSTK_LABEL_SEQUENCE_NBR,
                purchaseReferenceLineNumber)
            .replace(' ', '0');
    String sequenceStartingDigit =
        labelType.equals(LabelType.ORDERED)
            ? RdcConstants.LABEL_SEQUENCE_NUMBER_STARTING_DIGIT_ORDERED_SSTK
            : RdcConstants.LABEL_SEQUENCE_NUMBER_STARTING_DIGIT_OVERAGE_SSTK;
    purchaseReferenceMustArriveByDateSeq =
        sequenceStartingDigit
            .concat(purchaseReferenceMustArriveByDateSeq)
            .concat(poLineNo)
            .concat(RdcConstants.SSTK_LABEL_STARTING_SERIAL_NBR);
    return Long.parseLong(purchaseReferenceMustArriveByDateSeq);
  }

  /**
   * Fetches lpns for the given count
   *
   * @return lpnList
   * @throws ReceivingException
   */
  private List<String> getLpns(int lpnsCount, Long itemNumber) throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    List<String> lpnList = new ArrayList<>();
    logger.info("Fetching LPNs for a count of {} for item {}", lpnsCount, itemNumber);
    lpnList.addAll(lpnCacheService.getLPNSBasedOnTenant(lpnsCount, httpHeaders));
    return lpnList;
  }

  /**
   * Creates labelData for the given lpns
   *
   * @param deliveryNumber
   * @param deliveryDocumentLine
   * @param lpns
   * @param purchaseReferenceMustArriveByDateSeq
   * @param labelType
   * @return labelDataList
   */
  private List<LabelData> createLabelData(
      Long deliveryNumber,
      DeliveryDocumentLine deliveryDocumentLine,
      List<String> lpns,
      Long purchaseReferenceMustArriveByDateSeq,
      LabelType labelType) {
    logger.info(
        "Creating LabelData for PO : {} and Item : {} with labelType : {}",
        deliveryDocumentLine.getPurchaseReferenceNumber(),
        deliveryDocumentLine.getItemNbr(),
        labelType);
    List<LabelData> labelDataList = new ArrayList<>();
    Boolean isItemSymEligible =
        rdcSSTKInstructionUtils.isAtlasItemSymEligible(deliveryDocumentLine);
    String possibleUPC =
        gson.toJson(rdcLabelGenerationUtils.getPossibleUPCv2(deliveryDocumentLine));
    for (String lpn : lpns) {
      LabelData labelData =
          getLabelData(
              deliveryNumber, deliveryDocumentLine, labelType, possibleUPC, isItemSymEligible);
      labelData.setTrackingId(lpn);
      labelData.setLabelSequenceNbr(purchaseReferenceMustArriveByDateSeq++);
      labelDataList.add(labelData);
    }
    return labelDataList;
  }

  /**
   * Returns labelData for the POLine/Item
   *
   * @param deliveryNumber
   * @param deliveryDocumentLine
   * @param labelType
   * @param possibleUPC
   * @param isSymEligible
   * @return labelData
   */
  private LabelData getLabelData(
      Long deliveryNumber,
      DeliveryDocumentLine deliveryDocumentLine,
      LabelType labelType,
      String possibleUPC,
      Boolean isSymEligible) {

    return LabelData.builder()
        .deliveryNumber(deliveryNumber)
        .purchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber())
        .purchaseReferenceLineNumber(deliveryDocumentLine.getPurchaseReferenceLineNumber())
        .itemNumber(deliveryDocumentLine.getItemNbr())
        .vnpk(deliveryDocumentLine.getVnpkQty())
        .whpk(deliveryDocumentLine.getWhpkQty())
        .orderQuantity(deliveryDocumentLine.getVnpkQty())
        .quantity(deliveryDocumentLine.getVnpkQty())
        .quantityUOM(ReceivingConstants.Uom.VNPK)
        .labelType(labelType)
        .status(LabelInstructionStatus.AVAILABLE.toString())
        .labelDataMiscInfo(
            gson.toJson(
                buildLabelDataMiscInfo(deliveryDocumentLine, isSymEligible),
                LabelDataMiscInfo.class))
        .possibleUPC(possibleUPC)
        .label(ReceivingConstants.PURCHASE_REF_TYPE_SSTK)
        .build();
  }

  /**
   * Creates LabelDownloadEvent
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param itemNbr
   * @param purchaseReferenceLineNumber
   * @param rejectReason
   * @param deliveryUpdateMessage
   * @param rdcSlotUpdateMessage
   * @return labelDownloadEvent
   */
  private LabelDownloadEvent createLabelDownloadEvent(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Long itemNbr,
      Integer purchaseReferenceLineNumber,
      RejectReason rejectReason,
      DeliveryUpdateMessage deliveryUpdateMessage,
      RdcSlotUpdateMessage rdcSlotUpdateMessage) {
    deliveryUpdateMessage.setHttpHeaders(
        buildHeadersForMessagePayload(deliveryUpdateMessage.getHttpHeaders()));
    LabelDownloadEventMiscInfo labelDownloadEventMiscInfo = new LabelDownloadEventMiscInfo();
    labelDownloadEventMiscInfo.setLabelType(ReceivingConstants.PURCHASE_REF_TYPE_SSTK);
    labelDownloadEventMiscInfo.setPurchaseReferenceLineNumber(purchaseReferenceLineNumber);
    logger.info(
        "Creating labelDownloadEvent record for Delivery : {}, PO : {} and Item : {}",
        deliveryNumber,
        purchaseReferenceNumber,
        itemNbr);
    return LabelDownloadEvent.builder()
        .deliveryNumber(deliveryNumber)
        .purchaseReferenceNumber(purchaseReferenceNumber)
        .itemNumber(itemNbr)
        .rejectReason(rejectReason)
        .status(LabelDownloadEventStatus.IN_PROGRESS.name())
        .miscInfo(gson.toJson(labelDownloadEventMiscInfo))
        .messagePayload(
            (Objects.isNull(rdcSlotUpdateMessage)
                ? gson.toJson(deliveryUpdateMessage)
                : gson.toJson(rdcSlotUpdateMessage)))
        .build();
  }

  /**
   * Generates SSTK labels for POLine quantity change. This method will be called for PO_LINE_UPDATE
   *
   * @param deliveryDocument
   * @return labelDataList
   * @throws ReceivingException
   */
  public List<LabelData> generateLabelsForSSTKPOLineQuantityChange(
      com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument)
      throws ReceivingException {
    List<LabelData> generatedLabelDataListForOrderQty;
    List<LabelData> generatedLabelDataListForOverageQty;
    List<LabelData> generatedLabelDataList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    int generatedOrderQty = 0;
    int generatedOverageQty = 0;
    List<LabelData> labelDataList =
        labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
    if (CollectionUtils.isEmpty(labelDataList)) {
      return generateAndPersistLabels(
          deliveryDocument.getPurchaseReferenceMustArriveByDate(),
          deliveryDocument.getDeliveryNumber(),
          deliveryDocumentLine,
          generatedOrderQty,
          generatedOverageQty);
    }
    generatedOrderQty = getLabelDataCount(labelDataList, LabelType.ORDERED);
    generatedOverageQty = getLabelDataCount(labelDataList, LabelType.OVERAGE);
    AtomicBoolean isLpnsCancelled = new AtomicBoolean(false);
    Boolean isOrderedLpnsGenerated;
    Boolean isOverageLpnsGenerated;
    isOrderedLpnsGenerated =
        validateAndProcessQuantityChange(
            labelDataList,
            LabelType.ORDERED,
            deliveryDocumentLine,
            generatedOrderQty,
            isLpnsCancelled);
    if (!isLpnsCancelled.get()
        && Boolean.FALSE.equals(isOrderedLpnsGenerated)
        && Objects.nonNull(deliveryDocumentLine.getExpectedQty())
        && deliveryDocumentLine.getExpectedQty() > 0) {
      generatedLabelDataListForOrderQty =
          generateLpnsAndLabelData(
              deliveryDocument.getPurchaseReferenceMustArriveByDate(),
              deliveryDocument.getDeliveryNumber(),
              deliveryDocumentLine,
              generatedOrderQty,
              deliveryDocumentLine.getExpectedQty(),
              LabelType.ORDERED);
      if (CollectionUtils.isNotEmpty(generatedLabelDataListForOrderQty)) {
        generatedLabelDataList.addAll(generatedLabelDataListForOrderQty);
      }
    }
    isOverageLpnsGenerated =
        validateAndProcessQuantityChange(
            labelDataList,
            LabelType.OVERAGE,
            deliveryDocumentLine,
            generatedOverageQty,
            isLpnsCancelled);
    if (!isLpnsCancelled.get()
        && Boolean.FALSE.equals(isOverageLpnsGenerated)
        && Objects.nonNull(deliveryDocumentLine.getOverageQtyLimit())
        && deliveryDocumentLine.getOverageQtyLimit() > 0) {
      generatedLabelDataListForOverageQty =
          generateLpnsAndLabelData(
              deliveryDocument.getPurchaseReferenceMustArriveByDate(),
              deliveryDocument.getDeliveryNumber(),
              deliveryDocumentLine,
              generatedOverageQty,
              deliveryDocumentLine.getOverageQtyLimit(),
              LabelType.OVERAGE);
      if (CollectionUtils.isNotEmpty(generatedLabelDataListForOverageQty)) {
        generatedLabelDataList.addAll(generatedLabelDataListForOverageQty);
      }
    }
    // Persists labelData
    if (CollectionUtils.isNotEmpty(generatedLabelDataList)) {
      labelDataService.saveAllAndFlush(generatedLabelDataList);
    }
    if (Boolean.TRUE.equals(isOrderedLpnsGenerated)
        || Boolean.TRUE.equals(isOverageLpnsGenerated)) {
      labelDataList =
          labelDataList
              .stream()
              .filter(
                  labelData ->
                      labelData.getStatus().equals(LabelInstructionStatus.AVAILABLE.name()))
              .collect(Collectors.toList());
      generatedLabelDataList.addAll(labelDataList);
    }
    return generatedLabelDataList;
  }

  /**
   * This method validates the following cases for both the expected and overage quantity for an
   * item: - If the number of labels generated is more than the required quantity then we cancel the
   * delta labels and void the lpns in Hawkeye and set the isLpnCancelled flag to TRUE. - If the
   * number of labels generated is equal to the required quantity then we return TRUE indicating
   * that labels are generated and need to be republished - And in case, labels generated are less
   * than the required quantity then we return FALSE indicating labels needs to be generated.
   *
   * @param labelDataList
   * @param labelType
   * @param deliveryDocumentLine
   * @param generatedQty
   * @param isLpnsCancelled
   * @return Boolean
   */
  private Boolean validateAndProcessQuantityChange(
      List<LabelData> labelDataList,
      LabelType labelType,
      DeliveryDocumentLine deliveryDocumentLine,
      int generatedQty,
      AtomicBoolean isLpnsCancelled) {
    Integer quantity =
        labelType.name().equals(LabelType.ORDERED.name())
            ? deliveryDocumentLine.getExpectedQty()
            : deliveryDocumentLine.getOverageQtyLimit();
    Integer quantityInVnpks =
        ReceivingUtils.conversionToVendorPack(
            quantity,
            deliveryDocumentLine.getExpectedQtyUOM(),
            deliveryDocumentLine.getVnpkQty(),
            deliveryDocumentLine.getWhpkQty());
    int noOfLpnsToGenerate = quantityInVnpks - generatedQty;
    if (noOfLpnsToGenerate < 0) {
      HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
      logger.info(
          "Negative quantity change for the PO : {} and Item : {} with labelType : {}",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getItemNbr(),
          labelType);
      processLabelsToUpdateLabelStatus(
          labelDataList, labelType, Math.abs(noOfLpnsToGenerate), httpHeaders);
      isLpnsCancelled.set(true);
      return Boolean.FALSE;
    } else if (noOfLpnsToGenerate == 0) {
      logger.info(
          "Lpns already generated for PO : {} and Item : {}",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getItemNbr());
      return Boolean.TRUE;
    }
    return Boolean.FALSE;
  }
  /**
   * This method is to find SYM eligible items, Slotting call for each item to validate the prime
   *
   * @param deliveryDocuments
   * @param httpHeaders
   */
  public void getPrimeSlotDetails(
      List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments,
      HttpHeaders httpHeaders) {
    Set<Long> itemNumbers = RdcUtils.fetchItemNumbersFromDeliveryDocuments(deliveryDocuments);
    List<ContainerItem> containerItems =
        itemNumbers
            .stream()
            .map(
                item -> {
                  ContainerItem containerItem = new ContainerItem();
                  containerItem.setItemNumber(item);
                  return containerItem;
                })
            .collect(Collectors.toList());
    SlottingPalletResponse slottingPalletResponse =
        slottingService.getPrimeSlot(containerItems, httpHeaders);

    for (com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument :
        deliveryDocuments) {
      for (com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine
          deliveryDocumentLine : deliveryDocument.getDeliveryDocumentLines()) {
        if (Objects.nonNull(slottingPalletResponse)
            && !org.springframework.util.CollectionUtils.isEmpty(
                slottingPalletResponse.getLocations())) {
          SlottingDivertLocations slottingDivertLocations = null;
          Optional<SlottingDivertLocations> primeSlotLocation =
              slottingPalletResponse
                  .getLocations()
                  .stream()
                  .filter(
                      divertLocation ->
                          divertLocation.getItemNbr()
                              == deliveryDocumentLine.getItemNbr().intValue())
                  .findFirst();
          if (primeSlotLocation.isPresent()) {
            slottingDivertLocations = primeSlotLocation.get();
            ItemData itemData;
            if (Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())) {
              itemData = deliveryDocumentLine.getAdditionalInfo();
            } else {
              itemData = new ItemData();
            }
            itemData.setPrimeSlot(slottingDivertLocations.getLocation());
            itemData.setPrimeSlotSize((int) slottingDivertLocations.getLocationSize());
            itemData.setSlotType(slottingDivertLocations.getSlotType());
            if (org.apache.commons.lang3.StringUtils.isNotEmpty(
                slottingDivertLocations.getAsrsAlignment())) {
              itemData.setAsrsAlignment(slottingDivertLocations.getAsrsAlignment());
            }
            deliveryDocumentLine.setAdditionalInfo(itemData);
          }
        }
      }
    }
  }

  /**
   * This method will process delivery events and generate and persist store friendly labels
   *
   * @param deliveryUpdateMessage message based on different events e.g. DOOR_ASSIGNED, PO_ADDED etc
   */
  @Timed(
      name = "preLabelGenTimed",
      level1 = "uwms-receiving",
      level2 = "rdcLabelDeliveryService",
      level3 = "processDeliveryEvent")
  @ExceptionCounted(
      name = "preLabelGenExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rdcLabelDeliveryService",
      level3 = "processDeliveryEvent")
  @Async
  public void processDeliveryEventAsync(DeliveryUpdateMessage deliveryUpdateMessage) {
    // Setting tenant context as it is an async function
    logger.info(
        "Process delivery event for delivery: {} in async mode",
        deliveryUpdateMessage.getDeliveryNumber());
    TenantContext.setFacilityNum(
        Integer.valueOf(
            Objects.requireNonNull(
                    deliveryUpdateMessage
                        .getHttpHeaders()
                        .get(ReceivingConstants.TENENT_FACLITYNUM))
                .get(0)));
    TenantContext.setFacilityCountryCode(
        Objects.requireNonNull(
                deliveryUpdateMessage.getHttpHeaders().get(ReceivingConstants.TENENT_COUNTRY_CODE))
            .get(0));
    processDeliveryEvent(deliveryUpdateMessage);
  }

  /**
   * This method will process delivery events and generate and persist store friendly labels
   *
   * @param deliveryUpdateMessage message based on different events e.g. DOOR_ASSIGNED, PO_ADDED etc
   */
  @Timed(
      name = "preLabelGenTimed",
      level1 = "uwms-receiving",
      level2 = "rdcLabelDeliveryService",
      level3 = "processDeliveryEvent")
  @ExceptionCounted(
      name = "preLabelGenExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rdcLabelDeliveryService",
      level3 = "processDeliveryEvent")
  public void processDeliveryEvent(DeliveryUpdateMessage deliveryUpdateMessage) {
    logger.info(
        "Received: {} event for DeliveryNumber: {} with {} status.",
        deliveryUpdateMessage.getEventType(),
        deliveryUpdateMessage.getDeliveryNumber(),
        deliveryUpdateMessage.getDeliveryStatus());
    if (!(ReceivingUtils.isValidPreLabelEvent(deliveryUpdateMessage.getEventType())
        && ReceivingUtils.isValidStatus(
            DeliveryStatus.valueOf(deliveryUpdateMessage.getDeliveryStatus())))) {
      logger.info(
          "Received: {} event with {} delivery status. Hence ignoring for pre-label generation",
          deliveryUpdateMessage.getEventType(),
          deliveryUpdateMessage.getDeliveryStatus());
      return;
    }

    TenantContext.get().setFetchSSTKAutoDeliveryDetailsStart(System.currentTimeMillis());
    String deliveryDetailsUrl = getDeliveryDetailsUrl(deliveryUpdateMessage);
    DeliveryDetails deliveryDetails =
        rdcSSTKLabelGenerationUtils.fetchDeliveryDetails(
            deliveryDetailsUrl, Long.parseLong(deliveryUpdateMessage.getDeliveryNumber()));
    deliveryUpdateMessage.setUrl(deliveryDetailsUrl);
    TenantContext.get().setFetchSSTKAutoDeliveryDetailsEnd(System.currentTimeMillis());

    if ((!deliveryUpdateMessage.getEventType().equals(ReceivingConstants.EVENT_DOOR_ASSIGNED))
        && Objects.nonNull(deliveryDetails)
        && !rdcInstructionUtils.isSSTKDocument(deliveryDetails.getDeliveryDocuments().get(0))) {
      logger.info(
          "Received DA PO event, Hence ignoring label generation for delivery {} and PO {}",
          deliveryUpdateMessage.getDeliveryNumber(),
          deliveryUpdateMessage.getPoNumber());
      return;
    }
    TenantContext.get().setValidateDeliveryEventAndPublishStatusStart(System.currentTimeMillis());
    validateDeliveryEventAndPublishStatus(deliveryUpdateMessage, deliveryDetails);
    TenantContext.get().setValidateDeliveryEventAndPublishStatusEnd(System.currentTimeMillis());

    calculateAndLogElapsedTimeSummary4SSTKLabelGenerationAndPublish();
  }

  private void validateDeliveryEventAndPublishStatus(
      DeliveryUpdateMessage deliveryUpdateMessage, DeliveryDetails deliveryDetails) {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocumentList =
        null;

    DeliveryEvent deliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    if (Objects.isNull(deliveryEvent)) {
      logger.info(
          "Nothing to process for pre-label generation for delivery no. {}",
          deliveryUpdateMessage.getDeliveryNumber());
      return;
    }

    if (Objects.nonNull(deliveryDetails)) {
      TenantContext.get().setFetchAndFilterSSTKDeliveryDocumentsStart(System.currentTimeMillis());
      deliveryDocumentList =
          fetchAndFilterSSTKDeliveryDocuments(
              deliveryDetails, deliveryUpdateMessage.getHttpHeaders());
      TenantContext.get().setFetchAndFilterSSTKDeliveryDocumentsEnd(System.currentTimeMillis());

      if (CollectionUtils.isEmpty(deliveryDocumentList)) {
        logger.info(
            "Delivery doesn't have SSTK PO's, Hence ignoring label generation for delivery {}",
            deliveryUpdateMessage.getDeliveryNumber());
        deliveryEvent.setEventStatus(EventTargetStatus.DELETE);
        deliveryEventPersisterService.save(deliveryEvent);
        return;
      }
    }

    if (Objects.isNull(deliveryDetails)) {
      logger.info(
          "Failed to fetch delivery: {} details. Hence storing the event and ignoring pre-label generation",
          deliveryUpdateMessage.getDeliveryNumber());
      deliveryEvent.setEventStatus(EventTargetStatus.EVENT_PENDING);
      deliveryEventPersisterService.save(deliveryEvent);
      return;
    }

    try {
      TenantContext.get().setGenerateGenericLabelForSSTKStart(System.currentTimeMillis());
      generateGenericLabelForSSTK(deliveryEvent, deliveryDocumentList, deliveryUpdateMessage);
      TenantContext.get().setGenerateGenericLabelForSSTKEnd(System.currentTimeMillis());

      logger.info(
          "Pre generation successful for delivery no. {} and delivery event {}. Hence marking it as DELETE",
          deliveryUpdateMessage.getDeliveryNumber(),
          deliveryUpdateMessage.getEventType());
      deliveryEvent.setEventStatus(EventTargetStatus.DELETE);

      TenantContext.get().setDeliveryEventPersistDBStart(System.currentTimeMillis());
      deliveryEventPersisterService.save(deliveryEvent);
      TenantContext.get().setDeliveryEventPersistDBEnd(System.currentTimeMillis());
    } catch (Exception e) {
      logger.error(
          "{} exception occurred while generating pre-label for delivery no. {} and event {}. Hence marking delivery event as PENDING",
          e.getMessage(),
          deliveryUpdateMessage.getDeliveryNumber(),
          deliveryUpdateMessage.getEventType());
      deliveryEvent.setEventStatus(EventTargetStatus.EVENT_PENDING);
      TenantContext.get().setDeliveryEventPersistDBStart(System.currentTimeMillis());
      deliveryEventPersisterService.save(deliveryEvent);
      TenantContext.get().setDeliveryEventPersistDBEnd(System.currentTimeMillis());
    }
  }

  public void generateGenericLabelForSSTK(
      DeliveryEvent deliveryEvent,
      List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments,
      DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {
    switch (deliveryEvent.getEventType()) {
      case ReceivingConstants.EVENT_DOOR_ASSIGNED:
      case ReceivingConstants.EVENT_PO_ADDED:
        generateAndPublishSSTKLabels(deliveryDocuments, deliveryUpdateMessage, Boolean.FALSE, null);
        return;
      case ReceivingConstants.EVENT_PO_UPDATED:
        processPOUpdatedEvent(deliveryDocuments, deliveryUpdateMessage.getHttpHeaders());
        return;
      case ReceivingConstants.EVENT_PO_LINE_ADDED:
        processPOLineAddedEvent(deliveryDocuments, deliveryUpdateMessage);
        return;
      case ReceivingConstants.EVENT_PO_LINE_UPDATED:
        processPOLineUpdatedEvent(deliveryDocuments, deliveryUpdateMessage);
        return;
      default:
        logger.info(
            "Invalid event received for pre label generation : {}", deliveryEvent.getEventType());
    }
  }

  private void processPOLineUpdatedEvent(
      List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments,
      DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {
    for (com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument :
        deliveryDocuments) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        if (POLineStatus.RECEIVED
            .name()
            .equalsIgnoreCase(deliveryDocumentLine.getPurchaseReferenceLineStatus())) {
          logger.info(
              "PO {} line {} is received. Skipping label generation",
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
          return;
        }
        if (POLineStatus.CANCELLED
            .name()
            .equalsIgnoreCase(deliveryDocumentLine.getPurchaseReferenceLineStatus())) {
          logger.info(
              "PO {} line {} is cancelled. Voiding the Labels and Skipping label generation",
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
          List<LabelData> labelDataList =
              labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                  deliveryDocumentLine.getPurchaseReferenceNumber(),
                  deliveryDocumentLine.getPurchaseReferenceLineNumber());
          updateLabelStatusToCancelled(
              labelDataList,
              deliveryUpdateMessage.getHttpHeaders(),
              ReceivingConstants.PURCHASE_REF_TYPE_SSTK);
          return;
        }
      }
    }
    generateAndPublishSSTKLabels(deliveryDocuments, deliveryUpdateMessage, Boolean.TRUE, null);
  }

  private void processPOLineAddedEvent(
      List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments,
      DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {

    for (com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument :
        deliveryDocuments) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        if (POLineStatus.CANCELLED
            .name()
            .equalsIgnoreCase(deliveryDocumentLine.getPurchaseReferenceLineStatus())) {
          logger.info(
              "PO {} line {} is cancelled Ignoring the event",
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
          return;
        }
      }
    }
    generateAndPublishSSTKLabels(deliveryDocuments, deliveryUpdateMessage, Boolean.FALSE, null);
  }

  /**
   * Processes the PO updated event for the PO and delivery
   *
   * @param deliveryDocuments
   * @param httpHeaders
   */
  private void processPOUpdatedEvent(
      List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments,
      HttpHeaders httpHeaders) {
    com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument =
        deliveryDocuments.get(0);
    if (Objects.equals(deliveryDocument.getPurchaseReferenceStatus(), POStatus.HISTORY.name())) {
      logger.info(
          "PO : {} status is {}, cancelling the labels under the PO and voiding the lpns in Hawkeye",
          deliveryDocument.getPurchaseReferenceNumber(),
          deliveryDocument.getPurchaseReferenceStatus());
      List<LabelData> labelDataList =
          labelDataService.fetchByPurchaseReferenceNumberAndStatus(
              deliveryDocument.getPurchaseReferenceNumber(),
              LabelInstructionStatus.AVAILABLE.name());
      updateLabelStatusToCancelled(
          labelDataList, httpHeaders, ReceivingConstants.PURCHASE_REF_TYPE_SSTK);
    } else if (Objects.equals(
        deliveryDocument.getPurchaseReferenceStatus(), POStatus.CNCL.name())) {
      logger.info(
          "PO : {} status is Cancelled, ignoring label generation for the PO",
          deliveryDocument.getPurchaseReferenceNumber());
    }
  }

  /**
   * Update label status as cancelled in Labeldata & Void LPNs at Hawkeye end
   *
   * @param labelDataList
   * @param labelPurchaseRefType
   */
  public void updateLabelStatusToCancelled(
      List<LabelData> labelDataList, HttpHeaders httpHeaders, String labelPurchaseRefType) {
    if (CollectionUtils.isNotEmpty(labelDataList)) {
      labelDataList.forEach(
          labelData -> {
            labelData.setStatus(LabelInstructionStatus.CANCELLED.name());
          });
      TenantContext.get().setUpdateTrackingIdStatusCancelledStart(System.currentTimeMillis());
      Lists.partition(labelDataList, labelDataBatchSize)
          .forEach(labelDataBatch -> labelDataService.saveAllAndFlush(labelDataBatch));
      TenantContext.get().setUpdateTrackingIdStatusCancelledStart(System.currentTimeMillis());
    }

    // void labels only when the item is case pack
    if (CollectionUtils.isNotEmpty(labelDataList)) {
      List<String> trackingIdList =
          labelDataList.stream().map(LabelData::getTrackingId).collect(Collectors.toList());
      if (labelPurchaseRefType.equals(ReceivingConstants.PURCHASE_REF_TYPE_DA)
          && ObjectUtils.allNotNull(labelDataList.get(0).getVnpk(), labelDataList.get(0).getWhpk())
          && labelDataList.get(0).getVnpk().equals(labelDataList.get(0).getWhpk())) {
        rdcAsyncUtils.updateLabelStatusVoidToHawkeye(trackingIdList, httpHeaders);
      } else if (labelPurchaseRefType.equals(ReceivingConstants.PURCHASE_REF_TYPE_SSTK))
        rdcAsyncUtils.updateLabelStatusVoidToHawkeye(trackingIdList, httpHeaders);
    }
  }

  private String getDeliveryDetailsUrl(DeliveryUpdateMessage deliveryUpdateMessage) {
    String url = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI;
    Map<String, String> pathParams = new HashMap<>();
    Map<String, String> queryParameters = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryUpdateMessage.getDeliveryNumber());
    if (Objects.nonNull(deliveryUpdateMessage.getPoNumber())) {
      queryParameters.put(ReceivingConstants.PO_NUMBER, deliveryUpdateMessage.getPoNumber());
    }
    if (Objects.nonNull(deliveryUpdateMessage.getPoLineNumber())) {
      queryParameters.put(
          ReceivingConstants.PO_LINE_NUMBER,
          String.valueOf(deliveryUpdateMessage.getPoLineNumber()));
    }
    return ReceivingUtils.replacePathParamsAndQueryParams(url, pathParams, queryParameters)
        .toString();
  }

  /**
   * This method will process the new label to Hawkeye
   *
   * @param labelData
   * @param httpHeaders
   */
  @Timed(
      name = "publishNewLabelToHawkeyeTimed",
      level1 = "uwms-receiving",
      level2 = "RdcLabelGenerationService",
      level3 = "publishNewLabelToHawkeye")
  @ExceptionCounted(
      name = "publishNewLabelToHawkeyeTimedExceptionCount",
      level1 = "uwms-receiving",
      level2 = "RdcLabelGenerationService",
      level3 = "publishNewLabelToHawkeye")
  @Async
  public void publishNewLabelToHawkeye(LabelData labelData, HttpHeaders httpHeaders) {
    // Set TenantContext as this is an async method
    String facilityNum = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);
    String facilityCountryCode = httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE);
    if (Objects.isNull(facilityNum) || Objects.isNull(facilityCountryCode)) {
      logger.error(
          "Couldn't publish new label to Hawkeye as there are empty values for required headers facilityNum/facilityCountryCode");
      return;
    }
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false)
        && rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(
            labelData.getDeliveryNumber(), labelData.getItemNumber())) {
      InstructionDownloadMessageDTO instructionDownloadMessageDTO =
          new InstructionDownloadMessageDTO();
      instructionDownloadMessageDTO.setPoNumber(labelData.getPurchaseReferenceNumber());
      instructionDownloadMessageDTO.setPurchaseReferenceLineNumber(
          labelData.getPurchaseReferenceLineNumber());
      instructionDownloadMessageDTO.setItemNumber(labelData.getItemNumber());
      instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
      logger.info("Process and publish new label for lpn: {}", labelData.getTrackingId());
      Boolean isAtlasSendingUniqueLpnToHawkeyeEnabled =
          tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              RdcConstants.IS_ATLAS_SENDING_UNIQUE_LPN_TO_HAWKEYE_ENABLED,
              false);
      if (Boolean.FALSE.equals(isAtlasSendingUniqueLpnToHawkeyeEnabled)) {
        // If sending unique lpns to HE is disabled then publish label data for all deliveries with
        // the PO/Item combination
        List<LabelDownloadEvent> labelDownloadEventList =
            labelDownloadEventService.findByPurchaseReferenceNumberAndItemNumber(
                labelData.getPurchaseReferenceNumber(), labelData.getItemNumber());
        // TODO: Publish labelData to HE only when delivery status is ARV/OPN/WRK
        for (LabelDownloadEvent labelDownloadEvent : labelDownloadEventList) {
          instructionDownloadMessageDTO.setDeliveryNumber(labelDownloadEvent.getDeliveryNumber());
          processAndPublishLabelData(
              instructionDownloadMessageDTO,
              Collections.singletonList(labelData),
              labelDownloadEvent);
        }
      } else {
        // Publish label data only for one delivery
        instructionDownloadMessageDTO.setDeliveryNumber(labelData.getDeliveryNumber());
        processAndPublishLabelData(
            instructionDownloadMessageDTO, Collections.singletonList(labelData), null);
      }
    }
  }

  public void updateRejectReasonAndStatusInLabelDownloadEvent(
      LabelDownloadEvent labelDownloadEvent, RejectReason rejectReason) {
    if (Objects.nonNull(labelDownloadEvent)) {
      labelDownloadEvent.setRejectReason(rejectReason);
      labelDownloadEvent.setStatus(LabelDownloadEventStatus.PROCESSED.toString());
      labelDownloadEvent.setPublishedTs(new Date());
      labelDownloadEventService.saveAll(Collections.singletonList(labelDownloadEvent));
      logger.info(
          "Updated labelDownloadEvent for delivery:{} and item :{} with reject reason {}",
          labelDownloadEvent.getDeliveryNumber(),
          labelDownloadEvent.getItemNumber(),
          rejectReason);
    }
  }

  public void updatePossibleUpcInLabelData(
      List<LabelData> labelDataList, List<DeliveryDocument> deliveryDocuments) {
    PossibleUPC possibleUPC =
        rdcLabelGenerationUtils.getPossibleUPC(
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    labelDataList.stream().forEach(labelData -> labelData.setPossibleUPC(gson.toJson(possibleUPC)));
    labelDataService.saveAll(labelDataList);
  }
  /**
   * Label download event data insertion
   *
   * @param instructionDownloadMessageDTO
   */
  private LabelDownloadEvent saveLabelDownloadEvent(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO, String status) {
    LabelDownloadEvent labelDownloadEvent =
        transformLabelDownloadEventEntity(instructionDownloadMessageDTO, status);
    labelDownloadEventService.saveAll(Collections.singletonList(labelDownloadEvent));
    logger.info("Completed label download event data insertion");
    return labelDownloadEvent;
  }

  private LabelDownloadEvent transformLabelDownloadEventEntity(
      InstructionDownloadMessageDTO instructionDownloadMessageDTO, String status) {
    LabelDownloadEvent labelDownloadEvent = new LabelDownloadEvent();
    labelDownloadEvent.setFacilityNum(TenantContext.getFacilityNum());
    labelDownloadEvent.setFacilityCountryCode(TenantContext.getFacilityCountryCode());
    labelDownloadEvent.setDeliveryNumber((instructionDownloadMessageDTO.getDeliveryNumber()));
    labelDownloadEvent.setPurchaseReferenceNumber(
        instructionDownloadMessageDTO.getPoNumber().trim());
    labelDownloadEvent.setItemNumber(instructionDownloadMessageDTO.getItemNumber());
    labelDownloadEvent.setStatus(status);
    labelDownloadEvent.setMessagePayload(buildMessagePayload(instructionDownloadMessageDTO));
    return labelDownloadEvent;
  }

  private String buildMessagePayload(InstructionDownloadMessageDTO instructionDownloadMessageDTO) {
    InstructionDownloadMessageDTO messagePayload = new InstructionDownloadMessageDTO();
    messagePayload.setHttpHeaders(
        buildHeadersForMessagePayload(instructionDownloadMessageDTO.getHttpHeaders()));
    messagePayload.setPoNumber(instructionDownloadMessageDTO.getPoNumber().trim());
    messagePayload.setItemNumber(instructionDownloadMessageDTO.getItemNumber());
    messagePayload.setPurchaseReferenceLineNumber(
        instructionDownloadMessageDTO.getPurchaseReferenceLineNumber());
    messagePayload.setDeliveryNumber(instructionDownloadMessageDTO.getDeliveryNumber());
    messagePayload.setBlobStorage(instructionDownloadMessageDTO.getBlobStorage());
    return gson.toJson(messagePayload);
  }

  public HttpHeaders buildHeadersForMessagePayload(HttpHeaders payloadHeaders) {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.put(
        ReceivingConstants.TENENT_FACLITYNUM,
        Collections.singletonList(payloadHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM)));
    httpHeaders.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        Collections.singletonList(payloadHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE)));
    httpHeaders.put(
        ReceivingConstants.TENENT_GROUP_TYPE,
        Collections.singletonList(payloadHeaders.getFirst(ReceivingConstants.TENENT_GROUP_TYPE)));
    httpHeaders.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        Collections.singletonList(
            payloadHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY)));
    httpHeaders.put(
        ReceivingConstants.USER_ID_HEADER_KEY,
        Collections.singletonList(payloadHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY)));
    httpHeaders.put(
        ReceivingConstants.EVENT_TYPE,
        Collections.singletonList(payloadHeaders.getFirst(ReceivingConstants.EVENT_TYPE)));
    return httpHeaders;
  }

  private void calculateAndLogElapsedTimeSummary4labelDownload() {
    long timeTakenForLabelDownload =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getLabelDownloadStart(), TenantContext.get().getLabelDownloadEnd());
    long timeTakenForLabelDataFetchForDuplicateLabelDataList =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getLabelDataFetchWithPoAndPoLineAndItemNumberAndStatusStart(),
            TenantContext.get().getLabelDataFetchWithPoAndPoLineAndItemNumberAndStatusEnd());
    long timeTakenForGdmDeliveryInfoByDeliveryItemNumberCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getGdmDeliveryInfoByDeliveryItemNumberCallStart(),
            TenantContext.get().getGdmDeliveryInfoByDeliveryItemNumberCallEnd());
    long timeTakenForLabelConstructAndPublishtoHawkeye =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getLabelConstructAndPublishToHawkeyeStart(),
            TenantContext.get().getLabelConstructAndPublishToHawkeyeEnd());
    long timeTakenForLabelDownloadEventPersistUpdateProcessed =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getLabelDownloadEventPersistStart(),
            TenantContext.get().getLabelDownloadEventPersistEnd());
    long timeTakenForLabelDownloadUpdatePossibleUpc =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getLabelDataPossibleUpcPersistStart(),
            TenantContext.get().getLabelDataPossibleUpcPersistEnd());
    long overallTimeTakenForLabelDownloadIncludingAutomation =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getLabelDownloadEventIncludingAutomationStart(),
            TenantContext.get().getLabelDownloadEventIncludingAutomationEnd());

    logger.warn(
        "LatencyCheck LabelDownloadToHawkeye at ts={} time in overallTimeTakenForLabelDownloadIncludingAutomation={}, timeTakenForLabelDownload={}, "
            + "timeTakenForLabelDataFetchForDuplicateLabelDataList={}, timeTakenForGdmDeliveryInfoByDeliveryItemNumberCall={}, "
            + "timeTakenForLabelConstructAndPublishtoHawkeye={}, timeTakenForLabelDownloadEventPersistUpdateProcessed = {}, "
            + "timeTakenForLabelDownloadUpdatePossibleUpc = {} and correlationId={}",
        TenantContext.get().getLabelDownloadStart(),
        overallTimeTakenForLabelDownloadIncludingAutomation,
        timeTakenForLabelDownload,
        timeTakenForLabelDataFetchForDuplicateLabelDataList,
        timeTakenForGdmDeliveryInfoByDeliveryItemNumberCall,
        timeTakenForLabelConstructAndPublishtoHawkeye,
        timeTakenForLabelDownloadEventPersistUpdateProcessed,
        timeTakenForLabelDownloadUpdatePossibleUpc,
        TenantContext.getCorrelationId());
  }

  /** This method captures all timetaken for SSTK Label Generation */
  private void calculateAndLogElapsedTimeSummary4SSTKLabelGenerationAndPublish() {

    long timeTakenForFetchSSTKAutoDeliveryDetails =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchSSTKAutoDeliveryDetailsStart(),
            TenantContext.get().getFetchSSTKAutoDeliveryDetailsEnd());

    long timeTakenForValidateDeliveryEventAndPublishStatus =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchSSTKAutoDeliveryDetailsStart(),
            TenantContext.get().getFetchSSTKAutoDeliveryDetailsEnd());

    long timeTakenForGenerateGenericLabelForSSTK =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getGenerateGenericLabelForSSTKStart(),
            TenantContext.get().getGenerateGenericLabelForSSTKEnd());

    long timeTakenForFetchAndFilterSSTKDeliveryDocuments =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchAndFilterSSTKDeliveryDocumentsStart(),
            TenantContext.get().getFetchAndFilterSSTKDeliveryDocumentsEnd());
    long timeTakenForDeliveryEventPersistDB =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getDeliveryEventPersistDBStart(),
            TenantContext.get().getDeliveryEventPersistDBEnd());

    long timeTakenForSmartSlottingPrimeSlotDetails =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getSmartSlottingPrimeSlotDetailsStart(),
            TenantContext.get().getSmartSlottingPrimeSlotDetailsEnd());

    logger.warn(
        "LatencyCheck SSTK Label Generation: timeTakenForfetchSSTKAutoDeliveryDetails={},  "
            + "timeTakenForValidateDeliveryEventAndPublishStatusStart = {},"
            + "timeTakenForGenerateGenericLabelForSSTKStart = {}"
            + "timeTakenForFetchAndFilterSSTKDeliveryDocuments = {}"
            + "timeTakenForDeliveryEventPersistDB = {}"
            + "timeTakenForSmartSlottingPrimeSlotDetails = {}"
            + "timeTakenForSmartSlottingPrimeSlotDetails = {}"
            + "and correlationId={}",
        TenantContext.get().getLabelDownloadStart(),
        timeTakenForFetchSSTKAutoDeliveryDetails,
        timeTakenForValidateDeliveryEventAndPublishStatus,
        timeTakenForGenerateGenericLabelForSSTK,
        timeTakenForFetchAndFilterSSTKDeliveryDocuments,
        timeTakenForDeliveryEventPersistDB,
        timeTakenForSmartSlottingPrimeSlotDetails,
        TenantContext.getCorrelationId());
  }

  private String buildMessagePayloadForItemUpdate(ItemUpdateMessage itemUpdateMessage) {
    itemUpdateMessage.setHttpHeaders(
        buildHeadersForMessagePayload(itemUpdateMessage.getHttpHeaders()));
    itemUpdateMessage.setActiveDeliveries(Collections.emptyList());
    return gson.toJson(itemUpdateMessage);
  }

  private LabelDownloadEvent transformLabelDownloadEventEntity(
      ItemUpdateMessage itemUpdateMessage,
      Long deliveryNumber,
      String poNumber,
      String status,
      String miscInfo) {
    return LabelDownloadEvent.builder()
        .deliveryNumber(deliveryNumber)
        .messagePayload(buildMessagePayloadForItemUpdate(itemUpdateMessage))
        .itemNumber(Long.valueOf(itemUpdateMessage.getItemNumber()))
        .purchaseReferenceNumber(poNumber)
        .miscInfo(miscInfo)
        .status(status)
        .build();
  }

  public List<LabelDownloadEvent> saveLabelDownloadEventsForItemUpdate(
      Map<String, Set<Long>> poToDeliveryMap,
      String status,
      ItemUpdateMessage itemUpdateMessage,
      boolean isSSTKLabelType) {
    TenantContext.get().setLabelDownloadEventPersistStart(System.currentTimeMillis());
    List<LabelDownloadEvent> labelDownloadEventList =
        fetchAllLabelDownloadEvents(
            poToDeliveryMap, itemUpdateMessage, status, false, isSSTKLabelType);
    labelDownloadEventService.saveAll(labelDownloadEventList);
    logger.info("Completed label download event data insertion");
    TenantContext.get().setLabelDownloadEventPersistEnd(System.currentTimeMillis());
    return labelDownloadEventList;
  }

  private LabelDataMiscInfo buildLabelDataMiscInfo(
      DeliveryDocumentLine deliveryDocumentLine, boolean isSymEligible) {
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    LabelDataMiscInfo labelDataMiscInfo = new LabelDataMiscInfo();
    labelDataMiscInfo.setPurchaseRefType(deliveryDocumentLine.getPurchaseRefType());
    labelDataMiscInfo.setSymEligible(isSymEligible);
    if (Objects.nonNull(itemData)) {
      labelDataMiscInfo.setLocation(itemData.getPrimeSlot());
      labelDataMiscInfo.setAsrsAlignment(itemData.getAsrsAlignment());
      labelDataMiscInfo.setSlotType(itemData.getSlotType());
      if (Objects.nonNull(itemData.getPrimeSlotSize())) {
        labelDataMiscInfo.setLocationSize(itemData.getPrimeSlotSize());
      }
    }
    return labelDataMiscInfo;
  }

  public void updateStatusInLabelDownloadEvent(LabelDownloadEvent labelDownloadEvent) {
    if (Objects.nonNull(labelDownloadEvent)) {
      labelDownloadEvent.setStatus(LabelDownloadEventStatus.PROCESSED.toString());
      labelDownloadEvent.setPublishedTs(new Date());
      labelDownloadEventService.saveAll(Collections.singletonList(labelDownloadEvent));
      logger.info(
          "Updated labelDownloadEvent for delivery:{} and item :{} with reject reason {}",
          labelDownloadEvent.getDeliveryNumber(),
          labelDownloadEvent.getItemNumber(),
          labelDownloadEvent.getRejectReason());
    }
  }

  public void buildPayloadAndPublishLabelsToHawkeye(
      DeliveryDocumentLine deliveryDocumentLine,
      List<LabelData> labelDataList,
      RejectReason rejectReason,
      HttpHeaders headers,
      Long deliveryNumber) {
    ACLLabelDataTO aclLabelDataTO =
        rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            deliveryDocumentLine, labelDataList, rejectReason, deliveryNumber);
    aclLabelDataTO.setDeliveryNumber(aclLabelDataTO.getGroupNumber());
    headers.add(ReceivingConstants.DELIVERY_NUMBER, String.valueOf(deliveryNumber));
    publishACLLabelData(aclLabelDataTO, headers);
  }

  @Async
  public void fetchLabelDataAndUpdateLabelStatusToCancelled(
      com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders,
      int quantity) {
    List<LabelData> labelDataList =
        labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name());
    List<LabelData> labelDataListToCancel =
        labelDataList
            .stream()
            .sorted(Comparator.comparingLong(LabelData::getLabelSequenceNbr).reversed())
            .limit(quantity)
            .collect(Collectors.toList());

    updateLabelStatusToCancelled(
        labelDataListToCancel, httpHeaders, ReceivingConstants.PURCHASE_REF_TYPE_SSTK);
  }

  private int getLabelDataCount(List<LabelData> labelDataList, LabelType labelType) {
    return (int)
        labelDataList
            .stream()
            .filter(
                labelData ->
                    labelData.getLabelType().name().equals(labelType.name())
                        && (labelData.getStatus().equals(LabelInstructionStatus.AVAILABLE.name())
                            || labelData
                                .getStatus()
                                .equals(LabelInstructionStatus.COMPLETE.name())))
            .count();
  }

  /**
   * This method returns true if label generation through scheduler is successful for the delivery
   *
   * @param deliveryEvent
   * @return
   */
  public boolean processDeliveryEventForScheduler(DeliveryEvent deliveryEvent) {
    deliveryEvent.setEventStatus(EventTargetStatus.EVENT_IN_PROGRESS);
    deliveryEventPersisterService.save(deliveryEvent);
    DeliveryDetails deliveryDetails =
        rdcSSTKLabelGenerationUtils.fetchDeliveryDetails(
            deliveryEvent.getUrl(), deliveryEvent.getDeliveryNumber());
    if (Objects.isNull(deliveryDetails)) {
      logger.info(
          "Failed to fetch delivery: {} details. Hence storing the event and ignoring pre-label generation",
          deliveryEvent.getDeliveryNumber());
      deliveryEvent.setEventStatus(EventTargetStatus.EVENT_PENDING);
      deliveryEventPersisterService.save(deliveryEvent);
      return false;
    }
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocumentList =
        fetchAndFilterSSTKDeliveryDocuments(deliveryDetails, ReceivingUtils.getHeaders());
    if (CollectionUtils.isEmpty(deliveryDocumentList)) {
      logger.info(
          "DeliveryDocuments doesn't have SSTK PO's, Hence ignoring label generation for delivery {} and Event {}",
          deliveryEvent.getDeliveryNumber(),
          deliveryEvent.getEventType());
      deliveryEvent.setEventStatus(EventTargetStatus.DELETE);
      deliveryEventPersisterService.save(deliveryEvent);
      return true;
    }
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber(String.valueOf(deliveryEvent.getDeliveryNumber()))
            .eventType(deliveryEvent.getEventType())
            .httpHeaders(ReceivingUtils.getHeaders())
            .build();
    try {
      generateGenericLabelForSSTK(
          deliveryEvent, deliveryDetails.getDeliveryDocuments(), deliveryUpdateMessage);
      logger.info(
          "Pre generation successful for delivery no. {} and delivery event {}. Hence marking it as DELETE",
          deliveryUpdateMessage.getDeliveryNumber(),
          deliveryUpdateMessage.getEventType());
      deliveryEvent.setEventStatus(EventTargetStatus.DELETE);
      deliveryEventPersisterService.save(deliveryEvent);
      return true;
    } catch (Exception e) {
      logger.error(
          "{} exception occurred while generating pre-label for delivery no. {} and event {}. Hence marking delivery event as PENDING",
          e.getMessage(),
          deliveryUpdateMessage.getDeliveryNumber(),
          deliveryUpdateMessage.getEventType());
      deliveryEvent.setEventStatus(EventTargetStatus.EVENT_PENDING);
      deliveryEventPersisterService.save(deliveryEvent);
    }
    return false;
  }

  /**
   * This method is to publish labels to Hawkeye Asynchronously
   *
   * @param deliveryNumber
   * @param itemNumber
   * @param purchaseReferenceNumberSet
   * @param labelDataList
   * @param httpHeaders
   */
  @Async
  public void processAndPublishLabelDataAsync(
      Long deliveryNumber,
      Long itemNumber,
      Set<String> purchaseReferenceNumberSet,
      List<LabelData> labelDataList,
      HttpHeaders httpHeaders) {
    TenantContext.setFacilityNum(
        Integer.valueOf(
            Objects.requireNonNull(httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM)).get(0)));
    TenantContext.setFacilityCountryCode(
        Objects.requireNonNull(httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE)).get(0));

    for (String purchaseReferenceNumber : purchaseReferenceNumberSet) {
      List<LabelData> filteredLabelDataList =
          labelDataList
              .stream()
              .filter(
                  labelData ->
                      purchaseReferenceNumber.equals(labelData.getPurchaseReferenceNumber()))
              .collect(Collectors.toList());
      if (CollectionUtils.isNotEmpty(filteredLabelDataList)) {
        InstructionDownloadMessageDTO instructionDownloadMessageDTO =
            buildInstructionDownloadMessageDTO(
                deliveryNumber, itemNumber, purchaseReferenceNumber, httpHeaders);
        LabelDownloadEvent labelDownloadEvent =
            saveLabelDownloadEvent(
                instructionDownloadMessageDTO, LabelDownloadEventStatus.IN_PROGRESS.name());
        logger.info(
            "Republishing Labels to hawkeye for deliveryNumber: {}, itemNumber: {} and purchaseReferenceNumber: {}",
            instructionDownloadMessageDTO.getDeliveryNumber(),
            instructionDownloadMessageDTO.getItemNumber(),
            instructionDownloadMessageDTO.getPoNumber());
        processAndPublishLabelData(
            instructionDownloadMessageDTO, filteredLabelDataList, labelDownloadEvent);
      }
    }
  }

  private InstructionDownloadMessageDTO buildInstructionDownloadMessageDTO(
      Long deliveryNumber,
      Long itemNumber,
      String purchaseReferenceNumber,
      HttpHeaders httpHeaders) {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new InstructionDownloadMessageDTO();
    instructionDownloadMessageDTO.setDeliveryNumber(deliveryNumber);
    instructionDownloadMessageDTO.setItemNumber(itemNumber);
    instructionDownloadMessageDTO.setPoNumber(purchaseReferenceNumber);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    return instructionDownloadMessageDTO;
  }
}
