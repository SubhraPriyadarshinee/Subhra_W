package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MultipleTransferInstructionsRequestBody {

  @NotEmpty private List<Long> instructionId;
}
