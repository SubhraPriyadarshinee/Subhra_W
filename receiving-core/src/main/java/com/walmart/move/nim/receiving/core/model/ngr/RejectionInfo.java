package com.walmart.move.nim.receiving.core.model.ngr;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RejectionInfo {
  String reason;
  String comment;
}
