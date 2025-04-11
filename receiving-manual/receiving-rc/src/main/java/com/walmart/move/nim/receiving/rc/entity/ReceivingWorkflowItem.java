package com.walmart.move.nim.receiving.rc.entity;

import com.walmart.move.nim.receiving.core.entity.BaseMTEntity;
import com.walmart.move.nim.receiving.rc.contants.WorkflowAction;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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

/**
 * Entity for receiving workflow items. Holds a reference to parent ReceivingWorkflow entity.
 *
 * @see ReceivingWorkflow
 * @author m0s0mqs
 */
@Entity
@Table(name = "RECEIVING_WORKFLOW_ITEM")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingWorkflowItem extends BaseMTEntity {
  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ReceivingWorkflowItemSequence")
  @SequenceGenerator(
      name = "ReceivingWorkflowItemSequence",
      sequenceName = "receiving_workflow_item_sequence",
      allocationSize = 50)
  private Long id;

  @Column(name = "ITEM_TRACKING_ID")
  private String itemTrackingId;

  @Column(name = "GTIN", length = 40, nullable = false)
  private String gtin;

  @Enumerated(EnumType.STRING)
  @Column(name = "ACTION", length = 20)
  private WorkflowAction action;

  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "WORKFLOW_ID")
  private ReceivingWorkflow receivingWorkflow;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "CREATE_TS", nullable = false)
  private Date createTs;

  @Column(name = "CREATE_USER", length = 50, nullable = false)
  private String createUser;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "LAST_CHANGED_TS")
  private Date lastChangedTs;

  @Column(name = "LAST_CHANGED_USER", length = 50)
  private String lastChangedUser;

  @PreUpdate
  public void onUpdate() {
    this.lastChangedTs = new Date();
  }

  @PrePersist
  public void onCreate() {
    this.createTs = new Date();
  }
}
