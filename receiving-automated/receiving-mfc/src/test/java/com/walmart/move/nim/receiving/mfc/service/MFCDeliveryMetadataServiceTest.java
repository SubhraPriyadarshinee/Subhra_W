package com.walmart.move.nim.receiving.mfc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Optional;
import org.mockito.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MFCDeliveryMetadataServiceTest extends ReceivingTestBase {

  @InjectMocks private MFCDeliveryMetadataService mfcDeliveryMetadataService;

  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;

  private static final String TEST_DELIVERY_NUM = "550400001";

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);
  }

  @AfterMethod
  public void resetMock() {
    Mockito.reset(deliveryMetaDataRepository);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testFindAndUpdateDeliveryStatus_NoDeliveryMetaData() throws ReceivingException {
    when(deliveryMetaDataRepository.findByDeliveryNumber(any())).thenReturn(Optional.empty());
    mfcDeliveryMetadataService.findAndUpdateDeliveryStatus(
        TEST_DELIVERY_NUM, DeliveryStatus.COMPLETE);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testFindAndUpdateDeliveryStatus_SameDeliveryStatus() throws ReceivingException {
    when(deliveryMetaDataRepository.findByDeliveryNumber(any()))
        .thenReturn(
            Optional.of(
                DeliveryMetaData.builder()
                    .deliveryNumber(TEST_DELIVERY_NUM)
                    .deliveryStatus(DeliveryStatus.COMPLETE)
                    .build()));
    mfcDeliveryMetadataService.findAndUpdateDeliveryStatus(
        TEST_DELIVERY_NUM, DeliveryStatus.COMPLETE);
  }

  @Test
  public void testFindAndUpdateDeliveryStatus_WrkToComplete() throws ReceivingException {
    when(deliveryMetaDataRepository.findByDeliveryNumber(any()))
        .thenReturn(
            Optional.of(
                DeliveryMetaData.builder()
                    .deliveryNumber(TEST_DELIVERY_NUM)
                    .deliveryStatus(DeliveryStatus.WRK)
                    .build()));
    mfcDeliveryMetadataService.findAndUpdateDeliveryStatus(
        TEST_DELIVERY_NUM, DeliveryStatus.COMPLETE);
    ArgumentCaptor<DeliveryMetaData> deliveryMetaDataArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryMetaData.class);
    verify(deliveryMetaDataRepository, times(1)).save(deliveryMetaDataArgumentCaptor.capture());
    DeliveryMetaData deliveryMetaData = deliveryMetaDataArgumentCaptor.getValue();
    assertEquals(DeliveryStatus.COMPLETE, deliveryMetaData.getDeliveryStatus());
  }
}
