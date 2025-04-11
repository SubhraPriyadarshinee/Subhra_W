package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ShipmentAdditionalInfo {
  private List<String> commodityTypes;
  private List<String> storeInvoices;
  private List<String> mfcInvoices;
  private String bannerCode;
  private List<String> mfcInvoiceNumbers;
  private Integer floorLoadedCases;
  private String regionCode;
  private List<String> mfcWarehouseAreaDescription;
  private String timeZoneCode;
  private Integer totalPallets;
  private String marketCode;
  private List<Stop> stops;
  private String bannerDescription;
}
