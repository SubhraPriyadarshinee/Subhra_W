package com.walmart.move.nim.receiving.rc.model.dto.request;

import lombok.*;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateReturnOrderDataRequest {
  private String rcTrackingId;
  private String soNumber;
  private String roNumber;
  private Integer roLineNumber;
  private Integer soLineNumber;
  private Integer soLineId;
}
