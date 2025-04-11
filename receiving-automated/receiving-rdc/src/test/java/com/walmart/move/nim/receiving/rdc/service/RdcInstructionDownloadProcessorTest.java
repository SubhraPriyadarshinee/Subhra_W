package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.azure.AzureStorageUtils;
import com.walmart.move.nim.receiving.core.client.orderservice.OrderServiceRestApiClient;
import com.walmart.move.nim.receiving.core.client.orderservice.model.LpnUpdateRequest;
import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.AzureBlobException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.instruction.*;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.data.MockInstructionDownloadEvent;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.utils.RdcAsyncUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.junit.Assert;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcInstructionDownloadProcessorTest {

  @Mock private AzureStorageUtils azureStorageUtils;
  @Mock private LabelDataService labelDataService;
  @Mock private RdcLabelGenerationService rdcLabelGenerationService;
  @Mock private RdcOfflineReceiveService rdcOfflineReceiveService;
  @InjectMocks private RdcInstructionDownloadProcessor rdcInstructionDownloadProcessor;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private OrderServiceRestApiClient orderServiceRestApiClient;
  @Mock RdcManagedConfig rdcManagedConfig;
  @Mock private RdcAsyncUtils rdcAsyncUtils;
  @Mock private ContainerPersisterService containerPersisterService;

  @BeforeMethod
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32679);
    ReflectionTestUtils.setField(rdcInstructionDownloadProcessor, "gson", new Gson());
    ReflectionTestUtils.setField(rdcInstructionDownloadProcessor, "labelDataBatchSize", 100);
    ReflectionTestUtils.setField(rdcInstructionDownloadProcessor, "fetchExistingLpnLimit", 1000);
  }

  @AfterMethod
  public void cleanup() {
    reset(
        azureStorageUtils,
        labelDataService,
        rdcLabelGenerationService,
        rdcManagedConfig,
        rdcAsyncUtils);
  }

  @Test
  public void testProcessLabelsGeneratedEventSuccess_WithChildContainers_BP() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_GENERATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = getInstructionsDownloadData();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(1)).saveAllAndFlush(any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
  }

  @Test
  public void testProcessLabelsGeneratedEventSuccess_LabelsUpdateEvent_CasePackSuccess()
      throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_UPDATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = getInstructionsDownloadData_LabelsUpdateEvent();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    when(labelDataService.findByTrackingIdIn(Arrays.asList("a060200000200000003889650")))
        .thenReturn(mockPrevLabelDataForLabelUpdateEvent());
    when(labelDataService.findByTrackingIdIn(Arrays.asList("a060200000200000003889651")))
        .thenReturn(Collections.emptyList());
    doNothing().when(labelDataService).saveAllAndFlush(anyList());
    doNothing()
        .when(orderServiceRestApiClient)
        .sendLabelUpdate(any(LpnUpdateRequest.class), any(HttpHeaders.class));
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    verify(orderServiceRestApiClient, times(1))
        .sendLabelUpdate(any(LpnUpdateRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testProcessLabelsGeneratedEventSuccess_LabelsUpdateEvent_CasePack_SendOPWithFailedAndSuccessListLpns()
          throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_UPDATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = getInstructionsDownloadData_LabelsUpdateEvent_MoreLPNs();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    List<String> prevTrackingIds = new ArrayList<>();
    prevTrackingIds.add("a060200000200000003889650");
    prevTrackingIds.add("a060200000200000003889660");
    when(labelDataService.findByTrackingIdIn(prevTrackingIds))
        .thenReturn(mockPrevLabelDataForLabelUpdateEventMoreLpns());
    when(labelDataService.findByTrackingIdIn(
            Arrays.asList("a060200000200000003889651", "a060200000200000003889661")))
        .thenReturn(Collections.emptyList());
    doNothing().when(labelDataService).saveAllAndFlush(anyList());
    doNothing()
        .when(orderServiceRestApiClient)
        .sendLabelUpdate(any(LpnUpdateRequest.class), any(HttpHeaders.class));
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    verify(orderServiceRestApiClient, times(1))
        .sendLabelUpdate(any(LpnUpdateRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testProcessLabelsGeneratedEventSuccess_LabelsUpdateEvent_BreakPackToBreakPackNonConSuccess()
          throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_UPDATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = getInstructionsDownloadData_LabelsUpdateEvent_BreakPackNonConveyable();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    when(labelDataService.findByTrackingIdIn(Arrays.asList("a060200000200000003889000")))
        .thenReturn(mockPrevLabelDataForLabelUpdateEventBreakPackConveyable());
    when(labelDataService.findByTrackingIdIn(
            Arrays.asList("a060200000200000003889000", "a060200000200000003889001")))
        .thenReturn(Collections.emptyList());
    doNothing().when(labelDataService).saveAllAndFlush(anyList());
    doNothing()
        .when(orderServiceRestApiClient)
        .sendLabelUpdate(any(LpnUpdateRequest.class), any(HttpHeaders.class));
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    verify(orderServiceRestApiClient, times(1))
        .sendLabelUpdate(any(LpnUpdateRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testProcessLabelsGeneratedEventSuccess_LabelsUpdateEvent_BreakPackNonConToBreakPackSuccess()
          throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_UPDATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = getInstructionsDownloadData_LabelsUpdateEvent_BreakPackConveyable();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    when(labelDataService.findByTrackingIdIn(
            Arrays.asList("001000132679203001", "001000132679202001")))
        .thenReturn(mockPrevLabelDataForLabelUpdateEventBreakPackNonConveyable());
    when(labelDataService.findByTrackingIdIn(Arrays.asList("b326790000100000003438861")))
        .thenReturn(Collections.emptyList());
    doNothing().when(labelDataService).saveAllAndFlush(anyList());
    doNothing()
        .when(orderServiceRestApiClient)
        .sendLabelUpdate(any(LpnUpdateRequest.class), any(HttpHeaders.class));
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    verify(orderServiceRestApiClient, times(1))
        .sendLabelUpdate(any(LpnUpdateRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testProcessLabelsGeneratedEventSuccess_DSDC_DoNotPublishLabelsForAutomation()
      throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_GENERATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    byte blobData[] = getInstructionsDownloadDataForDSDC();
    List<String> lpnsList = Arrays.asList("b326790000100000003438861");
    List<LabelData> labelDataList = mockLabelDataForDSDC();
    labelDataList.get(0).setTrackingId("b326790000100000003438861");
    when(labelDataService.findByTrackingIdIn(lpnsList)).thenReturn(Collections.emptyList());
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1)).findByTrackingIdIn(lpnsList);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(1)).saveAllAndFlush(any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
  }

  @Test
  public void testProcessLabelsGeneratedEventSuccessWithExistingLabelData() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_GENERATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    InstructionDownloadBlobStorageDTO blobStorageDTO =
        instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = getInstructionsDownloadData();
    List<String> lpnsList = Arrays.asList("E06938000020267142");
    List<LabelData> labelDataList = mockLabelData();
    labelDataList.get(0).setTrackingId("E06938000020267142");
    when(labelDataService.findByTrackingIdIn(lpnsList)).thenReturn(labelDataList);
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1)).findByTrackingIdIn(lpnsList);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
  }

  @Test
  public void testProcessLabelsGeneratedEventSuccessWithEmptyInstructionsData() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_GENERATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = "[]".getBytes();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(
            any(InstructionDownloadMessageDTO.class), eq(new ArrayList<>()));
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomation(
            any(InstructionDownloadMessageDTO.class), eq(new ArrayList<>()));
  }

  @Test
  public void testProcessLabelsGeneratedEventException() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_GENERATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    InstructionDownloadBlobStorageDTO blobStorageDTO =
        instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenThrow(AzureBlobException.class);
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
  }

  /** @return */
  private byte[] getInstructionsDownloadData() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_WITH_CHILD_CONTAINERS
            .getBytes();
    return data;
  }

  private byte[] getInstructionsDownloadData_LabelsUpdateEvent() {
    byte data[] =
        MockInstructionDownloadEvent
            .INSTRUCTION_DOWNLOAD_BLOB_DATA_WITH_EMPTY_CHILD_CONTAINERS_LABELS_UPDATE.getBytes();
    return data;
  }

  private byte[] getInstructionsDownloadData_LabelsUpdateEvent_MoreLPNs() {
    byte data[] =
        MockInstructionDownloadEvent
            .INSTRUCTION_DOWNLOAD_BLOB_DATA_WITH_EMPTY_CHILD_CONTAINERS_LABELS_UPDATE_MORE_LPNS
            .getBytes();
    return data;
  }

  private byte[] getInstructionsDownloadData_LabelsUpdateEvent_BreakPackNonConveyable() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_BREAK_PACK_NON_CONVEYABLE
            .getBytes();
    return data;
  }

  private byte[] getInstructionsDownloadData_LabelsUpdateEvent_BreakPackConveyable() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_BREAK_CONVEYABLE.getBytes();
    return data;
  }

  /** @return */
  private byte[] getInstructionsDownloadData_WithEmptyChildContainer() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_WITH_EMPTY_CHILD_CONTAINERS
            .getBytes();
    return data;
  }

  /** @return */
  private byte[] getInstructionsDownloadDataForDSDC() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_WITH_CHILD_CONTAINERS_FOR_DSDC
            .getBytes();
    return data;
  }

  @Test
  public void testProcessLabelsGeneratedEventEmptyBlob() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_GENERATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setBlobStorage(Collections.emptyList());
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), eq(null));
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(0)).downloadWithRetry(any(), any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), eq(null));
  }

  @Test
  public void testProcessLabelsGeneratedEventEmptyBlob_AsynPublishingToHawkeye() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_GENERATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setBlobStorage(Collections.emptyList());
    when(rdcManagedConfig.isPublishLabelsToHawkeyeByAsyncEnabled()).thenReturn(true);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomationAsync(any(InstructionDownloadMessageDTO.class), eq(null));
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(0)).downloadWithRetry(any(), any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomationAsync(any(InstructionDownloadMessageDTO.class), eq(null));
  }

  @Test
  public void testProcessLabelsCancelledEventSuccess() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("q060200000100000000767001"));
    when(labelDataService.findByTrackingIdIn(instructionDownloadMessageDTO.getTrackingIds()))
        .thenReturn(mockLabelData());
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1))
        .findByTrackingIdIn(instructionDownloadMessageDTO.getTrackingIds());
    verify(rdcLabelGenerationService, times(1))
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void testProcessLabelsCancelledEvent_ReceivingBadDataException() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("q060200000100000000767001"));
    when(labelDataService.findByTrackingIdIn(instructionDownloadMessageDTO.getTrackingIds()))
        .thenReturn(mockLabelData());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doThrow(new ReceivingBadDataException("Some Error", "Some Description"))
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    try {
      rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    } catch (ReceivingBadDataException e) {
      Assert.assertNotNull(e.getMessage());
    }
  }

  @Test
  public void testProcessLabelsCancelledEventWithEmptyLpns() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Collections.emptyList());
    when(labelDataService.findByTrackingIdIn(instructionDownloadMessageDTO.getTrackingIds()))
        .thenReturn(mockLabelData());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(0))
        .findByTrackingIdIn(instructionDownloadMessageDTO.getTrackingIds());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
  }

  @Test
  public void testProcessLabelsCancelledEventLabelDataNotExist() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("q060200000100000000767001"));
    when(labelDataService.findByTrackingIdIn(instructionDownloadMessageDTO.getTrackingIds()))
        .thenReturn(Collections.emptyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1))
        .findByTrackingIdIn(instructionDownloadMessageDTO.getTrackingIds());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
  }

  @Test
  public void testProcessLabelsCancelledEventException() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setTrackingIds(Arrays.asList("q060200000100000000767001"));
    when(labelDataService.findByTrackingIdIn(instructionDownloadMessageDTO.getTrackingIds()))
        .thenThrow(new RuntimeException(""));
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1))
        .findByTrackingIdIn(instructionDownloadMessageDTO.getTrackingIds());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
  }

  @Test
  public void testProcessPOCancelledEventSuccess() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.PO_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    when(labelDataService.fetchByPurchaseReferenceNumber(
            instructionDownloadMessageDTO.getPoNumber()))
        .thenReturn(mockLabelData());
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumber(instructionDownloadMessageDTO.getPoNumber());
    verify(rdcLabelGenerationService, times(1))
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void testProcessPOCancelledEventLabelDataNotExist() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.PO_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    when(labelDataService.fetchByPurchaseReferenceNumber(
            instructionDownloadMessageDTO.getPoNumber()))
        .thenReturn(Collections.emptyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumber(instructionDownloadMessageDTO.getPoNumber());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
  }

  @Test
  public void testProcessPOCancelledEventException() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.PO_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    when(labelDataService.fetchByPurchaseReferenceNumber(
            instructionDownloadMessageDTO.getPoNumber()))
        .thenThrow(new RuntimeException(""));
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumber(instructionDownloadMessageDTO.getPoNumber());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
  }

  @Test
  public void testProcessPOLineCancelledEventSuccess() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.PO_LINE_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setPurchaseReferenceLineNumber(1234);
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            instructionDownloadMessageDTO.getPoNumber(),
            instructionDownloadMessageDTO.getPurchaseReferenceLineNumber()))
        .thenReturn(mockLabelData());
    doNothing()
        .when(rdcLabelGenerationService)
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            instructionDownloadMessageDTO.getPoNumber(),
            instructionDownloadMessageDTO.getPurchaseReferenceLineNumber());
    verify(rdcLabelGenerationService, times(1))
        .updateLabelStatusToCancelled(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void testProcessPOLineCancelledEventLabelDataNotExist() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.PO_LINE_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            instructionDownloadMessageDTO.getPoNumber(),
            instructionDownloadMessageDTO.getPurchaseReferenceLineNumber()))
        .thenReturn(Collections.emptyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            instructionDownloadMessageDTO.getPoNumber(),
            instructionDownloadMessageDTO.getPurchaseReferenceLineNumber());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
  }

  @Test
  public void testProcessPOLineCancelledEventException() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.PO_LINE_CANCELLED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setPurchaseReferenceLineNumber(1234);
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            instructionDownloadMessageDTO.getPoNumber(),
            instructionDownloadMessageDTO.getPurchaseReferenceLineNumber()))
        .thenThrow(new RuntimeException());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            instructionDownloadMessageDTO.getPoNumber(),
            instructionDownloadMessageDTO.getPurchaseReferenceLineNumber());
    verify(labelDataService, times(0)).saveAll(any());
  }

  @Test
  public void testProcessUnknownEvent() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, "Test");
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
  }

  @Test
  public void testProcessEventException() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, "Test");
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
  }

  private List<LabelData> mockLabelData() {
    LabelData labelData = new LabelData();
    labelData.setId(1);
    labelData.setFacilityNum(32679);
    labelData.setDeliveryNumber(39380405l);
    labelData.setItemNumber(658232698l);
    labelData.setPurchaseReferenceNumber("5030140191");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setLabelSequenceNbr(20231023000100001l);
    return Arrays.asList(labelData);
  }

  private List<LabelData> mockPrevLabelDataForLabelUpdateEvent() {
    LabelData labelData = new LabelData();
    labelData.setId(1);
    labelData.setFacilityNum(32679);
    labelData.setDeliveryNumber(39380405l);
    labelData.setItemNumber(658232698l);
    labelData.setPurchaseReferenceNumber("5030140191");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setLabelSequenceNbr(20231023000100001l);
    labelData.setTrackingId("a060200000200000003889650");
    labelData.setStatus(LabelInstructionStatus.AVAILABLE.name());
    labelData.setVnpk(12);
    labelData.setWhpk(12);
    return Collections.singletonList(labelData);
  }

  private List<LabelData> mockPrevLabelDataForLabelUpdateEventBreakPackConveyable() {
    LabelData labelData = new LabelData();
    labelData.setId(1);
    labelData.setFacilityNum(32679);
    labelData.setDeliveryNumber(39380405L);
    labelData.setItemNumber(596942996L);
    labelData.setPurchaseReferenceNumber("5030140191");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setLabelSequenceNbr(10231023000100001L);
    labelData.setTrackingId("a060200000200000003889000");
    labelData.setStatus(LabelInstructionStatus.AVAILABLE.name());
    LabelDataAllocationDTO labelDataAllocationDTO = new LabelDataAllocationDTO();
    InstructionDownloadContainerDTO instructionDownloadContainerDTO =
        new InstructionDownloadContainerDTO();
    instructionDownloadContainerDTO.setTrackingId("a060200000200000003889000");
    InstructionDownloadChildContainerDTO instructionDownloadChildContainerDTO1 =
        new InstructionDownloadChildContainerDTO();
    instructionDownloadChildContainerDTO1.setTrackingId("a060200000200000003889001");
    InstructionDownloadChildContainerDTO instructionDownloadChildContainerDTO2 =
        new InstructionDownloadChildContainerDTO();
    instructionDownloadChildContainerDTO1.setTrackingId("a060200000200000003889002");
    List<InstructionDownloadChildContainerDTO> instructionDownloadChildContainerDTOS =
        new ArrayList<>();
    instructionDownloadChildContainerDTOS.add(instructionDownloadChildContainerDTO1);
    instructionDownloadChildContainerDTOS.add(instructionDownloadChildContainerDTO2);
    labelDataAllocationDTO.setChildContainers(instructionDownloadChildContainerDTOS);
    labelDataAllocationDTO.setContainer(instructionDownloadContainerDTO);
    labelData.setAllocation(labelDataAllocationDTO);
    labelData.setVnpk(6);
    labelData.setWhpk(3);
    return Collections.singletonList(labelData);
  }

  private List<LabelData> mockPrevLabelDataForLabelUpdateEventBreakPackNonConveyable() {
    LabelData labelData = new LabelData();
    List<LabelData> labelDataList = new ArrayList<>();
    labelData.setId(1);
    labelData.setFacilityNum(32679);
    labelData.setDeliveryNumber(39380405L);
    labelData.setItemNumber(596942996L);
    labelData.setPurchaseReferenceNumber("5030140191");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setLabelSequenceNbr(10231023000100001L);
    labelData.setTrackingId("001000132679203001");
    labelData.setStatus(LabelInstructionStatus.AVAILABLE.name());
    LabelDataAllocationDTO labelDataAllocationDTO = new LabelDataAllocationDTO();
    InstructionDownloadContainerDTO instructionDownloadContainerDTO =
        new InstructionDownloadContainerDTO();
    instructionDownloadContainerDTO.setTrackingId("a060200000200000003889000");
    labelDataAllocationDTO.setContainer(instructionDownloadContainerDTO);
    labelData.setAllocation(labelDataAllocationDTO);
    labelData.setVnpk(6);
    labelData.setWhpk(3);

    LabelData labelData1 = new LabelData();
    labelData1.setId(1);
    labelData1.setFacilityNum(32679);
    labelData1.setDeliveryNumber(39380405L);
    labelData1.setItemNumber(596942996L);
    labelData1.setPurchaseReferenceNumber("5030140191");
    labelData1.setPurchaseReferenceLineNumber(1);
    labelData1.setLabelSequenceNbr(10231023000100001L);
    labelData1.setTrackingId("001000132679202001");
    labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
    instructionDownloadContainerDTO.setTrackingId("a060200000200000003889000");
    labelDataAllocationDTO.setContainer(instructionDownloadContainerDTO);
    labelData1.setAllocation(labelDataAllocationDTO);
    labelData1.setVnpk(6);
    labelData1.setWhpk(3);

    labelDataList.add(labelData);
    labelDataList.add(labelData1);
    return labelDataList;
  }

  private List<LabelData> mockPrevLabelDataForLabelUpdateEventMoreLpns() {
    List<LabelData> labelDataList = new ArrayList<>();
    LabelData labelData1 = new LabelData();
    labelData1.setId(1);
    labelData1.setFacilityNum(32679);
    labelData1.setDeliveryNumber(39380405l);
    labelData1.setItemNumber(658232698l);
    labelData1.setPurchaseReferenceNumber("5030140191");
    labelData1.setPurchaseReferenceLineNumber(1);
    labelData1.setLabelSequenceNbr(20231023000100001l);
    labelData1.setTrackingId("a060200000200000003889650");
    labelData1.setVnpk(12);
    labelData1.setWhpk(12);
    labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
    LabelData labelData2 = new LabelData();
    labelData2.setId(1);
    labelData2.setFacilityNum(32679);
    labelData2.setDeliveryNumber(39380405l);
    labelData2.setItemNumber(658232698l);
    labelData2.setPurchaseReferenceNumber("5030140191");
    labelData2.setPurchaseReferenceLineNumber(1);
    labelData2.setLabelSequenceNbr(20231023000100001l);
    labelData2.setTrackingId("a060200000200000003889660");
    labelData2.setStatus(LabelInstructionStatus.COMPLETE.name());
    labelData2.setVnpk(12);
    labelData2.setWhpk(12);
    labelDataList.add(labelData1);
    labelDataList.add(labelData2);
    return labelDataList;
  }

  private List<LabelData> mockLabelDataForLabelUpdateEvent() {
    LabelData labelData = new LabelData();
    labelData.setId(1);
    labelData.setFacilityNum(32679);
    labelData.setDeliveryNumber(39380405l);
    labelData.setItemNumber(658232698l);
    labelData.setPurchaseReferenceNumber("5030140191");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setLabelSequenceNbr(20231023000100001l);
    labelData.setVnpk(12);
    labelData.setWhpk(12);
    labelData.setTrackingId("a060200000200000003889651");
    labelData.setStatus(LabelInstructionStatus.AVAILABLE.name());
    return Collections.singletonList(labelData);
  }

  private List<LabelData> mockLabelDataForDSDC() {
    LabelData labelData = new LabelData();
    labelData.setId(1);
    labelData.setFacilityNum(32679);
    labelData.setDeliveryNumber(39380405l);
    labelData.setItemNumber(658232698l);
    labelData.setPurchaseReferenceNumber("5030140191");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setLabelSequenceNbr(20231023000100001l);
    labelData.setAsnNumber("323232323");
    labelData.setSscc("00032323223232323");
    return Arrays.asList(labelData);
  }

  /**
   * labelDataList is donwloaded and persisted
   *
   * @throws Exception
   */
  @Test
  public void testProcessLabelsGeneratedEventForOfflineRcvSuccess() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = getInstructionsDownloadDataForOfflineRcv();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(1)).saveAllAndFlush(any());
    verify(rdcOfflineReceiveService, times(1))
        .autoReceiveContainersForOfflineReceiving(any(), any());
  }

  /**
   * labelDataList is donwloaded and persisted - with child
   *
   * @throws Exception
   */
  @Test
  public void testProcessLabelsGeneratedEventForOfflineRcvSuccess_withChild() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = getInstructionsDownloadDataForOfflineRcvWithChild();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(1)).saveAllAndFlush(any());
    verify(rdcOfflineReceiveService, times(1))
        .autoReceiveContainersForOfflineReceiving(any(), any());
  }

  /**
   * labelDataList is donwloaded and persisted for WPM
   *
   * @throws Exception
   */
  @Test
  public void testProcessLabelsGeneratedEventForOfflineRcvSuccessForWpm() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = getInstructionsDownloadDataForOfflineRcvForWpm();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    ArrayList wpmSites = new ArrayList();
    wpmSites.add("6014");
    when(rdcManagedConfig.getWpmSites()).thenReturn(wpmSites);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(1)).saveAllAndFlush(any());
    verify(rdcOfflineReceiveService, times(1))
        .autoReceiveContainersForOfflineReceiving(any(), any());
  }

  @Test
  public void testProcessLabelsGeneratedEventForOfflineRcvSuccessForRdc2Rdc() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] = getInstructionsDownloadDataForOfflineRcvForWpm();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    ArrayList<String> rdc2rdcSites = new ArrayList();
    rdc2rdcSites.add("6014");
    when(rdcManagedConfig.getRdc2rdcSites()).thenReturn(rdc2rdcSites);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(1)).saveAllAndFlush(any());
    verify(rdcOfflineReceiveService, times(1))
        .autoReceiveContainersForOfflineReceiving(any(), any());
  }

  private byte[] getInstructionsDownloadDataForOfflineRcv() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV.getBytes();
    return data;
  }

  private byte[] getInstructionsDownloadDataForOfflineRcvWithChild() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV_WITH_CHILD
            .getBytes();
    return data;
  }

  private byte[] getInstructionsDownloadDataForOfflineRcvForWpm() {
    byte data[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV_WPM.getBytes();
    return data;
  }

  @Test
  public void testProcessLabelsGeneratedEventForOfflineRcvEmptyBlob() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.setBlobStorage(Collections.emptyList());
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), eq(null));
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(0)).downloadWithRetry(any(), any());
    verify(rdcLabelGenerationService, times(0))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), eq(null));
    verify(rdcLabelGenerationService, times(0))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), eq(null));
    verify(rdcOfflineReceiveService, times(0))
        .autoReceiveContainersForOfflineReceiving(any(), any());
  }

  @Test
  public void testProcessLabelsGeneratedEventForOfflineRcvException() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    InstructionDownloadBlobStorageDTO blobStorageDTO =
        instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenThrow(AzureBlobException.class);
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
    verify(rdcOfflineReceiveService, times(0))
        .autoReceiveContainersForOfflineReceiving(any(), any());
  }

  @Test
  public void testProcessLabelsGeneratedEventForOfflineRcvWithoutChildContainer() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV.getBytes();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(1)).saveAllAndFlush(any());
    verify(rdcOfflineReceiveService, times(1))
        .autoReceiveContainersForOfflineReceiving(any(), any());
  }

  /**
   * This test validates ProcessLabelsGeneratedEventForOffline() without child Container when
   * enablePrepareConsolidatedContainers flag is false
   *
   * @throws Exception
   */
  @Test
  public void testProcessLabelsGeneratedEventForOfflineRcvWithoutChild_enableCC_false()
      throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV.getBytes();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(false);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Collections.EMPTY_LIST);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(1)).saveAllAndFlush(any());
    verify(rdcOfflineReceiveService, times(1))
        .autoReceiveContainersForOfflineReceiving(any(), any());
  }

  /**
   * This test validates ProcessLabelsGeneratedEventForOffline() without child Container when
   * enablePrepareConsolidatedContainers flag is true
   *
   * @throws Exception
   */
  @Test
  public void testProcessLabelsGeneratedEventForOfflineRcvWithoutChild_enableCC_true()
      throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.OFFLINE_RECEIVING.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    instructionDownloadMessageDTO.getBlobStorage().stream().findFirst().get();
    byte blobData[] =
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_DATA_FOR_OFFLINE_RCV.getBytes();
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    when(rdcManagedConfig.getEnableSingleTransactionForOffline()).thenReturn(true);

    when(rdcManagedConfig.getDcListEligibleForPrepareConsolidatedContainer())
        .thenReturn(Collections.EMPTY_LIST);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(labelDataService, times(0)).saveAllAndFlush(any());
    verify(rdcOfflineReceiveService, times(1))
        .autoReceiveContainersForOfflineReceiving(any(), any());
  }

  @Test
  public void testProcessLabelsGeneratedEventTrackingAlreadyExist() throws Exception {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.add(ReceivingConstants.EVENT_TYPE, EventType.LABELS_GENERATED.name());
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    byte blobData[] = getInstructionsDownloadData();
    List<String> lpnsList = Arrays.asList("E06938000020267142");
    List<LabelData> labelDataList = mockLabelData();
    labelDataList.get(0).setTrackingId("E06938000020267142");
    when(labelDataService.findByTrackingIdIn(lpnsList)).thenReturn(labelDataList);
    when(azureStorageUtils.downloadWithRetry(any(), any())).thenReturn(blobData);
    doNothing()
        .when(rdcLabelGenerationService)
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
    rdcInstructionDownloadProcessor.processEvent(instructionDownloadMessageDTO);
    verify(labelDataService, times(1)).findByTrackingIdIn(lpnsList);
    verify(azureStorageUtils, times(1)).downloadWithRetry(any(), any());
    verify(rdcLabelGenerationService, times(1))
        .processLabelsForAutomation(any(InstructionDownloadMessageDTO.class), anyList());
  }
}
