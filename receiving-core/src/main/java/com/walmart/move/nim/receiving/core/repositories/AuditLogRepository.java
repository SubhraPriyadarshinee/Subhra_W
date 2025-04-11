package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.common.AuditStatus;
import com.walmart.move.nim.receiving.core.entity.AuditLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

  public List<AuditLogEntity> findByDeliveryNumberAndAuditStatus(
      Long deliveryNumber, AuditStatus auditStatus);

  public AuditLogEntity findByAsnNumberAndSsccNumberAndAuditStatus(
      String asnNumber, String ssccNumber, AuditStatus auditStatus);

  Integer countByDeliveryNumberAndAuditStatus(Long deliveryNumber, AuditStatus auditStatus);

  public AuditLogEntity findByAsnNumberAndSsccNumber(String asnNumber, String ssccNumber);
}
