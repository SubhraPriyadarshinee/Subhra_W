/** */
package com.walmart.move.nim.receiving.config;

import com.mockrunner.jms.ConfigurationManager;
import com.mockrunner.jms.DestinationManager;
import com.mockrunner.mock.jms.MockQueueConnectionFactory;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JMS Bean configurations
 *
 * @author sitakant
 */
@Configuration
@Import(AppConfigUT.class)
@EnableJms
@EnableScheduling
@Profile("test")
public class JMSAppConfigUT {

  @Autowired private AppConfigUT jmsConfig;

  @Bean
  public ConnectionFactory cachingConnectionFactory() throws JMSException {

    ConnectionFactory connectionFactory =
        new MockQueueConnectionFactory(new DestinationManager(), new ConfigurationManager());
    CachingConnectionFactory cachingConnectionFactory =
        new CachingConnectionFactory(connectionFactory);

    return cachingConnectionFactory;
  }

  @Bean
  public JmsTemplate jmsTemplate() throws JMSException {
    return new JmsTemplate(cachingConnectionFactory());
  }

  // This will enable @JmsListener to function
  @Bean("receivingJMSListener")
  public DefaultJmsListenerContainerFactory topicListenerContainerFactory() throws JMSException {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(cachingConnectionFactory());
    factory.setConcurrency(jmsConfig.getListenerConcurrency());
    return factory;
  }
}
