package com.walmart.move.nim.receiving.core.message.listener.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.service.DefaultEventProcessor;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SecureGdmItemUpdateListenerTest {
  @InjectMocks SecureGdmItemUpdateListener itemUpdateListener;
  @Mock AppConfig appConfig;
  @Mock DefaultEventProcessor eventProcessor;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;

  private String handlingCodeUpdateMessage;
  private String channelFlipMessage;
  private String undoCatalogMessage;
  private String catalogUpdateMessage;
  private String invalidItemUpdateMessage;
  private String dataPath;

  @BeforeClass
  public void initMocks() throws IOException {
    MockitoAnnotations.initMocks(this);
    try {
      dataPath =
          new File(
                  "../receiving-test/src/main/resources/json/ItemUpdateMessageForHandlingCodeUpdate.json")
              .getCanonicalPath();
      handlingCodeUpdateMessage = new String(Files.readAllBytes(Paths.get(dataPath)));
      dataPath =
          new File("../receiving-test/src/main/resources/json/ItemUpdateMessageForChannelFlip.json")
              .getCanonicalPath();
      channelFlipMessage = new String(Files.readAllBytes(Paths.get(dataPath)));
      dataPath =
          new File("../receiving-test/src/main/resources/json/ItemUpdateMessageForUndoCatalog.json")
              .getCanonicalPath();
      undoCatalogMessage = new String(Files.readAllBytes(Paths.get(dataPath)));
      dataPath =
          new File(
                  "../receiving-test/src/main/resources/json/ItemUpdateMessageForCatalogUpdate.json")
              .getCanonicalPath();
      catalogUpdateMessage = new String(Files.readAllBytes(Paths.get(dataPath)));
      dataPath =
          new File("../receiving-test/src/main/resources/json/ItemUpdateMessage_InvalidEvent.json")
              .getCanonicalPath();
      invalidItemUpdateMessage = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @BeforeMethod
  public void beforeMethod() {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(eventProcessor);
    TenantContext.setFacilityNum(32888);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void resetMocks() {
    reset(tenantSpecificConfigReader);
    reset(eventProcessor);
    reset(appConfig);
  }

  @Test
  public void testListen_EmptyMessage() {
    itemUpdateListener.listen("", MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(appConfig, times(0)).getGdmItemUpdateListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0)).isFeatureFlagEnabled(anyString());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(eventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListen_ItemNumberIsNull() {
    String message1 = "{\n" + "   \"xyz\": \"123\"\n" + "}";
    itemUpdateListener.listen(message1, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(appConfig, times(0)).getGdmItemUpdateListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0)).isFeatureFlagEnabled(anyString());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(eventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListen_ItemUpdateNotEnabledForFacility() {
    when(appConfig.getGdmItemUpdateListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(1234));
    itemUpdateListener.listen(
        handlingCodeUpdateMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(appConfig, times(1)).getGdmItemUpdateListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0)).isFeatureFlagEnabled(anyString());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(eventProcessor, times(0)).processEvent(any());
  }

  @Test
  private void testListen_InvalidEvent() {
    when(appConfig.getGdmItemUpdateListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32888));
    itemUpdateListener.listen(
        invalidItemUpdateMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(appConfig, times(1)).getGdmItemUpdateListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0)).isFeatureFlagEnabled(anyString());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(eventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListen_HandlingCodeUpdate() {
    when(appConfig.getGdmItemUpdateListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32888));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.ITEM_HANDLING_CODE_UPDATE_ENABLED))
        .thenReturn(true);
    itemUpdateListener.listen(
        handlingCodeUpdateMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(appConfig, times(1)).getGdmItemUpdateListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1)).isFeatureFlagEnabled(anyString());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(eventProcessor, times(1)).processEvent(any());
  }

  @Test
  public void testListen_ChannelFlip() {
    when(appConfig.getGdmItemUpdateListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32888));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.ITEM_CHANNEL_FLIP_ENABLED))
        .thenReturn(true);
    itemUpdateListener.listen(channelFlipMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(appConfig, times(1)).getGdmItemUpdateListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1)).isFeatureFlagEnabled(anyString());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(eventProcessor, times(1)).processEvent(any());
  }

  @Test
  public void testListen_UndoCatalog() {
    when(appConfig.getGdmItemUpdateListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32888));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.ITEM_UNDO_CATALOG_ENABLED))
        .thenReturn(true);
    itemUpdateListener.listen(undoCatalogMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(appConfig, times(1)).getGdmItemUpdateListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1)).isFeatureFlagEnabled(anyString());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(eventProcessor, times(1)).processEvent(any());
  }

  @Test
  public void testListen_Catalog() {
    when(appConfig.getGdmItemUpdateListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32888));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.ITEM_CATALOG_UPDATE_ENABLED))
        .thenReturn(true);
    itemUpdateListener.listen(
        catalogUpdateMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(appConfig, times(1)).getGdmItemUpdateListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1)).isFeatureFlagEnabled(anyString());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(eventProcessor, times(1)).processEvent(any());
  }

  @Test
  public void testListen_ThrowsException() {
    try {
      when(appConfig.getGdmItemUpdateListenerEnabledFacilities())
          .thenReturn(Collections.singletonList(32888));
      when(tenantSpecificConfigReader.isFeatureFlagEnabled(
              ReceivingConstants.ITEM_HANDLING_CODE_UPDATE_ENABLED))
          .thenReturn(true);
      doThrow(ReceivingException.class).when(eventProcessor).processEvent(any());
      itemUpdateListener.listen(
          handlingCodeUpdateMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
      verify(appConfig, times(1)).getGdmItemUpdateListenerEnabledFacilities();
      verify(tenantSpecificConfigReader, times(1)).isFeatureFlagEnabled(anyString());
      verify(tenantSpecificConfigReader, times(1))
          .getConfiguredInstance(anyString(), anyString(), any());
      verify(eventProcessor, times(1)).processEvent(any());
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.UNABLE_TO_PROCESS_ITEM_UPDATE);
      assertEquals(
          e.getDescription(),
          String.format(
              ReceivingConstants.UNABLE_TO_PROCESS_ITEM_UPDATE_ERROR_MSG,
              handlingCodeUpdateMessage));
    }
  }
}
