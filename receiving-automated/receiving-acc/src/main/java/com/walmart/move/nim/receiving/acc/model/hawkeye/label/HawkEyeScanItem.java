package com.walmart.move.nim.receiving.acc.model.hawkeye.label;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import com.walmart.move.nim.receiving.utils.constants.ItemGroupType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class HawkEyeScanItem extends ScanItem {
  private ItemGroupType itemGroupType;
  private String desc1;
  private String desc2;
  private String event;
  private Integer deptNumber;
  private Integer vendorNumber;
  private String hazmat;
  private String color;
  private String containerType;
  private String size;
  private String storeZone;
  private String channel;
  private String packType;
  private String origin;
  private String fullUserId;
  private List<String> possibleUPCs;
  private Integer vnpkQty;

  @JsonProperty("itemNumber")
  @Override
  public Long getItem() {
    return super.item;
  }

  @JsonProperty("trailerCaseLabels")
  public List<FormattedLabels> getLabels() {
    return labels;
  }

  @JsonIgnore
  @Override
  public String getExceptionLabelURL() {
    return super.exceptionLabelURL;
  }

  public List<String> getPossibleUPCs() {
    return possibleUPC.getPossibleUpcAsList();
  }
}
