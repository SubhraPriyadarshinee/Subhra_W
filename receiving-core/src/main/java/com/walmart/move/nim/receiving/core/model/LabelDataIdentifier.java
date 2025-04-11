package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import lombok.*;

@Getter
@Setter
@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class LabelDataIdentifier {

  private Long deliveryNumber;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private Long itemNumber;
  private LabelType labelType;
  private Integer facilityNum;
  private String facilityCountryCode;

  public static LabelDataIdentifier from(LabelData labelData) {
    return LabelDataIdentifier.builder()
        .deliveryNumber(labelData.getDeliveryNumber())
        .purchaseReferenceNumber(labelData.getPurchaseReferenceNumber())
        .purchaseReferenceLineNumber(labelData.getPurchaseReferenceLineNumber())
        .itemNumber(labelData.getItemNumber())
        .labelType(labelData.getLabelType())
        .facilityNum(labelData.getFacilityNum())
        .facilityCountryCode(labelData.getFacilityCountryCode())
        .build();
  }
}
