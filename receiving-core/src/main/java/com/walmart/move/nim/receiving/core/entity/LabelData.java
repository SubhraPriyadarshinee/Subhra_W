package com.walmart.move.nim.receiving.core.entity;

import com.walmart.move.nim.receiving.core.common.AllocationConverterJson;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseOrderInfo;
import com.walmart.move.nim.receiving.core.model.instruction.LabelDataAllocationDTO;
import com.walmart.move.nim.receiving.core.model.label.RejectReason;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.List;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "LABEL_DATA")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@NamedNativeQueries(
    value = {
      @NamedNativeQuery(
          name = "LabelData.findByDeliveryAndContainsLPN",
          query =
              "SELECT DELIVERY_NUMBER, PURCHASE_REFERENCE_NUMBER, PURCHASE_REFERENCE_LINE_NUMBER, POSSIBLE_UPC FROM LABEL_DATA WHERE facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode and DELIVERY_NUMBER =:deliveryNumber and CONTAINS(LPNS, :lpn)",
          resultSetMapping = "PurchaseOrderInfo"),
      @NamedNativeQuery(
          name = "LabelData.findByDeliveryNumberAndUPCAndLabelType",
          query =
              "select top(1) * from LABEL_DATA where facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode AND DELIVERY_NUMBER = :deliveryNumber AND LABEL_TYPE = :labelType AND (JSON_VALUE(POSSIBLE_UPC, '$.orderableGTIN') =  :upc or JSON_VALUE(POSSIBLE_UPC, '$.consumableGTIN') =  :upc or JSON_VALUE(POSSIBLE_UPC, '$.catalogGTIN') =  :upc) order by MUST_ARRIVE_BEFORE_DATE asc",
          resultSetMapping = "LabelData"),
      @NamedNativeQuery(
          name = "LabelData.findByDeliveryAndLPNLike",
          query =
              "SELECT DELIVERY_NUMBER, PURCHASE_REFERENCE_NUMBER, PURCHASE_REFERENCE_LINE_NUMBER, POSSIBLE_UPC FROM LABEL_DATA WHERE facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode and DELIVERY_NUMBER =:deliveryNumber and LPNS LIKE :lpn",
          resultSetMapping = "PurchaseOrderInfo")
    })
@SqlResultSetMappings(
    value = {
      @SqlResultSetMapping(
          name = "PurchaseOrderInfo",
          classes =
              @ConstructorResult(
                  targetClass = PurchaseOrderInfo.class,
                  columns = {
                    @ColumnResult(name = "DELIVERY_NUMBER", type = Long.class),
                    @ColumnResult(name = "PURCHASE_REFERENCE_NUMBER", type = String.class),
                    @ColumnResult(name = "PURCHASE_REFERENCE_LINE_NUMBER", type = Integer.class),
                    @ColumnResult(name = "POSSIBLE_UPC", type = String.class)
                  })),
      @SqlResultSetMapping(
          name = "LabelData",
          entities = {
            @EntityResult(
                entityClass = LabelData.class,
                fields = {
                  @FieldResult(name = "id", column = "ID"),
                  @FieldResult(name = "deliveryNumber", column = "DELIVERY_NUMBER"),
                  @FieldResult(
                      name = "purchaseReferenceNumber",
                      column = "PURCHASE_REFERENCE_NUMBER"),
                  @FieldResult(
                      name = "purchaseReferenceLineNumber",
                      column = "PURCHASE_REFERENCE_LINE_NUMBER"),
                  @FieldResult(name = "itemNumber", column = "ITEM_NUMBER"),
                  @FieldResult(name = "label", column = "LABEL"),
                  @FieldResult(name = "lpns", column = "LPNS"),
                  @FieldResult(name = "mustArriveBeforeDate", column = "MUST_ARRIVE_BEFORE_DATE"),
                  @FieldResult(name = "possibleUPC", column = "POSSIBLE_UPC"),
                  @FieldResult(name = "isDAConveyable", column = "IS_DA_CONVEYABLE"),
                  @FieldResult(name = "lpnsCount", column = "LPNS_COUNT"),
                  @FieldResult(name = "labelType", column = "LABEL_TYPE"),
                  @FieldResult(name = "sequenceNo", column = "SEQUENCE_NO"),
                  @FieldResult(name = "createTs", column = "CREATE_TS"),
                  @FieldResult(name = "lastChangeTs", column = "LAST_CHANGE_TS"),
                  @FieldResult(
                      name = ReceivingConstants.TENENT_FACLITYNUM,
                      column = ReceivingConstants.TENENT_FACLITYNUM),
                  @FieldResult(
                      name = ReceivingConstants.TENENT_COUNTRY_CODE,
                      column = ReceivingConstants.TENENT_COUNTRY_CODE)
                })
          })
    })
