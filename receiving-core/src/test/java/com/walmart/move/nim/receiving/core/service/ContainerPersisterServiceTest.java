package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.ReceipPutawayQtySummaryByContainer;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemCustomRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ContainerPersisterServiceTest {

  @Mock private ReceiptRepository receiptRepository;
  @Mock private ContainerRepository containerRepository;
  @Mock private ContainerItemRepository containerItemRepository;
  @Mock private ContainerItemCustomRepository containerItemCustomRepository;
  @Mock private AppConfig appConfig;

  @InjectMocks private ContainerPersisterService containerPersisterService;

  private PageRequest pageReq;
  private Container sstkContainer = MockContainer.getSSTKContainer();
  private Container daContainer = MockContainer.getDAContainer();

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    pageReq = PageRequest.of(0, 10);
  }

  @AfterMethod
  public void tearDown() {
    reset(receiptRepository);
    reset(containerItemRepository);
    reset(containerRepository);
    reset(appConfig);
  }

  @Test
  public void testGetContainers_forParentOnly() throws Exception {

    List<Container> mockResultList = new ArrayList<>();
    Page<Container> mockResult = new PageImpl<>(mockResultList);
    doReturn(mockResult)
        .when(containerRepository)
        .findByParentTrackingIdIsNull(any(Pageable.class));

    List<Container> result = containerPersisterService.getContainers("id", "desc", 0, 10, true);
    assertEquals(result.size(), 0);

    verify(containerRepository).findByParentTrackingIdIsNull(any(Pageable.class));
  }

  @Test
  public void testGetContainers_forAll() throws Exception {

    List<Container> mockResultList = new ArrayList<>();
    Page<Container> mockResult = new PageImpl<>(mockResultList);
    doReturn(mockResult).when(containerRepository).findAll(any(Pageable.class));

    List<Container> result = containerPersisterService.getContainers("id", "desc", 0, 10, false);
    assertEquals(result.size(), 0);

    verify(containerRepository).findAll(any(Pageable.class));
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    Container container = MockContainer.getSSTKContainer();
    container.setId(1L);
    container.setCreateTs(cal.getTime());

    Container container1 = MockContainer.getSSTKContainer();
    container1.setId(10L);
    container1.setCreateTs(cal.getTime());

    when(containerRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(container, container1));
    doNothing().when(containerRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.CONTAINER)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = containerPersisterService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    Container container = MockContainer.getSSTKContainer();
    container.setId(1L);
    container.setCreateTs(cal.getTime());

    Container container1 = MockContainer.getSSTKContainer();
    container1.setId(10L);
    container1.setCreateTs(cal.getTime());

    when(containerRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(container, container1));
    doNothing().when(containerRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.CONTAINER)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = containerPersisterService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    Container container = MockContainer.getSSTKContainer();
    container.setId(1L);
    container.setCreateTs(cal.getTime());

    Container container1 = MockContainer.getSSTKContainer();
    container1.setId(10L);
    container1.setCreateTs(new Date());

    when(containerRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(container, container1));
    doNothing().when(containerRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.CONTAINER)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = containerPersisterService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void updateContainerStatusAndSaveReceipts() {
    Container container = MockContainer.getSSTKContainer();
    container.setId(1L);
    final ContainerItem containerItem = container.getContainerItems().get(0);
    List<Receipt> receipts = Collections.singletonList(new Receipt());

    containerPersisterService.updateContainerContainerItemReceipt(
        container, containerItem, "uerId", receipts);

    verify(containerRepository, times(1)).save(any());
    verify(containerItemRepository, times(1)).save(any());
    verify(receiptRepository, times(1)).saveAll(any());
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-29 - Checks if saveAll for receipt repository is
   * invoked
   */
  @Test
  public void testCreateMultipleReceiptAndContainer() {
    List<Container> containers = new ArrayList<>();
    Container container = MockContainer.getContainer();
    containers.add(container);
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(container.getContainerItems().get(0));
    List<Receipt> receipts = new ArrayList<>();
    containerPersisterService.createMultipleReceiptAndContainer(
        receipts, containers, containerItems);
    verify(receiptRepository, times(1)).saveAll(receipts);
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-29 - Checks if saveAll for container and container item
   * repository are invoked
   */
  @Test
  public void testSaveContainerAndContainerItems() {
    List<Container> containers = new ArrayList<>();
    Container container = MockContainer.getContainer();
    containers.add(container);
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(container.getContainerItems().get(0));
    containerPersisterService.saveContainerAndContainerItems(containers, containerItems);
    verify(containerRepository, times(1)).saveAll(containers);
    verify(containerItemRepository, times(1)).saveAll(containerItems);
  }

  @Test
  public void testDeleteContainerAndContainerItems() {
    List<Container> containers = new ArrayList<>();
    Container container = MockContainer.getContainer();
    containers.add(container);
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(container.getContainerItems().get(0));
    containerPersisterService.deleteContainerAndContainerItemsGivenTrackingId(
        Collections.singletonList("B67387000020002031"));
    verify(containerRepository, times(1))
        .deleteByTrackingIdIn(Collections.singletonList("B67387000020002031"));
    verify(containerItemRepository, times(1))
        .deleteByTrackingIdIn(Collections.singletonList("B67387000020002031"));
  }

  @Test
  public void testGetConsolidatedContainerForPublish() throws ReceivingException {
    when(containerRepository.findByTrackingId(anyString())).thenReturn(sstkContainer);
    when(containerItemRepository.findByTrackingId(anyString()))
        .thenReturn(sstkContainer.getContainerItems());
    when(containerRepository.findAllByParentTrackingId(anyString())).thenReturn(new HashSet<>());

    containerPersisterService.getConsolidatedContainerForPublish(sstkContainer.getTrackingId());

    verify(containerRepository, times(1)).findByTrackingId(anyString());
    verify(containerItemRepository, times(1)).findByTrackingId(anyString());
    verify(containerRepository, times(1)).findAllByParentTrackingId(anyString());
  }

  @Test
  public void testGetConsolidatedContainerForPublish_EmptyTrackingId() {
    try {
      containerPersisterService.getConsolidatedContainerForPublish(null);
    } catch (ReceivingException receivingException) {
      assert (true);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(), "trackingId should not be null");
    }
  }

  @Test
  public void testGetConsolidatedContainerForPublish_BadTrackingId() {
    try {
      when(containerRepository.findByTrackingId(anyString())).thenReturn(null);
      containerPersisterService.getConsolidatedContainerForPublish(sstkContainer.getTrackingId());
    } catch (ReceivingException receivingException) {
      assert (true);
      assertEquals(receivingException.getErrorResponse().getErrorMessage(), "container not found");
    }
  }

  @Test
  public void testGetConsolidatedContainerForPublish_WithChild() throws ReceivingException {
    when(containerRepository.findByTrackingId(anyString())).thenReturn(daContainer);
    when(containerItemRepository.findByTrackingId(anyString()))
        .thenReturn(daContainer.getContainerItems());
    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(daContainer.getChildContainers());
    when(containerItemRepository.findByTrackingIdIn(anyList()))
        .thenReturn(MockContainer.getMockContainerItem());

    containerPersisterService.getConsolidatedContainerForPublish(daContainer.getTrackingId());

    verify(containerRepository, times(1)).findByTrackingId(anyString());
    verify(containerItemRepository, times(1)).findByTrackingId(anyString());
    verify(containerRepository, times(1)).findAllByParentTrackingId(anyString());
    verify(containerItemRepository, times(1)).findByTrackingIdIn(anyList());
  }

  @Test
  public void testGetContainerByDeliveryNumberOrContainerStatus() throws ReceivingException {
    List<Container> containers = new ArrayList<Container>();
    containers.add(sstkContainer);
    List<String> containerStatusList =
        Arrays.asList(
            ReceivingConstants.STATUS_COMPLETE_NO_ASN, ReceivingConstants.STATUS_ACTIVE_NO_ASN);
    when(containerRepository.findByParentTrackingIdInAndContainerStatusIn(
            anyList(), eq(containerStatusList)))
        .thenReturn(containers);
    when(containerItemRepository.findByTrackingIdIn(any()))
        .thenReturn(sstkContainer.getContainerItems());
    when(appConfig.getInSqlBatchSize()).thenReturn(999);
    containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
        Arrays.asList("a329870000000000000000001"), containerStatusList);

    verify(containerRepository, Mockito.times(1))
        .findByParentTrackingIdInAndContainerStatusIn(anyList(), anyList());
    verify(containerItemRepository, Mockito.times(1)).findByTrackingIdIn(any());
  }

  @Test
  public void testGetInstructionIdsByTrackingIds() {
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
    containerPersisterService.getInstructionIdsByTrackingIds(
        Arrays.asList("a329870000000000000000001"));
    verify(containerRepository, Mockito.times(1))
        .getInstructionIdsByTrackingIds(anyList(), any(Integer.class), any(String.class));
  }

  @Test
  public void testReceivedContainerQuantityBySSCC() {
    doReturn(1)
        .when(containerItemRepository)
        .receivedContainerQuantityBySSCCAndStatus(anyString(), anyString(), anyInt(), anyString());
    int receivedQty = containerPersisterService.receivedContainerQuantityBySSCCAndStatus("SSCC");
    assertEquals(1, 1);
    verify(containerItemRepository, times(1))
        .receivedContainerQuantityBySSCCAndStatus(anyString(), anyString(), anyInt(), anyString());
  }

  @Test
  public void testReceivedContainerQuantityBySSCC_Notreceived() {
    doReturn(null)
        .when(containerItemRepository)
        .receivedContainerQuantityBySSCCAndStatus(anyString(), anyString(), anyInt(), anyString());
    int receivedQty = containerPersisterService.receivedContainerQuantityBySSCCAndStatus("SSCC");
    assertEquals(0, 0);
    verify(containerItemRepository, times(1))
        .receivedContainerQuantityBySSCCAndStatus(anyString(), anyString(), anyInt(), anyString());
  }

  @Test
  public void testGetReceiptPutawayQtySummaryByDeliveryNumber() {
    List<ReceipPutawayQtySummaryByContainer> mockResponse = new ArrayList<>();
    ReceipPutawayQtySummaryByContainer receipPutawayQtySummaryByContainer =
        new ReceipPutawayQtySummaryByContainer("2323232323", 1, 1L);
    mockResponse.add(receipPutawayQtySummaryByContainer);
    doReturn(mockResponse)
        .when(containerItemCustomRepository)
        .getReceiptPutawayQtySummaryByDeliveryNumber(anyLong());
    List<ReceipPutawayQtySummaryByContainer> response =
        containerPersisterService.getReceiptPutawayQtySummaryByDeliveryNumber(323232323L);
    assertTrue(response.size() > 0);
    verify(containerItemCustomRepository, times(1))
        .getReceiptPutawayQtySummaryByDeliveryNumber(anyLong());
  }

  @Test
  public void testGetConsolidatedContainerForPublish_ShipmentIdIsNotNull()
      throws ReceivingException {
    Container daContainer = MockContainer.getDAContainer();
    daContainer.setShipmentId("test1234");
    when(containerRepository.findByTrackingId(anyString())).thenReturn(daContainer);
    when(containerItemRepository.findByTrackingId(anyString()))
        .thenReturn(daContainer.getContainerItems());
    when(containerRepository.findAllByParentTrackingId(anyString()))
        .thenReturn(daContainer.getChildContainers());
    when(containerItemRepository.findByTrackingIdIn(anyList()))
        .thenReturn(MockContainer.getMockContainerItem());

    containerPersisterService.getConsolidatedContainerForPublish(daContainer.getTrackingId());

    verify(containerRepository, times(1)).findByTrackingId(anyString());
    verify(containerItemRepository, times(1)).findByTrackingId(anyString());
    verify(containerRepository, times(1)).findAllByParentTrackingId(anyString());
    verify(containerItemRepository, times(1)).findByTrackingIdIn(anyList());
  }
}
