package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.annotations.Expose;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "PURGE_DATA")
@Getter
@Setter
@Builder
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PurgeData {
  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "PURGE_DATA_SEQUENCE")
  @SequenceGenerator(name = "PURGE_DATA_SEQUENCE", sequenceName = "PURGE_DATA_SEQUENCE")
  @Expose(serialize = false, deserialize = false)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "ENTITY_TYPE")
  private PurgeEntityType purgeEntityType;

  @Column(name = "LAST_DELETED_ID")
  private Long lastDeleteId;

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "STATUS")
  private EventTargetStatus eventTargetStatus;

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
  }
}