public class LabelData extends BaseMTEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "LABEL_DATA_SEQUENCE")
  @SequenceGenerator(
      name = "LABEL_DATA_SEQUENCE",
      sequenceName = "LABEL_DATA_SEQUENCE",
      allocationSize = 100)
  @Column(name = "ID")
  private long id;

  @Column(name = "DELIVERY_NUMBER")
  private Long deliveryNumber;

  @Column(name = "PURCHASE_REFERENCE_NUMBER")
  private String purchaseReferenceNumber;

  @Column(name = "PURCHASE_REFERENCE_LINE_NUMBER")
  private Integer purchaseReferenceLineNumber;

  @Column(name = "ITEM_NUMBER")
  private Long itemNumber;

  @Column(name = "LABEL")
  private String label;

  @Column(name = "LPNS", columnDefinition = "nvarchar(max)")
  private String lpns;

  @Column(name = "TRACKING_ID")
  private String trackingId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "MUST_ARRIVE_BEFORE_DATE")
  private Date mustArriveBeforeDate;

  @Column(name = "POSSIBLE_UPC")
  private String possibleUPC;

  @Column(name = "IS_DA_CONVEYABLE")
  private Boolean isDAConveyable;

  @Column(name = "LPNS_COUNT")
  private Integer lpnsCount;

  @Column(name = "LABEL_TYPE")
  @Enumerated(EnumType.ORDINAL)
  private LabelType labelType;

  @Column(name = "REJECT_REASON")
  @Enumerated(EnumType.ORDINAL)
  private RejectReason rejectReason;

  @Column(name = "SEQUENCE_NO")
  private Integer sequenceNo;

  @Column(name = "LABEL_SEQUENCE_NBR")
  private Long labelSequenceNbr;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGE_TS")
  private Date lastChangeTs;

  @Column(name = "VNPK")
  private Integer vnpk;

  @Column(name = "WHPK")
  private Integer whpk;

  @Column(name = "ALLOCATION")
  @Convert(converter = AllocationConverterJson.class)
  private LabelDataAllocationDTO allocation;

  @Column(name = "QUANTITY_UOM")
  private String quantityUOM;

  @Column(name = "STATUS")
  private String status;

  @Version
  @Column(name = "VERSION")
  @Setter(value = AccessLevel.PRIVATE)
  private Integer version;

  @Column(name = "ORDER_QTY")
  private Integer orderQuantity;

  @Column(name = "QUANTITY")
  private Integer quantity;

  @Column(name = "DEST_STR_NBR")
  private Integer destStrNbr;

  @Column(name = "SSCC")
  private String sscc;

  @Column(name = "ASN_NBR")
  private String asnNumber;

  @Transient private String channelMethod;

  @Transient private String sourceFacilityNumber;

  @Transient private List<LabelDataLpn> labelDataLpnList;

  @Column(name = "LABEL_DATA_MISC_INFO")
  private String labelDataMiscInfo;

  @PreUpdate
  public void onUpdate() {
    this.lastChangeTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }

  public boolean sequenceNotAssigned() {
    return this.sequenceNo == null;
  }
}
