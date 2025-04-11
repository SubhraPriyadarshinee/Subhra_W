package com.walmart.move.nim.receiving.core.service;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
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
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.mock.data.MockReceipt;
import com.walmart.move.nim.receiving.core.mock.data.WitronContainer;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplOld;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.DockTagExceptionContainerHandler;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.ExceptionContainerHandlerFactory;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Optional;
import lombok.SneakyThrows;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class CompleteInstructionServiceTest extends ReceivingTestBase {
  @InjectMocks private InstructionService instructionService;
  @InjectMocks private LabelServiceImpl labelServiceImpl;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private PrintJobService printJobService;
  @Mock private ContainerService containerService;
  @Mock private DCFinService dcFinService;
  @Mock private AppConfig appConfig;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private MovePublisher movePublisher;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private GdcPutawayPublisher gdcPutawayPublisher;
  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;
  @Mock private ExceptionContainerHandlerFactory exceptionContainerHandlerFactory;
  @Mock private DockTagExceptionContainerHandler dockTagExceptionContainerHandler;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private JMSInstructionPublisher jmsInstructionPublisher;
  @InjectMocks private ResourceBundleMessageSource resourceBundleMessageSource;
  @InjectMocks private ContainerPersisterService containerPersisterService;
  @Mock private ContainerRepository containerRepository;
  @Mock private ContainerItemRepository containerItemRepository;
  private final Gson gson = new Gson();
  private Instruction openInstruction;
  private Instruction completedInstruction;
  private final PrintJob printJob = MockInstruction.getPrintJob();
  private final Container container = MockInstruction.getContainer();
  private final Container containerRotateDate = MockInstruction.getContainer();

  private final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private CompleteInstructionRequest completeInstructionRequest;
  private final JsonObject defaultFeatureFlagsByFacility = new JsonObject();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");

    completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setPrinterId(101);

    ReflectionTestUtils.setField(
        instructionService, "purchaseReferenceValidator", purchaseReferenceValidator);

    ReflectionTestUtils.setField(
        instructionService, "instructionHelperService", instructionHelperService);

    ReflectionTestUtils.setField(
        instructionService, "resourceBundleMessageSource", resourceBundleMessageSource);

    ReflectionTestUtils.setField(
        labelServiceImpl, "resourceBundleMessageSource", resourceBundleMessageSource);
  }

  @BeforeMethod
  public void setUpTestDataBeforeEachTest() {
    openInstruction = MockInstruction.getOpenInstruction();
    completedInstruction = MockInstruction.getCompleteInstruction();

    doAnswer(
            new Answer<DCFinService>() {
              public DCFinService answer(InvocationOnMock invocation) {
                return dcFinService;
              }
            })
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(jmsInstructionPublisher);
    when(exceptionContainerHandlerFactory.exceptionContainerHandler(
            eq(ContainerException.DOCK_TAG)))
        .thenReturn(dockTagExceptionContainerHandler);
    ReflectionTestUtils.setField(instructionService, "labelServiceImpl", labelServiceImpl);
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(jmsPublisher);
    reset(movePublisher);
    reset(instructionRepository);
    reset(gdcPutawayPublisher);
    reset(containerService);
  }

  @BeforeClass
  public void setReflectionTestUtil() {
    ReflectionTestUtils.setField(instructionService, "gson", gson);
    ReflectionTestUtils.setField(
        instructionService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(
        instructionService, "containerPersisterService", containerPersisterService);
    ReflectionTestUtils.setField(
        containerPersisterService, "containerRepository", containerRepository);
    ReflectionTestUtils.setField(
        containerPersisterService, "containerItemRepository", containerItemRepository);
  }

  @Test
  public void completeInstruction_wiht_rotate_date_label_change() throws ReceivingException {
    doReturn(true).when(configUtils).isShowRotateDateOnPrintLabelEnabled(any(Integer.class));
    String updatedPrintRequest =
        "[{\"ttlInHours\":1,\"labelIdentifier\":\"a328990000000000000106509\",\"data\":[{\"key\":\"DESC1\",\"value\":\"TR ED 3PC FRY/GRL RD\"},{\"key\":\"QTY\",\"value\":\"2\"},{\"key\":\"ROTATEDATE\",\"value\":\"-\"}],\"formatName\":\"pallet_lpn_format\"}]";
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
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
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals("DA", response.getInstruction().getActivityName());
      assertEquals(gson.toJson(response.getPrintJob().get("printRequests")), updatedPrintRequest);
    } catch (ReceivingException ex) {
      fail();
    }

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
    verify(configUtils, times(1)).isShowRotateDateOnPrintLabelEnabled(any(Integer.class));
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test
  public void completeInstruction_Success() throws ReceivingException {
    String updatedPrintRequest =
        "[{\"ttlInHours\":1,\"labelIdentifier\":\"a328990000000000000106509\",\"data\":[{\"key\":\"DESC1\",\"value\":\"TR ED 3PC FRY/GRL RD\"},{\"key\":\"QTY\",\"value\":\"2\"}],\"formatName\":\"pallet_lpn_format\"}]";
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
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
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals("DA", response.getInstruction().getActivityName());
      assertEquals(gson.toJson(response.getPrintJob().get("printRequests")), updatedPrintRequest);
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
  public void completeInstructionOldPrint_Success_rotate_date_label_flag()
      throws ReceivingException {
    doReturn(true).when(configUtils).isShowRotateDateOnPrintLabelEnabled(any(Integer.class));
    String updatedPrintRequest = // With no rotatedate
        "{ttlInHours=1, labelData=[{value=100001, key=ITEM}, {value=2, key=QTY}, {value=-, key=ROTATEDATE}], labelIdentifier=a328990000000000000106509, formatID=pallet_lpn_format, clientId=OF, data=[{value=100001, key=ITEM}, {value=2, key=QTY}, {value=-, key=ROTATEDATE}]}";

    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(MockInstruction.getOldPrintOpenInstruction()));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(false);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplOld response =
          (InstructionResponseImplOld)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals("DA", response.getInstruction().getActivityName());
      assertEquals(response.getPrintJob().get(0).toString(), updatedPrintRequest);
    } catch (ReceivingException ex) {
      fail();
    }

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());

    verify(configUtils, times(1)).isShowRotateDateOnPrintLabelEnabled(any(Integer.class));
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test
  public void completeInstructionOldPrint_Success() throws ReceivingException {
    String updatedPrintRequest =
        "{ttlInHours=1, labelData=[{value=100001, key=ITEM}, {value=2, key=QTY}], labelIdentifier=a328990000000000000106509, formatID=pallet_lpn_format, clientId=OF, data=[{value=100001, key=ITEM}, {value=2, key=QTY}]}";
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(MockInstruction.getOldPrintOpenInstruction()));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(false);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplOld response =
          (InstructionResponseImplOld)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals("DA", response.getInstruction().getActivityName());
      assertEquals(updatedPrintRequest, response.getPrintJob().get(0).toString());
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
  public void completeInstruction_WitronHandler_rotate_date_flag() throws ReceivingException {
    doReturn(true).when(configUtils).isShowRotateDateOnPrintLabelEnabled(any(Integer.class));
    String updatedPrintRequest =
        "[{\"ttlInHours\":1,\"labelIdentifier\":\"a328990000000000000106509\",\"data\":[{\"key\":\"DESC1\",\"value\":\"TR ED 3PC FRY/GRL RD\"},{\"key\":\"QTY\",\"value\":\"2\"},{\"key\":\"ROTATEDATE\",\"value\":\"-\"}],\"formatName\":\"pallet_lpn_format\"}]";
    openInstruction.setMove(new LinkedTreeMap<>());
    completedInstruction.setMove(new LinkedTreeMap<>());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);
    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals("DA", response.getInstruction().getActivityName());
      assertEquals(updatedPrintRequest, gson.toJson(response.getPrintJob().get("printRequests")));
    } catch (ReceivingException ex) {
      fail();
    }

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
    verify(configUtils, times(1)).isShowRotateDateOnPrintLabelEnabled(any(Integer.class));
  }

  @Test
  public void completeInstruction_WitronHandler() throws ReceivingException {
    String updatedPrintRequest =
        "[{\"ttlInHours\":1,\"labelIdentifier\":\"a328990000000000000106509\",\"data\":[{\"key\":\"DESC1\",\"value\":\"TR ED 3PC FRY/GRL RD\"},{\"key\":\"QTY\",\"value\":\"2\"}],\"formatName\":\"pallet_lpn_format\"}]";
    openInstruction.setMove(new LinkedTreeMap<>());
    completedInstruction.setMove(new LinkedTreeMap<>());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);
    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);

    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals("DA", response.getInstruction().getActivityName());
      assertEquals(updatedPrintRequest, gson.toJson(response.getPrintJob().get("printRequests")));
    } catch (ReceivingException ex) {
      fail();
    }

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  @Test
  public void completeInstruction_InstructionAlreadyCompleted() throws ReceivingException {
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(completedInstruction));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);

    try {
      instructionService.completeInstruction(
          Long.valueOf("1"), completeInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException ex) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatus());
      assertEquals(
          ReceivingException.ERROR_HEADER_PALLET_COMPLETED, ex.getErrorResponse().getErrorHeader());
      assertEquals(
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED,
          ex.getErrorResponse().getErrorCode());
      assertEquals(
          String.format(
              ReceivingException.COMPLETE_INSTRUCTION_ALREADY_COMPLETE,
              completedInstruction.getCompleteUserId()),
          ex.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void completeInstruction_InstructionAlreadyCancelled() throws ReceivingException {
    completedInstruction.setReceivedQuantity(0);
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(completedInstruction));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    try {
      instructionService.completeInstruction(
          Long.valueOf("1"), completeInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException ex) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatus());
      assertEquals(
          ReceivingException.ERROR_HEADER_PALLET_CANCELLED, ex.getErrorResponse().getErrorHeader());
      assertEquals(
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED,
          ex.getErrorResponse().getErrorCode());
      assertEquals(
          String.format(
              ReceivingException.COMPLETE_INSTRUCTION_PALLET_CANCELLED,
              completedInstruction.getCompleteUserId()),
          ex.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void completeInstruction_BadRequest() throws ReceivingException {
    openInstruction.setContainer(null);
    completedInstruction.setContainer(null);
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);

    try {
      instructionService.completeInstruction(
          Long.valueOf("1"), completeInstructionRequest, httpHeaders);
      fail();
    } catch (ReceivingException ex) {
      assertEquals(ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG, ex.getMessage());
      assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
      assertEquals(
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE, ex.getErrorResponse().getErrorCode());
    }
  }

  @Test
  public void testGetConsolidatedContainerAndPublishContainer() throws ReceivingException {
    when(containerService.getContainerIncludingChild(any(Container.class)))
        .thenReturn(MockContainer.getSSTKContainer());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing().when(containerService).publishContainer(any(), any(), any());

    instructionService.getConsolidatedContainerAndPublishContainer(
        MockContainer.getSSTKContainer(), MockHttpHeaders.getHeaders(), false);
    verify(containerService, times(1)).getContainerIncludingChild(any(Container.class));
  }

  @Test
  public void testNewlabelInformationforPoCon() throws ReceivingException {
    openInstruction.setActivityName("POCON");
    openInstruction.setOriginalChannel("SSTK");
    openInstruction.setContainer(MockInstruction.getContainerDetailsforPocon());
    completedInstruction.setActivityName("POCON");
    completedInstruction.setOriginalChannel("SSTK");
    completedInstruction.setContainer(MockInstruction.getContainerDetailsforPocon());
    String updatedPrintRequest =
        "[{\"ttlInHours\":1,\"labelIdentifier\":\"a328990000000000000106509\",\"data\":[{\"key\":\"TYPE\",\"value\":\"POCON\"},{\"key\":\"QTY\",\"value\":\"2\"},{\"key\":\"CHANNELMETHOD\",\"value\":\"SSTK\"}],\"formatName\":\"pallet_lpn_format\"}]";
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);

    when(receiptService.findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPalletQtyIsNull(
            any(), any()))
        .thenReturn(MockReceipt.getReceipt());

    when(receiptService.saveReceipt(any())).thenReturn(MockReceipt.getReceipt());

    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals("POCON", response.getInstruction().getActivityName());
      assertEquals(updatedPrintRequest, gson.toJson(response.getPrintJob().get("printRequests")));
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
  public void testOldlabelInformationforPoCon() throws ReceivingException {
    openInstruction = MockInstruction.getOldPrintOpenInstruction();
    openInstruction.setActivityName("POCON");
    openInstruction.setOriginalChannel("SSTK");
    openInstruction.setContainer(MockInstruction.getContainerDetailsOldPrintForPoCon());
    completedInstruction.setActivityName("POCON");
    completedInstruction.setOriginalChannel("SSTK");
    completedInstruction.setContainer(MockInstruction.getContainerDetailsOldPrintForPoCon());
    String updatedPrintRequest =
        "{ttlInHours=1, labelData=[{value=POCON, key=TYPE}, {value=2, key=QTY}, {value=SSTK, key=CHANNELMETHOD}], labelIdentifier=a328990000000000000106509, formatID=pallet_lpn_format, clientId=OF, data=[{value=POCON, key=TYPE}, {value=2, key=QTY}, {value=SSTK, key=CHANNELMETHOD}]}";
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(false);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);

    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplOld response =
          (InstructionResponseImplOld)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals("POCON", response.getInstruction().getActivityName());
      assertEquals(updatedPrintRequest, response.getPrintJob().get(0).toString());
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
  public void testOldlabelInformationforDSDC() throws ReceivingException {
    Instruction oldLabelInstruction = MockInstruction.getOldPrintOpenInstruction();
    oldLabelInstruction.setActivityName("DSDC");
    oldLabelInstruction.setOriginalChannel("DA");
    oldLabelInstruction.setContainer(MockInstruction.getContainerDetailsOldPrintForDSDC());
    completedInstruction.setActivityName("DSDC");
    completedInstruction.setOriginalChannel("DA");
    completedInstruction.setContainer(MockInstruction.getContainerDetailsOldPrintForDSDC());
    String updatedPrintRequest =
        "{ttlInHours=1, labelData=[{value=DSDC, key=TYPE}, {value=2, key=QTY}, {value=DA, key=CHANNELMETHOD}], labelIdentifier=a328990000000000000106509, formatID=pallet_lpn_format, clientId=OF, data=[{value=DSDC, key=TYPE}, {value=2, key=QTY}, {value=DA, key=CHANNELMETHOD}]}";
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(oldLabelInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(false);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);

    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplOld response =
          (InstructionResponseImplOld)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals(response.getInstruction().getActivityName(), "DSDC");
      assertEquals(response.getPrintJob().get(0).toString(), updatedPrintRequest);
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
  public void testNewlabelInformationforDSDC() throws ReceivingException {
    openInstruction.setActivityName("DSDC");
    openInstruction.setOriginalChannel("DA");
    openInstruction.setContainer(MockInstruction.getContainerDetailsforDSDC());
    completedInstruction.setActivityName("DSDC");
    completedInstruction.setOriginalChannel("DA");
    completedInstruction.setContainer(MockInstruction.getContainerDetailsforDSDC());
    String updatedPrintRequest =
        "[{\"ttlInHours\":1,\"labelIdentifier\":\"a328990000000000000106509\",\"data\":[{\"key\":\"TYPE\",\"value\":\"DSDC\"},{\"key\":\"QTY\",\"value\":\"2\"},{\"key\":\"CHANNELMETHOD\",\"value\":\"DA\"}],\"formatName\":\"pallet_lpn_format\"}]";
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);

    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);

    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals("DSDC", response.getInstruction().getActivityName());
      assertEquals(updatedPrintRequest, gson.toJson(response.getPrintJob().get("printRequests")));
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

  @SneakyThrows
  @Test
  public void completeInstruction_RotateDateExist_PoConfirmationEnabled()
      throws ReceivingException {
    String printRequestStringHavingRotateDate =
        "[{ttlInHours=1, labelIdentifier=a328990000000000000106509, data=[{value=TR ED 3PC FRY/GRL RD, key=DESC1}, {value=09/16/20, key=rotateDate}";
    openInstruction.setMove(new LinkedTreeMap<>());
    completedInstruction.setMove(new LinkedTreeMap<>());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);

    setRotateDate();

    when(containerService.containerComplete(any(), anyString())).thenReturn(containerRotateDate);
    doNothing().when(containerService).publishContainer(any(), anyMap());

    when(containerService.getContainerIncludingChild(any())).thenReturn(containerRotateDate);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(containerRotateDate, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);
    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    when(configUtils.isPoConfirmationFlagEnabled(32987)).thenReturn(true);
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(false);
    when(tenantSpecificConfigReader.getDCTimeZone(32987)).thenReturn("US/Central");

    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      final String printRequests = response.getPrintJob().get("printRequests").toString();
      assertNotNull(printRequests);
      assertTrue(printRequests.contains(printRequestStringHavingRotateDate));
    } catch (ReceivingException ex) {
      fail();
    }
  }

  @SneakyThrows
  @Test
  public void completeInstruction_RotateDateExist_PoConfirmationDisabled() {
    String updatedPrintRequest =
        "[{\"ttlInHours\":1,\"labelIdentifier\":\"a328990000000000000106509\",\"data\":[{\"key\":\"DESC1\",\"value\":\"TR ED 3PC FRY/GRL RD\"},{\"key\":\"QTY\",\"value\":\"2\"}],\"formatName\":\"pallet_lpn_format\"}]";

    openInstruction.setMove(new LinkedTreeMap<>());
    completedInstruction.setMove(new LinkedTreeMap<>());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    setRotateDate();
    when(containerService.containerComplete(any(), anyString())).thenReturn(containerRotateDate);
    doNothing().when(containerService).publishContainer(any(), anyMap());

    when(containerService.getContainerIncludingChild(any())).thenReturn(containerRotateDate);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(containerRotateDate, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);
    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);

    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertEquals(gson.toJson(response.getPrintJob().get("printRequests")), updatedPrintRequest);
    } catch (ReceivingException ex) {
      fail();
    }
  }

  @SneakyThrows
  @Test
  public void completeInstruction_RotateDateExist_Witron_OldPrint() throws ReceivingException {
    String updatedPrintRequest =
        "{ttlInHours=1, labelData=[{value=100001, key=ITEM}, {value=2, key=QTY}], labelIdentifier=a328990000000000000106509, formatID=pallet_lpn_format, clientId=OF, data=[{value=100001, key=ITEM}, {value=2, key=QTY}]}";
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(MockInstruction.getOldPrintOpenInstruction()));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);

    setRotateDate();

    when(containerService.containerComplete(any(), anyString())).thenReturn(containerRotateDate);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(any())).thenReturn(containerRotateDate);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(false);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplOld response =
          (InstructionResponseImplOld)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);

      assertEquals(updatedPrintRequest, response.getPrintJob().get(0).toString());
    } catch (ReceivingException ex) {
      fail();
    }
  }

  public void setRotateDate() throws ParseException {
    ContainerItem containerItem = WitronContainer.getContainerItem(1L);
    final String rotateDateInDb = "09/16/2020";
    containerItem.setRotateDate(new SimpleDateFormat("MM/dd/yy").parse(rotateDateInDb));
    containerRotateDate.setContainerItems(Arrays.asList(containerItem));
  }

  @Test
  public void testCompleteDockTagInstruction_HappyFlow() throws ReceivingException {
    String dockTagId = "c32987000000000000000001";

    String updatedPrintRequest =
        "[{\"ttlInHours\":72,\"labelIdentifier\":\""
            + dockTagId
            + "\",\"data\":[{\"key\":\"DELIVERYNBR\",\"value\":\"261220189\"},{\"key\":\"QTY\",\"value\":\"0\"},{\"key\":\"ROTATEDATE\",\"value\":\"-\"}],\"formatName\":\"dock_tag_atlas\"}]";

    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(MockInstruction.getDockTagInstruction()));
    when(instructionRepository.save(any(Instruction.class)))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(MockInstruction.getPrintJob());
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);

    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isShowRotateDateOnPrintLabelEnabled(any(Integer.class))).thenReturn(true);
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstruction(1L, completeInstructionRequest, httpHeaders);

      Assert.assertNotNull(response.getInstruction().getCompleteUserId());
      Assert.assertNotNull(response.getInstruction().getCompleteTs());
      Assert.assertEquals("Dock Tag", response.getInstruction().getActivityName());
      Assert.assertEquals(
          updatedPrintRequest, gson.toJson(response.getPrintJob().get("printRequests")));
    } catch (ReceivingException ex) {
      fail();
    }

    verify(jmsPublisher, times(1))
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(movePublisher, times(1))
        .publishMove(
            eq(1), eq("13"), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test
  public void disable_lable_priting_For_Configured_Format() throws ReceivingException {
    String updatedPrintRequest =
        "[{\"ttlInHours\":1,\"labelIdentifier\":\"a328990000000000000106509\",\"data\":[{\"key\":\"DESC1\",\"value\":\"TR ED 3PC FRY/GRL RD\"},{\"key\":\"QTY\",\"value\":\"2\"}],\"formatName\":\"pallet_lpn_format\"}]";
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(openInstruction));
    when(instructionRepository.save(any(Instruction.class))).thenReturn(completedInstruction);
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(printJob);
    when(containerService.containerComplete(any(), anyString())).thenReturn(container);
    doNothing().when(containerService).publishContainer(any(), anyMap());
    when(containerService.getContainerIncludingChild(container)).thenReturn(container);
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMoveEvent()).thenReturn("onInitiate");
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.DISABLE_PRINTING_MASTER_PALLET_LPN))
        .thenReturn(true);
    when(configUtils.getCcmValue(
            anyInt(), eq(ReceivingConstants.PRINT_DISABLED_LABEL_FORMAT), anyString()))
        .thenReturn("case_lpn_format");
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.PUBLISH_CONTAINER)).thenReturn(true);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    doNothing()
        .when(dcFinService)
        .postReceiptsToDCFin(any(Container.class), any(HttpHeaders.class), eq(true));
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstruction(
                  Long.valueOf("1"), completeInstructionRequest, httpHeaders);
      assertNotNull(response.getInstruction().getCompleteUserId());
      assertNotNull(response.getInstruction().getCompleteTs());
      assertEquals(gson.toJson(response.getPrintJob().get("printRequests")), updatedPrintRequest);
    } catch (ReceivingException ex) {
      fail();
    }
  }
}
