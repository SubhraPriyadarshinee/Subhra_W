package com.walmart.move.nim.receiving.witron.service;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.service.LPNService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * @author sks0013
 */
@Service(GdcConstants.WITRON_LPN_SERVICE)
public class WitronLPNServiceImpl implements LPNService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WitronLPNServiceImpl.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @Autowired protected TenantSpecificConfigReader configUtils;

  /**
   * This service makes rest call to LPN cloud and returns a completable future
   *
   * @param count
   * @return CompletableFuture
   */
  @Async
  @Timed(
      name = "retrieveLPNTimed",
      level1 = "uwms-receiving",
      level2 = "witronLPNServiceImpl",
      level3 = "retrieveLPN")
  @ExceptionCounted(
      name = "retrieveLPNExceptionCount",
      level1 = "uwms-receiving",
      level2 = "witronLPNServiceImpl",
      level3 = "retrieveLPN")
  public CompletableFuture<Set<String>> retrieveLPN(int count, HttpHeaders headers) {
    LOGGER.info("Requesting LPN of count {}", count);

    ResponseEntity<Map> response = null;
    String lpnGeneratorUrlPath =
        configUtils.getConfiguredFeatureFlag(
                headers.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
                ReceivingConstants.IS_LPN_7_DIGIT_ENABLED,
                false)
            ? ReceivingConstants.LPN7_GENERATOR_BY_COUNT
            : ReceivingConstants.LPN18_GENERATOR_BY_COUNT;
    String url = appConfig.getLpnBaseUrl() + lpnGeneratorUrlPath + count;

    try {
      LOGGER.info("Request lpnService to Generate LPN GET URI = {} ", url);
      response =
          retryableRestConnector.exchange(
              url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    } catch (RestClientResponseException | ResourceAccessException e) {
      LOGGER.error(
          "Response from lpnService for Generate LPN GET URI : {}, message : {}",
          url,
          e.getMessage(),
          e);
      // Sending empty if we get other than 200 status code
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
    return CompletableFuture.completedFuture(successResponse);
  }
}
