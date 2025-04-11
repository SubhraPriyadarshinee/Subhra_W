package com.walmart.move.nim.receiving.mfc.model.inventory;

import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class WhsePackSell {

  private String uom;

  private Double value;

  public String getUom() {
    return Objects.nonNull(uom) ? uom : StringUtils.EMPTY;
  }

  public Double getValue() {
    return Objects.nonNull(value) ? value : 0;
  }

  @Override
  public String toString() {
    return "WhsePackSell{" + "uom = '" + uom + '\'' + ",value = '" + value + '\'' + "}";
  }
}
