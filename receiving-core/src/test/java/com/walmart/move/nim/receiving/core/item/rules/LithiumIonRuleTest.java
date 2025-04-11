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

public class LithiumIonRuleTest {

  @InjectMocks private LithiumIonRule lithiumIonRule;

  @BeforeClass
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testLithiumIonRule_Valid_data_Success() {
    boolean isLithiumIonItem =
        lithiumIonRule.validateRule(getMockTransportationModeForLithiumIonSuccess());
    assertTrue(isLithiumIonItem);
  }

  @Test
  public void testLithiumIonRule_EmptyPackageInstruction() {
    boolean isLithiumIonItem =
        lithiumIonRule.validateRule(getMockTransportationModeForLithiumIon_Failure());
    assertFalse(isLithiumIonItem);
  }

  @Test
  public void testLithiumIonRule_NotGroundTransportationMode() {
    boolean isLithiumIonItem =
        lithiumIonRule.validateRule(
            getMockTransportationModeForLithiumIon_NotGroundTransportMode());
    assertFalse(isLithiumIonItem);
  }

  private DeliveryDocumentLine getMockTransportationModeForLithiumIonSuccess() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("N/A");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(Arrays.asList("965"));
    transportationModes.setMode(mode);
    deliveryDocumentLine.setTransportationModes(Arrays.asList(transportationModes));
    return deliveryDocumentLine;
  }

  private DeliveryDocumentLine getMockTransportationModeForLithiumIon_Failure() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("N/A");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(Collections.emptyList());
    transportationModes.setMode(mode);
    deliveryDocumentLine.setTransportationModes(Arrays.asList(transportationModes));
    return deliveryDocumentLine;
  }

  private DeliveryDocumentLine getMockTransportationModeForLithiumIon_NotGroundTransportMode() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(0);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("N/A");
    transportationModes.setMode(mode);
    deliveryDocumentLine.setTransportationModes(Arrays.asList(transportationModes));
    return deliveryDocumentLine;
  }
}
