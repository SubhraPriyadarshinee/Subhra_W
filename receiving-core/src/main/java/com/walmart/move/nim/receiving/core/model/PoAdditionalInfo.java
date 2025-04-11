package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.sql.Timestamp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class PoAdditionalInfo implements Serializable {
  private ReceivingTier receivingTier;
  private Boolean isAuditRequired;
  private String packId; // nothing but sscc-18 code (18 digits long)

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private Timestamp receivingScanTimeStamp;

  private String shelfLPN; // LPN25
  private String reReceivingShipmentNumber;
}
