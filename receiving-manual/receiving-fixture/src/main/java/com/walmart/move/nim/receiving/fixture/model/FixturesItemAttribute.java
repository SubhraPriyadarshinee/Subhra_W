package com.walmart.move.nim.receiving.fixture.model;

import lombok.*;

// TODO : rafactoring data of @AllArgsConstructor
@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class FixturesItemAttribute {

  private Integer articleId;

  private String articleDescription;

  private String baseUnitOfMeasure;

  private Double articleLength;

  private Double articleWidth;

  private Double articleHeight;

  private String dimensionUnitOfMeasure;

  private String weightUnit;

  private Double articleVolume;

  private String volumeUnit;
}
