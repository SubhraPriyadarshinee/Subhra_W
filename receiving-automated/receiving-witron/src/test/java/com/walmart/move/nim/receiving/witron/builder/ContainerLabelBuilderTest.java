package com.walmart.move.nim.receiving.witron.builder;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_LPN_7_DIGIT_ENABLED;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.core.client.gls.model.GLSReceiveResponse;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.config.WitronManagedConfig;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.witron.model.ContainerLabel;
import java.util.Arrays;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ContainerLabelBuilderTest {

  @InjectMocks private ContainerLabelBuilder containerLabelBuilder;
  @Mock private TenantSpecificConfigReader configUtils;

  @Mock private WitronManagedConfig witronManagedConfig;

  @Mock private GDCFlagReader gdcFlagReader;

  private HttpHeaders httpHeaders;
  private DeliveryDocumentLine deliveryDocumentLine;
  private String lpn = "A10181107692957111";
  private String slot = "MG1234-12";

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(containerLabelBuilder, "configUtils", configUtils);
  }

  @BeforeMethod
  public void initMocks() {

    httpHeaders = GdcHttpHeaders.getHeaders();
    deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("0294235326");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setItemNbr(436617391l);
    deliveryDocumentLine.setDescription("TEST ITEM DESCR");
    deliveryDocumentLine.setPalletTie(6);
    deliveryDocumentLine.setPalletHigh(7);

    ItemData additionalInfo = new ItemData();
    additionalInfo.setProfiledWarehouseArea("CPS");
    additionalInfo.setWarehouseAreaCode("3");
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

    when(witronManagedConfig.getMechGDCProfiledWarehouseAreaValues())
        .thenReturn(Arrays.asList("CPS", "OPM", "GTP", "FPP", "CPZ"));
    when(witronManagedConfig.getNonMechGDCProfiledWarehouseAreaValues())
        .thenReturn(Arrays.asList("MAN"));
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");

    TenantContext.setFacilityNum(32612);
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(gdcFlagReader);
    reset(witronManagedConfig);
  }

  @Test
  public void testGenerateContainerLabel() {
    when(configUtils.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()), IS_WITRON_LABEL_ENABLE, false))
        .thenReturn(true);
    when(gdcFlagReader.isAutomatedDC()).thenReturn(true);
    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabel(lpn, slot, deliveryDocumentLine, httpHeaders);

    assertEquals(lpn, containerLabel.getPrintRequests().get(0).getLabelIdentifier());
    assertTrue(containerLabel.getPrintRequests().get(0).getData().get(2).getValue().length() > 7);
    assertEquals(
        ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE,
        containerLabel.getPrintRequests().get(0).getFormatName());
    assertEquals(
        "warehouseArea", containerLabel.getPrintRequests().get(0).getData().get(3).getKey());
    assertEquals("Dairy", containerLabel.getPrintRequests().get(0).getData().get(3).getValue());
    assertEquals(
        "profiledWarehouseArea",
        containerLabel.getPrintRequests().get(0).getData().get(4).getKey());
    assertEquals("CPS", containerLabel.getPrintRequests().get(0).getData().get(4).getValue());
    assertEquals("MECH", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
  }

  @Test
  public void testGenerateContainerLabel_witronLableDisable() {
    when(configUtils.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()), IS_WITRON_LABEL_ENABLE, false))
        .thenReturn(false);
    when(gdcFlagReader.isAutomatedDC()).thenReturn(true);

    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabel(lpn, slot, deliveryDocumentLine, httpHeaders);

    assertEquals(lpn, containerLabel.getPrintRequests().get(0).getLabelIdentifier());
    assertTrue(containerLabel.getPrintRequests().get(0).getData().get(2).getValue().length() > 7);
    assertEquals(
        GDC_LABEL_FORMAT_NAME_V2, containerLabel.getPrintRequests().get(0).getFormatName());
    assertEquals(
        "warehouseArea", containerLabel.getPrintRequests().get(0).getData().get(3).getKey());
    assertEquals("Dairy", containerLabel.getPrintRequests().get(0).getData().get(3).getValue());
    assertEquals(
        "profiledWarehouseArea",
        containerLabel.getPrintRequests().get(0).getData().get(4).getKey());
    assertEquals("CPS", containerLabel.getPrintRequests().get(0).getData().get(4).getValue());
    assertEquals(
        "A10181107692957111", containerLabel.getPrintRequests().get(0).getData().get(2).getValue());
    assertEquals("436617391", containerLabel.getPrintRequests().get(0).getData().get(6).getValue());
    assertEquals("6 - 7", containerLabel.getPrintRequests().get(0).getData().get(11).getValue());
    assertEquals("DD", containerLabel.getPrintRequests().get(0).getData().get(5).getValue());
    assertEquals("", containerLabel.getPrintRequests().get(0).getData().get(9).getValue());
    assertEquals("MECH", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
  }

  @Test
  public void testGenerateContainerLabelV2() {
    when(configUtils.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()), IS_WITRON_LABEL_ENABLE, false))
        .thenReturn(false);
    when(gdcFlagReader.isAutomatedDC()).thenReturn(false);
    deliveryDocumentLine.getAdditionalInfo().setProfiledWarehouseArea(null);

    GLSReceiveResponse glsReceiveResponse = MockInstruction.mockGlsResponse();

    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabelV2(
            glsReceiveResponse, deliveryDocumentLine, httpHeaders);

    assertEquals(
        glsReceiveResponse.getPalletTagId(),
        containerLabel.getPrintRequests().get(0).getLabelIdentifier());
    assertEquals(GDC_LABEL_FORMAT_NAME, containerLabel.getPrintRequests().get(0).getFormatName());
    assertTrue(containerLabel.getPrintRequests().get(0).getData().get(1).getValue().length() == 7);
    assertEquals("TAG-123", containerLabel.getPrintRequests().get(0).getData().get(1).getValue());
    assertEquals("436617391", containerLabel.getPrintRequests().get(0).getData().get(6).getValue());
    assertEquals("6 - 7", containerLabel.getPrintRequests().get(0).getData().get(11).getValue());
    assertEquals("DD", containerLabel.getPrintRequests().get(0).getData().get(5).getValue());
    assertEquals("", containerLabel.getPrintRequests().get(0).getData().get(9).getValue());
    assertEquals("SLOT-1", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
  }

  @Test
  public void testGenerateContainerLabelV3() {
    when(configUtils.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()), IS_GDC_LABEL_V3_ENABLE, false))
        .thenReturn(false);
    when(gdcFlagReader.isAutomatedDC()).thenReturn(false);
    deliveryDocumentLine.getAdditionalInfo().setProfiledWarehouseArea(null);

    GLSReceiveResponse glsReceiveResponse = MockInstruction.mockGlsResponse();

    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabelV2(
            glsReceiveResponse, deliveryDocumentLine, httpHeaders);

    assertEquals(
        glsReceiveResponse.getPalletTagId(),
        containerLabel.getPrintRequests().get(0).getLabelIdentifier());
    assertEquals(GDC_LABEL_FORMAT_NAME, containerLabel.getPrintRequests().get(0).getFormatName());
    assertTrue(containerLabel.getPrintRequests().get(0).getData().get(1).getValue().length() == 7);
    assertEquals("TAG-123", containerLabel.getPrintRequests().get(0).getData().get(1).getValue());
    assertEquals("436617391", containerLabel.getPrintRequests().get(0).getData().get(6).getValue());
    assertEquals("6 - 7", containerLabel.getPrintRequests().get(0).getData().get(11).getValue());
    assertEquals("DD", containerLabel.getPrintRequests().get(0).getData().get(5).getValue());
    assertEquals("", containerLabel.getPrintRequests().get(0).getData().get(9).getValue());
    assertEquals("SLOT-1", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
    assertEquals("lpn5", containerLabel.getPrintRequests().get(0).getData().get(14).getKey());
    assertEquals("G-123", containerLabel.getPrintRequests().get(0).getData().get(14).getValue());
    assertEquals("md", containerLabel.getPrintRequests().get(0).getData().get(15).getKey());
    assert containerLabel.getPrintRequests().get(0).getData().get(14).getValue().length() == 5;
    assert containerLabel.getPrintRequests().get(0).getData().get(15).getValue().length() == 5;
  }

  @Test
  public void testGenerateContainerLabelV3_blankTimeZone() {
    when(configUtils.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()), IS_GDC_LABEL_V3_ENABLE, false))
        .thenReturn(false);
    when(gdcFlagReader.isAutomatedDC()).thenReturn(false);
    deliveryDocumentLine.getAdditionalInfo().setProfiledWarehouseArea(null);
    when(configUtils.getDCTimeZone(any())).thenReturn(null);

    GLSReceiveResponse glsReceiveResponse = MockInstruction.mockGlsResponse();

    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabelV2(
            glsReceiveResponse, deliveryDocumentLine, httpHeaders);

    assertEquals(
        glsReceiveResponse.getPalletTagId(),
        containerLabel.getPrintRequests().get(0).getLabelIdentifier());
    assertEquals(GDC_LABEL_FORMAT_NAME, containerLabel.getPrintRequests().get(0).getFormatName());
    assertTrue(containerLabel.getPrintRequests().get(0).getData().get(1).getValue().length() == 7);
    assertEquals("TAG-123", containerLabel.getPrintRequests().get(0).getData().get(1).getValue());
    assertEquals("436617391", containerLabel.getPrintRequests().get(0).getData().get(6).getValue());
    assertEquals("6 - 7", containerLabel.getPrintRequests().get(0).getData().get(11).getValue());
    assertEquals("DD", containerLabel.getPrintRequests().get(0).getData().get(5).getValue());
    assertEquals("", containerLabel.getPrintRequests().get(0).getData().get(9).getValue());
    assertEquals("SLOT-1", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
    assertEquals("lpn5", containerLabel.getPrintRequests().get(0).getData().get(14).getKey());
    assertEquals("G-123", containerLabel.getPrintRequests().get(0).getData().get(14).getValue());
    assertEquals("md", containerLabel.getPrintRequests().get(0).getData().get(15).getKey());
    assert containerLabel.getPrintRequests().get(0).getData().get(14).getValue().length() == 5;
    assert containerLabel.getPrintRequests().get(0).getData().get(15).getValue().length() == 5;
  }

  @Test
  public void testGenerateContainerLabelV2_itemSizePresent() {
    GLSReceiveResponse glsReceiveResponse = MockInstruction.mockGlsResponse();
    deliveryDocumentLine.setSize("1.0EA");
    deliveryDocumentLine.getAdditionalInfo().setProfiledWarehouseArea(null);

    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabelV2(
            glsReceiveResponse, deliveryDocumentLine, httpHeaders);

    assertEquals(
        glsReceiveResponse.getPalletTagId(),
        containerLabel.getPrintRequests().get(0).getLabelIdentifier());
    assertEquals(GDC_LABEL_FORMAT_NAME, containerLabel.getPrintRequests().get(0).getFormatName());
    assertTrue(containerLabel.getPrintRequests().get(0).getData().get(1).getValue().length() == 7);
    assertEquals("TAG-123", containerLabel.getPrintRequests().get(0).getData().get(1).getValue());
    assertEquals("SLOT-1", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
    assertEquals("436617391", containerLabel.getPrintRequests().get(0).getData().get(6).getValue());
    assertEquals("6 - 7", containerLabel.getPrintRequests().get(0).getData().get(11).getValue());
    assertEquals("DD", containerLabel.getPrintRequests().get(0).getData().get(5).getValue());
    assertEquals("1.0EA", containerLabel.getPrintRequests().get(0).getData().get(9).getValue());
  }

  @Test
  public void testGenerateContainerLabel_nonMechSlot() {
    when(gdcFlagReader.isAutomatedDC()).thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()), IS_WITRON_LABEL_ENABLE, false))
        .thenReturn(true);
    deliveryDocumentLine.getAdditionalInfo().setProfiledWarehouseArea("MAN");

    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabel(lpn, slot, deliveryDocumentLine, httpHeaders);

    assertEquals(lpn, containerLabel.getPrintRequests().get(0).getLabelIdentifier());
    assertTrue(containerLabel.getPrintRequests().get(0).getData().get(2).getValue().length() > 7);
    assertEquals(
        ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE,
        containerLabel.getPrintRequests().get(0).getFormatName());
    assertEquals(
        "warehouseArea", containerLabel.getPrintRequests().get(0).getData().get(3).getKey());
    assertEquals("Dairy", containerLabel.getPrintRequests().get(0).getData().get(3).getValue());
    assertEquals(
        "profiledWarehouseArea",
        containerLabel.getPrintRequests().get(0).getData().get(4).getKey());
    assertEquals("NON-MECH", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
  }

  @Test
  public void testGenerateContainerLabel_nullSlot() {
    when(gdcFlagReader.isAutomatedDC()).thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()), IS_WITRON_LABEL_ENABLE, false))
        .thenReturn(false);
    deliveryDocumentLine.getAdditionalInfo().setProfiledWarehouseArea(null);

    GLSReceiveResponse glsReceiveResponse = MockInstruction.mockGlsResponse();
    glsReceiveResponse.setSlotId(null);

    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabelV2(
            glsReceiveResponse, deliveryDocumentLine, httpHeaders);

    assertEquals(
        glsReceiveResponse.getPalletTagId(),
        containerLabel.getPrintRequests().get(0).getLabelIdentifier());
    assertEquals(GDC_LABEL_FORMAT_NAME, containerLabel.getPrintRequests().get(0).getFormatName());
    assertTrue(containerLabel.getPrintRequests().get(0).getData().get(1).getValue().length() == 7);
    assertEquals("TAG-123", containerLabel.getPrintRequests().get(0).getData().get(1).getValue());
    assertEquals("436617391", containerLabel.getPrintRequests().get(0).getData().get(6).getValue());
    assertEquals("6 - 7", containerLabel.getPrintRequests().get(0).getData().get(11).getValue());
    assertEquals("DD", containerLabel.getPrintRequests().get(0).getData().get(5).getValue());
    assertEquals("", containerLabel.getPrintRequests().get(0).getData().get(9).getValue());
    assertEquals("", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
  }

  @Test
  public void testGenerateContainerLabel_mech18CharContainerManual() {
    when(gdcFlagReader.isAutomatedDC()).thenReturn(false);
    deliveryDocumentLine.getAdditionalInfo().setProfiledWarehouseArea("MAN");

    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabel(lpn, slot, deliveryDocumentLine, httpHeaders);

    assertEquals(lpn, containerLabel.getPrintRequests().get(0).getLabelIdentifier());
    assertTrue(containerLabel.getPrintRequests().get(0).getData().get(2).getValue().length() > 7);
    assertEquals(
        GDC_LABEL_FORMAT_NAME_V2, containerLabel.getPrintRequests().get(0).getFormatName());
    assertEquals(
        "warehouseArea", containerLabel.getPrintRequests().get(0).getData().get(3).getKey());
    assertEquals("Dairy", containerLabel.getPrintRequests().get(0).getData().get(3).getValue());
    assertEquals(
        "profiledWarehouseArea",
        containerLabel.getPrintRequests().get(0).getData().get(4).getKey());
    assertEquals("MG1234-12", containerLabel.getPrintRequests().get(0).getData().get(0).getValue());
  }

  @Test
  public void testGetPrintLabelFormat_LpnLength_LessOrEqualTo_7() {
    final String printLabelFormat = containerLabelBuilder.getPrintLabelFormat("123-456");
    assertEquals(GDC_LABEL_FORMAT_NAME, printLabelFormat);
  }

  @Test
  public void testGetPrintLabelFormat_LpnLength__7_digit() {
    when(configUtils.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()), IS_LPN_7_DIGIT_ENABLED, false))
        .thenReturn(true);
    final String printLabelFormat = containerLabelBuilder.getPrintLabelFormat("123-456");
    assertEquals(GDC_LABEL_FORMAT_7_DIGIT, printLabelFormat);
  }

  @Test
  public void testGetPrintLabelFormat_LpnLength_moreThan_7() {
    final String printLabelFormat = containerLabelBuilder.getPrintLabelFormat("123-56789101112");
    assertEquals(GDC_LABEL_FORMAT_NAME_V2, printLabelFormat);
  }

  @Test
  public void testGetPrintLabelFormat_LpnLength_moreThan_7_witron() {
    final String printLabelFormat = containerLabelBuilder.getPrintLabelFormat("123-56789101112");
    when(configUtils.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()), IS_WITRON_LABEL_ENABLE, false))
        .thenReturn(true);
    assertEquals(GDC_LABEL_FORMAT_NAME_V2, printLabelFormat);
  }
}
