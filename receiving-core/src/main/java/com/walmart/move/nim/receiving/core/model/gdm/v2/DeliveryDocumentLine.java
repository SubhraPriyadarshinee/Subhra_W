package com.walmart.move.nim.receiving.core.model.gdm.v2;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.OperationalInfo;
import com.walmart.move.nim.receiving.core.model.TransportationModes;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class DeliveryDocumentLine implements Serializable {
  private String purchaseRefType;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String purchaseReferenceLineStatus;
  private Long itemNbr;
  private String itemUPC;
  private String caseUPC;
  private String vendorUPC;
  private Integer expectedQty;
  private String expectedQtyUOM;
  private Integer vnpkQty;
  private Integer whpkQty;
  private String vendorStockNumber;
  private Double vendorPackCost;
  private Double whpkSell;
  private String vnpkCostUom;
  private Double vnpkWgtQty;
  private String vnpkWgtUom;
  private Double vnpkcbqty;
  private String vnpkcbuomcd;
  private String event;
  private String freeTextDetail;
  private Integer palletTi;
  private Integer palletHi;
  private String department;
  private Boolean isHazmat;
  private Boolean isConveyable;
  private Integer overageQtyLimit;
  private String itemType;
  private String color;
  private String size;
  private String itemDescription1;
  private String itemDescription2;
  private ItemData additionalInfo;
  private String warehouseRotationTypeCode;
  private Integer warehouseMinLifeRemainingToReceive;
  private String warehouseMinLifeRemainingToReceiveUOM;
  private String profiledWarehouseArea;
  private String promoBuyInd;
  private Integer freightBillQty;
  private String financialGroupCode;
  private Integer poDCNumber;
  private Integer purchaseCompanyId;
  private Integer purchaseReferenceLegacyType;
  private String freightTermCode;
  private String vendorNumber;
  private List<String> activeChannelMethods;
  private OperationalInfo operationalInfo;
  private String rejectCode;
  private Map<String, Object> rejectQty;
  private Float bolWeight;
  private String dotIdNbr;
  private List<TransportationModes> transportationModes;
  private String handlingCode;
  private String packType;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  private Date hazmatVerifiedOn;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  private Date limitedQtyVerifiedOn;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  private Date lithiumIonVerifiedOn;
}
