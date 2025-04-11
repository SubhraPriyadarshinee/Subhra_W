package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.BILL_CODE;
import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.CARRIER_NAME;
import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.FREIGHT_BILL_QTY;
import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.TRAILER_NBR;
import static com.walmart.move.nim.receiving.core.common.JacksonParser.convertJsonToObject;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_EXP_DATE_FORMAT;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.GLS_RCV_INSTRUCTION_COMPLETED;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.GROCERY_OVERAGE_ERROR_CODE;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.DeliveryStatus.getDeliveryStatus;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Arrays.asList;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.dcfin.model.DcFinAdjustRequest;
import com.walmart.move.nim.receiving.core.client.dcfin.model.TransactionsItem;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.config.WitronManagedConfig;
import com.walmart.move.nim.receiving.witron.helper.GdcManualReceiveHelper;
import com.walmart.move.nim.receiving.witron.helper.LabelPrintingHelper;
import com.walmart.move.nim.receiving.witron.mock.data.MockContainer;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.witron.publisher.WitronDCFinServiceImpl;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.*;

public class GdcReceiveInstructionHandlerTest extends ReceivingTestBase {
  @InjectMocks private GdcReceiveInstructionHandler gdcReceiveInstructionHandler;
  @Mock private GdcPutawayPublisher gdcPutawayPublisher;
  @Mock private WitronDCFinServiceImpl witronDCFinService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;
  @Spy private InstructionStateValidator instructionStateValidator;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryCacheServiceInMemoryImpl deliveryCacheServiceInMemoryImpl;
  @Mock private ContainerService containerService;
  @Mock private InstructionService instructionService;
  @Mock private MovePublisher movePublisher;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private WitronManagedConfig witronManagedConfig;

  @Mock private GDCFlagReader gdcFlagReader;
  @Mock private GdcManualReceiveHelper gdcManualReceiveHelper;
  @Mock private AppConfig appConfig;
  @InjectMocks @Spy private LabelPrintingHelper labelPrintingHelper;
  @Mock private DCFinRestApiClient dcFinRestApiClient;
  @Mock private DeliveryService deliveryService;
  @Mock private GdcInstructionService gdcInstructionService;
  @Mock Transformer<Container, ContainerDTO> transformer;
  @Mock AsyncPoReceivingProgressPublisher asyncPoReceivingProgressPublisher;
  @Mock InventoryRestApiClient inventoryRestApiClient;

