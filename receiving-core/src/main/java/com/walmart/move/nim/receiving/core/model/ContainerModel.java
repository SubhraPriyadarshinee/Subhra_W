package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model to represent container(used in InstructionData model)
 *
 * @author g0k0072
 */
@Getter
@Setter
@NoArgsConstructor
public class ContainerModel {
  private String trackingId;
  private Facility ctrDestination;
  private String ctrType;
  private Float weight;
  private String weightUom;
  private Float cube;
  private String cubeUom;
  private List<ContainerModel> childContainers;
  private List<Content> contents;
  private String containerLocation;
}
