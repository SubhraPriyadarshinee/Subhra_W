package com.walmart.move.nim.receiving.fixture.model;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class PalletReceiveRequest {
  @NotEmpty private String packNumber;
  @Valid private List<PalletItem> items;
  private boolean receiveWithoutASN;
  private String storeNumber;
  private boolean isAudited;
  private String containerName;
  private boolean isMultiPallet;
  private String stageLocationName;
}
