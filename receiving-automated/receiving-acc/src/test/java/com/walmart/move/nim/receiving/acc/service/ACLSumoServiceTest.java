package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLSumoNotification;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.SumoConfig;
import com.walmart.move.nim.receiving.core.model.sumo.SumoAudience;
import com.walmart.move.nim.receiving.core.model.sumo.SumoContent;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.core.service.SumoService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACLSumoServiceTest extends ReceivingTestBase {

  public static final List<String> USER_LIST = Arrays.asList("user1");
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private SumoConfig sumoConfig;
  @Mock private AppConfig appConfig;
  @Mock private HttpHeaders httpHeaders;
  private Gson gson = new Gson();

  @Spy @InjectMocks SumoService sumoService;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(sumoService, "gson", gson);
    ReflectionTestUtils.setField(sumoService, "privateKeyVersion", "1");

    TenantContext.setFacilityNum(7377);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void tearDown() {
    reset(retryableRestConnector);
    reset(sumoConfig);
    reset(appConfig);
  }

  @Test
  public void testSendNotificationToSumo() throws IOException {
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));

    ACLNotification aclNotification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    ACLSumoNotification expectedNotification =
        new ACLSumoNotification("title", "alert", aclNotification);
    SumoAudience expectedAudience = SumoAudience.builder().user_id(USER_LIST).build();

    sumoService.sendNotificationToSumo(expectedNotification, expectedAudience.getUser_id());

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(retryableRestConnector)
        .post(anyString(), captor.capture(), any(HttpHeaders.class), same(String.class));

    String aclSumoPayloadSchemaFilePath =
        new File("../../receiving-test/src/main/resources/jsonSchema/aclSumoPayload.json")
            .getCanonicalPath();
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(aclSumoPayloadSchemaFilePath))),
            captor.getValue()));
  }

  @Test
  public void testSendUnknownNotificationToSumo() throws IOException {
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));

    ACLNotification aclNotification =
        gson.fromJson(MockACLMessageData.getUnknownNotificationEvent(), ACLNotification.class);
    ACLSumoNotification expectedNotification =
        new ACLSumoNotification("title", "alert", aclNotification);
    SumoAudience expectedAudience = SumoAudience.builder().user_id(USER_LIST).build();

    sumoService.sendNotificationToSumo(expectedNotification, expectedAudience.getUser_id());

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(retryableRestConnector)
        .post(anyString(), captor.capture(), any(HttpHeaders.class), same(String.class));

    String aclSumoPayloadSchemaFilePath =
        new File(
                "../../receiving-test/src/main/resources/jsonSchema/aclSumoPayloadWithoutMessage.json")
            .getCanonicalPath();
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(aclSumoPayloadSchemaFilePath))),
            captor.getValue()));
  }

  @Test
  public void testSendNotificationToSumo2() throws Exception {
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(sumoConfig.getContentAvailable()).thenReturn(1);
    when(sumoConfig.getSumoDomain()).thenReturn("DC");
    when(sumoConfig.getPrivateKeyLocation())
        .thenReturn(
            "../../receiving-test/src/main/resources/environmentConfig/receiving-api/appConfig.properties");
    when(appConfig.getReceivingConsumerId()).thenReturn("dummy");
    doReturn("mocksignature").when(sumoService).generateSignature(anyString(), anyString());

    ACLNotification aclNotification =
        gson.fromJson(MockACLMessageData.getNotificationEvent(), ACLNotification.class);
    ACLSumoNotification expectedNotification =
        new ACLSumoNotification(null, null, aclNotification, new SumoContent(1));
    SumoAudience expectedAudience = SumoAudience.builder().user_id(USER_LIST).build();

    sumoService.sendNotificationToSumo2(expectedNotification, expectedAudience.getUser_id());

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(retryableRestConnector)
        .post(anyString(), captor.capture(), any(HttpHeaders.class), same(String.class));

    String aclSumo2PayloadSchemaFilePath =
        new File("../../receiving-test/src/main/resources/jsonSchema/aclSumo2Payload.json")
            .getCanonicalPath();
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(aclSumo2PayloadSchemaFilePath))),
            captor.getValue()));
  }

  @Test
  public void testSendUnknownNotificationToSumo2() throws Exception {
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    when(sumoConfig.getContentAvailable()).thenReturn(1);
    when(sumoConfig.getSumoDomain()).thenReturn("DC");
    when(sumoConfig.getPrivateKeyLocation())
        .thenReturn(
            "../../receiving-test/src/main/resources/environmentConfig/receiving-api/appConfig.properties");
    when(appConfig.getReceivingConsumerId()).thenReturn("dummy");
    doReturn("mocksignature").when(sumoService).generateSignature(anyString(), anyString());

    ACLNotification aclNotification =
        gson.fromJson(MockACLMessageData.getUnknownNotificationEvent(), ACLNotification.class);
    ACLSumoNotification expectedNotification =
        new ACLSumoNotification(null, null, aclNotification, new SumoContent(1));
    SumoAudience expectedAudience = SumoAudience.builder().user_id(USER_LIST).build();

    sumoService.sendNotificationToSumo2(expectedNotification, expectedAudience.getUser_id());

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(retryableRestConnector)
        .post(anyString(), captor.capture(), any(HttpHeaders.class), same(String.class));

    String aclSumo2PayloadSchemaFilePath =
        new File("../../receiving-test/src/main/resources/jsonSchema/aclSumo2Payload.json")
            .getCanonicalPath();
    assertTrue(
        validateContract(
            new String(Files.readAllBytes(Paths.get(aclSumo2PayloadSchemaFilePath))),
            captor.getValue()));
  }
}
