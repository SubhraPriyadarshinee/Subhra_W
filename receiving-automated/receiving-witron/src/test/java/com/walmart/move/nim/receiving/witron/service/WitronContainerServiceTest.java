package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.LABEL_ON_HOLD_REQUEST_IN_GLS_INSTEAD_OF_ATLAS;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVALID_REQUEST;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EACHES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_ADJUSTMENT_ADJUSTMENT_TO;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_OBJECT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_ADJUSTMENT_ITEM_LIST;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_ADJUSTMENT_QTY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_ADJUSTMENT_QTY_UOM;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_AVAILABLE_TO_SELL_QTY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom;
import static java.lang.Integer.valueOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingPalletBuildResponse;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.GdcPutawayPublisher;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceiptPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.helper.ReceiptHelper;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentValidator;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.DefaultPutawayHandler;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.mock.data.MockContainer;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WitronContainerServiceTest extends ReceivingTestBase {
  @InjectMocks private WitronContainerService witronContainerService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private InventoryService inventoryService;
  @Mock private ContainerRepository containerRepository;
  @Mock private GdcPutawayPublisher gdcPutawayPublisher;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private AppConfig appConfig;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private MovePublisher movePublisher;
  @Mock private GdcSlottingServiceImpl slottingService;
  @Mock private GDCFlagReader gdcFlagReader;
  @Mock private ReceiptPublisher receiptPublisher;
  @Spy private ReceiptHelper receiptHelper;
  @Autowired Gson gson;
  final JsonParser parser = new JsonParser();

  private final HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private final String facilityNum = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);
  private final String trackingId = "030125103317445953";

  private Container container;
  private ContainerItem containerItem;
  private SlottingPalletBuildResponse slottingResponse;
  private final String newLocationFromSlottingServiceResponse = "Staging_Slot_01";
  private final String sourceLocationFromInventory = "door101";

  @InjectMocks private ContainerAdjustmentValidator containerAdjustmentValidator;
  @InjectMocks private ContainerPersisterService containerPersisterService2;
  @Mock private DeliveryService deliveryService;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private DefaultPutawayHandler defaultPutawayHandler;
  @Spy private ReceiptRepository receiptRepository;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(valueOf(facilityNum));

    ReflectionTestUtils.setField(
        witronContainerService, "containerAdjustmentValidator", containerAdjustmentValidator);
  }

  @BeforeMethod
  public void initMocks() {

    container = new Container();
    container.setDeliveryNumber(Long.valueOf("121212121"));
    container.setTrackingId(trackingId);
    container.setContainerType("13");
    container.setLocation("101");
    container.setInventoryStatus(InventoryStatus.AVAILABLE.toString());
    container.setContainerStatus(null);
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());

    containerItem = new ContainerItem();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItem.setTrackingId(trackingId);
    containerItem.setPurchaseReferenceNumber("6712345678");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(80);
    containerItem.setGtin("7874213228");
    containerItem.setItemNumber(Long.valueOf("554930276"));
    containerItem.setQuantity(480);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(8);
    containerItem.setActualHi(10);
    containerItem.setLotNumber("555");
    containerItem.setVendorNumber(579284);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    slottingResponse = new SlottingPalletBuildResponse();
    slottingResponse.setContainerTrackingId(trackingId);
    slottingResponse.setDivertLocation(newLocationFromSlottingServiceResponse);
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(inventoryService);
    reset(containerRepository);
    reset(gdcPutawayPublisher);
    reset(containerPersisterService);
    reset(jmsPublisher);
    reset(movePublisher);
    reset(appConfig);
    reset(deliveryService);
    reset(receiptService);
    reset(deliveryStatusPublisher);
    reset(receiptRepository);
  }

  @Test
  public void testPalletOnHold_success() throws ReceivingException {
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(containerPersisterService.getContainerDetails(trackingId)).thenReturn(container);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(slottingService.getDivertLocation(any(), any(), anyString())).thenReturn(slottingResponse);

    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing().when(inventoryService).onHold(container, httpHeaders);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    doReturn(sourceLocationFromInventory)
        .when(inventoryService)
        .getContainerLocation(trackingId, httpHeaders);

    witronContainerService.palletOnHold(trackingId, httpHeaders);

    verify(containerPersisterService, times(1)).getContainerDetails(trackingId);
    verify(containerRepository, times(1)).save(container);
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(gdcPutawayPublisher, times(1))
        .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
  }

  @Test
  public void testPalletOnHold_containerNotFound() throws ReceivingException {
    try {
      when(containerPersisterService.getContainerDetails(trackingId)).thenReturn(null);

      witronContainerService.palletOnHold(trackingId, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assert (true);

      assertEquals(
          e.getErrorResponse().getErrorMessage(), ReceivingException.MATCHING_CONTAINER_NOT_FOUND);
      verify(containerPersisterService, times(1)).getContainerDetails(trackingId);
      verify(containerRepository, times(0)).save(container);
      verify(gdcPutawayPublisher, times(0))
          .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
    }
  }

  @Test
  public void testPalletOnHold_containerAlreadyOnHold() throws ReceivingException {
    try {
      container.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.toString());
      when(containerPersisterService.getContainerDetails(trackingId)).thenReturn(container);

      witronContainerService.palletOnHold(trackingId, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assert (true);

      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          ReceivingException.LABEL_ALREADY_ONHOLD_ERROR_MSG);
      verify(containerPersisterService, times(1)).getContainerDetails(trackingId);
      verify(containerRepository, times(0)).save(container);
      verify(gdcPutawayPublisher, times(0))
          .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
    }
  }

  @Test
  public void testPalletOnHold_dBError() throws ReceivingException {
    try {
      when(slottingService.getDivertLocation(any(), any(), anyString()))
          .thenReturn(slottingResponse);
      when(containerPersisterService.getContainerDetails(trackingId)).thenReturn(container);
      when(containerRepository.save(container))
          .thenThrow(new RuntimeException("could not execute SQL"));

      witronContainerService.palletOnHold(trackingId, httpHeaders);
      fail();
    } catch (Exception e) {
      assert (true);

      assertEquals(e.getMessage(), "could not execute SQL");
      verify(containerPersisterService, times(1)).getContainerDetails(trackingId);
      verify(containerRepository, times(1)).save(container);
      verify(gdcPutawayPublisher, times(0))
          .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
    }
  }

  @Test
  public void testPalletOnHold_inventoryCallFailed() throws ReceivingException {
    try {
      when(slottingService.getDivertLocation(any(), any(), anyString()))
          .thenReturn(slottingResponse);
      when(containerPersisterService.getContainerDetails(trackingId)).thenReturn(container);
      when(containerRepository.save(any(Container.class))).thenReturn(container);

      doThrow(
              new ReceivingException(
                  ReceivingException.INVENTORY_ERROR_MSG,
                  HttpStatus.BAD_REQUEST,
                  ReceivingException.INVENTORY_ERROR_CODE))
          .when(inventoryService)
          .onHold(container, httpHeaders);

      witronContainerService.palletOnHold(trackingId, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assert (true);

      assertEquals(e.getErrorResponse().getErrorCode(), ReceivingException.INVENTORY_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.INVENTORY_ERROR_MSG);
      verify(containerPersisterService, times(1)).getContainerDetails(trackingId);
      verify(gdcPutawayPublisher, times(0))
          .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
    }
  }

  @Test
  public void testPalletOffHold_success() throws ReceivingException {
    container.setInventoryStatus(InventoryStatus.AVAILABLE.toString());
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(containerPersisterService.getContainerDetails(trackingId)).thenReturn(container);
    when(containerRepository.save(any(Container.class))).thenReturn(container);
    when(slottingService.getDivertLocation(any(), any(), anyString())).thenReturn(slottingResponse);

    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing().when(inventoryService).onHold(container, httpHeaders);
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    container.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.toString());
    doReturn(sourceLocationFromInventory)
        .when(inventoryService)
        .getContainerLocation(trackingId, httpHeaders);

    witronContainerService.palletOffHold(trackingId, httpHeaders);

    verify(containerPersisterService, times(1)).getContainerDetails(trackingId);
    verify(containerRepository, times(1)).save(container);
    verify(inventoryService, times(1)).palletOffHold(anyString(), any());
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(gdcPutawayPublisher, times(1))
        .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
  }

  @Test
  public void testPalletOffHold_containerNotFound() throws ReceivingException {
    try {
      when(containerPersisterService.getContainerDetails(trackingId)).thenReturn(null);

      witronContainerService.palletOffHold(trackingId, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assert (true);

      assertEquals(
          e.getErrorResponse().getErrorMessage(), ReceivingException.MATCHING_CONTAINER_NOT_FOUND);
      verify(containerPersisterService, times(1)).getContainerDetails(trackingId);
      verify(containerRepository, times(0)).save(container);
      verify(gdcPutawayPublisher, times(0))
          .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
    }
  }

  @Test
  public void testPalletOffHold_container_Already_OffHold() throws ReceivingException {
    try {
      container.setInventoryStatus(InventoryStatus.AVAILABLE.toString());
      container.setInventoryStatus(null);

      when(containerPersisterService.getContainerDetails(trackingId)).thenReturn(container);
      when(slottingService.getDivertLocation(any(), any(), anyString()))
          .thenReturn(slottingResponse);

      witronContainerService.palletOffHold(trackingId, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assert (true);

      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          ReceivingException.LABEL_ALREADY_OFF_HOLD_ERROR_MSG);
      verify(containerPersisterService, times(1)).getContainerDetails(trackingId);
      verify(containerRepository, times(0)).save(container);
      verify(gdcPutawayPublisher, times(0))
          .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
    }
  }

  @Test
  public void testPalletOffHold_dBError() throws ReceivingException {
    try {
      when(slottingService.getDivertLocation(any(), any(), anyString()))
          .thenReturn(slottingResponse);
      when(containerPersisterService.getContainerDetails(trackingId)).thenReturn(container);
      when(containerRepository.save(container))
          .thenThrow(new RuntimeException("could not execute SQL"));
      container.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.toString());

      witronContainerService.palletOffHold(trackingId, httpHeaders);
      fail();
    } catch (Exception e) {
      assert (true);

      assertEquals(e.getMessage(), "could not execute SQL");
      verify(containerPersisterService, times(1)).getContainerDetails(trackingId);
      verify(containerRepository, times(1)).save(container);
      verify(gdcPutawayPublisher, times(0))
          .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
    }
  }

  @Test
  public void testPalletOffHold_inventoryCallFailed() throws ReceivingException {
    try {
      container.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.toString());

      when(slottingService.getDivertLocation(any(), any(), anyString()))
          .thenReturn(slottingResponse);
      when(containerPersisterService.getContainerDetails(trackingId)).thenReturn(container);
      when(containerRepository.save(any(Container.class))).thenReturn(container);

      doThrow(
              new ReceivingException(
                  ReceivingException.INVENTORY_ERROR_MSG,
                  HttpStatus.BAD_REQUEST,
                  ReceivingException.INVENTORY_ERROR_CODE))
          .when(inventoryService)
          .palletOffHold(trackingId, httpHeaders);

      witronContainerService.palletOffHold(trackingId, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assert (true);

      assertEquals(e.getErrorResponse().getErrorCode(), ReceivingException.INVENTORY_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.INVENTORY_ERROR_MSG);
      verify(containerPersisterService, times(1)).getContainerDetails(trackingId);
      verify(gdcPutawayPublisher, times(0))
          .publishMessage(container, ReceivingConstants.PUTAWAY_UPDATE_ACTION, httpHeaders);
    }
  }

  /**
   * This method will test the processVTR
   *
   * @throws ReceivingException
   */
  @Test
  public void test_processVTR_full_VTR_HappyPath_NO_Damages() throws ReceivingException {
    TenantContext.setFacilityCountryCode("us");

    doReturn(container)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(trackingId);
    when(deliveryService.getDeliveryByDeliveryNumber(121212121L, httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"PNDFNL\"}");
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);

    int full_VTR_Quantity = -480;
    witronContainerService.processVTR(trackingId, httpHeaders, full_VTR_Quantity, EACHES, 0);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  /**
   * This method will test the processVTR including if any damages based on INV quantity
   *
   * <p>use case 20 receved , 5 damages , now rcv=20 but inv=15 cancel label should send full VTR
   * then Rcv should be 5, inv=0, RTU=0 and Rtu=Delete
   *
   * @throws ReceivingException
   */
  @Test
  public void test_processVTR_full_VTR_with_Damages() throws Exception {
    TenantContext.setFacilityCountryCode("us");

    File resource = new ClassPathResource("witron_inventory_full_vtr_with_damages.json").getFile();
    String full_vtr_with_damages = new String(Files.readAllBytes(resource.toPath()));

    final JsonObject jsonObject = parser.parse(full_vtr_with_damages).getAsJsonObject();
    final JsonObject eventObject = jsonObject.getAsJsonObject(INVENTORY_ADJUSTMENT_EVENT_OBJECT);
    JsonObject itemList =
        (JsonObject) eventObject.getAsJsonArray(INVENTORY_ADJUSTMENT_ITEM_LIST).get(0);
    final int availableToSellQty = itemList.get(INVENTORY_AVAILABLE_TO_SELL_QTY).getAsInt();
    JsonObject adjustmentTO = itemList.getAsJsonObject(INVENTORY_ADJUSTMENT_ADJUSTMENT_TO);
    int adjustmentToQty = adjustmentTO.get(INVENTORY_ADJUSTMENT_QTY).getAsInt();
    String adjustmentToQtyUOM = adjustmentTO.get(INVENTORY_ADJUSTMENT_QTY_UOM).getAsString();

    doReturn(container)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(trackingId);
    when(deliveryService.getDeliveryByDeliveryNumber(121212121L, httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"PNDFNL\"}");
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);

    witronContainerService.processVTR(
        trackingId, httpHeaders, adjustmentToQty, adjustmentToQtyUOM, availableToSellQty);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());

    ArgumentCaptor<Container> argumentCaptorRtuContainer = ArgumentCaptor.forClass(Container.class);
    ArgumentCaptor<String> argumentCaptorRtuAction = ArgumentCaptor.forClass(String.class);
    verify(gdcPutawayPublisher, times(1))
        .publishMessage(
            argumentCaptorRtuContainer.capture(), argumentCaptorRtuAction.capture(), any());
    Container rtuContainer = argumentCaptorRtuContainer.getValue();
    assertEquals(rtuContainer.getTrackingId(), "030125103317445953");
    String rtuAction = argumentCaptorRtuAction.getValue();
    assertEquals(rtuAction, "delete");
  }
  /**
   * This method will test the processVTR including if any damages based on INV quantity
   *
   * <p>use case 20 receved , 5 damages , now rcv=20 but inv=15 cancel label should send full VTR
   * then Rcv should be 5, inv=0, RTU=0 and Rtu=Delete
   *
   * @throws ReceivingException
   */
  @Test
  public void test_processVTR_partial_VTR_with_Damages() throws Exception {
    TenantContext.setFacilityCountryCode("us");

    File resource =
        new ClassPathResource("witron_inventory_partial_vtr_with_damages.json").getFile();
    String full_vtr_with_damages = new String(Files.readAllBytes(resource.toPath()));

    final JsonObject jsonObject = parser.parse(full_vtr_with_damages).getAsJsonObject();
    final JsonObject eventObject = jsonObject.getAsJsonObject(INVENTORY_ADJUSTMENT_EVENT_OBJECT);
    JsonObject itemList =
        (JsonObject) eventObject.getAsJsonArray(INVENTORY_ADJUSTMENT_ITEM_LIST).get(0);
    final int availableToSellQty = itemList.get(INVENTORY_AVAILABLE_TO_SELL_QTY).getAsInt();
    JsonObject adjustmentTO = itemList.getAsJsonObject(INVENTORY_ADJUSTMENT_ADJUSTMENT_TO);
    int adjustmentToQty = adjustmentTO.get(INVENTORY_ADJUSTMENT_QTY).getAsInt();
    String adjustmentToQtyUOM = adjustmentTO.get(INVENTORY_ADJUSTMENT_QTY_UOM).getAsString();

    Container containerPartialVtr = container;
    containerPartialVtr.getContainerItems().get(0).setVnpkWgtQty(1f);
    containerPartialVtr.getContainerItems().get(0).setVnpkWgtUom(Uom.LB);

    doReturn(containerPartialVtr)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(trackingId);
    when(deliveryService.getDeliveryByDeliveryNumber(121212121L, httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"PNDFNL\"}");
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);

    witronContainerService.processVTR(
        trackingId, httpHeaders, adjustmentToQty, adjustmentToQtyUOM, availableToSellQty);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());

    ArgumentCaptor<Container> argumentCaptorRtuContainer = ArgumentCaptor.forClass(Container.class);
    ArgumentCaptor<String> argumentCaptorRtuAction = ArgumentCaptor.forClass(String.class);
    verify(gdcPutawayPublisher, times(1))
        .publishMessage(
            argumentCaptorRtuContainer.capture(), argumentCaptorRtuAction.capture(), any());
    Container container_RTU = argumentCaptorRtuContainer.getValue();
    assertEquals(container_RTU.getTrackingId(), "030125103317445953");
    assertEquals(container_RTU.getWeight(), 5.0f);
    assertEquals(container_RTU.getWeightUOM(), Uom.LB);

    final ContainerItem containerItem_RTU = container_RTU.getContainerItems().get(0);
    AssertJUnit.assertSame(containerItem_RTU.getQuantity(), 30);
    AssertJUnit.assertSame(containerItem_RTU.getActualHi(), 1);

    String rtuAction = argumentCaptorRtuAction.getValue();
    assertEquals(rtuAction, "update");
  }

  @Test
  public void test_processVTR_partial_VTR_HappyPath() throws ReceivingException {
    final ContainerItem containerItem = container.getContainerItems().get(0);
    containerItem.setVnpkWgtQty(1f);
    containerItem.setVnpkWgtUom("LB");

    doReturn(container)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(trackingId);
    when(deliveryService.getDeliveryByDeliveryNumber(121212121L, httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"PNDFNL\"}");
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);

    int full_VTR_Quantity = -6; // multiple of 6 as vnpk is 6
    witronContainerService.processVTR(trackingId, httpHeaders, full_VTR_Quantity, EACHES, 480);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  @Test
  public void test_ProcessVTR_ContainerAlreadyBackout() {
    container = MockContainer.getContainerInfo();
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    doReturn(container)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(trackingId);

    try {
      witronContainerService.processVTR(trackingId, httpHeaders, -1, EACHES, 0);
      fail();
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG);
      assertEquals(e.getHttpStatus(), HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * This method will test the processVTR
   *
   * @throws ReceivingException
   */
  @Test
  public void test_processVTR_full_VTR_HappyPath_deliveryStatus_WRK() throws ReceivingException {

    doReturn(container)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(trackingId);
    when(deliveryService.getDeliveryByDeliveryNumber(121212121L, httpHeaders))
        .thenReturn("{\"deliveryNumber\":1234,\"deliveryStatus\":\"WRK\"}");
    doNothing()
        .when(gdcPutawayPublisher)
        .publishMessage(container, ReceivingConstants.PUTAWAY_DELETE_ACTION, httpHeaders);

    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), anyMap()))
        .thenReturn(null);

    int full_VTR_Quantity = -480;
    witronContainerService.processVTR(trackingId, httpHeaders, full_VTR_Quantity, EACHES, 0);
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), any(), any());
  }

  @Test
  public void testValidateRequestForGls() {
    try {
      witronContainerService.validateRequestForGls(containerItem);
      assert (true);
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testValidateRequestForGls_full_AutomationDc() {
    // setup
    doReturn(false).when(gdcFlagReader).isDCOneAtlasEnabled();
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();
    try {
      witronContainerService.validateRequestForGls(containerItem);
      assert (true);
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testValidateRequestForGls_fullGls() {
    // setup
    doReturn(false).when(gdcFlagReader).isDCOneAtlasEnabled();
    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();
    try {
      witronContainerService.validateRequestForGls(containerItem);
      fail();
    } catch (ReceivingException e) {
      final ErrorResponse err = e.getErrorResponse();
      assertEquals(err.getErrorCode(), INVALID_REQUEST);
      assertEquals(err.getErrorMessage(), LABEL_ON_HOLD_REQUEST_IN_GLS_INSTEAD_OF_ATLAS);
    }
  }

  @Test
  public void testValidateRequestForGls_oneAtlas_NotConverted() {
    // setup
    doReturn(true).when(gdcFlagReader).isDCOneAtlasEnabled();
    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();
    try {
      witronContainerService.validateRequestForGls(containerItem);
      fail();
    } catch (ReceivingException e) {
      final ErrorResponse err = e.getErrorResponse();
      assertEquals(err.getErrorCode(), INVALID_REQUEST);
      assertEquals(err.getErrorMessage(), LABEL_ON_HOLD_REQUEST_IN_GLS_INSTEAD_OF_ATLAS);
    }
  }

  @Test
  public void testValidateRequestForGls_oneAtlas_Converted() {
    // setup
    doReturn(false).when(gdcFlagReader).isDCOneAtlasEnabled();
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();
    try {
      ContainerItem containerItem = new ContainerItem();
      Map<String, String> containerItemMiscInfo = new HashMap<>();
      containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
      containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
      witronContainerService.validateRequestForGls(containerItem);
      assert (true);
    } catch (ReceivingException e) {
      fail();
    }
  }
}
