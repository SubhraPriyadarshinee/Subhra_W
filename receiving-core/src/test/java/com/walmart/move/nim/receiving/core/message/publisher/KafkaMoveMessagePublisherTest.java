package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.MoveEvent.CANCEL;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.APP_NAME_VALUE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REQUEST_ORIGINATOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.move.CancelMove;
import com.walmart.move.nim.receiving.core.model.move.MoveData;
import com.walmart.move.nim.receiving.core.model.move.MoveInfo;
import com.walmart.move.nim.receiving.core.model.move.MoveType;
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

public class KafkaMoveMessagePublisherTest {

  @Mock KafkaTemplate secureKafkaTemplate;
  @Mock private KafkaConfig kafkaConfig;
  @Mock RapidRelayerService rapidRelayerService;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private KafkaMoveMessagePublisher kafkaMoveMessagePublisher;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(1234);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("aafa2fcc-d299-4663-aa64-ba6f7970463");
  }

  @Test
  public void testPublish() {

    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);

    kafkaMoveMessagePublisher.publish(getMoveInfoMock(), getMockHeaders());

    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_outbox() throws ReceivingException {

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);

    kafkaMoveMessagePublisher.publish(getMoveInfoMock(), getMockHeaders());

    verify(rapidRelayerService, times(1)).produceMessage(any(), anyString(), anyString(), anyMap());
  }

  @Test
  public void testPublishOnSecureKafka() {
    ReflectionTestUtils.setField(
        kafkaMoveMessagePublisher, "secureKafkaTemplate", secureKafkaTemplate);

    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);
    when(kafkaConfig.isGDMOnSecureKafka()).thenReturn(true);

    kafkaMoveMessagePublisher.publish(getMoveInfoMock(), getMockHeaders());

    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_error() {

    try {

      when(secureKafkaTemplate.send(any(Message.class))).thenThrow(new NullPointerException());

      kafkaMoveMessagePublisher.publish(getMoveInfoMock(), getMockHeaders());

      verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.KAFKA_NOT_ACCESSABLE);
      assertEquals(e.getDescription(), "Unable to access Kafka. Flow= Move info flow");
    }
  }

  @Test
  public void testPublishCanceMove() {

    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);

    kafkaMoveMessagePublisher.publishCancelMove(getCancelMove(), getMockHeaders());

    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublishCancelMove_error() {

    try {

      when(secureKafkaTemplate.send(any(Message.class))).thenThrow(new NullPointerException());

      kafkaMoveMessagePublisher.publishCancelMove(getCancelMove(), getMockHeaders());

      verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.KAFKA_NOT_ACCESSABLE);
      assertEquals(e.getDescription(), "Unable to access Kafka. Flow= Move info flow");
    }
  }

  @Test
  public void testPublishForAutomatedDC() {

    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);

    kafkaMoveMessagePublisher.publish(getMoveDataMock(), getMockHeaders());

    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testUnableToPublish() {

    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);

    kafkaMoveMessagePublisher.publish(new ContainerDTO(), getMockHeaders());

    verify(secureKafkaTemplate, times(0)).send(any(Message.class));
  }

  private static MoveInfo getMoveInfoMock() {
    MoveInfo moveInfo = new MoveInfo();
    moveInfo.setMoveQty(234);
    moveInfo.setMoveEvent("onInitiate");
    moveInfo.setMoveType(new MoveType(5, "Putaway Move"));
    moveInfo.setToLocation("DPF031T");
    moveInfo.setContainerTag("502-AAB");
    moveInfo.setVnpkQty(3);
    moveInfo.setWhpkQty(3);
    moveInfo.setMoveQtyUOM("EA");
    moveInfo.setPriority(40);
    return moveInfo;
  }

  static Map<String, Object> getMockHeaders() {
    Map<String, Object> mockHttpHeaders = new HashMap<>();
    mockHttpHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32987");
    mockHttpHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    mockHttpHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    mockHttpHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    mockHttpHeaders.put(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    mockHttpHeaders.put(ReceivingConstants.CONTENT_TYPE, "application/json");
    mockHttpHeaders.put(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    return mockHttpHeaders;
  }

  static CancelMove getCancelMove() {
    return CancelMove.builder()
        .containerTag("502-AAB")
        .moveEvent(CANCEL.getMoveEvent())
        .moveType(MoveType.builder().code(Integer.parseInt("5")).desc("Putaway").build())
        .build();
  }

  private static MoveData getMoveDataMock() {
    MoveData moveInfo = new MoveData();
    moveInfo.setMoveQty(234);
    moveInfo.setMoveEvent("onInitiate");
    moveInfo.setMoveType(new MoveType(5, "Putaway Move"));
    moveInfo.setToLocation("DPF031T");
    moveInfo.setContainerTag("502-AAB");
    moveInfo.setMoveQtyUOM("EA");
    moveInfo.setPriority(40);
    return moveInfo;
  }
}
