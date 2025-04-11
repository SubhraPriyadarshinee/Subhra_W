package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class PutawayMessage {
  private String action;
  private PutawayHeader header;
  private String deliveryNumber;
  private String trackingId;
  private String parentTrackingId;
  private Facility facility;
  private Facility destination;
  private Facility finalDestination;
  private String containerType;
  private Float cube;
  private String cubeUOM;
  private Float weight;
  private String weightUOM;
  private String orgUnitId;
  private String location;
  private String inventoryStatus;
  private Boolean ctrShippable;
  private Boolean ctrReusable;
  private Date createTs;
  private String createUser;
  private Date lastChangedTs;
  private String lastChangedUser;
  private Date publishTs;
  private Date completeTs;
  private List<PutawayItem> contents;
}
