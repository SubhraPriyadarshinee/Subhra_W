package com.walmart.move.nim.receiving.sib.messsage.listener;

import static com.walmart.move.nim.receiving.sib.utils.Constants.STORE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_STORE_AUTO_INITIALIZATION_ENABLED;
import static java.util.stream.Collectors.groupingBy;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.StoreAutoInitializationEvents;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.service.PackContainerService;
import com.walmart.move.nim.receiving.sib.service.StoreDeliveryService;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import com.walmart.move.nim.receiving.sib.utils.Util;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

public class StoreAutoInitializationEventListener
    implements ApplicationListener<StoreAutoInitializationEvents> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(StoreAutoInitializationEventListener.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private StoreDeliveryService storeDeliveryService;

  @Autowired private ProcessInitiator processInitiator;

  @Autowired private PackContainerService packContainerService;

  @Autowired private EventRepository eventRepository;

  @Override
  public void onApplicationEvent(StoreAutoInitializationEvents storeAutoInitializationEvents) {
    List<Event> eventList = storeAutoInitializationEvents.getEvents();
    try {
      LOGGER.info("Processing StoreAutoInitialization events");
      processEvents(eventList);
      eventList.forEach(event -> event.setStatus(EventTargetStatus.SUCCESSFUL));
    } catch (Exception e) {
      LOGGER.info("[CRITICAL] : Unable to process event for store auto initialization", e);
      eventList.forEach(event -> event.setStatus(EventTargetStatus.STALE));
    } finally {
      eventRepository.saveAll(eventList);
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
                  String countryCode = eventList.get(0).getFacilityCountryCode();
                  CoreUtil.setTenantContext(facilityNum, countryCode, correlationId);
                  CoreUtil.setMDC();

                  if (tenantSpecificConfigReader.isFeatureFlagEnabled(
                      IS_STORE_AUTO_INITIALIZATION_ENABLED)) {
                    try {
                      LOGGER.info(
                          "Starting pallet shortages & loose case creation for deliveryNumber : {}",
                          deliveryNumber);
                      createShortages(deliveryNumber);
                      packContainerService.createCaseContainers(deliveryNumber);
                    } catch (Exception e) {
                      _events.forEach(event -> event.setStatus(EventTargetStatus.STALE));
                      LOGGER.error(
                          "Exception occurred while processing the store auto initialization for deliveryNumber={}",
                          deliveryNumber,
                          e);
                    } finally {
                      eventRepository.saveAll(_events);
                      MDC.clear();
                      TenantContext.clear();
                      LOGGER.info(
                          "Store auto initialization processed for delivery={}", deliveryNumber);
                    }
                  }
                }));
  }

  public void createShortages(Long deliveryNumber) {
    List<ASNDocument> asnDocuments = storeDeliveryService.getAsnDocuments(deliveryNumber);
    asnDocuments.forEach(
        asnDocument -> {
          Map<String, String> palletTypeMap = Util.getPalletTypeMap(asnDocument.getPacks());

          List<Pack> packs =
              asnDocument
                  .getPacks()
                  .stream()
                  .filter(pack -> STORE.equalsIgnoreCase(palletTypeMap.get(pack.getPalletNumber())))
                  .collect(Collectors.toList());
          asnDocument.setPacks(packs);

          Map<String, Object> forwardableHeaders =
              ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());
          // Action for overage / shortage handling .
          ReceivingEvent receivingEvent =
              ReceivingEvent.builder()
                  .payload(JacksonParser.writeValueAsString(asnDocument))
                  .name(Constants.STORE_CONTAINER_SHORTAGE_PROCESSOR)
                  .additionalAttributes(forwardableHeaders)
                  .processor(Constants.STORE_CONTAINER_SHORTAGE_PROCESSOR)
                  .build();
          LOGGER.info(
              "Going to initiate the process for store shortage processing for delivery Number {}",
              deliveryNumber);
          processInitiator.initiateProcess(receivingEvent, forwardableHeaders);
          LOGGER.info("Completed the process for store shortage processing");
        });
  }
}
