package com.walmart.move.nim.receiving.fixture.client;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.SecurityUtil;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.model.FixturesItemAttribute;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ItemREPServiceClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(ItemREPServiceClient.class);

  @ManagedConfiguration private FixtureManagedConfig fixtureManagedConfig;
  @Autowired private RetryableRestConnector retryableRestConnector;
  @Autowired private Gson gson;

  @Value("${secrets.key}")
  private String secretKey;

  @Timed(
      name = "getItemDetailsOfItemNumbersFromREPTimed",
      level1 = "uwms-receiving",
      level2 = "itemREPServiceClient",
      level3 = "cancelLabel")
  @ExceptionCounted(
      name = "getItemDetailsOfItemNumbersFromREPCount",
      level1 = "uwms-receiving",
      level2 = "itemREPServiceClient",
      level3 = "cancelLabel")
  public Map<Integer, FixturesItemAttribute> getItemDetailsOfItemNumbersFromREP(
      Set<Long> itemNumbers) {
    LOGGER.info("Item detail request info {}", itemNumbers);
    Map<Integer, FixturesItemAttribute> fixturesItemAttributeMap = new HashMap<>();
    Collection<List<Long>> batchifyCollection =
        ReceivingUtils.batchifyCollection(itemNumbers, fixtureManagedConfig.getItemRepBatchSize());
    HttpHeaders requestHeaders = buildHttpHeaders();
    String url = fixtureManagedConfig.getItemRepBaseUrl();
    URI itemRepSummaryUri =
        UriComponentsBuilder.fromHttpUrl(url)
            .queryParam(ReceivingConstants.TENENT_FACILITYNUMBER, TenantContext.getFacilityNum())
            .queryParam(
                ReceivingConstants.TENENT_FACILITY_COUNTRY_CODE,
                TenantContext.getFacilityCountryCode())
            .build()
            .toUri();

    for (List<Long> itemNbrEOSBatch : batchifyCollection) {

      String requestBody = gson.toJson(itemNbrEOSBatch);

      LOGGER.info(
          "Calling Rep Item master for  items: {} with url: {}, jsonRequest: {}, httpHeaders: {}",
          itemNbrEOSBatch,
          url,
          requestBody,
          requestHeaders);

      ResponseEntity<FixturesItemAttribute[]> responseEntity =
          retryableRestConnector.exchange(
              itemRepSummaryUri.toString(),
              HttpMethod.POST,
              new HttpEntity<>(requestBody, requestHeaders),
              FixturesItemAttribute[].class);

      if (!responseEntity.getStatusCode().is2xxSuccessful()) {
        LOGGER.error(
            "Error in calling Rep Service for url={}  header={}, body={}",
            url,
            requestHeaders,
            requestBody);
        throw new ReceivingBadDataException(
            ExceptionCodes.ITEM_CONFIG_SEARCH_BAD_REQUEST,
            String.format(ReceivingConstants.BAD_RESPONSE_ERROR_MSG, ReceivingConstants.ITEM_REP));
      }

      LOGGER.info("REP Response : {}", responseEntity.getBody());
      if (Objects.nonNull(responseEntity)) {
        fixturesItemAttributeMap.putAll(
            Arrays.asList(responseEntity.getBody())
                .stream()
                .collect(
                    Collectors.toMap(FixturesItemAttribute::getArticleId, Function.identity())));
      }
    }
    return fixturesItemAttributeMap;
  }

  private HttpHeaders buildHttpHeaders() {
    HttpHeaders requestHeaders = ReceivingUtils.getHeaders();
    try {
      requestHeaders.add(HttpHeaders.AUTHORIZATION, getAuthzHeaderValue());
    } catch (Exception e) {
      LOGGER.error("Error while  Building REP Header exception", e);
    }
    return requestHeaders;
  }

  private String getAuthzHeaderValue()
      throws NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException,
          BadPaddingException, InvalidKeyException {
    String clientIdAndSecret =
        String.format(
            "%s:%s",
            SecurityUtil.decryptValue(secretKey, fixtureManagedConfig.getItemRepUsername()),
            SecurityUtil.decryptValue(secretKey, fixtureManagedConfig.getItemRepPassword()));
    return String.format(
        "Basic %s", new String(Base64.getEncoder().encode(clientIdAndSecret.getBytes())));
  }
}
