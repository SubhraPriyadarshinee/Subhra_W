package com.walmart.move.nim.receiving.core.client.nimrds.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DALabelBackoutRequest {
  private List<String> labels;
  private String userId;
}
