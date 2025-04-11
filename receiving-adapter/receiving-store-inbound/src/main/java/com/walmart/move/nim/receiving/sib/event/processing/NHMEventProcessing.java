package com.walmart.move.nim.receiving.sib.event.processing;

import static com.walmart.move.nim.receiving.sib.utils.Constants.*;

import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.utils.Util;
import java.util.Date;

public class NHMEventProcessing extends EventProcessing {

  @Override
  public Date decoratePickupTime(Event event) {
    return getSibManagedConfig().isEnableNewAvailabilityFlow()
        ? getNewFlowPickUpTs(event)
        : Util.addHoursToJavaUtilDate(
            (Date) event.getAdditionalInfo().get(CREATE_TS),
            getSibManagedConfig().getNhmEventPickTimeDelay());
  }
}
