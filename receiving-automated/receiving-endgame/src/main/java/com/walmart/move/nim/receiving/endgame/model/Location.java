package com.walmart.move.nim.receiving.endgame.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Location {
  private String location;
  private Boolean moveRequired;
}
