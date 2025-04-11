package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.common.SymAsrsSorterMapping;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.DotHazardousClass;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.Mode;
import com.walmart.move.nim.receiving.core.model.TransportationModes;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Instant;
import java.util.*;
import org.junit.Assert;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcSSTKInstructionUtilsTest {

  @InjectMocks private RdcSSTKInstructionUtils rdcSSTKInstructionUtils;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private AppConfig appConfig;
  private static final String facilityNum = "32818";
  private final List<String> VALID_ASRS_LIST =
      Arrays.asList(
          ReceivingConstants.SYM_BRKPK_ASRS_VALUE, ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
  }

  @AfterMethod
  public void resetMocks() {
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void isHazmatItemForSSTKTest() {
    com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine deliveryDocumentLine =
        new com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(8798l);
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    Boolean isHazmat = rdcSSTKInstructionUtils.isHazmatItemForSSTK(deliveryDocumentLine);
    assertTrue(isHazmat);
  }

  @Test
  public void isHazmatItemForSSTKWithTransportationModeValidatedForHazmatTest() {
    com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine deliveryDocumentLine =
        new com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine();
    Boolean isHazmat = rdcSSTKInstructionUtils.isHazmatItemForSSTK(deliveryDocumentLine);
    assertFalse(isHazmat);
  }

  @Test
  public void isAtlasItemSymEligibleTest() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData itemData = new ItemData();
    itemData.setAsrsAlignment(String.valueOf(SymAsrsSorterMapping.SYM2));
    itemData.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    boolean atlasItemSymEligible =
        rdcSSTKInstructionUtils.isAtlasItemSymEligible(deliveryDocumentLine);
    assertTrue(atlasItemSymEligible);
  }

  @Test
  public void isAtlasItemSymEligibleNonPrimeTest() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData itemData = new ItemData();
    itemData.setAsrsAlignment(String.valueOf(SymAsrsSorterMapping.SYM2));
    itemData.setSlotType(RESERVE_SLOT_TYPE);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    boolean atlasItemSymEligible =
        rdcSSTKInstructionUtils.isAtlasItemSymEligible(deliveryDocumentLine);
    assertFalse(atlasItemSymEligible);
  }

  @Test
  public void isAtlasItemSymEligibleDifferentAsrsAlignmentTest() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData itemData = new ItemData();
    itemData.setAsrsAlignment(String.valueOf(SymAsrsSorterMapping.SYM2));
    itemData.setSlotType(RESERVE_SLOT_TYPE);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(Arrays.asList());
    boolean atlasItemSymEligible =
        rdcSSTKInstructionUtils.isAtlasItemSymEligible(deliveryDocumentLine);
    assertFalse(atlasItemSymEligible);
  }

  @Test
  public void isAtlasItemSymEligibleNoAsrsAlignmentTest() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData itemData = new ItemData();
    itemData.setSlotType(RESERVE_SLOT_TYPE);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(Arrays.asList());
    boolean atlasItemSymEligible =
        rdcSSTKInstructionUtils.isAtlasItemSymEligible(deliveryDocumentLine);
    assertFalse(atlasItemSymEligible);
  }

  @Test
  public void validateLimitedQtyRuleTest() {
    boolean isLithiumIonItem =
        rdcSSTKInstructionUtils.validateLimitedQtyRule(
            getMockTransportationModeForLithiumIonLimitedQty());
    assertTrue(isLithiumIonItem);
  }

  @Test
  public void testLithiumIonLimitedQtyRule_EmptyPackageInstruction() {
    DeliveryDocumentLine deliveryDocumentLine = getMockTransportationModeForLithiumIonLimitedQty();
    deliveryDocumentLine.getTransportationModes().get(0).setPkgInstruction(Collections.emptyList());
    boolean isLithiumIonItem = rdcSSTKInstructionUtils.validateLimitedQtyRule(deliveryDocumentLine);
    assertTrue(isLithiumIonItem);
  }

  @Test
  public void testLithiumIonRule_Valid_data_Success() {
    boolean isLithiumIonItem =
        rdcSSTKInstructionUtils.validateLithiumIonRule(getMockTransportationModeForLithiumIon());
    assertTrue(isLithiumIonItem);
  }

  @Test
  public void testLithiumIonRule_EmptyPackageInstruction() {
    DeliveryDocumentLine deliveryDocumentLine = getMockTransportationModeForLithiumIon();
    deliveryDocumentLine.getTransportationModes().get(0).setPkgInstruction(Collections.emptyList());
    boolean isLithiumIonItem = rdcSSTKInstructionUtils.validateLithiumIonRule(deliveryDocumentLine);
    assertFalse(isLithiumIonItem);
  }

  @Test
  public void testLithiumIonRule_NotGroundTransportationMode() {
    boolean isLithiumIonItem =
        rdcSSTKInstructionUtils.validateLithiumIonRule(
            getMockTransportationModeForLithiumIon_NotGroundTransportMode());
    assertFalse(isLithiumIonItem);
  }

  @Test
  public void testValidateHazmatValidateRule() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setHazmatVerifiedOn(new Date());
    deliveryDocumentLine.setItemNbr(123l);
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            facilityNum, ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED, false))
        .thenReturn(true);
    boolean isHazmat = rdcSSTKInstructionUtils.validateHazmatValidateRule(deliveryDocumentLine);
    assertFalse(isHazmat);
  }

  @Test
  public void testValidateHazmatValidateRuleWithTransportMode() {
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
    boolean ishazmat = rdcSSTKInstructionUtils.validateHazmatValidateRule(deliveryDocumentLine);
    Assert.assertFalse(ishazmat);
  }

  @Test
  public void testValidateHazmatValidateRuleWithRegionCodeAnd365DaysAbove() {
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
    boolean ishazmat = rdcSSTKInstructionUtils.validateHazmatValidateRule(deliveryDocumentLine);
    assertTrue(ishazmat);
  }

  @Test
  public void isBreakPackConveyPicksWithNoHandlingCodeTest() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData itemData = new ItemData();
    deliveryDocumentLine.setAdditionalInfo(itemData);
    boolean breakPackConveyPicks =
        rdcSSTKInstructionUtils.isBreakPackConveyPicks(deliveryDocumentLine);
    assertFalse(breakPackConveyPicks);
  }

  @Test
  public void isBreakPackConveyPicksTest() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData itemData = new ItemData();
    itemData.setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    boolean breakPackConveyPicks =
        rdcSSTKInstructionUtils.isBreakPackConveyPicks(deliveryDocumentLine);
    assertTrue(breakPackConveyPicks);
  }

  private DeliveryDocumentLine getMockTransportationModeForLithiumIonLimitedQty() {
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

  private DeliveryDocumentLine getMockTransportationModeForLithiumIon() {
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
