package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

/** @author v0k00fe */
@Data
public class BulkCompleteInstructionRequest {

  @NotEmpty private List<CompleteMultipleInstructionData> instructionData;
}
