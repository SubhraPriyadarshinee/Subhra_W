package com.walmart.move.nim.receiving.sib.messsage.listener;

import static java.util.stream.Collectors.groupingBy;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.ManualFinalizationEvents;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.service.StoreDeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

public class ManualFinalizationEventListener
    implements ApplicationListener<ManualFinalizationEvents> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerEventListener.class);

  @Autowired private EventRepository eventRepository;

  @Autowired private ProcessInitiator processInitiator;

  @Autowired private StoreDeliveryService storeDeliveryService;

  @Override
  public void onApplicationEvent(ManualFinalizationEvents manualFinalizationEvents) {
    try {
      processEvents(manualFinalizationEvents);
    } catch (Exception e) {
      LOGGER.info("[CRITICAL] : Unable to process event for status publish", e);
    }
  }

  private void processEvents(ManualFinalizationEvents manualFinalizationEvents) {
    String correlationId = UUID.randomUUID().toString();
    LOGGER.info(
        "Received application events: {}, correlationId: {}",
        manualFinalizationEvents,
        correlationId);
    List<Event> events = manualFinalizationEvents.getEvents();
    // group events by facility number and then delivery number
    Map<Integer, Map<Long, List<Event>>> eventsGroupedByFacilityAndDeliveryNumber =
        events
            .stream()
            .collect(groupingBy(Event::getFacilityNum, groupingBy(Event::getDeliveryNumber)));

    eventsGroupedByFacilityAndDeliveryNumber.forEach(
        (facilityNum, eventMap) -> {
          eventMap.forEach(
              (deliveryNumber, _events) -> {
                try {

                  String countryCode = _events.get(0).getFacilityCountryCode();
                  CoreUtil.setTenantContext(facilityNum, countryCode, correlationId);
                  CoreUtil.setMDC();
                  Optional<List<Event>> _stockedEvents =
                      eventRepository.findAllByDeliveryNumberAndFacilityNumAndFacilityCountryCode(
                          deliveryNumber, facilityNum, countryCode);

                  if (_stockedEvents.isPresent()) {
                    List<Event> stockedEvents = _stockedEvents.get();
                    stockedEvents.forEach(event -> event.setPickUpTime(new Date()));
                    eventRepository.saveAll(stockedEvents);
                    LOGGER.info("Pick up time is updated for delivery = {} ", deliveryNumber);
                  } else {
                    LOGGER.info("No stocked event found for delivery = {} ", deliveryNumber);
                  }
                  _events.forEach(event -> event.setStatus(EventTargetStatus.SUCCESSFUL));

                  // update delivery event as success
                } catch (Exception e) {
                  _events.forEach(event -> event.setStatus(EventTargetStatus.STALE));
                  LOGGER.error(
                      "Exception occured while processing the manual finalisation deliveryNumber={} hence , is on stale ",
                      deliveryNumber,
                      e);
                } finally {
                  eventRepository.saveAll(_events);
                  MDC.clear();
                  TenantContext.clear();
                  LOGGER.info(
                      "Manual finalization pick up time is updated for delivery={}",
                      deliveryNumber);
                }
              });
        });
  }

  private void initiateBulkReceiving(Long deliveryNumber) {

    List<ASNDocument> asnDocuments = storeDeliveryService.getAsnDocuments(deliveryNumber);

    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());

    // mfc pallet should not get received . however, it should get into exception flow
    forwardableHeaders.put(ReceivingConstants.STORE_PALLET_INCLUDED, Boolean.TRUE);
    forwardableHeaders.put(ReceivingConstants.MFC_PALLET_INCLUDED, Boolean.FALSE);

    for (ASNDocument asnDocument : asnDocuments) {
      ReceivingEvent receivingEvent =
          ReceivingEvent.builder()
              .payload(JacksonParser.writeValueAsString(asnDocument))
              .name(ReceivingConstants.STORE_BULK_RECEIVING_PROCESSOR)
              .additionalAttributes(forwardableHeaders)
              .processor(ReceivingConstants.STORE_BULK_RECEIVING_PROCESSOR)
              .build();
      LOGGER.info("Going to initiate Store Build receiving processor{}", deliveryNumber);
      processInitiator.initiateProcess(receivingEvent, forwardableHeaders);
    }
  }
}
