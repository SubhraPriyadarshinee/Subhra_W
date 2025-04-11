package com.walmart.move.nim.receiving.core.handler;

import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.lang.reflect.Method;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

public class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(AsyncExceptionHandler.class);

  @Override
  public void handleUncaughtException(Throwable ex, Method method, Object... params) {
    log.error(
        "Async Error occurs on Method = {}, having param={} and exception= {}",
        method.getName(),
        params,
        ExceptionUtils.getStackTrace(ex));
    TenantContext.clear();
  }
}
