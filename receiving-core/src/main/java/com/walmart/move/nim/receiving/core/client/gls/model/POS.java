package com.walmart.move.nim.receiving.core.client.gls.model;

import java.util.List;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class POS {

  private String poNumber;
  private List<POLine> polines;
}
