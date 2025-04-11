package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.ChildContainerConverterJson;
import com.walmart.move.nim.receiving.core.common.ContainerConverterJson;
import com.walmart.move.nim.receiving.core.common.JpaConverterJson;
import com.walmart.move.nim.receiving.core.common.LabelConverterJson;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.Labels;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity class to map instruction data
 *
 * @author g0k0072
 */
@Entity
@Table(name = "INSTRUCTION")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Instruction extends BaseMTEntity implements Serializable {

  /** */
  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "instructionSequence")
  @SequenceGenerator(
      name = "instructionSequence",
      sequenceName = "instruction_sequence",
      allocationSize = 50)
  private Long id;
  // Indexed Column
  @NotNull
  @Column(name = "DELIVERY_NUMBER", columnDefinition = "bigint")
  private Long deliveryNumber;
  // Indexed Column
  @NotNull
  @Column(name = "PURCHASE_REFERENCE_NUMBER", length = 20)
  private String purchaseReferenceNumber;
  // Indexed Column
  @Column(name = "PURCHASE_REFERENCE_LINE_NUMBER")
  private Integer purchaseReferenceLineNumber;
  // Indexed Column
  @Column(name = "GTIN", length = 40)
  private String gtin;

  @Column(name = "RECEIVED_QUANTITY")
  private int receivedQuantity;

  @Column(name = "RECEIVED_QUANTITY_UOM", length = 2)
  private String receivedQuantityUOM;

  @Column(name = "ITEM_DESCRIPTION", length = 255)
  private String itemDescription;

  @Column(name = "PO_DC_NUMBER", length = 10)
  private String poDcNumber;

  @Column(name = "CREATE_USER_ID", length = 20)
  private String createUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Column(name = "LAST_CHANGE_USER_ID", length = 20)
  private String lastChangeUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGE_TS")
  private Date lastChangeTs;

  @Column(name = "COMPLETE_USER_ID", length = 20)
  private String completeUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "COMPLETE_TS")
  private Date completeTs;

  @Column(name = "PRINT_CHILD_CONTAINER_LABELS", columnDefinition = "TINYINT")
  private Boolean printChildContainerLabels;

  @Column(name = "ACTIVITY_NAME", length = 50)
  private String activityName;

  @Column(name = "PROVIDER_ID", length = 15)
  private String providerId;

  @Column(name = "INSTRUCTION_MSG", length = 40)
  private String instructionMsg;

  @Column(name = "INSTRUCTION_CODE", length = 20)
  private String instructionCode;

  @Column(name = "MESSAGE_ID", length = 50)
  private String messageId;

  @Column(name = "PROJECTED_RECEIVED_QTY")
  private int projectedReceiveQty;

  @Column(name = "PROJECTED_RECEIVED_QTY_UOM", length = 2)
  private String projectedReceiveQtyUOM;

  @Column(name = "CONTAINER", columnDefinition = "nvarchar(max)")
  @Convert(converter = ContainerConverterJson.class)
  private ContainerDetails container;

  @Column(name = "CHILD_CONTAINERS", columnDefinition = "nvarchar(max)")
  @Getter(value = AccessLevel.NONE)
  @Setter(value = AccessLevel.NONE)
  private String childContainers;

  @Column(name = "MOVE", columnDefinition = "nvarchar(500)")
  @Convert(converter = JpaConverterJson.class)
  private LinkedTreeMap<String, Object> move;

  @Column(name = "LABELS", columnDefinition = "nvarchar(max)")
  @Getter(value = AccessLevel.NONE)
  @Setter(value = AccessLevel.NONE)
  private String labels;

  @Column(name = "SSCC_NUMBER", length = 40)
  private String ssccNumber;
  // Indexed Column
  @Column(name = "PROBLEM_TAG_ID", length = 40)
  private String problemTagId;

  @Column(name = "FIRST_EXPIRY_FIRST_OUT", columnDefinition = "TINYINT")
  private Boolean firstExpiryFirstOut;

  @Column(name = "DELIVERY_DOCUMENT", columnDefinition = "nvarchar(max)")
  private String deliveryDocument;

  @Column(name = "MANUAL_INSTRUCTION", columnDefinition = "TINYINT")
  private Boolean manualInstruction;

  @Column(name = "DOCK_TAG_ID", length = 40)
  private String dockTagId;

  @Column(name = "ORIGINAL_CHANNEL", length = 20)
  private String originalChannel;

  @Column(name = "IS_RECEIVE_CORRECTION", columnDefinition = "TINYINT")
  private Boolean isReceiveCorrection;

  @Version
  @Setter(value = AccessLevel.PRIVATE)
  @Column(name = "VERSION", columnDefinition = "INTEGER")
  private int version;

  @Column(name = "INSTRUCTION_SET_ID", columnDefinition = "bigint")
  private Long instructionSetId;

  @Column(name = "SOURCE_MESSAGE_ID", length = 50)
  private String sourceMessageId;

  @Column(name = "SUBCENTER_ID")
  private Integer orgUnitId;

  @Column(name = "INSTR_CREATED_BY_PACKAGE_INFO", columnDefinition = "nvarchar(max)")
  private String instructionCreatedByPackageInfo;

  @Column(name = "RECEIVING_METHOD", length = 50)
  private String receivingMethod;

  @PreUpdate
  public void onUpdate() {
    this.lastChangeTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }

  /** @return the childContainers */
  public List<ContainerDetails> getChildContainers() {
    return ChildContainerConverterJson.convertToEntityAttribute(this.childContainers);
  }

  /** @return the labels */
  public Labels getLabels() {
    return LabelConverterJson.convertToEntityAttribute(this.labels);
  }

  /** @param childContainerList the childContainers to set */
  public void setChildContainers(List<ContainerDetails> childContainerList) {
    this.childContainers = ChildContainerConverterJson.convertToDatabaseColumn(childContainerList);
  }

  /** @param labelData the labels to set */
  public void setLabels(Labels labelData) {
    this.labels = LabelConverterJson.convertToDatabaseColumn(labelData);
  }
}
