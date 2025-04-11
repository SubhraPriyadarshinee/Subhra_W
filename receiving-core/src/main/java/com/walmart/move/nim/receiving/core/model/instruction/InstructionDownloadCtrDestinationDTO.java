package com.walmart.move.nim.receiving.core.model.instruction;

import com.walmart.move.nim.receiving.core.model.Facility;
import lombok.Data;

@Data
public class InstructionDownloadCtrDestinationDTO extends Facility {

  private String aisle;
  private String zone;
  private String storeZone;
  private String dcZone;
  private Integer pickBatch;
  private String eventCharacter;
}
