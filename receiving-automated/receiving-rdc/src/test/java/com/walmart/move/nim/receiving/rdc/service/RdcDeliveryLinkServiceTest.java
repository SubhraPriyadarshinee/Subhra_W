package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeLabelGroupUpdateRequest;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.LabelReadinessRequest;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.LabelReadinessResponse;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.DeliveryLinkRequest;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.GdmDeliveryStatusUpdateEvent;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.LocalDateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

public class RdcDeliveryLinkServiceTest {
  @InjectMocks RdcDeliveryLinkService rdcDeliveryLinkService;
  @Mock private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Mock private LabelDataService labelDataService;
  @Mock DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock DeliveryService deliveryService;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("us");
    ReflectionTestUtils.setField(rdcDeliveryLinkService, "gson", new Gson());
  }

  @After
  public void resetMocks() {
    reset(hawkeyeRestApiClient, labelDataService);
  }

  @Test
  public void testValidateReadinessAndLinkDelivery_Success() {
    DeliveryLinkRequest deliveryLinkRequest = getDeliveryLinkRequest();
    deliveryLinkRequest.setDeliveryStatus(DeliveryStatus.ARV.toString());
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(hawkeyeRestApiClient)
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), eq(null), anyMap());
    doReturn(2).when(labelDataService).fetchLabelCountByDeliveryNumber(anyLong());
    doReturn(1).when(labelDataService).fetchItemCountByDeliveryNumber(anyLong());
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(hawkeyeRestApiClient)
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false);
    ResponseEntity<String> response =
        rdcDeliveryLinkService.validateReadinessAndLinkDelivery(
            deliveryLinkRequest, MockHttpHeaders.getHeaders());
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    verify(hawkeyeRestApiClient, times(1))
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    verify(labelDataService, times(1)).fetchLabelCountByDeliveryNumber(anyLong());
    verify(labelDataService, times(1)).fetchItemCountByDeliveryNumber(anyLong());
    verify(hawkeyeRestApiClient, times(1))
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
    verify(hawkeyeRestApiClient, times(1))
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testValidateReadinessAndLinkDelivery_GdmDeliveryStatusUpdateEvent_Success() {
    DeliveryLinkRequest deliveryLinkRequest = getDeliveryLinkRequest();
    deliveryLinkRequest.setDeliveryStatus(DeliveryStatus.ARV.toString());
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(hawkeyeRestApiClient)
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(),
            eq(ReceivingConstants.IS_DELIVERY_UPDATE_STATUS_ENABLED_BY_HTTP),
            anyBoolean());
    doReturn(new GdmDeliveryStatusUpdateEvent())
        .when(deliveryService)
        .updateDeliveryStatusToOpen(anyLong(), anyMap());
    doReturn(2).when(labelDataService).fetchLabelCountByDeliveryNumber(anyLong());
    doReturn(1).when(labelDataService).fetchItemCountByDeliveryNumber(anyLong());
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(hawkeyeRestApiClient)
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false);
    ResponseEntity<String> response =
        rdcDeliveryLinkService.validateReadinessAndLinkDelivery(
            deliveryLinkRequest, MockHttpHeaders.getHeaders());
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    verify(hawkeyeRestApiClient, times(1))
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    verify(deliveryService, times(1)).updateDeliveryStatusToOpen(anyLong(), anyMap());
    verify(labelDataService, times(1)).fetchLabelCountByDeliveryNumber(anyLong());
    verify(labelDataService, times(1)).fetchItemCountByDeliveryNumber(anyLong());
    verify(hawkeyeRestApiClient, times(1))
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
    verify(hawkeyeRestApiClient, times(1))
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
  }

  // @Test(expected = ReceivingBadDataException.class)
  public void testValidateReadinessAndLinkDelivery_NoLabelsFound() {
    DeliveryLinkRequest deliveryLinkRequest = getDeliveryLinkRequest();
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(hawkeyeRestApiClient)
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false);
    doReturn(0).when(labelDataService).fetchLabelCountByDeliveryNumber(anyLong());
    doReturn(0).when(labelDataService).fetchItemCountByDeliveryNumber(anyLong());
    doReturn(0)
        .when(labelDataService)
        .fetchLabelCountByDeliveryNumberInLabelDownloadEvent(anyLong());
    doReturn(0)
        .when(labelDataService)
        .fetchItemCountByDeliveryNumberInLabelDownloadEvent(anyLong());
    ResponseEntity<String> response =
        rdcDeliveryLinkService.validateReadinessAndLinkDelivery(
            deliveryLinkRequest, MockHttpHeaders.getHeaders());
  }

  @Test
  public void testValidateReadinessAndLinkDelivery_NoLabelsFound_LinkingSuccess() {
    DeliveryLinkRequest deliveryLinkRequest = getDeliveryLinkRequest();
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(hawkeyeRestApiClient)
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false);
    doReturn(0).when(labelDataService).fetchLabelCountByDeliveryNumber(anyLong());
    doReturn(0).when(labelDataService).fetchItemCountByDeliveryNumber(anyLong());
    ResponseEntity<String> response =
        rdcDeliveryLinkService.validateReadinessAndLinkDelivery(
            deliveryLinkRequest, MockHttpHeaders.getHeaders());
    verify(labelDataService, times(0)).fetchLabelCountByDeliveryNumber(anyLong());
    verify(labelDataService, times(0)).fetchItemCountByDeliveryNumber(anyLong());
  }

  @Test
  public void testValidateReadinessAndLinkDelivery_LocationLinkedToSameDelivery() {
    DeliveryLinkRequest deliveryLinkRequest = getDeliveryLinkRequest();
    LabelReadinessResponse labelReadinessResponse = getLabelReadinessResponse();
    doReturn(new ResponseEntity<>(new Gson().toJson(labelReadinessResponse), HttpStatus.CONFLICT))
        .when(hawkeyeRestApiClient)
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(hawkeyeRestApiClient)
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
    ResponseEntity<String> response =
        rdcDeliveryLinkService.validateReadinessAndLinkDelivery(
            deliveryLinkRequest, MockHttpHeaders.getHeaders());
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    verify(hawkeyeRestApiClient, times(1))
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    verify(labelDataService, times(0)).fetchLabelCountByDeliveryNumber(anyLong());
    verify(labelDataService, times(0)).fetchItemCountByDeliveryNumber(anyLong());
    verify(hawkeyeRestApiClient, times(0))
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void testValidateReadinessAndLinkDelivery_LocationLinkedToDifferentDelivery() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.remove(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    headers.set(ReceivingConstants.WMT_MSG_TIMESTAMP, LocalDateTime.now().toString());
    DeliveryLinkRequest deliveryLinkRequest = getDeliveryLinkRequest();
    LabelReadinessResponse labelReadinessResponse = getLabelReadinessResponse();
    labelReadinessResponse.setGroupNumber("45678");
    doReturn(new ResponseEntity<>(new Gson().toJson(labelReadinessResponse), HttpStatus.CONFLICT))
        .when(hawkeyeRestApiClient)
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    doReturn(2).when(labelDataService).fetchLabelCountByDeliveryNumber(anyLong());
    doReturn(1).when(labelDataService).fetchItemCountByDeliveryNumber(anyLong());
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(hawkeyeRestApiClient)
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
    ResponseEntity<String> response =
        rdcDeliveryLinkService.validateReadinessAndLinkDelivery(deliveryLinkRequest, headers);
  }

  @Test
  public void testValidateReadinessAndLinkDelivery_NoLabelsFoundInLabelData_DuplicateLpn() {
    DeliveryLinkRequest deliveryLinkRequest = getDeliveryLinkRequest();
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(hawkeyeRestApiClient)
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false);
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(hawkeyeRestApiClient)
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
    doReturn(0).when(labelDataService).fetchLabelCountByDeliveryNumber(anyLong());
    doReturn(0).when(labelDataService).fetchItemCountByDeliveryNumber(anyLong());
    doReturn(20)
        .when(labelDataService)
        .fetchLabelCountByDeliveryNumberInLabelDownloadEvent(anyLong());
    doReturn(1)
        .when(labelDataService)
        .fetchItemCountByDeliveryNumberInLabelDownloadEvent(anyLong());
    ResponseEntity<String> response =
        rdcDeliveryLinkService.validateReadinessAndLinkDelivery(
            deliveryLinkRequest, MockHttpHeaders.getHeaders());
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    verify(hawkeyeRestApiClient, times(1))
        .checkLabelGroupReadinessStatus(any(LabelReadinessRequest.class), any(HttpHeaders.class));
    verify(labelDataService, times(1)).fetchLabelCountByDeliveryNumber(anyLong());
    verify(labelDataService, times(1)).fetchItemCountByDeliveryNumber(anyLong());
    verify(labelDataService, times(1))
        .fetchLabelCountByDeliveryNumberInLabelDownloadEvent(anyLong());
    verify(labelDataService, times(1))
        .fetchItemCountByDeliveryNumberInLabelDownloadEvent(anyLong());
    verify(hawkeyeRestApiClient, times(1))
        .sendLabelGroupUpdateToHawkeye(
            any(HawkeyeLabelGroupUpdateRequest.class), anyLong(), any(HttpHeaders.class));
  }

  private DeliveryLinkRequest getDeliveryLinkRequest() {
    return DeliveryLinkRequest.builder()
        .deliveryNumber("12345")
        .locationId("DOOR_111")
        .deliveryStatus(DeliveryStatus.OPEN.toString())
        .build();
  }

  private LabelReadinessResponse getLabelReadinessResponse() {
    return LabelReadinessResponse.builder()
        .groupNumber("12345")
        .locationId("DOOR_111")
        .groupType("RCV_DA")
        .build();
  }
}
