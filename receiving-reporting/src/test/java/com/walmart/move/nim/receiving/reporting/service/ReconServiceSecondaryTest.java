package com.walmart.move.nim.receiving.reporting.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertSame;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.PrintingAndLabellingService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingContainerRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.DateUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ReconServiceSecondaryTest extends ReceivingTestBase {

  @Autowired ReportingContainerRepository containerRepository;
  @Autowired ContainerItemRepository containerItemRepository;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private InstructionService instructionService;
  @Mock private ReportPersisterService reportPersisterService;
  @Mock private PrintingAndLabellingService printingAndLabellingService;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader configUtils;
  private Gson gson = new Gson();
  @Mock private RetryService retryService;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks ReconServiceSecondary reconService;

  private ContainerItem containerItem;
  private List<ContainerItem> containerItemList;
  private ContainerDetails containerDetails = new ContainerDetails();
  private Container container;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32835);
    TenantContext.setFacilityCountryCode("US");

    ReflectionTestUtils.setField(reconService, "containerRepository", containerRepository);
    ReflectionTestUtils.setField(reconService, "gson", gson);
    ReflectionTestUtils.setField(
        reconService, "instructionHelperService", instructionHelperService);

    container = new Container();
    container.setTrackingId("a32L8990000000000000106519");
    container.setMessageId("aebdfdf0-feb6-11e8-9ed2-f32La312b7689");
    container.setDeliveryNumber(1l);

    populateDataInContainerTable();
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "REQUEST ERROR: fromDate should come before toDate.")
  public void testPostReceivedQtyGivenTimeAndActivityName_Failure() throws ReceivingException {
    Date date1 = new Date();
    Date date2 = new Date();
    date2.setTime(date2.getTime() + 10000);
    reconService.postReceivedQtyGivenTimeAndActivityName(
        "SSTK", date2, date1, MockHttpHeaders.getHeaders());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "REQUEST ERROR: fromDate and toDate are mandatory.")
  public void testPostReceivedQtyGivenTimeAndActivityName_DateNotProvided()
      throws ReceivingException {
    reconService.postReceivedQtyGivenTimeAndActivityName(
        "SSTK", new Date(), null, MockHttpHeaders.getHeaders());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "REQUEST ERROR: dateTime interval can not be more than 24 hrs.")
  public void testPostReceivedQtyGivenTimeAndActivityName_DateTimeIntervalIsMoreThan24Hrs()
      throws ReceivingException {
    Date date1 = new Date();
    Date date2 = new Date();
    date2.setTime(date2.getTime() + TimeUnit.DAYS.toMillis(2));
    reconService.postReceivedQtyGivenTimeAndActivityName(
        "SSTK", date1, date2, MockHttpHeaders.getHeaders());
  }

  private void populateDataInContainerTable() {
    Date fromDate1 = new Date();
    List<Container> containers = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();

    Container container1 = new Container();
    ContainerItem containerItem1 = new ContainerItem();
    container1.setDeliveryNumber(123456L);
    container1.setFacilityNum(32835);
    container1.setFacilityCountryCode("US");
    container1.setMessageId("erhn-hdfnn-43ewh3-85hd7");
    container1.setTrackingId("987654321");
    container1.setCreateUser("userA");
    container1.setCreateTs(fromDate1);
    containerItem1.setTrackingId("987654321");
    containerItem1.setPurchaseReferenceLineNumber(1);
    containerItem1.setQuantity(5);
    containerItem1.setInboundChannelMethod("SSTK");
    containers.add(container1);
    containerItems.add(containerItem1);

    Container container2 = new Container();
    ContainerItem containerItem2 = new ContainerItem();
    container2.setDeliveryNumber(123457L);
    container2.setMessageId("e1hn-hd22n-431wh3-85cd7");
    container2.setFacilityNum(32835);
    container2.setFacilityCountryCode("US");
    container2.setTrackingId("987654322");
    container2.setCreateUser("userA");
    container2.setCreateTs(fromDate1);
    containerItem2.setTrackingId("987654322");
    containerItem2.setPurchaseReferenceLineNumber(2);
    containerItem2.setQuantity(4);
    containerItem2.setInboundChannelMethod("SSTK");
    containers.add(container2);
    containerItems.add(containerItem2);

    Container container3 = new Container();
    ContainerItem containerItem3 = new ContainerItem();
    container3.setDeliveryNumber(123458L);
    container3.setMessageId("13hn-4dfnn-43ew5d-1d3d7");
    container3.setFacilityNum(32835);
    container3.setFacilityCountryCode("US");
    container3.setTrackingId("987654323");
    container3.setCreateUser("userA");
    container3.setCreateTs(fromDate1);
    containerItem3.setTrackingId("987654323");
    containerItem3.setPurchaseReferenceLineNumber(1);
    containerItem3.setQuantity(10);
    containerItem3.setInboundChannelMethod("CROSSU");
    containers.add(container3);
    containerItems.add(containerItem3);

    containerRepository.saveAll(containers);
    containerItemRepository.saveAll(containerItems);
  }

  @Test
  public void testPostReceivedQtyGivenTimeAndActivityName_WhenActivityNameIsGiven()
      throws ReceivingException {

    Date fromDate = new Date();
    Date toDate = new Date();
    fromDate.setTime(fromDate.getTime() - 100000);
    toDate.setTime(toDate.getTime() + 100000);

    List<WFTResponse> wftResponses =
        reconService.postReceivedQtyGivenTimeAndActivityName(
            "SSTK", fromDate, toDate, MockHttpHeaders.getHeaders("32835", "US"));

    Assert.assertEquals(wftResponses.size(), 1);
    Assert.assertEquals(wftResponses.get(0).getUserName(), "userA");
    Assert.assertEquals(wftResponses.get(0).getReceivedQty(), (Long) 9L);
  }

  @Test
  public void testPostReceivedQtyGivenTimeAndActivityName_WhenActivityNameIsNotGiven()
      throws ReceivingException {

    Date fromDate = new Date();
    Date toDate = new Date();
    fromDate.setTime(fromDate.getTime() - 20000);
    toDate.setTime(toDate.getTime() + 20000);
    List<WFTResponse> wftResponses =
        reconService.postReceivedQtyGivenTimeAndActivityName(
            null, fromDate, toDate, MockHttpHeaders.getHeaders("32835", "US"));
    Assert.assertEquals(wftResponses.size(), 2);
    Assert.assertEquals(wftResponses.get(1).getActivityName(), "SSTK");
    Assert.assertEquals(wftResponses.get(1).getReceivedQty(), (Long) 9L);
    Assert.assertEquals(wftResponses.get(0).getActivityName(), "CROSSU");
    Assert.assertEquals(wftResponses.get(0).getReceivedQty(), (Long) 10L);
  }

  @Test
  public void testGetReconciledSummaryByTime_Success() throws ReceivingException {

    DcFinReconciledDate dcFinReconciledDate = new DcFinReconciledDate();
    List<DcFinReconciledDate> dcFinReconciledDates = new ArrayList<>();
    dcFinReconciledDate.setContainerId("a32L8990000000000000106519");
    dcFinReconciledDate.setDeliveryNum(1l);
    dcFinReconciledDates.add(dcFinReconciledDate);

    Date fromDate = new Date();
    Date toDate = new Date();
    fromDate.setTime(fromDate.getTime() - 100000);
    toDate.setTime(toDate.getTime() + 100000);

    List<DcFinReconciledDate> dcFinReconciledDatesResponse =
        reconService.getReconciledDataSummaryByTime(fromDate, toDate, MockHttpHeaders.getHeaders());
    for (DcFinReconciledDate dcFinReconciledDatesResp : dcFinReconciledDatesResponse) {
      assertNotNull(dcFinReconciledDatesResp.getDeliveryNum());
      assertNotNull(dcFinReconciledDatesResp.getContainerId());
    }
  }

  @AfterMethod
  public void tearDown() {
    reset(instructionPersisterService);
    reset(instructionService);
    reset(reportPersisterService);
    reset(appConfig);
  }

  //  private static String getFileAsString(String filePath) {
  //
  //    try {
  //      String dataPath = new File(filePath).getCanonicalPath();
  //      return new String(Files.readAllBytes(Paths.get(dataPath)));
  //    } catch (IOException e) {
  //      fail("Unable to read file " + e.getMessage());
  //    }
  //    return null;
  //  }

  public static String getMockInstruction() {
    return getFileAsString("../receiving-test/src/main/resources/" + "json/MockInstruction.json");
  }

  @Test
  public void testPostLabels() throws ReceivingException {
    String trackingId = "a123400000001";
    Container container = getContainer(trackingId);
    when(reportPersisterService.findContainerByTrackingId(trackingId)).thenReturn(container);

    List<Container> containerList = new ArrayList<>();
    containerList.add(getContainer("a123400000002"));
    containerList.add(getContainer("a123400000003"));
    when(reportPersisterService.findContainerByDeliveryNumberAndUser(
            any(), anyString(), any(), any()))
        .thenReturn(containerList);

    Instruction instruction = gson.fromJson(getMockInstruction(), Instruction.class);
    instruction.setCompleteTs(new Date());
    when(reportPersisterService.findInstructionByIds(anyList()))
        .thenReturn(Arrays.asList(instruction));

    when(instructionPersisterService.getPrintlabeldata(any(), anyInt(), anyInt(), any()))
        .thenReturn(getCtrLabel());

    ReprintLabelRequest reprintLabelRequest = new ReprintLabelRequest();
    reprintLabelRequest.setTrackingId(trackingId);

    ReprintLabelResponse reprintLabelResponse =
        reconService.postLabels(reprintLabelRequest, MockHttpHeaders.getHeaders());
    assertNotNull(reprintLabelResponse);
    assertNotNull(reprintLabelResponse.getTrackingIds());
    assertEquals(reprintLabelResponse.getTrackingIds().size(), 2);

    verify(reportPersisterService, times(1)).findContainerByTrackingId(eq(trackingId));
    verify(reportPersisterService, times(1))
        .findContainerByDeliveryNumberAndUser(eq(12345678L), eq("sys"), any(), any());
    verify(reportPersisterService, times(1)).findInstructionByIds(anyList());
    verify(instructionPersisterService, times(1))
        .getPrintlabeldata(any(), anyInt(), anyInt(), any());
  }

  @Test
  public void testPostLabelsGivenDeliveryNumber() throws ReceivingException {

    List<Container> containerList = new ArrayList<>();
    containerList.add(getContainer("a123400000002"));
    containerList.add(getContainer("a123400000003"));
    when(reportPersisterService.findContainersByDeliveryNumberAndCreateUserAndCreateTsBefore(
            any(), anyString(), any()))
        .thenReturn(containerList);

    Instruction instruction = gson.fromJson(getMockInstruction(), Instruction.class);
    instruction.setCompleteTs(new Date());
    when(reportPersisterService.findInstructionByIds(anyList()))
        .thenReturn(Arrays.asList(instruction));

    when(instructionPersisterService.getPrintlabeldata(any(), anyInt(), anyInt(), any()))
        .thenReturn(getCtrLabel());

    ReprintLabelRequest reprintLabelRequest = new ReprintLabelRequest();
    reprintLabelRequest.setDeliveryNumber(12345678L);

    ReprintLabelResponse reprintLabelResponse =
        reconService.postLabels(reprintLabelRequest, MockHttpHeaders.getHeaders());
    assertNotNull(reprintLabelResponse);
    assertNotNull(reprintLabelResponse.getTrackingIds());
    assertEquals(reprintLabelResponse.getTrackingIds().size(), 2);

    verify(reportPersisterService, times(1))
        .findContainersByDeliveryNumberAndCreateUserAndCreateTsBefore(
            eq(12345678L), eq("sysadmin"), any());
    verify(reportPersisterService, times(1)).findInstructionByIds(anyList());
    verify(instructionPersisterService, times(1))
        .getPrintlabeldata(any(), anyInt(), anyInt(), any());
  }

  @Test
  public void testPostLabelsGivenDeliveryNumberAndDateRange() throws ReceivingException {

    List<Container> containerList = new ArrayList<>();
    containerList.add(getContainer("a123400000002"));
    containerList.add(getContainer("a123400000003"));
    when(reportPersisterService.findContainerByDeliveryNumberAndUser(
            any(), anyString(), any(), any()))
        .thenReturn(containerList);

    Instruction instruction = gson.fromJson(getMockInstruction(), Instruction.class);
    instruction.setCompleteTs(new Date());
    when(reportPersisterService.findInstructionByIds(anyList()))
        .thenReturn(Arrays.asList(instruction));

    when(instructionPersisterService.getPrintlabeldata(any(), anyInt(), anyInt(), any()))
        .thenReturn(getCtrLabel());

    ReprintLabelRequest reprintLabelRequest = new ReprintLabelRequest();
    reprintLabelRequest.setDeliveryNumber(12345678L);
    reprintLabelRequest.setFromDate(new Date());
    reprintLabelRequest.setToDate(new Date());

    ReprintLabelResponse reprintLabelResponse =
        reconService.postLabels(reprintLabelRequest, MockHttpHeaders.getHeaders());
    assertNotNull(reprintLabelResponse);
    assertNotNull(reprintLabelResponse.getTrackingIds());
    assertEquals(reprintLabelResponse.getTrackingIds().size(), 2);

    verify(reportPersisterService, times(1))
        .findContainerByDeliveryNumberAndUser(eq(12345678L), eq("sysadmin"), any(), any());
    verify(reportPersisterService, times(1)).findInstructionByIds(anyList());
    verify(instructionPersisterService, times(1))
        .getPrintlabeldata(any(), anyInt(), anyInt(), any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Invalid request. Please provide either of following :.*")
  public void testPostLabels_InvalidRequest() {
    reconService.postLabels(new ReprintLabelRequest(), MockHttpHeaders.getHeaders());
  }

  @Test
  public void testPostLabelsNoContainers() {
    when(reportPersisterService.findContainerByTrackingId(any())).thenReturn(null);
    ReprintLabelRequest reprintLabelRequest = new ReprintLabelRequest();
    reprintLabelRequest.setTrackingId("a12300000001");
    ReprintLabelResponse reprintLabelResponse =
        reconService.postLabels(reprintLabelRequest, MockHttpHeaders.getHeaders());
    assertNotNull(reprintLabelResponse);
    assertNull(reprintLabelResponse.getTrackingIds());
  }

  public static Map<String, Object> getCtrLabel() {
    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "ITEM");
    labelData.put("value", "100001");
    labelData.put("key", "DESTINATION");
    labelData.put("value", "06021 US");
    labelData.put("key", "UPCBAR");
    labelData.put("value", "00075486091132");
    labelData.put("key", "LPN");
    labelData.put("value", "a328990000000000000106509");
    labelData.put("key", "FULLUSERID");
    labelData.put("value", "sysadmin");
    labelData.put("key", "TYPE");
    labelData.put("value", "DA");
    labelData.put("key", "DESC1");
    labelData.put("value", "TR ED 3PC FRY/GRL RD");

    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "a328990000000000000106509");
    printRequest.put("formatName", "pallet_lpn_format");
    printRequest.put("ttlInHours", 1);
    printRequest.put("data", labelDataList);

    List<Map<String, Object>> printRequestList = new ArrayList<Map<String, Object>>();
    printRequestList.add(printRequest);

    Map<String, Object> containerLabel = new HashMap<>();
    containerLabel.put("clientId", "OF");
    containerLabel.put("headers", MockHttpHeaders.getHeaders());
    containerLabel.put("printRequests", printRequestList);
    return containerLabel;
  }

  private Container getContainer(String trackingId) {
    Container container = new Container();
    container.setTrackingId(trackingId);
    container.setCreateUser("sys");
    container.setCreateTs(new Date());
    container.setDeliveryNumber(12345678L);
    container.setInstructionId(1L);
    return container;
  }

  @Test
  public void testMetricReport() {
    when(reportPersisterService.getAtlasReportCount(any(), any(), any(), anyString()))
        .thenReturn(null);
    when(reportPersisterService.getPalletSSCCScanCount(
            any(), any(), any(), anyString(), anyList()))
        .thenReturn(null);
    when(reportPersisterService.getCaseSSCCScanCount(
            any(), any(), any(), anyString(), anyList()))
        .thenReturn(null);
    when(reportPersisterService.getPalletLabelCanceledCount(
            any(), any(), any(), anyString(), anyString()))
        .thenReturn(null);
  }

  @Test
  public void test_resetJmsRetryCount() {

    ArgumentCaptor<Integer> applicationTypeCaptor = ArgumentCaptor.forClass(Integer.class);
    doNothing()
        .when(retryService)
        .resetJmsRetryCount(
            anyInt(), anyLong(), anyInt(), any(Date.class), any(Date.class), anyInt());

    JmsRetryResetRequest jmsRetryResetRequest = new JmsRetryResetRequest();
    jmsRetryResetRequest.setFromDate(DateUtils.addDays(new Date(), -2));
    jmsRetryResetRequest.setToDate(new Date());
    jmsRetryResetRequest.setActivityName("REST");

    reconService.resetJmsRetryCount(jmsRetryResetRequest);

    verify(retryService, times(1))
        .resetJmsRetryCount(
            anyInt(),
            anyLong(),
            applicationTypeCaptor.capture(),
            any(Date.class),
            any(Date.class),
            anyInt());
    assertSame(applicationTypeCaptor.getValue(), 1);
  }

  @Test
  public void test_resetJmsRetryCountById() {

    ArgumentCaptor<Integer> applicationTypeCaptor = ArgumentCaptor.forClass(Integer.class);
    doNothing().when(retryService).resetJmsRetryCount(anyInt(), anyLong(), anyInt(), anyList());

    ActivityWithIdRequest activityWithIdRequest = new ActivityWithIdRequest();
    activityWithIdRequest.setActivityName("REST");
    activityWithIdRequest.setIds(Arrays.asList(1l));

    reconService.resetJmsRetryCountById(activityWithIdRequest);

    verify(retryService, times(1))
        .resetJmsRetryCount(anyInt(), anyLong(), applicationTypeCaptor.capture(), anyList());
    assertSame(applicationTypeCaptor.getValue(), 1);
  }
}
