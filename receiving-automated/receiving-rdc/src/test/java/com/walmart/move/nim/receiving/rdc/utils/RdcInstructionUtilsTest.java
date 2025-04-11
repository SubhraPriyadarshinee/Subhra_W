package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionRequest.getSSCCInstructionRequest;
import static com.walmart.move.nim.receiving.rdc.mock.data.MockReceivedQtyRespFromRds.getReceivedQtyMapForOnePoWithOneLine;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ATLAS_COMPLETE_MIGRATED_DC_LIST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigRestApiClientException;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedQuantityResponseFromRDS;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.DCFinServiceV2;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionSetIdGenerator;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.SlottingServiceImpl;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionRequest;
import com.walmart.move.nim.receiving.rdc.mock.data.MockLocationHeaders;
import com.walmart.move.nim.receiving.rdc.mock.data.MockProblemResponse;
import com.walmart.move.nim.receiving.rdc.mock.data.MockReceivedQtyRespFromRds;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.model.RdcReceivingType;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.service.NimRdsService;
import com.walmart.move.nim.receiving.rdc.service.RdcDeliveryService;
import com.walmart.move.nim.receiving.rdc.service.RdcFixitProblemService;
import com.walmart.move.nim.receiving.rdc.service.RdcQuantityCalculator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.PoType;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcInstructionUtilsTest {
  @Mock private NimRdsService nimRdsService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private InstructionSetIdGenerator instructionSetIdGenerator;
  @Mock private SlottingServiceImpl slottingServiceImpl;
  @Mock private ReceiptService receiptService;
  @Mock private ItemConfigApiClient itemConfigApiClient;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcFixitProblemService rdcFixitProblemService;
  @Mock private ProblemService problemService;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;
  @Mock private DCFinServiceV2 dcFinServiceV2;
  @Mock private RdcProblemUtils rdcProblemUtils;
  @Mock private RdcDeliveryService rdcDeliveryService;
  @Mock private AppConfig appConfig;
  @Spy private RdcQuantityCalculator rdcQuantityCalculator = new RdcQuantityCalculator();
  @Mock private RdcReceivingUtils rdcReceivingUtils;

  private static final String problemTagId = "PTAG1";
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private static final String upcNumber = "upc123";
  private static final String itemNumber = "5689452";
  private static final String itemDesc = "test desc";
  private static final String createTs = "2021-08-11T03:48:27.133Z";
  private HttpHeaders headers;
  private Gson gson;
  private ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS;

  @InjectMocks private RdcInstructionUtils rdcInstructionUtils;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    gson = new Gson();
    ReflectionTestUtils.setField(rdcInstructionUtils, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        nimRdsService,
        instructionPersisterService,
        instructionRepository,
        rdcManagedConfig,
        instructionSetIdGenerator,
        slottingServiceImpl,
        receiptService,
        itemConfigApiClient,
        tenantSpecificConfigReader,
        rdcFixitProblemService,
        problemService,
        dcFinServiceV2,
        deliveryMetaDataService,
        rdcProblemUtils,
        rdcContainerUtils,
        rdcDeliveryService,
        appConfig);
  }

  @Test
  public void test_isDADocument_Returns_True_For_DA_Document() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    Boolean isDADocument = rdcInstructionUtils.isDADocument(deliveryDocuments.get(0));
    assertTrue(isDADocument);
  }

  @Test
  public void test_isDADocument_Returns_False_For_SSTK_Document() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    Boolean isDADocument = rdcInstructionUtils.isDADocument(deliveryDocuments.get(0));
    assertFalse(isDADocument);
  }

  @Test
  public void test_validateAndProcessGdmDeliveryDocuments_ReturnsDeliveryDocument_Has_SSTK_Item()
      throws IOException, ReceivingException {
    rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine(),
        getMockInstructionRequest());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_validateAndProcessGdmDeliveryDocuments_Single_DA_Throws_Exception()
      throws IOException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_QTY_RECEIVING_ENABLED,
            false))
        .thenReturn(false);
    rdcInstructionUtils.filterNonDADeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA(), getMockInstructionRequest());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_validateAndProcessGdmDeliveryDocuments_Multiple_DA_Throws_Exception()
      throws IOException {
    rdcInstructionUtils.filterNonDADeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA(), getMockInstructionRequest());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      test_filterHistoryPoDocuments_Throws_Exception_When_No_ActivePo_IsAvailable_ToReceive()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocumentList.forEach(
        document -> document.setPurchaseReferenceStatus(POStatus.HISTORY.name()));
    rdcInstructionUtils.filterNonHistoryDeliveryDocuments(
        deliveryDocumentList, getMockInstructionRequest());
  }

  @Test
  public void test_filterHistoryPoDocuments_Returns_Valid_DeliveryDocuments() throws IOException {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    DeliveryDocument deliveryDocument1 = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    DeliveryDocument deliveryDocument2 = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    deliveryDocument2.setPurchaseReferenceStatus(null);
    deliveryDocumentList.add(deliveryDocument1);
    deliveryDocumentList.add(deliveryDocument2);
    List<DeliveryDocument> filteredDeliveryDocumentList =
        rdcInstructionUtils.filterNonHistoryDeliveryDocuments(
            deliveryDocumentList, getMockInstructionRequest());
    assertTrue(filteredDeliveryDocumentList.size() > 0);
  }

  @Test
  public void test_filterDADeliveryDocuments_Returns_Valid_DA_DeliveryDocuments()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<DeliveryDocument> daDeliveryDocuments =
        rdcInstructionUtils.filterDADeliveryDocuments(
            deliveryDocumentList, getMockInstructionRequest());
    assertTrue(daDeliveryDocuments.size() > 0);
    assertEquals(daDeliveryDocuments.size(), deliveryDocumentList.size());
  }

  @Test
  public void test_filterDADeliveryDocuments_Returns_Valid_Multiple_DA_DeliveryDocuments()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<DeliveryDocument> daDeliveryDocuments =
        rdcInstructionUtils.filterDADeliveryDocuments(
            deliveryDocumentList, getMockInstructionRequest());
    assertTrue(daDeliveryDocuments.size() > 0);
    assertEquals(daDeliveryDocuments.size(), deliveryDocumentList.size());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_filterDADeliveryDocuments_ThrowsExceptionWhenNoDADocumentFound_SingleSSTK()
      throws IOException {
    List<DeliveryDocument> sstkDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    rdcInstructionUtils.filterDADeliveryDocuments(
        sstkDeliveryDocuments, getMockInstructionRequest());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_filterDADeliveryDocuments_ThrowsExceptionWhenNoDADocumentFound_MultipleSSTK()
      throws IOException {
    List<DeliveryDocument> sstkDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    rdcInstructionUtils.filterDADeliveryDocuments(
        sstkDeliveryDocuments, getMockInstructionRequest());
  }

  @Test
  public void test_filterDADeliveryDocuments_ReturnsDeliveryDocuments_WhenMixedPO()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO();
    List<DeliveryDocument> mixedDeliveryDocuments =
        rdcInstructionUtils.filterDADeliveryDocuments(
            deliveryDocumentList, getMockInstructionRequest());
    assertTrue(mixedDeliveryDocuments.size() > 0);
    assertEquals(mixedDeliveryDocuments.size(), deliveryDocumentList.size());
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionExists_allowableProjectedReceiveQty_Success()
          throws IOException, ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(getInstruction());
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(25);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            MockInstructionRequest.getInstructionRequestWithDeliveryDocuments(), 10L, headers);

    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertNotNull(
        instructionResp.getInstruction().getInstructionMsg(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResp.getDeliveryDocuments().get(0));
    assertNotNull(instructionResp.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo());
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getImages());
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getImages()
            .size(),
        5);
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getImages()
            .get(ReceivingConstants.GDM_ITEM_IMAGE_URL_SIZE_450),
        "https://i5.walmartimages.com/asr/65ef69c2-a13f-4601-bf1e-4ac9a9c99e30_9.91e70d2feaa250e7acfc5a9139237360.jpeg?odnHeight=450&odnWidth=450&odnBg=ffffff");

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void test_createInstructionForUpcReceiving_ScannedSscc_NewInstruction_Success()
      throws IOException, ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(25);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocumentsWithShipmentDetails();
    instructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    instructionRequest.setSscc("001234567890908");
    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            instructionRequest, 10L, headers);

    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getInstruction().getSsccNumber());
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertNotNull(
        instructionResp.getInstruction().getInstructionMsg(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResp.getDeliveryDocuments().get(0));
    assertNotNull(instructionResp.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo());
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getImages());
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getImages()
            .size(),
        5);
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getImages()
            .get("IMAGE_SIZE_450"),
        "https://i5.walmartimages.com/asr/65ef69c2-a13f-4601-bf1e-4ac9a9c99e30_9.91e70d2feaa250e7acfc5a9139237360.jpeg?odnHeight=450&odnWidth=450&odnBg=ffffff");

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionExists_OverageAlert_GDMReturnsImageDetails_Success()
          throws IOException, ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(getInstruction());
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(25);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(3900L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            MockInstructionRequest.getInstructionRequestWithDeliveryDocuments(), 3900L, headers);

    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertNotNull(
        instructionResp.getInstruction().getInstructionMsg(),
        RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionMsg());
    assertNotNull(
        instructionResp.getInstruction().getInstructionCode(),
        RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionCode());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResp.getDeliveryDocuments().get(0));
    assertNotNull(instructionResp.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo());
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getImages());
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getImages()
            .size(),
        5);
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getImages()
            .get("IMAGE_SIZE_450"),
        "https://i5.walmartimages.com/asr/65ef69c2-a13f-4601-bf1e-4ac9a9c99e30_9.91e70d2feaa250e7acfc5a9139237360.jpeg?odnHeight=450&odnWidth=450&odnBg=ffffff");

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionExists_ReachedMaxReceiveInstructionWhenNoneReceivedInRDS_Success()
          throws IOException, ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(getInstruction());
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(388);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(0L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            MockInstructionRequest.getInstructionRequestWithDeliveryDocuments(), 10L, headers);

    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertEquals(instructionResp.getInstruction().getProjectedReceiveQty(), 50);
    assertFalse(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void test_createInstructionForUpcReceiving_whenInstructionExists_OverageWarning_Success()
      throws IOException, ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(getInstruction());
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(15);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(350L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            MockInstructionRequest.getInstructionRequestWithDeliveryDocuments(), 350L, headers);

    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertTrue(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionExists_InstructionCountWithinPalletCapacity_Success()
          throws IOException, ReceivingException {
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(340L));
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(getInstruction());
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(15);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            MockInstructionRequest.getInstructionRequestWithDeliveryDocuments(), 10L, headers);

    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertFalse(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());
    assertEquals(instructionResp.getInstruction().getProjectedReceiveQty(), 38);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void test_createInstructionForUpcReceiving_whenInstructionNotExists_Success()
      throws IOException, ReceivingException {
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            instructionRequest, 10L, headers);
    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertNotNull(
        instructionResp.getInstruction().getInstructionMsg(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    assertEquals(
        10,
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalReceivedQty()
            .intValue());
    assertFalse(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertFalse(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceivingThrowsException_whenInstructionNotExists_AndItemHasInvalidItemHandlingMethod()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments();
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    ItemData itemData =
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo();
    itemData.setHandlingCode("C");
    itemData.setPackTypeCode(null);
    instructionRequest
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setAdditionalInfo(itemData);

    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    try {
      InstructionResponse instructionResp =
          rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
              instructionRequest, 10L, headers);
    } catch (ReceivingBadDataException exception) {
      assertNotNull(exception);
      assertSame(exception.getErrorCode(), ExceptionCodes.INVALID_ITEM_HANDLING_METHOD);
    }

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionExists_allowableProjectedReceiveQty_Success_MultiPOLines()
          throws IOException, ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(), any(), anyString()))
        .thenReturn(getInstruction());
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(25);

    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapForOnePoWithTwoLines());
    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class)))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_MultiPOLines(),
            10L,
            headers);

    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertNotNull(
        instructionResp.getInstruction().getInstructionMsg(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertFalse(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(), any(), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionExists_allowableProjectedReceiveQty_Success_MultiPOs()
          throws IOException, ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(), any(), anyString()))
        .thenReturn(getInstruction());
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(25);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapForMoreThanOnePo());
    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class)))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_MultiPOs(),
            10L,
            headers);

    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertNotNull(
        instructionResp.getInstruction().getInstructionMsg(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertFalse(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(), any(), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionNotExists_SmartSlottingEnabledToUpdatePrimeSlot_IqsDisabled_Success()
          throws IOException, ReceivingException {
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapForMoreThanOnePo();
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_AtlasItems();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    doReturn(getPrimeSlotFromSmartSlotting())
        .when(slottingServiceImpl)
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            instructionRequest, 10L, headers);
    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertTrue(
        (instructionResp.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size() > 0));
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo());
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlotSize(),
        72);
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlot(),
        "A0001");

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(slottingServiceImpl, times(1))
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionNotExists_SSEnabledAndIQSIntegrationEnabledToUpdateItemAttributes()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_AtlasItemAndIQSEnabled();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    doReturn(getPrimeSlotFromSmartSlotting())
        .when(slottingServiceImpl)
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            instructionRequest, 10L, headers);
    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertTrue(
        (instructionResp.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size() > 0));
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo());
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlotSize(),
        72);
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlot(),
        "A0001");
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getHandlingCode(),
        "C");
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPackTypeCode(),
        "C");
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPalletHi());
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPalletTi());
    assertTrue(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsHazmat());

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(slottingServiceImpl, times(1))
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionNotExists_ReturnsNewItemException_WhenSSEnabledAndIQSIntegrationEnabled()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_AtlasItemAndIQSEnabled();
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setNewItem(true);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);

    try {
      rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
          instructionRequest, 10L, headers);
    } catch (ReceivingInternalException excep) {
      assertNotNull(excep);
      assertSame(excep.getErrorCode(), ExceptionCodes.NEW_ITEM);
      assertTrue(
          excep
              .getDescription()
              .equals(
                  String.format(
                      ReceivingException.NEW_ITEM_ERROR_MSG,
                      deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemNbr())));
    }

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionNotExists_SSEnabledAndIQSIntegrationDisabledToUpdateItemAttributes()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_AtlasItems();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapForOnePoWithOneLine());
    doReturn(getPrimeSlotFromSmartSlotting())
        .when(slottingServiceImpl)
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            instructionRequest, 10L, headers);
    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertTrue(
        (instructionResp.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size() > 0));
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo());
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlotSize(),
        72);
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlot(),
        "A0001");

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(slottingServiceImpl, times(1))
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Resource exception from SMART-SLOTTING. Error Code = GLS-RCV-SMART-SLOT-PRIME-404, Error Message = Invalid Slot ID")
  public void
      test_createInstructionForUpcReceiving_whenInstructionNotExists_SmartSlottingEnabledToUpdatePrimeSlot_Failure()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_AtlasItems();

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                    "Invalid Slot ID"),
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                "Invalid Slot ID"))
        .when(slottingServiceImpl)
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
        instructionRequest, 10L, headers);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(slottingServiceImpl, times(1))
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void test_updateAdditionalItemDetailsFromGDM() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_IQSIntegrationEnabled();
    rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(deliveryDocumentList);

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull((deliveryDocumentLine.getAdditionalInfo().getPackTypeCode()));
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertTrue(deliveryDocumentLine.getIsHazmat());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletHi());
    assertFalse(deliveryDocumentLine.getAdditionalInfo().getIsDefaultTiHiUsed());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode(), "CC");
  }

  @Test
  public void test_updateAdditionalItemDetailsFromGDM_InvalidTiHiDefaultTo100x100()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_IQSIntegrationEnabled();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPalletHigh(0);
    deliveryDocumentLine.setPalletTie(0);

    rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(deliveryDocumentList);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPalletTi(), Integer.valueOf(100));
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPalletHi(), Integer.valueOf(100));
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull((deliveryDocumentLine.getAdditionalInfo().getPackTypeCode()));
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertTrue(deliveryDocumentLine.getIsHazmat());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi(), Integer.valueOf(0));
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi(), Integer.valueOf(0));
    assertTrue(deliveryDocumentLine.getAdditionalInfo().getIsDefaultTiHiUsed());
  }

  @Test
  public void
      test_updateAdditionalItemDetailsFromGDM_InvalidTiHiDefaultTo100x100_DA_Items_DoNotDisplayPopUpMessage()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPalletHigh(0);
    deliveryDocumentLine.setPalletTie(0);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DEFAULT_ITEM_HANDLING_CODE_ENABLED,
            false))
        .thenReturn(true);

    rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(deliveryDocumentList);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPalletTi(), Integer.valueOf(100));
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPalletHi(), Integer.valueOf(100));
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "C");
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertFalse(deliveryDocumentLine.getIsHazmat());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi(), Integer.valueOf(0));
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi(), Integer.valueOf(0));
    assertFalse(deliveryDocumentLine.getAdditionalInfo().getIsDefaultTiHiUsed());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_updateAdditionalItemDetailsFromGDM_InvalidItemHandlingMethod()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);

    rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(deliveryDocumentList);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertFalse(deliveryDocumentLine.getIsHazmat());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletHi());
  }

  @Test
  public void
      test_updateAdditionalItemDetailsFromGDM_InvalidItemHandlingMethod_EitherPackTypeHandlingMethodIsNull()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setHandlingCode("C");

    rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(deliveryDocumentList);

    assertEquals(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(), "Breakpack Conveyable");
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull((deliveryDocumentLine.getAdditionalInfo().getPackTypeCode()));
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertFalse(deliveryDocumentLine.getIsHazmat());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletHi());
  }

  @Test
  public void test_updateAdditionalItemDetailsFromGDMWhenUniqueItemSelectedByUser()
      throws IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocumentsForUniqueItemNumber();
    List<DeliveryDocument> deliveryDocumentList = instructionRequest.getDeliveryDocuments();
    rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(deliveryDocumentList);

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertFalse(deliveryDocumentLine.getIsHazmat());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletHi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull((deliveryDocumentLine.getAdditionalInfo().getPackTypeCode()));
    assertEquals(
        deliveryDocumentLine.getAdditionalInfo().getPalletHi(),
        deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi());
    assertEquals(
        deliveryDocumentLine.getAdditionalInfo().getPalletTi(),
        deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(), "Casepack Conveyable");
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionExists_SmartSlottingEnabledToUpdatePrimeSlot_GdmDoesNotReturnImageDetails_Success()
          throws IOException, ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(getInstruction());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
                anyLong(), anyString(), anyInt()))
        .thenReturn(25);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    doReturn(getPrimeSlotFromSmartSlotting())
        .when(slottingServiceImpl)
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_AtlasItems(),
            10L,
            headers);

    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertTrue(
        (instructionResp.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size() > 0));
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo());
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlotSize(),
        72);
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlot(),
        "A0001");
    assertEquals(instructionResp.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResp.getDeliveryDocuments().get(0));
    assertNotNull(instructionResp.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));
    assertNotNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo());
    assertNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getImages());

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(slottingServiceImpl, times(1))
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Resource exception from SMART-SLOTTING. Error Code = GLS-RCV-SMART-SLOT-PRIME-404, Error Message = Invalid Slot ID")
  public void
      test_createInstructionForUpcReceiving_whenInstructionExists_SmartSlottingEnabledToUpdatePrimeSlot_Failure()
          throws IOException, ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(getInstruction());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
                anyLong(), anyString(), anyInt()))
        .thenReturn(25);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                    "Invalid Slot ID"),
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                "Invalid Slot ID"))
        .when(slottingServiceImpl)
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments_AtlasItems(),
        10L,
        headers);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(2))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(slottingServiceImpl, times(1))
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_returns_instruction_whenInstructionNotExists_andVendorComplianceValidationIsNotNeeded()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments();
    instructionRequest.setVendorComplianceValidated(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            instructionRequest, 10L, headers);
    assertNotNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertNotNull(
        instructionResp.getInstruction().getInstructionMsg(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    assertEquals(
        10,
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalReceivedQty()
            .intValue());
    assertFalse(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertFalse(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void test_createInstructionForUpcReceiving_ReturnsNoInstruction_dueToLimitedQtyCompliance()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithLimitedQtyCompliance();
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setLimitedQtyVerificationRequired(true);
    instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(instructionResponse);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            instructionRequest, 10L, headers);

    assertNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertFalse(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertTrue(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());
    assertNull(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getLabelTypeCode());

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
  }

  @Test
  public void test_createInstructionForUpcReceiving_ReturnsNoInstruction_dueToLithiumIonCompliance()
      throws IOException, ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithLithiumIonCompliance();
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setLithiumIonVerificationRequired(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setLabelTypeCode(ReceivingConstants.LITHIUM_LABEL_CODE_3480);
    instructionResponse.setDeliveryDocuments(deliveryDocuments);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(instructionResponse);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            instructionRequest, 10L, headers);

    assertNull(instructionResp.getInstruction());
    assertNotNull(instructionResp.getDeliveryDocuments());
    assertTrue(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertFalse(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());
    assertEquals(
        instructionResp
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getLabelTypeCode(),
        ReceivingConstants.LITHIUM_LABEL_CODE_3480);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
  }

  @Test
  public void test_createInstructionForSinglePOLine_ReturnsOverageWarning_InstructionResponse()
      throws IOException, ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(instructionResponse);
    InstructionResponse instructionForUpcReceiving =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            MockInstructionRequest.getInstructionRequestWithDeliveryDocuments(), 358L, headers);

    assertNotNull(instructionForUpcReceiving);
    assertNotNull(instructionForUpcReceiving.getInstruction());
    assertTrue(instructionForUpcReceiving.getDeliveryDocuments().size() > 0);
    assertTrue(
        instructionForUpcReceiving
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());
    assertEquals(
        (int)
            instructionResponse
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getTotalReceivedQty(),
        358);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Item 3804890 is showing as blocked and cannot be received. Please contact the QA team on how to proceed.")
  public void test_createInstruction_X_Block_Item() throws IOException, ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocumentsForXBlockItem();
    instructionRequest
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("X");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(), any(), anyString()))
        .thenReturn(null);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());

    rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
        instructionRequest, 10L, headers);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The PO: 8458708162 or PO Line: 1 for this item has been cancelled. Please see a supervisor for assistance with this item.")
  public void testCreateInstructionForUpcReceivingReturnsErrorForCancelledPoOrPoLine()
      throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
        instructionRequest, 10L, headers);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The PO Line 1 for this item has been rejected. Please see a supervisor for assistance with this item.")
  public void testCreateInstructionForUpcReceivingReturnsErrorForRejectedPoLine()
      throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    OperationalInfo operationalInfo = new OperationalInfo();
    operationalInfo.setUserId("sysadmin");
    operationalInfo.setState(POLineStatus.REJECTED.name());
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setOperationalInfo(operationalInfo);

    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
        instructionRequest, 10L, headers);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_whenInstructionNotExists_ReturnsSuccessResponse_WithOverageAlertFlagAsTrue()
          throws IOException, ReceivingException {
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                any(DeliveryDocument.class), any(InstructionRequest.class), anyString()))
        .thenReturn(null);
    when(instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            anyString(), anyInt()))
        .thenReturn(null);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    InstructionResponse instructionResp =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            MockInstructionRequest.getInstructionRequestWithDeliveryDocuments(), 388L, headers);
    assertNotNull(instructionResp.getInstruction());
    assertTrue(
        instructionResp
            .getInstruction()
            .getInstructionCode()
            .equalsIgnoreCase(RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionCode()));
    assertTrue(
        instructionResp
            .getInstruction()
            .getInstructionMsg()
            .equalsIgnoreCase(RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionMsg()));
    assertTrue(
        instructionResp
            .getInstruction()
            .getProviderId()
            .equalsIgnoreCase(RdcConstants.PROVIDER_ID));
    assertNotNull(instructionResp.getInstruction().getGtin());
    assertEquals(
        (int)
            instructionResp
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getTotalReceivedQty(),
        388);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
            any(DeliveryDocument.class), any(InstructionRequest.class), anyString());
    verify(instructionRepository, times(0))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
    verify(instructionPersisterService, times(0)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void test_AutoPoSelectReturnsOldestPo_When_GDMReturned2Pos_And_RDSReturnedSuccessResponse()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();

    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapForMoreThanOnePo());

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        rdcInstructionUtils.autoSelectDocumentAndDocumentLine(
            deliveryDocuments, RdcConstants.QTY_TO_RECEIVE, upcNumber, headers);

    assertNotNull(autoSelectDocumentAndDocumentLine);
    assertEquals(
        deliveryDocuments.get(1).getPurchaseReferenceNumber(),
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber());
    assertEquals((long) autoSelectDocumentAndDocumentLine.getValue(), 10L);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void
      test_AutoPoSelectReturnsActivePO_When_GDMReturned2Pos_And_RDSReturnedSuccessResponseFor1PO()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();

    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(
            MockReceivedQtyRespFromRds
                .getReceivedQtyMapForMultiPORDSReturnsFoundAndErrorResponseForOnePO(
                    deliveryDocumentList));

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        rdcInstructionUtils.autoSelectDocumentAndDocumentLine(
            deliveryDocumentList, RdcConstants.QTY_TO_RECEIVE, upcNumber, headers);

    assertNotNull(autoSelectDocumentAndDocumentLine);
    assertEquals(
        deliveryDocumentList.get(1).getPurchaseReferenceNumber(),
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber());
    assertEquals((long) autoSelectDocumentAndDocumentLine.getValue(), 10L);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void
      test_AutoPoSelectReturns1stPoLine_When_GDMReturnedSinglePoWith2PoLines_And_RDSReturnedSuccessResponse()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();

    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapForOnePoWithTwoLines());

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        rdcInstructionUtils.autoSelectDocumentAndDocumentLine(
            deliveryDocuments, RdcConstants.QTY_TO_RECEIVE, upcNumber, headers);

    assertNotNull(autoSelectDocumentAndDocumentLine);
    assertEquals(
        deliveryDocuments.get(0).getPurchaseReferenceNumber(),
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber());
    assertEquals(
        autoSelectDocumentAndDocumentLine
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals((long) autoSelectDocumentAndDocumentLine.getValue(), 10L);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void
      test_AutoPoSelectReturns2ndPoLine_When_GDMReturnedSinglePoWith2PoLines_And_RDSReturnedSuccessResponse_And_1stPoLineOpenQtyHasBeenFulfilled()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();

    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(
            MockReceivedQtyRespFromRds
                .getReceivedQtyMapByDeliveryDocumentForLine1OpenQtyIsFulfilled(deliveryDocuments));

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        rdcInstructionUtils.autoSelectDocumentAndDocumentLine(
            deliveryDocuments, RdcConstants.QTY_TO_RECEIVE, upcNumber, headers);

    assertNotNull(autoSelectDocumentAndDocumentLine);
    assertEquals(
        deliveryDocuments.get(0).getPurchaseReferenceNumber(),
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber());
    assertEquals(
        autoSelectDocumentAndDocumentLine
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        2);
    assertEquals((long) autoSelectDocumentAndDocumentLine.getValue(), 0L);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void
      test_AutoPoSelectReturns1stPoAndPoLineToReceiveAllowedOverage_When_GDMReturnedSinglePoWith2PoLines_And_RDSReturnedSuccessResponse()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();

    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(
            MockReceivedQtyRespFromRds.getReceivedQtyMapByDeliveryDocumentHasFulfilledOpenQty(
                deliveryDocuments));

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        rdcInstructionUtils.autoSelectDocumentAndDocumentLine(
            deliveryDocuments, RdcConstants.QTY_TO_RECEIVE, upcNumber, headers);

    assertNotNull(autoSelectDocumentAndDocumentLine);
    assertEquals(
        deliveryDocuments.get(0).getPurchaseReferenceNumber(),
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber());
    assertEquals(
        autoSelectDocumentAndDocumentLine
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertNotNull(autoSelectDocumentAndDocumentLine.getValue());
    assertEquals((long) autoSelectDocumentAndDocumentLine.getValue(), 3000L);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void
      test_AutoPoSelectDocumentByDocumentLine_When_GDMReturnedSinglePoWith2PoLines_And_RDSReturnedErrorResponseForPoLine1()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();

    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(
            MockReceivedQtyRespFromRds
                .getReceivedQtyMapForOnePoWithTwoLinesReturnsFoundAndErrorResponseForPoLine1(
                    deliveryDocuments));

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        rdcInstructionUtils.autoSelectDocumentAndDocumentLine(
            deliveryDocuments, RdcConstants.QTY_TO_RECEIVE, upcNumber, headers);

    assertNotNull(autoSelectDocumentAndDocumentLine);
    assertEquals(
        deliveryDocuments.get(0).getPurchaseReferenceNumber(),
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber());
    assertEquals(
        autoSelectDocumentAndDocumentLine
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        2);
    assertNotNull(autoSelectDocumentAndDocumentLine.getValue());
    assertEquals((long) autoSelectDocumentAndDocumentLine.getValue(), 10L);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void
      test_AutoPoSelectDocumentByDocumentLine_When_GDMReturnedSinglePoWith2PoLines_And_RDSReturnedErrorResponseForPoLine2()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();

    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(
            MockReceivedQtyRespFromRds
                .getReceivedQtyMapForOnePoWithTwoLinesReturnsFoundAndErrorResponseForPoLine2(
                    deliveryDocuments));

    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine =
        rdcInstructionUtils.autoSelectDocumentAndDocumentLine(
            deliveryDocuments, RdcConstants.QTY_TO_RECEIVE, upcNumber, headers);

    assertNotNull(autoSelectDocumentAndDocumentLine);
    assertEquals(
        deliveryDocuments.get(0).getPurchaseReferenceNumber(),
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber());
    assertEquals(
        autoSelectDocumentAndDocumentLine
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertNotNull(autoSelectDocumentAndDocumentLine.getValue());
    assertEquals((long) autoSelectDocumentAndDocumentLine.getValue(), 10L);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void test_checkIfNewInstructionCanBeCreated_Returns_true()
      throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int maxReceivedQty = deliveryDocumentLine.getTotalOrderQty() + 50;
    deliveryDocumentLine.setMaxReceiveQty(maxReceivedQty);
    deliveryDocumentLine.setTotalReceivedQty(0);
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setDeliveryNumber("32334344");
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(25);

    assertTrue(
        rdcInstructionUtils
            .checkIfNewInstructionCanBeCreated(
                mockInstructionRequest, deliveryDocumentLine, 0, headers)
            .getKey());

    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
  }

  @Test
  public void test_checkIfNewInstructionCanBeCreated_Returns_false()
      throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int maxReceivedQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    deliveryDocumentLine.setMaxReceiveQty(maxReceivedQty);
    deliveryDocumentLine.setTotalReceivedQty(maxReceivedQty);
    InstructionRequest mockInstructionRequest = new InstructionRequest();

    when(instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            anyString(), anyInt()))
        .thenReturn(Long.valueOf(10));

    assertFalse(
        rdcInstructionUtils
            .checkIfNewInstructionCanBeCreated(
                mockInstructionRequest, deliveryDocumentLine, 0, headers)
            .getKey());

    verify(instructionRepository, times(0))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
  }

  @Test
  public void test_checkIfNewInstructionCanBeCreated_Throws_Exception() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int maxReceivedQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    deliveryDocumentLine.setMaxReceiveQty(maxReceivedQty);
    deliveryDocumentLine.setTotalReceivedQty(deliveryDocumentLine.getTotalOrderQty());
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setDeliveryNumber("32334344");

    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(30);

    try {
      rdcInstructionUtils.checkIfNewInstructionCanBeCreated(
          mockInstructionRequest, deliveryDocumentLine, 0, headers);
    } catch (ReceivingException excp) {
      assertNotNull(excp);
      assertSame(excp.getErrorResponse().getErrorCode(), "GLS-RCV-MULTI-SPLIT-INST-400");
    }

    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
  }

  @Test
  public void
      testCheckIfNewInstructionCanBeCreated_ThrowsException_WhenMaxAllowedQtyIsReceived_ForProblem_FIXIT()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest = getMockInstructionRequest();
    instructionRequest.setProblemTagId(problemTagId);
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int maxReceivedQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    deliveryDocumentLine.setMaxReceiveQty(maxReceivedQty);
    deliveryDocumentLine.setTotalReceivedQty(maxReceivedQty);

    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setIssueId("3434-32323");
    problemLabel.setProblemTagId("326792343434");

    doNothing()
        .when(rdcProblemUtils)
        .reportErrorForProblemReceiving(
            anyString(), nullable(String.class), anyString(), anyInt(), any(HttpHeaders.class));

    try {
      rdcInstructionUtils.checkIfNewInstructionCanBeCreated(
          instructionRequest, deliveryDocumentLine, 0, headers);
    } catch (ReceivingException excp) {
      assertNotNull(excp);
    }

    verify(rdcProblemUtils, times(1))
        .reportErrorForProblemReceiving(
            anyString(), nullable(String.class), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void
      testCheckIfNewInstructionCanBeCreated_ThrowsException_WhenMaxAllowedQtyIsReceived_ForProblem_FIT()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest = getMockInstructionRequest();
    instructionRequest.setProblemTagId(problemTagId);
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int maxReceivedQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    deliveryDocumentLine.setMaxReceiveQty(maxReceivedQty);
    deliveryDocumentLine.setTotalReceivedQty(maxReceivedQty);

    doNothing()
        .when(rdcProblemUtils)
        .reportErrorForProblemReceiving(
            anyString(), nullable(String.class), anyString(), anyInt(), any(HttpHeaders.class));

    try {
      rdcInstructionUtils.checkIfNewInstructionCanBeCreated(
          instructionRequest, deliveryDocumentLine, 0, headers);
    } catch (ReceivingException excp) {
      assertNotNull(excp);
    }

    verify(rdcProblemUtils, times(1))
        .reportErrorForProblemReceiving(
            anyString(), nullable(String.class), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void test_checkIfNewInstructionCanBeCreated_ReturnsTrue_ForProblem()
      throws IOException, ReceivingException {
    InstructionRequest mockInstructionRequest = getMockInstructionRequest();
    mockInstructionRequest.setProblemTagId(problemTagId);
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int maxReceivedQty = deliveryDocumentLine.getTotalOrderQty() + 50;
    deliveryDocumentLine.setMaxReceiveQty(maxReceivedQty);
    deliveryDocumentLine.setTotalReceivedQty(0);

    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);

    assertTrue(
        rdcInstructionUtils
            .checkIfNewInstructionCanBeCreated(
                mockInstructionRequest, deliveryDocumentLine, 0, headers)
            .getKey());

    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
  }

  @Test
  public void test_checkIfNewInstructionCanBeCreated_For_Problem_Throws_Exception()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int maxReceivedQty =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    deliveryDocumentLine.setMaxReceiveQty(maxReceivedQty);
    deliveryDocumentLine.setTotalReceivedQty(deliveryDocumentLine.getTotalOrderQty());

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setProblemTagId(problemTagId);
    mockInstructionRequest.setDeliveryNumber("32334344");

    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(30);
    when(instructionPersisterService.findNonSplitPalletInstructionCount(anyString(), anyInt()))
        .thenReturn(2);

    try {
      rdcInstructionUtils.checkIfNewInstructionCanBeCreated(
          mockInstructionRequest, deliveryDocumentLine, 0, headers);
    } catch (ReceivingException excp) {
      assertNotNull(excp);
      assertSame(excp.getErrorResponse().getErrorCode(), "GLS-RCV-MULTI-OPEN-INST-PROBLEM-400");
    }

    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1))
        .findNonSplitPalletInstructionCount(anyString(), anyInt());
  }

  @Test
  public void test_IsSinglePoAndPoLineReturnsTrue_ForSinglePoAndPoLine() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    assertTrue(rdcInstructionUtils.isSinglePoAndPoLine(deliveryDocuments));
  }

  @Test
  public void test_IsSinglePoAndPoLineReturnsFalse_ForMultiPo() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    assertFalse(rdcInstructionUtils.isSinglePoAndPoLine(deliveryDocuments));
  }

  @Test
  public void test_IsSinglePoAndPoLineReturnsFalse_ForSinglePoWithMultiPoLines()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();
    assertFalse(rdcInstructionUtils.isSinglePoAndPoLine(deliveryDocuments));
  }

  @Test
  public void testGetOverageAlertInstruction() throws IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments();
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData additionalItemData = new ItemData();
    additionalItemData.setPrimeSlot("A0001");
    additionalItemData.setPrimeSlotSize(72);
    deliveryDocumentLine.setAdditionalInfo(additionalItemData);

    Instruction overageAlertInstruction =
        rdcInstructionUtils.getOverageAlertInstruction(instructionRequest, headers);

    assertNotNull(overageAlertInstruction);
    assertNotNull(overageAlertInstruction.getMove());
    assertTrue(
        overageAlertInstruction
            .getInstructionCode()
            .equalsIgnoreCase(RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionCode()));
    assertTrue(
        overageAlertInstruction
            .getInstructionMsg()
            .equalsIgnoreCase(RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionMsg()));
    assertEquals(instructionRequest.getUpcNumber(), overageAlertInstruction.getGtin());
    assertTrue(overageAlertInstruction.getProviderId().equalsIgnoreCase(RdcConstants.PROVIDER_ID));
    assertNotNull(overageAlertInstruction.getMove().get(ReceivingConstants.MOVE_PRIME_LOCATION));
    assertNotNull(
        overageAlertInstruction.getMove().get(ReceivingConstants.MOVE_PRIME_LOCATION_SIZE));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetOverageAlertInstructionForAsnReceiving() throws IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments();
    instructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    ItemData additionalItemData = new ItemData();
    additionalItemData.setPrimeSlot("A0001");
    additionalItemData.setPrimeSlotSize(72);
    deliveryDocumentLine.setAdditionalInfo(additionalItemData);

    rdcInstructionUtils.getOverageAlertInstruction(instructionRequest, headers);
  }

  @Test
  public void testGetReceiptsFromRDSByDocumentLine_HappyPath() throws IOException {
    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
    documentLine.setPurchaseReferenceNumber("8458708162");
    documentLine.setPurchaseReferenceLineNumber(1);
    List<DeliveryDocumentLine> documentLines = Collections.singletonList(documentLine);

    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(any(), any(HttpHeaders.class)))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapForOnePoWithOneLine());

    Pair<DeliveryDocumentLine, Long> receivedQtyResponseFromRDS =
        rdcInstructionUtils.getReceiptsFromRDSByDocumentLine(documentLines, headers);

    assertNotNull(receivedQtyResponseFromRDS);
    assertEquals(
        documentLines.get(0).getPurchaseReferenceNumber(),
        receivedQtyResponseFromRDS.getKey().getPurchaseReferenceNumber());
    assertEquals((long) receivedQtyResponseFromRDS.getValue(), 10L);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocumentLine(any(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetReceiptsFromRDSByDocumentLine_ErrorPath() throws IOException {
    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
    documentLine.setPurchaseReferenceNumber("8458708162");
    documentLine.setPurchaseReferenceLineNumber(1);
    List<DeliveryDocumentLine> documentLines = Collections.singletonList(documentLine);

    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(any(), any(HttpHeaders.class)))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapForOnePoWithOneLineReturnsError());

    rdcInstructionUtils.getReceiptsFromRDSByDocumentLine(documentLines, headers);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocumentLine(any(), any(HttpHeaders.class));
  }

  @Test
  public void testVerifyTiHiPopulatesRDSTiHiInDeliveryDocumentLine() throws IOException {
    DeliveryDocument deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setAdditionalInfo(getAdditionItemInfo());

    deliveryDocumentLine = rdcInstructionUtils.validateTiHiFromRdsAndGdm(deliveryDocumentLine);

    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi());
    assertEquals(
        deliveryDocumentLine.getPalletTie(),
        deliveryDocumentLine.getAdditionalInfo().getPalletTi());
    assertEquals(
        deliveryDocumentLine.getPalletHigh(),
        deliveryDocumentLine.getAdditionalInfo().getPalletHi());
  }

  @Test
  public void testVerifyTiHiPopulatesRDSTiHiInDeliveryDocumentLine_MissingTixHiFromGDM()
      throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_MissingTixHi().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setAdditionalInfo(getAdditionItemInfo());

    deliveryDocumentLine = rdcInstructionUtils.validateTiHiFromRdsAndGdm(deliveryDocumentLine);

    assertNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi());
    assertEquals(
        deliveryDocumentLine.getPalletTie(),
        deliveryDocumentLine.getAdditionalInfo().getPalletTi());
    assertEquals(
        deliveryDocumentLine.getPalletHigh(),
        deliveryDocumentLine.getAdditionalInfo().getPalletHi());
  }

  @Test
  public void testVerifyTiHiPopulatesRDSTiHiInDeliveryDocumentLine_ZeroTixHiFromGDM()
      throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_ZeroTixHi().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setAdditionalInfo(getAdditionItemInfo());

    deliveryDocumentLine = rdcInstructionUtils.validateTiHiFromRdsAndGdm(deliveryDocumentLine);

    assertTrue(Objects.equals(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi(), 0));
    assertTrue(Objects.equals(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi(), 0));
    assertEquals(
        deliveryDocumentLine.getPalletTie(),
        deliveryDocumentLine.getAdditionalInfo().getPalletTi());
    assertEquals(
        deliveryDocumentLine.getPalletHigh(),
        deliveryDocumentLine.getAdditionalInfo().getPalletHi());
  }

  @Test
  public void testValidatePoLineIsCancelledOrRejectedReturnsPOLCancelledException()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocuments.get(0).setPurchaseReferenceStatus(POStatus.CNCL.name());
    try {
      rdcInstructionUtils.validatePoLineIsCancelledOrClosedOrRejected(deliveryDocumentLine);
    } catch (ReceivingException exception) {
      assertEquals(exception.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(
          exception.getErrorResponse().getErrorCode(),
          ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      assertEquals(
          exception.getErrorResponse().getErrorHeader(),
          GdmError.PO_OR_POL_CANCELLED_ERROR.getErrorHeader());
    }
  }

  @Test
  public void testValidatePoLineIsCancelledOrRejectedReturnsPOLClosedException()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPurchaseReferenceLineStatus(POLineStatus.CLOSED.name());
    try {
      rdcInstructionUtils.validatePoLineIsCancelledOrClosedOrRejected(deliveryDocumentLine);
    } catch (ReceivingException exception) {
      assertEquals(exception.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(
          exception.getErrorResponse().getErrorCode(),
          ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      assertEquals(
          exception.getErrorResponse().getErrorHeader(),
          GdmError.PO_LINE_CLOSED_ERROR.getErrorHeader());
    }
  }

  @Test
  public void testValidatePoLineIsCancelledOrRejectedReturnsPOLRejectionException()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    OperationalInfo operationalInfo = new OperationalInfo();
    operationalInfo.setState(POLineStatus.REJECTED.name());
    deliveryDocumentLine.setOperationalInfo(operationalInfo);
    try {
      rdcInstructionUtils.validatePoLineIsCancelledOrClosedOrRejected(deliveryDocumentLine);
    } catch (ReceivingException exception) {
      assertEquals(exception.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(
          exception.getErrorResponse().getErrorCode(),
          ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      assertEquals(
          exception.getErrorResponse().getErrorHeader(),
          GdmError.PO_LINE_REJECTION_ERROR.getErrorHeader());
    }
  }

  @Test
  public void testCancelInstructionAllowedReturnsTrue() throws ReceivingException {
    Instruction instruction = getInstruction();
    Boolean isCancelInstructionAllowed =
        rdcInstructionUtils.isCancelInstructionAllowed(
            instruction, ReceivingConstants.DEFAULT_USER);
    assertTrue(isCancelInstructionAllowed);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testCancelInstructionAllowedThrowsExceptionForCompletedInstruction()
      throws ReceivingException {
    Instruction instruction = getInstruction();
    instruction.setCompleteUserId(ReceivingConstants.DEFAULT_USER);
    instruction.setCompleteTs(new Date());
    rdcInstructionUtils.isCancelInstructionAllowed(instruction, ReceivingConstants.DEFAULT_USER);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testCancelInstructionAllowedThrowsExceptionForPartiallyReceivedInstruction()
      throws ReceivingException {
    Instruction instruction = getInstruction();
    instruction.setReceivedQuantity(100);
    rdcInstructionUtils.isCancelInstructionAllowed(instruction, ReceivingConstants.DEFAULT_USER);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCancelInstructionAllowedThrowsExceptionWhenInstructionIsOwnedBySomeOneElse()
      throws ReceivingException {
    Instruction instruction = getInstruction();
    rdcInstructionUtils.isCancelInstructionAllowed(
        instruction, headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
  }

  @Test
  public void testHasMoreUniqItemsReturnsTrue() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setItemNbr(123L);
    Boolean hasMoreUniqItems = rdcInstructionUtils.hasMoreUniqueItems(deliveryDocuments);
    assertTrue(hasMoreUniqItems);
  }

  @Test
  public void testHasMoreUniqItemsReturnsFalse() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    Boolean hasMoreUniqItems = rdcInstructionUtils.hasMoreUniqueItems(deliveryDocuments);
    assertFalse(hasMoreUniqItems);
  }

  @Test
  public void testAutoSelectDocumentForMultiPoOrPoLinesReturnsPOL1ForMaxQuantitiesFulfilled()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708162-1", 310L);
    receivedQtyMap.put("8458708162-2", 120L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        rdcInstructionUtils.autoSelectDocumentForMultiPoOrPoLines(
            deliveryDocumentList, 1, receivedQuantityResponseFromRDS, upcNumber);

    assertNotNull(autoSelectedDocument);
    assertNotNull(autoSelectedDocument.getKey());
    assertNotNull(autoSelectedDocument.getValue());
    assertEquals(autoSelectedDocument.getKey().getPurchaseReferenceNumber(), "8458708162");
    assertSame(autoSelectedDocument.getKey().getDeliveryDocumentLines().size(), 1);
    assertSame(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals((long) autoSelectedDocument.getValue(), 310L);
    assertTrue(rdcInstructionUtils.isSSTKDocument(autoSelectedDocument.getKey()));
  }

  @Test
  public void
      testAutoSelectDocumentForMultiPoOrPoLinesReturnsPO1POL1WhenOrderQtyIsNotFulfilledForPO1AndPOL1()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708162-1", 210L);
    receivedQtyMap.put("8458708162-2", 200L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        rdcInstructionUtils.autoSelectDocumentForMultiPoOrPoLines(
            deliveryDocumentList, 1, receivedQuantityResponseFromRDS, upcNumber);

    assertNotNull(autoSelectedDocument);
    assertNotNull(autoSelectedDocument.getKey());
    assertNotNull(autoSelectedDocument.getValue());
    assertEquals(autoSelectedDocument.getKey().getPurchaseReferenceNumber(), "8458708162");
    assertSame(autoSelectedDocument.getKey().getDeliveryDocumentLines().size(), 1);
    assertSame(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals((long) autoSelectedDocument.getValue(), 210L);
    assertFalse(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());
    assertTrue(rdcInstructionUtils.isSSTKDocument(autoSelectedDocument.getKey()));
  }

  @Test
  public void
      testAutoSelectDocumentForMultiPoOrPoLinesReturnsPO1POL2WhenOrderQtyIsFulfilledForPO1AndPOL1()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708162-1", 300L);
    receivedQtyMap.put("8458708162-2", 90L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        rdcInstructionUtils.autoSelectDocumentForMultiPoOrPoLines(
            deliveryDocumentList, 1, receivedQuantityResponseFromRDS, upcNumber);

    assertNotNull(autoSelectedDocument);
    assertNotNull(autoSelectedDocument.getKey());
    assertNotNull(autoSelectedDocument.getValue());
    assertEquals(autoSelectedDocument.getKey().getPurchaseReferenceNumber(), "8458708162");
    assertSame(autoSelectedDocument.getKey().getDeliveryDocumentLines().size(), 1);
    assertSame(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        2);
    assertSame(autoSelectedDocument.getValue(), 90L);
    assertFalse(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());
    assertTrue(rdcInstructionUtils.isSSTKDocument(autoSelectedDocument.getKey()));
  }

  @Test
  public void
      testAutoSelectDocumentForMultiPoOrPoLinesReturnsPO1POL1WhenOrderQtyIsFulfilledForBothLinesButAllowedOverageIsNotFulfilledForPO1AndPOL1()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708162-1", 300L);
    receivedQtyMap.put("8458708162-2", 100L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        rdcInstructionUtils.autoSelectDocumentForMultiPoOrPoLines(
            deliveryDocumentList, 1, receivedQuantityResponseFromRDS, upcNumber);

    assertNotNull(autoSelectedDocument);
    assertNotNull(autoSelectedDocument.getKey());
    assertNotNull(autoSelectedDocument.getValue());
    assertEquals(autoSelectedDocument.getKey().getPurchaseReferenceNumber(), "8458708162");
    assertSame(autoSelectedDocument.getKey().getDeliveryDocumentLines().size(), 1);
    assertSame(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals((long) autoSelectedDocument.getValue(), 300L);
    assertTrue(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());
    assertTrue(rdcInstructionUtils.isSSTKDocument(autoSelectedDocument.getKey()));
  }

  @Test
  public void
      testAutoSelectDocumentForMultiPoOrPoLinesReturnsPO1POL2WhenOrderQtyIsFulfilledForBothLinesButAllowedOverageIsNotFulfilledForPO1AndPOL2()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708162-1", 310L);
    receivedQtyMap.put("8458708162-2", 100L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        rdcInstructionUtils.autoSelectDocumentForMultiPoOrPoLines(
            deliveryDocumentList, 1, receivedQuantityResponseFromRDS, upcNumber);

    assertNotNull(autoSelectedDocument);
    assertNotNull(autoSelectedDocument.getKey());
    assertNotNull(autoSelectedDocument.getValue());
    assertTrue(autoSelectedDocument.getKey().getPurchaseReferenceNumber().equals("8458708162"));
    assertSame(autoSelectedDocument.getKey().getDeliveryDocumentLines().size(), 1);
    assertSame(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        2);
    assertSame(autoSelectedDocument.getValue(), 100L);
    assertTrue(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());
    assertTrue(rdcInstructionUtils.isSSTKDocument(autoSelectedDocument.getKey()));
  }

  @Test
  public void
      testAutoSelectDocumentForMultiPoOrPoLinesReturnsPO1POL1WhenMaxReceiveQtyIsFulfilledForAllPoAndPoLines()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708162-1", 3780L);
    receivedQtyMap.put("8458708163-1", 3780L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        rdcInstructionUtils.autoSelectDocumentForMultiPoOrPoLines(
            deliveryDocumentList, 1, receivedQuantityResponseFromRDS, upcNumber);

    assertNotNull(autoSelectedDocument);
    assertNotNull(autoSelectedDocument.getKey());
    assertNotNull(autoSelectedDocument.getValue());
    assertEquals(autoSelectedDocument.getKey().getPurchaseReferenceNumber(), "8458708163");
    assertSame(autoSelectedDocument.getKey().getDeliveryDocumentLines().size(), 1);
    assertSame(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals((long) autoSelectedDocument.getValue(), 3780L);
    assertNull(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());
    assertTrue(rdcInstructionUtils.isSSTKDocument(autoSelectedDocument.getKey()));
  }

  @Test
  public void
      testAutoSelectDocumentForMultiPoOrPoLinesReturnsPO1POL1WhenOrderQtyIsNotFulfilledForPO1AndPOL1_MultipleDAPos_SameMABD()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708163-1", 0L);
    receivedQtyMap.put("8458708164-1", 0L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        rdcInstructionUtils.autoSelectDocumentForMultiPoOrPoLines(
            deliveryDocumentList, 1, receivedQuantityResponseFromRDS, upcNumber);

    assertNotNull(autoSelectedDocument);
    assertNotNull(autoSelectedDocument.getKey());
    assertNotNull(autoSelectedDocument.getValue());
    assertEquals(autoSelectedDocument.getKey().getPurchaseReferenceNumber(), "8458708163");
    assertSame(autoSelectedDocument.getKey().getDeliveryDocumentLines().size(), 1);
    assertSame(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals((long) autoSelectedDocument.getValue(), 0L);
    assertFalse(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());
    assertTrue(rdcInstructionUtils.isDADocument(autoSelectedDocument.getKey()));
  }

  @Test
  public void
      testAutoSelectDocumentForMultiPoOrPoLinesReturnsPO1POL1WhenOrderQtyIsNotFulfilledForPO1AndPOL1_MultipleDAPos_DifferentMABD()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA_differentMABD();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708163-1", 0L);
    receivedQtyMap.put("8458708164-1", 0L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        rdcInstructionUtils.autoSelectDocumentForMultiPoOrPoLines(
            deliveryDocumentList, 1, receivedQuantityResponseFromRDS, upcNumber);

    assertNotNull(autoSelectedDocument);
    assertNotNull(autoSelectedDocument.getKey());
    assertNotNull(autoSelectedDocument.getValue());
    assertEquals(autoSelectedDocument.getKey().getPurchaseReferenceNumber(), "8458708164");
    assertSame(autoSelectedDocument.getKey().getDeliveryDocumentLines().size(), 1);
    assertSame(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals((long) autoSelectedDocument.getValue(), 0L);
    assertFalse(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());
    assertTrue(rdcInstructionUtils.isDADocument(autoSelectedDocument.getKey()));
  }

  @Test
  public void
      testAutoSelectDocumentForMultiPoOrPoLinesReturnsPO1POL1WhenOrderQtyIsNotFulfilledForPO1AndPOL1_MixedPos_SameMABD()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708162-1", 0L);
    receivedQtyMap.put("8458708163-1", 0L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        rdcInstructionUtils.autoSelectDocumentForMultiPoOrPoLines(
            deliveryDocumentList, 1, receivedQuantityResponseFromRDS, upcNumber);

    assertNotNull(autoSelectedDocument);
    assertNotNull(autoSelectedDocument.getKey());
    assertNotNull(autoSelectedDocument.getValue());
    assertEquals(autoSelectedDocument.getKey().getPurchaseReferenceNumber(), "8458708163");
    assertSame(autoSelectedDocument.getKey().getDeliveryDocumentLines().size(), 1);
    assertSame(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals((long) autoSelectedDocument.getValue(), 0L);
    assertFalse(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());
    assertTrue(rdcInstructionUtils.isDADocument(autoSelectedDocument.getKey()));
  }

  @Test
  public void
      testAutoSelectDocumentForMultiPoOrPoLinesReturnsPO1POL1WhenOrderQtyIsNotFulfilledForPO1AndPOL1_MixedPos_DifferentMABD()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO_DifferentMABD();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708162-1", 0L);
    receivedQtyMap.put("8458708163-1", 0L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        rdcInstructionUtils.autoSelectDocumentForMultiPoOrPoLines(
            deliveryDocumentList, 1, receivedQuantityResponseFromRDS, upcNumber);

    assertNotNull(autoSelectedDocument);
    assertNotNull(autoSelectedDocument.getKey());
    assertNotNull(autoSelectedDocument.getValue());
    assertEquals(autoSelectedDocument.getKey().getPurchaseReferenceNumber(), "8458708162");
    assertSame(autoSelectedDocument.getKey().getDeliveryDocumentLines().size(), 1);
    assertSame(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals((long) autoSelectedDocument.getValue(), 0L);
    assertFalse(
        autoSelectedDocument
            .getKey()
            .getDeliveryDocumentLines()
            .get(0)
            .getAutoPoSelectionOverageIncluded());
    assertTrue(rdcInstructionUtils.isSSTKDocument(autoSelectedDocument.getKey()));
  }

  @Test
  public void testFilterSSTKDocumentsReturnsDocuments() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    List<DeliveryDocument> filterSSTKDeliveryDocuments =
        rdcInstructionUtils.filterSSTKDeliveryDocuments(deliveryDocuments);
    assertTrue(filterSSTKDeliveryDocuments.size() > 0);
  }

  @Test
  public void testFilterSSTKDocumentsReturnsEmptyDocuments() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<DeliveryDocument> filterSSTKDeliveryDocuments =
        rdcInstructionUtils.filterSSTKDeliveryDocuments(deliveryDocuments);
    assertEquals(filterSSTKDeliveryDocuments.size(), 0);
  }

  @Test
  public void testFilterSSTKDocumentsReturnsDocumentsForMixedPoTypes() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO();
    List<DeliveryDocument> filterSSTKDeliveryDocuments =
        rdcInstructionUtils.filterSSTKDeliveryDocuments(deliveryDocuments);
    assertTrue(filterSSTKDeliveryDocuments.size() > 0);
  }

  @Test
  public void testCheckDeliveryReceivableIsSuccess() throws IOException, ReceivingException {
    DeliveryDocument deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    rdcInstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocument);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCheckDeliveryReceivableThrowsException() throws IOException, ReceivingException {
    DeliveryDocument deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    deliveryDocument.setDeliveryStatus(DeliveryStatus.PNDFNL);
    rdcInstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocument);
  }

  @Test
  public void test_createInstructionForUpcReceiving_validateExistingInstruction_OverageAlert()
      throws IOException {

    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(25);

    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(400L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    Pair<Instruction, List<DeliveryDocument>> response =
        rdcInstructionUtils.validateExistingInstruction(
            getInstruction(),
            MockInstructionRequest.getDeliveryDocumentsForSSTK_BreakPackRatioOne(),
            headers);

    assertNotNull(response.getKey());
    assertNotNull(response.getKey().getGtin());
    assertNotNull(
        response.getKey().getInstructionMsg(),
        RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionMsg());
    assertNotNull(
        response.getKey().getInstructionCode(),
        RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionCode());
    assertNotNull(response.getValue());
    assertEquals(response.getValue().size(), 1);

    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_validateExistingInstruction_IqsDisabled_SmartSlottingDisabled()
          throws IOException {

    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(20);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(400L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    Pair<Instruction, List<DeliveryDocument>> response =
        rdcInstructionUtils.validateExistingInstruction(
            getInstruction(),
            MockInstructionRequest.getDeliveryDocumentsForSSTK_BreakPackRatioOne(),
            headers);

    assertNotNull(response.getValue());
    assertEquals(response.getValue().size(), 1);
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(slottingServiceImpl, times(0))
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_validateExistingInstruction_IqsEnabled_SmartSlottingDisabled()
          throws IOException {

    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(20);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DEFAULT_ITEM_HANDLING_CODE_ENABLED,
            false))
        .thenReturn(true);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(400L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    Pair<Instruction, List<DeliveryDocument>> response =
        rdcInstructionUtils.validateExistingInstruction(
            getInstruction(),
            MockInstructionRequest.getDeliveryDocumentsForSSTK_BreakPackRatioOne(),
            headers);

    assertNotNull(response.getValue());
    assertEquals(response.getValue().size(), 1);
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(slottingServiceImpl, times(0))
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_validateExistingInstruction_IqsDisabled_SmartSlottingEnabled()
          throws IOException {

    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(20);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(400L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(getPrimeSlotFromSmartSlotting())
        .when(slottingServiceImpl)
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));

    Pair<Instruction, List<DeliveryDocument>> response =
        rdcInstructionUtils.validateExistingInstruction(
            getInstruction(),
            MockInstructionRequest.getDeliveryDocumentsForSSTK_BreakPackRatioOne(),
            headers);

    assertNotNull(response.getValue());
    assertEquals(response.getValue().size(), 1);
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(slottingServiceImpl, times(1))
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void
      test_createInstructionForUpcReceiving_validateExistingInstruction_IqsEnabled_SmartSlottingEnabled()
          throws IOException {

    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(20);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DEFAULT_ITEM_HANDLING_CODE_ENABLED,
            false))
        .thenReturn(true);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(400L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(getPrimeSlotFromSmartSlotting())
        .when(slottingServiceImpl)
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));

    Pair<Instruction, List<DeliveryDocument>> response =
        rdcInstructionUtils.validateExistingInstruction(
            getInstruction(),
            MockInstructionRequest.getDeliveryDocumentsForSSTK_BreakPackRatioOne(),
            headers);

    assertNotNull(response.getValue());
    assertEquals(response.getValue().size(), 1);
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(slottingServiceImpl, times(1))
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testFetchExistingInstructionReturnsInstruction() throws IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments();
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(getInstruction());
    Instruction instruction =
        rdcInstructionUtils.fetchExistingInstruction(deliveryDocument, instructionRequest, headers);
    assertNotNull(instruction);
    verify(instructionPersisterService, times(1))
        .fetchExistingInstructionIfexists(any(InstructionRequest.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testFilterInvalidPoLinesFromDeliveryDocuments_returnsException_for_multiPO_and_allLines_areIn_cancelledStatus()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPoWithAllLinesCancelled();
    rdcInstructionUtils.filterInvalidPoLinesFromDocuments(
        getMockInstructionRequest(), deliveryDocuments);
  }

  @Test
  public void
      testFilterInvalidPoLinesFromDeliveryDocuments_returnsDocument_for_multiPO_and_allLines_are_notIn_cancelled_status()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithAllPartiallyCancelledLines();
    List<DeliveryDocument> filteredDocuments =
        rdcInstructionUtils.filterInvalidPoLinesFromDocuments(
            getMockInstructionRequest(), deliveryDocuments);
    assertNotNull(filteredDocuments);
    assertTrue(filteredDocuments.size() > 0);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testFilterInvalidPoLinesFromDeliveryDocuments_returnsException_for_SinglePO_and_allLines_areIn_cancelledStatus()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithAllCancelledLines();
    rdcInstructionUtils.filterInvalidPoLinesFromDocuments(
        getMockInstructionRequest(), deliveryDocuments);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testFilterCancelledLinesFromDeliveryDocuments_returnsException_for_SinglePO_and_allLines_areIn_closedStatus()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithAllCancelledLines();
    deliveryDocuments
        .stream()
        .forEach(
            document ->
                document
                    .getDeliveryDocumentLines()
                    .stream()
                    .forEach(
                        line -> line.setPurchaseReferenceLineStatus(POLineStatus.CLOSED.name())));
    rdcInstructionUtils.filterInvalidPoLinesFromDocuments(
        getMockInstructionRequest(), deliveryDocuments);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testFilterInvalidPoLinesFromDeliveryDocuments_returnsException_for_SinglePO_and_allLines_areIn_EitherCancelled_OrClosedStatus()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithAllCancelledLines();
    deliveryDocuments
        .stream()
        .forEach(
            document -> {
              document
                  .getDeliveryDocumentLines()
                  .stream()
                  .forEach(
                      line -> {
                        if (!line.getPurchaseReferenceLineStatus()
                            .equalsIgnoreCase(POLineStatus.CANCELLED.name())) {
                          line.setPurchaseReferenceLineStatus(POLineStatus.CLOSED.name());
                        }
                      });
            });
    rdcInstructionUtils.filterInvalidPoLinesFromDocuments(
        getMockInstructionRequest(), deliveryDocuments);
  }

  @Test
  public void
      testFilterCancelledLinesFromDeliveryDocuments_returnsDocument_for_singlePO_and_allLines_are_notIn_cancelled_status()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithAllPartiallyCancelledLines();
    List<DeliveryDocument> filteredDocuments =
        rdcInstructionUtils.filterInvalidPoLinesFromDocuments(
            getMockInstructionRequest(), deliveryDocuments);
    assertNotNull(filteredDocuments);
    assertTrue(filteredDocuments.size() > 0);
  }

  @Test
  public void testPrepareInstructionMessageWhenReceivingAnyItem() {
    DocumentLine documentLine = new DocumentLine();
    documentLine.setPurchaseReferenceNumber("PO1");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setVnpkQty(6);
    documentLine.setWhpkQty(6);

    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDeliveryDocumentLines(Collections.singletonList(documentLine));

    PublishInstructionSummary publishInstructionSummary =
        rdcInstructionUtils.prepareInstructionMessage(
            getInstruction(),
            updateInstructionRequest,
            10,
            MockLocationHeaders.getHeaders(facilityNum, countryCode));
    assertNotNull(publishInstructionSummary);
    assertEquals(
        publishInstructionSummary.getActivityName(), WFTInstruction.SSTK.getActivityName());
    assertEquals(
        publishInstructionSummary.getInstructionCode(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    assertEquals(
        publishInstructionSummary.getInstructionMsg(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    assertNotNull(publishInstructionSummary.getLocation());
  }

  @Test
  public void testPrepareInstructionMessageForPalletQtyAdjustments() {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);

    PublishInstructionSummary publishInstructionSummary =
        rdcInstructionUtils.prepareInstructionMessage(
            containerItem,
            LabelAction.BACKOUT,
            10,
            MockLocationHeaders.getHeaders(facilityNum, countryCode));

    assertNotNull(publishInstructionSummary);
    assertEquals(
        publishInstructionSummary.getInstructionCode(), WFTInstruction.LABEL_BACKOUT.getCode());
    assertEquals(
        publishInstructionSummary.getInstructionMsg(), WFTInstruction.LABEL_BACKOUT.getMessage());
    assertNotNull(publishInstructionSummary.getLocation());
  }

  @Test
  public void testIsAllSSTKPoFulfilledReturnsTrueWhenAllSSTKPosAreFulfilled() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> currentReceivedQty = new HashMap<>();
    for (DeliveryDocument po : deliveryDocumentList) {
      for (DeliveryDocumentLine pol : po.getDeliveryDocumentLines()) {
        String key =
            pol.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + pol.getPurchaseReferenceLineNumber();
        Integer receivedQty = pol.getTotalOrderQty() * pol.getVendorPack();
        currentReceivedQty.put(key, receivedQty.longValue());
      }
    }
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(currentReceivedQty);
    Pair<Boolean, List<DeliveryDocument>> responsePair =
        rdcInstructionUtils.isAllSSTKPoFulfilled(
            deliveryDocumentList, receivedQuantityResponseFromRDS);
    assertTrue(responsePair.getKey());
    assertEquals(responsePair.getValue().size(), 2);
  }

  @Test
  public void testIsAllSSTKPoFulfilledReturnsTrueWhenAllSSTKPosArePartiallySatisfied()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> currentReceivedQty = new HashMap<>();
    for (DeliveryDocument po : deliveryDocumentList) {
      for (DeliveryDocumentLine pol : po.getDeliveryDocumentLines()) {
        String key =
            pol.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + pol.getPurchaseReferenceLineNumber();
        Integer receivedQty = pol.getTotalOrderQty() * pol.getWarehousePack() - 10;
        currentReceivedQty.put(key, receivedQty.longValue());
      }
    }
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(currentReceivedQty);
    Pair<Boolean, List<DeliveryDocument>> responsePair =
        rdcInstructionUtils.isAllSSTKPoFulfilled(
            deliveryDocumentList, receivedQuantityResponseFromRDS);
    assertFalse(responsePair.getKey());
    assertEquals(responsePair.getValue().size(), 2);
  }

  @Test
  public void
      testIsAllSSTKPoFulfilledThrowsExceptionWhenRDSReturnsReceivedQtyForOnePoAndPoNotFoundForOtherPO()
          throws IOException {
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, String> errorMapResponse = new HashMap<>();
    Map<String, Long> currentReceivedQty = new HashMap<>();
    for (DeliveryDocument po : deliveryDocumentList) {
      for (DeliveryDocumentLine pol : po.getDeliveryDocumentLines()) {
        if (pol.getPurchaseReferenceNumber().equalsIgnoreCase("8458708163")) {
          String key =
              pol.getPurchaseReferenceNumber()
                  + ReceivingConstants.DELIM_DASH
                  + pol.getPurchaseReferenceLineNumber();
          Integer receivedQty = pol.getTotalOrderQty() * pol.getWarehousePack() - 10;
          currentReceivedQty.put(key, receivedQty.longValue());
        } else {
          String key =
              pol.getPurchaseReferenceNumber()
                  + ReceivingConstants.DELIM_DASH
                  + pol.getPurchaseReferenceLineNumber();
          errorMapResponse.put(key, "PO Not found");
        }
      }
    }
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(currentReceivedQty);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(errorMapResponse);
    Pair<Boolean, List<DeliveryDocument>> isAllSSTKPoFulfilledPair =
        rdcInstructionUtils.isAllSSTKPoFulfilled(
            deliveryDocumentList, receivedQuantityResponseFromRDS);
    assertFalse(isAllSSTKPoFulfilledPair.getKey());
    assertEquals(isAllSSTKPoFulfilledPair.getValue().size(), 1);
  }

  @Test
  public void
      testIsAllSSTKPoFulfilledThrowsExceptionWhenRDSReturnsReceivedQtyForOnePoPartiallyFulfilledAndPoCancelledForOtherPO()
          throws IOException {
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> currentReceivedQty = new HashMap<>();
    for (DeliveryDocument po : deliveryDocumentList) {
      for (DeliveryDocumentLine pol : po.getDeliveryDocumentLines()) {
        if (pol.getPurchaseReferenceNumber().equalsIgnoreCase("8458708163")) {
          String key =
              pol.getPurchaseReferenceNumber()
                  + ReceivingConstants.DELIM_DASH
                  + pol.getPurchaseReferenceLineNumber();
          Integer receivedQty = pol.getTotalOrderQty() * pol.getWarehousePack() - 10;
          currentReceivedQty.put(key, receivedQty.longValue());
        }
      }
    }
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(currentReceivedQty);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());

    Pair<Boolean, List<DeliveryDocument>> isAllSSTKPoFulfilledPair =
        rdcInstructionUtils.isAllSSTKPoFulfilled(
            deliveryDocumentList, receivedQuantityResponseFromRDS);
    assertFalse(isAllSSTKPoFulfilledPair.getKey());
    assertEquals(isAllSSTKPoFulfilledPair.getValue().size(), 1);
  }

  @Test
  public void testIsAllSSTKPoFulfilledReturnsTrueWhenAllSSTKPosAreFulfilled_SinglePoMultiLines()
      throws IOException {
    String upcNumber = "05723242323";
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> currentReceivedQty = new HashMap<>();
    for (DeliveryDocument po : deliveryDocumentList) {
      for (DeliveryDocumentLine pol : po.getDeliveryDocumentLines()) {
        String key =
            pol.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + pol.getPurchaseReferenceLineNumber();
        Integer receivedQty = pol.getTotalOrderQty() * pol.getVendorPack();
        currentReceivedQty.put(key, receivedQty.longValue());
      }
    }
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(currentReceivedQty);
    Pair<Boolean, List<DeliveryDocument>> responsePair =
        rdcInstructionUtils.isAllSSTKPoFulfilled(
            deliveryDocumentList, receivedQuantityResponseFromRDS);
    assertTrue(responsePair.getKey());
    assertEquals(responsePair.getValue().size(), 1);
    assertEquals(responsePair.getValue().get(0).getDeliveryDocumentLines().size(), 2);
  }

  @Test
  public void
      testIsAllSSTKPoFulfilledReturnsTrueWhenAllSSTKPosArePartiallyFulfilled_SinglePoMultiLines()
          throws IOException {
    String upcNumber = "05723242323";
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> currentReceivedQty = new HashMap<>();
    for (DeliveryDocument po : deliveryDocumentList) {
      for (DeliveryDocumentLine pol : po.getDeliveryDocumentLines()) {
        String key =
            pol.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + pol.getPurchaseReferenceLineNumber();
        Integer receivedQty = 10;
        currentReceivedQty.put(key, receivedQty.longValue());
      }
    }
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(currentReceivedQty);
    Pair<Boolean, List<DeliveryDocument>> responsePair =
        rdcInstructionUtils.isAllSSTKPoFulfilled(
            deliveryDocumentList, receivedQuantityResponseFromRDS);
    assertFalse(responsePair.getKey());
    assertEquals(responsePair.getValue().size(), 1);
    assertEquals(responsePair.getValue().get(0).getDeliveryDocumentLines().size(), 2);
  }

  @Test
  public void
      testIsAllSSTKPoFulfilledReturnsTrueWhenAllSSTKPoPoLinesFulfilled_OnePoLineIsNotActiveInRDS()
          throws IOException {
    String upcNumber = "05723242323";
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> currentReceivedQty = new HashMap<>();
    for (DeliveryDocument po : deliveryDocumentList) {
      for (DeliveryDocumentLine pol : po.getDeliveryDocumentLines()) {
        if (pol.getPurchaseReferenceLineNumber() == 1) {
          String key =
              pol.getPurchaseReferenceNumber()
                  + ReceivingConstants.DELIM_DASH
                  + pol.getPurchaseReferenceLineNumber();
          Integer receivedQty = pol.getTotalOrderQty() * pol.getVendorPack();
          currentReceivedQty.put(key, receivedQty.longValue());
        }
      }
    }
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(currentReceivedQty);
    Pair<Boolean, List<DeliveryDocument>> responsePair =
        rdcInstructionUtils.isAllSSTKPoFulfilled(
            deliveryDocumentList, receivedQuantityResponseFromRDS);
    assertTrue(responsePair.getKey());
    assertEquals(responsePair.getValue().size(), 1);
    assertEquals(responsePair.getValue().get(0).getDeliveryDocumentLines().size(), 1);
  }

  private Instruction getInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(123L);
    instruction.setGtin("2323232323");
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setActivityName(WFTInstruction.SSTK.getActivityName());
    instruction.setInstructionMsg(RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(ReceivingConstants.DEFAULT_USER);
    instruction.setPurchaseReferenceNumber("8458708162");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(30);
    instruction.setDeliveryNumber(345233434L);
    instruction.setPrintChildContainerLabels(false);
    String deliveryDocument =
        "{\"purchaseReferenceNumber\":\"8458708162\",\"financialGroupCode\":\"US\",\"baseDivCode\":\"WM\",\"vendorNumber\":\"666951\",\"vendorNbrDeptSeq\":666951923,\"deptNumber\":\"92\",\"purchaseCompanyId\":\"1\",\"purchaseReferenceLegacyType\":\"20\",\"poDCNumber\":\"6020\",\"purchaseReferenceStatus\":\"ACTV\",\"deliveryDocumentLines\":[{\"gtin\":\"00078742352077\",\"itemUPC\":\"00078742352077\",\"caseUPC\":\"20078742352071\",\"purchaseReferenceNumber\":\"2173162066\",\"purchaseReferenceLineNumber\":7,\"event\":\"POS REPLEN\",\"purchaseReferenceLineStatus\":\"PARTIALLY_RECEIVED\",\"autoPoSelectionOverageIncluded\":\"false\",\"whpkSell\":8.0,\"vendorPackCost\":8.16,\"vnpkQty\":5,\"whpkQty\":5,\"orderableQuantity\":0,\"warehousePackQuantity\":0,\"expectedQtyUOM\":\"ZA\",\"openQty\":154,\"expectedQty\":154,\"overageQtyLimit\":61,\"itemNbr\":557449570,\"purchaseRefType\":\"SSTKU\",\"palletTi\":14,\"palletHi\":6,\"vnpkWgtQty\":4.6,\"vnpkWgtUom\":\"LB\",\"vnpkcbqty\":0.541,\"vnpkcbuomcd\":\"CF\",\"color\":\"3352\",\"size\":\"100.0\",\"isHazmat\":false,\"itemDescription1\":\"GV BLK TEABAGS 100CT\",\"itemDescription2\":\"TEABAGS\",\"isConveyable\":true,\"warehouseRotationTypeCode\":\"2\",\"promoBuyInd\":\"N\",\"additionalInfo\":{\"isNewItem\":false,\"warehouseRotationTypeCode\":\"2\",\"weight\":4.6,\"weightFormatTypeCode\":\"F\",\"weightUOM\":\"LB\",\"warehouseMinLifeRemainingToReceive\":0,\"gdmPalletTi\":14,\"gdmPalletHi\":6,\"primeSlot\":\"A0002\",\"primeSlotSize\":772,\"handlingCode\":\"I\",\"packTypeCode\":\"C\",\"symEligibleIndicator\":\"A\",\"palletTi\":14,\"palletHi\":6,\"isHazardous\":0},\"operationalInfo\":{\"state\":\"ACTIVE\"},\"freightBillQty\":0,\"activeChannelMethods\":[],\"department\":\"92\",\"limitedQty\":0.0,\"originalChannel\":\"STAPLESTOCK\",\"vendorStockNumber\":\"00787423520702\",\"totalReceivedQty\":0,\"maxAllowedOverageQtyIncluded\":false,\"maxReceiveQty\":215,\"lithiumIonVerificationRequired\":false,\"limitedQtyVerificationRequired\":false}],\"totalPurchaseReferenceQty\":927,\"weight\":3440.0,\"weightUOM\":\"LB\",\"cubeQty\":399.0,\"cubeUOM\":\"CF\",\"freightTermCode\":\"COLL\",\"deliveryStatus\":\"WRK\",\"poTypeCode\":20,\"totalBolFbq\":0,\"poDcCountry\":\"US\",\"deliveryLegacyStatus\":\"WRK\",\"purchaseReferenceMustArriveByDate\":\"May 24, 2021 7:00:00 PM\",\"stateReasonCodes\":[\"WORKING\"],\"deliveryNumber\":27526360,\"importInd\":false}";
    instruction.setDeliveryDocument(deliveryDocument);

    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_PRIME_LOCATION, "A1002");
    moveTreeMap.put(ReceivingConstants.MOVE_PRIME_LOCATION_SIZE, 72);
    instruction.setMove(moveTreeMap);

    return instruction;
  }

  private Instruction getInstructionWithSscc() {
    Instruction instruction = new Instruction();
    instruction.setId(123L);
    instruction.setGtin("2323232323");
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setActivityName(WFTInstruction.SSTK.getActivityName());
    instruction.setInstructionMsg(RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(ReceivingConstants.DEFAULT_USER);
    instruction.setPurchaseReferenceNumber("8458708162");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(30);
    instruction.setDeliveryNumber(345233434L);
    instruction.setPrintChildContainerLabels(false);
    instruction.setSsccNumber("001234567890");
    String deliveryDocument =
        "{\"purchaseReferenceNumber\":\"8458708162\",\"financialGroupCode\":\"US\",\"baseDivCode\":\"WM\",\"vendorNumber\":\"666951\",\"vendorNbrDeptSeq\":666951923,\"deptNumber\":\"92\",\"purchaseCompanyId\":\"1\",\"purchaseReferenceLegacyType\":\"20\",\"poDCNumber\":\"6020\",\"purchaseReferenceStatus\":\"ACTV\",\"deliveryDocumentLines\":[{\"gtin\":\"00078742352077\",\"itemUPC\":\"00078742352077\",\"caseUPC\":\"20078742352071\",\"purchaseReferenceNumber\":\"8458708162\",\"purchaseReferenceLineNumber\":1,\"event\":\"POS REPLEN\",\"shippedQty\":200,\"shippedQtyUom\":\"ZA\",\"purchaseReferenceLineStatus\":\"PARTIALLY_RECEIVED\",\"autoPoSelectionOverageIncluded\":\"false\",\"whpkSell\":8.0,\"vendorPackCost\":8.16,\"vnpkQty\":5,\"whpkQty\":5,\"orderableQuantity\":0,\"warehousePackQuantity\":0,\"expectedQtyUOM\":\"ZA\",\"openQty\":154,\"expectedQty\":154,\"overageQtyLimit\":61,\"itemNbr\":557449570,\"purchaseRefType\":\"SSTKU\",\"palletTi\":14,\"palletHi\":6,\"vnpkWgtQty\":4.6,\"vnpkWgtUom\":\"LB\",\"vnpkcbqty\":0.541,\"vnpkcbuomcd\":\"CF\",\"color\":\"3352\",\"size\":\"100.0\",\"isHazmat\":false,\"itemDescription1\":\"GV BLK TEABAGS 100CT\",\"itemDescription2\":\"TEABAGS\",\"isConveyable\":true,\"warehouseRotationTypeCode\":\"2\",\"promoBuyInd\":\"N\",\"additionalInfo\":{\"isNewItem\":false,\"warehouseRotationTypeCode\":\"2\",\"weight\":4.6,\"weightFormatTypeCode\":\"F\",\"weightUOM\":\"LB\",\"warehouseMinLifeRemainingToReceive\":0,\"gdmPalletTi\":14,\"gdmPalletHi\":6,\"primeSlot\":\"A0002\",\"primeSlotSize\":772,\"handlingCode\":\"I\",\"packTypeCode\":\"C\",\"symEligibleIndicator\":\"A\",\"palletTi\":14,\"palletHi\":6,\"isHazardous\":0},\"operationalInfo\":{\"state\":\"ACTIVE\"},\"freightBillQty\":0,\"activeChannelMethods\":[],\"department\":\"92\",\"limitedQty\":0.0,\"originalChannel\":\"STAPLESTOCK\",\"vendorStockNumber\":\"00787423520702\",\"totalReceivedQty\":0,\"maxAllowedOverageQtyIncluded\":false,\"maxReceiveQty\":215,\"lithiumIonVerificationRequired\":false,\"limitedQtyVerificationRequired\":false}],\"totalPurchaseReferenceQty\":927,\"weight\":3440.0,\"weightUOM\":\"LB\",\"cubeQty\":399.0,\"cubeUOM\":\"CF\",\"freightTermCode\":\"COLL\",\"deliveryStatus\":\"WRK\",\"poTypeCode\":20,\"totalBolFbq\":0,\"poDcCountry\":\"US\",\"deliveryLegacyStatus\":\"WRK\",\"purchaseReferenceMustArriveByDate\":\"May 24, 2021 7:00:00 PM\",\"stateReasonCodes\":[\"WORKING\"],\"deliveryNumber\":27526360,\"importInd\":false}";
    instruction.setDeliveryDocument(deliveryDocument);

    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_PRIME_LOCATION, "A1002");
    moveTreeMap.put(ReceivingConstants.MOVE_PRIME_LOCATION_SIZE, 72);
    instruction.setMove(moveTreeMap);

    return instruction;
  }

  private Instruction getMockReceivedInstruction() {
    Instruction instruction = getInstruction();
    instruction.setReceivedQuantity(30);
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId("sysadmin");
    return instruction;
  }

  public static ItemData getAdditionItemInfo() {
    ItemData itemData = new ItemData();
    itemData.setPalletHi(5);
    itemData.setPalletTi(2);
    itemData.setPrimeSlot("A2104");
    itemData.setPrimeSlotSize(72);
    itemData.setPalletTi(10);
    itemData.setPalletHi(8);
    return itemData;
  }

  private InstructionRequest getMockInstructionRequest() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("123");
    instructionRequest.setUpcNumber("upc123");
    return instructionRequest;
  }

  @Test
  public void test_create_instruction_split_pallet() throws IOException, ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    doReturn(true).when(rdcManagedConfig).isSplitPalletEnabled();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doReturn(1L).when(instructionSetIdGenerator).generateInstructionSetId();
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
                any(), any(), anyString()))
        .thenReturn(null);
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(instructionResponse);
    InstructionRequest mockInstructionRequestWithDeliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments4BreakPack();
    mockInstructionRequestWithDeliveryDocuments.setReceivingType("SPLIT_PALLET_UPC");
    InstructionResponse instructionForUpcReceiving =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            mockInstructionRequestWithDeliveryDocuments, 358L, headers);

    assertNotNull(instructionForUpcReceiving);
    assertNotNull(instructionForUpcReceiving.getInstruction());
    assertTrue(instructionForUpcReceiving.getDeliveryDocuments().size() > 0);
    assertTrue(
        instructionForUpcReceiving
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());
    assertEquals(
        (int)
            instructionResponse
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getTotalReceivedQty(),
        358);

    assertSame(instructionResponse.getInstruction().getInstructionSetId(), 1L);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
            any(), any(), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcManagedConfig, times(3)).isSplitPalletEnabled();
    verify(instructionSetIdGenerator, times(1)).generateInstructionSetId();
  }

  @Test
  public void testCreateInstructionSplitPallet_FirstAtlasItem()
      throws IOException, ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(358L);
    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionSetIdGenerator.generateInstructionSetId()).thenReturn(1012L);
    doReturn(true).when(rdcManagedConfig).isSplitPalletEnabled();
    doReturn(1L).when(instructionSetIdGenerator).generateInstructionSetId();
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
                any(), any(), anyString()))
        .thenReturn(null);
    doReturn(getPrimeSlotFromSmartSlotting())
        .when(slottingServiceImpl)
        .getPrimeSlot(any(DeliveryDocumentLine.class), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(instructionResponse);
    InstructionRequest mockInstructionRequestWithDeliveryDocuments =
        MockInstructionRequest
            .getInstructionRequestWithDeliveryDocuments4BreakPackAtlasItemSplitPallet();
    mockInstructionRequestWithDeliveryDocuments.setReceivingType("SPLIT_PALLET_UPC");
    InstructionResponse instructionForUpcReceiving =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            mockInstructionRequestWithDeliveryDocuments, 358L, MockHttpHeaders.getHeaders());

    assertNotNull(instructionForUpcReceiving);
    assertNotNull(instructionForUpcReceiving.getInstruction());
    assertTrue(instructionForUpcReceiving.getDeliveryDocuments().size() > 0);
    assertTrue(
        instructionForUpcReceiving
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());
    assertEquals(
        (int)
            instructionResponse
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getTotalReceivedQty(),
        358);
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlot(),
        "A0001");

    assertSame(instructionResponse.getInstruction().getInstructionSetId(), 1L);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
            any(), any(), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcManagedConfig, times(4)).isSplitPalletEnabled();
    verify(instructionSetIdGenerator, times(1)).generateInstructionSetId();
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_create_instruction_split_pallet_throws_exception_for_mixed_item_types()
      throws IOException, ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    doReturn(true).when(rdcManagedConfig).isSplitPalletEnabled();
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
                any(), any(), anyString()))
        .thenReturn(null);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(358L));
    when(instructionPersisterService.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
            anyLong(), anyLong()))
        .thenReturn(getMockDeliveryDocumentsByDeliveryNumberInstructionSetId(true));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(instructionResponse);
    InstructionRequest mockInstructionRequestWithDeliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments4BreakPack();
    mockInstructionRequestWithDeliveryDocuments.setReceivingType("SPLIT_PALLET_UPC");
    mockInstructionRequestWithDeliveryDocuments.setInstructionSetId(1234L);
    rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
        mockInstructionRequestWithDeliveryDocuments, 10L, headers);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
            any(), any(), anyString());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcManagedConfig, times(2)).isSplitPalletEnabled();
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      test_create_instruction_split_pallet_throws_exception_for_mixed_po_numbers_only_for_atlas_items()
          throws IOException, ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();

    doReturn(true).when(rdcManagedConfig).isSplitPalletEnabled();
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
                any(), any(), anyString()))
        .thenReturn(null);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(358L));
    when(instructionPersisterService.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
            anyLong(), anyLong()))
        .thenReturn(getMockDeliveryDocumentsByDeliveryNumberInstructionSetId(false));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(instructionResponse);
    InstructionRequest mockInstructionRequestWithDeliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments4BreakPack();
    mockInstructionRequestWithDeliveryDocuments.setReceivingType("SPLIT_PALLET_UPC");
    mockInstructionRequestWithDeliveryDocuments.setInstructionSetId(1234L);
    rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
        mockInstructionRequestWithDeliveryDocuments, 10L, headers);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
            any(), any(), anyString());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcManagedConfig, times(2)).isSplitPalletEnabled();
  }

  @Test
  public void testCreateInstructionSplitPallet_primeSlotCompatibilityCheckForAtlasItems()
      throws IOException, ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(34323233L);
    ItemData itemData = new ItemData();
    itemData.setAtlasConvertedItem(true);
    itemData.setPrimeSlot("A1234");
    deliveryDocumentLine.setAdditionalInfo(itemData);
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    String existingSplitPalletInstructionDeliveryDocument = gson.toJson(deliveryDocument);

    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionSetIdGenerator.generateInstructionSetId()).thenReturn(1012L);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
                any(), any(), anyString(), anyLong()))
        .thenReturn(null);
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(slottingServiceImpl.getPrimeSlotForSplitPallet(
            any(DeliveryDocumentLine.class), anyList(), anyString(), any(HttpHeaders.class)))
        .thenReturn(getPrimeSlotCompatibilityResponseFromSmartSlotting());

    when(instructionPersisterService.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
            anyLong(), anyLong()))
        .thenReturn(Collections.singletonList(existingSplitPalletInstructionDeliveryDocument));
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(instructionResponse);
    InstructionRequest mockInstructionRequestWithDeliveryDocuments =
        MockInstructionRequest
            .getInstructionRequestWithDeliveryDocuments4BreakPackAtlasItemSplitPallet();
    mockInstructionRequestWithDeliveryDocuments.setReceivingType("SPLIT_PALLET_UPC");
    mockInstructionRequestWithDeliveryDocuments.setInstructionSetId(1L);

    InstructionResponse instructionForUpcReceiving =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            mockInstructionRequestWithDeliveryDocuments, 358L, headers);

    assertNotNull(instructionForUpcReceiving);
    assertNotNull(instructionForUpcReceiving.getInstruction());
    assertTrue(instructionForUpcReceiving.getDeliveryDocuments().size() > 0);
    assertTrue(
        instructionForUpcReceiving
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());
    assertEquals(
        (int)
            instructionResponse
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getTotalReceivedQty(),
        358);
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPrimeSlot(),
        "G3434");

    assertSame(instructionResponse.getInstruction().getInstructionSetId(), 1L);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
            any(), any(), anyString(), anyLong());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));

    verify(rdcManagedConfig, times(4)).isSplitPalletEnabled();
    verify(instructionSetIdGenerator, times(0)).generateInstructionSetId();
    verify(instructionPersisterService, times(0))
        .getSlotDetailsByDeliveryNumberAndInstructionSetId(anyString(), anyLong());
    verify(instructionPersisterService, times(2))
        .getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(anyLong(), anyLong());
    verify(slottingServiceImpl, times(1))
        .getPrimeSlotForSplitPallet(
            any(DeliveryDocumentLine.class), anyList(), anyString(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testCreateInstructionSplitPallet_primeSlotCompatibilityCheckForAtlasItems_ThrowsError()
          throws IOException, ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(34323233L);
    deliveryDocumentLine.setAdditionalInfo(new ItemData());
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    String existingSplitPalletInstructionDeliveryDocument = gson.toJson(deliveryDocument);

    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);
    when(instructionSetIdGenerator.generateInstructionSetId()).thenReturn(1012L);
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
                any(), any(), anyString(), anyLong()))
        .thenReturn(null);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(358L));
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                    "Invalid Slot ID"),
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                "Invalid Slot ID"))
        .when(slottingServiceImpl)
        .getPrimeSlotForSplitPallet(
            any(DeliveryDocumentLine.class), anyList(), anyString(), any(HttpHeaders.class));
    when(appConfig.getProblemTagTypesList())
        .thenReturn(Collections.singletonList(ReceivingConstants.OVG));
    when(appConfig.isProblemTagCheckEnabled()).thenReturn(true);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(instructionResponse);
    when(instructionPersisterService.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
            anyLong(), anyLong()))
        .thenReturn(Collections.singletonList(existingSplitPalletInstructionDeliveryDocument));

    InstructionRequest mockInstructionRequestWithDeliveryDocuments =
        MockInstructionRequest
            .getInstructionRequestWithDeliveryDocuments4BreakPackAtlasItemSplitPallet();
    mockInstructionRequestWithDeliveryDocuments.setReceivingType("SPLIT_PALLET_UPC");
    mockInstructionRequestWithDeliveryDocuments.setInstructionSetId(1L);

    rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
        mockInstructionRequestWithDeliveryDocuments, 10L, headers);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
            any(), any(), anyString(), anyLong());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(0)).saveInstruction(any(Instruction.class));

    verify(rdcManagedConfig, times(3)).isSplitPalletEnabled();
    verify(instructionSetIdGenerator, times(0)).generateInstructionSetId();
    verify(instructionPersisterService, times(0))
        .getSlotDetailsByDeliveryNumberAndInstructionSetId(anyString(), anyLong());
    verify(instructionPersisterService, times(1))
        .getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(anyLong(), anyLong());
    verify(slottingServiceImpl, times(1))
        .getPrimeSlotForSplitPallet(
            any(DeliveryDocumentLine.class), anyList(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void test_create_instruction_split_pallet_existing_instruction_set()
      throws IOException, ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doReturn(true).when(rdcManagedConfig).isSplitPalletEnabled();
    doReturn(1L).when(instructionSetIdGenerator).generateInstructionSetId();
    when(instructionPersisterService.fetchExistingInstructionIfexists(any())).thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
                any(), any(), anyString(), anyLong()))
        .thenReturn(null);
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(0);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    when(instructionPersisterService.getSlotDetailsByDeliveryNumberAndInstructionSetId(
            anyString(), anyLong()))
        .thenReturn(Arrays.asList("A130", "A140"));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(instructionResponse);

    InstructionRequest mockInstructionRequestWithDeliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments4BreakPack();
    mockInstructionRequestWithDeliveryDocuments.setReceivingType("SPLIT_PALLET_UPC");
    mockInstructionRequestWithDeliveryDocuments.setInstructionSetId(1L);

    InstructionResponse instructionForUpcReceiving =
        rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            mockInstructionRequestWithDeliveryDocuments, 358L, headers);

    assertNotNull(instructionForUpcReceiving);
    assertNotNull(instructionForUpcReceiving.getInstruction());
    assertTrue(instructionForUpcReceiving.getDeliveryDocuments().size() > 0);
    assertTrue(
        instructionForUpcReceiving
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());
    assertEquals(
        (int)
            instructionResponse
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getTotalReceivedQty(),
        358);

    assertSame(instructionResponse.getInstruction().getInstructionSetId(), 1L);

    verify(instructionPersisterService, times(1)).fetchExistingInstructionIfexists(any());
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
            any(), any(), anyString(), anyLong());
    verify(instructionPersisterService, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcManagedConfig, times(3)).isSplitPalletEnabled();
    verify(instructionSetIdGenerator, times(0)).generateInstructionSetId();
    verify(instructionPersisterService, times(1))
        .getSlotDetailsByDeliveryNumberAndInstructionSetId(anyString(), anyLong());
  }

  /**
   * This is a valid scenario when we add an item in the split pallet and instructionSetId will be
   * generatedv when we remove and add one more, we will be getting instructionSetId even though we
   * do not have any instruction on the split pallet this empty array check with stop
   * IndexOutOfBoundsException
   */
  @Test
  public void test_validatePrimeSlotCompatibility_no_slots_in_db() {
    doReturn(true).when(rdcManagedConfig).isSplitPalletEnabled();

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType("SPLIT_PALLET_UPC");
    mockInstructionRequest.setInstructionSetId(1L);
    mockInstructionRequest.setDeliveryNumber("12345");

    DeliveryDocumentLine mockSelectedDeliveryDocumentLine = new DeliveryDocumentLine();
    mockSelectedDeliveryDocumentLine.setItemNbr(9876L);
    ItemData mockAdditionalInfo = new ItemData();
    mockAdditionalInfo.setPrimeSlot("A120");
    mockSelectedDeliveryDocumentLine.setAdditionalInfo(mockAdditionalInfo);

    doReturn(Collections.emptyList())
        .when(instructionPersisterService)
        .getSlotDetailsByDeliveryNumberAndInstructionSetId(anyString(), anyLong());

    rdcInstructionUtils.validatePrimeSlotCompatibility(
        mockInstructionRequest, mockSelectedDeliveryDocumentLine);

    verify(instructionPersisterService, times(1))
        .getSlotDetailsByDeliveryNumberAndInstructionSetId(anyString(), anyLong());
  }

  @Test
  public void testValidateCreateInstructionForSplitPalletThrowsExceptionWhenItemIsNotBreakPack() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType(RdcReceivingType.SPLIT_PALLET_UPC.getReceivingType());
    mockInstructionRequest.setDeliveryNumber("1234");

    DeliveryDocumentLine mockSelectedDeliveryDocumentLine = new DeliveryDocumentLine();
    ItemData mockAdditionalInfo = new ItemData();
    mockAdditionalInfo.setPackTypeCode("C");
    mockAdditionalInfo.setPrimeSlot("A120");
    mockSelectedDeliveryDocumentLine.setAdditionalInfo(mockAdditionalInfo);

    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);

    try {
      rdcInstructionUtils.validateInstructionCreationForSplitPallet(
          mockInstructionRequest, mockSelectedDeliveryDocumentLine);
    } catch (ReceivingBadDataException excep) {
      assertNotNull(excep);
      assertSame(
          excep.getErrorCode(), ExceptionCodes.INSTR_CREATE_SPLIT_PALLET_NOT_BREAK_PACK_ITEM);
    }

    verify(rdcManagedConfig, times(1)).isSplitPalletEnabled();
  }

  @Test
  public void
      testValidateCreateInstructionForSplitPalletThrowsExceptionWhenAtlasItemIsBreakPackButSymbotic() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType(RdcReceivingType.SPLIT_PALLET_UPC.getReceivingType());
    mockInstructionRequest.setDeliveryNumber("1234");

    DeliveryDocumentLine mockSelectedDeliveryDocumentLine = new DeliveryDocumentLine();
    mockSelectedDeliveryDocumentLine.setItemNbr(456789L);
    ItemData mockAdditionalInfo = new ItemData();
    mockAdditionalInfo.setPackTypeCode("B");
    mockAdditionalInfo.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    mockAdditionalInfo.setAsrsAlignment("SYM2");
    mockSelectedDeliveryDocumentLine.setAdditionalInfo(mockAdditionalInfo);

    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);

    try {
      rdcInstructionUtils.validateCreateSplitPalletInstructionForSymItem(
          mockInstructionRequest, mockSelectedDeliveryDocumentLine);
    } catch (ReceivingBadDataException excep) {
      assertNotNull(excep);
      assertSame(
          excep.getErrorCode(), ExceptionCodes.INSTR_CREATE_SPLIT_PALLET_SYMBOTIC_BREAK_PACK_ITEM);
      assertSame(
          excep.getDescription(),
          ReceivingConstants.INSTR_CREATE_SPLIT_PALLET_SYMBOTIC_BREAK_PACK_ITEM);
    }

    verify(rdcManagedConfig, times(1)).isSplitPalletEnabled();
  }

  @Test
  public void
      testValidateCreateInstructionForSplitPalletThrowsExceptionWhenNonAtlasItemIsBreakPackButSymbotic() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType(RdcReceivingType.SPLIT_PALLET_UPC.getReceivingType());
    mockInstructionRequest.setDeliveryNumber("1234");

    DeliveryDocumentLine mockSelectedDeliveryDocumentLine = new DeliveryDocumentLine();
    mockSelectedDeliveryDocumentLine.setItemNbr(456789L);
    ItemData mockAdditionalInfo = new ItemData();
    mockAdditionalInfo.setPackTypeCode("B");
    mockAdditionalInfo.setSymEligibleIndicator("A");
    mockSelectedDeliveryDocumentLine.setAdditionalInfo(mockAdditionalInfo);

    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);

    try {
      rdcInstructionUtils.validateCreateSplitPalletInstructionForSymItem(
          mockInstructionRequest, mockSelectedDeliveryDocumentLine);
    } catch (ReceivingBadDataException excep) {
      assertNotNull(excep);
      assertSame(
          excep.getErrorCode(), ExceptionCodes.INSTR_CREATE_SPLIT_PALLET_SYMBOTIC_BREAK_PACK_ITEM);
      assertSame(
          excep.getDescription(),
          ReceivingConstants.INSTR_CREATE_SPLIT_PALLET_SYMBOTIC_BREAK_PACK_ITEM);
    }

    verify(rdcManagedConfig, times(1)).isSplitPalletEnabled();
  }

  @Test
  public void
      testValidateCreateInstructionForSplitPalletThrowsExceptionWhenNonAtlasItemIsBreakPackButSymboticItem() {
    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType(RdcReceivingType.SPLIT_PALLET_UPC.getReceivingType());
    mockInstructionRequest.setDeliveryNumber("1234");

    DeliveryDocumentLine mockSelectedDeliveryDocumentLine = new DeliveryDocumentLine();
    mockSelectedDeliveryDocumentLine.setItemNbr(456789L);
    ItemData mockAdditionalInfo = new ItemData();
    mockAdditionalInfo.setPackTypeCode("B");
    mockAdditionalInfo.setSymEligibleIndicator("M");
    mockSelectedDeliveryDocumentLine.setAdditionalInfo(mockAdditionalInfo);

    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);

    try {
      rdcInstructionUtils.validateCreateSplitPalletInstructionForSymItem(
          mockInstructionRequest, mockSelectedDeliveryDocumentLine);
    } catch (ReceivingBadDataException excep) {
      assertNotNull(excep);
      assertSame(
          excep.getErrorCode(), ExceptionCodes.INSTR_CREATE_SPLIT_PALLET_SYMBOTIC_BREAK_PACK_ITEM);
      assertSame(
          excep.getDescription(),
          ReceivingConstants.INSTR_CREATE_SPLIT_PALLET_SYMBOTIC_BREAK_PACK_ITEM);
    }

    verify(rdcManagedConfig, times(1)).isSplitPalletEnabled();
  }

  @Test
  public void testValidateCreateInstructionForSplitPalletThrowsExceptionForMixedItemTypes()
      throws IOException {
    InstructionRequest mockInstructionRequest = getMockInstructionRequest();
    mockInstructionRequest.setReceivingType(RdcReceivingType.SPLIT_PALLET_UPC.getReceivingType());
    mockInstructionRequest.setInstructionSetId(1234L);

    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments
            .getDeliveryDocumentForReceiveInstructionFromInstructionAtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(false);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode(RdcConstants.BREAK_PACK_TYPE_CODE);

    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);
    when(instructionPersisterService.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
            anyLong(), anyLong()))
        .thenReturn(getMockDeliveryDocumentsByDeliveryNumberInstructionSetId(false));

    try {
      rdcInstructionUtils.validateInstructionCreationForSplitPallet(
          mockInstructionRequest, deliveryDocumentLine);
    } catch (ReceivingBadDataException rbde) {
      assertNotNull(rbde);
      assertSame(
          rbde.getErrorCode(), ExceptionCodes.INSTR_CREATE_SPLIT_PALLET_DIFFERENT_ITEM_TYPES_400);
    }

    verify(rdcManagedConfig, times(1)).isSplitPalletEnabled();
    verify(instructionPersisterService, times(1))
        .getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(anyLong(), anyLong());
  }

  @Test
  public void testValidateCreateInstructionForSplitPalletThrowsExceptionWhenPrimSlotIsDifferent()
      throws IOException {
    InstructionRequest mockInstructionRequest = getMockInstructionRequest();
    mockInstructionRequest.setReceivingType(RdcReceivingType.SPLIT_PALLET_UPC.getReceivingType());
    mockInstructionRequest.setInstructionSetId(1234L);

    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments
            .getDeliveryDocumentForReceiveInstructionFromInstructionAtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(false);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode(RdcConstants.BREAK_PACK_TYPE_CODE);
    deliveryDocumentLine.getAdditionalInfo().setPrimeSlot("C1201");

    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);
    when(instructionPersisterService.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
            anyLong(), anyLong()))
        .thenReturn(getMockDeliveryDocumentsByDeliveryNumberInstructionSetId(false));
    doReturn(Collections.singletonList("B1201"))
        .when(instructionPersisterService)
        .getSlotDetailsByDeliveryNumberAndInstructionSetId(anyString(), anyLong());

    try {
      rdcInstructionUtils.validateInstructionCreationForSplitPallet(
          mockInstructionRequest, deliveryDocumentLine);
    } catch (ReceivingBadDataException rbde) {
      assertNotNull(rbde);
      assertSame(rbde.getErrorCode(), ExceptionCodes.INSTR_CREATE_SPLIT_PALLET_PRIMES_COMPATIBLE);
    }

    verify(rdcManagedConfig, times(1)).isSplitPalletEnabled();
    verify(instructionPersisterService, times(1))
        .getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(anyLong(), anyLong());
    verify(instructionPersisterService, times(1))
        .getSlotDetailsByDeliveryNumberAndInstructionSetId(anyString(), anyLong());
  }

  @Test
  public void testValidateCreateInstructionForSplitPalletIsSuccess() throws IOException {
    InstructionRequest mockInstructionRequest = getMockInstructionRequest();
    mockInstructionRequest.setReceivingType(RdcReceivingType.SPLIT_PALLET_UPC.getReceivingType());
    mockInstructionRequest.setInstructionSetId(1234L);

    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments
            .getDeliveryDocumentForReceiveInstructionFromInstructionAtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(false);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode(RdcConstants.BREAK_PACK_TYPE_CODE);
    deliveryDocumentLine.getAdditionalInfo().setPrimeSlot("B1201");

    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);
    when(instructionPersisterService.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
            anyLong(), anyLong()))
        .thenReturn(getMockDeliveryDocumentsByDeliveryNumberInstructionSetId(false));
    doReturn(Collections.singletonList("B1201"))
        .when(instructionPersisterService)
        .getSlotDetailsByDeliveryNumberAndInstructionSetId(anyString(), anyLong());

    try {
      rdcInstructionUtils.validateInstructionCreationForSplitPallet(
          mockInstructionRequest, deliveryDocumentLine);
    } catch (ReceivingBadDataException rbde) {
      assertNull(rbde);
    }

    verify(rdcManagedConfig, times(1)).isSplitPalletEnabled();
    verify(instructionPersisterService, times(1))
        .getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(anyLong(), anyLong());
    verify(instructionPersisterService, times(1))
        .getSlotDetailsByDeliveryNumberAndInstructionSetId(anyString(), anyLong());
  }

  @Test
  public void testValidateCreateInstructionForSplitPalletIsSuccessAtlasItems() throws IOException {
    InstructionRequest mockInstructionRequest = getMockInstructionRequest();
    mockInstructionRequest.setReceivingType(RdcReceivingType.SPLIT_PALLET_UPC.getReceivingType());
    mockInstructionRequest.setInstructionSetId(1234L);

    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments
            .getDeliveryDocumentForReceiveInstructionFromInstructionAtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode(RdcConstants.BREAK_PACK_TYPE_CODE);
    deliveryDocumentLine.getAdditionalInfo().setPrimeSlot("B1201");

    when(rdcManagedConfig.isSplitPalletEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(instructionPersisterService.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
            anyLong(), anyLong()))
        .thenReturn(getMockDeliveryDocumentsByDeliveryNumberInstructionSetId(true));

    try {
      rdcInstructionUtils.validateInstructionCreationForSplitPallet(
          mockInstructionRequest, deliveryDocumentLine);
    } catch (ReceivingBadDataException rbde) {
      assertNull(rbde);
    }

    verify(rdcManagedConfig, times(1)).isSplitPalletEnabled();
    verify(instructionPersisterService, times(1))
        .getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(anyLong(), anyLong());
    verify(instructionPersisterService, times(0))
        .getSlotDetailsByDeliveryNumberAndInstructionSetId(anyString(), anyLong());
  }

  @Test
  public void testPopulateOpenQuantityInDeliveryDocumentsIsSuccess() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(
            MockReceivedQtyRespFromRds.getReceivedQtyMapByDeliveryDocument(deliveryDocumentList));

    rdcInstructionUtils.populateOpenAndReceivedQtyInDeliveryDocuments(
        deliveryDocumentList, headers, upcNumber);

    deliveryDocumentList.forEach(
        document -> {
          document
              .getDeliveryDocumentLines()
              .forEach(
                  documentLine -> {
                    assertNotNull(documentLine.getOpenQty());
                    assertNotNull(documentLine.getTotalReceivedQty());
                  });
        });
  }

  @Test
  public void testPopulateOpenQuantityInDeliveryDocumentsWhenRDSReturnsEmptyResponse()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(
            MockReceivedQtyRespFromRds
                .getEmptyReceivedAndErrorQtyResponseFromRdsForSinglePoAndPoLine());

    rdcInstructionUtils.populateOpenAndReceivedQtyInDeliveryDocuments(
        deliveryDocumentList, headers, upcNumber);
    assertTrue(deliveryDocumentList.size() > 0);
    assertEquals(
        (int) deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).getTotalOrderQty(),
        378);
  }

  @Test
  public void testCheckAllSSTKPoFulfilledIsSuccess() throws IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO();
    List<DeliveryDocument> filteredSSTKDocument =
        rdcInstructionUtils.filterSSTKDeliveryDocuments(deliveryDocumentList);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    String key =
        filteredSSTKDocument.get(0).getPurchaseReferenceNumber()
            + ReceivingConstants.DELIM_DASH
            + filteredSSTKDocument
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getPurchaseReferenceLineNumber();
    receivedQtyMap.put(key, 340L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(null);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);

    List<DeliveryDocument> deliveryDocuments =
        rdcInstructionUtils.checkAllSSTKPoFulfilled(
            filteredSSTKDocument, instructionRequest, receivedQuantityResponseFromRDS);
    assertNotNull(deliveryDocuments);
    assertEquals(deliveryDocuments.size(), 1);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testCheckAllSSTKPoFulfilledThrowsExceptionForDA() throws IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO();
    List<DeliveryDocument> filteredSSTKDocument =
        rdcInstructionUtils.filterSSTKDeliveryDocuments(deliveryDocumentList);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    String key =
        filteredSSTKDocument.get(0).getPurchaseReferenceNumber()
            + ReceivingConstants.DELIM_DASH
            + filteredSSTKDocument
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getPurchaseReferenceLineNumber();
    receivedQtyMap.put(key, 378L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(null);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);

    rdcInstructionUtils.checkAllSSTKPoFulfilled(
        filteredSSTKDocument, instructionRequest, receivedQuantityResponseFromRDS);
  }

  @Test
  public void testValidateOverageForReceivedQty_forNonAtlasConvertedItems_notExceedsMaxReceiveQty()
      throws IOException, ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    int quantityToReceive = 10;
    Instruction instruction = new Instruction();
    instruction.setProblemTagId(null);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setMaxReceiveQty(20);
    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class)))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    rdcInstructionUtils.validateOverage(
        deliveryDocumentList.get(0).getDeliveryDocumentLines(),
        quantityToReceive,
        instruction,
        httpHeaders);
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class));
  }

  @Test
  public void testValidateOverageForReceivedQty_forAtlasConvertedItems_notExceedsMaxReceiveQty()
      throws IOException, ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    int quantityToReceive = 10;
    Instruction instruction = new Instruction();
    instruction.setProblemTagId(null);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setMaxReceiveQty(20);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), any(Integer.class)))
        .thenReturn(10L);
    rdcInstructionUtils.validateOverage(
        deliveryDocumentList.get(0).getDeliveryDocumentLines(),
        quantityToReceive,
        instruction,
        httpHeaders);
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), any(Integer.class));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Reached maximum receivable quantity threshold for this PO 8458708162 and POLine 1 combination, Please cancel/Finish this pallet.")
  public void testValidateOverageForReceivedQty_forNonAtlasConvertedItems_hasReachedMaxReceiveQty()
      throws IOException, ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    int quantityToReceive = 10;
    Instruction instruction = new Instruction();
    instruction.setProblemTagId(null);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setMaxReceiveQty(20);
    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class)))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(200L));
    rdcInstructionUtils.validateOverage(
        deliveryDocumentList.get(0).getDeliveryDocumentLines(),
        quantityToReceive,
        instruction,
        httpHeaders);
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Total receive quantity exceeds maximum receivable quantity threshold for this PO: 8458708162 and POLine: 1 combination, Please cancel/finish this pallet.")
  public void testValidateOverageForReceivedQty_forNonAtlasConvertedItems_hasExceededMaxReceiveQty()
      throws IOException, ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    int quantityToReceive = 10;
    Instruction instruction = new Instruction();
    instruction.setProblemTagId(null);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setMaxReceiveQty(20);
    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class)))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(210L));
    rdcInstructionUtils.validateOverage(
        deliveryDocumentList.get(0).getDeliveryDocumentLines(),
        quantityToReceive,
        instruction,
        httpHeaders);
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testValidateOverageForReceivedQty_forProblemAndAtlasConvertedItem_notExceedsMaxReceiveQty()
          throws IOException, ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    int quantityToReceive = 10;
    Instruction instruction = new Instruction();
    instruction.setProblemTagId(problemTagId);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setMaxReceiveQty(20);

    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), any(Integer.class)))
        .thenReturn(10L);
    doNothing()
        .when(rdcProblemUtils)
        .reportErrorForProblemReceiving(
            anyString(), nullable(String.class), anyString(), anyInt(), any(HttpHeaders.class));

    rdcInstructionUtils.validateOverage(
        deliveryDocumentList.get(0).getDeliveryDocumentLines(),
        quantityToReceive,
        instruction,
        httpHeaders);

    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), any(Integer.class));
    verify(rdcProblemUtils, times(0))
        .reportErrorForProblemReceiving(
            anyString(), nullable(String.class), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Received maximum allowable quantity threshold for problem label: PTAG1, Please check with your supervisor")
  public void
      testValidateOverageForReceivedQty_forProblemAndNonAtlasConvertedItems_hasExceededMaxReceiveQty()
          throws IOException, ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    int quantityToReceive = 10;
    Instruction instruction = new Instruction();
    instruction.setProblemTagId(problemTagId);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setMaxReceiveQty(20);

    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class)))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(210L));
    doThrow(
            new ReceivingException(
                String.format(ReceivingException.MAX_QUANTITY_REACHED_FOR_PROBLEM, problemTagId),
                BAD_REQUEST,
                ReceivingException.CREATE_OR_RECEIVE_INSTRUCTION_ERROR_CODE,
                ReceivingException.MAX_QUANTITY_REACHED_ERROR_HEADER_FOR_PROBLEM))
        .when(rdcProblemUtils)
        .reportErrorForProblemReceiving(
            anyString(), nullable(String.class), anyString(), anyInt(), any(HttpHeaders.class));

    rdcInstructionUtils.validateOverage(
        deliveryDocumentList.get(0).getDeliveryDocumentLines(),
        quantityToReceive,
        instruction,
        httpHeaders);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class));
    verify(rdcProblemUtils, times(1))
        .reportErrorForProblemReceiving(
            anyString(), nullable(String.class), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void testAtlasConvertItemReturnsFalseForAllItems() throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        assertFalse(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
      }
    }
  }

  @Test
  public void testAtlasConvertItemReturnsTrueForAllItems()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(572730927L);
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(572730927L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
      }
    }
  }

  @Test
  public void testAtlasConvertItemReturnsTrueForAllSSTKItems()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
      }
    }
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
  }

  @Test
  public void testAtlasConvertItemReturnsTrueForAllDAItems_DaItemConversionDisabled()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        assertFalse(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
      }
    }
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
  }

  @Test
  public void testAtlasConvertItemReturnsTrueForAllDAItems_DaItemConversionEnabled()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
      }
    }
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    verify(rdcManagedConfig, times(2)).getDaAtlasItemEnabledPackHandlingCode();
  }

  @Test
  public void testAtlasConvertItemReturnsTrueForMixedPOs_BothDaAndSstkAtlasItems()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO();
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));

    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType())) {
          assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
        } else if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType())) {
          assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
        }
      }
    }
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
  }

  @Test
  public void
      testAtlasConvertItemReturnsTrueForMixedPOs_BothDaAndSstkAtlasItems_SSTKItemAvailableInItemConfig_DAItemNotAvailableInItemConfig()
          throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO_differentItemNumbers();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));

    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType())) {
          assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
        } else if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType())) {
          assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
        }
      }
    }
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
  }

  @Test
  public void
      testAtlasConvertItemReturnsTrueForMixedPOs_BothDaAndSstkAtlasItems_BothSSTKAndDaItemsAvailableInItemConfig()
          throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO_differentItemNumbers();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
      }
    }
    ItemConfigDetails itemConfigDetails1 =
        ItemConfigDetails.builder()
            .createdDateTime(createTs)
            .desc(itemDesc)
            .item("3804890")
            .build();
    ItemConfigDetails itemConfigDetails2 =
        ItemConfigDetails.builder()
            .createdDateTime(createTs)
            .desc(itemDesc)
            .item("3804891")
            .build();
    List<ItemConfigDetails> itemConfigDetailsList = new ArrayList<>();
    itemConfigDetailsList.add(itemConfigDetails1);
    itemConfigDetailsList.add(itemConfigDetails2);
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(itemConfigDetailsList);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));

    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType())) {
          assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
        } else if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType())) {
          assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
        }
      }
    }
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
  }

  @Test
  public void testAtlasConvertItemReturnsTrueForMixedPOs_SSTKIsAtlasItemDaNonAtlasItem()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("BC");
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType())) {
          assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
        } else if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
            deliveryDocumentLine.getPurchaseRefType())) {
          assertFalse(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
        }
      }
    }
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testAtlasConvertItemReturnsException()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(572730927L);
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(572730927L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenThrow(
            new ItemConfigRestApiClientException(
                "error-123", HttpStatus.SERVICE_UNAVAILABLE, "service down"));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
  }

  @Test
  public void testFetchExistingOpenInstructionForProblemTagIdReturnsInstruction()
      throws IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("test-message-id");
    instructionRequest.setProblemTagId("PTAG123");
    instructionRequest.setUpcNumber("UPC123");
    instructionRequest.setDeliveryNumber("1234567");

    when(instructionPersisterService.fetchExistingInstructionIfexists(
            any(InstructionRequest.class)))
        .thenReturn(null);
    when(instructionPersisterService
            .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId(
                any(InstructionRequest.class), anyString(), anyString()))
        .thenReturn(getMockProblemInstruction());

    Instruction existingInstruction =
        rdcInstructionUtils.fetchExistingInstruction(null, instructionRequest, headers);

    assertNotNull(existingInstruction);

    verify(instructionPersisterService, times(1))
        .fetchExistingInstructionIfexists(any(InstructionRequest.class));
  }

  @Test
  public void testGetProjectedReceiveQtyByTiHiForProblemInstruction_FIXIT() throws IOException {
    Instruction instruction = getMockProblemInstruction();
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setOpenQty(5);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.PROBLEM_SERVICE,
            ProblemService.class))
        .thenReturn(rdcFixitProblemService);
    when(rdcFixitProblemService.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(MockProblemResponse.getMockProblemLabel());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(10L);
    int projectedReceiveQty =
        rdcInstructionUtils.getProjectedReceiveQtyByTiHi(deliveryDocumentLine, 0L, instruction);

    assertSame(projectedReceiveQty, 5);
    assertNotNull(deliveryDocumentLine);
    assertEquals(deliveryDocumentLine.getTotalReceivedQty().intValue(), 10);
    assertEquals(deliveryDocumentLine.getTotalOrderQty().intValue(), 335);
    assertEquals(deliveryDocumentLine.getOpenQty().intValue(), 5);

    verify(rdcFixitProblemService, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
  }

  @Test
  public void testGetProjectedReceiveQtyByTiHiForProblemInstruction_FIT() throws IOException {
    Instruction instruction = getMockProblemInstruction();
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setOpenQty(5);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.PROBLEM_SERVICE,
            ProblemService.class))
        .thenReturn(problemService);
    when(problemService.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(MockProblemResponse.getMockProblemLabel());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(10L);
    int projectedReceiveQty =
        rdcInstructionUtils.getProjectedReceiveQtyByTiHi(deliveryDocumentLine, 0L, instruction);

    assertSame(projectedReceiveQty, 5);
    assertNotNull(deliveryDocumentLine);
    assertEquals(deliveryDocumentLine.getTotalReceivedQty().intValue(), 10);
    assertEquals(deliveryDocumentLine.getTotalOrderQty().intValue(), 335);
    assertEquals(deliveryDocumentLine.getOpenQty().intValue(), 5);

    verify(problemService, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
  }

  @Test
  public void testUpdateInstructionIsSuccess() throws ReceivingException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockReceivedInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    Instruction instruction = rdcInstructionUtils.updateInstructionQuantity(123L, 20);

    assertNotNull(instruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());

    ArgumentCaptor<Instruction> instructionArgumentCaptor =
        ArgumentCaptor.forClass(Instruction.class);
    verify(instructionPersisterService).saveInstruction(instructionArgumentCaptor.capture());

    assertSame(instructionArgumentCaptor.getValue().getReceivedQuantity(), 20);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testUpdateInstructionReturnsNullWhenInstructionDoesntExists()
      throws ReceivingException {
    doThrow(
            new ReceivingException(
                ReceivingException.INSTRUCTION_NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR))
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    rdcInstructionUtils.updateInstructionQuantity(123L, 20);
  }

  @Test
  public void testNewItemDoesNothingWhenItemAlreadyExists_FTSConfigEnabled() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.FTS_ITEM_CHECK_ENABLED);
    deliveryDocumentLine.setNewItem(false);

    try {
      rdcInstructionUtils.isNewItem(deliveryDocumentLine);
    } catch (ReceivingInternalException excep) {
      assertNull(excep);
    }
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.FTS_ITEM_CHECK_ENABLED,
            false);
  }

  @Test
  public void testNewItemThrowsExceptionWhenItemIsNew_FTSConfigEnabled() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.FTS_ITEM_CHECK_ENABLED);
    deliveryDocumentLine.setNewItem(true);
    try {
      rdcInstructionUtils.isNewItem(deliveryDocumentLine);
    } catch (ReceivingBadDataException excep) {
      assertNotNull(excep);
      assertSame(excep.getErrorCode(), ExceptionCodes.NEW_ITEM);
      assertTrue(
          excep
              .getDescription()
              .equals(
                  String.format(
                      ReceivingException.NEW_ITEM_ERROR_MSG, deliveryDocumentLine.getItemNbr())));
    }
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.FTS_ITEM_CHECK_ENABLED,
            false);
  }

  @Test
  public void testNewItemThrowsExceptionWhenItemIsNew_FTSConfigDisabled() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.FTS_ITEM_CHECK_ENABLED);
    deliveryDocumentLine.setNewItem(true);

    try {
      rdcInstructionUtils.isNewItem(deliveryDocumentLine);
    } catch (ReceivingInternalException excep) {
      assertNull(excep);
    }
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.FTS_ITEM_CHECK_ENABLED,
            false);
  }

  @Test
  public void testCheckIfContainerAlreadyReceived() throws IOException {
    doReturn(1).when(rdcContainerUtils).receivedContainerQuantityBySSCC(anyString());
    doReturn(true).when(rdcManagedConfig).isAsnReceivingEnabled();
    try {
      rdcInstructionUtils.checkIfContainerAlreadyReceived(getSSCCInstructionRequest());
    } catch (ReceivingBadDataException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.SSCC_RECEIVED_ALREADY);
    }
    verify(rdcContainerUtils, times(1)).receivedContainerQuantityBySSCC(anyString());
  }

  @Test
  public void testValidateItemHandlingMethodIsSuccess() {
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("B");
    itemData.setHandlingCode("C");
    itemData.setItemHandlingMethod("BC");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(1234567L);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    try {
      rdcInstructionUtils.validateItemHandlingMethod(deliveryDocumentLine);
    } catch (ReceivingBadDataException exception) {
      assertNull(exception);
    }
  }

  @Test
  public void
      testValidateItemHandlingMethodThrowsExceptionForInvalidPackTypeOrHandlingCodeCombination() {
    ItemData itemData = new ItemData();
    itemData.setItemHandlingMethod("AB");
    itemData.setHandlingCode("A");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(1234567L);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    try {
      rdcInstructionUtils.validateItemHandlingMethod(deliveryDocumentLine);
    } catch (ReceivingBadDataException exception) {
      assertNotNull(exception);
      assertSame(exception.getErrorCode(), ExceptionCodes.INVALID_ITEM_HANDLING_METHOD);
    }
  }

  @Test
  public void testValidateItemHandlingMethodThrowsExceptionWhenItemHandlingMethodIsInvalid() {
    ItemData itemData = new ItemData();
    itemData.setItemHandlingMethod(ReceivingUtils.INVALID_HANDLING_METHOD_OR_PACK_TYPE);
    itemData.setItemHandlingMethod("AB");
    itemData.setHandlingCode("A");
    itemData.setItemHandlingMethod(INVALID_HANDLING_METHOD_OR_PACK_TYPE);
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(1234567L);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    try {
      rdcInstructionUtils.validateItemHandlingMethod(deliveryDocumentLine);
    } catch (ReceivingBadDataException exception) {
      assertNotNull(exception);
      assertSame(exception.getErrorCode(), ExceptionCodes.INVALID_ITEM_HANDLING_METHOD);
    }
  }

  @Test
  public void testValidateItemHandlingMethodThrowsExceptionWhenItemHandlingMethodIsInvalidForDA()
      throws IOException {
    ItemData itemData = new ItemData();
    itemData.setItemHandlingMethod("AB");
    itemData.setHandlingCode("A");
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine.setItemNbr(1234567L);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    try {
      rdcInstructionUtils.validateItemHandlingMethod(deliveryDocumentLine);
    } catch (ReceivingBadDataException exception) {
      assertNotNull(exception);
      assertSame(exception.getErrorCode(), ExceptionCodes.INVALID_ITEM_HANDLING_METHOD);
    }
  }

  @Test
  public void testValidateItemHandlingMethodThrowExceptionWhenInvalidHandlingMethodReturnedForDA()
      throws IOException {
    ItemData itemData = new ItemData();
    itemData.setItemHandlingMethod(INVALID_HANDLING_METHOD_OR_PACK_TYPE);
    itemData.setHandlingCode("C");
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine.setItemNbr(1234567L);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    try {
      rdcInstructionUtils.validateItemHandlingMethod(deliveryDocumentLine);
    } catch (ReceivingBadDataException exception) {
      assertNotNull(exception);
      assertSame(exception.getErrorCode(), ExceptionCodes.INVALID_ITEM_HANDLING_METHOD);
    }
  }

  @Test
  public void testValidateItemHandlingMethodDoNotThrowExceptionWhenItemHandlingMethodIsValidForDA()
      throws IOException {
    ItemData itemData = new ItemData();
    itemData.setItemHandlingMethod("Casepack Conveyable");
    itemData.setHandlingCode("C");
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine.setItemNbr(1234567L);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    try {
      rdcInstructionUtils.validateItemHandlingMethod(deliveryDocumentLine);
    } catch (ReceivingBadDataException exception) {
      assertNull(exception);
    }
  }

  @Test
  public void testIsAtlasConvertedInstruction_happy_path() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    Instruction instruction = new Instruction();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocuments.get(0)));
    boolean response = rdcInstructionUtils.isAtlasConvertedInstruction(instruction);
    assertTrue(response);
  }

  @Test
  public void testIsAtlasConvertedInstruction_false() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    Instruction instruction = new Instruction();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocuments.get(0)));
    boolean response = rdcInstructionUtils.isAtlasConvertedInstruction(instruction);
    assertFalse(response);
  }

  @Test
  public void testIsSameUpcReturnsTrue() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    String scannedUpc = deliveryDocumentLine.getCaseUpc();
    assertTrue(rdcInstructionUtils.isSameUpc(scannedUpc, deliveryDocumentLine));
  }

  @Test
  public void testIsSameUpcReturnsFalse() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    String scannedUpc = "Upc123";
    assertFalse(rdcInstructionUtils.isSameUpc(scannedUpc, deliveryDocumentLine));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "GLS-RCV-MULTI-INST-400")
  public void testCheckIfInstructionExistsWithSsccAndValidateInstruction_InstructionExistsDiffUser()
      throws IOException {
    when(instructionPersisterService.checkIfInstructionExistsWithSscc(
            any(InstructionRequest.class)))
        .thenReturn(Arrays.asList(getInstruction()));
    rdcInstructionUtils.checkIfInstructionExistsWithSsccAndValidateInstruction(
        getSSCCInstructionRequest(), headers);
  }

  @Test
  public void testCheckIfInstructionExistsWithSsccAndValidateInstruction_InstructionExists()
      throws IOException {
    Instruction existingInstruction = getInstructionWithSscc();
    existingInstruction.setLastChangeUserId("sysadmin");

    InstructionRequest request = MockInstructionRequest.getSSCCInstructionRequest();
    when(instructionPersisterService.checkIfInstructionExistsWithSscc(
            any(InstructionRequest.class)))
        .thenReturn(Arrays.asList(existingInstruction));
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(20);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(1L));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    InstructionResponse response =
        rdcInstructionUtils.checkIfInstructionExistsWithSsccAndValidateInstruction(
            request, headers);
    assertNotNull(response);
  }

  @Test
  public void testCheckIfInstructionExistsWithSsccAndValidateInstruction_InstructionDoesNotExists()
      throws IOException {
    when(instructionPersisterService.checkIfInstructionExistsWithSscc(
            any(InstructionRequest.class)))
        .thenReturn(null);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    InstructionResponse response =
        rdcInstructionUtils.checkIfInstructionExistsWithSsccAndValidateInstruction(
            MockInstructionRequest.getSSCCInstructionRequest(), headers);
    assertNull(response);
    verify(instructionPersisterService, times(1)).checkIfInstructionExistsWithSscc(any());
    verify(rdcContainerUtils, times(1)).receivedContainerQuantityBySSCC(anyString());
  }

  @Test
  public void
      testCheckIfInstructionExistsWithSsccAndValidateInstruction_InstructionDoesNotExists_ItemNotReceived()
          throws IOException {
    when(instructionPersisterService.checkIfInstructionExistsWithSscc(
            any(InstructionRequest.class)))
        .thenReturn(null);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);

    InstructionRequest request = MockInstructionRequest.getSSCCInstructionRequest();
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    request.setDeliveryDocuments(deliveryDocuments);

    InstructionResponse response =
        rdcInstructionUtils.checkIfInstructionExistsWithSsccAndValidateInstruction(
            request, headers);
    assertNull(response);
    verify(instructionPersisterService, times(1)).checkIfInstructionExistsWithSscc(any());
    verify(rdcContainerUtils, times(1)).isContainerReceivedBySSCCAndItem(anyString(), anyLong());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "You cannot receive label 00000000605388022945 as it is already received. Please scan UPC to receive the remaining freight")
  public void
      testCheckIfInstructionExistsWithSsccAndValidateInstruction_InstructionDoesNotExists_ItemReceivedAlready()
          throws IOException {
    when(instructionPersisterService.checkIfInstructionExistsWithSscc(
            any(InstructionRequest.class)))
        .thenReturn(null);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    when(rdcContainerUtils.isContainerReceivedBySSCCAndItem(anyString(), anyLong()))
        .thenReturn(true);

    InstructionRequest request = MockInstructionRequest.getSSCCInstructionRequest();
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    request.setDeliveryDocuments(deliveryDocuments);

    InstructionResponse response =
        rdcInstructionUtils.checkIfInstructionExistsWithSsccAndValidateInstruction(
            request, headers);
    assertNull(response);
    verify(instructionPersisterService, times(1)).checkIfInstructionExistsWithSscc(any());
    verify(rdcContainerUtils, times(1)).isContainerReceivedBySSCCAndItem(anyString(), anyLong());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "You cannot receive label 00000000605388022945 as it is already received. Please scan UPC to receive the remaining freight")
  public void
      testCheckIfInstructionExistsWithSsccAndValidateInstruction_InstructionDoesNotExistsButReceivedWithSsccAlready()
          throws IOException {
    when(instructionPersisterService.checkIfInstructionExistsWithSscc(
            any(InstructionRequest.class)))
        .thenReturn(null);
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    when(rdcContainerUtils.receivedContainerQuantityBySSCC(anyString())).thenReturn(10);
    InstructionResponse response =
        rdcInstructionUtils.checkIfInstructionExistsWithSsccAndValidateInstruction(
            MockInstructionRequest.getSSCCInstructionRequest(), headers);
    assertNull(response);
    verify(instructionPersisterService, times(1)).checkIfInstructionExistsWithSscc(any());
    verify(rdcContainerUtils, times(1)).receivedContainerQuantityBySSCC(anyString());
  }

  @Test
  public void
      testCheckIfInstructionExistsWithSsccAndValidateInstruction_InstructionExistsWithItemAndSscc()
          throws IOException {
    Instruction existingInstruction = getInstructionWithSscc();
    existingInstruction.setLastChangeUserId("sysadmin");
    when(instructionPersisterService.checkIfInstructionExistsWithSscc(
            any(InstructionRequest.class)))
        .thenReturn(Arrays.asList(existingInstruction));
    InstructionRequest request = MockInstructionRequest.getSSCCInstructionRequest();
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setItemNbr(557449570L);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceNumber("8458708162");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPurchaseReferenceLineNumber(1);
    request.setDeliveryDocuments(deliveryDocuments);
    when(instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                anyLong(), anyString(), anyInt()))
        .thenReturn(20);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            true))
        .thenReturn(false);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(1L));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    InstructionResponse response =
        rdcInstructionUtils.checkIfInstructionExistsWithSsccAndValidateInstruction(
            request, headers);
    assertNotNull(response);
  }

  @Test
  public void
      testCheckIfInstructionExistsWithSsccAndValidateInstruction_InstructionDoesNotExistsWithItemAndSscc()
          throws IOException {
    when(instructionPersisterService.checkIfInstructionExistsWithSscc(
            any(InstructionRequest.class)))
        .thenReturn(Arrays.asList(getInstruction()));
    InstructionRequest request = MockInstructionRequest.getSSCCInstructionRequest();
    request.setDeliveryDocuments(MockDeliveryDocuments.getDeliveryDocumentsForSSTK());

    InstructionResponse response =
        rdcInstructionUtils.checkIfInstructionExistsWithSsccAndValidateInstruction(
            request, headers);
    assertNull(response);
  }

  @Test
  public void testIsAtlasItemSymEligible_happy_path() throws IOException {
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(Arrays.asList(ReceivingConstants.SYM_BRKPK_ASRS_VALUE));
    boolean response =
        rdcInstructionUtils.isAtlasItemSymEligible(
            MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity()
                .getDeliveryDocumentLines()
                .get(0));
    assertTrue(response);
  }

  @Test
  public void testIsAtlasItemSymEligible_false() throws IOException {
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(Arrays.asList(ReceivingConstants.PTL_ASRS_VALUE));
    boolean response =
        rdcInstructionUtils.isAtlasItemSymEligible(
            MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity()
                .getDeliveryDocumentLines()
                .get(0));
    assertFalse(response);
  }

  @Test
  public void testVerifyAndPopulateProDateInfo_WhenDCFinIsEnabled_UpdateProDate()
      throws ReceivingException, IOException {
    Instruction mockInstruction = getMockInstruction_Atlas_Converted_Item();
    DeliveryDocument deliveryDocument =
        gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
    when(rdcDeliveryService.getPOLineInfoFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(getMockDeliveryDocumentResponseWithProDate());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);

    rdcInstructionUtils.verifyAndPopulateProDateInfo(deliveryDocument, mockInstruction, headers);

    verify(rdcDeliveryService, times(1))
        .getPOLineInfoFromGDM(anyString(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testVerifyAndPopulateProDateInfo_ThrowsException_WhenDCFinIsEnabled_AndNoDeliveryDocumentsFoundInGDM()
          throws ReceivingException, IOException {
    Instruction mockInstruction = getMockInstruction_Atlas_Converted_Item();
    DeliveryDocument deliveryDocument =
        gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
    when(rdcDeliveryService.getPOLineInfoFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(getMockDeliveryDocumentResponseWithNoDeliveryDocumentInfo());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);

    rdcInstructionUtils.verifyAndPopulateProDateInfo(deliveryDocument, mockInstruction, headers);

    verify(rdcDeliveryService, times(1))
        .getPOLineInfoFromGDM(anyString(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void
      testVerifyAndPopulateProDateInfo_throwsException_whenProDateIsMissing_inDeliveryDocuments()
          throws IOException, ReceivingException {
    Instruction mockInstruction = getMockInstruction_Atlas_Converted_Item();
    DeliveryDocument deliveryDocument =
        gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(rdcDeliveryService.getPOLineInfoFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(getMockDeliveryDocumentResponseWithOutProDate());

    rdcInstructionUtils.verifyAndPopulateProDateInfo(deliveryDocument, mockInstruction, headers);

    verify(rdcDeliveryService, times(1))
        .getPOLineInfoFromGDM(anyString(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false);
  }

  @Test
  public void testCreateInstructionForThreeScanDocktag_happy_path()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt())).thenReturn(100L);

    InstructionResponse response =
        rdcInstructionUtils.createInstructionForThreeScanDocktag(
            instructionRequest,
            new InstructionResponseImplNew(),
            false,
            MockHttpHeaders.getHeaders());
    assertEquals(
        response.getInstruction().getInstructionCode(),
        RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_CODE);
  }

  @Test
  public void testCreateInstructionForThreeScanDocktag_SSCC_SCAN_happy_path()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    instructionRequest.setSscc("00000731613344740018");
    instructionRequest.setDeliveryNumber("23369104");
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt())).thenReturn(100L);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_THREE_SCAN_DOCKTAG_ENABLED_FOR_SSCC,
            false))
        .thenReturn(true);

    InstructionResponse response =
        rdcInstructionUtils.createInstructionForThreeScanDocktag(
            instructionRequest,
            new InstructionResponseImplNew(),
            false,
            MockHttpHeaders.getHeaders());
    assertEquals(
        response.getInstruction().getInstructionCode(),
        RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_CODE);
    assertEquals(
        response.getInstruction().getInstructionMsg(),
        RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_MESSAGE);
    assertTrue(response.getDeliveryDocuments().size() == 1);
    DeliveryDocumentLine delDocLine =
        response.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    assertEquals(delDocLine.getPurchaseRefType(), PoType.DSDC.name());
    assertEquals(delDocLine.getItemNbr().toString(), instructionRequest.getDeliveryNumber());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_THREE_SCAN_DOCKTAG_ENABLED_FOR_SSCC,
            false);
  }

  @Test
  public void testCreateInstructionForThreeScanDocktag_SSCC_SCAN_Last_SSTK_Scan_Type()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    instructionRequest.setLastScannedFreightType(PoType.SSTK.getpoType());
    instructionRequest.setSscc("00000731613344740018");
    instructionRequest.setDeliveryNumber("23369104");
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt())).thenReturn(100L);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_THREE_SCAN_DOCKTAG_ENABLED_FOR_SSCC,
            false))
        .thenReturn(true);

    try {
      rdcInstructionUtils.createInstructionForThreeScanDocktag(
          instructionRequest,
          new InstructionResponseImplNew(),
          false,
          MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.THREE_SCAN_DOCKTAG_MIXED_ITEM_ERROR);
    }
  }

  @Test
  public void testCreateInstructionForThreeScanDocktag_UPC_SCAN_DSDC_Not_Allowed()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt())).thenReturn(100L);
    try {
      rdcInstructionUtils.createInstructionForThreeScanDocktag(
          instructionRequest,
          new InstructionResponseImplNew(),
          false,
          MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.THREE_SCAN_DOCKTAG_DSDC_SCAN_UPC);
      assertEquals(e.getMessage(), ReceivingException.THREE_SCAN_DOCKTAG_DSDC_SCAN_UPC_ERROR_MSG);
    }
  }

  @Test
  public void testCreateInstructionForThreeScanDocktag_po_line_cancelled_exception()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithAllCancelledLines();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    try {
      InstructionResponse response =
          rdcInstructionUtils.createInstructionForThreeScanDocktag(
              instructionRequest,
              new InstructionResponseImplNew(),
              false,
              MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(
          e.getErrorCode(), ExceptionCodes.THREE_SCAN_DOCKTAG_CANCELLED_REJECTED_POL_ERROR);
    }
  }

  @Test
  public void testCreateInstructionForThreeScanDocktag_history_po_exception()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments.get(0).setPurchaseReferenceStatus(POStatus.HISTORY.name());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    try {
      InstructionResponse response =
          rdcInstructionUtils.createInstructionForThreeScanDocktag(
              instructionRequest,
              new InstructionResponseImplNew(),
              false,
              MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.THREE_SCAN_DOCKTAG_HISTORY_PO_ERROR);
    }
  }

  @Test
  public void testCreateInstructionForThreeScanDocktag_mixed_item_exception()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    instructionRequest.setLastScannedFreightType(PoType.CROSSDOCK.getpoType());
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    try {
      InstructionResponse response =
          rdcInstructionUtils.createInstructionForThreeScanDocktag(
              instructionRequest,
              new InstructionResponseImplNew(),
              false,
              MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.THREE_SCAN_DOCKTAG_MIXED_ITEM_ERROR);
    }
  }

  @Test
  public void testCreateInstructionForThreeScanDocktag_blocked_item_exception()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("X");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    try {
      rdcInstructionUtils.createInstructionForThreeScanDocktag(
          instructionRequest,
          new InstructionResponseImplNew(),
          false,
          MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.THREE_SCAN_DOCKTAG_XBLOCK_ITEM_ERROR);
    }
  }

  @Test
  public void testCreateInstructionForThreeScanDocktag_freight_identification_scenario()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo_AtlasConvertedItems();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setItemNbr(585169196L);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionResponse response =
        rdcInstructionUtils.createInstructionForThreeScanDocktag(
            instructionRequest,
            new InstructionResponseImplNew(),
            false,
            MockHttpHeaders.getHeaders());
    assertNull(response.getInstruction());
    assertTrue(CollectionUtils.isNotEmpty(response.getDeliveryDocuments()));
  }

  @Test
  public void testCreateInstructionForThreeScanDocktag_freight_identification_request_true()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    instructionRequest.setLastScannedFreightType(PoType.CROSSDOCK.getpoType());
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo_AtlasConvertedItems();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setItemNbr(585169196L);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PoType.CROSSDOCK.name());

    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionResponse response =
        rdcInstructionUtils.createInstructionForThreeScanDocktag(
            instructionRequest,
            new InstructionResponseImplNew(),
            true,
            MockHttpHeaders.getHeaders());
    assertTrue(CollectionUtils.isNotEmpty(response.getDeliveryDocuments()));
    assertEquals(
        response.getInstruction().getInstructionCode(),
        RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_CODE);
    // validate purchaseRef type of lastScannedFreightType is selected
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseRefType(),
        PoType.CROSSDOCK.name());
  }

  @Test
  public void
      testCreateInstructionForThreeScanDocktag_freight_identification_request_false_first_scan_mixed()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    // set LastScannedFreightType as null for first scan
    instructionRequest.setLastScannedFreightType(null);
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .add(
            MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0));
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMap = new HashMap<>();
    receivedQtyMap.put("8458708163-1", 310L);
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMap);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());
    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class)))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt())).thenReturn(100L);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionResponse response =
        rdcInstructionUtils.createInstructionForThreeScanDocktag(
            instructionRequest,
            new InstructionResponseImplNew(),
            false,
            MockHttpHeaders.getHeaders());
    assertTrue(CollectionUtils.isNotEmpty(response.getDeliveryDocuments()));
    assertEquals(
        response.getInstruction().getInstructionCode(),
        RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_CODE);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseRefType(),
        PurchaseReferenceType.CROSSU.name());
  }

  @Test
  public void testReceivedQtyByPoAndPoLine_ForAtlasConvertedItemSSTK_IsSuccess()
      throws IOException, ReceivingException, ItemConfigRestApiClientException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_CONVERSION_ENABLED_FOR_ALL_SSTK_ITEMS,
            false))
        .thenReturn(true);
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    Long receivedQtyByPoAndPol =
        rdcInstructionUtils.getReceivedQtyByPoAndPoLine(
            deliveryDocumentLine,
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());

    assertNotNull(receivedQtyByPoAndPol);
    assertSame(receivedQtyByPoAndPol, 10L);

    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_CONVERSION_ENABLED_FOR_ALL_SSTK_ITEMS,
            false);
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void testReceivedQtyByPoAndPoLine_ForAtlasConvertedItemDA_IsSuccess()
      throws IOException, ReceivingException, ItemConfigRestApiClientException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("0");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLineDA(10L));

    Long receivedQtyByPoAndPol =
        rdcInstructionUtils.getReceivedQtyByPoAndPoLine(
            deliveryDocumentLine,
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());

    assertNotNull(receivedQtyByPoAndPol);
    assertSame(receivedQtyByPoAndPol, 10L);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    verify(nimRdsService, times(0))
        .getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class));
    verify(rdcManagedConfig, times(1)).getDaAtlasItemEnabledPackHandlingCode();
  }

  @Test
  public void testReceivedQtyByPoAndPoLine_ForNonAtlasConvertedItem_IsSuccess()
      throws IOException, ReceivingException, ItemConfigRestApiClientException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(true);
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.emptyList());
    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class)))
        .thenReturn(getReceivedQtyMapForOnePoWithOneLine());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));

    Long receivedQtyByPoAndPol =
        rdcInstructionUtils.getReceivedQtyByPoAndPoLine(
            deliveryDocumentLine,
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());

    assertNotNull(receivedQtyByPoAndPol);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(true);
  }

  @Test
  public void testReceivedQtyByPoAndPoLine_ForNonAtlasDAItem_IsSuccess()
      throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLineDA(10L));
    Long receivedQtyByPoAndPol =
        rdcInstructionUtils.getReceivedQtyByPoAndPoLine(
            deliveryDocumentLine,
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());

    assertNotNull(receivedQtyByPoAndPol);
    assertSame(receivedQtyByPoAndPol, 10L);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceivedQtyByPoAndPoLine_ForNonAtlasConvertedItem_IsNotSuccess_WhenRdsThrowsException()
          throws IOException, ReceivingException, ItemConfigRestApiClientException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(true);
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.emptyList());
    doThrow(new ReceivingBadDataException("mock_error", "mock"))
        .when(nimRdsService)
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt())).thenReturn(10L);

    rdcInstructionUtils.getReceivedQtyByPoAndPoLine(
        deliveryDocumentLine,
        deliveryDocumentLine.getPurchaseReferenceNumber(),
        deliveryDocumentLine.getPurchaseReferenceLineNumber());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false);
  }

  private SlottingPalletResponse getPrimeSlotFromSmartSlotting() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    SlottingDivertLocations slottingDivertLocations = new SlottingDivertLocations();
    slottingPalletResponse.setMessageId("Test-Smart-Slotting");
    slottingDivertLocations.setLocation("A0001");
    slottingDivertLocations.setType("success");
    slottingDivertLocations.setItemNbr(3804890L);
    slottingDivertLocations.setLocationSize(72);
    slottingDivertLocations.setSlotType("prime");
    slottingPalletResponse.setLocations(Collections.singletonList(slottingDivertLocations));
    return slottingPalletResponse;
  }

  private ItemConfigDetails mockItemConfigDetails() {
    return ItemConfigDetails.builder()
        .createdDateTime(createTs)
        .desc(itemDesc)
        .item(itemNumber)
        .build();
  }

  private Instruction getMockProblemInstruction() throws IOException {
    Instruction instruction = new Instruction();
    instruction.setId(123L);
    instruction.setProblemTagId("PTAG123");
    instruction.setMessageId("abc-def");
    instruction.setGtin("UPC123");
    instruction.setCreateUserId("sysadmin");
    instruction.setProjectedReceiveQty(30);
    instruction.setPurchaseReferenceNumber("32322434");
    instruction.setPurchaseReferenceLineNumber(1);

    instruction.setDeliveryDocument(
        gson.toJson(MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0)));
    return instruction;
  }

  private List<String> getMockDeliveryDocumentsByDeliveryNumberInstructionSetId(
      Boolean isAtlasConvertedItem) throws IOException {
    List<String> deliveryDocuments = new ArrayList<>();
    DeliveryDocument deliveryDocument1 =
        MockDeliveryDocuments
            .getDeliveryDocumentForReceiveInstructionFromInstructionAtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument1.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(isAtlasConvertedItem);
    deliveryDocumentLine.getAdditionalInfo().setPrimeSlot("B1201");

    DeliveryDocument deliveryDocument2 = deliveryDocument1;

    deliveryDocuments.add(gson.toJson(deliveryDocument1));
    deliveryDocuments.add(gson.toJson(deliveryDocument2));
    return deliveryDocuments;
  }

  private SlottingPalletResponse getPrimeSlotCompatibilityResponseFromSmartSlotting() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    SlottingDivertLocations slottingDivertLocations1 = new SlottingDivertLocations();
    SlottingDivertLocations slottingDivertLocations2 = new SlottingDivertLocations();
    List<SlottingDivertLocations> slottingDivertLocationsList = new ArrayList<>();
    slottingPalletResponse.setMessageId("Test-Smart-Slotting");
    slottingDivertLocations1.setLocation("G3232");
    slottingDivertLocations1.setItemNbr(34323233);
    slottingDivertLocations1.setType("success");
    slottingDivertLocations1.setLocationSize(72);
    slottingDivertLocations1.setSlotType("prime");

    slottingDivertLocations2.setLocation("G3434");
    slottingDivertLocations2.setItemNbr(3804890);
    slottingDivertLocations2.setType("success");
    slottingDivertLocations2.setLocationSize(72);
    slottingDivertLocations2.setSlotType("prime");

    slottingDivertLocationsList.add(slottingDivertLocations1);
    slottingDivertLocationsList.add(slottingDivertLocations2);
    slottingPalletResponse.setLocations(slottingDivertLocationsList);
    return slottingPalletResponse;
  }

  private Instruction getMockInstruction_Atlas_Converted_Item() throws IOException {
    Instruction instruction = new Instruction();
    instruction.setId(12345L);
    instruction.setLastChangeUserId("sysadmin");
    instruction.setCreateUserId("sysadmin");
    instruction.setDeliveryNumber(23371015L);
    instruction.setMessageId("123e4567-e89b-12d3-a456-426655440000");
    instruction.setPurchaseReferenceNumber("4223042727");
    instruction.setPurchaseReferenceLineNumber(2);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments
            .getDeliveryDocumentForReceiveInstructionFromInstructionAtlasConvertedItem();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    return instruction;
  }

  public GdmPOLineResponse getMockDeliveryDocumentResponseWithProDate() throws IOException {
    Instruction instruction = getMockInstruction_Atlas_Converted_Item();
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    deliveryDocument.setProDate(new Date());
    List<DeliveryDocument> deliveryDocumentList = Collections.singletonList(deliveryDocument);
    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    gdmPOLineResponse.setDeliveryDocuments(deliveryDocumentList);
    return gdmPOLineResponse;
  }

  public GdmPOLineResponse getMockDeliveryDocumentResponseWithOutProDate() throws IOException {
    Instruction instruction = getMockInstruction_Atlas_Converted_Item();
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    List<DeliveryDocument> deliveryDocumentList = Collections.singletonList(deliveryDocument);
    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    gdmPOLineResponse.setDeliveryDocuments(deliveryDocumentList);
    return gdmPOLineResponse;
  }

  public GdmPOLineResponse getMockDeliveryDocumentResponseWithNoDeliveryDocumentInfo()
      throws IOException {
    Instruction instruction = getMockInstruction_Atlas_Converted_Item();
    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    gdmPOLineResponse.setDeliveryNumber(123456L);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.WORKING.name());
    gdmPOLineResponse.setDeliveryDocuments(null);
    return gdmPOLineResponse;
  }

  @Test
  public void testIsHazmatItem_WithValidScenarioAndGdmHazmatFlagDisabled() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED,
            false))
        .thenReturn(false);
    deliveryDocumentLine.setTransportationModes(getMockTransportationModes_ValidHazmat());
    assertTrue(rdcInstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_WithNoTransportationModesAndGdmHazmatFlagDisabled() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED,
            false))
        .thenReturn(false);
    deliveryDocumentLine.setTransportationModes(null);
    assertFalse(rdcInstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_WithTransportationModesAsORMDAndGdmHazmatFlagDisabled() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED,
            false))
        .thenReturn(false);
    deliveryDocumentLine.setTransportationModes(getMockTransportationModes_ORMD());
    assertFalse(rdcInstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_WithNoGroundTransportationModesAndGdmHazmatFlagDisabled() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED,
            false))
        .thenReturn(false);
    deliveryDocumentLine.setTransportationModes(getMockTransportationModes_NotGroundTransport());
    assertFalse(rdcInstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_NoDotNumberIdAndGdmHazmatFlagDisabled() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED,
            false))
        .thenReturn(false);
    deliveryDocumentLine.setTransportationModes(getMockTransportationModes_NoDotNumber());
    assertFalse(rdcInstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_NoDotHazardousClassAndGdmHazmatFlagDisabled() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED,
            false))
        .thenReturn(false);
    deliveryDocumentLine.setTransportationModes(getMockTransportationModes_NoDotHazardousClass());
    assertFalse(rdcInstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_WithGDMHazmatValidationEnabled() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    deliveryDocumentLine.setTransportationModes(getMockTransportationModes_NotGroundTransport());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED,
            false))
        .thenReturn(true);
    assertTrue(rdcInstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_WithGDMHazmatValidationEnabledNotHazmatItem() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(false);
    deliveryDocumentLine.setTransportationModes(getMockTransportationModes_ValidHazmat());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED,
            false))
        .thenReturn(true);
    assertFalse(rdcInstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_filterDSDCDeliveryDocuments_Returns_Valid_DSDC_DeliveryDocuments()
      throws IOException, ReceivingBadDataException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    List<DeliveryDocument> dsdcDeliveryDocuments =
        rdcInstructionUtils.filterDADeliveryDocuments(
            deliveryDocumentList, getMockInstructionRequest());
    assertTrue(dsdcDeliveryDocuments.size() > 0);
    assertEquals(dsdcDeliveryDocuments.size(), deliveryDocumentList.size());
  }

  @Test
  public void test_isLegacyInProgressDelivery_Returns_True_For_CurrentDelivery() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CUTOFF_ENABLED_FOR_ATLAS_DA_DELIVERIES,
            false))
        .thenReturn(true);
    List<String> deliveryListEnabledForCutOff = new ArrayList<>();
    deliveryListEnabledForCutOff.add("24356812");
    deliveryListEnabledForCutOff.add("45378954");
    deliveryListEnabledForCutOff.add("90876542");
    doReturn(deliveryListEnabledForCutOff)
        .when(rdcManagedConfig)
        .getInProgressCutOffDeliveriesList();

    boolean isDeliveryNumberBlockedForAtlas =
        rdcInstructionUtils.isLegacyInProgressDelivery(45378954L);
    assertTrue(isDeliveryNumberBlockedForAtlas);
  }

  @Test
  public void test_isLegacyInProgressDelivery_Returns_False_For_CurrentDelivery_Cutoff_Enabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CUTOFF_ENABLED_FOR_ATLAS_DA_DELIVERIES,
            false))
        .thenReturn(true);

    List<String> deliveryListEnabledForCutOff = new ArrayList<>();
    deliveryListEnabledForCutOff.add("24356812");
    deliveryListEnabledForCutOff.add("45378954");
    deliveryListEnabledForCutOff.add("90876542");
    doReturn(deliveryListEnabledForCutOff)
        .when(rdcManagedConfig)
        .getInProgressCutOffDeliveriesList();

    boolean isDeliveryNumberBlockedForAtlas =
        rdcInstructionUtils.isLegacyInProgressDelivery(42098954L);
    assertFalse(isDeliveryNumberBlockedForAtlas);
  }

  @Test
  public void test_isLegacyInProgressDelivery_Returns_False_For_CurrentDelivery_Cutoff_Disabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CUTOFF_ENABLED_FOR_ATLAS_DA_DELIVERIES,
            false))
        .thenReturn(false);

    List<String> deliveryListEnabledForCutOff = new ArrayList<>();
    deliveryListEnabledForCutOff.add("24356812");
    deliveryListEnabledForCutOff.add("45378954");
    deliveryListEnabledForCutOff.add("90876542");
    doReturn(deliveryListEnabledForCutOff)
        .when(rdcManagedConfig)
        .getInProgressCutOffDeliveriesList();

    boolean isDeliveryNumberBlockedForAtlas =
        rdcInstructionUtils.isLegacyInProgressDelivery(45378954L);
    assertFalse(isDeliveryNumberBlockedForAtlas);
  }

  @Test
  private void testMoveDetailsForInstruction() {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("223");
    when(rdcManagedConfig.getMoveTypeCode()).thenReturn(50);
    when(rdcManagedConfig.getMoveTypeDesc()).thenReturn("Haul");
    LinkedTreeMap<String, Object> response =
        rdcInstructionUtils.moveDetailsForInstruction(
            receiveInstructionRequest, "a2323232", "L323", MockHttpHeaders.getHeaders());
    assertFalse(response.isEmpty());
  }

  public List<TransportationModes> getMockTransportationModes_ORMD() {
    TransportationModes transportationModes = new TransportationModes();

    Mode mode = new Mode();
    mode.setCode(1);
    mode.setDescription("GROUND");

    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    dotHazardousClass.setDescription("Other Regulated Material");

    transportationModes.setDotRegionCode("UN");

    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }

  public List<TransportationModes> getMockTransportationModes_ValidHazmat() {
    TransportationModes transportationModes = new TransportationModes();

    Mode mode = new Mode();
    mode.setCode(1);
    mode.setDescription("GROUND");

    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("HAZMAT");
    dotHazardousClass.setDescription("HAZMAT Material");

    transportationModes.setDotRegionCode("UN");
    transportationModes.setDotIdNbr("DotRegion");

    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }

  public List<TransportationModes> getMockTransportationModes_NoDotNumber() {
    TransportationModes transportationModes = new TransportationModes();

    Mode mode = new Mode();
    mode.setCode(1);
    mode.setDescription("GROUND");

    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("HAZMAT");
    dotHazardousClass.setDescription("HAZMAT Material");

    transportationModes.setDotRegionCode("UN");
    transportationModes.setDotIdNbr(null);

    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }

  public List<TransportationModes> getMockTransportationModes_NotGroundTransport() {
    TransportationModes transportationModes = new TransportationModes();

    Mode mode = new Mode();
    mode.setCode(2);
    mode.setDescription("AIR");

    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("HAZMAT");
    dotHazardousClass.setDescription("HAZMAT Material");

    transportationModes.setDotRegionCode("UN");

    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }

  public List<TransportationModes> getMockTransportationModes_NoDotHazardousClass() {
    TransportationModes transportationModes = new TransportationModes();

    Mode mode = new Mode();
    mode.setCode(1);
    mode.setDescription("GROUND");

    transportationModes.setDotRegionCode("UN");
    transportationModes.setDotIdNbr("valid");

    transportationModes.setMode(mode);
    transportationModes.setDotHazardousClass(null);
    List<TransportationModes> transportationModesList = new ArrayList<>();
    transportationModesList.add(transportationModes);
    return transportationModesList;
  }

  @Test
  public void test_isSSTKDocument_v2() throws IOException, ReceivingBadDataException {
    String dataPath =
        new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetailsSSTK.json")
            .getCanonicalPath();
    DeliveryDetails deliveryDetails =
        JacksonParser.convertJsonToObject(
            new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);
    com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument =
        deliveryDetails.getDeliveryDocuments().get(0);
    boolean isSSTKDocument = rdcInstructionUtils.isSSTKDocument(deliveryDocument);
    assertTrue(isSSTKDocument);
  }

  @Test
  public void testAtlasConvertItemReturnsTrueForDAItemsInPilotDelivery_DaItemConversionEnabled()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_PILOT_DELIVERY_ENABLED,
            false);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
      }
    }
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    verify(rdcManagedConfig, times(2)).getDaAtlasItemEnabledPackHandlingCode();
    verify(rdcManagedConfig, times(4)).getAtlasDaPilotDeliveries();
  }

  @Test
  public void testAtlasConvertItemReturnsFalseForDAItemsInPilotDelivery_DaItemConversionDisabled()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<String> deliveryListEnabledForCutOff = new ArrayList<>();
    deliveryListEnabledForCutOff.add("24356812");
    deliveryListEnabledForCutOff.add("60032433");
    deliveryListEnabledForCutOff.add("90876542");
    doReturn(deliveryListEnabledForCutOff)
        .when(rdcManagedConfig)
        .getInProgressCutOffDeliveriesList();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CUTOFF_ENABLED_FOR_ATLAS_DA_DELIVERIES,
            false))
        .thenReturn(true);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        assertFalse(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
      }
    }
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
    verify(rdcManagedConfig, times(4)).getInProgressCutOffDeliveriesList();
  }

  @Test
  public void test_isAtlasDaPilotDelivery_Returns_True_For_CurrentDelivery() {

    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();

    boolean isDaPilotDelivery = true;
    boolean isDeliveryNumberValidForAtlas = rdcInstructionUtils.isAtlasDaPilotDelivery(60032433L);
    assertTrue(isDeliveryNumberValidForAtlas);
  }

  @Test
  public void test_isAtlasDaPilotDelivery_Returns_False_For_CurrentDelivery() {
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    boolean isDeliveryNumberValidForAtlas = rdcInstructionUtils.isAtlasDaPilotDelivery(45378954L);
    assertFalse(isDeliveryNumberValidForAtlas);
  }

  @Test
  public void test_isDeliveryEligibleForAtlasDaReceiving_Returns_True_For_PilotDelivery()
      throws IOException {

    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setItemNbr(3804890L);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");

    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();

    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_PILOT_DELIVERY_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CUTOFF_ENABLED_FOR_ATLAS_DA_DELIVERIES,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    boolean isDeliveryNumberValidForAtlas =
        rdcInstructionUtils.isDeliveryEligibleForAtlasDaReceiving(60032433L, deliveryDocumentLine);
    assertTrue(isDeliveryNumberValidForAtlas);
  }

  @Test
  public void test_isDeliveryEligibleForAtlasDaReceiving_Returns_True_For_NotInProgressDelivery()
      throws IOException {

    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setItemNbr(3804890L);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");

    List<String> deliveryListEnabledForCutOff = new ArrayList<>();
    deliveryListEnabledForCutOff.add("24356812");
    deliveryListEnabledForCutOff.add("45378954");
    deliveryListEnabledForCutOff.add("90876542");
    doReturn(deliveryListEnabledForCutOff)
        .when(rdcManagedConfig)
        .getInProgressCutOffDeliveriesList();

    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_PILOT_DELIVERY_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CUTOFF_ENABLED_FOR_ATLAS_DA_DELIVERIES,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    boolean isDeliveryNumberValidForAtlas =
        rdcInstructionUtils.isDeliveryEligibleForAtlasDaReceiving(60032433L, deliveryDocumentLine);
    assertTrue(isDeliveryNumberValidForAtlas);
  }

  @Test
  public void
      test_isDeliveryEligibleForAtlasDaReceiving_Returns_False_For_AtlasDaItemConversionDisabled()
          throws IOException {

    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setItemNbr(3804890L);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");

    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();

    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_PILOT_DELIVERY_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CUTOFF_ENABLED_FOR_ATLAS_DA_DELIVERIES,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(false);
    boolean isDeliveryNumberValidForAtlas =
        rdcInstructionUtils.isDeliveryEligibleForAtlasDaReceiving(60032433L, deliveryDocumentLine);
    assertFalse(isDeliveryNumberValidForAtlas);
  }

  @Test
  public void testAtlasConvertItemSSTKItemAdded()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
        deliveryDocumentLine.setPurchaseRefType("SSTKU");
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804891L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    List<String> freightTypes = new ArrayList<>();
    freightTypes.add("SSTK");
    when(tenantSpecificConfigReader.getFreightSpecificType(any())).thenReturn(freightTypes);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ATLAS_COMPLETE_MIGRATED_DC_LIST, false);
    doNothing().when(itemConfigApiClient).addAsAtlasItems(anySet(), any(HttpHeaders.class));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
    verify(itemConfigApiClient, times(1)).addAsAtlasItems(anySet(), any(HttpHeaders.class));
  }

  @Test
  public void testAtlasConvertItemDAItemAdded()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
        deliveryDocumentLine.setPurchaseRefType("CROSSU");
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804891L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    List<String> freightTypes = new ArrayList<>();
    freightTypes.add("DA");
    when(tenantSpecificConfigReader.getFreightSpecificType(any())).thenReturn(freightTypes);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ATLAS_COMPLETE_MIGRATED_DC_LIST, false);
    doNothing().when(itemConfigApiClient).addAsAtlasItems(anySet(), any(HttpHeaders.class));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
    verify(itemConfigApiClient, times(1)).addAsAtlasItems(anySet(), any(HttpHeaders.class));
  }

  @Test
  public void testAtlasConvertItemDAItemAddedWithSameItemNumber()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
        deliveryDocumentLine.setPurchaseRefType("CROSSU");
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    List<String> freightTypes = new ArrayList<>();
    freightTypes.add("DA");
    when(tenantSpecificConfigReader.getFreightSpecificType(any())).thenReturn(freightTypes);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ATLAS_COMPLETE_MIGRATED_DC_LIST, false);
    doNothing().when(itemConfigApiClient).addAsAtlasItems(anySet(), any(HttpHeaders.class));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
    verify(itemConfigApiClient, times(0)).addAsAtlasItems(anySet(), any(HttpHeaders.class));
  }

  @Test
  public void testAtlasConvertItemSSTKItemAddedHavingSameItemNumber()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
        deliveryDocumentLine.setPurchaseRefType("SSTKU");
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    List<String> freightTypes = new ArrayList<>();
    freightTypes.add("SSTK");
    when(tenantSpecificConfigReader.getFreightSpecificType(any())).thenReturn(freightTypes);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ATLAS_COMPLETE_MIGRATED_DC_LIST, false);
    doNothing().when(itemConfigApiClient).addAsAtlasItems(anySet(), any(HttpHeaders.class));
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(anySet(), any(HttpHeaders.class));
    verify(itemConfigApiClient, times(0)).addAsAtlasItems(anySet(), any(HttpHeaders.class));
  }

  @Test
  public void testCheckAtlasConvertedItemForDa_ItemConfigEnabled()
      throws IOException, ReceivingException, ItemConfigRestApiClientException {
    List<String> deliveryListEnabledForCutOff = new ArrayList<>();
    deliveryListEnabledForCutOff.add("24356812");
    deliveryListEnabledForCutOff.add("90876542");
    doReturn(deliveryListEnabledForCutOff)
        .when(rdcManagedConfig)
        .getInProgressCutOffDeliveriesList();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CUTOFF_ENABLED_FOR_ATLAS_DA_DELIVERIES,
            false))
        .thenReturn(true);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    rdcInstructionUtils.checkAtlasConvertedItemForDa(deliveryDocuments, httpHeaders);

    assertNotNull(deliveryDocuments);
    assertTrue(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());
  }

  @Test
  public void testCheckAtlasConvertedItemForDa_ItemConfigDisabled()
      throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS,
            false))
        .thenReturn(false);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    rdcInstructionUtils.checkAtlasConvertedItemForDa(deliveryDocuments, httpHeaders);

    assertNotNull(deliveryDocuments);
    assertTrue(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());
  }

  @Test
  public void testCheckAtlasConvertedItemForDa_ItemConfigDisabled_BreakPackItem_ReferItemConfigApi()
      throws IOException, ReceivingException, ItemConfigRestApiClientException {
    List<String> deliveryListEnabledForCutOff = new ArrayList<>();
    deliveryListEnabledForCutOff.add("999999999");
    doReturn(deliveryListEnabledForCutOff)
        .when(rdcManagedConfig)
        .getInProgressCutOffDeliveriesList();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS,
            false))
        .thenReturn(false);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(Collections.singletonList("CC"));
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(3804890L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCodeWithItemConfig())
        .thenReturn(Collections.singletonList("BC"));
    rdcInstructionUtils.checkAtlasConvertedItemForDa(deliveryDocuments, httpHeaders);

    assertNotNull(deliveryDocuments);
    assertTrue(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());
  }

  @Test
  public void testGetDADeliveryDocumentsFromGDMDeliveryDocuments() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<DeliveryDocument> filteredDADocuments =
        rdcInstructionUtils.getDADeliveryDocumentsFromGDMDeliveryDocuments(deliveryDocuments);
    assertTrue(filteredDADocuments.size() > 0);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testGetDADeliveryDocumentsFromGDMDeliveryDocuments_ThrowsException_whenFoundNoDADocuments()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    List<DeliveryDocument> filteredDADocuments =
        rdcInstructionUtils.getDADeliveryDocumentsFromGDMDeliveryDocuments(deliveryDocuments);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testGetDADeliveryDocumentsFromGDMDeliveryDocuments_ThrowsException_whenFoundDSDCDocuments()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDSDC();
    List<DeliveryDocument> filteredDADocuments =
        rdcInstructionUtils.getDADeliveryDocumentsFromGDMDeliveryDocuments(deliveryDocuments);
  }

  @Test
  public void testValidateAndProcessGdmDeliveryDocuments_HistoryPO() throws IOException {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
        gdmDeliveryDocumentList = MockDeliveryDocuments.getDeliveryDocumentsForSSTKV2();
    gdmDeliveryDocumentList.get(0).setPurchaseReferenceStatus(POStatus.HISTORY.name());
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
        filteredDeliveryDocumentList =
            rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(gdmDeliveryDocumentList);
    assertEquals(filteredDeliveryDocumentList, CollectionUtils.EMPTY_COLLECTION);
  }

  @Test
  public void testValidateAndProcessGdmDeliveryDocuments_CancelledLineStatus() throws IOException {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
        gdmDeliveryDocumentList = MockDeliveryDocuments.getDeliveryDocumentsForSSTKV2();
    gdmDeliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(String.valueOf(POLineStatus.CANCELLED));
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
        filteredDeliveryDocumentList =
            rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(gdmDeliveryDocumentList);
    assertEquals(filteredDeliveryDocumentList, CollectionUtils.EMPTY_COLLECTION);
  }

  @Test
  public void
      testFilterInvalidPoLinesFromDeliveryDocuments_returnsException_for_multiPO_and_allLines_areIn_cancelledStatus_V2()
          throws IOException {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPoWithAllLinesCancelled_V2();
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> filteredDocuments =
        rdcInstructionUtils.filterInvalidPoLinesFromDocuments(deliveryDocuments);
    assertEquals(filteredDocuments, CollectionUtils.EMPTY_COLLECTION);
  }

  @Test
  public void
      testFilterInvalidPoLinesFromDeliveryDocuments_returnsDocument_for_multiPO_and_allLines_are_notIn_cancelled_status_V2()
          throws IOException {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithAllPartiallyCancelledLines_V2();
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> filteredDocuments =
        rdcInstructionUtils.filterInvalidPoLinesFromDocuments(deliveryDocuments);
    assertNotNull(filteredDocuments);
    assertTrue(filteredDocuments.size() > 0);
  }

  @Test
  public void
      testFilterCancelledLinesFromDeliveryDocuments_returnsException_for_SinglePO_and_allLines_areIn_closedStatus_V2()
          throws IOException {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPoWithAllLinesCancelled_V2();
    deliveryDocuments
        .stream()
        .forEach(
            document ->
                document
                    .getDeliveryDocumentLines()
                    .stream()
                    .forEach(
                        line -> line.setPurchaseReferenceLineStatus(POLineStatus.CLOSED.name())));
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> filteredDocuments =
        rdcInstructionUtils.filterInvalidPoLinesFromDocuments(deliveryDocuments);
    assertEquals(filteredDocuments, CollectionUtils.EMPTY_COLLECTION);
  }

  @Test
  public void testValidateAndAddAsAtlasConvertedItemsV2()
      throws IOException, ReceivingException, ItemConfigRestApiClientException {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
        gdmDeliveryDocumentList = MockDeliveryDocuments.getDeliveryDocumentsForSSTKV2();
    rdcInstructionUtils.validateAndAddAsAtlasConvertedItemsV2(
        gdmDeliveryDocumentList, MockHttpHeaders.getHeaders());
    verify(itemConfigApiClient, times(1)).checkAndAddAsAtlasItems(anySet(), any(HttpHeaders.class));
  }

  @Test
  public void testValidateAndAddAsAtlasConvertedItemsV2_catchesException()
      throws ItemConfigRestApiClientException, IOException {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
        gdmDeliveryDocumentList = MockDeliveryDocuments.getDeliveryDocumentsForSSTKV2();
    doThrow(
            new ItemConfigRestApiClientException(
                "error", HttpStatus.SERVICE_UNAVAILABLE, "service down"))
        .when(itemConfigApiClient)
        .checkAndAddAsAtlasItems(anySet(), any(HttpHeaders.class));
    rdcInstructionUtils.validateAndAddAsAtlasConvertedItemsV2(
        gdmDeliveryDocumentList, MockHttpHeaders.getHeaders());
  }

  @Test
  public void testValidateItemHandlingMethodThrowsExceptionForInvalidHandlingCode() {
    ItemData itemData = new ItemData();
    itemData.setItemHandlingMethod("AB");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(1234567L);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    try {
      rdcInstructionUtils.validateItemHandlingMethod(deliveryDocumentLine);
    } catch (ReceivingBadDataException exception) {
      assertNotNull(exception);
      assertSame(exception.getErrorCode(), ExceptionCodes.MISSING_ITEM_HANDLING_CODE);
    }
  }

  @Test
  public void testValidateItemHandlingMethodThrowsExceptionForEmptyHandlingCode() {
    ItemData itemData = new ItemData();
    itemData.setItemHandlingMethod("AB");
    itemData.setHandlingCode("");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(1234567L);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    try {
      rdcInstructionUtils.validateItemHandlingMethod(deliveryDocumentLine);
    } catch (ReceivingBadDataException exception) {
      assertNotNull(exception);
      assertSame(exception.getErrorCode(), ExceptionCodes.MISSING_ITEM_HANDLING_CODE);
    }
  }

  @Test
  public void testValidateItemHandlingMethod_success() {
    ItemData itemData = new ItemData();
    itemData.setItemHandlingMethod("CC");
    itemData.setHandlingCode("C");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(1234567L);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    try {
      rdcInstructionUtils.validateItemHandlingMethod(deliveryDocumentLine);
    } catch (ReceivingBadDataException exception) {
      assertNotNull(exception);
      assertSame(exception.getErrorCode(), ExceptionCodes.MISSING_ITEM_HANDLING_CODE);
    }
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_updateAdditionalItemDetailsFromGDM_MissingItemHandlingMethod()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);

    rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(deliveryDocumentList);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertFalse(deliveryDocumentLine.getIsHazmat());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletHi());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_updateAdditionalItemDetailsFromGDM_EmptyItemHandlingMethod() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setHandlingCode("");

    rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(deliveryDocumentList);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertFalse(deliveryDocumentLine.getIsHazmat());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletHi());
  }

  @Test
  public void
      test_updateAdditionalItemDetailsFromGDM_EmptyItemHandlingMethod_DefaultHandlingCodeEnabled()
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setHandlingCode("");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DEFAULT_ITEM_HANDLING_CODE_ENABLED,
            false))
        .thenReturn(true);

    rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(deliveryDocumentList);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertFalse(deliveryDocumentLine.getIsHazmat());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletHi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getGdmPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletTi());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPalletHi());
  }

  @Test
  public void testCheckIfDsdcInstructionAlreadyExists_SSTK_InstructionExists() {
    Instruction existingInstruction = getInstructionWithSscc();
    existingInstruction.setInstructionCode(
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    existingInstruction.setLastChangeUserId("sysadmin");

    InstructionRequest request = MockInstructionRequest.getSSCCInstructionRequest();
    when(instructionPersisterService.findInstructionByDeliveryNumberAndSscc(
            any(InstructionRequest.class)))
        .thenReturn(Arrays.asList(existingInstruction));
    Boolean isDsdcInstruction = rdcInstructionUtils.checkIfDsdcInstructionAlreadyExists(request);
    assertFalse(isDsdcInstruction);
  }

  @Test
  public void testCheckIfDsdcInstructionAlreadyExists_DSDC_InstructionExists() {
    Instruction existingInstruction = getInstructionWithSscc();
    existingInstruction.setInstructionCode(RdcInstructionType.DSDC_RECEIVING.getInstructionCode());
    existingInstruction.setActivityName("DSDC");
    existingInstruction.setLastChangeUserId("sysadmin");

    InstructionRequest request = MockInstructionRequest.getSSCCInstructionRequest();
    when(instructionPersisterService.findInstructionByDeliveryNumberAndSscc(
            any(InstructionRequest.class)))
        .thenReturn(Arrays.asList(existingInstruction));
    Boolean isDsdcInstruction = rdcInstructionUtils.checkIfDsdcInstructionAlreadyExists(request);
    assertTrue(isDsdcInstruction);
  }

  @Test
  public void testCheckIfDsdcInstructionAlreadyExists_InstructionsDoesNotExist() {
    Instruction existingInstruction = getInstructionWithSscc();
    existingInstruction.setInstructionCode(
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    existingInstruction.setActivityName("DSDC");
    existingInstruction.setLastChangeUserId("sysadmin");

    InstructionRequest request = MockInstructionRequest.getSSCCInstructionRequest();
    when(instructionPersisterService.findInstructionByDeliveryNumberAndSscc(
            any(InstructionRequest.class)))
        .thenReturn(new ArrayList<>());
    Boolean isDsdcInstruction = rdcInstructionUtils.checkIfDsdcInstructionAlreadyExists(request);
    assertFalse(isDsdcInstruction);
  }

  @Test
  public void testIsCasePackNonConveyableToShipping() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setPackTypeCode("C");
        deliveryDocumentLine.getAdditionalInfo().setHandlingCode("L");
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CL");
      }
    }

    assertTrue(
        rdcInstructionUtils.isCasePackPalletReceiving(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0)));
  }

  @Test
  public void testIsCasePackNonConveyableToShipping_CaseConveyable() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    List<String> deliveryListInDaPilotDelivery = new ArrayList<>();
    deliveryListInDaPilotDelivery.add("24356812");
    deliveryListInDaPilotDelivery.add("60032433");
    deliveryListInDaPilotDelivery.add("90876542");
    doReturn(deliveryListInDaPilotDelivery).when(rdcManagedConfig).getAtlasDaPilotDeliveries();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(3804890L);
        deliveryDocumentLine.getAdditionalInfo().setPackTypeCode("C");
        deliveryDocumentLine.getAdditionalInfo().setHandlingCode("C");
        deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
      }
    }

    assertFalse(
        rdcInstructionUtils.isCasePackPalletReceiving(
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0)));
  }

  @Test
  public void testValidateAtlasConvertedItemsWithIsOneAtlasEnabled()
      throws IOException, ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_CONVERSION_ENABLED_FOR_ALL_SSTK_ITEMS,
            false))
        .thenReturn(true);
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocumentList, headers);
    assertTrue(
        deliveryDocumentList
            .stream()
            .allMatch(
                deliveryDocument ->
                    deliveryDocument
                        .getDeliveryDocumentLines()
                        .stream()
                        .allMatch(
                            deliveryDocumentLine ->
                                deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem())));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_CONVERSION_ENABLED_FOR_ALL_SSTK_ITEMS,
            false);
  }
}
