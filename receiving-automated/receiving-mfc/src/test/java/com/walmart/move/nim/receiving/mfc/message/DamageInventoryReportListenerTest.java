package com.walmart.move.nim.receiving.mfc.message;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.common.AuditStatus;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.HawkeyeAdjustment;
import com.walmart.move.nim.receiving.mfc.service.DecantAuditService;
import com.walmart.move.nim.receiving.mfc.service.InventoryAdjustmentHelper;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCReceivingService;
import com.walmart.move.nim.receiving.mfc.transformer.HawkeyeReceiptTransformer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DamageInventoryReportListenerTest extends ReceivingTestBase {

  private static final String DECANTING_EVENT = "DECANTING";

  private String damageInventoryTestJson;
  private String damageDsdInventoryJson;

  @InjectMocks private DamageInventoryReportListener damageInventoryReportListener;

  @InjectMocks private InventoryAdjustmentHelper inventoryAdjustmentHelper;

  @Mock private HawkeyeReceiptTransformer hawkeyeReceiptTransformer;

  @Mock private MFCReceivingService mfcReceivingService;

  @Mock private DecantAuditService decantAuditService;

  @Mock private MFCContainerService mfcContainerService;

  @Mock private MFCManagedConfig mfcManagedConfig;

  private Gson gson;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setMessageId("TENANT_MSG_ID");
    TenantContext.setCorrelationId("TEST_CORRELATION_ID");
    gson = new Gson();
    ReflectionTestUtils.setField(
        damageInventoryReportListener, "inventoryAdjustmentHelper", inventoryAdjustmentHelper);
    ReflectionTestUtils.setField(
        inventoryAdjustmentHelper, "hawkeyeReceiptTransformer", hawkeyeReceiptTransformer);
    ReflectionTestUtils.setField(
        inventoryAdjustmentHelper, "mfcReceivingService", mfcReceivingService);
    ReflectionTestUtils.setField(
        inventoryAdjustmentHelper, "decantAuditService", decantAuditService);
    ReflectionTestUtils.setField(inventoryAdjustmentHelper, "gson", gson);
    ReflectionTestUtils.setField(
        inventoryAdjustmentHelper, "mfcContainerService", mfcContainerService);
    damageInventoryTestJson = readInputFile("autoMFC/test_damagedInventory.json");
    damageDsdInventoryJson = readInputFile("autoMFC/testAdjustmentWithDsdItem.json");
  }

  @AfterMethod
  private void resetMock() {
    Mockito.reset(hawkeyeReceiptTransformer);
    Mockito.reset(mfcReceivingService);
    Mockito.reset(decantAuditService);
    Mockito.reset(mfcContainerService);
    Mockito.reset(mfcManagedConfig);
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
  public void testListen() {
    when(mfcManagedConfig.isEnableAutoMFCExceptionProcessing()).thenReturn(true);
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

    damageInventoryReportListener.listen(
        damageInventoryTestJson, DECANTING_EVENT.getBytes(StandardCharsets.UTF_8), null);

    verify(decantAuditService, times(1)).findByCorrelationId(anyString());
    verify(mfcReceivingService, times(1)).performReceiving(any());
    verify(decantAuditService, times(1))
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
  }

  @Test
  public void testListen_mixedPallet_Reject() {
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

    damageInventoryReportListener.listen(
        damageInventoryTestJson,
        DECANTING_EVENT.getBytes(StandardCharsets.UTF_8),
        MFCConstant.MIXED_PALLET_REJECT.getBytes(StandardCharsets.UTF_8));

    verify(decantAuditService, times(0)).findByCorrelationId(anyString());
    verify(mfcReceivingService, times(0)).performReceiving(any());
    verify(decantAuditService, times(0))
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
  }

  @Test
  public void testListenWithInvalidEventType() {
    when(mfcManagedConfig.isEnableAutoMFCExceptionProcessing()).thenReturn(true);
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

    damageInventoryReportListener.listen(
        damageInventoryTestJson, StringUtils.EMPTY.getBytes(StandardCharsets.UTF_8), null);

    verify(decantAuditService, never()).findByCorrelationId(anyString());
    verify(mfcReceivingService, never()).performReceiving(any());
    verify(decantAuditService, never())
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
  }

  @Test
  public void testListenWithDsdItem() {
    when(mfcManagedConfig.isEnableAutoMFCExceptionProcessing()).thenReturn(true);
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

    when(mfcManagedConfig.getItemTypeCodes()).thenReturn(getItemtypeCode(7L));
    when(mfcManagedConfig.getReplenishSubTypeCodes()).thenReturn(getReplenishmentTypeCode(0L));

    damageInventoryReportListener.listen(
        damageDsdInventoryJson, DECANTING_EVENT.getBytes(StandardCharsets.UTF_8), null);

    verify(decantAuditService, never()).findByCorrelationId(anyString());
    verify(mfcReceivingService, never()).performReceiving(any());
    verify(decantAuditService, never())
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
    verify(mfcManagedConfig, times(1)).getItemTypeCodes();
    verify(mfcManagedConfig, times(1)).getReplenishSubTypeCodes();
  }

  @Test
  public void testListenWithDsdItem_invalid() {
    when(mfcManagedConfig.isEnableAutoMFCExceptionProcessing()).thenReturn(true);
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

    when(mfcManagedConfig.getItemTypeCodes()).thenReturn(getItemtypeCode(8L));
    when(mfcManagedConfig.getReplenishSubTypeCodes()).thenReturn(getReplenishmentTypeCode(5L));

    damageInventoryReportListener.listen(
        damageDsdInventoryJson, DECANTING_EVENT.getBytes(StandardCharsets.UTF_8), null);

    verify(decantAuditService, times(1)).findByCorrelationId(anyString());
    verify(mfcReceivingService, times(1)).performReceiving(any());
    verify(decantAuditService, times(1))
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
    verify(mfcManagedConfig, times(1)).getItemTypeCodes();
    verify(mfcManagedConfig, times(0)).getReplenishSubTypeCodes();
  }

  @Test
  public void testListenExceptionProcessingDisabled() {
    when(mfcManagedConfig.isEnableAutoMFCExceptionProcessing()).thenReturn(false);
    damageInventoryReportListener.listen(
        damageDsdInventoryJson, DECANTING_EVENT.getBytes(StandardCharsets.UTF_8), null);
    verify(decantAuditService, never()).findByCorrelationId(anyString());
    verify(mfcReceivingService, never()).performReceiving(any());
    verify(decantAuditService, never())
        .createHawkeyeDecantAudit(any(HawkeyeAdjustment.class), any(AuditStatus.class), anyList());
  }

  private List<Long> getItemtypeCode(Long itemCode) {
    List<Long> itemTypeCodes = new ArrayList<>();
    itemTypeCodes.add(itemCode);
    return itemTypeCodes;
  }

  private List<Long> getReplenishmentTypeCode(Long replenishmentTypeCode) {
    List<Long> itemTypeCodes = new ArrayList<>();
    itemTypeCodes.add(replenishmentTypeCode);
    return itemTypeCodes;
  }
}
