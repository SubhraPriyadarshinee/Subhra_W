package com.walmart.move.nim.receiving.core.common;

import static java.util.Objects.nonNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.UUID;
import javax.jms.JMSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JMSSyncPublisher {
  private static final Logger log = LoggerFactory.getLogger(JMSSyncPublisher.class);

  @Autowired private JmsTemplate jmsQueueTemplate;

  @Autowired private JmsTemplate jmsTopicTemplate;

  @ManagedConfiguration private AppConfig appConfig;

  private Gson gson;

  public JMSSyncPublisher() {
    gson = new Gson();
  }

  @Timed(name = "jmsPublishTimed", level1 = "uwms-receiving", level2 = "jmsPublisher")
  @ExceptionCounted(
      name = "jmsPublishExceptionCount",
      level1 = "uwms-receiving",
      level2 = "jmsPublisher")
  public void publishInternal(String queueName, ReceivingJMSEvent messageTemplate) {
    if (queueName != null) {
      MessagePostProcessor messagePostProcessor = setHeaders(messageTemplate);
      if (appConfig.getPubsubEnabled() != null && appConfig.getPubsubEnabled()) {
        if (StringUtils.startsWithIgnoreCase(queueName, ReceivingConstants.JMS_QUEUE_PREFIX)) {
          jmsQueueTemplate.convertAndSend(
              queueName, messageTemplate.getMessageBody(), messagePostProcessor);
        } else {
          jmsTopicTemplate.convertAndSend(
              queueName, messageTemplate.getMessageBody(), messagePostProcessor);
        }
        log.info(
            "Message Published queueName :{} CorrelationId :{} messageBody :{}",
            queueName,
            messageTemplate.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
            messageTemplate.getMessageBody());
        log.info(
            "Message Published queueName :{} CorrelationId :{} messageHeader :{}",
            queueName,
            messageTemplate.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
            messageTemplate.getHeaders());
      } else {
        log.info("pub-sub is disabled");
      }

    } else {
      log.error("Unable to publish because of TOPIC/QUEUE name is not existing");
    }
  }

  /**
   * This method will create the headers for jmsTemplate
   *
   * @param messageTemplate
   * @return
   */
  private MessagePostProcessor setHeaders(ReceivingJMSEvent messageTemplate) {
    return m -> {
      m.setJMSCorrelationID(UUID.randomUUID().toString());
      m.setJMSExpiration(appConfig.getQueueTimeOut());
      m.setJMSMessageID(UUID.randomUUID().toString());
      messageTemplate
          .getHeaders()
          .forEach(
              (k, v) -> {
                try {
                  String key = k.replace('-', '_');
                  if (k.equals(ReceivingConstants.TENENT_FACLITYNUM)) {
                    m.setIntProperty(key, Integer.parseInt(v.toString()));
                  } else if (nonNull(v)) {
                    m.setStringProperty(key, v.toString());
                  }
                } catch (JMSException e) {
                  log.error("Unable to set meta data ", e);
                }
              });
      return m;
    };
  }
}
