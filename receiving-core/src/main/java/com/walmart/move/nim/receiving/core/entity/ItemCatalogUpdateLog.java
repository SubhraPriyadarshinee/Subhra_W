package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.annotations.Expose;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "ITEM_CATALOG_UPDATE_LOG")
@Builder
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ItemCatalogUpdateLog extends BaseMTEntity implements Serializable {

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "itemCatalogSequence")
  @SequenceGenerator(name = "itemCatalogSequence", sequenceName = "ITEM_CATALOG_SEQUENCE")
  @Expose(serialize = false, deserialize = false)
  private Long id;

  @Expose
  @Column(name = "DELIVERY_NUMBER", columnDefinition = "bigint")
  private Long deliveryNumber;

  @Expose
  @Column(name = "ITEM_NUMBER", length = 20)
  private Long itemNumber;

  @Column(name = "OLD_ITEM_UPC", length = 40)
  private String oldItemUPC;

  @Expose
  @Column(name = "NEW_ITEM_UPC", length = 40)
  private String newItemUPC;

  @Expose
  @Column(name = "ITEM_INFO_HAND_KEYED")
  private boolean itemInfoHandKeyed;

  @Expose
  @Column(name = "CREATE_USER_ID", length = 20)
  private String createUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Expose
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Expose
  @Column(name = "VENDOR_STOCK_NUMBER", length = 40)
  private String vendorStockNumber;

  @Expose
  @Column(name = "VENDOR_NUMBER", length = 40)
  private String vendorNumber;

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }
}
