package com.walmart.move.nim.receiving.mfc.service;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.ORIGINATOR_ID;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.UNDERSCORE;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.VERSION_1_0_0;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.mfc.model.csm.ContainerEventPayload;
import com.walmart.move.nim.receiving.mfc.model.csm.ConteinerEvent;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

public class ContainerEventService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerEventService.class);

  @Value("${container.event.data.topic:default}")
  private String csmUpdateTopic;

  @SecurePublisher private KafkaTemplate kafkaTemplate;

  private Gson gson;

  public ContainerEventService() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.MESSAGE,
      externalCall = true,
      executionFlow = "INVT-Update-CTR-Pub")
  public void publishContainerUpdate(
      Optional<ConteinerEvent> containerUpdate,
      Long deliveryNumber,
      String eventType,
      String eventSubType) {
    // converting container Object into String
    String payload =
        gson.toJson(
            ContainerEventPayload.builder().payload(Arrays.asList(containerUpdate.get())).build());

    Map<String, Object> headers = new HashMap<>();
    headers.put(EVENT_TYPE, eventType);
    headers.put(EVENT_SUB_TYPE, eventSubType);
    headers.put(ORIGINATOR_ID, RECEIVING);
    headers.put(
        KEY,
        formatEventKey(
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode(),
            deliveryNumber));
    headers.put(VERSION, VERSION_1_0_0);
    headers.put(MESSAGE_ID_HEADER_KEY, UUID.randomUUID());
    headers.put(CORRELATION_ID, UUID.randomUUID());

    String kafkaKey =
        new StringBuilder(TenantContext.getFacilityCountryCode())
            .append(ReceivingConstants.DELIM_DASH)
            .append(TenantContext.getFacilityNum())
            .append(ReceivingConstants.DELIM_DASH)
            .append(deliveryNumber)
            .toString();
    LOGGER.info("Kafka set for transaction is {}", kafkaKey);

    try {
      Message<String> message =
          KafkaHelper.buildKafkaMessage(kafkaKey, payload, csmUpdateTopic, headers);
      kafkaTemplate.send(message).get();
      LOGGER.info(
          "Secure Kafka: Successfully sent the container update list = {} to Inventory = {}",
          payload,
          csmUpdateTopic);
    } catch (Exception exception) {
      LOGGER.error("Unable to send to Inventory {}", ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, MULTIPLE_PALLET_RECEIVING_FLOW));
    }
  }

  private String formatEventKey(
      Integer facilityNum, String facilityCountryCode, Long deliveryNumber) {
    return StringUtils.joinWith(UNDERSCORE, facilityCountryCode, facilityNum, deliveryNumber);
  }
}
