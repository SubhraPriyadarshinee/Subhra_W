package com.walmart.move.nim.receiving.core.item.rules;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.DotHazardousClass;
import com.walmart.move.nim.receiving.core.model.Mode;
import com.walmart.move.nim.receiving.core.model.TransportationModes;
import java.util.Arrays;
import java.util.Collections;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LithiumIonLimitedQtyRuleTest {

  @InjectMocks private LithiumIonLimitedQtyRule lithiumIonLimitedQtyRule;

  @BeforeClass
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testLithiumIonAndLimitedQty_Success() {
    boolean isLithiumIonItem =
        lithiumIonLimitedQtyRule.validateRule(
            getMockTransportationModeForLithiumIonLimitedQtySuccess());
    assertTrue(isLithiumIonItem);
  }

  @Test
  public void testLithiumIonLimitedQtyRule_EmptyPackageInstruction() {
    boolean isLithiumIonItem =
        lithiumIonLimitedQtyRule.validateRule(
            getMockTransportationModeForLithiumIonLimitedQty_Failure());
    assertFalse(isLithiumIonItem);
  }

  @Test
  public void testLithiumIonLimitedQtyRule_NullPackageInstruction() {
    boolean isLithiumIonItem =
        lithiumIonLimitedQtyRule.validateRule(
            getMockTransportationModeForLithiumIonLimitedQtyWithNullPackageInstruction());
    assertFalse(isLithiumIonItem);
  }

  private DeliveryDocumentLine getMockTransportationModeForLithiumIonLimitedQtySuccess() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("LTD-Q");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(Arrays.asList("970"));
    transportationModes.setMode(mode);
    deliveryDocumentLine.setTransportationModes(Arrays.asList(transportationModes));
    return deliveryDocumentLine;
  }

  private DeliveryDocumentLine getMockTransportationModeForLithiumIonLimitedQty_Failure() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("LTD-Q");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(Collections.emptyList());
    transportationModes.setMode(mode);
    deliveryDocumentLine.setTransportationModes(Arrays.asList(transportationModes));
    return deliveryDocumentLine;
  }

  private DeliveryDocumentLine
      getMockTransportationModeForLithiumIonLimitedQtyWithNullPackageInstruction() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("LTD-Q");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(null);
    transportationModes.setMode(mode);
    deliveryDocumentLine.setTransportationModes(Arrays.asList(transportationModes));
    return deliveryDocumentLine;
  }
}
