package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.QuantityDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Vnpk;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Whpk;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PurchaseOrderLineWithOSDRResponse {

  private Integer freightBillQty = 0;

  private POLineOSDR osdr;

  @NotEmpty private Integer poLineNumber;
  private String poLineStatus;
  @NotNull private QuantityDetail ordered;
  private ItemDetails itemDetails;
  private OperationalInfo operationalInfo;
  private Reject reject;
  private Whpk whpk;
  private Vnpk vnpk;

  private String finalizedUserId;

  @JsonFormat(pattern = ReceivingConstants.UTC_DATE_FORMAT)
  private Date finalizedTimeStamp;

  @NotNull private Integer version;
}
