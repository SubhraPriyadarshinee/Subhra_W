package com.walmart.move.nim.receiving.core.model.label;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
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
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ScanItem {

  protected Long item;

  private String itemDesc;

  private String groupType;

  @JsonUnwrapped protected PossibleUPC possibleUPC;

  protected String reject;

  protected List<FormattedLabels> labels;

  protected FormattedLabels exceptionLabels;

  protected String exceptionLabelURL;
}
