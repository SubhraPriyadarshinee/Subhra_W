package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class RecordOSDRRequest {

  private Integer overageQty;
  private String overageUOM;
  private String overageReasonCode;

  private Integer shortageQty;
  private String shortageUOM;
  private String shortageReasonCode;

  private Integer damageQty;
  private String damageUOM;
  private String damageReasonCode;

  @NotNull
  @Min(value = 0, message = ReceivingException.INVALID_REJECT_QUANTITY)
  private Integer rejectedQty;

  private String rejectedUOM;
  private String rejectedReasonCode;
  private String rejectionComment;

  private Integer concealedShortageQty;
  private String concealedShortageReasonCode;

  private Integer problemQty;
  private String problemUOM;

  @NotNull(message = ReceivingException.VERSION_NOT_NULL)
  private Integer version;

  // GDC Receive Reject event
  private String rejectDisposition;
  private boolean rejectEntireDelivery;
  private boolean fullLoadProduceRejection;
  private String rejectionReason;
  private String itemNumber;
  private String itemDescription;
}
