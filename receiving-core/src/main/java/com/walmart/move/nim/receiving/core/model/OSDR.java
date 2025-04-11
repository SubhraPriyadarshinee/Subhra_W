package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class OSDR {

  private Integer pofbqReceivedQty = 0;
  private Integer receivedQty = 0;
  private String receivedUOM = ReceivingConstants.Uom.VNPK;
  private Integer damageQty = 0;
  private String damageUOM = ReceivingConstants.Uom.VNPK;
  private Integer overageQty = 0;
  private String overageUOM = ReceivingConstants.Uom.VNPK;
  private Integer rejectedQty = 0;
  private String rejectedUOM = ReceivingConstants.Uom.VNPK;
  private Integer shortageQty = 0;
  private String shortageUOM = ReceivingConstants.Uom.VNPK;
  private Integer problemQty = 0;
  private String problemUOM = ReceivingConstants.Uom.VNPK;
  private Integer concealedShortageQty = 0;
  private String concealedShortageUOM = ReceivingConstants.Uom.VNPK;
  private Integer vnpkQty;
  private Integer whpkQty;
  private Integer palletQty = 0;

  public void addOverageQty(Integer overageQty) {
    this.overageQty += overageQty;
  }

  public void addShortageQty(Integer shortageQty) {
    this.shortageQty += shortageQty;
  }

  public void addDamageQty(Integer damageQty) {
    this.damageQty += damageQty;
  }

  public void addRejectedQty(Integer rejectedQty) {
    this.rejectedQty += rejectedQty;
  }

  public void addProblemQty(Integer problemQty) {
    this.problemQty += problemQty;
  }

  public void addReceivedQty(Integer receivedQty) {
    this.receivedQty += receivedQty;
  }

  public void addPOfbqReceivedQty(Integer pofbqReceivedQty) {
    this.pofbqReceivedQty += pofbqReceivedQty;
  }

  public void addConcealedShortageQty(Integer concealedShortageQty) {
    this.concealedShortageQty += concealedShortageQty;
  }

  public void addPalletQty(Integer palletQty) {
    this.palletQty += palletQty;
  }

  /** @param osdr */
  public void add(OSDR osdr) {
    addOverageQty(osdr.getOverageQty());
    addShortageQty(osdr.getShortageQty());
    addDamageQty(osdr.getDamageQty());
    addRejectedQty(osdr.getRejectedQty());
    addProblemQty(osdr.getProblemQty());
    addReceivedQty(osdr.getReceivedQty());
    addPOfbqReceivedQty(osdr.getPofbqReceivedQty());
    addConcealedShortageQty(osdr.getConcealedShortageQty());
  }
}
