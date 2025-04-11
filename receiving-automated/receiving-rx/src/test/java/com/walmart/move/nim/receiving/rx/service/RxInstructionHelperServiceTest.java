package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.commons.lang3.time.DateUtils;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxInstructionHelperServiceTest {

  @Mock private AppConfig appConfig;
  @Mock private RxManagedConfig rxManagedConfig;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private ReceiptService receiptService;
  @Mock private ContainerItemService containerItemService;
  @Mock private ContainerService containerService;
  @Mock private Transformer<Container, ContainerDTO> transformer;

  @InjectMocks private RxInstructionHelperService rxInstructionHelperService;
  @Mock private LPNCacheService lpnCacheService;

  @BeforeClass
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32709);
  }

  @AfterMethod
  public void teardown() {
    reset(appConfig);
    reset(containerPersisterService);
    reset(instructionPersisterService);
    reset(receiptService);
    reset(containerItemService);
    reset(containerService);
    reset(lpnCacheService);
  }

  @BeforeMethod
  public void beforeMethod() {
    doReturn(true).when(appConfig).isCloseDateCheckEnabled();
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_closeDatedItem_CloseDatedItem() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expiryScannedData = new ScannedData();
    expiryScannedData.setKey("exp");
    expiryScannedData.setApplicationIdentifier("17");
    expiryScannedData.setValue("102112");
    scannedDataList.add(expiryScannedData);
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, expiryScannedData);

    doReturn(365).when(appConfig).getCloseDateLimitDays();

    rxInstructionHelperService.checkIfContainerIsCloseDated(scannedDataMap);

    verify(appConfig, times(1)).getCloseDateLimitDays();
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_closeDatedItem_InvalidDate() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expiryScannedData = new ScannedData();
    expiryScannedData.setKey("exp");
    expiryScannedData.setApplicationIdentifier("17");
    expiryScannedData.setValue("aaaaaa");
    scannedDataList.add(expiryScannedData);
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, expiryScannedData);

    doReturn(365).when(appConfig).getCloseDateLimitDays();
    rxInstructionHelperService.checkIfContainerIsCloseDated(scannedDataMap);
    verify(appConfig, times(1)).getCloseDateLimitDays();
  }

  @Test
  public void test_closeDatedItem_ProblemFlow() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expDateScannedData = new ScannedData();
    expDateScannedData.setApplicationIdentifier(
        ApplicationIdentifier.EXP.getApplicationIdentifier());
    expDateScannedData.setKey(ReceivingConstants.KEY_EXPIRY_DATE);
    LocalDate now = LocalDate.now();
    LocalDate futureDate = now.plus(100, ChronoUnit.DAYS);
    expDateScannedData.setValue(futureDate.format(DateTimeFormatter.ofPattern("yyMMdd")));
    scannedDataList.add(expDateScannedData);
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, expDateScannedData);
    FitProblemTagResponse fitProblemTagResponse = new FitProblemTagResponse();
    Issue issue = new Issue();
    issue.setType(ReceivingConstants.FIXIT_ISSUE_TYPE_DI);
    fitProblemTagResponse.setIssue(issue);

    doReturn(365).when(appConfig).getCloseDateLimitDays();
    rxInstructionHelperService.checkIfContainerIsCloseDated(fitProblemTagResponse, scannedDataMap);
    verify(appConfig, times(0)).getCloseDateLimitDays();
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_closeDatedItem_ProblemFlow_ExpiredItem() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expDateScannedData = new ScannedData();
    expDateScannedData.setApplicationIdentifier(
        ApplicationIdentifier.EXP.getApplicationIdentifier());
    expDateScannedData.setKey(ReceivingConstants.KEY_EXPIRY_DATE);
    LocalDate now = LocalDate.now();
    LocalDate futureDate = now.plus(-1, ChronoUnit.DAYS);
    expDateScannedData.setValue(futureDate.format(DateTimeFormatter.ofPattern("yyMMdd")));
    scannedDataList.add(expDateScannedData);
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, expDateScannedData);
    FitProblemTagResponse fitProblemTagResponse = new FitProblemTagResponse();
    Issue issue = new Issue();
    issue.setType(ReceivingConstants.FIXIT_ISSUE_TYPE_DI);
    fitProblemTagResponse.setIssue(issue);

    doReturn(365).when(appConfig).getCloseDateLimitDays();
    rxInstructionHelperService.checkIfContainerIsCloseDated(fitProblemTagResponse, scannedDataMap);
    verify(appConfig, times(1)).getCloseDateLimitDays();
  }

  @Test
  public void test_checkIfContainerIsCloseDated_expired_item() {

    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData mockDateScannedData = new ScannedData();
    mockDateScannedData.setValue(
        DateFormatUtils.format(
            DateUtils.addDays(new Date(), -1), ReceivingConstants.EXPIRY_DATE_FORMAT));
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, mockDateScannedData);

    String problemTagId = "DUMMY_PROBLEM_TAG";
    FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
    Issue mockIssue = new Issue();
    mockIssue.setType("DI");
    mockFitProblemTagResponse.setIssue(mockIssue);

    doReturn(365).when(appConfig).getCloseDateLimitDays();
    try {
      rxInstructionHelperService.checkIfContainerIsCloseDated(
          mockFitProblemTagResponse, scannedDataMap);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.EXPIRED_ITEM);
      assertEquals(e.getDescription(), RxConstants.EXPIRED_ITEM);
    }
  }

  @Test
  public void test_checkIfContainerIsCloseDated_less_than_365() {

    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData mockDateScannedData = new ScannedData();
    mockDateScannedData.setValue(
        DateFormatUtils.format(
            DateUtils.addDays(new Date(), 364), ReceivingConstants.EXPIRY_DATE_FORMAT));
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, mockDateScannedData);
    doReturn(365).when(appConfig).getCloseDateLimitDays();
    try {
      rxInstructionHelperService.checkIfContainerIsCloseDated(scannedDataMap);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.CLOSE_DATED_ITEM);
      assertEquals(e.getDescription(), RxConstants.CLOSE_DATED_ITEM);
    }
    verify(appConfig, times(1)).getCloseDateLimitDays();
  }

  @Test
  public void test_persistForUpdateInstruction() {

    doReturn(new Instruction())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));
    doReturn(Collections.emptyList()).when(receiptService).saveAll(any(List.class));
    doNothing().when(containerPersisterService).saveContainers(any(List.class));
    doNothing().when(containerItemService).saveAll(any(List.class));

    Instruction mockInstruction = new Instruction();
    List<Receipt> mockReceipts = Arrays.asList(new Receipt());
    List<Container> mockContainers = Arrays.asList(new Container());
    List<ContainerItem> mockContainerItems = Arrays.asList(new ContainerItem());

    rxInstructionHelperService.persistForUpdateInstruction(
        mockInstruction, mockReceipts, mockContainers, mockContainerItems);

    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(receiptService, times(1)).saveAll(any(List.class));
    verify(containerPersisterService, times(1)).saveContainers(any(List.class));
    verify(containerItemService, times(1)).saveAll(any(List.class));
  }

  @Test
  public void test_rollbackContainers() {

    doAnswer(invocation -> invocation.getArguments()[0])
        .when(instructionPersisterService)
        .saveAllInstruction(anyList());
    doNothing().when(containerService).deleteContainersByTrackingIds(anyList());
    doAnswer(invocation -> invocation.getArguments()[0]).when(receiptService).saveAll(anyList());

    rxInstructionHelperService.rollbackContainers(
        Arrays.asList("MOCK_TRACKING_ID"),
        Arrays.asList(new Receipt()),
        Arrays.asList(MockInstruction.getInstructionWithManufactureDetails()));

    verify(instructionPersisterService, times(1)).saveAllInstruction(anyList());
    verify(containerService, times(1)).deleteContainersByTrackingIds(anyList());
    verify(receiptService, times(1)).saveAll(anyList());
  }

  @Test
  public void test_persist() {

    doNothing().when(containerPersisterService).saveContainers(anyList());
    doNothing().when(containerPersisterService).saveContainers(anyList());
    doReturn(Arrays.asList(new Instruction()))
        .when(instructionPersisterService)
        .saveAllInstruction(anyList());

    Container mockContainer = new Container();
    mockContainer.setTrackingId("MOCK_TRACKING_ID");

    Container mockChildContainer = new Container();
    mockChildContainer.setTrackingId("MOCK_CHILD_TRACKING_ID");
    Set<Container> children = new HashSet<>();
    children.add(mockChildContainer);
    mockContainer.setChildContainers(children);

    rxInstructionHelperService.persist(
        Arrays.asList(mockContainer),
        Collections.emptyList(),
        Arrays.asList(new Instruction()),
        "MOCK_USERID");

    verify(containerPersisterService, times(1)).saveContainers(anyList());
    verify(instructionPersisterService, times(1)).saveAllInstruction(anyList());
  }

  @Test
  public void testUpdateDeliveryDocumentLineAdditionalInfo() {
    doReturn(true).when(rxManagedConfig).isWholesalerLotCheckEnabled();
    doReturn("482497180").when(rxManagedConfig).getWholesalerVendors();
    List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocuments();
    rxInstructionHelperService.updateDeliveryDocumentLineAdditionalInfo(deliveryDocuments);
    assertTrue(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getIsWholesaler());
  }

  @Test
  public void testUpdateDeliveryDocumentLineAdditionalInfo_NonWholesaler() {
    doReturn(true).when(rxManagedConfig).isWholesalerLotCheckEnabled();
    doReturn("482497181").when(rxManagedConfig).getWholesalerVendors();
    List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocuments();
    rxInstructionHelperService.updateDeliveryDocumentLineAdditionalInfo(deliveryDocuments);
    assertFalse(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getIsWholesaler());
  }

  @Test
  public void testUpdateDeliveryDocumentLineAdditionalInfo_WholesalerCheckDisabled() {
    doReturn(false).when(rxManagedConfig).isWholesalerLotCheckEnabled();
    doReturn("482497180").when(rxManagedConfig).getWholesalerVendors();
    List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocuments();
    rxInstructionHelperService.updateDeliveryDocumentLineAdditionalInfo(deliveryDocuments);
    assertFalse(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getIsWholesaler());
  }

  @Test
  public void testPublishContainers() {
    doReturn(true).when(rxManagedConfig).isPublishContainersToKafkaEnabled();
    rxInstructionHelperService.publishContainers(Arrays.asList(new Container()));
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
  }

  @Test
  public void testPublishContainers_Fail() {
    doReturn(true).when(rxManagedConfig).isPublishContainersToKafkaEnabled();
    doThrow(new ReceivingInternalException("", ""))
        .when(containerService)
        .publishMultipleContainersToInventory(anyList());
    rxInstructionHelperService.publishContainers(Arrays.asList(new Container()));
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
  }

  @Test
  public void fetchMultiSkuInstrDeliveryDocument() {
    rxInstructionHelperService.fetchMultiSkuInstrDeliveryDocument("1", "12", "1", "user");
    verify(instructionPersisterService, times(1))
        .fetchMultiSkuInstrDeliveryDocument(
            anyString(), eq(1L), anyString(), anyString(), anyString());
  }

  @Test
  public void fetchMultiSkuInstrDeliveryDocumentByDelivery() {
    rxInstructionHelperService.fetchMultiSkuInstrDeliveryDocumentByDelivery("1", "1", "user");
    verify(instructionPersisterService, times(1))
        .fetchMultiSkuInstrDeliveryDocumentByDelivery(
            anyString(), anyLong(), anyString(), anyString());
  }

  @Test
  public void fetchMultiSkuInstrDeliveryDocumentForCompleteIns() {
    rxInstructionHelperService.fetchMultiSkuInstrDeliveryDocumentForCompleteIns(1L, "1", "user");
    verify(instructionPersisterService, times(1))
        .fetchMultiSkuInstrByDelivery(anyString(), anyLong(), anyString(), anyString());
  }

  @Test
  public void sameItemOnProblemEmptyItemTest() {
    Issue issue = new Issue();
    FitProblemTagResponse mockProblemLabelResponse = new FitProblemTagResponse();
    mockProblemLabelResponse.setIssue(issue);
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setItemNbr(24324L);
    try {
      rxInstructionHelperService.sameItemOnProblem(
          mockProblemLabelResponse, mockDeliveryDocumentLine);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.PROBLEM_TICKET_MISSING_ITEM);
      assertEquals(e.getDescription(), ReceivingException.PROBLEM_TICKET_MISSING_ITEM);
    }
  }

  @Test
  public void sameItemOnProblemMismatchItemTest() {
    Issue issue = new Issue();
    issue.setItemNumber(23234343L);
    FitProblemTagResponse mockProblemLabelResponse = new FitProblemTagResponse();
    mockProblemLabelResponse.setIssue(issue);
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setItemNbr(24324L);
    try {
      rxInstructionHelperService.sameItemOnProblem(
          mockProblemLabelResponse, mockDeliveryDocumentLine);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.PROBLEM_ITEM_DOES_NOT_MATCH);
      assertEquals(e.getDescription(), ReceivingException.PROBLEM_ITEM_DOES_NOT_MATCH);
    }
  }

  @Test
  public void testGenerateTrackingId() {
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
            .when(lpnCacheService)
            .getLPNBasedOnTenant(any(HttpHeaders.class));
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    String returnedTrackingId = rxInstructionHelperService.generateTrackingId(httpHeaders);
    assertNotNull(returnedTrackingId);
  }
  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testInvalidTrackingId() throws ReceivingBadDataException{
    doReturn(null)
            .when(lpnCacheService)
            .getLPNBasedOnTenant(any(HttpHeaders.class));
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    rxInstructionHelperService.generateTrackingId(httpHeaders);
  }
}
