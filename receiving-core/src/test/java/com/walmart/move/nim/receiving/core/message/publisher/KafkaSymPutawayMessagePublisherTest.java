package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayItem;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayMessage;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
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

public class KafkaSymPutawayMessagePublisherTest {
  @Mock KafkaTemplate kafkaTemplate;
  @Mock private KafkaConfig kafkaConfig;

  @InjectMocks KafkaSymPutawayMessagePublisher kafkaSymPutawayMessagePublisher;

  private Gson gson =
      new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();

  @BeforeMethod
  public void setUp() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(kafkaSymPutawayMessagePublisher, "hawkeyeSymPutawayTopic", "TEST");
  }

  @AfterMethod
  public void resetMocks() {
    reset(kafkaConfig);
  }

  @Test
  public void testPublish_happy_path_non_secure_kafka() {
    when(kafkaConfig.isHawkeyeSecurePublish()).thenReturn(false);
    when(kafkaTemplate.send(any(Message.class))).thenReturn(null);
    SymPutawayMessage symPutawayMessage = getMockSymboticPutawayMessage();
    kafkaSymPutawayMessagePublisher.publish(symPutawayMessage, getMessageHeaders());

    verify(kafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_happy_path_secure_kafka() {
    when(kafkaConfig.isHawkeyeSecurePublish()).thenReturn(true);
    ReflectionTestUtils.setField(
        kafkaSymPutawayMessagePublisher, "secureKafkaTemplate", kafkaTemplate);
    when(kafkaTemplate.send(any(Message.class))).thenReturn(null);
    SymPutawayMessage symPutawayMessage = getMockSymboticPutawayMessage();
    kafkaSymPutawayMessagePublisher.publish(symPutawayMessage, getMessageHeaders());

    verify(kafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublishException() {
    when(kafkaTemplate.send(any(Message.class)))
        .thenThrow(
            new ReceivingInternalException(
                ExceptionCodes.KAFKA_NOT_ACCESSABLE,
                String.format(
                    KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
                    ReceivingConstants.HAWKEYE_SYM_PUTAWAY_PUBLISH_FLOW)));
    SymPutawayMessage symPutawayMessage = getMockSymboticPutawayMessage();
    try {
      kafkaSymPutawayMessagePublisher.publish(symPutawayMessage, getMessageHeaders());
    } catch (ReceivingInternalException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.KAFKA_NOT_ACCESSABLE);
    }
    verify(kafkaTemplate, times(1)).send(any(Message.class));
  }

  private SymPutawayMessage getMockSymboticPutawayMessage() {
    return SymPutawayMessage.builder()
        .action("add")
        .freightType("SSTK")
        .labelType("PALLET")
        .inventoryStatus("AVAILABLE")
        .trackingId("97700000")
        .contents(
            Arrays.asList(
                SymPutawayItem.builder()
                    .baseDivisionCode("WM")
                    .deliveryNumber(1234564L)
                    .deptNumber(123)
                    .primeSlotId("A0001")
                    .build()))
        .build();
  }

  private Map<String, Object> getMessageHeaders() {
    Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    messageHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    messageHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    messageHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "asdf1234");
    messageHeaders.put(ReceivingConstants.SYM_MESSAGE_ID_HEADER, "asdf1234");
    messageHeaders.put(ReceivingConstants.SYM_PUTAWAY_ORDER_NO, "asdf1234");
    messageHeaders.put(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    messageHeaders.put(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    return messageHeaders;
  }
}
