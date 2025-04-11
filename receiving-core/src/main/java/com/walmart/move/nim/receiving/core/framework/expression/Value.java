package com.walmart.move.nim.receiving.core.framework.expression;

/**
 * This could be simple object / SpEL object / any sort of complex object and it will get replace in
 * the placeholder
 *
 * @author sitakant
 * @see StandardValue
 */
public interface Value {

  void setValue(Object object);

  Object getValue();

  String toString();
}
