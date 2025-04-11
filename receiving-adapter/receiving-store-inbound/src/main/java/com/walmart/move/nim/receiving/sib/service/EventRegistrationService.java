package com.walmart.move.nim.receiving.sib.service;

import static com.walmart.move.nim.receiving.sib.utils.Constants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CASE;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.DocumentType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.event.processing.EventProcessing;
import com.walmart.move.nim.receiving.sib.event.processing.EventProcessingResolver;
import com.walmart.move.nim.receiving.sib.event.processing.SuperCentreEventProcessing;
import com.walmart.move.nim.receiving.sib.model.FreightType;
import com.walmart.move.nim.receiving.sib.model.ei.*;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.sib.utils.Util;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

public class EventRegistrationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventRegistrationService.class);

  @Autowired private EventProcessingResolver eventProcessingResolver;

  @Autowired private EventRepository eventRepository;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @InjectTenantFilter
  @Transactional
  public void processEventOperation(List<ContainerDTO> containers, DeliveryInfo deliveryInfo) {
    // process case or received container
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.ENABLE_STORE_FINALIZATION_EVENT)) {
      processContainerEventRegistration(containers);
    }
    processUpdateIfApplicable(deliveryInfo);
  }

  private void processUpdateIfApplicable(DeliveryInfo deliveryInfo) {

    if (Objects.isNull(deliveryInfo)) {
      LOGGER.info(
          "Not eligible to update pick up time and hence, ignoring . deliveryInfo={}",
          deliveryInfo);
      return;
    }

    SuperCentreEventProcessing superCentreEventProcessing =
        (SuperCentreEventProcessing) eventProcessingResolver.resolve(FreightType.SC);
    superCentreEventProcessing.updatePickUpTime(
        deliveryInfo.getDeliveryNumber(), deliveryInfo.getTs());
    LOGGER.info("Pickup time is updated successfully");
  }

  private void processContainerEventRegistration(List<ContainerDTO> containers) {
    if (CollectionUtils.isEmpty(containers)) {
      LOGGER.info("Not eligible for event registration as there is no container present");
      return;
    }

    Map<FreightType, List<ContainerDTO>> freightTypeListMap = groupContainerByType(containers);

    for (Map.Entry<FreightType, List<ContainerDTO>> entry : freightTypeListMap.entrySet()) {
      LOGGER.info("Event registration started for type={}", entry.getKey());
      EventProcessing eventProcessing = eventProcessingResolver.resolve(entry.getKey());
      eventProcessing.createEvents(entry.getValue());
      LOGGER.info("Event registration completed for type={}", entry.getKey());
    }
    LOGGER.info("Event registration completed ");
  }

  private Map<FreightType, List<ContainerDTO>> groupContainerByType(
      List<ContainerDTO> containerDTOs) {
    Map<FreightType, List<ContainerDTO>> freightTypeListMap = new HashMap<>();

    for (ContainerDTO containerDTO : containerDTOs) {

      FreightType freightType = FreightTypeResolver.resolveFreightType(containerDTO);
      List<ContainerDTO> containerDTOS =
          freightTypeListMap.getOrDefault(freightType, new ArrayList<>());
      containerDTOS.add(containerDTO);
      freightTypeListMap.put(freightType, containerDTOS);
    }
    return freightTypeListMap;
  }

  public Set<String> processNGRParity(
      ASNDocument asnDocument,
      Set<String> processedTrackingIds,
      BiFunction<Pack, Item, String> typeResolver) {
    List<EIEvent> eiEvents = createEvents(asnDocument, processedTrackingIds, typeResolver);
    Set<String> trackingIds = new HashSet<>();

    try {
      Map<FreightType, List<EIEvent>> freightTypeListMap = new HashMap<>();
      // Send RIP Event
      for (EIEvent eiEvent : eiEvents) {
        processEvent(eiEvent, freightTypeListMap);
        trackingIds.add(eiEvent.getBody().getDocuments().getPalletId());
      }

      if (DocumentType.CHARGE_ASN.equalsType(asnDocument.getShipment().getDocumentType())
          || DocumentType.CREDIT_ASN.equalsType(asnDocument.getShipment().getDocumentType())) {
        LOGGER.info(
            "Skipping the flow for delivery number {} as STOCKED event is not required for document type {}",
            asnDocument.getDelivery().getDeliveryNumber(),
            asnDocument.getShipment().getDocumentType());
        return trackingIds;
      }
      // Store the event
      for (Map.Entry<FreightType, List<EIEvent>> entry : freightTypeListMap.entrySet()) {
        EventProcessing eventProcessing = eventProcessingResolver.resolve(entry.getKey());
        eventProcessing.createNGREvents(entry.getValue());
        LOGGER.info("Successfully registered STOCKED event for type = {}", entry.getKey());
      }

    } catch (Exception exception) {
      LOGGER.error("Unable to register event for eiEvents ", exception);
    }
    return trackingIds;
  }

  private void processEvent(EIEvent eiEvent, Map<FreightType, List<EIEvent>> freightTypeListMap) {
    FreightType freightType = FreightTypeResolver.resolveFreightType(eiEvent);
    EventProcessing eventProcessing = eventProcessingResolver.resolve(freightType);
    eventProcessing.sendArrivalEvent(eiEvent);
    List<EIEvent> eiEvents = freightTypeListMap.getOrDefault(freightType, new ArrayList<>());
    eiEvents.add(eiEvent);
    freightTypeListMap.put(freightType, eiEvents);
  }

  private List<EIEvent> createEvents(
      ASNDocument asnDocument,
      Set<String> processedTrackingIds,
      BiFunction<Pack, Item, String> typeResolver) {

    // For idempotancy check
    List<String> pallets =
        eventRepository
            .findAllByDeliveryNumberAndFacilityNumAndFacilityCountryCode(
                asnDocument.getDelivery().getDeliveryNumber(),
                TenantContext.getFacilityNum(),
                TenantContext.getFacilityCountryCode())
            .orElse(new ArrayList<>())
            .stream()
            .filter(event -> event.getEventType().equals(EventType.STOCKED))
            // remove processedId
            .filter(event -> !processedTrackingIds.contains(event.getKey()))
            .map(event -> event.getKey())
            .collect(Collectors.toList());

    Pair<String, String> eventTypes = getEligibleEventTypeForNGRParity(asnDocument);

    EventHeader eventHeader =
        EventHeader.builder()
            .eventType(eventTypes.getKey())
            .eventSubType(eventTypes.getValue())
            .messageId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .msgTimestamp(new Date())
            .nodeInfo(
                NodeInfo.builder()
                    .nodeId(TenantContext.getFacilityNum())
                    .nodeType(Constants.STORE)
                    .countryCode(TenantContext.getFacilityCountryCode())
                    .build())
            .originatorId(Constants.EVENT_ORIGINATOR)
            .version(Constants.VERSION_1_0_0)
            .build();

    Map<String, List<LineItem>> lineItemMap = new HashMap<>();

    // TODO  : JVM Memory to check
    Map<Long, ItemDetails> itemMap = Util.extractItemMapFromASN(asnDocument);

    asnDocument
        .getPacks()
        .stream()
        .forEach(
            pack -> {

              // Handling case and Pallet
              String packId =
                  Objects.isNull(pack.getPalletNumber())
                      ? pack.getPackNumber()
                      : pack.getPalletNumber();

              List<LineItem> lineItems = lineItemMap.getOrDefault(packId, new ArrayList<>());

              pack.getItems()
                  .stream()
                  .forEach(
                      item -> {
                        String locationType = typeResolver.apply(pack, item);
                        LOGGER.info("Location is resolved as {}", locationType);

                        if (sibManagedConfig.getAssortmentTypes().contains(item.getAssortmentType())
                            && !CollectionUtils.isEmpty(item.getChildItems())) {
                          LOGGER.info(
                              "Assortment Shipper detected for itemNumber={}",
                              item.getItemNumber());
                          populateAssortmentLines(
                              asnDocument, itemMap, pack, item, locationType, lineItems);
                        } else {
                          LineItem lineItem =
                              buildLineItem(asnDocument, itemMap, pack, item, locationType);
                          lineItems.add(lineItem);
                        }
                      });
              // Updating Map
              lineItemMap.put(packId, lineItems);
            });

    List<EIEvent> eiEvents = new ArrayList<>();

    for (Map.Entry<String, List<LineItem>> entry : lineItemMap.entrySet()) {
      // ignoring duplicate store pallet RIP sending
      if (pallets.contains(entry.getKey())) {
        LOGGER.info(
            "There is already a event record for palletId={} and hence, will ignore it ",
            entry.getKey());
        continue;
      }

      List<LineItem> lineItems = entry.getValue();

      EventBody eventBody =
          EventBody.builder()
              .lineInfo(lineItems)
              .sourceNode(
                  SourceNode.builder()
                      .nodeId(Integer.valueOf(asnDocument.getShipment().getSource().getNumber()))
                      .nodeType("DC")
                      .countryCode(asnDocument.getShipment().getSource().getCountryCode())
                      .build())
              .documents(DocumentInfo.builder().palletId(entry.getKey()).build())
              .userId(ReceivingConstants.DEFAULT_USER)
              .reasonDetails(populateReasonDetails(asnDocument))
              .build();

      EIEvent eiEvent = EIEvent.builder().header(eventHeader).body(eventBody).build();
      eiEvents.add(eiEvent);
    }

    return removeMFCEventsIfNeeded(pallets, eiEvents);
  }

  private List<ReasonDetail> populateReasonDetails(ASNDocument asnDocument) {
    if (DocumentType.CREDIT_ASN.equalsType(asnDocument.getShipment().getDocumentType())) {
      return Arrays.asList(
          ReasonDetail.builder().reasonCode(REASON_SHORTAGE).reasonDesc(REASON_SHORTAGE).build());
    }
    if (DocumentType.CHARGE_ASN.equalsType(asnDocument.getShipment().getDocumentType())) {
      return Arrays.asList(
          ReasonDetail.builder().reasonCode(REASON_OVERAGE).reasonDesc(REASON_OVERAGE).build());
    }
    return null;
  }

  private Pair<String, String> getEligibleEventTypeForNGRParity(ASNDocument asnDocument) {
    DocumentType documentType =
        DocumentType.getDocumentType(asnDocument.getShipment().getDocumentType());
    if (DocumentType.CREDIT_ASN.equals(documentType)) {
      return new Pair<>(
          sibManagedConfig.getNgrEventForCreditInvoice(),
          sibManagedConfig.getCorrectionalInvoiceEventSubType());
    }
    if (DocumentType.CHARGE_ASN.equals(documentType)) {
      return new Pair<>(
          sibManagedConfig.getNgrEventForChargeInvoice(),
          sibManagedConfig.getCorrectionalInvoiceEventSubType());
    }
    return new Pair<>(Constants.EVENT_RECEIVING, null);
  }

  private void populateAssortmentLines(
      ASNDocument asnDocument,
      Map<Long, ItemDetails> itemMap,
      Pack pack,
      Item packItem,
      String locationType,
      List<LineItem> lineItems) {

    for (Item item : packItem.getChildItems()) {
      LOGGER.info("Adding assortmentItem to lineItem . itemNumber={} ", item.getItemNumber());
      LineItem lineItem = buildLineItem(asnDocument, itemMap, pack, packItem, locationType);

      // Overriding with child item details
      lineItem.setItemIdentifier(
          ItemIdentifier.builder()
              .itemNbr(String.valueOf(item.getItemNumber()))
              .gtin(item.getGtin())
              .build());
      lineItem.setQuantity(retrieveQty(item, asnDocument));
      lineItem.setUom(retrieveQtyUom(item));
      LOGGER.info(
          "Overriding child item invoiceNUmber={} from parentItem={} for assortmentShipper childItem={}",
          lineItem.getInvoiceNbr(),
          packItem.getItemNumber(),
          item.getItemNumber());
      lineItems.add(lineItem);
    }
  }

  private LineItem buildLineItem(
      ASNDocument asnDocument,
      Map<Long, ItemDetails> itemMap,
      Pack pack,
      Item item,
      String locationType) {
    String quantityType = getQuantityType(asnDocument.getShipment().getDocumentType());
    return LineItem.builder()
        .itemIdentifier(
            ItemIdentifier.builder()
                .itemNbr(String.valueOf(item.getItemNumber()))
                .gtin(item.getGtin())
                .build())
        .quantity(
            getQuantity(
                item.getInventoryDetail().getReportedQuantity(),
                asnDocument.getShipment().getDocumentType()))
        .uom(item.getInventoryDetail().getReportedUom())
        .expiryDate(null)
        .sourceLocation(SourceLocation.builder().locationArea(null).location(null).build())
        .destinationLocation(
            DestinationLocation.builder().locationArea(locationType).location(null).build())
        .eventCode(retrieveEventCode(item))
        .deptNbr(Long.valueOf(item.getItemDepartment()))
        .warehousePackQuantity(retrieveWhpkQty(itemMap.get(item.getItemNumber()), item))
        .invoiceNbr(item.getInvoice().getInvoiceNumber())
        .quantityType(quantityType)
        .lineMetaInfo(
            LineMetaInfo.builder()
                .bannerDescription(retrieveBannerDescription(asnDocument))
                .timezone(retrieveTimeZone(asnDocument))
                .wareHouseAreaCode(
                    Util.retrieveWarehouseAreaCode(itemMap.get(item.getItemNumber())))
                .deliveryNumber(asnDocument.getDelivery().getDeliveryNumber())
                .palletType(Util.getPackType(pack))
                .containerType(Objects.nonNull(pack.getPalletNumber()) ? PALLET : CASE)
                .scheduleTs(asnDocument.getDelivery().getScheduled())
                .arriveTs(asnDocument.getDelivery().getArrivalTimeStamp())
                .documentIngestTime(asnDocument.getShipment().getDocumentIngestTime())
                .build())
        .build();
  }

  private Double getQuantity(Double reportedQuantity, String documentType) {
    if (DocumentType.CREDIT_ASN.equalsType(documentType)
        || DocumentType.CHARGE_ASN.equalsType(documentType)) {
      return Math.abs(reportedQuantity);
    }
    return reportedQuantity;
  }

  private String getQuantityType(String documentType) {
    if (DocumentType.CREDIT_ASN.equalsType(documentType)
        || DocumentType.CHARGE_ASN.equalsType(documentType)) {
      return CORRECTIONAL_INVOICE_QTY_TYPE_DELTA;
    }
    return null;
  }

  private List<EIEvent> removeMFCEventsIfNeeded(List<String> pallets, List<EIEvent> eiEvents) {
    // First time RIP event for all type of pallet
    if (pallets.isEmpty()) {
      return eiEvents;
    }

    // 2nd time onwords no MFC RIP should go to EI
    List<EIEvent> eventToProcess =
        eiEvents.stream().filter(eiEvent -> !Util.isMFCType(eiEvent)).collect(Collectors.toList());
    return eventToProcess;
  }

  private String retrieveEventCode(Item item) {
    return Objects.nonNull(item.getAdditionalInfo())
        ? item.getAdditionalInfo().getEventType()
        : null;
  }

  private Long retrieveWhpkQty(ItemDetails item, Item packItem) {
    return Objects.nonNull(packItem)
            && Objects.nonNull(packItem.getInventoryDetail())
            && Objects.nonNull(packItem.getInventoryDetail().getWarehouseCaseQuantity())
        ? packItem.getInventoryDetail().getWarehouseCaseQuantity()
        : Objects.nonNull(item) && Objects.nonNull(item.getWarehousePackQuantity())
            ? item.getWarehousePackQuantity()
            : 0L;
  }

  private String retrieveTimeZone(ASNDocument asnDocument) {
    return Objects.nonNull(asnDocument.getShipment())
            && Objects.nonNull(asnDocument.getShipment().getAdditionalInfo())
        ? String.valueOf(asnDocument.getShipment().getAdditionalInfo().getTimeZoneCode())
        : null;
  }

  private String retrieveBannerDescription(ASNDocument asnDocument) {

    return Objects.nonNull(asnDocument.getShipment())
            && Objects.nonNull(asnDocument.getShipment().getAdditionalInfo())
        ? String.valueOf(asnDocument.getShipment().getAdditionalInfo().getBannerDescription())
        : null;
  }

  private Double retrieveQty(Item packItem, ASNDocument asnDocument) {
    return (Objects.nonNull(packItem)
            && Objects.nonNull(packItem.getInventoryDetail())
            && Objects.nonNull(packItem.getInventoryDetail().getReportedQuantity()))
        ? getQuantity(
            packItem.getInventoryDetail().getReportedQuantity(),
            asnDocument.getShipment().getDocumentType())
        : getQuantity(
            packItem.getInventoryDetail().getDerivedQuantity(),
            asnDocument.getShipment().getDocumentType());
  }

  private String retrieveQtyUom(Item packItem) {
    return Objects.nonNull(packItem)
            && Objects.nonNull(packItem.getInventoryDetail())
            && !StringUtils.isEmpty(packItem.getInventoryDetail().getReportedUom())
        ? packItem.getInventoryDetail().getReportedUom()
        : packItem.getInventoryDetail().getDerivedUom();
  }
}
