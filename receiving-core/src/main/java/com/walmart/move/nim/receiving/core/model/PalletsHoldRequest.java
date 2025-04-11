package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PalletsHoldRequest {
  @NotNull private List<String> trackingIds;
}
