package com.walmart.move.nim.receiving.utils.constants;

import java.util.HashMap;
import java.util.Map;

public class WarehouseAreaCodes {
  private static final Map<String, String> wareHouseCodeMapping = new HashMap<String, String>();
  private static final Map<String, String> wareHouseDescMapping = new HashMap<String, String>();

  static {
    wareHouseCodeMapping.put("1", "M");
    wareHouseCodeMapping.put("2", "DD");
    wareHouseCodeMapping.put("3", "DD");
    wareHouseCodeMapping.put("4", "F");
    wareHouseCodeMapping.put("5", "I");
    wareHouseCodeMapping.put("6", "DG");
    wareHouseCodeMapping.put("7", "WP");
    wareHouseCodeMapping.put("8", "DP");
    wareHouseCodeMapping.put("9", "CP");
  }

  static {
    wareHouseDescMapping.put("1", "Meat");
    wareHouseDescMapping.put("2", "Deli");
    wareHouseDescMapping.put("3", "Dairy");
    wareHouseDescMapping.put("4", "Frozen");
    wareHouseDescMapping.put("5", "Ice Cream");
    wareHouseDescMapping.put("6", "Dry Grocery");
    wareHouseDescMapping.put("7", "Wet Produce");
    wareHouseDescMapping.put("8", "Dry Produce");
    wareHouseDescMapping.put("9", "Cold Produce");
  }

  public static String getwareHouseCodeMapping(String wareHouseAreaCode) {
    return wareHouseCodeMapping.get(wareHouseAreaCode);
  }

  public static String getwareHouseDescMapping(String wareHouseAreaCode) {
    return wareHouseDescMapping.get(wareHouseAreaCode);
  }
}
