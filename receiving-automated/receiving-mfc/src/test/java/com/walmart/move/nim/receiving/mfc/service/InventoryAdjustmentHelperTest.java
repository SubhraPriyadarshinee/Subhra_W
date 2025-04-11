package com.walmart.move.nim.receiving.mfc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.mfc.entity.DecantAudit;
import com.walmart.move.nim.receiving.mfc.model.common.AuditStatus;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.HawkeyeAdjustment;
import com.walmart.move.nim.receiving.mfc.transformer.HawkeyeReceiptTransformer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class InventoryAdjustmentHelperTest extends ReceivingTestBase {

  @InjectMocks private InventoryAdjustmentHelper inventoryAdjustmentHelper;

  @Mock private HawkeyeReceiptTransformer hawkeyeReceiptTransformer;

  @Mock private MFCReceivingService mfcReceivingService;

  @Mock private DecantAuditService decantAuditService;

  @Mock private MFCContainerService mfcContainerService;

  private Gson gson;
  private static final String DECANTING_EVENT = "DECANTING";
  private String sampleInput;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setMessageId("TENANT_MSG_ID");
    TenantContext.setCorrelationId("TEST_CORRELATION_ID");
    gson = new Gson();
    ReflectionTestUtils.setField(
        inventoryAdjustmentHelper, "hawkeyeReceiptTransformer", hawkeyeReceiptTransformer);
    ReflectionTestUtils.setField(
        inventoryAdjustmentHelper, "mfcReceivingService", mfcReceivingService);
    ReflectionTestUtils.setField(
        inventoryAdjustmentHelper, "decantAuditService", decantAuditService);
    ReflectionTestUtils.setField(inventoryAdjustmentHelper, "gson", gson);
    ReflectionTestUtils.setField(
        inventoryAdjustmentHelper, "mfcContainerService", mfcContainerService);
    ReflectionTestUtils.setField(mfcReceivingService, "mfcContainerService", mfcContainerService);
    sampleInput = readInputFile("autoMFC/test_damagedInventory.json");
  }

  @AfterMethod
  private void resetMock() {
    Mockito.reset(hawkeyeReceiptTransformer);
    Mockito.reset(mfcReceivingService);
    Mockito.reset(decantAuditService);
    Mockito.reset(mfcContainerService);
  }

  private String readInputFile(String fileName) {
    String jsonString = "";
    try {
      File resource = readFile(fileName);
      jsonString = new String(Files.readAllBytes(resource.toPath()));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return jsonString;
  }

  @Test
  public void testProcessInventoryAdjustment() {
    when(decantAuditService.findByCorrelationId(anyString())).thenReturn(Optional.empty());
    when(hawkeyeReceiptTransformer.transform(any(HawkeyeAdjustment.class)))
        .thenReturn(new CommonReceiptDTO());
    when(mfcContainerService.detectContainers(any(CommonReceiptDTO.class)))
        .thenReturn(Collections.singletonList(new Container()));
    when(mfcReceivingService.performReceiving(any()))
        .thenReturn(Collections.singletonList(new Receipt()));
    doNothing()
        .when(decantAuditService)
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());

    inventoryAdjustmentHelper.processInventoryAdjustment(sampleInput, DECANTING_EVENT);

    verify(decantAuditService, times(1)).findByCorrelationId(anyString());
    verify(mfcReceivingService, times(1)).performReceiving(any());
    verify(decantAuditService, times(1))
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
  }

  @Test
  public void testProcessInventoryAdjustmentWithInvalidEventType() {
    when(decantAuditService.findByCorrelationId(anyString())).thenReturn(Optional.empty());
    when(hawkeyeReceiptTransformer.transform(any(HawkeyeAdjustment.class)))
        .thenReturn(new CommonReceiptDTO());
    when(mfcContainerService.detectContainers(any(CommonReceiptDTO.class)))
        .thenReturn(Collections.singletonList(new Container()));
    when(mfcReceivingService.performReceiving(any()))
        .thenReturn(Collections.singletonList(new Receipt()));
    doNothing()
        .when(decantAuditService)
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());

    inventoryAdjustmentHelper.processInventoryAdjustment(sampleInput, StringUtils.EMPTY);

    verify(decantAuditService, never()).findByCorrelationId(anyString());
    verify(mfcReceivingService, never()).performReceiving(any());
    verify(decantAuditService, never())
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
  }

  @Test
  public void testProcessInventoryAdjustmentWithDuplicateAdjustment() {
    DecantAudit decantAudit = DecantAudit.builder().correlationId("TEST_CORRELATION_ID").build();
    when(decantAuditService.findByCorrelationId(anyString())).thenReturn(Optional.of(decantAudit));
    when(hawkeyeReceiptTransformer.transform(any(HawkeyeAdjustment.class)))
        .thenReturn(new CommonReceiptDTO());
    when(mfcContainerService.detectContainers(any(CommonReceiptDTO.class)))
        .thenReturn(Collections.singletonList(new Container()));
    when(mfcReceivingService.performReceiving(any()))
        .thenReturn(Collections.singletonList(new Receipt()));
    doNothing()
        .when(decantAuditService)
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());

    inventoryAdjustmentHelper.processInventoryAdjustment(sampleInput, DECANTING_EVENT);

    verify(decantAuditService, times(1)).findByCorrelationId(anyString());
    verify(mfcReceivingService, never()).performReceiving(any());
    verify(decantAuditService, never())
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
  }

  @Test
  public void testProcessInventoryAdjustmentForReDecanting() {
    when(decantAuditService.findByCorrelationId(anyString())).thenReturn(Optional.empty());
    when(hawkeyeReceiptTransformer.transform(any(HawkeyeAdjustment.class)))
        .thenReturn(new CommonReceiptDTO());
    when(mfcContainerService.detectContainers(any(CommonReceiptDTO.class)))
        .thenReturn(Collections.singletonList(new Container()));
    when(mfcReceivingService.performReceiving(any()))
        .thenReturn(Collections.singletonList(new Receipt()));
    doNothing()
        .when(decantAuditService)
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
    inventoryAdjustmentHelper.processInventoryAdjustment(sampleInput, "REDECANTING");

    verify(decantAuditService, never()).findByCorrelationId(anyString());
    verify(mfcReceivingService, never()).performReceiving(any());
    verify(decantAuditService, never())
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
  }

  @Test
  public void testProcessInventoryAdjustmentForReInductCorrection() {
    when(decantAuditService.findByCorrelationId(anyString())).thenReturn(Optional.empty());
    when(hawkeyeReceiptTransformer.transform(any(HawkeyeAdjustment.class)))
        .thenReturn(new CommonReceiptDTO());
    when(mfcContainerService.detectContainers(any(CommonReceiptDTO.class)))
        .thenReturn(Collections.singletonList(new Container()));
    when(mfcReceivingService.performReceiving(any()))
        .thenReturn(Collections.singletonList(new Receipt()));
    doNothing()
        .when(decantAuditService)
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());

    inventoryAdjustmentHelper.processInventoryAdjustment(sampleInput, "CORRECTION");

    verify(decantAuditService, never()).findByCorrelationId(anyString());
    verify(mfcReceivingService, never()).performReceiving(any());
    verify(decantAuditService, never())
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
  }
}
