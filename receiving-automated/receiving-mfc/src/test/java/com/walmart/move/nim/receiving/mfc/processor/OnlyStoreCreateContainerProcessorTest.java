package com.walmart.move.nim.receiving.mfc.processor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
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

public class OnlyStoreCreateContainerProcessorTest extends ReceivingTestBase {
  @InjectMocks private OnlyStoreCreateContainerProcessor onlyStoreCreateContainerProcessor;

  @Mock private MFCDeliveryService deliveryService;

  @Mock private MFCContainerService mfcContainerService;

  @Mock private MFCDeliveryMetadataService mfcDeliveryMetadataService;

  private static final long DELIVERY_NUM = 560135L;
  private static final String TRACKING_ID = "300008760310160015";

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        onlyStoreCreateContainerProcessor, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(
        onlyStoreCreateContainerProcessor, "mfcContainerService", mfcContainerService);
    ReflectionTestUtils.setField(
        onlyStoreCreateContainerProcessor,
        "mfcDeliveryMetadataService",
        mfcDeliveryMetadataService);
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(deliveryService);
    Mockito.reset(mfcContainerService);
    Mockito.reset(mfcDeliveryMetadataService);
  }

  @Test(expectedExceptions = ReceivingConflictException.class)
  public void testCreateContainerWithAsnDocumentForMFCPallet() {
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
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(new ContainerDTO());
    onlyStoreCreateContainerProcessor.createContainer(containerScanRequest);
  }

  @Test
  public void testCreateContainerWithAsnDocumentForStorePallet() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentOnlyStoreSite.json");

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
    onlyStoreCreateContainerProcessor.createContainer(containerScanRequest);
    verify(deliveryService, never())
        .findDeliveryDocumentByPalletAndDelivery(any(ScanPalletRequest.class), anyBoolean());
    verify(mfcContainerService, times(1))
        .createContainer(any(Container.class), any(ASNDocument.class));
    Assert.assertFalse(containerScanRequest.getAsnDocument().isOverage());
  }

  @Test
  public void testCreateContainerWithAsnDocumentForMixPallet() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentWithMixedItems.json");

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
    when(mfcContainerService.createContainer(any(Container.class), any(ASNDocument.class)))
        .thenReturn(new ContainerDTO());
    onlyStoreCreateContainerProcessor.createContainer(containerScanRequest);
  }
}
