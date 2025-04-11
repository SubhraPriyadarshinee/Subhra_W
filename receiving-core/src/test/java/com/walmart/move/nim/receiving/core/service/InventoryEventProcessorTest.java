package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_RIP_INVENTORY_ADJUSTMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.mock.data.MockReceipt;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockInventoryAdjustmentEvent;
import java.util.Arrays;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class InventoryEventProcessorTest extends ReceivingTestBase {

  @InjectMocks private InventoryEventProcessor inventoryEventProcessor;

  @Spy private InventoryAdjustmentTO messageData;

  @Spy private ContainerService containerService;

  @Mock private ReceiptService receiptService;

  @Mock private OSDRRecordCountAggregator osdrRecordCountAggregator;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Captor ArgumentCaptor<String> updatedContainerTrackingId;

  @Captor ArgumentCaptor<Integer> adjustQty;

  @Captor ArgumentCaptor<String> newContainerTrackingId;

  private JsonParser parser = new JsonParser();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(containerService, "receiptService", receiptService);
    ReflectionTestUtils.setField(
        containerService, "osdrRecordCountAggregator", osdrRecordCountAggregator);
  }

  @AfterMethod
  public void cleanup() {
    reset(messageData);
    reset(containerService);
    reset(receiptService);
    reset(osdrRecordCountAggregator);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testInventoryAdjustmentWithDamageEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser.parse(MockInventoryAdjustmentEvent.VALID_DAMAGE_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), Mockito.anyInt(), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithVdgEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.VALID_VDM_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithConcealedShortageOrOverageEvent()
      throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_CONCEALED_SHORTAGE_OVERAGE_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithValidEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.VALID_VTR_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing().when(containerService).backoutContainer(anyString(), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1)).backoutContainer(anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithInvalidEvent() throws ReceivingException {

    when(messageData.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.INVALID_VTR_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(0)).backoutContainer(anyString(), any(HttpHeaders.class));
  }

  private Receipt getMockReceipt() {
    Receipt receipt = new Receipt();
    receipt.setPurchaseReferenceNumber("2323234232");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setDeliveryNumber(3232323L);
    receipt.setQuantity(43);
    return receipt;
  }

  @Test
  public void testInventoryAdjustmentWithRIPNegativeDamageEventWithFeatureFlagOff()
      throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_RIP_NEGATIVE_DM_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT))
        .thenReturn(false);
    doNothing()
        .when(containerService)
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);
  }

  @Test
  public void testInventoryAdjustmentWithRIPNegativeDamageEventWithPositiveRIPQty()
      throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_RIP_NEGATIVE_DM_QTT_POSITIVE_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT))
        .thenReturn(true);
    doNothing()
        .when(containerService)
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT);
  }

  @Test
  public void testInventoryAdjustmentWithRIPNegativeDamageEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_RIP_NEGATIVE_DM_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT))
        .thenReturn(true);
    Receipt optionalReceipt = MockReceipt.getOSDRMasterReceipt();
    optionalReceipt.setId(76834L);
    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.of(optionalReceipt));
    Receipt receipt = getMockReceipt();
    when(receiptService.saveAll(Arrays.asList(new Receipt()))).thenReturn(Arrays.asList(receipt));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processRIPNegativeDamagedAdjustment(
            anyString(),
            anyLong(),
            anyInt(),
            any(JsonObject.class),
            any(JsonObject.class),
            anyString());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT);
  }

  @Test
  public void testInventoryAdjustmentWithRIPNegativeConcealedShortageEvent()
      throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(
                    MockInventoryAdjustmentEvent
                        .VALID_RIP_NEGATIVE_CONCEALED_SHORTAGE_OVERAGE_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT))
        .thenReturn(true);
    Receipt optionalReceipt = MockReceipt.getOSDRMasterReceipt();
    optionalReceipt.setId(76834L);
    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.of(optionalReceipt));
    Receipt receipt = getMockReceipt();
    when(receiptService.saveAll(Arrays.asList(new Receipt()))).thenReturn(Arrays.asList(receipt));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processRIPNegativeDamagedAdjustment(
            anyString(),
            anyLong(),
            anyInt(),
            any(JsonObject.class),
            any(JsonObject.class),
            anyString());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT);
  }

  @Test
  public void testInventoryAdjustmentWithRIPNegativeDamageUOMNullEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_RIP_NEGATIVE_DM_UOM_NULL_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT))
        .thenReturn(true);
    Receipt optionalReceipt = MockReceipt.getOSDRMasterReceipt();
    optionalReceipt.setId(76834L);
    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.of(optionalReceipt));
    Receipt receipt = getMockReceipt();
    when(receiptService.saveAll(Arrays.asList(new Receipt()))).thenReturn(Arrays.asList(receipt));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processRIPNegativeDamagedAdjustment(
            anyString(),
            anyLong(),
            anyInt(),
            any(JsonObject.class),
            any(JsonObject.class),
            anyString());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT);
  }

  @Test
  public void testInventoryAdjustmentWithRIPNegativeDamageContainerTypeNullEvent()
      throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_RIP_NEGATIVE_DM_CONTAINER_TYPE_NULL_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT))
        .thenReturn(true);
    doNothing()
        .when(containerService)
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT);
  }

  @Test
  public void testInventoryAdjustmentWithRIPNegativeConcealedShortageContainerTypeNullEvent()
      throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(
                    MockInventoryAdjustmentEvent
                        .VALID_RIP_NEGATIVE_CONCEALED_SHORTAGE_OVERAGE_CONTAINER_TYPE_NULL_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT))
        .thenReturn(true);
    doNothing()
        .when(containerService)
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT);
  }

  @Test
  public void testInventoryAdjustmentWithRIPStatusInValidEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_RIP_STATUS_INVALID_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT))
        .thenReturn(true);
    doNothing()
        .when(containerService)
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT);
  }

  @Test
  public void testInventoryAdjustmentWithRIPQtyMissingEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_RIP_QTY_MISSING_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT))
        .thenReturn(true);
    doNothing()
        .when(containerService)
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT);
  }

  @Test
  public void testInventoryAdjustmentWithRIPNegativeDamageOSDREvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_RIP_NEGATIVE_DM_EVENT)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT))
        .thenReturn(true);
    when(osdrRecordCountAggregator.findOsdrMasterReceipt(anyLong(), anyString(), anyInt()))
        .thenReturn(Optional.empty());
    Receipt receipt = getMockReceipt();
    when(receiptService.saveAll(Arrays.asList(new Receipt()))).thenReturn(Arrays.asList(receipt));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processRIPNegativeDamagedAdjustment(
            anyString(),
            anyLong(),
            anyInt(),
            any(JsonObject.class),
            any(JsonObject.class),
            anyString());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(ENABLE_RIP_INVENTORY_ADJUSTMENT);
  }
}
