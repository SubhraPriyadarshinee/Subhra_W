package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class ContainerRequest {
  private String trackingId;
  private String messageId;
  private Long deliveryNumber;
  private String location;
  private String orgUnitId;
  private Map<String, String> facility;
  private String ctrType;
  private String ctrStatus;
  private Float ctrWght;
  private String ctrWghtUom;
  private Float ctrCube;
  private String ctrCubeUom;
  private Boolean ctrShippable;
  private Boolean ctrReusable;
  private String inventoryStatus;
  private List<ContainerRequest> childContainers;
  private List<ContainerItemRequest> contents;
}
