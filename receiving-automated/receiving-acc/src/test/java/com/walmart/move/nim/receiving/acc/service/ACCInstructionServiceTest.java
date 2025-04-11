package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.JMSInstructionPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.DockTagExceptionContainerHandler;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.ExceptionContainerHandlerFactory;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ACCInstructionServiceTest extends ReceivingTestBase {
  @InjectMocks private ACCInstructionService instructionService;
  @InjectMocks private InstructionService instructionServiceBaseClass;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private JMSInstructionPublisher jmsInstructionPublisher;
  @InjectMocks private ACCDockTagService accDockTagService;
  @InjectMocks private LabelServiceImpl labelServiceImpl;
  @Mock private DockTagPersisterService dockTagPersisterService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private DockTagService dockTagService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private MovePublisher movePublisher;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private AppConfig appConfig;
  @Mock private DefaultPutawayHandler defaultPutawayHandler;
  @Mock private PrintJobService printJobService;
  @Mock private ContainerService containerService;
  @Mock private LPNCacheService lpnCacheService;
  @Mock private ReceiptService receiptService;
  @Mock private FdeService fdeService;
  @Mock private RegulatedItemService regulatedItemService;
  @Mock private ExceptionContainerHandlerFactory exceptionContainerHandlerFactory;
  @Mock private ContainerRepository containerRepository;
  @Mock private ContainerItemRepository containerItemRepository;
  @InjectMocks private DockTagExceptionContainerHandler dockTagExceptionContainerHandler;
  @InjectMocks private ResourceBundleMessageSource resourceBundleMessageSource;
  @InjectMocks private ContainerPersisterService containerPersisterService;
  @InjectMocks @Spy private DefaultOpenQtyCalculator defaultOpenQtyCalculator;
  private InstructionRequest instructionRequest;
  private List<DeliveryDocument> deliveryDocuments;
  private Instruction pendingInstruction;
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private Gson gson = new Gson();
  private String dockTagId;
  private JsonObject defaultFeatureFlagsByFacility = new JsonObject();
  private Container container;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        instructionPersisterService, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(
        instructionService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(
        instructionService, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(instructionService, "regulatedItemService", regulatedItemService);
    ReflectionTestUtils.setField(instructionService, "tenantSpecificConfigReader", configUtils);
    ReflectionTestUtils.setField(instructionService, "gson", gson);
    ReflectionTestUtils.setField(
        instructionService, "exceptionContainerHandlerFactory", exceptionContainerHandlerFactory);
    ReflectionTestUtils.setField(
        dockTagExceptionContainerHandler, "containerService", containerService);
    ReflectionTestUtils.setField(
        instructionService, "resourceBundleMessageSource", resourceBundleMessageSource);
    ReflectionTestUtils.setField(
        labelServiceImpl, "resourceBundleMessageSource", resourceBundleMessageSource);
    ReflectionTestUtils.setField(instructionService, "labelServiceImpl", labelServiceImpl);
    ReflectionTestUtils.setField(
        instructionService, "containerPersisterService", containerPersisterService);
    ReflectionTestUtils.setField(
        containerPersisterService, "containerRepository", containerRepository);
    ReflectionTestUtils.setField(
        containerPersisterService, "containerItemRepository", containerItemRepository);

    deliveryDocuments =
        gson.fromJson(
            MockACLMessageData.getDeliveryDocuments(),
            new TypeToken<List<DeliveryDocument>>() {}.getType());
    instructionRequest =
        gson.fromJson(MockACLMessageData.getMockInstructionRequest(), InstructionRequest.class);
    pendingInstruction = gson.fromJson(MockACLMessageData.getMockInstruction(), Instruction.class);
    dockTagId = "c32987000000000000000001";

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    TenantContext.setCorrelationId("1a2bc3d4");

    container = MockInstruction.getDockTagContainer();
  }

  @AfterMethod
  public void teardown() {
    reset(dockTagService);
    reset(jmsPublisher);
    reset(configUtils);
    reset(instructionRepository);
    reset(lpnCacheService);
    reset(dockTagPersisterService);
    reset(fdeService);
    reset(containerService);
    reset(printJobService);
    reset(dockTagPersisterService);
    reset(regulatedItemService);
  }

  @BeforeMethod()
  public void before() {
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(jmsInstructionPublisher);
    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(dockTagExceptionContainerHandler);
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO);
  }

  @Test
  public void testCreateInstructionReturnsBuildPalletInstruction() {
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine(null);
    when(instructionRepository.findByMessageId(anyString())).thenReturn(pendingInstruction);

    try {
      Instruction instructionResponse =
          instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assertNotNull(instructionResponse);
      assert (true);
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test
  public void testCreateInstructionReturnsBuildPalletInstruction_kotlinOverage()
      throws ReceivingException {
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine(null);
    when(instructionRepository.findByMessageId(anyString())).thenReturn(null);
    when(receiptService.getReceivedQtyByPoAndPoLine(any(), any())).thenReturn(50L);
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    try {
      httpHeaders.add("isKotlin", "true");
      Instruction instructionResponse =
          instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assertNotNull(instructionResponse);
      assertEquals("CCOveragePallet", instructionResponse.getInstructionMsg());
      assertEquals("CCOveragePallet", instructionResponse.getInstructionCode());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCreateInstructionReturnsException_kotlinOverage() throws ReceivingException {
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine(null);
    instructionRequest.setProblemTagId("DUMMY_PROBLEM_ID");
    when(instructionRepository.findByMessageId(anyString())).thenReturn(null);
    when(receiptService.getReceivedQtyByPoAndPoLine(any(), any())).thenReturn(50L);
    when(receiptService.getReceivedQtyByProblemId(any())).thenReturn(50L);

    httpHeaders.add("isKotlin", "true");
    Instruction instructionResponse =
        instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
  }

  @Test
  public void testCreateInstructionReturnsPlaceOnConveyorInstruction() {
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setOnline(Boolean.TRUE);
    when(appConfig.isDeliveryDocInPlaceOnConveyorEnabled()).thenReturn(Boolean.TRUE);
    try {
      Instruction instructionResponse =
          instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assertNotNull(instructionResponse);
      assertEquals(ReceivingConstants.RECEIVING_PROVIDER_ID, instructionResponse.getProviderId());
      assertEquals("Place On Conveyor", instructionResponse.getInstructionCode());
      assertEquals("Place item on conveyor instruction", instructionResponse.getInstructionMsg());
      assertEquals(
          gson.toJson(deliveryDocuments.get(0)), instructionResponse.getDeliveryDocument());
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  @Test
  public void testCheckForPlaceOnConveyorOrFloorLine_PlaceOnConveyorInstruction_MultiPo_OpenQty() {
    deliveryDocuments.add(deliveryDocuments.get(0));
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setOnline(Boolean.TRUE);
    instructionRequest.setOverflowReceiving(Boolean.FALSE);
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    when(regulatedItemService.isVendorComplianceRequired(any())).thenReturn(Boolean.FALSE);
    when(appConfig.isDeliveryDocInPlaceOnConveyorEnabled()).thenReturn(Boolean.TRUE);
    httpHeaders.add("isKotlin", "true");
    when(receiptService.getReceivedQtyByPoAndPoLine(any(), any())).thenReturn(10l);
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    try {
      InstructionResponse instructionResponse = new InstructionResponseImplNew();
      boolean returnInstruction =
          instructionService.checkForPlaceOnConveyorOrFloorLine(
              deliveryDocuments,
              instructionRequest,
              instructionResponse,
              Boolean.TRUE,
              httpHeaders);
      assertTrue(returnInstruction);
      assertNotNull(instructionResponse);
      assertNotNull(instructionResponse.getInstruction());
      assertEquals(
          ReceivingConstants.RECEIVING_PROVIDER_ID,
          instructionResponse.getInstruction().getProviderId());
      assertEquals("Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
      assertEquals(
          "Place item on conveyor instruction",
          instructionResponse.getInstruction().getInstructionMsg());

      assertEquals(
          20,
          (int)
              instructionResponse
                  .getDeliveryDocuments()
                  .get(0)
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getOpenQty());
      assertEquals(
          30,
          (int)
              instructionResponse
                  .getDeliveryDocuments()
                  .get(0)
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getTotalOrderQty());
      assertEquals(
          10,
          (int)
              instructionResponse
                  .getDeliveryDocuments()
                  .get(0)
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getTotalReceivedQty());

      httpHeaders.remove("isKotlin");
    } catch (ReceivingException e) {
      assert (false);
    }
    deliveryDocuments.remove(1);
  }

  @Test
  public void testCreateDockTagInstruction_HappyFlow() {
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    when(instructionRepository.findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
            anyString(), anyLong()))
        .thenReturn(new ArrayList<>());
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    try {
      Instruction instructionResponse =
          instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assertNotNull(instructionResponse);
      assertEquals(ReceivingConstants.RECEIVING_PROVIDER_ID, instructionResponse.getProviderId());
      assertEquals(ReceivingConstants.DOCK_TAG, instructionResponse.getActivityName());
      assertEquals(ReceivingConstants.DOCK_TAG, instructionResponse.getInstructionCode());
      assertEquals("", instructionResponse.getPurchaseReferenceNumber());
      assertEquals(dockTagId, instructionResponse.getContainer().getTrackingId());
      assertEquals(false, instructionResponse.getContainer().getCtrShippable());
      assertEquals(false, instructionResponse.getContainer().getCtrReusable());
      assertEquals(
          InventoryStatus.WORK_IN_PROGRESS.name(),
          instructionResponse.getContainer().getInventoryStatus());
      Map<String, Object> ctrLabel = instructionResponse.getContainer().getCtrLabel();

      verify(lpnCacheService, times(1)).getLPNBasedOnTenant(httpHeaders);
      verify(instructionRepository, times(1)).save(any());
      verify(instructionRepository, times(1))
          .findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(anyString(), anyLong());
      verify(dockTagPersisterService, times(1)).saveDockTag(any());
      verify(jmsPublisher, times(1)).publish(anyString(), any(), anyBoolean());
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testCreateDockTagInstruction_ExistingOpenDockTagInstruction() {
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    when(instructionRepository.findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
            "Dock Tag", 261220189L))
        .thenReturn(Collections.singletonList(MockInstruction.getDockTagInstruction()));
    try {
      when(configUtils.getConfiguredInstance(anyString(), anyString(), any()))
          .thenReturn(accDockTagService);
      Instruction instructionResponse =
          instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assertNotNull(instructionResponse);
      assertEquals(ReceivingConstants.RECEIVING_PROVIDER_ID, instructionResponse.getProviderId());
      assertEquals(ReceivingConstants.DOCK_TAG, instructionResponse.getActivityName());
      assertEquals(ReceivingConstants.DOCK_TAG, instructionResponse.getInstructionCode());
      assertEquals("", instructionResponse.getPurchaseReferenceNumber());
      assertEquals(
          MockInstruction.getDockTagContainerDetails().getTrackingId(),
          instructionResponse.getContainer().getTrackingId());
      assertEquals(
          MockInstruction.getDockTagContainerDetails().getCtrShippable(),
          instructionResponse.getContainer().getCtrShippable());
      assertEquals(
          MockInstruction.getDockTagContainerDetails().getCtrReusable(),
          instructionResponse.getContainer().getCtrReusable());
      assertEquals(
          MockInstruction.getDockTagContainerDetails().getInventoryStatus(),
          instructionResponse.getContainer().getInventoryStatus());

      verify(configUtils, times(0)).getConfiguredInstance(eq("32987"), anyString(), any());
      verify(lpnCacheService, times(0)).getLPNBasedOnTenant(httpHeaders);
      verify(instructionRepository, times(0)).save(any());
      verify(instructionRepository, times(1))
          .findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(anyString(), anyLong());
      verify(dockTagPersisterService, times(0)).saveDockTag(any());
      verify(jmsPublisher, times(0)).publish(anyString(), any(), anyBoolean());
      verify(dockTagService, times(0)).createDockTag(anyString(), anyLong(), anyString(), any());
      verify(dockTagService, times(0)).saveDockTag(any());
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testCheckForPlaceOnConveyorOrFloorLine_PlaceOnConveyorInstruction_MultiPo() {
    deliveryDocuments.add(deliveryDocuments.get(0));
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setOnline(Boolean.TRUE);
    instructionRequest.setOverflowReceiving(Boolean.FALSE);
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    when(regulatedItemService.isVendorComplianceRequired(any())).thenReturn(Boolean.FALSE);
    when(appConfig.isDeliveryDocInPlaceOnConveyorEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    try {
      InstructionResponse instructionResponse = new InstructionResponseImplNew();
      boolean returnInstruction =
          instructionService.checkForPlaceOnConveyorOrFloorLine(
              deliveryDocuments,
              instructionRequest,
              instructionResponse,
              Boolean.TRUE,
              httpHeaders);
      assertTrue(returnInstruction);
      assertNotNull(instructionResponse);
      assertNotNull(instructionResponse.getInstruction());
      assertEquals(
          ReceivingConstants.RECEIVING_PROVIDER_ID,
          instructionResponse.getInstruction().getProviderId());
      assertEquals("Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
      assertEquals(
          "Place item on conveyor instruction",
          instructionResponse.getInstruction().getInstructionMsg());
      assertEquals(
          gson.toJson(deliveryDocuments.get(0)),
          instructionResponse.getInstruction().getDeliveryDocument());
    } catch (ReceivingException e) {
      assert (false);
    }
    deliveryDocuments.remove(1);
  }

  @Test
  public void testCheckForPlaceOnConveyorOrFloorLine_PlaceOnConveyorInstruction_MultiPoLine() {
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocuments.get(0).setDeliveryDocumentLines(deliveryDocumentLines);
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setOnline(Boolean.TRUE);
    instructionRequest.setOverflowReceiving(Boolean.FALSE);
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    when(regulatedItemService.isVendorComplianceRequired(any())).thenReturn(Boolean.FALSE);
    when(appConfig.isDeliveryDocInPlaceOnConveyorEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    try {
      InstructionResponse instructionResponse = new InstructionResponseImplNew();
      boolean returnInstruction =
          instructionService.checkForPlaceOnConveyorOrFloorLine(
              deliveryDocuments,
              instructionRequest,
              instructionResponse,
              Boolean.TRUE,
              httpHeaders);
      assertTrue(returnInstruction);
      assertNotNull(instructionResponse);
      assertNotNull(instructionResponse.getInstruction());
      assertEquals(
          ReceivingConstants.RECEIVING_PROVIDER_ID,
          instructionResponse.getInstruction().getProviderId());
      assertEquals("Place On Conveyor", instructionResponse.getInstruction().getInstructionCode());
      assertEquals(
          "Place item on conveyor instruction",
          instructionResponse.getInstruction().getInstructionMsg());
      assertEquals(
          gson.toJson(deliveryDocuments.get(0)),
          instructionResponse.getInstruction().getDeliveryDocument());
    } catch (ReceivingException e) {
      assert (false);
    }
    deliveryDocuments
        .get(0)
        .setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
  }

  @Test
  public void testCheckForPlaceOnConveyorOrFloorLine_DockTagInstruction_MultiPo() {
    deliveryDocuments.add(deliveryDocuments.get(0));
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    when(regulatedItemService.isVendorComplianceRequired(any())).thenReturn(Boolean.FALSE);
    when(instructionRepository.findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
            anyString(), anyLong()))
        .thenReturn(new ArrayList<>());
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    try {
      InstructionResponse instructionResponse = new InstructionResponseImplNew();
      boolean returnInstruction =
          instructionService.checkForPlaceOnConveyorOrFloorLine(
              deliveryDocuments,
              instructionRequest,
              instructionResponse,
              Boolean.TRUE,
              httpHeaders);
      assertTrue(returnInstruction);
      assertNotNull(instructionResponse);
      assertNotNull(instructionResponse.getInstruction());
      assertEquals(
          ReceivingConstants.RECEIVING_PROVIDER_ID,
          instructionResponse.getInstruction().getProviderId());
      assertEquals(
          ReceivingConstants.DOCK_TAG, instructionResponse.getInstruction().getActivityName());
      assertEquals(
          ReceivingConstants.DOCK_TAG, instructionResponse.getInstruction().getInstructionCode());
      assertEquals("", instructionResponse.getInstruction().getPurchaseReferenceNumber());
      assertEquals(dockTagId, instructionResponse.getInstruction().getContainer().getTrackingId());
      assertEquals(false, instructionResponse.getInstruction().getContainer().getCtrShippable());
      assertEquals(false, instructionResponse.getInstruction().getContainer().getCtrReusable());
      assertEquals(
          InventoryStatus.WORK_IN_PROGRESS.name(),
          instructionResponse.getInstruction().getContainer().getInventoryStatus());
      Map<String, Object> ctrLabel =
          instructionResponse.getInstruction().getContainer().getCtrLabel();

      verify(lpnCacheService, times(1)).getLPNBasedOnTenant(httpHeaders);
      verify(instructionRepository, times(1)).save(any());
      verify(instructionRepository, times(1))
          .findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(anyString(), anyLong());
      verify(dockTagPersisterService, times(1)).saveDockTag(any());
      verify(jmsPublisher, times(1)).publish(anyString(), any(), anyBoolean());
    } catch (ReceivingException e) {
      fail();
    }
    deliveryDocuments.remove(1);
  }

  @Test
  public void testCheckForPlaceOnConveyorOrFloorLine_DockTagInstruction_MultiPoLine() {
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocuments.get(0).setDeliveryDocumentLines(deliveryDocumentLines);
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    instructionRequest.setOverflowReceiving(Boolean.FALSE);
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    when(regulatedItemService.isVendorComplianceRequired(any())).thenReturn(Boolean.FALSE);
    when(instructionRepository.findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
            anyString(), anyLong()))
        .thenReturn(new ArrayList<>());
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    try {
      InstructionResponse instructionResponse = new InstructionResponseImplNew();
      boolean returnInstruction =
          instructionService.checkForPlaceOnConveyorOrFloorLine(
              deliveryDocuments,
              instructionRequest,
              instructionResponse,
              Boolean.TRUE,
              httpHeaders);
      assertTrue(returnInstruction);
      assertNotNull(instructionResponse);
      assertNotNull(instructionResponse.getInstruction());
      assertEquals(
          ReceivingConstants.RECEIVING_PROVIDER_ID,
          instructionResponse.getInstruction().getProviderId());
      assertEquals(
          ReceivingConstants.DOCK_TAG, instructionResponse.getInstruction().getActivityName());
      assertEquals(
          ReceivingConstants.DOCK_TAG, instructionResponse.getInstruction().getInstructionCode());
      assertEquals("", instructionResponse.getInstruction().getPurchaseReferenceNumber());
      assertEquals(dockTagId, instructionResponse.getInstruction().getContainer().getTrackingId());
      assertEquals(false, instructionResponse.getInstruction().getContainer().getCtrShippable());
      assertEquals(false, instructionResponse.getInstruction().getContainer().getCtrReusable());
      assertEquals(
          InventoryStatus.WORK_IN_PROGRESS.name(),
          instructionResponse.getInstruction().getContainer().getInventoryStatus());
      Map<String, Object> ctrLabel =
          instructionResponse.getInstruction().getContainer().getCtrLabel();

      verify(lpnCacheService, times(1)).getLPNBasedOnTenant(httpHeaders);
      verify(instructionRepository, times(1)).save(any());
      verify(instructionRepository, times(1))
          .findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(anyString(), anyLong());
      verify(dockTagPersisterService, times(1)).saveDockTag(any());
      verify(jmsPublisher, times(1)).publish(anyString(), any(), anyBoolean());
    } catch (ReceivingException e) {
      fail();
    }
    deliveryDocuments
        .get(0)
        .setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
  }

  @Test
  public void testCheckForPlaceOnConveyorOrFloorLine_NonCon() {
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    instructionRequest.setOverflowReceiving(Boolean.FALSE);
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    when(instructionRepository.findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
            anyString(), anyLong()))
        .thenReturn(new ArrayList<>());
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    try {
      InstructionResponse instructionResponse = new InstructionResponseImplNew();
      boolean returnInstruction =
          instructionService.checkForPlaceOnConveyorOrFloorLine(
              deliveryDocuments,
              instructionRequest,
              instructionResponse,
              Boolean.FALSE,
              httpHeaders);
      assertFalse(returnInstruction);
      assertNotNull(instructionResponse);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testCheckForPlaceOnConveyorOrFloorLine_RegulatedItem() {
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    instructionRequest.setOverflowReceiving(Boolean.FALSE);
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    when(regulatedItemService.isVendorComplianceRequired(any())).thenReturn(Boolean.TRUE);
    when(instructionRepository.findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
            anyString(), anyLong()))
        .thenReturn(new ArrayList<>());
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    try {
      InstructionResponse instructionResponse = new InstructionResponseImplNew();
      boolean returnInstruction =
          instructionService.checkForPlaceOnConveyorOrFloorLine(
              deliveryDocuments,
              instructionRequest,
              instructionResponse,
              Boolean.TRUE,
              httpHeaders);
      assertFalse(returnInstruction);
      assertNotNull(instructionResponse);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testCheckForPlaceOnConveyorOrFloorLine_ManualReceiving() {
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    instructionRequest.setManualReceivingEnabled(Boolean.TRUE);
    instructionRequest.setOverflowReceiving(Boolean.FALSE);
    when(regulatedItemService.isVendorComplianceRequired(any())).thenReturn(Boolean.FALSE);
    when(instructionRepository.findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
            anyString(), anyLong()))
        .thenReturn(new ArrayList<>());
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    try {
      InstructionResponse instructionResponse = new InstructionResponseImplNew();
      boolean returnInstruction =
          instructionService.checkForPlaceOnConveyorOrFloorLine(
              deliveryDocuments,
              instructionRequest,
              instructionResponse,
              Boolean.TRUE,
              httpHeaders);
      assertFalse(returnInstruction);
      assertNotNull(instructionResponse);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testCheckForPlaceOnConveyorOrFloorLine_OverflowReceiving() {
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    instructionRequest.setOverflowReceiving(Boolean.TRUE);
    instructionRequest.setManualReceivingEnabled(Boolean.FALSE);
    when(regulatedItemService.isVendorComplianceRequired(any())).thenReturn(Boolean.FALSE);
    when(instructionRepository.findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
            anyString(), anyLong()))
        .thenReturn(new ArrayList<>());
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    try {
      InstructionResponse instructionResponse = new InstructionResponseImplNew();
      boolean returnInstruction =
          instructionService.checkForPlaceOnConveyorOrFloorLine(
              deliveryDocuments,
              instructionRequest,
              instructionResponse,
              Boolean.TRUE,
              httpHeaders);
      assertFalse(returnInstruction);
      assertNotNull(instructionResponse);
      assertNull(instructionResponse.getInstruction());
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testCompleteDockTagInstruction_HappyFlow() throws ReceivingException {

    String updatedPrintRequest =
        "[{\"ttlInHours\":72,\"labelIdentifier\":\""
            + dockTagId
            + "\",\"data\":[{\"key\":\"DELIVERYNBR\",\"value\":\"261220189\"},{\"key\":\"QTY\",\"value\":\"0\"},{\"key\":\"ROTATEDATE\",\"value\":\"-\"}],\"formatName\":\"dock_tag_atlas\"}]";

    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    Map<String, Object> completeAndCreatePrintJobsResponse = new HashMap<>();
    completeAndCreatePrintJobsResponse.put(
        "instruction", MockInstruction.getCompletedDockTagInstruction());
    completeAndCreatePrintJobsResponse.put("container", MockInstruction.getDockTagContainer());
    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(MockInstruction.getDockTagInstruction()));
    when(instructionRepository.save(any(Instruction.class)))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(MockInstruction.getPrintJob());
    when(containerService.containerComplete(anyString(), anyString())).thenReturn(container);
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
    when(configUtils.getPutawayServiceByFacility("32987")).thenReturn(defaultPutawayHandler);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    when(configUtils.isShowRotateDateOnPrintLabelEnabled(any(Integer.class))).thenReturn(true);
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstructionForDockTag(
                  MockInstruction.getDockTagInstruction(), httpHeaders);

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
            eq(1), eq("5555"), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCompleteDockTagInstruction_AlreadyCompletedInstruction()
      throws ReceivingException {
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    Map<String, Object> completeAndCreatePrintJobsResponse = new HashMap<>();
    completeAndCreatePrintJobsResponse.put(
        "instruction", MockInstruction.getCompletedDockTagInstruction());
    completeAndCreatePrintJobsResponse.put("container", MockInstruction.getDockTagContainer());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(MockInstruction.getCompletedDockTagInstruction());
    when(instructionPersisterService.completeAndCreatePrintJob(any(), any()))
        .thenReturn(completeAndCreatePrintJobsResponse);
    when(instructionRepository.save(any(Instruction.class)))
        .thenReturn(MockInstruction.getDockTagInstruction());
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(MockInstruction.getPrintJob());
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
    when(configUtils.getPutawayServiceByFacility("32987")).thenReturn(defaultPutawayHandler);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstructionForDockTag(
                  MockInstruction.getCompletedDockTagInstruction(), httpHeaders);
      fail();
    } catch (ReceivingException ex) {
      verify(jmsPublisher, times(0))
          .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
      verify(movePublisher, times(0))
          .publishMove(
              eq(1), eq("5555"), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
      throw ex;
    }
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCompleteDockTagInstruction_ExceptionThrown() throws ReceivingException {
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    Map<String, Object> completeAndCreatePrintJobsResponse = new HashMap<>();
    completeAndCreatePrintJobsResponse.put(
        "instruction", MockInstruction.getCompletedDockTagInstruction());
    completeAndCreatePrintJobsResponse.put("container", MockInstruction.getDockTagContainer());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(MockInstruction.getDockTagInstruction());
    when(instructionPersisterService.completeAndCreatePrintJob(any(), any()))
        .thenThrow(ReceivingException.class);
    when(instructionRepository.save(any(Instruction.class)))
        .thenReturn(MockInstruction.getDockTagInstruction());
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(MockInstruction.getPrintJob());
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
    when(configUtils.getPutawayServiceByFacility("32987")).thenReturn(defaultPutawayHandler);
    when(configUtils.getFeatureFlagsByFacility("32987")).thenReturn(defaultFeatureFlagsByFacility);
    try {
      InstructionResponseImplNew response =
          (InstructionResponseImplNew)
              instructionService.completeInstructionForDockTag(
                  MockInstruction.getDockTagInstruction(), httpHeaders);
      fail();
    } catch (ReceivingException ex) {
      verify(jmsPublisher, times(0))
          .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
      verify(movePublisher, times(0))
          .publishMove(
              eq(1), eq("5555"), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
      throw ex;
    }
  }

  @Test
  public void testCreateNonConDockTag() throws IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDoorNumber("101");
    instructionRequest.setDeliveryNumber("1234567");
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(configUtils.getDCSpecificMoveDestinationForNonConDockTag(any())).thenReturn("PSN");
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(MockInstruction.getPrintJob());

    Container dockTagContainer = MockInstruction.getDockTagContainer();
    dockTagContainer.setIsConveyable(false);
    dockTagContainer.setCreateTs(new Date());
    dockTagContainer.setLastChangedTs(new Date());
    dockTagContainer.setCompleteTs(new Date());
    dockTagContainer.setPublishTs(new Date());
    when(containerService.createDockTagContainer(any(), any(), any(), anyBoolean()))
        .thenReturn(dockTagContainer);
    InstructionResponseImplNew nonConDockTag =
        (InstructionResponseImplNew)
            instructionService.createNonConDockTag(instructionRequest, headers, "PTR001");
    assertNotNull(nonConDockTag);
    assertNull(nonConDockTag.getDeliveryDocuments());
    assertNull(nonConDockTag.getDeliveryStatus());
    assertNotNull(nonConDockTag.getInstruction());

    assertNotNull(nonConDockTag.getPrintJob());

    ArgumentCaptor<Instruction> insCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(insCaptor.capture());
    assertNotNull(insCaptor.getValue());
    assertEquals(insCaptor.getValue().getMessageId(), dockTagId);
    assertEquals(insCaptor.getValue().getMove().get("toLocation"), "PTR001");
    assertNull(insCaptor.getValue().getGtin());
    assertNull(insCaptor.getValue().getItemDescription());

    verify(containerService, times(1))
        .createDockTagContainer(any(), any(), any(), eq(Boolean.TRUE));
    verify(printJobService, times(1)).createPrintJob(eq(1234567L), any(), anySet(), eq("sysadmin"));
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());
    assertEquals(dockTagArgumentCaptor.getValue().getDockTagId(), dockTagId);
    assertEquals(dockTagArgumentCaptor.getValue().getDockTagStatus(), InstructionStatus.CREATED);
    assertNull(dockTagArgumentCaptor.getValue().getCompleteTs());
    assertEquals(dockTagArgumentCaptor.getValue().getDeliveryNumber(), Long.valueOf(1234567));
    assertEquals(dockTagArgumentCaptor.getValue().getDockTagType(), DockTagType.NON_CON);

    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(2))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMCreateMessage = receivingJMSEventList.get(0).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(1).getMessageBody();
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/docktagWFMCreate.json")
                            .getCanonicalPath()))),
            WFMCreateMessage));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/docktagWFMComplete.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    verify(containerService, times(1)).publishExceptionContainer(any(), any(), eq(Boolean.TRUE));
  }

  @Test
  public void testCreateNonConDockTag_MappedPbylAreaMissing() throws IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDoorNumber("101");
    instructionRequest.setDeliveryNumber("1234567");
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(configUtils.getDCSpecificMoveDestinationForNonConDockTag(any())).thenReturn("PSN");
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(MockInstruction.getPrintJob());
    Container dockTagContainer = MockInstruction.getDockTagContainer();
    dockTagContainer.setIsConveyable(false);
    dockTagContainer.setCreateTs(new Date());
    dockTagContainer.setLastChangedTs(new Date());
    dockTagContainer.setCompleteTs(new Date());
    dockTagContainer.setPublishTs(new Date());
    when(containerService.createDockTagContainer(any(), any(), any(), anyBoolean()))
        .thenReturn(dockTagContainer);
    InstructionResponseImplNew nonConDockTag =
        (InstructionResponseImplNew)
            instructionService.createNonConDockTag(instructionRequest, headers, null);
    assertNotNull(nonConDockTag);
    assertNull(nonConDockTag.getDeliveryDocuments());
    assertNull(nonConDockTag.getDeliveryStatus());
    assertNotNull(nonConDockTag.getInstruction());

    assertNotNull(nonConDockTag.getPrintJob());

    ArgumentCaptor<Instruction> insCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(insCaptor.capture());
    assertNotNull(insCaptor.getValue());
    assertEquals(insCaptor.getValue().getMessageId(), dockTagId);
    assertEquals(insCaptor.getValue().getMove().get("toLocation"), "PSN");
    assertNull(insCaptor.getValue().getGtin());
    assertNull(insCaptor.getValue().getItemDescription());

    verify(containerService, times(1))
        .createDockTagContainer(any(), any(), any(), eq(Boolean.TRUE));
    verify(printJobService, times(1)).createPrintJob(eq(1234567L), any(), anySet(), eq("sysadmin"));
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());
    assertEquals(dockTagArgumentCaptor.getValue().getDockTagId(), dockTagId);
    assertEquals(dockTagArgumentCaptor.getValue().getDockTagStatus(), InstructionStatus.CREATED);
    assertNull(dockTagArgumentCaptor.getValue().getCompleteTs());
    assertEquals(dockTagArgumentCaptor.getValue().getDeliveryNumber(), Long.valueOf(1234567));
    assertEquals(dockTagArgumentCaptor.getValue().getDockTagType(), DockTagType.NON_CON);

    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(2))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMCreateMessage = receivingJMSEventList.get(0).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(1).getMessageBody();
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/docktagWFMCreate.json")
                            .getCanonicalPath()))),
            WFMCreateMessage));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/docktagWFMComplete.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    verify(containerService, times(1)).publishExceptionContainer(any(), any(), eq(Boolean.TRUE));
  }

  @Test
  public void
      testCreateInstructionReturnsBuildPalletInstructionForNonConDTReceivingWhenPbylLocation()
          throws ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        gson.fromJson(
            MockACLMessageData.getDeliveryDocuments(),
            new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setIsConveyable(Boolean.FALSE);
    InstructionRequest instructionRequest =
        gson.fromJson(MockACLMessageData.getMockInstructionRequest(), InstructionRequest.class);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    instructionRequest.setPbylDockTagId("a328180000000000000123");
    instructionRequest.setPbylLocation("PTR003");

    doReturn(MockACLMessageData.getBuildContainerResponseForACLReceiving())
        .when(fdeService)
        .receive(any(), any());
    when(instructionRepository.findByMessageId(anyString())).thenReturn(null);
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    Instruction instructionResponse =
        instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    assertNotNull(instructionResponse);
    ArgumentCaptor<FdeCreateContainerRequest> argumentCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(argumentCaptor.capture(), any());
    assertEquals(argumentCaptor.getValue().getDoorNumber(), "PTR003");
  }

  @Test
  public void testCreateInstructionReturnsBuildPalletInstructionForNonConDTReceiving()
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        gson.fromJson(
            MockACLMessageData.getDeliveryDocuments(),
            new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).setIsConveyable(Boolean.FALSE);
    InstructionRequest instructionRequest =
        gson.fromJson(MockACLMessageData.getMockInstructionRequest(), InstructionRequest.class);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    instructionRequest.setPbylDockTagId("a328180000000000000123");

    doReturn(MockACLMessageData.getBuildContainerResponseForACLReceiving())
        .when(fdeService)
        .receive(any(), any());
    when(instructionRepository.findByMessageId(anyString())).thenReturn(null);
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    DockTag dockTag = new DockTag();
    dockTag.setScannedLocation("PTR001");
    dockTag.setDeliveryNumber(1234567L);
    dockTag.setDockTagId("a328180000000000000123");
    when(dockTagPersisterService.getDockTagByDockTagId("a328180000000000000123"))
        .thenReturn(dockTag);
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);

    Instruction instructionResponse =
        instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    assertNotNull(instructionResponse);
    ArgumentCaptor<FdeCreateContainerRequest> argumentCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(argumentCaptor.capture(), any());
    assertEquals(argumentCaptor.getValue().getDoorNumber(), "PTR001");
  }

  @Test
  public void testCreateFLDockTagInstructionATNonConDTReceiving() {
    InstructionRequest instructionRequest =
        gson.fromJson(MockACLMessageData.getMockInstructionRequest(), InstructionRequest.class);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setOnline(Boolean.FALSE);
    instructionRequest.setMappedFloorLine("RCV100");
    instructionRequest.setPbylDockTagId("a328180000000000000123");
    instructionRequest.setPbylLocation("PTR001");

    when(instructionRepository.findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
            anyString(), anyLong()))
        .thenReturn(new ArrayList<>());
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32987", ReceivingConstants.PUBLISH_CONTAINER);
    try {
      Instruction instructionResponse =
          instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assertNotNull(instructionResponse);
      assertEquals(ReceivingConstants.RECEIVING_PROVIDER_ID, instructionResponse.getProviderId());
      assertEquals(ReceivingConstants.DOCK_TAG, instructionResponse.getActivityName());
      assertEquals(ReceivingConstants.DOCK_TAG, instructionResponse.getInstructionCode());
      assertEquals("", instructionResponse.getPurchaseReferenceNumber());
      assertEquals(dockTagId, instructionResponse.getContainer().getTrackingId());
      assertEquals(false, instructionResponse.getContainer().getCtrShippable());
      assertEquals(false, instructionResponse.getContainer().getCtrReusable());
      assertEquals(
          InventoryStatus.WORK_IN_PROGRESS.name(),
          instructionResponse.getContainer().getInventoryStatus());
      Map<String, Object> ctrLabel = instructionResponse.getContainer().getCtrLabel();

      verify(lpnCacheService, times(1)).getLPNBasedOnTenant(httpHeaders);
      verify(instructionRepository, times(1)).save(any());
      verify(instructionRepository, times(1))
          .findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(anyString(), anyLong());
      verify(dockTagPersisterService, times(1)).saveDockTag(any());
      verify(jmsPublisher, times(1)).publish(anyString(), any(), anyBoolean());
      ArgumentCaptor<InstructionRequest> argumentCaptor =
          ArgumentCaptor.forClass(InstructionRequest.class);
      verify(containerService, times(1))
          .createDockTagContainer(any(), argumentCaptor.capture(), any(), anyBoolean());
      assertEquals("PTR001", argumentCaptor.getValue().getDoorNumber());
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testCreatePbylDocktagInstructionResponse() throws IOException {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDoorNumber("101");
    instructionRequest.setDeliveryNumber("1234567");
    instructionRequest.setPbylDockTagId("PTR001");
    HttpHeaders headers = MockHttpHeaders.getHeaders();

    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn(dockTagId);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
    when(configUtils.getDCSpecificMoveDestinationForNonConDockTag(any())).thenReturn("PSN");
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(printJobService.createPrintJob(any(Long.class), any(Long.class), any(), anyString()))
        .thenReturn(MockInstruction.getPrintJob());

    Container dockTagContainer = MockInstruction.getDockTagContainer();
    dockTagContainer.setIsConveyable(false);
    dockTagContainer.setCreateTs(new Date());
    dockTagContainer.setLastChangedTs(new Date());
    dockTagContainer.setCompleteTs(new Date());
    dockTagContainer.setPublishTs(new Date());
    when(containerService.createDockTagContainer(any(), any(), any(), anyBoolean()))
        .thenReturn(dockTagContainer);
    InstructionResponseImplNew nonConDockTag =
        (InstructionResponseImplNew)
            instructionService.createPByLDockTagInstructionResponse(instructionRequest, headers);
    assertNotNull(nonConDockTag);
    assertNull(nonConDockTag.getDeliveryDocuments());
    assertNull(nonConDockTag.getDeliveryStatus());
    assertNotNull(nonConDockTag.getInstruction());

    ArgumentCaptor<Instruction> insCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(insCaptor.capture());
    assertNotNull(insCaptor.getValue());
    assertEquals(insCaptor.getValue().getMessageId(), dockTagId);
    assertNull(insCaptor.getValue().getGtin());
    assertNull(insCaptor.getValue().getItemDescription());

    verify(containerService, times(1))
        .createDockTagContainer(any(), any(), any(), eq(Boolean.FALSE));
    ArgumentCaptor<DockTag> dockTagArgumentCaptor = ArgumentCaptor.forClass(DockTag.class);
    verify(dockTagPersisterService, times(1)).saveDockTag(dockTagArgumentCaptor.capture());
    assertEquals(dockTagArgumentCaptor.getValue().getDockTagId(), dockTagId);
    assertEquals(dockTagArgumentCaptor.getValue().getDockTagStatus(), InstructionStatus.CREATED);
    assertNull(dockTagArgumentCaptor.getValue().getCompleteTs());
    assertEquals(dockTagArgumentCaptor.getValue().getDeliveryNumber(), Long.valueOf(1234567));
    assertEquals(dockTagArgumentCaptor.getValue().getDockTagType(), DockTagType.NON_CON);
  }
}
