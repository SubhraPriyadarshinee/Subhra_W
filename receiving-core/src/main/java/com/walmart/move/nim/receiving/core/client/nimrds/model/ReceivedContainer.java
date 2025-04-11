package com.walmart.move.nim.receiving.core.client.nimrds.model;

import com.google.gson.annotations.SerializedName;
import com.walmart.move.nim.receiving.core.common.InventoryLabelType;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import java.util.List;
import lombok.Data;

@Data
public class ReceivedContainer {
  private Long deliveryNumber;
  private String poNumber;
  private Integer poLine;
  private Integer returnCode;
  private String message;
  private String labelTrackingId;
  private String parentTrackingId;
  private String carton;
  private Integer batch;
  private Integer shippingLane;
  private Integer receiver;
  private Integer pack;
  private Integer department;
  private Integer division;
  private Integer pocode;
  private String aisle;
  private String poevent;
  private String storezone;
  private String eventchar;
  private String pri_loc;
  private String sec_loc;
  private String ter_loc;
  private String hazmat;
  private String sscc;
  private String tag;
  private String labelTimestamp;
  private String asnNumber;
  private String channelMethod;
  private String destType;
  private String palletId;

  @SerializedName(value = "_id")
  private String id;

  private List<Destination> destinations;
  private String storeAlignment;
  private String labelType;
  private String fulfillmentMethod;
  private boolean routingLabel;
  private boolean palletPullByStore;
  private boolean sorterDivertRequired;
  private String dcZoneRange;
  private Integer pickBatch;
  private List<Distribution> distributions;
  private boolean autoReceivedContainer;
  private String labelPackTypeHandlingCode;
  private InventoryLabelType inventoryLabelType;
  private List<ContainerTag> containerTags;
}
