package com.walmart.move.nim.receiving.core.client.relayer;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_TOPIC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KEY;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.model.RapidRelayerData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.service.OutboxEventSinkService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RapidRelayerClientTest {

  @Mock private OutboxEventSinkService outboxEventSinkService;
  @InjectMocks private RapidRelayerClient rapidRelayerClientMock;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void sendDataToRapidRelayerTest() {
    rapidRelayerClientMock.sendDataToRapidRelayer(mockRelayerData());
    verify(outboxEventSinkService, times(1)).saveEvent(any(OutboxEvent.class));
  }

  private RapidRelayerData mockRelayerData() {
    Map<String, Object> rapidRelayerDataHeaders = new HashMap<>();
    rapidRelayerDataHeaders.put("TEST_KAFKA_HEADER_FACILITY_NUM", "32709");
    Map<String, Object> rapiRelayerMetaDataValues = new HashMap<>();
    rapiRelayerMetaDataValues.put(KAFKA_TOPIC, "TEST_TOPIC_NAME");
    rapiRelayerMetaDataValues.put(KEY, "Message_Key");

    return RapidRelayerData.builder()
        .eventIdentifier("TEST_UNIQUE_EVENT_IDENTIFIER")
        .executionTs(Instant.now())
        .publisherPolicyId("KAFKA_PUBLISHER_POLICY_ID")
        .headers(rapidRelayerDataHeaders)
        .body("TEST_KAFKA_PULISHER_PAYLOAD")
        .metaDataValues(rapiRelayerMetaDataValues)
        .build();
  }
}
