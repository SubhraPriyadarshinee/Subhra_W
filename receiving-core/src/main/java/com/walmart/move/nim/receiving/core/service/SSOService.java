package com.walmart.move.nim.receiving.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.SsoConfig;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.sso.SSOConstants;
import com.walmart.move.nim.receiving.core.model.sso.TokenRequestDTO;
import com.walmart.move.nim.receiving.core.model.sso.TokenResponseDTO;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SSOService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SSOService.class);

  @ManagedConfiguration private SsoConfig ssoConfig;

  @Resource(name = "restConnector")
  private RestConnector simpleRestConnector;

  public String authenticate(String code) {
    String url = ssoConfig.getSsoPlatformBaseUrl() + SSOConstants.TOKEN_END_POINT;

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, getAuthzHeaderValue());
    headers.setContentType(MediaType.APPLICATION_JSON);
    TokenRequestDTO tokenRequest = new TokenRequestDTO();
    tokenRequest.setCode(code);
    tokenRequest.setGrantType(SSOConstants.AUTHORIZATION_CODE_GRANT);
    tokenRequest.setRedirectUri(ssoConfig.getClientRegisteredRedirectUri());
    ResponseEntity<TokenResponseDTO> response;
    try {
      response = simpleRestConnector.post(url, tokenRequest, headers, TokenResponseDTO.class);
    } catch (RestClientResponseException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.IDP_SSO_UNAUTHORISED,
          String.format(SSOConstants.IDP_SSO_UNAUTHORISED, e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.IDP_SERVICE_ERROR, SSOConstants.IDP_SSO_SERVICE_DOWN);
    }

    LOGGER.info("{}", response.getBody());
    if (Objects.nonNull(response.getBody())) {
      return response.getBody().getIdToken();
    }
    return null;
  }

  private String getAuthzHeaderValue() {
    String clientIdAndSecret =
        String.format("%s:%s", ssoConfig.getClientId(), ssoConfig.getClientSecret());
    return String.format(
        "Basic %s", new String(Base64.getEncoder().encode(clientIdAndSecret.getBytes())));
  }

  public Pair<String, String> validateToken(String token) {
    String payloadForClaimsSet = SSOConstants.TOKEN_PARAM_NAME + "=" + token;
    String url = ssoConfig.getSsoPlatformBaseUrl() + SSOConstants.CLAIMS_SET_END_POINT;

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, getAuthzHeaderValue());
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    ResponseEntity<String> claimsSetResponse;
    try {
      claimsSetResponse = simpleRestConnector.post(url, payloadForClaimsSet, headers, String.class);
    } catch (RestClientResponseException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.IDP_SSO_UNAUTHORISED,
          String.format(SSOConstants.IDP_SSO_UNAUTHORISED, e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.IDP_SERVICE_ERROR, SSOConstants.IDP_SSO_SERVICE_DOWN);
    }

    TypeReference<HashMap<String, Object>> typeRef =
        new TypeReference<HashMap<String, Object>>() {};
    Map<String, Object> claimsSet =
        JacksonParser.convertJsonToObject(claimsSetResponse.getBody(), typeRef);
    if (!(boolean) claimsSet.get(SSOConstants.UA_ACTIVE)) {
      return null;
    }
    String userIdLongForm = (String) claimsSet.get(SSOConstants.UA_LOGIN_ID);
    String userName = (String) claimsSet.get(SSOConstants.UA_NAME);
    String userId = userIdLongForm.substring(userIdLongForm.indexOf('\\') + 1);
    LOGGER.info("Report dashboard - User id is {}", userId);
    LOGGER.info("Report dashboard - User name is {}", userName);
    return new Pair<>(userId, userName);
  }

  public String getRedirectUri() {
    String redirectUrl = ssoConfig.getSsoPlatformBaseUrl() + SSOConstants.AUTHORIZE_END_POINT;
    String nonce = new BigInteger(50, new SecureRandom()).toString(16);
    String state = new BigInteger(50, new SecureRandom()).toString(16);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put(SSOConstants.RESPONSE_TYPE_PARAM_NAME, SSOConstants.OPENID_RESTYPE_CODE);
    queryParams.put(SSOConstants.CLIENT_ID_PARAM_NAME, ssoConfig.getClientId());
    queryParams.put(SSOConstants.SCOPE_PARAM_NAME, SSOConstants.OPENID_TOKEN_SCOPE);
    queryParams.put(
        SSOConstants.REDIRECT_URI_PARAM_NAME, ssoConfig.getClientRegisteredRedirectUri());
    queryParams.put(SSOConstants.NONCE_PARAM_NAME, nonce);
    queryParams.put(SSOConstants.STATE_PARAM_NAME, state);
    return ReceivingUtils.replacePathParamsAndQueryParams(redirectUrl, null, queryParams)
        .toString();
  }

  public String getLogoutRedirectUri() {
    String logoutRedirectUri = ssoConfig.getSsoPlatformBaseUrl() + SSOConstants.LOGOUT_END_POINT;
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put(
        SSOConstants.LOGOUT_REDIRECT_URI_PARAM_NAME, ssoConfig.getClientLogoutRedirectUrl());
    return ReceivingUtils.replacePathParamsAndQueryParams(logoutRedirectUri, null, queryParams)
        .toString();
  }
}
