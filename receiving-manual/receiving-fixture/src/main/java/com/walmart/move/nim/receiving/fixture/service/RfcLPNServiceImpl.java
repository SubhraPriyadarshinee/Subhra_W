package com.walmart.move.nim.receiving.fixture.service;

import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.service.LPNService;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Service for getting lpns of LPN25 format
 *
 * @author sks0013
 */
@Service(FixtureConstants.RFC_LPN_SERVICE)
public class RfcLPNServiceImpl implements LPNService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RfcLPNServiceImpl.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  /**
   * This service makes rest call to LPN cloud and returns a completable future
   *
   * @param count
   * @return CompletableFuture
   */
  @Timed(
      name = "retrieveLPNTimed",
      level1 = "uwms-receiving",
      level2 = "lpnService",
      level3 = "retrieveLPN")
  @ExceptionCounted(
      name = "retrieveLPNExceptionCount",
      level1 = "uwms-receiving",
      level2 = "lpnService",
      level3 = "retrieveLPN")
  @Async
  public CompletableFuture<Set<String>> retrieveLPN(int count, HttpHeaders headers) {
    LOGGER.info("Rfc Requesting LPN of count {}", count);

    ResponseEntity<Map> response = null;
    String url = appConfig.getLpnBaseUrl() + ReceivingConstants.LPN18_GENERATOR_BY_COUNT + count;
    try {
      response =
          retryableRestConnector.exchange(
              url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    } catch (RestClientResponseException | ResourceAccessException e) {
      // Sending empty if we get other than 200 status code
      LOGGER.error("Error while fetching LPNs, Exception - {}", e.getMessage(), e);
      return CompletableFuture.completedFuture(new HashSet<>());
    }

    if (Objects.isNull(response)
        || Objects.isNull(response.getBody())
        || response.getBody().isEmpty()
        || CollectionUtils.isEmpty((List) response.getBody().get(ReceivingConstants.LPNS))) {
      LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, "", "");
      // Sending empty if we get empty response
      return CompletableFuture.completedFuture(new HashSet<>());
    }

    Set<String> successResponse =
        new HashSet<>((List) response.getBody().get(ReceivingConstants.LPNS));
    LOGGER.info("Got LPNs {}", successResponse);
    return CompletableFuture.completedFuture(successResponse);
  }
}
