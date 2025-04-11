package com.walmart.move.nim.receiving.core.model.instruction;

import lombok.Data;

@Data
public class InstructionDownloadBlobStorageDTO {

  private String blobUri;
  private String secondaryBlobUri;
  private String blobName;
  private boolean isCompressed;
}
