package com.walmart.move.nim.receiving.core.model.decant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * * This is the contract for archiving firefly event to SCT
 *
 * @author j0m14t1
 */
@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PalletScanArchiveMessage {

  private String scannedTrackingId;
  private String scannedBy;
  private String deliveryNumber;
  private String scannedAt;
  private String storeNumber;
  private String type;
}
