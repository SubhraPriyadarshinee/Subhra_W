package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.LocationSummary;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class KafkaWftLocationMessagePublisherTest {

  @Mock private KafkaTemplate secureKafkaTemplate;
  @Mock private AppConfig appConfig;
  @InjectMocks private KafkaWftLocationMessagePublisher kafkaWftLocationMessagePublisher;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(kafkaWftLocationMessagePublisher, "locationWftKafkaTopic", "TEST");
  }

  @AfterMethod
  public void tearDown() throws Exception {
    reset(secureKafkaTemplate, appConfig);
  }

  @Test
  public void testPublish_happy_path() {
    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);
    kafkaWftLocationMessagePublisher.publish(mockLocationSummary(), getMessageHeaders());
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_failure() {
    when(secureKafkaTemplate.send(any(Message.class)))
        .thenThrow(
            new ReceivingInternalException(
                ExceptionCodes.KAFKA_NOT_ACCESSABLE,
                String.format(
                    KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
                    ReceivingConstants.WFT_LOCATION_SCAN_PUBLISH_FLOW)));
    try {
      kafkaWftLocationMessagePublisher.publish(mockLocationSummary(), getMessageHeaders());
    } catch (ReceivingInternalException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.KAFKA_NOT_ACCESSABLE);
    }
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  private LocationSummary mockLocationSummary() {
    LocationSummary locationSummary = new LocationSummary();
    locationSummary.setLocation(new LocationSummary.Location(1234, "Door-100", "000111222"));
    locationSummary.setReceivingTS(new Date());
    locationSummary.setSource("atlas");
    locationSummary.setUserId("sysadmin");
    locationSummary.setFacilityCountryCode("9999");
    locationSummary.setFacilityCountryCode("US");
    return locationSummary;
  }

  private Map<String, Object> getMessageHeaders() {
    Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "9999");
    messageHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    messageHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    return messageHeaders;
  }
}
