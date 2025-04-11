package com.walmart.move.nim.receiving.fixture.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.SecurityUtil;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.fixture.common.CTToken;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.entity.ControlTowerTracker;
import com.walmart.move.nim.receiving.fixture.model.CTAuthPayload;
import com.walmart.move.nim.receiving.fixture.model.CTWarehouseResponse;
import com.walmart.move.nim.receiving.fixture.model.PutAwayInventory;
import com.walmart.move.nim.receiving.fixture.repositories.ControlTowerTrackerRepository;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

public class ControlTowerService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ControlTowerService.class);
  @ManagedConfiguration private FixtureManagedConfig fixtureManagedConfig;
  @Autowired private CTToken ctToken;
  @Autowired private ControlTowerTrackerRepository controlTowerTrackerRepository;

  @Value("${secrets.key}")
  private String secretKey;

  @Resource(name = "restConnector")
  private RestConnector simpleRestConnector;

  private void authenticate(CTAuthPayload ctAuthPayload) {
    String url = fixtureManagedConfig.getCtBaseUrl() + FixtureConstants.CT_AUTHENTICATE_URL;
    ResponseEntity<CTToken> response = null;
    HttpHeaders httpHeaders = getHttpHeaders();
    try {
      response =
          simpleRestConnector.post(
              url, JacksonParser.writeValueAsString(ctAuthPayload), httpHeaders, CTToken.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_CONTROL_TOWER_REQ,
          String.format(
              ReceivingConstants.BAD_RESPONSE_ERROR_MSG,
              ReceivingConstants.CONTROL_TOWER,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.CONTROL_TOWER_SERVICE_ERROR,
          ReceivingConstants.CONTROL_TOWER_SERVICE_DOWN);
    }
    ctToken = response.getBody();
  }

  @Async
  public void putAwayInventory(
      List<PutAwayInventory> putAwayInventoryList, ControlTowerTracker controlTowerTracker) {
    String response = putAwayInventory(putAwayInventoryList);
    if (!StringUtils.isEmpty(response)) {
      // update ack key in DB
      controlTowerTracker.setAckKey(response);
      save(controlTowerTracker);
    }
  }

  @Timed(
      name = "putAwayCTTimed",
      level1 = "uwms-receiving",
      level2 = "controlTowerService",
      level3 = "putAwayInventory")
  @ExceptionCounted(
      name = "putAwayCTExceptionCount",
      level1 = "uwms-receiving",
      level2 = "controlTowerService",
      level3 = "putAwayInventory")
  public String putAwayInventory(List<PutAwayInventory> putAwayInventoryList) {
    String url = fixtureManagedConfig.getCtBaseUrl() + FixtureConstants.CT_POST_INVENTORY;
    ResponseEntity<Object> response = null;
    HttpHeaders httpHeaders = getHttpHeadersWithAuthToken();
    String request = JacksonParser.writeValueAsString(putAwayInventoryList);

    try {
      response = simpleRestConnector.post(url, request, httpHeaders, Object.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          request,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));

      forceUpdateTokenOn401(e.getRawStatusCode());

      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_CONTROL_TOWER_REQ,
          String.format(
              ReceivingConstants.BAD_RESPONSE_ERROR_MSG,
              ReceivingConstants.CONTROL_TOWER,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          request,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.CONTROL_TOWER_SERVICE_ERROR,
          ReceivingConstants.CONTROL_TOWER_SERVICE_DOWN);
    }
    if (Objects.isNull(response.getBody())) {
      LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, request, response.getBody());
      return null;
    }

    LOGGER.info(
        ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, request, response.getBody().toString());
    return response.getBody().toString();
  }

  private void forceUpdateTokenOn401(int rawStatusCode) {
    if (rawStatusCode == HttpStatus.UNAUTHORIZED.value()) {
      getToken(true);
    }
  }

  public void refreshToken() {
    getToken(true);
  }

  @Timed(
      name = "getCTStatusTimed",
      level1 = "uwms-receiving",
      level2 = "controlTowerService",
      level3 = "getInventoryStatus")
  @ExceptionCounted(
      name = "getCTStatusExceptionCount",
      level1 = "uwms-receiving",
      level2 = "controlTowerService",
      level3 = "getInventoryStatus")
  public CTWarehouseResponse getInventoryStatus(String ackKey) {
    ResponseEntity<CTWarehouseResponse> response = null;
    HttpHeaders httpHeaders = getHttpHeadersWithAuthToken();
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(FixtureConstants.BATCH_ID, ackKey);
    String url =
        ReceivingUtils.replacePathParams(
                fixtureManagedConfig.getCtBaseUrl() + FixtureConstants.CT_GET_INVENTORY, pathParams)
            .toString();
    try {
      response =
          simpleRestConnector.exchange(
              url, HttpMethod.GET, new HttpEntity<>(httpHeaders), CTWarehouseResponse.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));

      forceUpdateTokenOn401(e.getRawStatusCode());

      return null;
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      return null;
    }
    if (Objects.isNull(response)) {
      LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, "", response.getBody());
      return null;
    }

    LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, "", response.getBody().toString());
    return response.getBody();
  }

  private HttpHeaders getHttpHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, "application/json");
    httpHeaders.set(ReceivingConstants.ACCEPT, "application/json");
    return httpHeaders;
  }

  private HttpHeaders getHttpHeadersWithAuthToken() {
    HttpHeaders httpHeaders = getHttpHeaders();
    httpHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer " + getToken(false));
    return httpHeaders;
  }

  private String getToken(boolean forceUpdateToken) {
    boolean isTokenInvalid = true;
    if (!Objects.isNull(ctToken) && !StringUtils.isEmpty(ctToken.getToken())) {
      // sub 5 mins from expiry time, so that there we refresh token 5 mins earlier
      long systemTimeInEpoch = System.currentTimeMillis();
      long tokenExpiryInEpoch = ctToken.getExpires().getTime() - 300000;
      if (systemTimeInEpoch < tokenExpiryInEpoch) {
        isTokenInvalid = false;
      }
    }
    if (forceUpdateToken || isTokenInvalid) {
      // update token
      String password = null;
      try {
        password = SecurityUtil.decryptValue(secretKey, fixtureManagedConfig.getCtPassword());
      } catch (Exception e) {
        LOGGER.error(
            ReceivingConstants.DECRYPTION_ERROR_MESSAGE,
            "password",
            "",
            ExceptionUtils.getStackTrace(e));
        throw new ReceivingInternalException(
            ExceptionCodes.RECEIVING_INTERNAL_ERROR, ReceivingConstants.DECRYPT_ERROR_MESSAGE);
      }
      authenticate(
          CTAuthPayload.builder()
              .userName(fixtureManagedConfig.getCtUserName())
              .password(password)
              .build());
    }
    return ctToken.getToken();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @InjectTenantFilter
  public ControlTowerTracker putForTracking(String lpn) {
    return controlTowerTrackerRepository.save(
        ControlTowerTracker.builder().submissionStatus(EventTargetStatus.PENDING).lpn(lpn).build());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @InjectTenantFilter
  public ControlTowerTracker resetForTracking(String lpn) {
    ControlTowerTracker byLpn = controlTowerTrackerRepository.findByLpn(lpn);
    if (Objects.isNull(byLpn)) {
      byLpn =
          ControlTowerTracker.builder()
              .submissionStatus(EventTargetStatus.PENDING)
              .lpn(lpn)
              .build();
    }

    byLpn.setSubmissionStatus(EventTargetStatus.PENDING);
    byLpn.setRetriesCount(0);
    byLpn.setAckKey(null);
    return controlTowerTrackerRepository.save(byLpn);
  }

  @Transactional(propagation = Propagation.NEVER)
  public ControlTowerTracker save(ControlTowerTracker controlTowerTracker) {
    return controlTowerTrackerRepository.save(controlTowerTracker);
  }

  /**
   * This method saves the managed object only. Please note, this dooes not
   * require @InjectTenantFilter
   *
   * @param controlTowerTrackerList
   */
  @Transactional
  public void saveManagedObjectsOnly(List<ControlTowerTracker> controlTowerTrackerList) {
    controlTowerTrackerRepository.saveAll(controlTowerTrackerList);
  }

  @Transactional
  public List<ControlTowerTracker> getCTEntitiesToValidate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.MINUTE, -1);
    Date lessThan = cal.getTime();
    List<ControlTowerTracker> entries =
        controlTowerTrackerRepository
            .findBySubmissionStatusNotAndRetriesCountLessThanAndCreateTsLessThan(
                EventTargetStatus.DELETE, 5, lessThan, PageRequest.of(0, 10));
    // Increase the retry count
    entries.forEach(ctTracker -> ctTracker.setRetriesCount(ctTracker.getRetriesCount() + 1));
    controlTowerTrackerRepository.saveAll(entries);
    return entries;
  }
}
