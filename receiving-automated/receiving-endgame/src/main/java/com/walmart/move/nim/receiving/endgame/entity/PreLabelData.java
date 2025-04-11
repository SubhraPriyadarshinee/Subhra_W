package com.walmart.move.nim.receiving.endgame.entity;

import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.entity.AuditableEntity;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import java.util.Date;
import java.util.Objects;
import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * * This is the entity to captured the pre-label related data
 *
 * @author sitakant
 */
@Entity
@Table(name = "ENDGAME_LABEL_DETAILS")
@NamedQueries({
  @NamedQuery(
      name = "PreLabelData.updateStatus",
      query =
          "UPDATE com.walmart.move.nim.receiving.endgame.entity.PreLabelData pld set pld.status=:updatedStatus, pld.reason = :reason WHERE pld.deliveryNumber = :deliveryNumber AND pld.status= :currentStatus"),
  // TODO : Performance needs to be checked
  @NamedQuery(
      name = "PreLabelData.getSummaryDetailsByDeliveryNumber",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.endgame.model.LabelSummary(type ,COUNT(1) as count) FROM com.walmart.move.nim.receiving.endgame.entity.PreLabelData pld WHERE pld.deliveryNumber = :deliveryNumber GROUP BY pld.type"),
  @NamedQuery(
      name = "PreLabelData.getSummaryDetailsByDeliveryNumberAndType",
      query =
          "SELECT NEW com.walmart.move.nim.receiving.endgame.model.LabelSummary(type ,COUNT(1) as count) FROM com.walmart.move.nim.receiving.endgame.entity.PreLabelData pld WHERE pld.deliveryNumber = :deliveryNumber AND pld.type = :labelType GROUP BY pld.type")
})
@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreLabelData extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ENDGAME_LABEL_DETAILS_SEQUENCE")
  @SequenceGenerator(
      name = "ENDGAME_LABEL_DETAILS_SEQUENCE",
      sequenceName = "ENDGAME_LABEL_DETAILS_SEQUENCE")
  @Column(name = "ID")
  private long id;

  @Column(name = "TCL_NUMBER")
  private String tcl;

  @Column(name = "STATUS")
  @Enumerated(EnumType.STRING)
  private LabelStatus status;

  @Column(name = "DELIVERY_NUMBER")
  private long deliveryNumber;

  @Column(name = "REASON")
  private String reason;

  @Column(name = "TYPE")
  @Enumerated(EnumType.STRING)
  private LabelType type;

  @Column(name = "CASE_UPC")
  private String caseUpc;

  @Column(name = "DIVERT_ACK_EVENT", columnDefinition = "nvarchar(max)")
  private String diverAckEvent;

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
