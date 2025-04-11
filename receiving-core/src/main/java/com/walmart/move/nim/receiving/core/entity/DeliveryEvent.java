package com.walmart.move.nim.receiving.core.entity;

import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import javax.persistence.Column;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "DELIVERY_EVENT")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeliveryEvent extends BaseMTEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "DELIVERY_EVENT_SEQUENCE")
  @SequenceGenerator(
      name = "DELIVERY_EVENT_SEQUENCE",
      sequenceName = "DELIVERY_EVENT_SEQUENCE",
      allocationSize = 50)
  @Column(name = "ID")
  private long id;

  @Column(name = "DELIVERY_NUMBER")
  private Long deliveryNumber;

  @Column(name = "EVENT_TYPE")
  private String eventType;

  @Column(name = "EVENT_TYPE_PRIORITY")
  private Integer eventTypePriority;

  @Column(name = "URL")
  private String url;

  @Column(name = "EVENT_STATUS")
  @Enumerated(EnumType.ORDINAL)
  private EventTargetStatus eventStatus;

  @Column(name = "RETRIES_COUNT")
  private Integer retriesCount;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGE_TS")
  private Date lastChangeTs;

  @PreUpdate
  public void onUpdate() {
    this.lastChangeTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
    this.retriesCount = 0;
    this.lastChangeTs = this.createTs;
    switch (eventType) {
      case ReceivingConstants.PRE_LABEL_GEN_FALLBACK:
        this.eventTypePriority = 0;
        break;
      case ReceivingConstants.EVENT_DOOR_ASSIGNED:
        this.eventTypePriority = 1;
        break;
      case ReceivingConstants.EVENT_PO_LINE_ADDED:
      case ReceivingConstants.EVENT_PO_ADDED:
      case ReceivingConstants.EVENT_PO_LINE_UPDATED:
      case ReceivingConstants.EVENT_PO_UPDATED:
        this.eventTypePriority = 2;
        break;
      default:
        this.eventTypePriority = 3;
        break;
    }
  }
}
