package com.walmart.move.nim.receiving.core.model.gdm.v2;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class DeliveryDocument implements Serializable {
  private Long deliveryNumber;
  private String purchaseReferenceNumber;
  private String poDCNumber;
  private String baseDivCode;
  private String purchaseReferenceLegacyType;
  private Integer totalPurchaseReferenceQty;
  private Integer palletQty;
  private Integer purchaseCompanyId;
  private Long receiverNumber;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  private Date purchaseReferenceMustArriveByDate;

  private String purchaseReferenceStatus;
  private String bolNumber;
  private String deptNumber;
  private String vendorNumber;
  private String vendorNbrOnly;
  private String vendorName;
  private Integer legacyType;
  private Double weight;
  private String weightUOM;
  private Integer bolFbqty;
  private Double cubeQty;
  private String cubeUOM;
  private String financialGroupCode;
  private Integer totalBolFbq;
  private Integer vendorNbrDeptSeq;
  private String freightTermCode;
  private Integer poTypeCode;
  private String poDcCountry;
  private String deliveryLegacyStatus;
  private List<DeliveryDocumentLine> deliveryDocumentLines;
  private Boolean importInd;
}
