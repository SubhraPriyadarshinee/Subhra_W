package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.item.rules.HazmatValidateRule;
import com.walmart.move.nim.receiving.core.item.rules.LimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonRule;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.LabelDownloadEventService;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.mockito.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcItemValidatorTest {

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks RdcItemValidator rdcItemValidator;
  @Mock LabelDataService labelDataService;
  @Captor private ArgumentCaptor<List<LabelData>> labelDataListCaptor;
  @Mock RdcInstructionUtils rdcInstructionUtils;
  @Mock LimitedQtyRule limitedQtyRule;
  @Mock LithiumIonRule lithiumIonRule;
  @Mock HazmatValidateRule hazmatValidateRule;
  @Mock AppConfig appConfig;
  @Mock LabelDownloadEventService labelDownloadEventService;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32679);
  }

  @AfterMethod
  public void shutdownMocks() {
    reset(
        rdcInstructionUtils,
        tenantSpecificConfigReader,
        limitedQtyRule,
        lithiumIonRule,
        labelDataService,
        hazmatValidateRule,
        appConfig);
  }

  @Test
  private void testBreakPackValidationRejectReason_aclEnabled() throws IOException {
    doReturn(Collections.singletonList(32679)).when(appConfig).getAclEnabledSitesList();
    doReturn(Collections.singletonList(32818)).when(appConfig).getSymEnabledSitesList();

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    RejectReason rejectReason =
        rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());
    assertEquals(rejectReason, RejectReason.BREAKOUT);
  }

  @Test
  private void testBreakPackValidationRejectReason_symEnabled() throws IOException {
    doReturn(Collections.singletonList(32818)).when(appConfig).getAclEnabledSitesList();
    doReturn(Collections.singletonList(32679)).when(appConfig).getSymEnabledSitesList();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setIsConveyable(Boolean.TRUE);
    RejectReason rejectReason =
        rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());
    assertEquals(rejectReason, RejectReason.BREAKOUT);
  }

  @Test
  private void testMasterPackValidationRejectReason() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("M");
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setIsConveyable(Boolean.TRUE);
    RejectReason rejectReason =
        rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());
    assertEquals(rejectReason, RejectReason.RDC_MASTER_PACK);
  }

  @Test
  private void testNonConValidationRejectReason() throws IOException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode(Arrays.asList((RdcConstants.NON_CON_ITEM_HANDLING_CODES)).get(0));
    RejectReason rejectReason =
        rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());
    assertEquals(rejectReason, RejectReason.RDC_NONCON);
  }

  @Test
  private void testNonConValidationRejectReason_invalidCombinations() throws IOException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(Arrays.asList("CC", "CI", "CJ"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("D");
    RejectReason rejectReason =
        rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());
    assertEquals(rejectReason, RejectReason.RDC_NONCON);
  }

  @Test
  private void testValidItem() throws IOException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(Arrays.asList("CC", "CI", "CJ"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    RejectReason rejectReason =
        rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());
    assertNull(rejectReason);
  }

  @Test
  private void testXBlockValidationRejectReason() throws IOException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForXBlockItem();
    RejectReason rejectReason =
        rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());
    assertEquals(rejectReason, RejectReason.X_BLOCK);
  }

  @Test
  private void testHazmatValidationRejectReason() throws IOException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(Arrays.asList("CC", "CI", "CJ", "BC", "BI", "BJ"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setIsHazmat(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_HAZAMT_ITEM_VALIDATION_DISABLED,
            false))
        .thenReturn(false);
    doReturn(true).when(hazmatValidateRule).validateRule(any(DeliveryDocumentLine.class));
    RejectReason rejectReason =
        rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());
    assertEquals(rejectReason, RejectReason.RDC_HAZMAT);
  }

  @Test
  private void testLimitedQtyValidationRejectReason() throws IOException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(Arrays.asList("CC", "CI", "CJ", "BC", "BI", "BJ"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    doReturn(true).when(limitedQtyRule).validateRule(any(DeliveryDocumentLine.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_LIMITED_QTY_VALIDATION_DISABLED,
            false))
        .thenReturn(false);
    RejectReason rejectReason =
        rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());
    assertEquals(rejectReason, RejectReason.RDC_LIMITED_ITEM);
  }

  @Test
  private void testLithiumIonValidationRejectReason() throws IOException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(Arrays.asList("CC", "CI", "CJ", "BC", "BI", "BJ"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_LITHIUM_ION_VALIDATION_DISABLED,
            false))
        .thenReturn(false);
    doReturn(true).when(lithiumIonRule).validateRule(any(DeliveryDocumentLine.class));
    RejectReason rejectReason =
        rdcItemValidator.validateItem(deliveryDocumentList.get(0).getDeliveryDocumentLines());
    assertEquals(rejectReason, RejectReason.RDC_LITHIUM_ION);
  }

  public static LabelData getMockLabelData() {
    return LabelData.builder()
        .deliveryNumber(94769060L)
        .purchaseReferenceNumber("3615852071")
        .isDAConveyable(Boolean.TRUE)
        .itemNumber(566051127L)
        .possibleUPC(
            "{\"sscc\":null,\"orderableGTIN\":\"10074451115207\",\"consumableGTIN\":\"00074451115200\",\"catalogGTIN\":null}")
        .lpns(
            "[\"c32987000000000000000004\",\"c32987000000000000000005\",\"c32987000000000000000006\"]")
        .lpnsCount(6)
        .labelSequenceNbr(20231016000100001L)
        .labelType(LabelType.ORDERED)
        .status(ReceivingConstants.AVAILABLE)
        .build();
  }
}
