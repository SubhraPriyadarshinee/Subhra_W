package com.walmart.move.nim.receiving.core.framework.consumer;

/**
 * Consumer which will take 2 parameter and return result
 *
 * @author sitakant
 */
@FunctionalInterface
public interface BiParameterConsumer<P, Q, R> {
  R apply(P p, Q q);
}
