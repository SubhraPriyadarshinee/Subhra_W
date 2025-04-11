package com.walmart.move.nim.receiving.core.model.label.acl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ACLLabelDataTO extends LabelData {

  @JsonProperty(value = "deliveryNumber")
  private Long deliveryNbr;

  private String groupNumber;

  private List<ScanItem> scanItems;

  @Builder
  public ACLLabelDataTO(
      String clientId,
      String formatName,
      String user,
      String deliveryNumber,
      String groupNumber,
      Long deliveryNbr,
      List<ScanItem> scanItems) {
    super(clientId, formatName, user, deliveryNumber);
    this.deliveryNbr = deliveryNbr;
    this.groupNumber = groupNumber;
    this.scanItems = scanItems;
  }
}
