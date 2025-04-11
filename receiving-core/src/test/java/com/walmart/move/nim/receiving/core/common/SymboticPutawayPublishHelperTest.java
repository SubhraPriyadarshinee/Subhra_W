package com.walmart.move.nim.receiving.core.common;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaSymPutawayMessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.Content;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayMessage;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SymboticPutawayPublishHelperTest extends ReceivingTestBase {
  @InjectMocks private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private KafkaSymPutawayMessagePublisher kafkaSymPutawayMessagePublisher;
  @Mock private ContainerItemRepository containerItemRepository;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private ContainerPersisterService containerPersisterService;

  private final String trackingId = "9778092003";
  private final Integer backoutQty = 0;
  private final Integer palletAdjustmentQty = 10;

  private final String poNumber = "987654321";
  private final List<String> VALID_ASRS_LIST =
      Arrays.asList(
          ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE, ReceivingConstants.SYM_BRKPK_ASRS_VALUE);

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("us");
  }

  @AfterMethod
  public void tearDown() {
    reset(kafkaSymPutawayMessagePublisher, containerItemRepository, containerPersisterService);
  }

  @Test
  public void testPublishPutawayAddMessageToKafkaHappyPathDA() throws ReceivingException {
    Container container = mock(Container.class);
    Mockito.doNothing()
        .when(kafkaSymPutawayMessagePublisher)
        .publish(any(SymPutawayMessage.class), anyMap());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(container.getContainerItems()).thenReturn(Arrays.asList(mockContainer()));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(mockContainer());
    SymPutawayMessage content = mock(SymPutawayMessage.class);
    try (MockedStatic<SymboticUtils> mocked = Mockito.mockStatic(SymboticUtils.class)) {
      mocked
          .when(
              () ->
                  SymboticUtils.createPutawayAddMessage(
                      any(Container.class), any(ContainerItem.class), anyString()))
          .thenReturn(content);
    }
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(
        Arrays.asList(trackingId), new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(1))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishPutawayAddMessageToKafkaHappyPathDASymConnected()
      throws ReceivingException {
    Container container = mock(Container.class);
    Mockito.doNothing()
        .when(kafkaSymPutawayMessagePublisher)
        .publish(any(SymPutawayMessage.class), anyMap());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(container.getContainerItems()).thenReturn(Arrays.asList(mockContainer()));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("SYM2_5");
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(mockContainer());
    SymPutawayMessage content = mock(SymPutawayMessage.class);
    try (MockedStatic<SymboticUtils> mocked = Mockito.mockStatic(SymboticUtils.class)) {
      mocked
          .when(
              () ->
                  SymboticUtils.createPutawayAddMessage(
                      any(Container.class), any(ContainerItem.class), anyString()))
          .thenReturn(content);
    }
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(
        Arrays.asList(trackingId), new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(1))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishPutawayAddMessageToKafkaHappyPathSSTK() throws ReceivingException {
    Container container = mock(Container.class);
    Mockito.doNothing()
        .when(kafkaSymPutawayMessagePublisher)
        .publish(any(SymPutawayMessage.class), anyMap());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(container.getContainerItems()).thenReturn(Arrays.asList(mockContainer()));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(mockContainer());
    SymPutawayMessage content = mock(SymPutawayMessage.class);
    try (MockedStatic<SymboticUtils> mocked = Mockito.mockStatic(SymboticUtils.class)) {
      mocked
          .when(() -> SymboticUtils.isValidForSymPutaway(anyString(), anyList(), anyString()))
          .thenReturn(true);
      mocked
          .when(
              () ->
                  SymboticUtils.createPutawayAddMessage(
                      any(Container.class), any(ContainerItem.class), anyString()))
          .thenReturn(content);
    }
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(
        Arrays.asList(trackingId), new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(1))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishPutawayAddMessageToKafkaException() throws ReceivingException {
    Container container = mock(Container.class);
    Mockito.doThrow(ReceivingInternalException.class)
        .when(kafkaSymPutawayMessagePublisher)
        .publish(any(SymPutawayMessage.class), anyMap());
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(container.getContainerItems()).thenReturn(Arrays.asList(mockContainer()));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(mockContainer());
    SymPutawayMessage content = mock(SymPutawayMessage.class);
    try (MockedStatic<SymboticUtils> mocked = Mockito.mockStatic(SymboticUtils.class)) {
      mocked
          .when(
              () ->
                  SymboticUtils.createPutawayAddMessage(
                      any(Container.class), any(ContainerItem.class), anyString()))
          .thenReturn(content);
    }
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(
        Arrays.asList(trackingId), new HttpHeaders());
    // assertEquals("Container is null", exception.getMessage());
    verify(kafkaSymPutawayMessagePublisher, times(1))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_validTrackingIdWithContainerItems()
      throws ReceivingException {
    Container container = mock(Container.class);
    ContainerItem containerItem = mock(ContainerItem.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(container.getContainerItems()).thenReturn(Arrays.asList(containerItem));
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders);
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_validFreightTypeSSTK() throws ReceivingException {
    Container container = mock(Container.class);
    ContainerItem containerItem = mock(ContainerItem.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(container.getContainerItems()).thenReturn(Arrays.asList(containerItem));
    when(containerItem.getInboundChannelMethod()).thenReturn("SSTK");
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders);
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_validFreightTypeDA() throws ReceivingException {
    Container container = mock(Container.class);
    ContainerItem containerItem = mock(ContainerItem.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(container.getContainerItems()).thenReturn(Arrays.asList(containerItem));
    when(containerItem.getInboundChannelMethod()).thenReturn("DA");
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders);
  }

  @Test
  public void testCreatePutawayAddMessage_validParameters_SSTK() {
    ContainerItem containerItem = mock(ContainerItem.class);
    Container container = mock(Container.class);
    String symFreightType = SymFreightType.SSTK.name();
    assertNotNull(container);
    assertNotNull(containerItem);
    assertNotNull(symFreightType);
    SymPutawayMessage result =
        SymboticUtils.createPutawayAddMessage(container, containerItem, symFreightType);
    assertEquals("SSTK", result.getFreightType());
    assertEquals("PALLET", result.getLabelType());
  }

  @Test
  public void testCreatePutawayAddMessage_validParameters_DA() {
    ContainerItem containerItem = mock(ContainerItem.class);
    Container container = mock(Container.class);
    String symFreightType = SymFreightType.DA.name();
    assertNotNull(container);
    assertNotNull(containerItem);
    assertNotNull(symFreightType);
    SymPutawayMessage result =
        SymboticUtils.createPutawayAddMessage(container, containerItem, symFreightType);
    assertEquals("DA", result.getFreightType());
    assertEquals("ROUTING", result.getLabelType());
  }

  @Test
  public void testCreatePutawayAddMessage_nullParameters() {
    ContainerItem containerItem = null;
    Container container = null;
    SymFreightType symFreightType = null;
    if (containerItem != null) {
      SymboticUtils.createPutawayAddMessage(
          container, containerItem, String.valueOf(symFreightType));
    }
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_validSymPutawayEligibility_SSTK()
      throws ReceivingException {
    Container container = mock(Container.class);
    ContainerItem containerItem = mock(ContainerItem.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(container.getContainerItems()).thenReturn(Arrays.asList(containerItem));
    when(containerItem.getInboundChannelMethod()).thenReturn("SSTK");
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(containerItem);
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders);
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_validSymPutawayEligibility_DA()
      throws ReceivingException {
    Container container = mock(Container.class);
    ContainerItem containerItem = mock(ContainerItem.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(container.getContainerItems()).thenReturn(Arrays.asList(containerItem));
    when(containerItem.getInboundChannelMethod()).thenReturn("DA");
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(containerItem);
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders);
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_invalidSymPutawayEligibility()
      throws ReceivingException {
    Container container = mock(Container.class);
    ContainerItem containerItem = mock(ContainerItem.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(container.getContainerItems()).thenReturn(Arrays.asList(containerItem));
    when(containerItem.getInboundChannelMethod()).thenReturn("Invalid");
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders);
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_validContainer() throws ReceivingException {
    Container container = mock(Container.class);
    ContainerItem containerItem = mock(ContainerItem.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders);
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_emptyTrackingIds() throws ReceivingException {
    List<String> trackingIds = new ArrayList<>();
    HttpHeaders httpHeaders = new HttpHeaders();
    symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders);
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_nullTrackingIds() throws ReceivingException {
    List<String> trackingIds = null;
    HttpHeaders httpHeaders = new HttpHeaders();
    Exception exception =
        assertThrows(
            NullPointerException.class,
            () -> {
              symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(
                  trackingIds, httpHeaders);
            });
    assertNotNull(exception);
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_SSTK() throws ReceivingException {
    ContainerItem containerItem = mock(ContainerItem.class);
    Container container = mock(Container.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(containerItem);
    when(containerItem.getInboundChannelMethod()).thenReturn("SSTK");
    when(containerItem.getAsrsAlignment()).thenReturn("validAlignment");
    when(containerItem.getSlotType()).thenReturn("validSlotType");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(Arrays.asList("validAlignment"));
    assertDoesNotThrow(
        () ->
            symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders));
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_SSTK_invalidValidSymPutawayParameters()
      throws ReceivingException {
    ContainerItem containerItem = mock(ContainerItem.class);
    Container container = mock(Container.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(containerItem);
    when(containerItem.getInboundChannelMethod()).thenReturn("SSTK");
    when(containerItem.getAsrsAlignment()).thenReturn("invalidAlignment");
    when(containerItem.getSlotType()).thenReturn("invalidSlotType");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(Arrays.asList("invalidAlignment"));
    assertDoesNotThrow(
        () ->
            symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders));
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_DA_invalidValidSymPutawayParameters()
      throws ReceivingException {
    ContainerItem containerItem = mock(ContainerItem.class);
    Container container = mock(Container.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(containerItem);
    when(containerItem.getAsrsAlignment()).thenReturn("invalidAlignment");
    when(containerItem.getSlotType()).thenReturn("invalidSlotType");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(Arrays.asList("invalidAlignment"));
    when(containerItem.getInboundChannelMethod()).thenReturn("DA");
    assertDoesNotThrow(
        () ->
            symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders));
  }

  @Test
  public void testPublishPutawayAddMessageToKafka_DA() throws ReceivingException {
    ContainerItem containerItem = mock(ContainerItem.class);
    Container container = mock(Container.class);
    List<String> trackingIds = Arrays.asList(trackingId);
    HttpHeaders httpHeaders = new HttpHeaders();
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(container);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(containerItem);
    when(containerItem.getAsrsAlignment()).thenReturn("validAlignment");
    when(containerItem.getSlotType()).thenReturn("validSlotType");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(Arrays.asList("validAlignment"));
    when(containerItem.getInboundChannelMethod()).thenReturn("DA");
    assertDoesNotThrow(
        () ->
            symboticPutawayPublishHelper.publishPutawayAddMessageToKafka(trackingIds, httpHeaders));
  }

  @Test
  public void testPublishSymPutawayDeleteMessageHappyPath() {
    Mockito.doNothing()
        .when(kafkaSymPutawayMessagePublisher)
        .publish(any(SymPutawayMessage.class), anyMap());
    symboticPutawayPublishHelper.publishSymPutawayUpdateOrDeleteMessage(
        trackingId,
        mockContainer(),
        ReceivingConstants.PUTAWAY_DELETE_ACTION,
        backoutQty,
        new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(1))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishSymPutawayUpdateMessageHappyPath() {
    Mockito.doNothing()
        .when(kafkaSymPutawayMessagePublisher)
        .publish(any(SymPutawayMessage.class), anyMap());
    symboticPutawayPublishHelper.publishSymPutawayUpdateOrDeleteMessage(
        trackingId,
        mockContainer(),
        ReceivingConstants.PUTAWAY_UPDATE_ACTION,
        palletAdjustmentQty,
        new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(1))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishSymPutawayUpdateInvalidMessageType() {
    symboticPutawayPublishHelper.publishSymPutawayUpdateOrDeleteMessage(
        trackingId,
        mockContainer(),
        ReceivingConstants.PUTAWAY_ADD_ACTION,
        palletAdjustmentQty,
        new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(0))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishPutawayAddMessageHappyPathDA() {
    Mockito.doNothing()
        .when(kafkaSymPutawayMessagePublisher)
        .publish(any(SymPutawayMessage.class), anyMap());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(mockContainer());
    symboticPutawayPublishHelper.publishPutawayAddMessage(
        mockReceivedContainer(),
        getMockDeliveryDocument(),
        mockInstruction(),
        SymFreightType.DA,
        new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(1))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishPutawayAddMessageAfterSymConnectionHappyPathDA() {
    Mockito.doNothing()
        .when(kafkaSymPutawayMessagePublisher)
        .publish(any(SymPutawayMessage.class), anyMap());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(mockContainer());
    symboticPutawayPublishHelper.publishPutawayAddMessage(
        mockReceivedContainer(),
        getMockDeliveryDocument(),
        mockInstruction(),
        SymFreightType.DA,
        new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(1))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishPutawayAddMessageHappyPathSSTK() {
    Mockito.doNothing()
        .when(kafkaSymPutawayMessagePublisher)
        .publish(any(SymPutawayMessage.class), anyMap());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(mockContainer());
    symboticPutawayPublishHelper.publishPutawayAddMessage(
        mockReceivedContainer(),
        getMockDeliveryDocument(),
        mockInstruction(),
        SymFreightType.SSTK,
        new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(1))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishPutaway_invalid_asrs_Alignment() {
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    ContainerItem containerItem = mockContainer();
    containerItem.setAsrsAlignment(ReceivingConstants.PTL_ASRS_VALUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(containerItem);
    symboticPutawayPublishHelper.publishPutawayAddMessage(
        mockReceivedContainer(),
        getMockDeliveryDocument(),
        mockInstruction(),
        SymFreightType.SSTK,
        new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(0))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  @Test
  public void testPublishPutawayAddMessageException() {
    Mockito.doThrow(ReceivingInternalException.class)
        .when(kafkaSymPutawayMessagePublisher)
        .publish(any(SymPutawayMessage.class), anyMap());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                anyString(), anyString(), anyInt()))
        .thenReturn(mockContainer());
    symboticPutawayPublishHelper.publishPutawayAddMessage(
        mockReceivedContainer(),
        getMockDeliveryDocument(),
        mockInstruction(),
        SymFreightType.SSTK,
        new HttpHeaders());
    verify(kafkaSymPutawayMessagePublisher, times(1))
        .publish(any(SymPutawayMessage.class), anyMap());
  }

  private ContainerItem mockContainer() {
    ContainerItem containerItem = MockContainer.getMockContainerItem().get(0);
    containerItem.setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    containerItem.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    return containerItem;
  }

  private ReceivedContainer mockReceivedContainer() {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    Destination destination = new Destination();
    destination.setSlot("A0001");
    destination.setSlot_size(3);
    destination.setSlot("ADS");
    destination.setZone("AZ");
    receivedContainer.setPoNumber(poNumber);
    receivedContainer.setPoLine(1);
    receivedContainer.setLabelTrackingId(trackingId);
    receivedContainer.setDestinations(Collections.singletonList(destination));
    return receivedContainer;
  }

  private DeliveryDocument getMockDeliveryDocument() {
    DeliveryDocument deliveryDocument = MockInstruction.getDeliveryDocuments().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    return deliveryDocument;
  }

  private Instruction mockInstruction() {
    Instruction instruction = MockInstruction.getCompleteInstruction();
    ContainerDetails containerDetails = new ContainerDetails();
    Content content = new Content();
    content.setItemNbr(12345L);
    containerDetails.setContents(Collections.singletonList(content));
    instruction.setChildContainers(Collections.singletonList(containerDetails));
    return instruction;
  }
}
