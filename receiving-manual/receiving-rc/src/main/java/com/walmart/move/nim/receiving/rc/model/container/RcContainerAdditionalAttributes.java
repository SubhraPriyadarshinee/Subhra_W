package com.walmart.move.nim.receiving.rc.model.container;

import com.walmart.move.nim.receiving.rc.model.gad.QuestionsItem;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RcContainerAdditionalAttributes {
  private String packageItemIdentificationCode;
  private String packageItemIdentificationMessage;
  private String serviceType;
  private String returnOrderNumber;
  private String itemType;
  private String itemCategory;
  private String proposedDispositionType;
  private Integer returnOrderLineNumber;
  private Integer salesOrderLineId;
  private String trackingNumber;
  private String finalDispositionType;
  private String sellerCountryCode;
  private Boolean isConsumable;
  private String itemId;
  private String legacySellerId;
  private String itemBarCodeValue;
  private List<QuestionsItem> userAnswers;
  private Boolean isOverride;
  private Boolean isFragile;
  private Boolean isHazmat;
  private Boolean isHazardous;
  private String regulatedItemType;
  private String regulatedItemLabelCode;
  private String itemCondition;
  private String feedbackType;
  private String feedbackReason;
  private String scannedSerialNumber;
  private List<String> expectedSerialNumbers;
  private Boolean isGoodwill;
  private String goodwillReason;
  private String potentialFraudReason;
}
