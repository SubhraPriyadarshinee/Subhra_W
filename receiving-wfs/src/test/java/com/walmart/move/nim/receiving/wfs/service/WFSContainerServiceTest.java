package com.walmart.move.nim.receiving.wfs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentValidator;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.WFSTestUtils;
import com.walmart.move.nim.receiving.wfs.mock.data.MockContainer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import lombok.SneakyThrows;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSContainerServiceTest extends ReceivingTestBase {

  @InjectMocks private WFSContainerService wfsContainerService;

  @InjectMocks private ContainerPersisterService containerPersisterService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private DeliveryService deliveryService;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Spy private ReceiptRepository receiptRepository;
  @Spy private ContainerRepository containerRepository;
  @Spy private ContainerItemRepository containerItemRepository;
  @InjectMocks private ContainerAdjustmentValidator containerAdjustmentValidator;
  private Gson gson;

  private Container container;
  private Container childContainer;
  private final String trackingId = "c32987000000000000000001";
  private final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

  private final String correlationId = "54d09f29-ad47-471f-aaef-931c7bc88cb9";

  private final String userId = "user";

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        containerAdjustmentValidator, "containerPersisterService", containerPersisterService);
    ReflectionTestUtils.setField(
        wfsContainerService, "containerAdjustmentValidator", containerAdjustmentValidator);

    ReflectionTestUtils.setField(
        wfsContainerService, "containerPersisterService", containerPersisterService);

    TenantContext.setFacilityNum(4093);
    TenantContext.setFacilityCountryCode("us");
  }

  @AfterMethod
  public void tearDown() {
    reset(containerRepository);
    reset(containerItemRepository);
    reset(receiptRepository);
    reset(receiptService);
    reset(deliveryStatusPublisher);
    reset(configUtils);
    reset(deliveryService);
  }

  @BeforeMethod
  public void beforeMethod() {
    when(configUtils.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any()))
        .thenReturn(deliveryService);
  }

  public InstructionRequest getInstructionRequest() {
    String inputPayloadWithDeliveryDocumentFilePath =
        "../receiving-test/src/main/resources/json/wfs/wfsInputPayloadWithDeliveryDocument.json";
    String inputPayloadWithDeliveryDocument =
        WFSTestUtils.getJSONStringResponse(inputPayloadWithDeliveryDocumentFilePath);
    return gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
  }

  /**
   * This method will test the backoutContainerForWFS
   *
   * @throws ReceivingException
   */
  @Test
  public void testBackoutContainerForWFSHappyPath() throws ReceivingException {

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getWFSContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(
            MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo())
                .getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo())
                .getDeliveryNumber(),
            httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"PNDFNL\"}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);

    wfsContainerService.backoutContainerForWFS(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test
  public void testBackoutContainerForWFSHappyPath_1() throws ReceivingException {

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getWFSContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(
            MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo())
                .getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo())
                .getDeliveryNumber(),
            httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"PNDPT\"}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);

    wfsContainerService.backoutContainerForWFS(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test
  public void testBackoutContainerForWFSHappyPath_2() throws ReceivingException {

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getWFSContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(
            MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo())
                .getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo())
                .getDeliveryNumber(),
            httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"REO\"}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);

    wfsContainerService.backoutContainerForWFS(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test
  public void testBackoutContainerForWFSDoesNotExists() {
    when(containerRepository.findByTrackingId(trackingId)).thenReturn(null);
    when(containerRepository.findAllByParentTrackingId(trackingId)).thenReturn(null);
    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(null);

    try {
      wfsContainerService.backoutContainerForWFS(trackingId, httpHeaders);

    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  public void testBackoutContainerForWFSAlreadyBackout() {
    container = MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo());
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
    when(containerRepository.findAllByParentTrackingId(trackingId)).thenReturn(null);
    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(null);

    try {
      wfsContainerService.backoutContainerForWFS(trackingId, httpHeaders);

    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  public void testBackoutContainerForWFSHasChilds() {
    Set<Container> childContainers = new HashSet<Container>();
    childContainer = new Container();
    childContainer.setDeliveryNumber(1234L);
    childContainer.setParentTrackingId(trackingId);
    childContainer.setContainerStatus("");
    childContainers.add(childContainer);

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getWFSContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId)).thenReturn(childContainers);
    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(null);

    try {
      wfsContainerService.backoutContainerForWFS(trackingId, httpHeaders);

    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.CONTAINER_WITH_CHILD_ERROR_MSG);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  @SneakyThrows
  public void testBackoutContainerForWFSDoesNotHaveContents() {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getWFSContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(new ArrayList<ContainerItem>());

    try {
      wfsContainerService.backoutContainerForWFS(trackingId, httpHeaders);

    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_MSG);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  public void testBackoutContainerForWFS_NotPublish_ReceiptsSummary() throws ReceivingException {

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getWFSContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(
            MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo())
                .getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo())
                .getDeliveryNumber(),
            httpHeaders))
        .thenReturn(
            "{\"deliveryNumber\":95350003,\"deliveryStatus\":\"WRK\",\"stateReasonCodes\":[\"WORKING\"]}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);

    wfsContainerService.backoutContainerForWFS(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test
  public void testBackoutContainerForWFS_Publish_ReceiptsSummary() throws ReceivingException {

    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(MockContainer.getWFSContainerInfo());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(
            MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo())
                .getContainerItems());
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(deliveryService.getDeliveryByDeliveryNumber(
            MockContainer.updateContainerForWFS(MockContainer.getWFSContainerInfo())
                .getDeliveryNumber(),
            httpHeaders))
        .thenReturn(
            "{\"deliveryNumber\":95350003,\"deliveryStatus\":\"OPN\",\"stateReasonCodes\":[\"PENDING_PROBLEM\"]}");
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("EA")))
        .thenReturn(new ArrayList<>());
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);

    wfsContainerService.backoutContainerForWFS(trackingId, httpHeaders);

    verify(containerRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).findAllByParentTrackingId(trackingId);
    verify(containerItemRepository, Mockito.times(2)).findByTrackingId(trackingId);
    verify(containerRepository, Mockito.times(1)).save(any(Container.class));
    verify(receiptRepository, times(1)).saveAll(any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  @Test
  public void testCreateDiffReceipt() {
    Container mockContainer = MockContainer.getWFSContainerInfo();
    ContainerItem containerItem = mockContainer.getContainerItems().get(0);
    Receipt adjustedReceipt =
        wfsContainerService.createDiffReceipt(mockContainer, containerItem, 5, "sysadmin");
    assertEquals(adjustedReceipt.getDeliveryNumber(), mockContainer.getDeliveryNumber());
    assertEquals(adjustedReceipt.getDoorNumber(), mockContainer.getLocation());
    assertEquals(adjustedReceipt.getEachQty(), Integer.valueOf(5));
    assertEquals(adjustedReceipt.getFacilityCountryCode(), containerItem.getFacilityCountryCode());
    assertEquals(adjustedReceipt.getFacilityNum(), containerItem.getFacilityNum());
    assertEquals(
        adjustedReceipt.getPurchaseReferenceLineNumber(),
        containerItem.getPurchaseReferenceLineNumber());
    assertEquals(
        adjustedReceipt.getPurchaseReferenceNumber(), containerItem.getPurchaseReferenceNumber());
    assertEquals(adjustedReceipt.getCreateUserId(), "sysadmin");
    assertEquals(adjustedReceipt.getQuantity(), Integer.valueOf(5));
    assertEquals(adjustedReceipt.getQuantityUom(), ReceivingConstants.Uom.EACHES);
    assertEquals(adjustedReceipt.getVnpkQty(), containerItem.getVnpkQty());
    assertEquals(adjustedReceipt.getWhpkQty(), containerItem.getWhpkQty());
  }

  @Test
  public void testAdjustContainerItemQuantityAndGetDiff_INV_RECV_inSync_DiffQtyRCV_Positive()
      throws ReceivingException {
    // keeping all UoM EA to keep calculation easier, conversionToEaches should take
    // care of all other cases correctly
    int newQuantityInUI = 120; // required diff is 30EA
    int quantityInInv = 90; // same as MockContainer (for INV RCV in sync)
    Container mockContainer = MockContainer.getWFSContainerInfo();
    ContainerItem containerItem = mockContainer.getContainerItems().get(0);
    ContainerUpdateResponse response = new ContainerUpdateResponse();
    // setting containerItemQty Explicitly, so that test does not fail if mock object changes
    containerItem.setQuantity(90);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

    when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(null);
    when(containerRepository.save(any(Container.class))).thenReturn(container);

    int diffQuantityInEaches_RCV =
        wfsContainerService.adjustContainerItemQuantityAndGetDiff(
            correlationId,
            userId,
            newQuantityInUI,
            response,
            mockContainer,
            containerItem,
            quantityInInv,
            ReceivingConstants.Uom.EACHES);

    verify(containerItemRepository, times(1)).save(any(ContainerItem.class));
    verify(containerRepository, times(1)).save(any(Container.class));

    assertEquals(diffQuantityInEaches_RCV, 30);
    Container updatedContainer = response.getContainer();
    assertEquals(updatedContainer.getWeight(), 40.0f);
    assertEquals(updatedContainer.getWeightUOM(), ReceivingConstants.Uom.LB);
    assertEquals(
        updatedContainer.getContainerItems().get(0).getQuantity(),
        Integer.valueOf(newQuantityInUI));
    assertEquals(updatedContainer.getLastChangedUser(), userId);
  }

  @Test
  public void testAdjustContainerItemQuantityAndGetDiff_INV_RECV_inSync_DiffQtyRCV_Negative()
      throws ReceivingException {
    // keeping all UoM EA to keep calculation easier, conversionToEaches should take
    // care of all other cases correctly
    int newQuantityInUI = 80; // required diff is -10 EA
    int quantityInInv = 90; // same as MockContainer (for INV RCV in sync)
    Container mockContainer = MockContainer.getWFSContainerInfo();
    ContainerItem containerItem = mockContainer.getContainerItems().get(0);
    ContainerUpdateResponse response = new ContainerUpdateResponse();
    // setting containerItemQty Explicitly, so that test does not fail if mock object changes
    containerItem.setQuantity(90);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

    when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(null);
    when(containerRepository.save(any(Container.class))).thenReturn(container);

    int diffQuantityInEaches_RCV =
        wfsContainerService.adjustContainerItemQuantityAndGetDiff(
            correlationId,
            userId,
            newQuantityInUI,
            response,
            mockContainer,
            containerItem,
            quantityInInv,
            ReceivingConstants.Uom.EACHES);

    verify(containerItemRepository, times(1)).save(any(ContainerItem.class));
    verify(containerRepository, times(1)).save(any(Container.class));

    assertEquals(diffQuantityInEaches_RCV, -10);
    Container updatedContainer = response.getContainer();
    assertEquals(updatedContainer.getWeight(), 26.0f);
    assertEquals(updatedContainer.getWeightUOM(), ReceivingConstants.Uom.LB);
    assertEquals(updatedContainer.getContainerItems().get(0).getQuantity(), Integer.valueOf(80));
    assertEquals(updatedContainer.getLastChangedUser(), userId);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testAdjustContainerItemQuantityAndGetDiff_INV_RECV_inSync_DiffQtyRCV_Zero()
      throws ReceivingException {
    // keeping all UoM EA to keep calculation easier, conversionToEaches should take
    // care of all other cases correctly
    int newQuantityInUI = 90; // required diff is 0, so same as mockContainer
    int quantityInInv = 90; // same as MockContainer (for INV RCV in sync)
    Container mockContainer = MockContainer.getWFSContainerInfo();
    ContainerItem containerItem = mockContainer.getContainerItems().get(0);
    ContainerUpdateResponse response = new ContainerUpdateResponse();
    // setting containerItemQty Explicitly, so that test does not fail if mock object changes
    containerItem.setQuantity(90);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

    when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(null);
    when(containerRepository.save(any(Container.class))).thenReturn(container);

    int diffQuantityInEaches_RCV =
        wfsContainerService.adjustContainerItemQuantityAndGetDiff(
            correlationId,
            userId,
            newQuantityInUI,
            response,
            mockContainer,
            containerItem,
            quantityInInv,
            ReceivingConstants.Uom.EACHES);

    verify(containerItemRepository, times(0)).save(any(ContainerItem.class));
    verify(containerRepository, times(0)).save(any(Container.class));
  }

  @Test
  public void testAdjustContainerItemQuantityAndGetDiff_INV_RECV_NotinSync_DiffQtyRCV_Positive()
      throws ReceivingException {
    // keeping all UoM EA to keep calculation easier, conversionToEaches should take
    // care of all other cases correctly
    int newQuantityInUI = 120; // required diff is 30EA
    int quantityInInv = 100; // (for INV RCV NOT in sync)
    Container mockContainer = MockContainer.getWFSContainerInfo();
    ContainerItem containerItem = mockContainer.getContainerItems().get(0);
    ContainerUpdateResponse response = new ContainerUpdateResponse();
    // setting containerItemQty Explicitly, so that test does not fail if mock object changes
    containerItem.setQuantity(90);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

    when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(null);
    when(containerRepository.save(any(Container.class))).thenReturn(container);

    int diffQuantityInEaches_RCV =
        wfsContainerService.adjustContainerItemQuantityAndGetDiff(
            correlationId,
            userId,
            newQuantityInUI,
            response,
            mockContainer,
            containerItem,
            quantityInInv,
            ReceivingConstants.Uom.EACHES);

    verify(containerItemRepository, times(1)).save(any(ContainerItem.class));
    verify(containerRepository, times(1)).save(any(Container.class));

    assertEquals(diffQuantityInEaches_RCV, 20);
    Container updatedContainer = response.getContainer();
    assertEquals(updatedContainer.getWeight(), 36.0f);
    assertEquals(updatedContainer.getWeightUOM(), ReceivingConstants.Uom.LB);
    assertEquals(updatedContainer.getContainerItems().get(0).getQuantity(), Integer.valueOf(110));
    assertEquals(updatedContainer.getLastChangedUser(), userId);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testAdjustContainerItemQuantityAndGetDiff_INV_RECV_NotinSync_DiffQtyRCV_Zero()
      throws ReceivingException {
    // keeping all UoM EA to keep calculation easier, conversionToEaches should take
    // care of all other cases correctly
    int newQuantityInUI = 100; // required diff 0EA (between UI and INV)
    int quantityInInv = 100; // (for INV RCV NOT in sync)
    Container mockContainer = MockContainer.getWFSContainerInfo();
    ContainerItem containerItem = mockContainer.getContainerItems().get(0);
    ContainerUpdateResponse response = new ContainerUpdateResponse();
    // setting containerItemQty Explicitly, so that test does not fail if mock object changes
    containerItem.setQuantity(90);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

    when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(null);
    when(containerRepository.save(any(Container.class))).thenReturn(container);

    int diffQuantityInEaches_RCV =
        wfsContainerService.adjustContainerItemQuantityAndGetDiff(
            correlationId,
            userId,
            newQuantityInUI,
            response,
            mockContainer,
            containerItem,
            quantityInInv,
            ReceivingConstants.Uom.EACHES);
    verify(containerItemRepository, times(0)).save(any(ContainerItem.class));
    verify(containerRepository, times(0)).save(any(Container.class));
  }

  @Test
  public void
      testAdjustContainerItemQuantityAndGetDiff_INV_RECV_inSync_DiffQtyRCV_Negative_QtyUI_Zero()
          throws ReceivingException {
    // keeping all UoM EA to keep calculation easier, conversionToEaches should take
    // care of all other cases correctly
    int newQuantityInUI = 0; // required to trigger delete in inventory (BACKOUT status)
    int quantityInInv = 90; // same as MockContainer (for INV RCV in sync)
    Container mockContainer = MockContainer.getWFSContainerInfo();
    ContainerItem containerItem = mockContainer.getContainerItems().get(0);
    ContainerUpdateResponse response = new ContainerUpdateResponse();
    // setting containerItemQty Explicitly, so that test does not fail if mock object changes
    containerItem.setQuantity(90);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

    when(containerItemRepository.save(any(ContainerItem.class))).thenReturn(null);
    when(containerRepository.save(any(Container.class))).thenReturn(container);

    int diffQuantityInEaches_RCV =
        wfsContainerService.adjustContainerItemQuantityAndGetDiff(
            correlationId,
            userId,
            newQuantityInUI,
            response,
            mockContainer,
            containerItem,
            quantityInInv,
            ReceivingConstants.Uom.EACHES);

    verify(containerItemRepository, times(1)).save(any(ContainerItem.class));
    verify(containerRepository, times(1)).save(any(Container.class));

    assertEquals(diffQuantityInEaches_RCV, -90);
    Container updatedContainer = response.getContainer();
    assertEquals(updatedContainer.getContainerStatus(), ReceivingConstants.STATUS_BACKOUT);
    assertEquals(updatedContainer.getWeight(), 0.0f);
    assertEquals(updatedContainer.getWeightUOM(), ReceivingConstants.Uom.LB);
    assertEquals(updatedContainer.getContainerItems().get(0).getQuantity(), Integer.valueOf(0));
  }
}
