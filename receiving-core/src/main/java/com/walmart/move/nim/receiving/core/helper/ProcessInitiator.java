package com.walmart.move.nim.receiving.core.helper;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.framework.message.processor.DefaultExecutor;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class ProcessInitiator implements ApplicationContextAware {

  private final Logger LOGGER = LoggerFactory.getLogger(ProcessInitiator.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @SecurePublisher private KafkaTemplate kafkaTemplate;

  @Value("${receiving.self.process.topic:default}")
  private String receivingSelfLoopTopic;

  @Value("${enable.self.loop.processing:false}")
  private Boolean selfLoopEnabled;

  private ApplicationContext applicationContext;

  private Gson gson;

  public ProcessInitiator() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  public ProcessExecutor loadProcessExecutor(ReceivingEvent receivingEvent) {
    if (Objects.nonNull(receivingEvent.getProcessor())) {
      try {
        return (ProcessExecutor) this.applicationContext.getBean(receivingEvent.getProcessor());
      } catch (Exception exception) {
        LOGGER.error(
            "Unable to load the requested processor = {} and hence falling back to default ",
            receivingEvent.getProcessor(),
            exception);
      }
    }

    if (Objects.nonNull(receivingEvent.getName())) {
      LOGGER.info("No processor found in payload and hence, looking into ccm config");
      return tenantSpecificConfigReader.getConfiguredInstance(
          TenantContext.getFacilityNum().toString(),
          receivingEvent.getName(),
          ProcessExecutor.class);
    }
    LOGGER.warn("No Process Executor found and hence, defaulting to DefaultProcessExecutor ");
    return this.applicationContext.getBean(DefaultExecutor.class);
  }

  public void initiateProcess(ReceivingEvent receivingEvent, Map<String, Object> headers) {
    ProcessExecutor processExecutor = loadProcessExecutor(receivingEvent);
    if (selfLoopEnabled && processExecutor.isAsync()) {
      publishReceivingEvent(receivingEvent, headers);
      return;
    }
    processExecutor.doExecute(receivingEvent);
  }

  private void publishReceivingEvent(ReceivingEvent receivingEvent, Map<String, Object> headers) {
    Message<String> message =
        KafkaHelper.buildKafkaMessage(
            receivingEvent.getKey(), gson.toJson(receivingEvent), receivingSelfLoopTopic, headers);
    try {
      kafkaTemplate.send(message).get();
      LOGGER.info(
          "Successfully published the receiving-event = {} to topic = {}",
          message,
          receivingSelfLoopTopic);
    } catch (Exception exception) {
      LOGGER.error(
          "Error in publishing receiving-event = {} with exception = {}",
          receivingEvent,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, "Receiving-Event-Internal-Flow"));
    }
  }
}
