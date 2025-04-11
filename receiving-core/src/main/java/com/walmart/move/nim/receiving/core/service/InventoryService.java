package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeadersV2;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.replacePathParams;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.stringfyJson;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVENTORY_NOT_FOUND;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Arrays.asList;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.inventory.model.ContainerInventory;
import com.walmart.move.nim.receiving.core.client.inventory.model.InventoryContainerResponse;
import com.walmart.move.nim.receiving.core.client.inventory.model.InventoryOnHoldRequestV1;
import com.walmart.move.nim.receiving.core.common.ContainerUtils;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentList;
import com.walmart.move.nim.receiving.core.model.InventoryItemExceptionPayload;
import com.walmart.move.nim.receiving.core.model.InventoryItemPODetailUpdateRequest;
import com.walmart.move.nim.receiving.core.model.InventoryLocationUpdateRequest;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.utils.constants.EndgameOutboxServiceName;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Service to interact with Inventory product
 *
 * @author lkotthi
 */
@Service
public class InventoryService {
  private static final Logger LOG = LoggerFactory.getLogger(InventoryService.class);

  public static final String INVENTORY_CONTAINER_LOCATION_NAME = "locationName";
  public static final String FAILED_INVENTORY_BULK_ADJUSTMENT_ALERT_METRIC_NAME =
      "failedInventoryBulkAdjustment";
  public static final String APP_NAME = "uwms-receiving";
  public static final String INVENTORY_ADJUSTMENT = "inventory-adjustment";
  public static final String RECEIVING_INV_BULK_ADJUSTMENT = "receiving_InventoryBulkAdjustment";

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired
  @Qualifier(GSON_UTC_ADAPTER)
  private Gson gsonUTCDateAdapter;

  @Autowired private RestUtils restUtils;

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private AsyncPersister asyncPersister;

  @Autowired private InventoryRestApiClient inventoryRestApiClient;

  @Autowired private ContainerItemRepository containerItemRepository;
  @Autowired private RetryTemplate retryTemplate;
  @Autowired private Gson gson;

  @Resource(name = ReceivingConstants.BEAN_REST_CONNECTOR)
  private RestConnector simpleRestConnector;

  @Resource(name = BEAN_RETRYABLE_CONNECTOR)
  private RestConnector retryableRestConnector;

  @Autowired private EndgameOutboxHandler endgameOutboxHandler;

  public InventoryService() {}

