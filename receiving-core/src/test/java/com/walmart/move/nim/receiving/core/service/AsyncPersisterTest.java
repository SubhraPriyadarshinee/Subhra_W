package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.EventTargetStatus.DELETE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.*;
import static org.testng.Assert.*;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsyncPersisterTest extends ReceivingTestBase {

  @InjectMocks private AsyncPersister asyncPersister;

  private Instruction pendingInstruction;
  private LinkedTreeMap<String, Object> move;
  @Mock private RestUtils restUtils;
  @Mock private RetryService jmsRecoveryService;
  @Mock AppConfig appConfig;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    // Move data
    move = new LinkedTreeMap<>();
    move.put("lastChangedBy", "OF-SYS");
    move.put("lastChangedOn", new Date());
    move.put("sequenceNbr", 543397582);
    move.put("containerTag", "b328990000000000000048571");
    move.put("correlationID", "98e22370-f2f0-11e8-b725-95f2a20d59c0");
    move.put("toLocation", "302");

    Map<String, Object> ctrLabel = new HashMap<String, Object>();
    Map<String, Object> data = new HashMap<String, Object>();

    data.put("value", "5213389");
    data.put("key", "H");
    data.put("value", "5216389");
    data.put("key", "M");
    List<Map<String, Object>> dataArray = new ArrayList<Map<String, Object>>();

    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("value", "526389");
    labelData.put("key", "Y");
    labelData.put("value", "526789");
    labelData.put("key", "A");
    List<Map<String, Object>> labelDataArray = new ArrayList<Map<String, Object>>();

    Map<String, String> destination = new HashMap<String, String>();
    destination.put("countryCode", "US");
    destination.put("buNumber", "6012");
    dataArray.add(data);
    ctrLabel.put("ttlInHours", "72.0");
    ctrLabel.put("data", dataArray);
    ctrLabel.put("labelData", labelDataArray);

    Distribution distribution1 = new Distribution();
    distribution1.setAllocQty(1);
    distribution1.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
    Map<String, String> item = new HashMap<String, String>();
    item.put("financialReportingGroup", "US");
    item.put("baseDivisionCode", "WM");
    item.put("itemNbr", "1084445");
    distribution1.setItem(item);

    Distribution distribution2 = new Distribution();
    distribution2.setAllocQty(2);
    distribution2.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
    Map<String, String> item1 = new HashMap<String, String>();
    item1.put("financialReportingGroup", "US");
    item1.put("baseDivisionCode", "WM");
    item1.put("itemNbr", "1084445");
    distribution2.setItem(item1);
    List<Distribution> distributions = new ArrayList<Distribution>();

    distributions.add(distribution1);
    distributions.add(distribution2);

    List<ContainerDetails> childContainers = new ArrayList<ContainerDetails>();

    ContainerDetails childContainer1 = new ContainerDetails();
    childContainer1.setCtrLabel(ctrLabel);
    childContainer1.setCtrShippable(Boolean.TRUE);
    childContainer1.setCtrReusable(Boolean.FALSE);
    childContainer1.setCtrDestination(destination);
    childContainer1.setCtrType("Vendor Pack");
    childContainer1.setQuantity(3);
    childContainer1.setOutboundChannelMethod("CROSSU");
    childContainer1.setInventoryStatus("PICKED");
    childContainer1.setDistributions(distributions);
    childContainer1.setProjectedWeight(20F);
    childContainer1.setProjectedWeightUom("EA");
    childContainer1.setOrgUnitId(1);
    childContainer1.setTrackingId("a32L8990000000000000106519");
    childContainers.add(childContainer1);

    ContainerDetails childContainer2 = new ContainerDetails();
    childContainer2.setCtrLabel(ctrLabel);
    childContainer2.setCtrShippable(Boolean.TRUE);
    childContainer2.setCtrReusable(Boolean.FALSE);
    childContainer2.setCtrDestination(destination);
    childContainer2.setCtrType("Vendor Pack");
    childContainer2.setQuantity(3);
    childContainer2.setOutboundChannelMethod("CROSSU");
    childContainer2.setInventoryStatus("PICKED");
    childContainer2.setDistributions(distributions);
    childContainer2.setProjectedWeight(20F);
    childContainer2.setProjectedWeightUom("EA");
    childContainer2.setOrgUnitId(1);
    childContainer2.setTrackingId("a32L8990000000000000106567");
    childContainers.add(childContainer2);

    // Pending Instruction
    pendingInstruction = new Instruction();
    pendingInstruction.setId(2L);
    pendingInstruction.setContainer(Mockito.mock(ContainerDetails.class));
    pendingInstruction.setChildContainers(childContainers);
    pendingInstruction.setCreateTs(new Date());
    pendingInstruction.setCreateUserId("sysadmin");
    pendingInstruction.setLastChangeTs(new Date());
    pendingInstruction.setLastChangeUserId("sysadmin");
    pendingInstruction.setDeliveryNumber(Long.valueOf("21119003"));
    pendingInstruction.setGtin("00000943037204");
    pendingInstruction.setInstructionCode("Build Container");
    pendingInstruction.setInstructionMsg("Build the Container");
    pendingInstruction.setItemDescription("HEM VALUE PACK (5)");
    pendingInstruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    pendingInstruction.setMove(move);
    pendingInstruction.setPoDcNumber("32899");
    pendingInstruction.setPrintChildContainerLabels(true);
    pendingInstruction.setPurchaseReferenceNumber("9763140005");
    pendingInstruction.setPurchaseReferenceLineNumber(1);
    pendingInstruction.setProjectedReceiveQty(2);
    pendingInstruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    pendingInstruction.setProviderId("DA");
  }

  @AfterMethod
  public void resetAfter() {
    reset(jmsRecoveryService);
    reset(restUtils);
    reset(appConfig);
  }

  @Test
  public void testAsyncPost() {
    doReturn(new ResponseEntity<>("{}", OK)).when(restUtils).post(any(), any(), any(), any());

    try {
      asyncPersister.asyncPost(
          "", "", getRetryEntity(), "url", MockHttpHeaders.getHeaders(), "{a:b}");
      verify(jmsRecoveryService, times(1)).save(any(RetryEntity.class));
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  private RetryEntity getRetryEntity() {
    RetryEntity retryEntity = new RetryEntity();
    retryEntity.setId(1L);
    return retryEntity;
  }

  @Test
  public void persistAsyncPost_success_200() {
    final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    final String url =
        "url=https://inventory-server.dev.prod.us.walmart.net/inventory/inventories/receipt?flow=rcvCorrection";

    final RetryEntity retryEntity = new RetryEntity();
    final long entityId = 1l;
    retryEntity.setId(entityId);
    retryEntity.setRetryTargetFlow(RetryTargetFlow.INVENTORY_RECEIPT_RECEIVE_CORRECTION);
    doReturn(retryEntity).when(jmsRecoveryService).putForRetries(any(), any(), any(), any(), any());
    doReturn(new ResponseEntity<>("{}", OK)).when(restUtils).post(any(), any(), any(), any());

    httpHeaders.add(IDEM_POTENCY_KEY, "trackingId");
    if (isBlank(httpHeaders.getFirst(REQUEST_ORIGINATOR))) {
      httpHeaders.add(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    }
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    asyncPersister.persistAsyncHttp(
        POST, url, "{}", httpHeaders, RetryTargetFlow.INVENTORY_RECEIPT_RECEIVE_CORRECTION);

    ArgumentCaptor<RetryEntity> argumentCaptor = ArgumentCaptor.forClass(RetryEntity.class);
    verify(jmsRecoveryService, times(1)).save(argumentCaptor.capture());
    final RetryEntity retryEntityCaptored = argumentCaptor.getValue();
    final EventTargetStatus eventTargetStatus = retryEntityCaptored.getEventTargetStatus();
    assertEquals(eventTargetStatus, DELETE);
  }

  @Test
  public void persistAsyncPost_failure_409() {
    final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    final String url =
        "url=https://inventory-server.dev.prod.us.walmart.net/inventory/inventories/receipt?flow=rcvCorrection";

    final RetryEntity retryEntity = new RetryEntity();
    retryEntity.setId(1l);
    doReturn(retryEntity).when(jmsRecoveryService).putForRetries(any(), any(), any(), any(), any());
    doReturn(new ResponseEntity<>("{}", CONFLICT)).when(restUtils).post(any(), any(), any(), any());

    httpHeaders.add(IDEM_POTENCY_KEY, "trackingId");
    if (isBlank(httpHeaders.getFirst(REQUEST_ORIGINATOR))) {
      httpHeaders.add(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    }
    asyncPersister.persistAsyncHttp(
        POST, url, "{}", httpHeaders, RetryTargetFlow.INVENTORY_RECEIPT_RECEIVE_CORRECTION);

    ArgumentCaptor<RetryEntity> argumentCaptor = ArgumentCaptor.forClass(RetryEntity.class);
    verify(jmsRecoveryService, times(0)).save(any());
  }

  /** http response code other than 200 or 409 rest should be retried */
  @Test
  public void persistAsyncPost_failure_404_5xx() {
    final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    final String url =
        "url=https://inventory-server.dev.prod.us.walmart.net/inventory/inventories/receipt?flow=rcvCorrection";

    final RetryEntity retryEntity = new RetryEntity();
    retryEntity.setId(1l);
    doReturn(retryEntity).when(jmsRecoveryService).putForRetries(any(), any(), any(), any(), any());
    doReturn(new ResponseEntity<>("{}", NOT_FOUND))
        .when(restUtils)
        .post(any(), any(), any(), any());

    httpHeaders.add(IDEM_POTENCY_KEY, "trackingId");
    if (isBlank(httpHeaders.getFirst(REQUEST_ORIGINATOR))) {
      httpHeaders.add(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    }
    // try 404
    asyncPersister.persistAsyncHttp(
        POST, url, "{}", httpHeaders, RetryTargetFlow.INVENTORY_RECEIPT_RECEIVE_CORRECTION);
    // verify
    verify(jmsRecoveryService, times(0)).save(any());

    // try 500
    doReturn(new ResponseEntity<>("{}", INTERNAL_SERVER_ERROR))
        .when(restUtils)
        .post(any(), any(), any(), any());
    asyncPersister.persistAsyncHttp(
        POST, url, "{}", httpHeaders, RetryTargetFlow.INVENTORY_RECEIPT_RECEIVE_CORRECTION);

    // verify
    verify(jmsRecoveryService, times(0)).save(any());
  }

  @Test
  public void test_persistAsyncHttpCall_POST() {

    final RetryEntity retryEntity = new RetryEntity();
    retryEntity.setId(1l);
    doReturn(retryEntity).when(jmsRecoveryService).putForRetries(any(), any(), any(), any(), any());
    doReturn(new ResponseEntity<>("{}", NOT_FOUND))
        .when(restUtils)
        .post(any(), any(), any(), any());
    asyncPersister.persistAsyncHttp(
        POST,
        "anyUrl",
        null,
        MockHttpHeaders.getHeaders(),
        RetryTargetFlow.INVENTORY_RECEIPT_RECEIVE_CORRECTION);

    verify(restUtils, atLeastOnce()).post(any(), any(), any(), any());
  }

  @Test
  public void test_persistAsyncHttpCall_PUT() {

    final RetryEntity retryEntity = new RetryEntity();
    retryEntity.setId(1l);
    doReturn(retryEntity).when(jmsRecoveryService).putForRetries(any(), any(), any(), any(), any());
    doReturn(new ResponseEntity<>("{}", NOT_FOUND)).when(restUtils).put(any(), any(), any(), any());

    asyncPersister.persistAsyncHttp(
        PUT,
        "anyUrl",
        null,
        MockHttpHeaders.getHeaders(),
        RetryTargetFlow.INVENTORY_RECEIPT_RECEIVE_CORRECTION);

    verify(restUtils, atLeastOnce()).put(any(), any(), any(), any());
  }

  @Test
  public void asyncPost_dcfin_409_no_retry_mark_delete() {
    // mock data
    final ResponseEntity<String> failEntity409 = new ResponseEntity<>("{}", CONFLICT);
    doReturn(failEntity409).when(restUtils).post(any(), any(), any(), any());

    // call
    asyncPersister.asyncPost(
        "", "", getRetryEntity(), "url", MockHttpHeaders.getHeaders(), "{a:b}");
    // verify
    ArgumentCaptor<RetryEntity> argumentCaptor = ArgumentCaptor.forClass(RetryEntity.class);
    verify(jmsRecoveryService, times(0)).save(any());
  }
}
