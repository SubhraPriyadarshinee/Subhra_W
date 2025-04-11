package com.walmart.move.nim.receiving.core.message.listener.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * This is a test class for OfflineInstructionDownloadListener for Kafka topic b/w OP and RCV for
 * Offline Receiving flow. Author: s0g0g7u
 */
public class OfflineInstructionDownloadListenerTest {

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private AppConfig appConfig;
  @Mock private DefaultKafkaInventoryEventProcessor instructionDownloadProcessor;
  @Mock private Acknowledgment acknowledgment;
  @InjectMocks private OfflineInstructionDownloadListener offlineInstructionDownloadListener;

  private static final String facilityNum = "6020";
  private static final String countryCode = "US";

  @BeforeMethod
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    ReflectionTestUtils.setField(offlineInstructionDownloadListener, "gson", new Gson());
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(instructionDownloadProcessor);
  }

  @AfterMethod
  public void cleanup() {
    reset(tenantSpecificConfigReader, appConfig, instructionDownloadProcessor);
  }

  /**
   * Success for LABELS_GENERATED event type
   *
   * @throws Exception
   */
  @Test
  public void testLabelDownloadListenerSuccess() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    offlineInstructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT, headers, acknowledgment);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(1)).processEvent(any());
    verify(acknowledgment, times(1)).acknowledge();
  }

  /**
   * Success for OFFLINE_RECEIVING event type
   *
   * @throws Exception
   */
  @Test
  public void testOfflineLabelDownloadListenerSuccess() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    offlineInstructionDownloadListener.listen(
        MockInstructionDownloadEvent.OFFLINE_INSTRUCTION_DOWNLOAD_EVENT, headers, acknowledgment);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(1)).processEvent(any());
    verify(acknowledgment, times(1)).acknowledge();
  }

  /**
   * Invalid URI is passed
   *
   * @throws Exception
   */
  @Test
  public void testOfflineLabelDownloadListenerSuccess_ValidUri() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    offlineInstructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_URL_NOT_EXIST_EVENT,
        headers,
        acknowledgment);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(0)).processEvent(any());
    verify(acknowledgment, times(1)).acknowledge();
  }

  /**
   * Invalid event type is passed
   *
   * @throws Exception
   */
  @Test
  public void testOfflineLabelDownloadListenerInvalidEventType() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    headers.put(ReceivingConstants.EVENT_TYPE, EventType.UNKNOWN.name().getBytes());
    offlineInstructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_INVALID_EVENT, headers, acknowledgment);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(0)).processEvent(any());
    verify(acknowledgment, times(1)).acknowledge();
  }

  /**
   * Inavlid facility number is passed for which the offline instruction download listener is not
   * enabled
   *
   * @throws Exception
   */
  @Test
  public void testOfflineLabelDownloadListenerInvalidFacility() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(0));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    offlineInstructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT, headers, acknowledgment);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(0)).processEvent(any());
    verify(acknowledgment, times(1)).acknowledge();
  }

  /**
   * If no blob storage exists in the kafka message
   *
   * @throws Exception
   */
  @Test
  public void testOfflineLabelDownloadListenerBlobStorageNotExist() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    offlineInstructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_STORAGE_NOT_EXIST_EVENT,
        headers,
        acknowledgment);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(1)).processEvent(any());
    verify(acknowledgment, times(1)).acknowledge();
  }

  /**
   * If blob url does not exist
   *
   * @throws Exception
   */
  @Test
  public void testOfflineLabelDownloadListenerBlobURLNotExist() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    offlineInstructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_BLOB_URL_NOT_EXIST_EVENT,
        headers,
        acknowledgment);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(0)).processEvent(any());
    verify(acknowledgment, times(1)).acknowledge();
  }

  /**
   * If invalid event type + blob url does not exist
   *
   * @throws Exception
   */
  @Test
  public void testOfflineLabelDownloadListenerInvalidEventBlobURLNotExist() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doNothing().when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    offlineInstructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_INVALID_EVENT_BLOB_URL_NOT_EXIST,
        headers,
        acknowledgment);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(0)).processEvent(any());
    verify(acknowledgment, times(1)).acknowledge();
  }

  /**
   * If any error comes while processing blob
   *
   * @throws Exception
   */
  @Test
  public void testOfflineLabelDownloadListenerProcessError() throws Exception {
    when(appConfig.getInstructionDownloadListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(Integer.valueOf(facilityNum)));
    doThrow(new RuntimeException()).when(instructionDownloadProcessor).processEvent(any());
    Map<String, byte[]> headers = MockMessageHeaders.getMockKafkaListenerHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    offlineInstructionDownloadListener.listen(
        MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT, headers, acknowledgment);
    verify(appConfig, times(1)).getInstructionDownloadListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(instructionDownloadProcessor, times(1)).processEvent(any());
    verify(acknowledgment, times(1)).acknowledge();
  }
}
