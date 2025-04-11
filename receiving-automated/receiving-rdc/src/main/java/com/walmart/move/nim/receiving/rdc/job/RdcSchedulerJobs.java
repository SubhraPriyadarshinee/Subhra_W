package com.walmart.move.nim.receiving.rdc.job;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.ScheduleConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.service.RdcLabelGenerationService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ConditionalOnExpression("${enable.rdc.label.generation.scheduler:false}")
@Component
public class RdcSchedulerJobs {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcSchedulerJobs.class);
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private DeliveryEventPersisterService deliveryEventPersisterService;
  @Autowired private RdcLabelGenerationService rdcLabelGenerationService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private ScheduleConfig scheduleConfig;

  /**
   * This scheduler is used to process the labels from DB which are marked PENDING during GDM Event.
   * If a delivery event exists with eventType DOOR_ASSIGNED, then all PO_ADDED and PO_LINE_ADDED
   * events are ignored and only the door assigned and update events with status PENDING are
   * processed else all the existing events in PENDING status are processed.
   */
  @Timed(
      name = "preLabelGenerationSchedulerTimed",
      level1 = "uwms-receiving",
      level2 = "rdcScheduler",
      level3 = "preLabelGenerationScheduler")
  @ExceptionCounted(
      name = "plgSchedulerExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rdcScheduler",
      level3 = "preLabelGenerationScheduler")
  @Scheduled(fixedDelayString = "${rdc.sstk.prelabel.generation.scheduler.frequency:60000}")
  @SchedulerLock(name = "RdcSchedulerJobs_aclPrelabelGeneration", lockAtMostFor = 90000)
  public void preLabelGenerationScheduler() {
    try {
      LOGGER.info("Flib SSTK Pre label generation scheduler started");
      String correlationId = UUID.randomUUID().toString();
      scheduleConfig
          .getSchedulerTenantConfig(scheduleConfig.getRdcLabelGenerationSpec())
          .forEach(
              pair -> {
                populateInfraValues(pair, correlationId);
                if (rdcManagedConfig
                    .getFlibSstkPregenSchedulerEnabledSites()
                    .contains(TenantContext.getFacilityNum().toString())) {
                  DeliveryEvent deliveryEvent =
                      deliveryEventPersisterService.getDeliveryForRdcScheduler(
                          rdcManagedConfig.getFlibSstkPregenSchedulerRetriesCount());
                  if (Objects.nonNull(deliveryEvent)) {
                    Long deliveryNumber = deliveryEvent.getDeliveryNumber();
                    LOGGER.info(
                        "Picked up delivery number {}, correlation id {}",
                        deliveryNumber,
                        correlationId);
                    List<DeliveryEvent> deliveryEvents =
                        deliveryEventPersisterService.getDeliveryEventsForScheduler(
                            deliveryEvent.getDeliveryNumber(),
                            Arrays.asList(
                                EventTargetStatus.EVENT_PENDING, EventTargetStatus.IN_RETRY),
                            rdcManagedConfig.getFlibSstkPregenSchedulerRetriesCount());

                    if (ReceivingUtils.isDeliveryExistsOfType(
                        deliveryEvents, ReceivingConstants.EVENT_DOOR_ASSIGNED)) {
                      generateLabelsForDeliveryEvents(deliveryEvents);
                    } else {
                      for (DeliveryEvent oldestDeliveryEvent : deliveryEvents) {
                        if (!rdcLabelGenerationService.processDeliveryEventForScheduler(
                            oldestDeliveryEvent)) {
                          return;
                        }
                      }
                    }
                  }
                }
              });
    } catch (Exception e) {
      LOGGER.error(RdcConstants.PRE_LABEL_GENERATION_SCHEDULER_ERROR, (Object) e.getStackTrace());
    } finally {
      clearTenantContext();
      LOGGER.info("Flib SSTK Pre label generation scheduler job completed.");
    }
  }

  /**
   * This method marks deliveryEvents of eventTypes PO_ADDED and PO_LINE_ADDED Delete and generates
   * labels for all other deliveryEvents.
   *
   * @param deliveryEvents
   */
  private void generateLabelsForDeliveryEvents(List<DeliveryEvent> deliveryEvents) {
    List<DeliveryEvent> poAddedAndPoLineAddedEvents =
        deliveryEvents
            .stream()
            .filter(
                deliveryEvent ->
                    ReceivingConstants.EVENT_PO_ADDED.equals(deliveryEvent.getEventType())
                        || ReceivingConstants.EVENT_PO_LINE_ADDED.equals(
                            deliveryEvent.getEventType()))
            .collect(Collectors.toList());
    if (CollectionUtils.isNotEmpty(poAddedAndPoLineAddedEvents)) {
      deliveryEventPersisterService.markAndSaveDeliveryEvents(
          poAddedAndPoLineAddedEvents, EventTargetStatus.DELETE);
    }
    Optional<DeliveryEvent> doorAssignedEvent =
        deliveryEvents
            .parallelStream()
            .filter(de -> ReceivingConstants.EVENT_DOOR_ASSIGNED.equals(de.getEventType()))
            .findAny();
    deliveryEvents.removeIf(
        deliveryEvent ->
            ReceivingConstants.EVENT_PO_ADDED.equals(deliveryEvent.getEventType())
                || ReceivingConstants.EVENT_PO_LINE_ADDED.equals(deliveryEvent.getEventType())
                || ReceivingConstants.EVENT_DOOR_ASSIGNED.equals(deliveryEvent.getEventType()));
    if (doorAssignedEvent.isPresent()
        && !rdcLabelGenerationService.processDeliveryEventForScheduler(doorAssignedEvent.get())) {
      return;
    }
    for (DeliveryEvent event : deliveryEvents) {
      if (!rdcLabelGenerationService.processDeliveryEventForScheduler(event)) {
        return;
      }
    }
  }

  private void populateInfraValues(Pair<String, Integer> pair, String correlationId) {

    TenantContext.setFacilityCountryCode(pair.getKey());
    TenantContext.setFacilityNum(pair.getValue());
    TenantContext.setCorrelationId(correlationId);
    TenantContext.setAdditionalParams(
        ReceivingConstants.USER_ID_HEADER_KEY,
        ReceivingConstants.LABEL_GENERATION_SCHEDULER_USER_NAME);

    MDC.put(ReceivingConstants.TENENT_FACLITYNUM, String.valueOf(pair.getValue()));
    MDC.put(ReceivingConstants.TENENT_COUNTRY_CODE, pair.getKey());
    MDC.put(
        ReceivingConstants.USER_ID_HEADER_KEY,
        ReceivingConstants.LABEL_GENERATION_SCHEDULER_USER_NAME);
    MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, correlationId);
  }

  private void clearTenantContext() {
    TenantContext.clear();
    MDC.clear();
  }
}
