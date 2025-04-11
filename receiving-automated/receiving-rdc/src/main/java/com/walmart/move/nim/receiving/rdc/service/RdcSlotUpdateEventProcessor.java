package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ITEM_PRIME_DELETE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ITEM_PRIME_DETAILS;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeItemUpdateRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.LabelDownloadEventMiscInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcSlotUpdateMessage;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.LabelDownloadEventService;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelDownloadEventStatus;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.utils.RdcLabelGenerationUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcSSTKLabelGenerationUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.RDC_SLOT_UPDATE_EVENT_PROCESSOR)
public class RdcSlotUpdateEventProcessor implements EventProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcSlotUpdateEventProcessor.class);
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired LabelDownloadEventService labelDownloadEventService;
  @Autowired RdcSSTKLabelGenerationUtils rdcSSTKLabelGenerationUtils;
  @Autowired RdcLabelGenerationService rdcLabelGenerationService;
  @Autowired private Gson gson;
  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired private LabelDataService labelDataService;
  @Autowired private RdcLabelGenerationUtils rdcLabelGenerationUtils;

  @Override
  public void processEvent(MessageData messageData) {
    try {
      RdcSlotUpdateMessage rdcSlotUpdateMessage = (RdcSlotUpdateMessage) messageData;
      HttpHeaders httpHeaders = rdcSlotUpdateMessage.getHttpHeaders();
      List<String> eventTypeList = httpHeaders.get(ReceivingConstants.EVENT_TYPE);
      String eventType =
          CollectionUtils.isNotEmpty(eventTypeList)
              ? eventTypeList.get(0)
              : ReceivingConstants.EMPTY_STRING;
      switch (eventType) {
        case ITEM_PRIME_DETAILS:
          processPrimeSlotDetailsEvent(rdcSlotUpdateMessage);
          break;
        case ITEM_PRIME_DELETE:
          processPrimeSlotDeleteEvent(rdcSlotUpdateMessage);
          break;
        default:
          LOGGER.error("Invalid Slot update event type {}, skipping process", eventType);
          break;
      }
    } catch (Exception exception) {
      LOGGER.error("Error occurred during slot update processEvent", exception);
    }
  }

  /**
   * If the slot is updated from conventional to sym slot and reject reason is RDC_SSTK in
   * LabelDownloadEvent then labels are generated for PO and item, and if the slot is updated from
   * sym slot to conventional slot then item update with reject reason RDC_SSTK and labels for the
   * PO and item are voided in Hawkeye and cancelled in LabelData.
   *
   * @param rdcSlotUpdateMessage
   */
  private void processPrimeSlotDetailsEvent(RdcSlotUpdateMessage rdcSlotUpdateMessage) {
    HttpHeaders httpHeaders = rdcSlotUpdateMessage.getHttpHeaders();
    List<LabelDownloadEvent> labelDownloadEventList =
        fetchAndFilterLabelDownloadEvent(rdcSlotUpdateMessage.getItemNbr());
    labelDownloadEventList =
        rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(labelDownloadEventList);
    if (CollectionUtils.isNotEmpty(labelDownloadEventList)) {
      Set<Long> deliveryList = fetchDeliveries(labelDownloadEventList);
      Boolean isAtlasItemSymEligible =
          rdcSSTKLabelGenerationUtils.isAsrsAlignmentSymEligible(
              rdcSlotUpdateMessage.getAsrsAlignment());
      RejectReason rejectReason = labelDownloadEventList.get(0).getRejectReason();
      if (Boolean.TRUE.equals(isAtlasItemSymEligible)
          && (Objects.nonNull(rejectReason))
          && rejectReason.equals(RejectReason.RDC_SSTK)) {
        generateAndPublishLabels(rdcSlotUpdateMessage, deliveryList);
      } else if (Boolean.FALSE.equals(isAtlasItemSymEligible)
          && (Objects.isNull(rejectReason) || !rejectReason.equals(RejectReason.RDC_SSTK))) {
        updateLabelStatusToCancelledAndVoidInHawkeye(labelDownloadEventList, httpHeaders);
        updateRejectReasonToHawkeye(
            rdcSlotUpdateMessage, deliveryList, labelDownloadEventList, RejectReason.RDC_SSTK);
      }
    }
  }

  /**
   * If the deleted prime slot for item is sym eligible and if item is valid for label generation
   * then labels are cancelled and reject reason is set in LabelDownloadEvent. If the deleted prime
   * slot is conventional slot then no action is taken
   *
   * @param rdcSlotUpdateMessage
   */
  private void processPrimeSlotDeleteEvent(RdcSlotUpdateMessage rdcSlotUpdateMessage) {
    List<LabelDownloadEvent> labelDownloadEventList =
        fetchAndFilterLabelDownloadEvent(rdcSlotUpdateMessage.getItemNbr());
    labelDownloadEventList =
        rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(labelDownloadEventList);
    if (CollectionUtils.isNotEmpty(labelDownloadEventList)) {
      Set<Long> deliveryList = fetchDeliveries(labelDownloadEventList);
      Boolean isAsrsAlignmentSymEligible =
          rdcSSTKLabelGenerationUtils.isAsrsAlignmentSymEligible(
              rdcSlotUpdateMessage.getAsrsAlignment());
      RejectReason rejectReason = labelDownloadEventList.get(0).getRejectReason();
      if (Boolean.TRUE.equals(isAsrsAlignmentSymEligible)
          && isLabelsGeneratedForItem(rejectReason)) {
        // If reject reason is BREAKOUT, as no labels are generated only the reject reason needs to
        // be updated in label download event and Hawkeye
        if (!isBreakoutRejectReason(rejectReason)) {
          updateLabelStatusToCancelledAndVoidInHawkeye(
              labelDownloadEventList, rdcSlotUpdateMessage.getHttpHeaders());
        }
        updateRejectReasonToHawkeye(
            rdcSlotUpdateMessage, deliveryList, labelDownloadEventList, RejectReason.RDC_SSTK);
      }
    }
  }

  /**
   * This method validates if the labels are generated for the item by validating the rejectReason.
   * If reject reason is RDC_SSTK, then no action needs to be taken.
   *
   * @param rejectReason
   * @return
   */
  private boolean isLabelsGeneratedForItem(RejectReason rejectReason) {
    return Objects.isNull(rejectReason) || !(rejectReason.equals(RejectReason.RDC_SSTK));
  }

  /**
   * This method validates if the reject reason is Breakout
   *
   * @param rejectReason
   * @return
   */
  private boolean isBreakoutRejectReason(RejectReason rejectReason) {
    return Objects.nonNull(rejectReason) && (rejectReason.equals(RejectReason.BREAKOUT));
  }
  /**
   * This method generates and publishes labels for every delivery, SSTK PO and item.
   *
   * @param rdcSlotUpdateMessage
   * @param deliveryList
   */
  private void generateAndPublishLabels(
      RdcSlotUpdateMessage rdcSlotUpdateMessage, Set<Long> deliveryList) {
    rdcSlotUpdateMessage.setHttpHeaders(
        rdcLabelGenerationService.buildHeadersForMessagePayload(
            rdcSlotUpdateMessage.getHttpHeaders()));
    deliveryList.forEach(
        deliveryNumber -> {
          DeliveryDetails deliveryDetails =
              rdcSSTKLabelGenerationUtils.fetchDeliveryDetails(
                  getDeliveryDetailsUrl(String.valueOf(deliveryNumber)), deliveryNumber);
          if (Objects.nonNull(deliveryDetails)) {
            TenantContext.get()
                .setFetchAndFilterSSTKDeliveryDocumentsStart(System.currentTimeMillis());
            List<DeliveryDocument> deliveryDocumentList =
                fetchAndFilterSSTKDeliveryDocuments(
                    deliveryDetails,
                    rdcSlotUpdateMessage.getHttpHeaders(),
                    rdcSlotUpdateMessage.getItemNbr());
            populatePrimeSlotInfoInDeliveryDocumentLine(deliveryDocumentList, rdcSlotUpdateMessage);
            TenantContext.get()
                .setFetchAndFilterSSTKDeliveryDocumentsEnd(System.currentTimeMillis());
            DeliveryUpdateMessage deliveryUpdateMessage =
                DeliveryUpdateMessage.builder()
                    .httpHeaders(rdcSlotUpdateMessage.getHttpHeaders())
                    .build();
            try {
              rdcLabelGenerationService.generateAndPublishSSTKLabels(
                  deliveryDocumentList, deliveryUpdateMessage, Boolean.FALSE, rdcSlotUpdateMessage);
            } catch (ReceivingException e) {
              LOGGER.error(
                  "{} exception occurred while generating pre-label for delivery no. {} and item {} during slot update",
                  e.getMessage(),
                  deliveryNumber,
                  rdcSlotUpdateMessage.getItemNbr());
            }
          }
        });
  }

  /**
   * Updates reject reason in labelDownloadEvent and sends update to Hawkeye
   *
   * @param rdcSlotUpdateMessage
   * @param rejectReason
   * @param deliveryList
   */
  private void updateRejectReasonToHawkeye(
      RdcSlotUpdateMessage rdcSlotUpdateMessage,
      Set<Long> deliveryList,
      List<LabelDownloadEvent> labelDownloadEventList,
      RejectReason rejectReason) {
    List<LabelDownloadEvent> generatedLabelDownloadEvents =
        createLabelDownloadEvents(labelDownloadEventList, rejectReason, rdcSlotUpdateMessage);
    deliveryList.forEach(
        deliveryNumber -> {
          HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest =
              RdcUtils.createHawkeyeItemUpdateRequest(
                  rdcSlotUpdateMessage.getItemNbr(), deliveryNumber, rejectReason, null, true);
          hawkeyeRestApiClient.itemUpdateToHawkeye(
              hawkeyeItemUpdateRequest, rdcSlotUpdateMessage.getHttpHeaders());
        });
    TenantContext.get().setLabelDownloadEventPersistStart(System.currentTimeMillis());
    labelDownloadEventService.saveAll(generatedLabelDownloadEvents);
    TenantContext.get().setLabelDownloadEventPersistEnd(System.currentTimeMillis());
  }

  /**
   * This method updates label data status to Cancelled and voids labels in Hawkeye for all PO and
   * item
   *
   * @param labelDownloadEvents
   * @param headers
   */
  private void updateLabelStatusToCancelledAndVoidInHawkeye(
      List<LabelDownloadEvent> labelDownloadEvents, HttpHeaders headers) {
    Set<String> purchaseReferenceNumbers =
        labelDownloadEvents
            .parallelStream()
            .map(LabelDownloadEvent::getPurchaseReferenceNumber)
            .collect(Collectors.toSet());
    Long itemNumber = labelDownloadEvents.get(0).getItemNumber();
    purchaseReferenceNumbers.forEach(
        purchaseReferenceNumber -> {
          List<LabelData> labelDataList =
              labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
                  purchaseReferenceNumber, itemNumber, LabelInstructionStatus.AVAILABLE.name());
          rdcLabelGenerationService.updateLabelStatusToCancelled(
              labelDataList, headers, ReceivingConstants.PURCHASE_REF_TYPE_SSTK);
        });
  }

  /**
   * This method creates new labelDownloadEvents from the existing events
   *
   * @param labelDownloadEventList
   * @param rejectReason
   * @param rdcSlotUpdateMessage
   * @return
   */
  private List<LabelDownloadEvent> createLabelDownloadEvents(
      List<LabelDownloadEvent> labelDownloadEventList,
      RejectReason rejectReason,
      RdcSlotUpdateMessage rdcSlotUpdateMessage) {
    List<LabelDownloadEvent> generatedLabelDownloadEvents = new ArrayList<>();
    Map<String, Set<Long>> poToDeliveryMap =
        rdcSSTKLabelGenerationUtils.buildPoDeliveryMap(labelDownloadEventList);
    LabelDownloadEventMiscInfo labelDownloadEventMiscInfo = new LabelDownloadEventMiscInfo();
    labelDownloadEventMiscInfo.setLabelType(ReceivingConstants.PURCHASE_REF_TYPE_SSTK);
    poToDeliveryMap.forEach(
        (poNumber, deliveryNumberSet) ->
            deliveryNumberSet.forEach(
                deliveryNumber ->
                    generatedLabelDownloadEvents.add(
                        LabelDownloadEvent.builder()
                            .deliveryNumber(deliveryNumber)
                            .itemNumber(rdcSlotUpdateMessage.getItemNbr())
                            .messagePayload(buildMessagePayloadForSlotUpdate(rdcSlotUpdateMessage))
                            .purchaseReferenceNumber(poNumber)
                            .rejectReason(rejectReason)
                            .miscInfo(gson.toJson(labelDownloadEventMiscInfo))
                            .status(LabelDownloadEventStatus.PROCESSED.name())
                            .build())));
    return generatedLabelDownloadEvents;
  }

  private String buildMessagePayloadForSlotUpdate(RdcSlotUpdateMessage rdcSlotUpdateMessage) {
    rdcSlotUpdateMessage.setHttpHeaders(
        rdcLabelGenerationService.buildHeadersForMessagePayload(
            rdcSlotUpdateMessage.getHttpHeaders()));
    return gson.toJson(rdcSlotUpdateMessage);
  }

  /**
   * Fetches and filters active and PROCESSED and SSTK deliveries from the labelDownloadEvent
   *
   * @param itemNumber
   * @return
   */
  private List<LabelDownloadEvent> fetchAndFilterLabelDownloadEvent(long itemNumber) {
    Calendar calendar =
        Calendar.getInstance(TimeZone.getTimeZone(ReceivingConstants.UTC_TIME_ZONE));
    Date currentDate = calendar.getTime();
    int itemUpdateHawkeyeDeliveriesDayLimit =
        Objects.nonNull(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit())
            ? rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()
            : RdcConstants.ITEM_UPDATE_HAWKEYE_DELIVERIES_DAY_LIMIT;
    Date dateRangeStart =
        Date.from(
            currentDate.toInstant().minus(itemUpdateHawkeyeDeliveriesDayLimit, ChronoUnit.DAYS));
    List<LabelDownloadEvent> labelDownloadEventList =
        labelDownloadEventService.findByItemNumber(itemNumber);
    return labelDownloadEventList
        .stream()
        .filter(
            labelDownloadEvent ->
                labelDownloadEvent.getCreateTs().after(dateRangeStart)
                    && labelDownloadEvent
                        .getStatus()
                        .equals(LabelDownloadEventStatus.PROCESSED.toString())
                    && rdcSSTKLabelGenerationUtils.isSSTKLabelDownloadEvent(labelDownloadEvent))
        .sorted(Comparator.comparing(LabelDownloadEvent::getCreateTs).reversed())
        .collect(Collectors.toList());
  }

  public Set<Long> fetchDeliveries(List<LabelDownloadEvent> labelDownloadEventList) {
    return labelDownloadEventList
        .stream()
        .map(LabelDownloadEvent::getDeliveryNumber)
        .collect(Collectors.toSet());
  }

  private String getDeliveryDetailsUrl(String deliveryNumber) {
    String url = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI;
    Map<String, String> pathParams = new HashMap<>();
    Map<String, String> queryParameters = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    return ReceivingUtils.replacePathParamsAndQueryParams(url, pathParams, queryParameters)
        .toString();
  }

  /**
   * This method fetches delivery documents and filters SSTK documents and deliveryDocumentLine with
   * itemNumber
   *
   * @param deliveryDetails
   * @param httpHeaders
   * @param itemNumber
   * @return
   */
  public List<DeliveryDocument> fetchAndFilterSSTKDeliveryDocuments(
      DeliveryDetails deliveryDetails, HttpHeaders httpHeaders, Long itemNumber) {
    List<DeliveryDocument> filteredSSTKDocuments =
        rdcLabelGenerationService.fetchAndFilterSSTKDeliveryDocuments(deliveryDetails, httpHeaders);
    filteredSSTKDocuments
        .stream()
        .forEach(
            deliveryDocument -> {
              List<DeliveryDocumentLine> filteredSSTKDeliveryDocumentLines =
                  deliveryDocument
                      .getDeliveryDocumentLines()
                      .stream()
                      .filter(line -> line.getItemNbr().equals(itemNumber))
                      .collect(Collectors.toList());
              deliveryDocument.setDeliveryDocumentLines(filteredSSTKDeliveryDocumentLines);
            });
    return filteredSSTKDocuments;
  }

  private void populatePrimeSlotInfoInDeliveryDocumentLine(
      List<DeliveryDocument> deliveryDocuments, RdcSlotUpdateMessage rdcSlotUpdateMessage) {
    deliveryDocuments.forEach(
        deliveryDocument ->
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(
                    deliveryDocumentLine -> {
                      ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
                      itemData.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
                      itemData.setPrimeSlot(rdcSlotUpdateMessage.getPrimeSlotId());
                      itemData.setAsrsAlignment(rdcSlotUpdateMessage.getAsrsAlignment());
                    }));
  }
}
