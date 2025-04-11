package com.walmart.move.nim.receiving.rdc.service;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeItemUpdateRequest;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.service.DeliveryItemOverrideService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.LabelDownloadEventService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.client.ngr.NgrRestApiClient;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcItemServiceHandlerTest {
  @InjectMocks RdcItemServiceHandler rdcItemServiceHandler;
  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;
  @Mock private NgrRestApiClient ngrRestApiClient;
  @Mock private ItemUpdateUtils itemUpdateUtils;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock LabelDataService labelDataService;
  @Mock LabelDownloadEventService labelDownloadEventService;
  @Mock AppConfig appConfig;
  @Captor private ArgumentCaptor<List<LabelData>> labelDataListCaptor;
  @Mock private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf("32818"));
    TenantContext.setFacilityCountryCode("us");
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        ngrRestApiClient,
        deliveryItemOverrideService,
        labelDataService,
        labelDownloadEventService,
        tenantSpecificConfigReader);
  }

  @Test
  public void testUpdateItemProperties_Success() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders("32818", "US");
    doNothing()
        .when(ngrRestApiClient)
        .updateItemProperties(any(ItemOverrideRequest.class), any(HttpHeaders.class));
    doNothing()
        .when(deliveryItemOverrideService)
        .updateDeliveryItemOverride(any(ItemOverrideRequest.class), any(HttpHeaders.class));
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 32432L, "B", "C", "B", "C", "234234", 1, false);
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);
    rdcItemServiceHandler.updateItemProperties(itemOverrideRequest, httpHeaders);
    verify(ngrRestApiClient, times(1)).updateItemProperties(itemOverrideRequest, httpHeaders);
    verify(deliveryItemOverrideService, times(1))
        .updateDeliveryItemOverride(itemOverrideRequest, httpHeaders);
    verify(rdcReceivingUtils, times(1)).isNGRServicesEnabled();
  }

  @Test
  public void
      testUpdateItemProperties_Success_IgnoreItemCacheUpdate_WhenTheRequestOriginatedFromNGR() {
    doNothing()
        .when(ngrRestApiClient)
        .updateItemProperties(any(ItemOverrideRequest.class), any(HttpHeaders.class));
    doNothing()
        .when(deliveryItemOverrideService)
        .updateDeliveryItemOverride(any(ItemOverrideRequest.class), any(HttpHeaders.class));
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 32432L, "B", "C", "B", "C", "234234", 1, false);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.REQUEST_ORIGINATOR, "NGR");
    rdcItemServiceHandler.updateItemProperties(itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(ngrRestApiClient, times(0))
        .updateItemProperties(itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideService, times(1))
        .updateDeliveryItemOverride(itemOverrideRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testUpdateItemProperties_ThrowsReceivingInternalException() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders("32818", "US");
    doThrow(new ReceivingInternalException("Mock_error", "Mock_error"))
        .when(ngrRestApiClient)
        .updateItemProperties(any(ItemOverrideRequest.class), any(HttpHeaders.class));
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 32432L, "B", "C", "B", "C", "234234", 1, false);
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);
    rdcItemServiceHandler.updateItemProperties(itemOverrideRequest, httpHeaders);
    verify(ngrRestApiClient, times(1)).updateItemProperties(itemOverrideRequest, httpHeaders);
    verify(rdcReceivingUtils, times(1)).isNGRServicesEnabled();
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateItemProperties_ThrowsReceivingBadDataException() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders("32818", "US");
    doThrow(new ReceivingBadDataException("mock_error", "mock_error"))
        .when(ngrRestApiClient)
        .updateItemProperties(any(ItemOverrideRequest.class), any(HttpHeaders.class));
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 32432L, "B", "C", "B", "C", "234234", 1, false);
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);
    rdcItemServiceHandler.updateItemProperties(itemOverrideRequest, httpHeaders);
    verify(ngrRestApiClient, times(1)).updateItemProperties(itemOverrideRequest, httpHeaders);
    verify(rdcReceivingUtils, times(1)).isNGRServicesEnabled();
  }

  @Test
  public void testUpdateItemProperties_Success_IsAtlasItem_DoNotUpdateInNGR() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders("32818", "US");
    doNothing()
        .when(deliveryItemOverrideService)
        .updateDeliveryItemOverride(any(ItemOverrideRequest.class), any(HttpHeaders.class));
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 32432L, "B", "C", "B", "C", "234234", 1, true);
    rdcItemServiceHandler.updateItemProperties(itemOverrideRequest, httpHeaders);
    verify(ngrRestApiClient, times(0)).updateItemProperties(itemOverrideRequest, httpHeaders);
    verify(deliveryItemOverrideService, times(1))
        .updateDeliveryItemOverride(itemOverrideRequest, httpHeaders);
  }

  @Test
  public void testUpdateRejectReason_removeHazmat() {
    HttpHeaders httpHeaders = new HttpHeaders();
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, null, "B", "C", "B", "N", null, 1, true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(any(), any())).thenReturn(true);
    ArgumentCaptor<HawkeyeItemUpdateRequest> captor =
        ArgumentCaptor.forClass(HawkeyeItemUpdateRequest.class);
    doNothing()
        .when(hawkeyeRestApiClient)
        .sendItemUpdateToHawkeye(captor.capture(), any(HttpHeaders.class));
    List<LabelDownloadEvent> labelDownloadEvents = new ArrayList<>();
    LabelDownloadEvent labelDownloadEvent = new LabelDownloadEvent();
    labelDownloadEvent.setRejectReason(RejectReason.HAZMAT);
    labelDownloadEvents.add(labelDownloadEvent);
    doReturn(labelDownloadEvents).when(labelDownloadEventService).findByItemNumber(anyLong());
    rdcItemServiceHandler.updateItemRejectReason(null, itemOverrideRequest, httpHeaders);
    assertNull(labelDownloadEvents.get(0).getRejectReason());
    assertEquals(captor.getValue().getReject(), StringUtils.EMPTY);
    assertNull(captor.getValue().getGroupNumber());
  }

  @Test
  public void testUpdateRejectReason_ItemNumber() {
    ItemOverrideRequest itemOverrideRequest =
        ItemOverrideRequest.builder().itemNumber(123456L).deliveryNumber(345678L).build();
  }

  @Test
  public void testUpdateRejectReason_LabelDownloadEventWithItemNumber() {
    HttpHeaders httpHeaders = new HttpHeaders();
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, null, "B", "C", "B", "N", null, 1, true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(any(), any())).thenReturn(true);
    ArgumentCaptor<HawkeyeItemUpdateRequest> captor =
        ArgumentCaptor.forClass(HawkeyeItemUpdateRequest.class);
    doNothing()
        .when(hawkeyeRestApiClient)
        .sendItemUpdateToHawkeye(captor.capture(), any(HttpHeaders.class));
    doReturn(Collections.singletonList(getMockLabelData()))
        .when(labelDataService)
        .findByItemNumberAndStatus(anyLong(), anyString());
    doReturn(null).when(labelDataService).saveAll(labelDataListCaptor.capture());
    List<LabelDownloadEvent> labelDownloadEvents = new ArrayList<>();
    LabelDownloadEvent labelDownloadEvent = new LabelDownloadEvent();
    labelDownloadEvent.setRejectReason(RejectReason.ITEM_BLOCKED);
    labelDownloadEvents.add(labelDownloadEvent);
    doReturn(labelDownloadEvents).when(labelDownloadEventService).findByItemNumber(anyLong());
    rdcItemServiceHandler.updateItemRejectReason(null, itemOverrideRequest, httpHeaders);
    assertNull(labelDownloadEvents.get(0).getRejectReason());
    assertEquals(captor.getValue().getReject(), StringUtils.EMPTY);
    assertNull(captor.getValue().getGroupNumber());
  }

  @Test
  public void testUpdateRejectReason_LabelDownloadEventWithDeliveryNumber() {
    HttpHeaders httpHeaders = new HttpHeaders();
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, null, "B", "C", "B", "N", null, 1, true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    ArgumentCaptor<HawkeyeItemUpdateRequest> captor =
        ArgumentCaptor.forClass(HawkeyeItemUpdateRequest.class);
    doNothing()
        .when(hawkeyeRestApiClient)
        .sendItemUpdateToHawkeye(captor.capture(), any(HttpHeaders.class));
    doReturn(Collections.singletonList(getMockLabelData()))
        .when(labelDataService)
        .findByDeliveryNumberAndItemNumberAndStatus(anyLong(), anyLong(), anyString());
    doReturn(null).when(labelDataService).saveAll(labelDataListCaptor.capture());
    List<LabelDownloadEvent> labelDownloadEvents = new ArrayList<>();
    LabelDownloadEvent labelDownloadEvent = new LabelDownloadEvent();
    labelDownloadEvent.setRejectReason(RejectReason.ITEM_BLOCKED);
    LabelDownloadEvent labelDownloadEvent2 = new LabelDownloadEvent();
    labelDownloadEvent2.setRejectReason(RejectReason.RDC_NONCON);
    labelDownloadEvents.add(labelDownloadEvent);
    labelDownloadEvents.add(labelDownloadEvent2);
    doReturn(labelDownloadEvents)
        .when(labelDownloadEventService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    doReturn(labelDownloadEvents).when(labelDownloadEventService).findByItemNumber(anyLong());
    itemOverrideRequest.setDeliveryNumber(12345L);
    rdcItemServiceHandler.updateItemRejectReason(
        RejectReason.RDC_NONCON, itemOverrideRequest, httpHeaders);
    assertEquals(labelDownloadEvents.get(0).getRejectReason(), RejectReason.RDC_NONCON);
    assertEquals(labelDownloadEvents.get(1).getRejectReason(), RejectReason.RDC_NONCON);
    assertEquals(captor.getValue().getReject(), RejectReason.RDC_NONCON.getRejectCode());
  }

  @Test
  public void testUpdateRejectReason_throws_ReceivingInternalException() {
    HttpHeaders httpHeaders = new HttpHeaders();
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, null, "B", "C", "B", "N", null, 1, true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(any(), any())).thenReturn(true);
    doThrow(new ReceivingInternalException("mock error", "mock_error"))
        .when(hawkeyeRestApiClient)
        .sendItemUpdateToHawkeye(any(), any(HttpHeaders.class));
    rdcItemServiceHandler.updateItemRejectReason(null, itemOverrideRequest, httpHeaders);
    verify(labelDownloadEventService, times(0))
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    verify(labelDownloadEventService, times(0)).findByItemNumber(anyLong());
    verify(labelDownloadEventService, times(0)).saveAll(anyList());
  }

  @Test
  public void testUpdateRejectReason_throws_ReceivingBadDataException() {
    HttpHeaders httpHeaders = new HttpHeaders();
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, null, "B", "C", "B", "N", null, 1, true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(any(), any())).thenReturn(true);
    doThrow(new ReceivingBadDataException("mock error", "mock_error"))
        .when(hawkeyeRestApiClient)
        .sendItemUpdateToHawkeye(any(), any(HttpHeaders.class));
    rdcItemServiceHandler.updateItemRejectReason(null, itemOverrideRequest, httpHeaders);
    verify(labelDownloadEventService, times(0))
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    verify(labelDownloadEventService, times(0)).findByItemNumber(anyLong());
    verify(labelDownloadEventService, times(0)).saveAll(anyList());
  }

  public static LabelData getMockLabelData() {
    return LabelData.builder()
        .deliveryNumber(94769060L)
        .purchaseReferenceNumber("3615852071")
        .isDAConveyable(Boolean.TRUE)
        .itemNumber(566051127L)
        .lpnsCount(6)
        .labelSequenceNbr(20231016000100001L)
        .labelType(LabelType.ORDERED)
        .build();
  }
}
