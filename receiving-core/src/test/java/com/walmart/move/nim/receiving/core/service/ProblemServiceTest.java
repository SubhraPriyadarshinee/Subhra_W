package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.*;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants.FIT_BAD_DATA_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants.GENERIC_ERROR_RE_TRY_OR_REPORT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.fixit.ReportProblemRequest;
import com.walmart.move.nim.receiving.core.model.fixit.UserInfo;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ProblemStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ProblemServiceTest extends ReceivingTestBase {

  public static final String HTTPS_FIT_BASE_URL_DEV = "https://fit-dev2.prod.us.walmart.net";
  public static final String WM_RCV_CONSUMER_ID = "5e17f2df-cc5e-40e1-a5b2-c4899d6c190b";
  public static final String FIXIT_PLATFORM = "FIXIT-PLATFORM";
  public static final String FIXIT_DEV = "FIXIT-DEV";
  @Mock private RestUtils restUtils;

  @Mock AppConfig appConfig;
  @Mock private ProblemRepository problemRepository;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @InjectMocks private ProblemService problemService;
  @Mock private ProblemReceivingHelper problemReceivingHelper;
  @InjectMocks private DeliveryServiceImpl deliveryService;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private InstructionPersisterService instructionPersisterService;

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
  private Resolution resolution3 = new Resolution();
  private ProblemLabel problemLabel = new ProblemLabel();
  private InboundDocument inboundDocument1 = new InboundDocument();
  private InboundDocument inboundDocument2 = new InboundDocument();
  private DeliveryDocument deliveryDocument = new DeliveryDocument();

  private DeliveryDocument deliveryDocument3 = new DeliveryDocument();
  private List<Resolution> resolutions1 = new ArrayList<Resolution>();
  private List<Resolution> resolutions2 = new ArrayList<Resolution>();
  private List<Resolution> resolutions3 = new ArrayList<>();
  private GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();

  private GdmPOLineResponse gdmPOLineResponse1 = new GdmPOLineResponse();
  private GdmPOLineResponse gdmPOLineResponse2 = new GdmPOLineResponse();

  private GdmPOLineResponse gdmPOLineResponseSerializedItem = new GdmPOLineResponse();
  private ProblemTagResponse problemTagResponse1 = new ProblemTagResponse();
  private ProblemTagResponse problemTagResponse2 = new ProblemTagResponse();
  private DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
  private FitProblemTagResponse fitProblemTagResponse1 = new FitProblemTagResponse();
  private FitProblemTagResponse fitProblemTagResponse2 = new FitProblemTagResponse();
  private FitProblemTagResponse fitProblemTagResponse3 = new FitProblemTagResponse();
  private List<DeliveryDocument> deliveryDocuments = new ArrayList<DeliveryDocument>();

  private List<DeliveryDocument> deliveryDocuments3 = new ArrayList<DeliveryDocument>();
  private List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<DeliveryDocumentLine>();

  private String problemTagId = "920403185";
  private String deliveryNumber = "123231212";
  private String issueId = "1aa1-2bb2-3cc3-4dd4-5ee5";
  private String resolutionId = "2aa1-2bb2-3cc3-4dd4-5ee5";
  private String poNbr = "4445530688";
  private String userId = "sysadmin";
  private String itemUpc = "00260434000001";
  private String caseUpc = "09071749713118";

  private PageRequest pageReq;

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
    resolution2.setAcceptedQuantity(0);
    resolution2.setRemainingQty(10);
    resolutions2.add(resolution2);
    fitProblemTagResponse2.setLabel(problemTagId);
    fitProblemTagResponse2.setRemainingQty(10);
    fitProblemTagResponse2.setReportedQty(10);
    fitProblemTagResponse2.setSlot("S1234");
    fitProblemTagResponse2.setIssue(issue2);
    fitProblemTagResponse2.setResolutions(resolutions2);

    resolution3.setId("2");
    resolution3.setResolutionPoNbr(poNbr);
    resolution3.setResolutionPoLineNbr(10);
    resolution3.setQuantity(10);
    resolution3.setAcceptedQuantity(0);
    resolution3.setRemainingQty(10);
    resolutions3.add(resolution3);
    fitProblemTagResponse3.setLabel(problemTagId);
    fitProblemTagResponse3.setRemainingQty(10);
    fitProblemTagResponse3.setReportedQty(10);
    fitProblemTagResponse3.setSlot("S1234");
    fitProblemTagResponse3.setIssue(issue2);
    fitProblemTagResponse3.setResolutions(resolutions3);

    problemLabel.setProblemResponse(gson.toJson(fitProblemTagResponse1));

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

    deliveryDocument3.setPurchaseReferenceNumber(poNbr);
    deliveryDocument3.setDeptNumber("40");
    deliveryDocuments3.add(deliveryDocument3);
    gdmPOLineResponse1.setDeliveryNumber(Long.valueOf(deliveryNumber));
    gdmPOLineResponse1.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    gdmPOLineResponse1.setDeliveryDocuments(deliveryDocuments3);

    gdmPOLineResponse2.setDeliveryNumber(Long.valueOf(deliveryNumber));
    gdmPOLineResponse2.setDeliveryStatus(DeliveryStatus.WORKING.toString());

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
    pageReq = PageRequest.of(0, 10);
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
        appConfig,
        instructionPersisterService);
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
      assertEquals(ReceivingException.FIT_SERVICE_DOWN, e.getErrorResponse().getErrorMessage());
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
    assertNotNull(response.getProblem().getReceivedQty());

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
    assertEquals(
        problemTagResponse1.getProblem().getReceivedQty(), response.getProblem().getReceivedQty());
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
  public void testGetProblemTagInfo_ifContainerReceivableAndGdmHasNoPoInfo()
      throws ReceivingException {

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
  public void testGetProblemDetailsFromFIT_returnException() throws ReceivingException {
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
  public void testGetProblemTagInfo_ifContainerReceivableAndQtyNotAvailableInGDM()
      throws ReceivingException {
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

  @SuppressWarnings("unchecked")
  @Test
  public void testGetProblemTagInfo_ifContainerReceivableAndPoLineNotAvailableInGDM()
      throws ReceivingException {
    String problem = gson.toJson(fitProblemTagResponse3);
    String delivery = gson.toJson(gdmPOLineResponse1);
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(ResponseEntity.ok(problem), ResponseEntity.ok(delivery));

    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    doReturn(10L).when(receiptService).getReceivedQtyByPoAndPoLine("4445530688", 1);

    try {
      problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
      fail();
    } catch (ReceivingBadDataException e) {
      assertEquals(ExceptionCodes.INVALID_PROBLEM_RESOLUTION, e.getErrorCode());
      assertEquals(
          ReceivingException.PROBLEM_RESOLUTION_DOC_DOCLINE_NOT_FOUND_IN_DELIVERY_ERROR,
          e.getDescription());
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testGetProblemTagInfo_ifContainerReceivableAndPoNotAvailableInGDM()
      throws ReceivingException {
    String problem = gson.toJson(fitProblemTagResponse3);
    String delivery = gson.toJson(new GdmPOLineResponse());
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(ResponseEntity.ok(problem), ResponseEntity.ok(delivery));

    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    doReturn(10L).when(receiptService).getReceivedQtyByPoAndPoLine("4445530688", 1);

    try {
      problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
      fail();
    } catch (ReceivingBadDataException e) {
      assertEquals(ExceptionCodes.INVALID_PROBLEM_RESOLUTION, e.getErrorCode());
      assertEquals(
          ReceivingException.PROBLEM_RESOLUTION_DOC_DOCLINE_NOT_FOUND_IN_DELIVERY_ERROR,
          e.getDescription());
    }
  }

  @Test
  public void testGetProblemTagInfo_ifContainerReceivableAndPoNotAvailableInGDM2()
      throws ReceivingException {
    String problem = gson.toJson(fitProblemTagResponse3);
    String delivery = gson.toJson(gdmPOLineResponse2);
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(ResponseEntity.ok(problem), ResponseEntity.ok(delivery));

    doReturn(true).when(problemReceivingHelper).isContainerReceivable(any());
    doReturn(10L).when(receiptService).getReceivedQtyByPoAndPoLine("4445530688", 1);

    try {
      problemService.txGetProblemTagInfo(problemTagId, httpHeaders);
      fail();
    } catch (ReceivingBadDataException e) {
      assertEquals(ExceptionCodes.INVALID_PROBLEM_RESOLUTION, e.getErrorCode());
      assertEquals(
          ReceivingException.PROBLEM_RESOLUTION_DOC_DOCLINE_NOT_FOUND_IN_DELIVERY_ERROR,
          e.getDescription());
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
  public void testCompleteProblemTag_returnFailureWithNull() throws ReceivingException {
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
      assertEquals(ReceivingException.FIT_SERVICE_DOWN, e.getErrorResponse().getErrorMessage());
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
  public void testCreateOrUpdateProblemLabel() throws ReceivingException {
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(null);
    when(problemRepository.save(any())).thenReturn(problemLabel);

    ProblemLabel respponse =
        problemService.saveProblemLabel(
            Long.valueOf(deliveryNumber), problemTagId, issueId, resolutionId, null);

    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(problemRepository, times(1)).save(any());
    assertEquals(problemLabel.getIssueId(), respponse.getIssueId());
    assertEquals(problemLabel.getProblemTagId(), respponse.getProblemTagId());
    assertEquals(problemLabel.getDeliveryNumber(), respponse.getDeliveryNumber());
  }

  private ProblemLabel getProblemLabel() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setId(1l);
    problemLabel.setProblemTagId(problemTagId);
    problemLabel.setDeliveryNumber(Long.valueOf(deliveryNumber));
    problemLabel.setIssueId(issueId);
    problemLabel.setProblemStatus(ProblemStatus.WORKING.toString());
    problemLabel.setLastChangeUserId(userId);
    problemLabel.setCreateTs(new Date());
    problemLabel.setLastChangeTs(new Date());
    return problemLabel;
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    ProblemLabel problemLabel = getProblemLabel();
    problemLabel.setId(1L);
    problemLabel.setCreateTs(cal.getTime());

    ProblemLabel problemLabel1 = getProblemLabel();
    problemLabel1.setId(10L);
    problemLabel1.setCreateTs(cal.getTime());

    when(problemRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(problemLabel, problemLabel1));
    doNothing().when(problemRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.PROBLEM)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = problemService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    ProblemLabel problemLabel = getProblemLabel();
    problemLabel.setId(1L);
    problemLabel.setCreateTs(cal.getTime());

    ProblemLabel problemLabel1 = getProblemLabel();
    problemLabel1.setId(10L);
    problemLabel1.setCreateTs(cal.getTime());

    when(problemRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(problemLabel, problemLabel1));
    doNothing().when(problemRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.PROBLEM)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = problemService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    ProblemLabel problemLabel = getProblemLabel();
    problemLabel.setId(1L);
    problemLabel.setCreateTs(cal.getTime());

    ProblemLabel problemLabel1 = getProblemLabel();
    problemLabel1.setId(10L);
    problemLabel1.setCreateTs(new Date());

    when(problemRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(problemLabel, problemLabel1));
    doNothing().when(problemRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.PROBLEM)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = problemService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void test_getProblemsForDelivery_successResponse_FIT() throws ReceivingException {
    String expected_successResponse =
        "{\n"
            + "  \"data\": {\n"
            + "    \"allIssues\": {\n"
            + "      \"pageInfo\": {\n"
            + "        \"totalCount\": 30,\n"
            + "        \"totalPages\": 1,\n"
            + "        \"pageNumber\": 1,\n"
            + "        \"pageSize\": 100,\n"
            + "        \"filterCount\": 30,\n"
            + "        \"hasNext\": false,\n"
            + "        \"hasPrev\": false\n"
            + "      },\n"
            + "      \"issues\": [\n"
            + "        {\n"
            + "          \"issueIdentifier\": \"OVG3261221012592455\",\n"
            + "          \"issueType\": \"OVG\",\n"
            + "          \"itemNumber\": 1806311,\n"
            + "          \"upcNumber\": \"11188122713797\",\n"
            + "          \"issueQuantity\": 81\n"
            + "        },\n"
            + "        {\n"
            + "          \"issueIdentifier\": \"OVG3261221012392259\",\n"
            + "          \"issueType\": \"OVG\",\n"
            + "          \"itemNumber\": 9050230,\n"
            + "          \"itemDescription\": \"4\\\" MKT BAN CRM N\",\n"
            + "          \"upcNumber\": \"11188122713797\",\n"
            + "          \"issueQuantity\": 81\n"
            + "        }\n"
            + "      ]\n"
            + "    }\n"
            + "  }\n"
            + "}";
    final String expected_graphql_url = "https://fit-dev2.prod.us.walmart.net/graphql";

    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<>(expected_successResponse, HttpStatus.OK));
    doReturn(HTTPS_FIT_BASE_URL_DEV).when(appConfig).getFitBaseUrl();
    doReturn(FIT_PROBLEMS_GRAPH_QL_DEFAULT)
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());

    final String problemsForDelivery_response =
        problemService.getProblemsForDelivery(111, httpHeaders);

    // verify
    assertEquals(problemsForDelivery_response, expected_successResponse);

    ArgumentCaptor<String> argumentCaptor_url = ArgumentCaptor.forClass(String.class);
    verify(restUtils, times(1)).post(argumentCaptor_url.capture(), any(), any(), any());
    final String captured_url = argumentCaptor_url.getValue();
    assertEquals(captured_url, expected_graphql_url);
  }

  @Test
  public void test_getProblemsForDelivery_successResponse_FixIT() throws ReceivingException {
    String expected_successResponse =
        "{\n"
            + "    \"data\": {\n"
            + "        \"searchException\": {\n"
            + "            \"pageInfo\": {\n"
            + "                \"totalCount\": 2,\n"
            + "                \"filterCount\": 2,\n"
            + "                \"hasNext\": false,\n"
            + "                \"hasPrev\": false\n"
            + "            },\n"
            + "            \"issues\": [\n"
            + "                {\n"
            + "                    \"identifier\": \"210723-42051-6557-0000\",\n"
            + "                    \"details\": [\n"
            + "                        {\n"
            + "                            \"exceptionType\": \"WRP\",\n"
            + "                            \"remainingQty\": 1,\n"
            + "                            \"exceptionQty\": 1\n"
            + "                        }\n"
            + "                    ],\n"
            + "                    \"items\": [\n"
            + "                        {\n"
            + "                            \"itemNumber\": \"557959102\",\n"
            + "                            \"itemUpc\": \"00260434000001\",\n"
            + "                            \"itemDescription\": \"MKS LAMB BRST SPLIT, LAMB BREAST\"\n"
            + "                        }\n"
            + "                    ]\n"
            + "                },\n"
            + "                {\n"
            + "                    \"identifier\": \"210722-65693-6557-0000\",\n"
            + "                    \"details\": [\n"
            + "                        {\n"
            + "                            \"exceptionType\": \"WRP\",\n"
            + "                            \"remainingQty\": 1,\n"
            + "                            \"exceptionQty\": 1\n"
            + "                        }\n"
            + "                    ],\n"
            + "                    \"items\": [\n"
            + "                        {\n"
            + "                            \"itemNumber\": \"557959102\",\n"
            + "                            \"itemUpc\": \"00260434000001\",\n"
            + "                            \"itemDescription\": \"MKS LAMB BRST SPLIT, LAMB BREAST\"\n"
            + "                        }\n"
            + "                    ]\n"
            + "                }\n"
            + "            ]\n"
            + "        }\n"
            + "    }\n"
            + "}";

    final String expected_graphql_url =
        "https://fixit-platform-application.dev.walmart.net/graphql";
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<>(expected_successResponse, HttpStatus.OK));
    doReturn("https://fixit-platform-application.dev.walmart.net")
        .when(appConfig)
        .getFixitPlatformBaseUrl();
    doReturn(FIXIT_PROBLEMS_GRAPH_QL_DEFAULT)
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());

    doReturn(WM_RCV_CONSUMER_ID).when(appConfig).getReceivingConsumerId();
    doReturn(FIXIT_PLATFORM).when(appConfig).getFixitServiceName();
    doReturn(FIXIT_DEV).when(appConfig).getFixitServiceEnv();

    final String problemsForDelivery_response =
        problemService.getProblemsForDelivery(111, httpHeaders);

    // verify
    assertEquals(problemsForDelivery_response, expected_successResponse);
    ArgumentCaptor<String> argumentCaptor_url = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpHeaders> argumentCaptorHeaders = ArgumentCaptor.forClass(HttpHeaders.class);
    ArgumentCaptor<String> argumentCaptorGraphQl = ArgumentCaptor.forClass(String.class);

    verify(restUtils, times(1))
        .post(
            argumentCaptor_url.capture(),
            argumentCaptorHeaders.capture(),
            any(),
            argumentCaptorGraphQl.capture());
    final String captured_url = argumentCaptor_url.getValue();
    assertEquals(captured_url, expected_graphql_url);

    final HttpHeaders httpHeaders = argumentCaptorHeaders.getAllValues().get(0);
    assertEquals(httpHeaders.getFirst(CONTENT_TYPE), APPLICATION_GRAPHQL);
    assertEquals(httpHeaders.getFirst(WM_CONSUMER_ID), WM_RCV_CONSUMER_ID);
    assertEquals(httpHeaders.getFirst(WM_SVC_NAME), FIXIT_PLATFORM);
    assertEquals(httpHeaders.getFirst(WM_SVC_ENV), FIXIT_DEV);
    assertEquals(httpHeaders.getFirst(WMT_SOURCE), APP_NAME_VALUE);
    assertEquals(httpHeaders.getFirst(WMT_CHANNEL), WEB);

    final String actualGraphQlQuery = argumentCaptorGraphQl.getValue();
    assertEquals(
        actualGraphQlQuery,
        "query { searchException( searchInput: { delivery: { number: \"111\" } exceptionCategory: \"PROBLEM\" details: { exceptionType: [\"NOP\", \"WRP\", \"NWF\"] } } sortDirection: DESC pageSize: 100 sortBy: \"createdOn\" pageContinuationToken: null ) { pageInfo { totalCount totalPages pageNumber pageSize filterCount hasNext hasPrev pageContinuationToken } issues { identifier details { exceptionType remainingQty exceptionQty } items { itemNumber itemUpc itemDescription pluNumber } } }}");
  }

  @Test
  public void test_getProblemsForDelivery_successResponse_withErrors() {
    String successMessageWithErrors =
        "{\n"
            + "  \"errors\": [\n"
            + "    {\n"
            + "      \"message\": \"Invalid Syntax : offending token 'issueType' at line 6 column 7\",\n"
            + "      \"extensions\": {\n"
            + "        \"classification\": \"InvalidSyntax\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<>(successMessageWithErrors, HttpStatus.OK));
    doReturn(HTTPS_FIT_BASE_URL_DEV).when(appConfig).getFitBaseUrl();
    doReturn(FIT_PROBLEMS_GRAPH_QL_DEFAULT)
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());

    try {
      problemService.getProblemsForDelivery(111, httpHeaders);
    } catch (Exception e) {
      assertEquals(((ReceivingDataNotFoundException) e).getErrorCode(), FIT_NOT_FOUND);
      assertEquals(e.getMessage(), FIT_BAD_DATA_ERROR_MSG);
    }
  }

  @Test
  public void test_getProblemsForDelivery_Exception_Generic_ErrorToReTryOrReport() {
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(
            new ResponseEntity<String>("Internal Server Error.", HttpStatus.INTERNAL_SERVER_ERROR));
    doReturn(HTTPS_FIT_BASE_URL_DEV).when(appConfig).getFitBaseUrl();
    doReturn(FIT_PROBLEMS_GRAPH_QL_DEFAULT)
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());

    try {
      problemService.getProblemsForDelivery(111, httpHeaders);
    } catch (Exception e) {
      assertEquals(((ReceivingDataNotFoundException) e).getErrorCode(), FIT_NOT_ACCESSIBLE);
      assertEquals(e.getMessage(), GENERIC_ERROR_RE_TRY_OR_REPORT);
    }
  }

  @Test
  public void tes_getProblemsForDelivery_FitServiceDown() {
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(
            new ResponseEntity<>("Error in fetching resource.", HttpStatus.SERVICE_UNAVAILABLE));
    doReturn(FIT_PROBLEMS_GRAPH_QL_DEFAULT)
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());

    try {
      problemService.getProblemsForDelivery(111, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(e.getErrorResponse().getErrorCode(), ReceivingException.GET_PTAG_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.FIT_SERVICE_DOWN);
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
      assertEquals(ReceivingException.FIT_SERVICE_DOWN, e.getErrorResponse().getErrorMessage());
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
          ReceivingException.CREATE_PTAG_ERROR_CODE_FIT, e.getErrorResponse().getErrorCode());
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
          ReceivingException.CREATE_PTAG_ERROR_CODE_FIT, e.getErrorResponse().getErrorCode());
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
          ReceivingException.CREATE_PTAG_ERROR_CODE_FIT, e.getErrorResponse().getErrorCode());
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
  public void testDeleteProblemLabel() {
    doNothing().when(problemRepository).delete(any(ProblemLabel.class));
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setProblemTagId("22323334");
    problemService.deleteProblemLabel(problemLabel);

    verify(problemRepository, times(1)).delete(any(ProblemLabel.class));
  }

  @Test
  public void testReportProblemHappyPathFIT() throws ReceivingException {
    when(appConfig.getFitBaseUrl()).thenReturn("fitURL");
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(new ResponseEntity<String>("Success.", HttpStatus.OK));

    try {
      problemService.reportProblem("PTAG1", "2324-43423", getMockReportProblemRequest());
    } catch (Exception excp) {
      assertNull(excp);
    }
    verify(appConfig, times(1)).getFitBaseUrl();
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReportProblemThrowsExceptionForBadDataOrConflictFIT() throws ReceivingException {
    when(appConfig.getFitBaseUrl()).thenReturn("fitURL");
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(new ResponseEntity<String>("Failure", HttpStatus.CONFLICT));

    problemService.reportProblem("PTAG1", "2324-43423", getMockReportProblemRequest());

    verify(appConfig, times(1)).getFitBaseUrl();
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReportProblemThrowsExceptionForBadGatewayFIT() throws ReceivingException {
    when(appConfig.getFitBaseUrl()).thenReturn("fitURL");
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(new ResponseEntity<String>("Failure", HttpStatus.BAD_GATEWAY));

    problemService.reportProblem("PTAG1", "2324-43423", getMockReportProblemRequest());

    verify(appConfig, times(1)).getFitBaseUrl();
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
  }

  private ReportProblemRequest getMockReportProblemRequest() {
    UserInfo userInfo = new UserInfo();
    userInfo.setUserId("sysadmin");
    String errorMessage = "Problem Receive Error";
    return ReportProblemRequest.builder().userInfo(userInfo).errorMessage(errorMessage).build();
  }

  /**
   * Happy path for FIXIT API call - Fetch problem tickets count for a PO
   *
   * @throws ReceivingException
   */
  @Test
  public void test_getProblemTicketsForPO_successResponse_FixIT() throws ReceivingException {
    String expected_apiResponse =
        "{\n"
            + "  \"data\": {\n"
            + "    \"searchException\": {\n"
            + "      \"pageInfo\": {\n"
            + "        \"totalCount\": 0,\n"
            + "        \"filterCount\": 0\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";
    ProblemTicketResponseCount expected_successResponse = new ProblemTicketResponseCount();
    expected_successResponse.setTicketCount(0);

    final String expected_graphql_url =
        "https://fixit-platform-application.dev.walmart.net/graphql";
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<>(expected_apiResponse, HttpStatus.OK));
    doReturn("https://fixit-platform-application.dev.walmart.net")
        .when(appConfig)
        .getFixitPlatformBaseUrl();
    doReturn(FIXIT_PROBLEM_TICKETS_GRAPH_QL_DEFAULT)
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());

    doReturn(WM_RCV_CONSUMER_ID).when(appConfig).getReceivingConsumerId();
    doReturn(FIXIT_PLATFORM).when(appConfig).getFixitServiceName();
    doReturn(FIXIT_DEV).when(appConfig).getFixitServiceEnv();
    final ProblemTicketResponseCount problemTicketsForPO_response =
        problemService.getProblemTicketsForPo("123456", httpHeaders);

    // verify
    assertEquals(
        problemTicketsForPO_response.getTicketCount(), expected_successResponse.getTicketCount());
    ArgumentCaptor<String> argumentCaptor_url = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpHeaders> argumentCaptorHeaders = ArgumentCaptor.forClass(HttpHeaders.class);
    ArgumentCaptor<String> argumentCaptorGraphQl = ArgumentCaptor.forClass(String.class);

    verify(restUtils, times(1))
        .post(
            argumentCaptor_url.capture(),
            argumentCaptorHeaders.capture(),
            any(),
            argumentCaptorGraphQl.capture());
    final String captured_url = argumentCaptor_url.getValue();
    assertEquals(captured_url, expected_graphql_url);

    final HttpHeaders httpHeaders = argumentCaptorHeaders.getAllValues().get(0);
    assertEquals(httpHeaders.getFirst(CONTENT_TYPE), APPLICATION_GRAPHQL);
    assertEquals(httpHeaders.getFirst(WM_CONSUMER_ID), WM_RCV_CONSUMER_ID);
    assertEquals(httpHeaders.getFirst(WM_SVC_NAME), FIXIT_PLATFORM);
    assertEquals(httpHeaders.getFirst(WM_SVC_ENV), FIXIT_DEV);
    assertEquals(httpHeaders.getFirst(WMT_SOURCE), APP_NAME_VALUE);
    assertEquals(httpHeaders.getFirst(WMT_CHANNEL), WEB);

    final String actualGraphQlQuery = argumentCaptorGraphQl.getValue();
    assertEquals(
        actualGraphQlQuery,
        "query { searchException( searchInput: { purchaseOrders: { poNumber: \"123456\" } exceptionCategory: \"PROBLEM\" details: { businessStatus: [\"ANSWERED\", \"ASSIGNED\", \"REASSIGNED\", \"AWAITING_INFORMATION\", \"SOLUTION_AVAILABLE\", \"READY_TO_RECEIVE\"] } } pageSize: 100 ) { pageInfo { totalCount filterCount pageContinuationToken } issues { exceptionId identifier status createdBy createdOn details { businessStatus exceptionType exceptionUOM exceptionQty } delivery { number trailerNumber } purchaseOrders { poNumber } } }}");
  }

  @Test
  public void test_getProblemTicketsForPO_Exception_Generic_ErrorToReTryOrReport() {
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(
            new ResponseEntity<String>("Internal Server Error.", HttpStatus.INTERNAL_SERVER_ERROR));
    doReturn("https://fixit-platform-application.dev.walmart.net")
        .when(appConfig)
        .getFixitPlatformBaseUrl();
    doReturn(FIXIT_PROBLEM_TICKETS_GRAPH_QL_DEFAULT)
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());

    try {
      problemService.getProblemTicketsForPo("123456", httpHeaders);
    } catch (Exception e) {
      assertEquals(((ReceivingDataNotFoundException) e).getErrorCode(), FIXIT_NOT_ACCESSIBLE);
      assertEquals(e.getMessage(), GENERIC_ERROR_RE_TRY_OR_REPORT);
    }
  }

  @Test
  public void test_getProblemTicketsForPO_FixServiceDown() {
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(
            new ResponseEntity<>("Error in fetching resource.", HttpStatus.SERVICE_UNAVAILABLE));
    doReturn(FIXIT_PROBLEM_TICKETS_GRAPH_QL_DEFAULT)
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());

    try {
      problemService.getProblemTicketsForPo("123456", httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(e.getErrorResponse().getErrorCode(), ReceivingException.GET_PTAG_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.FIXIT_SERVICE_DOWN);
    }
  }

  @Test
  public void test_getProblemTicketsForPO_successResponse_withErrors() {
    String successMessageWithErrors =
        "{\n"
            + "  \"errors\": [\n"
            + "    {\n"
            + "      \"message\": \"Invalid Syntax : There are more tokens in the query that have not been consumed offending token '}' at line 38 column 2\",\n"
            + "      \"locations\": [],\n"
            + "      \"extensions\": {\n"
            + "        \"classification\": \"InvalidSyntax\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    when(restUtils.post(anyString(), any(), any(), any()))
        .thenReturn(new ResponseEntity<>(successMessageWithErrors, HttpStatus.OK));
    doReturn("https://fixit-platform-application.dev.walmart.net")
        .when(appConfig)
        .getFixitPlatformBaseUrl();
    doReturn(FIXIT_PROBLEM_TICKETS_GRAPH_QL_DEFAULT)
        .when(configUtils)
        .getCcmValue(anyInt(), anyString(), anyString());

    try {
      problemService.getProblemTicketsForPo("123456", httpHeaders);
    } catch (Exception e) {
      assertEquals(((ReceivingDataNotFoundException) e).getErrorCode(), FIXIT_NOT_FOUND);
      assertEquals(e.getMessage(), FIT_BAD_DATA_ERROR_MSG);
    }
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testCompleteProblemThrowsExceptionWhenProblemTagIdDoesNotExist()
      throws ReceivingException {
    Instruction instruction = MockInstruction.getInstruction();
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(null);
    problemService.completeProblem(instruction);
    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
  }

  @Test
  public void testCompleteProblemSuccessDoNotDeleteProblemLabelFitPlatform()
      throws ReceivingException {
    Instruction instruction = MockInstruction.getCompleteInstructionWithProblemTagId();
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(100L);
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(new ResponseEntity<String>("Success.", HttpStatus.OK));
    doNothing().when(problemRepository).delete(any(ProblemLabel.class));
    problemService.completeProblem(instruction);

    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
    verify(problemRepository, times(0)).delete(any(ProblemLabel.class));
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
  }

  @Test
  public void testCompleteProblemSuccessDeleteProblemLabel() throws ReceivingException {
    Instruction instruction = MockInstruction.getCompleteInstructionWithProblemTagId();
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(10L);
    doNothing().when(problemRepository).delete(any(ProblemLabel.class));
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(new ResponseEntity<String>("Success.", HttpStatus.OK));
    problemService.completeProblem(instruction);

    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
    verify(problemRepository, times(1)).delete(any(ProblemLabel.class));
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCompleteProblemFixitThrowsError() throws ReceivingException {
    Instruction instruction = MockInstruction.getCompleteInstructionWithProblemTagId();
    when(problemRepository.findProblemLabelByProblemTagId(anyString())).thenReturn(problemLabel);
    when(restUtils.post(anyString(), any(), any(), anyString()))
        .thenReturn(new ResponseEntity<String>("Error.", HttpStatus.INTERNAL_SERVER_ERROR));
    problemService.completeProblem(instruction);

    verify(problemRepository, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(restUtils, times(1)).post(anyString(), any(), any(), anyString());
  }
}
