package com.walmart.move.nim.receiving.sib.messsage.listener;

import static java.util.stream.Collectors.groupingBy;

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.event.processing.DefaultEventProcessing;
import com.walmart.move.nim.receiving.sib.model.ContainerStockedEvents;
import com.walmart.move.nim.receiving.sib.model.ei.EIEvent;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

public class EIStockEventListener implements ApplicationListener<ContainerStockedEvents> {

  private static final Logger LOGGER = LoggerFactory.getLogger(EIStockEventListener.class);

  @Autowired private DefaultEventProcessing defaultEventProcessing;

  @Autowired private EventRepository eventRepository;

  @Autowired private Gson gson;

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @Override
  public void onApplicationEvent(ContainerStockedEvents containerStockedEvents) {
    try {
      LOGGER.info("Stocked event processing started");
      processEvents(containerStockedEvents);
      LOGGER.info("Stocked event processing finished");
    } catch (Exception e) {
      LOGGER.error("[CRITICAL] : Unable to process STOCKED event for status publish ", e);
    }
  }

  private void processEvents(ContainerStockedEvents containerStockedEvents) {
    {
      String correlationId = UUID.randomUUID().toString();
      LOGGER.info(
          "Received application events: {}, correlationId: {}",
          containerStockedEvents,
          correlationId);
      List<Event> events = containerStockedEvents.getEvents();
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

                              // Set tenantContext & MDC
                              String countryCode = eventsPerBatch.get(0).getFacilityCountryCode();
                              setTenantContext(facilityNum, countryCode, correlationId);
                              setMDC();
                              String subEventType = null;
                              // need to perform failure testing

                              eventsPerBatch.forEach(
                                  event -> {
                                    EIEvent eiEvent =
                                        gson.fromJson(event.getPayload(), EIEvent.class);
                                    eiEvent.getHeader().setMsgTimestamp(event.getPickUpTime());
                                    eiEvent.getHeader().setEventCreationTime(event.getPickUpTime());
                                    defaultEventProcessing.sendArrivalEvent(eiEvent);
                                    event.setStatus(EventTargetStatus.SUCCESSFUL);
                                    event.setPayload(gson.toJson(eiEvent));
                                  });

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
  }

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
}
