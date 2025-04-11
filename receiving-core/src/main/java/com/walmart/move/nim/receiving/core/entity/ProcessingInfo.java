package com.walmart.move.nim.receiving.core.entity;

import com.google.gson.annotations.Expose;
import java.io.Serializable;
import java.util.*;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "PROCESSING_INFO")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingInfo extends BaseMTEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "processingInfoSequence")
  @SequenceGenerator(
      name = "processingInfoSequence",
      sequenceName = "processing_info_sequence",
      allocationSize = 100)
  private Long id;

  @Column(name = "system_container_id", length = 50)
  private String systemContainerId;

  @Column(name = "reference_info", columnDefinition = "nvarchar(max)")
  private String referenceInfo;

  @Column(name = "status", length = 40)
  private String status;

  @Column(name = "instruction_id")
  private Long instructionId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "create_ts")
  private Date createTs;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "last_changed_ts")
  private Date lastChangedTs;

  @Column(name = "create_user_id", length = 20)
  private String createUserId;

  @PreUpdate
  public void onUpdate() {
    this.lastChangedTs = new Date();
  }

  @PrePersist
  protected void onCreate() {
    if (Objects.isNull(getCreateTs())) this.createTs = new Date();
    if (Objects.isNull(getLastChangedTs())) this.lastChangedTs = new Date();
  }
}
