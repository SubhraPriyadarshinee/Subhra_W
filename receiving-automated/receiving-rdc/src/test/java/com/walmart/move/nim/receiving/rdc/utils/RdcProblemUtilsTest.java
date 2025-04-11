package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.PROBLEM_CONFLICT;
import static com.walmart.move.nim.receiving.rdc.mock.data.MockReceivedQtyRespFromRds.getReceivedQtyMapForOnePoWithOneLine;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.DeliveryDocumentHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.fixit.ReportProblemRequest;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockProblemResponse;
import com.walmart.move.nim.receiving.rdc.service.NimRdsService;
import com.walmart.move.nim.receiving.rdc.service.RdcFitProblemService;
import com.walmart.move.nim.receiving.rdc.service.RdcFixitProblemService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ProblemStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcProblemUtilsTest {

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcFixitProblemService rdcFixitProblemService;
  @Mock private RdcFitProblemService rdcFitProblemService;
  @Mock private NimRdsService nimRdsService;
  @Mock private RdcInstructionUtils instructionUtils;
  @Mock private ReceiptService receiptService;
  @Mock private ProblemReceivingHelper problemReceivingHelper;
  @Mock private DeliveryService deliveryService;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock private ProblemService problemService;
  @Mock private InstructionPersisterService instructionPersisterService;

  @InjectMocks private RdcProblemUtils rdcProblemUtils;

  private Gson gson;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private static final String problemTagId = "PTAG1";
  private static final String issueId = "2033-323-32323";
  private static final String poNumber = "PO1";
  private static final Integer poLineNumber = 1;
  private HttpHeaders headers;
  private static final String PROBLEM_TAG = "testProblemTag";
  private static final String deliveryNumber = "123231212";
  private static final String poNbr = "4445530688";
  private static final String userId = "sysadmin";
  private static final String SLOT_1 = "A0001";
  private static final String OVG_TYPE = "OVG";

  @BeforeClass
  public void initMocks() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    ReflectionTestUtils.setField(rdcProblemUtils, "problemReceivingHelper", problemReceivingHelper);
    ReflectionTestUtils.setField(rdcProblemUtils, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        tenantSpecificConfigReader,
        rdcFixitProblemService,
        nimRdsService,
        rdcFitProblemService,
        instructionUtils,
        receiptService,
        problemReceivingHelper,
        deliveryService,
        problemService,
        deliveryDocumentHelper,
        instructionPersisterService);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "Error while reporting overage to Fixit")
  public void testReportErrorToProblemThrowsExceptionFromProblemResponseFixit()
      throws ReceivingException {

    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(rdcFixitProblemService.reportProblem(
            anyString(), anyString(), any(ReportProblemRequest.class)))
        .thenThrow(
            new ReceivingException(
                ReceivingException.REPORT_PROBLEM_ERROR_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR));

    rdcProblemUtils.reportErrorForProblemReceiving(
        problemTagId, issueId, poNumber, poLineNumber, headers);

    verify(rdcFixitProblemService, times(1))
        .reportProblem(anyString(), anyString(), any(ReportProblemRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "Error while reporting overage to Fixit")
  public void testReportErrorToProblemThrowsExceptionFromProblemResponseFit()
      throws ReceivingException {

    doReturn(rdcFitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(rdcFitProblemService.reportProblem(
            anyString(), anyString(), any(ReportProblemRequest.class)))
        .thenThrow(
            new ReceivingException(
                ReceivingException.REPORT_PROBLEM_ERROR_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR));

    rdcProblemUtils.reportErrorForProblemReceiving(
        problemTagId, issueId, poNumber, poLineNumber, headers);

    verify(rdcFitProblemService, times(1))
        .reportProblem(anyString(), anyString(), any(ReportProblemRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp =
          "Received maximum allowable quantity threshold for problem label: PTAG1, Please check with your supervisor")
  public void testReportErrorToProblemThrowsExceptionIssueIdExistsFixit()
      throws ReceivingException {
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(rdcFixitProblemService.reportProblem(
            anyString(), anyString(), any(ReportProblemRequest.class)))
        .thenReturn("Success");

    rdcProblemUtils.reportErrorForProblemReceiving(
        problemTagId, issueId, poNumber, poLineNumber, headers);

    verify(rdcFixitProblemService, times(1))
        .reportProblem(anyString(), anyString(), any(ReportProblemRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp =
          "Received maximum allowable quantity threshold for problem label: PTAG1, Please check with your supervisor")
  public void testReportErrorToProblemThrowsExceptionIssueIdExistsForFit()
      throws ReceivingException {
    doReturn(rdcFitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(rdcFitProblemService.reportProblem(
            anyString(), anyString(), any(ReportProblemRequest.class)))
        .thenReturn("Success");

    rdcProblemUtils.reportErrorForProblemReceiving(
        problemTagId, issueId, poNumber, poLineNumber, headers);

    verify(rdcFitProblemService, times(1))
        .reportProblem(anyString(), anyString(), any(ReportProblemRequest.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp =
          "Received maximum allowable quantity threshold for problem label: PTAG1, Please check with your supervisor")
  public void testReportErrorToProblemThrowsExceptionIssueIdNotExist() throws ReceivingException {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setIssueId("23323-2323-2323");
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(rdcFixitProblemService.reportProblem(
            anyString(), anyString(), any(ReportProblemRequest.class)))
        .thenReturn("Success");
    when(rdcFixitProblemService.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(problemLabel);

    rdcProblemUtils.reportErrorForProblemReceiving(
        problemTagId, null, poNumber, poLineNumber, headers);

    verify(rdcFixitProblemService, times(1))
        .reportProblem(anyString(), anyString(), any(ReportProblemRequest.class));
    verify(rdcFixitProblemService, times(1)).findProblemLabelByProblemTagId(anyString());
  }

  @Test
  public void testReceivedQtyByPoAndPoLine_ForAtlasConvertedItem_IsSuccess()
      throws IOException, ReceivingException {
    Resolution resolution = new Resolution();
    resolution.setResolutionPoNbr("8458708162");
    resolution.setResolutionPoLineNbr(1);

    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
    when(instructionUtils.getReceivedQtyByPoAndPoLine(
            any(DeliveryDocumentLine.class), anyString(), anyInt()))
        .thenReturn(10L);

    Long receivedQtyByPoAndPol =
        rdcProblemUtils.receivedQtyByPoAndPoLine(resolution, deliveryDocumentLine);

    assertNotNull(receivedQtyByPoAndPol);
    assertSame(receivedQtyByPoAndPol, 10L);

    verify(instructionUtils, times(1))
        .getReceivedQtyByPoAndPoLine(any(DeliveryDocumentLine.class), anyString(), anyInt());
  }

  @Test
  public void testReceivedQtyByPoAndPoLine_ForNonAtlasConvertedItem_IsSuccess()
      throws IOException, ReceivingException {
    Resolution resolution = new Resolution();
    resolution.setResolutionPoNbr("8458708162");
    resolution.setResolutionPoLineNbr(1);

    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(false);
    when(instructionUtils.getReceivedQtyByPoAndPoLine(
            any(DeliveryDocumentLine.class), anyString(), anyInt()))
        .thenReturn(20L);

    Long receivedQtyByPoAndPol =
        rdcProblemUtils.receivedQtyByPoAndPoLine(resolution, deliveryDocumentLine);

    assertNotNull(receivedQtyByPoAndPol);
    assertSame(receivedQtyByPoAndPol, 20L);

    verify(instructionUtils, times(1))
        .getReceivedQtyByPoAndPoLine(any(DeliveryDocumentLine.class), anyString(), anyInt());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceivedQtyByPoAndPoLine_ForNonAtlasConvertedItem_IsNotSuccess_WhenRdsThrowsException()
          throws IOException, ReceivingException {
    Resolution resolution = new Resolution();
    resolution.setResolutionPoNbr("8458708162");
    resolution.setResolutionPoLineNbr(1);

    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(false);

    when(instructionUtils.getReceivedQtyByPoAndPoLine(
            any(DeliveryDocumentLine.class), anyString(), anyInt()))
        .thenThrow(
            new ReceivingBadDataException(
                RdcConstants.GET_RCVD_QTY_ERROR_FROM_RDS,
                String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, "PO Not Found")));

    rdcProblemUtils.receivedQtyByPoAndPoLine(resolution, deliveryDocumentLine);

    verify(instructionUtils, times(1))
        .getReceivedQtyByPoAndPoLine(any(DeliveryDocumentLine.class), anyString(), anyInt());
  }

  @Test
  public void testGetProblemTagInfo_containerNotReceivable()
      throws ReceivingException, IOException {
    FitProblemTagResponse fitProblemTagResponse = mockFitProblemTagResponse();

    when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
        .thenReturn(false);
    try {
      rdcProblemUtils.txGetProblemTagInfo(fitProblemTagResponse, PROBLEM_TAG, headers);
    } catch (ReceivingConflictException e) {
      assertEquals(e.getDescription(), ReceivingException.PTAG_NOT_READY_TO_RECEIVE);
      assertEquals(e.getErrorCode(), PROBLEM_CONFLICT);
    }
  }

  @Test
  public void testGetProblemTagInfo_containerReceivable_nonAtlasItem()
      throws ReceivingException, IOException {
    GdmPOLineResponse gdmNonAtlasItem = mockGdmPOLineResponse();
    gdmNonAtlasItem.setDeliveryDocuments(MockDeliveryDocuments.getDeliveryDocumentsForSSTK());
    FitProblemTagResponse fitProblemTagResponse = mockFitProblemTagResponse();
    fitProblemTagResponse
        .getResolutions()
        .get(0)
        .setResolutionPoNbr(
            gdmNonAtlasItem.getDeliveryDocuments().get(0).getPurchaseReferenceNumber());
    fitProblemTagResponse
        .getResolutions()
        .get(0)
        .setResolutionPoLineNbr(
            gdmNonAtlasItem
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getPurchaseReferenceLineNumber());
    doReturn(rdcFitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
        .thenReturn(true);
    when(deliveryService.getPOLineInfoFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(mockGdmPOLineResponse());
    when(nimRdsService.getReceivedQtyByDeliveryDocumentLine(
            any(DeliveryDocumentLine.class), any(HttpHeaders.class)))
        .thenReturn(getReceivedQtyMapForOnePoWithOneLine());
    when(rdcFitProblemService.saveProblemLabel(
            anyLong(), anyString(), anyString(), anyString(), any(FitProblemTagResponse.class)))
        .thenReturn(mockProblemLabel());
    doReturn(new ProblemTagResponse())
        .when(rdcFitProblemService)
        .getConsolidatedResponse(
            any(FitProblemTagResponse.class), any(GdmPOLineResponse.class), anyLong());
    rdcProblemUtils.txGetProblemTagInfo(fitProblemTagResponse, PROBLEM_TAG, headers);
    verify(rdcFitProblemService, times(1))
        .saveProblemLabel(
            anyLong(), anyString(), anyString(), anyString(), any(FitProblemTagResponse.class));
  }

  @Test
  public void testGetProblemTagInfo_containerReceivable_atlasItem()
      throws ReceivingException, IOException {
    mockGdmPOLineResponse()
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    FitProblemTagResponse fitProblemTagResponse = mockFitProblemTagResponse();
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
        .thenReturn(true);
    when(deliveryService.getPOLineInfoFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(mockGdmPOLineResponse());
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt())).thenReturn(15L);
    when(deliveryDocumentHelper.isFirstExpiryFirstOut(anyString())).thenReturn(true);
    when(rdcFixitProblemService.saveProblemLabel(
            anyLong(), anyString(), anyString(), anyString(), any(FitProblemTagResponse.class)))
        .thenReturn(mockProblemLabel());
    doReturn(new ProblemTagResponse())
        .when(rdcFixitProblemService)
        .getConsolidatedResponse(
            any(FitProblemTagResponse.class), any(GdmPOLineResponse.class), anyLong());
    rdcProblemUtils.txGetProblemTagInfo(fitProblemTagResponse, PROBLEM_TAG, headers);
    verify(rdcFixitProblemService, times(1))
        .saveProblemLabel(
            anyLong(), anyString(), anyString(), anyString(), any(FitProblemTagResponse.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp =
          "Received maximum allowable quantity threshold for problem label: testProblemTag, Please check with your supervisor")
  public void testGetProblemTagInfo_containerReceivable_reportOverageScenario()
      throws ReceivingException, ReceivingConflictException, ReceivingInternalException,
          IOException {
    mockGdmPOLineResponse()
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    FitProblemTagResponse fitProblemTagResponse = mockFitProblemTagResponse();
    when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
        .thenReturn(true);
    when(deliveryService.getPOLineInfoFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(mockGdmPOLineResponse());
    doReturn(rdcFitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(rdcFitProblemService.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(MockProblemResponse.getMockProblemLabel());
    doReturn("Success")
        .when(rdcFitProblemService)
        .reportProblem(anyString(), anyString(), any(ReportProblemRequest.class));
    when(instructionUtils.getReceivedQtyByPoAndPoLine(
            any(DeliveryDocumentLine.class), anyString(), anyInt()))
        .thenReturn(22L);

    try {
      rdcProblemUtils.txGetProblemTagInfo(fitProblemTagResponse, PROBLEM_TAG, headers);
    } catch (ReceivingConflictException e) {
      assertEquals(e.getDescription(), ReceivingException.PTAG_RESOLVED_BUT_LINE_ALREADY_RECEIVED);
      assertEquals(e.getErrorCode(), PROBLEM_CONFLICT);
    }
  }

  private FitProblemTagResponse mockFitProblemTagResponse() throws IOException {

    FitProblemTagResponse fitProblemTagResponse = new FitProblemTagResponse();
    List<Resolution> resolutions1 = new ArrayList<>();

    Issue issue1 = new Issue();
    Resolution resolution1 = new Resolution();
    resolution1.setId("1");
    resolution1.setResolutionPoNbr(
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0).getPurchaseReferenceNumber());
    resolution1.setResolutionPoLineNbr(1);
    resolution1.setQuantity(10);
    resolution1.setAcceptedQuantity(2);
    resolution1.setRemainingQty(8);
    resolutions1.add(resolution1);
    issue1.setDeliveryNumber(deliveryNumber);
    issue1.setId(issueId);
    issue1.setItemNumber(84834L);
    issue1.setStatus(ProblemStatus.ANSWERED.toString());
    issue1.setResolutionStatus(ProblemStatus.PARTIAL_RESOLUTION.toString());
    issue1.setType(OVG_TYPE);

    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setId(1L);
    problemLabel.setProblemTagId(PROBLEM_TAG);
    problemLabel.setDeliveryNumber(Long.valueOf(deliveryNumber));
    problemLabel.setIssueId(issueId);
    problemLabel.setProblemStatus(ProblemStatus.WORKING.toString());
    problemLabel.setLastChangeUserId(userId);
    problemLabel.setCreateTs(new Date());
    problemLabel.setLastChangeTs(new Date());

    fitProblemTagResponse.setLabel(PROBLEM_TAG);
    fitProblemTagResponse.setRemainingQty(10);
    fitProblemTagResponse.setReportedQty(10);
    fitProblemTagResponse.setSlot(SLOT_1);
    fitProblemTagResponse.setIssue(issue1);
    fitProblemTagResponse.setResolutions(resolutions1);

    return fitProblemTagResponse;
  }

  private GdmPOLineResponse mockGdmPOLineResponse() {
    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setWarehouseRotationTypeCode("3");
    additionalInfo.setProfiledWarehouseArea("CPS");
    additionalInfo.setWarehouseGroupCode("F");
    additionalInfo.setWarehouseAreaCode("1");
    additionalInfo.setAtlasConvertedItem(true);
    deliveryDocumentLine.setVendorNbrDeptSeq(40);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(10);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    deliveryDocumentLine.setVendorPackCost(100F);
    deliveryDocumentLine.setWarehousePackSell(100F);

    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setPurchaseReferenceNumber(poNbr);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocument.setDeptNumber("40");
    deliveryDocuments.add(deliveryDocument);
    gdmPOLineResponse.setDeliveryNumber(Long.valueOf(deliveryNumber));
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    gdmPOLineResponse.setDeliveryDocuments(deliveryDocuments);
    return gdmPOLineResponse;
  }

  private ProblemLabel mockProblemLabel() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setId(1L);
    problemLabel.setProblemTagId(PROBLEM_TAG);
    problemLabel.setDeliveryNumber(Long.valueOf(deliveryNumber));
    problemLabel.setIssueId(issueId);
    problemLabel.setProblemStatus(ProblemStatus.WORKING.toString());
    problemLabel.setLastChangeUserId(userId);
    problemLabel.setCreateTs(new Date());
    problemLabel.setLastChangeTs(new Date());
    return problemLabel;
  }
}
