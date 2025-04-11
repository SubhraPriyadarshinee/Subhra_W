package com.walmart.move.nim.receiving.endgame.message.listener;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.endgame.common.PrintingAckHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class SecurePrintingAckListenerSCUS {
  @Autowired PrintingAckHelper printingAckHelper;

  @KafkaListener(
      topics = "#{'${hawkeye.print.ack.topic}'.split(',')}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY_SCUS)
  @Timed(name = "Endgame-PrintAck", level1 = "uwms-receiving", level2 = "Endgame-PrintAck")
  @ExceptionCounted(
      name = "Endgame-PrintAck-Exception",
      level1 = "uwms-receiving",
      level2 = "Endgame-PrintAck-Exception")
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.MESSAGE, flow = "PrintAck")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    printingAckHelper.doProcess(message, kafkaHeaders);
  }
}
