package com.walmart.move.nim.receiving.acc.message.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
import com.walmart.move.nim.receiving.acc.service.ACLNotificationProcessor;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.jms.JMSException;
import javax.jms.TextMessage;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACLNotificationListenerTest extends ReceivingTestBase {

  private Gson gson = new Gson();
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @InjectMocks private ACLNotificationListener aclNotificationListener;

  @Spy private TextMessage textMessage;

  @Mock private ACLNotificationProcessor aclNotificationProcessor;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(aclNotificationListener, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(textMessage);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testListen() {
    try {

      when(tenantSpecificConfigReader.getConfiguredInstance(
              anyString(),
              eq(ReceivingConstants.ACL_NOTIFICATION_PROCESSOR),
              eq(EventProcessor.class)))
          .thenReturn(aclNotificationProcessor);
      String aclNotification = MockACLMessageData.getNotificationEvent();
      when(textMessage.getText()).thenReturn(aclNotification);
      aclNotificationListener.listen(textMessage, MockMessageHeaders.getHeaders());

      verify(textMessage, times(1)).getText();
      ArgumentCaptor<ACLNotification> captor = ArgumentCaptor.forClass(ACLNotification.class);
      verify(aclNotificationProcessor).processEvent(captor.capture());
      assertEquals(
          gson.toJson(captor.getValue()).replaceAll("\\s+", ""),
          aclNotification.replaceAll("\\s+", ""));

    } catch (Exception e) {
      fail("No exception expected. Exception occurred - " + e.getMessage());
    }
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Invalid tenant headers in.*")
  public void testListenHeadersMissing() throws ReceivingException, JMSException {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.ACL_NOTIFICATION_PROCESSOR),
            eq(EventProcessor.class)))
        .thenReturn(aclNotificationProcessor);
    aclNotificationListener.listen(textMessage, MockMessageHeaders.getHeadersWithoutFacilityNum());
    verify(textMessage, times(0)).getText();
  }
}
