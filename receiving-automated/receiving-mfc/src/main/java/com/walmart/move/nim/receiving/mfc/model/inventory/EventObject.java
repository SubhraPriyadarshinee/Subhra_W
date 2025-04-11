package com.walmart.move.nim.receiving.mfc.model.inventory;

import java.util.List;

public class EventObject {

  private Boolean isReusable;

  private List<Object> lotNumbers;

  private Integer dcNumber;

  private String countryCode;

  private Double itemNetWeight;

  private Integer containerId;

  private String itemWeightUOM;

  private String containerStatus;

  private Boolean isShippable;

  private String deliveryNumber;

  private String trackingId;

  private String containerTypeAbbr;

  private String containerCreatedDate;

  private Integer locationOrgUnitId;

  private Double containerCube;

  private String locationName;

  private Integer destinationLocationId;

  private List<String> poNums;

  private String warehouseArea;

  private String containerUpdatedDate;

  private Boolean isContainerCubeCalc;

  private List<Object> rotateDates;

  private Boolean isNetItemWeightCalc;

  private Integer sourceSys;

  private String containerCubeUOM;

  private List<ItemListItem> itemList;

  private List<Object> childContainers;

  public Boolean isIsReusable() {
    return isReusable;
  }

  public List<Object> getLotNumbers() {
    return lotNumbers;
  }

  public Integer getDcNumber() {
    return dcNumber;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public Double getItemNetWeight() {
    return itemNetWeight;
  }

  public Integer getContainerId() {
    return containerId;
  }

  public String getItemWeightUOM() {
    return itemWeightUOM;
  }

  public String getContainerStatus() {
    return containerStatus;
  }

  public Boolean isIsShippable() {
    return isShippable;
  }

  public String getDeliveryNumber() {
    return deliveryNumber;
  }

  public String getTrackingId() {
    return trackingId;
  }

  public String getContainerTypeAbbr() {
    return containerTypeAbbr;
  }

  public String getContainerCreatedDate() {
    return containerCreatedDate;
  }

  public Integer getLocationOrgUnitId() {
    return locationOrgUnitId;
  }

  public Double getContainerCube() {
    return containerCube;
  }

  public String getLocationName() {
    return locationName;
  }

  public Integer getDestinationLocationId() {
    return destinationLocationId;
  }

  public List<String> getPoNums() {
    return poNums;
  }

  public String getWarehouseArea() {
    return warehouseArea;
  }

  public String getContainerUpdatedDate() {
    return containerUpdatedDate;
  }

  public Boolean isIsContainerCubeCalc() {
    return isContainerCubeCalc;
  }

  public List<Object> getRotateDates() {
    return rotateDates;
  }

  public Boolean isIsNetItemWeightCalc() {
    return isNetItemWeightCalc;
  }

  public Integer getSourceSys() {
    return sourceSys;
  }

  public String getContainerCubeUOM() {
    return containerCubeUOM;
  }

  public List<ItemListItem> getItemList() {
    return itemList;
  }

  public List<Object> getChildContainers() {
    return childContainers;
  }

  @Override
  public String toString() {
    return "EventObject{"
        + "isReusable = '"
        + isReusable
        + '\''
        + ",lotNumbers = '"
        + lotNumbers
        + '\''
        + ",dcNumber = '"
        + dcNumber
        + '\''
        + ",countryCode = '"
        + countryCode
        + '\''
        + ",itemNetWeight = '"
        + itemNetWeight
        + '\''
        + ",containerId = '"
        + containerId
        + '\''
        + ",itemWeightUOM = '"
        + itemWeightUOM
        + '\''
        + ",containerStatus = '"
        + containerStatus
        + '\''
        + ",isShippable = '"
        + isShippable
        + '\''
        + ",deliveryNumber = '"
        + deliveryNumber
        + '\''
        + ",trackingId = '"
        + trackingId
        + '\''
        + ",containerTypeAbbr = '"
        + containerTypeAbbr
        + '\''
        + ",containerCreatedDate = '"
        + containerCreatedDate
        + '\''
        + ",locationOrgUnitId = '"
        + locationOrgUnitId
        + '\''
        + ",containerCube = '"
        + containerCube
        + '\''
        + ",locationName = '"
        + locationName
        + '\''
        + ",destinationLocationId = '"
        + destinationLocationId
        + '\''
        + ",poNums = '"
        + poNums
        + '\''
        + ",warehouseArea = '"
        + warehouseArea
        + '\''
        + ",containerUpdatedDate = '"
        + containerUpdatedDate
        + '\''
        + ",isContainerCubeCalc = '"
        + isContainerCubeCalc
        + '\''
        + ",rotateDates = '"
        + rotateDates
        + '\''
        + ",isNetItemWeightCalc = '"
        + isNetItemWeightCalc
        + '\''
        + ",sourceSys = '"
        + sourceSys
        + '\''
        + ",containerCubeUOM = '"
        + containerCubeUOM
        + '\''
        + ",itemList = '"
        + itemList
        + '\''
        + ",childContainers = '"
        + childContainers
        + '\''
        + "}";
  }
}
