package com.walmart.move.nim.receiving.core.client.dcfin.model;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Purchase {

  private String docType;
  private String documentNum;
  private String deliveryNum;
  private String carrierName;
  private String carrierScacCode;
  private String trailerNbr;
  private String billCode;
  private Integer freightBillQty;
  private Date dateReceived;
  private Date proDate;
  private Integer originFacilityNum;
  private String originFacilityCountryCode;
  private String originType;
  private List<PurchaseLine> lines;
  private String channelMethod;
}
