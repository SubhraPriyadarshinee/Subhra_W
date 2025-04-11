package com.walmart.move.nim.receiving.sib.messsage.listener;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.DeliveryUnloadCompleteEvent;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.service.StoreDeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpHeaders;

public class DeliveryUnloadCompleteEventListener
    implements ApplicationListener<DeliveryUnloadCompleteEvent> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeliveryUnloadCompleteEventListener.class);

  @Autowired private EventRepository eventRepository;

  @Autowired private StoreDeliveryService storeDeliveryService;

  @Override
  public void onApplicationEvent(DeliveryUnloadCompleteEvent deliveryUnloadCompleteEvent) {
    try {
      LOGGER.info("Delivery Auto Unload Complete Event Processing Started.");
      List<Event> eventList = deliveryUnloadCompleteEvent.getEvents();
      if (CollectionUtils.isNotEmpty(eventList)) {
        String correlationId = UUID.randomUUID().toString();
        Map<Pair<Integer, String>, List<Event>> facilityWiseEvents =
            eventList
                .stream()
                .collect(
                    Collectors.groupingBy(
                        event ->
                            new Pair<>(event.getFacilityNum(), event.getFacilityCountryCode())));
        facilityWiseEvents.forEach(
            (facility, events) -> processEvents(correlationId, facility, events));
      }
      LOGGER.info("Delivery Auto Unload Complete Event Processing Completed.");
    } catch (Exception e) {
      LOGGER.error("Error while processing Auto Delivery Unload Complete Event.", e);
    }
  }

  private void processEvents(
      String correlationId, Pair<Integer, String> facility, List<Event> events) {
    populateContext(correlationId, facility.getKey(), facility.getValue());
    LOGGER.info(
        "Initiating Delivery Unload Complete Event for deliver numbers  {}",
        events.stream().map(Event::getDeliveryNumber).collect(Collectors.toList()));
    events.forEach(
        event -> {
          try {
            TenantContext.setAdditionalParams(USER_ID_HEADER_KEY, event.getCreatedBy());
            MDC.put(USER_ID_HEADER_KEY, event.getCreatedBy());
            HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
            storeDeliveryService.unloadComplete(
                event.getDeliveryNumber(), ReceivingConstants.DEFAULT_DOOR_NUM, null, httpHeaders);
            event.setStatus(EventTargetStatus.SUCCESSFUL);
          } catch (Exception e) {
            LOGGER.error(
                "Error while initiating delivery unload complete for delivery number {}",
                event.getDeliveryNumber(),
                e);
            event.setStatus(EventTargetStatus.STALE);
          }
        });
    eventRepository.saveAll(events);
  }

  private void populateContext(String correlationId, Integer facility, String facilityCountryCode) {
    CoreUtil.setTenantContext(facility, facilityCountryCode, correlationId);
    CoreUtil.setMDC();
  }
}
