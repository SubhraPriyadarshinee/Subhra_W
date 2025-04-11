/**
 * InjectTenantFilterSecondary annotation used to inject {@link TenantFilterInjectorAspect} in
 * reporting service methods. This annotation is marker for injecting tenant filter for secondary
 * data source
 */
package com.walmart.move.nim.receiving.reporting.advice;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(METHOD)
/** @author sks0013 */
public @interface InjectTenantFilterSecondary {}
