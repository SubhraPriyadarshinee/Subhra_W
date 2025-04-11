package com.walmart.move.nim.receiving.core.service;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.core.common.AuditStatus;
import com.walmart.move.nim.receiving.core.entity.AuditLogEntity;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogRequest;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogResponse;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultAuditLogProcessorTest {

  @Mock private AuditLogPersisterService auditLogPersisterService;
  @InjectMocks DefaultAuditLogProcessor defaultAuditLogProcessor;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset(auditLogPersisterService);
  }

  @Test
  public void testGetAuditLogsWithCompleteAuditStatus() {
    AuditLogRequest auditLogRequest = mockAuditLogRequest(AuditStatus.COMPLETED);
    doReturn(mockAuditLogEntity(AuditStatus.COMPLETED))
        .when(auditLogPersisterService)
        .getAuditLogByDeliveryNumberAndStatus(
            auditLogRequest.getDeliveryNumber(),
            AuditStatus.valueOfStatus(auditLogRequest.getStatus()));
    AuditLogResponse auditLogResponse = defaultAuditLogProcessor.getAuditLogs(auditLogRequest);
    assertNotNull(auditLogResponse);
    assertNotNull(auditLogResponse.getAuditPacks());
    assertNotNull(auditLogResponse.getAuditPacks().getDeliveryNumber());
    assertNotNull(auditLogResponse.getAuditPacks().getPackSummary());
    assertTrue(auditLogResponse.getAuditPacks().getPackSummary().size() == 1);
    assertEquals(
        auditLogResponse.getAuditPacks().getDeliveryNumber(), auditLogRequest.getDeliveryNumber());
    verify(auditLogPersisterService, times(1))
        .getAuditLogByDeliveryNumberAndStatus(
            auditLogRequest.getDeliveryNumber(),
            AuditStatus.valueOfStatus(auditLogRequest.getStatus()));
  }

  @Test
  public void testGetAuditLogsWithPendingAuditStatus() {
    AuditLogRequest auditLogRequest = mockAuditLogRequest(AuditStatus.PENDING);
    doReturn(mockAuditLogEntity(AuditStatus.PENDING))
        .when(auditLogPersisterService)
        .getAuditLogByDeliveryNumberAndStatus(
            auditLogRequest.getDeliveryNumber(),
            AuditStatus.valueOfStatus(auditLogRequest.getStatus()));
    AuditLogResponse auditLogResponse = defaultAuditLogProcessor.getAuditLogs(auditLogRequest);
    assertNotNull(auditLogResponse);
    assertNotNull(auditLogResponse.getAuditPacks());
    assertNotNull(auditLogResponse.getAuditPacks().getDeliveryNumber());
    assertNotNull(auditLogResponse.getAuditPacks().getPackSummary());
    assertTrue(auditLogResponse.getAuditPacks().getPackSummary().size() == 1);
    assertEquals(
        auditLogResponse.getAuditPacks().getDeliveryNumber(), auditLogRequest.getDeliveryNumber());
    verify(auditLogPersisterService, times(1))
        .getAuditLogByDeliveryNumberAndStatus(
            auditLogRequest.getDeliveryNumber(),
            AuditStatus.valueOfStatus(auditLogRequest.getStatus()));
  }

  @Test
  public void testGetAuditLogsWithEmptyAuditStatus() {
    AuditLogRequest auditLogRequest = mockAuditLogRequest(AuditStatus.PENDING);
    auditLogRequest.setStatus(null);
    doReturn(mockAuditLogEntity(AuditStatus.PENDING))
        .when(auditLogPersisterService)
        .getAuditLogByDeliveryNumberAndStatus(
            auditLogRequest.getDeliveryNumber(), AuditStatus.PENDING);
    AuditLogResponse auditLogResponse = defaultAuditLogProcessor.getAuditLogs(auditLogRequest);
    assertNotNull(auditLogResponse);
    assertNotNull(auditLogResponse.getAuditPacks());
    assertNotNull(auditLogResponse.getAuditPacks().getDeliveryNumber());
    assertNotNull(auditLogResponse.getAuditPacks().getPackSummary());
    assertTrue(auditLogResponse.getAuditPacks().getPackSummary().size() == 1);
    assertEquals(
        auditLogResponse.getAuditPacks().getDeliveryNumber(), auditLogRequest.getDeliveryNumber());
    verify(auditLogPersisterService, times(1))
        .getAuditLogByDeliveryNumberAndStatus(
            auditLogRequest.getDeliveryNumber(),
            AuditStatus.valueOfStatus(auditLogRequest.getStatus()));
  }

  /**
   * Mock AuditLogRequest
   *
   * @param auditStatus
   * @return
   */
  private AuditLogRequest mockAuditLogRequest(AuditStatus auditStatus) {
    return new AuditLogRequest(658790751l, auditStatus.getStatus(), MockHttpHeaders.getHeaders());
  }

  /**
   * AuditLogEntity
   *
   * @param auditStatus
   * @return
   */
  private List<AuditLogEntity> mockAuditLogEntity(AuditStatus auditStatus) {
    AuditLogEntity auditLogEntity = new AuditLogEntity();
    auditLogEntity.setId(1l);
    auditLogEntity.setAsnNumber("ASN1719188772378238721");
    auditLogEntity.setDeliveryNumber(658790751l);
    auditLogEntity.setSsccNumber("SSCC167217");
    auditLogEntity.setAuditStatus(auditStatus);
    auditLogEntity.setCreatedBy("sysadmin");
    auditLogEntity.setCreatedTs(new Date());
    auditLogEntity.setCompletedBy("sysadmin");
    auditLogEntity.setCompletedTs(new Date());
    auditLogEntity.setUpdatedBy("sysadmin");
    auditLogEntity.setLastUpdatedTs(new Date());
    auditLogEntity.setVersion(1);
    return Arrays.asList(auditLogEntity);
  }
}
