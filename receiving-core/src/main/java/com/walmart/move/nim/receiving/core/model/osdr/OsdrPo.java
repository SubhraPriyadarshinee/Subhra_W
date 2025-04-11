package com.walmart.move.nim.receiving.core.model.osdr;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OsdrPo {
  private String purchaseReferenceNumber;
  private Integer rcvdQty;
  private Integer orderFilledQty;
  private Integer rcvdPackCount;
  private String rcvdQtyUom;
  private OsdrData overage;
  private OsdrData shortage;
  private OsdrData damage;
  private OsdrData reject;
  private Integer palletQty;
  private String cutOverType;
  private List<OsdrPoLine> lines;
  private boolean isLessThanCaseRcvd;

  public void addReceiveQty(Integer rcvdQty) {
    this.rcvdQty += rcvdQty;
  }

  public void addPalletQty(Integer palletQty) {
    this.palletQty += palletQty;
  }
}
