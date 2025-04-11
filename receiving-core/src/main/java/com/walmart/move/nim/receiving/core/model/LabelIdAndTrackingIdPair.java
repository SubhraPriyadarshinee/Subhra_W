package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LabelIdAndTrackingIdPair {
  String trackingId;
  Integer labelId;
}
