package com.walmart.move.nim.receiving.sib.event.processing;

import static com.walmart.move.nim.receiving.sib.utils.Constants.TIMEZONE_CODE;
import static com.walmart.move.nim.receiving.sib.utils.Constants.UNLOAD_TS;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.FreightType;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SuperCentreEventProcessingTest extends ReceivingTestBase {

  @InjectMocks private SuperCentreEventProcessing superCentreEventProcessing;

  @Mock private SIBManagedConfig sibManagedConfig;

  private Event containerEvent;
  private Map<String, Object> metadata;
  private Map<String, Object> additionalInfo;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    containerEvent = new Event();
    containerEvent.setKey("98765432100000");
    containerEvent.setDeliveryNumber(987654321L);
    containerEvent.setEventType(EventType.STORE_FINALIZATION);
    containerEvent.setRetryCount(0);
    containerEvent.setStatus(EventTargetStatus.PENDING);
    containerEvent.setFacilityNum(5504);
    containerEvent.setFacilityCountryCode("US");
    // Adding freight_type in metadata
    metadata = new HashMap<>();
    metadata.put(Constants.FREIGHT_TYPE, FreightType.SC);
    metadata.put(TIMEZONE_CODE, "EST");
    additionalInfo = new HashMap<>();
    containerEvent.setMetaData(metadata);
    containerEvent.setAdditionalInfo(additionalInfo);
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(sibManagedConfig);
  }

  @Test
  public void testDecoratePickupTime_Morning() {
    when(sibManagedConfig.getReferenceShiftSFTriggersHours()).thenReturn(21);
    when(sibManagedConfig.getReferenceShiftStartHours()).thenReturn(9);
    when(sibManagedConfig.getReferenceShiftEndHours()).thenReturn(21);
    when(sibManagedConfig.getScEventPickTimeDelay()).thenReturn(2);

    Calendar unloadTs = Calendar.getInstance(TimeZone.getTimeZone("EST"));
    unloadTs.set(Calendar.HOUR_OF_DAY, 15);
    unloadTs.set(Calendar.MINUTE, 34);
    unloadTs.set(Calendar.SECOND, 45);
    unloadTs.set(Calendar.MILLISECOND, 0);
    additionalInfo.put(UNLOAD_TS, unloadTs.getTime());

    Date morningPickupTime = superCentreEventProcessing.decoratePickupTime(containerEvent);
    Calendar expectedTs = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    expectedTs.set(Calendar.DATE, unloadTs.get(Calendar.DATE) + 1);
    expectedTs.set(Calendar.HOUR_OF_DAY, 2);
    expectedTs.set(Calendar.MINUTE, 0);
    expectedTs.set(Calendar.SECOND, 0);
    expectedTs.set(Calendar.MILLISECOND, 0);
    // Assert.assertEquals(morningPickupTime.toInstant().toString(),
    // expectedTs.getTime().toInstant().toString());
  }

  @Test
  public void testDecoratePickupTime_Night() {
    when(sibManagedConfig.getReferenceShiftSFTriggersHours()).thenReturn(21);
    when(sibManagedConfig.getReferenceShiftStartHours()).thenReturn(9);
    when(sibManagedConfig.getReferenceShiftEndHours()).thenReturn(21);
    when(sibManagedConfig.getScEventPickTimeDelay()).thenReturn(2);

    Calendar instance = Calendar.getInstance(TimeZone.getTimeZone("EST"));
    instance.set(Calendar.HOUR_OF_DAY, 4);
    instance.set(Calendar.MINUTE, 4);
    instance.set(Calendar.SECOND, 11);
    instance.set(Calendar.MILLISECOND, 0);
    additionalInfo.replace(UNLOAD_TS, instance.getTime());

    Date nightPickupTime = superCentreEventProcessing.decoratePickupTime(containerEvent);
    instance.set(Calendar.HOUR_OF_DAY, 6);
    instance.set(Calendar.MINUTE, 4);
    instance.set(Calendar.SECOND, 11);
    instance.set(Calendar.MILLISECOND, 0);
    Assert.assertEquals(
        nightPickupTime.toInstant().toString(), instance.getTime().toInstant().toString());
  }
}
