package com.walmart.move.nim.receiving.mfc.model.inventory;

public class ItemStatusChange {

  private Integer availabletosellQty;

  public Integer getAvailabletosellQty() {
    return availabletosellQty;
  }

  @Override
  public String toString() {
    return "ItemStatusChange{" + "availabletosellQty = '" + availabletosellQty + '\'' + "}";
  }
}
