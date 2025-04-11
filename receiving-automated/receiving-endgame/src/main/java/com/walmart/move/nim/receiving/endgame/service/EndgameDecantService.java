package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.framework.expression.StandardExpressionEvaluator;
import com.walmart.move.nim.receiving.core.framework.expression.TenantPlaceholder;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.model.DimensionPayload;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

public class EndgameDecantService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndgameDecantService.class);

  @Value("${decant.dimension.topic}")
  private String decantDimensionTopic;

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;
  @Autowired private Gson gson;
  @ManagedConfiguration private KafkaConfig kafkaConfig;

  @Autowired @Lazy private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired @Lazy private IOutboxPublisherService outboxPublisherService;

  public void publish(DimensionPayload dimensionPayload) {
    Object kafkaKey = dimensionPayload.getItemNumber();
    String payload = gson.toJson(dimensionPayload);
    if (tenantSpecificConfigReader.isOutboxEnabledForVendorDimensionEvents()) {
      String correlationId = UUID.randomUUID().toString();
      LOGGER.info(
          "Publishing the Vendor Dimension Details to Outbox with correlation id {} ",
          correlationId);
      String facilityCountryCode = getFacilityCountryCode();
      Integer facilityNum = TenantContext.getFacilityNum();
      Map<String, Object> messageHeader = new HashMap<>(ReceivingUtils.getHeaders());
      messageHeader.put(TENENT_COUNTRY_CODE, facilityCountryCode);
      messageHeader.put(TENENT_FACLITYNUM, facilityNum);
      outboxPublisherService.publishToHTTP(
          correlationId,
          payload,
          messageHeader,
          tenantSpecificConfigReader.getOutboxDecantVendorDimensionEventServiceName(),
          facilityNum,
          facilityCountryCode,
          Collections.emptyMap());
    } else {
      try {
        Message<String> message =
            KafkaHelper.buildKafkaMessage(
                kafkaKey,
                payload,
                StandardExpressionEvaluator.EVALUATOR.evaluate(
                    decantDimensionTopic, new TenantPlaceholder(TenantContext.getFacilityNum())));

        secureKafkaTemplate.send(message);
      } catch (Exception exception) {
        LOGGER.error(
            "Unable to send to Decant [error={}]", ExceptionUtils.getStackTrace(exception));
        throw new ReceivingInternalException(
            ExceptionCodes.KAFKA_NOT_ACCESSABLE,
            String.format(
                ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
                EndgameConstants.VENDOR_PACK_DIMENSIONS_FLOW));
      }
    }
  }
}
