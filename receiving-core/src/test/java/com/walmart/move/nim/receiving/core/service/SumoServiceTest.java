package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.SumoConfig;
import com.walmart.move.nim.receiving.core.model.mqtt.MqttNotificationData;
import com.walmart.move.nim.receiving.core.model.sumo.*;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SumoServiceTest extends ReceivingTestBase {

  public static final List<String> USER_LIST = Arrays.asList("user1");
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private SumoConfig sumoConfig;
  @Mock private AppConfig appConfig;
  @Mock private HttpHeaders httpHeaders;
  @Mock private MqttPublisher mqttPublisher;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  private Gson gson = new Gson();

  @InjectMocks SumoService sumoService;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(sumoService, "gson", gson);
    ReflectionTestUtils.setField(
        sumoService, "tenantSpecificConfigReader", tenantSpecificConfigReader);
  }

  @AfterMethod
  public void tearDown() {
    reset(retryableRestConnector);
    reset(tenantSpecificConfigReader);
    reset(mqttPublisher);
  }

  @Test
  public void testSendNotificationToSumo() {
    when(retryableRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"ok\": true, \"alert_id\": \"662aaf20-bf90-11e9-92f6-6d754776ff09\"}",
                HttpStatus.OK));
    SumoNotification expectedNotification = getMockSumoNotification();
    SumoAudience expectedAudience = SumoAudience.builder().user_id(USER_LIST).build();

    sumoService.sendNotificationToSumo(expectedNotification, expectedAudience.getUser_id());

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(retryableRestConnector)
        .post(anyString(), captor.capture(), any(HttpHeaders.class), same(String.class));

    SumoRequest actualRequest = gson.fromJson(captor.getValue(), SumoRequest.class);
    assertEquals(actualRequest.getAudience(), expectedAudience);
    assertEquals(actualRequest.getNotification(), expectedNotification);
    assertTrue(actualRequest.getExpire_ts() != null && !actualRequest.getExpire_ts().isEmpty());
  }

  @Test
  public void testSendNotificationUsingMqtt() {

    SumoNotification expectedNotification = getMockSumoNotification();
    SumoAudience expectedAudience = SumoAudience.builder().user_id(USER_LIST).build();

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.MQTT_NOTIFICATION_PUBLISHER), any()))
        .thenReturn(mqttPublisher);
    doNothing().when(mqttPublisher).publish(any(MqttNotificationData.class), isNull());

    sumoService.sendNotificationUsingMqtt(expectedNotification, expectedAudience.getUser_id());

    verify(tenantSpecificConfigReader, times(1)).getConfiguredInstance(any(), any(), any());
    verify(mqttPublisher, times(1)).publish(any(MqttNotificationData.class), isNull());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "We are unable to reach Sumo service.")
  public void testSumoDown() {
    doThrow(new ResourceAccessException("Error"))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    sumoService.sendNotificationToSumo(getMockSumoNotification(), USER_LIST);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Client exception from Sumo..*")
  public void testSumoBadRequest() {
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.BAD_REQUEST.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), eq(String.class));
    sumoService.sendNotificationToSumo(getMockSumoNotification(), USER_LIST);
  }

  private static SumoNotification getMockSumoNotification() {
    SumoNotification sumoNotification =
        SumoNotification.builder().title("Receiving Notification").alert("Receiving Alert").build();
    return sumoNotification;
  }

  private static SumoNotification getMockSumo2Notification() {
    SumoNotification sumoNotification =
        SumoNotification.builder().title("Receiving Notification").alert("Receiving Body").build();
    return sumoNotification;
  }

  @Test
  public void testFormatExpiryDateForSumo_milliseconds_missing() {
    String test =
        sumoService.formatExpiryDateForSumo(
            Instant.ofEpochMilli(0)); // Instant.ofEpochMilli(0) : 1970-01-01T00:00:00Z
    assertEquals(test, "1970-01-01T00:00:00.000Z");
  }

  @Test
  public void testFormatExpiryDateForSumo() {
    String test = sumoService.formatExpiryDateForSumo(Instant.ofEpochMilli(1));
    assertEquals(test, "1970-01-01T00:00:00.001Z");
  }
}
