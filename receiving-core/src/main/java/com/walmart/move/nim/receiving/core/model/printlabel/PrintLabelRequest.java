package com.walmart.move.nim.receiving.core.model.printlabel;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintLabelRequest implements Serializable {
  @JsonProperty("printJobId")
  @JsonAlias("jobId")
  private String printJobId;

  private String formatName;
  private String labelIdentifier;
  private long ttlInHours;
  private List<LabelData> data;
}
