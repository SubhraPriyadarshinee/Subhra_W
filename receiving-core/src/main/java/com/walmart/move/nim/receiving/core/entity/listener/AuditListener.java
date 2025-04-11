package com.walmart.move.nim.receiving.core.entity.listener;

import com.walmart.move.nim.receiving.core.entity.AuditableEntity;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * * Class to have a hook on JPA prePersist and preUpdate operation to update the audit records
 *
 * @author sitakant
 */
public class AuditListener {

  /**
   * * This will append the {@link AuditableEntity#createdBy} {@link AuditableEntity#createdDate} to
   * the JPA entity before insert operation
   *
   * @param auditableEntity
   * @throws NoSuchFieldException
   * @throws IllegalAccessException
   */
  @PrePersist
  public void preCreate(AuditableEntity auditableEntity) {
    auditableEntity.setCreatedBy(getCurrentActedUser());
    auditableEntity.setCreatedDate(new Date());
  }

  /**
   * * This will append the {@link AuditableEntity#updatedBy} {@link
   * AuditableEntity#lastUpdatedDate} to the JPA entity before update operation
   *
   * @param auditableEntity
   * @throws NoSuchFieldException
   * @throws IllegalAccessException
   */
  @PreUpdate
  public void preUpdate(AuditableEntity auditableEntity) {
    auditableEntity.setUpdatedBy(getCurrentActedUser());
    auditableEntity.setLastUpdatedDate(new Date());
  }

  private String getCurrentActedUser() {
    String user = MDC.get(ReceivingConstants.USER_ID_HEADER_KEY);
    if (user == null || StringUtils.isEmpty(user)) {
      user = ReceivingConstants.DEFAULT_AUDIT_USER;
    }
    return user;
  }
}
