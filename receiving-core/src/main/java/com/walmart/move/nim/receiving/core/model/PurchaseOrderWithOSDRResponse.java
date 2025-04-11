package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Vendor;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PurchaseOrderWithOSDRResponse {

  @NotEmpty private String poNumber;
  private String poDcNumber;
  private Integer legacyType;
  private String baseDivisionCode;
  private Integer freightBillQty = 0;
  private Integer totalBolFbq = 0;
  private Integer purchaseCompanyId;
  private String poStatus;
  private String freightTermCode;
  private String financialGroupCode;
  private Vendor vendor;
  private OperationalInfo operationalInfo;

  private OSDR osdr;

  private String finalizedUserId;

  @JsonFormat(pattern = ReceivingConstants.UTC_DATE_FORMAT)
  private Date finalizedTimeStamp;

  @NotBlank private String poHashKey;

  private List<PurchaseOrderLineWithOSDRResponse> lines = new ArrayList<>();
}
