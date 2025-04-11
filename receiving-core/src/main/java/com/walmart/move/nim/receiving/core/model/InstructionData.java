package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model to represent instruction data(used in instruction summary)
 *
 * @author g0k0072
 */
@Getter
@Setter
@NoArgsConstructor
public class InstructionData {
  private ContainerModel container;
  private String messageId;
}
