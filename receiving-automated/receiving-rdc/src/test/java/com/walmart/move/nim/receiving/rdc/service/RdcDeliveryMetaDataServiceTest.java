package com.walmart.move.nim.receiving.rdc.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.rdc.utils.MockDeliveryMetaData;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcDeliveryMetaDataServiceTest {
  @InjectMocks private RdcDeliveryMetaDataService rdcDeliveryMetaDataService;
  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        rdcDeliveryMetaDataService, "deliveryMetaDataRepository", deliveryMetaDataRepository);
  }

  @AfterMethod
  public void resetMocks() {
    reset(deliveryMetaDataRepository);
  }

  @Test
  public void testFindAndUpdateForOsdrProcessing() {
    when(deliveryMetaDataRepository
            .findByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
                any(DeliveryStatus.class),
                any(Date.class),
                any(Date.class),
                any(DeliveryStatus.class),
                any(Date.class),
                any(Pageable.class)))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());
    when(deliveryMetaDataRepository.saveAll(anyList()))
        .thenReturn(MockDeliveryMetaData.getDeliveryMetaData_ForOSDR());
    List<DeliveryMetaData> deliveryMetaDataList =
        rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(5, 240, 10, null);
    assertEquals(deliveryMetaDataList.size(), 2);
    verify(deliveryMetaDataRepository, times(1))
        .findByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
            any(DeliveryStatus.class),
            any(Date.class),
            any(Date.class),
            any(DeliveryStatus.class),
            any(Date.class),
            any(Pageable.class));
    verify(deliveryMetaDataRepository, times(1)).saveAll(anyList());
  }

  @Test
  public void testFindAndUpdateForOsdrProcessing_EmptyList() {
    when(deliveryMetaDataRepository
            .findByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
                any(DeliveryStatus.class),
                any(Date.class),
                any(Date.class),
                any(DeliveryStatus.class),
                any(Date.class),
                any(Pageable.class)))
        .thenReturn(new ArrayList<>());
    List<DeliveryMetaData> deliveryMetaDataList =
        rdcDeliveryMetaDataService.findAndUpdateForOsdrProcessing(5, 240, 10, null);
    assertEquals(deliveryMetaDataList.size(), 0);
    verify(deliveryMetaDataRepository, times(1))
        .findByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
            any(DeliveryStatus.class),
            any(Date.class),
            any(Date.class),
            any(DeliveryStatus.class),
            any(Date.class),
            any(Pageable.class));
    verify(deliveryMetaDataRepository, times(0)).saveAll(anyList());
  }

  @Test
  public void testUpdateDeliveryMetaDataOsdrTimestamp_Null_Timestamps() {
    when(deliveryMetaDataRepository.findDeliveryMetaDataByDeliveryNumber(any(String.class)))
        .thenReturn(Arrays.asList(new DeliveryMetaData()));
    rdcDeliveryMetaDataService.updateDeliveryMetaData(12345L, DeliveryStatus.COMPLETE.name());
    verify(deliveryMetaDataRepository, times(1))
        .findDeliveryMetaDataByDeliveryNumber(any(String.class));
    verify(deliveryMetaDataRepository, times(1)).save(any());
  }

  @Test
  public void testFindDeliveryMetaData() {
    when(deliveryMetaDataRepository.findDeliveryMetaDataByDeliveryNumber(any(String.class)))
        .thenReturn(Arrays.asList(new DeliveryMetaData()));
    DeliveryMetaData deliveryMetaData = rdcDeliveryMetaDataService.findDeliveryMetaData(12345L);
    assertNotNull(deliveryMetaData);
    verify(deliveryMetaDataRepository, times(1))
        .findDeliveryMetaDataByDeliveryNumber(any(String.class));
  }

  @Test
  public void testFindDeliveryMetaData_No_Entry() {
    when(deliveryMetaDataRepository.findDeliveryMetaDataByDeliveryNumber(any(String.class)))
        .thenReturn(Arrays.asList());
    DeliveryMetaData deliveryMetaData = rdcDeliveryMetaDataService.findDeliveryMetaData(12345L);
    assertNull(deliveryMetaData);
    verify(deliveryMetaDataRepository, times(1))
        .findDeliveryMetaDataByDeliveryNumber(any(String.class));
  }

  @Test
  public void testUpdateDeliveryMetaDataOsdrTimestamp_Non_Null_Timestamps() {
    DeliveryMetaData deliveryMetadata = new DeliveryMetaData();
    deliveryMetadata.setUnloadingCompleteDate(new Date());
    deliveryMetadata.setOsdrLastProcessedDate(new Date());
    when(deliveryMetaDataRepository.findDeliveryMetaDataByDeliveryNumber(any(String.class)))
        .thenReturn(Arrays.asList(deliveryMetadata));
    rdcDeliveryMetaDataService.updateDeliveryMetaData(12345L, DeliveryStatus.COMPLETE.name());
    verify(deliveryMetaDataRepository, times(1))
        .findDeliveryMetaDataByDeliveryNumber(any(String.class));
    verify(deliveryMetaDataRepository, times(1)).save(any());
  }

  @Test
  public void testUpdateDeliveryMetaDataForCompleteDeliveryStatus() {
    DeliveryMetaData deliveryMetadata = new DeliveryMetaData();
    deliveryMetadata.setDeliveryStatus(DeliveryStatus.ARV);
    deliveryMetadata.setDeliveryNumber("12345");
    when(deliveryMetaDataRepository.findDeliveryMetaDataByDeliveryNumber(any(String.class)))
        .thenReturn(Arrays.asList(deliveryMetadata));
    rdcDeliveryMetaDataService.updateDeliveryMetaData(12345L, DeliveryStatus.COMPLETE.name());
    assertNotNull(deliveryMetadata.getOsdrLastProcessedDate());
    assertNotNull(deliveryMetadata.getUnloadingCompleteDate());
    assertEquals(deliveryMetadata.getDeliveryStatus().name(), DeliveryStatus.COMPLETE.name());
    verify(deliveryMetaDataRepository, times(1))
        .findDeliveryMetaDataByDeliveryNumber(any(String.class));
    verify(deliveryMetaDataRepository, times(1)).save(any());
  }

  @Test
  public void testUpdateDeliveryMetaDataForUnloadingCompleteDeliveryStatus() {
    DeliveryMetaData deliveryMetadata = new DeliveryMetaData();
    deliveryMetadata.setDeliveryStatus(DeliveryStatus.ARV);
    deliveryMetadata.setDeliveryNumber("12345");
    when(deliveryMetaDataRepository.findDeliveryMetaDataByDeliveryNumber(any(String.class)))
        .thenReturn(Arrays.asList(deliveryMetadata));
    rdcDeliveryMetaDataService.updateDeliveryMetaData(
        12345L, DeliveryStatus.UNLOADING_COMPLETE.name());
    assertNull(deliveryMetadata.getOsdrLastProcessedDate());
    assertNotNull(deliveryMetadata.getUnloadingCompleteDate());
    assertEquals(
        deliveryMetadata.getDeliveryStatus().name(), DeliveryStatus.UNLOADING_COMPLETE.name());
    verify(deliveryMetaDataRepository, times(1))
        .findDeliveryMetaDataByDeliveryNumber(any(String.class));
    verify(deliveryMetaDataRepository, times(1)).save(any());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void test_updateAuditInfo() {
    rdcDeliveryMetaDataService.updateAuditInfo(new DeliveryMetaData(), new ArrayList<>());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void test_updateDeliveryMetaDataForItemOverrides() {
    rdcDeliveryMetaDataService.updateDeliveryMetaDataForItemOverrides(
        new DeliveryMetaData(), null, null, null);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void test_updateAuditInfoInDeliveryMetaData() {
    rdcDeliveryMetaDataService.updateAuditInfoInDeliveryMetaData(new ArrayList<>(), 1, 1L);
  }

  @Test(expectedExceptions = ReceivingNotImplementedException.class)
  public void test_getReceivedQtyFromMetadata() {
    rdcDeliveryMetaDataService.getReceivedQtyFromMetadata(1234L, 1);
  }

  @Test
  public void test_findActiveDelivery() {
    when(deliveryMetaDataRepository.findAllByDeliveryStatus(any(DeliveryStatus.class)))
        .thenReturn(Collections.emptyList());
    rdcDeliveryMetaDataService.findActiveDelivery();
    verify(deliveryMetaDataRepository, times(1)).findAllByDeliveryStatus(any(DeliveryStatus.class));
  }
}
