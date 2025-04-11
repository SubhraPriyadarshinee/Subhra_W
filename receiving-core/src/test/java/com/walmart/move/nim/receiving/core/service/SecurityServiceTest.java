package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.OverrideRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SecurityServiceTest extends ReceivingTestBase {

  @Mock private RestConnector restConnector;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader configUtils;
  private Gson gson = new Gson();

  @InjectMocks SecurityService securityService;

  private Map<String, Object> mockHeaders = new HashMap<>();
  private OverrideRequest overrideRequest = new OverrideRequest();
  private String mockDelivery = "20782785";

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    ReflectionTestUtils.setField(securityService, "gson", gson);

    overrideRequest.setUserId("sysadmin");
    overrideRequest.setPassword("pwd");
    overrideRequest.setPurchaseReferenceNumber("784349344");
    overrideRequest.setPurchaseReferenceLineNumber(1);

    mockHeaders = new HashMap<>();
    mockHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32612");
    mockHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    mockHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    mockHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "a1b2c3d4");
    mockHeaders.put(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void tearDown() {
    reset(restConnector);
  }

  @Test
  public void testAuthenticateSuccess() throws ReceivingException {
    Map<String, Object> authDetails = new HashMap<>();
    authDetails.put(ReceivingConstants.SECURITY_ID, 1);
    authDetails.put(ReceivingConstants.TOKEN, "dummy");

    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doReturn(new ResponseEntity<Map>(authDetails, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> authenticateRsp =
        securityService.authenticate(overrideRequest, mockHeaders);

    assertEquals(
        authenticateRsp.get(ReceivingConstants.SECURITY_ID),
        authDetails.get(ReceivingConstants.SECURITY_ID));
    assertEquals(
        authenticateRsp.get(ReceivingConstants.TOKEN), authDetails.get(ReceivingConstants.TOKEN));
  }

  @Test
  public void testAuthenticateSuccessWithNullRsp() {
    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doReturn(new ResponseEntity<Map>(null, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      securityService.authenticate(overrideRequest, mockHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.SM_AUTHENTICATE_ERROR_MSG);
    }
  }

  @Test
  public void testAuthenticateFailure() {
    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doThrow(
            new RestClientResponseException(
                "",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "{\"messages\":[{\"type\":\"error\",\"code\":\"WM-SM-BE-0002\",\"desc\":\"Invalid Credentials.\"}]}"
                    .getBytes(),
                StandardCharsets.UTF_8))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      securityService.authenticate(overrideRequest, mockHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.UNAUTHORIZED);
      assertEquals(e.getMessage(), ReceivingException.SM_AUTHENTICATE_ERROR_MSG);
    }
  }

  @Test
  public void testSecurityMgmtDown() {
    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doThrow(new ResourceAccessException("Error"))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      securityService.authenticate(overrideRequest, mockHeaders);
    } catch (ReceivingException e) {

      assertEquals(e.getMessage(), ReceivingException.SM_SERVICE_DOWN_ERROR_MSG);
    }
  }

  @Test
  public void testAuthorizeSuccessForExpiry() throws ReceivingException {
    String mockResponse =
        "[{\n"
            + "    \"abbr\": \"AUEX\",\n"
            + "    \"desc\": \"AUTH EXPIRY\",\n"
            + "    \"capName\": \"009_AUTH_EXPIRY\",\n"
            + "    \"applicationName\": \"RCV\",\n"
            + "    \"productName\": \"Receiving\"\n"
            + "  }]";

    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doReturn(new ResponseEntity<String>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    boolean authorizeFlag =
        securityService.authorize("sysadmin", "1", "dummy", ReceivingConstants.EXPIRY, mockHeaders);

    assertTrue(authorizeFlag);
  }

  @Test
  public void testAuthorizeSuccessForOverage() throws ReceivingException {
    String mockResponse =
        "[{\n"
            + "    \"abbr\": \"AUOV\",\n"
            + "    \"desc\": \"AUTH OVERAGES\",\n"
            + "    \"capName\": \"009_AUTH_OVERAGES\",\n"
            + "    \"applicationName\": \"RCV\",\n"
            + "    \"productName\": \"Receiving\"\n"
            + "  }]";

    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doReturn(new ResponseEntity<String>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    boolean authorizeFlag =
        securityService.authorize(
            "sysadmin", "1", "dummy", ReceivingConstants.OVERAGES, mockHeaders);

    assertTrue(authorizeFlag);
  }

  @Test
  public void testAuthorizeSuccessForHaccp() throws ReceivingException {
    String mockResponse =
        "[{\n"
            + "    \"abbr\": \"AUHA\",\n"
            + "    \"desc\": \"AUTH HACCP\",\n"
            + "    \"capName\": \"009_AUTH_HACCP\",\n"
            + "    \"applicationName\": \"RCV\",\n"
            + "    \"productName\": \"Receiving\"\n"
            + "  }]";

    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doReturn(new ResponseEntity<String>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    boolean authorizeFlag =
        securityService.authorize("sysadmin", "1", "dummy", ReceivingConstants.HACCP, mockHeaders);

    assertTrue(authorizeFlag);
  }

  @Test
  public void testAuthorizeWithEmptyCapability() throws ReceivingException {
    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doReturn(new ResponseEntity<String>("[]", HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    try {
      boolean authorizeFlag =
          securityService.authorize(
              "sysadmin", "1", "dummy", ReceivingConstants.OVERAGES, mockHeaders);

    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(), String.format(ReceivingException.SM_AUTHORIZE_ERROR_MSG, "sysadmin"));
    }
  }

  @Test
  public void testAuthorizeSuccessWithNullRsp() {
    String mockResponse = null;

    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doReturn(new ResponseEntity<String>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      securityService.authorize("sysadmin", "1", "dummy", ReceivingConstants.EXPIRY, mockHeaders);
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(), String.format(ReceivingException.SM_AUTHORIZE_ERROR_MSG, "sysadmin"));
    }
  }

  @Test
  public void testAuthorizeWithoutCapability() {
    String mockResponse = null;

    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doReturn(new ResponseEntity<String>(mockResponse, HttpStatus.OK))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      securityService.authorize("sysadmin", "1", "dummy", "testCapName", mockHeaders);
    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(), String.format(ReceivingException.SM_AUTHORIZE_ERROR_MSG, "sysadmin"));
    }
  }

  @Test
  public void testAuthorizeServiceDown() {
    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doThrow(new ResourceAccessException("Error"))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      securityService.authorize("sysadmin", "1", "dummy", ReceivingConstants.EXPIRY, mockHeaders);
    } catch (ReceivingException e) {

      assertEquals(e.getMessage(), ReceivingException.SM_SERVICE_DOWN_ERROR_MSG);
    }
  }

  @Test
  public void testAuthorizationFailure() {
    doReturn("http://mockSmServer/user").when(configUtils).getSmBaseUrl(anyInt(), anyString());

    doThrow(
            new RestClientResponseException(
                "",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "{\"messages\":[{\"type\":\"error\",\"code\":\"WM-SM-BE-0005\",\"desc\":\"Invalid Token.\"}]}"
                    .getBytes(),
                StandardCharsets.UTF_8))
        .when(restConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      securityService.authorize("sysadmin", "1", "dummy", ReceivingConstants.OVERAGES, mockHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.UNAUTHORIZED);
      assertEquals(
          e.getMessage(), String.format(ReceivingException.SM_AUTHORIZE_ERROR_MSG, "sysadmin"));
    }
  }

  @Test
  public void testAuthorizeWithCcmToken_1() {
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("AUTH_EXPIRY", "AUTH_OVERAGES", "AUTH_HACCP");

    boolean authorizeFlag =
        securityService.authorizeWithCcmToken("AUTH_EXPIRY", ReceivingConstants.EXPIRY);

    assertTrue(authorizeFlag);
  }

  @Test
  public void testAuthorizeWithCcmToken_2() {
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("AUTH_EXPIRY", "AUTH_OVERAGES", "AUTH_HACCP");

    boolean authorizeFlag =
        securityService.authorizeWithCcmToken("AUTH_OVERAGES", ReceivingConstants.OVERAGES);

    assertTrue(authorizeFlag);
  }

  @Test
  public void testAuthorizeWithCcmToken_3() {
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("AUTH_EXPIRY", "AUTH_OVERAGES", "AUTH_HACCP");

    boolean authorizeFlag =
        securityService.authorizeWithCcmToken(
            "AUTH_EXPIRY_AUTH_OVERAGES", ReceivingConstants.EXPIRY);

    assertTrue(authorizeFlag);
  }

  @Test
  public void testAuthorizeWithCcmToken_4() throws ReceivingException {
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("AUTH_EXPIRY", "AUTH_OVERAGES", "AUTH_HACCP");

    boolean authorizeFlag =
        securityService.authorizeWithCcmToken(
            "AUTH_EXPIRY_AUTH_OVERAGES", ReceivingConstants.OVERAGES);

    assertTrue(authorizeFlag);
  }

  @Test
  public void testAuthorizeWithCcmToken_5() throws ReceivingException {
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("AUTH_EXPIRY", "AUTH_OVERAGES", "AUTH_HACCP");

    boolean authorizeFlag =
        securityService.authorizeWithCcmToken("AUTH_HACCP", ReceivingConstants.HACCP);

    assertTrue(authorizeFlag);
  }

  @Test
  public void testAuthorizeWithCcmToken_6() {
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("AUTH_EXPIRY", "AUTH_OVERAGES", "AUTH_HACCP");

    boolean authorizeFlag =
        securityService.authorizeWithCcmToken("dummy", ReceivingConstants.EXPIRY);

    assertFalse(authorizeFlag);
  }

  @Test
  public void testAuthorizeWithCcmToken_7() {
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("AUTH_EXPIRY", "AUTH_OVERAGES", "AUTH_HACCP");

    boolean authorizeFlag =
        securityService.authorizeWithCcmToken("dummy", ReceivingConstants.OVERAGES);

    assertFalse(authorizeFlag);
  }

  @Test
  public void testAuthorizeWithCcmToken_8() {
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("AUTH_EXPIRY", "AUTH_OVERAGES", "AUTH_HACCP");

    boolean authorizeFlag =
        securityService.authorizeWithCcmToken("dummy", ReceivingConstants.HACCP);

    assertFalse(authorizeFlag);
  }

  @Test
  public void testValidateAuthorizationWithoutKotlinApp() {
    try {
      securityService.validateAuthorization(
          overrideRequest.getUserId(), Boolean.FALSE, Boolean.FALSE);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.UNAUTHORIZED);
      assertEquals(
          e.getMessage(),
          "sysadmin is unauthorized to approve this overage. Please have a supervisor enter their credentials to continue.");
    }
  }

  @Test
  public void testValidateAuthorizationWithKotlinApp() {
    try {
      securityService.validateAuthorization(
          overrideRequest.getUserId(), Boolean.FALSE, Boolean.TRUE);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.UNAUTHORIZED);
      assertEquals(
          e.getMessage(),
          "The QR Code scanned is not the correct code to authorize this override, please scan the correct QR Code.");
    }
  }
}
