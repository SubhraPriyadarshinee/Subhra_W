package com.walmart.move.nim.receiving.core.advice;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * * Condition to enable or disable the TimeTracing
 *
 * @see TimeTracing
 * @author sitakant
 */
public class EnableTimeTraceCondition implements Condition {

  /**
   * * This method will look for enable.time.tracing.log value in the spring container, if enable
   * then this will return {@link Boolean#TRUE} else {@link Boolean#FALSE}
   *
   * @param context
   * @param annotatedTypeMetadata
   * @return Boolean
   */
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata annotatedTypeMetadata) {
    // Looking for properties details
    String value = getValue(context, "enable.time.tracing.log");
    return Boolean.valueOf(value);
  }

  private String getValue(ConditionContext context, String key) {
    return Boolean.valueOf(context.getEnvironment().getProperty(key))
        ? context.getEnvironment().getProperty(key)
        : "false";
  }
}
