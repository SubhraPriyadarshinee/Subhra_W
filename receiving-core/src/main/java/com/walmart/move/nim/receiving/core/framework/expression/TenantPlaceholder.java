package com.walmart.move.nim.receiving.core.framework.expression;

public class TenantPlaceholder extends StandardPlaceholder {

  private static PlaceHolder placeHolder;

  private static final String NAME = "tenant";
  private static final String PREFIX = "{";
  private static final String SUFFIX = "}";

  private TenantPlaceholder(String name, String prefix, String suffix, Value value) {
    super(name, prefix, suffix, value);
  }

  public TenantPlaceholder(Object value) {
    super(NAME, PREFIX, SUFFIX, new StandardValue(value));
  }
}
