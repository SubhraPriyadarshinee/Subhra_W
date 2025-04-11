package com.walmart.move.nim.receiving.core.service;

import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.client.relayer.RapidRelayerClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.RapidRelayerData;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;

public class RapidRelayerServiceTest {
  @InjectMocks private RapidRelayerService rapidRelayerServiceMock;

  @Mock private RapidRelayerClient rapidRelayerClient;

  @Mock private AppConfig appConfiguration;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void produceMessageTest_OutboxPatternEnabled() throws ReceivingException {
    String topicAndPublisherPolicyIdJson = "{\"SAMPLE_KAFKA_TOPIC\": \"PUBLISHER_POLICY_ID\"}";
    when(appConfiguration.getOutboxPatternPublisherPolicyIds())
        .thenReturn(topicAndPublisherPolicyIdJson);
    Map<String, Object> headers = new HashMap<>();
    rapidRelayerServiceMock.produceMessage(
        "SAMPLE_KAFKA_TOPIC", "message key", "message body", headers);
    verify(rapidRelayerClient, times(1)).sendDataToRapidRelayer(any(RapidRelayerData.class));
  }

  @Test
  public void produceMessageTest_OutboxPatternEnabled_publisher_policy_not_configured_in_ccm() {
    when(appConfiguration.getOutboxPatternPublisherPolicyIds()).thenReturn("");
    try {
      Map<String, Object> headers = new HashMap<>();
      rapidRelayerServiceMock.produceMessage(
          "SAMPLE_KAFKA_TOPIC", "message key", "message body", headers);
    } catch (ReceivingException re) {
      Assert.assertEquals(
          re.getErrorResponse().getErrorMessage(), "KAFKA_PUBLISHER_POLICY_ID_NOT_CONFIGURED");
    }
  }

  @Test
  public void produceHttpMessageTest_OutboxPatternEnabled() throws ReceivingException {
    String topicAndPublisherPolicyIdJson = "{\"SAMPLE_HTTP_URL\": \"PUBLISHER_POLICY_ID\"}";
    when(appConfiguration.getOutboxPatternPublisherPolicyIds())
        .thenReturn(topicAndPublisherPolicyIdJson);
    Map<String, Object> headers = new HashMap<>();
    rapidRelayerServiceMock.produceHttpMessage("SAMPLE_HTTP_URL", "message body", headers);
    verify(rapidRelayerClient, times(1)).sendDataToRapidRelayer(any(RapidRelayerData.class));
  }

  @Test
  public void produceHttpMessageTest_OutboxPatternEnabled_publisher_policy_not_configured_in_ccm() {
    when(appConfiguration.getOutboxPatternPublisherPolicyIds()).thenReturn("");
    try {
      Map<String, Object> headers = new HashMap<>();
      rapidRelayerServiceMock.produceHttpMessage("SAMPLE_HTTP_URL", "message body", headers);
    } catch (ReceivingException re) {
      Assert.assertEquals(
          re.getErrorResponse().getErrorMessage(), "HTTP_PUBLISHER_POLICY_ID_NOT_CONFIGURED");
    }
  }
}
