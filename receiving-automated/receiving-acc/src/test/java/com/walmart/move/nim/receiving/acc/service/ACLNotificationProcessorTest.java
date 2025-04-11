package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACLNotificationProcessorTest extends ReceivingTestBase {

  private Gson gson = new Gson();
  @InjectMocks private ACLNotificationProcessor aclNotificationProcessor;

  @Mock private ACLNotificationService aclNotificationService;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
  }

  @Test
  public void testProcessEvent() {
    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    doNothing()
        .when(aclNotificationService)
        .sendNotificationToSumo(
            notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    aclNotificationProcessor.processEvent(notification);
    verify(aclNotificationService, times(1))
        .sendNotificationToSumo(
            notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
  }
}
