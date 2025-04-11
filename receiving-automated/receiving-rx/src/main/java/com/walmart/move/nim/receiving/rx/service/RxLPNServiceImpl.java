package com.walmart.move.nim.receiving.rx.service;

import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.service.LPNService;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
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
 * @author k0a00vx
 */
@Service(RxConstants.RX_LPN_SERVICE)
public class RxLPNServiceImpl implements LPNService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RxLPNServiceImpl.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  /**
   * This service makes rest call to LPN service and returns a completable future
   *
   * @param count
   * @return CompletableFuture
   */
  @Async
  @Timed(
      name = "rxRetrieveLPNTimed",
      level1 = "uwms-receiving",
      level2 = "rxLPNServiceImpl",
      level3 = "retrieveLPN")
  @ExceptionCounted(
      name = "rxRetrieveLPNExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxLPNServiceImpl",
      level3 = "retrieveLPN")
  public CompletableFuture<Set<String>> retrieveLPN(int count, HttpHeaders headers) {
    LOGGER.info("Requesting License Plate Numbers (LPN) of count {}", count);

    ResponseEntity<Map> lpnResponse = null;
    String lpnUrl = appConfig.getLpnBaseUrl() + ReceivingConstants.LPN18_GENERATOR_BY_COUNT + count;
    try {
      LOGGER.info("Request lpnService to Generate LPN 18 GET URI for RX = {} ", lpnUrl);
      lpnResponse =
          retryableRestConnector.exchange(
              lpnUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    } catch (RestClientResponseException | ResourceAccessException exception) {
      LOGGER.error(
          "Exception from lpnService for Generate LPN 18 GET URI : {}, message : {}",
          lpnUrl,
          exception.getMessage(),
          exception);
      return CompletableFuture.completedFuture(new HashSet<>());
    }

    if (Objects.isNull(lpnResponse)
        || Objects.isNull(lpnResponse.getBody())
        || lpnResponse.getBody().isEmpty()
        || CollectionUtils.isEmpty((List) lpnResponse.getBody().get(ReceivingConstants.LPNS))) {
      LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, lpnUrl, "", "");
      return CompletableFuture.completedFuture(new HashSet<>());
    }

    Set<String> lpnSuccessResponse =
        new HashSet<>((List) lpnResponse.getBody().get(ReceivingConstants.LPNS));
    return CompletableFuture.completedFuture(lpnSuccessResponse);
  }
}
