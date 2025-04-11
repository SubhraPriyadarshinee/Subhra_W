package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.fixit.ReportProblemRequest;
import com.walmart.move.nim.receiving.core.model.fixit.UserInfo;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ProblemStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ProblemServiceFixitTest extends ReceivingTestBase {

  @Mock private RestUtils restUtils;
  @Mock private AppConfig appConfig;
  @Mock private ProblemRepository problemRepository;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private ProblemReceivingHelper problemReceivingHelper;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private RetryableRestConnector retryableRestConnector;
  @InjectMocks private DeliveryServiceImpl deliveryService;
  @InjectMocks private ProblemServiceFixit problemService;

  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private Gson gson = new Gson();
  private Item item1 = new Item();
  private Item item2 = new Item();
  private Issue issue1 = new Issue();
  private Issue issue2 = new Issue();
  private Problem problem1 = new Problem();
  private Problem problem2 = new Problem();
  private Resolution resolution1 = new Resolution();
  private Resolution resolution2 = new Resolution();
  private ProblemLabel problemLabel = new ProblemLabel();
  private InboundDocument inboundDocument1 = new InboundDocument();
  private InboundDocument inboundDocument2 = new InboundDocument();
  private DeliveryDocument deliveryDocument = new DeliveryDocument();
  private List<Resolution> resolutions1 = new ArrayList<Resolution>();
  private List<Resolution> resolutions2 = new ArrayList<Resolution>();
  private GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
  private GdmPOLineResponse gdmPOLineResponseSerializedItem = new GdmPOLineResponse();
  private ProblemTagResponse problemTagResponse1 = new ProblemTagResponse();
  private ProblemTagResponse problemTagResponse2 = new ProblemTagResponse();
  private DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
  private FitProblemTagResponse fitProblemTagResponse1 = new FitProblemTagResponse();
  private FitProblemTagResponse fitProblemTagResponse2 = new FitProblemTagResponse();
  private List<DeliveryDocument> deliveryDocuments = new ArrayList<DeliveryDocument>();
  private List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<DeliveryDocumentLine>();

  private String problemTagId = "920403185";
  private String deliveryNumber = "123231212";
  private String issueId = "1aa1-2bb2-3cc3-4dd4-5ee5";
  private String poNbr = "4445530688";
  private String userId = "sysadmin";
  private String itemUpc = "00260434000001";
  private String caseUpc = "09071749713118";
  private String problemBaseUrl = "http://localhost:8080";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(problemService, "problemReceivingHelper", problemReceivingHelper);
    ReflectionTestUtils.setField(deliveryService, "gson", gson);
    ReflectionTestUtils.setField(problemService, "gson", gson);
    ReflectionTestUtils.setField(problemService, "deliveryService", deliveryService);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32898);

    // Mock problemLabel
    problemLabel.setId(1l);
    problemLabel.setProblemTagId(problemTagId);
    problemLabel.setDeliveryNumber(Long.valueOf(deliveryNumber));
    problemLabel.setIssueId(issueId);
    problemLabel.setProblemStatus(ProblemStatus.WORKING.toString());
    problemLabel.setLastChangeUserId(userId);
    problemLabel.setCreateTs(new Date());
    problemLabel.setLastChangeTs(new Date());

    // Mock partial resolution from FIT
    issue1.setDeliveryNumber(deliveryNumber);
    issue1.setId(issueId);
    issue1.setItemNumber(84834l);
    issue1.setStatus(ProblemStatus.ANSWERED.toString());
    issue1.setResolutionStatus(ProblemStatus.PARTIAL_RESOLUTION.toString());
    issue1.setType("OVG");
    resolution1.setId("1");
    resolution1.setResolutionPoNbr(poNbr);
    resolution1.setResolutionPoLineNbr(1);
    resolution1.setQuantity(10);
    resolution1.setAcceptedQuantity(2);
    resolution1.setRemainingQty(8);
    resolutions1.add(resolution1);
    fitProblemTagResponse1.setLabel(problemTagId);
    fitProblemTagResponse1.setRemainingQty(10);
    fitProblemTagResponse1.setReportedQty(10);
    fitProblemTagResponse1.setSlot("S1234");
    fitProblemTagResponse1.setIssue(issue1);
    fitProblemTagResponse1.setResolutions(resolutions1);

    // Mock complete resolution from FIT
    issue2.setDeliveryNumber(deliveryNumber);
    issue2.setId(issueId);
    issue2.setItemNumber(84834l);
    issue2.setStatus(ProblemStatus.ANSWERED.toString());
    issue2.setResolutionStatus(ProblemStatus.COMPLETE_RESOLUTON.toString());
    resolution2.setId("2");
    resolution2.setResolutionPoNbr(poNbr);
    resolution2.setResolutionPoLineNbr(1);
    resolution2.setQuantity(10);
    resolution2.setRemainingQty(10);
    resolutions2.add(resolution2);
    fitProblemTagResponse2.setLabel(problemTagId);
    fitProblemTagResponse2.setRemainingQty(10);
    fitProblemTagResponse2.setReportedQty(10);
    fitProblemTagResponse2.setSlot("S1234");
    fitProblemTagResponse2.setIssue(issue2);
    fitProblemTagResponse2.setResolutions(resolutions2);

    // Mock POLINE response from GDM
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setQtyUOM(ReceivingConstants.Uom.VNPK);
    deliveryDocumentLine.setItemNbr(84834l);
    deliveryDocumentLine.setItemUpc(itemUpc);
    deliveryDocumentLine.setCaseUpc(caseUpc);
    deliveryDocumentLine.setDescription("ITEM-DESCR-1");
    deliveryDocumentLine.setPalletHigh(5);
    deliveryDocumentLine.setPalletTie(4);
    deliveryDocumentLine.setVendorPack(1);
    deliveryDocumentLine.setWarehousePack(1);
    deliveryDocumentLine.setVendorPackCost(1f);
    deliveryDocumentLine.setWarehousePackSell(1f);
    deliveryDocumentLine.setCurrency("USD");
    deliveryDocumentLine.setOverageQtyLimit(0);
    deliveryDocumentLine.setWeight(1.00f);
    deliveryDocumentLine.setWeightUom("LB");
    deliveryDocumentLine.setCube(1.00f);
    deliveryDocumentLine.setCubeUom("INCH");
    deliveryDocumentLine.setSecondaryDescription("ITEM-DESCR-2");

    ItemData additionalInfo = new ItemData();
    additionalInfo.setWarehouseRotationTypeCode("3");
    additionalInfo.setProfiledWarehouseArea("CPS");
    additionalInfo.setWarehouseGroupCode("F");
    additionalInfo.setWarehouseAreaCode("1");
    deliveryDocumentLine.setVendorNbrDeptSeq(40);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setPurchaseReferenceNumber(poNbr);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocument.setDeptNumber("40");
    deliveryDocuments.add(deliveryDocument);
    gdmPOLineResponse.setDeliveryNumber(Long.valueOf(deliveryNumber));
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    gdmPOLineResponse.setDeliveryDocuments(deliveryDocuments);

    gdmPOLineResponseSerializedItem.setDeliveryNumber(Long.valueOf(deliveryNumber));
    gdmPOLineResponseSerializedItem.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    List<DeliveryDocumentLine> deliveryDocumentLines2 = new ArrayList<DeliveryDocumentLine>();
    DeliveryDocumentLine deliveryDocumentLine2 = new DeliveryDocumentLine();
    List<DeliveryDocument> deliveryDocuments2 = new ArrayList<DeliveryDocument>();
    DeliveryDocument deliveryDocument2 = new DeliveryDocument();

    deliveryDocumentLine2.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine2.setTotalOrderQty(10);
    deliveryDocumentLine2.setQtyUOM(ReceivingConstants.Uom.VNPK);
    deliveryDocumentLine2.setItemNbr(84834l);
    deliveryDocumentLine2.setItemUpc(itemUpc);
    deliveryDocumentLine2.setCaseUpc(caseUpc);
    deliveryDocumentLine2.setDescription("ITEM-DESCR-1");
    deliveryDocumentLine2.setPalletHigh(5);
    deliveryDocumentLine2.setPalletTie(4);
    deliveryDocumentLine2.setVendorPack(1);
    deliveryDocumentLine2.setWarehousePack(1);
    deliveryDocumentLine2.setVendorPackCost(1f);
    deliveryDocumentLine2.setWarehousePackSell(1f);
    deliveryDocumentLine2.setCurrency("USD");
    deliveryDocumentLine2.setOverageQtyLimit(0);
    deliveryDocumentLine2.setWeight(1.00f);
    deliveryDocumentLine2.setWeightUom("LB");
    deliveryDocumentLine2.setCube(1.00f);
    deliveryDocumentLine2.setCubeUom("INCH");
    deliveryDocumentLine2.setSecondaryDescription("ITEM-DESCR-2");
    deliveryDocumentLine2.setVendorNbrDeptSeq(38);
    deliveryDocumentLine2.setAdditionalInfo(additionalInfo);
    deliveryDocumentLine2.setDeptNumber("38");
    deliveryDocumentLine2.setBolWeight(1.0f);
    deliveryDocumentLine2.setFreightBillQty(8);
    deliveryDocumentLines2.add(deliveryDocumentLine2);
    deliveryDocument2.setPurchaseReferenceNumber(poNbr);
    deliveryDocument2.setDeliveryDocumentLines(deliveryDocumentLines2);
    deliveryDocuments2.add(deliveryDocument2);
    deliveryDocument2.setDeptNumber("38");
    gdmPOLineResponseSerializedItem.setDeliveryDocuments(deliveryDocuments2);

    // Mock PTAG response back to client
    problem1.setProblemTagId(problemTagId);
    problem1.setDeliveryNumber(deliveryNumber);
    problem1.setIssueId(issueId);
    problem1.setResolutionId("1");
    problem1.setResolutionQty(8);
    problem1.setSlotId("S1234");
    problem1.setReceivedQty(2);
    inboundDocument1.setPurchaseReferenceNumber(poNbr);
    inboundDocument1.setPurchaseReferenceLineNumber(1);
    inboundDocument1.setExpectedQty(10);
    inboundDocument1.setVendorPack(1);
    inboundDocument1.setWarehousePack(1);
    inboundDocument1.setExpectedQtyUOM(ReceivingConstants.Uom.VNPK);
    inboundDocument1.setVendorPackCost(1.00);
    inboundDocument1.setWhpkSell(1.00);
    inboundDocument1.setCurrency("USD");
    inboundDocument1.setOverageQtyLimit(0);
    inboundDocument1.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    inboundDocument1.setWeight(1.00f);
    inboundDocument1.setWeightUom("LB");
    inboundDocument1.setCube(1.00f);
    inboundDocument1.setCubeUom("INCH");
    inboundDocument1.setDescription("ITEM-DESCR-1");
    inboundDocument1.setSecondaryDescription("ITEM-DESCR-2");
    inboundDocument1.setDepartment("40");
    item1.setNumber(84834l);
    item1.setDescription("ITEM-DESCR-1");
    item1.setPalletTi(4);
    item1.setPalletHi(5);
    item1.setSecondaryDescription("ITEM-DESCR-2");
    item1.setCube(1.00f);
    item1.setCubeUom("INCH");
    item1.setWeight(1.00f);
    item1.setWeightUom("LB");
    item1.setDeptNumber("40");
    problemTagResponse1.setProblem(problem1);
    problemTagResponse1.setInboundDocument(inboundDocument1);
    problemTagResponse1.setItem(item1);

    problem2.setProblemTagId(problemTagId);
    problem2.setDeliveryNumber(deliveryNumber);
    problem2.setIssueId(issueId);
    problem2.setResolutionId("2");
    problem2.setResolutionQty(10);
    problem2.setSlotId("S1234");
    problem2.setReceivedQty(0);
    inboundDocument2.setPurchaseReferenceNumber(poNbr);
    inboundDocument2.setPurchaseReferenceLineNumber(1);
    inboundDocument2.setExpectedQty(10);
    inboundDocument2.setVendorPack(1);
    inboundDocument2.setWarehousePack(1);
    inboundDocument2.setExpectedQtyUOM(ReceivingConstants.Uom.VNPK);
    inboundDocument2.setVendorPackCost(1.00);
    inboundDocument2.setWhpkSell(1.00);
    inboundDocument2.setCurrency("USD");
    inboundDocument2.setOverageQtyLimit(0);
    inboundDocument2.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    inboundDocument2.setWeight(1.00f);
    inboundDocument2.setWeightUom("LB");
    inboundDocument2.setCube(1.00f);
    inboundDocument2.setCubeUom("INCH");
    inboundDocument2.setDescription("ITEM-DESCR-1");
    inboundDocument2.setSecondaryDescription("ITEM-DESCR-2");
    item2.setNumber(84834l);
    item2.setDescription("ITEM-DESCR-1");
    item2.setPalletTi(4);
    item2.setPalletHi(5);
    item2.setSecondaryDescription("ITEM-DESCR-2");
    item2.setCube(1.00f);
    item2.setCubeUom("INCH");
    item2.setWeight(1.00f);
    item2.setWeightUom("LB");
    problemTagResponse2.setProblem(problem2);
    problemTagResponse2.setInboundDocument(inboundDocument2);
    problemTagResponse2.setItem(item2);
  }

  @BeforeMethod
  public void beforeMethod() throws ReceivingException {
    when(deliveryDocumentHelper.isFirstExpiryFirstOut(anyString())).thenReturn(Boolean.TRUE);
  }

  @AfterMethod
  public void tearDown() {
    reset(
        restUtils,
        receiptService,
        problemRepository,
        deliveryStatusPublisher,
        configUtils,
        problemReceivingHelper,
        appConfig);
  }

  @Test
  public void testGetProblemTagInfo_IfGetProblemDetailsFromFITReturnException()
      throws ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            new ResponseEntity<String>("Internal Server Error.", HttpStatus.INTERNAL_SERVER_ERROR));

    try {
      problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
      assertEquals(ReceivingException.GET_PTAG_ERROR_CODE, e.getErrorResponse().getErrorCode());
      assertEquals(ReceivingException.PTAG_NOT_FOUND, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testGetProblemTagInfo_FitServiceDown() throws ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            new ResponseEntity<String>(
                "Error in fetching resource.", HttpStatus.SERVICE_UNAVAILABLE));

    try {
      problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getHttpStatus());
      assertEquals(ReceivingException.GET_PTAG_ERROR_CODE, e.getErrorResponse().getErrorCode());
      assertEquals(ReceivingException.FIXIT_SERVICE_DOWN, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testGetProblemTagInfo_IfGetProblemDetailsFromFITReturnFailure()
      throws ReceivingException {
    when(restUtils.get(anyString(), any(), any())).thenReturn(ResponseEntity.ok(""));

    try {
      problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
      assertEquals(ReceivingException.GET_PTAG_ERROR_CODE, e.getErrorResponse().getErrorCode());
      assertEquals(ReceivingException.PTAG_NOT_FOUND, e.getErrorResponse().getErrorMessage());
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testGetProblemTagInfo_ifContainerNotReceivable() throws ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            ResponseEntity.ok(gson.toJson(fitProblemTagResponse1)),
            ResponseEntity.ok(gson.toJson(gdmPOLineResponse)));

    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    doReturn(false).when(problemReceivingHelper).isContainerReceivable(any());
    when(problemRepository.save(problemLabel)).thenReturn(problemLabel);
    try {
      problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
      assertEquals(ReceivingException.GET_PTAG_ERROR_CODE, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.PTAG_NOT_READY_TO_RECEIVE, e.getErrorResponse().getErrorMessage());
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testGetProblemTagInfo_ifContainerReceivableAndGdmHasPoInfo()
      throws ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            ResponseEntity.ok(gson.toJson(fitProblemTagResponse1)),
            ResponseEntity.ok(gson.toJson(gdmPOLineResponse)));

    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    when(problemRepository.save(problemLabel)).thenReturn(problemLabel);

    ProblemTagResponse response = problemService.txGetProblemTagInfo(problemTagId, httpHeaders);

    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(problemRepository, times(1)).save(problemLabel);

    assertEquals(response.getItem().getGtin(), itemUpc);
    assertEquals(response.getInboundDocument().getGtin(), itemUpc);
    assertEquals(response.getDeliveryDocumentLine().getGtin(), itemUpc);
    assertNotNull(response.getProblem().getReceivedQty());

    assertEquals(problemTagResponse1.getItem().getDeptNumber(), response.getItem().getDeptNumber());
    assertEquals(
        problemTagResponse1.getItem().getDescription(), response.getItem().getDescription());
    assertEquals(problemTagResponse1.getItem().getNumber(), response.getItem().getNumber());
    assertEquals(problemTagResponse1.getItem().getPalletHi(), response.getItem().getPalletHi());
    assertEquals(problemTagResponse1.getItem().getPalletTi(), response.getItem().getPalletTi());
    assertEquals(
        problemTagResponse1.getProblem().getDeliveryNumber(),
        response.getProblem().getDeliveryNumber());
    assertEquals(
        problemTagResponse1.getProblem().getProblemTagId(),
        response.getProblem().getProblemTagId());
    assertEquals(
        problemTagResponse1.getProblem().getResolutionId(),
        response.getProblem().getResolutionId());
    assertEquals(
        problemTagResponse1.getProblem().getResolutionQty(),
        response.getProblem().getResolutionQty());
    assertEquals(
        problemTagResponse1.getProblem().getReceivedQty(), response.getProblem().getReceivedQty());
    assertEquals(problemTagResponse1.getProblem().getSlotId(), response.getProblem().getSlotId());
    assertEquals(
        problemTagResponse1.getInboundDocument().getBaseDivisionCode(),
        response.getInboundDocument().getBaseDivisionCode());
    assertEquals(
        problemTagResponse1.getInboundDocument().getDepartment(),
        response.getInboundDocument().getDepartment());
    assertEquals(
        problemTagResponse1.getInboundDocument().getEvent(),
        response.getInboundDocument().getEvent());
    assertEquals(
        problemTagResponse1.getInboundDocument().getExpectedQty(),
        response.getInboundDocument().getExpectedQty());
    assertEquals(
        problemTagResponse1.getInboundDocument().getExpectedQtyUOM(),
        response.getInboundDocument().getExpectedQtyUOM());
    assertEquals(
        problemTagResponse1.getInboundDocument().getFinancialReportingGroup(),
        response.getInboundDocument().getFinancialReportingGroup());
    assertEquals(
        problemTagResponse1.getInboundDocument().getInboundChannelType(),
        response.getInboundDocument().getInboundChannelType());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPoDcNumber(),
        response.getInboundDocument().getPoDcNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseCompanyId(),
        response.getInboundDocument().getPurchaseCompanyId());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseReferenceLegacyType(),
        response.getInboundDocument().getPurchaseReferenceLegacyType());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseReferenceLineNumber(),
        response.getInboundDocument().getPurchaseReferenceLineNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseReferenceNumber(),
        response.getInboundDocument().getPurchaseReferenceNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getVendorNumber(),
        response.getInboundDocument().getVendorNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getVendorPack(),
        response.getInboundDocument().getVendorPack());
    assertEquals(
        problemTagResponse1.getInboundDocument().getVendorPackCost(),
        response.getInboundDocument().getVendorPackCost());
    assertEquals(
        problemTagResponse1.getInboundDocument().getWarehousePack(),
        response.getInboundDocument().getWarehousePack());
    assertEquals(
        problemTagResponse1.getInboundDocument().getWhpkSell(),
        response.getInboundDocument().getWhpkSell());
    assertEquals(Boolean.TRUE, response.getDeliveryDocumentLine().getFirstExpiryFirstOut());
    assertEquals(
        "CPS", response.getDeliveryDocumentLine().getAdditionalInfo().getProfiledWarehouseArea());
    assertEquals(
        "1", response.getDeliveryDocumentLine().getAdditionalInfo().getWarehouseAreaCode());
    assertEquals(
        "F", response.getDeliveryDocumentLine().getAdditionalInfo().getWarehouseGroupCode());
  }

  @Test
  public void testGetProblemTagInfoWithIssueType_ifContainerReceivableAndGdmHasPoInfo()
      throws ReceivingException {

    gdmPOLineResponseSerializedItem
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setIsDscsaExemptionInd(true);

    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            ResponseEntity.ok(gson.toJson(fitProblemTagResponse1)),
            ResponseEntity.ok(gson.toJson(gdmPOLineResponse)));

    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    doReturn(true).when(configUtils).getConfiguredFeatureFlag(anyString(), anyString());
    when(problemRepository.save(problemLabel)).thenReturn(problemLabel);

    ProblemTagResponse response = problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(problemRepository, times(1)).save(problemLabel);

    assertEquals(response.getItem().getGtin(), itemUpc);
    assertEquals(response.getInboundDocument().getGtin(), itemUpc);
    assertEquals(response.getDeliveryDocumentLine().getGtin(), itemUpc);
    assertNotNull(response.getProblem().getReceivedQty());

    assertEquals(
        problemTagResponse1.getItem().getDescription(), response.getItem().getDescription());
    assertEquals(problemTagResponse1.getItem().getNumber(), response.getItem().getNumber());
    assertEquals(problemTagResponse1.getItem().getPalletHi(), response.getItem().getPalletHi());
    assertEquals(problemTagResponse1.getItem().getPalletTi(), response.getItem().getPalletTi());
    assertEquals(
        problemTagResponse1.getProblem().getDeliveryNumber(),
        response.getProblem().getDeliveryNumber());
    assertEquals(
        problemTagResponse1.getProblem().getProblemTagId(),
        response.getProblem().getProblemTagId());
    assertEquals(
        problemTagResponse1.getProblem().getResolutionId(),
        response.getProblem().getResolutionId());
    assertEquals(
        problemTagResponse1.getProblem().getResolutionQty(),
        response.getProblem().getResolutionQty());
    assertEquals(
        problemTagResponse1.getProblem().getReceivedQty(), response.getProblem().getReceivedQty());
    assertEquals(problemTagResponse1.getProblem().getSlotId(), response.getProblem().getSlotId());
    assertEquals(
        problemTagResponse1.getInboundDocument().getBaseDivisionCode(),
        response.getInboundDocument().getBaseDivisionCode());
    assertEquals("40", response.getInboundDocument().getDepartment());
    assertEquals(
        problemTagResponse1.getInboundDocument().getEvent(),
        response.getInboundDocument().getEvent());
    assertEquals(
        problemTagResponse1.getInboundDocument().getExpectedQty(),
        response.getInboundDocument().getExpectedQty());
    assertEquals(
        problemTagResponse1.getInboundDocument().getExpectedQtyUOM(),
        response.getInboundDocument().getExpectedQtyUOM());
    assertEquals(
        problemTagResponse1.getInboundDocument().getFinancialReportingGroup(),
        response.getInboundDocument().getFinancialReportingGroup());
    assertEquals(
        problemTagResponse1.getInboundDocument().getInboundChannelType(),
        response.getInboundDocument().getInboundChannelType());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPoDcNumber(),
        response.getInboundDocument().getPoDcNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseCompanyId(),
        response.getInboundDocument().getPurchaseCompanyId());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseReferenceLegacyType(),
        response.getInboundDocument().getPurchaseReferenceLegacyType());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseReferenceLineNumber(),
        response.getInboundDocument().getPurchaseReferenceLineNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseReferenceNumber(),
        response.getInboundDocument().getPurchaseReferenceNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getVendorNumber(),
        response.getInboundDocument().getVendorNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getVendorPack(),
        response.getInboundDocument().getVendorPack());
    assertEquals(
        problemTagResponse1.getInboundDocument().getVendorPackCost(),
        response.getInboundDocument().getVendorPackCost());
    assertEquals(
        problemTagResponse1.getInboundDocument().getWarehousePack(),
        response.getInboundDocument().getWarehousePack());
    assertEquals(
        problemTagResponse1.getInboundDocument().getWhpkSell(),
        response.getInboundDocument().getWhpkSell());
    assertEquals(Boolean.TRUE, response.getDeliveryDocumentLine().getFirstExpiryFirstOut());
    assertEquals(
        "CPS", response.getDeliveryDocumentLine().getAdditionalInfo().getProfiledWarehouseArea());
    assertEquals(
        "1", response.getDeliveryDocumentLine().getAdditionalInfo().getWarehouseAreaCode());
    assertEquals(
        "F", response.getDeliveryDocumentLine().getAdditionalInfo().getWarehouseGroupCode());
    assertEquals(response.getProblem().getResolution(), ReceivingConstants.PROBLEM_RECEIVE_UPC);
  }

  @Test
  public void
      testGetProblemTagInfoWithIssueTypeReceiveSerialized_ifContainerReceivableAndGdmHasPoInfo()
          throws ReceivingException {

    gdmPOLineResponseSerializedItem
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setIsDscsaExemptionInd(false);

    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            ResponseEntity.ok(gson.toJson(fitProblemTagResponse1)),
            ResponseEntity.ok(gson.toJson(gdmPOLineResponseSerializedItem)));

    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    doReturn(true).when(configUtils).getConfiguredFeatureFlag(anyString(), anyString());
    when(problemRepository.save(problemLabel)).thenReturn(problemLabel);

    ProblemTagResponse response = problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(problemRepository, times(1)).save(problemLabel);

    assertEquals(response.getItem().getGtin(), itemUpc);
    assertEquals(response.getInboundDocument().getGtin(), itemUpc);
    assertEquals(response.getDeliveryDocumentLine().getGtin(), itemUpc);

    assertEquals(ReceivingConstants.SERIALIZED_DEPT_TYPE, response.getItem().getDeptNumber());
    assertEquals(
        problemTagResponse1.getItem().getDescription(), response.getItem().getDescription());
    assertEquals(problemTagResponse1.getItem().getNumber(), response.getItem().getNumber());
    assertEquals(problemTagResponse1.getItem().getPalletHi(), response.getItem().getPalletHi());
    assertEquals(problemTagResponse1.getItem().getPalletTi(), response.getItem().getPalletTi());
    assertEquals(
        problemTagResponse1.getProblem().getDeliveryNumber(),
        response.getProblem().getDeliveryNumber());
    assertEquals(
        problemTagResponse1.getProblem().getProblemTagId(),
        response.getProblem().getProblemTagId());
    assertEquals(
        problemTagResponse1.getProblem().getResolutionId(),
        response.getProblem().getResolutionId());
    assertEquals(
        problemTagResponse1.getProblem().getResolutionQty(),
        response.getProblem().getResolutionQty());
    assertEquals(problemTagResponse1.getProblem().getSlotId(), response.getProblem().getSlotId());
    assertEquals(
        problemTagResponse1.getInboundDocument().getBaseDivisionCode(),
        response.getInboundDocument().getBaseDivisionCode());
    assertEquals(
        ReceivingConstants.SERIALIZED_DEPT_TYPE, response.getInboundDocument().getDepartment());
    assertEquals(
        problemTagResponse1.getInboundDocument().getEvent(),
        response.getInboundDocument().getEvent());
    assertEquals(
        problemTagResponse1.getInboundDocument().getExpectedQty(),
        response.getInboundDocument().getExpectedQty());
    assertEquals(
        problemTagResponse1.getInboundDocument().getExpectedQtyUOM(),
        response.getInboundDocument().getExpectedQtyUOM());
    assertEquals(
        problemTagResponse1.getInboundDocument().getFinancialReportingGroup(),
        response.getInboundDocument().getFinancialReportingGroup());
    assertEquals(
        problemTagResponse1.getInboundDocument().getInboundChannelType(),
        response.getInboundDocument().getInboundChannelType());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPoDcNumber(),
        response.getInboundDocument().getPoDcNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseCompanyId(),
        response.getInboundDocument().getPurchaseCompanyId());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseReferenceLegacyType(),
        response.getInboundDocument().getPurchaseReferenceLegacyType());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseReferenceLineNumber(),
        response.getInboundDocument().getPurchaseReferenceLineNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getPurchaseReferenceNumber(),
        response.getInboundDocument().getPurchaseReferenceNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getVendorNumber(),
        response.getInboundDocument().getVendorNumber());
    assertEquals(
        problemTagResponse1.getInboundDocument().getVendorPack(),
        response.getInboundDocument().getVendorPack());
    assertEquals(
        problemTagResponse1.getInboundDocument().getVendorPackCost(),
        response.getInboundDocument().getVendorPackCost());
    assertEquals(
        problemTagResponse1.getInboundDocument().getWarehousePack(),
        response.getInboundDocument().getWarehousePack());
    assertEquals(
        problemTagResponse1.getInboundDocument().getWhpkSell(),
        response.getInboundDocument().getWhpkSell());
    assertEquals(Boolean.TRUE, response.getDeliveryDocumentLine().getFirstExpiryFirstOut());
    assertEquals(
        "CPS", response.getDeliveryDocumentLine().getAdditionalInfo().getProfiledWarehouseArea());
    assertEquals(
        "1", response.getDeliveryDocumentLine().getAdditionalInfo().getWarehouseAreaCode());
    assertEquals(
        "F", response.getDeliveryDocumentLine().getAdditionalInfo().getWarehouseGroupCode());
    assertEquals(
        response.getProblem().getResolution(), ReceivingConstants.PROBLEM_RECEIVE_SERIALIZED);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testGetProblemTagInfo_ifContainerReceivableAndGdmHasNoPoInfo() {

    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            ResponseEntity.ok(gson.toJson(fitProblemTagResponse1)),
            new ResponseEntity<String>("Internal Server Error.", HttpStatus.INTERNAL_SERVER_ERROR));

    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    try {
      problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
      assertEquals(ReceivingException.GET_PTAG_ERROR_CODE, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.PTAG_NOT_READY_TO_RECEIVE, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testGetProblemDetailsFromFIT_returnException() {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<String>("Unkown Error", HttpStatus.NOT_FOUND));
    try {
      problemService.getProblemDetails(problemTagId);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testGetProblemTagInfo_ifContainerReceivableAndQtyNotAvailableInGDM() {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            ResponseEntity.ok(gson.toJson(fitProblemTagResponse1)),
            ResponseEntity.ok(gson.toJson(gdmPOLineResponse)));

    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    doReturn(10L).when(receiptService).getReceivedQtyByPoAndPoLine("4445530688", 1);

    try {
      problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
      assertEquals(ReceivingException.GET_PTAG_ERROR_CODE, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.PTAG_RESOLVED_BUT_LINE_ALREADY_RECEIVED,
          e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testGetProblemTagInfoWithRejectedLine() throws ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            ResponseEntity.ok(gson.toJson(fitProblemTagResponse1)),
            ResponseEntity.ok(gson.toJson(MockGdmResponse.getRejectedLine())));
    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());

    try {
      problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
      assertEquals(ReceivingException.GET_PTAG_ERROR_CODE, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.PTAG_RESOLVED_BUT_LINE_REJECTED,
          e.getErrorResponse().getErrorMessage());
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testGetProblemTagInfo_IgnoreOveragesForGrocery() throws ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            ResponseEntity.ok(gson.toJson(fitProblemTagResponse1)),
            ResponseEntity.ok(gson.toJson(gdmPOLineResponseSerializedItem)));

    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    doReturn(20L).when(receiptService).getReceivedQtyByPoAndPoLine("4445530688", 1);
    when(problemRepository.save(problemLabel)).thenReturn(problemLabel);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());

    ProblemTagResponse response = problemService.txGetProblemTagInfo(problemTagId, httpHeaders);

    assertEquals(response.getDeliveryDocumentLine().getBolWeight(), 1.00f);
    assertEquals(response.getDeliveryDocumentLine().getPalletTie().intValue(), 4);
    assertEquals(response.getDeliveryDocumentLine().getPalletHigh().intValue(), 5);
    assertEquals(response.getDeliveryDocumentLine().getOpenQty().intValue(), 0);
    assertEquals(response.getDeliveryDocumentLine().getQuantity().intValue(), 10);
    assertEquals(response.getDeliveryDocumentLine().getTotalOrderQty().intValue(), 10);
    assertEquals(response.getDeliveryDocumentLine().getFreightBillQty().intValue(), 8);

    verify(restUtils, times(2)).get(anyString(), any(), any());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(problemRepository, times(1)).save(problemLabel);
  }

  @Test
  public void testGetProblemTagInfo_calculateOpenQty() throws ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            ResponseEntity.ok(gson.toJson(fitProblemTagResponse1)),
            ResponseEntity.ok(gson.toJson(gdmPOLineResponseSerializedItem)));

    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    doReturn(4L).when(receiptService).getReceivedQtyByPoAndPoLine("4445530688", 1);
    when(problemRepository.save(problemLabel)).thenReturn(problemLabel);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());

    ProblemTagResponse response = problemService.txGetProblemTagInfo(problemTagId, httpHeaders);

    assertEquals(response.getDeliveryDocumentLine().getBolWeight(), 1.00f);
    assertEquals(response.getDeliveryDocumentLine().getPalletTie().intValue(), 4);
    assertEquals(response.getDeliveryDocumentLine().getPalletHigh().intValue(), 5);
    assertEquals(response.getDeliveryDocumentLine().getOpenQty().intValue(), 6);
    assertEquals(response.getDeliveryDocumentLine().getQuantity().intValue(), 10);
    assertEquals(response.getDeliveryDocumentLine().getTotalOrderQty().intValue(), 10);
    assertEquals(response.getDeliveryDocumentLine().getFreightBillQty().intValue(), 8);
  }

  @Test
  public void testCompleteProblemTag_returnSuccess() throws ReceivingException {
    List<ReceiptSummaryResponse> receipts = new ArrayList<>();
    when(receiptService.getReceivedQtySummaryByPOForDelivery(anyLong(), anyString()))
        .thenReturn(receipts);
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(ResponseEntity.ok("Successfully updated"));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), any()))
        .thenReturn(new DeliveryInfo());
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    // For getting delivery by delivery number
    when(restUtils.get(anyString(), any(), anyMap()))
        .thenReturn(ResponseEntity.ok("{\"deliveryNumber\":1234,\"deliveryStatus\":\"OPN\"}"));

    problemService.completeProblemTag(problemTagId, problem1, httpHeaders);

    verify(receiptService, times(1)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());
    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
  }

  @Test
  public void testCompleteProblemTag_whenDeliveryInWrkStatus() throws ReceivingException {
    List<ReceiptSummaryResponse> receipts = new ArrayList<>();
    when(receiptService.getReceivedQtySummaryByPOForDelivery(anyLong(), anyString()))
        .thenReturn(receipts);
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(ResponseEntity.ok("Successfully updated"));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), any()))
        .thenReturn(new DeliveryInfo());
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    // For getting delivery by delivery number
    when(restUtils.get(anyString(), any(), anyMap()))
        .thenReturn(ResponseEntity.ok("{\"deliveryNumber\":1234,\"deliveryStatus\":\"WRK\"}"));
    problemService.completeProblemTag(problemTagId, problem1, httpHeaders);

    verify(receiptService, times(1)).getReceivedQtySummaryByPOForDelivery(anyLong(), anyString());
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());
    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
  }

  @Test
  public void testCompleteProblemTag_returnFailureWithNull() {
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(null);

    try {
      problemService.completeProblemTag(problemTagId, problem1, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getHttpStatus());
      assertEquals(ReceivingException.PTAG_NOT_FOUND, e.getMessage());
    }
  }

  @Test
  public void testCompleteProblemTag_FitServiceDown() {
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    when(receiptService.getReceivedQtyByProblemId(any())).thenReturn(Long.parseLong("10"));
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(
            new ResponseEntity<String>(
                "Error in fetching resource.", HttpStatus.SERVICE_UNAVAILABLE));

    try {
      problemService.completeProblemTag(problemTagId, problem1, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getHttpStatus());
      assertEquals(ReceivingException.FIXIT_SERVICE_DOWN, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testNotifyCompleteProblemTagThrowException() {
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    when(receiptService.getReceivedQtyByProblemId(any())).thenReturn(Long.parseLong("10"));
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(new ResponseEntity<String>("Unkown Error", HttpStatus.INTERNAL_SERVER_ERROR));
    try {
      problemService.completeProblemTag(problemTagId, problem1, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getHttpStatus());
    }
  }

  @Test
  public void testCreateProblem_FitServiceDown() {
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(
            new ResponseEntity<String>(
                "Error in fetching resource.", HttpStatus.SERVICE_UNAVAILABLE));

    try {
      problemService.createProblemTag("create problem payload");
      fail("Service down exception is supposed to be thrown");
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getHttpStatus());
      assertEquals(ReceivingException.FIXIT_SERVICE_DOWN, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testCreateProblem_BadRequest() {
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<String>("Bad request", HttpStatus.BAD_REQUEST));
    try {
      problemService.createProblemTag("create problem payload");
      fail("Bad request exception is supposed to be thrown");
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
      assertEquals(
          ReceivingException.CREATE_PTAG_ERROR_CODE_FIXIT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.CREATE_PTAG_ERROR_MESSAGE, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testCreateProblem_NotFound() {
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<String>("Not found", HttpStatus.NOT_FOUND));
    try {
      problemService.createProblemTag("create problem payload");
      fail("Not found exception is supposed to be thrown");
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
      assertEquals(
          ReceivingException.CREATE_PTAG_ERROR_CODE_FIXIT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.CREATE_PTAG_ERROR_MESSAGE, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testCreateProblem_Conflict() {
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<String>("Conflict", HttpStatus.CONFLICT));
    try {
      problemService.createProblemTag("create problem payload");
      fail("Conflict exception is supposed to be thrown");
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
      assertEquals(
          ReceivingException.CREATE_PTAG_ERROR_CODE_FIXIT, e.getErrorResponse().getErrorCode());
      assertEquals(
          ReceivingException.CREATE_PTAG_ERROR_MESSAGE, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testCreateProblem_OtherExceptions() {
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<String>("Random error", HttpStatus.FORBIDDEN));
    try {
      problemService.createProblemTag("create problem payload");
      fail("Internal server error is supposed to be thrown");
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getHttpStatus());
      assertEquals(
          ReceivingException.CREATE_PTAG_ERROR_MESSAGE, e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testCreateProblemWhenFixitSuccessful() throws ReceivingException {
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<>("create problem response", HttpStatus.OK));

    problemService.createProblemTag("create problem payload");
    verify(restUtils, times(1)).post(anyString(), any(), any(), any());
  }

  @Test
  public void testReportProblemHappyPath() throws ReceivingException {

    when(appConfig.getFixitPlatformBaseUrl()).thenReturn(problemBaseUrl);
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(new ResponseEntity<String>("Success", HttpStatus.OK));
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);

    try {
      String reportProblemResponse =
          problemService.reportProblem("PTAG1", "2324-43423", getMockReportProblemRequest());
    } catch (Exception excp) {
      assertNull(excp);
    }

    verify(appConfig, times(1)).getFixitPlatformBaseUrl();
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReportProblemThrowsExceptionForBadDataOrConflict() throws ReceivingException {

    when(appConfig.getFixitPlatformBaseUrl()).thenReturn(problemBaseUrl);
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(new ResponseEntity<String>("Failure", HttpStatus.CONFLICT));
    problemService.reportProblem("PTAG1", "2324-43423", getMockReportProblemRequest());

    verify(appConfig, times(1)).getFixitPlatformBaseUrl();
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReportProblemThrowsExceptionForBadGateway() throws ReceivingException {

    when(appConfig.getFixitPlatformBaseUrl()).thenReturn(problemBaseUrl);
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(new ResponseEntity<String>("Failure", HttpStatus.BAD_GATEWAY));

    problemService.reportProblem("PTAG1", "2324-43423", getMockReportProblemRequest());

    verify(appConfig, times(1)).getFixitPlatformBaseUrl();
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
  }

  private ReportProblemRequest getMockReportProblemRequest() {
    UserInfo userInfo = new UserInfo();
    userInfo.setUserId("sysadmin");
    String errorMessage = "Problem Receive Error";
    return ReportProblemRequest.builder().userInfo(userInfo).errorMessage(errorMessage).build();
  }
}
