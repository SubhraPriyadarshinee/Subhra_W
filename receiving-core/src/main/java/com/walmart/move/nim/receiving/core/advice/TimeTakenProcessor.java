package com.walmart.move.nim.receiving.core.advice;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * * Annotation Processor for {@link TimeTracing}. This directly depends on {@link
 * EnableTimeTraceCondition}
 *
 * @author sitakant
 */
@Component
@Aspect
@Order(Integer.MAX_VALUE)
@Conditional(EnableTimeTraceCondition.class)
public class TimeTakenProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimeTakenProcessor.class);
  public static final String BRACKET_SQ_OPEN = "[";
  public static final String BRACKET_SQ_CLOSE = "]";
  public static final String OPERATOR_EQUALS = "=";
  public static final String DELIM_COMMA = ",";
  public static final String NAME_COMPONENT = "component";
  public static final String NAME_TYPE = "type";
  public static final String NAME_EXTERNAL_CALL = "isExternalCall";

  @Around("@annotation(TimeTracing)")
  public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
    long startTime = System.currentTimeMillis();

    MethodSignature signature = (MethodSignature) joinPoint.getSignature();

    TimeTracing timeTracing = signature.getMethod().getAnnotation(TimeTracing.class);

    Object proceed = joinPoint.proceed();

    StringBuilder loggerBuilder = new StringBuilder(BRACKET_SQ_OPEN);

    loggerBuilder
        .append(NAME_COMPONENT)
        .append(OPERATOR_EQUALS)
        .append(timeTracing.component().getComponent())
        .append(DELIM_COMMA)
        .append(ifNullRemove(timeTracing.flow(), "flow"))
        .append(ifNullRemove(timeTracing.executionFlow(), "executionFlow"))
        .append(NAME_TYPE)
        .append(OPERATOR_EQUALS)
        .append(timeTracing.type())
        .append(DELIM_COMMA)
        .append(NAME_EXTERNAL_CALL)
        .append(OPERATOR_EQUALS)
        .append(timeTracing.externalCall())
        .append(BRACKET_SQ_CLOSE);

    LOGGER.info(
        "Time taken for {} is {} ms",
        loggerBuilder.toString(),
        System.currentTimeMillis() - startTime);

    return proceed;
  }

  private String ifNullRemove(String object, String param) {
    StringBuilder resultBuilder = new StringBuilder();
    if (!StringUtils.isEmpty(object)) {
      resultBuilder.append(param).append(OPERATOR_EQUALS).append(object).append(DELIM_COMMA);
    }
    return resultBuilder.toString();
  }
}
