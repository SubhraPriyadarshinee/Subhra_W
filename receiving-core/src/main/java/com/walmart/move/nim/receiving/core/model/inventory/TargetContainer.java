package com.walmart.move.nim.receiving.core.model.inventory;

import java.util.Date;
import java.util.List;
import lombok.Data;

@Data
public class TargetContainer {
  public String trackingId;
  public String locationName;
  public String containerStatus;
  public Date createDate;
  public String createUserid;
  public int destLocationId;
  public String containerType;
  public Cube cube;
  public Integer orgUnitId;
  public int versionId;
  public boolean isShippable;
  public boolean isReusable;
  public String deliveryNumber;
  public String channelType;
  public List<Item> items;
  public List<Object> childContainerCreateRequests;
  public String sourceSys;
  public boolean isConveyable;
  public String originSystem;
  public int originDcNumber;
  public String originCountryCode;
  public ContainerTagOss containerTag;
}
