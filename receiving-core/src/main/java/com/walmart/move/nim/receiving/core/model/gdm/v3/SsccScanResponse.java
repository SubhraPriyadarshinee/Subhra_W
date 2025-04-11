package com.walmart.move.nim.receiving.core.model.gdm.v3;

import com.walmart.move.nim.receiving.core.common.exception.Error;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class SsccScanResponse {
  private List<Error> errors;
  private Delivery delivery;
  @NotEmpty private List<Shipment> shipments;
  @NotEmpty private List<PurchaseOrder> purchaseOrders;
  @NotEmpty private List<Pack> packs;
  private List<Container> containers;
  private AdditionalInfo additionalInfo;

  @Data
  public static class Container {
    private String id;
    private String parentId;
    private String shipmentDocumentId;
    private String shipmentNumber;
    private String sscc;
    private String gtin;
    private String serial;
    private String lotNumber;
    private String expiryDate;
    private Double unitCount;
    private Double childCount;
    private String receivingStatus;
    private String trackingStatus;
    private String postingStatus;
    private boolean isQueried;
    private List<String> hints;
    private List<ItemInfo> itemInfo;
    private String topLevelContainerSscc;
    private String topLevelContainerId;


    @Data
    public static class ItemInfo {
      private String itemNumber;
      private String gtin;
      private Double totalUnitQty;
    }
  }
}
