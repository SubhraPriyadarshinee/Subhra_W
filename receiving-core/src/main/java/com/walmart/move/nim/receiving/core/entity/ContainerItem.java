package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.annotations.Expose;
import com.walmart.move.nim.receiving.core.common.DistributionConverterJson;
import com.walmart.move.nim.receiving.core.common.JpaConverterJson;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.model.ReceipPutawayQtySummaryByContainer;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** @author vn50o7n */
@Entity
@NamedQueries({
  @NamedQuery(
      name = "ContainerItem.PoLineQuantity",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ContainerPoLineQuantity (CI.purchaseReferenceNumber, CI.purchaseReferenceLineNumber, SUM(CI.quantity) as quantity) "
              + " FROM ContainerItem CI, Container C "
              + " WHERE CI.trackingId = C.trackingId AND C.publishTs is not null "
              + " AND C.containerStatus is null AND C.deliveryNumber = :deliveryNumber "
              + " AND CI.facilityNum = :facilityNum AND CI.facilityCountryCode = :facilityCountryCode "
              + " GROUP BY CI.purchaseReferenceNumber, CI.purchaseReferenceLineNumber ")
})
@NamedNativeQuery(
    name = "ContainerItem.ReceiptPutawayQtySummary",
    query =
        "SELECT ci.PURCHASE_REFERENCE_NUMBER, ci.PURCHASE_REFERENCE_LINE_NUMBER, SUM(CAST(ci.QUANTITY AS DECIMAL(10,4)) / ci.VNPK_QTY) as PUTAWAY_QTY "
            + "FROM CONTAINER c, CONTAINER_ITEM ci "
            + "WHERE c.facilityNum = ci.facilityNum "
            + "AND c.facilityCountryCode = ci.facilityCountryCode "
            + "AND c.TRACKING_ID = ci.TRACKING_ID "
            + "AND ci.facilityNum = :facilityNum AND ci.facilityCountryCode = :facilityCountryCode "
            + "AND c.DELIVERY_NUMBER = :deliveryNumber "
            + "AND c.CONTAINER_STATUS = :containerStatus "
            + "GROUP by PURCHASE_REFERENCE_NUMBER, PURCHASE_REFERENCE_LINE_NUMBER",
    resultSetMapping = "ReceipPutawayQtySummaryByContainer")
@SqlResultSetMappings(
    value = {
      @SqlResultSetMapping(
          name = "ReceipPutawayQtySummaryByContainer",
          classes =
              @ConstructorResult(
                  targetClass = ReceipPutawayQtySummaryByContainer.class,
                  columns = {
                    @ColumnResult(name = "PURCHASE_REFERENCE_NUMBER", type = String.class),
                    @ColumnResult(name = "PURCHASE_REFERENCE_LINE_NUMBER", type = Integer.class),
                    @ColumnResult(name = "PUTAWAY_QTY", type = Long.class),
                  }))
    })

/**
 * Entity class to map ContainerItem data
 *
 * @author pcr000m
 */
