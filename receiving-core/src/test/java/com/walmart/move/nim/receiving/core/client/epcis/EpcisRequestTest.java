package com.walmart.move.nim.receiving.core.client.epcis;

import static org.testng.Assert.*;

import java.util.Collections;
import org.testng.annotations.Test;

public class EpcisRequestTest {

  @Test
  public void testTestToString() {
    EpcisRequest.ChildEPCs childEPCs = new EpcisRequest.ChildEPCs(Collections.singletonList("epc"));
    EpcisRequest epcisRequest = new EpcisRequest();
    epcisRequest.setIch(true);
    epcisRequest.setValidationPerformed(true);
    epcisRequest.setParentID("parent");
    epcisRequest.setReasonCode("reason");
    epcisRequest.setChildEPCs(childEPCs);
    assertNotNull(epcisRequest.toString());
  }
}
