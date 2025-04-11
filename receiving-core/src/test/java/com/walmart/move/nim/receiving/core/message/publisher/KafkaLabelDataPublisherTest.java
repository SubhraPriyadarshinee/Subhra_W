package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.base.ReceivingTestBase.readFileFromCp;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData;
import com.walmart.move.nim.receiving.core.model.label.acl.ACLLabelDataTO;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.SettableListenableFuture;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class KafkaLabelDataPublisherTest {

  @InjectMocks KafkaLabelDataPublisher kafkaLabelDataPublisher;

  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock KafkaTemplate securePublisher;

  @Autowired private ObjectMapper mapper;

  Gson gson = new Gson();

  private static final String facilityNum = "32679";
  private static final String countryCode = "US";

  @BeforeMethod
  public void setUp() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32679);
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testPublish() {
    String kafkaLabelDataResponse = readFileFromCp("kafka_publish_message.json");
    SettableListenableFuture<Object> settableListenableFuture = new SettableListenableFuture();
    settableListenableFuture.set(new Object());
    LabelData labelData = gson.fromJson(kafkaLabelDataResponse, ACLLabelDataTO.class);
    ReflectionTestUtils.setField(
        kafkaLabelDataPublisher, "aclLabelDataTopic", "secureKafkaTemplate");
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);
    when(securePublisher.send(any(Message.class))).thenReturn(settableListenableFuture);
    KafkaHelper.buildKafkaMessage(
        1234, kafkaLabelDataResponse, "aclLabelDataTopic", getKafkaHeaders());
    kafkaLabelDataPublisher.publish(labelData, getKafkaHeaders());
    verify(securePublisher, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_exception() {
    String kafkaLabelDataResponse = readFileFromCp("kafka_publish_message.json");
    LabelData labelData = gson.fromJson(kafkaLabelDataResponse, ACLLabelDataTO.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(securePublisher.send(any(Message.class))).thenThrow(ReceivingInternalException.class);
    try {
      kafkaLabelDataPublisher.publish(labelData, getKafkaHeaders());
    } catch (ReceivingInternalException e) {
      verify(securePublisher, times(1)).send(any(Message.class));
    }
  }

  private Map<String, Object> getKafkaHeaders() {
    Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    messageHeaders.put(ReceivingConstants.TENENT_GROUP_TYPE, "RCV_DA".getBytes());
    messageHeaders.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString().getBytes());
    return messageHeaders;
  }
}
