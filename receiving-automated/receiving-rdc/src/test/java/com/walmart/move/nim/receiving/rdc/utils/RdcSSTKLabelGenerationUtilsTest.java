package com.walmart.move.nim.receiving.rdc.utils;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.LabelDownloadEventMiscInfo;
import com.walmart.move.nim.receiving.core.model.label.acl.ACLLabelDataTO;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymLabelType;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.rdc.model.LabelDownloadEventStatus;
import com.walmart.move.nim.receiving.rdc.model.symbotic.SymInventoryStatus;
import com.walmart.move.nim.receiving.rdc.model.symbotic.SymLabelData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.logging.log4j.util.Strings;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcSSTKLabelGenerationUtilsTest {

  @InjectMocks private RdcSSTKLabelGenerationUtils rdcSSTKLabelGenerationUtils;
  @Mock private AppConfig appConfig;
  @Mock private RdcLabelGenerationUtils rdcLabelGenerationUtils;
  @Mock private RdcSSTKInstructionUtils rdcSSTKInstructionUtils;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DeliveryService deliveryService;
  private String facilityNum = "32679";
  private String facilityCountryCode = "us";
  private static final Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset(tenantSpecificConfigReader);
    reset(appConfig);
  }

  @Test
  public void buildLabelDownloadForHawkeyeForSSTKWithInvalidPackTypeAndHandlingCodeTest() {
    List<LabelData> labelDataList = new ArrayList<>();
    com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument =
        new com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument();
    deliveryDocument.setDeliveryNumber(812345l);
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine>
        deliveryDocumentLines = new ArrayList<>();
    com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine deliveryDocumentLine =
        new com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(5678l);
    deliveryDocumentLine.setItemDescription1("mockItemDesc");
    deliveryDocumentLine.setItemUPC("53545");
    deliveryDocumentLine.setVendorUPC("65465");
    deliveryDocumentLine.setCaseUPC("75878876897070070707");
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("888");
    itemData.setHandlingCode("666");
    deliveryDocumentLine.setAdditionalInfo(itemData);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    ACLLabelDataTO aclLabelDataTO =
        rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            deliveryDocumentLine,
            labelDataList,
            RejectReason.HAZMAT,
            deliveryDocument.getDeliveryNumber());
    assertNotNull(aclLabelDataTO);
    assertEquals(aclLabelDataTO.getGroupNumber(), deliveryDocument.getDeliveryNumber().toString());
    assertFalse(aclLabelDataTO.getScanItems().isEmpty());
  }

  @Test
  public void buildLabelDownloadForHawkeyeForSSTKTest() {
    List<LabelData> labelDataList = new ArrayList<>();
    LabelData labelData = new LabelData();
    labelData.setLabelSequenceNbr(123l);
    labelData.setPurchaseReferenceNumber("765786897");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setTrackingId("6897089097");
    labelData.setItemNumber(1523l);
    labelDataList.add(labelData);
    com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument =
        new com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument();
    deliveryDocument.setDeliveryNumber(812345l);
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine>
        deliveryDocumentLines = new ArrayList<>();
    com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine deliveryDocumentLine =
        new com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(5678l);
    deliveryDocumentLine.setItemDescription1("mockItemDesc");
    deliveryDocumentLine.setItemUPC("53545");
    deliveryDocumentLine.setVendorUPC("65465");
    deliveryDocumentLine.setCaseUPC("75878");
    deliveryDocumentLine.setVnpkQty(8);
    deliveryDocumentLine.setWhpkQty(8);
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    deliveryDocumentLine.setAdditionalInfo(itemData);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocumentLine.setPurchaseRefType(PurchaseReferenceType.SSTKU.name());
    deliveryDocumentLine.setPurchaseReferenceLegacyType(20);
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Arrays.asList("CI", "CJ", "CC"));
    when(rdcLabelGenerationUtils.getLabelTagType(anyString(), anyBoolean()))
        .thenReturn(SymLabelType.SHIPPING.toString());
    when(rdcLabelGenerationUtils.getFreightType(anyString(), anyBoolean()))
        .thenReturn(SymFreightType.BRPK.toString());
    when(rdcSSTKInstructionUtils.isHazmatItemForSSTK(any())).thenReturn(true);
    ACLLabelDataTO aclLabelDataTO =
        rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            deliveryDocumentLine,
            labelDataList,
            RejectReason.HAZMAT,
            deliveryDocument.getDeliveryNumber());
    SymLabelData symLabelData =
        gson.fromJson(
            aclLabelDataTO.getScanItems().get(0).getLabels().get(0).getLabelData(),
            SymLabelData.class);
    assertEquals(aclLabelDataTO.getGroupNumber(), deliveryDocument.getDeliveryNumber().toString());
    assertFalse(aclLabelDataTO.getScanItems().isEmpty());
    assertEquals(
        aclLabelDataTO.getScanItems().get(0).getLabels().get(0).getPurchaseReferenceNumber(),
        labelData.getPurchaseReferenceNumber());
    assertEquals(
        aclLabelDataTO.getScanItems().get(0).getLabels().get(0).getPurchaseReferenceLineNumber(),
        labelData.getPurchaseReferenceLineNumber());
    assertEquals(symLabelData.getHoldStatus(), SymInventoryStatus.HOLD.getStatus());
  }

  @Test
  public void testIsSstkLabelDownloadEvent() {
    assertTrue(rdcSSTKLabelGenerationUtils.isSSTKLabelDownloadEvent(getMockLabelDownloadEvent()));
  }

  @Test
  public void testFetchDeliveryDetails() throws ReceivingException {
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    rdcSSTKLabelGenerationUtils.fetchDeliveryDetails(Strings.EMPTY, 1234567L);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any());
  }

  @Test
  public void testFetchDeliveryDetails_exception() throws ReceivingException {
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doThrow(new ReceivingException("Mock exception"))
        .when(deliveryService)
        .getDeliveryDetails(anyString(), anyLong());
    rdcSSTKLabelGenerationUtils.fetchDeliveryDetails(
        ReceivingConstants.DELIVERY_SERVICE_KEY, 1234567L);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any());
  }

  @Test
  public void testIsAsrsAlignmentSymEligible_true() {
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(Collections.singletonList(ReceivingConstants.SYM_SYSTEM_DEFAULT_VALUE));
    boolean isAsrsAlignmentSymEligible =
        rdcSSTKLabelGenerationUtils.isAsrsAlignmentSymEligible(
            ReceivingConstants.SYM_SYSTEM_DEFAULT_VALUE);
    verify(appConfig, times(2)).getValidSymAsrsAlignmentValues();
    assertTrue(isAsrsAlignmentSymEligible);
  }

  @Test
  public void testIsAsrsAlignmentSymEligible_false() {
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(Collections.singletonList(ReceivingConstants.SYM_SYSTEM_DEFAULT_VALUE));
    boolean isAsrsAlignmentSymEligible =
        rdcSSTKLabelGenerationUtils.isAsrsAlignmentSymEligible(ReceivingConstants.PTL_ASRS_VALUE);
    verify(appConfig, times(2)).getValidSymAsrsAlignmentValues();
    assertFalse(isAsrsAlignmentSymEligible);
  }

  private LabelDownloadEvent getMockLabelDownloadEvent() {
    LabelDownloadEventMiscInfo labelDownloadEventMiscInfo =
        LabelDownloadEventMiscInfo.builder().labelType("SSTK").build();
    return LabelDownloadEvent.builder()
        .deliveryNumber(94769060L)
        .itemNumber(566051127L)
        .purchaseReferenceNumber("3615852071")
        .createTs(new Date())
        .status(LabelDownloadEventStatus.PROCESSED.toString())
        .miscInfo(gson.toJson(labelDownloadEventMiscInfo))
        .build();
  }
}
