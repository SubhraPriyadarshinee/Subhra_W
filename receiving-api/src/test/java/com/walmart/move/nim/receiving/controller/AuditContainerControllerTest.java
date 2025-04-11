package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.core.common.AuditStatus;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackRequest;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackResponse;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogResponse;
import com.walmart.move.nim.receiving.core.service.DefaultAuditLogProcessor;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.service.RdcAtlasDsdcService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AuditContainerControllerTest {

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DefaultAuditLogProcessor defaultAuditLogProcessor;
  @InjectMocks private AuditContainerController auditContainerController;
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private @Mock RdcAtlasDsdcService rdcAtlasDsdcService;

  @BeforeClass
  public void init() {
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(tenantSpecificConfigReader, defaultAuditLogProcessor, rdcAtlasDsdcService);
  }

  @Test
  public void testGetAuditLogs() {
    when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any()))
        .thenReturn(defaultAuditLogProcessor);
    when(defaultAuditLogProcessor.getAuditLogs(any())).thenReturn(mockAuditLogResponse());
    AuditLogResponse auditLogResponse =
        auditContainerController.getAuditLogs(
            658790751l, AuditStatus.COMPLETED.getStatus(), httpHeaders);
    assertNotNull(auditLogResponse);
    verify(tenantSpecificConfigReader, times(1)).getConfiguredInstance(any(), any(), any());
    verify(defaultAuditLogProcessor, times(1)).getAuditLogs(any());
  }

  @Test
  public void testReceivePack() throws Exception {
    String asnNumber = "43432323";
    String packNumber = "00000301720095496316";
    when(rdcAtlasDsdcService.receivePack(any(ReceivePackRequest.class), any(HttpHeaders.class)))
        .thenReturn(mockReceivePackResponse());
    ReceivePackResponse receivePackResponse =
        auditContainerController.receivePack(asnNumber, packNumber, httpHeaders);
    assertNotNull(receivePackResponse);
    verify(rdcAtlasDsdcService, times(1))
        .receivePack(any(ReceivePackRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testUpdatePack() throws Exception {
    String asnNumber = "43432323";
    String packNumber = "00000301720095496316";
    ReceivePackRequest receivePackRequest = new ReceivePackRequest(asnNumber, packNumber, null);
    when(rdcAtlasDsdcService.updatePack(any(ReceivePackRequest.class), any(HttpHeaders.class)))
        .thenReturn(String.format(RdcConstants.CANCEL_AUDIT_PACK_SUCCESS_MESSAGE, packNumber));
    ResponseEntity<String> response =
        auditContainerController.updateAuditPackStatus(receivePackRequest, httpHeaders);
    assertNotNull(response);
    verify(rdcAtlasDsdcService, times(1))
        .updatePack(any(ReceivePackRequest.class), any(HttpHeaders.class));
  }

  private AuditLogResponse mockAuditLogResponse() {
    AuditLogResponse auditLogResponse = new AuditLogResponse();
    return auditLogResponse;
  }

  private ReceivePackResponse mockReceivePackResponse() {
    return ReceivePackResponse.builder().build();
  }
}
