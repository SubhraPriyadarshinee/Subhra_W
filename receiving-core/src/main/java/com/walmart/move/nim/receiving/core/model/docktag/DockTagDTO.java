package com.walmart.move.nim.receiving.core.model.docktag;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DockTagDTO extends MessageData implements Serializable {
  private String dockTagId;
  private Long deliveryNumber;
  private InstructionStatus dockTagStatus;
  private DockTagType dockTagType;
}
