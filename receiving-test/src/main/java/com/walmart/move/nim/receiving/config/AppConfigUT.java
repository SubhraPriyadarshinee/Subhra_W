package com.walmart.move.nim.receiving.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

@TestConfiguration
@PropertySource("classpath:environmentConfig/receiving-api/appConfig.properties")
@Profile("test")
@ComponentScan(basePackages = {"com.walmart.move.nim.receiving"})
@Getter
public class AppConfigUT {

  /*
   * JMS Configurations Property
   */

  @Value("${maas.username}")
  private String massUserName;

  @Value("${maas.password}")
  private String massPassword;

  @Value("${ccm.name}")
  private String ccmName;

  @Value("${receiving.test.queue}")
  private String testQueueName;

  @Value("${queue.timeout}")
  private Long queryTimeOut;

  @Value("${listener.concurrency}")
  private String listenerConcurrency;

  @Value("${jms.auth.enabled}")
  private Boolean jmsAuthEnabled;

  @Value("${jms.async.publish.enabled:true}")
  private Boolean jmsAsyncPublishEnabled;

  @Value("${pubsub.enabled}")
  private Boolean pubsubEnabled;

  /*
   * JMS Configuration Property end
   */
}
