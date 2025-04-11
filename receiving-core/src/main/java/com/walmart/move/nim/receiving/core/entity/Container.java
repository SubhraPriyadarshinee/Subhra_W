package com.walmart.move.nim.receiving.core.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.walmart.move.nim.receiving.core.common.JpaConverterJson;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.utils.constants.Eligibility;
import java.io.Serializable;
import java.util.*;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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
import javax.persistence.*;
import com.walmart.move.nim.receiving.core.model.ReceivingProductivityResponseDTO;
import lombok.*;

@NamedNativeQueries({
        @NamedNativeQuery(
                name = "Container.getReceivingProductivityForOneUser",
                query = "SELECT c.create_user as receiverUserId, " +
                        "                          CONVERT(DATE,c.create_ts) as receivedDate, " +
                        "                          sum(CASE WHEN(ci.quantity/ci.vnpk_qty) = 0 THEN 1 " +
                        "                          ELSE (ci.quantity/ci.vnpk_qty) END) as casesReceived, " +
                        "                          count(distinct c.tracking_id) as palletsReceived " +
                        "                          FROM Container c, Container_Item ci " +
                        "                          WHERE c.tracking_Id = ci.tracking_Id " +
                        "                          AND c.facilityNum = ci.facilityNum " +
                        "                          AND c.parent_tracking_id is null " +
                        "                          AND c.facilityCountryCode = ci.facilityCountryCode " +
                        "                          AND c.facilityNum= :facilityNum  AND c.facilityCountryCode= :facilityCountryCode " +
                        "                          AND c.create_user= :userId " +
                        "                          AND c.create_ts between :fromDate AND :toDate " +
                        "                          GROUP BY c.CREATE_USER, CONVERT(DATE,c.CREATE_TS) " +
                        "                          ORDER BY CONVERT(DATE,c.CREATE_TS) ",
                resultSetMapping = "getReceivingProductivity"),
        @NamedNativeQuery(
                name = "Container.getReceivingProductivityForOneUser.count",
                query =
                        "select count(distinct c.CREATE_USER ||  CONVERT(DATE,c.create_ts)) as cnt " +
                "                          FROM Container c, Container_Item ci " +
                "                          WHERE c.tracking_Id = ci.tracking_Id " +
                "                          AND c.facilityNum = ci.facilityNum " +
                "                          AND c.parent_tracking_id is null " +
                "                          AND c.facilityCountryCode = ci.facilityCountryCode " +
                "                          AND c.facilityNum= :facilityNum  AND c.facilityCountryCode= :facilityCountryCode " +
                "                          AND c.create_user= :userId " +
                "                          AND c.create_ts between :fromDate AND :toDate ",
                resultSetMapping = "Container.getReceivingProductivity.count"),
        @NamedNativeQuery(
                name = "Container.getReceivingProductivityForAllUsers",
                query = "SELECT c.create_user as receiverUserId, " +
                        "                          CONVERT(DATE,c.create_ts) as receivedDate, " +
                        "                          sum(CASE WHEN(ci.quantity/ci.vnpk_qty) = 0 THEN 1 " +
                        "                          ELSE (ci.quantity/ci.vnpk_qty) END) as casesReceived, " +
                        "                          count(distinct c.tracking_id) as palletsReceived " +
                        "                          FROM Container c, Container_Item ci " +
                        "                          WHERE c.tracking_Id = ci.tracking_Id " +
                        "                          AND c.facilityNum = ci.facilityNum " +
                        "                          AND c.parent_tracking_id is null " +
                        "                          AND c.facilityCountryCode = ci.facilityCountryCode " +
                        "                          AND c.facilityNum= :facilityNum  AND c.facilityCountryCode= :facilityCountryCode " +
                        "                          AND c.create_ts between :fromDate AND :toDate " +
                        "                          GROUP BY c.CREATE_USER, CONVERT(DATE,c.CREATE_TS) " +
                        "                          ORDER BY CONVERT(DATE,c.CREATE_TS) ",
                resultSetMapping = "getReceivingProductivity"),
        @NamedNativeQuery(
                name = "Container.getReceivingProductivityForAllUsers.count",
                query =
                        "select count(distinct c.CREATE_USER ||  CONVERT(DATE,c.create_ts)) as cnt " +
                                "                          FROM Container c, Container_Item ci " +
                                "                          WHERE c.tracking_Id = ci.tracking_Id " +
                                "                          AND c.facilityNum = ci.facilityNum " +
                                "                          AND c.parent_tracking_id is null " +
                                "                          AND c.facilityCountryCode = ci.facilityCountryCode " +
                                "                          AND c.facilityNum= :facilityNum  AND c.facilityCountryCode= :facilityCountryCode " +
                                "                          AND c.create_ts between :fromDate AND :toDate ",
                resultSetMapping = "Container.getReceivingProductivity.count"),
})
@SqlResultSetMapping(
        name = "getReceivingProductivity",
        classes =
        @ConstructorResult(
                targetClass = ReceivingProductivityResponseDTO.class,
                columns = {
                        @ColumnResult(name = "receiverUserId", type = String.class),
                        @ColumnResult(name = "receivedDate", type = String.class),
                        @ColumnResult(name = "casesReceived", type = Long.class),
                        @ColumnResult(name = "palletsReceived", type = Long.class)
                }))
@SqlResultSetMapping(name = "Container.getReceivingProductivity.count", columns = @ColumnResult(name = "cnt"))

/**
 * Entity class to map ContainerModel data
 *
 * @author pcr000m
 */
