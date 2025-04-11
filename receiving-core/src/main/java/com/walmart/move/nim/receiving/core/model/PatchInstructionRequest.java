package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatchInstructionRequest {
  @NotNull private String upcNumber;
}
