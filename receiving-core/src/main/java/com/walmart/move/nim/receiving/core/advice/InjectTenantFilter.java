/**
 * InjectTenantFilter annotation used to inject {@link TenantFilterInjectorAspect} in service
 * methods. This annotation is marker for injecting tenant filter
 */
package com.walmart.move.nim.receiving.core.advice;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target({METHOD, TYPE})
/** @author m0g028p */
public @interface InjectTenantFilter {}
