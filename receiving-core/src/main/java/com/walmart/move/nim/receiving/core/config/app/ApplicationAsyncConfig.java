package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.move.nim.receiving.core.handler.AsyncExceptionHandler;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ApplicationAsyncConfig implements AsyncConfigurer {
  /**
   * This will create an executer service to process asynchronous request processing
   *
   * @return Executor
   */
  // TODO: Thread Number needs  to  be configurable in CCM
  @Value("${async.executor.core.pool.size:20}")
  private int asyncExecutorCorePoolSize;

  @Value("${async.executor.max.pool.size:25}")
  private int asyncExecutorMaxPoolSize;

  @Override
  public Executor getAsyncExecutor() {

    final TaskDecorator mdcDecorator =
        (runnable -> {
          return () -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            try {
              if (java.util.Objects.isNull(contextMap)) {
                contextMap = new HashMap<>();
                contextMap.put(
                    ReceivingConstants.TENENT_FACLITYNUM, ReceivingConstants.DEFAULT_MDC);
                contextMap.put(
                    ReceivingConstants.TENENT_COUNTRY_CODE, ReceivingConstants.DEFAULT_MDC);
                contextMap.put(
                    ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.DEFAULT_MDC_USER);
                contextMap.put(
                    ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
              }
              // Right now: @Async thread context !
              // (Restore the Web thread context's MDC data)
              MDC.setContextMap(contextMap);
              runnable.run();
            } finally {
              MDC.clear();
            }
          };
        });

    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(asyncExecutorCorePoolSize);
    executor.setMaxPoolSize(asyncExecutorMaxPoolSize);
    executor.setTaskDecorator(mdcDecorator);
    executor.setThreadNamePrefix("async-pool-");
    executor.initialize();
    return executor;
  }

  /**
   * Exception Handler for Async request processing
   *
   * @see AsyncExceptionHandler
   * @return AsyncUncaughtExceptionHandler
   */
  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new AsyncExceptionHandler();
  }
}
