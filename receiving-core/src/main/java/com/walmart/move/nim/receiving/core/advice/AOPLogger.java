package com.walmart.move.nim.receiving.core.advice;

import com.google.gson.Gson;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AOPLogger {
  private static final Logger LOGGER = LoggerFactory.getLogger(AOPLogger.class);
  private static final String CORE_API =
      "execution (* com.walmart.move.nim.receiving.controller.*.*(..))";
  private static final String COMPONENT_API =
      "execution (* com.walmart.move.nim.receiving.*.controller.*.*(..))";
  private static final String STRING_OR = " || ";
  private static final String CONTROLLER_POINTCUT = CORE_API + STRING_OR + COMPONENT_API;

  @Autowired private Gson gson;

  public AOPLogger() {
    super();
    LOGGER.info("AOPLogger loaded...");
  }

  @Before(CONTROLLER_POINTCUT)
  public void beforeControllerMethod(JoinPoint joinPoint) {

    LOGGER.info(
        "Entering into  [methodName={}]  with [requests={}]",
        joinPoint.getSignature().getName(),
        gson.toJson(joinPoint.getArgs()));
  }

  @AfterReturning(value = CONTROLLER_POINTCUT, returning = "val")
  public void afterReturningControllerMethod(JoinPoint joinPoint, Object val) {
    LOGGER.info(
        "Returning from [methodName={}] [responses={}]",
        joinPoint.getSignature().getName(),
        gson.toJson(val));
  }
}
