package com.walmart.move.nim.receiving.sib.messsage.listener;

import static com.walmart.move.nim.receiving.core.common.ContainerUtils.replaceContainerWithSSCC;
import static java.util.stream.Collectors.groupingBy;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.CleanupEvent;
import com.walmart.move.nim.receiving.sib.model.inventory.ContainerInventoriesItem;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.service.StoreInventoryService;
import com.walmart.move.nim.receiving.sib.transformer.ContainerDataTransformer;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

public class CleanupEventListener implements ApplicationListener<CleanupEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CleanupEventListener.class);

  @Autowired private StoreInventoryService storeInventoryService;

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private Gson gson;

  @Autowired private ContainerDataTransformer containerDataTransformer;

  @Autowired private ContainerTransformer containerTransformer;

  @Autowired private EventRepository eventRepository;

  @Override
  public void onApplicationEvent(CleanupEvent cleanupEvent) {
    List<Event> cleanupEvents = cleanupEvent.getEvents();
    try {
      processEvents(cleanupEvents);
      cleanupEvents.forEach(event -> event.setStatus(EventTargetStatus.SUCCESSFUL));
    } catch (Exception e) {
      LOGGER.info("[CRITICAL] : Unable to process event for cleanup", e);
      cleanupEvents.forEach(event -> event.setStatus(EventTargetStatus.STALE));
    } finally {
      eventRepository.saveAll(cleanupEvents);
    }
  }

  public void processEvents(List<Event> eventList) {
    String correlationId = UUID.randomUUID().toString();
    Map<Integer, Map<Long, List<Event>>> eventsGroupedByFacilityAndDeliveryNumber =
        eventList
            .stream()
            .collect(groupingBy(Event::getFacilityNum, groupingBy(Event::getDeliveryNumber)));

    eventsGroupedByFacilityAndDeliveryNumber.forEach(
        (facilityNum, eventMap) ->
            eventMap.forEach(
                (deliveryNumber, _events) -> {
                  CoreUtil.setTenantContext(
                      facilityNum,
                      _events.stream().findAny().get().getFacilityCountryCode(),
                      correlationId);
                  CoreUtil.setMDC();

                  LOGGER.info(
                      "Starting cleanup of unstocked inventories for delivery: {}", deliveryNumber);
                  List<ContainerInventoriesItem> unstockedInventoryForDelivery =
                      storeInventoryService.getUnstockedInventoryForDelivery(deliveryNumber);
                  List<String> unstockedTrackingIds =
                      unstockedInventoryForDelivery
                          .stream()
                          .map(ContainerInventoriesItem::getTrackingId)
                          .collect(Collectors.toList());
                  if (unstockedTrackingIds.isEmpty()) {
                    LOGGER.info("Nothing to clean for delivery: {}", deliveryNumber);
                    return;
                  }
                  List<Container> unstockedContainers =
                      containerPersisterService.findAllBySSCCIn(unstockedTrackingIds);
                  List<Event> correctionEvents =
                      unstockedContainers
                          .stream()
                          .map(
                              container ->
                                  createCorrectionContainerEvent(
                                      replaceContainerWithSSCC(
                                          containerTransformer.transform(container))))
                          .collect(Collectors.toList());
                  LOGGER.info(
                      "Saving correction events for delivery: {} for sscc: {} ",
                      deliveryNumber,
                      unstockedTrackingIds);
                  eventRepository.saveAll(correctionEvents);
                }));
  }

  private Event createCorrectionContainerEvent(ContainerDTO containerDTO) {
    Event containerEvent = new Event();
    containerEvent.setKey(containerDTO.getTrackingId());
    containerEvent.setDeliveryNumber(containerDTO.getDeliveryNumber());
    containerEvent.setEventType(EventType.CORRECTION);
    containerEvent.setPayload(
        gson.toJson(containerDataTransformer.transformToContainerEvent(containerDTO)));
    containerEvent.setPickUpTime(new Date());
    containerEvent.setRetryCount(0);
    containerEvent.setStatus(EventTargetStatus.PENDING);
    containerEvent.setFacilityNum(TenantContext.getFacilityNum());
    containerEvent.setFacilityCountryCode(TenantContext.getFacilityCountryCode());
    return containerEvent;
  }
}
