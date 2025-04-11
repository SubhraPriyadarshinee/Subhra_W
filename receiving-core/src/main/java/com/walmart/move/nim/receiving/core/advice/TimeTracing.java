package com.walmart.move.nim.receiving.core.advice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * * Annotation to calculate the time different between method in and method out
 *
 * @author sitakant
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeTracing {
  AppComponent component();

  String flow() default "";

  String executionFlow() default "";

  Type type();

  boolean externalCall() default false;
}
