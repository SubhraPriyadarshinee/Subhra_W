package com.walmart.move.nim.receiving.core.framework.expression;

/**
 * * Interface to preserve the placeholder information
 *
 * @author sitakant
 * @see TenantPlaceholder
 */
public interface PlaceHolder {
  String getPrefix();

  String getSuffix();

  Value getValue();

  String getStandardPlaceholderName();
}
