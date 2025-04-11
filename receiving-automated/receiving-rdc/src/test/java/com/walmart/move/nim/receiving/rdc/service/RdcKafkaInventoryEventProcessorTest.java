package com.walmart.move.nim.receiving.rdc.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockInventoryAdjustmentEvent;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Instant;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcKafkaInventoryEventProcessorTest {

  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private RdcKafkaInventoryEventProcessor rdcKafkaInventoryEventProcessor;
  @Spy private InventoryAdjustmentTO inventoryAdjustmentTO;
  @Mock private AppConfig appConfig;
  @Mock private RdcCancelContainerProcessor rdcCancelContainerProcessor;

  private JsonParser parser = new JsonParser();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32679);
  }

  @AfterMethod
  public void cleanup() {
    reset(
        inventoryAdjustmentTO,
        containerAdjustmentHelper,
        rdcInstructionUtils,
        rdcContainerUtils,
        containerPersisterService,
        tenantSpecificConfigReader,
        receiptService);
  }

  @Test
  public void testKafkaInventoryAdjustmentWithValidVTREventAndEmptyHeaders()
      throws ReceivingException {
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.VALID_VTR_EVENT).getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(null);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(getMockContainer());
    doNothing()
        .when(rdcContainerUtils)
        .backoutContainer(any(Container.class), any(HttpHeaders.class));

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(0))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(rdcContainerUtils, times(0))
        .backoutContainer(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void testKafkaInventoryAdjustmentWithValidVTREvent() throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    httpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "");
    Container container = getMockContainer();
    container.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.VALID_VTR_EVENT).getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(httpHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    doNothing()
        .when(rdcContainerUtils)
        .backoutContainer(any(Container.class), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS,
            false))
        .thenReturn(false);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(rdcContainerUtils, times(1))
        .backoutContainer(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void testKafkaInventoryAdjustmentDoNothingWhenValidVTREventWithSourceAsReceiving()
      throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    httpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.VALID_VTR_EVENT).getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(httpHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(getMockContainer());
    doNothing()
        .when(rdcContainerUtils)
        .backoutContainer(any(Container.class), any(HttpHeaders.class));

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(0))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(rdcContainerUtils, times(0))
        .backoutContainer(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void testKafkaInventoryAdjustmentMessageWithInValidReasonCode() throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    httpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "");
    Container mockContainer = getMockContainer();
    mockContainer.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.INVALID_VTR_EVENT).getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(httpHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    doNothing()
        .when(rdcContainerUtils)
        .backoutContainer(any(Container.class), any(HttpHeaders.class));
    doNothing()
        .when(rdcContainerUtils)
        .applyReceivingCorrections(any(Container.class), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS,
            false))
        .thenReturn(false);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(rdcContainerUtils, times(0))
        .backoutContainer(any(Container.class), any(HttpHeaders.class));
    verify(rdcContainerUtils, times(0))
        .applyReceivingCorrections(any(Container.class), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void testKafkaInventoryAdjustmentWithValidReceivingCorrectionEvent()
      throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    httpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "");
    Container mockContainer = getMockContainer();
    mockContainer.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_VALID_RECEIVING_CORRECTION_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(httpHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    doNothing()
        .when(rdcContainerUtils)
        .applyReceivingCorrections(any(Container.class), anyInt(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS,
            false))
        .thenReturn(false);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(rdcContainerUtils, times(1))
        .applyReceivingCorrections(any(Container.class), anyInt(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            eq(LabelAction.CORRECTION),
            any(HttpHeaders.class));
  }

  @Test
  public void testKafkaInventoryAdjustmentWithValidReceivingCorrectionEvent_DA()
      throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    httpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "");
    Container container = getMockContainer();
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    container
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_VALID_RECEIVING_CORRECTION_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(httpHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    doNothing()
        .when(rdcContainerUtils)
        .applyReceivingCorrections(any(Container.class), anyInt(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS,
            false))
        .thenReturn(false);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(rdcContainerUtils, times(1))
        .applyReceivingCorrections(any(Container.class), anyInt(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            eq(LabelAction.DA_BACKOUT),
            any(HttpHeaders.class));
  }

  @Test
  public void
      testKafkaInventoryAdjustmentDoNothingWhenValidReceivingCorrectionEventWithSourceAsReceiving()
          throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_VALID_RECEIVING_CORRECTION_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(httpHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(getMockContainer());
    doNothing()
        .when(rdcContainerUtils)
        .applyReceivingCorrections(any(Container.class), anyInt(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(0))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(rdcContainerUtils, times(0))
        .applyReceivingCorrections(any(Container.class), anyInt(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0))
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testKafkaValidInventoryMovedEvent() throws ReceivingException {
    TenantContext.setFacilityNum(32679);
    Container mockContainer = getMockContainer();
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    Receipt masterReceipt = getOSDRMasterReceipt();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_VALID_RECEIVING_CORRECTION_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    when(receiptService.updateOrderFilledQuantityInReceipts(mockContainer))
        .thenReturn(masterReceipt);
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(masterReceipt, mockContainer);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(ReceivingConstants.STATUS_PUTAWAY_COMPLETE, mockContainer.getContainerStatus());
    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        MockHttpHeaders.getMoveEventInventoryHeaders()
            .getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  /**
   * Commented this code for temp purpose
   *
   * @throws ReceivingException
   */
  //  @Test(
  //          expectedExceptions = ReceivingException.class,
  //          expectedExceptionsMessageRegExp = ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG)
  @Test
  public void testKafkaInventoryMovedEventInvalidContainer() throws ReceivingException {
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_VALID_RECEIVING_CORRECTION_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(null);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        MockHttpHeaders.getMoveEventInventoryHeaders()
            .getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
  }

  @Test
  public void testKafkaInventoryMovedEventInvalidHeaders() throws ReceivingException {
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_VALID_RECEIVING_CORRECTION_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(0))
        .getContainerWithChildContainersExcludingChildContents(anyString());
  }

  @Test
  public void testKafkaInventoryEventWithItemListEmpty() throws ReceivingException {
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.INVALID_INVENTORY_EVENT_ITEMLIST_EMPTY)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(0))
        .getContainerWithChildContainersExcludingChildContents(anyString());
  }

  @Test
  public void testKafkaInventoryMovedEventNotMatchingLocationNameWithSlot()
      throws ReceivingException {
    Container mockContainer = getMockContainer();
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    Map<String, String> mockDestination = new HashMap<>();
    mockDestination.put(ReceivingConstants.SLOT, "A002");
    mockContainer.setDestination(mockDestination);
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_VALID_RECEIVING_CORRECTION_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(ReceivingConstants.STATUS_COMPLETE, mockContainer.getContainerStatus());
    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        MockHttpHeaders.getMoveEventInventoryHeaders()
            .getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(receiptService, times(0)).updateOrderFilledQuantityInReceipts(mockContainer);
    verify(containerAdjustmentHelper, times(0))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  /**
   * Commented this code for temp purpose
   *
   * @throws ReceivingException
   */
  //  @Test(expectedExceptions = ReceivingException.class)
  @Test
  public void testKafkaInventoryMovedEventForDaNonAtlasOrSSTKOutboundContainers_IgnoreUpdates()
      throws ReceivingException {
    Container mockContainer = getMockNonAtlasDAContainer();
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(
                    MockInventoryAdjustmentEvent
                        .NON_ATLAS_DA_CONTAINER_SORTER_DIVERT_OR_OUTBOUND_SSTK_SORTER_DIVERT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(null);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(receiptService, times(0)).updateOrderFilledQuantityInReceipts(mockContainer);
    verify(containerAdjustmentHelper, times(0))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  @Test
  public void testKafkaInventoryMovedEventForAtlasDAContainers_SorterDivertCompleted()
      throws ReceivingException {
    Container mockContainer = getMockAtlasDAContainer();
    Receipt masterReceipt = getOSDRMasterReceipt();
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.ATLAS_DA_CONTAINER_SORTER_DIVERT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    when(receiptService.updateOrderFilledQuantityInReceipts(mockContainer))
        .thenReturn(masterReceipt);
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(masterReceipt, mockContainer);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_DIVERT_INFO,
            false))
        .thenReturn(true);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        MockHttpHeaders.getMoveEventInventoryHeaders()
            .getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(receiptService, times(1)).updateOrderFilledQuantityInReceipts(mockContainer);
    verify(containerAdjustmentHelper, times(1))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  @Test
  public void
      testKafkaInventoryMovedEventForAtlasDAContainers_SorterDivertCompleted_UpdateContainerStatus_DoNotAdjustOrderFilledQtyInReceipts()
          throws ReceivingException {
    Container mockContainer = getMockAtlasDAContainer();
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.ATLAS_DA_CONTAINER_SORTER_DIVERT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_DIVERT_INFO,
            false))
        .thenReturn(true);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        MockHttpHeaders.getMoveEventInventoryHeaders()
            .getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(receiptService, times(0)).updateOrderFilledQuantityInReceipts(mockContainer);
    verify(containerAdjustmentHelper, times(0))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
  }

  @Test
  public void
      testKafkaInventoryMovedEventForAtlasDAContainers_IgnoreSorterDivertForSYMASRSAlignedContainers()
          throws ReceivingException {
    Container mockContainer = getMockAtlasDAContainerWithASRSAligned();
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.ATLAS_DA_CONTAINER_SORTER_DIVERT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_DIVERT_INFO,
            false))
        .thenReturn(true);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        MockHttpHeaders.getMoveEventInventoryHeaders()
            .getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(receiptService, times(0)).updateOrderFilledQuantityInReceipts(mockContainer);
    verify(containerAdjustmentHelper, times(0))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  @Test
  public void testKafkaInventoryMovedEventForAtlasDAContainers_EIPickEventUpdates()
      throws ReceivingException {
    Container mockContainer = getMockAtlasDAContainerWithAllocatedStatus();
    HttpHeaders mockHeaders = MockHttpHeaders.getPickedEventHeaders();
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.ATLAS_DA_CONTAINER_UPDATE_PICK_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    doNothing()
        .when(rdcContainerUtils)
        .publishContainerToEI(mockContainer, ReceivingConstants.EI_DC_PICKED_EVENT);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_DIVERT_INFO,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS,
            false))
        .thenReturn(true);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_PICKED,
        MockHttpHeaders.getPickedEventHeaders().getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(rdcContainerUtils, times(1))
        .publishContainerToEI(mockContainer, ReceivingConstants.EI_DC_PICKED_EVENT);
  }

  @Test
  public void
      testKafkaInventoryMovedEventForAtlasDAContainers_BreakPackInnerContainer_PostEIPickEvents_PutawayCompleteStatus()
          throws ReceivingException {
    Receipt masterReceipt = getOSDRMasterReceipt();
    Container mockContainer = getMockAtlasDABreakPackContainer();
    HttpHeaders mockHeaders = MockHttpHeaders.getPickedEventHeaders();
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.ATLAS_DA_BREAK_PACK_CONTAINER_INNER_PICK_PICKED)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    doNothing()
        .when(rdcContainerUtils)
        .publishContainerToEI(mockContainer, ReceivingConstants.EI_DC_PICKED_EVENT);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_DIVERT_INFO,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS,
            false))
        .thenReturn(true);
    when(receiptService.updateOrderFilledQuantityInReceipts(mockContainer))
        .thenReturn(masterReceipt);
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(masterReceipt, mockContainer);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_PICKED,
        MockHttpHeaders.getPickedEventHeaders().getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(2))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(rdcContainerUtils, times(1))
        .publishContainerToEI(mockContainer, ReceivingConstants.EI_DC_PICKED_EVENT);
    verify(receiptService, times(1)).updateOrderFilledQuantityInReceipts(mockContainer);
    verify(containerAdjustmentHelper, times(1))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  @Test
  public void testKafkaInventoryMovedEventLegacyDAContainers_SorterDivertCompleted()
      throws ReceivingException {
    Container mockContainer = getMockNonAtlasDAContainer();
    Receipt masterReceipt = getOSDRMasterReceipt();
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.LEGACY_DA_CONTAINER_SORTER_DIVERT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        MockHttpHeaders.getMoveEventInventoryHeaders()
            .getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(0))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(receiptService, times(0)).updateOrderFilledQuantityInReceipts(mockContainer);
    verify(containerAdjustmentHelper, times(0))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  @Test
  public void testKafkaInventoryMovedEventContainerStatusAlreadyCompleted()
      throws ReceivingException {
    Container mockContainer = getMockContainer();
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_VALID_RECEIVING_CORRECTION_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(ReceivingConstants.STATUS_PUTAWAY_COMPLETE, mockContainer.getContainerStatus());
    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        MockHttpHeaders.getMoveEventInventoryHeaders()
            .getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(receiptService, times(0)).updateOrderFilledQuantityInReceipts(mockContainer);
    verify(containerAdjustmentHelper, times(0))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  @Test
  public void testKafkaInvalidInventoryMovedEvent() throws ReceivingException {
    Container mockContainer = getMockContainer();
    mockContainer.setInventoryStatus(InventoryStatus.PICKED.name());
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.INVENTORY_EVENT, "");
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.INVALID_VTR_EVENT).getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS,
            false))
        .thenReturn(false);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertNotEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        mockHeaders.getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
  }

  @Test
  public void testKafkaInventoryAdjustmentWithValidWarehouseDamageEvent()
      throws ReceivingException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    httpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "");
    Container container = getMockContainer();
    container.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_VALID_WAREHOUSE_DAMAGE_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(httpHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    doNothing()
        .when(rdcContainerUtils)
        .processWarehouseDamageAdjustments(any(Container.class), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS,
            false))
        .thenReturn(false);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(rdcContainerUtils, times(1))
        .processWarehouseDamageAdjustments(any(Container.class), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void testKafkaValidInventoryLoadedEvent_NonConQtyReceivedCases()
      throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setInventoryStatus(InventoryStatus.PICKED.name());
    mockContainer.setContainerType(ContainerType.CASE.getText());
    mockContainer.setTrackingId("00232323232323");
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.INVENTORY_EVENT, "loaded");
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_INVENTORY_LOADED_EVENT_DA_NON_CON_QTY_RCVD)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    when(containerPersisterService.saveContainer(any(Container.class))).thenReturn(mockContainer);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(mockContainer.getContainerStatus(), ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    assertEquals(
        mockHeaders.getFirst(ReceivingConstants.INVENTORY_EVENT),
        ReceivingConstants.INVENTORY_EVENT_LOADED);
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
  }

  @Test
  public void testKafkaValidInventoryLoadedEvent_NonConPallet() throws ReceivingException {
    Container mockContainer = new Container();
    mockContainer.setInventoryStatus(InventoryStatus.PICKED.name());
    mockContainer.setContainerType(ContainerType.PALLET.getText());
    mockContainer.setTrackingId("b06020323232323232323");
    mockContainer.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    Container putawayCompletedContainer = new Container();
    putawayCompletedContainer.setTrackingId("b06020323232323232323");
    putawayCompletedContainer.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.INVENTORY_EVENT, "loaded");
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(
                    MockInventoryAdjustmentEvent.RDC_INVENTORY_LOADED_EVENT_DA_NON_CON_PALLET_RCVD)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    when(containerPersisterService.saveContainer(any(Container.class))).thenReturn(mockContainer);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(mockContainer.getContainerStatus(), ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    assertEquals(
        mockHeaders.getFirst(ReceivingConstants.INVENTORY_EVENT),
        ReceivingConstants.INVENTORY_EVENT_LOADED);
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
  }

  @Test
  public void
      testKafkaInventoryMovedEventForAtlasDAContainers_SorterDivertCompleted_UpdateContainerStatus_ForDAFreight()
          throws ReceivingException {
    Container mockContainer = getMockAtlasDAContainerWithPalletInAllocatedStatus();
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.ATLAS_DA_CONTAINER_ALLOCATED_STATUS)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    when(containerPersisterService.saveContainer(any(Container.class))).thenReturn(mockContainer);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_DIVERT_INFO,
            false))
        .thenReturn(true);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        MockHttpHeaders.getMoveEventInventoryHeaders()
            .getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(receiptService, times(0)).updateOrderFilledQuantityInReceipts(mockContainer);
    verify(containerAdjustmentHelper, times(0))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
  }

  @Test
  public void testKafkaValidInventorySorterDivertEvent() throws ReceivingException {
    Container mockContainer = getMockContainer();
    mockContainer.getContainerItems().get(0).setInboundChannelMethod("Mock");
    mockContainer.setSsccNumber("12345");
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setInboundChannelMethod(RdcConstants.DSDC_CHANNEL_METHODS_FOR_RDC);
    containerItems.add(containerItem);
    mockContainer.setContainerItems(containerItems);
    Set<Container> childContainers = new HashSet<>();
    Container c1 = new Container();
    Container c2 = new Container();
    c1.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    c2.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    childContainers.add(c1);
    childContainers.add(c2);
    mockContainer.setChildContainers(childContainers);
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    fetchDataForDSDC(mockContainer, mockHeaders);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);
    assertEquals(
        ReceivingConstants.STATUS_PUTAWAY_COMPLETE,
        mockContainer.getChildContainers().stream().findFirst().get().getContainerStatus());
    verify(containerPersisterService, times(1)).saveContainers(anyList());
  }

  @Test
  public void testKafkaValidInventorySorterDivertEventWithChildContainersAlreadyPutawayComplete()
      throws ReceivingException {
    Container mockContainer = getMockContainer();
    mockContainer.getContainerItems().get(0).setInboundChannelMethod("Mock");
    mockContainer.setSsccNumber("12345");
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setInboundChannelMethod(RdcConstants.DSDC_CHANNEL_METHODS_FOR_RDC);
    containerItems.add(containerItem);
    mockContainer.setContainerItems(containerItems);
    Set<Container> childContainers = new HashSet<>();
    Container c1 = new Container();
    Container c2 = new Container();
    c1.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    c2.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    childContainers.add(c1);
    childContainers.add(c2);
    mockContainer.setChildContainers(childContainers);
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    fetchDataForDSDC(mockContainer, mockHeaders);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);
    assertEquals(
        ReceivingConstants.STATUS_PUTAWAY_COMPLETE,
        mockContainer.getChildContainers().stream().findFirst().get().getContainerStatus());
    verify(containerPersisterService, times(0)).saveContainers(anyList());
  }

  @Test
  public void testKafkaValidInventorySorterDivertEventWithoutDSDCFreight()
      throws ReceivingException {
    Container mockContainer = getMockContainer();
    mockContainer.getContainerItems().get(0).setInboundChannelMethod("Mock");
    mockContainer.setSsccNumber("12345");
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    fetchDataForDSDC(mockContainer, mockHeaders);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);
    assertNull(mockContainer.getChildContainers());
  }

  @Test
  public void testKafkaValidInventorySorterDivertEventWithNoChildContainers()
      throws ReceivingException {
    Container mockContainer = getMockContainer();
    mockContainer.getContainerItems().get(0).setInboundChannelMethod("Mock");
    mockContainer.setSsccNumber("12345");
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setInboundChannelMethod(RdcConstants.DSDC_CHANNEL_METHODS_FOR_RDC);
    containerItems.add(containerItem);
    mockContainer.setContainerItems(containerItems);
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    fetchDataForDSDC(mockContainer, mockHeaders);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);
    assertNull(mockContainer.getChildContainers());
  }

  @Test
  public void testKafkaValidInventorySorterDivertEventNotDSDC() throws ReceivingException {
    Container mockContainer = getMockContainer();
    mockContainer.getContainerItems().get(0).setInboundChannelMethod("Mock");
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setInboundChannelMethod(RdcConstants.DSDC_CHANNEL_METHODS_FOR_RDC);
    containerItems.add(containerItem);
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockContainer.setContainerItems(containerItems);
    fetchDataForDSDC(mockContainer, mockHeaders);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);
    assertNull(mockContainer.getChildContainers());
  }

  private void fetchDataForDSDC(Container mockContainer, HttpHeaders mockHeaders) {
    mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.INVENTORY_DA_ATLAS_ITEM_CONTAINER_LOCATION_DETAILS, "US");
    Receipt masterReceipt = getOSDRMasterReceipt();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.RDC_DSDC_VALID_RECEIVING_CORRECTION_EVENT)
                .getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    when(receiptService.updateOrderFilledQuantityInReceipts(mockContainer))
        .thenReturn(masterReceipt);
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(masterReceipt, mockContainer);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_DIVERT_INFO,
            false))
        .thenReturn(true);
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(1901L);
    container.setTrackingId("lpn123");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setContainerType(ContainerType.PALLET.getText());
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "A0001");
    container.setDestination(destination);
    container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("lpn123");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(4);
    containerItem.setActualHi(6);
    containerItem.setInboundChannelMethod("SSTKU");

    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }

  private Container getMockNonAtlasDAContainer() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(1901L);
    container.setTrackingId("053000106020021602");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setContainerType(ContainerType.CASE.getText());
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "R8000");
    container.setDestination(destination);
    container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("lpn123");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(4);
    containerItem.setActualHi(6);

    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }

  private Container getMockAtlasDAContainer() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(1901L);
    container.setTrackingId("053000106020021602");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setContainerType(ContainerType.CASE.getText());
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "R8000");
    container.setDestination(destination);
    container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    container.setInventoryStatus(InventoryStatus.PICKED.name());
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("lpn123");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(4);
    containerItem.setActualHi(6);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setAsrsAlignment("MANUAL");

    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }

  private Container getMockAtlasDABreakPackContainer() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(1901L);
    container.setTrackingId("053000106020021602");
    container.setParentTrackingId("a6030302323232323");
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setContainerType(ContainerType.CASE.getText());
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "R8000");
    container.setDestination(destination);
    container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("lpn123");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(4);
    containerItem.setActualHi(6);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setAsrsAlignment("MANUAL");

    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }

  private Container getMockAtlasDAContainerWithASRSAligned() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(1901L);
    container.setTrackingId("053000106020021602");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setContainerType(ContainerType.CASE.getText());
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "R8000");
    container.setDestination(destination);
    container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    container.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("lpn123");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(4);
    containerItem.setActualHi(6);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setAsrsAlignment("SYM2");

    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }

  private Container getMockAtlasDAContainerWithAllocatedStatus() {
    Container container = getMockAtlasDAContainer();
    container.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    return container;
  }

  private Receipt getOSDRMasterReceipt() {
    Receipt receipt = new Receipt();
    receipt.setCreateTs(Date.from(Instant.now()));
    receipt.setQuantity(20);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setDeliveryNumber(21119003L);
    receipt.setPurchaseReferenceNumber("9763140005");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setOsdrMaster(1);
    receipt.setFbProblemQty(0);
    receipt.setFbDamagedQty(0);
    receipt.setFbRejectedQty(0);
    receipt.setFbShortQty(0);
    receipt.setFbOverQty(0);
    receipt.setFbConcealedShortageQty(0);
    receipt.setOrderFilledQuantity(10);
    return receipt;
  }

  private Container getMockAtlasDAContainerWithPalletInAllocatedStatus() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(1901L);
    container.setTrackingId("053000106020021602");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setContainerType(ContainerType.PALLET.getText());
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "R8000");
    container.setDestination(destination);
    container.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    container.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("lpn123");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(4);
    containerItem.setActualHi(6);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setAsrsAlignment("MANUAL");

    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }

  @Test
  public void testKafkaValidInventoryDeleteEvent() throws ReceivingException {
    Container mockContainer = getMockContainer();
    HttpHeaders mockHeaders = MockHttpHeaders.getDeleteEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser.parse(MockInventoryAdjustmentEvent.INVENTORY_DELETE_EVENT).getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    doNothing()
        .when(rdcCancelContainerProcessor)
        .publishInvDeleteEventsToEI(mockContainer, ReceivingConstants.DC_VOID);

    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);
    verify(rdcCancelContainerProcessor, times(1))
        .publishInvDeleteEventsToEI(any(Container.class), anyString());
  }

  @Test
  public void testKafkaInvalidInventoryMovedEventWithEmptyItemList() throws ReceivingException {
    Container mockContainer = getMockContainer();
    mockContainer.setInventoryStatus(InventoryStatus.PICKED.name());
    HttpHeaders mockHeaders = MockHttpHeaders.getMoveEventInventoryHeaders();
    mockHeaders.set(ReceivingConstants.INVENTORY_EVENT, ReceivingConstants.INVENTORY_EVENT_MOVED);
    mockHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "inventory-api");
    when(inventoryAdjustmentTO.getJsonObject())
        .thenReturn(
            parser.parse(MockInventoryAdjustmentEvent.MOVED_EVENT_EMPTY_ITEMS).getAsJsonObject());
    when(inventoryAdjustmentTO.getHttpHeaders()).thenReturn(mockHeaders);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(mockContainer);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS,
            false))
        .thenReturn(false);
    rdcKafkaInventoryEventProcessor.processEvent(inventoryAdjustmentTO);

    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        mockHeaders.getFirst(ReceivingConstants.INVENTORY_EVENT));
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
  }
}
