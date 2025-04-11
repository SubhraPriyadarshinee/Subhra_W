package com.walmart.move.nim.receiving.core.framework.expression;

public class StandardValue implements Value {

  private Object value;

  public StandardValue(Object value) {
    this.value = value;
  }

  @Override
  public void setValue(Object object) {
    this.value = object;
  }

  @Override
  public Object getValue() {
    return this.value;
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
