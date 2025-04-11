package com.walmart.move.nim.receiving.sib.messsage.listener;

import static com.walmart.move.nim.receiving.sib.utils.Constants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.messsage.publisher.KafkaContainerEventPublisher;
import com.walmart.move.nim.receiving.sib.model.ContainerApplicationEvents;
import com.walmart.move.nim.receiving.sib.model.ContainerEventData;
import com.walmart.move.nim.receiving.sib.model.ContainerEventPublishingPayload;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

/**
 * Listens to events published by EventPublisherService and processes these further
 *
 * @see com.walmart.move.nim.receiving.sib.service.EventPublisherService
 *     <p>Events are processesed asynchronously if this bean is initialised
 * @see com.walmart.move.nim.receiving.sib.config.AsynchronousSpringEventsConfig
 */
public class ContainerEventListener implements ApplicationListener<ContainerApplicationEvents> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerEventListener.class);

  @Autowired private KafkaContainerEventPublisher kafkaContainerEventPublisher;

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @Autowired private EventRepository eventRepository;

  @Autowired private Gson gson;

  @Override
  public void onApplicationEvent(ContainerApplicationEvents containerApplicationEvents) {
    try {
      processEvents(containerApplicationEvents);
    } catch (Exception e) {
      LOGGER.info("[CRITICAL] : Unable to process event for status publish", e);
    }
  }

  private void processEvents(ContainerApplicationEvents containerApplicationEvents) {
    String correlationId = UUID.randomUUID().toString();
    LOGGER.info(
        "Received application events: {}, correlationId: {}",
        containerApplicationEvents,
        correlationId);
    List<Event> events = containerApplicationEvents.getEvents();
    // group events by facility number and then delivery number
    Map<Integer, Map<Long, List<Event>>> eventsGroupedByFacilityAndDeliveryNumber =
        events
            .stream()
            .collect(groupingBy(Event::getFacilityNum, groupingBy(Event::getDeliveryNumber)));

    eventsGroupedByFacilityAndDeliveryNumber.forEach(
        (facilityNum, eventsGroupedByDeliveryNumber) ->
            eventsGroupedByDeliveryNumber.forEach(
                (deliveryNumber, eventsPerDelivery) -> {
                  Map<EventType, List<Event>> mapEventTypeAndListEvent =
                      eventsPerDelivery.stream().collect(groupingBy(Event::getEventType));
                  mapEventTypeAndListEvent.forEach(
                      (eventType, eventsPerEventType) -> {
                        for (List<Event> eventsPerBatch :
                            Iterables.partition(
                                eventsPerEventType,
                                sibManagedConfig.getPublishEventKafkaBatchSize())) {
                          try {

                            ContainerEventPublishingPayload containerEventPublishingPayload =
                                new ContainerEventPublishingPayload();

                            // Set tenantContext & MDC
                            String countryCode = eventsPerBatch.get(0).getFacilityCountryCode();
                            setTenantContext(facilityNum, countryCode, correlationId);
                            setMDC();
                            String subEventType = null;
                            // populate headers
                            Map<String, Object> headers =
                                getHeaders(
                                    facilityNum,
                                    deliveryNumber,
                                    resolveEventType(eventType),
                                    resolveEventSubType(eventType),
                                    eventsPerBatch,
                                    countryCode,
                                    correlationId);

                            List<ContainerEventData> containerEventDataList = new ArrayList<>();
                            containerEventPublishingPayload.setPayload(containerEventDataList);
                            eventsPerBatch.forEach(
                                event -> {
                                  ContainerEventData containerEventData =
                                      gson.fromJson(event.getPayload(), ContainerEventData.class);
                                  containerEventDataList.add(containerEventData);
                                });
                            kafkaContainerEventPublisher.publish(
                                containerEventPublishingPayload, headers);
                            eventsPerBatch.forEach(
                                event -> event.setStatus(EventTargetStatus.SUCCESSFUL));

                          } catch (Exception e) {
                            LOGGER.error(
                                "Unable to process the event due to exception and hence, skipping ",
                                e);
                            eventsPerBatch.forEach(
                                event -> event.setStatus(EventTargetStatus.STALE));
                          } finally {
                            eventRepository.saveAll(eventsPerBatch);
                            TenantContext.clear();
                          }
                        }
                      });
                }));
  }

  private Map<String, Object> getHeaders(
      Integer facilityNum,
      Long deliveryNumber,
      EventType eventType,
      String eventSubType,
      List<Event> eventsPerBatch,
      String countryCode,
      String correlationId) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(EVENT_TYPE, eventType);
    headers.put(ORIGINATOR_ID, RECEIVING);
    headers.put(
        KEY,
        formatEventKey(
            facilityNum, eventsPerBatch.get(0).getFacilityCountryCode(), deliveryNumber));
    headers.put(VERSION, VERSION_1_0_0);
    headers.put(MESSAGE_ID_HEADER_KEY, UUID.randomUUID());
    headers.put(CORRELATION_ID, correlationId);
    headers.put(MESSAGE_TIMESTAMP, new Date());
    headers.put(TENENT_FACLITYNUM, facilityNum);
    headers.put(TENENT_COUNTRY_CODE, countryCode);
    headers.put(USER_ID, DEFAULT_USER);
    if (!StringUtils.isEmpty(eventSubType)) {
      headers.put(EVENT_SUB_TYPE, eventSubType);
    }
    return headers;
  }

  private String formatEventKey(
      Integer facilityNum, String facilityCountryCode, Long deliveryNumber) {
    return StringUtils.joinWith(UNDERSCORE, facilityCountryCode, facilityNum, deliveryNumber);
  }

  /**
   * This method is responsible for setting up tenant context.
   *
   * @param facilityNum
   * @param facilityCountryCode
   * @param correlationId
   */
  private void setTenantContext(
      Integer facilityNum, String facilityCountryCode, String correlationId) {
    TenantContext.setFacilityNum(facilityNum);
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    TenantContext.setCorrelationId(correlationId);
  }

  /**
   * This method is responsible for setting up @{@link org.slf4j.MDC} context. For logging purposes
   */
  private void setMDC() {
    MDC.put(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum().toString());
    MDC.put(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, TenantContext.getCorrelationId());
  }

  private EventType resolveEventType(EventType eventType) {
    switch (eventType) {
      case CORRECTION:
        return EventType.CORRECTION;
      default:
        return EventType.STOCKING;
    }
  }

  private String resolveEventSubType(EventType eventType) {
    switch (eventType) {
      case CORRECTION:
        return PROBLEM;
      default:
        return EMPTY_STRING;
    }
  }
}
