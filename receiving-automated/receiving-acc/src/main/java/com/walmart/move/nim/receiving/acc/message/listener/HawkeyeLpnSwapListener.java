package com.walmart.move.nim.receiving.acc.message.listener;

import com.walmart.move.nim.receiving.acc.model.HawkeyeLpnPayload;
import com.walmart.move.nim.receiving.acc.service.HawkeyeLpnSwapService;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class HawkeyeLpnSwapListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(HawkeyeLpnSwapListener.class);

  @Autowired private ContainerService containerService;
  @Autowired private HawkeyeLpnSwapService hawkeyeLpnSwapService;

  @KafkaListener(
      topics = "${acc.pa.lpn.swap.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY,
      groupId = "#{'${kafka.consumer.groupid:receiving-consumer}'.concat('-hawkeye-lpn-swap')}")
  @Timed(name = "AccPaLPNSwap", level1 = "uwms-receiving", level2 = "AccPaLPNSwap")
  @ExceptionCounted(
      name = "AccPaLPNSwap-Exception",
      level1 = "uwms-receiving",
      level2 = "AccPaLPNSwap-Exception")
  @TimeTracing(component = AppComponent.ACC, type = Type.MESSAGE, flow = "AccPaLPNSwap")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    LOGGER.info("ACC PA LPN Swap payload:{}", message);
    processListener(message);
    TenantContext.clear();
  }

  public void processListener(String message) {
    if (StringUtils.isEmpty(message)) {
      LOGGER.error("ACC PA LPN Swap payload is null or empty");
      return;
    }
    hawkeyeLpnSwapService.swapAndProcessLpn(
        JacksonParser.convertJsonToObject(message, HawkeyeLpnPayload.class));
  }
}
