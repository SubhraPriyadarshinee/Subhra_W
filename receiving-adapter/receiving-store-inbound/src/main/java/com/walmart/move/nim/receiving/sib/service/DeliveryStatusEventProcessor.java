package com.walmart.move.nim.receiving.sib.service;

import static com.walmart.move.nim.receiving.sib.utils.Constants.MFC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FACILITY_TYPES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_STORE_AUTO_INITIALIZATION_ENABLED;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.walmart.move.nim.receiving.core.common.DocumentType;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.sib.utils.Util;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public class DeliveryStatusEventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryStatusEventProcessor.class);

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private EventRegistrationService eventRegistrationService;

  @Autowired private EventRepository eventRepository;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  @Value("${mixed.pallet.mfc.only.reject:true}")
  private String onlyMFCReject;

  private Gson gson;

  public DeliveryStatusEventProcessor() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  public void doProcessEvent(DeliveryUpdateMessage deliveryUpdateMessage) {
    Shipment shipment = getDeliveryShipment(deliveryUpdateMessage);
    if (Objects.nonNull(shipment)
        && !sibManagedConfig
            .getEligibleDeliverySourceTypeForGDMEventProcessing()
            .contains(shipment.getSource().getType())) {
      LOGGER.info(
          "Event processing not applicable for delivery number = {} and source type {}",
          deliveryUpdateMessage.getDeliveryNumber(),
          shipment.getSource().getType());
      return;
    }
    createCleanupEvent(deliveryUpdateMessage);
    createStoreAutoInitializationEvent(deliveryUpdateMessage);
    processNGRParity(deliveryUpdateMessage);
  }

  public void processNGRParity(DeliveryUpdateMessage deliveryUpdateMessage) {

    populateShipmentDetails(deliveryUpdateMessage);
    if (!isEventProcessable(deliveryUpdateMessage)) {
      LOGGER.info("Not a valid status for NGR Parity processing {}", deliveryUpdateMessage);
      return;
    }

    if (!isFacilityEnabled(deliveryUpdateMessage)) {
      LOGGER.info("Not a valid facility for NGR Parity processing {}", deliveryUpdateMessage);
      return;
    }

    LOGGER.info(
        "NGR Parity operation started for deliveryNumber={}",
        deliveryUpdateMessage.getDeliveryNumber());
    Set<String> mixedPalletTrackingId = new HashSet<>();

    String asnId = populateASNId(deliveryUpdateMessage);

    ASNDocument mixedPalletAsnInfo =
        findMixedContainerFromASN(deliveryUpdateMessage.getDeliveryNumber(), asnId);

    LOGGER.info("Mixed Pallet asn for RIP = {} ", mixedPalletAsnInfo);

    if (isEligibleForMixedPalletProcessing(mixedPalletAsnInfo)) {
      removeStoreItemIfApplicable(mixedPalletAsnInfo);
      LOGGER.info(
          "Got Mixed Pallet ASN from GDM with ASNID = {} ",
          mixedPalletAsnInfo.getShipment().getDocumentId());

      BiFunction<Pack, Item, String> itemTypeResolver =
          getEligibleItemTypeResolver(mixedPalletAsnInfo);

      eventRegistrationService.processNGRParity(
          mixedPalletAsnInfo, new HashSet<>(), itemTypeResolver);

      mixedPalletAsnInfo
          .getPacks()
          .stream()
          .map(
              pack ->
                  Objects.isNull(pack.getPalletNumber())
                      ? pack.getPackNumber()
                      : pack.getPalletNumber())
          .forEach(id -> mixedPalletTrackingId.add(id));
      LOGGER.info("Mixed pallet id found = {} ", mixedPalletTrackingId);
    }

    ASNDocument asnDocument = getGDMData(deliveryUpdateMessage.getDeliveryNumber(), asnId);
    BiFunction<Pack, Item, String> _itemTypeResolver = getEligibleItemTypeResolver(asnDocument);

    LOGGER.info("Got ASN from GDM with ASNID = {} ", asnDocument.getShipment().getDocumentId());
    filterEligiblePacks(asnDocument);
    if (CollectionUtils.isEmpty(asnDocument.getPacks())) {
      LOGGER.info(
          "Skipping NGR parity flow as no eligible packs exists for delivery number {} and asn {}.",
          deliveryUpdateMessage.getDeliveryNumber(),
          deliveryUpdateMessage.getShipmentDocumentId());
      return;
    }
    // removed mfc item from mixed pack as it is already processed earlier
    removeMixedPackMFCItem(mixedPalletTrackingId, asnDocument);
    removeStorePackIfApplicable(asnDocument);
    eventRegistrationService.processNGRParity(
        asnDocument, mixedPalletTrackingId, _itemTypeResolver);
    LOGGER.info(
        "NGR Parity operation completed for deliveryNumber={}",
        deliveryUpdateMessage.getDeliveryNumber());
  }

  private void populateShipmentDetails(DeliveryUpdateMessage deliveryUpdateMessage) {
    if (Objects.isNull(deliveryUpdateMessage.getShipmentDocumentType())) {
      Delivery delivery = getGDMData(deliveryUpdateMessage);
      Shipment shipment =
          delivery
              .getShipments()
              .stream()
              .findFirst()
              .orElseThrow(
                  () ->
                      new ReceivingDataNotFoundException(
                          ExceptionCodes.INVALID_DATA,
                          String.format(
                              "Shipment is not attached in delivery = %s",
                              deliveryUpdateMessage.getDeliveryNumber())));
      deliveryUpdateMessage.setShipmentDocumentType(shipment.getDocumentType());
      if (Objects.isNull(deliveryUpdateMessage.getShipmentDocumentId())) {
        deliveryUpdateMessage.setShipmentDocumentId(shipment.getDocumentId());
      }
    }
  }

  private boolean isEligibleForMixedPalletProcessing(ASNDocument mixedPalletAsnInfo) {
    return (Objects.nonNull(mixedPalletAsnInfo)
        && sibManagedConfig
            .getEligibleDocumentTypeForMixedPalletProcessing()
            .contains(mixedPalletAsnInfo.getShipment().getDocumentType()));
  }

  private BiFunction<Pack, Item, String> getEligibleItemTypeResolver(ASNDocument asnDocument) {
    if (DocumentType.CHARGE_ASN.equalsType(asnDocument.getShipment().getDocumentType())
        || DocumentType.CREDIT_ASN.equalsType(asnDocument.getShipment().getDocumentType())) {
      return (pack, item) -> Constants.STORE;
    }
    return (pack, item) -> Util.getPackType(pack);
  }

  private boolean isFacilityEnabled(DeliveryUpdateMessage deliveryUpdateMessage) {
    DocumentType type =
        DocumentType.getDocumentType(deliveryUpdateMessage.getShipmentDocumentType());
    if (sibManagedConfig.getCorrectionalInvoiceDocumentType().contains(type.getDocType())) {
      return tenantSpecificConfigReader.isFeatureFlagEnabled(
          Constants.ENABLE_NGR_PARITY_FOR_CORRECTIONAL_INVOICE);
    }

    return DocumentType.ASN.equals(type)
        && sibManagedConfig.getNgrParityFacilities().contains(TenantContext.getFacilityNum());
  }

  private void createCleanupEvent(DeliveryUpdateMessage deliveryUpdateMessage) {
    if (!sibManagedConfig
        .getCleanupRegisterOnDeliveryEvent()
        .equals(deliveryUpdateMessage.getEventType())) {
      LOGGER.info("Not a valid event for cleanup event registration {}", deliveryUpdateMessage);
      return;
    }

    List<Event> cleanupEventsForDelivery =
        eventRepository.findAllByDeliveryNumberAndEventType(
            Long.valueOf(deliveryUpdateMessage.getDeliveryNumber()), EventType.CLEANUP);
    if (CollectionUtils.isEmpty(cleanupEventsForDelivery)) {
      Event cleanupEvent = new Event();
      cleanupEvent.setKey(deliveryUpdateMessage.getDeliveryNumber());
      cleanupEvent.setDeliveryNumber(Long.valueOf(deliveryUpdateMessage.getDeliveryNumber()));
      cleanupEvent.setEventType(EventType.CLEANUP);
      cleanupEvent.setPayload(gson.toJson(deliveryUpdateMessage));
      cleanupEvent.setPickUpTime(
          Util.addHoursToJavaUtilDate(new Date(), sibManagedConfig.getCleanupEventDelayHours()));
      cleanupEvent.setRetryCount(0);
      cleanupEvent.setStatus(EventTargetStatus.PENDING);
      cleanupEvent.setFacilityNum(TenantContext.getFacilityNum());
      cleanupEvent.setFacilityCountryCode(TenantContext.getFacilityCountryCode());
      eventRepository.save(cleanupEvent);
    }
  }

  // Register event to create Store pallet shortages and also create loose cases in CSM
  private void createStoreAutoInitializationEvent(DeliveryUpdateMessage deliveryUpdateMessage) {
    if (!sibManagedConfig
        .getStoreAutoInitializationOnDeliveryEvent()
        .equals(deliveryUpdateMessage.getEventType())) {
      LOGGER.info(
          "Not a valid event for store auto initialization event registration {}",
          deliveryUpdateMessage);
      return;
    }
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(IS_STORE_AUTO_INITIALIZATION_ENABLED)) {

      List<Event> storeAutoInitializationEvents =
          eventRepository.findAllByDeliveryNumberAndEventType(
              Long.valueOf(deliveryUpdateMessage.getDeliveryNumber()),
              EventType.STORE_AUTO_INITIALIZATION);
      if (CollectionUtils.isEmpty(storeAutoInitializationEvents)) {
        Event storeShortageCreateEvent = new Event();
        storeShortageCreateEvent.setKey(deliveryUpdateMessage.getDeliveryNumber());
        storeShortageCreateEvent.setDeliveryNumber(
            Long.valueOf(deliveryUpdateMessage.getDeliveryNumber()));
        storeShortageCreateEvent.setEventType(EventType.STORE_AUTO_INITIALIZATION);
        storeShortageCreateEvent.setPickUpTime(
            Util.addMinsToJavaUtilDate(
                new Date(), sibManagedConfig.getStoreAutoInitializationEventDelayMins()));
        storeShortageCreateEvent.setRetryCount(0);
        storeShortageCreateEvent.setStatus(EventTargetStatus.PENDING);
        storeShortageCreateEvent.setFacilityNum(TenantContext.getFacilityNum());
        storeShortageCreateEvent.setFacilityCountryCode(TenantContext.getFacilityCountryCode());
        eventRepository.save(storeShortageCreateEvent);
      }
    }
  }

  private void removeMixedPackMFCItem(Set<String> mixedPalletTrackingId, ASNDocument asnDocument) {

    if (mixedPalletTrackingId.isEmpty()) {
      LOGGER.info("No Mixed Pack found and hence, ignoring it");
      return;
    }

    asnDocument
        .getPacks()
        .stream()
        .filter(
            pack -> {
              String packId =
                  Objects.isNull(pack.getPalletNumber())
                      ? pack.getPackNumber()
                      : pack.getPalletNumber();
              return mixedPalletTrackingId.contains(packId);
            })
        .forEach(
            pack -> {
              String packId =
                  Objects.isNull(pack.getPalletNumber())
                      ? pack.getPackNumber()
                      : pack.getPalletNumber();
              List<Item> storeItems =
                  pack.getItems()
                      .stream()
                      .filter(item -> !Constants.MFC.equalsIgnoreCase(Util.getItemType(item)))
                      .collect(Collectors.toList());
              pack.setItems(storeItems);
              LOGGER.info("Removed mfc pack from pack = {}", packId);
            });
  }

  private void removeStorePackIfApplicable(ASNDocument asnDocument) {
    List<String> supportedFacilityTypes = getSupportedFacilityTypes();
    if (supportedFacilityTypes.size() == 1 && supportedFacilityTypes.contains(MFC)) {
      removeStorePacks(asnDocument);
    }
  }

  private void removeStoreItemIfApplicable(ASNDocument asnDocument) {
    List<String> supportedFacilityTypes = getSupportedFacilityTypes();
    if (supportedFacilityTypes.size() == 1 && supportedFacilityTypes.contains(MFC)) {
      removeStoreItem(asnDocument);
    }
  }

  private String populateASNId(DeliveryUpdateMessage deliveryUpdateMessage) {

    if (Objects.nonNull(deliveryUpdateMessage.getShipmentDocumentId())) {
      return deliveryUpdateMessage.getShipmentDocumentId();
    }

    Delivery delivery = getGDMData(deliveryUpdateMessage);
    return delivery
        .getShipments()
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ReceivingDataNotFoundException(
                    ExceptionCodes.INVALID_DATA,
                    String.format(
                        "Shipment is not attached in delivery = %s",
                        deliveryUpdateMessage.getDeliveryNumber())))
        .getDocumentId();
  }

  public Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage) {

    StringBuilder urlBuilder =
        new StringBuilder(appConfig.getGdmBaseUrl())
            .append("/api")
            .append("/deliveries/")
            .append(deliveryUpdateMessage.getDeliveryNumber());

    return restConnector
        .exchange(
            urlBuilder.toString(),
            HttpMethod.GET,
            new HttpEntity<>(ReceivingUtils.getHeaders()),
            Delivery.class)
        .getBody();
  }

  public ASNDocument getGDMData(String deliveryNumber, String shipmentDocumentId) {
    StringBuilder urlBuilder =
        new StringBuilder(appConfig.getGdmBaseUrl())
            .append("/api")
            .append("/deliveries/")
            .append(deliveryNumber)
            .append("/shipments/")
            .append(shipmentDocumentId);

    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(HttpHeaders.ACCEPT, "application/vnd.DeliveryShipmentSearchZipResponse1+json");
    headers.add("Compression-Type", "gzip");

    String shipmentDocuments =
        restConnector
            .exchange(
                urlBuilder.toString(), HttpMethod.GET, new HttpEntity<>(headers), String.class)
            .getBody();

    ASNDocument shipmentDocs = gson.fromJson(shipmentDocuments, ASNDocument.class);
    if (Objects.isNull(shipmentDocs)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format(
              "Shipment = %s from delivery = %s is not available",
              deliveryNumber, shipmentDocumentId));
    }
    return shipmentDocs;
  }

  public ASNDocument findMixedContainerFromASN(String deliveryNumber, String asnDocId) {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    pathParams.put(ReceivingConstants.SHIPMENT_NUMBER, asnDocId);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("includeOnlyMfcItems", onlyMFCReject);

    String url =
        ReceivingUtils.replacePathParamsAndQueryParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_LINK_SHIPMENT_DELIVERY,
                pathParams,
                queryParams)
            .toString();

    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(
        HttpHeaders.ACCEPT, "application/vnd.DeliveryShipmentSearchMixedPackZipResponse1+json");
    headers.add("Compression-Type", "gzip");

    ASNDocument asnDocument = null;

    try {
      asnDocument =
          restConnector
              .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), ASNDocument.class)
              .getBody();
      LOGGER.info(
          "Got information for asn on mixed pallet delivery {} shipment id {} ",
          deliveryNumber,
          asnDocId);

    } catch (Exception ex) {
      LOGGER.error("Unable to fetch mixed Item from GDM ", ex);
    }

    return asnDocument;
  }

  private List<String> getSupportedFacilityTypes() {
    JsonArray facilityTypesArray =
        tenantSpecificConfigReader
            .getCcmConfigValueAsJson(TenantContext.getFacilityNum().toString(), FACILITY_TYPES)
            .getAsJsonArray();
    List<String> supportedFacilityTypes = new ArrayList<>();
    facilityTypesArray.forEach(
        facilityType -> supportedFacilityTypes.add(facilityType.getAsString()));
    return supportedFacilityTypes;
  }

  private void removeStorePacks(ASNDocument asnDocument) {
    List<Pack> mfcPacks =
        asnDocument
            .getPacks()
            .stream()
            .filter(pack -> Util.getPackType(pack).equals(MFC))
            .collect(Collectors.toList());
    asnDocument.setPacks(mfcPacks);
  }

  private void removeStoreItem(ASNDocument asnDocument) {
    List<Pack> mfcPacks = new ArrayList<>();
    for (Pack pack : asnDocument.getPacks()) {
      List<Item> items =
          pack.getItems()
              .stream()
              .filter(item -> MFC.equalsIgnoreCase(Util.getItemType(item)))
              .collect(Collectors.toList());
      pack.setItems(items);
      mfcPacks.add(pack);
    }
    asnDocument.setPacks(mfcPacks);
  }

  /**
   * Filter eligible pallets for NGR events 1. floor loaded cases 2. Pallet types configured for a
   * document type. 3. If document type is not configured publish NGR events for all pallets / packs
   * by default.
   */
  private void filterEligiblePacks(ASNDocument asnDocument) {
    String documentType = asnDocument.getShipment().getDocumentType();
    if (MapUtils.isNotEmpty(sibManagedConfig.getNgrEventEligiblePalletTypeMap())
        && sibManagedConfig.getNgrEventEligiblePalletTypeMap().containsKey(documentType)) {

      Set<String> eligiblePalletType =
          sibManagedConfig.getNgrEventEligiblePalletTypeMap().get(documentType);
      LOGGER.info(
          "Filter {} pallets for NGR events for delivery Number {} Document type {}.",
          eligiblePalletType,
          asnDocument.getDelivery().getDeliveryNumber(),
          documentType);
      LOGGER.info(
          "Before filter {} packs present for delivery number {}.",
          asnDocument.getPacks().size(),
          asnDocument.getDelivery().getDeliveryNumber());

      Map<String, String> palletTypeMap = Util.getPalletTypeMap(asnDocument.getPacks());
      List<Pack> packs =
          asnDocument
              .getPacks()
              .stream()
              .filter(pack -> getEligiblePacksForNGRParity(pack, palletTypeMap, eligiblePalletType))
              .collect(Collectors.toList());

      LOGGER.info(
          "Eligible packs for NGR events for delivery number {} document type {} - {}",
          asnDocument.getDelivery().getDeliveryNumber(),
          documentType,
          packs.size());
      asnDocument.setPacks(packs);
    }
  }

  private boolean getEligiblePacksForNGRParity(
      Pack pack, Map<String, String> palletTypeMap, Set<String> eligiblePalletType) {
    String packType =
        Objects.isNull(pack.getPalletNumber())
            ? Constants.CASE
            : palletTypeMap.get(pack.getPalletNumber());
    return eligiblePalletType.contains(packType);
  }

  private boolean isEventProcessable(DeliveryUpdateMessage deliveryUpdateMessage) {
    DocumentType type =
        DocumentType.getDocumentType(deliveryUpdateMessage.getShipmentDocumentType());
    if (CollectionUtils.isNotEmpty(sibManagedConfig.getCorrectionalInvoiceDocumentType())
        && sibManagedConfig.getCorrectionalInvoiceDocumentType().contains(type.getDocType())) {
      return sibManagedConfig
          .getCorrectionalInvNgrParityRIPEvent()
          .contains(deliveryUpdateMessage.getEventType());
    }

    return DocumentType.ASN.equals(type)
        && sibManagedConfig.getNgrParityRIPEvent().contains(deliveryUpdateMessage.getEventType());
  }

  private Shipment getDeliveryShipment(DeliveryUpdateMessage deliveryUpdateMessage) {
    if (Objects.nonNull(deliveryUpdateMessage.getDeliveryNumber())) {
      Delivery delivery = getGDMData(deliveryUpdateMessage);
      Shipment shipment =
          delivery
              .getShipments()
              .stream()
              .findFirst()
              .orElseThrow(
                  () ->
                      new ReceivingDataNotFoundException(
                          ExceptionCodes.INVALID_DATA,
                          String.format(
                              ReceivingConstants.SHIPMENT_NOT_ATTACHED_TO_DELIVERY,
                              deliveryUpdateMessage.getDeliveryNumber())));

      if (Objects.isNull(deliveryUpdateMessage.getShipmentDocumentType())) {
        deliveryUpdateMessage.setShipmentDocumentType(shipment.getDocumentType());
      }
      if (Objects.isNull(deliveryUpdateMessage.getShipmentDocumentId())) {
        deliveryUpdateMessage.setShipmentDocumentId(shipment.getDocumentId());
      }
      return shipment;
    }
    return null;
  }
}
