package com.walmart.move.nim.receiving.core.model.printlabel;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class PrintLabelServiceResponse implements Serializable {
  private String labelIdentifier;
  private String userId;
  private String labelId;
  private String createdTimeStamp;
  private String clientId;
  private String formatName;
  private long ttlInHours;
  private List<LabelData> data;

  private String formattedData;
  private String clientID;
  private String formatId;
  private String printableData;
  private String formatID;
  private List<LabelData> labelData;
}
