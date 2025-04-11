package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.JpaConverterJson;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Date;
import java.util.Objects;
import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "DELIVERY_METADATA")
@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryMetaData extends AuditableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "DELIVERY_METADATA_SEQUENCE")
  @SequenceGenerator(
      name = "DELIVERY_METADATA_SEQUENCE",
      sequenceName = "DELIVERY_METADATA_SEQUENCE")
  @Column(name = "ID")
  private long id;

  @Version
  @Column(name = "VERSION")
  private long version;

  @Column(name = "DELIVERY_NUMBER")
  private String deliveryNumber;

  @Column(name = "DELIVERY_STATUS")
  @Enumerated(EnumType.STRING)
  private DeliveryStatus deliveryStatus;

  @Column(name = "TOTAL_CASE")
  private long totalCaseCount;

  @Column(name = "TOTAL_CASE_SENT")
  private long totalCaseLabelSent;

  @Column(name = "DOOR_NUMBER")
  protected String doorNumber;

  @Column(name = "TRAILER_NUMBER")
  private String trailerNumber;

  @Column(name = "ITEM_OVERRIDES", columnDefinition = "nvarchar(max)")
  @Convert(converter = JpaConverterJson.class)
  private LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetails;

  @Column(name = "PO_LINE_OVERRIDES", columnDefinition = "nvarchar(max)")
  private String poLineDetails;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "UNLOADING_COMPLETE_DATE")
  private Date unloadingCompleteDate;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "OSDR_LAST_PROCESSED_DATE")
  private Date osdrLastProcessedDate;

  @Column(name = "CARRIER_NAME")
  private String carrierName;

  @Column(name = "CARRIER_SCAC_CODE")
  private String carrierScacCode;

  @Column(name = "BILL_CODE")
  private String billCode;

  @Column(name = "RECEIVE_PROGRESS", columnDefinition = "nvarchar(max)")
  private String receiveProgress;

  @Column(name = "ATTACHED_PO_NUMBERS", columnDefinition = "nvarchar(max)")
  private String attachedPoNumbers;

  @PreUpdate
  public void onUpdate() {
    this.setLastUpdatedDate(new Date());
  }

  @PrePersist
  protected void onCreate() {
    if (Objects.isNull(getLastUpdatedDate())) setLastUpdatedDate(new Date());
    if (Objects.isNull(getCreatedDate())) setCreatedDate(new Date());
  }
}
