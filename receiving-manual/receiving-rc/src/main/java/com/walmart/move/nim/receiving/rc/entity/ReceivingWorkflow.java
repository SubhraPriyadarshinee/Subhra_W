package com.walmart.move.nim.receiving.rc.entity;

import com.walmart.move.nim.receiving.core.common.JpaConverterJson;
import com.walmart.move.nim.receiving.core.entity.BaseMTEntity;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.contants.WorkflowStatus;
import com.walmart.move.nim.receiving.rc.contants.WorkflowType;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.persistence.*;
import javax.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

/**
 * Entity for receiving workflows. Contains a list of ReceivingWorkflowItem.
 *
 * @see ReceivingWorkflowItem
 * @author m0s0mqs
 */
@Entity
@Table(name = "RECEIVING_WORKFLOW")
@Getter
@Setter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingWorkflow extends BaseMTEntity {
  @Id
  @Column(name = "ID")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ReceivingWorkflowSequence")
  @SequenceGenerator(
      name = "ReceivingWorkflowSequence",
      sequenceName = "receiving_workflow_sequence",
      allocationSize = 50)
  private Long id;

  @Column(name = "WORKFLOW_ID", length = 50, nullable = false)
  private String workflowId;

  @Column(name = "PACKAGE_BARCODE_VALUE", length = 50, nullable = false)
  private String packageBarcodeValue;

  @Column(name = "PACKAGE_BARCODE_TYPE", length = 20)
  private String packageBarcodeType;

  @Enumerated(EnumType.STRING)
  @Column(name = "TYPE", length = 20, nullable = false)
  private WorkflowType type;

  @Column(name = "CREATE_REASON", length = 150, nullable = false)
  private String createReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", length = 20, nullable = false)
  private WorkflowStatus status;

  @OneToMany(
      mappedBy = "receivingWorkflow",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @BatchSize(size = RcConstants.BATCH_SIZE)
  private List<ReceivingWorkflowItem> workflowItems;

  public Integer getImageCount() {
    return imageCount != null ? imageCount : 0;
  }

  @Min(value = 0, message = "image_count must be positive")
  @Column(name = "IMAGE_COUNT")
  private Integer imageCount;

  @Column(name = "IMAGE_COMMENT", length = 1024)
  private String imageComment;

  @Convert(converter = JpaConverterJson.class)
  @Column(name = "IMAGE_URLS", columnDefinition = "varchar(2048)")
  private List<String> imageUrls;

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

  @Column(name = "additional_attributes", length = 4096)
  private String additionalAttributes;

  @PreUpdate
  public void onUpdate() {
    this.lastChangedTs = new Date();
  }

  @PrePersist
  public void onCreate() {
    this.createTs = new Date();
  }

  public void addWorkflowItem(ReceivingWorkflowItem receivingWorkflowItem) {
    if (Objects.isNull(this.workflowItems)) {
      this.workflowItems = new ArrayList<>();
    }
    this.workflowItems.add(receivingWorkflowItem);
    if (receivingWorkflowItem.getReceivingWorkflow() != this) {
      receivingWorkflowItem.setReceivingWorkflow(this);
    }
  }
}
