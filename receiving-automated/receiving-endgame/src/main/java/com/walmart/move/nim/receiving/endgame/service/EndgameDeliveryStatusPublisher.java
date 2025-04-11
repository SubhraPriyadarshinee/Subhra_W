package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.framework.expression.StandardExpressionEvaluator;
import com.walmart.move.nim.receiving.core.framework.expression.TenantPlaceholder;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.DeliveryCompletedDTO;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

public class EndgameDeliveryStatusPublisher implements MessagePublisher<DeliveryInfo> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(EndgameDeliveryStatusPublisher.class);

  @Autowired protected JmsPublisher jmsPublisher;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  protected DeliveryMetaDataService deliveryMetaDataService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private KafkaConfig kafkaConfig;
  @SecurePublisher private KafkaTemplate secureTemplate;
  @ManagedConfiguration private MaasTopics maasTopics;

  @Value("${delivery.status.topic}")
  protected String deliveryStatusTopic;

  protected Gson gson;

  @Autowired private IOutboxPublisherService outboxPublisherService;

  public EndgameDeliveryStatusPublisher() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public void publish(DeliveryInfo deliveryInfo, Map<String, Object> messageHeader) {
    DeliveryMetaData deliveryMetaData = populateDeliveryMetaInfo(deliveryInfo);
    publishMessage(deliveryInfo, messageHeader);
    updateUnloadingCompleteTs(deliveryMetaData, deliveryInfo);
    publishMessageToKafka(deliveryInfo, deliveryMetaData);
  }

  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.MESSAGE,
      executionFlow = "HWK-Clear-TCL",
      externalCall = true)
  private void publishMessageToKafka(DeliveryInfo deliveryInfo, DeliveryMetaData deliveryMetaData) {

    if (!(StringUtils.equalsAnyIgnoreCase(
        deliveryInfo.getDeliveryStatus(), DeliveryStatus.UNLOADING_COMPLETE.name()))) {
      LOGGER.warn(
          "Delivery [status={}]. So not sending message to hawkeye",
          deliveryInfo.getDeliveryStatus());
      return;
    }

    DeliveryCompletedDTO deliveryCompletedDTO =
        DeliveryCompletedDTO.builder()
            .deliveryStatus(deliveryInfo.getDeliveryStatus())
            .deliveryNumber(deliveryInfo.getDeliveryNumber())
            .build();

    String payload = gson.toJson(deliveryCompletedDTO);

    LOGGER.info(
        "Sending the deliveryComplete event for [deliveryNumber={}] to Hawkeye [payload={}]",
        deliveryCompletedDTO.getDeliveryNumber(),
        payload);

    try {
      Integer facilityNum = TenantContext.getFacilityNum();
      String key = String.valueOf(deliveryInfo.getDeliveryNumber());
      if (tenantSpecificConfigReader.isUnloadCompleteOutboxKafkaPublishEnabled(facilityNum)) {
        Map<String, Object> headers =
            EndGameUtils.getHawkeyeHeaderMap(
                deliveryStatusTopic,
                key,
                facilityNum,
                TenantContext.getFacilityCountryCode(),
                UUID.randomUUID().toString());
        outboxPublisherService.publishToKafka(
            payload,
            headers,
            deliveryStatusTopic,
            facilityNum,
            TenantContext.getFacilityCountryCode(),
            key);
      } else {
        Message<String> message =
            EndGameUtils.setDefaultHawkeyeHeaders(
                payload,
                StandardExpressionEvaluator.EVALUATOR.evaluate(
                    deliveryStatusTopic, new TenantPlaceholder(facilityNum)),
                deliveryInfo.getUserId(),
                EndGameUtils.createDeliveryCloseHeaders(deliveryInfo),
                key);
        secureTemplate.send(message);
      }
      LOGGER.info(
          "Secure Kafka successfully send the deliveryComplete event for [deliveryNumber={}] to Hawkeye [payload={}]",
          deliveryCompletedDTO.getDeliveryNumber(),
          payload);
    } catch (Exception exception) {
      LOGGER.error("Unable to send to hawkeye [error={}]", ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(
              ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
              EndgameConstants.DELIVERY_COMPLETED_FLOW));
    }
  }

  protected void updateUnloadingCompleteTs(
      DeliveryMetaData deliveryMetaData, DeliveryInfo deliveryInfo) {

    if (Objects.isNull(deliveryInfo.getDoorNumber())) {
      LOGGER.warn(
          "Door Number is not present as it might already unloaded on [deliveryNumber={}]",
          deliveryInfo.getDeliveryNumber());
      return;
    }

    deliveryMetaData.setUnloadingCompleteDate(new Date());
    deliveryMetaDataService.save(deliveryMetaData);
  }

  protected DeliveryMetaData populateDeliveryMetaInfo(DeliveryInfo deliveryInfo) {

    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(deliveryInfo.getDeliveryNumber()))
            .orElse(new DeliveryMetaData());

    LOGGER.info(
        "Got meta data info for [deliveryNumber={}] before publishing to GDM/YMS [doorNumber={}] and [trailerNumber={}]",
        deliveryInfo.getDeliveryNumber(),
        deliveryMetaData.getDoorNumber(),
        deliveryMetaData.getTrailerNumber());

    String doorNumber =
        StringUtils.equalsIgnoreCase(deliveryInfo.getDeliveryStatus(), DeliveryStatus.OPEN.name())
            ? deliveryMetaData.getDoorNumber()
            : deliveryInfo.getDoorNumber();
    LOGGER.info("DoorNumber to be posted [doorNumber={}]", doorNumber);

    deliveryInfo.setDoorNumber(doorNumber);
    deliveryInfo.setTrailerNumber(deliveryMetaData.getTrailerNumber());
    return deliveryMetaData;
  }

  /**
   * This method is responsible for publishing {@link DeliveryInfo} to Maas
   * Topic:TOPIC/RECEIVE/DELIVERYSTATUS
   *
   * @param deliveryInfo
   * @param messageHeader
   */
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.MESSAGE,
      externalCall = true,
      executionFlow = "HWK-Complete-Delivery")
  public void publishMessage(DeliveryInfo deliveryInfo, Map<String, Object> messageHeader) {

    // Dont send event to YMS/GDM if doorNumber is not coming from UI. Hence,
    // ignoring the
    // osdr_event
    if (!DeliveryStatus.COMPLETE.name().equals(deliveryInfo.getDeliveryStatus())
        && Objects.isNull(deliveryInfo.getDoorNumber())
        && !(deliveryInfo instanceof OsdrSummary)) {
      LOGGER.warn(
          "Not publishing to GDM or YMS as no door is associate to [deliveryNumber={}]",
          deliveryInfo.getDeliveryNumber());
      return;
    }
    String payload = gson.toJson(deliveryInfo);
    if (tenantSpecificConfigReader.isOutboxEnabledForDeliveryEvents()) {
      String correlationId = UUID.randomUUID().toString();
      LOGGER.info(
          "Publishing the Delivery Status Details to Outbox with correlation id {} ",
          correlationId);
      String facilityCountryCode = getFacilityCountryCode();
      Integer facilityNum = TenantContext.getFacilityNum();
      messageHeader.put(TENENT_COUNTRY_CODE, facilityCountryCode);
      messageHeader.put(TENENT_FACLITYNUM, facilityNum);
      outboxPublisherService.publishToHTTP(
          correlationId,
          payload,
          messageHeader,
          tenantSpecificConfigReader.getOutboxDeliveryEventServiceName(),
          facilityNum,
          facilityCountryCode,
          Collections.emptyMap());
    } else {
      ReceivingJMSEvent receivingJMSEvent = new ReceivingJMSEvent(messageHeader, payload);
      jmsPublisher.publish(maasTopics.getPubDeliveryStatusTopic(), receivingJMSEvent, Boolean.TRUE);
    }
  }
}
