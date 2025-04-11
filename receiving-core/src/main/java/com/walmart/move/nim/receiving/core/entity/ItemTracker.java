package com.walmart.move.nim.receiving.core.entity;

import com.walmart.move.nim.receiving.utils.constants.ItemTrackerCode;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "ITEM_TRACKER")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class ItemTracker extends BaseMTEntity {
  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ItemTrackerSequence")
  @SequenceGenerator(name = "ItemTrackerSequence", sequenceName = "item_tracker_sequence")
  private Long id;

  @Column(name = "PARENT_TRACKING_ID", length = 100)
  private String parentTrackingId;

  @Column(name = "TRACKING_ID", length = 100)
  private String trackingId;

  @Column(name = "GTIN", length = 40)
  private String gtin;

  @Enumerated(EnumType.STRING)
  @Column(name = "REASON_CODE", length = 100)
  private ItemTrackerCode itemTrackerCode;

  @Column(name = "CREATE_USER", length = 50)
  private String createUser;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @PrePersist
  public void onCreate() {
    this.createTs = new Date();
  }
}
