/** */
package com.walmart.move.nim.receiving.core.advice;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** @author m0g028p */
@Aspect
@Component
public class FeatureFlagAspect {

  private static final int DEFAULT_INT = 0;

  private static final double DEFAULT_DOUBLE = 0.0d;

  private static final char DEFAULT_CHAR = '0';

  private static final String DEFAULT_VALUE = "0";

  private static final Logger log = LoggerFactory.getLogger(FeatureFlagAspect.class);

  @Autowired private TenantSpecificConfigReader configUtils;

  @Pointcut("execution(@FeatureFlag * *(..))")
  public void isAnnotated() {
    // do nothing
  }

  @Around("isAnnotated()")
  public Object checkForFeatureFlag(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
    Method method = methodSignature.getMethod();
    FeatureFlag flag = method.getAnnotation(FeatureFlag.class);

    if (configUtils.getConfiguredFeatureFlag(flag.value())) {
      return joinPoint.proceed();
    } else {
      log.debug("{} not allowed for current tenant", flag.value());
    }
    return (method.getReturnType().isPrimitive())
        ? PRIMITIVES_TO_WRAPPERS.get(method.getReturnType())
        : null;
  }

  private static final Map<Class<?>, Object> PRIMITIVES_TO_WRAPPERS;

  static {
    Map<Class<?>, Object> initializer = new HashMap<>();
    initializer.put(boolean.class, Boolean.valueOf(false));
    initializer.put(byte.class, Byte.valueOf(DEFAULT_VALUE));
    initializer.put(char.class, Character.valueOf(DEFAULT_CHAR));
    initializer.put(double.class, Double.valueOf(DEFAULT_DOUBLE));
    initializer.put(float.class, Float.valueOf(DEFAULT_INT));
    initializer.put(int.class, Integer.valueOf(DEFAULT_INT));
    initializer.put(long.class, Long.valueOf(DEFAULT_INT));
    initializer.put(short.class, Short.valueOf(DEFAULT_VALUE));
    initializer.put(void.class, null);
    PRIMITIVES_TO_WRAPPERS = Collections.unmodifiableMap(initializer);
  }
}
