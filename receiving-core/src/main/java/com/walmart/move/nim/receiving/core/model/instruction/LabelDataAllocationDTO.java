package com.walmart.move.nim.receiving.core.model.instruction;

import java.util.List;
import lombok.Data;

@Data
public class LabelDataAllocationDTO {

  private InstructionDownloadContainerDTO container;
  private List<InstructionDownloadChildContainerDTO> childContainers;
}
