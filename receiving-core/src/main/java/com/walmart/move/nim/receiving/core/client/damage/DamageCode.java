package com.walmart.move.nim.receiving.core.client.damage;

/**
 * Possible Damage Code that can be expected from Damage Service Response
 *
 * @author v0k00fe
 */
public enum DamageCode {
  D10("D10"),
  D11("D11"),
  D12("D12"),
  D14("D14"),
  D29("D29"),
  NA("NA");

  private String code;

  DamageCode(String code) {
    this.code = code;
  }
}
