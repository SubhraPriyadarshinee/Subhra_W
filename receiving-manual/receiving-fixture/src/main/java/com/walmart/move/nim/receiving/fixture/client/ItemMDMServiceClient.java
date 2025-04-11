package com.walmart.move.nim.receiving.fixture.client;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.utils.ItemMDMUtils;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ItemMDMServiceClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(ItemMDMServiceClient.class);

  private @ManagedConfiguration FixtureManagedConfig fixtureManagedConfig;

  private @ManagedConfiguration AppConfig appConfig;

  @Resource(name = ReceivingConstants.BEAN_REST_CONNECTOR)
  private RestConnector simpleRestConnector;

  @Resource(name = ReceivingConstants.BEAN_RETRYABLE_CONNECTOR)
  private RestConnector retryableRestConnector;

  @Autowired private Gson gson;

  @Timed(name = "MDM-Get-Items", level1 = "uwms-receiving", level2 = "MDM-Get-Items")
  @ExceptionCounted(
      name = "MDM-Get-Items-Exception",
      level1 = "uwms-receiving",
      level2 = "MDM-Get-Items-Exception")
  @TimeTracing(
      component = AppComponent.FIXTURE,
      executionFlow = "MDM-Get-Items",
      type = Type.REST,
      externalCall = true)
  public Map<String, Object> retrieveItemDetails(
      Set<Long> itemNumbers, HttpHeaders httpHeaders, boolean isRetryable) {

    httpHeaders.add(ReceivingConstants.HEADER_AUTH_KEY, fixtureManagedConfig.getRfcMdmAuthKey());
    String mdmUrl = appConfig.getItemMDMBaseUrl() + ReceivingConstants.MDM_ITEM_SEARCH_PATH;
    LOGGER.info("Going to call item mdm [url={}] [headers={}]", mdmUrl, httpHeaders);

    Collection<List<Long>> partitionedItemNumbers =
        ReceivingUtils.batchifyCollection(itemNumbers, appConfig.getItemBatchSize());

    final List<Map<String, Object>> foundItems = new ArrayList<>();
    final List<Map<String, Object>> notFoundItems = new ArrayList<>();

    Map<String, Object> responseItemMap = new HashMap<>();

    partitionedItemNumbers
        .parallelStream()
        .forEach(
            items -> {
              try {

                Map<String, Object> newItemDetails =
                    makeMDMCall(items, httpHeaders, isRetryable, mdmUrl);

                populateItems(foundItems, notFoundItems, newItemDetails);

                responseItemMap.put(ReceivingConstants.ITEM_FOUND_SUPPLY_ITEM, foundItems);

                responseItemMap.put(ReceivingConstants.ITEM_NOT_FOUND_SUPPLY_ITEM, notFoundItems);
                LOGGER.info(
                    "Resultant ItemMap [size={}] foundItems [size={}] notFoundItem [size={}] requested itemResponse [size={}]",
                    responseItemMap.size(),
                    foundItems.size(),
                    notFoundItems.size(),
                    newItemDetails.size());
              } catch (Exception e) {
                LOGGER.error(
                    "Exception occur while retrieving the mdm chunk data [itemNumbers={}] [error={}]",
                    items,
                    e);
              }
            });

    if (responseItemMap.size() == 0) {
      LOGGER.error(
          "Seems MDM is down. Hence none of the item retrieved [mdmUrl={}] [isRetryable={}] [headers={}] [itemNumbers={}]",
          mdmUrl,
          isRetryable,
          httpHeaders,
          itemNumbers);
      throw new ReceivingBadDataException(
          ExceptionCodes.ITEM_NOT_FOUND,
          String.format(
              ExceptionDescriptionConstants.ITEM_MDM_SERVICE_DOWN_ERROR_MSG, "Unable to retrieve"));
    }

    if (!CollectionUtils.isEmpty(notFoundItems)) {
      LOGGER.info("Not Found Item Lists [notFoundItem={}]", notFoundItems);
    }

    return responseItemMap;
  }

  private Map<String, Object> makeMDMCall(
      List<Long> itemNumbers, HttpHeaders httpHeaders, boolean isRetryable, String mdmUrl) {
    HttpEntity httpEntity = new HttpEntity(gson.toJson(itemNumbers), httpHeaders);

    ResponseEntity<Map> responseEntity = null;
    try {
      if (isRetryable) {
        responseEntity =
            retryableRestConnector.exchange(mdmUrl, HttpMethod.POST, httpEntity, Map.class);
      } else {
        responseEntity =
            simpleRestConnector.exchange(mdmUrl, HttpMethod.POST, httpEntity, Map.class);
      }
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          mdmUrl,
          itemNumbers,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.ITEM_NOT_FOUND,
          String.format(
              ExceptionDescriptionConstants.ITEM_MDM_ERROR_MSG,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          mdmUrl,
          itemNumbers,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.ITEM_NOT_FOUND,
          String.format(
              ExceptionDescriptionConstants.ITEM_MDM_SERVICE_DOWN_ERROR_MSG, e.getMessage()));
    }

    if (Objects.isNull(responseEntity) || !responseEntity.hasBody()) {
      LOGGER.error(ReceivingConstants.RESTUTILS_INFO_MESSAGE, mdmUrl, itemNumbers, responseEntity);
      throw new ReceivingBadDataException(
          ExceptionCodes.ITEM_NOT_FOUND,
          String.format(ExceptionDescriptionConstants.ITEM_MDM_BAD_DATA_ERROR_MSG, itemNumbers));
    }
    LOGGER.info(
        ReceivingConstants.RESTUTILS_INFO_MESSAGE, mdmUrl, itemNumbers, responseEntity.getBody());
    return responseEntity.getBody();
  }

  /**
   * * Populate items into a list of found and nonFound item
   *
   * @param foundItems
   * @param notFoundItems
   * @param responseItemMap
   */
  private void populateItems(
      List<Map<String, Object>> foundItems,
      List<Map<String, Object>> notFoundItems,
      Map<String, Object> responseItemMap) {
    foundItems.addAll(
        (List<Map<String, Object>>) responseItemMap.get(ReceivingConstants.ITEM_FOUND_SUPPLY_ITEM));

    notFoundItems.addAll(
        (List<Map<String, Object>>)
            responseItemMap.get(ReceivingConstants.ITEM_NOT_FOUND_SUPPLY_ITEM));
  }

  public String getItemUpc(Set<Long> itemNumber) {
    try {
      Map<String, Object> itemMDMDetailsList =
          retrieveItemDetails(itemNumber, ReceivingUtils.getHeaders(), false);

      List<Map<String, Object>> foundItems =
          (List<Map<String, Object>>)
              itemMDMDetailsList.get(ReceivingConstants.ITEM_FOUND_SUPPLY_ITEM);

      if (Objects.nonNull(foundItems)) {
        Map<String, Object> itemMDMDCPropertiesSupplyItemDetails =
            ItemMDMUtils.getItemMDMDCPropertiesSupplyItemDetails(foundItems);
        return ItemMDMUtils.getUpc(itemMDMDCPropertiesSupplyItemDetails);
      }
    } catch (Exception e) {
      LOGGER.error("Item mdm call failed...... reason ::", e);
    }
    return null;
  }
}
