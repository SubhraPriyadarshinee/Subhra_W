package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Model to hold delivery status message
 *
 * @author g0k0072
 */
@Getter
@Setter
public class DeliveryInfo extends MessageData {
  private Long deliveryNumber;
  private String deliveryStatus;
  private String userId;
  private String doorNumber;
  private String trailerNumber;
  private Date ts = new Date();
  private List<ReceiptSummaryResponse> receipts;
  private OpenDockTagCount openDockTags;
  private PendingAuditTags pendingAuditTags;
  private Integer tagCount;
  private Integer remainingTags;
  private String tagType;
  private String tagValue;
  private String action;
  private Integer numberOfPallets;
}
