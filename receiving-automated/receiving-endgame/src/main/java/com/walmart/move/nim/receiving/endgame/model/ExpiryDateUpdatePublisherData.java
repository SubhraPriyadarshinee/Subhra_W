package com.walmart.move.nim.receiving.endgame.model;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ExpiryDateUpdatePublisherData {
  private SearchCriteria searchCriteria;
  private UpdateAttributes updateAttributes;
}
