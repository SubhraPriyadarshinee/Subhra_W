package com.walmart.move.nim.receiving.acc.entity;

import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.Date;
import javax.persistence.Column;
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
import lombok.*;

@Entity
@Table(name = "LPN_SWAP")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class LpnSwapEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "LPN_SWAP_SEQUENCE")
  @SequenceGenerator(
      name = "LPN_SWAP_SEQUENCE",
      sequenceName = "LPN_SWAP_SEQUENCE",
      allocationSize = 50)
  @Column(name = "ID")
  private long id;

  @Column(name = "LPN")
  private String lpn;

  @Column(name = "SWAPPED_LPN")
  private String swappedLPN;

  @Column(name = "DESTINATION")
  private String destination;

  @Column(name = "SWAPPED_DESTINATION")
  private String swappedDestination;

  @Column(name = "GROUP_NUMBER")
  private String groupNumber;

  @Column(name = "ITEM_NUMBER")
  private int itemNumber;

  @Column(name = "PO_NUMBER")
  private String poNumber;

  @Column(name = "PO_TYPE")
  private String poType;

  @Column(name = "SWAP_STATUS")
  private EventTargetStatus swapStatus;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGE_TS")
  private Date lastChangeTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "SWAP_TS")
  private Date swapTs;

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }

  @PreUpdate
  public void onUpdate() {
    this.lastChangeTs = new Date();
  }
}