/** @author vn50o7n */
@Table(name = "CONTAINER_ITEM")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class ContainerItem extends BaseMTEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Expose(serialize = false, deserialize = false)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "containerItemSequence")
  @SequenceGenerator(
      name = "containerItemSequence",
      sequenceName = "container_item_sequence",
      allocationSize = 100)
  @Column(name = "CONTAINER_ITEM_ID")
  private Long id;

  @Expose(serialize = false, deserialize = false)
  @Column(name = "TRACKING_ID", nullable = false, length = 50)
  private String trackingId;

  @Expose
  @Column(name = "PURCHASE_REFERENCE_NUMBER", length = 20)
  private String purchaseReferenceNumber;

  @Expose
  @Column(name = "PURCHASE_REFERENCE_LINE_NUMBER")
  private Integer purchaseReferenceLineNumber;

  @Expose
  @Column(name = "INBOUND_CHANNEL_METHOD", length = 15)
  private String inboundChannelMethod;

  @Expose
  @Column(name = "OUTBOUND_CHANNEL_METHOD", length = 15)
  private String outboundChannelMethod;

  @Expose
  @Column(name = "TOTAL_PURCHASE_REFERENCE_QTY")
  private Integer totalPurchaseReferenceQty;

  @Expose
  @Column(name = "PURCHASE_COMPANY_ID")
  private Integer purchaseCompanyId;

  @Expose
  @Column(name = "DEPT_NUMBER")
  private Integer deptNumber;

  @Expose
  @Column(name = "PO_DEPT_NUMBER", length = 10)
  private String poDeptNumber;

  @Expose
  @Column(name = "ITEM_NUMBER", length = 20)
  private Long itemNumber;

  @Expose
  @Column(name = "GTIN", length = 40)
  private String gtin;

  @Expose
  @Column(name = "QUANTITY")
  private Integer quantity;

  @Expose
  @Column(name = "QUANTITY_UOM", length = 2)
  private String quantityUOM =
      "EA"; // by default EA, currently we are always storing quantity in EA

  /**
   * GET container details API response requires actual inventory data to display on the screen Will
   * move these to POJO model once we started using new API instead of GET /containers/{LPN}
   */
  @Expose @Transient private Integer inventoryQuantity;

  @Expose @Transient private String inventoryQuantityUOM;

  // Signifies the actual number of eaches in the case. Useful for audit or each-based receiving
  // scenarios where eaches in the case may not be equal to vnpk or whpk
  @Expose @Transient private Integer caseQty;

  @Expose
  @Column(name = "VNPK_QTY")
  private Integer vnpkQty;

  @Expose
  @Column(name = "WHPK_QTY")
  private Integer whpkQty;

  @Expose
  @Column(name = "VENDOR_PACK_COST")
  private Double vendorPackCost;

  @Expose
  @Column(name = "WHPK_SELL")
  private Double whpkSell;

  @Expose
  @Column(name = "BASE_DIVISION_CODE", length = 8)
  private String baseDivisionCode;

  @Expose
  @Column(name = "FINANCIAL_REPORTING_GROUP_CODE", length = 8)
  private String financialReportingGroupCode;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "ROTATE_DATE")
  private Date rotateDate;

  @Expose
  @Column(name = "VENDOR_GS_128", length = 32)
  private String vendorGS128;

  @Expose
  @Column(name = "DISTRIBUTIONS", columnDefinition = "nvarchar(max)")
  @Convert(converter = DistributionConverterJson.class)
  private List<Distribution> distributions;

  @Expose
  @Column(name = "VNPK_WEIGHT_QTY")
  private Float vnpkWgtQty;

  @Expose
  @Column(name = "VNPK_WEIGHT_QTY_UOM", length = 4)
  private String vnpkWgtUom;

  @Expose
  @Column(name = "VNPK_CUBE_QTY")
  private Float vnpkcbqty;

  @Expose
  @Column(name = "VNPK_CUBE_QTY_UOM", length = 4)
  private String vnpkcbuomcd;

  @Expose
  @Column(name = "DESCRIPTION", length = 255)
  private String description;

  @Expose
  @Column(name = "SECONDARY_DESCRIPTION", length = 255)
  private String secondaryDescription;

  @Expose
  @Column(name = "VENDOR_NUMBER")
  private Integer vendorNumber;

  @Expose
  @Column(name = "LOT_NUMBER", length = 20)
  private String lotNumber;

  @Expose
  @Column(name = "ACTUAL_TI")
  private Integer actualTi;

  @Expose
  @Column(name = "ACTUAL_HI")
  private Integer actualHi;

  @Expose
  @Column(name = "PACKAGED_AS_UOM", length = 2)
  private String packagedAsUom;

  @Expose
  @Column(name = "PROMO_BUY_IND", length = 1)
  private String promoBuyInd;

  @Expose
  @Column(name = "ITEM_UPC", length = 40)
  private String itemUPC;

  @Expose
  @Column(name = "CASE_UPC", length = 40)
  private String caseUPC;

  @Expose
  @Column(name = "WAREHOSUE_GROUP_CODE", length = 8)
  private String warehouseGroupCode;

  @Expose
  @Column(name = "WAREHOUSE_AC", length = 8)
  private String warehouseAreaCode;

  @Expose
  @Column(name = "PROFILED_WHAC", length = 8)
  private String profiledWarehouseArea;

  @Expose
  @Column(name = "WAREHOUSE_RTC", length = 8)
  private String warehouseRotationTypeCode;

  @Expose
  @Column(name = "RECALL", columnDefinition = "TINYINT")
  private Boolean recall;

  @Expose
  @Column(name = "VARIABLE_WEIGHT", columnDefinition = "TINYINT")
  private Boolean isVariableWeight;

  @Expose
  @Column(name = "ORDERABLE_QUANTITY")
  private Integer orderableQuantity;

  @Expose
  @Column(name = "WAREHOUSE_PACK_QUANTITY")
  private Integer warehousePackQuantity;

  @Expose
  @Column(name = "PO_TYPE_CODE")
  private Integer poTypeCode;

  @Expose
  @Column(name = "WEIGHT_FORMAT_TYPE_CODE", length = 1)
  private String weightFormatTypeCode;

  @Expose
  @Column(name = "SERIAL", length = 20)
  private String serial;

  @Expose
  @Column(name = "MULTI_PO_PALLET", columnDefinition = "TINYINT")
  private Boolean isMultiPoPallet;

  /**
   * VendorNumber, DepartmentNumber, SequenceNumber Concatenation as one number VendorNbrDeptSeq
   * having total of nine digits aka ninedigit-vendorNumber
   */
  @Expose
  @Column(name = "VENDOR_DEPT_SEQ_NUMBERS")
  private Integer vendorNbrDeptSeq;

  @Expose
  @Column(name = "IMPORT_IND", columnDefinition = "TINYINT")
  private Boolean importInd;

  @Expose
  @Column(name = "PO_DC_NUMBER", length = 10)
  private String poDCNumber;

  @Expose
  @Column(name = "PO_DC_COUNTRY_CODE", length = 10)
  private String poDcCountry;

  @Expose
  @Column(name = "CONTAINER_ITEM_MISC_INFO", length = 600)
  @Convert(converter = JpaConverterJson.class)
  private Map<String, String> containerItemMiscInfo;

  @Expose
  @Column(name = "ASRS_ALIGNMENT", length = 10)
  private String asrsAlignment;

  @Expose
  @Column(name = "SLOT_TYPE", length = 10)
  private String slotType;

  @Expose
  @Column(name = "SELLER_ID", length = 32)
  private String sellerId;

  @Expose
  @Column(name = "SELLER_TYPE", length = 20)
  private String sellerType;

  @Expose
  @Column(name = "SELLER_TRUST_LEVEL", length = 10)
  private String sellerTrustLevel;

  @Expose
  @Column(name = "INVOICE_NUMBER", length = 50)
  private String invoiceNumber;

  @Expose
  @Column(name = "INVOICE_LINE_NUMBER")
  private Integer invoiceLineNumber;

  @Column(name = "ORDER_FILLED_QTY")
  private Long orderFilledQty;

  @Column(name = "ORDER_FILLED_QTY_UOM")
  private String orderFilledQtyUom;

  @Expose
  @Column(name = "PLU_NUMBER")
  private Integer pluNumber;

  @Expose
  @Column(name = "SUBCENTER_ID")
  private Integer orgUnitId;

  /*
  Added field for variable weight Items, to determine derived Qty
  Gets derivedQuantity. Ex: 1234 centi-LB -> 12.34 (LB)
   */
  @Expose @Transient private Double derivedQuantity;
  // Gets derivedQuantityUOM. Ex: centi-LB -> (LB)
  @Expose @Transient private String derivedQuantityUOM;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "EXPIRY_DATE")
  private Date expiryDate;

  @Expose
  @Column(name = "CID")
  private String cid;

  @Expose
  @Column(name = "DEPT_CATEGORY")
  private Integer deptCatNbr;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGED_TS")
  private Date lastChangedTs;

  @Expose
  @Column(name = "HYBRID_STORAGE_FLAG", length = 10)
  private String hybridStorageFlag;

  @Expose
  @Column(name = "DEPT_SUBCATG_NBR", length = 15)
  private String deptSubcatgNbr;

  @Expose
  @Column(name = "ITEM_TYPE_CODE", length = 15)
  private Integer itemTypeCode;

  @Expose @Transient private boolean isAudited;
  @Expose @Transient private String type;
  @Expose @Transient private String nationalDrugCode;
  @Expose @Transient private Boolean isDscsaExemptionInd;
  @Expose @Transient private Boolean isCompliancePack;
  @Expose @Transient private String deptNbr;
  @Expose @Transient private Integer divisionNbr;
  @Expose @Transient private String carrierScacCode;
  @Expose @Transient private String carrierName;
  @Expose @Transient private String trailerNbr;
  @Expose @Transient private String billCode;
  @Expose @Transient private Integer freightBillQty;

  @Expose
  @Column(name = "REPLENISHMENT_SUB_TYPE_CODE", length = 15)
  private Integer replenishSubTypeCode;

  /* Generic map for any information which needs to be bundled with containerItem for processing in following flows.
  This information is not saved.
  The other transient information within class should be moved to be used from this map */
  @Transient private Map<String, Object> additionalInformation;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_UPDATE_TS")
  private Date lastUpdateTs;

  @PreUpdate
  public void onUpdate() {
    this.lastChangedTs = new Date();
    this.lastUpdateTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    if (Objects.isNull(getLastChangedTs())) this.lastChangedTs = new Date();
    if (Objects.isNull(getCreateTs())) this.createTs = new Date();
    if (Objects.isNull(getLastUpdateTs())) this.lastUpdateTs = new Date();
  }
}
