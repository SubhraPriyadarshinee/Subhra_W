package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.annotations.Expose;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import lombok.*;

/**
 * Entity class to map unloader data
 *
 * @author s0g0gp4
 */
@Entity
@Table(name = "UNLOADER_INFO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnloaderInfo extends BaseMTEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "unloaderInfoIdSequence")
  @SequenceGenerator(
      name = "unloaderInfoIdSequence",
      sequenceName = "UNLOADER_INFO_ID_SEQUENCE",
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

  @Expose
  @Column(name = "ITEM_NUMBER", length = 20)
  private Long itemNumber;

  @Expose
  @Column(name = "ACTUAL_TI")
  private Integer actualTi;

  @Expose
  @Column(name = "ACTUAL_HI")
  private Integer actualHi;

  @Expose
  @Column(name = "FBQ")
  private Integer fbq;

  @Expose
  @Column(name = "CASE_QTY")
  private Integer caseQty;

  @Column(name = "PALLET_QTY")
  private Integer palletQty;

  @Column(name = "IS_UNLOADED_FULL_FBQ")
  private Boolean unloadedFullFbq;

  @Column(length = 32, name = "CREATE_USER_ID")
  private String createUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Column(name = "SUBCENTER_ID")
  private Integer orgUnitId;

  @PrePersist
  protected void onCreate() {
    if (Objects.isNull(getCreateTs())) this.createTs = new Date();
  }
}
