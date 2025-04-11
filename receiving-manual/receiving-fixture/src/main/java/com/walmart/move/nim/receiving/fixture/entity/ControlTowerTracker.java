package com.walmart.move.nim.receiving.fixture.entity;

import com.walmart.move.nim.receiving.core.entity.BaseMTEntity;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CT_TRACKER")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ControlTowerTracker extends BaseMTEntity {
  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CT_TRACKER_SEQUENCE")
  @SequenceGenerator(
      name = "CT_TRACKER_SEQUENCE",
      sequenceName = "CT_TRACKER_SEQUENCE",
      allocationSize = 50)
  private Long id;

  @Column(name = "LPN", nullable = false, length = 50)
  private String lpn;

  @Column(name = "ACK_KEY")
  private String ackKey;

  @Column(name = "SUBMISSION_STATUS")
  @Enumerated(EnumType.ORDINAL)
  private EventTargetStatus submissionStatus;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Column(name = "RETRIES_COUNT")
  private Integer retriesCount;

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
    this.retriesCount = 0;
  }
}
