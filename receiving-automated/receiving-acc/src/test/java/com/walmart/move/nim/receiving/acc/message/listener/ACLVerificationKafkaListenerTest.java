package com.walmart.move.nim.receiving.acc.message.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.acc.service.ACLVerificationProcessor;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACLVerificationKafkaListenerTest {

  @InjectMocks private ACLVerificationKafkaListener aclVerificationKafkaListener;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private ACLVerificationProcessor aclVerificationProcessor;

  private String eventMessage;

  @BeforeClass
  public void setRootUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    String dataPath;
    try {

      dataPath =
          new File("../../receiving-core/src/test/resources/ACL_verification_kafka_listener.json")
              .getCanonicalPath();
      eventMessage = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assert (false);
    }

    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("us");
  }

  @AfterMethod
  public void tearDown() {
    reset(tenantSpecificConfigReader);
    reset(aclVerificationProcessor);
  }

  @Test
  public void testListenWithData() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.ACL_VERIFICATION_PROCESSOR),
            eq(EventProcessor.class)))
        .thenReturn(aclVerificationProcessor);
    aclVerificationKafkaListener.listen(
        eventMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(tenantSpecificConfigReader, times(1)).getConfiguredInstance(any(), any(), any());
    verify(aclVerificationProcessor, times(1)).processEvent(any(MessageData.class));
  }

  @Test
  public void testListenWithoutData() throws ReceivingException {
    String emptyArrayJson = "{\"labelVerificationAck\": []}";
    aclVerificationKafkaListener.listen(
        emptyArrayJson, MockMessageHeaders.getMockKafkaListenerHeaders());

    verify(tenantSpecificConfigReader, times(0)).getConfiguredInstance(any(), any(), any());
    verify(aclVerificationProcessor, times(0)).processEvent(any(MessageData.class));
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to process Notification message = \\{}")
  public void testListenWithException() {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.ACL_VERIFICATION_PROCESSOR),
            eq(EventProcessor.class)))
        .thenReturn(aclVerificationProcessor);

    String emptyJson = "{}";

    aclVerificationKafkaListener.listen(
        emptyJson, MockMessageHeaders.getMockKafkaListenerHeaders());
  }

  @Test
  public void testListenWithNullMessage() {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.ACL_VERIFICATION_PROCESSOR),
            eq(EventProcessor.class)))
        .thenReturn(aclVerificationProcessor);

    String nullJson = null;

    aclVerificationKafkaListener.listen(nullJson, MockMessageHeaders.getMockKafkaListenerHeaders());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to process Notification message = abc")
  public void testListenWithWrongJson() {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.ACL_VERIFICATION_PROCESSOR),
            eq(EventProcessor.class)))
        .thenReturn(aclVerificationProcessor);

    String wrongJson = "abc";

    aclVerificationKafkaListener.listen(
        wrongJson, MockMessageHeaders.getMockKafkaListenerHeaders());
  }
}
