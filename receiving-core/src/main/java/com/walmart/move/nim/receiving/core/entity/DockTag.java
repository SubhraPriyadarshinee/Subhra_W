package com.walmart.move.nim.receiving.core.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.Serializable;
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
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity class to store information related to docktags
 *
 * @author sks0013
 */
@Entity
@Table(name = "DOCK_TAG")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DockTag extends BaseMTEntity implements Serializable {

  /** */
  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "DOCK_TAG_SEQUENCE")
  @SequenceGenerator(
      name = "DOCK_TAG_SEQUENCE",
      sequenceName = "DOCK_TAG_SEQUENCE",
      allocationSize = 50)
  private Long id;

  @NotNull
  @Column(name = "DOCK_TAG_ID", length = 40)
  private String dockTagId;

  @NotNull
  @Column(name = "DELIVERY_NUMBER", columnDefinition = "bigint")
  private Long deliveryNumber;

  @Column(name = "CREATE_USER_ID", length = 20)
  private String createUserId;

  @JsonFormat(pattern = ReceivingConstants.UTC_DATE_FORMAT)
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS")
  private Date createTs;

  @Column(name = "COMPLETE_USER_ID", length = 20)
  private String completeUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "COMPLETE_TS")
  private Date completeTs;

  @Column(name = "DOCK_TAG_STATUS", length = 2)
  private InstructionStatus dockTagStatus;

  @Column(name = "LAST_CHANGED_USER_ID", length = 20)
  private String lastChangedUserId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGED_TS")
  private Date lastChangedTs;

  @Column(name = "SCANNED_LOCATION")
  private String scannedLocation;

  @Column(name = "WORKSTATION_LOCATION")
  private String workstationLocation;

  @Column(name = "DOCK_TAG_TYPE", length = 2)
  private DockTagType dockTagType;

  @PrePersist
  protected void onCreate() {
    this.createTs = new Date();
  }

  @PreUpdate
  protected void onUpdate() {
    this.lastChangedTs = new Date();
  }
}
