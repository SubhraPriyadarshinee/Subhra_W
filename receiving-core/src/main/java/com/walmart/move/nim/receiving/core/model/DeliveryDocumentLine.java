package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import com.walmart.move.nim.receiving.core.client.nimrds.model.PoLineDistribution;
import com.walmart.move.nim.receiving.core.model.gdm.v3.GtinHierarchy;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * "deliveryDocumentLines": [ { "purchaseRefType": "CROSSU", "purchaseReferenceNumber":
 * "4763030184", "purchaseReferenceLineNumber": 2, "purchaseReferenceLineStatus": "RECEIVED",
 * "itemNbr": 553505280, "itemUPC": "00031009437443", "caseUPC": "20031009437447", "expectedQty":
 * 752, "vnpkQty": 12, "whpkQty": 12, "vendorStockNumber": "5253", "vendorPackCost": 7.32,
 * "whpkSell": 8.81, "vnpkWgtQty": 20.2, "vnpkWgtUom": "LB", "vnpkcbqty": 0.938, "vnpkcbuomcd":
 * "CF", "event": "POS REPLEN", "palletTi": 0, "palletHi": 0, "department": "14", "color": "",
 * "size": "", "itemDescription1": "GIBRALTAR ICED TEA", "itemDescription2": "" }
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryDocumentLine implements Serializable {
  private String gtin;

  @SerializedName(value = "itemUPC", alternate = "itemUpc")
  private String itemUpc;

  @SerializedName(value = "caseUPC", alternate = "caseUpc")
  private String caseUpc;

  @NotNull private String purchaseReferenceNumber;

  @NotNull private int purchaseReferenceLineNumber;
  private String event;

  private String purchaseReferenceLineStatus;

  @SerializedName(value = "whpkSell", alternate = "warehousePackSell")
  private Float warehousePackSell;

  private Float vendorPackCost;
  private String currency;

  @SerializedName(value = "vnpkQty", alternate = "vendorPack")
  private Integer vendorPack;

  @SerializedName(value = "whpkQty", alternate = "warehousePack")
  private Integer warehousePack;

  private Integer orderableQuantity;

  private Integer warehousePackQuantity;

  @SerializedName(value = "expectedQtyUOM", alternate = "qtyUOM")
  @NotNull
  private String qtyUOM;

  private Integer openQty;

  @SerializedName(value = "expectedQty", alternate = "totalOrderQty")
  private Integer totalOrderQty;

  private List<PoLineDistribution> distributions;
  private Integer overageQtyLimit = 0;
  private Long itemNbr;
  @NotNull private String purchaseRefType;

  @SerializedName(
      value = "palletTi",
      alternate = {"palletTie"})
  private Integer palletTie;

  @SerializedName(
      value = "palletHi",
      alternate = {"palletHigh"})
  private Integer palletHigh;

  @SerializedName(value = "vnpkWgtQty", alternate = "weight")
  private Float weight;

  @SerializedName(value = "vnpkWgtUom", alternate = "weightUom")
  private String weightUom;

  @SerializedName(value = "vnpkcbqty", alternate = "cube")
  private Float cube;

  @SerializedName(value = "vnpkcbuomcd", alternate = "cubeUom")
  private String cubeUom;

  private String color;
  private String size;
  private Boolean isHazmat;

  @NotNull
  @SerializedName(value = "itemDescription1", alternate = "description")
  private String description;

  @SerializedName(value = "itemDescription2", alternate = "secondaryDescription")
  private String secondaryDescription;

  private Boolean isConveyable;
  private String itemType;
  private String warehouseRotationTypeCode;
  private Boolean firstExpiryFirstOut;
  private Integer warehouseMinLifeRemainingToReceive;
  private String profiledWarehouseArea;
  private String promoBuyInd;
  private ItemData additionalInfo;
  private List<ProblemData> problems;
  private OperationalInfo operationalInfo;
  private Integer freightBillQty;
  private Float bolWeight;
  private List<String> activeChannelMethods;
  private String dotIdNbr;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private Date hazmatVerifiedOn;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private Date limitedQtyVerifiedOn;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private Date lithiumIonVerifiedOn;

  private String department;
  private Double limitedQty;
  private String palletSSCC;
  private String packSSCC;
  private String originalChannel;
  private String ndc;
  private Integer quantity;
  private List<ShipmentDetails> shipmentDetailsList;
  private List<ManufactureDetail> manufactureDetails;
  @JsonIgnore private transient List<Pack> packs;
  private String vendorUPC;
  private String deptNumber;
  private Integer vendorNbrDeptSeq;
  private String vendorStockNumber;
  private String inboundShipmentDocId;
  private Integer shippedQty;
  private String shippedQtyUom;
  private String warehousePackGtin;
  private String consumableGTIN;
  private String orderableGTIN;
  private String catalogGTIN;
  private String lotNumber;
  private List<TransportationModes> transportationModes;
  private Integer totalReceivedQty;
  private boolean maxAllowedOverageQtyIncluded;
  private Integer maxReceiveQty;
  private String handlingCode;
  private Boolean isTemperatureSensitive = false;
  private Boolean isControlledSubstance = false;
  private String packType;

  @JsonProperty("isLithiumIonVerificationRequired")
  private boolean lithiumIonVerificationRequired;

  @JsonProperty("isLimitedQtyVerificationRequired")
  private boolean limitedQtyVerificationRequired;

  private String labelTypeCode;
  private Boolean autoPoSelectionOverageIncluded;
  private List<GtinHierarchy> gtinHierarchy;

  @SerializedName(value = "isNewItem", alternate = "newItem")
  private boolean newItem;

  // a flag for client whether client can auto-populate receiving qty or not.
  private boolean autoPopulateReceivingQty;

  private String imageUrl;

  private Integer storeMinLifeRemaining;

  private Integer enteredQty;
  private String enteredQtyUOM;

  private Boolean breakPackValidationRequired;
  private Boolean nonConValidationRequired;
  private Integer palletTiHiVersion;
  // This has actual ti and hi from GDM
  private Integer actualTi;
  private Integer actualHi;
  private Boolean complianceItem = false;
  private String fromOrgUnitId;
  private String fromPoLineDCNumber;
  private Integer invBohQty;
  private List<String> originCountryCode;
  private String storeAlignment;

  private String childTrackingId;
  private String trackingId;
  private String messageNumber;
}
