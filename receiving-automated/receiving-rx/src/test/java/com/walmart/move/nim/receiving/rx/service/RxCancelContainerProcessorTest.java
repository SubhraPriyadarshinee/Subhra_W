package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ENABLE_INV_LABEL_BACKOUT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_ONE_ATLAS_ENABLED;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.CancelContainerRequest;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.SwapContainerRequest;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;
import org.mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxCancelContainerProcessorTest {

  @Mock private ContainerItemRepository containerItemRepository;
  @Mock private RxContainerAdjustmentValidator rxContainerAdjustmentValidator;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ReceiptService receiptService;
  @Mock private NimRdsServiceImpl nimRdsServiceImpl;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private RxSlottingServiceImpl rxSlottingServiceImpl;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock AppConfig appConfig;
  @Mock RxManagedConfig rxManagedConfig;
  @Mock private ContainerService containerService;
  @InjectMocks private RxCancelContainerHelper rxCancelContainerHelper;
  @Spy private RxReceiptsBuilder rxReceiptsBuilder = new RxReceiptsBuilder();
  @Mock private Transformer<Container, ContainerDTO> transformer;

  @InjectMocks private RxCancelContainerProcessor rxCancelContainerProcessor;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private InventoryRestApiClient inventoryRestApiClient;

  @BeforeMethod
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(6001);
    TenantContext.setFacilityCountryCode("us");
    doReturn(true).when(appConfig).isSlotUnlockingEnabled();
    ReflectionTestUtils.setField(
        rxCancelContainerProcessor, "rxCancelContainerHelper", rxCancelContainerHelper);
    doReturn(true).when(rxManagedConfig).isPublishContainersToKafkaEnabled();
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32898);
  }

  private Container createContainer(String parentTrackingId, String trackingId, int quantity) {
    Container container = new Container();
    container.setParentTrackingId(parentTrackingId);
    container.setTrackingId(trackingId);
    container.setDeliveryNumber(12345l);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("MOCK_PURCHASE_REFERENCE_NUMBER");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(quantity);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setItemNumber(12345l);
    container.setContainerItems(Arrays.asList(containerItem));

    return container;
  }

  @Test
  public void testCancelContainers_no_parentExists() throws Exception {

    doReturn(null)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Arrays.asList("PALLET_CONTAINER_TRACKING_ID"));
    doReturn(false).when(rxManagedConfig).isRollbackReceiptsByShipment();

    List<CancelContainerResponse> cancelContainersResponse =
        rxCancelContainerProcessor.cancelContainers(
            cancelContainerRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(cancelContainersResponse);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents("PALLET_CONTAINER_TRACKING_ID");

    assertEquals(
        ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE,
        cancelContainersResponse.get(0).getErrorCode());
    assertEquals(
        ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG,
        cancelContainersResponse.get(0).getErrorMessage());
  }

  @Test
  public void testCancelContainers_lessthan_case() throws Exception {

    doReturn(null)
        .when(rxContainerAdjustmentValidator)
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    doReturn(false).when(rxManagedConfig).isRollbackReceiptsByShipment();

    Container palletContainer = createContainer(null, "PALLET_CONTAINER_TRACKING_ID", 2);

    Set<Container> childContainers = new HashSet<>();
    Container caseContainer1 =
        createContainer("PALLET_CONTAINER_TRACKING_ID", "CASE_CONTAINER_TRACKING_ID1", 1);
    childContainers.add(caseContainer1);

    Container caseContainer2 =
        createContainer("PALLET_CONTAINER_TRACKING_ID", "CASE_CONTAINER_TRACKING_ID2", 1);
    childContainers.add(caseContainer2);

    palletContainer.setChildContainers(childContainers);

    doReturn(palletContainer)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());

    doAnswer(
            new Answer<Set>() {
              public Set answer(InvocationOnMock invocation) {
                String trackingId = (String) invocation.getArgument(0);
                Container whpkContainer =
                    createContainer(trackingId, "WHPK_CONTAINER_TRACKING_ID1", 1);
                Set<Container> whpkContainerSet = new HashSet<>();
                whpkContainerSet.add(whpkContainer);

                return whpkContainerSet;
              }
            })
        .when(containerPersisterService)
        .getContainerDetailsByParentTrackingId(anyString());

    doNothing().when(containerPersisterService).saveContainers(any(List.class));
    doReturn(null).when(containerItemRepository).saveAll(any(List.class));

    doNothing().when(rxSlottingServiceImpl).freeSlot(any(), anyString(), any(HttpHeaders.class));
    doReturn(MockInstruction.getMockNewInstruction())
        .when(instructionPersisterService)
        .getInstructionById(any());

    ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
    doReturn(new Receipt()).when(receiptService).saveReceipt(receiptCaptor.capture());
    doReturn(Arrays.asList(new ContainerItem()))
        .when(containerItemRepository)
        .findByTrackingId(anyString());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Arrays.asList("PALLET_CONTAINER_TRACKING_ID"));

    List<CancelContainerResponse> cancelContainersResponse =
        rxCancelContainerProcessor.cancelContainers(
            cancelContainerRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(cancelContainersResponse);
    assertSame(receiptCaptor.getValue().getQuantity(), -1);
    assertSame(receiptCaptor.getValue().getEachQty(), -2);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents("PALLET_CONTAINER_TRACKING_ID");
    verify(containerPersisterService, times(2)).getContainerDetailsByParentTrackingId(anyString());
    verify(containerPersisterService, times(1)).saveContainers(any(List.class));
    verify(receiptService, times(1)).saveReceipt(any(Receipt.class));
    verify(containerItemRepository, times(1)).saveAll(any(List.class));
    verify(containerItemRepository, times(2)).findByTrackingId(anyString());

    verify(rxSlottingServiceImpl, times(1)).freeSlot(any(), anyString(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(any());
  }

  @Test
  public void testCancelContainers() throws Exception {

    doReturn(null)
        .when(rxContainerAdjustmentValidator)
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    doReturn(false).when(rxManagedConfig).isRollbackReceiptsByShipment();

    Container palletContainer = createContainer(null, "PALLET_CONTAINER_TRACKING_ID", 6);

    Set<Container> childContainers = new HashSet<>();
    Container caseContainer1 =
        createContainer("PALLET_CONTAINER_TRACKING_ID", "CASE_CONTAINER_TRACKING_ID1", 1);
    childContainers.add(caseContainer1);

    Container caseContainer2 =
        createContainer("PALLET_CONTAINER_TRACKING_ID", "CASE_CONTAINER_TRACKING_ID2", 1);
    childContainers.add(caseContainer2);

    palletContainer.setChildContainers(childContainers);

    doReturn(palletContainer)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());

    doAnswer(
            new Answer<Set>() {
              public Set answer(InvocationOnMock invocation) {
                String trackingId = (String) invocation.getArgument(0);
                Container whpkContainer =
                    createContainer(trackingId, "WHPK_CONTAINER_TRACKING_ID1", 1);
                Set<Container> whpkContainerSet = new HashSet<>();
                whpkContainerSet.add(whpkContainer);

                return whpkContainerSet;
              }
            })
        .when(containerPersisterService)
        .getContainerDetailsByParentTrackingId(anyString());

    doNothing().when(containerPersisterService).saveContainers(any(List.class));
    doReturn(null).when(containerItemRepository).saveAll(any(List.class));

    doNothing().when(rxSlottingServiceImpl).freeSlot(any(), anyString(), any(HttpHeaders.class));
    doReturn(MockInstruction.getMockNewInstruction())
        .when(instructionPersisterService)
        .getInstructionById(any());

    ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
    doReturn(new Receipt()).when(receiptService).saveReceipt(receiptCaptor.capture());
    doReturn(Arrays.asList(new ContainerItem()))
        .when(containerItemRepository)
        .findByTrackingId(anyString());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Arrays.asList("PALLET_CONTAINER_TRACKING_ID"));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", IS_DC_ONE_ATLAS_ENABLED);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_INV_LABEL_BACKOUT);

    List<CancelContainerResponse> cancelContainersResponse =
        rxCancelContainerProcessor.cancelContainers(
            cancelContainerRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(cancelContainersResponse);
    assertSame(receiptCaptor.getValue().getQuantity(), -1);
    assertSame(receiptCaptor.getValue().getEachQty(), -6);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents("PALLET_CONTAINER_TRACKING_ID");
    verify(containerPersisterService, times(2)).getContainerDetailsByParentTrackingId(anyString());
    verify(containerPersisterService, times(1)).saveContainers(any(List.class));
    verify(receiptService, times(1)).saveReceipt(any(Receipt.class));
    verify(containerItemRepository, times(1)).saveAll(any(List.class));
    verify(containerItemRepository, times(2)).findByTrackingId(anyString());

    verify(rxSlottingServiceImpl, times(1)).freeSlot(any(), anyString(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(any());
  }

  @Test
  public void testCancelContainersInventory() throws Exception {

    doReturn(null)
        .when(rxContainerAdjustmentValidator)
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    doReturn(false).when(rxManagedConfig).isRollbackReceiptsByShipment();

    Container palletContainer = createContainer(null, "PALLET_CONTAINER_TRACKING_ID", 6);

    Set<Container> childContainers = new HashSet<>();
    Container caseContainer1 =
        createContainer("PALLET_CONTAINER_TRACKING_ID", "CASE_CONTAINER_TRACKING_ID1", 1);
    childContainers.add(caseContainer1);

    Container caseContainer2 =
        createContainer("PALLET_CONTAINER_TRACKING_ID", "CASE_CONTAINER_TRACKING_ID2", 1);
    childContainers.add(caseContainer2);

    palletContainer.setChildContainers(childContainers);

    doReturn(palletContainer)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());

    doAnswer(
            new Answer<Set>() {
              public Set answer(InvocationOnMock invocation) {
                String trackingId = (String) invocation.getArgument(0);
                Container whpkContainer =
                    createContainer(trackingId, "WHPK_CONTAINER_TRACKING_ID1", 1);
                Set<Container> whpkContainerSet = new HashSet<>();
                whpkContainerSet.add(whpkContainer);

                return whpkContainerSet;
              }
            })
        .when(containerPersisterService)
        .getContainerDetailsByParentTrackingId(anyString());

    doNothing().when(containerPersisterService).saveContainers(any(List.class));
    doReturn(null).when(containerItemRepository).saveAll(any(List.class));

    doNothing().when(rxSlottingServiceImpl).freeSlot(any(), anyString(), any(HttpHeaders.class));
    doReturn(MockInstruction.getMockNewInstruction())
        .when(instructionPersisterService)
        .getInstructionById(any());

    ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
    doReturn(new Receipt()).when(receiptService).saveReceipt(receiptCaptor.capture());
    doReturn(Arrays.asList(new ContainerItem()))
        .when(containerItemRepository)
        .findByTrackingId(anyString());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Arrays.asList("PALLET_CONTAINER_TRACKING_ID"));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), Mockito.any(), anyBoolean()))
        .thenAnswer(
            invocation -> ((String) invocation.getArguments()[1]).equals(ENABLE_INV_LABEL_BACKOUT));

    List<CancelContainerResponse> cancelContainersResponse =
        rxCancelContainerProcessor.cancelContainers(
            cancelContainerRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(cancelContainersResponse);
    assertSame(receiptCaptor.getValue().getQuantity(), -1);
    assertSame(receiptCaptor.getValue().getEachQty(), -6);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents("PALLET_CONTAINER_TRACKING_ID");
    verify(containerPersisterService, times(2)).getContainerDetailsByParentTrackingId(anyString());
    verify(containerPersisterService, times(1)).saveContainers(any(List.class));
    verify(receiptService, times(1)).saveReceipt(any(Receipt.class));
    verify(containerItemRepository, times(1)).saveAll(any(List.class));
    verify(containerItemRepository, times(2)).findByTrackingId(anyString());

    verify(rxSlottingServiceImpl, times(1)).freeSlot(any(), anyString(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(any());
    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(any(), any(HttpHeaders.class));
  }

  @Test
  public void testCancelContainers_RollbackReceiptsWithShipment() throws Exception {

    doReturn(null)
        .when(rxContainerAdjustmentValidator)
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    doReturn(true).when(rxManagedConfig).isRollbackReceiptsByShipment();

    Container palletContainer = createContainer(null, "PALLET_CONTAINER_TRACKING_ID", 6);

    HashMap<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(RxConstants.SHIPMENT_DOCUMENT_ID, "shipmentDocId");

    Set<Container> childContainers = new HashSet<>();
    Container caseContainer1 =
        createContainer("PALLET_CONTAINER_TRACKING_ID", "CASE_CONTAINER_TRACKING_ID1", 1);
    caseContainer1.setContainerMiscInfo(containerMiscInfo);
    childContainers.add(caseContainer1);

    Container caseContainer2 =
        createContainer("PALLET_CONTAINER_TRACKING_ID", "CASE_CONTAINER_TRACKING_ID2", 1);
    caseContainer2.setContainerMiscInfo(containerMiscInfo);
    childContainers.add(caseContainer2);

    Container caseContainer3 =
        createContainer("PALLET_CONTAINER_TRACKING_ID", "CASE_CONTAINER_TRACKING_ID2", 1);
    caseContainer3.setContainerMiscInfo(containerMiscInfo);

    palletContainer.setChildContainers(childContainers);

    doReturn(palletContainer)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());

    doAnswer(
            new Answer<Set>() {
              public Set answer(InvocationOnMock invocation) {
                String trackingId = (String) invocation.getArgument(0);
                Container whpkContainer =
                    createContainer(trackingId, "WHPK_CONTAINER_TRACKING_ID1", 1);
                Set<Container> whpkContainerSet = new HashSet<>();
                whpkContainerSet.add(whpkContainer);

                return whpkContainerSet;
              }
            })
        .when(containerPersisterService)
        .getContainerDetailsByParentTrackingId(anyString());

    doNothing().when(containerPersisterService).saveContainers(any(List.class));
    doReturn(null).when(containerItemRepository).saveAll(any(List.class));

    doNothing().when(rxSlottingServiceImpl).freeSlot(any(), anyString(), any(HttpHeaders.class));
    doReturn(MockInstruction.getMockNewInstruction())
        .when(instructionPersisterService)
        .getInstructionById(any());

    ArgumentCaptor<List<Receipt>> receiptCaptor = ArgumentCaptor.forClass(List.class);
    doReturn(Arrays.asList(new Receipt())).when(receiptService).saveAll(receiptCaptor.capture());
    doReturn(caseContainer3.getContainerItems())
        .when(containerItemRepository)
        .findByTrackingId(anyString());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Arrays.asList("PALLET_CONTAINER_TRACKING_ID"));

    List<CancelContainerResponse> cancelContainersResponse =
        rxCancelContainerProcessor.cancelContainers(
            cancelContainerRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(cancelContainersResponse);
    assertSame(receiptCaptor.getValue().get(0).getQuantity(), 0);
    assertSame(receiptCaptor.getValue().get(0).getEachQty(), -2);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents("PALLET_CONTAINER_TRACKING_ID");
    verify(containerPersisterService, times(2)).getContainerDetailsByParentTrackingId(anyString());
    verify(containerPersisterService, times(1)).saveContainers(any(List.class));
    verify(receiptService, times(1)).saveAll(any(List.class));
    verify(containerItemRepository, times(1)).saveAll(any(List.class));
    verify(containerItemRepository, times(2)).findByTrackingId(anyString());

    verify(rxSlottingServiceImpl, times(1)).freeSlot(any(), anyString(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(any());
  }

  @Test
  public void testswapCancelContainers() throws Exception {
    List<SwapContainerRequest> swapCancellist = new ArrayList<>();
    try {
      rxCancelContainerProcessor.swapContainers(swapCancellist, MockRxHttpHeaders.getHeaders());
    } catch (ReceivingInternalException ex) {
      assertEquals("GLS-RCV-CNF-500", ex.getErrorCode());
    }
  }
}
