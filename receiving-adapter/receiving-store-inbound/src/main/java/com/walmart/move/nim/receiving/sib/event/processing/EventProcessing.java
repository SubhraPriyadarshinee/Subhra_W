package com.walmart.move.nim.receiving.sib.event.processing;

import static com.walmart.move.nim.receiving.sib.utils.Constants.*;
import static com.walmart.move.nim.receiving.sib.utils.Util.isMorningTime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.ei.EIEvent;
import com.walmart.move.nim.receiving.sib.model.ei.LineItem;
import com.walmart.move.nim.receiving.sib.model.ei.LineMetaInfo;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.service.FreightTypeResolver;
import com.walmart.move.nim.receiving.sib.service.StoreDeliveryMetadataService;
import com.walmart.move.nim.receiving.sib.service.StoreDeliveryService;
import com.walmart.move.nim.receiving.sib.transformer.ContainerDataTransformer;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.sib.utils.KafkaHelper;
import com.walmart.move.nim.receiving.sib.utils.Util;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.util.CollectionUtils;

public abstract class EventProcessing {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventProcessing.class);
  @Autowired private EventRepository eventRepository;

  @Autowired private ContainerDataTransformer containerDataTransformer;

  @Autowired private StoreDeliveryMetadataService storeDeliveryMetadataService;

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @SecurePublisher private KafkaTemplate kafkaTemplate;

  @Autowired private StoreDeliveryService storeDeliveryService;

  @Value("${ngr.parity.ei.events.topic}")
  private String eiEventTopicName;

  private Gson gson;

  public EventProcessing() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    ;
  }

  private List<Event> createEventList(List<ContainerDTO> containerDTOS) {
    Optional<ContainerDTO> _containerDTO = containerDTOS.stream().findAny();
    Optional<DeliveryMetaData> _deliveryMetaData = Optional.empty();
    if (_containerDTO.isPresent()) {
      _deliveryMetaData =
          storeDeliveryMetadataService.findDeliveryMetadataByDeliveryNumber(
              String.valueOf(_containerDTO.get().getDeliveryNumber()));
    }
    Date unloadTs = _deliveryMetaData.orElse(new DeliveryMetaData()).getUnloadingCompleteDate();

    List<Event> events = new ArrayList<>();
    containerDTOS.forEach(
        containerDTO -> {
          Event containerEvent = new Event();
          containerEvent.setKey(containerDTO.getTrackingId());
          containerEvent.setDeliveryNumber(containerDTO.getDeliveryNumber());
          containerEvent.setEventType(EventType.STORE_FINALIZATION);
          containerEvent.setPayload(
              gson.toJson(containerDataTransformer.transformToContainerEvent(containerDTO)));
          containerEvent.setRetryCount(0);
          containerEvent.setStatus(EventTargetStatus.PENDING);
          containerEvent.setFacilityNum(TenantContext.getFacilityNum());
          containerEvent.setFacilityCountryCode(TenantContext.getFacilityCountryCode());
          // Adding freight_type in metadata
          Map<String, Object> metadata = new HashMap<>();
          metadata.put(
              Constants.FREIGHT_TYPE, FreightTypeResolver.resolveFreightType(containerDTO));
          metadata.put(TIMEZONE_CODE, containerDTO.getContainerMiscInfo().get(TIMEZONE_CODE));
          Map<String, Object> additionalInfo = new HashMap<>();
          additionalInfo.put(CREATE_TS, containerDTO.getCreateTs());
          additionalInfo.put(UNLOAD_TS, unloadTs);
          containerEvent.setMetaData(metadata);
          containerEvent.setAdditionalInfo(additionalInfo);
          events.add(containerEvent);
        });
    return events;
  }

  public List<Event> createEvents(List<ContainerDTO> containerDTOS) {
    List<Event> events = createEventList(containerDTOS);
    events.forEach(event -> event.setPickUpTime(decoratePickupTime(event)));
    return this.eventRepository.saveAll(events);
  }

  public abstract Date decoratePickupTime(Event event);

  public EventRepository getEventRepository() {
    return eventRepository;
  }

  public SIBManagedConfig getSibManagedConfig() {
    return sibManagedConfig;
  }

  public Gson getGson() {
    return gson;
  }

  protected EIEvent updateEventHeader(EIEvent eiEvent, String eventType) {
    eiEvent.getHeader().setEventCreationTime(new Date());
    eiEvent.getHeader().setCorrelationId(UUID.randomUUID().toString());
    eiEvent.getHeader().setMessageId(UUID.randomUUID().toString());
    eiEvent.getHeader().setMsgTimestamp(new Date());

    if (Objects.nonNull(eventType)) {
      eiEvent.getHeader().setEventType(eventType);
    }

    return eiEvent;
  }

  public void sendArrivalEvent(EIEvent eiEvent) {
    LOGGER.info(
        "NGR Parity Receiving message to be sent to EI = {}",
        eiEvent.getBody().getDocuments().getPalletId());

    EIEvent _eiEvent = updateEventHeader(eiEvent, null);

    Message<String> message = KafkaHelper.buildKafkaMessage(_eiEvent, eiEventTopicName, gson);

    kafkaTemplate.send(message);
    LOGGER.info(
        "NGR Parity Receiving message send successfully for palletId={}",
        eiEvent.getBody().getDocuments().getPalletId());
  }

  public List<Event> createNGREvents(List<EIEvent> eiEvents) {
    List<Event> events = createNGREventList(eiEvents);
    events.forEach(event -> event.setPickUpTime(decoratePickupTime(event)));
    return this.eventRepository.saveAll(events);
  }

  public List<Event> createNGREventList(List<EIEvent> eiEvents) {
    LineMetaInfo lineMetaInfo =
        eiEvents
            .stream()
            .findAny()
            .flatMap(
                eiEvent ->
                    eiEvent
                        .getBody()
                        .getLineInfo()
                        .stream()
                        .findAny()
                        .map(LineItem::getLineMetaInfo))
            .orElse(null);
    if (Objects.isNull(lineMetaInfo)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DATA, "No Items found in any event");
    }

    Optional<DeliveryMetaData> _deliveryMetaData =
        storeDeliveryMetadataService.findDeliveryMetadataByDeliveryNumber(
            String.valueOf(lineMetaInfo.getDeliveryNumber()));
    Date unloadTs = _deliveryMetaData.orElse(new DeliveryMetaData()).getUnloadingCompleteDate();

    List<Event> events = new ArrayList<>();
    eiEvents.forEach(
        eiEvent -> {
          updateEventHeader(eiEvent, Constants.EVENT_STOCKED);

          Event containerEvent = new Event();
          containerEvent.setKey(eiEvent.getBody().getDocuments().getPalletId());

          // extracting the store lines
          // containerType = CASE / PALLET
          // PalletType = MFC / STORE
          List<LineItem> lineItems =
              eiEvent
                  .getBody()
                  .getLineInfo()
                  .stream()
                  .filter(
                      line ->
                          !Constants.MFC.equalsIgnoreCase(line.getLineMetaInfo().getPalletType()))
                  .collect(Collectors.toList());
          if (CollectionUtils.isEmpty(lineItems)) {
            return;
          }
          eiEvent.getBody().setLineInfo(lineItems);

          // to be populated
          containerEvent.setDeliveryNumber(lineMetaInfo.getDeliveryNumber());
          containerEvent.setEventType(EventType.STOCKED);
          containerEvent.setPayload(gson.toJson(eiEvent));
          containerEvent.setRetryCount(0);
          containerEvent.setStatus(EventTargetStatus.PENDING);
          containerEvent.setFacilityNum(TenantContext.getFacilityNum());
          containerEvent.setFacilityCountryCode(TenantContext.getFacilityCountryCode());
          // Adding freight_type in metadata
          Map<String, Object> metadata = new HashMap<>();
          metadata.put(Constants.FREIGHT_TYPE, FreightTypeResolver.resolveFreightType(eiEvent));
          metadata.put(TIMEZONE_CODE, lineMetaInfo.getTimezone());
          Map<String, Object> additionalInfo = new HashMap<>();
          additionalInfo.put(CREATE_TS, new Date());
          additionalInfo.put(UNLOAD_TS, unloadTs);
          additionalInfo.put(DELIVERY_ARV_TS, lineMetaInfo.getArriveTs());
          additionalInfo.put(DELIVERY_SCH_TS, lineMetaInfo.getScheduleTs());
          additionalInfo.put(DOCUMENT_INGEST_TIME, lineMetaInfo.getDocumentIngestTime());
          containerEvent.setMetaData(metadata);
          containerEvent.setAdditionalInfo(additionalInfo);
          events.add(containerEvent);
        });
    return events;
  }

  protected Date calculateAvailabilityTs(
      Date scheduleTs, Date arriveTs, Date documentIngestTime, Event event) {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.DELAYED_DOCUMENT_INGEST_EARLY_STOCKED_ENABLED)) {
      // For times when there is a delayed signal or no signal from ETA to GDM for arrival
      if (Objects.nonNull(documentIngestTime)
          && documentIngestTime.after(
              Util.addHoursToJavaUtilDate(
                  arriveTs, sibManagedConfig.getDelayedDocumentIngestThresholdHours()))) {
        return Util.addMinsToJavaUtilDate(
            new Date(), sibManagedConfig.getDelayedDocumentScEventPickTimeDelayMinutes());
      }
    }

    return Objects.isNull(arriveTs) || scheduleTs.after(arriveTs)
        ? calculatePickUpTs(scheduleTs, event)
        : calculatePickUpTs(arriveTs, event);
  }

  private Date calculatePickUpTs(Date ts, Event event) {
    boolean isMorningTs =
        Util.isMorningTime(ts, (String) event.getMetaData().get(TIMEZONE_CODE), sibManagedConfig);

    // If ts between 9am-9pm , then 9pm or else arvTs + x min
    return isMorningTs
        ? getPickUpDate(event, ts)
        : Util.addMinsToJavaUtilDate(ts, getSibManagedConfig().getDefaultEventPickTimeDelay());
  }

  protected Date getNewFlowPickUpTs(Event event) {
    Date scheduleTs = (Date) event.getAdditionalInfo().get(DELIVERY_SCH_TS);
    Date arrivalTs = (Date) event.getAdditionalInfo().get(DELIVERY_ARV_TS);
    Date documentIngestTime = (Date) event.getAdditionalInfo().get(DOCUMENT_INGEST_TIME);

    if (scheduleTs == null || arrivalTs == null || documentIngestTime == null) {
      Delivery delivery = storeDeliveryService.getGDMData(event.getDeliveryNumber());
      LOGGER.info(
          "Retrieved Delivery scheduled timestamp and arrival timestamp for deliveryNumber = {} as {} and {}",
          event.getDeliveryNumber(),
          delivery.getScheduled(),
          delivery.getArrivalTimeStamp());
      event.getAdditionalInfo().put(DELIVERY_SCH_TS, delivery.getAppointmentStartDateTime());
      event.getAdditionalInfo().put(DELIVERY_ARV_TS, delivery.getArrivalTime());
      event
          .getAdditionalInfo()
          .put(
              DOCUMENT_INGEST_TIME,
              delivery.getShipments().stream().findAny().get().getDocumentIngestTime());
    }
    return calculateAvailabilityTs(
        getDate(event.getAdditionalInfo().get(DELIVERY_SCH_TS)),
        getDate(event.getAdditionalInfo().get(DELIVERY_ARV_TS)),
        getDate(event.getAdditionalInfo().get(DOCUMENT_INGEST_TIME)),
        event);
  }

  private Date getDate(Object date) {
    return Objects.nonNull(date) ? (Date) date : null;
  }

  protected Date getPickUpDate(Event event, Date unloadTs) {

    String timeZoneCode =
        Objects.isNull(event.getMetaData().get(TIMEZONE_CODE))
            ? null
            : event.getMetaData().get(TIMEZONE_CODE).toString();

    if (Objects.nonNull(timeZoneCode)
        && isMorningTime(unloadTs, timeZoneCode, getSibManagedConfig())) {
      Calendar instance = Calendar.getInstance(TimeZone.getTimeZone(timeZoneCode));
      instance.set(Calendar.HOUR_OF_DAY, getSibManagedConfig().getReferenceShiftSFTriggersHours());
      instance.set(Calendar.MINUTE, 0);
      instance.set(Calendar.SECOND, 0);
      instance.set(Calendar.MILLISECOND, 0);
      return instance.getTime();
    } else {
      return Util.addHoursToJavaUtilDate(unloadTs, getSibManagedConfig().getScEventPickTimeDelay());
    }
  }

  public String getEiEventTopicName() {
    return eiEventTopicName;
  }

  public KafkaTemplate getKafkaTemplate() {
    return kafkaTemplate;
  }
}
