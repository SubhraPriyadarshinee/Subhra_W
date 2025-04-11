package com.walmart.move.nim.receiving.mfc.processor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryMetadataService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Optional;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AutoMFCProcessorTest extends ReceivingTestBase {

  @InjectMocks private AutoMFCProcessor autoMFCProcessor;
  @Mock private AsyncPersister asyncPersister;
  @Mock private MFCDeliveryService deliveryService;

  @Mock private MFCDeliveryMetadataService mfcDeliveryMetadataService;

  private static final String DELIVERY_NUM = "100";

  @BeforeClass
  private void init() {

    MockitoAnnotations.openMocks(this);
    ReflectionTestUtils.setField(autoMFCProcessor, "asyncPersister", asyncPersister);
    ReflectionTestUtils.setField(autoMFCProcessor, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(
        autoMFCProcessor, "mfcDeliveryMetadataService", mfcDeliveryMetadataService);
  }

  @AfterMethod
  private void resetMocks() {
    Mockito.reset(asyncPersister);
    Mockito.reset(deliveryService);
    Mockito.reset(mfcDeliveryMetadataService);
  }

  @Test
  public void testSaveDeliveryMetadata_SchToArrived() {
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(
            Optional.of(DeliveryMetaData.builder().deliveryStatus(DeliveryStatus.SCH).build()));
    ArgumentCaptor<DeliveryMetaData> deliveryMetaDataArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryMetaData.class);
    autoMFCProcessor.saveDeliveryMetadata(
        DeliveryUpdateMessage.builder()
            .deliveryStatus(DeliveryStatus.ARV.name())
            .deliveryNumber(DELIVERY_NUM)
            .build());
    verify(mfcDeliveryMetadataService, times(1)).save(deliveryMetaDataArgumentCaptor.capture());
    DeliveryMetaData deliveryMetaData = deliveryMetaDataArgumentCaptor.getValue();
    assertEquals(deliveryMetaData.getDeliveryStatus(), DeliveryStatus.ARV);
  }

  @Test
  public void testSaveDeliveryMetadata_SchToWrk() {
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(
            Optional.of(DeliveryMetaData.builder().deliveryStatus(DeliveryStatus.SCH).build()));
    ArgumentCaptor<DeliveryMetaData> deliveryMetaDataArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryMetaData.class);
    autoMFCProcessor.saveDeliveryMetadata(
        DeliveryUpdateMessage.builder()
            .deliveryStatus(DeliveryStatus.WRK.name())
            .deliveryNumber(DELIVERY_NUM)
            .build());
    verify(mfcDeliveryMetadataService, times(1)).save(deliveryMetaDataArgumentCaptor.capture());
    DeliveryMetaData deliveryMetaData = deliveryMetaDataArgumentCaptor.getValue();
    assertEquals(deliveryMetaData.getDeliveryStatus(), DeliveryStatus.WRK);
  }

  @Test
  public void testSaveDeliveryMetadata_WrkToSch() {
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(
            Optional.of(DeliveryMetaData.builder().deliveryStatus(DeliveryStatus.WRK).build()));
    autoMFCProcessor.saveDeliveryMetadata(
        DeliveryUpdateMessage.builder()
            .deliveryStatus(DeliveryStatus.SCH.name())
            .deliveryNumber(DELIVERY_NUM)
            .build());
    verify(mfcDeliveryMetadataService, never()).save(any());
  }

  @Test
  public void testSaveDeliveryMetadata_WrkToArv() {
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString()))
        .thenReturn(
            Optional.of(DeliveryMetaData.builder().deliveryStatus(DeliveryStatus.WRK).build()));
    autoMFCProcessor.saveDeliveryMetadata(
        DeliveryUpdateMessage.builder()
            .deliveryStatus(DeliveryStatus.ARV.name())
            .deliveryNumber(DELIVERY_NUM)
            .build());
    verify(mfcDeliveryMetadataService, never()).save(any());
  }
}
