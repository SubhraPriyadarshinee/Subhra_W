package com.walmart.move.nim.receiving.core.common;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.walmart.move.nim.receiving.core.model.RapidRelayerData;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class RapidRelayerDataMapperTest {
  @InjectMocks RapidRelayerDataMapper rapidRelayerDataMapperMock;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void mapKafkaPublisherRequestoRapidRelayerDataTest() {
    Map<String, Object> rapidRelayerDataHeaders = new HashMap<>();
    rapidRelayerDataHeaders.put("TEST_KAFKA_HEADER_FACILITY_NUM", "32709");
    RapidRelayerData rapidRelayerData =
        rapidRelayerDataMapperMock.mapKafkaPublisherRequestoRapidRelayerData(
            "TEST_KAFKA_TOPIC",
            "TEST_KAFKA_PUBLISHER_POLICY_ID",
            "TEST_KAFKA_MESSAGE_KEY",
            "TEST_KAFKA_MESSAGE_PAYLOAD",
            rapidRelayerDataHeaders);
    assertThat(rapidRelayerData.getEventIdentifier(), is(notNullValue()));
    assertThat(rapidRelayerData.getExecutionTs(), is(notNullValue()));
    assertThat(rapidRelayerData.getPublisherPolicyId(), is(notNullValue()));
    assertThat(rapidRelayerData.getHeaders(), is(notNullValue()));
    assertThat(rapidRelayerData.getBody(), is(notNullValue()));
    assertThat(rapidRelayerData.getMetaDataValues(), is(notNullValue()));
  }

  @Test
  public void mapHttpPublisherRequestoRapidRelayerDataTest() {
    Map<String, Object> rapidRelayerDataHeaders = new HashMap<>();
    rapidRelayerDataHeaders.put("TEST_KAFKA_HEADER_FACILITY_NUM", "32709");
    RapidRelayerData rapidRelayerData =
        rapidRelayerDataMapperMock.mapHttpPublisherRequestoRapidRelayerData(
            "TEST_HTTP_PUBLISHER_POLICY_ID", "TEST_HTTP_MESSAGE_PAYLOAD", rapidRelayerDataHeaders);
    assertThat(rapidRelayerData.getEventIdentifier(), is(notNullValue()));
    assertThat(rapidRelayerData.getExecutionTs(), is(notNullValue()));
    assertThat(rapidRelayerData.getPublisherPolicyId(), is(notNullValue()));
    assertThat(rapidRelayerData.getHeaders(), is(notNullValue()));
    assertThat(rapidRelayerData.getBody(), is(notNullValue()));
    assertThat(rapidRelayerData.getMetaDataValues(), is(notNullValue()));
  }
}
