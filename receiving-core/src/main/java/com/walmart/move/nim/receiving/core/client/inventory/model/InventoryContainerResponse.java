package com.walmart.move.nim.receiving.core.client.inventory.model;

import java.util.ArrayList;
import lombok.Data;

@Data
public class InventoryContainerResponse {
  public int orgUnitId;
  public String trackingId;
  public String containerTypeAbbr;
  public String containerType;
  public String sourceSystem;
  public String containerStatus;
  public String containerTag;
  public ArrayList<String> containerTagsSet;
  public String locationName;
  public int destinationLocationId;
  public boolean isShippable;
  public boolean isReusable;
  public String deliveryNumber;
  public String channelType;
  public boolean isVirtualContainer;
  public double netWeight;
  public String netWeightUOM;
  public double netCube;
  public String netCubeUOM;
  public boolean isEligibleForAdjustment;
  public boolean isAuditDone;
  public boolean isAuditInProgress;
  public String lastChangeUserId;
  public String originCountryCode;
  public String originSystem;
  public int dcNumber;
  public String countryCode;
  public ArrayList<Object> childContainers;
  public ArrayList<Object> childContainersList;
  public ArrayList<Object> referenceContainersList;
  public ArrayList<ContainerInventory> containerInventoryList;
  public ArrayList<Object> itemSummary;
  public boolean auditInProgress;
  public String baseUri;
  public String containerUpdatedDate;
  public int lastChangeDate;
  public String containerCreatedDate;
  public String containerCreatedUserId;
  public ArrayList<Object> links;
  public ArrayList<Object> messages;
  public boolean strictWeightCalculated;
  public boolean auditRequired;
}
