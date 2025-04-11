package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model for representing used and availabel labels
 *
 * @author g0k0072
 */
@Getter
@Setter
@NoArgsConstructor
public class Labels {
  private List<String> usedLabels;
  private List<String> availableLabels;
}
