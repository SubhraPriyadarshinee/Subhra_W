package com.walmart.move.nim.receiving.mfc.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.DocumentType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ngr.NGRPack;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.entity.DecantAudit;
import com.walmart.move.nim.receiving.mfc.model.common.AuditStatus;
import com.walmart.move.nim.receiving.mfc.processor.StoreNGRFinalizationEventProcessor;
import com.walmart.move.nim.receiving.mfc.service.DecantAuditService;
import java.util.Arrays;
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

public class StoreNGRFinalizationEventListenerTest extends ReceivingTestBase {

  @InjectMocks private StoreNGRFinalizationEventListener storeNgrFinalizationEventListener;

  @Mock private MFCManagedConfig mfcManagedConfig;

  @Mock private AppConfig appConfig;
  @Mock private StoreNGRFinalizationEventProcessor storeNgrFinalizationEventProcessor;

  @Mock private DecantAuditService decantAuditService;
  private Gson gson;
  private List<Integer> ELIGIBLE_SITES;
  private List<String> ELIGIBLE_DELIVERY_TYPE;

  private List<String> ELIGIBLE_DOC_TYPE;

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);
    gson = new Gson();
    ReflectionTestUtils.setField(storeNgrFinalizationEventListener, "gson", gson);
    ELIGIBLE_SITES = Arrays.asList(100, 266, 3284);
    ELIGIBLE_DELIVERY_TYPE = Arrays.asList(MFCConstant.DSD);
    ELIGIBLE_DOC_TYPE = Arrays.asList(DocumentType.ASN.name());
  }

  @AfterMethod
  private void resetMocks() {
    Mockito.reset(
        mfcManagedConfig, appConfig, storeNgrFinalizationEventProcessor, decantAuditService);
  }

  @Test
  public void testInvalidSite() throws ReceivingException {
    storeNgrFinalizationEventListener.listen(
        MFCTestUtils.readInputFile(
            "src/test/resources/ngrFinalizationEvent/ngrInvalidDeliveryType.json"),
        Collections.emptyMap());
    Mockito.verify(storeNgrFinalizationEventProcessor, Mockito.never())
        .processEvent(any(NGRPack.class));
    Mockito.verify(mfcManagedConfig, Mockito.never())
        .getEligibleDeliveryTypeForNgrFinalizationEvent();
  }

  @Test
  public void testEmptyMessage() throws ReceivingException {
    storeNgrFinalizationEventListener.listen(StringUtils.EMPTY, Collections.emptyMap());
    Mockito.verify(storeNgrFinalizationEventProcessor, Mockito.never())
        .processEvent(any(NGRPack.class));
    Mockito.verify(mfcManagedConfig, Mockito.never())
        .getEligibleDeliveryTypeForNgrFinalizationEvent();
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFacilityNumNotPresent() throws ReceivingException {
    NGRPack ngrPack =
        NGRPack.builder()
            .inboundDocumentId("123")
            .sourceNumber("6050")
            .sourceCountryCode("US")
            .build();
    storeNgrFinalizationEventListener.listen(gson.toJson(ngrPack), Collections.emptyMap());
    Mockito.verify(storeNgrFinalizationEventProcessor, Mockito.never())
        .processEvent(any(NGRPack.class));
    Mockito.verify(mfcManagedConfig, Mockito.never())
        .getEligibleDeliveryTypeForNgrFinalizationEvent();
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testInvalidMessageFormat() throws ReceivingException {
    storeNgrFinalizationEventListener.listen("Dummy Data", Collections.emptyMap());
    Mockito.verify(storeNgrFinalizationEventProcessor, Mockito.never())
        .processEvent(any(NGRPack.class));
    Mockito.verify(mfcManagedConfig, Mockito.never())
        .getEligibleDeliveryTypeForNgrFinalizationEvent();
  }

  @Test
  public void testInvalidDeliveryType() throws ReceivingException {
    when(appConfig.getStoreNgrFinalizationEventKafkaListenerEnabledFacilities())
        .thenReturn(ELIGIBLE_SITES);
    when(mfcManagedConfig.getEligibleDeliveryTypeForNgrFinalizationEvent())
        .thenReturn(ELIGIBLE_DELIVERY_TYPE);
    when(mfcManagedConfig.getEligibleDocumentTypeForNgrFinalizationEvent())
        .thenReturn(ELIGIBLE_DOC_TYPE);
    storeNgrFinalizationEventListener.listen(
        MFCTestUtils.readInputFile(
            "src/test/resources/ngrFinalizationEvent/ngrInvalidDeliveryType.json"),
        Collections.emptyMap());
    Mockito.verify(storeNgrFinalizationEventProcessor, Mockito.never())
        .processEvent(any(NGRPack.class));
    Mockito.verify(mfcManagedConfig, times(1)).getEligibleDeliveryTypeForNgrFinalizationEvent();
    Mockito.verify(mfcManagedConfig, never()).getEligibleDocumentTypeForNgrFinalizationEvent();
  }

  @Test
  public void testInvalidDocumentType() throws ReceivingException {
    when(appConfig.getStoreNgrFinalizationEventKafkaListenerEnabledFacilities())
        .thenReturn(ELIGIBLE_SITES);
    when(mfcManagedConfig.getEligibleDeliveryTypeForNgrFinalizationEvent())
        .thenReturn(ELIGIBLE_DELIVERY_TYPE);
    when(mfcManagedConfig.getEligibleDocumentTypeForNgrFinalizationEvent())
        .thenReturn(ELIGIBLE_DOC_TYPE);
    storeNgrFinalizationEventListener.listen(
        MFCTestUtils.readInputFile(
            "src/test/resources/ngrFinalizationEvent/ngrEventInvalidDocumentType.json"),
        Collections.emptyMap());
    Mockito.verify(storeNgrFinalizationEventProcessor, Mockito.never())
        .processEvent(any(NGRPack.class));
    Mockito.verify(mfcManagedConfig, times(1)).getEligibleDeliveryTypeForNgrFinalizationEvent();
    Mockito.verify(mfcManagedConfig, times(1)).getEligibleDocumentTypeForNgrFinalizationEvent();
  }

  @Test
  public void testValidEvent() throws ReceivingException {
    when(appConfig.getStoreNgrFinalizationEventKafkaListenerEnabledFacilities())
        .thenReturn(ELIGIBLE_SITES);
    when(mfcManagedConfig.getEligibleDeliveryTypeForNgrFinalizationEvent())
        .thenReturn(ELIGIBLE_DELIVERY_TYPE);
    when(mfcManagedConfig.getEligibleDocumentTypeForNgrFinalizationEvent())
        .thenReturn(ELIGIBLE_DOC_TYPE);
    when(decantAuditService.findByCorrelationId(any())).thenReturn(Optional.empty());
    when(decantAuditService.createAuditDataDuringNgrProcess(any(), any(), any()))
        .thenReturn(
            DecantAudit.builder().correlationId("131213").status(AuditStatus.IN_PROGRESS).build());
    storeNgrFinalizationEventListener.listen(
        MFCTestUtils.readInputFile("src/test/resources/ngrFinalizationEvent/ngrEvent.json"),
        Collections.emptyMap());
    Mockito.verify(storeNgrFinalizationEventProcessor, times(1)).processEvent(any(NGRPack.class));
    Mockito.verify(decantAuditService, times(1))
        .createAuditDataDuringNgrProcess(any(), any(), any());
    Mockito.verify(decantAuditService, times(1)).save(any(DecantAudit.class));
    Mockito.verify(mfcManagedConfig, times(1)).getEligibleDeliveryTypeForNgrFinalizationEvent();
    Mockito.verify(mfcManagedConfig, times(1)).getEligibleDocumentTypeForNgrFinalizationEvent();
  }

  @Test
  public void testRepublishedEvent() throws ReceivingException {
    when(appConfig.getStoreNgrFinalizationEventKafkaListenerEnabledFacilities())
        .thenReturn(ELIGIBLE_SITES);
    when(mfcManagedConfig.getEligibleDeliveryTypeForNgrFinalizationEvent())
        .thenReturn(ELIGIBLE_DELIVERY_TYPE);
    when(mfcManagedConfig.getEligibleDocumentTypeForNgrFinalizationEvent())
        .thenReturn(ELIGIBLE_DOC_TYPE);
    when(decantAuditService.findByCorrelationId(any()))
        .thenReturn(
            Optional.of(
                DecantAudit.builder()
                    .correlationId("131213")
                    .status(AuditStatus.IN_PROGRESS)
                    .build()));
    storeNgrFinalizationEventListener.listen(
        MFCTestUtils.readInputFile("src/test/resources/ngrFinalizationEvent/ngrEvent.json"),
        Collections.emptyMap());
    Mockito.verify(storeNgrFinalizationEventProcessor, never()).processEvent(any(NGRPack.class));
    Mockito.verify(decantAuditService, never())
        .createAuditDataDuringNgrProcess(any(), any(), any());
    Mockito.verify(decantAuditService, never()).save(any(DecantAudit.class));
    Mockito.verify(mfcManagedConfig, times(1)).getEligibleDeliveryTypeForNgrFinalizationEvent();
    Mockito.verify(mfcManagedConfig, times(1)).getEligibleDocumentTypeForNgrFinalizationEvent();
  }

  @Test
  public void testRepublishedEvent_StatusFailed() throws ReceivingException {
    when(appConfig.getStoreNgrFinalizationEventKafkaListenerEnabledFacilities())
        .thenReturn(ELIGIBLE_SITES);
    when(mfcManagedConfig.getEligibleDeliveryTypeForNgrFinalizationEvent())
        .thenReturn(ELIGIBLE_DELIVERY_TYPE);
    when(mfcManagedConfig.getEligibleDocumentTypeForNgrFinalizationEvent())
        .thenReturn(ELIGIBLE_DOC_TYPE);
    when(decantAuditService.findByCorrelationId(any()))
        .thenReturn(
            Optional.of(
                DecantAudit.builder().correlationId("131213").status(AuditStatus.FAILURE).build()));
    storeNgrFinalizationEventListener.listen(
        MFCTestUtils.readInputFile("src/test/resources/ngrFinalizationEvent/ngrEvent.json"),
        Collections.emptyMap());
    Mockito.verify(storeNgrFinalizationEventProcessor, times(1)).processEvent(any(NGRPack.class));
    Mockito.verify(decantAuditService, never())
        .createAuditDataDuringNgrProcess(any(), any(), any());
    Mockito.verify(decantAuditService, times(1)).save(any(DecantAudit.class));
    Mockito.verify(mfcManagedConfig, times(1)).getEligibleDeliveryTypeForNgrFinalizationEvent();
    Mockito.verify(mfcManagedConfig, times(1)).getEligibleDocumentTypeForNgrFinalizationEvent();
  }
}
