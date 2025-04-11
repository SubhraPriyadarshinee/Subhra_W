package com.walmart.move.nim.receiving.sib.service;

import static com.walmart.move.nim.receiving.sib.utils.Constants.AVAILABLE_TO_SELL;
import static com.walmart.move.nim.receiving.sib.utils.Constants.CONTAINER_CREATE_DATE;
import static com.walmart.move.nim.receiving.sib.utils.Constants.STORE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_SEARCH_URI;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.sib.model.inventory.ContainerInventoriesItem;
import com.walmart.move.nim.receiving.sib.model.inventory.InventorySearchRequest;
import com.walmart.move.nim.receiving.sib.model.inventory.InventorySearchResponse;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

public class StoreInventoryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoreInventoryService.class);

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  @Autowired private Gson gson;

  @ManagedConfiguration private AppConfig appConfig;

  public List<ContainerInventoriesItem> getUnstockedInventoryForDelivery(Long deliveryNumber) {
    InventorySearchRequest inventorySearchRequest = new InventorySearchRequest();
    inventorySearchRequest.setPopulateItemMetadata(true);
    inventorySearchRequest.setChildContainersRequired(true);
    inventorySearchRequest.setDeliveryNum(deliveryNumber.toString());
    inventorySearchRequest.setInventoryStatusList(Collections.singletonList(AVAILABLE_TO_SELL));
    inventorySearchRequest.setSortByOldestToLatest(true);
    inventorySearchRequest.setSortCriteria(CONTAINER_CREATE_DATE);
    inventorySearchRequest.setContainerWarehouseArea(STORE);

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put("pageNumber", "1");
    pathParams.put("pageSize", "100");
    String inventorySearchURL =
        UriComponentsBuilder.fromUriString(appConfig.getInventoryBaseUrl() + INVENTORY_SEARCH_URI)
            .buildAndExpand(pathParams)
            .toUri()
            .toString();

    HttpHeaders headers =
        ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator(ReceivingUtils.getHeaders());

    LOGGER.info("Going to find unstocked inventory from CSM for delivery: {}", deliveryNumber);
    List<ContainerInventoriesItem> unstockedInventoryList = new ArrayList<>();
    ResponseEntity<InventorySearchResponse> inventorySearchResponse;
    do {
      try {
        inventorySearchResponse =
            restConnector.exchange(
                inventorySearchURL,
                HttpMethod.POST,
                new HttpEntity<>(gson.toJson(inventorySearchRequest), headers),
                InventorySearchResponse.class);

        if (HttpStatus.NO_CONTENT.equals(inventorySearchResponse.getStatusCode())) {
          LOGGER.info("No unstocked inventory found for delivery: {}", deliveryNumber);
          return Collections.emptyList();
        }
        if (Objects.isNull(inventorySearchResponse.getBody())) {
          throw new ReceivingDataNotFoundException(
              ExceptionCodes.INVENTORY_NOT_FOUND,
              String.format("Inventory for delivery = %s is not available", deliveryNumber));
        }
        LOGGER.info("Response from CSM: {}", inventorySearchResponse.getBody());
      } catch (RestClientResponseException e) {
        if (e.getRawStatusCode() == HttpStatus.BAD_REQUEST.value()) {
          LOGGER.error(
              ReceivingConstants.RESTUTILS_ERROR_MESSAGE_WITH_ERROR_DETAILS,
              inventorySearchURL,
              inventorySearchRequest,
              e.getResponseBodyAsString(),
              e.getRawStatusCode(),
              ExceptionUtils.getStackTrace(e));
          throw new ReceivingBadDataException(
              ExceptionCodes.INVENTORY_NOT_FOUND,
              String.format(
                  "Inventory not found, status: %s, response: %s",
                  e.getRawStatusCode(), e.getResponseBodyAsString()));
        } else {
          throw e;
        }
      } catch (ResourceAccessException e) {
        LOGGER.error("Error accessing CSM", e);
        throw new ReceivingInternalException(
            ExceptionCodes.INVENTORY_SERVICE_UNAVAILABLE, "Error accessing CSM");
      }
      if (Objects.nonNull(inventorySearchResponse.getBody().getNextPage())) {
        inventorySearchURL = inventorySearchResponse.getBody().getNextPage().getHref();
      }
      unstockedInventoryList.addAll(
          inventorySearchResponse
              .getBody()
              .getAggrInvList()
              .stream()
              .flatMap(aggrInvListItem -> aggrInvListItem.getContainerInventories().stream())
              .collect(Collectors.toList()));
    } while (Objects.nonNull(inventorySearchResponse.getBody().getNextPage()));
    LOGGER.info(
        "Fetched containers from CSM: {}",
        unstockedInventoryList
            .stream()
            .map(ContainerInventoriesItem::getTrackingId)
            .collect(Collectors.toList()));
    return unstockedInventoryList;
  }
}
