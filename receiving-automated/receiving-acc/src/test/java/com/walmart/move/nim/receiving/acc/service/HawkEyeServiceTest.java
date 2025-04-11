package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.common.base.Supplier;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.model.acl.label.ACLLabelCount;
import com.walmart.move.nim.receiving.acc.model.acl.notification.HawkEyeDeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class HawkEyeServiceTest extends ReceivingTestBase {

  @Mock private SimpleRestConnector simpleRestConnector;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private ACCManagedConfig accManagedConfig;

  @InjectMocks private HawkEyeService hawkEyeService;

  private Gson gson;

  private HawkEyeDeliveryAndLocationMessage hawkEyeDeliveryAndLocationMessage;
  private HawkEyeDeliveryAndLocationMessage hawkEyeDeliveryAndLocationMessage2;

  List<HawkEyeDeliveryAndLocationMessage> hawkEyeDeliveryAndLocationMessages;

  private ACLLabelCount[] aclLabelCountsResponse;

  @BeforeClass()
  public void setupRoot() {
    TenantContext.setFacilityNum(32800);
    TenantContext.setFacilityCountryCode("US");
    hawkEyeDeliveryAndLocationMessage = new HawkEyeDeliveryAndLocationMessage();
    hawkEyeDeliveryAndLocationMessage.setUserId("sysadmin");
    hawkEyeDeliveryAndLocationMessage.setLocation("247");
    hawkEyeDeliveryAndLocationMessage.setDeliveryNumber(40375082L);

    hawkEyeDeliveryAndLocationMessage2 = new HawkEyeDeliveryAndLocationMessage();
    hawkEyeDeliveryAndLocationMessage2.setUserId("rcvuser");
    hawkEyeDeliveryAndLocationMessage2.setLocation("248");
    hawkEyeDeliveryAndLocationMessage2.setDeliveryNumber(40375083L);

    hawkEyeDeliveryAndLocationMessages = new ArrayList<>();
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage);
    hawkEyeDeliveryAndLocationMessages.add(hawkEyeDeliveryAndLocationMessage2);

    aclLabelCountsResponse = new ACLLabelCount[1];
  }

  @BeforeMethod()
  public void setup() {
    MockitoAnnotations.initMocks(this);
    gson = new Gson();
    ReflectionTestUtils.setField(hawkEyeService, "gson", gson);

    when(tenantSpecificConfigReader.getHawkEyeRoninUrlOrDefault(any(Supplier.class)))
        .thenReturn("baseUrl1");
  }

  @AfterMethod()
  public void resetMocks() {
    reset(simpleRestConnector);
    reset(accManagedConfig);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testDeliveryLink_Success() {
    aclLabelCountsResponse[0] =
        gson.fromJson(MockACLMessageData.getHawkeyeDeliveryLinkEvent(), ACLLabelCount.class);
    when(simpleRestConnector.post(anyString(), any(), any(HttpHeaders.class), any()))
        .thenReturn(new ResponseEntity<>(aclLabelCountsResponse, HttpStatus.OK));
    List<ACLLabelCount> aclLabelCounts =
        hawkEyeService.deliveryLink(hawkEyeDeliveryAndLocationMessages);
    assertNotNull(aclLabelCounts);
    assertEquals(
        aclLabelCounts,
        Collections.singletonList(
            gson.fromJson(MockACLMessageData.getHawkeyeDeliveryLinkEvent(), ACLLabelCount.class)));
  }

  @Test(
      expectedExceptions = ReceivingConflictException.class,
      expectedExceptionsMessageRegExp =
          "System is currently busy processing requests. Please try after some time")
  public void testDeliveryLink_Conflict() {
    aclLabelCountsResponse[0] =
        gson.fromJson(MockACLMessageData.getHawkeyeDeliveryLinkEvent(), ACLLabelCount.class);
    when(simpleRestConnector.post(anyString(), any(), any(HttpHeaders.class), any()))
        .thenThrow(mockRestClientException(HttpStatus.CONFLICT));
    hawkEyeService.deliveryLink(hawkEyeDeliveryAndLocationMessages);
  }

  @Test
  public void testDeliveryLink_NotFound() {
    aclLabelCountsResponse[0] =
        gson.fromJson(MockACLMessageData.getHawkeyeDeliveryLinkEvent(), ACLLabelCount.class);
    when(simpleRestConnector.post(anyString(), any(), any(HttpHeaders.class), any()))
        .thenThrow(mockRestClientException(HttpStatus.NOT_FOUND));
    List<ACLLabelCount> aclLabelCounts =
        hawkEyeService.deliveryLink(hawkEyeDeliveryAndLocationMessages);
    assertEquals(aclLabelCounts.size(), 0);
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp =
          "System is currently busy processing requests. Please try after some time")
  public void testDeliveryLink_InternalServerError() {
    aclLabelCountsResponse[0] =
        gson.fromJson(MockACLMessageData.getHawkeyeDeliveryLinkEvent(), ACLLabelCount.class);
    when(simpleRestConnector.post(anyString(), any(), any(HttpHeaders.class), any()))
        .thenThrow(new ResourceAccessException("Error"));
    hawkEyeService.deliveryLink(hawkEyeDeliveryAndLocationMessages);
  }

  private RestClientResponseException mockRestClientException(HttpStatus httpStatus) {
    return new RestClientResponseException(
        "Some error.", httpStatus.value(), "", null, "".getBytes(), StandardCharsets.UTF_8);
  }
}
