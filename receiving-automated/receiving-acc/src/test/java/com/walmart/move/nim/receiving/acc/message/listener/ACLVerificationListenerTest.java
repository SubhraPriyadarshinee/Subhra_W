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
import com.walmart.move.nim.receiving.acc.model.acl.verification.ACLVerificationEventMessage;
import com.walmart.move.nim.receiving.acc.service.ACLVerificationProcessor;
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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACLVerificationListenerTest extends ReceivingTestBase {
  private Gson gson = new Gson();
  @Spy private TextMessage textMessage;

  @InjectMocks ACLVerificationListener aclVerificationListener;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private ACLVerificationProcessor aclVerificationProcessor;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    reset(tenantSpecificConfigReader);
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
              eq(ReceivingConstants.ACL_VERIFICATION_PROCESSOR),
              eq(EventProcessor.class)))
          .thenReturn(aclVerificationProcessor);

      String aclVerification = MockACLMessageData.getVerificationEvent();
      when(textMessage.getText()).thenReturn(aclVerification);
      aclVerificationListener.listen(textMessage, MockMessageHeaders.getHeaders());
      verify(textMessage, times(1)).getText();

      ArgumentCaptor<ACLVerificationEventMessage> captor =
          ArgumentCaptor.forClass(ACLVerificationEventMessage.class);
      verify(aclVerificationProcessor, times(1)).processEvent(captor.capture());
      assertEquals(
          gson.toJson(captor.getValue()).replaceAll("\\s+", ""),
          aclVerification.replaceAll("\\s+", ""));

    } catch (Exception e) {
      fail("No exception expected. Exception occurred - " + e.getMessage());
    }
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Invalid tenant headers in.*")
  public void testListenHeadersMissing() throws JMSException, ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.ACL_VERIFICATION_PROCESSOR),
            eq(EventProcessor.class)))
        .thenReturn(aclVerificationProcessor);
    aclVerificationListener.listen(textMessage, MockMessageHeaders.getHeadersWithoutFacilityNum());
    verify(textMessage, times(0)).getText();
  }
}
