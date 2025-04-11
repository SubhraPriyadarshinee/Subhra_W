package com.walmart.move.nim.receiving.core.framework.expression;

public abstract class StandardPlaceholder implements PlaceHolder {
  private String prefix;
  private String suffix;
  private Value value;
  private String name;

  public StandardPlaceholder() {}

  public StandardPlaceholder(String name, String prefix, String suffix, Value value) {
    this.name = name;
    this.prefix = prefix;
    this.suffix = suffix;
    this.value = value;
  }

  @Override
  public String getPrefix() {
    return this.prefix;
  }

  @Override
  public String getSuffix() {
    return this.suffix;
  }

  @Override
  public Value getValue() {
    return this.value;
  }

  @Override
  public String getStandardPlaceholderName() {
    return this.name;
  }
}
