package com.walmart.move.nim.receiving.core.entity;

import com.walmart.move.nim.receiving.core.entity.listener.AuditListener;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * * This is the base entity to capture the audit column in the database
 *
 * @author sitakant
 */
@MappedSuperclass
@EntityListeners(AuditListener.class)
@Setter
@Getter
public class AuditableEntity extends BaseMTEntity {

  @Column(name = "CREATED_DATE")
  private Date createdDate;

  @Column(name = "LAST_UPDATED_DATE")
  private Date lastUpdatedDate;

  @Column(name = "CREATED_BY", length = 32)
  private String createdBy;

  @Column(name = "LAST_UPDATED_BY", length = 32)
  private String updatedBy;
}
