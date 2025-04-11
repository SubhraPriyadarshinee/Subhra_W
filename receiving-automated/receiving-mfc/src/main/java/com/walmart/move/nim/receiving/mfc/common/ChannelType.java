package com.walmart.move.nim.receiving.mfc.common;

import com.walmart.move.nim.receiving.core.common.SourceType;
import org.apache.commons.lang3.StringUtils;

public enum ChannelType {
  DSD_ASN(SourceType.VENDOR.name(), "ASN"),
  STAPLESTOCK(SourceType.DC.name(), "ASN"),
  DSD_DEX(SourceType.VENDOR.name(), "DEX"),
  DSD_MANUAL(SourceType.VENDOR.name(), "ASN");

  private String sourceType;
  private String documentType;

  ChannelType(String sourceType, String documentType) {
    this.sourceType = sourceType;
    this.documentType = documentType;
  }

  public static ChannelType getChannelType(String sourceType, String documentType) {
    for (ChannelType channelType : ChannelType.values()) {
      if (StringUtils.equalsIgnoreCase(sourceType, channelType.sourceType)
          && StringUtils.equalsIgnoreCase(documentType, channelType.documentType)) {
        return channelType;
      }
    }
    return STAPLESTOCK;
  }
}
