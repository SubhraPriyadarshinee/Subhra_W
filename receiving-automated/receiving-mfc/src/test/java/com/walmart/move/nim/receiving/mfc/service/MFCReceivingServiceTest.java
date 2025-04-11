package com.walmart.move.nim.receiving.mfc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.common.Quantity;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MFCReceivingServiceTest extends ReceivingTestBase {

  @InjectMocks private MFCReceivingService mfcReceivingService;

  @Mock private MFCContainerService mfcContainerService;

  @Mock private MFCReceiptService mfcReceiptService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private ContainerItemRepository containerItemRepository;

  @InjectMocks private TenantContext tenantContext;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(5504);
  }

  @AfterMethod
  public void resetMocks() {
    reset(mfcReceiptService);
    reset(mfcContainerService);
    reset(containerItemRepository);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void createReceiptTest_happyPath() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(Collections.singletonList(new Quantity(10L, "EA", QuantityType.DECANTED)))
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainers.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    if (receipt1.getInvoiceLineNumber() == 52) {
      Receipt temp = receipt1;
      receipt1 = receipt2;
      receipt2 = temp;
    }
    assertEquals(receipt1.getQuantity().intValue(), 6);
    assertEquals(receipt2.getQuantity().intValue(), 4);

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 6);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 4);
  }

  @Test
  public void createReceiptTest_happyPathWithExceptions() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Arrays.asList(
                    new Quantity(7L, "EA", QuantityType.DECANTED),
                    new Quantity(3L, "EA", QuantityType.DAMAGE),
                    new Quantity(3L, "EA", QuantityType.REJECTED)))
            .build();
    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainersWithExceptions.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    Receipt receipt3 = receipts.get(2);
    assertEquals(receipt1.getQuantity().intValue(), 7);
    assertEquals(receipt2.getFbDamagedQty().intValue(), 3);
    assertEquals(receipt3.getFbRejectedQty().intValue(), 3);

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 19);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 6);
  }

  @Test
  public void createReceiptTest_happyPathWithExceptions2() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Arrays.asList(
                    new Quantity(7L, "EA", QuantityType.DECANTED),
                    new Quantity(3L, "EA", QuantityType.DAMAGE),
                    new Quantity(3L, "EA", QuantityType.REJECTED)))
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainersWithOverage.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    Receipt receipt3 = receipts.get(2);
    assertEquals(receipt1.getQuantity().intValue(), 7);
    assertEquals(receipt2.getFbDamagedQty().intValue(), 3);
    assertEquals(receipt3.getFbRejectedQty().intValue(), 3);

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 26);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 6);
  }

  @Test
  public void createReceiptTest_ColdChainIssue() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Collections.singletonList(new Quantity(10L, "EA", QuantityType.COLD_CHAIN_REJECT)))
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainers.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    if (receipt1.getInvoiceLineNumber() == 52) {
      Receipt temp = receipt1;
      receipt1 = receipt2;
      receipt2 = temp;
    }
    assertEquals(receipt1.getFbRejectedQty().intValue(), 6);
    assertEquals(receipt2.getFbRejectedQty().intValue(), 4);

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 6);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 4);
  }

  @Test
  public void createReceiptTest_NotMFCItems() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Collections.singletonList(new Quantity(10L, "EA", QuantityType.NOTMFCASSORTMENT)))
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainers.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    if (receipt1.getInvoiceLineNumber() == 52) {
      Receipt temp = receipt1;
      receipt1 = receipt2;
      receipt2 = temp;
    }
    assertEquals(receipt1.getFbRejectedQty().intValue(), 6);
    assertEquals(receipt2.getFbRejectedQty().intValue(), 4);

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 6);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 4);
  }

  @Test
  public void createReceiptTest_Oversized() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Collections.singletonList(new Quantity(10L, "EA", QuantityType.MFCOVERSIZE)))
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainers.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    if (receipt1.getInvoiceLineNumber() == 52) {
      Receipt temp = receipt1;
      receipt1 = receipt2;
      receipt2 = temp;
    }
    assertEquals(receipt1.getFbRejectedQty().intValue(), 6);
    assertEquals(receipt2.getFbRejectedQty().intValue(), 4);

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 6);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 4);
  }

  @Test
  public void createReceiptTest_FreshnessExpired() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Collections.singletonList(
                    new Quantity(10L, "EA", QuantityType.FRESHNESSEXPIRATION)))
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainers.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    if (receipt1.getInvoiceLineNumber() == 52) {
      Receipt temp = receipt1;
      receipt1 = receipt2;
      receipt2 = temp;
    }
    assertEquals(receipt1.getFbRejectedQty().intValue(), 6);
    assertEquals(receipt2.getFbRejectedQty().intValue(), 4);

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 6);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 4);
  }

  @Test
  public void createReceiptTest_MultipleRejects() {

    List<Quantity> quantities = new ArrayList<>();
    quantities.add(new Quantity(3L, "EA", QuantityType.FRESHNESSEXPIRATION));
    quantities.add(new Quantity(4L, "EA", QuantityType.REJECTED));
    quantities.add(new Quantity(2L, "EA", QuantityType.NOTMFCASSORTMENT));
    quantities.add(new Quantity(2L, "EA", QuantityType.MFCOVERSIZE));

    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(quantities)
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainers.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 6);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 5);

    for (Receipt receipt : receipts) {
      if (OSDRCode.R87.getCode().equals(receipt.getFbRejectedReasonCode())) {
        assertEquals(receipt.getFbRejectedQty().intValue(), 4);
      }
      if (OSDRCode.R86.getCode().equals(receipt.getFbRejectedReasonCode())) {
        assertEquals(receipt.getFbRejectedQty().intValue(), 2);
      }
      if (OSDRCode.R88.getCode().equals(receipt.getFbRejectedReasonCode())) {
        assertEquals(receipt.getFbRejectedQty().intValue(), 2);
      }
      if (OSDRCode.R78.getCode().equals(receipt.getFbRejectedReasonCode())) {
        if (receipt.getInvoiceLineNumber().equals(51)) {
          assertEquals(receipt.getFbRejectedQty().intValue(), 2);
        } else if (receipt.getInvoiceLineNumber().equals(52)) {
          assertEquals(receipt.getFbRejectedQty().intValue(), 1);
        }
      }
    }
  }

  @Test
  public void createReceiptTest_MFCToStoreTransfer() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Collections.singletonList(
                    new Quantity(10L, "EA", QuantityType.MFC_TO_STORE_TRANSFER)))
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainers.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    if (receipt1.getInvoiceLineNumber() == 52) {
      Receipt temp = receipt1;
      receipt1 = receipt2;
      receipt2 = temp;
    }
    assertEquals(receipt1.getFbRejectedQty().intValue(), 6);
    assertEquals(receipt2.getFbRejectedQty().intValue(), 4);

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 6);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 4);
  }

  @Test
  public void createReceiptTest_WrongTempZone() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Collections.singletonList(new Quantity(10L, "EA", QuantityType.WRONG_TEMP_ZONE)))
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainers.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    if (receipt1.getInvoiceLineNumber() == 52) {
      Receipt temp = receipt1;
      receipt1 = receipt2;
      receipt2 = temp;
    }
    assertEquals(receipt1.getFbRejectedQty().intValue(), 6);
    assertEquals(receipt2.getFbRejectedQty().intValue(), 4);

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 6);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 4);
  }

  @Test
  public void createReceiptTest_NGRShortage() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(
                Collections.singletonList(new Quantity(10L, "EA", QuantityType.NGR_SHORTAGE)))
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainers.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    if (receipt1.getInvoiceLineNumber() == 52) {
      Receipt temp = receipt1;
      receipt1 = receipt2;
      receipt2 = temp;
    }
    assertEquals(receipt1.getFbShortQty().intValue(), 6);
    assertEquals(receipt2.getFbShortQty().intValue(), 4);

    assertEquals(receipt1.getFbConcealedShortageQty().intValue(), 6);
    assertEquals(receipt2.getFbConcealedShortageQty().intValue(), 4);

    assertEquals(receipt1.getFbShortReasonCode(), OSDRCode.S153);
    assertEquals(receipt2.getFbConcealedShortageReasonCode(), OSDRCode.S153);

    assertNull(receipt1.getFbRejectedQty());
    assertNull(receipt2.getFbRejectedQty());

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 6);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 4);
  }

  @Test
  public void createReceiptTest_NGRReject() {
    CommonReceiptDTO receiptDTO =
        CommonReceiptDTO.builder()
            .containerId("05504010701400108444")
            .gtin("00078742154640")
            .deliveryNumber(55040037L)
            .quantities(Collections.singletonList(new Quantity(10L, "EA", QuantityType.NGR_REJECT)))
            .build();

    List<Container> selectedContainers =
        MFCTestUtils.getContainers(
            "../../receiving-test/src/main/resources/json/mfc/MFCSelectedContainers.json");

    when(mfcContainerService.detectContainers(any())).thenReturn(selectedContainers);
    when(mfcReceiptService.saveReceipt(any())).thenAnswer(i -> i.getArguments()[0]);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);

    List<Receipt> receipts = mfcReceivingService.performReceiving(receiptDTO);

    Receipt receipt1 = receipts.get(0);
    Receipt receipt2 = receipts.get(1);
    if (receipt1.getInvoiceLineNumber() == 52) {
      Receipt temp = receipt1;
      receipt1 = receipt2;
      receipt2 = temp;
    }
    assertEquals(receipt1.getFbRejectedQty().intValue(), 6);
    assertEquals(receipt2.getFbRejectedQty().intValue(), 4);
    assertEquals(receipt2.getFbRejectedReasonCode(), OSDRCode.R152);

    assertNull(receipt1.getFbConcealedShortageQty());
    assertNull(receipt2.getFbConcealedShortageQty());

    assertNull(receipt1.getFbShortQty());
    assertNull(receipt2.getFbShortQty());

    ContainerItem containerItem1 = selectedContainers.get(0).getContainerItems().get(0);
    ContainerItem containerItem2 = selectedContainers.get(0).getContainerItems().get(1);
    assertEquals(containerItem1.getOrderFilledQty().intValue(), 6);
    assertEquals(containerItem2.getOrderFilledQty().intValue(), 4);
  }
}
