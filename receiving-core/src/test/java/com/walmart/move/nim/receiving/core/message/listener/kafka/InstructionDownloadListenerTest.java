package com.walmart.move.nim.receiving.core.message.listener.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.service.DefaultKafkaInventoryEventProcessor;
import com.walmart.move.nim.receiving.data.MockInstructionDownloadEvent;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class InstructionDownloadListenerTest {

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private AppConfig appConfig;
  @Mock private DefaultKafkaInventoryEventProcessor instructionDownloadProcessor;

  @InjectMocks private InstructionDownloadListener instructionDownloadListener;

  private static final String facilityNum = "6020";
  private static final String countryCode = "US";

  @BeforeMethod
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    ReflectionTestUtils.setField(instructionDownloadListener, "gson", new Gson());
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(instructionDownloadProcessor);
  }

  @AfterMethod
  public void cleanup() {
    reset(tenantSpecificConfigReader, appConfig, instructionDownloadProcessor);
  }

  @Test
  public void testOPLabelDownloadListenerSuccess() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    instructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT, headers);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(1)).processEvent(any());
  }

  @Test
  public void testOPLabelDownloadListenerSuccess_ValidUri() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    instructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_URL_NOT_EXIST_EVENT, headers);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testOPLabelDownloadListenerInvalidEventType() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    headers.put(ReceivingConstants.EVENT_TYPE, EventType.UNKNOWN.name().getBytes());
    instructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_INVALID_EVENT, headers);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testOPLabelDownloadListenerInvalidFacility() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(0));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    instructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT, headers);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testOPLabelDownloadListenerBlobStorageNotExist() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    instructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_STORAGE_NOT_EXIST_EVENT, headers);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(1)).processEvent(any());
  }

  @Test
  public void testOPLabelDownloadListenerBlobURLNotExist() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    instructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_URL_NOT_EXIST_EVENT, headers);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testOPLabelDownloadListenerInvalidEventBlobURLNotExist() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    instructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_INVALID_EVENT_BLOB_URL_NOT_EXIST,
        headers);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testOPLabelDownloadListenerProcessError() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doThrow(new RuntimeException()).when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    instructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT, headers);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(1)).processEvent(any());
  }
}
