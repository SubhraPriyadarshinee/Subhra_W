package com.walmart.move.nim.receiving.rdc.constants;

public enum SymTagType {
  SYM_MCPIB_TAG_TYPE_DOCKTAG("DOCK_TAG"),
  SYM_MCPIB_TAG_TYPE_PALLET("PALLET_TAG"),
  UNKNOWN("UNKNOWN");

  private String type;

  SymTagType(String type) {
    this.type = type;
  }

  public String getType() {
    return this.type;
  }

  public static SymTagType getSymTagType(String tagTypeText) {
    for (SymTagType symTagType : SymTagType.values()) {
      if (symTagType.getType().equals(tagTypeText)) {
        return symTagType;
      }
    }
    return UNKNOWN;
  }
}
