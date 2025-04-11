package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.Configuration;
import io.strati.configuration.ConfigurationService;
import io.strati.configuration.context.ConfigurationContext;
import io.strati.configuration.listener.ChangeListenerAdapter;
import io.strati.configuration.listener.ChangeLog;
import java.util.List;
import java.util.Objects;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ControlBusExecutor extends ChangeListenerAdapter {

  private static final String KAFKA_CONSUMER_CONTROL_CONFIG = "kafka_consumer_control_config";

  @Autowired private ConfigurationService configurationService;

  @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  public ControlBusExecutor() {
    super();
  }

  @PostConstruct
  public void init() {

    Configuration configuration =
        configurationService.getConfiguration(KAFKA_CONSUMER_CONTROL_CONFIG);
    if (!Objects.isNull(configuration)) {
      configuration.addConfigurationListener(this);
    } else {
      log.warn("Configuration not found for {}", KAFKA_CONSUMER_CONTROL_CONFIG);
    }
  }

  /**
   * When the CCM value changes for the kafka_consumer_control_config the changed values will be
   * fetched by the following method. It looks for the ccm config as commnad and if there are any
   * values changed for the config then it will trigger the start/stop listener. We will look for
   * the kafka consumer group from the kafka consumer registry and set start/stop based on the ccm
   * vale being passed. i.e. instructionDownloadListener-amb-qa-cell000:stop - This is the CCM value
   * for command key. instructionDownloadListener-amb-qa-cell000 - Consumer group and we split it by
   * : to get the start/stop. If the consumer group is stopped then the specific consumer will be
   * detached from the kafka topic. The topic still receives the messages. When we change the
   * command as start (instructionDownloadListener-amb-qa-cell000:start) then the consumer bean will
   * be active and connected to the kafka topic.
   *
   * @param configName
   * @param changeLogs
   * @param context
   */
  @Override
  public void configurationChanged(
      String configName, List<ChangeLog> changeLogs, ConfigurationContext context) {
    log.info("Change in event config ccm {} ", configName);
    for (ChangeLog changeLog : changeLogs) {

      String newValue = changeLog.getNewValue();
      log.info(
          "ChangeLog  {} - {}- {}",
          changeLog.getKey(),
          changeLog.getOldValue(),
          changeLog.getNewValue());

      if ("command".equals(changeLog.getKey()) && StringUtils.isNotBlank(newValue)) {

        try {
          startOrStopListener(newValue);
        } catch (Exception e) {
          log.error("Error in executing command {} control bus", newValue, e);
        }
      }
    }
  }

  /**
   * Start or Stop kafka listener
   *
   * @param newValue
   */
  private void startOrStopListener(String newValue) {
    String consumerGroupId = newValue.split(":")[0];
    String flag = newValue.split(":")[1];
    log.info("Consumer group id={}, flag={}", consumerGroupId, flag);
    MessageListenerContainer messageListenerContainer =
        kafkaListenerEndpointRegistry
            .getAllListenerContainers()
            .stream()
            .filter(
                listenerContainer ->
                    listenerContainer.getGroupId().equalsIgnoreCase(consumerGroupId))
            .findFirst()
            .orElse(null);
    if (Objects.isNull(messageListenerContainer)) {
      log.info("Kafka listener not exist for consumer group id={}", consumerGroupId);
      return;
    }
    if ("start".equalsIgnoreCase(flag) && !messageListenerContainer.isRunning()) {
      messageListenerContainer.start();
      log.info("Kafka listener started for consumer group id={}", consumerGroupId);
    } else if ("stop".equalsIgnoreCase(flag) && messageListenerContainer.isRunning()) {
      messageListenerContainer.stop();
      log.info("Kafka listener stopped for consumer group id={}", consumerGroupId);
    }
  }
}
