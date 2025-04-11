package com.walmart.move.nim.receiving.core.entity;

import com.walmart.move.nim.receiving.utils.constants.EventStoreType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "EVENT_STORE")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStore extends AuditableEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "EVENT_STORE_SEQUENCE")
  @SequenceGenerator(
      name = "EVENT_STORE_SEQUENCE",
      sequenceName = "EVENT_STORE_SEQUENCE",
      allocationSize = 50)
  @Column(name = "ID")
  private Long id;

  @Column(name = "EVENT_STORE_KEY")
  private String eventStoreKey;

  @Column(name = "CONTAINER_ID")
  private String containerId;

  @Column(name = "DELIVERY_NUMBER")
  private Long deliveryNumber;

  @Column(name = "STATUS")
  @Enumerated(EnumType.STRING)
  private EventTargetStatus status;

  @Column(name = "EVENT_TYPE")
  @Enumerated(EnumType.STRING)
  private EventStoreType eventStoreType;

  @Column(name = "PAYLOAD")
  private String payload;

  @Column(name = "RETRY_COUNT")
  int retryCount;

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
