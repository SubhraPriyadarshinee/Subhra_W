package com.walmart.move.nim.receiving.sib.messsage.listener;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.COMPLETE_DELIVERY_PROCESSOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.service.CompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.DeliveryAutoCompleteEvent;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
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

public class DeliveryAutoCompleteEventListener
    implements ApplicationListener<DeliveryAutoCompleteEvent> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeliveryAutoCompleteEventListener.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private EventRepository eventRepository;

  @Override
  public void onApplicationEvent(DeliveryAutoCompleteEvent deliveryAutoCompleteEvent) {
    try {
      LOGGER.info("Auto Delivery Complete Event Processing Started.");
      List<Event> eventList = deliveryAutoCompleteEvent.getEvents();
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
      LOGGER.info("Auto Delivery Complete Event Processing Completed.");
    } catch (Exception e) {
      LOGGER.error("Error while processing Auto Delivery Complete Event.", e);
    }
  }

  private void processEvents(
      String correlationId, Pair<Integer, String> facility, List<Event> events) {
    populateContext(correlationId, facility.getKey(), facility.getValue());
    LOGGER.info(
        "Initiating Auto Delivery Complete Event for deliver numbers  {}",
        events.stream().map(event -> event.getDeliveryNumber()).collect(Collectors.toList()));
    events.forEach(
        event -> {
          try {
            TenantContext.setAdditionalParams(USER_ID_HEADER_KEY, event.getCreatedBy());
            MDC.put(USER_ID_HEADER_KEY, event.getCreatedBy());
            HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
            tenantSpecificConfigReader
                .getConfiguredInstance(
                    String.valueOf(facility.getKey()),
                    COMPLETE_DELIVERY_PROCESSOR,
                    CompleteDeliveryProcessor.class)
                .completeDelivery(event.getDeliveryNumber(), false, httpHeaders);
            event.setStatus(EventTargetStatus.SUCCESSFUL);
          } catch (Exception e) {
            LOGGER.error(
                "Error while initiating auto delivery complete for delivery number {}",
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
