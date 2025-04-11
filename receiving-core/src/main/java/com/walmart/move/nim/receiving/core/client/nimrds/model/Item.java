package com.walmart.move.nim.receiving.core.client.nimrds.model;

import lombok.Data;

@Data
public class Item {

  private long item_nbr;
  private String catalog_upc_nbr;
  private String case_upc_nbr;
  private String upc_nbr;
  private String itemDesc;
  private String itemDesc2;
  private String size;
  private String color;
  private int ti;
  private int hi;
  private String handlingCode;
  private String packType;
  private String primeSlot;
  private int primeSlotSize;
  private int is_hazardous;
  private String sym_eligible_ind;
  private HazmatInfo hazmatInfo;
}
