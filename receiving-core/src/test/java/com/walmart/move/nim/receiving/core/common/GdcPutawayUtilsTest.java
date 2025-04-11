package com.walmart.move.nim.receiving.core.common;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.witron.WitronPutawayMessage;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Map;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GdcPutawayUtilsTest {

  @BeforeMethod
  public void setup() {
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
  }

  @Test
  public void testPrepareWitronPutawayHeaders() {
    Map<String, Object> messageHeader =
        GdcPutawayUtils.prepareWitronPutawayHeaders(MockHttpHeaders.getHeaders());

    assertNotNull(messageHeader);
    assertEquals(messageHeader.get("eventType"), "PUTAWAY_REQUEST");
    assertEquals(messageHeader.get("requestorId"), "receiving");
    assertEquals(messageHeader.get("facilityCountryCode"), "US");
    assertEquals(messageHeader.get("facilityNum"), "32612");
    assertEquals(messageHeader.get("messageId"), "1a2bc3d4");
    assertEquals(messageHeader.get("correlationId"), "1a2bc3d4");
    assertEquals(messageHeader.get("version"), 3);
  }

  @Test
  public void testPrepareWitronPutawayMessage() {
    WitronPutawayMessage witronPutawayMessage =
        GdcPutawayUtils.prepareWitronPutawayMessage(
            MockContainer.getContainer(), ReceivingConstants.PUTAWAY_ADD_ACTION);

    assertNotNull(witronPutawayMessage);
    assertEquals(witronPutawayMessage.getAction(), "add");
    assertEquals(witronPutawayMessage.getTrackingId(), "B67387000020002031");
    assertEquals(
        witronPutawayMessage.getContents().get(0).getPurchaseReferenceNumber(), "199557349");
  }
}
