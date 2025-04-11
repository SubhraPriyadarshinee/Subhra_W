package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.annotations.Expose;
import java.io.Serializable;
import java.util.*;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import lombok.*;

/**
 * Entity class to map Rejections data
 *
 * @author pcr000m
 */
@Entity
@Table(name = "REJECTIONS")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rejections extends BaseMTEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "containerSequence")
  @SequenceGenerator(
      name = "rejectionsIdSequence",
      sequenceName = "REJECTIONS_ID_SEQUENCE",
      allocationSize = 100)
  @Expose(serialize = false, deserialize = false)
  private Long id;

  @Expose
  @Column(name = "DELIVERY_NUMBER", columnDefinition = "bigint", nullable = false)
  private Long deliveryNumber;

  @NotNull
  @Column(name = "PURCHASE_REFERENCE_NUMBER", length = 20)
  private String purchaseReferenceNumber;

  @Column(name = "PURCHASE_REFERENCE_LINE_NUMBER")
  private Integer purchaseReferenceLineNumber;

  @Column(name = "SUBCENTER_ID")
  private Integer orgUnitId;

  @Expose
  @Column(name = "ITEM_NUMBER", length = 20)
  private Long itemNumber;

  @Column(name = "REASON", length = 100)
  private String reason;

  @Column(name = "DISPOSITION", length = 100)
  private String disposition;

  @Column(name = "IS_ENTIRE_DELIVERY_REJECT", columnDefinition = "TINYINT")
  private Boolean entireDeliveryReject;

  @Column(name = "IS_FULL_LOAD_PRODUCE", columnDefinition = "TINYINT")
  private Boolean fullLoadProduceRejection;

  @Column(name = "CLAIM_TYPE", length = 32)
  private String claimType;

  @Column(name = "QUANTITY")
  private Integer quantity;

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

  @PreUpdate
  public void onUpdate() {
    this.lastChangedTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    if (Objects.isNull(getCreateTs())) this.createTs = new Date();
    if (Objects.isNull(getLastChangedTs())) this.lastChangedTs = new Date();
    if (Objects.isNull(getLastChangedUser())) this.lastChangedUser = getCreateUser();
  }
}
