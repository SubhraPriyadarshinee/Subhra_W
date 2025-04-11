package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Model to represent instruction summary search(used in door scan flow)
 *
 * @author g0k0072
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class InstructionSearchRequest {
  private Long deliveryNumber;
  @NotNull private String deliveryStatus;
  private String problemTagId;
  private boolean includeInstructionSet;
  private boolean includeCompletedInstructions = true;
}
