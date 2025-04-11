package com.walmart.move.nim.receiving.sib.event.processing;

import static com.walmart.move.nim.receiving.sib.utils.Constants.*;

import com.walmart.move.nim.receiving.sib.entity.Event;
import java.util.*;

public class SuperCentreEventProcessing extends EventProcessing {

  @Override
  public Date decoratePickupTime(Event event) {
    return calculatePickupTs(event);
  }

  private Date calculatePickupTs(Event event) {

    return getSibManagedConfig().isEnableNewAvailabilityFlow()
        ? getNewFlowPickUpTs(event)
        : getOldFlowPickUpTs(event);
  }

  private Date getOldFlowPickUpTs(Event event) {
    return Objects.isNull(event.getAdditionalInfo().get(UNLOAD_TS))
        ? null
        : getPickUpDate(event, (Date) event.getAdditionalInfo().get(UNLOAD_TS));
  }

  public List<Event> updatePickUpTime(Long deliveryNumber, Date unloadingTs) {
    List<Event> events =
        getEventRepository().findAllByDeliveryNumberAndPickUpTimeIsNull(deliveryNumber);
    events.forEach(event -> event.setPickUpTime(getPickUpDate(event, unloadingTs)));
    return getEventRepository().saveAll(events);
  }
}
