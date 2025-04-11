package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.core.message.listener.DeliveryUpdateListener;
import com.walmart.move.nim.receiving.core.message.listener.InventoryAdjustmentListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.util.ErrorHandler;

@ConditionalOnExpression("${enable.jms.messages.listen:true}")
@Configuration
@Profile("!test")
public class JMSBeansConfig {

  @Value("${jms.autoconnect.enable:true}")
  private Boolean isAutoConnectOnStart;

  // This will enable @JmsListener to function
  @Bean("receivingJMSListener")
  public DefaultJmsListenerContainerFactory queueListenerContainerFactory(
      @Value("${listener.concurrency}") String listenerConcurrency,
      CachingConnectionFactory cachingConnectionFactory,
      ErrorHandler errorHandler) {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(cachingConnectionFactory);
    factory.setConcurrency(listenerConcurrency);
    factory.setErrorHandler(errorHandler);
    factory.setAutoStartup(isAutoConnectOnStart);
    return factory;
  }

  @Bean
  @ConditionalOnExpression("${enable.prelabel.generation:false}")
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public DeliveryUpdateListener deliveryUpdateListener() {
    return new DeliveryUpdateListener();
  }

  @Bean
  @ConditionalOnExpression("${enable.jms.messages.listen:true}")
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public InventoryAdjustmentListener inventoryAdjustmentListener() {
    return new InventoryAdjustmentListener();
  }
}
