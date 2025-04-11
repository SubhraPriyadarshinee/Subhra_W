package com.walmart.move.nim.receiving.acc.message.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.FileAssert.fail;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
import com.walmart.move.nim.receiving.acc.service.ACLNotificationProcessor;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.TenantSpecificBackendConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class KafkaNotificationListenerTest {

  @InjectMocks private KafkaNotificationListener kafkaNotificationListener;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private TenantSpecificBackendConfig tenantSpecificBackendConfig;
  @Mock private ACLNotificationProcessor aclNotificationProcessor;
  private String eventMessage;

  @BeforeClass
  public void setupRoot() {
    MockitoAnnotations.initMocks(this);
    eventMessage = MockACLMessageData.getHawkeyeNotificationEvent();
  }

  @BeforeMethod
  public void setup() {
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.ACL_NOTIFICATION_PROCESSOR),
            eq(EventProcessor.class)))
        .thenReturn(aclNotificationProcessor);
    when(tenantSpecificBackendConfig.getNotificationKafkaEnabledFacilities())
        .thenReturn(Arrays.asList(32987));
  }

  @AfterMethod
  public void cleanup() {
    reset(tenantSpecificConfigReader);
    reset(tenantSpecificBackendConfig);
    reset(aclNotificationProcessor);
  }

  @Test
  public void testListenEmptyMessage() {
    kafkaNotificationListener.listen("", ACCConstants.DOOR_LINE.getBytes());
    verify(aclNotificationProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListenFacilityNotEnabled() {
    when(tenantSpecificBackendConfig.getNotificationKafkaEnabledFacilities())
        .thenReturn(Arrays.asList(32988));
    kafkaNotificationListener.listen(eventMessage, ACCConstants.DOOR_LINE.getBytes());
    verify(aclNotificationProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListenOtherEvent() {
    when(tenantSpecificBackendConfig.getNotificationKafkaEnabledFacilities())
        .thenReturn(Arrays.asList(32988));
    kafkaNotificationListener.listen(eventMessage, "test".getBytes());
    verify(aclNotificationProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListen() {

    kafkaNotificationListener.listen(eventMessage, ACCConstants.DOOR_LINE.getBytes());
    ArgumentCaptor<ACLNotification> notificationArgumentCaptor =
        ArgumentCaptor.forClass(ACLNotification.class);
    verify(aclNotificationProcessor, times(1)).processEvent(notificationArgumentCaptor.capture());
    assertNotNull(notificationArgumentCaptor);
    assertEquals(notificationArgumentCaptor.getValue().getEquipmentName(), "W1001");
    assertEquals(notificationArgumentCaptor.getValue().getEquipmentType(), "DOOR_LINE");
    assertEquals(notificationArgumentCaptor.getValue().getLocationId(), "501");
    assertEquals(notificationArgumentCaptor.getValue().getEquipmentStatus().size(), 1);
    assertEquals(
        notificationArgumentCaptor.getValue().getEquipmentStatus().get(0).getStatusTimestamp(),
        "2020-12-18T17:35:50.324Z");
    assertEquals(
        notificationArgumentCaptor.getValue().getEquipmentStatus().get(0).getStatus(), "LABEL_LOW");
    assertEquals(
        notificationArgumentCaptor.getValue().getEquipmentStatus().get(0).getSeverity(), "WARNING");
    assertEquals(
        notificationArgumentCaptor.getValue().getEquipmentStatus().get(0).getComponentId(), "1");
    assertEquals(
        notificationArgumentCaptor.getValue().getEquipmentStatus().get(0).getDisplayMessage(),
        "Label low");
    assertFalse(notificationArgumentCaptor.getValue().getEquipmentStatus().get(0).getCleared());
  }

  private static String getFileAsString(String filePath) {

    try {
      String dataPath = new File(filePath).getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file {}" + e.getMessage());
    }
    return null;
  }
}
