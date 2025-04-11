package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.gson.annotations.SerializedName;
import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.message.common.PackData;
import com.walmart.move.nim.receiving.core.model.gdm.v3.AdditionalInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.message.common.PackData;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
public class DeliveryDocument implements Serializable {
  @NotNull private String purchaseReferenceNumber;

  @NotNull
  @SerializedName(value = "financialGroupCode", alternate = "financialReportingGroup")
  private String financialReportingGroup;

  @NotNull
  @SerializedName(value = "baseDivCode", alternate = "baseDivisionCode")
  private String baseDivisionCode;

  @NotNull private String vendorNumber;

  @NotNull private Integer vendorNbrDeptSeq;

  @NotNull private String deptNumber;

  @NotNull private String purchaseCompanyId;

  @NotNull private String purchaseReferenceLegacyType;

  @NotNull private String poDCNumber;

  private String purchaseReferenceStatus;

  @NotNull private List<DeliveryDocumentLine> deliveryDocumentLines;

  private Integer totalPurchaseReferenceQty;

  private String ctrType;

  private String asnNumber;

  private float weight;

  private String weightUOM;

  private float cubeQty;

  private String cubeUOM;

  private String freightTermCode;

  private Integer freightBillQty;

  private DeliveryStatus deliveryStatus;

  private Integer poTypeCode;

  private Integer totalBolFbq;

  private Long receivedCaseCount;

  private Integer receivedPalletCount;

  private Integer palletQty;

  private Integer enteredPalletQty;

  private String palletId;

  private Integer quantity;

  private String poDcCountry;

  private String originalFreightType;

  private String deliveryLegacyStatus;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private Date purchaseReferenceMustArriveByDate;

  private List<String> stateReasonCodes;

  private long deliveryNumber;

  private Boolean importInd;

  private String vendorName;

  private String sourceType;

  private String sellerId;

  private String sellerType;

  @SerializedName(value = "originNode", alternate = "originFacilityNum")
  private Integer originFacilityNum;

  private String originType;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private Date proDate;

  private String deliveryOwnership;

  private String trailerId;
  private PoAdditionalInfo additionalInfo;
  // custom attribute
  private String purchaseRefType;

  private String channelMethod;

  private String messageNumber;

  private String labelType;

  private EventType eventType;

  private Boolean auditDetails;

  private Set<PackData> packData;

  private String trackingId;

  private Integer projectedQty;
  private List<BOLDatum> bolNumbers;
  private String bpoNumber;
  private String poEvent;

  private GdmCurrentNodeDetail gdmCurrentNodeDetail;
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class GdmCurrentNodeDetail {
    private List<SsccScanResponse.Container> containers;
    private AdditionalInfo additionalInfo;
  }
}
