package com.walmart.move.nim.receiving.rc.entity;

import com.walmart.move.nim.receiving.core.common.JpaConverterJson;
import com.walmart.move.nim.receiving.core.entity.BaseMTEntity;
import com.walmart.move.nim.receiving.rc.common.QuestionConverterJson;
import com.walmart.move.nim.receiving.rc.model.gad.QuestionsItem;
import java.util.Date;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "CONTAINER_RLOG")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ContainerRLog extends BaseMTEntity {
  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "containerRLogSequence")
  @SequenceGenerator(name = "containerRLogSequence", sequenceName = "container_rlog_sequence")
  private Long id;

  @NotEmpty(message = "trackingId cannot be null or empty")
  @Column(name = "TRACKING_ID", length = 50, unique = true)
  private String trackingId;

  @Column(name = "CONTAINER_LOCATION", length = 20)
  private String location;

  @Column(name = "ORG_UNIT_ID", length = 20)
  private String orgUnitId;

  @Column(name = "INVENTORY_STATUS", length = 10)
  private String inventoryStatus;

  @Column(name = "DELIVERY_NUMBER", columnDefinition = "bigint", nullable = false)
  private Long deliveryNumber;

  @Column(name = "DESTINATION_PARENT_TRACKING_ID", length = 50)
  private String destinationParentTrackingId;

  @Column(name = "DESTINATION_PARENT_CONTAINER_TYPE", length = 20)
  private String destinationParentContainerType;

  @Column(name = "DESTINATION_TRACKING_ID", length = 50)
  private String destinationTrackingId;

  @Column(name = "DESTINATION_CONTAINER_TYPE", length = 20)
  private String destinationContainerType;

  @Column(name = "DESTINATION_CONTAINER_TAG", length = 50)
  private String destinationContainerTag;

  @Column(name = "PACKAGE_BARCODE_VALUE", length = 100)
  private String packageBarCodeValue;

  @Column(name = "PACKAGE_BARCODE_TYPE", length = 50)
  private String packageBarCodeType;

  @Column(name = "DISPOSITION_TYPE", length = 50)
  private String dispositionType;

  @Column(name = "MESSAGE_ID", length = 50, nullable = false)
  private String messageId;

  @Column(name = "CONTAINER_TYPE", length = 20)
  private String containerType;

  @Column(name = "CONTAINER_STATUS", length = 20)
  private String containerStatus;

  @Column(name = "CONTAINER_WEIGHT")
  private Double weight;

  @Column(name = "WEIGHT_UOM", length = 2)
  private String weightUOM;

  @Column(name = "CONTAINER_CUBE")
  private Double cube;

  @Column(name = "CUBE_UOM", length = 2)
  private String cubeUOM;

  @Column(name = "CONTAINER_SHIPPABLE", columnDefinition = "TINYINT")
  private Boolean ctrShippable;

  @Column(name = "CONTAINER_REUSABLE", columnDefinition = "TINYINT")
  private Boolean ctrReusable;

  @Column(name = "HAS_CHILD_CONTAINERS", columnDefinition = "TINYINT")
  private Boolean hasChildContainers = false;

  @Column(name = "TOTAL_PURCHASE_REFERENCE_QTY")
  private Integer totalPurchaseReferenceQty;

  @Column(name = "PURCHASE_COMPANY_ID")
  private Integer purchaseCompanyId;

  @NotNull(message = "quantity cannot be null or empty")
  @Column(name = "QUANTITY")
  private Integer quantity;

  @NotEmpty(message = "quantityUOM cannot be null or empty")
  @Column(name = "QUANTITY_UOM", length = 2)
  private String quantityUOM;

  @NotNull(message = "vnpkQty cannot be null or empty")
  @Column(name = "VNPK_QTY")
  private Integer vnpkQty;

  @NotNull(message = "whpkQty cannot be null or empty")
  @Column(name = "WHPK_QTY")
  private Integer whpkQty;

  @Column(name = "DESCRIPTION", length = 80)
  private String description;

  @Column(name = "SECONDARY_DESCRIPTION", length = 80)
  private String secondaryDescription;

  @Column(name = "ITEM_NUMBER", length = 20)
  private Long itemNumber;

  @Column(name = "PURCHASE_REFERENCE_NUMBER", length = 100)
  private String purchaseReferenceNumber;

  @Column(name = "PURCHASE_REFERENCE_LINE_NUMBER")
  private Integer purchaseReferenceLineNumber;

  @NotEmpty(message = "salesOrderNumber cannot be null or empty")
  @Column(name = "SALES_ORDER_NUMBER", length = 100)
  private String salesOrderNumber;

  @NotNull(message = "salesOrderLineNumber cannot be null or empty")
  @Column(name = "SALES_ORDER_LINE_NUMBER")
  private Integer salesOrderLineNumber;

  @Column(name = "SALES_ORDER_LINE_ID")
  private Integer salesOrderLineId;

  @Column(name = "RETURN_ORDER_NUMBER", length = 100)
  private String returnOrderNumber;

  @Column(name = "RETURN_ORDER_LINE_NUMBER")
  private Integer returnOrderLineNumber;

  @Column(name = "RETURN_TRACKING_NUMBER", length = 100)
  private String trackingNumber;

  @Column(name = "SCANNED_SERIAL_NUMBER", length = 100)
  private String scannedSerialNumber;

  @Column(name = "EXPECTED_SERIAL_NUMBERS", columnDefinition = "nvarchar(max)")
  @Convert(converter = JpaConverterJson.class)
  private List<String> expectedSerialNumbers;

  @Column(name = "ITEM_CATEGORY", columnDefinition = "nvarchar(1000)")
  private String itemCategory;

  @Column(name = "INBOUND_CHANNEL_METHOD", length = 15)
  private String inboundChannelMethod;

  @Column(name = "OUTBOUND_CHANNEL_METHOD", length = 15)
  private String outboundChannelMethod;

  @Column(name = "PIR_CODE", length = 20)
  private String packageItemIdentificationCode;

  @Column(name = "PIR_MESSAGE", columnDefinition = "nvarchar(max)")
  private String packageItemIdentificationMessage;

  @Column(name = "SERVICE_TYPE", length = 20)
  private String serviceType;

  @NotEmpty(message = "proposedDispositionType cannot be null or empty")
  @Column(name = "PROPOSED_DISPOSITION_TYPE", length = 50)
  private String proposedDispositionType;

  @NotEmpty(message = "finalDispositionType cannot be null or empty")
  @Column(name = "FINAL_DISPOSITION_TYPE", length = 50)
  private String finalDispositionType;

  @Column(name = "IS_OVERRIDE", columnDefinition = "TINYINT")
  private Boolean isOverride;

  @Column(name = "SELLER_COUNTRY_CODE", length = 10)
  private String sellerCountryCode;

  @Column(name = "IS_CONSUMABLE", columnDefinition = "TINYINT")
  private Boolean isConsumable;

  @Column(name = "ITEM_ID", length = 100)
  private String itemId;

  @Column(name = "LEGACY_SELLER_ID", length = 100)
  private String legacySellerId;

  @Column(name = "ITEM_BARCODE_VALUE", length = 40)
  private String itemBarCodeValue;

  @Column(name = "GTIN", length = 40)
  private String gtin;

  @Column(name = "ITEM_UPC", length = 40)
  private String itemUPC;

  @Column(name = "CASE_UPC", length = 40)
  private String caseUPC;

  @Column(name = "QUESTION", columnDefinition = "nvarchar(max)")
  @Getter(value = AccessLevel.NONE)
  @Setter(value = AccessLevel.NONE)
  private String question;

  @Column(name = "IS_FRAGILE", columnDefinition = "TINYINT")
  private Boolean isFragile;

  @Column(name = "IS_HAZMAT", columnDefinition = "TINYINT")
  private Boolean isHazmat;

  @Column(name = "IS_HAZARDOUS", columnDefinition = "TINYINT")
  private Boolean isHazardous;

  @Column(name = "REGULATED_ITEM_TYPE", length = 32)
  private String regulatedItemType;

  @Column(name = "REGULATED_ITEM_LABEL_CODE", length = 20)
  private String regulatedItemLabelCode;

  @Column(name = "ITEM_CONDITION", length = 100)
  private String itemCondition;

  @Column(name = "IS_GOODWILL", columnDefinition = "TINYINT")
  private Boolean isGoodwill;

  @Column(name = "GOODWILL_REASON", length = 30)
  private String goodwillReason;

  @Column(name = "MISSING_RETURN_INITIATED", columnDefinition = "TINYINT")
  private Boolean isMissingReturnInitiated;

  @Column(name = "MISSING_RETURN_RECEIVED", columnDefinition = "TINYINT")
  private Boolean isMissingReturnReceived;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGED_TS")
  private Date lastChangedTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "COMPLETE_TS")
  private Date completeTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "PUBLISH_TS")
  private Date publishTs;

  @Column(name = "CREATE_USER", length = 50)
  private String createUser;

  @Column(name = "LAST_CHANGED_USER", length = 50)
  private String lastChangedUser;

  @Column(name = "PRE_POPULATED_CATEGORY", length = 50)
  private String prePopulatedCategory;

  @Column(name = "CHOSEN_CATEGORY", length = 50)
  private String chosenCategory;

  @Column(name = "TENANT_ID", length = 30)
  private String tenantId;

  @Transient private String workFlowId;

  @PreUpdate
  public void onUpdate() {
    this.lastChangedTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }

  public List<QuestionsItem> getQuestion() {
    return QuestionConverterJson.convertToEntityAttribute(this.question);
  }

  public void setQuestion(List<QuestionsItem> questionsItems) {
    this.question = QuestionConverterJson.convertToDatabaseColumn(questionsItems);
  }
}
