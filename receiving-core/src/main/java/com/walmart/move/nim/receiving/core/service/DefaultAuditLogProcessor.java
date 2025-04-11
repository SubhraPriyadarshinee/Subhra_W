package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.AuditStatus;
import com.walmart.move.nim.receiving.core.entity.AuditLogEntity;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogDetails;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogRequest;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogResponse;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogSummary;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.DEFAULT_AUDIT_LOG_PROCESSOR)
public class DefaultAuditLogProcessor implements AuditLogProcessor {

  @Autowired private AuditLogPersisterService auditLogPersisterService;

  /**
   * Retrieving audit logs
   *
   * @param auditLogRequest
   * @return
   */
  public AuditLogResponse getAuditLogs(AuditLogRequest auditLogRequest) {
    List<AuditLogEntity> auditLogsList = fetchAuditLogs(auditLogRequest);
    AuditLogResponse auditLogResponse = prepareAuditLogResponse(auditLogsList, auditLogRequest);
    return auditLogResponse;
  }

  /**
   * Retrieving audit logs
   *
   * @param auditLogRequest
   * @return
   */
  private List<AuditLogEntity> fetchAuditLogs(AuditLogRequest auditLogRequest) {
    auditLogRequest.setStatus(
        StringUtils.isNotEmpty(auditLogRequest.getStatus())
            ? auditLogRequest.getStatus()
            : AuditStatus.PENDING.getStatus());
    List<AuditLogEntity> auditLogsList =
        auditLogPersisterService.getAuditLogByDeliveryNumberAndStatus(
            auditLogRequest.getDeliveryNumber(),
            AuditStatus.valueOfStatus(auditLogRequest.getStatus()));
    return auditLogsList;
  }

  /**
   * Preparation of AuditLogResponse
   *
   * @param auditLogsList
   * @param auditLogRequest
   * @return
   */
  private AuditLogResponse prepareAuditLogResponse(
      List<AuditLogEntity> auditLogsList, AuditLogRequest auditLogRequest) {
    List<AuditLogSummary> auditLogSummaryList = transformToAuditLogSummary(auditLogsList);
    AuditLogDetails auditPacks = new AuditLogDetails();
    auditPacks.setDeliveryNumber(auditLogRequest.getDeliveryNumber());
    auditPacks.setPendingAuditCount(
        AuditStatus.PENDING.getStatus().equalsIgnoreCase(auditLogRequest.getStatus())
            ? auditLogSummaryList.size()
            : 0);
    auditPacks.setPackSummary(auditLogSummaryList);
    AuditLogResponse auditLogResponse = new AuditLogResponse();
    auditLogResponse.setAuditPacks(auditPacks);
    return auditLogResponse;
  }

  /**
   * Transform audit logs to audit log summary
   *
   * @param auditLogsList
   * @return
   */
  private List<AuditLogSummary> transformToAuditLogSummary(List<AuditLogEntity> auditLogsList) {
    return auditLogsList
        .stream()
        .map(
            auditLogs -> {
              AuditLogSummary auditLogSummary =
                  new AuditLogSummary(
                      auditLogs.getSsccNumber(),
                      auditLogs.getCreatedBy(),
                      auditLogs.getCreatedTs(),
                      auditLogs.getCompletedTs());
              return auditLogSummary;
            })
        .collect(Collectors.toList());
  }
}
