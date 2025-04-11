package com.walmart.move.nim.receiving.endgame.message.listener;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.endgame.common.DivertAckHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class SecureDivertAckListenerSCUS {

  @Autowired private DivertAckHelper divertAckHelper;

  @KafkaListener(
      topics = "#{'${hawkeye.scan.topic}'.split(',')}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY_SCUS)
  @Timed(name = "Endgame-ScanAck", level1 = "uwms-receiving", level2 = "Endgame-ScanAck")
  @ExceptionCounted(
      name = "Endgame-ScanAck-Exception",
      level1 = "uwms-receiving",
      level2 = "Endgame-ScanAck-Exception")
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.MESSAGE, flow = "DivertAck")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    divertAckHelper.doProcess(message, kafkaHeaders);
  }
}
