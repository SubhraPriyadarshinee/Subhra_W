package com.walmart.move.nim.receiving.fixture.common;

public enum PalletSize {
  EXTRA_LARGE("XL", "120con"),
  LARGE("L", "80con"),
  MEDIUM("M", "60con"),
  SMALL("S", "40con");
  public final String containerType;
  public final String containerName;

  PalletSize(String containerType, String containerName) {
    this.containerType = containerType;
    this.containerName = containerName;
  }

  public String getContainerType() {
    return containerType;
  }

  public String getContainerName() {
    return containerName;
  }

  @Override
  public String toString() {
    return this.containerName;
  }

  public static String getContainerName(String containerType) {
    for (PalletSize palletSize : PalletSize.values()) {
      if (palletSize.getContainerType().equalsIgnoreCase(containerType)) {
        return palletSize.getContainerName();
      }
    }
    return null;
  }
}
