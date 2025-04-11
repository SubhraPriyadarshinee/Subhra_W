package com.walmart.move.nim.receiving.core.message.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateInstructionMessage;
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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class KafkaItemUpdateInstructionPublisherTest {
  @InjectMocks KafkaItemUpdateInstructionPublisher itemUpdateInstructionPublisher;
  @Mock KafkaTemplate securePublisher;

  private ItemUpdateInstructionMessage itemUpdateInstructionMessage;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    itemUpdateInstructionMessage =
        ItemUpdateInstructionMessage.builder()
            .itemNumber(123)
            .rejectCode("GLS-RCV-BE-0001")
            .build();
    TenantContext.setFacilityNum(32888);
    TenantContext.setFacilityCountryCode("US");
    ReflectionTestUtils.setField(
        itemUpdateInstructionPublisher, "itemUpdateInstructionTopic", "TEST");
  }

  @AfterMethod
  public void resetMocks() {
    reset(securePublisher);
  }

  @Test
  public void testPublish() {
    when(securePublisher.send(any(Message.class))).thenReturn(null);
    itemUpdateInstructionPublisher.publish(itemUpdateInstructionMessage, getMockHeaders());
    verify(securePublisher, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_throwsException() {
    try {
      when(securePublisher.send(any(Message.class))).thenThrow(new NullPointerException());
      itemUpdateInstructionPublisher.publish(itemUpdateInstructionMessage, getMockHeaders());
      verify(securePublisher, times(1)).send(any(Message.class));
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.KAFKA_NOT_ACCESSABLE);
      assertEquals(e.getDescription(), ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG);
    }
  }

  static Map<String, Object> getMockHeaders() {
    Map<String, Object> headers = new HashMap<>();
    headers.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "test123");
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum().toString());
    headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    headers.put(ReceivingConstants.EVENT_TYPE, "test_event");
    return headers;
  }
}
