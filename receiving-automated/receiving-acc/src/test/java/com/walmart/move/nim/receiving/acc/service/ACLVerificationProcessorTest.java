package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.model.acl.verification.ACLVerificationEventMessage;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACLVerificationProcessorTest extends ReceivingTestBase {

  @InjectMocks private ACLVerificationProcessor aclVerificationProcessor;
  @Mock private LPNReceivingService lpnReceivingService;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        aclVerificationProcessor, "lpnReceivingService", lpnReceivingService);
  }

  @AfterMethod
  public void tearDown() {
    reset(lpnReceivingService);
  }

  @Test
  public void testProcess() throws ReceivingException {
    ACLVerificationEventMessage aclVerificationEventMessage =
        JacksonParser.convertJsonToObject(
            MockACLMessageData.getVerificationEvent(), ACLVerificationEventMessage.class);

    aclVerificationProcessor.processEvent(aclVerificationEventMessage);

    verify(lpnReceivingService, times(1))
        .receiveByLPN(
            aclVerificationEventMessage.getLpn(),
            Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
            aclVerificationEventMessage.getLocationId());
  }

  @Test
  public void testProcessWhenLPNNotPresent() throws ReceivingException {
    ACLVerificationEventMessage aclVerificationEventMessage =
        JacksonParser.convertJsonToObject(
            MockACLMessageData.getVerificationEvent(), ACLVerificationEventMessage.class);
    aclVerificationEventMessage.setLpn("");

    aclVerificationProcessor.processEvent(aclVerificationEventMessage);

    verify(lpnReceivingService, times(0))
        .receiveByLPN(
            aclVerificationEventMessage.getLpn(),
            Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
            aclVerificationEventMessage.getLocationId());
  }

  @Test
  public void testProcessWhenLocationNotPresent() throws ReceivingException {
    ACLVerificationEventMessage aclVerificationEventMessage =
        JacksonParser.convertJsonToObject(
            MockACLMessageData.getVerificationEvent(), ACLVerificationEventMessage.class);
    aclVerificationEventMessage.setLocationId(null);

    aclVerificationProcessor.processEvent(aclVerificationEventMessage);

    verify(lpnReceivingService, times(0))
        .receiveByLPN(
            aclVerificationEventMessage.getLpn(),
            Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
            aclVerificationEventMessage.getLocationId());
  }

  @Test
  public void testProcessWhenDeliveryNotPresent() throws ReceivingException {
    ACLVerificationEventMessage aclVerificationEventMessage =
        JacksonParser.convertJsonToObject(
            MockACLMessageData.getVerificationEvent(), ACLVerificationEventMessage.class);
    aclVerificationEventMessage.setGroupNbr(null);

    aclVerificationProcessor.processEvent(aclVerificationEventMessage);

    verify(lpnReceivingService, times(0)).receiveByLPN(any(), anyLong(), any());
  }

  @Test
  public void testProcessWhenDeliveryisNotNumeric() throws ReceivingException {
    ACLVerificationEventMessage aclVerificationEventMessage =
        JacksonParser.convertJsonToObject(
            MockACLMessageData.getVerificationEvent(), ACLVerificationEventMessage.class);
    aclVerificationEventMessage.setGroupNbr("12a45678");

    aclVerificationProcessor.processEvent(aclVerificationEventMessage);

    verify(lpnReceivingService, times(0)).receiveByLPN(anyString(), anyLong(), anyString());
  }
}
