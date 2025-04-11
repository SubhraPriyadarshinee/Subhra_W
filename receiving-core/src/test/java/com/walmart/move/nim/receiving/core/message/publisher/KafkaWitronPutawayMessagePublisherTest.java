package com.walmart.move.nim.receiving.core.message.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.AssertJUnit.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.model.witron.WitronPutawayItem;
import com.walmart.move.nim.receiving.core.model.witron.WitronPutawayMessage;
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

public class KafkaWitronPutawayMessagePublisherTest {
  @InjectMocks KafkaWitronPutawayMessagePublisher kafkaWitronPutawayMessagePublisher;

  @Mock private AppConfig appConfig;
  @Mock private KafkaConfig kafkaConfig;
  @Mock private KafkaTemplate secureKafkaTemplate;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;

  private Gson gson =
      new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(7010);
    ReflectionTestUtils.setField(
        kafkaWitronPutawayMessagePublisher, "hawkeyeWitronPutawayTopic", "TEST");
  }

  @AfterMethod
  public void resetMocks() {
    reset(appConfig, secureKafkaTemplate, kafkaConfig);
  }

  @Test
  public void testPublish_happy_path() {
    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    kafkaWitronPutawayMessagePublisher.publish(getWitronPutawayMessage(), getMessageHeaders());

    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_exception() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(secureKafkaTemplate.send(any(Message.class)))
        .thenThrow(
            new ReceivingInternalException(
                ExceptionCodes.KAFKA_NOT_ACCESSABLE,
                String.format(
                    ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
                    ReceivingConstants.HAWKEYE_WITRON_PUTAWAY_PUBLISH_FLOW)));

    try {
      kafkaWitronPutawayMessagePublisher.publish(getWitronPutawayMessage(), getMessageHeaders());
    } catch (ReceivingInternalException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.KAFKA_NOT_ACCESSABLE);
    }
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  private Map<String, Object> getMessageHeaders() {
    Map<String, Object> messageHeaders = new HashMap<>();
    messageHeaders.put(ReceivingConstants.JMS_EVENT_TYPE, ReceivingConstants.PUTAWAY_REQUEST);
    messageHeaders.put(
        ReceivingConstants.MESSAGE_ID_HEADER, "defb25b3-7c92-4049-b6ca-e9a9cbaabe52");
    messageHeaders.put(ReceivingConstants.CORRELATION_ID, "defb25b3-7c92-4049-b6ca-e9a9cbaabe52");
    messageHeaders.put(
        ReceivingConstants.JMS_MESSAGE_TS, ReceivingUtils.dateConversionToUTC(new Date()));
    messageHeaders.put(ReceivingConstants.JMS_REQUESTOR_ID, ReceivingConstants.RECEIVING);
    messageHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32612");
    messageHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    messageHeaders.put(ReceivingConstants.JMS_MESSAGE_VERSION, 3);

    return messageHeaders;
  }

  private WitronPutawayMessage getWitronPutawayMessage() {
    return WitronPutawayMessage.builder()
        .action("add")
        .trackingId("A32612000021053308")
        .completeTs(new Date())
        .containerType("13")
        .inventoryStatus("AVAILABLE")
        .sourceLocationId("101")
        .contents(
            Arrays.asList(
                WitronPutawayItem.builder()
                    .purchaseReferenceNumber("7358227833")
                    .purchaseReferenceLineNumber(1)
                    .baseDivisionCode("WM")
                    .financialReportingGroupCode("US")
                    .vendorNumber("480889940")
                    .quantity(200)
                    .quantityUOM("EA")
                    .packagedAsUom("ZA")
                    .ti(5)
                    .hi(4)
                    .poTypeCode(20)
                    .deptNumber(94)
                    .build()))
        .build();
  }
}
