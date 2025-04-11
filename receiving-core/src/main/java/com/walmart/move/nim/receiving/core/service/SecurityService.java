package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.AuthenticateRequest;
import com.walmart.move.nim.receiving.core.model.Capability;
import com.walmart.move.nim.receiving.core.model.OverrideRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.OverrideCapability;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SecurityService {
  private static final Logger LOG = LoggerFactory.getLogger(SecurityService.class);

  @Autowired private Gson gson;
  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = ReceivingConstants.BEAN_REST_CONNECTOR)
  private RestConnector simpleRestConnector;

  @Autowired private TenantSpecificConfigReader configUtils;

  /**
   * Authenticate user based on the credentials
   *
   * @param overrideRequest
   * @param httpHeaders
   * @return Map<String, Object>
   * @throws ReceivingException
   */
  public Map<String, Object> authenticate(
      OverrideRequest overrideRequest, Map<String, Object> httpHeaders) throws ReceivingException {
    final String facilityNum = httpHeaders.get(TENENT_FACLITYNUM).toString();
    String authenticateUrl =
        configUtils.getSmBaseUrl(getFacilityNum(), facilityNum) + "/users" + SM_URI_AUTHENTICATE;
    AuthenticateRequest authenticateRequest = new AuthenticateRequest();
    authenticateRequest.setUserId(overrideRequest.getUserId());
    authenticateRequest.setPassword(overrideRequest.getPassword());
    authenticateRequest.setBusinessUnitId(facilityNum);
    authenticateRequest.setDomainName(ReceivingConstants.LOCAL);

    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.set(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    requestHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    requestHeaders.set(TENENT_FACLITYNUM, facilityNum);
    requestHeaders.set(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE).toString());
    requestHeaders.set(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString());

    ResponseEntity<Map> smResponseEntity = null;

    try {
      smResponseEntity =
          simpleRestConnector.exchange(
              authenticateUrl,
              HttpMethod.PUT,
              new HttpEntity<>(gson.toJson(authenticateRequest), requestHeaders),
              Map.class);

      if (Objects.isNull(smResponseEntity) || !smResponseEntity.hasBody()) {
        LOG.error(
            ReceivingConstants.RESTUTILS_INFO_MESSAGE,
            authenticateUrl,
            authenticateRequest,
            smResponseEntity);

        throw new ReceivingException(
            ReceivingException.SM_AUTHENTICATE_ERROR_MSG,
            HttpStatus.UNAUTHORIZED,
            ReceivingException.SM_AUTHENTICATE_ERROR_CODE,
            ReceivingException.SM_AUTHENTICATE_ERROR_HEADER);
      }
    } catch (RestClientResponseException e) {
      LOG.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          authenticateUrl,
          authenticateRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingException(
          ReceivingException.SM_AUTHENTICATE_ERROR_MSG,
          HttpStatus.UNAUTHORIZED,
          ReceivingException.SM_AUTHENTICATE_ERROR_CODE,
          ReceivingException.SM_AUTHENTICATE_ERROR_HEADER);
    } catch (ResourceAccessException e) {
      LOG.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          authenticateUrl,
          authenticateRequest,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingException(
          ReceivingException.SM_SERVICE_DOWN_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.SM_SERVICE_DOWN_ERROR_CODE);
    }

    return smResponseEntity.getBody();
  }

  /**
   * Authorize user based on the capability
   *
   * @param securityId
   * @param token
   * @param action
   * @param httpHeaders
   * @return boolean
   * @throws ReceivingException
   */
  public boolean authorize(
      String userId,
      String securityId,
      String token,
      String action,
      Map<String, Object> httpHeaders)
      throws ReceivingException {
    boolean isAuthorized = false;
    final String facilityNum = httpHeaders.get(TENENT_FACLITYNUM).toString();
    String capabilitiesUrl =
        configUtils.getSmBaseUrl(getFacilityNum(), facilityNum)
            + "/users/"
            + securityId
            + SM_URI_CAPABILITIES;

    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.set(ReceivingConstants.SECURITY_TOKEN, token);
    requestHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, securityId);
    requestHeaders.set(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    requestHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    requestHeaders.set(TENENT_FACLITYNUM, facilityNum);
    requestHeaders.set(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE).toString());
    requestHeaders.set(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString());

    ResponseEntity<String> capabilitiesResponse = null;

    try {
      capabilitiesResponse =
          simpleRestConnector.exchange(
              capabilitiesUrl, HttpMethod.GET, new HttpEntity<>(requestHeaders), String.class);

      if (Objects.isNull(capabilitiesResponse) || !capabilitiesResponse.hasBody()) {
        LOG.error(
            ReceivingConstants.RESTUTILS_INFO_MESSAGE, capabilitiesUrl, "", capabilitiesResponse);

        throw new ReceivingException(
            String.format(ReceivingException.SM_AUTHORIZE_ERROR_MSG, userId),
            HttpStatus.UNAUTHORIZED,
            ReceivingException.SM_AUTHORIZE_ERROR_CODE,
            ReceivingException.SM_AUTHORIZE_ERROR_HEADER);
      }
    } catch (RestClientResponseException e) {
      LOG.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          capabilitiesUrl,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingException(
          String.format(ReceivingException.SM_AUTHORIZE_ERROR_MSG, userId),
          HttpStatus.UNAUTHORIZED,
          ReceivingException.SM_AUTHORIZE_ERROR_CODE,
          ReceivingException.SM_AUTHORIZE_ERROR_HEADER);
    } catch (ResourceAccessException e) {
      LOG.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          capabilitiesUrl,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingException(
          ReceivingException.SM_SERVICE_DOWN_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.SM_SERVICE_DOWN_ERROR_CODE);
    }

    List<Capability> capabilities =
        Arrays.asList(gson.fromJson(capabilitiesResponse.getBody(), Capability[].class));

    if (CollectionUtils.isEmpty(capabilities)) {
      throw new ReceivingException(
          String.format(ReceivingException.SM_AUTHORIZE_ERROR_MSG, userId),
          HttpStatus.UNAUTHORIZED,
          ReceivingException.SM_AUTHORIZE_ERROR_CODE,
          ReceivingException.SM_AUTHORIZE_ERROR_HEADER);
    }

    switch (action.toLowerCase()) {
      case ReceivingConstants.EXPIRY:
        isAuthorized =
            capabilities
                .parallelStream()
                .anyMatch(
                    capability ->
                        OverrideCapability.EXPIRY
                            .getText()
                            .equalsIgnoreCase(capability.getCapName()));
        break;
      case ReceivingConstants.OVERAGES:
        isAuthorized =
            capabilities
                .parallelStream()
                .anyMatch(
                    capability ->
                        OverrideCapability.OVERAGES
                            .getText()
                            .equalsIgnoreCase(capability.getCapName()));
        break;
      case ReceivingConstants.HACCP:
        isAuthorized =
            capabilities
                .parallelStream()
                .anyMatch(
                    capability ->
                        OverrideCapability.HACCP
                            .getText()
                            .equalsIgnoreCase(capability.getCapName()));
        break;
      default:
        isAuthorized = false;
        break;
    }

    return isAuthorized;
  }

  /**
   * @param clientToken
   * @param action
   * @return boolean
   */
  public boolean authorizeWithCcmToken(String clientToken, String action) {
    LOG.info("Enter authorizeWithCcmToken() with clientToken:{}, action:{}", clientToken, action);
    boolean isAuthorized = false;
    final String authExpiry =
        configUtils.getCcmValue(TenantContext.getFacilityNum(), "authExpiryToken", "AUTH_EXPIRY");
    final String authOverage =
        configUtils.getCcmValue(
            TenantContext.getFacilityNum(), "authOverageToken", "AUTH_OVERAGES");
    final String authHaccp =
        configUtils.getCcmValue(TenantContext.getFacilityNum(), "authHaccpToken", "AUTH_HACCP");
    final String authExpiryAndOverage = String.format("%s_%s", authExpiry, authOverage);

    switch (action.toLowerCase()) {
      case ReceivingConstants.EXPIRY:
        if (clientToken.equalsIgnoreCase(authExpiry)
            || clientToken.equalsIgnoreCase(authExpiryAndOverage)) {
          isAuthorized = true;
        }
        break;
      case ReceivingConstants.OVERAGES:
        if (clientToken.equalsIgnoreCase(authOverage)
            || clientToken.equalsIgnoreCase(authExpiryAndOverage)) {
          isAuthorized = true;
        }
        break;
      case ReceivingConstants.HACCP:
        if (clientToken.equalsIgnoreCase(authHaccp)) {
          isAuthorized = true;
        }
        break;
      default:
        isAuthorized = false;
        break;
    }

    LOG.info("Exit authorizeWithCcmToken() with isAuthorized:{}", isAuthorized);
    return isAuthorized;
  }

  /**
   * @param userId
   * @param authorization
   * @param isKotlinEnabled
   * @throws ReceivingException
   */
  public void validateAuthorization(String userId, boolean authorization, boolean isKotlinEnabled)
      throws ReceivingException {
    if (!authorization) {
      throw new ReceivingException(
          isKotlinEnabled
              ? ReceivingException.TOKEN_AUTHORIZE_ERROR_MSG
              : String.format(ReceivingException.SM_AUTHORIZE_ERROR_MSG, userId),
          HttpStatus.UNAUTHORIZED,
          ReceivingException.SM_AUTHORIZE_ERROR_CODE,
          ReceivingException.SM_AUTHORIZE_ERROR_HEADER);
    }
  }
}
