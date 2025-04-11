package com.walmart.move.nim.receiving.endgame.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockDeliveryMetaData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.endgame.model.EndgameReceivingRequest;
import com.walmart.move.nim.receiving.endgame.model.ExpiryDateUpdatePublisherData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndgameContainerServiceTest extends ReceivingTestBase {

  @InjectMocks private EndgameContainerService endgameContainerService;

  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ContainerItemRepository containerItemRepository;
  @Mock private JmsPublisher jmsPublisher;

  @Mock private MaasTopics maasTopics;

  @Mock private DeliveryMetaDataService deliveryMetaDataService;

  private Gson gson;
  @InjectMocks private TenantContext tenantContext;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    gson = new Gson();
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(54321);
    ReflectionTestUtils.setField(endgameContainerService, "gson", gson);
    ReflectionTestUtils.setField(
        endgameContainerService, "containerPersisterService", containerPersisterService);
    ReflectionTestUtils.setField(
        endgameContainerService, "deliveryMetaDataService", deliveryMetaDataService);
    ReflectionTestUtils.setField(
        endgameContainerService, "containerItemRepository", containerItemRepository);
  }

  @AfterMethod
  public void resetMocks() {
    reset(containerPersisterService);
    reset(containerItemRepository);
    reset(jmsPublisher);
    reset(deliveryMetaDataService);
    reset(maasTopics);
  }

  @Test
  public void testCreateContainer() {
    Container container = getContainer();
    ContainerItem containerItem = getContainerItems();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));

    List<PurchaseOrder> purchaseOrderList = getPurchaseOrders();

    Container container2 =
        endgameContainerService.getContainer(
            MockMessageData.getMockScanEventData(),
            purchaseOrderList.get(0),
            purchaseOrderList.get(0).getLines().get(0),
            2);
    container2 =
        endgameContainerService.createAndSaveContainerAndReceipt(
            MockMessageData.getMockScanEventData(),
            purchaseOrderList.get(0),
            purchaseOrderList.get(0).getLines().get(0),
            2,
            container2);

    assertContainer(container, containerItems, container2);
  }

  @Test
  public void testCreateContainerSscc() {
    Container container = getContainer();
    ContainerItem containerItem = getContainerItems();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);
    DeliveryMetaData deliveryMetaData =
            MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
            .thenReturn(Optional.of(deliveryMetaData));

    List<PurchaseOrder> purchaseOrderList = getPurchaseOrdersWithSscc();

    Container container2 =
            endgameContainerService.getContainer(
                    MockMessageData.getMockScanEventDataSscc(),
                    purchaseOrderList.get(0),
                    purchaseOrderList.get(0).getLines().get(0),
                    2);
    container2 =
            endgameContainerService.createAndSaveContainerAndReceipt(
                    MockMessageData.getMockScanEventDataSscc(),
                    purchaseOrderList.get(0),
                    purchaseOrderList.get(0).getLines().get(0),
                    2,
                    container2);

    assertContainer(container, containerItems, container2);
  }

  @Test
  public void testCreateContainerSsccWithBoxIds() {
    Container container = getContainer();
    ContainerItem containerItem = getContainerItems();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);
    DeliveryMetaData deliveryMetaData =
            MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
            .thenReturn(Optional.of(deliveryMetaData));

    List<PurchaseOrder> purchaseOrderList = getPurchaseOrdersWithSscc();

    Container container2 =
            endgameContainerService.getContainer(
                    MockMessageData.getMockScanEventData(),
                    purchaseOrderList.get(0),
                    purchaseOrderList.get(0).getLines().get(0),
                    2);
    container2 =
            endgameContainerService.createAndSaveContainerAndReceipt(
                    MockMessageData.getMockScanEventData(),
                    purchaseOrderList.get(0),
                    purchaseOrderList.get(0).getLines().get(0),
                    2,
                    container2);

    assertContainer(container, containerItems, container2);
  }

  @Test
  public void testCreateContainer_DeliveryMetaDataHavingRotateDate() {
    DeliveryMetaData deliveryMetaData = MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails();
    Container container = getContainer();
    ContainerItem containerItem = getContainerItems();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);

    String strRotateDate =
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData,
            String.valueOf(containerItems.get(0).getItemNumber()),
            EndgameConstants.ROTATE_DATE);
    Date rotateDate = EndGameUtils.parseRotateDate(strRotateDate);
    containerItems.get(0).setRotateDate(rotateDate);

    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));

    List<PurchaseOrder> purchaseOrderList = getPurchaseOrders();

    Container container2 =
        endgameContainerService.getContainer(
            MockMessageData.getMockScanEventData(),
            purchaseOrderList.get(0),
            purchaseOrderList.get(0).getLines().get(0),
            2);

    container2 =
        endgameContainerService.createAndSaveContainerAndReceipt(
            MockMessageData.getMockScanEventData(),
            purchaseOrderList.get(0),
            purchaseOrderList.get(0).getLines().get(0),
            2,
            container2);

    assertContainer(container, containerItems, container2);
  }

  @Test
  public void testAddItemToContainer() {
    Container container = getContainer();
    ContainerItem containerItem = getContainerItems();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    when(deliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));

    List<PurchaseOrder> purchaseOrderList = getPurchaseOrders();

    Container container2 =
        endgameContainerService.addItemAndGetContainer(
            container,
            MockMessageData.getMockScanEventData(),
            purchaseOrderList.get(0),
            purchaseOrderList.get(0).getLines().get(0),
            2);
    container2 =
        endgameContainerService.createAndSaveContainerAndReceipt(
            MockMessageData.getMockScanEventData(),
            purchaseOrderList.get(0),
            purchaseOrderList.get(0).getLines().get(0),
            2,
            container2);

    assertContainer(container, containerItems, container2);
  }

  @Test
  public void testUpdateRotateDate() {
    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.FALSE);

    Container container1 = getContainer();
    Container container2 = getContainer();
    Container container3 = getContainer();

    ContainerItem containerItem1 = getContainerItems();
    ContainerItem containerItem2 = getContainerItems();
    ContainerItem containerItem3 = getContainerItems();

    container1.setContainerItems(Collections.singletonList(containerItem1));

    container2.setTrackingId("TC00000002");
    containerItem2.setTrackingId("TC00000002");
    container2.setContainerItems(Collections.singletonList(containerItem2));

    container3.setTrackingId("TC00000003");
    containerItem3.setTrackingId("TC00000003");
    containerItem3.setItemNumber(661298341L);
    container3.setContainerItems(Collections.singletonList(containerItem3));

    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);

    List<Container> containers = new ArrayList<>();
    containers.add(container1);
    containers.add(container2);
    containers.add(container3);

    when(containerPersisterService.getContainerByDeliveryNumber(anyLong())).thenReturn(containers);

    ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData =
        endgameContainerService.updateRotateDate(
            container1.getDeliveryNumber(), updateAttributesData);
    assertEquals(
        expiryDateUpdatePublisherData.getSearchCriteria().getTrackingIds().get(0),
        container2.getTrackingId(),
        "Search criteria tracking id not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getSearchCriteria().getBaseDivisionCode(),
        updateAttributesData.getSearchCriteria().getBaseDivisionCode(),
        "Search criteria base division code not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getSearchCriteria().getFinancialReportingGroup(),
        updateAttributesData.getSearchCriteria().getFinancialReportingGroup(),
        "Search criteria financial reporting group not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getSearchCriteria().getItemNumber(),
        updateAttributesData.getSearchCriteria().getItemNumber(),
        "Search criteria item number not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getSearchCriteria().getItemUPC(),
        updateAttributesData.getSearchCriteria().getItemUPC(),
        "Search criteria item upc not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getUpdateAttributes().getRotateDate(),
        updateAttributesData.getUpdateAttributes().getRotateDate(),
        "Update attributes rotate date not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getUpdateAttributes().isExpired(),
        updateAttributesData.getUpdateAttributes().isExpired(),
        "Update attributes is expired not matched.");

    verify(containerPersisterService, times(1)).getContainerByDeliveryNumber(anyLong());
    verify(containerItemRepository, times(1)).saveAll(anyList());
  }

  @Test
  public void testUpdateRotateDate_ContainerStatusIsBackout() {
    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.FALSE);
    Container container = getContainer();
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    ContainerItem containerItem = getContainerItems();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);
    List<Container> containers = new ArrayList<>();
    containers.add(container);
    when(containerPersisterService.getContainerByDeliveryNumber(anyLong())).thenReturn(containers);
    ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData =
        endgameContainerService.updateRotateDate(
            container.getDeliveryNumber(), updateAttributesData);
    assertEquals(expiryDateUpdatePublisherData, null);
    verify(containerPersisterService, times(1)).getContainerByDeliveryNumber(anyLong());
    verify(containerItemRepository, times(0)).saveAll(anyList());
  }

  @Test
  public void testUpdateRotateDate_NoContainerToUpdate() {
    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.FALSE);
    ContainerItem containerItem = getContainerItems();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);
    List<Container> containers = null;
    when(containerPersisterService.getContainerByDeliveryNumber(anyLong())).thenReturn(containers);
    ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData =
        endgameContainerService.updateRotateDate(12333333L, updateAttributesData);
    assertEquals(expiryDateUpdatePublisherData, null);
    verify(containerPersisterService, times(1)).getContainerByDeliveryNumber(anyLong());
    verify(containerItemRepository, times(0)).saveAll(anyList());
  }

  @Test
  public void testUpdateRotateDate_ItemIsExpired() {
    UpdateAttributesData updateAttributesData =
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.TRUE);

    Container container1 = getContainer();
    Container container2 = getContainer();

    ContainerItem containerItem1 = getContainerItems();
    ContainerItem containerItem2 = getContainerItems();

    container1.setContainerItems(Collections.singletonList(containerItem1));

    container2.setTrackingId("TC00000002");
    containerItem2.setTrackingId("TC00000002");
    container2.setContainerItems(Collections.singletonList(containerItem2));

    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);

    List<Container> containers = new ArrayList<>();
    containers.add(container1);
    containers.add(container2);

    when(containerPersisterService.getContainerByDeliveryNumber(anyLong())).thenReturn(containers);
    ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData =
        endgameContainerService.updateRotateDate(
            container1.getDeliveryNumber(), updateAttributesData);

    assertEquals(
        expiryDateUpdatePublisherData.getSearchCriteria().getTrackingIds().get(0),
        container2.getTrackingId(),
        "Search criteria tracking id not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getSearchCriteria().getBaseDivisionCode(),
        updateAttributesData.getSearchCriteria().getBaseDivisionCode(),
        "Search criteria base division code not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getSearchCriteria().getFinancialReportingGroup(),
        updateAttributesData.getSearchCriteria().getFinancialReportingGroup(),
        "Search criteria financial reporting group not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getSearchCriteria().getItemNumber(),
        updateAttributesData.getSearchCriteria().getItemNumber(),
        "Search criteria item number not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getSearchCriteria().getItemUPC(),
        updateAttributesData.getSearchCriteria().getItemUPC(),
        "Search criteria item upc not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getUpdateAttributes().getRotateDate(),
        updateAttributesData.getUpdateAttributes().getRotateDate(),
        "Update attributes rotate date not matched.");
    assertEquals(
        expiryDateUpdatePublisherData.getUpdateAttributes().isExpired(),
        updateAttributesData.getUpdateAttributes().isExpired(),
        "Update attributes is expired not matched.");

    verify(containerPersisterService, times(1)).getContainerByDeliveryNumber(anyLong());
    verify(containerItemRepository, times(0)).saveAll(anyList());
  }

  @Test
  public void testPublishContainerUpdate() {
    ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData =
        MockMessageData.getMockExpiryDateUpdatePublisherData();
    when(maasTopics.getPubContainerUpdateTopic())
        .thenReturn("TOPIC/RECEIVE/ENDGAME/CONTAINERUPDATE");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    endgameContainerService.publishContainerUpdate(expiryDateUpdatePublisherData);
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  private void assertContainer(
      Container container, List<ContainerItem> containerItems, Container container2) {
    /*
     * Asserting Container.
     */
    assertEquals(
        container.getTrackingId(),
        container2.getTrackingId(),
        "Container Tracking id not matched.");
    assertEquals(
        container.getLocation(), container2.getLocation(), "Container location not matched.");
    assertEquals(
        container.getDeliveryNumber(),
        container2.getDeliveryNumber(),
        "Container delivery number not matched.");
    assertEquals(
        container.getContainerType(), container2.getContainerType(), "Container type not matched.");
    assertEquals(
        container.getContainerStatus(),
        container2.getContainerStatus(),
        "Container status not matched.");
    assertEquals(container.getWeight(), container2.getWeight(), "Container weight not matched.");
    assertEquals(
        container.getWeightUOM(), container2.getWeightUOM(), "Container weight uom not matched.");
    assertEquals(container.getCube(), container2.getCube(), "Container cube not matched.");
    assertEquals(
        container.getCubeUOM(), container2.getCubeUOM(), "Container cube uom not matched.");
    assertEquals(
        container.getCtrShippable(),
        container2.getCtrShippable(),
        "Container shippable not matched.");
    assertEquals(
        container.getCtrReusable(), container2.getCtrReusable(), "Container reusable not matched.");
    assertEquals(
        container.getInventoryStatus(),
        container2.getInventoryStatus(),
        "Container inventory status not matched.");
    assertEquals(
        container.getCreateUser(),
        container2.getCreateUser(),
        "Container create user not matched.");
    assertEquals(
        container.getLastChangedUser(),
        container2.getLastChangedUser(),
        "Container last changed user not matched.");

    /*
     * Asserting ContainerItem.
     */
    assertEquals(
        containerItems.size(),
        container2.getContainerItems().size(),
        "Container items size not matched. Only one container item should be associated with a container.");
    assertEquals(
        containerItems.get(0).getTrackingId(),
        container2.getContainerItems().get(0).getTrackingId(),
        "Container item tracking id not matched.");
    assertEquals(
        containerItems.get(0).getPurchaseReferenceNumber(),
        container2.getContainerItems().get(0).getPurchaseReferenceNumber(),
        "Container item purchase reference number not matched.");
    assertEquals(
        containerItems.get(0).getPurchaseReferenceLineNumber(),
        container2.getContainerItems().get(0).getPurchaseReferenceLineNumber(),
        "Container item purchase reference line number not matched.");
    assertEquals(
        containerItems.get(0).getInboundChannelMethod(),
        container2.getContainerItems().get(0).getInboundChannelMethod(),
        "Container item inbound channel method not matched.");
    assertEquals(
        containerItems.get(0).getOutboundChannelMethod(),
        container2.getContainerItems().get(0).getOutboundChannelMethod(),
        "Container item outbound channel method not matched.");
    assertEquals(
        containerItems.get(0).getPurchaseCompanyId(),
        container2.getContainerItems().get(0).getPurchaseCompanyId(),
        "Container item purchase company id not matched.");
    assertEquals(
        containerItems.get(0).getPoDeptNumber(),
        container2.getContainerItems().get(0).getPoDeptNumber(),
        "Container item po dept number not matched.");
    assertEquals(
        containerItems.get(0).getItemNumber(),
        container2.getContainerItems().get(0).getItemNumber(),
        "Container item item number not matched.");
    assertEquals(
        containerItems.get(0).getGtin(),
        container2.getContainerItems().get(0).getGtin(),
        "Container item gtin not matched.");
    assertEquals(
        containerItems.get(0).getItemUPC(),
        container2.getContainerItems().get(0).getItemUPC(),
        "Container item item upc not matched.");
    assertEquals(
        containerItems.get(0).getCaseUPC(),
        container2.getContainerItems().get(0).getCaseUPC(),
        "Container item case upc not matched.");
    assertEquals(
        containerItems.get(0).getQuantity(),
        container2.getContainerItems().get(0).getQuantity(),
        "Container item quantity not matched.");
    assertEquals(
        containerItems.get(0).getQuantityUOM(),
        container2.getContainerItems().get(0).getQuantityUOM(),
        "Container item quantity uom not matched.");
    assertEquals(
        containerItems.get(0).getVnpkQty(),
        container2.getContainerItems().get(0).getVnpkQty(),
        "Container item vnpk quantity not matched.");
    assertEquals(
        containerItems.get(0).getWhpkQty(),
        container2.getContainerItems().get(0).getWhpkQty(),
        "Container item whpk quantity not matched.");
    assertEquals(
        containerItems.get(0).getWhpkSell(),
        container2.getContainerItems().get(0).getWhpkSell(),
        "Container item whpk sell not matched.");
    assertEquals(
        containerItems.get(0).getBaseDivisionCode(),
        container2.getContainerItems().get(0).getBaseDivisionCode(),
        "Container item base division code not matched.");
    assertEquals(
        containerItems.get(0).getFinancialReportingGroupCode(),
        container2.getContainerItems().get(0).getFinancialReportingGroupCode(),
        "Container item financial reporting group code not matched.");
    assertEquals(
        containerItems.get(0).getVnpkWgtQty(),
        container2.getContainerItems().get(0).getVnpkWgtQty(),
        "Container item vnpk wgt quantity not matched.");
    assertEquals(
        containerItems.get(0).getVnpkWgtUom(),
        container2.getContainerItems().get(0).getVnpkWgtUom(),
        "Container item vnpk wgt uom not matched.");
    assertEquals(
        containerItems.get(0).getVnpkcbqty(),
        container2.getContainerItems().get(0).getVnpkcbqty(),
        "Container item vnpk cb quantity not matched.");
    assertEquals(
        containerItems.get(0).getVnpkcbuomcd(),
        container2.getContainerItems().get(0).getVnpkcbuomcd(),
        "Container item vnpk cb uom cd not matched.");
    assertEquals(
        containerItems.get(0).getVendorNumber(),
        container2.getContainerItems().get(0).getVendorNumber(),
        "Container item vendor number not matched.");
    assertEquals(
        containerItems.get(0).getDescription(),
        container2.getContainerItems().get(0).getDescription(),
        "Container item description not matched.");
    assertEquals(
        containerItems.get(0).getSecondaryDescription(),
        container2.getContainerItems().get(0).getSecondaryDescription(),
        "Container item secondary description not matched.");

    assertEquals(
        containerItems.get(0).getRotateDate(),
        container2.getContainerItems().get(0).getRotateDate(),
        "Container item rotate date not matched.");
  }

  private List<PurchaseOrder> getPurchaseOrders() {
    List<PurchaseOrder> purchaseOrderList = null;
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/EndgameScanUPCOnePOLineV3.json")
              .getCanonicalPath();
      purchaseOrderList =
          Arrays.asList(
              gson.fromJson(
                  new String(Files.readAllBytes(Paths.get(dataPath))), PurchaseOrder[].class));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }

    return purchaseOrderList;
  }

  private List<PurchaseOrder> getPurchaseOrdersWithSscc() {
    List<PurchaseOrder> purchaseOrderList = null;
    try {
      String dataPath =
              new File("../../receiving-test/src/main/resources/json/EndgameScanUPCOnePOLineV3Sscc.json")
                      .getCanonicalPath();
      purchaseOrderList =
              Arrays.asList(
                      gson.fromJson(
                              new String(Files.readAllBytes(Paths.get(dataPath))), PurchaseOrder[].class));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }

    return purchaseOrderList;
  }

  private Container getContainer() {
    Container container = null;
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/EndgameContainer.json")
              .getCanonicalPath();
      container =
          gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), Container.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return container;
  }

  private ContainerItem getContainerItems() {
    ContainerItem containerItem = null;
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/EndgameContainerItem.json")
              .getCanonicalPath();
      containerItem =
          gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), ContainerItem.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return containerItem;
  }

  private Receipt getDummyReceipt() {
    return new Receipt();
  }

  @Test
  public void testCreateSaveContainerAndReceiptForContainerTransfer() {
    EndgameReceivingRequest receivingRequest = MockMessageData.getMockEndgameReceivingRequest();
    Container container = getContainer();
    doNothing().when(containerPersisterService).createReceiptAndContainer(anyList(), any());
    PurchaseOrder purchaseOrder = EndGameUtilsTestCase.getPurchaseOrder();
    endgameContainerService.createAndSaveContainerAndReceipt(
        receivingRequest, purchaseOrder, purchaseOrder.getLines().get(0), container);
  }

  @Test
  public void testGetContainer() {
    EndgameReceivingRequest receivingRequest = MockMessageData.getMockEndgameReceivingRequest();
    doNothing().when(containerPersisterService).createReceiptAndContainer(anyList(), any());
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithNoItemDetails();
    when(deliveryMetaDataService.findByDeliveryNumber(any()))
        .thenReturn(Optional.of(deliveryMetaData));
    PurchaseOrder purchaseOrder = EndGameUtilsTestCase.getPurchaseOrder();
    endgameContainerService.getContainer(
        receivingRequest, purchaseOrder, purchaseOrder.getLines().get(0));
  }
}
