package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.InventoryItemExceptionPayload;
import com.walmart.move.nim.receiving.core.model.decant.DecantMessagePublishRequest;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.core.service.DecantService;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.service.MixedPalletRejectService;
import com.walmart.move.nim.receiving.mfc.transformer.NGRAsnTransformer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DSDContainerCreatePostProcessorTest extends ReceivingTestBase {

  @InjectMocks private DSDContainerCreatePostProcessor dsdContainerCreatePostProcessor;

  @Mock private ProcessInitiator processInitiator;

  @Mock private InventoryService inventoryService;

  @Mock private MFCManagedConfig mfcManagedConfig;

  @Mock private MixedPalletRejectService mixedPalletRejectService;

  @Mock private NGRAsnTransformer ngrAsnTransformer;

  @Mock private DecantService decantService;

  @Mock private AsyncPersister asyncPersister;

  private Gson gson;

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    TenantContext.setFacilityNum(5504);
    TenantContext.setFacilityCountryCode("US");
    ReflectionTestUtils.setField(mixedPalletRejectService, "decantService", decantService);
    ReflectionTestUtils.setField(mixedPalletRejectService, "asyncPersister", asyncPersister);
    ReflectionTestUtils.setField(mixedPalletRejectService, "mfcManagedConfig", mfcManagedConfig);
    when(mfcManagedConfig.getMixedPalletCurrentState()).thenReturn(MFCConstant.REJECTED);
    when(mfcManagedConfig.getMixedPalletPreviousState()).thenReturn(MFCConstant.PENDING);
    when(mfcManagedConfig.getMixedPalletReasonCode()).thenReturn(MFCConstant.REJECTED);
    when(mfcManagedConfig.getMixedPalletRejectLocation()).thenReturn(MFCConstant.UNKNOWN);
    when(mfcManagedConfig.getMixedPalletRequestOriginator())
        .thenReturn(MFCConstant.ATLAS_INVENTORY);
    when(mfcManagedConfig.getMixedPalletRemovalEvent()).thenReturn(MFCConstant.EVENT_DECANTING);
    when(mfcManagedConfig.getMixedPalletRejectMultiplier()).thenReturn(1);
    gson =
        new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            .create();
    ReflectionTestUtils.setField(mixedPalletRejectService, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(processInitiator);
    Mockito.reset(inventoryService);
    Mockito.reset(mixedPalletRejectService);
    Mockito.reset(ngrAsnTransformer);
    Mockito.reset(decantService);
    Mockito.reset(asyncPersister);
  }

  @Test
  public void testAuditHandlingFlow() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(10000001L);
    String ngrPackPayload =
        MFCTestUtils.readInputFile("src/test/resources/ngrFinalizationEvent/ngrEventMFCItem.json");

    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(inventoryService.performInventoryBulkAdjustmentForItems(any())).thenReturn("Success");
    when(mfcManagedConfig.getDsdDeliveryUnloadCompleteEventThresholdMinutes()).thenReturn(100);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder().payload(ngrPackPayload).key("10000001").build();
    dsdContainerCreatePostProcessor.doExecute(receivingEvent);
    verify(inventoryService, times(0)).performInventoryBulkAdjustmentForItems(any());
    verify(mixedPalletRejectService, never()).processMixedPalletReject(any(), any());
  }

  @Test
  public void testAuditHandlingFlowNgrRejects() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(10000001L);

    ArgumentCaptor<List> inventoryItemExceptionPayloadArgumentCaptor =
        ArgumentCaptor.forClass(List.class);

    String ngrPackPayload =
        MFCTestUtils.readInputFile(
            "src/test/resources/ngrFinalizationEvent/ngrEventMFCItemAuditReject.json");

    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(inventoryService.performInventoryBulkAdjustmentForItems(any())).thenReturn("Success");
    when(mfcManagedConfig.getDsdDeliveryUnloadCompleteEventThresholdMinutes()).thenReturn(100);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder().payload(ngrPackPayload).key("10000001").build();
    dsdContainerCreatePostProcessor.doExecute(receivingEvent);

    verify(inventoryService, times(1))
        .performInventoryBulkAdjustmentForItems(
            inventoryItemExceptionPayloadArgumentCaptor.capture());
    verify(mixedPalletRejectService, never()).processMixedPalletReject(any(), any());

    List<InventoryItemExceptionPayload> exceptionPayloads =
        inventoryItemExceptionPayloadArgumentCaptor.getValue();
    Assert.assertEquals(exceptionPayloads.size(), 1);
    Assert.assertEquals(exceptionPayloads.get(0).getAdjustBy().intValue(), -1);
    Assert.assertEquals(
        exceptionPayloads.get(0).getReasonCode(),
        QuantityType.NGR_REJECT.getInventoryErrorReason());
  }

  @Test
  public void testAuditHandlingFlowNgrShortage() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(10000001L);

    String ngrPackPayload =
        MFCTestUtils.readInputFile(
            "src/test/resources/ngrFinalizationEvent/ngrEventMFCItemAuditShortage.json");

    ArgumentCaptor<List> inventoryItemExceptionPayloadArgumentCaptor =
        ArgumentCaptor.forClass(List.class);
    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(inventoryService.performInventoryBulkAdjustmentForItems(any())).thenReturn("Success");
    when(mfcManagedConfig.getDsdDeliveryUnloadCompleteEventThresholdMinutes()).thenReturn(100);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder().payload(ngrPackPayload).key("10000001").build();
    dsdContainerCreatePostProcessor.doExecute(receivingEvent);

    verify(inventoryService, times(1))
        .performInventoryBulkAdjustmentForItems(
            inventoryItemExceptionPayloadArgumentCaptor.capture());
    verify(mixedPalletRejectService, never()).processMixedPalletReject(any(), any());

    List<InventoryItemExceptionPayload> exceptionPayloads =
        inventoryItemExceptionPayloadArgumentCaptor.getValue();
    Assert.assertEquals(exceptionPayloads.size(), 1);
    Assert.assertEquals(exceptionPayloads.get(0).getAdjustBy().intValue(), -1);
    Assert.assertEquals(
        exceptionPayloads.get(0).getReasonCode(),
        QuantityType.NGR_SHORTAGE.getInventoryErrorReason());
  }

  @Test
  public void testAuditHandlingFlowNgrRejectShortage() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(10000001L);

    ArgumentCaptor<List> inventoryItemExceptionPayloadArgumentCaptor =
        ArgumentCaptor.forClass(List.class);
    String ngrPackPayload =
        MFCTestUtils.readInputFile(
            "src/test/resources/ngrFinalizationEvent/ngrEventMFCItemNgrRejectShortage.json");

    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(inventoryService.performInventoryBulkAdjustmentForItems(any())).thenReturn("Success");
    when(mfcManagedConfig.getDsdDeliveryUnloadCompleteEventThresholdMinutes()).thenReturn(100);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder().payload(ngrPackPayload).key("10000001").build();
    dsdContainerCreatePostProcessor.doExecute(receivingEvent);

    verify(inventoryService, times(1))
        .performInventoryBulkAdjustmentForItems(
            inventoryItemExceptionPayloadArgumentCaptor.capture());
    verify(mixedPalletRejectService, never()).processMixedPalletReject(any(), any());

    List<InventoryItemExceptionPayload> exceptionPayloads =
        inventoryItemExceptionPayloadArgumentCaptor.getValue();
    Assert.assertEquals(exceptionPayloads.size(), 2);
    for (InventoryItemExceptionPayload exceptionPayload : exceptionPayloads) {
      if (QuantityType.NGR_SHORTAGE
          .getInventoryErrorReason()
          .equals(exceptionPayload.getReasonCode())) {
        Assert.assertEquals(exceptionPayload.getAdjustBy().intValue(), -1);
        Assert.assertEquals(exceptionPayload.getCurrentQty().intValue(), 10);
      } else {
        Assert.assertEquals(exceptionPayload.getAdjustBy().intValue(), -2);
        Assert.assertEquals(exceptionPayload.getCurrentQty().intValue(), 12);
      }
    }
  }

  @Test
  public void testAuditHandlingFlowNgrRejectShortageMultipleItems() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(10000001L);

    ArgumentCaptor<List> inventoryItemExceptionPayloadArgumentCaptor =
        ArgumentCaptor.forClass(List.class);
    String ngrPackPayload =
        MFCTestUtils.readInputFile(
            "src/test/resources/ngrFinalizationEvent/ngrEventMFCMultipleItemNgrRejectShortage.json");

    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(inventoryService.performInventoryBulkAdjustmentForItems(any())).thenReturn("Success");
    when(mfcManagedConfig.getDsdDeliveryUnloadCompleteEventThresholdMinutes()).thenReturn(100);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder().payload(ngrPackPayload).key("10000001").build();
    dsdContainerCreatePostProcessor.doExecute(receivingEvent);

    verify(inventoryService, times(1))
        .performInventoryBulkAdjustmentForItems(
            inventoryItemExceptionPayloadArgumentCaptor.capture());
    verify(mixedPalletRejectService, never()).processMixedPalletReject(any(), any());

    List<InventoryItemExceptionPayload> exceptionPayloads =
        inventoryItemExceptionPayloadArgumentCaptor.getValue();
    Assert.assertEquals(exceptionPayloads.size(), 2);
    for (InventoryItemExceptionPayload exceptionPayload : exceptionPayloads) {
      if (QuantityType.NGR_SHORTAGE
          .getInventoryErrorReason()
          .equals(exceptionPayload.getReasonCode())) {
        Assert.assertEquals(exceptionPayload.getAdjustBy().intValue(), -3);
        Assert.assertEquals(exceptionPayload.getItemNumber().longValue(), 662051247l);
        Assert.assertEquals(exceptionPayload.getCurrentQty().intValue(), 9);
      } else {
        Assert.assertEquals(exceptionPayload.getAdjustBy().intValue(), -3);
        Assert.assertEquals(exceptionPayload.getItemNumber().longValue(), 662051247l);
        Assert.assertEquals(exceptionPayload.getCurrentQty().intValue(), 12);
      }
    }
  }

  @Test
  public void testMixedPalletReject_MixedItems() {
    String ngrPackPayload =
        MFCTestUtils.readInputFile(
            "src/test/resources/ngrFinalizationEvent/ngrEventMixedItem.json");
    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(inventoryService.performInventoryBulkAdjustmentForItems(any())).thenReturn("Success");
    when(mfcManagedConfig.getDsdDeliveryUnloadCompleteEventThresholdMinutes()).thenReturn(100);
    doCallRealMethod().when(mixedPalletRejectService).processMixedPalletReject(any(), any());
    when(ngrAsnTransformer.apply(any(), any())).thenCallRealMethod();
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder().payload(ngrPackPayload).key("10000001").build();
    ArgumentCaptor<List<DecantMessagePublishRequest>> decantMessagePublishArgCaptor =
        ArgumentCaptor.forClass(List.class);
    dsdContainerCreatePostProcessor.doExecute(receivingEvent);

    verify(inventoryService, never()).performInventoryBulkAdjustmentForItems(any());
    verify(mixedPalletRejectService, times(1)).processMixedPalletReject(any(), any());
    verify(decantService, times(1)).initiateMessagePublish(decantMessagePublishArgCaptor.capture());
    List<DecantMessagePublishRequest> decantMessagePublish =
        decantMessagePublishArgCaptor.getValue();
    Assert.assertNotNull(decantMessagePublish);
    Assert.assertEquals(decantMessagePublish.size(), 1);
    Map<String, String> headers = decantMessagePublish.get(0).getAdditionalHeaders();
    Assert.assertEquals(headers.get(EVENT_TYPE), MFCConstant.EVENT_DECANTING);
    Assert.assertEquals(headers.get("originatorId"), MFCConstant.ATLAS_INVENTORY);
  }
}
