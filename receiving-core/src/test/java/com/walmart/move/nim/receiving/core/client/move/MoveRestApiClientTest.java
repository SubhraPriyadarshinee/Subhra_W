package com.walmart.move.nim.receiving.core.client.move;

import static com.walmart.move.nim.receiving.core.client.move.Move.PUTAWAY;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.MOVE_SERVICE_DOWN_ERROR_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class MoveRestApiClientTest {

  @Mock private RestConnector simpleRestConnector;
  @Mock private AppConfig appConfig;
  @InjectMocks private MoveRestApiClient moveRestApiClient;

  @BeforeMethod
  public void setUp() throws Exception {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    TenantContext.setCorrelationId("test");
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(moveRestApiClient, "gson", new Gson());
  }

  @Test
  public void testGetMoveContainerByContainerId_success() throws IOException, ReceivingException {
    File resource = new ClassPathResource("move_success_response_mock.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://gls-atlas-uwms-merchandise-movement-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    JsonArray moveResponse =
        moveRestApiClient.getMoveContainerByContainerId("474-AAG", getMoveApiHttpHeaders());
    assertNotNull(moveResponse);
    assertFalse(moveResponse.isEmpty());
    assertEquals(moveResponse.get(0).getAsJsonObject().get("status").getAsString(), "COMPLETED");
  }

  @Test
  public void testGetMoveContainerByContainerId_successWithNoContainer()
      throws IOException, ReceivingException {

    doReturn("https://gls-atlas-uwms-merchandise-movement-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity = new ResponseEntity<String>("[]", HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    JsonArray moveResponse =
        moveRestApiClient.getMoveContainerByContainerId("474-AAG-INVALID", getMoveApiHttpHeaders());
    assertNotNull(moveResponse);
    assertTrue(moveResponse.isEmpty());
  }

  @Test
  public void testGetMoveContainerByContainerId_badRequest()
      throws IOException, ReceivingException {

    doReturn("https://gls-atlas-uwms-merchandise-movement-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>("[]", HttpStatus.BAD_REQUEST);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      JsonArray moveResponse =
          moveRestApiClient.getMoveContainerByContainerId(
              "474-AAG-INVALID", getMoveApiHttpHeaders());
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(),
          "Unable to verify move status for containerId=474-AAG-INVALID, please contact your supervisor or support.");
      assertEquals(e.getErrorResponse().getErrorCode(), MOVE_SERVICE_DOWN_ERROR_CODE);
    }
  }

  @Test
  public void testGetMoveContainerByContainerId_serverError()
      throws IOException, ReceivingException {

    doReturn("https://gls-atlas-uwms-merchandise-movement-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>("[]", HttpStatus.INTERNAL_SERVER_ERROR);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      JsonArray moveResponse =
          moveRestApiClient.getMoveContainerByContainerId(
              "474-AAG-INVALID", getMoveApiHttpHeaders());
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(),
          "Unable to verify move status for containerId=474-AAG-INVALID, please contact your supervisor or support.");
      assertEquals(e.getErrorResponse().getErrorCode(), MOVE_SERVICE_DOWN_ERROR_CODE);
    }
  }

  @Test
  public void testGetMoveContainerByContainerId_restException()
      throws IOException, ReceivingException {

    doReturn("https://gls-atlas-uwms-merchandise-movement-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "Some error.",
            HttpStatus.BAD_REQUEST.value(),
            "",
            null,
            "".getBytes(),
            Charset.forName("UTF-8"));
    doThrow(restClientResponseException)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      JsonArray moveResponse =
          moveRestApiClient.getMoveContainerByContainerId(
              "474-AAG-INVALID", getMoveApiHttpHeaders());
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(),
          "Unable to verify move status for containerId=474-AAG-INVALID, please contact your supervisor or support.");
      assertEquals(e.getErrorResponse().getErrorCode(), MOVE_SERVICE_DOWN_ERROR_CODE);
    }
  }

  @Test
  public void testGetMoveContainerByContainerId_baseUrlNullCheck()
      throws IOException, ReceivingException {

    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "Some error.",
            HttpStatus.BAD_REQUEST.value(),
            "",
            null,
            "".getBytes(),
            Charset.forName("UTF-8"));
    doThrow(restClientResponseException)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      JsonArray moveResponse =
          moveRestApiClient.getMoveContainerByContainerId(
              "474-AAG-INVALID", getMoveApiHttpHeaders());
      fail("This will fail due to base URL is null for move");
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(),
          "Unable to verify move status for containerId=474-AAG-INVALID, please contact your supervisor or support.");
      assertEquals(e.getErrorResponse().getErrorCode(), MOVE_SERVICE_DOWN_ERROR_CODE);
    }
  }

  @Test
  public void testGetMoveContainerDetails_success() throws IOException, ReceivingException {
    File resource = new ClassPathResource("move_success_response_mock.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://gls-atlas-uwms-merchandise-movement-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    List<String> moveContainerDetail =
        moveRestApiClient.getMoveContainerDetails("474-AAG", getMoveApiHttpHeaders());

    assertNotNull(moveContainerDetail);
    assertEquals(moveContainerDetail.get(0), "PUTAWAYCOMPLETED".toLowerCase());
  }

  @Test
  public void testGetMoveContainerDetails_EmptyTypeAndStatus()
      throws IOException, ReceivingException {
    File resource = new ClassPathResource("move_success_response_mock.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty(MOVE_STATUS, EMPTY_STRING);
    jsonObject.addProperty(TYPE, EMPTY_STRING);
    JsonArray jsonArray = new JsonArray();
    jsonArray.add(jsonObject);

    doReturn("https://gls-atlas-uwms-merchandise-movement-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(jsonArray.toString(), HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    List<String> moveContainerDetail =
        moveRestApiClient.getMoveContainerDetails("474-AAG", getMoveApiHttpHeaders());

    assertNotNull(moveContainerDetail);
    assertTrue(moveContainerDetail.get(0).isEmpty());
  }

  @Test
  public void testGetMoveContainerDetails_EmptyType() throws IOException, ReceivingException {
    File resource = new ClassPathResource("move_success_response_mock.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty(MOVE_STATUS, "COMPLETED");
    jsonObject.addProperty(TYPE, EMPTY_STRING);
    JsonArray jsonArray = new JsonArray();
    jsonArray.add(jsonObject);

    doReturn("https://gls-atlas-uwms-merchandise-movement-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(jsonArray.toString(), HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    List<String> moveContainerDetail =
        moveRestApiClient.getMoveContainerDetails("474-AAG", getMoveApiHttpHeaders());

    assertNotNull(moveContainerDetail);
    assertEquals(moveContainerDetail.get(0), "COMPLETED".toLowerCase());
  }

  @Test
  public void testGetMoveContainerDetails_EmptyStatus() throws IOException, ReceivingException {
    File resource = new ClassPathResource("move_success_response_mock.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty(MOVE_STATUS, EMPTY_STRING);
    jsonObject.addProperty(TYPE, "PUTAWAY");
    JsonArray jsonArray = new JsonArray();
    jsonArray.add(jsonObject);

    doReturn("https://gls-atlas-uwms-merchandise-movement-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(jsonArray.toString(), HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    List<String> moveContainerDetail =
        moveRestApiClient.getMoveContainerDetails("474-AAG", getMoveApiHttpHeaders());

    assertNotNull(moveContainerDetail);
    assertEquals(moveContainerDetail.get(0), PUTAWAY);
  }

  @Test
  public void testGetMoveContainerDetails_MultipleEntityResponse()
      throws IOException, ReceivingException {
    File resource = new ClassPathResource("move_success_haul_putaway.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty(MOVE_STATUS, (String) null);
    jsonObject.addProperty(TYPE, "PUTAWAY");
    JsonArray jsonArray = new JsonArray();
    jsonArray.add(jsonObject);

    doReturn("https://gls-atlas-moves-query-service-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    List<String> moveContainerDetail =
        moveRestApiClient.getMoveContainerDetails("474-AAG", getMoveApiHttpHeaders());

    assertNotNull(moveContainerDetail);
    assertEquals(moveContainerDetail.get(0), "HAULOPEN".toLowerCase());
    assertEquals(moveContainerDetail.get(1), "PUTAWAYPENDING".toLowerCase());
  }

  @Test
  public void testGetMoveContainerDetails_RestExceptionConflict()
      throws IOException, ReceivingException {
    doReturn("https://gls-atlas-uwms-merchandise-movement-grocery-wm-cell000-stg.walmart.com")
        .when(appConfig)
        .getMoveQueryBaseUrl();

    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "Some error.",
            HttpStatus.CONFLICT.value(),
            "",
            null,
            "".getBytes(),
            Charset.forName("UTF-8"));
    doThrow(restClientResponseException)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    JsonArray moveResponse =
        moveRestApiClient.getMoveContainerByContainerId("474-AAG-INVALID", getMoveApiHttpHeaders());
    assertNull(moveResponse);
  }

  public static HttpHeaders getMoveApiHttpHeaders() {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "6065");
    requestHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    requestHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
    requestHeaders.add(ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.DEFAULT_USER);
    requestHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "TestMoveApi");
    return requestHeaders;
  }
}
