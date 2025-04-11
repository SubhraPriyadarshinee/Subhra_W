package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.InstructionDetails;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.model.OpenDockTagCount;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceImpl;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rdc.message.publisher.RdcMessagePublisher;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.joda.time.DateTime;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcCompleteDeliveryProcessorTest {

  @Mock private InstructionRepository instructionRepository;
  @Mock private RdcOsdrService rdcOsdrSummaryService;
  @Mock private RdcMessagePublisher rdcMessagePublisher;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcDeliveryMetaDataService deliveryMetaDataService;
  @Mock private DeliveryServiceImpl deliveryService;
  @Mock private RdcInstructionService rdcInstructionService;
  @Mock private ReceiptRepository receiptRepository;

  @InjectMocks private RdcCompleteDeliveryProcessor rdcCompleteDeliveryProcessor;

  private Gson gson = new Gson();
  File resource = null;
  OsdrSummary osdrSummary = null;

  @BeforeClass
  public void setUpBeforeClass() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);

    resource = new ClassPathResource("OsdrReceiptsSummary.json").getFile();
    String json = new String(Files.readAllBytes(resource.toPath()));
    osdrSummary = gson.fromJson(json, OsdrSummary.class);
    ReflectionTestUtils.setField(rdcCompleteDeliveryProcessor, "gson", gson);
  }

  @BeforeMethod
  public void resetMocks() throws Exception {
    reset(
        instructionRepository,
        rdcOsdrSummaryService,
        rdcMessagePublisher,
        tenantSpecificConfigReader,
        deliveryMetaDataService,
        deliveryService,
        rdcInstructionService,
        receiptRepository);
  }

  @Test
  public void test_completeDelivery() throws ReceivingException {

    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.WMT_REQ_SOURCE, ReceivingConstants.NGR_RECEIVING);
    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            12345l))
        .thenReturn(0L);
    when(rdcOsdrSummaryService.getOsdrSummary(12345l, headers)).thenReturn(osdrSummary);

    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), anyMap());
    when(rdcMessagePublisher.publishDeliveryStatus(anyLong(), anyString(), anyMap()))
        .thenReturn(getDelivery_completed());

    DeliveryInfo completeDeliveryResponse =
        rdcCompleteDeliveryProcessor.completeDelivery(12345l, false, headers);

    assertNotNull(completeDeliveryResponse);
    assertNotNull(completeDeliveryResponse.getReceipts());
    assertTrue(completeDeliveryResponse.getReceipts().size() > 0);
    assertEquals(completeDeliveryResponse.getDeliveryStatus(), DeliveryStatus.COMPLETE.name());

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345l);
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(12345l, headers);
    verify(rdcMessagePublisher, times(1)).publishDeliveryReceipts(any(), anyMap());
    verify(deliveryMetaDataService, times(1)).updateDeliveryMetaData(anyLong(), any(String.class));
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(anyLong(), anyString(), anyMap());
  }

  @Test
  public void test_completeDelivery_EMPTY_receipts_FirstTimeDeliveryCompleteWithOpenDockTagsExists()
      throws ReceivingException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Collections.emptyList());
    osdrSummary.setOpenDockTags(OpenDockTagCount.builder().count(3).build());
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.WMT_REQ_SOURCE, ReceivingConstants.NGR_RECEIVING);
    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            12345L))
        .thenReturn(0L);
    osdrSummary.setSummary(new ArrayList<>());
    when(rdcOsdrSummaryService.getOsdrSummary(12345L, headers)).thenReturn(osdrSummary);

    when(deliveryMetaDataService.findDeliveryMetaData(anyLong())).thenReturn(getDeliveryMetaData());
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());

    DeliveryInfo completeDeliveryResponse =
        rdcCompleteDeliveryProcessor.completeDelivery(12345L, false, headers);

    assertNotNull(completeDeliveryResponse);
    assertNotNull(completeDeliveryResponse.getReceipts());
    assertEquals(completeDeliveryResponse.getReceipts().size(), 0);
    assertEquals(
        completeDeliveryResponse.getDeliveryStatus(), DeliveryStatus.UNLOADING_COMPLETE.name());

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(12345L, headers);
    verify(rdcMessagePublisher, times(0)).publishDeliveryReceipts(any(), anyMap());
    verify(rdcMessagePublisher, times(0)).publishDeliveryStatus(anyLong(), anyString(), anyMap());
    verify(deliveryMetaDataService, times(1)).findDeliveryMetaData(anyLong());
    verify(deliveryMetaDataService, times(1)).updateDeliveryMetaData(anyLong(), any(String.class));
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test
  public void
      test_completeDelivery_EMPTY_receipts_doNotPublishUnloadingCompleteDeliveryStatusToPreventFalseDeliveryStatusUpdates_OpenDockTagsExists()
          throws ReceivingException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Collections.emptyList());
    osdrSummary.setOpenDockTags(OpenDockTagCount.builder().count(3).build());
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.WMT_REQ_SOURCE, ReceivingConstants.NGR_RECEIVING);
    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            12345L))
        .thenReturn(0L);
    osdrSummary.setSummary(new ArrayList<>());
    when(rdcOsdrSummaryService.getOsdrSummary(12345L, headers)).thenReturn(osdrSummary);

    when(deliveryMetaDataService.findDeliveryMetaData(anyLong()))
        .thenReturn(getDeliveryMetaDataWithUnloadingCompleteStatus());
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());

    DeliveryInfo completeDeliveryResponse =
        rdcCompleteDeliveryProcessor.completeDelivery(12345L, false, headers);

    assertNotNull(completeDeliveryResponse);
    assertNotNull(completeDeliveryResponse.getReceipts());
    assertEquals(completeDeliveryResponse.getReceipts().size(), 0);
    assertEquals(
        completeDeliveryResponse.getDeliveryStatus(), DeliveryStatus.UNLOADING_COMPLETE.name());

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(12345L, headers);
    verify(rdcMessagePublisher, times(0)).publishDeliveryReceipts(any(), anyMap());
    verify(rdcMessagePublisher, times(0)).publishDeliveryStatus(anyLong(), anyString(), anyMap());
    verify(deliveryMetaDataService, times(1)).findDeliveryMetaData(anyLong());
    verify(deliveryMetaDataService, times(0)).updateDeliveryMetaData(anyLong(), any(String.class));
    verify(rdcMessagePublisher, times(0)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test
  public void
      test_completeDelivery_EMPTY_receipts_doNotPublishUnloadingCompleteDeliveryStatusToPreventFalseDeliveryStatusUpdates_OpenDockTagsDoesNotExist()
          throws ReceivingException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Collections.emptyList());
    osdrSummary.setOpenDockTags(OpenDockTagCount.builder().count(0).build());
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.WMT_REQ_SOURCE, ReceivingConstants.NGR_RECEIVING);
    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            12345L))
        .thenReturn(0L);
    osdrSummary.setSummary(new ArrayList<>());
    when(rdcOsdrSummaryService.getOsdrSummary(12345L, headers)).thenReturn(osdrSummary);

    when(deliveryMetaDataService.findDeliveryMetaData(anyLong()))
        .thenReturn(getDeliveryMetaDataWithUnloadingCompleteStatus());
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());

    DeliveryInfo completeDeliveryResponse =
        rdcCompleteDeliveryProcessor.completeDelivery(12345L, false, headers);

    assertNotNull(completeDeliveryResponse);
    assertNotNull(completeDeliveryResponse.getReceipts());
    assertEquals(completeDeliveryResponse.getReceipts().size(), 0);
    assertEquals(
        completeDeliveryResponse.getDeliveryStatus(), DeliveryStatus.UNLOADING_COMPLETE.name());

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(12345L, headers);
    verify(rdcMessagePublisher, times(0)).publishDeliveryReceipts(any(), anyMap());
    verify(rdcMessagePublisher, times(0)).publishDeliveryStatus(anyLong(), anyString(), anyMap());
    verify(deliveryMetaDataService, times(1)).findDeliveryMetaData(anyLong());
    verify(deliveryMetaDataService, times(0)).updateDeliveryMetaData(anyLong(), any(String.class));
    verify(rdcMessagePublisher, times(0)).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
  }

  @Test
  public void testCompleteDeliveryWithEmptyReceiptsUponForceCompleteDeliveryHeaderSentFromGDM()
      throws ReceivingException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setSummary(Collections.emptyList());
    osdrSummary.setOpenDockTags(OpenDockTagCount.builder().count(0).build());
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.WMT_REQ_SOURCE, ReceivingConstants.NGR_RECEIVING);
    headers.set(ReceivingConstants.GDM_FORCE_COMPLETE_DELIVERY_HEADER, "true");
    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            12345l))
        .thenReturn(0L);
    when(rdcOsdrSummaryService.getOsdrSummary(12345l, headers)).thenReturn(osdrSummary);

    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), anyMap());
    when(rdcMessagePublisher.publishDeliveryStatus(anyLong(), anyString(), anyMap()))
        .thenReturn(getDelivery_completed());

    DeliveryInfo completeDeliveryResponse =
        rdcCompleteDeliveryProcessor.completeDelivery(12345l, false, headers);

    assertNotNull(completeDeliveryResponse);
    assertNotNull(completeDeliveryResponse.getReceipts());
    assertEquals(completeDeliveryResponse.getReceipts().size(), 0);
    assertEquals(completeDeliveryResponse.getDeliveryStatus(), DeliveryStatus.COMPLETE.name());

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345l);
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(12345l, headers);
    verify(rdcMessagePublisher, times(1)).publishDeliveryReceipts(any(), anyMap());
    verify(deliveryMetaDataService, times(1)).updateDeliveryMetaData(anyLong(), any(String.class));
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(anyLong(), anyString(), anyMap());
  }

  @Test
  public void testCompleteDeliveryWithReceiptsUponForceCompleteDeliveryHeaderSentFromGDM()
      throws ReceivingException {

    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.WMT_REQ_SOURCE, ReceivingConstants.NGR_RECEIVING);
    headers.set(ReceivingConstants.GDM_FORCE_COMPLETE_DELIVERY_HEADER, "true");
    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            12345L))
        .thenReturn(0L);
    when(rdcOsdrSummaryService.getOsdrSummary(12345L, headers)).thenReturn(osdrSummary);

    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), anyMap());
    when(rdcMessagePublisher.publishDeliveryStatus(anyLong(), anyString(), anyMap()))
        .thenReturn(getDelivery_completed());

    DeliveryInfo completeDeliveryResponse =
        rdcCompleteDeliveryProcessor.completeDelivery(12345L, false, headers);

    assertNotNull(completeDeliveryResponse);
    assertNotNull(completeDeliveryResponse.getReceipts());
    assertTrue(completeDeliveryResponse.getReceipts().size() > 0);
    assertEquals(completeDeliveryResponse.getDeliveryStatus(), DeliveryStatus.COMPLETE.name());

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);
    verify(rdcOsdrSummaryService, times(1)).getOsdrSummary(12345L, headers);
    verify(rdcMessagePublisher, times(1)).publishDeliveryReceipts(any(), anyMap());
    verify(deliveryMetaDataService, times(1)).updateDeliveryMetaData(anyLong(), any(String.class));
    verify(rdcMessagePublisher, times(1)).publishDeliveryStatus(anyLong(), anyString(), anyMap());
  }

  @Test
  public void test_completeDelivery_pending_instructions() throws ReceivingException {

    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            12345l))
        .thenReturn(1L);

    try {
      rdcCompleteDeliveryProcessor.completeDelivery(12345l, false, MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException rbde) {
      assertEquals(
          rbde.getErrorCode(), ExceptionCodes.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE);
      assertEquals(
          rbde.getDescription(),
          ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE);
    } catch (Exception e) {
      throw e;
    }

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345l);
    verify(rdcOsdrSummaryService, times(0)).getOsdrSummary(12345l, MockHttpHeaders.getHeaders());
    verify(rdcMessagePublisher, times(0)).publishDeliveryReceipts(any(), anyMap());
    verify(rdcMessagePublisher, times(0)).publishDeliveryStatus(anyLong(), anyString(), anyMap());
  }

  @Test
  public void autoCompleteDeliveries_Success_UncompletedInstructionDetailsExists()
      throws ReceivingException, IOException {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(12345l);
    osdrSummary.setOpenDockTags(OpenDockTagCount.builder().count(0).build());

    File resource =
        new ClassPathResource("GDMDeliveryStatusForAutoCompleteDeliveries.json").getFile();
    String gdmDeliveryStatusResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryList deliveryList = gson.fromJson(gdmDeliveryStatusResponse, DeliveryList.class);
    Integer numberOfDeliveries = deliveryList.getData().size();
    Integer pendingInstructionsCount = getPendingInstructions().size();
    when(instructionRepository.getUncompletedInstructionDetailsByDeliveryNumber(
            any(Long.class), any(Integer.class)))
        .thenReturn(getPendingInstructions());
    when(deliveryService.fetchDeliveriesByStatus(any(String.class), any(int.class)))
        .thenReturn(gdmDeliveryStatusResponse);

    doReturn(gson.toJsonTree(Integer.valueOf(48)))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ReceivingConstants.RUN_AUTO_COMPLETE_DELIVERY_IN_HOUR));

    doReturn(gson.toJsonTree(Integer.valueOf(4)))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ReceivingConstants.MAX_DELIVERY_IDLE_DURATION_IN_HOUR));

    Receipt receiptWithIdealTime = getMockReceipt();
    receiptWithIdealTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    when(receiptRepository.findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class)))
        .thenReturn(receiptWithIdealTime);
    when(rdcInstructionService.cancelInstruction(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(new InstructionSummary());
    when(rdcOsdrSummaryService.getOsdrSummary(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(osdrSummary);
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), anyMap());
    when(rdcMessagePublisher.publishDeliveryStatus(anyLong(), anyString(), anyMap()))
        .thenReturn(getDelivery_completed());

    rdcCompleteDeliveryProcessor.autoCompleteDeliveries(32835);
    verify(instructionRepository, times(numberOfDeliveries))
        .getUncompletedInstructionDetailsByDeliveryNumber(any(Long.class), any(Integer.class));
    verify(deliveryService, times(1)).fetchDeliveriesByStatus(any(String.class), any(int.class));
    verify(receiptRepository, times(numberOfDeliveries))
        .findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class));
    verify(rdcOsdrSummaryService, times(numberOfDeliveries))
        .getOsdrSummary(any(Long.class), any(HttpHeaders.class));
    verify(rdcMessagePublisher, times(numberOfDeliveries)).publishDeliveryReceipts(any(), anyMap());
    verify(rdcMessagePublisher, times(numberOfDeliveries))
        .publishDeliveryStatus(anyLong(), anyString(), anyMap());
    verify(rdcInstructionService, times(numberOfDeliveries * pendingInstructionsCount))
        .cancelInstruction(anyLong(), eq(getAutoCompleteHeaders()));
  }

  @Test
  public void
      autoCompleteDeliveries_doNotCancelInstructions_whenNoUncompletedInstructionDetailsExists()
          throws ReceivingException, IOException {
    osdrSummary.setOpenDockTags(OpenDockTagCount.builder().count(0).build());
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(12345l);

    File resource =
        new ClassPathResource("GDMDeliveryStatusForAutoCompleteDeliveries.json").getFile();
    String gdmDeliveryStatusResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryList deliveryList = gson.fromJson(gdmDeliveryStatusResponse, DeliveryList.class);
    Integer numberOfDeliveries = deliveryList.getData().size();

    when(instructionRepository.getUncompletedInstructionDetailsByDeliveryNumber(
            any(Long.class), any(Integer.class)))
        .thenReturn(new ArrayList<>());
    when(deliveryService.fetchDeliveriesByStatus(any(String.class), any(int.class)))
        .thenReturn(gdmDeliveryStatusResponse);

    doReturn(gson.toJsonTree(Integer.valueOf(48)))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ReceivingConstants.RUN_AUTO_COMPLETE_DELIVERY_IN_HOUR));

    doReturn(gson.toJsonTree(Integer.valueOf(4)))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ReceivingConstants.MAX_DELIVERY_IDLE_DURATION_IN_HOUR));

    Receipt receiptWithIdealTime = getMockReceipt();
    receiptWithIdealTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    when(receiptRepository.findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class)))
        .thenReturn(receiptWithIdealTime);
    when(rdcOsdrSummaryService.getOsdrSummary(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(osdrSummary);
    doNothing().when(rdcMessagePublisher).publishDeliveryReceipts(any(), anyMap());
    when(rdcMessagePublisher.publishDeliveryStatus(anyLong(), anyString(), anyMap()))
        .thenReturn(getDelivery_completed());
    rdcCompleteDeliveryProcessor.autoCompleteDeliveries(32835);
    verify(instructionRepository, times(numberOfDeliveries))
        .getUncompletedInstructionDetailsByDeliveryNumber(any(Long.class), any(Integer.class));
    verify(deliveryService, times(1)).fetchDeliveriesByStatus(any(String.class), any(int.class));
    verify(receiptRepository, times(numberOfDeliveries))
        .findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class));
    verify(rdcOsdrSummaryService, times(numberOfDeliveries))
        .getOsdrSummary(any(Long.class), any(HttpHeaders.class));
    verify(rdcMessagePublisher, times(numberOfDeliveries)).publishDeliveryReceipts(any(), anyMap());
    verify(rdcMessagePublisher, times(numberOfDeliveries))
        .publishDeliveryStatus(anyLong(), anyString(), anyMap());
    verify(rdcInstructionService, times(0)).cancelInstruction(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void autoCompleteDeliveries_doNotComplete_AuditPending()
      throws ReceivingException, IOException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setAuditPending(true);
    osdrSummary.setOpenDockTags(OpenDockTagCount.builder().count(0).build());

    File resource =
        new ClassPathResource("GDMDeliveryStatusForAutoCompleteDeliveries.json").getFile();
    String gdmDeliveryStatusResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryList deliveryList = gson.fromJson(gdmDeliveryStatusResponse, DeliveryList.class);
    Integer numberOfDeliveries = deliveryList.getData().size();
    Integer pendingInstructionsCount = getPendingInstructions().size();

    when(instructionRepository.getUncompletedInstructionDetailsByDeliveryNumber(
            any(Long.class), any(Integer.class)))
        .thenReturn(getPendingInstructions());
    when(deliveryService.fetchDeliveriesByStatus(any(String.class), any(int.class)))
        .thenReturn(gdmDeliveryStatusResponse);

    doReturn(gson.toJsonTree(Integer.valueOf(48)))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ReceivingConstants.RUN_AUTO_COMPLETE_DELIVERY_IN_HOUR));

    doReturn(gson.toJsonTree(Integer.valueOf(4)))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ReceivingConstants.MAX_DELIVERY_IDLE_DURATION_IN_HOUR));

    Receipt receiptWithIdealTime = getMockReceipt();
    receiptWithIdealTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    when(receiptRepository.findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class)))
        .thenReturn(receiptWithIdealTime);
    when(rdcInstructionService.cancelInstruction(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(new InstructionSummary());
    when(rdcOsdrSummaryService.getOsdrSummary(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(osdrSummary);

    rdcCompleteDeliveryProcessor.autoCompleteDeliveries(32835);
    verify(instructionRepository, times(numberOfDeliveries))
        .getUncompletedInstructionDetailsByDeliveryNumber(any(Long.class), any(Integer.class));
    verify(deliveryService, times(1)).fetchDeliveriesByStatus(any(String.class), any(int.class));
    verify(receiptRepository, times(numberOfDeliveries))
        .findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class));
    verify(rdcOsdrSummaryService, times(numberOfDeliveries))
        .getOsdrSummary(any(Long.class), any(HttpHeaders.class));
    verify(rdcInstructionService, times(numberOfDeliveries * pendingInstructionsCount))
        .cancelInstruction(anyLong(), eq(getAutoCompleteHeaders()));
  }

  @Test
  public void autoCompleteDeliveries_doNotComplete_OpenDockTagsExists()
      throws ReceivingException, IOException {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setAuditPending(false);
    osdrSummary.setOpenDockTags(OpenDockTagCount.builder().count(2).build());

    File resource =
        new ClassPathResource("GDMDeliveryStatusForAutoCompleteDeliveries.json").getFile();
    String gdmDeliveryStatusResponse = new String(Files.readAllBytes(resource.toPath()));
    DeliveryList deliveryList = gson.fromJson(gdmDeliveryStatusResponse, DeliveryList.class);
    Integer numberOfDeliveries = deliveryList.getData().size();
    Integer pendingInstructionsCount = getPendingInstructions().size();

    when(instructionRepository.getUncompletedInstructionDetailsByDeliveryNumber(
            any(Long.class), any(Integer.class)))
        .thenReturn(getPendingInstructions());
    when(deliveryService.fetchDeliveriesByStatus(any(String.class), any(int.class)))
        .thenReturn(gdmDeliveryStatusResponse);

    doReturn(gson.toJsonTree(Integer.valueOf(48)))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ReceivingConstants.RUN_AUTO_COMPLETE_DELIVERY_IN_HOUR));

    doReturn(gson.toJsonTree(Integer.valueOf(4)))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyInt(), eq(ReceivingConstants.MAX_DELIVERY_IDLE_DURATION_IN_HOUR));

    Receipt receiptWithIdealTime = getMockReceipt();
    receiptWithIdealTime.setCreateTs((new DateTime().minusHours(5)).toDate());
    when(receiptRepository.findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class)))
        .thenReturn(receiptWithIdealTime);
    when(rdcInstructionService.cancelInstruction(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(new InstructionSummary());
    when(rdcOsdrSummaryService.getOsdrSummary(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(osdrSummary);

    rdcCompleteDeliveryProcessor.autoCompleteDeliveries(32835);
    verify(instructionRepository, times(numberOfDeliveries))
        .getUncompletedInstructionDetailsByDeliveryNumber(any(Long.class), any(Integer.class));
    verify(deliveryService, times(1)).fetchDeliveriesByStatus(any(String.class), any(int.class));
    verify(receiptRepository, times(numberOfDeliveries))
        .findFirstByDeliveryNumberOrderByCreateTsDesc(any(Long.class));
    verify(rdcOsdrSummaryService, times(numberOfDeliveries))
        .getOsdrSummary(any(Long.class), any(HttpHeaders.class));
    verify(rdcInstructionService, times(numberOfDeliveries * pendingInstructionsCount))
        .cancelInstruction(anyLong(), eq(getAutoCompleteHeaders()));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testDeliveryFetchByStatus_Failure() throws ReceivingException {
    doThrow(
            new ReceivingException(
                ReceivingException.GDM_SERVICE_DOWN,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.GDM_GET_DELIVERY_BY_STATUS_CODE_ERROR))
        .when(deliveryService)
        .fetchDeliveriesByStatus(any(String.class), any(int.class));
    rdcCompleteDeliveryProcessor.autoCompleteDeliveries(32835);
  }

  private DeliveryInfo getDelivery_completed() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(12345l);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.COMPLETE.name());
    return deliveryInfo;
  }

  private Receipt getMockReceipt() {
    Receipt receipt = new Receipt();
    receipt.setProblemId(null);
    receipt.setCreateUserId("sysadmin");
    receipt.setEachQty(4);
    receipt.setDeliveryNumber(21119003L);
    receipt.setDoorNumber("171");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setPurchaseReferenceNumber("9763140005");
    receipt.setQuantity(2);
    receipt.setQuantityUom("ZA");
    receipt.setVnpkQty(2);
    receipt.setWhpkQty(4);
    return receipt;
  }

  private List<InstructionDetails> getPendingInstructions() {
    InstructionDetails instructionDetails1 =
        InstructionDetails.builder()
            .id(2323232323l)
            .deliveryNumber(1234567L)
            .lastChangeUserId("sysadmin2")
            .receivedQuantity(0)
            .createUserId("sysadmin")
            .build();

    InstructionDetails instructionDetails2 =
        InstructionDetails.builder()
            .id(212142234342l)
            .deliveryNumber(1234567L)
            .lastChangeUserId(null)
            .receivedQuantity(0)
            .createUserId("sysadmin")
            .build();

    List<InstructionDetails> instructionListWithOpenInstructions = new ArrayList<>();
    instructionListWithOpenInstructions.add(instructionDetails1);
    instructionListWithOpenInstructions.add(instructionDetails2);
    return instructionListWithOpenInstructions;
  }

  private DeliveryMetaData getDeliveryMetaData() {
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber("12345");
    deliveryMetaData.setDoorNumber("123");
    deliveryMetaData.setTrailerNumber("128699");
    deliveryMetaData.setCarrierScacCode("WM");
    deliveryMetaData.setFacilityNum(32818);
    deliveryMetaData.setFacilityCountryCode("US");
    deliveryMetaData.setDeliveryStatus(DeliveryStatus.ARV);
    return deliveryMetaData;
  }

  private DeliveryMetaData getDeliveryMetaDataWithUnloadingCompleteStatus() {
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber("12345");
    deliveryMetaData.setDoorNumber("123");
    deliveryMetaData.setTrailerNumber("128699");
    deliveryMetaData.setCarrierScacCode("WM");
    deliveryMetaData.setFacilityNum(32818);
    deliveryMetaData.setFacilityCountryCode("US");
    deliveryMetaData.setDeliveryStatus(DeliveryStatus.UNLOADING_COMPLETE);
    return deliveryMetaData;
  }

  private HttpHeaders getAutoCompleteHeaders() {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.set(
        ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.AUTO_COMPLETE_DELIVERY_USERID);
    return httpHeaders;
  }
}
