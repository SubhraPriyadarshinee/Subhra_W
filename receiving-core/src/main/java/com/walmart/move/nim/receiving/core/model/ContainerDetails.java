package com.walmart.move.nim.receiving.core.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * This model will map to containers details in instruction.
 *
 * @author pcr000m
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ContainerDetails implements Serializable {

  private static final long serialVersionUID = 1L;

  private Map<String, Object> ctrLabel;

  private Boolean ctrShippable;

  private Boolean ctrReusable;

  private Integer quantity;

  private String outboundChannelMethod;

  private Map<String, String> ctrDestination;

  private Map<String, String> finalDestination;

  private String inventoryStatus;

  private String ctrType;

  private String ctrStatus;

  private String trackingId;

  private List<Distribution> distributions;

  private Float projectedWeight;

  private String projectedWeightUom;

  private Integer orgUnitId;

  private List<ContainerDetails> childContainers;

  private List<Content> contents;

  private String parentTrackingId;

  // Below fields are added for problem app integration
  // TODO Remove when problem app will be deprecated
  private String containerId;
  private Integer remainingQty;
  private ProblemFreightLocation problemFreightLocation;

  // Gls receive properties
  private String glsTimestamp;
  private Double glsWeight;
  private String glsWeightUOM;

  private Map<String, Object> containerMiscInfo;
}
