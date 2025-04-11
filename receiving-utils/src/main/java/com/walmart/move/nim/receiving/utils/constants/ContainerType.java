package com.walmart.move.nim.receiving.utils.constants;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@Getter
public enum ContainerType {
  PALLET(1, "Pallet"),
  SORTATIONS(2, "Sortations"),
  ROLLCAGE(3, "RollCage"),
  TOTE(4, "Tote"),
  DOLLY(5, "Dolly"),
  TRAY(6, "Tray"),
  HALFEURO(7, "Half Euro"),
  BDU(8, "BDU"),
  RPC1(9, "Rpc1"),
  RPC2(10, "Rpc2"),
  RPC3(11, "Rpc3"),
  MOTORCYCLECAGE(12, "Motorcycle Cage"),
  CHEPPALLET(13, "Chep Pallet"),
  RAIL(14, "Rail"),
  JET(15, "Jet"),
  TRAILER(16, "Trailer"),
  VENDORPACK(17, "Vendor Pack"),
  WHITEWOOD(18, "White wood"),
  PECO(19, "Peco"),
  STOREPALLET(20, "Store pallet"),
  IGPSPALLET(21, "iGPS Pallet"),
  BOX(22, "Box"),
  CART(23, "Cart"),
  VIRTUAL(25, "Virtual"),
  REPACK(26, "Repack"),
  CASE(27, "Case"),
  RM2(28, "RM2");

  private Integer code;
  private String text;
  private static Map<Integer, ContainerType> codeMap;
  private static Map<String, ContainerType> textMap;

  private ContainerType(Integer code, String text) {
    this.code = code;
    this.text = text;
  }

  static {
    codeMap = new HashMap<>();
    textMap = new HashMap<>();
    for (ContainerType containerTypeEnum : ContainerType.values()) {
      codeMap.put(containerTypeEnum.code, containerTypeEnum);
      textMap.put(containerTypeEnum.text.trim().toUpperCase(), containerTypeEnum);
    }
  }

  public static ContainerType getTypeEnum(Integer key) {
    return codeMap.get(key);
  }

  public static ContainerType getTypeEnum(String desc) {
    return textMap.get(desc.trim().toUpperCase());
  }
}
