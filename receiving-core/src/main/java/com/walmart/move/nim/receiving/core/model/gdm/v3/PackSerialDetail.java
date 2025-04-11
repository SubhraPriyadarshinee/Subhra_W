package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.List;
import lombok.Data;

@Data
public class PackSerialDetail {
  private String gtin;
  private String expiryDate;
  private String serial;
  private String trackingStatus;
  private String uom;
  private String palletNumber;
  private String packNumber;
  private String lotNumber;
  private String receivingStatus;
  private boolean isPartialPack;
  private boolean isMultiskuPack;
  private int packUnitSerialQty;
  List<PackUnitSerialDetail> packUnitSerialDetails;
}
