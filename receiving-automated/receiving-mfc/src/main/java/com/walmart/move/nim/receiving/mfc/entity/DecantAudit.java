/** */
package com.walmart.move.nim.receiving.mfc.entity;

import com.walmart.move.nim.receiving.core.entity.AuditableEntity;
import com.walmart.move.nim.receiving.mfc.model.common.AuditStatus;
import java.util.Date;
import javax.persistence.*;
import lombok.*;

/** @author sitakant */
@Entity
@Table(name = "DECANT_AUDIT")
@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecantAudit extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "DECANT_AUDIT_SEQUENCE")
  @SequenceGenerator(
      name = "DECANT_AUDIT_SEQUENCE",
      sequenceName = "DECANT_AUDIT_SEQUENCE",
      allocationSize = 50)
  @Column(name = "ID")
  private Long id;

  @Column(name = "UPC")
  private String upc;

  @Column(name = "DECANTED_CONTAINER_TRACKING_ID")
  private String decantedTrackingId;

  @Column(name = "RECEIPT_ID", columnDefinition = "nvarchar(max)")
  private String receiptId;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "PROCESSING_TS")
  private Date processingTs;

  @Column(name = "ALERT_REQUIRED")
  private Boolean alertRequired;

  @Column(name = "MSG_IDEMPOTANT_ID")
  private String correlationId;

  @Column(name = "STATUS")
  @Enumerated(EnumType.STRING)
  private AuditStatus status;

  @Column(name = "DECANTED_PAYLOAD", columnDefinition = "nvarchar(max)")
  private String payload;
}
