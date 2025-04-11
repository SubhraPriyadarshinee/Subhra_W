package com.walmart.move.nim.receiving.core.client.itemconfig;

import static com.walmart.move.nim.receiving.core.advice.Type.REST;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ATLAS_ADD_ITEM;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENTIRE_DC_ATLAS_CONVERTED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ONE_ATLAS_CONVERTED_ITEM;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ONE_ATLAS_NOT_CONVERTED_ITEM;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.util.CollectionUtils.isEmpty;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigRequest;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigResponse;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ItemConfigApiClient {
  public static final Logger LOGGER = LoggerFactory.getLogger(ItemConfigApiClient.class);

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private RetryableRestConnector retryableRestConnector;
  @Autowired private SimpleRestConnector simpleRestConnector;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private Gson gson;

  @TimeTracing(
      component = AppComponent.CORE,
      flow = "searchAtlasConvertedItems",
      externalCall = true,
      type = REST)
  @Counted(
      name = "searchAtlasConvertedItemsHitCount",
      level1 = "uwms-receiving",
      level2 = "itemConfigApiClient",
      level3 = "searchAtlasConvertedItems")
  @Timed(
      name = "searchAtlasConvertedItemsAPITimed",
      level1 = "uwms-receiving",
      level2 = "itemConfigApiClient",
      level3 = "searchAtlasConvertedItems")
  @ExceptionCounted(
      name = "searchAtlasConvertedItemsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "itemConfigApiClient",
      level3 = "searchAtlasConvertedItems")
  public List<ItemConfigDetails> searchAtlasConvertedItems(
      Set<Long> requestItemNumbers, HttpHeaders httpHeaders)
      throws ItemConfigRestApiClientException {
    // ENTIRE_DC_ATLAS_CONVERTED so no need to check item config
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ENTIRE_DC_ATLAS_CONVERTED, false)) {
      return requestItemNumbers
          .stream()
          .map(
              itemNum -> new ItemConfigDetails(itemNum.toString(), ENTIRE_DC_ATLAS_CONVERTED, null))
          .collect(Collectors.toList());
    }
    // NOT ENTIRE DC is one ATLAS CONVERTED so check against item config service
    ResponseEntity<String> responseEntity = null;
    ItemConfigRequest itemConfigRequest =
        ItemConfigRequest.builder().data(requestItemNumbers).build();
    List<ItemConfigDetails> itemConfigDetails = new ArrayList<>();
    ItemConfigResponse itemConfigResponse = null;
    String uri = appConfig.getItemConfigBaseUrl() + ReceivingConstants.ATLAS_CONVERTED_ITEM_SEARCH;
    String requestBody = gson.toJson(itemConfigRequest);
    final HttpEntity<String> request = new HttpEntity<>(requestBody, httpHeaders);
    LOGGER.info("ItemConfig post url={}, request={}", uri, request);
    try {
      if (configUtils.getConfiguredFeatureFlag(
          getFacilityNum().toString(), ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED, false)) {
        responseEntity = simpleRestConnector.exchange(uri, POST, request, String.class);
      } else {
        responseEntity = retryableRestConnector.exchange(uri, POST, request, String.class);
      }
      LOGGER.info("ItemConfig response={}", responseEntity);
    } catch (RestClientResponseException e) {
      handleItemConfigException(uri, request, e);
    }

    if (Objects.nonNull(responseEntity)) {
      itemConfigResponse = gson.fromJson(responseEntity.getBody(), ItemConfigResponse.class);
      if (CollectionUtils.isNotEmpty(itemConfigResponse.getItems())) {
        LOGGER.info(
            "ItemConfig api responseCode = {} , atlas converted item count = {} ",
            responseEntity.getStatusCodeValue(),
            itemConfigResponse.getTotalRecords());
        itemConfigDetails = itemConfigResponse.getItems();
      }
    }
    return itemConfigDetails;
  }

  /**
   * returns true if isAtlasConvertedItem else false
   *
   * @param itemNumber
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public boolean isAtlasConvertedItem(Long itemNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ENTIRE_DC_ATLAS_CONVERTED, false)) {
      return true;
    }
    Set<Long> itemConfigRequest = new HashSet<>();
    itemConfigRequest.add(itemNumber);
    try {
      Set<String> convertedItemsList =
          searchAtlasConvertedItems(itemConfigRequest, httpHeaders)
              .stream()
              .map(ItemConfigDetails::getItem)
              .collect(Collectors.toSet());

      final boolean isOneAtlasConverted =
          !isEmpty(convertedItemsList) && convertedItemsList.contains(itemNumber.toString());
      LOGGER.info("item={} isAtlasConvertedItem={}", itemNumber, isOneAtlasConverted);
      return isOneAtlasConverted;
    } catch (ItemConfigRestApiClientException e) {
      LOGGER.error(
          "Error when searching atlas converted items errorCode = {} and error message = {} ",
          e.getHttpStatus(),
          ExceptionUtils.getMessage(e));
      throw new ReceivingException(
          e.getErrorResponse().getErrorMessage(),
          e.getHttpStatus(),
          e.getErrorResponse().getErrorCode());
    }
  }

  /**
   * takes input as StringBuffer (mutable) as itemState representing current state. if itemState is
   * null or empty then calls Item Service end point else return
   *
   * @param isOneAtlas
   * @param itemState
   * @param itemNumber
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public String getOneAtlasState(
      boolean isOneAtlas, StringBuilder itemState, final Long itemNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.info("isOneAtlas={} item={} entry status={}", isOneAtlas, itemNumber, itemState);

    if (itemState == null) {
      itemState = new StringBuilder("");
    }
    if (!isOneAtlas) {
      LOGGER.info("return NOT-OneAtlas item={} as-is={}", itemNumber, itemState);
      return itemState.toString();
    }

    // if already have item status just return instead of making call to ItemConfig
    if (ONE_ATLAS_CONVERTED_ITEM.equals(itemState.toString())
        || ONE_ATLAS_NOT_CONVERTED_ITEM.equals(itemState.toString())) {
      LOGGER.info("returning already fetched item={} as itemState={}", itemNumber, itemState);
      return itemState.toString();
    }

    // Call ItemConfigService for 1 item
    if (isAtlasConvertedItem(
        itemNumber, getForwardableHttpHeadersWithRequestOriginator(httpHeaders))) {
      itemState.append(ONE_ATLAS_CONVERTED_ITEM);
    } else {
      itemState.append(ONE_ATLAS_NOT_CONVERTED_ITEM);
    }

    LOGGER.info("returning newly fetched item={} as {}", itemNumber, itemState);
    return itemState.toString();
  }

  private void handleItemConfigException(
      String uri, HttpEntity<String> request, RestClientResponseException e)
      throws ItemConfigRestApiClientException {
    if (HttpStatus.NOT_FOUND.value() != e.getRawStatusCode()) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          request,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      String errorCode = ExceptionCodes.ITEM_CONFIG_SEARCH_INTERNAL_SERVER_ERROR;
      if (HttpStatus.BAD_REQUEST.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.ITEM_CONFIG_SEARCH_BAD_REQUEST;
      } else if (HttpStatus.SERVICE_UNAVAILABLE.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.ITEM_CONFIG_SERVICE_UNAVAILABLE;
      }
      throw new ItemConfigRestApiClientException(
          String.format(ReceivingException.ITEM_CONFIG_ITEM_SEARCH_ERROR, e.getMessage()),
          HttpStatus.valueOf(e.getRawStatusCode()),
          errorCode);
    } else {
      // not error but biz case: 404 means not converted
      LOGGER.warn(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          request,
          e.getResponseBodyAsString(),
          e.getRawStatusCode());
    }
  }

  public boolean isOneAtlasConvertedItem(
      boolean isOneAtlas, StringBuilder itemState, Long itemNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    return ONE_ATLAS_CONVERTED_ITEM.equals(
        getOneAtlasState(isOneAtlas, itemState, itemNumber, httpHeaders));
  }

  public boolean isOneAtlasNotConvertedItem(
      boolean isOneAtlas, StringBuilder itemState, Long itemNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    return ONE_ATLAS_NOT_CONVERTED_ITEM.equals(
        getOneAtlasState(isOneAtlas, itemState, itemNumber, httpHeaders));
  }

  /**
   * This is to add Item into itemConfig
   *
   * @param requestItemNumbers
   * @param httpHeaders
   * @throws ItemConfigRestApiClientException
   */
  public void addAsAtlasItems(Set<Long> requestItemNumbers, HttpHeaders httpHeaders)
      throws ItemConfigRestApiClientException {
    ItemConfigRequest itemConfigRequest =
        ItemConfigRequest.builder().data(requestItemNumbers).build();
    String uri = appConfig.getItemConfigBaseUrl() + ATLAS_ADD_ITEM;
    String requestBody = gson.toJson(itemConfigRequest);
    final HttpEntity<String> request = new HttpEntity<>(requestBody, httpHeaders);
    LOGGER.info(
        "ItemConfig add url={}, request={}, itemNumbers={}", uri, request, requestItemNumbers);
    try {
      retryableRestConnector.exchange(uri, POST, request, String.class);
    } catch (RestClientResponseException e) {
      handleItemConfigException(uri, request, e);
    }
  }

  @Async
  public void checkAndAddAsAtlasItems(Set<Long> requestItemNumbers, HttpHeaders httpHeaders)
      throws ItemConfigRestApiClientException {

    ResponseEntity<String> responseEntity = null;
    Set<String> atlasConvertedItems = new HashSet<>();
    ItemConfigRequest atlasItemConfigRequest =
        ItemConfigRequest.builder().data(requestItemNumbers).build();
    String searchItemUrl =
        appConfig.getItemConfigBaseUrl() + ReceivingConstants.ATLAS_CONVERTED_ITEM_SEARCH;
    String atlasItemSearchRequest = gson.toJson(atlasItemConfigRequest);
    final HttpEntity<String> itemRequest = new HttpEntity<>(atlasItemSearchRequest, httpHeaders);
    ItemConfigResponse itemConfigResponse = null;

    try {
      responseEntity =
          retryableRestConnector.exchange(searchItemUrl, POST, itemRequest, String.class);

      if (Objects.nonNull(responseEntity)) {
        itemConfigResponse = gson.fromJson(responseEntity.getBody(), ItemConfigResponse.class);
        if (CollectionUtils.isNotEmpty(itemConfigResponse.getItems())) {
          Set<String> convertedItems =
              itemConfigResponse
                  .getItems()
                  .stream()
                  .map(ItemConfigDetails::getItem)
                  .collect(Collectors.toSet());
          atlasConvertedItems.addAll(convertedItems);
        }
      }
      Set<Long> nonConvertedItems =
          requestItemNumbers
              .stream()
              .filter(itemNumber -> !atlasConvertedItems.contains(String.valueOf(itemNumber)))
              .collect(Collectors.toSet());
      if (!CollectionUtils.isEmpty(nonConvertedItems)) {
        addAsAtlasItems(nonConvertedItems, httpHeaders);
      }
      LOGGER.info("ItemConfig response={}", responseEntity);
    } catch (RestClientResponseException e) {
      LOGGER.info("response {}", e.getResponseBodyAsString());
      if (e.getResponseBodyAsString().equals("NO_ITEMS")) {
        addAsAtlasItems(requestItemNumbers, httpHeaders);
      } else {
        handleItemConfigException(searchItemUrl, itemRequest, e);
      }
    }
  }
}
