package com.walmart.move.nim.receiving.core.item.rules;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.DotHazardousClass;
import com.walmart.move.nim.receiving.core.model.Mode;
import com.walmart.move.nim.receiving.core.model.TransportationModes;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class HazmatValidateRuleTest {
  @InjectMocks private HazmatValidateRule hazmatValidateRule;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InstructionUtils InstructionUtils;

  @BeforeClass
  public void setup() throws Exception {
    TenantContext.setFacilityNum(32818);
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testValidateRule() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setHazmatVerifiedOn(new Date());
    deliveryDocumentLine.setItemNbr(123l);
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    boolean ishazmat = hazmatValidateRule.validateRule(deliveryDocumentLine);
    assertFalse(ishazmat);
  }

  @Test
  public void testValidateRuleWithTransportMode() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setHazmatVerifiedOn(new Date());
    deliveryDocumentLine.setItemNbr(123l);
    List<TransportationModes> transportationModes = new ArrayList<>();
    TransportationModes transportationMode = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(HAZMAT_ITEM_GROUND_TRANSPORTATION);
    transportationMode.setMode(mode);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode(HAZMAT_ITEM_OTHER_REGULATED_MATERIAL);
    transportationMode.setDotHazardousClass(dotHazardousClass);
    transportationMode.setDotIdNbr("1");
    transportationModes.add(transportationMode);
    transportationMode.setDotRegionCode(HAZMAT_ITEM_REGION_CODE_UN);
    deliveryDocumentLine.setTransportationModes(transportationModes);
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    boolean ishazmat = hazmatValidateRule.validateRule(deliveryDocumentLine);
    assertFalse(ishazmat);
  }

  @Test
  public void testValidateRuleWithRegionCodeAnd365DaysAbove() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    Date date = Date.from(Instant.parse("2000-01-01T00:00:00.000Z"));
    deliveryDocumentLine.setHazmatVerifiedOn(date);
    deliveryDocumentLine.setItemNbr(123l);
    List<TransportationModes> transportationModes = new ArrayList<>();
    TransportationModes transportationMode = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(HAZMAT_ITEM_GROUND_TRANSPORTATION);
    transportationMode.setMode(mode);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("MOCK_CODE");
    transportationMode.setDotHazardousClass(dotHazardousClass);
    transportationMode.setDotIdNbr("1");
    transportationModes.add(transportationMode);
    transportationMode.setDotRegionCode(HAZMAT_ITEM_REGION_CODE_UN);
    deliveryDocumentLine.setTransportationModes(transportationModes);
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    boolean ishazmat = hazmatValidateRule.validateRule(deliveryDocumentLine);
    assertTrue(ishazmat);
  }
}
