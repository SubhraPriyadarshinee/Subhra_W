package com.walmart.move.nim.receiving.core.advice;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.transaction.annotation.Transactional;

@Retention(RUNTIME)
@InjectTenantFilter
@Transactional(readOnly = true)
@Target({METHOD})
public @interface ReadOnlyTransaction {}
