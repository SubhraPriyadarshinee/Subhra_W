package com.walmart.move.nim.receiving.sib.config;

import java.util.concurrent.Executor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration to process spring events asynchronously
 *
 * @see com.walmart.move.nim.receiving.sib.service.EventPublisherService
 * @see com.walmart.move.nim.receiving.sib.messsage.listener.ContainerEventListener
 */
@ConditionalOnExpression("${enable.sib.app:false}")
@Configuration
public class AsynchronousSpringEventsConfig {

  @Bean(name = "applicationEventMulticaster")
  public ApplicationEventMulticaster simpleApplicationEventMulticaster() {
    SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();

    eventMulticaster.setTaskExecutor(getAsyncExecutor());
    return eventMulticaster;
  }

  public Executor getAsyncExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // TODO: Thread Number needs to be configurable in CCM
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(5);
    executor.setThreadNamePrefix("async-event-pool-");
    executor.initialize();
    return executor;
  }
}