@Entity
@Table(name = "CONTAINER")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Container extends BaseMTEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "containerSequence")
  @SequenceGenerator(
      name = "containerSequence",
      sequenceName = "container_sequence",
      allocationSize = 100)
  @Expose(serialize = false, deserialize = false)
  private Long id;

  @Expose
  @Column(name = "TRACKING_ID", length = 50)
  private String trackingId;

  @Expose
  @Column(name = "MESSAGE_ID", length = 50, nullable = false)
  private String messageId;

  @Expose
  @Column(name = "PARENT_TRACKING_ID", length = 50)
  private String parentTrackingId;

  @Expose(serialize = false, deserialize = false)
  @Column(name = "INSTRUCTION_ID")
  private Long instructionId;

  @Expose
  @Column(name = "CONTAINER_LOCATION", length = 20)
  private String location;

  @Expose
  @Column(name = "DELIVERY_NUMBER", columnDefinition = "bigint", nullable = false)
  private Long deliveryNumber;

  @Expose
  @Column(name = "FACILITY", length = 80)
  @Convert(converter = JpaConverterJson.class)
  private Map<String, String> facility;

  @Expose
  @Column(name = "DESTINATION", length = 80)
  @Convert(converter = JpaConverterJson.class)
  private Map<String, String> destination;

  @Expose
  @Column(name = "CONTAINER_TYPE", length = 20)
  private String containerType;

  @Expose
  @Column(name = "CONTAINER_STATUS", length = 20)
  private String containerStatus;

  @Expose
  @Column(name = "CONTAINER_WEIGHT")
  private Float weight;

  @Expose
  @Column(name = "WEIGHT_UOM", length = 2)
  private String weightUOM;

  @Expose
  @Column(name = "CONTAINER_CUBE")
  private Float cube;

  @Expose
  @Column(name = "CUBE_UOM", length = 2)
  private String cubeUOM;

  @Expose
  @Column(name = "CONTAINER_SHIPPABLE", columnDefinition = "TINYINT")
  private Boolean ctrShippable;

  @Expose
  @Column(name = "CONTAINER_REUSABLE", columnDefinition = "TINYINT")
  private Boolean ctrReusable;

  @Expose
  @Column(name = "INVENTORY_STATUS")
  private String inventoryStatus;

  @Expose
  @Column(name = "ORG_UNIT_ID", length = 20)
  private String orgUnitId;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "COMPLETE_TS")
  private Date completeTs;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "PUBLISH_TS")
  private Date publishTs;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Expose
  @Column(name = "CREATE_USER", length = 20)
  private String createUser;

  @Expose
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGED_TS")
  private Date lastChangedTs;

  @Expose
  @Column(name = "LAST_CHANGED_USER", length = 20)
  private String lastChangedUser;

  @Expose @Transient private boolean isAudited;

  /*
   New field to indicate if we have any childContainers or not.
  */
  @Expose @Transient private boolean hasChildContainers = false;

  @Expose @Transient private Set<Container> childContainers;

  @Expose
  @Column(name = "ON_CONVEYOR", columnDefinition = "TINYINT")
  private Boolean onConveyor;

  @Expose
  @Column(name = "IS_CONVEYABLE", columnDefinition = "TINYINT")
  private Boolean isConveyable;

  @Expose
  @Column(name = "CONTAINER_EXCEPTION_CODE")
  private String containerException;

  @Expose
  @Column(name = "LABEL_ID")
  private Integer labelId;

  @Expose
  @Column(name = "ACTIVITY_NAME")
  private String activityName;

  @Expose
  @Column(name = "CONTAINER_MISC_INFO", length = 4000)
  @Convert(converter = JpaConverterJson.class)
  private Map<String, Object> containerMiscInfo;

  @Expose
  @SerializedName(value = "contents")
  @JsonAlias("contents")
  @Transient
  private List<ContainerItem> containerItems;

  @Expose
  @Column(name = "SSCC_NUMBER", length = 40)
  private String ssccNumber;

  @Column(name = "ASN_SHIPMENT_ID")
  private String shipmentId;

  @Expose
  @Column(name = "IS_AUDIT_REQUIRED")
  private Boolean isAuditRequired;

  @Expose
  @Column(name = "SUBCENTER_ID")
  private Integer subcenterId;

  @Column(name = "ELIGIBILITY")
  @Enumerated(EnumType.ORDINAL)
  private Eligibility eligibility;

  @Column(name = "RCVG_CONTAINER_TYPE")
  private String rcvgContainerType;

  @Expose @Transient String gtin;
  @Expose @Transient String serial;
  @Expose @Transient String lotNumber;
  @Expose @Transient String expiryDate;
  @Expose @Transient String documentType;
  @Expose @Transient String documentNumber;
  @Expose @Transient private String nationalDrugCode;
  @Expose @Transient private Boolean isDscsaExemptionInd;
  @Expose @Transient Boolean isCompliancePack;
  @Expose @Transient Boolean palletFlowInMultiSku = false;
  @Expose @Transient List<ContainerTag> tags; // used in containerDTO to publish tags to inventory!
  @Expose @Transient private String labelType;
  @Expose @Transient private String fulfillmentMethod;
  @Expose @Transient private String channelMethod;
  @Expose @Transient private String asnNumber;
  @Expose @Transient private String pickBatch;
  @Expose @Transient private String printBatch;
  @Expose @Transient private String aisle;
  @Expose @Transient private boolean isOfflineRcv;
  @Expose @Transient private String poEvent;

  /* Generic map for any information which needs to be bundled with container for processing in following flows.
  This information is not saved.
  The other transient information within class should be moved to be used from this map */
  @Transient private Map<String, Object> additionalInformation;

  @PreUpdate
  public void onUpdate() {
    this.lastChangedTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    if (Objects.isNull(getLastChangedTs())) this.lastChangedTs = new Date();
    if (Objects.isNull(getCreateTs())) this.createTs = new Date();
    if (Objects.isNull(getLastChangedTs())) this.lastChangedTs = new Date();
  }
}
