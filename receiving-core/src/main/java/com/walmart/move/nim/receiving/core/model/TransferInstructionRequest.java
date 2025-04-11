package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferInstructionRequest {

  @NotNull private Long deliveryNumber;

  @NotNull private List<String> userIds;
}
