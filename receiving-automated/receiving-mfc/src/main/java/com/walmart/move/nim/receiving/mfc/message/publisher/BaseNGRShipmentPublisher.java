package com.walmart.move.nim.receiving.mfc.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.NGR_SHIPMENT_ARRIVAL_EVENT_PUBLISH_FLOW;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.mfc.model.ngr.NGRShipment;
import java.util.Date;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

/**
 * This class promotes code reuse, maintainability, and separation of concerns. It provides a
 * flexible foundation for implementing different types of NGR shipment publisher while ensuring
 * consistency in behavior and structure.
 */
public abstract class BaseNGRShipmentPublisher implements NGRShipmentPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(BaseNGRShipmentPublisher.class);
  protected final Gson gson;

  public BaseNGRShipmentPublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @SecurePublisher private KafkaTemplate kafkaTemplate;

  /**
   * Publishes the given NGR shipment message to Kafka. Prepares a Kafka message, sends it using
   * KafkaTemplate, and performs post-processing
   *
   * @param message The NGR shipment message to be published.
   * @throws ReceivingInternalException if an error occurs while publishing the message to Kafka.
   */
  @Override
  public void publish(NGRShipment message) {
    try {
      Message<String> kafkaMessage = prepareKafkaMessage(message);
      kafkaTemplate.send(kafkaMessage);
      performPostProcessing(message, kafkaMessage);
    } catch (Exception exception) {
      LOGGER.error(
          "Unable to send message to Kafka. Exception - {}, Payload - {}",
          ExceptionUtils.getStackTrace(exception),
          gson.toJson(message));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, NGR_SHIPMENT_ARRIVAL_EVENT_PUBLISH_FLOW));
    }
  }

  /**
   * Abstract method to prepare a Kafka message for the given NGR shipment payload. Subclasses must
   * implement this method to define how NGR shipment messages are converted into Kafka messages. In
   * this way we can create different pauload from the same NGRShipment message
   *
   * @param payload The NGR shipment payload to be included in the Kafka message.
   * @return The Kafka message containing the serialized payload.
   */
  protected abstract Message<String> prepareKafkaMessage(NGRShipment payload);

  /**
   * Abstract method to perform post-processing tasks after publishing a NGR shipment message to
   * Kafka. Subclasses can implement this method to define any additional processing steps needed
   * after successfully publishing the message; such as reporting to corresponding stakeholders or
   * systems like ELK stack.
   *
   * @param ngrShipment The NGR shipment that was received.
   * @param message The Kafka message that was sent.
   */
  protected abstract void performPostProcessing(NGRShipment ngrShipment, Message<String> message);
}
