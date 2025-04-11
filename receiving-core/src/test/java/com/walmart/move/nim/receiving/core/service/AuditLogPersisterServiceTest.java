package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.core.common.AuditStatus;
import com.walmart.move.nim.receiving.core.entity.AuditLogEntity;
import com.walmart.move.nim.receiving.core.repositories.AuditLogRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AuditLogPersisterServiceTest {
  @Mock AuditLogRepository auditLogRepository;
  @InjectMocks AuditLogPersisterService auditLogPersisterService;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod()
  public void resetMocks() {
    reset(auditLogRepository);
  }

  @Test
  public void testGetAuditLogByDeliveryNumberAndStatus() {
    Long deliveryNumber = 12122L;
    AuditStatus auditStatus = AuditStatus.PENDING;
    AuditLogEntity auditLogEntity = new AuditLogEntity();
    List<AuditLogEntity> auditLogEntities = new ArrayList<>();
    auditLogEntity.setDeliveryNumber(322323L);
    auditLogEntity.setAuditStatus(AuditStatus.PENDING);
    auditLogEntities.add(auditLogEntity);
    when(auditLogRepository.findByDeliveryNumberAndAuditStatus(anyLong(), any()))
        .thenReturn(auditLogEntities);
    List<AuditLogEntity> response =
        auditLogPersisterService.getAuditLogByDeliveryNumberAndStatus(deliveryNumber, auditStatus);

    assertNotNull(response);
  }

  @Test
  public void testGetAuditTagCountByDeliveryNumberAndAuditStatus() {
    Long deliveryNumber = 12122L;
    AuditStatus auditStatus = AuditStatus.PENDING;
    when(auditLogRepository.countByDeliveryNumberAndAuditStatus(anyLong(), any())).thenReturn(1);
    int auditCount =
        auditLogPersisterService.getAuditTagCountByDeliveryNumberAndAuditStatus(
            deliveryNumber, auditStatus);
    assertEquals(auditCount, 1);
  }

  @Test
  public void testSaveAuditLogData() {
    when(auditLogRepository.save(any(AuditLogEntity.class))).thenReturn(new AuditLogEntity());
    AuditLogEntity auditLogEntity = auditLogPersisterService.saveAuditLogData(new AuditLogEntity());
    assertNotNull(auditLogEntity);
    verify(auditLogRepository, times(1)).save(any(AuditLogEntity.class));
  }

  @Test
  public void testGetAuditDetailsByAsnNumberAndDeliveryNumberAndStatus() {
    String asnNumber = "00792393289238";
    String ssccNumber = "763288";
    AuditStatus auditStatus = AuditStatus.PENDING;
    when(auditLogRepository.findByAsnNumberAndSsccNumberAndAuditStatus(
            asnNumber, ssccNumber, auditStatus))
        .thenReturn(new AuditLogEntity());
    AuditLogEntity auditLogEntity =
        auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            asnNumber, ssccNumber, auditStatus);
    assertNotNull(auditLogEntity);
    verify(auditLogRepository, times(1))
        .findByAsnNumberAndSsccNumberAndAuditStatus(asnNumber, ssccNumber, auditStatus);
  }
}
