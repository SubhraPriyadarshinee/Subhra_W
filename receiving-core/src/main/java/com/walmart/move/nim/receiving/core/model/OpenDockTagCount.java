package com.walmart.move.nim.receiving.core.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@Builder
public class OpenDockTagCount {
  Integer count;
}
