package com.walmart.move.nim.receiving.core.entity;

import com.walmart.move.nim.receiving.core.common.AuditStatus;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "AUDIT_DATA_LOG")
@Data
public class AuditLogEntity extends BaseMTEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "AUDIT_LOG_SEQUENCE")
  @SequenceGenerator(
      name = "AUDIT_LOG_SEQUENCE",
      sequenceName = "AUDIT_LOG_SEQUENCE",
      allocationSize = 1)
  @Column(name = "ID")
  private long id;

  @Column(name = "ASN_NBR", length = 40)
  private String asnNumber;

  @Column(name = "DELIVERY_NUMBER")
  private Long deliveryNumber;

  @Column(name = "SSCC_NUMBER", length = 40)
  private String ssccNumber;

  @Column(name = "AUDIT_STATUS")
  private AuditStatus auditStatus;

  @Column(name = "CREATED_BY", length = 32)
  private String createdBy;

  @Column(name = "CREATED_TS")
  private Date createdTs;

  @Column(name = "COMPLETED_BY", length = 32)
  private String completedBy;

  @Column(name = "COMPLETED_TS")
  private Date completedTs;

  @Column(name = "LAST_UPDATED_BY", length = 32)
  private String updatedBy;

  @Column(name = "LAST_UPDATED_TS")
  private Date lastUpdatedTs;

  @Version
  @Column(name = "VERSION")
  private long version;
}
