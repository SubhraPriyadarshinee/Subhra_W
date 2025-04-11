package com.walmart.move.nim.receiving.core.model;

import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class ReceivingCountSummary {

  private int totalBolFbq = 0;

  private int totalFBQty = 0;
  private String totalFBQtyUOM;

  private int receiveQty = 0;
  private String receiveQtyUOM;

  private int problemQty = 0;
  private String problemQtyUOM;

  private int damageQty = 0;
  private String damageQtyUOM;
  private String damageReasonCode;
  private String damageClaimType;

  private int rejectedQty = 0;
  private String rejectedQtyUOM;
  private String rejectedReasonCode;
  private String rejectedComment;

  private int overageQty = 0;
  private String overageQtyUOM;
  private String overageReasonCode;

  private int shortageQty = 0;
  private String shortageQtyUOM;
  private String shortageReasonCode;

  private int totalReceiveQty = 0;
  private String totalReceiveQtyUOM;

  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;

  private Integer palletQty = 0;
  private Integer vnpkQty;
  private Integer whpkQty;

  public void addReceiveQty(Integer receiveQty) {
    if (receiveQty != null) {
      this.receiveQty = this.receiveQty + receiveQty;
    }
  }

  public void addDamageQty(Integer damageQty) {
    if (damageQty != null) {
      this.damageQty = this.damageQty + damageQty;
    }
  }

  public void addRejectedQty(Integer rejectedQty) {
    if (rejectedQty != null) {
      this.rejectedQty = this.rejectedQty + rejectedQty;
    }
  }

  public void addTotalFBQty(Integer totalFBQty) {
    if (totalFBQty != null) {
      this.totalFBQty = this.totalFBQty + totalFBQty;
    }
  }

  public void addProblemQty(Integer problemQty) {
    if (problemQty != null) {
      this.problemQty = this.problemQty + problemQty;
    }
  }

  public void addShortageQty(Integer shortageQty) {
    if (Objects.nonNull(shortageQty)) {
      this.shortageQty += shortageQty;
    }
  }

  public void addOverageQty(Integer overageQty) {
    if (Objects.nonNull(overageQty)) {
      this.overageQty += overageQty;
    }
  }

  public boolean isOverage() {
    return overageQty > 0;
  }

  public boolean isShortage() {
    return shortageQty > 0;
  }
}
