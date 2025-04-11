package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.service.LPNService;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
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
 * Service for getting lpns of LPN18 format
 *
 * @author b0s06hg
 */
@Service(RdcConstants.RDC_LPN_SERVICE)
public class RdcLPNServiceImpl implements LPNService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcLPNServiceImpl.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  /**
   * This service makes rest call to LPN cloud and returns a completable future This method will
   * generate 18 or 25 digit LPNs based on channel type (DA/SSTK)
   *
   * @param count
   * @return CompletableFuture
   */
  @Async
  @Timed(
      name = "rdcRetrieveLPNTimed",
      level1 = "uwms-receiving",
      level2 = "rdcLPNServiceImpl",
      level3 = "retrieveLPN")
  @ExceptionCounted(
      name = "rdcRetrieveLPNExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rdcLPNServiceImpl",
      level3 = "retrieveLPN")
  public CompletableFuture<Set<String>> retrieveLPN(int count, HttpHeaders headers) {
    LOGGER.info("Requesting LPN of count {}", count);

    ResponseEntity<Map> response = null;
    String lpnGeneratorUrl =
        appConfig.getLpnBaseUrl() + ReceivingConstants.LPN18_GENERATOR_BY_COUNT + count;
    try {
      LOGGER.info("Request lpnService to Generate LPN 18 GET URI = {} ", lpnGeneratorUrl);
      response =
          retryableRestConnector.exchange(
              lpnGeneratorUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    } catch (RestClientResponseException | ResourceAccessException e) {
      LOGGER.error(
          "Response from lpnService for Generate LPN 18 GET URI : {}, message : {}",
          lpnGeneratorUrl,
          e.getMessage(),
          e);
      return CompletableFuture.completedFuture(new HashSet<>());
    }

    if (Objects.isNull(response)
        || Objects.isNull(response.getBody())
        || response.getBody().isEmpty()
        || CollectionUtils.isEmpty((List) response.getBody().get(ReceivingConstants.LPNS))) {
      LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, lpnGeneratorUrl, "", "");
      return CompletableFuture.completedFuture(new HashSet<>());
    }

    Set<String> successResponse =
        new HashSet<>((List) response.getBody().get(ReceivingConstants.LPNS));
    return CompletableFuture.completedFuture(successResponse);
  }
}
