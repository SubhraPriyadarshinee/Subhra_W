package com.walmart.move.nim.receiving.core.model.printlabel;

import com.walmart.move.nim.receiving.core.model.Pair;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** label request required by labeling library for generating formatted label */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrintableLabelDataRequest implements PrintableLabelData {
  @NonNull private String formatName;
  private String clientId;
  private String labelIdentifier;
  private int ttlInHours;
  private String facility;
  private Integer formatVersion;
  private boolean isPrintRequest;
  private String language;
  private String printerType;
  private String userId;

  @NonNull List<Pair<String, String>> labelData;
}
