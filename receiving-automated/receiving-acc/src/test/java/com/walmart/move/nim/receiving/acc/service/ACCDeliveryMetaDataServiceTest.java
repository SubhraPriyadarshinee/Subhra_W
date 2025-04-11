package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceImpl;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACCDeliveryMetaDataServiceTest extends ReceivingTestBase {
  @InjectMocks private ACCDeliveryMetaDataService accDeliveryMetaDataService;
  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Mock private DeliveryServiceImpl deliveryService;
  @Captor private ArgumentCaptor<List<DeliveryMetaData>> deliveryMetadataCaptor;
  private String deliveryNum1 = "123456879";
  private String deliveryNum2 = "987654321";
  private PageRequest pageReq;
  private DeliveryUpdateMessage deliveryUpdateMessage;

  private Delivery delivery;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        accDeliveryMetaDataService, "deliveryMetaDataRepository", deliveryMetaDataRepository);
    pageReq = PageRequest.of(0, 10);
  }

  @AfterMethod
  public void tearDown() {
    reset(deliveryMetaDataRepository);
    reset(deliveryService);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testFindAndUpdateForOsdrProcessing() {
    accDeliveryMetaDataService.findAndUpdateForOsdrProcessing(1, 1L, 1, null);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testUpdateAuditInfo() {
    accDeliveryMetaDataService.updateAuditInfo(null, null);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testUpdateDeliveryMetaDataForItemOverrides() {
    accDeliveryMetaDataService.updateDeliveryMetaDataForItemOverrides(
        DeliveryMetaData.builder().build(), "", "", "");
  }

  @Test
  public void testFindAndUpdateDeliveryStatus_DeliveryDoesNotExists() {
    when(deliveryMetaDataRepository.findByDeliveryNumber(deliveryNum1))
        .thenReturn(Optional.empty());
    accDeliveryMetaDataService.findAndUpdateDeliveryStatus(deliveryNum1, DeliveryStatus.SYS_REO);
    ArgumentCaptor<DeliveryMetaData> captor = ArgumentCaptor.forClass(DeliveryMetaData.class);
    verify(deliveryMetaDataRepository, times(1)).save(captor.capture());
    DeliveryMetaData deliveryMetaData = captor.getValue();
    assertEquals(deliveryMetaData.getDeliveryNumber(), deliveryNum1);
    assertEquals(deliveryMetaData.getDeliveryStatus(), DeliveryStatus.SYS_REO);
  }

  @Test
  public void testFindAndUpdateDeliveryStatus_DeliverExists() {
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber(deliveryNum1)
            .deliveryStatus(DeliveryStatus.COMPLETE)
            .build();
    when(deliveryMetaDataRepository.findByDeliveryNumber(deliveryNum1))
        .thenReturn(Optional.of(deliveryMetaData));
    accDeliveryMetaDataService.findAndUpdateDeliveryStatus(deliveryNum1, DeliveryStatus.SYS_REO);
    ArgumentCaptor<DeliveryMetaData> captor = ArgumentCaptor.forClass(DeliveryMetaData.class);
    verify(deliveryMetaDataRepository, times(1)).save(captor.capture());
    deliveryMetaData = captor.getValue();
    assertEquals(deliveryMetaData.getDeliveryNumber(), deliveryNum1);
    assertEquals(deliveryMetaData.getDeliveryStatus(), DeliveryStatus.SYS_REO);
  }

  @Test
  public void testCompleteSystematicallyReopenedDeliveriesBefore_noEvents()
      throws ReceivingException {
    Date beforeDate = new Date();
    when(deliveryMetaDataRepository.findAllByDeliveryStatusAndCreatedDateLessThan(
            DeliveryStatus.SYS_REO, beforeDate))
        .thenReturn(null);

    accDeliveryMetaDataService.completeSystematicallyReopenedDeliveriesBefore(beforeDate);
    verify(deliveryMetaDataRepository, times(1))
        .findAllByDeliveryStatusAndCreatedDateLessThan(DeliveryStatus.SYS_REO, beforeDate);
    verify(deliveryService, times(0)).completeDelivery(anyLong(), anyBoolean(), any());
    verify(deliveryMetaDataRepository, times(0)).saveAll(anyList());
  }

  @Test
  public void testCompleteSystematicallyReopenedDeliveriesBefore() throws ReceivingException {
    DeliveryMetaData dmd11 = getSysReoDeliveryMetadata(deliveryNum1, 32987);
    DeliveryMetaData dmd12 = getSysReoDeliveryMetadata(deliveryNum2, 32818);
    List<DeliveryMetaData> deliveryMetaDataList = new ArrayList<>();
    deliveryMetaDataList.add(dmd11);
    deliveryMetaDataList.add(dmd12);
    Date beforeDate = new Date();
    when(deliveryMetaDataRepository.findAllByDeliveryStatusAndCreatedDateLessThan(
            DeliveryStatus.SYS_REO, beforeDate))
        .thenReturn(deliveryMetaDataList);

    accDeliveryMetaDataService.completeSystematicallyReopenedDeliveriesBefore(beforeDate);

    verify(deliveryMetaDataRepository, times(1))
        .findAllByDeliveryStatusAndCreatedDateLessThan(DeliveryStatus.SYS_REO, beforeDate);
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryService, times(1))
        .completeDelivery(eq(Long.parseLong(deliveryNum1)), anyBoolean(), captor.capture());
    assertEquals(captor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32987");
    verify(deliveryService, times(1))
        .completeDelivery(eq(Long.parseLong(deliveryNum2)), anyBoolean(), captor.capture());
    assertEquals(captor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32818");
    verify(deliveryMetaDataRepository, times(1)).saveAll(deliveryMetadataCaptor.capture());
    List<DeliveryMetaData> deliveryMetaData = deliveryMetadataCaptor.getValue();
    assertEquals(deliveryMetaData.size(), 2);
    deliveryMetaData.forEach(
        dmd1 -> assertEquals(dmd1.getDeliveryStatus(), DeliveryStatus.COMPLETE));
  }

  @Test
  public void testCompleteSystematicallyReopenedDeliveriesBefore_completeFail()
      throws ReceivingException {
    DeliveryMetaData dmd11 = getSysReoDeliveryMetadata(deliveryNum1, 32987);
    DeliveryMetaData dmd12 = getSysReoDeliveryMetadata(deliveryNum2, 32818);
    List<DeliveryMetaData> deliveryMetaDataList = new ArrayList<>();
    deliveryMetaDataList.add(dmd11);
    deliveryMetaDataList.add(dmd12);
    Date beforeDate = new Date();
    when(deliveryMetaDataRepository.findAllByDeliveryStatusAndCreatedDateLessThan(
            DeliveryStatus.SYS_REO, beforeDate))
        .thenReturn(deliveryMetaDataList);
    when(deliveryService.completeDelivery(eq(Long.parseLong(deliveryNum2)), anyBoolean(), any()))
        .thenThrow(
            new ReceivingException(
                ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_CODE));

    accDeliveryMetaDataService.completeSystematicallyReopenedDeliveriesBefore(beforeDate);
    verify(deliveryMetaDataRepository, times(1))
        .findAllByDeliveryStatusAndCreatedDateLessThan(DeliveryStatus.SYS_REO, beforeDate);
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryService, times(1))
        .completeDelivery(eq(Long.parseLong(deliveryNum1)), anyBoolean(), captor.capture());
    assertEquals(captor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32987");
    verify(deliveryService, times(1))
        .completeDelivery(eq(Long.parseLong(deliveryNum2)), anyBoolean(), captor.capture());
    assertEquals(captor.getValue().getFirst(ReceivingConstants.TENENT_FACLITYNUM), "32818");
    verify(deliveryMetaDataRepository, times(1)).saveAll(deliveryMetadataCaptor.capture());
    List<DeliveryMetaData> deliveryMetaData = deliveryMetadataCaptor.getValue();
    assertEquals(deliveryMetaData.size(), 2);
    assertEquals(
        deliveryMetaData
            .stream()
            .filter(dmd -> dmd.getDeliveryStatus().equals(DeliveryStatus.COMPLETE))
            .collect(Collectors.toList())
            .size(),
        1);
    assertEquals(
        deliveryMetaData
            .stream()
            .filter(dmd -> dmd.getDeliveryStatus().equals(DeliveryStatus.SYS_REO))
            .collect(Collectors.toList())
            .size(),
        1);
  }

  private DeliveryMetaData getSysReoDeliveryMetadata(String deliveryNum, Integer facilityNum) {
    DeliveryMetaData dmd =
        DeliveryMetaData.builder()
            .id((int) Math.random())
            .deliveryNumber(deliveryNum)
            .deliveryStatus(DeliveryStatus.SYS_REO)
            .build();
    dmd.setFacilityCountryCode("US");
    dmd.setFacilityNum(facilityNum);
    return dmd;
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    DeliveryMetaData deliveryMetaData = getSysReoDeliveryMetadata(deliveryNum2, 32987);
    deliveryMetaData.setId(1L);
    deliveryMetaData.setCreatedDate(cal.getTime());

    DeliveryMetaData deliveryMetaData1 = getSysReoDeliveryMetadata(deliveryNum2, 32987);
    deliveryMetaData1.setId(10L);
    deliveryMetaData1.setCreatedDate(cal.getTime());

    when(deliveryMetaDataRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(deliveryMetaData, deliveryMetaData1));
    doNothing().when(deliveryMetaDataRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.DELIVERY_METADATA)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = accDeliveryMetaDataService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    DeliveryMetaData deliveryMetaData = getSysReoDeliveryMetadata(deliveryNum2, 32987);
    deliveryMetaData.setId(1L);
    deliveryMetaData.setCreatedDate(cal.getTime());

    DeliveryMetaData deliveryMetaData1 = getSysReoDeliveryMetadata(deliveryNum2, 32987);
    deliveryMetaData1.setId(10L);
    deliveryMetaData1.setCreatedDate(cal.getTime());

    when(deliveryMetaDataRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(deliveryMetaData, deliveryMetaData1));
    doNothing().when(deliveryMetaDataRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.DELIVERY_METADATA)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = accDeliveryMetaDataService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    DeliveryMetaData deliveryMetadata = getSysReoDeliveryMetadata(deliveryNum2, 32987);
    deliveryMetadata.setId(1L);
    deliveryMetadata.setCreatedDate(cal.getTime());

    DeliveryMetaData deliveryMetadata1 = getSysReoDeliveryMetadata(deliveryNum2, 32987);
    deliveryMetadata1.setId(10L);
    deliveryMetadata1.setCreatedDate(new Date());

    when(deliveryMetaDataRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(deliveryMetadata, deliveryMetadata1));
    doNothing().when(deliveryMetaDataRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.DELIVERY_METADATA)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = accDeliveryMetaDataService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testupdateAuditInfoInDeliveryMetaData() {
    accDeliveryMetaDataService.updateAuditInfoInDeliveryMetaData(new ArrayList<>(), 1, 1L);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void testGetReceivedQtyFromMetadata() {
    accDeliveryMetaDataService.getReceivedQtyFromMetadata(1234L, 1L);
  }

  @Test
  public void testPersistDeliveryForUpdatedDeliveryWhenDeliveryIsFoundInDeliveryMetaDataTable()
      throws ReceivingException {
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetaData());
    accDeliveryMetaDataService.persistMetaData(getDeliveryMessageInformation());
    verify(deliveryMetaDataRepository, times(1)).save(any(DeliveryMetaData.class));
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(anyString());
  }

  @Test
  public void
      testPersistFINALIZEDDeliveryForUpdatedDeliveryWhenDeliveryIsFoundInDeliveryMetaDataTable()
          throws ReceivingException {
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetaData());
    accDeliveryMetaDataService.persistMetaData(getDeliveryFinalizedMessageInformation());
    verify(deliveryMetaDataRepository, times(1)).save(any(DeliveryMetaData.class));
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(anyString());
  }

  @Test
  public void testPersistDeliveryForUpdatedDeliveryWhenDeliveryIsNotFoundInDeliveryMetaDataTable()
      throws ReceivingException {
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    accDeliveryMetaDataService.persistMetaData(getDeliveryMessageInformation());
    verify(deliveryMetaDataRepository, times(1)).save(any(DeliveryMetaData.class));
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(anyString());
  }

  @Test
  public void
      testPersistDeliveryForUpdatedDeliveryWhenDeliveryIsFoundInDeliveryMetaDataTableWithInvalidStatus()
          throws ReceivingException {
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetaData());
    accDeliveryMetaDataService.persistMetaData(getDeliveryFinalizedMessageInformationWithInvalid());
    verify(deliveryMetaDataRepository, times(0)).save(any(DeliveryMetaData.class));
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(anyString());
  }

  private DeliveryUpdateMessage getDeliveryFinalizedMessageInformation() {
    deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setCountryCode("US");
    deliveryUpdateMessage.setSiteNumber("32898");
    deliveryUpdateMessage.setDeliveryNumber("30008889");
    deliveryUpdateMessage.setEventType("FINALIZED");
    deliveryUpdateMessage.setDeliveryStatus("FNL");
    deliveryUpdateMessage.setUrl(
        "https://dev.gdm.prod.us.walmart.net/document/v2/deliveries/56003401");
    return deliveryUpdateMessage;
  }

  private DeliveryUpdateMessage getDeliveryMessageInformation() {
    deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setCountryCode("US");
    deliveryUpdateMessage.setSiteNumber("32898");
    deliveryUpdateMessage.setDeliveryNumber("30008889");
    deliveryUpdateMessage.setEventType("DELIVERY_UPDATED");
    deliveryUpdateMessage.setDeliveryStatus("WRK");
    deliveryUpdateMessage.setUrl(
        "https://dev.gdm.prod.us.walmart.net/document/v2/deliveries/56003401");
    return deliveryUpdateMessage;
  }

  private DeliveryUpdateMessage getDeliveryFinalizedMessageInformationWithInvalid() {
    deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setCountryCode("US");
    deliveryUpdateMessage.setSiteNumber("32898");
    deliveryUpdateMessage.setDeliveryNumber("30008889");
    deliveryUpdateMessage.setEventType("FINALIZED");
    deliveryUpdateMessage.setUrl(
        "https://dev.gdm.prod.us.walmart.net/document/v2/deliveries/56003401");
    return deliveryUpdateMessage;
  }

  private Optional<DeliveryMetaData> getDeliveryMetaData() {
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber(String.valueOf(30008889L))
            .deliveryStatus(DeliveryStatus.ARV)
            .build();
    return Optional.of(deliveryMetaData);
  }
}
