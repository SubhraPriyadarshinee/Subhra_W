package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.AuditStatus;
import com.walmart.move.nim.receiving.core.entity.AuditLogEntity;
import com.walmart.move.nim.receiving.core.repositories.AuditLogRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogPersisterService {
  @Autowired private AuditLogRepository auditLogRepository;

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<AuditLogEntity> getAuditLogByDeliveryNumberAndStatus(
      Long deliveryNumber, AuditStatus auditStatus) {
    return auditLogRepository.findByDeliveryNumberAndAuditStatus(deliveryNumber, auditStatus);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Integer getAuditTagCountByDeliveryNumberAndAuditStatus(
      Long deliveryNumber, AuditStatus auditStatus) {
    return auditLogRepository.countByDeliveryNumberAndAuditStatus(deliveryNumber, auditStatus);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public AuditLogEntity getAuditDetailsByAsnNumberAndSsccAndStatus(
      String asnNumber, String ssccNumber, AuditStatus auditStatus) {
    return auditLogRepository.findByAsnNumberAndSsccNumberAndAuditStatus(
        asnNumber, ssccNumber, auditStatus);
  }

  @Transactional
  @InjectTenantFilter
  public AuditLogEntity saveAuditLogData(AuditLogEntity auditLogEntity) {
    return auditLogRepository.save(auditLogEntity);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public AuditLogEntity getAuditDetailsByAsnNumberAndSscc(String asnNumber, String ssccNumber) {
    return auditLogRepository.findByAsnNumberAndSsccNumber(asnNumber, ssccNumber);
  }
}
