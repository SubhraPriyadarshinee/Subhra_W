package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.SSCC_RECEIVED_ALREADY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PALLET_ALREADY_RECEIVED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.OverageType;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.model.gdm.ScanPalletRequest;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryMetadataService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StoreInboundCreateContainerProcessorTest extends ReceivingTestBase {

  @InjectMocks private StoreInboundCreateContainerProcessor storeInboundCreateContainerProcessor;

  @Mock private MFCDeliveryService deliveryService;

  @Mock private MFCContainerService mfcContainerService;

  @Mock private MFCDeliveryMetadataService mfcDeliveryMetadataService;

  private static final long DELIVERY_NUM = 560135L;
  private static final String TRACKING_ID = "300008760310160015";
  private ASNDocument asnDocument;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        storeInboundCreateContainerProcessor, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(
        storeInboundCreateContainerProcessor, "mfcContainerService", mfcContainerService);
    ReflectionTestUtils.setField(
        storeInboundCreateContainerProcessor,
        "mfcDeliveryMetadataService",
        mfcDeliveryMetadataService);
    asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(deliveryService);
    Mockito.reset(mfcContainerService);
    Mockito.reset(mfcDeliveryMetadataService);
  }

  @Test
  public void testCreateContainerWithAsnDocument() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");

    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder()
            .asnDocument(asnDocument)
            .trackingId(TRACKING_ID)
            .deliveryNumber(DELIVERY_NUM)
            .build();
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(deliveryService.findDeliveryDocumentByPalletAndDelivery(
            any(ScanPalletRequest.class), anyBoolean()))
        .thenReturn(asnDocument);
    when(mfcContainerService.createTransientContainer(
            any(ContainerScanRequest.class), any(ASNDocument.class)))
        .thenReturn(new Container());
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(new ContainerDTO());
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(deliveryService, never())
        .findDeliveryDocumentByPalletAndDelivery(any(ScanPalletRequest.class), anyBoolean());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    Assert.assertFalse(containerScanRequest.getAsnDocument().isOverage());
  }

  @Test
  public void testCreateContainerWithOutAsnDocument() {

    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder().deliveryNumber(DELIVERY_NUM).trackingId(TRACKING_ID).build();
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());

    when(deliveryService.findDeliveryDocumentByPalletAndDelivery(
            any(ScanPalletRequest.class), anyBoolean()))
        .thenReturn(asnDocument);
    when(mfcContainerService.createTransientContainer(
            any(ContainerScanRequest.class), any(ASNDocument.class)))
        .thenReturn(new Container());
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(new ContainerDTO());
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(deliveryService, times(1))
        .findDeliveryDocumentByPalletAndDelivery(any(ScanPalletRequest.class), anyBoolean());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testCreateContainerDocumentNotFound() {
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder().deliveryNumber(DELIVERY_NUM).trackingId(TRACKING_ID).build();
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());

    when(deliveryService.findDeliveryDocumentByPalletAndDelivery(
            any(ScanPalletRequest.class), anyBoolean()))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.DELIVERY_NOT_FOUND,
                String.format(
                    "ASN = {} from delivery = {} is not available",
                    containerScanRequest.getTrackingId(),
                    containerScanRequest.getDeliveryNumber())));
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(new ContainerDTO());
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
  }

  @Test(expectedExceptions = ReceivingConflictException.class)
  public void testForAlreadyReceivedContainer() {
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder().deliveryNumber(DELIVERY_NUM).trackingId(TRACKING_ID).build();
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());

    when(mfcContainerService.findTopBySsccNumberAndDeliveryNumber(anyString(), anyLong()))
        .thenThrow(
            new ReceivingConflictException(
                SSCC_RECEIVED_ALREADY,
                String.format(
                    PALLET_ALREADY_RECEIVED,
                    containerScanRequest.getTrackingId(),
                    containerScanRequest.getDeliveryNumber()),
                containerScanRequest.getTrackingId()));
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
  }

  @Test
  public void testCreateContainerForOverage() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    when(mfcDeliveryMetadataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    ContainerScanRequest containerScanRequest =
        ContainerScanRequest.builder()
            .asnDocument(asnDocument)
            .trackingId(TRACKING_ID)
            .deliveryNumber(DELIVERY_NUM)
            .overageType(OverageType.DIFF_ASN_SAME_SITE)
            .build();
    when(deliveryService.findDeliveryDocumentByPalletAndDelivery(
            any(ScanPalletRequest.class), anyBoolean()))
        .thenReturn(asnDocument);
    when(mfcContainerService.createTransientContainer(
            any(ContainerScanRequest.class), any(ASNDocument.class)))
        .thenReturn(new Container());
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(new ContainerDTO());
    storeInboundCreateContainerProcessor.createContainer(containerScanRequest);
    verify(deliveryService, never())
        .findDeliveryDocumentByPalletAndDelivery(any(ScanPalletRequest.class), anyBoolean());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    Assert.assertTrue(containerScanRequest.getAsnDocument().isOverage());
  }
}
