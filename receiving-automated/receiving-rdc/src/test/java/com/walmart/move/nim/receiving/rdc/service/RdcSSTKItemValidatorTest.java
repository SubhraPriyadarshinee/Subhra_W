package com.walmart.move.nim.receiving.rdc.service;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.item.rules.HazmatValidateRule;
import com.walmart.move.nim.receiving.core.item.rules.LimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonRule;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.LabelDownloadEventService;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcSSTKInstructionUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcSSTKItemValidatorTest {

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks RdcSSTKItemValidator rdcSSTKItemValidator;
  @Mock LabelDataService labelDataService;
  @Captor private ArgumentCaptor<List<LabelData>> labelDataListCaptor;
  @Mock RdcInstructionUtils rdcInstructionUtils;
  @Mock LimitedQtyRule limitedQtyRule;
  @Mock LithiumIonRule lithiumIonRule;
  @Mock HazmatValidateRule hazmatValidateRule;
  @Mock AppConfig appConfig;
  @Mock LabelDownloadEventService labelDownloadEventService;
  @Mock RdcSSTKInstructionUtils rdcSSTKInstructionUtils;
  private static Gson gson = new Gson();

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
  private void testMasterPackValidationRejectReason() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        getDeliveryDocuments("GdmMappedResponseV2_DA_Item.json");
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
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
    assertEquals(rejectReason, RejectReason.RDC_MASTER_PACK);
  }

  @Test
  private void testSstkValidationRejectReason_SymEnabled() throws IOException {
    doReturn(Collections.singletonList(32818)).when(appConfig).getAclEnabledSitesList();
    doReturn(Collections.singletonList(32679)).when(appConfig).getSymEnabledSitesList();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocumentList =
        getDeliveryDocuments("GdmMappedResponseV2_DA_Item.json");
    doReturn(false)
        .when(rdcSSTKInstructionUtils)
        .isAtlasItemSymEligible(any(DeliveryDocumentLine.class));
    RejectReason rejectReason =
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
    assertEquals(rejectReason, RejectReason.RDC_SSTK);
  }

  @Test
  private void testSstkValidationRejectReason_AclEnabled() throws IOException {
    doReturn(Collections.singletonList(32679)).when(appConfig).getAclEnabledSitesList();
    doReturn(Collections.singletonList(32818)).when(appConfig).getSymEnabledSitesList();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocumentList =
        getDeliveryDocuments("GdmMappedResponseV2_SSTK_Item.json");
    RejectReason rejectReason =
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
    assertEquals(rejectReason, RejectReason.RDC_SSTK);
  }

  @Test
  private void testSstkValidationRejectReason_SymEnabled_Sym_InEligible() throws IOException {
    doReturn(Collections.singletonList(32818)).when(appConfig).getAclEnabledSitesList();
    doReturn(Collections.singletonList(32679)).when(appConfig).getSymEnabledSitesList();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_BREAKPACK_VALIDATION_DISABLED,
            false);
    List<DeliveryDocument> deliveryDocumentList =
        getDeliveryDocuments("GdmMappedResponseV2_SSTK_Item.json");
    doReturn(false)
        .when(rdcSSTKInstructionUtils)
        .isAtlasItemSymEligible(any(DeliveryDocumentLine.class));
    RejectReason rejectReason =
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
    assertEquals(rejectReason, RejectReason.RDC_SSTK);
  }

  @Test
  private void testXBlockValidationRejectReason() throws IOException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocumentList =
        getDeliveryDocuments("GdmMappedResponseV2_XBlock.json");
    RejectReason rejectReason =
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
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
        getDeliveryDocuments("GdmMappedResponseV2_DA_Item.json");
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
    doReturn(true)
        .when(rdcSSTKInstructionUtils)
        .validateHazmatValidateRule(any(DeliveryDocumentLine.class));
    RejectReason rejectReason =
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
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
        getDeliveryDocuments("GdmMappedResponseV2_DA_Item.json");
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
    doReturn(true)
        .when(rdcSSTKInstructionUtils)
        .validateLimitedQtyRule(any(DeliveryDocumentLine.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_LIMITED_QTY_VALIDATION_DISABLED,
            false))
        .thenReturn(false);
    doReturn(false)
        .when(rdcSSTKInstructionUtils)
        .validateHazmatValidateRule(any(DeliveryDocumentLine.class));
    RejectReason rejectReason =
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
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
        getDeliveryDocuments("GdmMappedResponseV2_DA_Item.json");
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
    doReturn(true)
        .when(rdcSSTKInstructionUtils)
        .validateLithiumIonRule(any(DeliveryDocumentLine.class));
    doReturn(false)
        .when(rdcSSTKInstructionUtils)
        .validateLimitedQtyRule(any(DeliveryDocumentLine.class));
    RejectReason rejectReason =
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
    assertEquals(rejectReason, RejectReason.RDC_LITHIUM_ION);
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
        getDeliveryDocuments("GdmMappedResponseV2_DA_BreakPack_Item.json");
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_SSTK_VALIDATION_DISABLED,
            false))
        .thenReturn(true);
    when(rdcSSTKInstructionUtils.isBreakPackConveyPicks(any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    RejectReason rejectReason =
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
    assertEquals(rejectReason, RejectReason.BREAKOUT);
  }

  @Test
  private void testBreakPackValidationRejectReason() throws IOException {
    doReturn(Collections.singletonList(3269)).when(appConfig).getAclEnabledSitesList();
    doReturn(Collections.singletonList(32818)).when(appConfig).getSymEnabledSitesList();

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocumentList =
        getDeliveryDocuments("GdmMappedResponseV2_DA_BreakPack_Item.json");
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_SSTK_VALIDATION_DISABLED,
            false))
        .thenReturn(true);
    RejectReason rejectReason =
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
    assertNull(rejectReason);
  }

  @Test
  private void testBreakPackValidationRejectReasonWithVnpkAndWhpkDiff() throws IOException {
    doReturn(Collections.singletonList(3269)).when(appConfig).getAclEnabledSitesList();
    doReturn(Collections.singletonList(32679)).when(appConfig).getSymEnabledSitesList();

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    List<DeliveryDocument> deliveryDocumentList =
        getDeliveryDocuments("GdmMappedResponseV2_DA_BreakPack_Item.json");
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_SSTK_VALIDATION_DISABLED,
            false))
        .thenReturn(true);
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setVnpkQty(3);
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setWhpkQty(2);
    RejectReason rejectReason =
        rdcSSTKItemValidator.validateItem(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0));
    assertEquals(rejectReason, RejectReason.BREAKOUT);
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

  public List<DeliveryDocument> getDeliveryDocuments(String fileName) throws IOException {
    File resource = new ClassPathResource(fileName).getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    return gdmDocuments;
  }
}
