package com.walmart.move.nim.receiving.fixture.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.InventoryLocationUpdateRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.fixture.client.ItemMDMServiceClient;
import com.walmart.move.nim.receiving.fixture.client.ItemREPServiceClient;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.entity.ControlTowerTracker;
import com.walmart.move.nim.receiving.fixture.mock.data.FixtureMockData;
import com.walmart.move.nim.receiving.fixture.model.CTWarehouseResponse;
import com.walmart.move.nim.receiving.fixture.model.PalletItem;
import com.walmart.move.nim.receiving.fixture.model.PalletMapLPNRequest;
import com.walmart.move.nim.receiving.fixture.model.PalletPutAwayRequest;
import com.walmart.move.nim.receiving.fixture.model.PalletPutAwayResponse;
import com.walmart.move.nim.receiving.fixture.model.PalletReceiveRequest;
import com.walmart.move.nim.receiving.fixture.model.PalletReceiveResponse;
import com.walmart.move.nim.receiving.fixture.model.PutAwayInventory;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class PalletReceivingServiceTest extends ReceivingTestBase {

  @InjectMocks private PalletReceivingService palletReceivingService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private FixtureManagedConfig fixtureManagedConfig;
  @Mock private DeliveryServiceImpl deliveryService;
  @Mock private ControlTowerService controlTowerService;
  @Mock InventoryService inventoryService;
  @Mock private ItemREPServiceClient itemREPServiceClient;
  @Mock private ContainerService containerService;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Captor private ArgumentCaptor<List<Container>> containerList;
  @Captor private ArgumentCaptor<List<ContainerItem>> containerItemList;
  @Mock private Transformer<Container, ContainerDTO> transformer;

  @Mock private AppConfig appConfig;
  @Mock private ItemMDMServiceClient itemMDMServiceClient;
  private static Gson gson = new Gson();
  @Mock private SlottingRestApiClient slottingRestApiClient;
  @Mock private MovePublisher movePublisher;
  @Mock private SlottingPalletRequest mockSlottingPalletRequest;
  @Mock LPNCacheService lpnCacheService;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void tearDown() {
    reset(containerPersisterService);
    reset(deliveryService);
    reset(fixtureManagedConfig);
    reset(controlTowerService);
    reset(containerService);
    reset(deliveryMetaDataService);
    reset(configUtils);
    reset(inventoryService);
  }

  @Test
  public void testProcessShipmentEvent() {
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getShipmentAddedEventPayload(), DeliveryUpdateMessage.class);
    when(deliveryMetaDataService.findByDeliveryNumber(any())).thenReturn(Optional.empty());

    palletReceivingService.processShipmentEvent(deliveryUpdateMessage);

    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(containerList.capture(), containerItemList.capture());
    List<Container> containers = containerList.getValue();
    List<ContainerItem> containerItems = containerItemList.getValue();
    assertEquals(containers.size(), 1);
    assertEquals(containerItems.size(), 1);

    Container container = containers.get(0);
    assertEquals(
        container.getTrackingId(),
        deliveryUpdateMessage.getPayload().getPacks().get(0).getPackNumber());
    assertEquals(
        container.getMessageId(),
        deliveryUpdateMessage.getPayload().getPacks().get(0).getPackNumber());
    assertEquals(
        container.getParentTrackingId(),
        deliveryUpdateMessage.getPayload().getPacks().get(0).getPackNumber());
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_PENDING_COMPLETE);
    assertEquals(container.getInventoryStatus(), InventoryStatus.AVAILABLE.name());
    assertEquals(
        container.getDestination().get("buNumber"),
        deliveryUpdateMessage.getPayload().getPacks().get(0).getHandledOnBehalfOf().getNumber());

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(
        containerItem.getTrackingId(),
        deliveryUpdateMessage.getPayload().getPacks().get(0).getPackNumber());
    assertEquals(
        containerItem.getItemNumber(),
        deliveryUpdateMessage.getPayload().getPacks().get(0).getItems().get(0).getItemNumber());
    assertEquals(
        containerItem.getDescription(),
        deliveryUpdateMessage
            .getPayload()
            .getPacks()
            .get(0)
            .getItems()
            .get(0)
            .getItemDescription());
    assertEquals(
        containerItem.getPurchaseReferenceNumber(),
        deliveryUpdateMessage
            .getPayload()
            .getPacks()
            .get(0)
            .getItems()
            .get(0)
            .getPurchaseOrder()
            .getPoNumber());
    assertEquals(
        containerItem.getPurchaseReferenceLineNumber().toString(),
        deliveryUpdateMessage
            .getPayload()
            .getPacks()
            .get(0)
            .getItems()
            .get(0)
            .getPurchaseOrder()
            .getPoLineNumber());
    assertEquals(
        containerItem.getQuantity(),
        Integer.valueOf(
            deliveryUpdateMessage
                .getPayload()
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        containerItem.getQuantityUOM(),
        deliveryUpdateMessage
            .getPayload()
            .getPacks()
            .get(0)
            .getItems()
            .get(0)
            .getInventoryDetail()
            .getReportedUom());
    assertEquals(containerItem.getVnpkQty(), FixtureConstants.DEFAULT_VNPK_QTY);
    assertEquals(containerItem.getWhpkQty(), FixtureConstants.DEFAULT_WHPK_QTY);
    assertEquals(containerItem.getBaseDivisionCode(), ReceivingConstants.BASE_DIVISION_CODE);
    assertEquals(containerItem.getFacilityCountryCode(), TenantContext.getFacilityCountryCode());

    verify(deliveryMetaDataService, times(1))
        .findByDeliveryNumber(
            String.valueOf(
                deliveryUpdateMessage.getPayload().getShipment().getShipmentNumber().hashCode()));
    ArgumentCaptor<DeliveryMetaData> deliveryMetaDataArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryMetaData.class);
    verify(deliveryMetaDataService, times(1)).save(deliveryMetaDataArgumentCaptor.capture());
    assertEquals(
        deliveryMetaDataArgumentCaptor.getValue().getDeliveryNumber(),
        String.valueOf(
            deliveryUpdateMessage.getPayload().getShipment().getShipmentNumber().hashCode()));
    assertEquals(
        deliveryMetaDataArgumentCaptor.getValue().getTrailerNumber(),
        deliveryUpdateMessage.getPayload().getShipment().getShipmentDetail().getLoadNumber());
  }

  @Test
  public void testProcessShipmentUpdateEventWhenNoContainerExistsForDelivery() {
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getShipmentUpdateEventPayload(), DeliveryUpdateMessage.class);

    palletReceivingService.processShipmentEvent(deliveryUpdateMessage);

    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(containerList.capture(), containerItemList.capture());
    List<Container> containers = containerList.getValue();
    List<ContainerItem> containerItems = containerItemList.getValue();
    assertEquals(containers.size(), 2);
    assertEquals(containerItems.size(), 3);

    Container container =
        containers
            .stream()
            .filter(container1 -> container1.getTrackingId().equals("B32899000020011086"))
            .collect(Collectors.toList())
            .get(0);
    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_PENDING_COMPLETE);
    assertEquals(container.getDestination().get("buNumber"), "6094");

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011086");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(561301081));
    assertEquals(containerItem.getDescription(), "Party Material");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(1120));
    assertEquals(containerItem.getQuantityUOM(), "EA");

    container =
        containers
            .stream()
            .filter(container1 -> container1.getTrackingId().equals("B32899000020011087"))
            .collect(Collectors.toList())
            .get(0);
    assertEquals(container.getTrackingId(), "B32899000020011087");
    assertEquals(container.getMessageId(), "B32899000020011087");
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_PENDING_COMPLETE);
    assertEquals(container.getDestination().get("buNumber"), "6094");

    containerItem =
        containerItems
            .stream()
            .filter(containerItem1 -> containerItem1.getItemNumber() == 671301082)
            .collect(Collectors.toList())
            .get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011087");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(671301082));
    assertEquals(containerItem.getDescription(), "Red Pen");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(2));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(20));
    assertEquals(containerItem.getQuantityUOM(), "EA");

    containerItem =
        containerItems
            .stream()
            .filter(containerItem1 -> containerItem1.getItemNumber() == 671301083)
            .collect(Collectors.toList())
            .get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011087");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(671301083));
    assertEquals(containerItem.getDescription(), "Black Pen");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(3));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(30));
    assertEquals(containerItem.getQuantityUOM(), "EA");
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataService, times(1)).save(any());
  }

  @Test
  public void testProcessShipmentUpdateEvent() {
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getShipmentUpdateEventPayload(), DeliveryUpdateMessage.class);
    long deliveryNumberHash =
        deliveryUpdateMessage.getPayload().getShipment().getShipmentNumber().hashCode();

    List<Container> cl = new ArrayList<>();
    Container pendingContainer = FixtureMockData.getPendingContainer();
    pendingContainer.setDeliveryNumber(
        (long) deliveryUpdateMessage.getPayload().getShipment().getShipmentNumber().hashCode());

    cl.add(pendingContainer);
    when(containerPersisterService.getContainerByDeliveryNumber(deliveryNumberHash)).thenReturn(cl);

    palletReceivingService.processShipmentEvent(deliveryUpdateMessage);

    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(containerList.capture(), containerItemList.capture());
    List<Container> containers = containerList.getValue();
    List<ContainerItem> containerItems = containerItemList.getValue();
    assertEquals(containers.size(), 2);
    assertEquals(containerItems.size(), 3);

    Container container =
        containers
            .stream()
            .filter(container1 -> container1.getTrackingId().equals("B32899000020011086"))
            .collect(Collectors.toList())
            .get(0);
    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_PENDING_COMPLETE);
    assertEquals(container.getDestination().get("buNumber"), "6094");

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011086");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(561301081));
    assertEquals(containerItem.getDescription(), "Party Material");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(1120));
    assertEquals(containerItem.getQuantityUOM(), "EA");

    container =
        containers
            .stream()
            .filter(container1 -> container1.getTrackingId().equals("B32899000020011087"))
            .collect(Collectors.toList())
            .get(0);
    assertEquals(container.getTrackingId(), "B32899000020011087");
    assertEquals(container.getMessageId(), "B32899000020011087");
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_PENDING_COMPLETE);
    assertEquals(container.getDestination().get("buNumber"), "6094");

    containerItem =
        containerItems
            .stream()
            .filter(containerItem1 -> containerItem1.getItemNumber() == 671301082)
            .collect(Collectors.toList())
            .get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011087");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(671301082));
    assertEquals(containerItem.getDescription(), "Red Pen");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(2));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(20));
    assertEquals(containerItem.getQuantityUOM(), "EA");

    containerItem =
        containerItems
            .stream()
            .filter(containerItem1 -> containerItem1.getItemNumber() == 671301083)
            .collect(Collectors.toList())
            .get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011087");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(671301083));
    assertEquals(containerItem.getDescription(), "Black Pen");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(3));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(30));
    assertEquals(containerItem.getQuantityUOM(), "EA");

    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
    verify(deliveryMetaDataService, times(0)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataService, times(0)).save(any());
  }

  @Test
  public void testProcessShipmentUpdateEventWhenOnePalletIsReceived() {
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getShipmentUpdateEventPayload(), DeliveryUpdateMessage.class);
    long deliveryNumberHash =
        deliveryUpdateMessage.getPayload().getShipment().getShipmentNumber().hashCode();

    List<Container> cl = new ArrayList<>();
    Container receivedContainer = FixtureMockData.getPendingContainer();
    receivedContainer.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    receivedContainer.setTrackingId("LPN 10656 INV 4784");
    receivedContainer.setDeliveryNumber(deliveryNumberHash);
    receivedContainer.getContainerItems().get(0).setTrackingId("LPN 10656 INV 4784");
    cl.add(receivedContainer);
    when(containerPersisterService.getContainerByDeliveryNumber(deliveryNumberHash)).thenReturn(cl);

    palletReceivingService.processShipmentEvent(deliveryUpdateMessage);

    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(containerList.capture(), containerItemList.capture());
    List<Container> containers = containerList.getValue();
    List<ContainerItem> containerItems = containerItemList.getValue();
    assertEquals(containers.size(), 2);
    assertEquals(containerItems.size(), 3);

    Container container =
        containers
            .stream()
            .filter(container1 -> container1.getTrackingId().equals("LPN 10656 INV 4784"))
            .collect(Collectors.toList())
            .get(0);
    assertEquals(container.getTrackingId(), "LPN 10656 INV 4784");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_COMPLETE);
    assertEquals(container.getDestination().get("buNumber"), "6001");

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "LPN 10656 INV 4784");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(561301081));
    assertEquals(containerItem.getDescription(), "Part");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "2356789123");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(5));
    assertEquals(containerItem.getQuantityUOM(), "EA");

    container =
        containers
            .stream()
            .filter(container1 -> container1.getTrackingId().equals("B32899000020011087"))
            .collect(Collectors.toList())
            .get(0);
    assertEquals(container.getTrackingId(), "B32899000020011087");
    assertEquals(container.getMessageId(), "B32899000020011087");
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_PENDING_COMPLETE);
    assertEquals(container.getDestination().get("buNumber"), "6094");

    containerItem =
        containerItems
            .stream()
            .filter(containerItem1 -> containerItem1.getItemNumber() == 671301082)
            .collect(Collectors.toList())
            .get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011087");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(671301082));
    assertEquals(containerItem.getDescription(), "Red Pen");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(2));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(20));
    assertEquals(containerItem.getQuantityUOM(), "EA");

    containerItem =
        containerItems
            .stream()
            .filter(containerItem1 -> containerItem1.getItemNumber() == 671301083)
            .collect(Collectors.toList())
            .get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011087");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(671301083));
    assertEquals(containerItem.getDescription(), "Black Pen");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(3));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(30));
    assertEquals(containerItem.getQuantityUOM(), "EA");
    verify(deliveryMetaDataService, times(0)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataService, times(0)).save(any());
  }

  @Test
  public void testProcessShipmentAddedEventForPalletReceivedWithoutASN() {
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getShipmentUpdateEventPayload(), DeliveryUpdateMessage.class);
    long deliveryNumberHash =
        deliveryUpdateMessage.getPayload().getShipment().getShipmentNumber().hashCode();

    List<Container> cl = new ArrayList<>();
    Container activeContainer = FixtureMockData.getCompletedContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_COMPLETE_NO_ASN);
    activeContainer.setTrackingId("LPN 10656 INV 4784");
    activeContainer.getContainerItems().get(0).setPurchaseReferenceLineNumber(null);
    activeContainer.getContainerItems().get(0).setPurchaseReferenceNumber(null);
    activeContainer.getContainerItems().get(0).setTrackingId("LPN 10656 INV 4784");

    cl.add(activeContainer);
    when(containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            anyList(), eq(FixtureConstants.CONTAINER_STATUS_WO_ASN)))
        .thenReturn(cl);

    ControlTowerTracker controlTowerTracker =
        ControlTowerTracker.builder()
            .id(1L)
            .lpn("LPN 10656 INV 4784")
            .submissionStatus(EventTargetStatus.PENDING)
            .build();

    when(controlTowerService.resetForTracking("LPN 10656 INV 4784"))
        .thenReturn(controlTowerTracker);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), FixtureConstants.IS_CT_ENABLED, false))
        .thenReturn(true);

    palletReceivingService.processShipmentEvent(deliveryUpdateMessage);

    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(
            captor.capture(), eq(FixtureConstants.CONTAINER_STATUS_WO_ASN));
    assertEquals(captor.getValue().size(), 2);

    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(containerList.capture(), containerItemList.capture());
    List<Container> containers = containerList.getValue();
    List<ContainerItem> containerItems = containerItemList.getValue();
    assertEquals(containers.size(), 1);
    assertEquals(containerItems.size(), 1);

    Container container = containers.get(0);
    assertEquals(container.getDeliveryNumber(), Long.valueOf(deliveryNumberHash));
    assertEquals(container.getTrackingId(), "LPN 10656 INV 4784");
    assertEquals(container.getParentTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getLastChangedUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_COMPLETE);
    assertEquals(container.getDestination().get("buNumber"), "6094");

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "LPN 10656 INV 4784");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(561301081));
    assertEquals(containerItem.getDescription(), "Part");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(5));
    assertEquals(containerItem.getOrderableQuantity(), Integer.valueOf(1120));
    assertEquals(containerItem.getQuantityUOM(), "EA");

    verify(controlTowerService, times(1)).resetForTracking("LPN 10656 INV 4784");
    ArgumentCaptor<List<PutAwayInventory>> putAwayCaptor = ArgumentCaptor.forClass(List.class);
    verify(controlTowerService, times(1))
        .putAwayInventory(putAwayCaptor.capture(), eq(controlTowerTracker));
    List<PutAwayInventory> putAwayCaptorValue = putAwayCaptor.getValue();
    assertNotNull(putAwayCaptorValue);
    assertEquals(putAwayCaptorValue.get(0).getPalletId(), "B32899000020011086");
    assertEquals(putAwayCaptorValue.get(0).getLpn(), "LPN 10656 INV 4784");
    assertEquals(putAwayCaptorValue.get(0).getDestination(), "6094");
    assertEquals(putAwayCaptorValue.get(0).getPutAwayLocation(), "F-012");
    assertEquals(putAwayCaptorValue.get(0).getItems().size(), 1);

    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getId(), "561301081");
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getDescription(), "Part");
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getQuantity(), 5);
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getDestination(), "6094");
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getPurchaseOrder(), "3001747108");
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getPoLineNumber(), "1");

    verify(containerService, times(1)).publishMultipleContainersToInventory(any());
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataService, times(1)).save(any());
  }

  @Test
  public void testProcessShipmentAddedEventForPalletReceivedWithoutASN2() {
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getShipmentAddedEventPayload(), DeliveryUpdateMessage.class);
    long deliveryNumberHash =
        deliveryUpdateMessage.getPayload().getShipment().getShipmentNumber().hashCode();

    List<Container> cl = new ArrayList<>();
    Container activeContainer = FixtureMockData.getActiveContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_ACTIVE_NO_ASN);
    activeContainer.setTrackingId("LPN 10656 INV 4784");
    activeContainer.getContainerItems().get(0).setPurchaseReferenceLineNumber(null);
    activeContainer.getContainerItems().get(0).setPurchaseReferenceNumber(null);
    activeContainer.getContainerItems().get(0).setTrackingId("LPN 10656 INV 4784");

    cl.add(activeContainer);
    when(containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            anyList(), eq(FixtureConstants.CONTAINER_STATUS_WO_ASN)))
        .thenReturn(cl);

    palletReceivingService.processShipmentEvent(deliveryUpdateMessage);

    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(containerList.capture(), containerItemList.capture());
    List<Container> containers = containerList.getValue();
    List<ContainerItem> containerItems = containerItemList.getValue();
    assertEquals(containers.size(), 1);
    assertEquals(containerItems.size(), 1);

    Container container = containers.get(0);
    assertEquals(container.getDeliveryNumber(), Long.valueOf(deliveryNumberHash));
    assertEquals(container.getTrackingId(), "LPN 10656 INV 4784");
    assertEquals(container.getParentTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getLastChangedUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);
    assertEquals(container.getDestination().get("buNumber"), "6094");

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "LPN 10656 INV 4784");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(561301081));
    assertEquals(containerItem.getDescription(), "Part");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(5));
    assertEquals(containerItem.getOrderableQuantity(), Integer.valueOf(1120));
    assertEquals(containerItem.getQuantityUOM(), "EA");

    verify(controlTowerService, times(0)).resetForTracking(any());
    verify(controlTowerService, times(0)).putAwayInventory(any(), any());
    verify(containerService, times(0)).publishMultipleContainersToInventory(any());
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataService, times(1)).save(any());
  }

  @Test
  public void testReceive() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");
    Container pendingContainer = FixtureMockData.getPendingContainer();
    when(containerPersisterService.getContainerDetails("B32899000020011086"))
        .thenReturn(pendingContainer);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(
            Optional.of(
                DeliveryMetaData.builder()
                    .deliveryNumber(String.valueOf(pendingContainer.getDeliveryNumber()))
                    .trailerNumber("88528711")
                    .build()));

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6001");
    assertEquals(palletReceiveResponse.getLoadNumber(), "88528711");
    assertNotNull(palletReceiveResponse.getItems());
    PalletItem palletItem = palletReceiveResponse.getItems().get(0);
    assertEquals(palletItem.getItemNumber(), Long.valueOf(561301081L));
    assertEquals(palletItem.getReceivedQty(), Integer.valueOf(5));
    assertEquals(palletItem.getOrderedQty(), Integer.valueOf(5));
    assertEquals(palletItem.getQuantityUOM(), "EA");
    assertEquals(palletItem.getItemDescription(), "Part");
    assertEquals(palletItem.getPurchaseReferenceNumber(), "2356789123");
    assertEquals(palletItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(anyString());
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);

    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), "sysadmin");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011086");
    assertEquals(containerItem.getQuantity(), Integer.valueOf(5));
    assertEquals(containerItem.getOrderableQuantity(), Integer.valueOf(5));

    verify(deliveryMetaDataService, times(1))
        .findByDeliveryNumber(String.valueOf(pendingContainer.getDeliveryNumber()));
  }

  @Test
  public void testReceive2() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");
    List<PalletItem> palletItemList = new ArrayList<>();
    PalletItem.builder().itemNumber(561301081L).build();
    palletReceiveRequest.setItems(palletItemList);

    when(containerPersisterService.getContainerDetails("B32899000020011086"))
        .thenReturn(FixtureMockData.getPendingContainer());
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6001");
    assertNotNull(palletReceiveResponse.getItems());
    PalletItem palletItem = palletReceiveResponse.getItems().get(0);
    assertEquals(palletItem.getItemNumber(), Long.valueOf(561301081L));
    assertEquals(palletItem.getReceivedQty(), Integer.valueOf(5));
    assertEquals(palletItem.getOrderedQty(), Integer.valueOf(5));
    assertEquals(palletItem.getQuantityUOM(), "EA");
    assertEquals(palletItem.getItemDescription(), "Part");
    assertEquals(palletItem.getPurchaseReferenceNumber(), "2356789123");
    assertEquals(palletItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(anyString());
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);

    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), "sysadmin");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011086");
    assertEquals(containerItem.getQuantity(), Integer.valueOf(5));
    assertEquals(containerItem.getOrderableQuantity(), Integer.valueOf(5));
  }

  @Test
  public void testReceive_PalletNotPresentInReceiving() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");

    when(containerPersisterService.getContainerDetails("B32899000020011086")).thenReturn(null);

    SsccScanResponse packScanResp =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getGlobalPackScanResponse(), SsccScanResponse.class);

    when(deliveryService.globalPackSearch(eq("B32899000020011086"), any()))
        .thenReturn(packScanResp);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6094");
    assertFalse(palletReceiveResponse.isAuditRequired());
    assertNotNull(palletReceiveResponse.getItems());
    PalletItem palletItem = palletReceiveResponse.getItems().get(0);
    assertEquals(
        palletItem.getItemNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemNumber());
    assertEquals(
        palletItem.getReceivedQty(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        palletItem.getOrderedQty(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        palletItem.getQuantityUOM(),
        packScanResp.getPacks().get(0).getItems().get(0).getInventoryDetail().getReportedUom());
    assertEquals(
        palletItem.getItemDescription(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemDescription());
    assertEquals(
        palletItem.getPurchaseReferenceNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoNumber());
    assertEquals(palletItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));

    verify(deliveryService, times(1)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(1))
        .getContainerDetailsByParentTrackingId(eq("B32899000020011086"));
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);
    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), "sysadmin");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011086");
    assertEquals(
        containerItem.getOrderableQuantity(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        containerItem.getItemNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemNumber());
    assertEquals(
        containerItem.getDescription(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemDescription());
    assertEquals(
        containerItem.getPurchaseReferenceNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoNumber());
    assertEquals(
        containerItem.getPurchaseReferenceLineNumber().toString(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoLineNumber());
    assertEquals(
        containerItem.getQuantity(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        containerItem.getQuantityUOM(),
        packScanResp.getPacks().get(0).getItems().get(0).getInventoryDetail().getReportedUom());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp =
          "Multiple pallets found for pallet ID B32899000020011086. Please contact your supervisor.")
  public void testReceive_PalletNotPresentInReceivingAndMultiplePalletsINGDM() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");

    when(containerPersisterService.getContainerDetails("B32899000020011086")).thenReturn(null);

    SsccScanResponse packScanResp =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getMultipleGlobalPackScanResponse(), SsccScanResponse.class);

    when(deliveryService.globalPackSearch(eq("B32899000020011086"), any()))
        .thenReturn(packScanResp);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);
    palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    verify(deliveryService, times(1)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(1))
        .getContainerDetailsByParentTrackingId(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).saveContainer(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "B32899000020011086 was not found in shipment details. Please contact your supervisor.")
  public void testReceive_PalletNotPresentInReceivingAndGDM() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");

    when(containerPersisterService.getContainerDetails("B32899000020011086")).thenReturn(null);

    SsccScanResponse packScanResp = new SsccScanResponse();

    when(deliveryService.globalPackSearch(eq("B32899000020011086"), any()))
        .thenReturn(packScanResp);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);
    palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    verify(deliveryService, times(1)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(1))
        .getContainerDetailsByParentTrackingId(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).saveContainer(any());
  }

  @Test()
  public void testReceiveAuditRequired() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");

    Container pendingContainer = FixtureMockData.getPendingContainer();
    ContainerItem containerItem = FixtureMockData.getContainerItem(561301082L);
    containerItem.setOrderableQuantity(2);
    pendingContainer.getContainerItems().add(containerItem);
    when(containerPersisterService.getContainerDetails("B32899000020011086"))
        .thenReturn(pendingContainer);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(4);

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6001");
    assertTrue(palletReceiveResponse.isAuditRequired());
    assertNotNull(palletReceiveResponse.getItems());
    assertEquals(palletReceiveResponse.getItems().size(), 2);

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(any());
    verify(containerPersisterService, times(0)).saveContainerAndContainerItems(any(), any());
  }

  @Test
  public void testReceiveNotPresentInReceivingAndIsAuditable() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");

    when(containerPersisterService.getContainerDetails("B32899000020011086")).thenReturn(null);

    SsccScanResponse packScanResp =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getGlobalPackScanResponse(), SsccScanResponse.class);

    when(deliveryService.globalPackSearch(eq("B32899000020011086"), any()))
        .thenReturn(packScanResp);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(1);

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6094");
    assertTrue(palletReceiveResponse.isAuditRequired());
    assertNotNull(palletReceiveResponse.getItems());
    PalletItem palletItem = palletReceiveResponse.getItems().get(0);
    assertEquals(
        palletItem.getItemNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemNumber());
    assertEquals(
        palletItem.getReceivedQty(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        palletItem.getOrderedQty(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        palletItem.getQuantityUOM(),
        packScanResp.getPacks().get(0).getItems().get(0).getInventoryDetail().getReportedUom());
    assertEquals(
        palletItem.getItemDescription(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemDescription());
    assertEquals(
        palletItem.getPurchaseReferenceNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoNumber());
    assertEquals(palletItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));

    verify(deliveryService, times(1)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(1)).getContainerDetailsByParentTrackingId(any());
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);
    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), "sysadmin");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_PENDING_COMPLETE);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011086");
    assertEquals(
        containerItem.getOrderableQuantity(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        containerItem.getItemNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemNumber());
    assertEquals(
        containerItem.getDescription(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemDescription());
    assertEquals(
        containerItem.getPurchaseReferenceNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoNumber());
    assertEquals(
        containerItem.getPurchaseReferenceLineNumber().toString(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoLineNumber());
    assertEquals(
        containerItem.getQuantity(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        containerItem.getQuantityUOM(),
        packScanResp.getPacks().get(0).getItems().get(0).getInventoryDetail().getReportedUom());
  }

  @Test
  public void testReceiveAuditRequest() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");
    palletReceiveRequest.setItems(
        Collections.singletonList(PalletItem.builder().itemNumber(2222L).receivedQty(20).build()));

    Container pendingContainer = FixtureMockData.getPendingContainer();
    ContainerItem item = FixtureMockData.getContainerItem(2222L);
    item.setQuantity(6);
    item.setOrderableQuantity(6);
    List<ContainerItem> containerItems1 = new ArrayList<>(pendingContainer.getContainerItems());
    containerItems1.add(item);
    pendingContainer.setContainerItems(containerItems1);

    when(containerPersisterService.getContainerDetails("B32899000020011086"))
        .thenReturn(pendingContainer);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6001");
    assertNotNull(palletReceiveResponse.getItems());
    assertEquals(palletReceiveResponse.getItems().size(), 1);

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(any());
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);
    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), "sysadmin");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011086");
    Integer receivedQty = 5;
    Integer orderedQty = 5;
    if (containerItem.getItemNumber() == 2222L) {
      receivedQty = 20;
      orderedQty = 6;
    }
    assertEquals(containerItem.getQuantity(), receivedQty);
    assertEquals(containerItem.getOrderableQuantity(), orderedQty);

    ArgumentCaptor<List<ContainerItem>> itemArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(containerPersisterService, times(1)).deleteContainerItems(itemArgumentCaptor.capture());
    assertEquals(itemArgumentCaptor.getValue().size(), 1);
  }

  @Test
  public void testReceiveAuditRequestAfterReceiving() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");
    palletReceiveRequest.setItems(
        Collections.singletonList(PalletItem.builder().itemNumber(2222L).receivedQty(20).build()));
    palletReceiveRequest.setStoreNumber("4279");

    Container receivedContainer = FixtureMockData.getPendingContainer();
    receivedContainer.setTrackingId("B32899000020011086");
    receivedContainer.setContainerStatus(ReceivingConstants.STATUS_ACTIVE);

    ContainerItem item = FixtureMockData.getContainerItem(2222L);
    item.setQuantity(6);
    item.setOrderableQuantity(6);
    List<ContainerItem> containerItems1 = new ArrayList<>(receivedContainer.getContainerItems());
    containerItems1.add(item);
    receivedContainer.setContainerItems(containerItems1);

    when(containerPersisterService.getContainerDetails("B32899000020011086"))
        .thenReturn(receivedContainer);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "4279");
    assertNotNull(palletReceiveResponse.getItems());
    assertEquals(palletReceiveResponse.getItems().size(), 1);

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(any());
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);
    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), "sysadmin");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);
    assertEquals(container.getDestination().get(ReceivingConstants.BU_NUMBER), "4279");

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011086");
    Integer receivedQty = 5;
    Integer orderedQty = 5;
    if (containerItem.getItemNumber() == 2222L) {
      receivedQty = 20;
      orderedQty = 6;
    }
    assertEquals(containerItem.getQuantity(), receivedQty);
    assertEquals(containerItem.getOrderableQuantity(), orderedQty);

    ArgumentCaptor<List<ContainerItem>> itemArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(containerPersisterService, times(1)).deleteContainerItems(itemArgumentCaptor.capture());
    assertEquals(itemArgumentCaptor.getValue().size(), 1);
  }

  @Test
  public void testReceiveAuditRequestAddNewItem() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");
    List<PalletItem> list = new ArrayList<>();
    list.add(PalletItem.builder().itemNumber(2222L).receivedQty(20).build());
    list.add(PalletItem.builder().itemNumber(3333L).receivedQty(5).itemDescription("test").build());
    palletReceiveRequest.setItems(list);

    Container pendingContainer = FixtureMockData.getPendingContainer();
    ContainerItem item = FixtureMockData.getContainerItem(2222L);
    item.setQuantity(6);
    item.setOrderableQuantity(6);
    List<ContainerItem> containerItems1 = new ArrayList<>(pendingContainer.getContainerItems());
    containerItems1.add(item);
    pendingContainer.setContainerItems(containerItems1);

    when(containerPersisterService.getContainerDetails("B32899000020011086"))
        .thenReturn(pendingContainer);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(0);

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6001");
    assertNotNull(palletReceiveResponse.getItems());
    assertEquals(palletReceiveResponse.getItems().size(), 2);

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(any());
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 2);
    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), "sysadmin");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);

    containerItems.forEach(
        containerItem -> {
          assertEquals(containerItem.getTrackingId(), "B32899000020011086");
          Integer receivedQty = 5;
          Integer orderedQty = 5;
          if (containerItem.getItemNumber() == 2222L) {
            receivedQty = 20;
            orderedQty = 6;
          } else if (containerItem.getItemNumber() == 3333L) {
            assertEquals(containerItem.getDescription(), "test");
            orderedQty = 0;
          }
          assertEquals(containerItem.getQuantity(), receivedQty);
          assertEquals(containerItem.getOrderableQuantity(), orderedQty);
        });

    ArgumentCaptor<List<ContainerItem>> itemArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(containerPersisterService, times(1)).deleteContainerItems(itemArgumentCaptor.capture());
    assertEquals(itemArgumentCaptor.getValue().size(), 1);
  }

  @Test
  public void testReceiveWithoutASN() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");
    palletReceiveRequest.setReceiveWithoutASN(true);
    palletReceiveRequest.setStoreNumber("6094");
    palletReceiveRequest.setItems(
        Collections.singletonList(
            PalletItem.builder()
                .itemNumber(2222L)
                .receivedQty(20)
                .itemDescription("Party Material")
                .build()));

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6094");
    assertNotNull(palletReceiveResponse.getItems());
    PalletItem palletItem = palletReceiveResponse.getItems().get(0);
    assertEquals(palletItem.getItemNumber(), Long.valueOf(2222L));
    assertEquals(palletItem.getReceivedQty(), Integer.valueOf(20));
    assertEquals(palletItem.getOrderedQty(), Integer.valueOf(0));
    assertEquals(palletItem.getQuantityUOM(), "EA");
    assertEquals(palletItem.getItemDescription(), "Party Material");
    assertNull(palletItem.getPurchaseReferenceNumber());
    assertNull(palletItem.getPurchaseReferenceLineNumber());

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(0)).getContainerDetails(any());
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(any());
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);

    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), "sysadmin");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE_NO_ASN);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011086");
    assertEquals(containerItem.getQuantity(), Integer.valueOf(20));
    assertEquals(containerItem.getOrderableQuantity(), Integer.valueOf(0));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Item details are missing. Please provide itemNumber, receivedQty & itemDescription.")
  public void testReceiveWithoutASNItemInfoMissing() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");
    palletReceiveRequest.setReceiveWithoutASN(true);
    palletReceiveRequest.setStoreNumber("6094");
    palletReceiveRequest.setItems(
        Collections.singletonList(PalletItem.builder().itemNumber(2222L).receivedQty(20).build()));

    palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(0)).getContainerDetails(any());
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(any());
    verify(containerPersisterService, times(0)).saveContainer(any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Item details are missing. Please provide itemNumber, receivedQty & itemDescription.")
  public void testReceiveWithoutASNItemInfoMissing2() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");
    palletReceiveRequest.setReceiveWithoutASN(true);
    palletReceiveRequest.setStoreNumber("6094");
    palletReceiveRequest.setItems(
        Collections.singletonList(
            PalletItem.builder().itemNumber(2222L).itemDescription("Material").build()));

    palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(0)).getContainerDetails(any());
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(any());
    verify(containerPersisterService, times(0)).saveContainer(any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Store number cannot be empty. Please enter a valid store number.")
  public void testReceiveWithoutASNStoreNumMissing() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");
    palletReceiveRequest.setReceiveWithoutASN(true);
    palletReceiveRequest.setItems(
        Collections.singletonList(
            PalletItem.builder()
                .itemNumber(2222L)
                .receivedQty(20)
                .itemDescription("Material")
                .build()));

    palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(0)).getContainerDetails(any());
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(any());
    verify(containerPersisterService, times(0)).saveContainer(any());
  }

  @Test
  public void testReceivePalletAlreadyMappedToLpn() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");

    when(containerPersisterService.getContainerDetails("B32899000020011086"))
        .thenReturn(FixtureMockData.getActiveContainer());
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);
    when(fixtureManagedConfig.getPrintingLabelDateFormat()).thenReturn("dd/MM/yyyy");

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6001");
    assertEquals(palletReceiveResponse.getLpn(), "LPN 10656 INV 4784");
    assertEquals(palletReceiveResponse.getStatus(), FixtureConstants.CONTAINER_STATUS_MAPPED_LPN);
    assertNotNull(palletReceiveResponse.getItems());
    PalletItem palletItem = palletReceiveResponse.getItems().get(0);
    assertEquals(palletItem.getItemNumber(), Long.valueOf(561301081L));
    assertEquals(palletItem.getReceivedQty(), Integer.valueOf(5));
    assertEquals(palletItem.getOrderedQty(), Integer.valueOf(5));
    assertEquals(palletItem.getQuantityUOM(), "EA");
    assertEquals(palletItem.getItemDescription(), "Part");
    assertEquals(palletItem.getPurchaseReferenceNumber(), "2356789123");
    assertEquals(palletItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(anyString());
    verify(containerPersisterService, times(0)).saveContainer(any());
    verify(containerService, times(0)).publishMultipleContainersToInventory(any());
  }

  @Test
  public void testReceiveLPNProvidedAsPalletId() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("LPN 10656 INV 4784");

    when(containerPersisterService.getContainerDetails("LPN 10656 INV 4784")).thenReturn(null);
    when(containerPersisterService.getContainerDetailsByParentTrackingId("LPN 10656 INV 4784"))
        .thenReturn(Stream.of(FixtureMockData.getActiveContainer()).collect(Collectors.toSet()));
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);
    when(fixtureManagedConfig.getPrintingLabelDateFormat()).thenReturn("dd/MM/yyyy");

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6001");
    assertEquals(palletReceiveResponse.getLpn(), "LPN 10656 INV 4784");
    assertEquals(palletReceiveResponse.getStatus(), FixtureConstants.CONTAINER_STATUS_MAPPED_LPN);
    assertNotNull(palletReceiveResponse.getItems());
    PalletItem palletItem = palletReceiveResponse.getItems().get(0);
    assertEquals(palletItem.getItemNumber(), Long.valueOf(561301081L));
    assertEquals(palletItem.getReceivedQty(), Integer.valueOf(5));
    assertEquals(palletItem.getOrderedQty(), Integer.valueOf(5));
    assertEquals(palletItem.getQuantityUOM(), "EA");
    assertEquals(palletItem.getItemDescription(), "Part");
    assertEquals(palletItem.getPurchaseReferenceNumber(), "2356789123");
    assertEquals(palletItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("LPN 10656 INV 4784"));
    verify(containerPersisterService, times(1))
        .getContainerDetailsByParentTrackingId(eq("LPN 10656 INV 4784"));
    verify(containerPersisterService, times(0)).saveContainer(any());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Pallet was completed")
  public void testReceive_PalletNotPresentInReceivingIsAlreadyReceivedInGDM() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");

    when(containerPersisterService.getContainerDetails("B32899000020011086")).thenReturn(null);

    SsccScanResponse packScanResp =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getGlobalPackScanResponse(), SsccScanResponse.class);
    packScanResp
        .getPacks()
        .stream()
        .forEach(pack -> pack.setStatus(ReceivingConstants.PACK_STATUS_RECEIVED));

    when(deliveryService.globalPackSearch(eq("B32899000020011086"), any()))
        .thenReturn(packScanResp);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    verify(deliveryService, times(1)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(1))
        .getContainerDetailsByParentTrackingId(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).saveContainer(any());
  }

  @Test
  public void testReceive_PalletNotPresentInReceivingMultiplePalletsInGDMOneReceived() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");

    when(containerPersisterService.getContainerDetails("B32899000020011086")).thenReturn(null);

    SsccScanResponse packScanResp =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getMultipleGlobalPackScanResponse(), SsccScanResponse.class);
    // mark one as received
    packScanResp
        .getPacks()
        .forEach(
            pack -> {
              if (pack.getShipmentNumber().equals("546191216"))
                pack.setStatus(ReceivingConstants.PACK_STATUS_RECEIVED);
            });

    when(deliveryService.globalPackSearch(eq("B32899000020011086"), any()))
        .thenReturn(packScanResp);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receive(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6094");
    assertFalse(palletReceiveResponse.isAuditRequired());
    assertNotNull(palletReceiveResponse.getItems());
    PalletItem palletItem = palletReceiveResponse.getItems().get(0);
    assertEquals(
        palletItem.getItemNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemNumber());
    assertEquals(
        palletItem.getReceivedQty(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        palletItem.getOrderedQty(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        palletItem.getQuantityUOM(),
        packScanResp.getPacks().get(0).getItems().get(0).getInventoryDetail().getReportedUom());
    assertEquals(
        palletItem.getItemDescription(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemDescription());
    assertEquals(
        palletItem.getPurchaseReferenceNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoNumber());
    assertEquals(palletItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));

    verify(deliveryService, times(1)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(1))
        .getContainerDetailsByParentTrackingId(eq("B32899000020011086"));
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);
    assertEquals(container.getTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), "sysadmin");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "B32899000020011086");
    assertEquals(
        containerItem.getOrderableQuantity(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        containerItem.getItemNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemNumber());
    assertEquals(
        containerItem.getDescription(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemDescription());
    assertEquals(
        containerItem.getPurchaseReferenceNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoNumber());
    assertEquals(
        containerItem.getPurchaseReferenceLineNumber().toString(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoLineNumber());
    assertEquals(
        containerItem.getQuantity(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        containerItem.getQuantityUOM(),
        packScanResp.getPacks().get(0).getItems().get(0).getInventoryDetail().getReportedUom());
  }

  @Test
  public void testMapLpn() {
    PalletMapLPNRequest palletMapLPNRequest =
        PalletMapLPNRequest.builder()
            .lpn("LPN 10656 INV 4784")
            .packNumber("B32899000020011086")
            .build();
    Container activeContainer = FixtureMockData.getActiveContainerLPNNotMapped();
    when(containerPersisterService.getContainerDetails(eq("B32899000020011086")))
        .thenReturn(activeContainer);

    PalletPutAwayResponse mapLpnResp =
        palletReceivingService.mapLpn(palletMapLPNRequest, MockHttpHeaders.getHeaders());
    assertNotNull(mapLpnResp);
    assertEquals(mapLpnResp.getLpn(), "LPN 10656 INV 4784");
    assertEquals(mapLpnResp.getPackNumber(), "B32899000020011086");
    assertNull(mapLpnResp.getLocation());
    assertEquals(mapLpnResp.getStoreNumber(), "6001");

    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(1)).checkIfContainerExist(eq("LPN 10656 INV 4784"));

    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);
    assertEquals(container.getTrackingId(), "LPN 10656 INV 4784");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "LPN 10656 INV 4784");
    verify(containerService, times(1)).publishMultipleContainersToInventory(any());
  }

  @Test
  public void testMapLpnAndPublishMove() {
    PalletMapLPNRequest palletMapLPNRequest =
        PalletMapLPNRequest.builder()
            .lpn("LPN 10656 INV 4784")
            .packNumber("B32899000020011086")
            .build();
    when(fixtureManagedConfig.isSlottingEnabledRFC()).thenReturn(true);
    Container activeContainer = FixtureMockData.getActiveContainerLPNNotMapped();
    when(containerPersisterService.getContainerDetails(eq("B32899000020011086")))
        .thenReturn(activeContainer);
    when(itemMDMServiceClient.retrieveItemDetails(anySet(), any(HttpHeaders.class), anyBoolean()))
        .thenReturn(getItemDetails());
    SlottingPalletResponse response = new SlottingPalletResponse();
    SlottingDivertLocations slottingDivertLocations = new SlottingDivertLocations();
    slottingDivertLocations.setLocation("suggestedSlot");
    response.setLocations(Collections.singletonList(slottingDivertLocations));
    when(fixtureManagedConfig.getReceivingDock()).thenReturn("TestDock");
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);

    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);
    ArgumentCaptor<SlottingPalletRequest> captor =
        ArgumentCaptor.forClass(SlottingPalletRequest.class);
    doReturn(mockSlottingResponseBody)
        .when(slottingRestApiClient)
        .multipleSlotsFromSlotting(captor.capture(), anyBoolean());
    PalletPutAwayResponse mapLpnResp =
        palletReceivingService.mapLpn(palletMapLPNRequest, MockHttpHeaders.getHeaders());
    assertNotNull(mapLpnResp);
    assertEquals(mapLpnResp.getLpn(), "LPN 10656 INV 4784");
    assertEquals(mapLpnResp.getPackNumber(), "B32899000020011086");
    assertNull(mapLpnResp.getLocation());
    assertEquals(mapLpnResp.getStoreNumber(), "6001");

    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(1)).checkIfContainerExist(eq("LPN 10656 INV 4784"));

    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);
    assertEquals(container.getTrackingId(), "LPN 10656 INV 4784");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "LPN 10656 INV 4784");
    verify(containerService, times(1)).publishMultipleContainersToInventory(any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "LPN 805IN111000 is already mapped for this pallet B32899000020011086.")
  public void mapLpnLpnAlreadyMapped() {
    PalletMapLPNRequest palletMapLPNRequest =
        PalletMapLPNRequest.builder().lpn("LPN 10656 INV 4784").packNumber("805IN111000").build();
    Container activeContainer = FixtureMockData.getActiveContainerLPNNotMapped();
    activeContainer.setTrackingId("805IN111000");
    activeContainer.setParentTrackingId("B32899000020011086");
    when(containerPersisterService.getContainerDetails(eq("805IN111000")))
        .thenReturn(activeContainer);
    palletReceivingService.mapLpn(palletMapLPNRequest, MockHttpHeaders.getHeaders());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("805IN111000"));
    verify(containerPersisterService, times(0)).checkIfContainerExist(eq("805IN111000"));
    verify(containerPersisterService, times(0)).saveContainer(any());
    verify(containerService, times(0)).publishMultipleContainersToInventory(any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "LPN 805IN111000 is already used. Please provide a different LPN.")
  public void mapLpnLpnAlreadyMappedToAnotherContainer() {
    PalletMapLPNRequest palletMapLPNRequest =
        PalletMapLPNRequest.builder().lpn("805IN111000").packNumber("B32899000020011086").build();
    Container activeContainer = FixtureMockData.getActiveContainerLPNNotMapped();

    when(containerPersisterService.getContainerDetails(eq("B32899000020011086")))
        .thenReturn(activeContainer);

    when(containerPersisterService.checkIfContainerExist(eq("805IN111000"))).thenReturn(true);

    palletReceivingService.mapLpn(palletMapLPNRequest, MockHttpHeaders.getHeaders());

    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(1)).checkIfContainerExist(eq("805IN111000"));
    verify(containerPersisterService, times(0)).saveContainer(any());
    verify(containerService, times(0)).publishMultipleContainersToInventory(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found for packageBarcodeValue=B32899000020011086")
  public void mapLpnContainerNotFound() {
    PalletMapLPNRequest palletMapLPNRequest =
        PalletMapLPNRequest.builder()
            .lpn("LPN 10656 INV 4784")
            .packNumber("B32899000020011086")
            .build();

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    palletReceivingService.mapLpn(palletMapLPNRequest, MockHttpHeaders.getHeaders());

    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).checkIfContainerExist(eq("805IN111000"));
    verify(containerPersisterService, times(0)).saveContainer(any());
    verify(containerService, times(0)).publishMultipleContainersToInventory(any());
  }

  @Test
  public void testPutAway() {
    PalletPutAwayRequest palletPutAwayRequest =
        PalletPutAwayRequest.builder().lpn("LPN 10656 INV 4784").location("F1-022").build();
    Container activeContainer = FixtureMockData.getActiveContainer();
    when(containerPersisterService.getContainerDetails("LPN 10656 INV 4784"))
        .thenReturn(activeContainer);
    ControlTowerTracker controlTowerTracker =
        ControlTowerTracker.builder().id(1L).lpn("LPN 10656 INV 4784").build();
    when(controlTowerService.putForTracking("LPN 10656 INV 4784")).thenReturn(controlTowerTracker);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), FixtureConstants.IS_CT_ENABLED, false))
        .thenReturn(true);

    PalletPutAwayResponse palletPutAwayResponse =
        palletReceivingService.putAway(palletPutAwayRequest, MockHttpHeaders.getHeaders());
    assertNotNull(palletPutAwayResponse);
    assertEquals(palletPutAwayResponse.getLpn(), "LPN 10656 INV 4784");
    assertEquals(palletPutAwayResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletPutAwayResponse.getLocation(), "F1-022");
    assertEquals(palletPutAwayResponse.getStoreNumber(), "6001");

    verify(containerPersisterService, times(1)).getContainerDetails(eq("LPN 10656 INV 4784"));

    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);
    assertEquals(container.getTrackingId(), "LPN 10656 INV 4784");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_COMPLETE);

    verify(controlTowerService, times(1)).putForTracking("LPN 10656 INV 4784");
    ArgumentCaptor<List<PutAwayInventory>> putAwayCaptor = ArgumentCaptor.forClass(List.class);
    verify(controlTowerService, times(1))
        .putAwayInventory(putAwayCaptor.capture(), eq(controlTowerTracker));
    List<PutAwayInventory> putAwayCaptorValue = putAwayCaptor.getValue();
    assertNotNull(putAwayCaptorValue);
    assertEquals(putAwayCaptorValue.get(0).getPalletId(), "B32899000020011086");
    assertEquals(putAwayCaptorValue.get(0).getLpn(), "LPN 10656 INV 4784");
    assertEquals(putAwayCaptorValue.get(0).getDestination(), "6001");
    assertEquals(putAwayCaptorValue.get(0).getPutAwayLocation(), "F1-022");
    assertEquals(putAwayCaptorValue.get(0).getItems().size(), 1);

    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getId(), "561301081");
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getDescription(), "Part");
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getQuantity(), 5);
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getDestination(), "6001");
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getPurchaseOrder(), "2356789123");
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getPoLineNumber(), "1");

    ArgumentCaptor<InventoryLocationUpdateRequest> captor =
        ArgumentCaptor.forClass(InventoryLocationUpdateRequest.class);
    verify(inventoryService, times(1)).updateLocation(captor.capture(), any());
    InventoryLocationUpdateRequest inventoryLocationUpdateRequest = captor.getValue();
    assertEquals(inventoryLocationUpdateRequest.getTrackingIds().get(0), "LPN 10656 INV 4784");
    assertEquals(
        inventoryLocationUpdateRequest.getDestinationLocation().getLocationName(), "F1-022");
  }

  @Test
  public void testPutAwayForReceiveWithoutASN() {
    PalletPutAwayRequest palletPutAwayRequest =
        PalletPutAwayRequest.builder().lpn("LPN 10656 INV 4784").location("F1-022").build();
    Container activeContainer = FixtureMockData.getActiveContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_ACTIVE_NO_ASN);
    activeContainer.getContainerItems().get(0).setPurchaseReferenceLineNumber(null);
    activeContainer.getContainerItems().get(0).setPurchaseReferenceNumber(null);
    when(containerPersisterService.getContainerDetails("LPN 10656 INV 4784"))
        .thenReturn(activeContainer);
    ControlTowerTracker controlTowerTracker =
        ControlTowerTracker.builder().id(1L).lpn("LPN 10656 INV 4784").build();
    when(controlTowerService.putForTracking("LPN 10656 INV 4784")).thenReturn(controlTowerTracker);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), FixtureConstants.IS_CT_ENABLED, false))
        .thenReturn(true);

    PalletPutAwayResponse palletPutAwayResponse =
        palletReceivingService.putAway(palletPutAwayRequest, MockHttpHeaders.getHeaders());
    assertNotNull(palletPutAwayResponse);
    assertEquals(palletPutAwayResponse.getLpn(), "LPN 10656 INV 4784");
    assertEquals(palletPutAwayResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletPutAwayResponse.getLocation(), "F1-022");
    assertEquals(palletPutAwayResponse.getStoreNumber(), "6001");

    verify(containerPersisterService, times(1)).getContainerDetails(eq("LPN 10656 INV 4784"));

    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);
    assertEquals(container.getTrackingId(), "LPN 10656 INV 4784");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getCreateUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_COMPLETE_NO_ASN);

    verify(controlTowerService, times(1)).putForTracking("LPN 10656 INV 4784");
    ArgumentCaptor<List<PutAwayInventory>> putAwayCaptor = ArgumentCaptor.forClass(List.class);
    verify(controlTowerService, times(1))
        .putAwayInventory(putAwayCaptor.capture(), eq(controlTowerTracker));
    List<PutAwayInventory> putAwayCaptorValue = putAwayCaptor.getValue();
    assertNotNull(putAwayCaptorValue);
    assertEquals(putAwayCaptorValue.get(0).getPalletId(), "B32899000020011086");
    assertEquals(putAwayCaptorValue.get(0).getLpn(), "LPN 10656 INV 4784");
    assertEquals(putAwayCaptorValue.get(0).getDestination(), "6001");
    assertEquals(putAwayCaptorValue.get(0).getPutAwayLocation(), "F1-022");
    assertEquals(putAwayCaptorValue.get(0).getItems().size(), 1);

    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getId(), "561301081");
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getDescription(), "Part");
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getQuantity(), 5);
    assertEquals(putAwayCaptorValue.get(0).getItems().get(0).getDestination(), "6001");
    assertNull(putAwayCaptorValue.get(0).getItems().get(0).getPurchaseOrder());
    assertNull(putAwayCaptorValue.get(0).getItems().get(0).getPoLineNumber());

    ArgumentCaptor<InventoryLocationUpdateRequest> captor =
        ArgumentCaptor.forClass(InventoryLocationUpdateRequest.class);
    verify(inventoryService, times(1)).updateLocation(captor.capture(), any());
    InventoryLocationUpdateRequest inventoryLocationUpdateRequest = captor.getValue();
    assertEquals(inventoryLocationUpdateRequest.getTrackingIds().get(0), "LPN 10656 INV 4784");
    assertEquals(
        inventoryLocationUpdateRequest.getDestinationLocation().getLocationName(), "F1-022");
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found for packageBarcodeValue=LPN 10656 INV 4784")
  public void putAwayContainerNotFound() {
    PalletPutAwayRequest palletPutAwayRequest =
        PalletPutAwayRequest.builder().lpn("LPN 10656 INV 4784").location("F1-022").build();

    when(containerPersisterService.getContainerDetails("LPN 10656 INV 4784")).thenReturn(null);
    palletReceivingService.putAway(palletPutAwayRequest, MockHttpHeaders.getHeaders());

    verify(containerPersisterService, times(1)).getContainerDetails(eq("LPN 10656 INV 4784"));
    verify(containerPersisterService, times(0)).saveContainer(any());
  }

  @Test
  public void testCheckAndRetryCTInventory() {
    List<ControlTowerTracker> controlTowerTrackerList = new ArrayList<>();
    ControlTowerTracker ctTracker1 =
        ControlTowerTracker.builder().lpn("LPN 10656 INV 4784").ackKey("A8D2BE3211").build();
    ctTracker1.setFacilityCountryCode("US");
    ctTracker1.setFacilityNum(32111);
    ctTracker1.setSubmissionStatus(EventTargetStatus.PENDING);
    controlTowerTrackerList.add(ctTracker1);

    ControlTowerTracker ctTracker2 =
        ControlTowerTracker.builder().lpn("LPN 10656 INV 4785").ackKey("B8D2BE3212").build();
    ctTracker2.setFacilityCountryCode("US");
    ctTracker2.setFacilityNum(32111);
    ctTracker2.setSubmissionStatus(EventTargetStatus.PENDING);
    controlTowerTrackerList.add(ctTracker2);

    when(controlTowerService.getCTEntitiesToValidate()).thenReturn(controlTowerTrackerList);
    CTWarehouseResponse successResponse =
        CTWarehouseResponse.builder().status(ReceivingConstants.SUCCESS).build();
    when(controlTowerService.getInventoryStatus("A8D2BE3211")).thenReturn(successResponse);
    when(controlTowerService.getInventoryStatus("B8D2BE3212")).thenReturn(successResponse);

    palletReceivingService.checkAndRetryCTInventory();

    ArgumentCaptor<List<ControlTowerTracker>> ctCaptor = ArgumentCaptor.forClass(List.class);
    verify(controlTowerService, times(1)).saveManagedObjectsOnly(ctCaptor.capture());

    List<ControlTowerTracker> value = ctCaptor.getValue();
    assertEquals(value.size(), 2);
    assertEquals(value.get(0).getSubmissionStatus(), EventTargetStatus.DELETE);
    assertEquals(value.get(1).getSubmissionStatus(), EventTargetStatus.DELETE);

    verify(controlTowerService, times(1)).getInventoryStatus("A8D2BE3211");
    verify(controlTowerService, times(1)).getInventoryStatus("B8D2BE3212");
    verify(controlTowerService, times(0)).putAwayInventory(anyList(), any());
    verify(containerPersisterService, times(0)).getContainerDetails(anyString());
  }

  @Test
  public void testCheckAndRetryCTInventoryNothingToCheck() {
    when(controlTowerService.getCTEntitiesToValidate()).thenReturn(null);
    palletReceivingService.checkAndRetryCTInventory();
    verify(controlTowerService, times(0)).saveManagedObjectsOnly(any());
    verify(controlTowerService, times(0)).getInventoryStatus(anyString());
    verify(controlTowerService, times(0)).putAwayInventory(anyList(), any());
    verify(containerPersisterService, times(0)).getContainerDetails(anyString());
  }

  @Test
  public void testCheckAndRetryCTInventoryOneWithoutAckKey() {
    List<ControlTowerTracker> controlTowerTrackerList = new ArrayList<>();
    ControlTowerTracker ctTracker1 =
        ControlTowerTracker.builder().lpn("LPN 10656 INV 4784").ackKey("A8D2BE3211").build();
    ctTracker1.setFacilityCountryCode("US");
    ctTracker1.setFacilityNum(32111);
    ctTracker1.setSubmissionStatus(EventTargetStatus.PENDING);
    controlTowerTrackerList.add(ctTracker1);

    ControlTowerTracker ctTracker2 =
        ControlTowerTracker.builder().lpn("LPN 10656 INV 4785").build();
    ctTracker2.setFacilityCountryCode("US");
    ctTracker2.setFacilityNum(32111);
    ctTracker2.setSubmissionStatus(EventTargetStatus.PENDING);
    controlTowerTrackerList.add(ctTracker2);

    when(controlTowerService.getCTEntitiesToValidate()).thenReturn(controlTowerTrackerList);
    CTWarehouseResponse successResponse =
        CTWarehouseResponse.builder().status(ReceivingConstants.SUCCESS).build();
    when(controlTowerService.getInventoryStatus("A8D2BE3211")).thenReturn(successResponse);

    when(containerPersisterService.getContainerDetails("LPN 10656 INV 4785"))
        .thenReturn(FixtureMockData.getCompletedContainer());
    palletReceivingService.checkAndRetryCTInventory();

    ArgumentCaptor<List<ControlTowerTracker>> ctCaptor = ArgumentCaptor.forClass(List.class);
    verify(controlTowerService, times(1)).saveManagedObjectsOnly(ctCaptor.capture());

    List<ControlTowerTracker> value = ctCaptor.getValue();
    assertEquals(value.size(), 1);
    assertEquals(value.get(0).getSubmissionStatus(), EventTargetStatus.DELETE);

    verify(controlTowerService, times(1)).getInventoryStatus(anyString());
    verify(controlTowerService, times(1)).putAwayInventory(anyList(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
  }

  @Test
  public void testCheckAndRetryCTInventoryOneFailedAndRetryEarlierFailed() {
    List<ControlTowerTracker> controlTowerTrackerList = new ArrayList<>();
    ControlTowerTracker ctTracker1 =
        ControlTowerTracker.builder().lpn("LPN 10656 INV 4784").ackKey("A8D2BE3211").build();
    ctTracker1.setFacilityCountryCode("US");
    ctTracker1.setFacilityNum(32111);
    ctTracker1.setSubmissionStatus(EventTargetStatus.PENDING);
    controlTowerTrackerList.add(ctTracker1);

    ControlTowerTracker ctTracker2 =
        ControlTowerTracker.builder()
            .lpn("LPN 10656 INV 4785")
            .ackKey("B8D2BE3212")
            .submissionStatus(EventTargetStatus.FAILED)
            .build();
    ctTracker2.setFacilityCountryCode("US");
    ctTracker2.setFacilityNum(32111);
    controlTowerTrackerList.add(ctTracker2);

    when(controlTowerService.getCTEntitiesToValidate()).thenReturn(controlTowerTrackerList);

    CTWarehouseResponse failedResponse =
        JacksonParser.convertJsonToObject(
            "{\n"
                + "    \"status\": \"Fail\",\n"
                + "    \"errors\": [\n"
                + "        {\n"
                + "            \"lpn\": \"LPN 10656 INV 4784\",\n"
                + "            \"errors\": [\n"
                + "                {\n"
                + "                    \"error\": \"The warehouse that services store 561 cannot be determined\"\n"
                + "                },\n"
                + "                {\n"
                + "                    \"error\": \"Could not find warehouse location using identifier 561. LPN: LPN 10656 INV 4784\"\n"
                + "                }\n"
                + "            ]\n"
                + "        }\n"
                + "    ]\n"
                + "}",
            CTWarehouseResponse.class);

    when(controlTowerService.getInventoryStatus("A8D2BE3211")).thenReturn(failedResponse);

    when(containerPersisterService.getContainerDetails("LPN 10656 INV 4785"))
        .thenReturn(FixtureMockData.getCompletedContainer());
    palletReceivingService.checkAndRetryCTInventory();

    ArgumentCaptor<List<ControlTowerTracker>> ctCaptor = ArgumentCaptor.forClass(List.class);
    verify(controlTowerService, times(1)).saveManagedObjectsOnly(ctCaptor.capture());

    List<ControlTowerTracker> value = ctCaptor.getValue();
    assertEquals(value.size(), 1);
    assertEquals(value.get(0).getSubmissionStatus(), EventTargetStatus.FAILED);

    verify(controlTowerService, times(1)).getInventoryStatus(anyString());
    verify(controlTowerService, times(1)).putAwayInventory(anyList(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
  }

  @Test
  public void testPostInventoryToCT() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), FixtureConstants.IS_CT_ENABLED, false))
        .thenReturn(true);
    when(containerPersisterService.getContainerDetails("LPN 10656 INV 4785"))
        .thenReturn(FixtureMockData.getCompletedContainer());
    palletReceivingService.postInventoryToCT("LPN 10656 INV 4785");
    verify(containerPersisterService, times(1)).getContainerDetails("LPN 10656 INV 4785");
    verify(controlTowerService, times(1)).putAwayInventory(anyList());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found for packageBarcodeValue=LPN 10656 INV 4785")
  public void testPostInventoryToCTContainerNotFound() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), FixtureConstants.IS_CT_ENABLED, false))
        .thenReturn(true);
    when(containerPersisterService.getContainerDetails("LPN 10656 INV 4785")).thenReturn(null);
    palletReceivingService.postInventoryToCT("LPN 10656 INV 4785");
    verify(containerPersisterService, times(1)).getContainerDetails("LPN 10656 INV 4785");
    verify(controlTowerService, times(0)).putAwayInventory(anyList());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Container is yet not completed : LPN 10656 INV 4785")
  public void testPostInventoryToCTContainerNotComplete() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), FixtureConstants.IS_CT_ENABLED, false))
        .thenReturn(true);
    when(containerPersisterService.getContainerDetails("LPN 10656 INV 4785"))
        .thenReturn(FixtureMockData.getActiveContainer());
    palletReceivingService.postInventoryToCT("LPN 10656 INV 4785");
    verify(containerPersisterService, times(1)).getContainerDetails("LPN 10656 INV 4785");
    verify(controlTowerService, times(0)).putAwayInventory(anyList());
  }

  @Test
  public void testPublishToInventory() {
    when(containerPersisterService.getContainerDetails("LPN 10656 INV 4785"))
        .thenReturn(FixtureMockData.getCompletedContainer());
    palletReceivingService.publishToInventory("LPN 10656 INV 4785");
    verify(containerPersisterService, times(1)).getContainerDetails("LPN 10656 INV 4785");
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
  }

  @Test
  public void testProcessShipmentAddedEventForPalletReceivedWithoutASN3() {
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getShipmentUpdateEventPayload(), DeliveryUpdateMessage.class);
    long deliveryNumberHash =
        deliveryUpdateMessage.getPayload().getShipment().getShipmentNumber().hashCode();

    List<Container> cl = new ArrayList<>();
    Container activeContainer = FixtureMockData.getCompletedContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_COMPLETE_NO_ASN);
    activeContainer.setTrackingId("LPN 10656 INV 4785");
    activeContainer.getContainerItems().get(0).setPurchaseReferenceLineNumber(1);

    cl.add(activeContainer);
    when(containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            anyList(), eq(FixtureConstants.CONTAINER_STATUS_WO_ASN)))
        .thenReturn(cl);

    activeContainer.getContainerItems().get(0).setPurchaseReferenceNumber("123");
    activeContainer.getContainerItems().get(0).setTrackingId("LPN 10656 INV 4785");

    palletReceivingService.processShipmentEvent(deliveryUpdateMessage);

    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(
            captor.capture(), eq(FixtureConstants.CONTAINER_STATUS_WO_ASN));
    assertEquals(captor.getValue().size(), 2);

    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(containerList.capture(), containerItemList.capture());
    List<Container> containers = containerList.getValue();
    List<ContainerItem> containerItems = containerItemList.getValue();
    assertEquals(containers.size(), 1);
    assertEquals(containerItems.size(), 1);

    Container container = containers.get(0);
    assertEquals(container.getDeliveryNumber(), Long.valueOf(deliveryNumberHash));
    assertEquals(container.getTrackingId(), "LPN 10656 INV 4785");
    assertEquals(container.getParentTrackingId(), "B32899000020011086");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getLastChangedUser(), ReceivingConstants.DEFAULT_USER);
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_COMPLETE);
    assertEquals(container.getDestination().get("buNumber"), "6094");

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "LPN 10656 INV 4785");
    assertEquals(containerItem.getItemNumber(), Long.valueOf(561301081));
    assertEquals(containerItem.getDescription(), "Part");
    assertEquals(containerItem.getPurchaseReferenceNumber(), "3001747108");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(5));
    assertEquals(containerItem.getOrderableQuantity(), Integer.valueOf(1120));
    assertEquals(containerItem.getQuantityUOM(), "EA");

    verify(inventoryService, times(1)).updateInventoryPoDetails(any());

    verify(containerService, times(1)).publishMultipleContainersToInventory(any());
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataService, times(1)).save(any());
  }

  public static Map<String, Object> getItemDetails() {
    try {
      String itemDetails =
          new File("../../receiving-test/src/main/resources/json/item_details_92.json")
              .getCanonicalPath();
      return gson.fromJson(new String(Files.readAllBytes(Paths.get(itemDetails))), Map.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
      return null;
    }
  }

  @Test
  public void testReceiveV2() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");
    Container pendingContainer = FixtureMockData.getPendingContainer();
    when(containerPersisterService.getContainerDetails("B32899000020011086"))
        .thenReturn(pendingContainer);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn("LPN");
    when(fixtureManagedConfig.getPrintingLabelDateFormat()).thenReturn("dd/MM/yyyy");
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(
            Optional.of(
                DeliveryMetaData.builder()
                    .deliveryNumber(String.valueOf(pendingContainer.getDeliveryNumber()))
                    .trailerNumber("88528711")
                    .build()));

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receiveV2(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6001");
    assertEquals(palletReceiveResponse.getLoadNumber(), "88528711");
    assertNotNull(palletReceiveResponse.getItems());
    PalletItem palletItem = palletReceiveResponse.getItems().get(0);
    assertEquals(palletItem.getItemNumber(), Long.valueOf(561301081L));
    assertEquals(palletItem.getReceivedQty(), Integer.valueOf(5));
    assertEquals(palletItem.getOrderedQty(), Integer.valueOf(5));
    assertEquals(palletItem.getQuantityUOM(), "EA");
    assertEquals(palletItem.getItemDescription(), "Part");
    assertEquals(palletItem.getPurchaseReferenceNumber(), "2356789123");
    assertEquals(palletItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));

    verify(deliveryService, times(0)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(0)).getContainerDetailsByParentTrackingId(anyString());
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);

    assertEquals(container.getTrackingId(), "LPN");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "LPN");
    assertEquals(containerItem.getQuantity(), Integer.valueOf(5));
    assertEquals(containerItem.getOrderableQuantity(), Integer.valueOf(5));

    verify(deliveryMetaDataService, times(1))
        .findByDeliveryNumber(String.valueOf(pendingContainer.getDeliveryNumber()));
  }

  @Test
  public void testReceiveV2_PalletNotPresentInReceiving() {
    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setPackNumber("B32899000020011086");

    when(containerPersisterService.getContainerDetails("B32899000020011086")).thenReturn(null);
    when(lpnCacheService.getLPNBasedOnTenant(any())).thenReturn("LPN");

    SsccScanResponse packScanResp =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getGlobalPackScanResponse(), SsccScanResponse.class);

    when(deliveryService.globalPackSearch(eq("B32899000020011086"), any()))
        .thenReturn(packScanResp);
    when(fixtureManagedConfig.getReceivableItemCountThreshold()).thenReturn(20);
    when(fixtureManagedConfig.getPrintingLabelDateFormat()).thenReturn("dd/MM/yyyy");

    PalletReceiveResponse palletReceiveResponse =
        palletReceivingService.receiveV2(palletReceiveRequest, MockHttpHeaders.getHeaders());

    assertNotNull(palletReceiveResponse);
    assertEquals(palletReceiveResponse.getPackNumber(), "B32899000020011086");
    assertEquals(palletReceiveResponse.getStoreNumber(), "6094");
    assertFalse(palletReceiveResponse.isAuditRequired());
    assertNotNull(palletReceiveResponse.getItems());
    PalletItem palletItem = palletReceiveResponse.getItems().get(0);
    assertEquals(
        palletItem.getItemNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemNumber());
    assertEquals(
        palletItem.getReceivedQty(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        palletItem.getOrderedQty(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        palletItem.getQuantityUOM(),
        packScanResp.getPacks().get(0).getItems().get(0).getInventoryDetail().getReportedUom());
    assertEquals(
        palletItem.getItemDescription(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemDescription());
    assertEquals(
        palletItem.getPurchaseReferenceNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoNumber());
    assertEquals(palletItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));

    verify(deliveryService, times(1)).globalPackSearch(any(), any());
    verify(containerPersisterService, times(1)).getContainerDetails(eq("B32899000020011086"));
    verify(containerPersisterService, times(1))
        .getContainerDetailsByParentTrackingId(eq("B32899000020011086"));
    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerArgumentCaptor.capture());
    Container container = containerArgumentCaptor.getValue();
    List<ContainerItem> containerItems = container.getContainerItems();
    assertNotNull(container);
    assertEquals(containerItems.size(), 1);
    assertEquals(container.getTrackingId(), "LPN");
    assertEquals(container.getMessageId(), "B32899000020011086");
    assertEquals(container.getContainerStatus(), ReceivingConstants.STATUS_ACTIVE);

    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getTrackingId(), "LPN");
    assertEquals(
        containerItem.getOrderableQuantity(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        containerItem.getItemNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemNumber());
    assertEquals(
        containerItem.getDescription(),
        packScanResp.getPacks().get(0).getItems().get(0).getItemDescription());
    assertEquals(
        containerItem.getPurchaseReferenceNumber(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoNumber());
    assertEquals(
        containerItem.getPurchaseReferenceLineNumber().toString(),
        packScanResp.getPacks().get(0).getItems().get(0).getPurchaseOrder().getPoLineNumber());
    assertEquals(
        containerItem.getQuantity(),
        Integer.valueOf(
            packScanResp
                .getPacks()
                .get(0)
                .getItems()
                .get(0)
                .getInventoryDetail()
                .getReportedQuantity()
                .intValue()));
    assertEquals(
        containerItem.getQuantityUOM(),
        packScanResp.getPacks().get(0).getItems().get(0).getInventoryDetail().getReportedUom());
  }
}
