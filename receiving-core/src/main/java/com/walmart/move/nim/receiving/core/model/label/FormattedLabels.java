package com.walmart.move.nim.receiving.core.model.label;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.utils.constants.LabelType;
import java.util.HashMap;
import java.util.List;
import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormattedLabels {
  private String seqNo;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String poEvent;
  private String poTypeCode;
  private String poCode;
  private List<String> lpns;
  private String labelData;
  private String lpn;
  private List<HashMap<String, String>> labelProperties;
  private LabelType labelType;
  private DestinationData destination;
}
