package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
public class ItemInfoResponse {
  private Long itemNumber;
  private String itemBaseDivisionCode;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ItemInfoResponse that = (ItemInfoResponse) o;
    return Objects.equals(itemNumber, that.itemNumber) && Objects.equals(itemBaseDivisionCode, that.itemBaseDivisionCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemNumber, itemBaseDivisionCode);
  }
}
