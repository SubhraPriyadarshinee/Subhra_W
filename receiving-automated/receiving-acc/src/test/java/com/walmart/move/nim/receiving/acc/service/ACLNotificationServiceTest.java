package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.entity.NotificationLog;
import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotificationSearchResponse;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotificationSummary;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLSumoNotification;
import com.walmart.move.nim.receiving.acc.repositories.ACLNotificationLogRepository;
import com.walmart.move.nim.receiving.acc.repositories.UserLocationRepo;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.SumoConfig;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.core.service.SumoService;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACLNotificationServiceTest extends ReceivingTestBase {

  @Mock private SumoConfig sumoConfig;
  @Mock private ACCManagedConfig accManagedConfig;
  private Gson gson = new Gson();

  @Mock SumoService sumoService;
  @Mock LocationService locationService;
  @InjectMocks ACLNotificationService aclNotificationService;
  @Autowired private ACLNotificationLogRepository aclNotificationLogRepository;
  @InjectMocks private UserLocationService userLocationService;
  @InjectMocks private ReportService reportService;
  @Autowired private UserLocationRepo userLocationRepo;

  @Captor private ArgumentCaptor<List<String>> captor;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private static List<Integer> IGNORE_LIST = Arrays.asList(17, 25);
  private PageRequest pageReq;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(aclNotificationService, "gson", gson);
    ReflectionTestUtils.setField(aclNotificationService, "reportService", reportService);
    ReflectionTestUtils.setField(
        aclNotificationService, "aclNotificationLogRepository", aclNotificationLogRepository);
    ReflectionTestUtils.setField(
        aclNotificationService, "userLocationService", userLocationService);
    ReflectionTestUtils.setField(userLocationService, "userLocationRepo", userLocationRepo);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
    pageReq = PageRequest.of(0, 10);
  }

  @AfterMethod
  public void tearDown() {
    reset(sumoService);
    reset(tenantSpecificConfigReader);
    aclNotificationLogRepository.deleteAll();
    userLocationRepo.deleteAll();
  }

  @Test
  public void testHappyPath_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    verify(sumoService, times(1)).sendNotificationToSumo(any(ACLSumoNotification.class), anyList());

    verify(sumoService, never())
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());

    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    verify(sumoService, times(1))
        .sendNotificationToSumo(any(ACLSumoNotification.class), captor.capture());

    List<String> users = captor.getValue();
    assertEquals(users.size(), 1);

    assertEquals(aclLogs.size(), 1);
    assertNotNull(aclLogs.get(0).getSumoResponse());

    ACLNotificationSummary notificationMessage =
        gson.fromJson(aclLogs.get(0).getNotificationMessage(), ACLNotificationSummary.class);
    assertEquals(notificationMessage.getEquipmentName(), notification.getEquipmentName());
    assertEquals(notificationMessage.getEquipmentType(), notification.getEquipmentType());
    assertEquals(notificationMessage.getLocationId(), notification.getLocationId());
    assertNotNull(notificationMessage.getUpdatedTs());
    assertEquals(
        notificationMessage.getEquipmentStatus().getCode(),
        notification.getEquipmentStatus().get(0).getCode());
    assertEquals(
        notificationMessage.getEquipmentStatus().getValue(),
        notification.getEquipmentStatus().get(0).getValue());
  }

  @Test
  public void testHappyPath_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue_MultiManifest() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ACCConstants.ENABLE_MULTI_MANIFEST))
        .thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ACCConstants.ENABLE_PARENT_ACL_LOCATION_CHECK))
        .thenReturn(true);
    when(locationService.getDoorInfo(notification.getLocationId(), Boolean.FALSE))
        .thenReturn(LocationInfo.builder().mappedParentAclLocation("PARENT").build());
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");
    createUserLocationMapping(notification.getLocationId() + "-sibling", "rcvuser.s32898");

    String childLocation = notification.getLocationId();
    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    verify(sumoService, times(1)).sendNotificationToSumo(any(ACLSumoNotification.class), anyList());
    verify(locationService, times(1)).getDoorInfo(eq(childLocation), eq(Boolean.FALSE));

    verify(sumoService, never())
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());

    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    verify(sumoService, times(1))
        .sendNotificationToSumo(any(ACLSumoNotification.class), captor.capture());

    List<String> users = captor.getValue();
    assertEquals(users.size(), 2);

    assertEquals(aclLogs.size(), 1);
    assertNotNull(aclLogs.get(0).getSumoResponse());

    ACLNotificationSummary notificationMessage =
        gson.fromJson(aclLogs.get(0).getNotificationMessage(), ACLNotificationSummary.class);
    assertEquals(notificationMessage.getEquipmentName(), notification.getEquipmentName());
    assertEquals(notificationMessage.getEquipmentType(), notification.getEquipmentType());
    assertEquals(notificationMessage.getLocationId(), notification.getLocationId());
    assertNotNull(notificationMessage.getUpdatedTs());
    assertEquals(
        notificationMessage.getEquipmentStatus().getCode(),
        notification.getEquipmentStatus().get(0).getCode());
    assertEquals(
        notificationMessage.getEquipmentStatus().getValue(),
        notification.getEquipmentStatus().get(0).getValue());
  }

  @Test
  public void testHappyPath_WhenSendACLNotificationsViaMqttIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);

    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);

    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(false);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_VIA_MQTT_ENABLED))
        .thenReturn(true);

    doNothing()
        .when(sumoService)
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    verify(sumoService, never()).sendNotificationToSumo(any(ACLSumoNotification.class), anyList());

    verify(sumoService, times(1))
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());

    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    verify(sumoService, times(1))
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), captor.capture());

    List<String> users = captor.getValue();
    assertEquals(users.size(), 1);

    assertEquals(aclLogs.size(), 1);
    assertNull(aclLogs.get(0).getSumoResponse());

    ACLNotificationSummary notificationMessage =
        gson.fromJson(aclLogs.get(0).getNotificationMessage(), ACLNotificationSummary.class);
    assertEquals(notificationMessage.getEquipmentName(), notification.getEquipmentName());
    assertEquals(notificationMessage.getEquipmentType(), notification.getEquipmentType());
    assertEquals(notificationMessage.getLocationId(), notification.getLocationId());
    assertNotNull(notificationMessage.getUpdatedTs());
    assertEquals(
        notificationMessage.getEquipmentStatus().getCode(),
        notification.getEquipmentStatus().get(0).getCode());
    assertEquals(
        notificationMessage.getEquipmentStatus().getValue(),
        notification.getEquipmentStatus().get(0).getValue());
  }

  private void createUserLocationMapping(String locationId, String userId) {
    UserLocation userLocation = new UserLocation();
    userLocation.setUserId(userId);
    userLocation.setLocationId(locationId);
    userLocation.setParentLocationId("PARENT");
    userLocationRepo.save(userLocation);
  }

  @Test
  public void testHappyPathWithMultipleEvent_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(
            MockACLMessageData.getNotificationEventWithMultipleEquipment(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));

    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    ArgumentCaptor<ACLSumoNotification> captor = ArgumentCaptor.forClass(ACLSumoNotification.class);
    verify(sumoService, times(1)).sendNotificationToSumo(captor.capture(), anyList());
    // verify only 2 message were sent to sumo
    ACLSumoNotification aclSumoNotification = captor.getValue();
    assertEquals(aclSumoNotification.toString().split("\"code\"").length - 1, 2);
    // verify 3 messages were saved in DB
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 3);
    assertNotNull(aclLogs.get(0).getSumoResponse());
    assertNotNull(aclLogs.get(1).getSumoResponse());
  }

  @Test
  public void testHappyPathWithMultipleEvent_WhenSendACLNotificationsViaMqttIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(
            MockACLMessageData.getNotificationEventWithMultipleEquipment(), ACLNotification.class);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(false);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_VIA_MQTT_ENABLED))
        .thenReturn(true);

    doNothing()
        .when(sumoService)
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());

    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    // verify 3 messages were saved in DB with Sumo Response as NULL
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 3);
    assertNull(aclLogs.get(0).getSumoResponse());
    assertNull(aclLogs.get(1).getSumoResponse());
    assertNull(aclLogs.get(2).getSumoResponse());
  }

  @Test
  public void testHappyPathWithUnknownEvent_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getUnknownNotificationEvent(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    verify(sumoService, times(1)).sendNotificationToSumo(any(ACLSumoNotification.class), anyList());
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNotNull(aclLogs.get(0).getSumoResponse());
    ACLNotificationSummary notificationMessage =
        gson.fromJson(aclLogs.get(0).getNotificationMessage(), ACLNotificationSummary.class);
    assertEquals(notificationMessage.getEquipmentName(), notification.getEquipmentName());
    assertEquals(notificationMessage.getEquipmentType(), notification.getEquipmentType());
    assertEquals(notificationMessage.getLocationId(), notification.getLocationId());
    assertNotNull(notificationMessage.getUpdatedTs());
    assertEquals(
        notificationMessage.getEquipmentStatus().getCode(),
        notification.getEquipmentStatus().get(0).getCode());
    assertEquals(
        notificationMessage.getEquipmentStatus().getValue(),
        notification.getEquipmentStatus().get(0).getValue());
  }

  @Test
  public void testHappyPathWithUnknownEvent_WhenSendACLNotificationsViaMqttIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getUnknownNotificationEvent(), ACLNotification.class);

    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(false);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_VIA_MQTT_ENABLED))
        .thenReturn(true);

    doNothing()
        .when(sumoService)
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());

    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    verify(sumoService, times(1))
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNull(aclLogs.get(0).getSumoResponse());
    ACLNotificationSummary notificationMessage =
        gson.fromJson(aclLogs.get(0).getNotificationMessage(), ACLNotificationSummary.class);
    assertEquals(notificationMessage.getEquipmentName(), notification.getEquipmentName());
    assertEquals(notificationMessage.getEquipmentType(), notification.getEquipmentType());
    assertEquals(notificationMessage.getLocationId(), notification.getLocationId());
    assertNotNull(notificationMessage.getUpdatedTs());
    assertEquals(
        notificationMessage.getEquipmentStatus().getCode(),
        notification.getEquipmentStatus().get(0).getCode());
    assertEquals(
        notificationMessage.getEquipmentStatus().getValue(),
        notification.getEquipmentStatus().get(0).getValue());
  }

  @Test
  public void testMultipleUserAtLocation_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));

    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");
    createUserLocationMapping(notification.getLocationId(), "user1.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(1))
        .sendNotificationToSumo(any(ACLSumoNotification.class), captor.capture());
    List<String> users = captor.getValue();
    assertEquals(users.size(), 2);
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNotNull(aclLogs.get(0).getSumoResponse());
  }

  @Test
  public void testMultipleUserAtLocation_WhenSendACLNotificationsViaMqttIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(false);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_VIA_MQTT_ENABLED))
        .thenReturn(true);

    doNothing()
        .when(sumoService)
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());

    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");
    createUserLocationMapping(notification.getLocationId(), "user1.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(1))
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), captor.capture());
    List<String> users = captor.getValue();
    assertEquals(users.size(), 2);
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNull(aclLogs.get(0).getSumoResponse());
  }

  @Test
  public void testUserAtLocationIgnoreCase_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(1))
        .sendNotificationToSumo(any(ACLSumoNotification.class), captor.capture());
    List<String> users = captor.getValue();
    assertEquals(users.size(), 1);
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNotNull(aclLogs.get(0).getSumoResponse());
  }

  @Test
  public void testUserAtLocationIgnoreCase_WhenSendACLNotificationsToSumo2IsEnabledFlagIsTrue()
      throws Exception {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo2(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.IS_SUMO2_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(1))
        .sendNotificationToSumo2(any(ACLSumoNotification.class), captor.capture());
    List<String> users = captor.getValue();
    assertEquals(users.size(), 1);
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNotNull(aclLogs.get(0).getSumoResponse());
  }

  @Test
  public void
      testUserAtLocationIgnoreCase_WhenSendACLNotificationsToSumo2IsEnabledFlagIsTrue_EquipmentStatusEmpty()
          throws Exception {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    notification.setEquipmentStatus(Collections.emptyList());
    when(sumoService.sendNotificationToSumo2(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.IS_SUMO2_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(0))
        .sendNotificationToSumo2(any(ACLSumoNotification.class), captor.capture());
    List<String> users = captor.getValue();
    assertEquals(users.size(), 1);
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 0);
  }

  @Test
  public void testUserAtLocationIgnoreCase_WhenSendACLNotificationsViaMqttIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);

    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(false);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_VIA_MQTT_ENABLED))
        .thenReturn(true);

    doNothing()
        .when(sumoService)
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(1))
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), captor.capture());
    List<String> users = captor.getValue();
    assertEquals(users.size(), 1);
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNull(aclLogs.get(0).getSumoResponse());
  }

  @Test
  public void testNoUserAtLocation_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(Boolean.TRUE);

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(0)).sendNotificationToSumo(any(ACLSumoNotification.class), anyList());
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNull(aclLogs.get(0).getSumoResponse());
  }

  @Test
  public void testNoUserAtLocation_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue_Sumo2()
      throws Exception {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo2(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.IS_SUMO2_ENABLED))
        .thenReturn(Boolean.TRUE);

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(0))
        .sendNotificationToSumo2(any(ACLSumoNotification.class), anyList());
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNull(aclLogs.get(0).getSumoResponse());
  }

  @Test
  public void testNoUserAtLocation_WhenSendACLNotificationsViaMqttIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(false);

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_VIA_MQTT_ENABLED))
        .thenReturn(true);

    doNothing()
        .when(sumoService)
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(0))
        .sendNotificationUsingMqtt(any(ACLSumoNotification.class), anyList());
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNull(aclLogs.get(0).getSumoResponse());
  }

  @Test
  public void testSumoBadRequest_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_SUMO_REQ,
                String.format(
                    ReceivingConstants.BAD_RESPONSE_ERROR_MSG,
                    ReceivingConstants.SUMO,
                    400,
                    "Invalid")))
        .when(sumoService)
        .sendNotificationToSumo(any(ACLSumoNotification.class), anyList());

    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(1)).sendNotificationToSumo(any(ACLSumoNotification.class), anyList());
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNull(aclLogs.get(0).getSumoResponse());
  }

  @Test
  public void testSumoDown_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.SUMO_SERVICE_ERROR, ReceivingConstants.SUMO_SERVICE_DOWN))
        .when(sumoService)
        .sendNotificationToSumo(any(ACLSumoNotification.class), anyList());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    ACLNotification notification =
        gson.fromJson(
            MockACLMessageData.getNotificationEventWithMultipleEquipment(), ACLNotification.class);

    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    verify(sumoService, times(1)).sendNotificationToSumo(any(ACLSumoNotification.class), anyList());
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 3);
    assertNull(aclLogs.get(0).getSumoResponse());
    assertNull(aclLogs.get(1).getSumoResponse());
  }

  @Test
  public void testGetNotificationByLocation() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    aclNotificationService.saveACLMessage(notification);
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    ACLNotificationSearchResponse response =
        aclNotificationService.getAclNotificationSearchResponse("D102A", 0, 10);

    assertEquals(response.getAclLogs().size(), 1);
    ACLNotificationSummary aclNotificationSummary = response.getAclLogs().get(0);
    assertEquals(aclNotificationSummary.getEquipmentName(), notification.getEquipmentName());
    assertEquals(aclNotificationSummary.getEquipmentType(), notification.getEquipmentType());
    assertEquals(aclNotificationSummary.getLocationId(), notification.getLocationId());
    assertNotNull(aclNotificationSummary.getUpdatedTs());

    assertEquals(
        aclNotificationSummary.getEquipmentStatus().getCode(),
        notification.getEquipmentStatus().get(0).getCode());
    assertEquals(
        aclNotificationSummary.getEquipmentStatus().getValue(),
        notification.getEquipmentStatus().get(0).getValue());
    assertEquals(
        aclNotificationSummary.getEquipmentStatus().getMessage(),
        "Communication between BOSS and GLS is slow. Solution: If the problem persists, contact maintenance.");
    assertTrue(aclNotificationSummary.getEquipmentStatus().getZone() == 2);
  }

  @Test
  public void testGetNotificationByLocationForUnknownEvent() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getUnknownNotificationEvent(), ACLNotification.class);
    aclNotificationService.saveACLMessage(notification);
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    ACLNotificationSearchResponse response =
        aclNotificationService.getAclNotificationSearchResponse("D102A", 0, 10);
    assertEquals(response.getAclLogs().size(), 1);
    ACLNotificationSummary aclNotificationSummary = response.getAclLogs().get(0);
    assertEquals(aclNotificationSummary.getEquipmentName(), notification.getEquipmentName());
    assertEquals(aclNotificationSummary.getEquipmentType(), notification.getEquipmentType());
    assertEquals(aclNotificationSummary.getLocationId(), notification.getLocationId());
    assertNotNull(aclNotificationSummary.getUpdatedTs());

    assertEquals(
        aclNotificationSummary.getEquipmentStatus().getCode(),
        notification.getEquipmentStatus().get(0).getCode());
    assertEquals(
        aclNotificationSummary.getEquipmentStatus().getValue(),
        notification.getEquipmentStatus().get(0).getValue());
    assertNull(aclNotificationSummary.getEquipmentStatus().getMessage());
    assertNull(aclNotificationSummary.getEquipmentStatus().getZone());
  }

  @Test
  public void testGetNotificationByLocationForEventWithCodeOnly() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getNotificationEventWithCodeOnly(), ACLNotification.class);
    aclNotificationService.saveACLMessage(notification);
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    ACLNotificationSearchResponse response =
        aclNotificationService.getAclNotificationSearchResponse("D102A", 0, 10);

    assertEquals(response.getAclLogs().size(), 1);
    ACLNotificationSummary aclNotificationSummary = response.getAclLogs().get(0);
    assertEquals(aclNotificationSummary.getEquipmentName(), notification.getEquipmentName());
    assertEquals(aclNotificationSummary.getEquipmentType(), notification.getEquipmentType());
    assertEquals(aclNotificationSummary.getLocationId(), notification.getLocationId());
    assertNotNull(aclNotificationSummary.getUpdatedTs());

    assertEquals(
        aclNotificationSummary.getEquipmentStatus().getCode(),
        notification.getEquipmentStatus().get(0).getCode());
    assertNotNull(aclNotificationSummary.getEquipmentStatus().getValue());
    assertNotNull(aclNotificationSummary.getEquipmentStatus().getMessage());
    assertNotNull(aclNotificationSummary.getEquipmentStatus().getZone());
  }

  @Test
  public void testCreateExcelReportForAclNotification() {
    when(tenantSpecificConfigReader.getEnabledFacilityNumListForFeature(
            ReceivingConstants.ACL_NOTIFICATION_ENABLED))
        .thenReturn(Arrays.asList(32818, 6561));

    String notificationMessage =
        "{\n"
            + "  \"equipmentName\": \"W1001\",\n"
            + "  \"equipmentType\": \"ACL\",\n"
            + "  \"locationId\": \"D102A\",\n"
            + "  \"equipmentStatus\": {\n"
            + "    \"code\": 3,\n"
            + "    \"value\": \"HOST_LATE\",\n"
            + "    \"msgSequence\": \"118-20191123165746\"\n"
            + "  },\n"
            + "  \"updatedTs\": \"Mar 30, 2020 11:10:44 PM\"\n"
            + "}";
    NotificationLog notificationLog1 =
        NotificationLog.builder()
            .id(1L)
            .notificationMessage(notificationMessage)
            .locationId("D102A")
            .logTs(new Date())
            .build();
    notificationLog1.setFacilityNum(32818);
    NotificationLog notificationLog2 =
        NotificationLog.builder()
            .id(2L)
            .notificationMessage(notificationMessage)
            .locationId("D102A")
            .logTs(new Date())
            .build();
    notificationLog2.setFacilityNum(6561);

    Workbook workbook =
        aclNotificationService.createExcelReportForAclNotificationLogs(
            Arrays.asList(notificationLog1, notificationLog2));

    Assert.assertNotNull(workbook);
  }

  @Test
  public void testGetAclLogsByDate() {
    // save 3 records
    ACLNotification notification =
        gson.fromJson(
            MockACLMessageData.getNotificationEventWithMultipleEquipment(), ACLNotification.class);
    aclNotificationService.saveACLMessage(notification);

    Calendar cal = Calendar.getInstance();
    Date toDate = cal.getTime();
    cal.add(Calendar.HOUR, -24);
    Date fromDate = cal.getTime();
    assertEquals(aclNotificationService.getAclLogsByDate(fromDate, toDate).size(), 3);
  }

  @Test
  public void testPurge() {

    saveNotificationLogInDB();

    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.NOTIFICATION_LOG)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = aclNotificationService.purge(purgeData, pageReq, 0);
    assertNotEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {

    saveNotificationLogInDB();

    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.NOTIFICATION_LOG)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = aclNotificationService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  private void saveNotificationLogInDB() {
    String notificationMessage =
        "{\n"
            + "  \"equipmentName\": \"W1001\",\n"
            + "  \"equipmentType\": \"ACL\",\n"
            + "  \"locationId\": \"D102A\",\n"
            + "  \"equipmentStatus\": {\n"
            + "    \"code\": 3,\n"
            + "    \"value\": \"HOST_LATE\",\n"
            + "    \"msgSequence\": \"118-20191123165746\"\n"
            + "  },\n"
            + "  \"updatedTs\": \"Mar 30, 2020 11:10:44 PM\"\n"
            + "}";

    NotificationLog notificationLog1 =
        NotificationLog.builder()
            .id(1L)
            .notificationMessage(notificationMessage)
            .locationId("D102A")
            .build();

    List<NotificationLog> notificationLogList = Arrays.asList(notificationLog1);
    aclNotificationLogRepository.saveAll(notificationLogList);
  }

  @Test
  public void testHappyPathHawkeyeNotification_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(MockACLMessageData.getHawkeyeNotificationEvent(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    verify(sumoService, times(1)).sendNotificationToSumo(any(ACLSumoNotification.class), anyList());
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    verify(sumoService, times(1))
        .sendNotificationToSumo(any(ACLSumoNotification.class), captor.capture());

    List<String> users = captor.getValue();
    assertEquals(users.size(), 1);

    assertEquals(aclLogs.size(), 1);
    assertNotNull(aclLogs.get(0).getSumoResponse());

    ACLNotificationSummary notificationMessage =
        gson.fromJson(aclLogs.get(0).getNotificationMessage(), ACLNotificationSummary.class);
    assertEquals(notificationMessage.getEquipmentName(), notification.getEquipmentName());
    assertEquals(notificationMessage.getEquipmentType(), notification.getEquipmentType());
    assertEquals(notificationMessage.getLocationId(), notification.getLocationId());
    assertNotNull(notificationMessage.getUpdatedTs());
    assertNull(notificationMessage.getEquipmentStatus().getCode());
    assertEquals(
        notificationMessage.getEquipmentStatus().getValue(),
        notification.getEquipmentStatus().get(0).getStatus());
    assertEquals(
        notificationMessage.getEquipmentStatus().getMessage(),
        notification.getEquipmentStatus().get(0).getDisplayMessage());
    assertEquals(
        notificationMessage.getEquipmentStatus().getZone(),
        notification.getEquipmentStatus().get(0).getZone());
  }

  @Test
  public void
      testHappyPathHawkeyeNotificationWithMultipleEvent_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(
            MockACLMessageData.getMultipleHawkeyeNotificationEvent(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));

    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    ArgumentCaptor<ACLSumoNotification> captor = ArgumentCaptor.forClass(ACLSumoNotification.class);
    verify(sumoService, times(1)).sendNotificationToSumo(captor.capture(), anyList());
    // verify only 2 message were sent to sumo
    ACLSumoNotification aclSumoNotification = captor.getValue();
    assertEquals(aclSumoNotification.toString().split("\"message\"").length - 1, 2);
    // verify 3 messages were saved in DB
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 2);
    assertNotNull(aclLogs.get(0).getSumoResponse());
    assertNotNull(aclLogs.get(1).getSumoResponse());
  }

  @Test
  public void
      testHappyPathHawkeyeNotificationWithMultipleEventOneCleared_WhenSendACLNotificationsToSumoIsEnabledFlagIsTrue() {

    ACLNotification notification =
        gson.fromJson(
            MockACLMessageData.getMultipleHawkeyeNotificationEvent2(), ACLNotification.class);
    when(sumoService.sendNotificationToSumo(any(ACLSumoNotification.class), anyList()))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));

    when(accManagedConfig.getAclNotificationIgnoreCodeList()).thenReturn(IGNORE_LIST);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED))
        .thenReturn(true);
    createUserLocationMapping(notification.getLocationId(), "sysadmin.s32898");

    aclNotificationService.sendNotificationToSumo(
        notification, TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());
    ArgumentCaptor<ACLSumoNotification> captor = ArgumentCaptor.forClass(ACLSumoNotification.class);
    verify(sumoService, times(1)).sendNotificationToSumo(captor.capture(), anyList());
    // verify only 2 message were sent to sumo
    ACLSumoNotification aclSumoNotification = captor.getValue();
    assertEquals(aclSumoNotification.toString().split("\"message\"").length - 1, 1);
    // verify 3 messages were saved in DB
    List<NotificationLog> aclLogs = aclNotificationLogRepository.findAll();
    assertEquals(aclLogs.size(), 1);
    assertNotNull(aclLogs.get(0).getSumoResponse());
  }

  @Test
  public void test_deleteByLocation() {
    ACLNotificationLogRepository mockACLNotificationLogRepository =
        Mockito.mock(ACLNotificationLogRepository.class);
    ReflectionTestUtils.setField(
        aclNotificationService, "aclNotificationLogRepository", mockACLNotificationLogRepository);
    String mockLocationId = "a123";
    doNothing()
        .when(mockACLNotificationLogRepository)
        .deleteByLocationId(eq(StringUtils.upperCase(mockLocationId)));
    aclNotificationService.deleteByLocation(mockLocationId);
    verify(mockACLNotificationLogRepository, times(1))
        .deleteByLocationId(eq(StringUtils.upperCase(mockLocationId)));

    // setting back to spring object for the sake of other unit tests
    ReflectionTestUtils.setField(
        aclNotificationService, "aclNotificationLogRepository", aclNotificationLogRepository);
  }

  @Test
  public void test_getAclNotificationLogsByLocation() {
    ACLNotificationLogRepository mockACLNotificationLogRepository =
        Mockito.mock(ACLNotificationLogRepository.class);
    ReflectionTestUtils.setField(
        aclNotificationService, "aclNotificationLogRepository", mockACLNotificationLogRepository);
    String mockLocationId = "a123";
    doReturn(Collections.emptyList())
        .when(mockACLNotificationLogRepository)
        .findByLocationId(eq(StringUtils.upperCase(mockLocationId)), any(PageRequest.class));
    aclNotificationService.getAclNotificationLogsByLocation(mockLocationId, PageRequest.of(0, 1));
    verify(mockACLNotificationLogRepository, times(1))
        .findByLocationId(eq(StringUtils.upperCase(mockLocationId)), any(PageRequest.class));

    // setting back to spring object for the sake of other unit tests
    ReflectionTestUtils.setField(
        aclNotificationService, "aclNotificationLogRepository", aclNotificationLogRepository);
  }
}