  private DeliveryCacheValue deliveryCacheValue;
  private Long instructionId = Long.valueOf("297252");
  private ReceiveInstructionRequest receiveInstructionRequest;
  private ReceiveAllRequest receiveAllRequest;
  private HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private Container container = MockContainer.getSSTKContainer();
  private Instruction instruction = MockInstruction.getInstruction();
  private Map<String, Object> mockInstructionContainerMap;
  Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(gdcReceiveInstructionHandler, "configUtils", configUtils);
    ReflectionTestUtils.setField(gdcReceiveInstructionHandler, "gson", gson);
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
  }

  @BeforeMethod
  public void setUpTestData() {
    receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("ZA");

    receiveAllRequest = new ReceiveAllRequest();
    receiveAllRequest.setDoorNumber("105");
    receiveAllRequest.setContainerType("Chep Pallet");
    receiveAllRequest.setRotateDate(new Date());
    receiveAllRequest.setPrinterName("TestPrinter");
    receiveAllRequest.setQuantity(1);
    receiveAllRequest.setQuantityUOM("ZA");
    receiveAllRequest.setReceiveAll(true);

    deliveryCacheValue = new DeliveryCacheValue();
    deliveryCacheValue.setBolWeight(123.45f);
    deliveryCacheValue.setTotalBolFbq(200);
    deliveryCacheValue.setTrailerId("55341");

    mockInstructionContainerMap = new HashMap<>();
    instruction.setCompleteUserId("sysadmin");
    instruction.setCompleteTs(new Date());
    mockInstructionContainerMap.put(ReceivingConstants.INSTRUCTION, instruction);
    mockInstructionContainerMap.put(ReceivingConstants.CONTAINER, container);
    mockInstructionContainerMap.put(ReceivingConstants.OSDR_PAYLOAD, new FinalizePORequestBody());

    when(configUtils.getWhiteWoodPalletMaxWeight(any(), any())).thenReturn(2100.0f);

    doReturn("32")
        .when(configUtils)
        .getCcmValue(
            anyInt(), eq(ELIGIBLE_TRANSFER_POS_CCM_CONFIG), eq(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE));
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(gdcPutawayPublisher);
    reset(witronDCFinService);
    reset(instructionPersisterService);
    reset(purchaseReferenceValidator);
    reset(instructionStateValidator);
    reset(instructionHelperService);
    reset(receiptService);
    reset(deliveryCacheServiceInMemoryImpl);
    reset(containerService);
    reset(instructionService);
    reset(movePublisher);
    reset(receiptPublisher);
    reset(gdcFlagReader);
    reset(gdcManualReceiveHelper);
    reset(dcFinRestApiClient);
    reset(transformer);
    reset(gdcInstructionService);
    reset(deliveryService);
    reset(inventoryRestApiClient);
  }

  @Test
  public void testReceiveInstructionAsNormal()
      throws ReceivingException, GDMRestApiClientException {
    when(gdcFlagReader.isAutomatedDC()).thenReturn(true);
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstruction());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);

    // Execute
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());

    verify(gdcPutawayPublisher, times(1))
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(witronDCFinService, times(1))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(null));
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(instructionHelperService, times(2))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(0))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
  }

  @Test
  public void testReceiveInstructionAsCorrection()
      throws ReceivingException, GDMRestApiClientException {
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setIsReceiveCorrection(true);
    when(gdcFlagReader.isAutomatedDC()).thenReturn(true);
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class))).thenReturn(instruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(containerService)
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    doNothing()
        .when(receiptPublisher)
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(containerService)
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(gdcPutawayPublisher, times(1))
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(instructionHelperService, times(2))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    verify(containerService, times(1))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(1))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(1))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));

    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(anyString(), anyString());
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(DcFinAdjustRequest.class), any());
  }

  @Test
  public void testReceiveInstructionAsProblem()
      throws ReceivingException, GDMRestApiClientException {
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setProblemTagId("PTAG-0001");
    when(gdcFlagReader.isAutomatedDC()).thenReturn(true);
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class))).thenReturn(instruction);
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);

    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(gdcPutawayPublisher, times(1))
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(witronDCFinService, times(1))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(null));
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(instructionHelperService, times(2))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    verify(receiptService, times(0)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(0))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
  }

  @Test
  public void testReceiveInstruction_exception1() {
    try {
      when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
      when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
      when(instructionPersisterService.getInstructionById(any(Long.class))).thenReturn(null);

      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              gdcReceiveInstructionHandler.receiveInstruction(
                  instructionId, receiveInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), "GLS-RCV-INT-500");
      assertEquals(rbde.getMessage(), "There is an error while completing instruction.");
    } catch (ReceivingException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testReceiveInstruction_exception2() {
    try {
      when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
      when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(MockInstruction.getInstruction());
      doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
      doNothing().when(instructionStateValidator).validate(any(Instruction.class));
      when(instructionHelperService.isManagerOverrideIgnoreExpiry(
              anyString(), anyString(), anyBoolean(), anyInt()))
          .thenReturn(false);
      when(instructionHelperService.isManagerOverrideIgnoreOverage(
              anyString(), anyString(), anyInt()))
          .thenReturn(false);
      when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
          .thenReturn(Long.parseLong("0"));
      doReturn(null)
          .when(deliveryCacheServiceInMemoryImpl)
          .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              gdcReceiveInstructionHandler.receiveInstruction(
                  instructionId, receiveInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException re) {
      assertEquals(re.getHttpStatus(), INTERNAL_SERVER_ERROR);
      assertEquals(
          re.getMessage(),
          "We’re having trouble reaching GDM now. Please try again or report this to your supervisor if it continues.");
    }
  }

  @Test
  public void testReceiveInstruction_exception3_overage_isKotlin_True() {
    try {
      when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
      when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(MockInstruction.getInstruction());
      doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
      doNothing().when(instructionStateValidator).validate(any(Instruction.class));
      when(instructionHelperService.isManagerOverrideIgnoreExpiry(
              anyString(), anyString(), anyBoolean(), anyInt()))
          .thenReturn(false);
      when(instructionHelperService.isManagerOverrideIgnoreOverage(
              anyString(), anyString(), anyInt()))
          .thenReturn(false);
      doReturn(null)
          .when(deliveryCacheServiceInMemoryImpl)
          .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

      httpHeaders.set("iskotlin", "true");
      when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
          .thenReturn(Long.parseLong("611"));
      gdcReceiveInstructionHandler.receiveInstruction(
          instructionId, receiveInstructionRequest, httpHeaders);
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(),
          "The allowed number of cases for this item have been received. Please report the remaining as overage.");
      final ErrorResponse errorResponse = e.getErrorResponse();
      assertEquals(errorResponse.getErrorCode(), GROCERY_OVERAGE_ERROR_CODE);
      assertEquals(e.getHttpStatus().value(), 500);
      assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void testReceiveInstruction_exception4_isGdcCancelInstructionErrorEnabled() {
    try {
      final Instruction completedInstruction = MockInstruction.getInstruction();
      completedInstruction.setId(1L);
      completedInstruction.setReceivedQuantity(0);
      completedInstruction.setCompleteTs(new Date());
      completedInstruction.setCompleteUserId("sysadmin");
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag("32612", IS_GDC_CANCEL_INSTRUCTION_ERROR_ENABLED, false);

      when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(completedInstruction);

      gdcReceiveInstructionHandler.receiveInstruction(
          instructionId, receiveInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(),
          "This pallet was cancelled by sysadmin, please start a new pallet to continue receiving.");
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getErrorResponse().getErrorCode(), GLS_RCV_INSTRUCTION_COMPLETED);
    }
  }

  @Test
  public void testReceiveInstruction_exception4_isGdcCancelInstructionErrorDisabled() {
    try {
      final Instruction completedInstruction = MockInstruction.getInstruction();
      completedInstruction.setId(1L);
      completedInstruction.setReceivedQuantity(0);
      completedInstruction.setCompleteTs(new Date());
      completedInstruction.setCompleteUserId("sysadmin");
      doReturn(false)
          .when(configUtils)
          .getConfiguredFeatureFlag("32612", IS_GDC_CANCEL_INSTRUCTION_ERROR_ENABLED, false);

      when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(completedInstruction);

      gdcReceiveInstructionHandler.receiveInstruction(
          instructionId, receiveInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(),
          "This pallet was cancelled by sysadmin, please start a new pallet to continue receiving.");
      assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
      assertEquals(
          e.getErrorResponse().getErrorCode(),
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED);
    }
  }

  @Test
  public void testReceiveInstruction_exception_white_wood_weight_exceed() {
    try {
      when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
      when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(MockInstruction.getInstruction());

      /* Mock quantity so that weight on wood white pallet exceed 2100lbs */
      receiveInstructionRequest.setContainerType("White wood");
      receiveInstructionRequest.setQuantity(2100);

      gdcReceiveInstructionHandler.receiveInstruction(
          instructionId, receiveInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException re) {
      assertEquals(re.getHttpStatus(), BAD_REQUEST);
      assertEquals(re.getMessage(), ReceivingException.EXCEED_WHITE_WOOD_PALLET_WIGHT);
    }
  }

  @Test
  public void testReceiveInstructionAsNormal_ManualGDC()
      throws ReceivingException, GDMRestApiClientException {
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstruction());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(true);
    when(gdcFlagReader.publishToWitronDisabled()).thenReturn(true);
    when(gdcFlagReader.isGdcLabelV2Enabled()).thenReturn(true);
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);

    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(gdcPutawayPublisher, times(0))
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(witronDCFinService, times(1))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(null));
    verify(movePublisher, times(0))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(instructionHelperService, times(2))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(0))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(DcFinAdjustRequest.class), any());
  }

  @Test
  public void testReceiveAsCorrection_ManualGDC()
      throws ReceivingException, GDMRestApiClientException {
    Instruction gdcInstruction = MockInstruction.getManualGdcInstruction();
    gdcInstruction.setIsReceiveCorrection(Boolean.TRUE);

    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(gdcInstruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(containerService)
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    doNothing()
        .when(receiptPublisher)
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(containerService)
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(true);
    when(gdcFlagReader.publishToWitronDisabled()).thenReturn(true);
    when(gdcFlagReader.isGdcLabelV2Enabled()).thenReturn(true);
    when(gdcFlagReader.publishToWFTDisabled()).thenReturn(true);

    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);

    assertEquals(printRequest.get("labelIdentifier"), "TAG-123");
    assertEquals(printRequest.get("formatName").toString(), "gdc_pallet_lpn");
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(gdcPutawayPublisher, times(0))
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(movePublisher, times(0))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(instructionHelperService, times(0))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(0))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(1))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(anyString(), anyString());
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(DcFinAdjustRequest.class), any());
  }

  @Test
  public void testReceiveInstructionAsNormal_withSlottingEnabled()
      throws ReceivingException, GDMRestApiClientException {
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);

    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstructionWithConvertedItemAtlas());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(gdcManualReceiveHelper, times(1))
        .buildInstructionFromSlotting(
            any(ReceiveInstructionRequest.class),
            any(Instruction.class),
            any(HttpHeaders.class),
            any(UpdateInstructionRequest.class));
    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(1))
        .publishMultipleContainersToInventory(any(List.class), any(HttpHeaders.class));
    verify(gdcPutawayPublisher, times(1))
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(witronDCFinService, times(1))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(null));
    verify(movePublisher, times(1))
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(instructionHelperService, times(2))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(0))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
  }

  @Test
  public void testReceiveInstruction_withPublishMoveV2()
      throws ReceivingException, GDMRestApiClientException {
    // Mocks
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);

    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstructionWithConvertedItemAtlas());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    // Invoke receive instruction
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    // Verify the response
    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(1))
        .publishMultipleContainersToInventory(any(List.class), any(HttpHeaders.class));
    verify(gdcPutawayPublisher, times(1))
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(witronDCFinService, times(1))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(null));
    verify(movePublisher, times(1))
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(instructionHelperService, times(2))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(0))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
  }

  @Test // OneAtlasNonConvertedItem
  public void testReceiveInstruction_withNonConvertedItem() throws ReceivingException {
    // Mocks
    when(gdcFlagReader.isAutomatedDC()).thenReturn(false);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(true);
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstruction());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    // data setup
    doNothing().when(dcFinRestApiClient).adjustOrVtr(any(DcFinAdjustRequest.class), any());

    // Invoke receive instruction
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    // Verify the response
    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(movePublisher, times(0))
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(DcFinAdjustRequest.class), any());
  }

  @Test
  public void testReceiveInstruction_withOneAtlasFalse() throws ReceivingException {
    // Mocks
    when(gdcFlagReader.isAutomatedDC()).thenReturn(false);
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstructionWithConvertedItemAtlas());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    // Invoke receive instruction
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    // Verify the response
    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(movePublisher, times(0))
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(DcFinAdjustRequest.class), any());
  }

  @Test
  public void testReceiveInstruction_withConvertedItem() throws ReceivingException {
    // Mocks
    when(gdcFlagReader.isAutomatedDC()).thenReturn(false);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(true);
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstructionWithConvertedItemAtlas());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);

    // Invoke receive instruction
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    // Verify the response
    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(movePublisher, times(1))
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(DcFinAdjustRequest.class), any());
  }

  @Test
  public void testReceiveInstruction_Automation_or_OneAtlasConvertedItem_kafka_receipts()
      throws ReceivingException {
    // Mocks
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isAutomatedDC()).thenReturn(false);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(true);
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstructionWithConvertedItemAtlas());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    List<ContainerDTO> containersDtoList = asList(new ContainerDTO());
    when(transformer.transformList(any(List.class))).thenReturn(containersDtoList);
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);

    // Invoke receive instruction
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    // Verify the response
    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    // to hit
    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(1)).setToBeAuditedTagGDC(any(ContainerDTO.class));
    verify(containerService, times(1))
        .publishMultipleContainersToInventory(any(List.class), any(HttpHeaders.class));
    verify(transformer, times(1)).transformList(any(List.class));

    verify(movePublisher, times(1))
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(DcFinAdjustRequest.class), any());
  }

  @Test
  public void testReceiveInstruction_withAutomatedDCTrue() throws ReceivingException {
    // Mocks
    when(gdcFlagReader.isAutomatedDC()).thenReturn(true);
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstructionWithConvertedItemAtlas());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);

    // Invoke receive instruction
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    // Verify the response
    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(movePublisher, times(0))
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(DcFinAdjustRequest.class), any());
  }

  @Test
  public void testReceiveInstructionAsCorrection_ManualGDCNonConvertedItem()
      throws ReceivingException, GDMRestApiClientException {
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setIsReceiveCorrection(true);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCFinApiDisabled()).thenReturn(false);
    when(gdcFlagReader.publishToWFTDisabled()).thenReturn(false);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);

    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class))).thenReturn(instruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(containerService)
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    doNothing()
        .when(receiptPublisher)
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(containerService)
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    // execution
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);
    // verify
    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(instructionHelperService, times(2))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(0))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(1))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));

    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .publishMultipleContainersToInventory(any(List.class), any(HttpHeaders.class));

    verify(movePublisher, times(0))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(movePublisher, times(0))
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(instructionHelperService, times(2))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(DcFinAdjustRequest.class), any());
  }

  @Test
  public void testReceiveAllPublishToWFT_exception()
      throws ReceivingException, GDMRestApiClientException {
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(true);
    when(gdcFlagReader.publishToWFTDisabled()).thenReturn(false);
    Instruction instruction1 = MockInstruction.getInstructionWithConvertedItemAtlas();
    instruction1.setDeliveryDocument(null);
    when(instructionPersisterService.getInstructionById(any(Long.class))).thenReturn(instruction1);

    // Bad Data Exception
    try {
      gdcReceiveInstructionHandler.receiveAll(instructionId, receiveAllRequest, httpHeaders);
    } catch (ReceivingBadDataException ex) {
      verify(movePublisher, times(0))
          .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
      verify(instructionHelperService, times(0))
          .publishInstruction(
              any(Instruction.class),
              any(),
              any(),
              any(Container.class),
              any(InstructionStatus.class),
              any(HttpHeaders.class));
    }

    Instruction instruction2 = MockInstruction.getInstructionWithConvertedItemAtlas();
    instruction2.setProjectedReceiveQty(0);
    when(instructionPersisterService.getInstructionById(any(Long.class))).thenReturn(instruction2);
    // Receiving Exception
    try {
      gdcReceiveInstructionHandler.receiveAll(instructionId, receiveAllRequest, httpHeaders);
    } catch (ReceivingException ex) {
      verify(movePublisher, times(0))
          .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
      verify(instructionHelperService, times(0))
          .publishInstruction(
              any(Instruction.class),
              any(),
              any(),
              any(Container.class),
              any(InstructionStatus.class),
              any(HttpHeaders.class));
    }

    // Generic Exception
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(new Instruction());
    try {
      gdcReceiveInstructionHandler.receiveAll(instructionId, receiveAllRequest, httpHeaders);
    } catch (Exception ex) {
      verify(movePublisher, times(0))
          .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
      verify(instructionHelperService, times(0))
          .publishInstruction(
              any(Instruction.class),
              any(),
              any(),
              any(Container.class),
              any(InstructionStatus.class),
              any(HttpHeaders.class));
    }
  }

  @Test
  public void testReceiveAllPublishToWFT_withJumpTrailerTrue()
      throws ReceivingException, GDMRestApiClientException {
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(true);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstructionWithConvertedItemAtlas());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(true);
    when(gdcFlagReader.publishToWitronDisabled()).thenReturn(true);
    when(gdcFlagReader.isGdcLabelV2Enabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    ReceiveAllResponse response =
        (ReceiveAllResponse)
            gdcReceiveInstructionHandler.receiveAll(instructionId, receiveAllRequest, httpHeaders);

    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(1))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));

    verify(gdcPutawayPublisher, times(0))
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(witronDCFinService, times(1))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(null));
    verify(movePublisher, times(1))
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(instructionHelperService, times(2))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(0))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(DcFinAdjustRequest.class), any());
  }

  @Test
  public void testReceiveInstructionAsNormal_withSlotNotFnd()
      throws ReceivingException, GDMRestApiClientException {
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    Instruction instruction1 = MockInstruction.getInstructionWithConvertedItemAtlas();
    LinkedTreeMap<String, Object> move = instruction1.getMove();
    move.put("toLocation", "SLT NT FND");
    instruction1.setMove(move);
    when(instructionPersisterService.getInstructionById(any(Long.class))).thenReturn(instruction1);
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    Map<String, Object> mockInstructionContainerMap1 = mockInstructionContainerMap;
    mockInstructionContainerMap1.put(ReceivingConstants.INSTRUCTION, instruction1);
    doReturn(mockInstructionContainerMap1)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(true);

    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    assertEquals(printRequest.get("labelIdentifier"), "a32612000000000001");
    assertEquals(
        printRequest.get("formatName").toString(), ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);

    verify(gdcManualReceiveHelper, times(1))
        .buildInstructionFromSlotting(
            any(ReceiveInstructionRequest.class),
            any(Instruction.class),
            any(HttpHeaders.class),
            any(UpdateInstructionRequest.class));
    verify(movePublisher, times(0))
        .publishMoveV2(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(movePublisher, times(0))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test
  public void testReceiveAsCorrection_OneAtlas_NotConvertedItem_notify_DcFin()
      throws ReceivingException, GDMRestApiClientException {
    Instruction gdcInstruction = MockInstruction.getManualGdcInstruction();
    gdcInstruction.setIsReceiveCorrection(Boolean.TRUE);

    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(gdcInstruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(containerService)
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    doNothing()
        .when(receiptPublisher)
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(containerService)
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    // Mocks
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(true);
    when(gdcFlagReader.publishToWitronDisabled()).thenReturn(true);
    when(gdcFlagReader.isGdcLabelV2Enabled()).thenReturn(true);
    when(gdcFlagReader.publishToWFTDisabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);

    ArgumentCaptor<DcFinAdjustRequest> dcFinAdjustRequestArgumentCaptor =
        ArgumentCaptor.forClass(DcFinAdjustRequest.class);

    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcReceiveInstructionHandler.receiveInstruction(
                instructionId, receiveInstructionRequest, httpHeaders);

    assertNotNull(response.getPrintJob().get("printRequests"));
    Map<String, Object> printJob = response.getPrintJob();
    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printJob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);

    assertEquals(printRequest.get("labelIdentifier"), "TAG-123");
    assertEquals(printRequest.get("formatName").toString(), "gdc_pallet_lpn");
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(gdcPutawayPublisher, times(0))
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    verify(movePublisher, times(0))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(instructionHelperService, times(0))
        .publishInstruction(
            any(Instruction.class),
            any(),
            any(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(0))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(1))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(anyString(), anyString());
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .publishMultipleContainersToInventory(any(List.class), any(HttpHeaders.class));

    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    // Dc Fin RC call
    verify(dcFinRestApiClient, atLeastOnce())
        .adjustOrVtr(dcFinAdjustRequestArgumentCaptor.capture(), any());
    final DcFinAdjustRequest adjustRequestArgumentCaptorValue =
        dcFinAdjustRequestArgumentCaptor.getValue();
    final TransactionsItem transactionsItem =
        adjustRequestArgumentCaptorValue.getTransactions().get(0);
    assertEquals(transactionsItem.getReasonCode(), INVENTORY_RECEIVING_CORRECTION_REASON_CODE + "");
    assertEquals(
        transactionsItem.getPrimaryQty(),
        container.getContainerItems().get(0).getQuantity().intValue());
    assertTrue(transactionsItem.getPrimaryQty() > 0);
  }

  @Test
  public void testGetContainerDtoList_nulls() {
    try {
      final List<ContainerDTO> containerDTOs =
          gdcReceiveInstructionHandler.getContainerDTOs(null, null);
      assertNotNull(containerDTOs);
    } catch (Exception e) {
      fail("should not throw exception");
    }
  }

  @Test
  public void testGetContainerDtoList_DcFinContract_Ensured() {
    DeliveryCacheValue gdmDeliveryCacheValue = new DeliveryCacheValue();
    gdmDeliveryCacheValue.setScacCode("PRPD1");
    gdmDeliveryCacheValue.setTrailerId("11223344");
    gdmDeliveryCacheValue.setFreightTermCode("PRP");
    gdmDeliveryCacheValue.setTotalBolFbq(200);
    final Container container = MockContainer.getContainerInfo();
    doReturn(asList(new ContainerDTO())).when(transformer).transformList(any(List.class));

    final List<ContainerDTO> containerDTOs =
        gdcReceiveInstructionHandler.getContainerDTOs(gdmDeliveryCacheValue, container);

    // verify
    assertNotNull(containerDTOs);
    assertTrue(containerDTOs.size() > 0);
    final ContainerDTO containerDTO = containerDTOs.get(0);
    assertNotNull(containerDTO);
    final Map<String, Object> containerMiscInfo = containerDTO.getContainerMiscInfo();
    assertNotNull(containerMiscInfo);
    assertEquals((String) containerMiscInfo.get(CARRIER_NAME), "PRPD1");
    assertEquals((String) containerMiscInfo.get(TRAILER_NBR), "11223344");
    assertEquals((String) containerMiscInfo.get(BILL_CODE), "PRP");
    assertEquals(containerMiscInfo.get(FREIGHT_BILL_QTY), 200);

    verify(containerService, atLeastOnce()).setToBeAuditedTagGDC(any(ContainerDTO.class));
  }

  @Test
  public void test_receiveIntoOss_ReceiveAndReject() throws ReceivingException, IOException {
    final ReceiveIntoOssRequest receiveIntoOssRequest_ReceiveAndReject =
        getReceiveIntoOssRequest("gdc_receive_intoOss_ui_request_ReceiveAndReject.json");
    Long deliveryNumber = 688567329L;
    when(configUtils.getConfiguredInstance(any(), eq(DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);

    final String gdmResponseJson = readFileFromCp("gdc_receive_intoOss_gdmResponse.json");
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(gdmResponseJson);
    doNothing()
        .when(gdcInstructionService)
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    HttpHeaders httpHeadersWithOrgUnitId = GdcHttpHeaders.getHeaders();
    httpHeadersWithOrgUnitId.add(ORG_UNIT_ID_HEADER, "3");

    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmResponseJson, GdmPOLineResponse.class);
    List<DeliveryDocument> gdmOnePoOneLineDoc = gdmPOLineResponse.getDeliveryDocuments();
    InstructionResponse createInstruction = new InstructionResponseImplNew();
    createInstruction.setDeliveryDocuments(gdmOnePoOneLineDoc);
    createInstruction.setInstruction(instruction);
    doReturn(createInstruction)
        .when(gdcInstructionService)
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());

    DeliveryCacheValue gdmDeliveryDetailsCached = new DeliveryCacheValue();
    doReturn(gdmDeliveryDetailsCached)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(CONTAINER_LIST, Arrays.asList(container));
    doReturn(responseMap)
        .when(instructionHelperService)
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());

    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCFinApiDisabled()).thenReturn(false);
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(DC_FIN_SERVICE), any()))
        .thenReturn(witronDCFinService);

    //
    ArgumentCaptor<PoLine> argCaptorPoLine = ArgumentCaptor.forClass(PoLine.class);

    // execute
    final ReceiveIntoOssResponse receiveIntoOssResponse =
        gdcReceiveInstructionHandler.receiveIntoOss(
            deliveryNumber, receiveIntoOssRequest_ReceiveAndReject, httpHeadersWithOrgUnitId);

    // verify
    assertNotNull(receiveIntoOssResponse);
    assertNotNull(receiveIntoOssResponse.getError());
    assertNull(receiveIntoOssResponse.getError().getErrPos());

    verify(gdcInstructionService, times(1))
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    verify(gdcInstructionService, times(1))
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());
    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            argCaptorPoLine.capture(),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());
    // verify Reject
    final PoLine argCaptorPoLineValue = argCaptorPoLine.getValue();
    assertNotNull(argCaptorPoLineValue);
    assertNotNull(argCaptorPoLineValue.getRejectQty());
    // ensures to call deliveryService.recordPalletReject
    assertTrue(argCaptorPoLineValue.getRejectQty() > 0);
    assertEquals(argCaptorPoLineValue.getRejectQtyUOM(), "ZA");
    assertEquals(argCaptorPoLineValue.getRejectReasonCode(), "R10");

    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(1))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));
    verify(witronDCFinService, times(1))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(null));
  }

  @Test
  public void test_receiveIntoOss_receiveOnly() throws ReceivingException, IOException {
    final ReceiveIntoOssRequest receiveIntoOssRequestReceiveOnly =
        getReceiveIntoOssRequest("gdc_receive_intoOss_ui_request_ReceiveOnly.json");
    Long deliveryNumber = 688567329L;
    when(configUtils.getConfiguredInstance(any(), eq(DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);

    final String gdmResponseJson = readFileFromCp("gdc_receive_intoOss_gdmResponse.json");
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(gdmResponseJson);
    doNothing()
        .when(gdcInstructionService)
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    HttpHeaders httpHeadersWithOrgUnitId = GdcHttpHeaders.getHeaders();
    httpHeadersWithOrgUnitId.add(ORG_UNIT_ID_HEADER, "3");

    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmResponseJson, GdmPOLineResponse.class);
    List<DeliveryDocument> gdmOnePoOneLineDoc = gdmPOLineResponse.getDeliveryDocuments();
    InstructionResponse createInstruction = new InstructionResponseImplNew();
    createInstruction.setDeliveryDocuments(gdmOnePoOneLineDoc);
    createInstruction.setInstruction(instruction);
    doReturn(createInstruction)
        .when(gdcInstructionService)
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());

    DeliveryCacheValue gdmDeliveryDetailsCached = new DeliveryCacheValue();
    doReturn(gdmDeliveryDetailsCached)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(CONTAINER_LIST, Arrays.asList(container));
    doReturn(responseMap)
        .when(instructionHelperService)
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());

    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCFinApiDisabled()).thenReturn(false);
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(DC_FIN_SERVICE), any()))
        .thenReturn(witronDCFinService);

    // execute
    final ReceiveIntoOssResponse receiveIntoOssResponse =
        gdcReceiveInstructionHandler.receiveIntoOss(
            deliveryNumber, receiveIntoOssRequestReceiveOnly, httpHeadersWithOrgUnitId);

    // verify
    assertNotNull(receiveIntoOssResponse);
    assertNotNull(receiveIntoOssResponse.getError());
    assertNull(receiveIntoOssResponse.getError().getErrPos());

    verify(gdcInstructionService, times(1))
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    verify(gdcInstructionService, times(1))
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());
    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionHelperService, times(1))
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());
    verify(instructionService, times(1))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(1))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));
    verify(witronDCFinService, times(1))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(null));
  }

  @Test
  public void test_receiveIntoOss_RejectOnly_updateMasterReceipt()
      throws ReceivingException, IOException {
    final ReceiveIntoOssRequest receiveIntoOssRequestRejectOnly =
        getReceiveIntoOssRequest("gdc_receive_intoOss_ui_request_RejectOnly.json");
    Long deliveryNumber = 688567329L;
    when(configUtils.getConfiguredInstance(any(), eq(DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);

    final String gdmResponseJson = readFileFromCp("gdc_receive_intoOss_gdmResponse.json");
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(gdmResponseJson);
    doNothing()
        .when(gdcInstructionService)
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    HttpHeaders httpHeadersWithOrgUnitId = GdcHttpHeaders.getHeaders();
    httpHeadersWithOrgUnitId.add(ORG_UNIT_ID_HEADER, "3");

    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmResponseJson, GdmPOLineResponse.class);
    List<DeliveryDocument> gdmOnePoOneLineDoc = gdmPOLineResponse.getDeliveryDocuments();
    InstructionResponse createInstruction = new InstructionResponseImplNew();
    createInstruction.setDeliveryDocuments(gdmOnePoOneLineDoc);
    createInstruction.setInstruction(instruction);
    doReturn(createInstruction)
        .when(gdcInstructionService)
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());

    DeliveryCacheValue gdmDeliveryDetailsCached = new DeliveryCacheValue();
    doReturn(gdmDeliveryDetailsCached)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(CONTAINER, container);
    doReturn(responseMap)
        .when(instructionHelperService)
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());

    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCFinApiDisabled()).thenReturn(false);
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(DC_FIN_SERVICE), any()))
        .thenReturn(witronDCFinService);
    when(receiptService
            .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyLong(), anyString(), anyInt()))
        .thenReturn(new Receipt());

    // execute
    final ReceiveIntoOssResponse receiveIntoOssResponse =
        gdcReceiveInstructionHandler.receiveIntoOss(
            deliveryNumber, receiveIntoOssRequestRejectOnly, httpHeadersWithOrgUnitId);

    // verify
    assertNotNull(receiveIntoOssResponse);
    assertNotNull(receiveIntoOssResponse.getError());
    assertNull(receiveIntoOssResponse.getError().getErrPos());

    verify(gdcInstructionService, times(1))
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    verify(gdcInstructionService, times(0))
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());
    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(anyString(), anyString());
    verify(deliveryCacheServiceInMemoryImpl, times(0))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionHelperService, times(0))
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));
    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());

    verify(receiptService, times(2))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());
    //    verify(receiptService, times(1))
    //        .updateRejects(anyInt(), anyString(), anyString(), anyString(), any(Receipt.class));
    verify(receiptService, times(2)).saveReceipt(any(Receipt.class));
  }

  @Test
  public void test_receiveIntoOss_RejectOnly_createNewMasterReceipt()
      throws ReceivingException, IOException {
    final ReceiveIntoOssRequest receiveIntoOssRequestRejectOnly =
        getReceiveIntoOssRequest("gdc_receive_intoOss_ui_request_RejectOnly.json");
    Long deliveryNumber = 688567329L;
    when(configUtils.getConfiguredInstance(any(), eq(DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);

    final String gdmResponseJson = readFileFromCp("gdc_receive_intoOss_gdmResponse.json");
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(gdmResponseJson);
    doNothing()
        .when(gdcInstructionService)
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    HttpHeaders httpHeadersWithOrgUnitId = GdcHttpHeaders.getHeaders();
    httpHeadersWithOrgUnitId.add(ORG_UNIT_ID_HEADER, "3");

    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmResponseJson, GdmPOLineResponse.class);
    List<DeliveryDocument> gdmOnePoOneLineDoc = gdmPOLineResponse.getDeliveryDocuments();
    InstructionResponse createInstruction = new InstructionResponseImplNew();
    createInstruction.setDeliveryDocuments(gdmOnePoOneLineDoc);
    createInstruction.setInstruction(instruction);
    doReturn(createInstruction)
        .when(gdcInstructionService)
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());

    DeliveryCacheValue gdmDeliveryDetailsCached = new DeliveryCacheValue();
    doReturn(gdmDeliveryDetailsCached)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(CONTAINER, container);
    doReturn(responseMap)
        .when(instructionHelperService)
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());

    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCFinApiDisabled()).thenReturn(false);
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(DC_FIN_SERVICE), any()))
        .thenReturn(witronDCFinService);
    when(receiptService
            .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyLong(), anyString(), anyInt()))
        .thenReturn(null);

    // execute
    final ReceiveIntoOssResponse receiveIntoOssResponse =
        gdcReceiveInstructionHandler.receiveIntoOss(
            deliveryNumber, receiveIntoOssRequestRejectOnly, httpHeadersWithOrgUnitId);

    // verify
    assertNotNull(receiveIntoOssResponse);
    assertNotNull(receiveIntoOssResponse.getError());
    assertNull(receiveIntoOssResponse.getError().getErrPos());

    verify(gdcInstructionService, times(1))
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    verify(gdcInstructionService, times(0))
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());
    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(anyString(), anyString());
    verify(deliveryCacheServiceInMemoryImpl, times(0))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionHelperService, times(0))
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));
    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());

    verify(receiptService, times(2))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());
    verify(receiptService, times(1))
        .updateRejects(anyInt(), anyString(), anyString(), anyString(), any(Receipt.class));
    verify(receiptService, times(2)).saveReceipt(any(Receipt.class));
  }

  @Test
  public void test_receiveIntoOss_DamageOnly_updateMasterReceipt() throws ReceivingException {
    final ReceiveIntoOssRequest receiveIntoOssRequestRejectOnly =
        getReceiveIntoOssRequest("gdc_receive_intoOss_ui_request_DamageOnly.json");
    Long deliveryNumber = 688567329L;
    when(configUtils.getConfiguredInstance(any(), eq(DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);

    final String gdmResponseJson = readFileFromCp("gdc_receive_intoOss_gdmResponse.json");
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(gdmResponseJson);
    doNothing()
        .when(gdcInstructionService)
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    HttpHeaders httpHeadersWithOrgUnitId = GdcHttpHeaders.getHeaders();
    httpHeadersWithOrgUnitId.add(ORG_UNIT_ID_HEADER, "3");

    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmResponseJson, GdmPOLineResponse.class);
    List<DeliveryDocument> gdmOnePoOneLineDoc = gdmPOLineResponse.getDeliveryDocuments();
    InstructionResponse createInstruction = new InstructionResponseImplNew();
    createInstruction.setDeliveryDocuments(gdmOnePoOneLineDoc);
    createInstruction.setInstruction(instruction);
    doReturn(createInstruction)
        .when(gdcInstructionService)
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());

    DeliveryCacheValue gdmDeliveryDetailsCached = new DeliveryCacheValue();
    doReturn(gdmDeliveryDetailsCached)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(CONTAINER, container);
    doReturn(responseMap)
        .when(instructionHelperService)
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());

    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCFinApiDisabled()).thenReturn(false);
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(DC_FIN_SERVICE), any()))
        .thenReturn(witronDCFinService);
    when(receiptService
            .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyLong(), anyString(), anyInt()))
        .thenReturn(new Receipt());

    // execute
    final ReceiveIntoOssResponse receiveIntoOssResponse =
        gdcReceiveInstructionHandler.receiveIntoOss(
            deliveryNumber, receiveIntoOssRequestRejectOnly, httpHeadersWithOrgUnitId);

    // verify
    assertNotNull(receiveIntoOssResponse);
    assertNotNull(receiveIntoOssResponse.getError());
    assertNull(receiveIntoOssResponse.getError().getErrPos());

    verify(gdcInstructionService, times(1))
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    verify(gdcInstructionService, times(0))
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());
    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(anyString(), anyString());
    verify(deliveryCacheServiceInMemoryImpl, times(0))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionHelperService, times(0))
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));
    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());

    verify(receiptService, times(2))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());
    verify(receiptService, times(1))
        .updateDamages(anyInt(), anyString(), anyString(), anyString(), any(Receipt.class));
    verify(receiptService, times(2)).saveReceipt(any(Receipt.class));
  }

  @Test
  public void test_receiveIntoOss_DamagetOnly_createNewMasterReceipt()
      throws ReceivingException, IOException {
    final ReceiveIntoOssRequest receiveIntoOssRequestRejectOnly =
        getReceiveIntoOssRequest("gdc_receive_intoOss_ui_request_DamageOnly.json");
    Long deliveryNumber = 688567329L;
    when(configUtils.getConfiguredInstance(any(), eq(DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);

    final String gdmResponseJson = readFileFromCp("gdc_receive_intoOss_gdmResponse.json");
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(gdmResponseJson);
    doNothing()
        .when(gdcInstructionService)
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    HttpHeaders httpHeadersWithOrgUnitId = GdcHttpHeaders.getHeaders();
    httpHeadersWithOrgUnitId.add(ORG_UNIT_ID_HEADER, "3");

    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmResponseJson, GdmPOLineResponse.class);
    List<DeliveryDocument> gdmOnePoOneLineDoc = gdmPOLineResponse.getDeliveryDocuments();
    InstructionResponse createInstruction = new InstructionResponseImplNew();
    createInstruction.setDeliveryDocuments(gdmOnePoOneLineDoc);
    createInstruction.setInstruction(instruction);
    doReturn(createInstruction)
        .when(gdcInstructionService)
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());

    DeliveryCacheValue gdmDeliveryDetailsCached = new DeliveryCacheValue();
    doReturn(gdmDeliveryDetailsCached)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(CONTAINER, container);
    doReturn(responseMap)
        .when(instructionHelperService)
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());

    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCFinApiDisabled()).thenReturn(false);
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(DC_FIN_SERVICE), any()))
        .thenReturn(witronDCFinService);
    when(receiptService
            .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyLong(), anyString(), anyInt()))
        .thenReturn(null);

    // execute
    final ReceiveIntoOssResponse receiveIntoOssResponse =
        gdcReceiveInstructionHandler.receiveIntoOss(
            deliveryNumber, receiveIntoOssRequestRejectOnly, httpHeadersWithOrgUnitId);

    // verify
    assertNotNull(receiveIntoOssResponse);
    assertNotNull(receiveIntoOssResponse.getError());
    assertNull(receiveIntoOssResponse.getError().getErrPos());

    verify(gdcInstructionService, times(1))
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    verify(gdcInstructionService, times(0))
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());
    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(anyString(), anyString());
    verify(deliveryCacheServiceInMemoryImpl, times(0))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionHelperService, times(0))
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));
    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());

    verify(receiptService, times(2))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());
    verify(receiptService, times(1))
        .updateDamages(anyInt(), anyString(), anyString(), anyString(), any(Receipt.class));
    verify(receiptService, times(2)).saveReceipt(any(Receipt.class));
  }

  @Test
  public void test_receiveIntoOss_NoReceiveNoReject() throws ReceivingException, IOException {
    final ReceiveIntoOssRequest receiveIntoOssRequest_NoReceiveNoReject =
        getReceiveIntoOssRequest("gdc_receive_intoOss_ui_request_NoReceiveNoReject.json");
    Long deliveryNumber = 688567329L;
    when(configUtils.getConfiguredInstance(any(), eq(DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);

    final String gdmResponseJson = readFileFromCp("gdc_receive_intoOss_gdmResponse.json");
    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(gdmResponseJson);
    doNothing()
        .when(gdcInstructionService)
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    HttpHeaders httpHeadersWithOrgUnitId = GdcHttpHeaders.getHeaders();
    httpHeadersWithOrgUnitId.add(ORG_UNIT_ID_HEADER, "3");

    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmResponseJson, GdmPOLineResponse.class);
    List<DeliveryDocument> gdmOnePoOneLineDoc = gdmPOLineResponse.getDeliveryDocuments();
    InstructionResponse createInstruction = new InstructionResponseImplNew();
    createInstruction.setDeliveryDocuments(gdmOnePoOneLineDoc);
    createInstruction.setInstruction(instruction);
    doReturn(createInstruction)
        .when(gdcInstructionService)
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());

    DeliveryCacheValue gdmDeliveryDetailsCached = new DeliveryCacheValue();
    doReturn(gdmDeliveryDetailsCached)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(CONTAINER, container);
    doReturn(responseMap)
        .when(instructionHelperService)
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());

    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isDCFinApiDisabled()).thenReturn(false);
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(any(), eq(DC_FIN_SERVICE), any()))
        .thenReturn(witronDCFinService);

    // execute
    final ReceiveIntoOssResponse receiveIntoOssResponse =
        gdcReceiveInstructionHandler.receiveIntoOss(
            deliveryNumber, receiveIntoOssRequest_NoReceiveNoReject, httpHeadersWithOrgUnitId);

    // verify
    assertNotNull(receiveIntoOssResponse);
    assertNotNull(receiveIntoOssResponse.getError());
    assertNull(receiveIntoOssResponse.getError().getErrPos());

    verify(gdcInstructionService, times(1))
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    verify(gdcInstructionService, times(0))
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());
    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(anyString(), anyString());
    verify(deliveryCacheServiceInMemoryImpl, times(0))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionHelperService, times(0))
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));
    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
  }

  private ReceiveIntoOssRequest getReceiveIntoOssRequest(final String fileName) {
    final String receiveIntoOssRequestJsonString = readFileFromCp(fileName);
    final ReceiveIntoOssRequest receiveIntoOssRequestRejectOnly =
        gson.fromJson(receiveIntoOssRequestJsonString, ReceiveIntoOssRequest.class);
    return receiveIntoOssRequestRejectOnly;
  }

  @Test
  public void test_receiveIntoOss_ui_bad_request_errorResponse_LineLevelErrorFirst()
      throws ReceivingException, IOException {
    final String receiveIntoOssRequestJsonString =
        readFileFromCp("gdc_receive_intoOss_ui_bad_request.json");
    final ReceiveIntoOssRequest receiveIntoOssRequest =
        gson.fromJson(receiveIntoOssRequestJsonString, ReceiveIntoOssRequest.class);
    Long deliveryNumber = 688567329L;
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(deliveryService);

    final String gdmResponseJson = readFileFromCp("gdc_receive_intoOss_gdmResponse.json");
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmResponseJson, GdmPOLineResponse.class);

    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(gdmResponseJson);
    doNothing()
        .when(gdcInstructionService)
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    HttpHeaders httpHeadersWithOrgUnitId = GdcHttpHeaders.getHeaders();
    httpHeadersWithOrgUnitId.add(ORG_UNIT_ID_HEADER, "3");

    InstructionResponse createInstruction = new InstructionResponseImplNew();
    List<DeliveryDocument> gdmOnePoOneLineDoc = gdmPOLineResponse.getDeliveryDocuments();
    createInstruction.setDeliveryDocuments(gdmOnePoOneLineDoc);
    createInstruction.setInstruction(instruction);
    doReturn(createInstruction)
        .when(gdcInstructionService)
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());

    DeliveryCacheValue gdmDeliveryDetailsCached = new DeliveryCacheValue();
    doReturn(gdmDeliveryDetailsCached)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(CONTAINER, container);
    doReturn(responseMap)
        .when(instructionHelperService)
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());

    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);

    // execute
    final ReceiveIntoOssResponse receiveIntoOssResponse =
        gdcReceiveInstructionHandler.receiveIntoOss(
            deliveryNumber, receiveIntoOssRequest, httpHeadersWithOrgUnitId);

    // verify
    verify(configUtils, times(1)).getConfiguredInstance(any(), any(), any());
    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
    verify(gdcInstructionService, times(1))
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));

    assertNotNull(receiveIntoOssResponse);
    assertNotNull(receiveIntoOssResponse.getError());
    final HashMap<String, Po> errPos = receiveIntoOssResponse.getError().getErrPos();
    assertNotNull(errPos);
    final Po po = errPos.get("1688567328");
    final HashMap<Integer, PoLine> errLines = po.getErrLines();
    assertEquals(errLines.size(), 2);

    final PoLine poLine1 = errLines.get(1);
    final String errorCode1 = poLine1.getErrorCode();
    final String errorMessage1 = poLine1.getErrorMessage();
    assertEquals(errorCode1, "PoLineLevelError");
    assertEquals(errorMessage1, "Negative quantity (-1) cannot be received");

    final PoLine poLine2 = errLines.get(2);
    final String errorCode2 = poLine2.getErrorCode();
    final String errorMessage2 = poLine2.getErrorMessage();
    assertEquals(errorCode2, "PoLineLevelError");
    final String err2 = "No Po Lines found for Po Number: [1688567328 ln#2]";
    assertEquals(errorMessage2, err2);

    final String actualErrorResponseJson = gson.toJson(receiveIntoOssResponse);
    String expectedErrorResponseJson =
        readFileFromCp("gdc_receive_intoOss_ui_bad_error_response.json");
    assertTrue(actualErrorResponseJson.contains(err2));
    assertTrue(expectedErrorResponseJson.contains(err2));
    // assertEquals(actualErrorResponseJson, expectedErrorResponseJson);
  }

  @Test
  public void test_receiveIntoOss_ui_bad_request_errorResponse2_PoLevelErrorFirst()
      throws ReceivingException, IOException {
    final String receiveIntoOssRequestJsonString =
        readFileFromCp("gdc_receive_intoOss_ui_bad_request2.json");
    final ReceiveIntoOssRequest receiveIntoOssRequest =
        gson.fromJson(receiveIntoOssRequestJsonString, ReceiveIntoOssRequest.class);
    Long deliveryNumber = 688567329L;
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(deliveryService);

    final String gdmResponseJson = readFileFromCp("gdc_receive_intoOss_gdmResponse.json");
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmResponseJson, GdmPOLineResponse.class);

    when(deliveryService.getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class)))
        .thenReturn(gdmResponseJson);
    doNothing()
        .when(gdcInstructionService)
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    HttpHeaders httpHeadersWithOrgUnitId = GdcHttpHeaders.getHeaders();
    httpHeadersWithOrgUnitId.add(ORG_UNIT_ID_HEADER, "3");

    InstructionResponse createInstruction = new InstructionResponseImplNew();
    List<DeliveryDocument> gdmOnePoOneLineDoc = gdmPOLineResponse.getDeliveryDocuments();
    createInstruction.setDeliveryDocuments(gdmOnePoOneLineDoc);
    createInstruction.setInstruction(instruction);
    doReturn(createInstruction)
        .when(gdcInstructionService)
        .serveInstructionRequestIntoOss(anyLong(), any(HttpHeaders.class), anyList());

    DeliveryCacheValue gdmDeliveryDetailsCached = new DeliveryCacheValue();
    doReturn(gdmDeliveryDetailsCached)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(CONTAINER, container);
    doReturn(responseMap)
        .when(instructionHelperService)
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(PoLine.class),
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyList());

    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isKafkaReceiptsEnabled()).thenReturn(true);

    // execute
    final ReceiveIntoOssResponse receiveIntoOssResponse =
        gdcReceiveInstructionHandler.receiveIntoOss(
            deliveryNumber, receiveIntoOssRequest, httpHeadersWithOrgUnitId);

    // verify
    verify(configUtils, times(1)).getConfiguredInstance(any(), any(), any());
    verify(deliveryService, times(1))
        .getDeliveryByDeliveryNumber(anyLong(), any(HttpHeaders.class));
    verify(gdcInstructionService, times(1))
        .enrichDeliveryStatusAndStateReasonCode(any(), any(), any());
    verify(instructionService, times(0))
        .publishConsolidatedContainer(any(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .publishMultipleContainersToInventory(anyList(), any(HttpHeaders.class));

    assertNotNull(receiveIntoOssResponse);
    assertNotNull(receiveIntoOssResponse.getError());
    final HashMap<String, Po> errPos = receiveIntoOssResponse.getError().getErrPos();
    assertNotNull(errPos);
    final Po po = errPos.get("1688567328");
    final HashMap<Integer, PoLine> errLines = po.getErrLines();
    assertEquals(errLines.size(), 2);

    final PoLine poLine1 = errLines.get(1);
    final String errorCode1 = poLine1.getErrorCode();
    final String errorMessage1 = poLine1.getErrorMessage();
    assertEquals(errorCode1, "PoLineLevelError");
    assertEquals(errorMessage1, "Negative quantity (-1) cannot be received");

    final PoLine poLine2 = errLines.get(2);
    final String errorCode2 = poLine2.getErrorCode();
    final String errorMessage2 = poLine2.getErrorMessage();
    assertEquals(errorCode2, "PoLineLevelError");
    final String err2 = "No Po Lines found for Po Number: [1688567328 ln#2]";
    assertEquals(errorMessage2, err2);

    final String actualErrorResponseJson = gson.toJson(receiveIntoOssResponse);
    String expectedErrorResponseJson =
        readFileFromCp("gdc_receive_intoOss_ui_bad_error_response.json");
    assertTrue(actualErrorResponseJson.contains(err2));
    assertTrue(expectedErrorResponseJson.contains(err2));
    // assertEquals(actualErrorResponseJson, expectedErrorResponseJson);

  }

  public void testReceiveInstruction_wmBeyondThresholdDateWarning() {
    Date userEnteredDate =
        Date.from(Instant.now().plus(100, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS));
    try {
      receiveInstructionRequest.setRotateDate(userEnteredDate);
      when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(MockInstruction.getWmInstruction());
      when(configUtils.getConfiguredFeatureFlag(
              getFacilityNum().toString(), SAFEGUARD_MAX_ALLOWED_STORAGE, false))
          .thenReturn(true);

      gdcReceiveInstructionHandler.receiveInstruction(
          instructionId, receiveInstructionRequest, httpHeaders);
    } catch (ReceivingBadDataException rbe) {
      String userEnteredDateString =
          userEnteredDate
              .toInstant()
              .atZone(ZoneId.of(UTC_TIME_ZONE))
              .toLocalDateTime()
              .format(DateTimeFormatter.ofPattern(INVALID_EXP_DATE_FORMAT));
      String errorMessage =
          "The date (%s) entered is beyond the number Max of (%s) days allowed for this item. To continue, confirm the Date or press Cancel to correct the date Entered";
      errorMessage = String.format(errorMessage, userEnteredDateString, 70);
      assertEquals(rbe.getErrorCode(), "GLS-RCV-BEYOND-THRESHOLD-DATE-WARN-CODE-400");
      assertEquals(rbe.getDescription(), errorMessage);
    } catch (ReceivingException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testReceiveInstruction_samsBeyondThresholdDateWarning() {
    Date userEnteredDate =
        Date.from(Instant.now().plus(100, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS));
    try {
      receiveInstructionRequest.setRotateDate(userEnteredDate);
      when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(MockInstruction.getSamsInstruction());
      when(configUtils.getConfiguredFeatureFlag(
              getFacilityNum().toString(), SAFEGUARD_MAX_ALLOWED_STORAGE, false))
          .thenReturn(true);

      gdcReceiveInstructionHandler.receiveInstruction(
          instructionId, receiveInstructionRequest, httpHeaders);
    } catch (ReceivingBadDataException rbe) {
      String userEnteredDateString =
          userEnteredDate
              .toInstant()
              .atZone(ZoneId.of(UTC_TIME_ZONE))
              .toLocalDateTime()
              .format(DateTimeFormatter.ofPattern(INVALID_EXP_DATE_FORMAT));
      String errorMessage =
          "The date (%s) entered is beyond the number Max of (%s) days allowed for this item. To continue, confirm the Date or press Cancel to correct the date Entered";
      errorMessage = String.format(errorMessage, userEnteredDateString, 90);
      assertEquals(rbe.getErrorCode(), "GLS-RCV-BEYOND-THRESHOLD-DATE-WARN-CODE-400");
      assertEquals(rbe.getDescription(), errorMessage);
    } catch (ReceivingException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testReceiveInstruction_samsWithRotationTypeCode4() {
    Date userEnteredDate =
        Date.from(Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS));
    try {
      receiveInstructionRequest.setRotateDate(userEnteredDate);
      when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(MockInstruction.getSamsInstructionWithRotationTypeCode4());
      when(configUtils.getConfiguredFeatureFlag(
              getFacilityNum().toString(), SAFEGUARD_MAX_ALLOWED_STORAGE, false))
          .thenReturn(true);

      gdcReceiveInstructionHandler.receiveInstruction(
          instructionId, receiveInstructionRequest, httpHeaders);
    } catch (ReceivingBadDataException rbe) {
      String userEnteredDateString =
          userEnteredDate
              .toInstant()
              .atZone(ZoneId.of(UTC_TIME_ZONE))
              .toLocalDateTime()
              .format(DateTimeFormatter.ofPattern(INVALID_EXP_DATE_FORMAT));
      String errorMessage =
          "The date (%s) entered is beyond todays date, this item requires a pack date Prior to today";
      errorMessage = String.format(errorMessage, userEnteredDateString);
      assertEquals(rbe.getErrorCode(), "GLS-RCV-BEYOND-THRESHOLD-DATE-WARN-CODE-400");
      assertEquals(rbe.getDescription(), errorMessage);
    } catch (ReceivingException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testReceiveInstructionAsNormal_fromOss_toMain()
      throws ReceivingException, GDMRestApiClientException {
    when(gdcFlagReader.isAutomatedDC()).thenReturn(true);
    when(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()).thenReturn(true);
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Central");
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(witronDCFinService);
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstruction());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(mockInstructionContainerMap)
        .when(instructionHelperService)
        .receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(instructionService)
        .publishConsolidatedContainer(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), anyBoolean());
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(any(Container.class), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
    when(gdcFlagReader.isDCFinHttpReceiptsEnabled()).thenReturn(true);
    when(gdcFlagReader.isMqReceiptsEnabled()).thenReturn(true);
    //
    String mockResponse = readFileFromCp("gdc_receive_intoOss_gdmResponse.json");
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(mockResponse, GdmPOLineResponse.class);
    List<DeliveryDocument> onePoOneLineDoc = gdmPOLineResponse.getDeliveryDocuments();
    DeliveryDetails gdmDeliveryDetails = convertJsonToObject(mockResponse, DeliveryDetails.class);
    gdcInstructionService.enrichDeliveryStatusAndStateReasonCode(
        onePoOneLineDoc,
        getDeliveryStatus(gdmDeliveryDetails.getDeliveryStatus()),
        gdmDeliveryDetails.getStateReasonCodes());
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    mockInstructionResponse.setDeliveryDocuments(onePoOneLineDoc);
    final ReceiveIntoOssRequest receiveIntoOssRequest =
        getReceiveIntoOssRequest("gdc_receive_intoOss_ui_request_ReceiveOnly.json");
    final PoLine poLineReq = receiveIntoOssRequest.getPos().get(0).getLines().get(0);
    final Instruction instruction = MockInstruction.getInstruction();
    mockInstructionResponse.setInstruction(instruction);

    HashMap<String, Object> map = new HashMap<>();
    map.put(INSTRUCTION, instruction);
    map.put(CONTAINER_LIST, Arrays.asList(container));

    doReturn(map)
        .when(instructionHelperService)
        .completeInstructionAndCreateContainerAndReceiptAndReject(
            any(), any(), any(), any(), anyList());

    ArgumentCaptor<HttpHeaders> receiptPostingHeadersArgumentCaptor =
        ArgumentCaptor.forClass(HttpHeaders.class);

    // Execute
    gdcReceiveInstructionHandler.receiveInstructionIntoOss(
        mockInstructionResponse, poLineReq, httpHeaders, 1);

    // Verify
    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(deliveryCacheServiceInMemoryImpl, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(instructionService, times(1))
        .publishConsolidatedContainer(
            any(Container.class), receiptPostingHeadersArgumentCaptor.capture(), anyBoolean());

    assert receiptPostingHeadersArgumentCaptor
        .getValue()
        .getFirst("WMT-CorrelationId")
        .contains("-100");
    verify(witronDCFinService, times(1))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(null));
    verify(containerService, times(0))
        .postReceiptsReceiveAsCorrection(any(Container.class), any(HttpHeaders.class));
    verify(receiptPublisher, times(0))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(containerService, times(0))
        .postFinalizePoOsdrToGdm(
            any(HttpHeaders.class), anyLong(), anyString(), any(FinalizePORequestBody.class));
  }
}
