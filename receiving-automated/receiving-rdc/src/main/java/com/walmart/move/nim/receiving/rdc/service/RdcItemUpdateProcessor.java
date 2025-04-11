package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.X_BLOCK_ITEM_HANDLING_CODES;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeItemUpdateRequest;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.message.common.ActiveDeliveryMessage;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateEventType;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.service.DeliveryDocumentsSearchHandler;
import com.walmart.move.nim.receiving.core.service.LabelDownloadEventService;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelDownloadEventStatus;
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
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class RdcItemUpdateProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcItemUpdateProcessor.class);

  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired private LabelDownloadEventService labelDownloadEventService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcLabelGenerationService rdcLabelGenerationService;
  @Autowired private ItemUpdateUtils itemUpdateUtils;
  @Autowired private RdcLabelGenerationUtils rdcLabelGenerationUtils;
  @Autowired private Gson gson;
  @Autowired private RdcSSTKLabelGenerationUtils rdcSSTKLabelGenerationUtils;

  @Override
  public void processEvent(MessageData messageData) {
    ItemUpdateMessage itemUpdateMessage = (ItemUpdateMessage) messageData;
    try {
      ItemUpdateEventType itemUpdateEventType =
          ItemUpdateEventType.valueOf(itemUpdateMessage.getEventType());
      if (Objects.isNull(itemUpdateMessage.getItemNumber())) {
        LOGGER.info(
            "Not processing item update event :{} because of missing Item number",
            itemUpdateEventType);
        return;
      }

      List<Long> deliveries = getDeliveriesFromItemUpdateMessage(itemUpdateMessage);

      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
          false)) {
        TenantContext.get().setGdmItemUpdateEventStart(System.currentTimeMillis());
        switch (itemUpdateEventType) {
          case HANDLING_CODE_UPDATE:
            LOGGER.info(
                "Processing the item handling code update event for itemNumber: {}",
                itemUpdateMessage.getItemNumber());
            if (Boolean.TRUE.equals(isCasePackItem(itemUpdateMessage, deliveries))) {
              processHandlingCodeUpdateEvent(itemUpdateMessage);
            }
            break;
          case CATALOG_GTIN_UPDATE:
            LOGGER.info(
                "Processing the item catalog gtin update event for itemNumber: {}",
                itemUpdateMessage.getItemNumber());
            processCatalogGtinUpdateEvent(itemUpdateMessage);
            break;
          default:
            LOGGER.info("Item update event type {} will not be processed", itemUpdateEventType);
        }
        TenantContext.get().setGdmItemUpdateEventEnd(System.currentTimeMillis());
        calculateAndLogElapsedTimeSummaryForItemUpdate();
      }
    } catch (Exception e) {
      LOGGER.error("Error while processing item update event", e);
    }
  }

  private void calculateAndLogElapsedTimeSummaryForItemUpdate() {
    Long timeTakenForItemUpdate =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getGdmItemUpdateEventStart(),
            TenantContext.get().getGdmItemUpdateEventEnd());
    Long timeTakenForPersistingLabelDownloadEvent =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getLabelDownloadEventPersistStart(),
            TenantContext.get().getLabelDownloadEventPersistEnd());
    Long timeTakenForRepublishingLabelsToHawkeye =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getRepublishToHawkeyeStart(),
            TenantContext.get().getRepublishToHawkeyeEnd());
    Long timeTakenForFetchingAndFilteringLabelDownloadEvent =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFetchAndFilterLabelDownloadEventsStart(),
            TenantContext.get().getFetchAndFilterLabelDownloadEventsEnd());
    LOGGER.warn(
        "LatencyCheck GDM ItemUpdate at ts={} time in totalTimeTakenForProcessingItemUpdateEvent={}, "
            + "timeTakenForFetchingAndFilteringLabelDownloadEvent={} timeTakenForPersistingLabelDownloadEvent={}, timeTakenForRepublishingLabelToHawkeye={}, and correlationId={}",
        TenantContext.get().getLabelDownloadStart(),
        timeTakenForItemUpdate,
        timeTakenForFetchingAndFilteringLabelDownloadEvent,
        timeTakenForPersistingLabelDownloadEvent,
        timeTakenForRepublishingLabelsToHawkeye,
        TenantContext.getCorrelationId());
  }

  /**
   * * This method validates if the handling code update is valid to process con -> NonCon (Valid)
   * NonCon -> Con (Valid) NonCon-> NonCon / Con -> Con (Invalid) Exceptions: Handling code(X/R) ->
   * Con/Non-con or fromHandlingCode is empty.
   *
   * @param fromHandlingCode
   * @param toHandlingCode
   * @return
   */
  private boolean isHandlingUpdateValid(String fromHandlingCode, String toHandlingCode) {

    if (StringUtils.isEmpty(fromHandlingCode)
        || Arrays.asList(X_BLOCK_ITEM_HANDLING_CODES).contains(fromHandlingCode)
        || Arrays.asList(X_BLOCK_ITEM_HANDLING_CODES).contains(toHandlingCode)) {
      return true;
    }

    String fromitemPackTypeAndHandlingCode = RdcConstants.CASE_PACK_TYPE_CODE + fromHandlingCode;
    String toitemPackTypeAndHandlingCode = RdcConstants.CASE_PACK_TYPE_CODE + toHandlingCode;

    if ((appConfig
                .getValidItemPackTypeHandlingCodeCombinations()
                .contains(fromitemPackTypeAndHandlingCode)
            && appConfig
                .getValidItemPackTypeHandlingCodeCombinations()
                .contains(toitemPackTypeAndHandlingCode))
        || (!appConfig
                .getValidItemPackTypeHandlingCodeCombinations()
                .contains(fromitemPackTypeAndHandlingCode)
            && !appConfig
                .getValidItemPackTypeHandlingCodeCombinations()
                .contains(toitemPackTypeAndHandlingCode))) return false;

    return true;
  }
  /**
   * Processes item handling code update event by fetching all the label download events for the
   * delivery.
   *
   * @param itemUpdateMessage
   */
  private void processHandlingCodeUpdateEvent(ItemUpdateMessage itemUpdateMessage) {
    Long itemNumber = Long.valueOf(itemUpdateMessage.getItemNumber());
    if (!isHandlingUpdateValid(itemUpdateMessage.getFrom(), itemUpdateMessage.getTo())) {
      LOGGER.info(
          "Item update not need to be processed,for item {},  {} -> {}",
          itemNumber,
          itemUpdateMessage.getFrom(),
          itemUpdateMessage.getTo());
      return;
    }

    String updatedHandlingCode = itemUpdateMessage.getTo();
    String itemPackTypeAndHandlingCode = RdcConstants.CASE_PACK_TYPE_CODE + updatedHandlingCode;
    List<LabelDownloadEvent> labelDownloadEventList =
        fetchAndFilterLabelDownloadEvents(itemNumber, itemUpdateMessage.getActivePOs());
    if (CollectionUtils.isNotEmpty(labelDownloadEventList)) {
      updateHandlingCode(
          itemUpdateMessage,
          updatedHandlingCode,
          itemPackTypeAndHandlingCode,
          labelDownloadEventList);
    }
  }

  /**
   * This method is to update handling code. For DA events, in case of C* -> CI/CJ/CC, labels are
   * published to Hawkeye and in case of C* to CE/CX/CR/CN, item update is sent to Hawkeye with
   * updated reject reason. For SSTK events, similar processing is done only in the following cases
   * : C* -> CX (validates if the existing reject reason in LabelDownloadEvent is X-Block) CX ->
   * CI/CJ/CC (validates if the reject reason with updatedHandlingCode is X-Block)
   *
   * @param itemUpdateMessage
   * @param updatedHandlingCode
   * @param itemPackTypeAndHandlingCode
   * @param labelDownloadEventList
   */
  private void updateHandlingCode(
      ItemUpdateMessage itemUpdateMessage,
      String updatedHandlingCode,
      String itemPackTypeAndHandlingCode,
      List<LabelDownloadEvent> labelDownloadEventList) {
    boolean isSSTKAutomationEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
            false);
    List<LabelDownloadEvent> daLabelDownloadEventList =
        filteredLabelDownloadEventList(labelDownloadEventList, Boolean.FALSE);
    Map<String, Set<Long>> daPoToDeliveryMap =
        rdcSSTKLabelGenerationUtils.buildPoDeliveryMap(daLabelDownloadEventList);
    List<LabelDownloadEvent> sstkLabelDownloadEventList = null;
    Map<String, Set<Long>> sstkPoToDeliveryMap = null;
    if (isSSTKAutomationEnabled) {
      sstkLabelDownloadEventList =
          filteredLabelDownloadEventList(labelDownloadEventList, Boolean.TRUE);
      sstkLabelDownloadEventList =
          rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(
              sstkLabelDownloadEventList);
      sstkPoToDeliveryMap =
          rdcSSTKLabelGenerationUtils.buildPoDeliveryMap(sstkLabelDownloadEventList);
    }
    if (appConfig
        .getValidItemPackTypeHandlingCodeCombinations()
        .contains(itemPackTypeAndHandlingCode)) {
      /**
       * Item update for casepack item with any handling code to conveyable/sym eligible handling
       * codes (C* to CI/CJ/CC)
       */
      TenantContext.get().setRepublishToHawkeyeStart(System.currentTimeMillis());
      // Item update for DA PO's
      if (CollectionUtils.isNotEmpty(daLabelDownloadEventList)) {
        rdcLabelGenerationService.republishLabelsToHawkeye(
            daPoToDeliveryMap, itemUpdateMessage, false);
      }
      // Item update for SSTK PO's
      if (isSSTKAutomationEnabled
          && CollectionUtils.isNotEmpty(sstkLabelDownloadEventList)
          && isItemUpdateRequiredForSSTK(sstkLabelDownloadEventList.get(0).getRejectReason())) {
        rdcLabelGenerationService.republishLabelsToHawkeye(
            sstkPoToDeliveryMap, itemUpdateMessage, true);
      }
      TenantContext.get().setRepublishToHawkeyeEnd(System.currentTimeMillis());
    } else {
      /**
       * Item update for casepack item with any handling code to non-conveyable/sym
       * ineligible/X-block handling codes (C* to CE/CX/CR/CN)
       */
      RejectReason rejectReason = getRejectReasonForHandlingCode(updatedHandlingCode);
      // Item update for DA PO's
      if (CollectionUtils.isNotEmpty(daLabelDownloadEventList)) {
        updateRejectReasonToHawkeye(
            itemUpdateMessage,
            daPoToDeliveryMap,
            rejectReason,
            daLabelDownloadEventList.get(0),
            false);
      }
      // Item update for SSTK PO's
      if (isSSTKAutomationEnabled
          && CollectionUtils.isNotEmpty(sstkLabelDownloadEventList)
          && isItemUpdateRequiredForSSTK(rejectReason)) {
        updateRejectReasonToHawkeye(
            itemUpdateMessage,
            sstkPoToDeliveryMap,
            rejectReason,
            sstkLabelDownloadEventList.get(0),
            true);
      }
    }
  }

  /**
   * Returns true when the reject Reason is X_BLOCK
   *
   * @param rejectReason
   * @return
   */
  private boolean isItemUpdateRequiredForSSTK(RejectReason rejectReason) {
    return Objects.equals(rejectReason, RejectReason.X_BLOCK);
  }

  /**
   * This method is to filter DA/SSTK LabelDownloadEventList based on the isSSTKLabelType flag
   *
   * @param labelDownloadEventList
   * @param isSSTKLabelType
   * @return List<LabelDownloadEvent>
   */
  private List<LabelDownloadEvent> filteredLabelDownloadEventList(
      List<LabelDownloadEvent> labelDownloadEventList, Boolean isSSTKLabelType) {
    return labelDownloadEventList
        .stream()
        .filter(
            labelDownloadEvent ->
                isSSTKLabelType.equals(
                    rdcSSTKLabelGenerationUtils.isSSTKLabelDownloadEvent(labelDownloadEvent)))
        .sorted(Comparator.comparing(LabelDownloadEvent::getCreateTs).reversed())
        .collect(Collectors.toList());
  }

  /**
   * Processes item handling code update event
   *
   * @param itemUpdateMessage
   */
  private void processCatalogGtinUpdateEvent(ItemUpdateMessage itemUpdateMessage) {
    HttpHeaders httpHeaders = itemUpdateMessage.getHttpHeaders();
    Long itemNumber = Long.valueOf(itemUpdateMessage.getItemNumber());
    String updatedCatalogGtin =
        StringUtils.isNotBlank(itemUpdateMessage.getTo())
            ? itemUpdateMessage.getTo()
            : RdcConstants.EMPTY_CATALOG_GTIN;

    List<LabelDownloadEvent> labelDownloadEventList =
        fetchAndFilterLabelDownloadEvents(itemNumber, itemUpdateMessage.getActivePOs());
    if (CollectionUtils.isNotEmpty(labelDownloadEventList)) {
      List<Long> deliveryNumbers =
          labelDownloadEventList
              .stream()
              .map(LabelDownloadEvent::getDeliveryNumber)
              .distinct()
              .collect(Collectors.toList());
      for (Long delivery : deliveryNumbers) {
        hawkeyeRestApiClient.itemUpdateToHawkeye(
            RdcUtils.createHawkeyeItemUpdateRequest(
                itemNumber, delivery, null, updatedCatalogGtin, false),
            httpHeaders);
      }
    }
  }

  /**
   * Get the active deliveries from ItemUpdateMessage
   *
   * @param itemUpdateMessage
   * @return
   */
  private List<Long> getDeliveriesFromItemUpdateMessage(ItemUpdateMessage itemUpdateMessage) {
    List<ActiveDeliveryMessage> activeDeliveryMessageList =
        CollectionUtils.isNotEmpty(itemUpdateMessage.getActiveDeliveries())
            ? itemUpdateMessage.getActiveDeliveries()
            : Collections.emptyList();
    return activeDeliveryMessageList
        .stream()
        .map(ActiveDeliveryMessage::getDeliveryNumber)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Validates if the item is a casePack item
   *
   * @param itemUpdateMessage
   * @param deliveries
   * @return
   * @throws ReceivingException
   */
  private Boolean isCasePackItem(ItemUpdateMessage itemUpdateMessage, List<Long> deliveries)
      throws ReceivingException {
    Integer vnpk = itemUpdateMessage.getVnpk();
    Integer whpk = itemUpdateMessage.getWhpk();
    if (Objects.isNull(vnpk) || Objects.isNull(whpk)) {
      if (!CollectionUtils.isEmpty(deliveries)) {
        List<DeliveryDocument> deliveryDocuments =
            fetchDeliveryDocumentsFromGDM(
                Long.valueOf(itemUpdateMessage.getItemNumber()),
                deliveries.get(0),
                itemUpdateMessage.getHttpHeaders());
        if (CollectionUtils.isNotEmpty(deliveryDocuments)) {
          List<DeliveryDocumentLine> deliveryDocumentLines =
              deliveryDocuments.get(0).getDeliveryDocumentLines();
          if (CollectionUtils.isNotEmpty(deliveryDocumentLines)) {
            DeliveryDocumentLine deliveryDocumentLine = deliveryDocumentLines.get(0);
            vnpk = deliveryDocumentLine.getVendorPack();
            whpk = deliveryDocumentLine.getWarehousePack();
          }
        }
      } else {
        // TODO: Fetch vnpk and whpk from label data
        return Boolean.FALSE;
      }
    }
    if (RdcUtils.isBreakPackItem(vnpk, whpk)) {
      LOGGER.info(
          "Breakpack Item identified, no further processing needed for item: {}",
          itemUpdateMessage.getItemNumber());
      return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  /**
   * Gets rejectReason for handling code
   *
   * @param handlingCode
   * @return
   */
  private RejectReason getRejectReasonForHandlingCode(String handlingCode) {
    if (Arrays.asList(RdcConstants.X_BLOCK_ITEM_HANDLING_CODES).contains(handlingCode)) {
      return RejectReason.X_BLOCK;
    }
    return RejectReason.RDC_NONCON;
  }

  /**
   * Fetches delivery documents from GDM for delivery/item
   *
   * @param itemNumber
   * @param deliveryNumber
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  private List<DeliveryDocument> fetchDeliveryDocumentsFromGDM(
      Long itemNumber, Long deliveryNumber, HttpHeaders httpHeaders) throws ReceivingException {
    DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    return deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
        itemNumber.toString(), Math.toIntExact(deliveryNumber), httpHeaders);
  }

  /**
   * Updates reject reason in labelDownloadEvent and sends update to Hawkeye
   *
   * @param itemUpdateMessage
   * @param rejectReason
   * @param lastCreatedLabelDownloadEvent
   */
  private void updateRejectReasonToHawkeye(
      ItemUpdateMessage itemUpdateMessage,
      Map<String, Set<Long>> poToDeliveryMap,
      RejectReason rejectReason,
      LabelDownloadEvent lastCreatedLabelDownloadEvent,
      boolean isSSTKLabeType) {
    if (Boolean.TRUE.equals(isItemUpdateRequired(lastCreatedLabelDownloadEvent, rejectReason))) {
      List<LabelDownloadEvent> labelDownloadEvents =
          rdcLabelGenerationService.fetchAllLabelDownloadEvents(
              poToDeliveryMap,
              itemUpdateMessage,
              LabelDownloadEventStatus.PROCESSED.name(),
              true,
              isSSTKLabeType);
      poToDeliveryMap.forEach(
          (poNumber, deliveryNumberSet) -> {
            deliveryNumberSet.forEach(
                deliveryNumber -> {
                  HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest =
                      RdcUtils.createHawkeyeItemUpdateRequest(
                          Long.valueOf(itemUpdateMessage.getItemNumber()),
                          deliveryNumber,
                          rejectReason,
                          null,
                          true);
                  hawkeyeRestApiClient.itemUpdateToHawkeye(
                      hawkeyeItemUpdateRequest, itemUpdateMessage.getHttpHeaders());
                });
          });
      labelDownloadEvents.forEach(
          (labelDownloadEvent -> labelDownloadEvent.setRejectReason(rejectReason)));
      TenantContext.get().setLabelDownloadEventPersistStart(System.currentTimeMillis());
      labelDownloadEventService.saveAll(labelDownloadEvents);
      TenantContext.get().setLabelDownloadEventPersistEnd(System.currentTimeMillis());
    } else {
      LOGGER.info(
          "Item update already done, no processing required for item: {}", itemUpdateMessage);
    }
  }

  /**
   * Fetches and filters active and PROCESSED deliveries from the labelDownloadEvent
   *
   * @param itemNumber
   * @param activePOs
   * @return
   */
  private List<LabelDownloadEvent> fetchAndFilterLabelDownloadEvents(
      Long itemNumber, List<String> activePOs) {
    TenantContext.get().setFetchAndFilterLabelDownloadEventsEnd(System.currentTimeMillis());
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
    List<LabelDownloadEvent> filteredLabelDownloadEventList =
        labelDownloadEventList
            .stream()
            .filter(
                labelDownloadEvent ->
                    labelDownloadEvent.getCreateTs().after(dateRangeStart)
                        && labelDownloadEvent
                            .getStatus()
                            .equals(LabelDownloadEventStatus.PROCESSED.toString())
                        && activePOs.contains(labelDownloadEvent.getPurchaseReferenceNumber()))
            .sorted(Comparator.comparing(LabelDownloadEvent::getCreateTs).reversed())
            .collect(Collectors.toList());
    if (CollectionUtils.isEmpty(filteredLabelDownloadEventList)) {
      LOGGER.info(
          "No active and PROCESSED deliveries found in last {} days for the itemNumber: {} in Label Download event, no further processing needed.",
          itemUpdateHawkeyeDeliveriesDayLimit,
          itemNumber);
      return Collections.emptyList();
    }
    LOGGER.info(
        "Filtered {} valid labelDownloadEvents for item number {}",
        filteredLabelDownloadEventList.size(),
        itemNumber);
    TenantContext.get().setFetchAndFilterLabelDownloadEventsEnd(System.currentTimeMillis());
    return filteredLabelDownloadEventList;
  }

  /**
   * if labelDownloadEvent already has the reject reason set then item update is not required
   *
   * @param labelDownloadEvent
   * @param rejectReason
   * @return
   */
  private Boolean isItemUpdateRequired(
      LabelDownloadEvent labelDownloadEvent, RejectReason rejectReason) {
    return !Objects.equals(rejectReason, labelDownloadEvent.getRejectReason());
  }
}
