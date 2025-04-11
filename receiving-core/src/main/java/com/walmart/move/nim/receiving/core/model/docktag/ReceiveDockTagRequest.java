package com.walmart.move.nim.receiving.core.model.docktag;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ReceiveDockTagRequest {

  @NotNull(message = "dockTagId cannot be empty")
  private String dockTagId;

  @NotNull(message = "mappedFloorLineLocation cannot be empty")
  private String mappedFloorLineLocation;

  private String workstationLocation;
}
