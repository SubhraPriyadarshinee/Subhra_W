package com.walmart.move.nim.receiving.core.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateAttributes {
  private Map<String, ItemPODetails> updatePODetails;
}
