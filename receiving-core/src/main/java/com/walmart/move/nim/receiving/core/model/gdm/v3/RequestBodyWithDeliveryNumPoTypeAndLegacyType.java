package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@NoArgsConstructor
public class RequestBodyWithDeliveryNumPoTypeAndLegacyType {
  @NotNull private List<Long> deliveryNumbers;

  @NotNull private List<String> channels;

  private Integer pageSize;

  private Integer pageNumber;

  private List<Integer> poTypeCodes;
}
