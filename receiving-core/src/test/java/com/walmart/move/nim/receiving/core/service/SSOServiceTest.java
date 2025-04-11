package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.SsoConfig;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.sso.TokenRequestDTO;
import com.walmart.move.nim.receiving.core.model.sso.TokenResponseDTO;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SSOServiceTest extends ReceivingTestBase {

  @InjectMocks private SSOService ssoService;
  @Mock private SsoConfig ssoConfig;
  @Mock private RestConnector simpleRestConnector;

  private String code = "dummycode";
  private String token = "dummytoken";
  private String claimsSet =
      "{\"active\":true,\"scope\":\"openid\",\"client_id\":\"dummyclientid\",\"token_type\":\"Bearer\",\"exp\":1604432325000,\"iat\":1604430525000,\"nbf\":1604430525000,\"sub\":\"7c1fc868-4636-42d2-aa03-0ba9e44adae3\",\"aud\":\"dbf1e337-2aaa-4b58-a468-ed0fdeee463f\",\"iss\":\"https://idp.qa.sso.platform.qa.walmart.com/platform-sso-server/\",\"username\":\"John Wick\",\"loginId\":\"homeoffice\\\\j0w0007\",\"email\":\"John.Wick@walmartlabs.com\",\"iamToken\":\"MDUxODMyMDE4Hs4OG739mAGtVIMhR/jkjbuZKOHkZd5Ipz45Ei3rOfBMoe21CY0a6Bc9mwzf4GS3VvyWmmaBuuc4woE1gXf9xagGzo3D02eUhgCLEWA9g1gRwiY2qR4oMMbCWkYUqdL98nKajkYz2eMSL49erj1hrtqNWdVQkwgM7SfEqJW1cUZSICq3u65XlQPdm29CARSxpS5UDyyyo6LgBEktmKKFo6iGE6vzwFRDsuB9waH46BPzEkfOuXfpbUMypw1HRdSTPzgNEnUnkwWKvmaUOXe2f9UXgVImZnz0Ea8WHO+WEbvZR8J0EPPYTRLNbD8fUAnM9YoAcB5dKV2ncHWEm1FV7IM1olVUfITpDkOFP6YeN4EAfq6hbq9RrUmXIJceJqaba1Il+kFg/Hp9crELsvE3lA==\"}";
  private String inactivetokenClaimsSet = "{\"active\": false}";

  private String username = "John Wick";
  private String userId = "j0w0007";

  private String idpUrl = "https://idp.qa.sso.platform.qa.walmart.com/platform-sso-server";
  private String clientId = "dummyclientid";
  private String clientRedirectUri = "dummyredirecturi";
  private String clientLogoutUri = "dummylogouturi";

  private TokenResponseDTO tokenResponseDTO;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    tokenResponseDTO = new TokenResponseDTO();
    tokenResponseDTO.setIdToken(token);
  }

  @AfterMethod
  public void resetMocks() {
    reset(ssoConfig);
    reset(simpleRestConnector);
  }

  @Test
  public void testAuthenticateSuccess() {
    when(simpleRestConnector.post(
            anyString(), any(TokenRequestDTO.class), any(HttpHeaders.class), any()))
        .thenReturn(new ResponseEntity<>(tokenResponseDTO, HttpStatus.OK));

    String tokenFromIdp = ssoService.authenticate(code);
    assertEquals(tokenFromIdp, token);
    verify(simpleRestConnector, times(1))
        .post(anyString(), any(TokenRequestDTO.class), any(HttpHeaders.class), any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testAuthenticateUnauthorised() {
    when(simpleRestConnector.post(
            anyString(), any(TokenRequestDTO.class), any(HttpHeaders.class), any()))
        .thenThrow(
            new RestClientResponseException(
                "Unauthorised",
                401,
                "Unauthorised",
                MockHttpHeaders.getHeaders(),
                new String(
                        "{\n"
                            + "    \"error\": \"invalid_token\",\n"
                            + "    \"error_description\": \"AuthorizationCode [84F8F2FCF2F04B54A6F8B1A07843BA32] : not found\"\n"
                            + "}")
                    .getBytes(),
                null));

    String tokenFromIdp = ssoService.authenticate(code);
    verify(simpleRestConnector, times(1))
        .post(anyString(), any(TokenRequestDTO.class), any(HttpHeaders.class), any());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testAuthenticateServiceUnavailable() {
    when(simpleRestConnector.post(
            anyString(), any(TokenRequestDTO.class), any(HttpHeaders.class), any()))
        .thenThrow(new ResourceAccessException("I/O Error"));

    String tokenFromIdp = ssoService.authenticate(code);
    verify(simpleRestConnector, times(1))
        .post(anyString(), any(TokenRequestDTO.class), any(HttpHeaders.class), any());
  }

  @Test
  public void testValidateTokenSuccess() {
    when(simpleRestConnector.post(anyString(), anyString(), any(HttpHeaders.class), any()))
        .thenReturn(new ResponseEntity<>(claimsSet, HttpStatus.OK));

    Pair<String, String> userInfo = ssoService.validateToken(token);
    assertEquals(userInfo.getKey(), userId);
    assertEquals(userInfo.getValue(), username);
    verify(simpleRestConnector, times(1))
        .post(anyString(), any(String.class), any(HttpHeaders.class), any());
  }

  @Test
  public void testValidateTokenExpired() {
    when(simpleRestConnector.post(anyString(), anyString(), any(HttpHeaders.class), any()))
        .thenReturn(new ResponseEntity<>(inactivetokenClaimsSet, HttpStatus.OK));

    Pair<String, String> userInfo = ssoService.validateToken(token);
    assertNull(userInfo);
    verify(simpleRestConnector, times(1))
        .post(anyString(), any(String.class), any(HttpHeaders.class), any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateTokenUnauthorised() {
    when(simpleRestConnector.post(anyString(), anyString(), any(HttpHeaders.class), any()))
        .thenThrow(
            new RestClientResponseException(
                "Unauthorised",
                401,
                "Unauthorised",
                MockHttpHeaders.getHeaders(),
                new String(
                        "<BadClientCredentialsException>\n"
                            + "    <error>invalid_client</error>\n"
                            + "    <error_description>Bad client credentials</error_description>\n"
                            + "</BadClientCredentialsException>")
                    .getBytes(),
                null));

    Pair<String, String> userInfo = ssoService.validateToken(token);
    verify(simpleRestConnector, times(1))
        .post(anyString(), any(String.class), any(HttpHeaders.class), any());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testValidateTokenServiceUnavailable() {
    when(simpleRestConnector.post(anyString(), anyString(), any(HttpHeaders.class), any()))
        .thenThrow(new ResourceAccessException("I/O Error"));

    Pair<String, String> userInfo = ssoService.validateToken(token);
    verify(simpleRestConnector, times(1))
        .post(anyString(), any(String.class), any(HttpHeaders.class), any());
  }

  @Test
  public void testGetRedirectUri() {
    when(ssoConfig.getSsoPlatformBaseUrl()).thenReturn(idpUrl);
    when(ssoConfig.getClientRegisteredRedirectUri()).thenReturn(clientRedirectUri);
    when(ssoConfig.getClientId()).thenReturn(clientId);
    assertNotNull(ssoService.getRedirectUri());
  }

  @Test
  public void testGetLogoutRedirectUri() {
    when(ssoConfig.getSsoPlatformBaseUrl()).thenReturn(idpUrl);
    when(ssoConfig.getClientLogoutRedirectUrl()).thenReturn(clientLogoutUri);
    assertEquals(
        ssoService.getLogoutRedirectUri(),
        "https://idp.qa.sso.platform.qa.walmart.com/platform-sso-server/ppidp/logout?post_logout_redirect_uri=dummylogouturi");
  }
}
