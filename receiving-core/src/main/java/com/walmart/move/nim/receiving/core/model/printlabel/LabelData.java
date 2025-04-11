package com.walmart.move.nim.receiving.core.model.printlabel;

import java.io.Serializable;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelData implements Serializable {
  private String key;
  private String value;
}