  @Timed(name = "OnHoldTimed", level1 = APP_NAME, level2 = "InventoryService", level3 = "OnHold")
  @ExceptionCounted(
      name = "OnHoldExceptionCount",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "OnHold")
  public void onHold(Container container, HttpHeaders httpHeaders) throws ReceivingException {
    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    final String trackingId = container.getTrackingId();
    String url;
    String body;
    ResponseEntity<String> response;
    httpHeaders.add(
        IDEM_POTENCY_KEY, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY) + "-" + trackingId);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), INV_V2_ENABLED, false)) {
      url = appConfig.getInventoryCoreBaseUrl() + INVENTORY_PALLET_ON_HOLD_V2;
      body = inventoryRestApiClient.createInventoryOnHoldRequest(trackingId);
    } else {
      url = appConfig.getInventoryBaseUrl() + INVENTORY_PALLET_ON_HOLD;
      body = getPalletOnHoldRequestV1(container);
    }
    response = restUtils.post(url, httpHeaders, new HashMap<>(), body);
    final HttpStatus statusCode = response.getStatusCode();
    if (!statusCode.is2xxSuccessful()) {
      LOG.error(
          "Unable to update Inventory - trackingId={}, requestHeaders={}, statusCode={} response={}",
          trackingId,
          httpHeaders,
          statusCode,
          response.getBody());

      if (statusCode == HttpStatus.SERVICE_UNAVAILABLE) {
        throw new ReceivingException(
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG,
            HttpStatus.BAD_REQUEST,
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE);
      } else {
        throw new ReceivingException(
            ReceivingException.INVENTORY_ERROR_MSG,
            HttpStatus.BAD_REQUEST,
            ReceivingException.INVENTORY_ERROR_CODE);
      }
    }
  }

  private static String getPalletOnHoldRequestV1(Container container) {

    final ContainerItem ci = container.getContainerItems().get(0);
    InventoryOnHoldRequestV1 onHoldReqV1 =
        InventoryOnHoldRequestV1.builder()
            .containerId(container.getTrackingId())
            .financialReportingGroup(ci.getFinancialReportingGroupCode())
            .baseDivisionCode(ci.getBaseDivisionCode())
            .holdReasons(asList(71))
            .holdDirectedBy("HO")
            .holdInitiatedTime(new Date())
            .build();
    final Gson gsonInvFmt =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            .create();
    return gsonInvFmt.toJson(asList(onHoldReqV1));
  }

  public void deleteContainer(String trackingId, HttpHeaders httpHeaders) {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(INVENTORY_ADJUSTMENT_TRACKING_ID, trackingId);
    String url =
        tenantSpecificConfigReader.getInventoryBaseUrlByFacility()
            + replacePathParams(appConfig.getInventoryContainerDeletePath(), pathParams);

    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);

    // Removing host header to properly route the request to WCNP
    httpHeaders.remove(ReceivingConstants.HOST);
    httpHeaders.add(ReceivingConstants.IDEM_POTENCY_KEY, trackingId);
    LOG.info(
        "Calling inventory url: {} to delete docktag container for lpn: {} and headers: {}",
        url,
        trackingId,
        httpHeaders);
    try {
      simpleRestConnector.exchange(
          url, HttpMethod.DELETE, new HttpEntity<>(httpHeaders), String.class);
    } catch (RestClientResponseException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      if (e.getRawStatusCode() == HttpStatus.NOT_FOUND.value()) {
        LOG.error(
            "deleteContainer : Container {} not found in inventory. Not throwing exception. returning",
            trackingId);
        return;
      }
      throw new ReceivingDataNotFoundException(
          INVENTORY_NOT_FOUND, String.format(INVENTORY_NOT_FOUND_MESSAGE, trackingId));
    } catch (ResourceAccessException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(UNABLE_TO_PROCESS_INVENTORY, INVENTORY_SERVICE_DOWN);
    }
  }

  @Timed(
      name = "PalletOffHoldTimed",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "PalletOffHoldDetails")
  @ExceptionCounted(
      name = "PalletOffHoldExceptionCount",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "PalletOffHoldDetails")
  public void palletOffHold(String trackingId, HttpHeaders headers) throws ReceivingException {
    LinkedTreeMap<String, Object> request =
        createPalletOffHoldInventoryRequest(trackingId, headers);
    headers = getForwardableHttpHeadersWithRequestOriginator(headers);
    final String cId = headers.getFirst(CORRELATION_ID_HEADER_KEY);
    String url;
    String body;
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), INV_V2_ENABLED, false)) {
      url = appConfig.getInventoryCoreBaseUrl() + INVENTORY_PALLET_OFF_HOLD_V2;
      headers.add(IDEM_POTENCY_KEY, cId + "-" + trackingId);
      body = inventoryRestApiClient.createInventoryOffHoldRequest(trackingId);
    } else {
      url = appConfig.getInventoryBaseUrl() + INVENTORY_PALLET_OFF_HOLD;
      body = gsonUTCDateAdapter.toJson(request);
    }
    LOG.info("Inventory offHold for cId={}, url={}, headers={}, body={}", cId, url, headers, body);
    ResponseEntity<String> response = restUtils.post(url, headers, new HashMap<>(), body);
    LOG.info("Inventory offHold for cId={}, response={}", cId, response.getBody());
    throwExceptionIfFail(trackingId, response);
  }

  public LinkedTreeMap<String, Object> createPalletOffHoldInventoryRequest(
      String trackingId, HttpHeaders httpHeaders) {
    LinkedTreeMap<String, Object> palletOffHoldInventoryRequest = new LinkedTreeMap<>();
    palletOffHoldInventoryRequest.put(INVENTORY_OFF_HOLD_CONTAINER_ID, trackingId);
    palletOffHoldInventoryRequest.put(INVENTORY_OFF_HOLD_QTY, true);
    palletOffHoldInventoryRequest.put(
        INVENTORY_OFF_HOLD_TARGET_QTY, INVENTORY_OFF_HOLD_TARGET_QTY_VALUE);
    httpHeaders.add(IDEM_POTENCY_KEY, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    return palletOffHoldInventoryRequest;
  }

  public String getContainerLocation(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException {
    final String containerDetailsJson = getContainerDetails(trackingId, httpHeaders);
    final JsonObject inventoryContainerDetailsJsonObject =
        gsonUTCDateAdapter.fromJson(containerDetailsJson, JsonObject.class);
    if (nonNull(inventoryContainerDetailsJsonObject)
        && nonNull(inventoryContainerDetailsJsonObject.get(INVENTORY_CONTAINER_LOCATION_NAME))) {
      final String inventoryLocation =
          inventoryContainerDetailsJsonObject.get(INVENTORY_CONTAINER_LOCATION_NAME).getAsString();
      if (isNotBlank(inventoryLocation)) {
        LOG.info("trackingId={}, inventory location={}", trackingId, inventoryLocation);
        return inventoryLocation;
      }
    }
    LOG.error(
        "inventoryLocation not found for trackingId={} in response={}",
        trackingId,
        containerDetailsJson);
    throw new ReceivingDataNotFoundException(
        INVENTORY_NOT_FOUND, String.format(INVENTORY_NOT_FOUND_MESSAGE, trackingId));
  }

  public String getContainerDetails(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException {
    httpHeaders = getForwardableHttpHeadersV2(httpHeaders);
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    // set WMT_UserId
    final String userId_ = httpHeaders.getFirst(PRINT_LABEL_USER_ID);
    if (isBlank(userId_)) {
      final String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);
      if (isBlank(userId)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_DATA,
            String.format(INVALID_HEADER_ERROR_MSG, PRINT_LABEL_USER_ID, userId_));
      }
      httpHeaders.add(PRINT_LABEL_USER_ID, userId);
    }

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(INVENTORY_ADJUSTMENT_TRACKING_ID, trackingId);

    String baseUrl;
    String facilityNum = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(facilityNum, INV_V2_ENABLED, false)) {
      baseUrl = appConfig.getInventoryQueryBaseUrl();
      httpHeaders.add(
          IDEM_POTENCY_KEY, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY) + "-" + trackingId);
    } else {
      baseUrl = appConfig.getInventoryBaseUrl();
    }

    // Get Inventory Container details from GLS for non-converted item - Manual GDC
    baseUrl = getInventoryContainerFromGls(trackingId, baseUrl, facilityNum);

    String url = baseUrl + replacePathParams(INVENTORY_CONTAINERS_DETAILS_URI, pathParams);

    try {
      // cId useful in async processing
      LOG.info("cId={}, request for url={}, headers={}", cId, url, httpHeaders);
      ResponseEntity<String> response =
          simpleRestConnector.exchange(url, GET, new HttpEntity<>(httpHeaders), String.class);
      LOG.info("cId={} response={}", cId, response);
      throwExceptionIfFail(trackingId, response);
      return response.getBody();

    } catch (RestClientResponseException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          cId,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingDataNotFoundException(
          INVENTORY_NOT_FOUND, String.format(INVENTORY_NOT_FOUND_MESSAGE, trackingId));
    } catch (ResourceAccessException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          cId,
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(UNABLE_TO_PROCESS_INVENTORY, INVENTORY_SERVICE_DOWN);
    }
  }

  private void throwExceptionIfFail(String trackingId, ResponseEntity<String> response)
      throws ReceivingException {
    if (!response.getStatusCode().is2xxSuccessful()) {
      LOG.error(
          "unsuccessful Inventory call for trackingId :{} errorCode :{} errorMessage :{}",
          trackingId,
          response.getStatusCode(),
          response.getBody());
      if (response.getStatusCode().is5xxServerError()) {
        throw new ReceivingException(
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG,
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE);
      } else {
        throw new ReceivingException(
            ReceivingException.INVENTORY_ERROR_MSG,
            HttpStatus.BAD_REQUEST,
            ReceivingException.INVENTORY_ERROR_CODE);
      }
    }
  }

  /**
   * Queries the inventory API for container details by LPN and returns necessary fields from it.
   * <code>inventoryQty</code> field has quantity in VNPK.
   *
   * @param trackingId
   * @param httpHeaders
   * @return {@link InventoryContainerDetails}
   * @throws ReceivingException
   */
  public InventoryContainerDetails getInventoryContainerDetails(
      String trackingId, HttpHeaders httpHeaders) throws ReceivingException {
    Integer inventoryQty = null;
    String containerStatus = null;
    Integer destinationLocationId = null;
    Integer allocatedQty = null;
    String locationName = null;

    final String response = getContainerDetails(trackingId, httpHeaders);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        IS_INVENTORY_VALID_ITEM_CHECK_ENABLED,
        false)) {
      return getInventoryContainerDetailsByValidItemQty(response);
    }
    final JsonObject responseObject = gsonUTCDateAdapter.fromJson(response, JsonObject.class);

    if (responseObject.has(ReceivingConstants.CONTAINER_STATUS)) {
      containerStatus = responseObject.get(ReceivingConstants.CONTAINER_STATUS).getAsString();
    }
    if (responseObject.has(INVENTORY_LOCATION_NAME)) {
      locationName = responseObject.get(INVENTORY_LOCATION_NAME).getAsString();
    }

    if (responseObject.has(ReceivingConstants.INVENTORY_CONTAINER_ITEM_LIST)
        && responseObject.getAsJsonArray(ReceivingConstants.INVENTORY_CONTAINER_ITEM_LIST).size()
            > 0) {
      JsonObject containerContent =
          (JsonObject)
              responseObject
                  .getAsJsonArray(ReceivingConstants.INVENTORY_CONTAINER_ITEM_LIST)
                  .get(0);

      if (containerContent.has(ReceivingConstants.INVENTORY_QTY_IN_VNPK)) {
        inventoryQty = containerContent.get(ReceivingConstants.INVENTORY_QTY_IN_VNPK).getAsInt();
      }
      if (containerContent.has(ReceivingConstants.ALLOCATED_QTY)) {
        allocatedQty = containerContent.get(ReceivingConstants.ALLOCATED_QTY).getAsInt();
      }
    }

    if (responseObject.has(ReceivingConstants.INVENTORY_DESTINATION_LOCATION_ID)) {
      destinationLocationId = responseObject.get(INVENTORY_DESTINATION_LOCATION_ID).getAsInt();
    }

    return new InventoryContainerDetails(
        inventoryQty, containerStatus, destinationLocationId, allocatedQty, locationName);
  }

  private InventoryContainerDetails getInventoryContainerDetailsByValidItemQty(String response) {
    Integer inventoryQty = null;
    Integer allocatedQty = null;
    InventoryContainerResponse inventoryContainerResponse =
        gsonUTCDateAdapter.fromJson(response, InventoryContainerResponse.class);
    if (!inventoryContainerResponse.getContainerInventoryList().isEmpty()) {
      ContainerInventory containerInventory =
          inventoryContainerResponse
              .getContainerInventoryList()
              .stream()
              .filter(container -> container.getVenPkQuantity() != ZERO_QTY)
              .findFirst()
              .get();
      inventoryQty = containerInventory.getVenPkQuantity();
      allocatedQty = containerInventory.getAllocatedQty();
    }

    return new InventoryContainerDetails(
        inventoryQty,
        inventoryContainerResponse.getContainerStatus(),
        inventoryContainerResponse.getDestinationLocationId(),
        allocatedQty,
        inventoryContainerResponse.getLocationName());
  }

  /**
   * Update inventory_traceability
   *
   * @param inventoryItemPODetailUpdateRequest input
   * @return
   */
  @Timed(
      name = "UpdateInventoryPODetailsTimed",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "UpdateInventoryPODetailsDetails")
  @ExceptionCounted(
      name = "UpdateInventoryPODetailsExceptionCount",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "UpdateInventoryPODetailsDetails")
  public void updateInventoryPoDetails(
      InventoryItemPODetailUpdateRequest inventoryItemPODetailUpdateRequest) {

    HttpHeaders requestHeaders = ReceivingUtils.getHeaders();
    requestHeaders = getForwardableHttpHeadersWithRequestOriginator(requestHeaders);

    // if INV_V2_ENABLED use appConfig.InventoryCoreBaseUrl() else InventoryBaseUrl
    String url = appConfig.getInventoryBaseUrl() + UPDATE_INVENTORY_CRITERIA_URI;
    asyncPersister.persistAsyncHttp(
        HttpMethod.PUT,
        url,
        gsonUTCDateAdapter.toJson(inventoryItemPODetailUpdateRequest),
        requestHeaders,
        RetryTargetFlow.INVENTORY_UPDATE_PO_DETAILS);
  }

  /**
   * Update location for a pallet in inventory
   *
   * @param inventoryLocationUpdateRequest
   * @param headers
   */
  @Timed(
      name = "updateLocationTimed",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "updateLocation")
  @ExceptionCounted(
      name = "updateLocationExceptionCount",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "updateLocation")
  public void updateLocation(
      InventoryLocationUpdateRequest inventoryLocationUpdateRequest, HttpHeaders headers) {

    headers = getForwardableHttpHeadersWithRequestOriginator(headers);
    // pattern after INV_V2_ENABLED for Inventory 2.0 InventoryCoreBaseUrl() +
    String url = appConfig.getInventoryBaseUrl() + UPDATE_INVENTORY_LOCATION_URI;
    asyncPersister.persistAsyncHttp(
        HttpMethod.PUT,
        url,
        JacksonParser.writeValueAsString(inventoryLocationUpdateRequest),
        headers,
        RetryTargetFlow.INVENTORY_UPDATE_LOCATION);
  }

  /**
   * * Client Endpoint to initiate bulk adjustment for Inventory Containers on Exception with retry
   * possibility
   *
   * @param adjustmentList
   * @return
   */
  public String performInventoryBulkAdjustment(InventoryAdjustmentList adjustmentList) {
    return performInventoryBulkAdjustment(adjustmentList, Boolean.FALSE);
  }

  public String performInventoryBulkAdjustment(
      InventoryAdjustmentList adjustmentList, Boolean isRetryEligible) {
    LOG.info(
        "Request : Inventory call to initiate bulk adjustment for Inventory Containers on Exception = {} ",
        adjustmentList);
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.add(REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    String url =
        appConfig.getInventoryBaseUrl()
            + ReceivingConstants.INVENTORY_EXCEPTION_ADJUSTMENT_BULK_URL;

    ResponseEntity<String> responseEntity = null;
    try {
      responseEntity =
          isRetryEligible
              ? retryableRestConnector.post(url, adjustmentList, httpHeaders, String.class)
              : simpleRestConnector.post(url, adjustmentList, httpHeaders, String.class);
      LOG.info(
          "Response : Inventory call to initiate bulk adjustment for Inventory Containers on Exception = {} , statusCode = {} ",
          adjustmentList,
          responseEntity.getStatusCode());
    } catch (RestClientResponseException e) {
      LOG.error("Unexpected error while calling Inventory", e);
      asyncPersister.publishMetric(
          FAILED_INVENTORY_BULK_ADJUSTMENT_ALERT_METRIC_NAME,
          APP_NAME,
          INVENTORY_ADJUSTMENT,
          RECEIVING_INV_BULK_ADJUSTMENT);
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_DATA, INVALID_DATA_WHILE_CALLING_INVENTORY);
    } catch (ResourceAccessException e) {
      LOG.error(ERROR_ACCESSING_INVENTORY, e);
      asyncPersister.publishMetric(
          FAILED_INVENTORY_BULK_ADJUSTMENT_ALERT_METRIC_NAME,
          APP_NAME,
          INVENTORY_ADJUSTMENT,
          RECEIVING_INV_BULK_ADJUSTMENT);
      throw new ReceivingInternalException(
          ExceptionCodes.INVENTORY_SERVICE_UNAVAILABLE, ERROR_ACCESSING_INVENTORY);
    }
    return responseEntity.getBody();
  }

  public String performInventoryBulkAdjustmentForItems(
      List<InventoryItemExceptionPayload> inventoryItemExceptionPayloads) {
    LOG.info(
        "Request : Inventory call to initiate bulk adjustment for Inventory items on containers on Exception = {} ",
        inventoryItemExceptionPayloads);
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.add(REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    String url = appConfig.getInventoryBaseUrl() + ReceivingConstants.INVENTORY_EXCEPTION_BULK_URL;
    try {
      ResponseEntity<String> response =
          retryTemplate.execute(
              context ->
                  retryPerformInventoryBulkAdjustmentForItems(
                      url, inventoryItemExceptionPayloads, httpHeaders));
      return response.getBody();
    } catch (Exception e) {
      LOG.error("Exception while audit adjustment :{}", e.getMessage());
    }
    return null;
  }

  private ResponseEntity<String> retryPerformInventoryBulkAdjustmentForItems(
      String url,
      List<InventoryItemExceptionPayload> inventoryItemExceptionPayloads,
      HttpHeaders httpHeaders) {
    ResponseEntity<String> responseEntity = null;
    try {
      responseEntity =
          simpleRestConnector.post(url, inventoryItemExceptionPayloads, httpHeaders, String.class);
      LOG.info(
          "Response : Inventory call to initiate bulk adjustment for items on Exception = {} , statusCode = {} ",
          inventoryItemExceptionPayloads,
          responseEntity.getStatusCode());
    } catch (RestClientResponseException e) {
      LOG.error("Unexpected error while calling Inventory", e);
      asyncPersister.publishMetric(
          FAILED_INVENTORY_BULK_ADJUSTMENT_ALERT_METRIC_NAME,
          APP_NAME,
          INVENTORY_ADJUSTMENT,
          RECEIVING_INV_BULK_ADJUSTMENT);
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_DATA, INVALID_DATA_WHILE_CALLING_INVENTORY);
    } catch (ResourceAccessException e) {
      LOG.error(ERROR_ACCESSING_INVENTORY, e);
      asyncPersister.publishMetric(
          FAILED_INVENTORY_BULK_ADJUSTMENT_ALERT_METRIC_NAME,
          APP_NAME,
          INVENTORY_ADJUSTMENT,
          RECEIVING_INV_BULK_ADJUSTMENT);
      throw new ReceivingInternalException(
          ExceptionCodes.INVENTORY_SERVICE_UNAVAILABLE, ERROR_ACCESSING_INVENTORY);
    }
    JsonObject inventoryExceptionResponse =
        gsonUTCDateAdapter.fromJson(responseEntity.getBody(), JsonObject.class);
    if (nonNull(inventoryExceptionResponse)
        && nonNull(inventoryExceptionResponse.get("failureContainers"))
        && !inventoryExceptionResponse.getAsJsonArray("failureContainers").isEmpty()) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.EXCEPTION_ON_PROCESSING_INV_ADJUSTMENT_MSG,
              inventoryItemExceptionPayloads.get(0).getTrackingId());
      throw new ReceivingBadDataException(
          ExceptionCodes.INVENTORY_ADJUSTMENT_MSG_PROCESSING_ERROR, errorDescription);
    }
    return responseEntity;
  }

  private String getInventoryContainerFromGls(String trackingId, String baseUrl, String facilityNum)
      throws ReceivingException {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        facilityNum, IS_INVENTORY_FROM_GLS_ENABLED, false)) {

      boolean isManualGDC =
          tenantSpecificConfigReader.getConfiguredFeatureFlag(
              facilityNum, IS_MANUAL_GDC_ENABLED, false);
      boolean isOneAtlas =
          tenantSpecificConfigReader.getConfiguredFeatureFlag(
              facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);

      if ((isManualGDC && !isOneAtlas) || (isOneAtlas && !isAtlasConvertedItem(trackingId))) {
        // Replaces base url to get inventory container details from GLS for Manual GDC
        // Non-converted Item
        baseUrl =
            tenantSpecificConfigReader.getCcmValue(
                getFacilityNum(),
                GlsRestApiClient.GLS_BASE_URL_FOR_TENANT,
                GlsRestApiClient.GLS_BASE_URL_DEFAULT);
        return baseUrl;
      }
    }
    return baseUrl;
  }

  public boolean isAtlasConvertedItem(String trackingId) throws ReceivingException {
    List<ContainerItem> containerItems = containerItemRepository.findByTrackingId(trackingId);
    return CollectionUtils.isEmpty(containerItems)
        ? false
        : ContainerUtils.isAtlasConvertedItem(containerItems.get(0));
  }

  @Timed(
      name = "createContainersTimed",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "createContainers")
  @ExceptionCounted(
      name = "createContainersExceptionCount",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "createContainers")
  public String createContainers(List<ContainerDTO> containers) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.add(REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    String url = appConfig.getInventoryBaseUrl() + INVENTORY_CONTAINER_CREATE_URI;
    List<String> trackingIdList =
        containers.stream().map(ContainerDTO::getTrackingId).collect(toList());
    ResponseEntity<String> responseEntity = null;
    try {
      LOG.info("[Request Inventory call to post containers={}]", stringfyJson(trackingIdList));
      responseEntity =
          retryableRestConnector.post(
              url, gsonUTCDateAdapter.toJson(containers), httpHeaders, String.class);
      LOG.info(
          ReceivingConstants.INVENTORY_CREATE_CONTAINERS_REQUEST_MSG,
          stringfyJson(trackingIdList),
          responseEntity.getStatusCode());
    } catch (RestClientResponseException e) {
      LOG.error(
          ReceivingConstants.INVENTORY_CREATE_CONTAINERS_ERROR_MSG,
          stringfyJson(trackingIdList),
          e);
      throw new ReceivingInternalException(
          ExceptionCodes.INTERNAL_ERROR_INV_CRT_CNTR, INVALID_DATA_WHILE_CALLING_INVENTORY);
    } catch (ResourceAccessException e) {
      LOG.error(
          ReceivingConstants.INVENTORY_CREATE_CONTAINERS_ERROR_RESP_MSG,
          stringfyJson(trackingIdList),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.INVENTORY_SERVICE_UNAVAILABLE, ERROR_ACCESSING_INVENTORY);
    }
    return responseEntity.getBody();
  }

  @Timed(
      name = "createContainersThroughOutboxTimed",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "createContainersThroughOutbox")
  @ExceptionCounted(
      name = "createContainersThroughOutboxExceptionCount",
      level1 = APP_NAME,
      level2 = "InventoryService",
      level3 = "createContainersThroughOutbox")
  public void createContainersThroughOutbox(List<ContainerDTO> containers) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.add(REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    List<String> trackingIdList =
        containers.stream().map(ContainerDTO::getTrackingId).collect(toList());
    try {
      LOG.info(
          "[Saving containers to outbox for inventory creation={}]", stringfyJson(trackingIdList));
      endgameOutboxHandler.sendToOutbox(
          gsonUTCDateAdapter.toJson(containers),
          EndgameOutboxServiceName.INVENTORY_RECEIPT_CREATION.getServiceName(),
          httpHeaders);
    } catch (Exception e) {
      LOG.error(
          ReceivingConstants.INVENTORY_CREATE_CONTAINERS_OUTBOX_ERROR_MSG,
          stringfyJson(trackingIdList),
          e);
      throw new ReceivingInternalException(
          ExceptionCodes.INTERNAL_ERROR_INV_CRT_CNTR, INVALID_DATA_WHILE_CALLING_INVENTORY);
    }
  }

  public String getBulkContainerDetails(List<String> trackingIds, HttpHeaders httpHeaders) {

    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);

    // set WMT_UserId
    final String wmtUserId = httpHeaders.getFirst(PRINT_LABEL_USER_ID);
    if (isBlank(wmtUserId)) {
      final String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);
      if (isBlank(userId)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_DATA,
            String.format(INVALID_HEADER_ERROR_MSG, PRINT_LABEL_USER_ID, wmtUserId));
      }
      httpHeaders.add(PRINT_LABEL_USER_ID, userId);
    }

    final String jsonRequest = gson.toJson(trackingIds);

    String facilityNum =
        Objects.isNull(getFacilityNum())
            ? httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM)
            : getFacilityNum().toString();
    String baseUrl =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(facilityNum, INV_V2_ENABLED, false)
            ? appConfig.getInventoryQueryBaseUrl()
            : appConfig.getInventoryBaseUrl();

    String url = baseUrl + INVENTORY_BULK_CONTAINERS_URL;

    try {
      LOG.info("trackingIds for url={}, headers={}", url, httpHeaders);
      ResponseEntity<String> response =
          retryableRestConnector.exchange(
              url, POST, new HttpEntity<>(jsonRequest, httpHeaders), String.class);
      LOG.info("response for url={}, is={}", url, response);
      return response.getBody();
    } catch (RestClientResponseException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingDataNotFoundException(
          INVENTORY_NOT_FOUND, String.format(INVENTORY_NOT_FOUND_MESSAGE, trackingIds));
    } catch (ResourceAccessException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(UNABLE_TO_PROCESS_INVENTORY, INVENTORY_SERVICE_DOWN);
    }
  }
}
