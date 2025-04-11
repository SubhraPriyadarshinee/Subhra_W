package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.APP_NAME_VALUE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REQUEST_ORIGINATOR;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class KafkaDeliveryMessagePublisherTest {

  @Mock KafkaTemplate secureKafkaTemplate;
  @Mock private KafkaConfig kafkaConfig;
  @Mock RapidRelayerService rapidRelayerService;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks KafkaDeliveryMessagePublisher kafkaDeliveryMessagePublisher;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("us");
  }

  @Test
  public void testPublish() {

    // returning null as validating return is not in this test scope
    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);

    DeliveryInfo mockDeliveryInfo = getMockDeliveryInfo();
    Map<String, Object> mockHttpHeaders = new HashMap<>();
    mockHttpHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32987");
    mockHttpHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    mockHttpHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    mockHttpHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    mockHttpHeaders.put(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    mockHttpHeaders.put(ReceivingConstants.CONTENT_TYPE, "application/json");
    mockHttpHeaders.put(REQUEST_ORIGINATOR, APP_NAME_VALUE);

    kafkaDeliveryMessagePublisher.publish(mockDeliveryInfo, mockHttpHeaders);

    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_outbox() throws ReceivingException {

    // returning null as validating return is not in this test scope

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    DeliveryInfo mockDeliveryInfo = getMockDeliveryInfo();
    Map<String, Object> mockHttpHeaders = new HashMap<>();
    mockHttpHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32987");
    mockHttpHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    mockHttpHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    mockHttpHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    mockHttpHeaders.put(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    mockHttpHeaders.put(ReceivingConstants.CONTENT_TYPE, "application/json");
    mockHttpHeaders.put(REQUEST_ORIGINATOR, APP_NAME_VALUE);

    kafkaDeliveryMessagePublisher.publish(mockDeliveryInfo, mockHttpHeaders);
    verify(rapidRelayerService, times(1)).produceMessage(any(), anyString(), anyString(), anyMap());
  }

  @Test
  public void testPublishOnSecureKafka() {
    ReflectionTestUtils.setField(
        kafkaDeliveryMessagePublisher, "secureKafkaTemplate", secureKafkaTemplate);

    // returning null as validating return is not in this test scope
    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);
    when(kafkaConfig.isGDMOnSecureKafka()).thenReturn(true);

    DeliveryInfo mockDeliveryInfo = getMockDeliveryInfo();
    Map<String, Object> mockHttpHeaders = new HashMap<>();
    mockHttpHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32987");
    mockHttpHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    mockHttpHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    mockHttpHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    mockHttpHeaders.put(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    mockHttpHeaders.put(ReceivingConstants.CONTENT_TYPE, "application/json");
    mockHttpHeaders.put(REQUEST_ORIGINATOR, APP_NAME_VALUE);

    kafkaDeliveryMessagePublisher.publish(mockDeliveryInfo, mockHttpHeaders);

    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_error() {

    try {
      // returning null as validating return is not in this test scope
      when(secureKafkaTemplate.send(any(Message.class))).thenThrow(new NullPointerException());

      DeliveryInfo mockDeliveryInfo = getMockDeliveryInfo();
      Map<String, Object> mockHttpHeaders = new HashMap<>();
      mockHttpHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32987");
      mockHttpHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
      mockHttpHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
      mockHttpHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
      mockHttpHeaders.put(ReceivingConstants.SECURITY_HEADER_KEY, "1");
      mockHttpHeaders.put(ReceivingConstants.CONTENT_TYPE, "application/json");
      mockHttpHeaders.put(REQUEST_ORIGINATOR, APP_NAME_VALUE);

      kafkaDeliveryMessagePublisher.publish(mockDeliveryInfo, mockHttpHeaders);

      verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.KAFKA_NOT_ACCESSABLE);
      assertEquals(
          e.getDescription(), "Unable to access Kafka. Flow= delivery status publish to GDM flow");
    }
  }

  private static DeliveryInfo getMockDeliveryInfo() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(2222323l);
    deliveryInfo.setTrailerNumber("22232");
    deliveryInfo.setDoorNumber("12");
    deliveryInfo.setDeliveryStatus("OPEN");
    return deliveryInfo;
  }
}
