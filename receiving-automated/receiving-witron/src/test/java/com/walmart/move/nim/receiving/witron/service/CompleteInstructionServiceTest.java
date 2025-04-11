package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.JMSInstructionPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.witron.mock.data.WitronContainer;
import com.walmart.move.nim.receiving.witron.publisher.WitronDCFinServiceImpl;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.SneakyThrows;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class CompleteInstructionServiceTest extends ReceivingTestBase {
  @InjectMocks private GdcInstructionService gdcInstructionService;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private PrintJobService printJobService;
  @Mock private ContainerService containerService;
  @Mock private WitronDCFinServiceImpl witronDCFinService;
  @Mock private AppConfig appConfig;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private MovePublisher movePublisher;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;
  @Mock private AsyncPersister asyncPersister;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private DeliveryCacheServiceInMemoryImpl deliveryCacheServiceInMemoryImpl;
  @Mock private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Mock private GdcPutawayPublisher gdcPutawayPublisher;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private JMSInstructionPublisher jmsInstructionPublisher;

  private final Gson gson = new Gson();
  private Instruction openInstruction;
  private Instruction completedInstruction;
  private final PrintJob printJob = MockInstruction.getPrintJob();
  private final Container container = MockInstruction.getContainer();
  private final Container containerRotateDate = MockInstruction.getContainer();

  private final HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private CompleteInstructionRequest completeInstructionRequest;
  private DeliveryCacheValue deliveryCacheValue;
  private final JsonObject defaultFeatureFlagsByFacility = new JsonObject();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");

    completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setPrinterId(101);
    completeInstructionRequest.setPrinterName("PrinterName_Xyz");

    ReflectionTestUtils.setField(
        gdcInstructionService, "purchaseReferenceValidator", purchaseReferenceValidator);

    ReflectionTestUtils.setField(
        gdcInstructionService, "instructionHelperService", instructionHelperService);
  }

  @BeforeMethod
  public void setUpTestDataBeforeEachTest() {
    openInstruction = MockInstruction.getOpenInstruction();
    openInstruction.setIsReceiveCorrection(false);
    completedInstruction = MockInstruction.getCompleteInstruction();

    deliveryCacheValue = new DeliveryCacheValue();
    deliveryCacheValue.setBolWeight(123.45f);
    deliveryCacheValue.setTotalBolFbq(200);
    deliveryCacheValue.setTrailerId("55341");

    when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any()))
        .thenReturn(witronDCFinService);

    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(jmsInstructionPublisher);
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(jmsPublisher);
    reset(movePublisher);
    reset(instructionRepository);
    reset(gdcPutawayPublisher);
    reset(containerService);
    reset(witronDCFinService);
    reset(purchaseReferenceValidator);
    reset(asyncPersister);
    reset(receiptPublisher);
  }

  @BeforeClass
  public void setReflectionTestUtil() {
    ReflectionTestUtils.setField(gdcInstructionService, "gson", gson);
    ReflectionTestUtils.setField(
        gdcInstructionService, "instructionPersisterService", instructionPersisterService);
  }

  @Test
  public void completeInstruction_wiht_rotate_date_label_change() throws ReceivingException {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

    doReturn(true).when(configUtils).isShowRotateDateOnPrintLabelEnabled(any(Integer.class));
    String updatedPrintRequest =
        "[{ttlInHours=1, labelIdentifier=a328990000000000000106509, data=[{value=TR ED 3PC FRY/GRL RD, key=DESC1}, {value=2, key=QTY}, {value=-, key=ROTATEDATE}], formatName=pallet_lpn_format}]";
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              gdcInstructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals(response.getInstruction().getActivityName(), "DA");
    } catch (ReceivingException ex) {
      fail();
    }

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test
  public void completeInstruction_Success() throws ReceivingException {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(tenantSpecificConfigReader.getDCTimeZone(any())).thenReturn("US/Central");

    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              gdcInstructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals(response.getInstruction().getActivityName(), "DA");
      assertNotNull(response.getPrintJob().get("printRequests"));
    } catch (ReceivingException ex) {
      fail();
    }

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    // verify po validation
    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(any(), any());

    // verify DcFin call
    verify(witronDCFinService, times(1))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
  }

  @Test
  public void completeInstruction_WitronHandler_rotate_date_flag() throws ReceivingException {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(true).when(configUtils).isShowRotateDateOnPrintLabelEnabled(any(Integer.class));
    openInstruction.setMove(new LinkedTreeMap<>());
    completedInstruction.setMove(new LinkedTreeMap<>());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              gdcInstructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals(response.getInstruction().getActivityName(), "DA");
      assertNotNull(response.getPrintJob().get("printRequests"));
    } catch (ReceivingException ex) {
      fail();
    }

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  @SneakyThrows
  @Test
  public void completeInstruction_WitronHandler() throws ReceivingException {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    openInstruction.setMove(new LinkedTreeMap<>());
    completedInstruction.setMove(new LinkedTreeMap<>());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);

    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              gdcInstructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals(response.getInstruction().getActivityName(), "DA");
      assertNotNull(response.getPrintJob().get("printRequests"));
    } catch (ReceivingException ex) {
      fail();
    }

    verify(containerService, times(0))
        .postFinalizePoOsdrToGdm(any(HttpHeaders.class), anyLong(), anyString(), any());
    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  @Test
  public void completeInstruction_InstructionAlreadyCompleted() throws ReceivingException {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(completedInstruction));
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);

    try {
      gdcInstructionService.completeInstruction(
          Long.valueOf("1"), completeInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException ex) {
      assertEquals(ex.getHttpStatus(), INTERNAL_SERVER_ERROR);
      assertEquals(ex.getErrorResponse().getErrorHeader(), ERROR_HEADER_PALLET_COMPLETED);
      assertEquals(
          ex.getErrorResponse().getErrorCode(),
          COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED);
      assertEquals(
          ex.getErrorResponse().getErrorMessage(),
          String.format(
              COMPLETE_INSTRUCTION_ALREADY_COMPLETE, completedInstruction.getCompleteUserId()));
    }
  }

  @Test
  public void completeInstruction_InstructionAlreadyCancelled() throws ReceivingException {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    completedInstruction.setReceivedQuantity(0);
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(completedInstruction));
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    try {
      gdcInstructionService.completeInstruction(
          Long.valueOf("1"), completeInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException ex) {
      assertEquals(ex.getHttpStatus(), Objects.requireNonNull(INTERNAL_SERVER_ERROR));
      assertEquals(ex.getErrorResponse().getErrorHeader(), ERROR_HEADER_PALLET_CANCELLED);
      assertEquals(
          ex.getErrorResponse().getErrorCode(),
          COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED);
      assertEquals(
          String.format(
              ReceivingException.COMPLETE_INSTRUCTION_PALLET_CANCELLED,
              completedInstruction.getCompleteUserId()),
          ex.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void completeInstruction_BadRequest() throws ReceivingException {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    openInstruction.setContainer(null);
    completedInstruction.setContainer(null);
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);

    try {
      gdcInstructionService.completeInstruction(
          Long.valueOf("1"), completeInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException ex) {
      assertEquals(ex.getMessage(), COMPLETE_INSTRUCTION_ERROR_MSG);
      assertEquals(ex.getHttpStatus(), BAD_REQUEST);
      assertEquals(ex.getErrorResponse().getErrorCode(), COMPLETE_INSTRUCTION_ERROR_CODE);
    }
  }

  @SneakyThrows
  @Test
  public void completeInstruction_RotateDateExist_PoConfirmationEnabled() {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    String printRequestStringHavingRotateDate =
        "[{ttlInHours=1, labelIdentifier=a328990000000000000106509, data=[{value=TR ED 3PC FRY/GRL RD, key=DESC1}, {value=09/16/2020, key=rotateDate}";
    openInstruction.setMove(new LinkedTreeMap<>());
    completedInstruction.setMove(new LinkedTreeMap<>());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);

    ContainerItem containerItem = WitronContainer.getContainerItem(1L);
    final String rotateDateInDb = "09/16/20";
    containerItem.setRotateDate(new SimpleDateFormat("MM/dd/yy").parse(rotateDateInDb));
    containerRotateDate.setContainerItems(Arrays.asList(containerItem));

    when(containerService.containerComplete(anyString(), anyString()))
        .thenReturn(containerRotateDate);
    doNothing().when(containerService).publishContainer(any(), anyMap());

    when(containerService.getContainerIncludingChild(any())).thenReturn(containerRotateDate);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(containerRotateDate, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(false);
    when(tenantSpecificConfigReader.getDCTimeZone(any())).thenReturn("US/Central");

    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              gdcInstructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      final String printRequestStringHavingRotateDateWithDDMMYYY_format_Actual =
          response.getPrintJob().get("printRequests").toString();
      assertNotNull(printRequestStringHavingRotateDateWithDDMMYYY_format_Actual);
      assertTrue(
          printRequestStringHavingRotateDateWithDDMMYYY_format_Actual.contains(
              printRequestStringHavingRotateDate));
    } catch (ReceivingException ex) {
      fail();
    }
  }

  @SneakyThrows
  @Test
  public void completeInstruction_Success_ReceiveAsCorrection() throws ReceivingException {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    // override flag
    openInstruction.setIsReceiveCorrection(true);
    FinalizePORequestBody mockFinalizePORequestBody = new FinalizePORequestBody();

    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());

    container.setContainerItems(Arrays.asList(WitronContainer.getContainerItem(1L)));
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(PUBLISH_CONTAINER)).thenReturn(true);
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", PUBLISH_CONTAINER);
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(tenantSpecificConfigReader.getDCTimeZone(any())).thenReturn("US/Central");
    doReturn("https://inventory-server.dev.prod.us.walmart.net")
        .when(appConfig)
        .getInventoryBaseUrl();
    doNothing().when(asyncPersister).persistAsyncHttp(any(), any(), any(), any(), any());
    doReturn(Boolean.TRUE).when(appConfig).getIsReceiptPostingEnaledForDCFin();
    doNothing()
        .when(receiptPublisher)
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    doReturn(mockFinalizePORequestBody)
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class));
    doReturn(false).when(configUtils).getConfiguredFeatureFlag("32612", INV_V2_ENABLED, false);

    // execute
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcInstructionService.completeInstruction(
                Long.valueOf("1"), completeInstructionRequest, httpHeaders);

    assertNotNull(response.getInstruction().getCompleteUserId());
    assertNotNull(response.getInstruction().getCompleteTs());
    assertNotNull(response.getPrintJob().get("printRequests"));

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    // verify no PO validation
    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(any(), any());

    // verify mandatory inventory headers to process and avoid infinity loop (inv to rcv)
    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

    verify(asyncPersister, times(1))
        .persistAsyncHttp(any(), urlCaptor.capture(), any(), headerCaptor.capture(), any());
    final String capturedUrlValue = urlCaptor.getValue();
    assertEquals(
        capturedUrlValue,
        "https://inventory-server.dev.prod.us.walmart.net/inventory/inventories/receipt?flow=rcvCorrection");
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get(IDEM_POTENCY_KEY).size(), 1);
    assertEquals(headers.get(REQUEST_ORIGINATOR).size(), 1);
    final String sourceAppName = headers.get(REQUEST_ORIGINATOR).get(0);
    assertTrue(APP_NAME_VALUE.equals(sourceAppName) || RECEIVING.equals(sourceAppName));

    verify(containerService, times(1))
        .postFinalizePoOsdrToGdm(any(HttpHeaders.class), anyLong(), anyString(), any());

    // make sure NO DcFin is called from RCV
    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
  }

  @SneakyThrows
  @Test
  public void completeInstruction_Success_ReceiveAsCorrection_Inventory2Url()
      throws ReceivingException {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    // override flag
    openInstruction.setIsReceiveCorrection(true);
    FinalizePORequestBody mockFinalizePORequestBody = new FinalizePORequestBody();

    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());

    container.setContainerItems(Arrays.asList(WitronContainer.getContainerItem(1L)));
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(PUBLISH_CONTAINER)).thenReturn(true);
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", PUBLISH_CONTAINER);
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(tenantSpecificConfigReader.getDCTimeZone(any())).thenReturn("US/Central");
    doReturn("https://gls-atlas-inventory-core-gdc-qa.walmart.com")
        .when(appConfig)
        .getInventoryCoreBaseUrl();
    doNothing().when(asyncPersister).persistAsyncHttp(any(), any(), any(), any(), any());
    doReturn(Boolean.TRUE).when(appConfig).getIsReceiptPostingEnaledForDCFin();
    doNothing()
        .when(receiptPublisher)
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    doReturn(mockFinalizePORequestBody)
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class));
    doReturn(true).when(configUtils).getConfiguredFeatureFlag("32612", INV_V2_ENABLED, false);

    // execute
    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcInstructionService.completeInstruction(
                Long.valueOf("1"), completeInstructionRequest, httpHeaders);

    assertNotNull(response.getInstruction().getCompleteUserId());
    assertNotNull(response.getInstruction().getCompleteTs());
    assertNotNull(response.getPrintJob().get("printRequests"));

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    // verify no PO validation
    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(any(), any());

    // verify mandatory inventory headers to process and avoid infinity loop (inv to rcv)
    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

    verify(asyncPersister, times(1))
        .persistAsyncHttp(any(), urlCaptor.capture(), any(), headerCaptor.capture(), any());
    final String capturedUrlValue = urlCaptor.getValue();
    assertEquals(
        capturedUrlValue,
        "https://gls-atlas-inventory-core-gdc-qa.walmart.com/inventory/inventories/receipt?flow=rcvCorrection");
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get(IDEM_POTENCY_KEY).size(), 1);
    assertEquals(headers.get(REQUEST_ORIGINATOR).size(), 1);
    final String sourceAppName = headers.get(REQUEST_ORIGINATOR).get(0);
    assertTrue(APP_NAME_VALUE.equals(sourceAppName) || RECEIVING.equals(sourceAppName));

    verify(containerService, times(1))
        .postFinalizePoOsdrToGdm(any(HttpHeaders.class), anyLong(), anyString(), any());

    // make sure NO DcFin is called from RCV
    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
  }

  @Test
  public void completeInstruction_Success_ReceiveAsCorrection_UI_missing_originator()
      throws ReceivingException {
    doReturn(deliveryCacheValue)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
    // override flag
    openInstruction.setIsReceiveCorrection(true);
    HttpHeaders httpHeaders_ui_missing_originator = GdcHttpHeaders.getHeaders();
    httpHeaders_ui_missing_originator.put(REQUEST_ORIGINATOR, null);

    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());

    container.setContainerItems(Arrays.asList(WitronContainer.getContainerItem(1L)));
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility(anyString()))
        .thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(witronDCFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(tenantSpecificConfigReader.getDCTimeZone(any())).thenReturn("US/Central");
    doReturn("https://inventory-server.dev.prod.us.walmart.net")
        .when(appConfig)
        .getInventoryBaseUrl();
    doNothing().when(asyncPersister).persistAsyncHttp(any(), any(), any(), any(), any());
    doReturn(Boolean.TRUE).when(appConfig).getIsReceiptPostingEnaledForDCFin();
    doNothing()
        .when(receiptPublisher)
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());

    InstructionResponseImplNew response =
        (InstructionResponseImplNew)
            gdcInstructionService.completeInstruction(
                Long.valueOf("1"), completeInstructionRequest, httpHeaders_ui_missing_originator);

    assertNotNull(response.getInstruction().getCompleteUserId());
    assertNotNull(response.getInstruction().getCompleteTs());
    assertNotNull(response.getPrintJob().get("printRequests"));

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    // verify no PO validation
    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(any(), any());

    // verify mandatory inventory headers to process and avoid infinity loop (inv to rcv)
    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    doNothing().when(asyncPersister).persistAsyncHttp(any(), any(), any(), any(), any());
    verify(asyncPersister, times(1))
        .persistAsyncHttp(any(), any(), any(), headerCaptor.capture(), any());
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get(IDEM_POTENCY_KEY).size(), 1);
    assertEquals(headers.get(REQUEST_ORIGINATOR).size(), 1);
    final String sourceAppName = headers.get(REQUEST_ORIGINATOR).get(0);
    assertTrue(APP_NAME_VALUE.equals(sourceAppName) || RECEIVING.equals(sourceAppName));

    // make sure NO DcFin is called from RCV
    verify(witronDCFinService, times(0))
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
  }

  @Test
  public void completeInstruction_DeliveryCacheValueNotSet() throws ReceivingException {
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    doReturn(null)
        .when(deliveryCacheServiceInMemoryImpl)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

    try {
      gdcInstructionService.completeInstruction(
          Long.valueOf("1"), completeInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException ex) {
      assertEquals(ex.getMessage(), ReceivingException.GDM_SERVICE_DOWN);
      assertEquals(ex.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(
          ex.getErrorResponse().getErrorCode(),
          ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE);
    }
  }
}
