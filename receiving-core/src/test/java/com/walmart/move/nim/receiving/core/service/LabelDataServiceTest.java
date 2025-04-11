package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDataLpn;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.mock.data.MockLabelData;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseOrderInfo;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.repositories.LabelDataLpnRepository;
import com.walmart.move.nim.receiving.core.repositories.LabelDataRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.data.domain.PageRequest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LabelDataServiceTest extends ReceivingTestBase {
  @InjectMocks private LabelDataService labelDataService;

  @Mock private LabelDataRepository labelDataRepository;
  @Mock private LabelDataLpnRepository labelDataLpnRepository;
  @Mock private LabelDataLpnService labelDataLpnService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  private LabelData labelData1;
  private LabelData labelData2;
  private List<LabelData> labelDataList;
  private PageRequest pageReq;

  @BeforeClass
  private void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    labelData1 = new LabelData();
    labelData2 = new LabelData();
    labelDataList = new ArrayList<>();
    labelDataList.add(labelData1);
    labelDataList.add(labelData2);
    pageReq = PageRequest.of(0, 10);
  }

  @BeforeMethod
  private void beforeMethod() {
    labelData1 = new LabelData();
    labelData2 = new LabelData();
    labelDataList = new ArrayList<>();
    labelDataList.add(labelData1);
    labelDataList.add(labelData2);
    pageReq = PageRequest.of(0, 10);
  }

  @AfterMethod
  private void resetMocks() {
    reset(labelDataRepository);
    reset(labelDataLpnRepository);
    reset(labelDataLpnService);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testCountByDeliveryNumber() {
    when(labelDataRepository.countByDeliveryNumber(anyLong())).thenReturn(99);
    Integer returnedLabelDataCount = labelDataService.countByDeliveryNumber(123456L);
    assertEquals(returnedLabelDataCount, Integer.valueOf(99));
  }

  @Test
  public void testDeleteByDeliveryNumber() {
    // Dummy Impl for now
  }

  @Test
  public void testFetchByLPNS() {
    when(labelDataRepository.findByLpns(anyString())).thenReturn(labelData2);
    labelDataService.fetchByLpns("123456L");
    verify(labelDataRepository, times(1)).findByLpns("123456L");
  }

  @Test
  public void testGetLabelDataByDeliveryNumber() {
    when(labelDataRepository.findByDeliveryNumber(anyLong())).thenReturn(labelDataList);
    List<LabelData> returnedLabelDataList = labelDataService.getLabelDataByDeliveryNumber(123456L);
    assertEquals(returnedLabelDataList.size(), 2);
  }

  @Test
  public void testSaveAll() {
    when(labelDataRepository.saveAll(anyList())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    List<LabelData> returnedLabelDataList = labelDataService.saveAll(labelDataList);
    assertEquals(returnedLabelDataList.size(), 2);
  }

  @Test
  public void testGetPurchaseOrderInfo_LabelDataLpnEnabled() {
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            123456789L,
            "4567890123",
            1,
            "{\"sscc\":null,\"orderableGTIN\":\"10097298051293\",\"consumableGTIN\":\"10097298051293\",\"catalogGTIN\":null}");
    LabelData labelData =
        LabelData.builder()
            .deliveryNumber(123456789L)
            .purchaseReferenceNumber("4567890123")
            .purchaseReferenceLineNumber(1)
            .possibleUPC(
                "{\"sscc\":null,\"orderableGTIN\":\"10097298051293\",\"consumableGTIN\":\"10097298051293\",\"catalogGTIN\":null}")
            .build();
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED))
        .thenReturn(true);
    when(labelDataLpnService.findLabelDataByLpn(anyString())).thenReturn(Optional.of(labelData));
    JSONAssert.assertEquals(
        JacksonParser.writeValueAsString(
            labelDataService.getPurchaseOrderInfo(123456789L, "a0984567")),
        JacksonParser.writeValueAsString(purchaseOrderInfo),
        true);
    verify(labelDataLpnService, times(1)).findLabelDataByLpn("a0984567");
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(123456789L, "a0984567");
  }

  @Test
  public void testGetPurchaseOrderInfoFallback() {
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            123456789L,
            "4567890123",
            1,
            "{\"sscc\":null,\"orderableGTIN\":\"10097298051293\",\"consumableGTIN\":\"10097298051293\",\"catalogGTIN\":null}");
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED))
        .thenReturn(true);
    when(labelDataLpnService.findLabelDataByLpn(anyString())).thenReturn(Optional.empty());
    when(labelDataRepository.findByDeliveryNumberAndContainsLPN(anyLong(), anyString()))
        .thenReturn(purchaseOrderInfo);
    assertEquals(labelDataService.getPurchaseOrderInfo(123456789L, "a0984567"), purchaseOrderInfo);
    verify(labelDataLpnService, times(1)).findLabelDataByLpn("a0984567");
    verify(labelDataRepository, times(1))
        .findByDeliveryNumberAndContainsLPN(123456789L, "a0984567");
  }

  @Test
  public void testGetPurchaseOrderInfoFallBack_SearchLabelDataByLpnLike() {
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            123456789L,
            "4567890123",
            1,
            "{\"sscc\":null,\"orderableGTIN\":\"10097298051293\",\"consumableGTIN\":\"10097298051293\",\"catalogGTIN\":null}");
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED))
        .thenReturn(true);
    when(labelDataLpnService.findLabelDataByLpn(anyString())).thenReturn(Optional.empty());
    when(labelDataRepository.findByDeliveryNumberAndContainsLPN(anyLong(), anyString()))
        .thenReturn(null);
    when(labelDataRepository.findByDeliveryNumberAndLPNLike(anyLong(), anyString()))
        .thenReturn(purchaseOrderInfo);
    assertEquals(labelDataService.getPurchaseOrderInfo(123456789L, "a0984567"), purchaseOrderInfo);
    verify(labelDataLpnService, times(1)).findLabelDataByLpn("a0984567");
    verify(labelDataRepository, times(1))
        .findByDeliveryNumberAndContainsLPN(123456789L, "a0984567");
    verify(labelDataRepository, times(1)).findByDeliveryNumberAndLPNLike(123456789L, "a0984567");
  }

  @Test
  public void testGetPurchaseOrderInfo_LabelDataLpnDisabled() {
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            123456789L,
            "4567890123",
            1,
            "{\"sscc\":null,\"orderableGTIN\":\"10097298051293\",\"consumableGTIN\":\"10097298051293\",\"catalogGTIN\":null}");
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED))
        .thenReturn(false);
    when(labelDataRepository.findByDeliveryNumberAndContainsLPN(anyLong(), anyString()))
        .thenReturn(purchaseOrderInfo);
    assertEquals(labelDataService.getPurchaseOrderInfo(123456789L, "a0984567"), purchaseOrderInfo);
    verify(labelDataLpnService, times(0)).findLabelDataByLpn("a0984567");
    verify(labelDataRepository, times(1))
        .findByDeliveryNumberAndContainsLPN(123456789L, "a0984567");
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    labelData1.setId(1L);
    labelData1.setCreateTs(cal.getTime());

    labelData2.setId(10L);
    labelData2.setCreateTs(cal.getTime());

    when(labelDataRepository.findByIdGreaterThanEqual(anyLong(), any())).thenReturn(labelDataList);
    doNothing().when(labelDataRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.LABEL_DATA)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = labelDataService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    labelData1.setId(1L);
    labelData1.setCreateTs(cal.getTime());

    labelData2.setId(10L);
    labelData2.setCreateTs(cal.getTime());

    when(labelDataRepository.findByIdGreaterThanEqual(anyLong(), any())).thenReturn(labelDataList);
    doNothing().when(labelDataRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.LABEL_DATA)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = labelDataService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    labelData1.setId(1L);
    labelData1.setCreateTs(cal.getTime());

    labelData2.setId(10L);
    labelData2.setCreateTs(new Date());

    when(labelDataRepository.findByIdGreaterThanEqual(anyLong(), any())).thenReturn(labelDataList);
    doNothing().when(labelDataRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.LABEL_DATA)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = labelDataService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void testFindLabelDataByItemNumber() {
    when(labelDataRepository.findByItemNumber(anyLong())).thenReturn(labelDataList);
    List<LabelData> returnedLabelDataList = labelDataService.findByItemNumber(123456L);
    assertEquals(returnedLabelDataList.size(), 2);
  }

  @Test
  public void testFetchLabelCountByDeliveryNumber() {
    when(labelDataRepository.fetchLabelCountByDeliveryNumber(anyLong())).thenReturn(50);
    int count = labelDataService.fetchLabelCountByDeliveryNumber(123456L);
    assertEquals(count, 50);
  }

  @Test
  public void testFetchItemCountByDeliveryNumber() {
    when(labelDataRepository.fetchItemCountByDeliveryNumber(anyLong())).thenReturn(2);
    int count = labelDataService.fetchItemCountByDeliveryNumber(123456L);
    assertEquals(count, 2);
  }

  @Test
  public void testFetchLabelDataByPoAndItemNumberAndStoreNumber() {
    when(labelDataRepository.fetchLabelDataByPoAndItemNumberAndStoreNumber(
            anyString(),
            anyLong(),
            any(Integer.class),
            anyString(),
            anyInt(),
            anyInt(),
            anyString()))
        .thenReturn(labelDataList);
    List<LabelData> labelDataList =
        labelDataService.fetchLabelDataByPoAndItemNumberAndStoreNumber(
            "3232323", 2222333L, 32323, "AVAILABLE", 13, 32679, "US");
    assertTrue(labelDataList.size() > 0);
  }

  @Test
  public void testFindByLpnsAndLabelIn() {
    when(labelDataRepository.findByLpnsAndLabelIn(anyString(), anyList()))
        .thenReturn(labelDataList.get(0));
    LabelData labelData =
        labelDataService.findByLpnsAndLabelIn("23472343749832", Arrays.asList("XDK1", "XDK2"));
    assertEquals(labelDataList.get(0), labelData);
  }

  @Test
  public void testFindByLpnsAndStatus() {
    when(labelDataRepository.findByLpnsAndStatus(anyString(), anyString()))
        .thenReturn(labelDataList.get(0));
    LabelData labelData = labelDataService.findByLpnsAndStatus("23472343749832", "AVAILABLE");
    assertEquals(labelDataList.get(0), labelData);
  }

  @Test
  public void testGetMaxSequence() {
    labelData2.setId(10L);
    labelData2.setCreateTs(new Date());
    labelData2.setSequenceNo(879012345);
    when(labelDataRepository.findFirstByDeliveryNumberOrderBySequenceNoDesc(anyLong()))
        .thenReturn(labelData2);
    Integer maxSequence = labelDataService.getMaxSequence(234723437L);
    assertNotNull(maxSequence);
  }

  @Test
  public void testFindByPurchaseReferenceNumberAndItemNumberAndStatus() {
    when(labelDataRepository.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            any(), any(), any()))
        .thenReturn(labelDataList);
    List<LabelData> labelDataList =
        labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            "123456324", 6567880984L, "AVAILABLE");
    assertNotNull(labelDataList);
  }

  @Test
  public void testFindByPurchaseReferenceNumberInAndItemNumberAndStatus() {
    when(labelDataRepository.findByPurchaseReferenceNumberInAndItemNumberAndStatus(
            anySet(), anyLong(), anyString()))
        .thenReturn(labelDataList);
    List<LabelData> labelDataList =
        labelDataService.findByPurchaseReferenceNumberInAndItemNumberAndStatus(
            Collections.singleton("123456324"), 6567880984L, "AVAILABLE");
    assertNotNull(labelDataList);
  }

  @Test
  public void testFindByDeliveryNumberAndItemNumberAndStatus() {
    when(labelDataRepository.findByDeliveryNumberAndItemNumberAndStatus(
            anyLong(), anyLong(), anyString()))
        .thenReturn(labelDataList);
    List<LabelData> labelDataList =
        labelDataService.findByDeliveryNumberAndItemNumberAndStatus(123456L, 234123L, "AVAILABLE");
    assertNotNull(labelDataList);
  }

  @Test
  public void testFindBySsccAndAsnNumberAndStatus() {
    when(labelDataRepository.findBySsccAndAsnNumberAndStatus(anyString(), anyString(), anyString()))
        .thenReturn(labelDataList);
    List<LabelData> labelDataList =
        labelDataService.findBySsccAndAsnNumberAndStatus(
            "34137405645475248", "1356084657487", "AVAILABLE");
    assertNotNull(labelDataList);
  }

  @Test
  public void testFindByItemNumberAndStatus() {
    when(labelDataRepository.findByItemNumberAndStatus(anyLong(), anyString()))
        .thenReturn(labelDataList);
    List<LabelData> labelDataList =
        labelDataService.findByItemNumberAndStatus(123456L, "AVAILABLE");
    assertNotNull(labelDataList);
  }

  @Test
  public void testFetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber() {
    when(labelDataRepository.findByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(labelDataList);
    List<LabelData> labelDataList =
        labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            "12345623453", 23532525);
    assertNotNull(labelDataList);
  }

  @Test
  public void testFetchByPurchaseReferenceNumber() {
    when(labelDataRepository.findByPurchaseReferenceNumber(anyString())).thenReturn(labelDataList);
    List<LabelData> labelDataList =
        labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            "12345623453", 23532525);
    assertNotNull(labelDataList);
  }

  @Test
  public void testfetchLabelDataByPoAndItemNumber() {
    when(labelDataRepository.fetchLabelDataByPoAndItemNumber(
            any(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(labelDataList);
    List<LabelData> labelDataList =
        labelDataService.fetchLabelDataByPoAndItemNumber(
            "12345623453", 23532525L, "AVAILABLE", 2, 32679, "US");
    assertNotNull(labelDataList);
  }

  @Test
  public void testSaveLabel() {
    when(labelDataRepository.save(any())).thenReturn(labelData1);
    LabelData labelData = labelDataService.save(labelData1);
    assertNotNull(labelData);
  }

  @Test
  public void testFindByPurchaseReferenceNumber() {
    when(labelDataRepository.findByPurchaseReferenceNumber(any())).thenReturn(labelDataList);
    List<LabelData> labelDataList = labelDataService.fetchByPurchaseReferenceNumber("12345623453");
    assertNotNull(labelDataList);
  }

  @Test
  public void testfindByDeliveryNumberAndUPCAndLabelType() {
    when(labelDataRepository.findByDeliveryNumberAndUPCAndLabelType(any(), anyString(), any()))
        .thenReturn(labelData1);
    LabelData labelData =
        labelDataService.findByDeliveryNumberAndUPCAndLabelType(
            345623453L, "23534524364", LabelType.ORDERED);
    assertNotNull(labelData);
  }

  @Test
  public void testFetchByLpnsIn() {
    when(labelDataRepository.findByLpnsIn(any())).thenReturn(labelDataList);
    List<String> lpns = new ArrayList<>();
    lpns.add("35464623462");
    lpns.add("32563464634");
    List<LabelData> labelDataList = labelDataService.fetchByLpnsIn(lpns);
    assertNotNull(labelDataList);
  }

  @Test
  public void testFetchLabelCountByDeliveryNumberInLabelDownloadEvent() {
    when(labelDataRepository.fetchLabelCountByDeliveryNumberInLabelDownloadEvent(anyLong()))
        .thenReturn(50);
    int count = labelDataService.fetchLabelCountByDeliveryNumberInLabelDownloadEvent(123456L);
    assertEquals(count, 50);
  }

  @Test
  public void testFetchItemCountByDeliveryNumberInLabelDownloadEvent() {
    when(labelDataRepository.fetchItemCountByDeliveryNumberInLabelDownloadEvent(anyLong()))
        .thenReturn(2);
    int count = labelDataService.fetchItemCountByDeliveryNumberInLabelDownloadEvent(123456L);
    assertEquals(count, 2);
  }

  @Test
  public void testFindByTrackingIdIn() {
    when(labelDataRepository.findByTrackingIdIn(anyList())).thenReturn(labelDataList);
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("35464623462");
    trackingIds.add("32563464634");
    List<LabelData> labelDataList = labelDataService.findByTrackingIdIn(trackingIds);
    assertNotNull(labelDataList);
  }

  @Test
  public void testFindByTrackingId() {
    when(labelDataRepository.findByTrackingId(anyString())).thenReturn(labelData2);
    labelDataService.findByTrackingId("123456L");
    verify(labelDataRepository, times(1)).findByTrackingId("123456L");
  }

  @Test
  public void testFindByTrackingIdAndLabelIn() {
    when(labelDataRepository.findByTrackingIdAndLabelIn(anyString(), anyList()))
        .thenReturn(labelDataList.get(0));
    LabelData labelData =
        labelDataService.findByTrackingIdAndLabelIn(
            "23472343749832", Arrays.asList("XDK1", "XDK2"));
    assertEquals(labelDataList.get(0), labelData);
  }

  @Test
  public void testFindByTrackingIdAndStatus() {
    when(labelDataRepository.findByTrackingIdAndStatus(anyString(), anyString()))
        .thenReturn(labelData2);
    LabelData labelData = labelDataService.findByTrackingIdAndStatus("23472343749832", "AVAILABLE");
    assertEquals(labelDataList.get(0), labelData);
  }

  @Test
  public void testSaveAllAndFlush() {
    labelDataService.saveAllAndFlush(labelDataList);
    verify(labelDataRepository, times(1)).saveAllAndFlush(anyList());
  }

  @Test
  public void testSaveLabelDataLpns_LabelDataLpnInsertionEnabled() {
    labelData1 = MockLabelData.getMockHawkeyeLabelDataWithLabelDataLpns();
    labelData2 = MockLabelData.getMockHawkeyeExceptionLabelDataWithLabelDataLpns();
    LabelData savedLabelData1 = MockLabelData.getMockHawkEyeLabelData();
    LabelData savedLabelData2 = MockLabelData.getMockHawkEyeExceptionLabelData();
    savedLabelData1.setId(1L);
    savedLabelData2.setId(2L);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.LABEL_DATA_LPN_INSERTION_ENABLED))
        .thenReturn(true);
    when(labelDataLpnRepository.saveAll(anyList())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    List<LabelDataLpn> returnedLabelDataLpnList =
        labelDataService.saveLabelDataLpns(
            Arrays.asList(labelData1, labelData2), Arrays.asList(savedLabelData1, savedLabelData2));
    assertEquals(returnedLabelDataLpnList.size(), 7);
  }

  @Test
  public void testSaveLabelDataLpns_NoLabelDataLpnsToSave() {
    labelData1 = MockLabelData.getMockHawkEyeLabelData();
    labelData2 = MockLabelData.getMockHawkEyeExceptionLabelData();
    LabelData savedLabelData1 = MockLabelData.getMockHawkEyeLabelData();
    LabelData savedLabelData2 = MockLabelData.getMockHawkEyeExceptionLabelData();
    savedLabelData1.setId(1L);
    savedLabelData2.setId(2L);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.LABEL_DATA_LPN_INSERTION_ENABLED))
        .thenReturn(true);
    when(labelDataLpnRepository.saveAll(anyList())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    List<LabelDataLpn> returnedLabelDataLpnList =
        labelDataService.saveLabelDataLpns(
            Arrays.asList(labelData1, labelData2), Arrays.asList(savedLabelData1, savedLabelData2));
    assertEquals(returnedLabelDataLpnList.size(), 0);
  }

  @Test
  public void testSaveLabelDataLpns_LabelDataLpnInsertionDisabled() {
    labelData1 = MockLabelData.getMockHawkeyeLabelDataWithLabelDataLpns();
    labelData2 = MockLabelData.getMockHawkeyeExceptionLabelDataWithLabelDataLpns();
    LabelData savedLabelData1 = MockLabelData.getMockHawkEyeLabelData();
    LabelData savedLabelData2 = MockLabelData.getMockHawkEyeExceptionLabelData();
    savedLabelData1.setId(1L);
    savedLabelData2.setId(2L);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.LABEL_DATA_LPN_INSERTION_ENABLED))
        .thenReturn(false);
    when(labelDataLpnRepository.saveAll(anyList())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    List<LabelDataLpn> returnedLabelDataLpnList =
        labelDataService.saveLabelDataLpns(
            Arrays.asList(labelData1, labelData2), Arrays.asList(savedLabelData1, savedLabelData2));
    assertEquals(returnedLabelDataLpnList.size(), 0);
  }
}
