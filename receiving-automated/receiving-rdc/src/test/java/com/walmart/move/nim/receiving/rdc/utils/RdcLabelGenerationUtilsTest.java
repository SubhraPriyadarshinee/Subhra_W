package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadContainerDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadDistributionsDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadItemDTO;
import com.walmart.move.nim.receiving.core.model.instruction.LabelDataAllocationDTO;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import com.walmart.move.nim.receiving.core.model.label.acl.ACLLabelDataTO;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymLabelType;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.model.symbotic.SymInventoryStatus;
import com.walmart.move.nim.receiving.rdc.model.symbotic.SymLabelData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcLabelGenerationUtilsTest {
  @InjectMocks private RdcLabelGenerationUtils rdcLabelGenerationUtils;

  private static final Gson gson = new Gson();

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  private DeliveryDetails deliveryDetails;
  private String deliveryDetailsJsonString;
  @Mock private AppConfig appConfig;
  @Mock private RdcManagedConfig rdcManagedConfig;

  private String facilityNum = "32818";
  private String facilityCountryCode = "us";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    TenantContext.setCorrelationId("2323-323dsds-323dwsd-3d23e");
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetailsSSTK.json")
              .getCanonicalPath();

      deliveryDetailsJsonString = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assert (false);
    }
  }

  @BeforeMethod
  public void reInitData() {
    deliveryDetails =
        JacksonParser.convertJsonToObject(deliveryDetailsJsonString, DeliveryDetails.class);
  }

  @AfterMethod
  public void afterMethod() {
    reset(tenantSpecificConfigReader, rdcInstructionUtils);
  }

  @Test
  public void testBuildConveyableLabelPayload_forDAFreightType() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    LabelData labelData = getMockLabelDataForDA();

    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(Arrays.asList("CI", "CJ", "CC"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    doReturn(false).when(rdcInstructionUtils).isHazmatItem(any(DeliveryDocumentLine.class));
    ACLLabelDataTO request =
        rdcLabelGenerationUtils.buildLabelDownloadForHawkeye(
            deliveryDocument, Collections.singletonList(labelData), RejectReason.BREAKOUT);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    verify(rdcInstructionUtils, times(1)).isHazmatItem(any(DeliveryDocumentLine.class));
    assertNotNull(request);
    assertNotNull(request.getGroupNumber());
    assertNotNull(request.getScanItems());
    assertEquals(request.getScanItems().size(), 1);
    assertEquals(request.getGroupNumber(), String.valueOf(deliveryDocument.getDeliveryNumber()));

    ScanItem scanItem = request.getScanItems().get(0);
    assertEquals(scanItem.getItem(), deliveryDocumentLine.getItemNbr());

    assertNotNull(scanItem.getLabels());
    assertEquals(scanItem.getLabels().size(), 1);

    FormattedLabels label = scanItem.getLabels().get(0);
    assertNotNull(label);
    assertEquals(label.getLpns().get(0), labelData.getTrackingId());
    assertEquals(label.getPurchaseReferenceNumber(), labelData.getPurchaseReferenceNumber());
    assertEquals(
        label.getPurchaseReferenceLineNumber(), labelData.getPurchaseReferenceLineNumber());
    assertEquals(label.getSeqNo(), "20231016000100001");
    assertNotNull(label.getLabelData());
    SymLabelData symLabelData = gson.fromJson(label.getLabelData(), SymLabelData.class);
    assertNull(symLabelData.getShipLabelData().getDataMatrix());
    assertEquals(symLabelData.getShipLabelData().getHazmatCode(), StringUtils.EMPTY);
    assertEquals(symLabelData.getLabelTagType(), SymLabelType.ROUTING.toString());
    assertEquals(symLabelData.getFreightType(), SymFreightType.DA.toString());
    assertEquals(symLabelData.getIsShipLabel(), Boolean.FALSE);
    assertEquals(symLabelData.getHoldStatus(), SymInventoryStatus.HOLD.getStatus());
    assertNotNull(label.getDestination());
  }

  @Test
  public void testBuildConveyableLabelPayload_forBreakpack() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("B");
    itemData.setHandlingCode("I");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPackType("B");
    LabelData labelData = getMockLabelDataForDA();
    doReturn(Arrays.asList("CI", "CJ", "CC"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    doReturn(false).when(rdcInstructionUtils).isHazmatItem(any(DeliveryDocumentLine.class));
    ACLLabelDataTO request =
        rdcLabelGenerationUtils.buildLabelDownloadForHawkeye(
            deliveryDocument, Collections.singletonList(labelData), RejectReason.BREAKOUT);
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    verify(rdcInstructionUtils, times(0)).isHazmatItem(any(DeliveryDocumentLine.class));

    assertNotNull(request);
    assertNotNull(request.getGroupNumber());
    assertNotNull(request.getScanItems());
    assertEquals(request.getScanItems().size(), 1);
    assertEquals(request.getGroupNumber(), String.valueOf(deliveryDocument.getDeliveryNumber()));

    ScanItem scanItem = request.getScanItems().get(0);
    assertEquals(scanItem.getItem(), deliveryDocumentLine.getItemNbr());
    assertEquals(scanItem.getLabels().size(), 0);
  }

  @Test
  public void testBuildConveyableLabelPayload_forDSDCFreightType() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    LabelData labelData = getMockLabelDataForSSTK();
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(Arrays.asList("CI", "CJ", "CC"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    doReturn(false).when(rdcInstructionUtils).isHazmatItem(any(DeliveryDocumentLine.class));
    ACLLabelDataTO request =
        rdcLabelGenerationUtils.buildLabelDownloadForHawkeye(
            deliveryDocument, Collections.singletonList(labelData), RejectReason.BREAKOUT);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    verify(rdcInstructionUtils, times(1)).isHazmatItem(any(DeliveryDocumentLine.class));

    assertNotNull(request);
    assertNotNull(request.getGroupNumber());
    assertNotNull(request.getScanItems());
    assertEquals(request.getScanItems().size(), 1);
    assertEquals(request.getGroupNumber(), String.valueOf(deliveryDocument.getDeliveryNumber()));

    ScanItem scanItem = request.getScanItems().get(0);
    assertEquals(scanItem.getItem(), deliveryDocumentLine.getItemNbr());

    assertNotNull(scanItem.getLabels());
    assertEquals(scanItem.getLabels().size(), 1);

    FormattedLabels label = scanItem.getLabels().get(0);
    assertNotNull(label);
    assertEquals(label.getLpns().get(0), labelData.getTrackingId());
    assertEquals(label.getPurchaseReferenceNumber(), labelData.getPurchaseReferenceNumber());
    assertEquals(
        label.getPurchaseReferenceLineNumber(), labelData.getPurchaseReferenceLineNumber());
    assertEquals(label.getSeqNo(), "20231016000100001");
    assertNotNull(label.getLabelData());
    SymLabelData symLabelData = gson.fromJson(label.getLabelData(), SymLabelData.class);
    assertNull(symLabelData.getShipLabelData().getDataMatrix());
    assertEquals(symLabelData.getShipLabelData().getHazmatCode(), StringUtils.EMPTY);
    assertEquals(symLabelData.getLabelTagType(), SymLabelType.SHIPPING.toString());
    assertEquals(symLabelData.getFreightType(), SymFreightType.DSDC.toString());
    assertEquals(symLabelData.getIsShipLabel(), Boolean.TRUE);
    assertEquals(symLabelData.getHoldStatus(), SymInventoryStatus.HOLD.getStatus());
    assertNull(label.getDestination());
  }

  @Test
  public void testBuildConveyableLabelPayload_forSSTKFreightType() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    LabelData labelData = getMockLabelDataForSSTK();
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(Arrays.asList("CI", "CJ", "CC"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    doReturn(false).when(rdcInstructionUtils).isHazmatItem(any(DeliveryDocumentLine.class));
    ACLLabelDataTO request =
        rdcLabelGenerationUtils.buildLabelDownloadForHawkeye(
            deliveryDocument, Collections.singletonList(labelData), RejectReason.BREAKOUT);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    verify(rdcInstructionUtils, times(1)).isHazmatItem(any(DeliveryDocumentLine.class));

    assertNotNull(request);
    assertNotNull(request.getGroupNumber());
    assertNotNull(request.getScanItems());
    assertEquals(request.getScanItems().size(), 1);
    assertEquals(request.getGroupNumber(), String.valueOf(deliveryDocument.getDeliveryNumber()));

    ScanItem scanItem = request.getScanItems().get(0);
    assertEquals(scanItem.getItem(), deliveryDocumentLine.getItemNbr());

    assertNotNull(scanItem.getLabels());
    assertEquals(scanItem.getLabels().size(), 1);

    FormattedLabels label = scanItem.getLabels().get(0);
    assertNotNull(label);
    assertEquals(label.getLpns().get(0), labelData.getTrackingId());
    assertEquals(label.getPurchaseReferenceNumber(), labelData.getPurchaseReferenceNumber());
    assertEquals(
        label.getPurchaseReferenceLineNumber(), labelData.getPurchaseReferenceLineNumber());
    assertEquals(label.getSeqNo(), "20231016000100001");
    assertNotNull(label.getLabelData());
    SymLabelData symLabelData = gson.fromJson(label.getLabelData(), SymLabelData.class);
    assertNull(symLabelData.getShipLabelData().getDataMatrix());
    assertEquals(symLabelData.getShipLabelData().getHazmatCode(), StringUtils.EMPTY);
    assertEquals(symLabelData.getLabelTagType(), SymLabelType.PALLET.toString());
    assertEquals(symLabelData.getFreightType(), SymFreightType.SSTK.toString());
    assertEquals(symLabelData.getIsShipLabel(), Boolean.FALSE);
    assertEquals(symLabelData.getHoldStatus(), SymInventoryStatus.HOLD.getStatus());
    assertNull(label.getDestination());
  }

  @Test
  public void testBuildConveyableLabelPayload_forListOfLabeldata() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    LabelData labelData1 = getMockLabelDataForSSTK();
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData1);
    LabelData labelData2 = getMockLabelDataForSSTK();
    labelData2.setTrackingId("c060200000100000025796158");
    labelData2.setLabelSequenceNbr(20231016000100001L);
    labelData2.setPurchaseReferenceLineNumber(2);
    labelDataList.add(labelData2);

    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(Arrays.asList("CI", "CJ", "CC"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();

    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    doReturn(false).when(rdcInstructionUtils).isHazmatItem(any(DeliveryDocumentLine.class));
    ACLLabelDataTO request =
        rdcLabelGenerationUtils.buildLabelDownloadForHawkeye(
            deliveryDocument, labelDataList, RejectReason.BREAKOUT);

    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    verify(rdcInstructionUtils, times(2)).isHazmatItem(any(DeliveryDocumentLine.class));

    assertNotNull(request);
    assertNotNull(request.getGroupNumber());
    assertNotNull(request.getScanItems());
    assertEquals(request.getScanItems().size(), 1);
    assertEquals(request.getGroupNumber(), String.valueOf(deliveryDocument.getDeliveryNumber()));

    ScanItem scanItem = request.getScanItems().get(0);
    assertEquals(scanItem.getItem(), deliveryDocumentLine.getItemNbr());

    assertNotNull(scanItem.getLabels());
    assertEquals(scanItem.getLabels().size(), 2);

    FormattedLabels label1 = scanItem.getLabels().get(0);
    FormattedLabels label2 = scanItem.getLabels().get(1);
    assertNotNull(label1);
    assertEquals(label1.getLpns().get(0), labelData1.getTrackingId());
    assertEquals(label1.getPurchaseReferenceNumber(), labelData1.getPurchaseReferenceNumber());
    assertEquals(
        label1.getPurchaseReferenceLineNumber(), labelData1.getPurchaseReferenceLineNumber());
    assertEquals(label1.getSeqNo(), labelData1.getLabelSequenceNbr().toString());
    assertNotNull(label1.getLabelData());
    assertEquals(
        label2.getPurchaseReferenceLineNumber(), labelData2.getPurchaseReferenceLineNumber());
    assertEquals(label2.getSeqNo(), labelData2.getLabelSequenceNbr().toString());
    SymLabelData symLabelData = gson.fromJson(label1.getLabelData(), SymLabelData.class);
    assertNull(symLabelData.getShipLabelData().getDataMatrix());
    assertEquals(symLabelData.getShipLabelData().getHazmatCode(), StringUtils.EMPTY);
    assertEquals(symLabelData.getLabelTagType(), SymLabelType.PALLET.toString());
    assertEquals(symLabelData.getFreightType(), SymFreightType.SSTK.toString());
    assertEquals(symLabelData.getIsShipLabel(), Boolean.FALSE);
    assertEquals(symLabelData.getHoldStatus(), SymInventoryStatus.HOLD.getStatus());
    assertNull(label1.getDestination());
  }

  @Test
  public void testBuildConveyableLabelPayload_disabledDAHoldFlagAndDataMatrixFlag()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    LabelData labelData = getMockLabelDataForSSTK();

    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(Arrays.asList("CI", "CJ", "CC"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    doReturn(false).when(rdcInstructionUtils).isHazmatItem(any(DeliveryDocumentLine.class));
    ACLLabelDataTO request =
        rdcLabelGenerationUtils.buildLabelDownloadForHawkeye(
            deliveryDocument, Collections.singletonList(labelData), RejectReason.BREAKOUT);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    verify(rdcInstructionUtils, times(1)).isHazmatItem(any(DeliveryDocumentLine.class));

    assertNotNull(request);
    assertNotNull(request.getGroupNumber());
    assertNotNull(request.getScanItems());
    assertEquals(request.getScanItems().size(), 1);
    assertEquals(request.getGroupNumber(), String.valueOf(deliveryDocument.getDeliveryNumber()));

    ScanItem scanItem = request.getScanItems().get(0);
    FormattedLabels label = scanItem.getLabels().get(0);
    SymLabelData symLabelData = gson.fromJson(label.getLabelData(), SymLabelData.class);
    assertEquals(
        symLabelData.getShipLabelData().getDataMatrix(),
        StringUtils.join(deliveryDocument.getDeliveryNumber(), "Y", labelData.getItemNumber()));
    assertEquals(symLabelData.getShipLabelData().getHazmatCode(), StringUtils.EMPTY);
    assertEquals(symLabelData.getLabelTagType(), SymLabelType.PALLET.toString());
    assertEquals(symLabelData.getFreightType(), SymFreightType.SSTK.toString());
    assertEquals(symLabelData.getIsShipLabel(), Boolean.FALSE);
    assertNull(label.getDestination());
  }

  @Test
  public void testBuildConveyableLabelPayload_isHazmatItem() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    LabelData labelData = getMockLabelDataForSSTK();

    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(Arrays.asList("CI", "CJ", "CC"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();

    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    doReturn(true).when(rdcInstructionUtils).isHazmatItem(any(DeliveryDocumentLine.class));
    ACLLabelDataTO request =
        rdcLabelGenerationUtils.buildLabelDownloadForHawkeye(
            deliveryDocument, Collections.singletonList(labelData), RejectReason.BREAKOUT);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    verify(rdcInstructionUtils, times(1)).isHazmatItem(any(DeliveryDocumentLine.class));

    assertNotNull(request);
    assertNotNull(request.getGroupNumber());
    assertNotNull(request.getScanItems());
    assertEquals(request.getScanItems().size(), 1);
    assertEquals(request.getGroupNumber(), String.valueOf(deliveryDocument.getDeliveryNumber()));

    ScanItem scanItem = request.getScanItems().get(0);
    FormattedLabels label = scanItem.getLabels().get(0);
    SymLabelData symLabelData = gson.fromJson(label.getLabelData(), SymLabelData.class);
    assertNull(symLabelData.getShipLabelData().getDataMatrix());
    assertEquals(symLabelData.getShipLabelData().getHazmatCode(), "H");
    assertEquals(symLabelData.getLabelTagType(), SymLabelType.PALLET.toString());
    assertEquals(symLabelData.getFreightType(), SymFreightType.SSTK.toString());
    assertEquals(symLabelData.getIsShipLabel(), Boolean.FALSE);
    assertEquals(symLabelData.getHoldStatus(), SymInventoryStatus.HOLD.getStatus());
    assertNull(label.getDestination());
  }

  @Test
  public void testBuildConveyableLabelPayload_isNonConItem() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    LabelData labelData = getMockLabelDataForSSTK();

    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    doReturn(true).when(rdcInstructionUtils).isHazmatItem(any(DeliveryDocumentLine.class));
    ACLLabelDataTO request =
        rdcLabelGenerationUtils.buildLabelDownloadForHawkeye(
            deliveryDocument, Collections.singletonList(labelData), RejectReason.BREAKOUT);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    verify(rdcInstructionUtils, times(1)).isHazmatItem(any(DeliveryDocumentLine.class));

    assertNotNull(request);
    assertNotNull(request.getGroupNumber());
    assertNotNull(request.getScanItems());
    assertEquals(request.getScanItems().size(), 1);
    assertEquals(request.getGroupNumber(), String.valueOf(deliveryDocument.getDeliveryNumber()));

    ScanItem scanItem = request.getScanItems().get(0);
    FormattedLabels label = scanItem.getLabels().get(0);
    SymLabelData symLabelData = gson.fromJson(label.getLabelData(), SymLabelData.class);
    assertNull(symLabelData.getShipLabelData().getDataMatrix());
    assertEquals(symLabelData.getShipLabelData().getHazmatCode(), "H");
    assertEquals(symLabelData.getLabelTagType(), SymLabelType.PALLET.toString());
    assertEquals(symLabelData.getFreightType(), SymFreightType.SSTK.toString());
    assertEquals(symLabelData.getIsShipLabel(), Boolean.FALSE);
    assertEquals(symLabelData.getHoldStatus(), SymInventoryStatus.HOLD.getStatus());
    assertNull(label.getDestination());
  }

  @Test
  public void testBuildConveyableLabelPayload_isSmartLabelFilteredValidation() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPackType("C");
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    LabelData labelData1 = getMockLabelDataForDA();
    labelData1.setTrackingId("c060200000100000025796158");
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData1);
    LabelData labelData2 = getMockLabelDataForDA();
    labelData2.setTrackingId("010840132679204332");
    LabelData labelData3 = getMockLabelDataForDA();
    labelData3.setTrackingId("010840132679204331");
    labelData2.setLabelSequenceNbr(20231016000100001L);
    labelData2.setPurchaseReferenceLineNumber(2);
    labelDataList.add(labelData2);
    labelDataList.add(labelData3);

    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(Arrays.asList("CI", "CJ", "CC"))
        .when(appConfig)
        .getValidItemPackTypeHandlingCodeCombinations();
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    doReturn(false).when(rdcInstructionUtils).isHazmatItem(any(DeliveryDocumentLine.class));
    ACLLabelDataTO request =
        rdcLabelGenerationUtils.buildLabelDownloadForHawkeye(
            deliveryDocument, labelDataList, RejectReason.BREAKOUT);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED), eq(false));
    verify(rdcInstructionUtils, times(1)).isHazmatItem(any(DeliveryDocumentLine.class));

    assertNotNull(request);
    assertNotNull(request.getGroupNumber());
    assertNotNull(request.getScanItems());
    assertEquals(request.getScanItems().size(), 1);
    assertEquals(request.getGroupNumber(), String.valueOf(deliveryDocument.getDeliveryNumber()));

    ScanItem scanItem = request.getScanItems().get(0);
    assertEquals(scanItem.getItem(), deliveryDocumentLine.getItemNbr());

    assertNotNull(scanItem.getLabels());
    assertEquals(scanItem.getLabels().size(), 1);

    FormattedLabels label1 = scanItem.getLabels().get(0);
    assertNotNull(label1);
    assertEquals(label1.getLpns().get(0), labelData1.getTrackingId());
    assertEquals(label1.getPurchaseReferenceNumber(), labelData1.getPurchaseReferenceNumber());
    assertEquals(
        label1.getPurchaseReferenceLineNumber(), labelData1.getPurchaseReferenceLineNumber());
    assertEquals(label1.getSeqNo(), labelData1.getLabelSequenceNbr().toString());
    assertNotNull(label1.getLabelData());
    SymLabelData symLabelData = gson.fromJson(label1.getLabelData(), SymLabelData.class);
    assertNull(symLabelData.getShipLabelData().getDataMatrix());
    assertEquals(symLabelData.getShipLabelData().getHazmatCode(), StringUtils.EMPTY);
    assertEquals(symLabelData.getLabelTagType(), SymLabelType.PALLET.toString());
    assertEquals(symLabelData.getFreightType(), SymFreightType.SSTK.toString());
    assertEquals(symLabelData.getIsShipLabel(), Boolean.FALSE);
    assertEquals(symLabelData.getHoldStatus(), SymInventoryStatus.HOLD.getStatus());
    assertNotNull(label1.getDestination());
  }

  @Test
  public void testGetPossibleUPC() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setOrderableGTIN(deliveryDocumentLine.getCaseUpc());
    deliveryDocumentLine.setConsumableGTIN(deliveryDocumentLine.getItemUpc());
    deliveryDocumentLine.setCatalogGTIN(deliveryDocumentLine.getVendorUPC());
    deliveryDocumentLine.setCaseUpc(null);
    deliveryDocumentLine.setVendorUPC(null);
    deliveryDocumentLine.setItemUpc(null);
    PossibleUPC possibleUPC = rdcLabelGenerationUtils.getPossibleUPC(deliveryDocumentLine);
    assertNotNull(possibleUPC.getCatalogGTIN());
    assertNotNull(possibleUPC.getOrderableGTIN());
    assertNotNull(possibleUPC.getConsumableGTIN());
    assertNull(possibleUPC.getParsedUPC());
  }

  @Test
  public void testGetPossibleUPCv2() {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments =
        deliveryDetails.getDeliveryDocuments();
    com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    PossibleUPC possibleUPC = rdcLabelGenerationUtils.getPossibleUPCv2(deliveryDocumentLine);
    assertEquals(deliveryDocumentLine.getVendorUPC(), possibleUPC.getCatalogGTIN());
    assertEquals(deliveryDocumentLine.getCaseUPC(), possibleUPC.getOrderableGTIN());
    assertEquals(deliveryDocumentLine.getItemUPC(), possibleUPC.getConsumableGTIN());
    String parsedUPC =
        deliveryDocumentLine
            .getCaseUPC()
            .substring(CASE_UPC_STARTING_INDEX, CASE_UPC_ENDING_INDEX)
            .replaceFirst(PARSED_UPC_REGEX_PATTERN, "");
    assertEquals(parsedUPC, possibleUPC.getParsedUPC());
  }

  @Test
  public void isSSTKPilotDeliveryEnabledTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_SSTK_PILOT_DELIVERY_ENABLED,
            false))
        .thenReturn(true);
    boolean isAtlasSSTKPilotDelivery = rdcLabelGenerationUtils.isSSTKPilotDeliveryEnabled();
    assertTrue(isAtlasSSTKPilotDelivery);
  }

  @Test
  public void isSSTKPilotDeliveryEnabledWithPilotNotEnabledTest() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_SSTK_PILOT_DELIVERY_ENABLED,
            false))
        .thenReturn(false);
    boolean isAtlasSSTKPilotDelivery = rdcLabelGenerationUtils.isSSTKPilotDeliveryEnabled();
    assertFalse(isAtlasSSTKPilotDelivery);
  }

  @Test
  public void isAtlasSSTKPilotDeliveryTest() {
    when(rdcManagedConfig.getAtlasSSTKPilotDeliveries()).thenReturn(Arrays.asList("5647698"));
    boolean isAtlasSSTKPilotDelivery = rdcLabelGenerationUtils.isAtlasSSTKPilotDelivery(5647698l);
    assertTrue(isAtlasSSTKPilotDelivery);
  }

  @Test
  public void isAtlasSSTKPilotDeliveryWithNoPilotDeliveryTest() {
    when(rdcManagedConfig.getAtlasSSTKPilotDeliveries()).thenReturn(Arrays.asList("09809809"));
    boolean isAtlasSSTKPilotDelivery = rdcLabelGenerationUtils.isAtlasSSTKPilotDelivery(5647698l);
    assertFalse(isAtlasSSTKPilotDelivery);
  }

  @Test
  public void filterLabelDownloadEventWithPilotDeliveryTest() {
    List<LabelDownloadEvent> labelDownloadEventList = new ArrayList<>();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_SSTK_PILOT_DELIVERY_ENABLED,
            false))
        .thenReturn(true);
    when(rdcManagedConfig.getAtlasSSTKPilotDeliveries()).thenReturn(Arrays.asList("5647698"));
    LabelDownloadEvent labelDownloadEvent = new LabelDownloadEvent();
    labelDownloadEvent.setDeliveryNumber(5647698l);
    labelDownloadEventList.add(labelDownloadEvent);
    List<LabelDownloadEvent> labelDownloadEvents =
        rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(labelDownloadEventList);
    assertTrue(labelDownloadEvents.size() > 0);
  }

  @Test
  public void filterLabelDownloadEventWithPilotDeliveryNoEventsTest() {
    List<LabelDownloadEvent> labelDownloadEventList = new ArrayList<>();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_SSTK_PILOT_DELIVERY_ENABLED,
            false))
        .thenReturn(true);
    when(rdcManagedConfig.getAtlasSSTKPilotDeliveries()).thenReturn(Arrays.asList("8787"));
    LabelDownloadEvent labelDownloadEvent = new LabelDownloadEvent();
    labelDownloadEvent.setDeliveryNumber(5647698l);
    labelDownloadEventList.add(labelDownloadEvent);
    List<LabelDownloadEvent> labelDownloadEvents =
        rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(labelDownloadEventList);
    assertFalse(labelDownloadEvents.size() > 0);
  }

  @Test
  public void filterLabelDownloadEventWithPilotDeliveryWithPilotNotEnabledTest() {
    List<LabelDownloadEvent> labelDownloadEventList = new ArrayList<>();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_SSTK_PILOT_DELIVERY_ENABLED,
            false))
        .thenReturn(false);
    when(rdcManagedConfig.getAtlasSSTKPilotDeliveries()).thenReturn(Arrays.asList("8787"));
    LabelDownloadEvent labelDownloadEvent = new LabelDownloadEvent();
    labelDownloadEvent.setDeliveryNumber(5647698l);
    labelDownloadEventList.add(labelDownloadEvent);
    List<LabelDownloadEvent> labelDownloadEvents =
        rdcLabelGenerationUtils.filterLabelDownloadEventWithPilotDelivery(labelDownloadEventList);
    assertTrue(labelDownloadEvents.size() > 0);
  }

  private LabelData getMockLabelDataForDA() {
    LabelData labelData = new LabelData();
    labelData.setPurchaseReferenceNumber("5232232323");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setDeliveryNumber(232323323L);
    labelData.setItemNumber(658790758L);
    labelData.setTrackingId("c060200000100000025796158");
    labelData.setLabelSequenceNbr(20231016000100001L);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .parsedUPC("232345454")
            .catalogGTIN("2378428458")
            .orderableGTIN("0002323454541")
            .consumableGTIN("2378428458")
            .build();
    labelData.setPossibleUPC(gson.toJson(possibleUPC));
    LabelDataAllocationDTO allocationDTO = new LabelDataAllocationDTO();
    InstructionDownloadContainerDTO allocationContainer = new InstructionDownloadContainerDTO();
    allocationContainer.setTrackingId("c060200000100000025796158");
    allocationContainer.setCtrType("CASE");
    allocationContainer.setOutboundChannelMethod("DA");
    InstructionDownloadDistributionsDTO distributions = new InstructionDownloadDistributionsDTO();
    InstructionDownloadItemDTO item = new InstructionDownloadItemDTO();
    item.setItemNbr(658790758L);
    item.setAisle("12");
    item.setItemUpc("78236478623");
    item.setPickBatch("281");
    item.setPrintBatch("281");
    item.setZone("03");
    item.setVnpk(1);
    item.setWhpk(1);
    distributions.setItem(item);
    allocationContainer.setDistributions(Collections.singletonList(distributions));
    Facility finalDestination = new Facility();
    finalDestination.setBuNumber("87623");
    finalDestination.setCountryCode("US");
    allocationContainer.setFinalDestination(finalDestination);
    allocationDTO.setContainer(allocationContainer);
    labelData.setAllocation(allocationDTO);
    labelData.setStatus("AVAILABLE");
    return labelData;
  }

  private LabelData getMockLabelDataForSSTK() {
    LabelData labelData = new LabelData();
    labelData.setPurchaseReferenceNumber("5232232323");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setDeliveryNumber(232323323L);
    labelData.setItemNumber(658790758L);
    labelData.setTrackingId("c060200000100000025796158");
    labelData.setStatus("AVAILABLE");
    labelData.setLabelSequenceNbr(20231016000100001L);
    return labelData;
  }
}
