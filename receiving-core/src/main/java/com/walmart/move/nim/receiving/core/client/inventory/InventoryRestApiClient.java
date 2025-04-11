package com.walmart.move.nim.receiving.core.client.inventory;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;
import static java.lang.Integer.parseInt;
import static java.util.Arrays.asList;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.*;
import com.walmart.move.nim.receiving.core.client.inventory.model.*;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.*;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class InventoryRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryRestApiClient.class);
  public static final String AVAILABLE = "AVAILABLE";
  public static final String AVAILABLE_TO_SELL = "AVAILABLE_TO_SELL";
  public static final String DESCRIPTION = "description";
  public static final String ERROR_CODE = "errorCode";
  public static final String INV_INSUFFICIENT_ERROR_CODE = "GLS-INV-BE-000378";
  public static final String INV_HOLD_REASON_CODE_DEFAULT = "InvHoldReasonCode";
  public static final String INV_HOLD_REASON_CODE_DEFAULT_VALUE = "1"; // Conditioning code
  public static final String DC = "DC";
  public static final String OSS_TO_MAIN_INVENTORY_TRANSFER = "/container/item/org/transfer";
  @Autowired private Gson gson;
  @Autowired private RetryableRestConnector retryableRestConnector;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = BEAN_REST_CONNECTOR)
  private RestConnector simpleRestConnector;

  @Timed(
      name = "notifyBackoutAdjustmentTimed",
      level1 = "uwms-receiving",
      level2 = "inventoryRestApiClient",
      level3 = "cancelLabel")
  @ExceptionCounted(
      name = "notifyBackoutAdjustmentCount",
      level1 = "uwms-receiving",
      level2 = "inventoryRestApiClient",
      level3 = "cancelLabel")
  public CancelContainerResponse notifyBackoutAdjustment(
      InventoryExceptionRequest inventoryExceptionRequest, HttpHeaders httpHeaders) {

    CancelContainerResponse cancelContainerResponse = null;

    String url =
        tenantSpecificConfigReader.getInventoryBaseUrlByFacility()
            + ReceivingConstants.INVENTORY_EXCEPTIONS_URI;

    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);

    LOGGER.info(
        "Notifying VTR to inventory service for lpn: {} with url: {}, jsonRequest: {}, httpHeaders: {}",
        inventoryExceptionRequest.getTrackingId(),
        url,
        inventoryExceptionRequest,
        httpHeaders);

    ResponseEntity<String> response =
        retryableRestConnector.exchange(
            url,
            HttpMethod.POST,
            new HttpEntity<>(gson.toJson(inventoryExceptionRequest), httpHeaders),
            String.class);
    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        cancelContainerResponse =
            new CancelContainerResponse(
                inventoryExceptionRequest.getTrackingId(),
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
      } else {
        cancelContainerResponse =
            new CancelContainerResponse(
                inventoryExceptionRequest.getTrackingId(),
                ReceivingException.INVENTORY_ERROR_CODE,
                ReceivingException.INVENTORY_ERROR_MSG);
      }
    }
    return cancelContainerResponse;
  }

  @Timed(
      name = "notifyReceivingCorrectionAdjustmentTimed",
      level1 = "uwms-receiving",
      level2 = "inventoryRestApiClient",
      level3 = "receivingCorrection")
  @ExceptionCounted(
      name = "notifyReceivingCorrectionAdjustmentCount",
      level1 = "uwms-receiving",
      level2 = "inventoryRestApiClient",
      level3 = "receivingCorrection")
  public ResponseEntity<String> notifyReceivingCorrectionAdjustment(
      InventoryReceivingCorrectionRequest inventoryReceivingCorrectionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    final String jsonRequest = gson.toJson(inventoryReceivingCorrectionRequest);
    final String url =
        tenantSpecificConfigReader.getInventoryBaseUrlByFacility() + INVENTORY_ADJUSTMENTS_URI;

    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);

    LOGGER.info(
        "Notifying receiving correction to inventory service for lpn: {} with jsonRequest: {}, httpHeaders: {}, url: {}",
        inventoryReceivingCorrectionRequest.getTrackingId(),
        jsonRequest,
        httpHeaders,
        url);
    final ResponseEntity<String> response =
        retryableRestConnector.exchange(
            url, HttpMethod.POST, new HttpEntity<>(jsonRequest, httpHeaders), String.class);
    if (response.getStatusCode().is2xxSuccessful()) {
      LOGGER.info(
          "Received receiving correction response: {} from inventory for lpn: {}",
          response.getStatusCodeValue(),
          inventoryReceivingCorrectionRequest.getTrackingId());
    } else {
      LOGGER.error(
          "Error in calling inventory Service for \nurl={}  \njsonRequest={}, \nresponse={}, \nHeaders={}, \nlpn={} ",
          url,
          jsonRequest,
          response,
          httpHeaders,
          inventoryReceivingCorrectionRequest.getTrackingId());
      throw new ReceivingException(
          ADJUST_PALLET_QUANTITY_ERROR_MSG,
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }

    return response;
  }

  /**
   *
   *
   * <pre>
   * {
   * "adjustmentData":
   * {
   * "trackingId": "B08852000020437868",
   * "itemDetails":
   * {
   * "itemNumber": "9126006",
   * "itemIdentifierType": "ITEM_NUMBER",
   * "baseDivisionCode": "WM",
   * "financialReportingGroup": "US"
   * },
   * "currentQty": 640,
   * "adjustByInEa": 10,
   * "reasonCode": 11,
   * "client": "MOBILE"
   * }
   * }
   * </pre>
   *
   * @param adjustByInEa
   * @param initialQtyInEa
   * @return
   */
  public String createInventoryAdjustRequest(
      final String trackingId,
      final String itemNumber,
      final String baseDivisionCode,
      final String financialReportingGroupCode,
      Integer adjustByInEa,
      Integer initialQtyInEa) {
    final InventoryAdjustRequest adjustRequest =
        InventoryAdjustRequest.builder()
            .adjustmentData(
                AdjustmentData.builder()
                    .trackingId(trackingId)
                    .currentQty(initialQtyInEa)
                    .adjustBy(adjustByInEa)
                    .uom(EACHES)
                    .reasonCode(INVENTORY_RECEIVING_CORRECTION_REASON_CODE)
                    .client(SOURCE_MOBILE)
                    .itemDetails(
                        ItemDetails.builder()
                            .itemIdentifierType(INVENTORY_V2_ITEM_NUMBER)
                            .itemIdentifierValue(itemNumber)
                            .baseDivisionCode(baseDivisionCode)
                            .financialReportingGroup(financialReportingGroupCode)
                            .build())
                    .build())
            .build();

    return gson.toJson(adjustRequest);
  }

  /**
   *
   *
   * <pre>
   * [
   * {
   * "trackingId": "R08852000020071729",
   * "holdAllQty": true,
   * "putCompleteHierarchyOnHold": false,
   * "holdReasons": [75],
   * "holdDirectedBy": "DC",
   * "holdInitiatedTime": "2022-08-31T23:07:05.223Z"
   * }
   * ]
   * </pre>
   *
   * @param trackingId
   * @return String InventoryOnHoldRequest as json string
   */
  public String createInventoryOnHoldRequest(String trackingId) {
    final InventoryOnHoldRequest inventoryOnHold =
        InventoryOnHoldRequest.builder()
            .trackingId(trackingId)
            .holdAllQty(true)
            .putCompleteHierarchyOnHold(false)
            .holdReasons(
                asList(
                    parseInt(
                        tenantSpecificConfigReader.getCcmValue(
                            getFacilityNum(),
                            INV_HOLD_REASON_CODE_DEFAULT,
                            INV_HOLD_REASON_CODE_DEFAULT_VALUE))))
            .holdDirectedBy(DC)
            .holdInitiatedTime(new Date())
            .build();
    final List<InventoryOnHoldRequest> inventoryOnHoldList = asList(inventoryOnHold);
    final Gson gsonInvFmt =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            .create();
    return gsonInvFmt.toJson(inventoryOnHoldList);
  }

  /**
   * Json Contract
   *
   * <pre>
   * [
   * {
   * "trackingId": "R08852000020071729",
   * "removeHierarchyOnUnHold": false,
   * "holdReasons":
   * [
   * 1
   * ],
   * "statusPostUnHold": "AVAILABLE",
   * "itemStatePostHold": "AVAILABLE_TO_SELL"
   * }
   * ]
   * </pre>
   *
   * @param trackingId
   * @return String InventoryOffHoldRequest as json string
   */
  public String createInventoryOffHoldRequest(String trackingId) {
    final InventoryOffHoldRequest inventoryOffHoldRequestItem =
        InventoryOffHoldRequest.builder()
            .trackingId(trackingId)
            .statusPostUnHold(AVAILABLE)
            .itemStatePostHold(AVAILABLE_TO_SELL)
            .removeHierarchyOnUnHold(false)
            .holdReasons(
                asList(
                    parseInt(
                        tenantSpecificConfigReader.getCcmValue(
                            getFacilityNum(),
                            INV_HOLD_REASON_CODE_DEFAULT,
                            INV_HOLD_REASON_CODE_DEFAULT_VALUE))))
            .build();
    final List<InventoryOffHoldRequest> inventoryOffHoldRequestItemList =
        asList(inventoryOffHoldRequestItem);
    return gson.toJson(inventoryOffHoldRequestItemList);
  }

  /**
   * Send an adjustment to INVENTORY
   *
   * @param trackingId
   * @param httpHeaders
   * @return CancelContainerResponse
   */
  public CancelContainerResponse notifyVtrToInventory(String trackingId, HttpHeaders httpHeaders) {
    CancelContainerResponse cancelContainerResponse = null;
    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    String url;
    String requestBody;

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), INV_V2_ENABLED, false)) {
      url = appConfig.getInventoryCoreBaseUrl() + INVENTORY_VTR_V2;
      httpHeaders.add(
          IDEM_POTENCY_KEY, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY) + "-" + trackingId);
      httpHeaders.add(FLOW_NAME, VTR_FLOW);
      requestBody =
          gson.toJson(
              InventoryVtrRequest.builder()
                  .containerAdjustmentData(
                      ContainerAdjustmentData.builder()
                          .reasonCode(VTR_REASON_CODE)
                          .reasonDesc(VTR_REASON_DESC)
                          .comment(VTR_REASON_DESC)
                          .trackingId(trackingId)
                          .vtrFlag(TRUE_STRING)
                          .build())
                  .build());
    } else {
      InventoryExceptionRequest inventoryExceptionRequest = new InventoryExceptionRequest();
      inventoryExceptionRequest.setTrackingId(trackingId);
      inventoryExceptionRequest.setComment(ReceivingConstants.VTR_COMMENT);
      inventoryExceptionRequest.setReasonCode(String.valueOf(VTR_REASON_CODE));

      url = appConfig.getInventoryBaseUrl() + INVENTORY_EXCEPTIONS_URI;
      requestBody = gson.toJson(inventoryExceptionRequest);
    }

    LOGGER.info("Calling inventory adjustment, URL={} Request={}", url, requestBody);
    ResponseEntity<String> response =
        simpleRestConnector.exchange(
            url, HttpMethod.POST, new HttpEntity<>(requestBody, httpHeaders), String.class);
    LOGGER.info("Response from inventory, trackingId={} Response={}", trackingId, response);

    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      LOGGER.error(
          "Failed to submit VTR to Inventory, post URL={},  Request&Headers={},  {},  Response={}",
          url,
          requestBody,
          httpHeaders,
          response);
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        cancelContainerResponse =
            new CancelContainerResponse(
                trackingId,
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
      } else {
        cancelContainerResponse =
            new CancelContainerResponse(
                trackingId,
                ReceivingException.INVENTORY_ERROR_CODE,
                ReceivingException.INVENTORY_ERROR_MSG);
      }
    }

    return cancelContainerResponse;
  }

  public String getInventoryBohQtyByItem(
      Long itemNumber,
      String baseDivisionCode,
      String reportingGroup,
      String fromOrgUnitId,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    String url = null;
    String requestBody = null;
    String bohQty = null;
    ResponseEntity<String> response = null;
    InventoryItemList inventoryItemList =
        InventoryItemList.builder()
            .itemNumber(itemNumber)
            .inventoryAggLevel(CONTAINER_ITEM)
            .build();
    InventoryItemBohQtyRequest inventoryItemBohQtyRequest =
        InventoryItemBohQtyRequest.builder()
            .baseDivisionCode(baseDivisionCode)
            .financialReportingGroup(reportingGroup)
            .bohQtyUom(EA)
            .inventoryStatus(AVAILABLE)
            .itemList(Arrays.asList(inventoryItemList))
            .build();
    try {
      url = appConfig.getInventoryQueryBaseUrl() + INV_BOH_QTY_URL;
      requestBody = gson.toJson(inventoryItemBohQtyRequest);
      httpHeaders.add(ORG_UNIT_ID_HEADER, fromOrgUnitId);
      LOGGER.info("Calling inventory for boh qty, URL={} Request={}", url, requestBody);
      response =
          simpleRestConnector.exchange(
              url, HttpMethod.POST, new HttpEntity<>(requestBody, httpHeaders), String.class);
      LOGGER.info(
          "Response from inventory for boh qty, itemNumber={} Response={}", itemNumber, response);
    } catch (Exception exp) {
      LOGGER.error(
          "Failed to get BOH qty from Inventory, post URL={},  Request&Headers={},  {},  Response={}, StackTrace={}",
          url,
          requestBody,
          httpHeaders,
          response,
          ExceptionUtils.getStackTrace(exp));
      throw new ReceivingException(
          String.format(ReceivingException.INVENTORY_BOH_ERROR, itemNumber),
          BAD_REQUEST,
          ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
          ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
    }
    if (response.getStatusCode().is2xxSuccessful()) {
      JsonObject responseObject = gson.fromJson(response.getBody(), JsonObject.class);
      JsonArray containerItemAggregatedList =
          responseObject
              .getAsJsonObject(BOH_DISTRIBUTION)
              .getAsJsonArray(CONTAINER_ITEM_AGGREGATED_LIST);
      if (!containerItemAggregatedList.isEmpty()) {
        for (JsonElement attribute : containerItemAggregatedList) {
          bohQty = attribute.getAsJsonObject().get(BOH).getAsJsonObject().get(QTY).getAsString();
        }
      } else {
        // log error as item is not in INV
        LOGGER.error(
            "Failed to get BOH qty from Inventory, post URL={},  Request&Headers={},  {},  Response={}",
            url,
            requestBody,
            httpHeaders,
            response);
        throw new ReceivingException(
            String.format(ReceivingException.INVENTORY_INSUFFICIENT_BOH_ERROR, itemNumber),
            BAD_REQUEST,
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
            ReceivingException.INVENTORY_QUANTITY_ERROR);
      }

    } else {
      LOGGER.error(
          "Failed to get BOH qty from Inventory, post URL={},  Request&Headers={},  {},  Response={}",
          url,
          requestBody,
          httpHeaders,
          response);
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        throw new ReceivingException(
            String.format(ReceivingException.INVENTORY_BOH_ERROR, itemNumber),
            SERVICE_UNAVAILABLE,
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
      } else {
        throw new ReceivingException(
            String.format(ReceivingException.INVENTORY_BOH_ERROR, itemNumber),
            BAD_REQUEST,
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
      }
    }

    return bohQty;
  }

  public void postInventoryOssReceiving(
      InventoryOssReceivingRequest inventoryOssReceivingRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    String requestBody = null;
    String url = null;
    ResponseEntity<String> response = null;
    Map<String, String> errorResponseMap = null;
    url = appConfig.getInventoryCoreBaseUrl() + OSS_TO_MAIN_INVENTORY_TRANSFER;

    try {
      requestBody = getObjectMapper().writeValueAsString(inventoryOssReceivingRequest);
      String userId =
          inventoryOssReceivingRequest.getTransfer().getTargetContainer().getCreateUserid();
      HttpHeaders invHttpHeaders = getHttpHeadersForOssTransfer(httpHeaders, userId);
      LOGGER.info(
          "Calling inventory to post OSS Receiving into main, URL={} Request={} httpHeaders={}",
          url,
          requestBody,
          invHttpHeaders);
      response =
          simpleRestConnector.exchange(
              url, HttpMethod.POST, new HttpEntity<>(requestBody, invHttpHeaders), String.class);
    } catch (HttpClientErrorException he) {
      LOGGER.error(
          "Exception from inventory while posting OSS Receiving into main Response={}",
          he.getResponseBodyAsString());
      if (StringUtils.isNotEmpty(he.getMessage())
          && he.getMessage().toUpperCase().contains(HttpStatus.CONFLICT.toString())) {
        errorResponseMap =
            (Map<String, String>) gson.fromJson(he.getResponseBodyAsString(), List.class).get(0);
        if (INV_INSUFFICIENT_ERROR_CODE.equalsIgnoreCase(errorResponseMap.get(ERROR_CODE))) {
          throw new ReceivingException(
              INVENTORY_INSUFFICIENT_ERROR_MSG,
              HttpStatus.CONFLICT,
              ReceivingException.INVENTORY_ERROR_CODE,
              ReceivingException.INVENTORY_QUANTITY_ERROR);
        } else {
          throw new ReceivingException(
              errorResponseMap.get(DESCRIPTION),
              HttpStatus.CONFLICT,
              ReceivingException.INVENTORY_ERROR_CODE,
              ReceivingException.INVENTORY_ERROR_MSG);
        }
      } else {
        throw new ReceivingException(
            ReceivingException.INVENTORY_ERROR_MSG,
            HttpStatus.CONFLICT,
            ReceivingException.INVENTORY_ERROR_CODE,
            ReceivingException.INVENTORY_ERROR_MSG);
      }
    } catch (Exception e) {
      LOGGER.info(
          "Exception from inventory while posting OSS Receiving into main Response={}", response);
      throw new ReceivingException(
          ReceivingException.INVENTORY_ERROR_MSG,
          SERVICE_UNAVAILABLE,
          ReceivingException.INVENTORY_ERROR_CODE,
          ReceivingException.INVENTORY_ERROR_MSG);
    }
    if (!response.getStatusCode().is2xxSuccessful()) {
      LOGGER.error(
          "Failed to post OSS Receiving into main with Inventory, post URL={},  Request&Headers={},  {},  Response={}",
          url,
          requestBody,
          httpHeaders,
          response);
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        throw new ReceivingException(
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG,
            SERVICE_UNAVAILABLE,
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
            ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
      } else {
        throw new ReceivingException(
            ReceivingException.INVENTORY_ERROR_MSG,
            SERVICE_UNAVAILABLE,
            ReceivingException.INVENTORY_ERROR_CODE,
            ReceivingException.INVENTORY_ERROR_MSG);
      }
    }
    LOGGER.info("Success posting OSS Receiving into main with Inventory, Response={}", response);
  }

  /**
   * Submit OSS VTR
   *
   * @param trackingId
   * @param containerItemMiscInfo
   * @param httpHeaders
   * @return
   */
  public CancelContainerResponse notifyOssVtrToInventory(
      String trackingId, Map<String, String> containerItemMiscInfo, HttpHeaders httpHeaders) {
    CancelContainerResponse cancelContainerResponse = null;
    Integer ossOrgUnitId = null;
    if (containerItemMiscInfo.containsKey(FROM_SUBCENTER)
        && containerItemMiscInfo.get(FROM_SUBCENTER) != null) {
      ossOrgUnitId = Integer.valueOf(containerItemMiscInfo.get(FROM_SUBCENTER));
    } else {
      return new CancelContainerResponse(trackingId, VTR_ERROR_CODE, INVALID_OSS_TRANSFER_PO_ERROR);
    }

    // Prepare request payload
    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    httpHeaders.add(
        IDEM_POTENCY_KEY, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY) + "-" + trackingId);
    httpHeaders.add(FLOW_NAME, VTR_FLOW);
    String requestBody = prepareOssVtrRequest(trackingId, ossOrgUnitId);
    String url = appConfig.getInventoryCoreBaseUrl() + OSS_TO_MAIN_INVENTORY_TRANSFER;
    LOGGER.info(
        "Calling inventory to post OSS VTR, URL={} Request={} httpHeaders={}",
        url,
        requestBody,
        httpHeaders);

    ResponseEntity<String> response = null;
    try {
      response =
          simpleRestConnector.exchange(
              url, HttpMethod.POST, new HttpEntity<>(requestBody, httpHeaders), String.class);
    } catch (Exception e) {
      return new CancelContainerResponse(
          trackingId,
          ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
          ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
    }
    LOGGER.info("Response from inventory for posting OSS VTR, Response={}", response);

    if (!response.getStatusCode().is2xxSuccessful()) {
      LOGGER.error(
          "Failed to submit OSS VTR to Inventory, post URL={},  Request&Headers={},  {},  Response={}",
          url,
          requestBody,
          httpHeaders,
          response);
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        cancelContainerResponse =
            new CancelContainerResponse(
                trackingId,
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE,
                ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
      } else {
        cancelContainerResponse =
            new CancelContainerResponse(
                trackingId,
                ReceivingException.INVENTORY_ERROR_CODE,
                ReceivingException.INVENTORY_ERROR_MSG);
      }
    }

    return cancelContainerResponse;
  }

  private String prepareOssVtrRequest(String trackingId, Integer ossOrgUnitId) {
    ContainerIdentifier containerIdentifier = new ContainerIdentifier();
    containerIdentifier.setIdentifierType(TRACKING_ID);
    containerIdentifier.setIdentifierValue(trackingId);
    ArrayList<ContainerIdentifier> containerIdentifierList = new ArrayList<>();
    containerIdentifierList.add(containerIdentifier);

    Source source = new Source();
    source.setCtnrIdentifiers(containerIdentifierList);

    TargetContainer targetContainer = new TargetContainer();
    targetContainer.setTrackingId(trackingId + "_" + ossOrgUnitId);
    targetContainer.setOrgUnitId(ossOrgUnitId);

    ReasonData reasonData = new ReasonData();
    reasonData.setReasonCode(VTR_REASON_CODE);
    reasonData.setClient(APP_NAME_VALUE);

    Transfer transfer = new Transfer();
    transfer.setSource(source);
    transfer.setTargetContainer(targetContainer);
    transfer.setReasonData(reasonData);

    InventoryOssReceivingRequest inventoryOssReceivingRequest = new InventoryOssReceivingRequest();
    inventoryOssReceivingRequest.setTransfer(transfer);

    return gson.toJson(inventoryOssReceivingRequest);
  }

  public HttpHeaders getHttpHeadersForOssTransfer(HttpHeaders httpHeaders, String userId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    headers.set(CORRELATION_ID_HEADER_KEY, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    headers.set(TENENT_FACLITYNUM, httpHeaders.getFirst(TENENT_FACLITYNUM));
    headers.set(TENENT_COUNTRY_CODE, httpHeaders.getFirst(TENENT_COUNTRY_CODE));
    headers.set(API_VERSION, API_VERSION_VALUE);
    headers.set(ORG_UNIT_ID, tenantSpecificConfigReader.getOrgUnitId());
    headers.set(FLOW_NAME, OSS_TRANSFER);
    headers.set(IDEM_POTENCY_KEY, httpHeaders.getFirst(IDEM_POTENCY_KEY));
    headers.set(CONTENT_TYPE, APPLICATION_JSON);
    headers.set(USER_ID_HEADER_KEY, userId);
    return headers;
  }

  public ObjectMapper getObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    SimpleDateFormat dateFormat = new SimpleDateFormat(UTC_DATE_FORMAT);
    objectMapper.setDateFormat(dateFormat);
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.registerModule(new Jdk8Module());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
    objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
    objectMapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    return objectMapper;
  }

  /**
   * Update location for a pallet in inventory
   *
   * @param inventoryLocationUpdateRequest
   * @param httpHeaders
   */
  @Timed(
      name = "updateLocationTimed",
      level1 = "uwms-receiving",
      level2 = "inventoryRestApiClient",
      level3 = "updateLocation")
  @ExceptionCounted(
      name = "updateLocationExceptionCount",
      level1 = "uwms-receiving",
      level2 = "inventoryRestApiClient",
      level3 = "updateLocation")
  public void updateLocation(
      InventoryLocationUpdateRequest inventoryLocationUpdateRequest, HttpHeaders httpHeaders) {
    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    String url =
        tenantSpecificConfigReader.getInventoryBaseUrlByFacility() + UPDATE_INVENTORY_LOCATION_URI;
    String requestBody = null;
    ResponseEntity<String> response = null;
    requestBody = gson.toJson(inventoryLocationUpdateRequest);
    LOGGER.info(
        "Calling Inventory Location Update for a pallet in inventory for CorrelationId={}, url={}, RequestBody={} and Headers={}",
        httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY),
        url,
        requestBody,
        httpHeaders);
    response =
        retryableRestConnector.exchange(
            url, HttpMethod.PUT, new HttpEntity<>(requestBody, httpHeaders), String.class);
    if (response.getStatusCode().is2xxSuccessful()) {
      LOGGER.info(
          "Successfully updated Location for a pallet in inventory for RequestBody={}",
          requestBody);
    } else {
      LOGGER.error(
          "Failed to Update location for a pallet in inventory for RequestBody={}", requestBody);
    }
  }

  /**
   * Inventory call for putawayConfirmation.
   *
   * @param inventoryPutawayConfirmationRequest
   * @return
   */
  @Timed(
      name = "putawayConfirmationTimed",
      level1 = "uwms-receiving",
      level2 = "InventoryService",
      level3 = "sendPutawayConfirmation")
  @ExceptionCounted(
      name = "putawayConfirmationExceptionCount",
      level1 = "uwms-receiving",
      level2 = "InventoryService",
      level3 = "sendPutawayConfirmation")
  public String sendPutawayConfirmation(
      InventoryPutawayConfirmationRequest inventoryPutawayConfirmationRequest) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.add(REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    String url =
        tenantSpecificConfigReader.getInventoryBaseUrlByFacility()
            + INVENTORY_PUTAWAY_CONFIRMATION_URI;
    ResponseEntity<String> responseEntity = null;
    try {
      LOGGER.info(
          "Request Inventory call for sendPutawayConfirmation with trackingId: {}",
          inventoryPutawayConfirmationRequest.getTrackingId());
      responseEntity =
          retryableRestConnector.post(
              url, gson.toJson(inventoryPutawayConfirmationRequest), httpHeaders, String.class);
      LOGGER.info(
          ReceivingConstants.INVENTORY_PUTAWAY_CONFIRMATION_REQUEST_MSG,
          inventoryPutawayConfirmationRequest.getTrackingId(),
          responseEntity.getStatusCode());
    } catch (RestClientResponseException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.INTERNAL_ERROR_INV_CRT_CNTR, INVENTORY_UNEXPECTED_DATA_ERROR);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(UNABLE_TO_PROCESS_INVENTORY, INVENTORY_SERVICE_DOWN);
    }
    return responseEntity.getBody();
  }

  public String createInventoryAdjustRequest(
          final String trackingId,
          final String itemNumber,
          final String baseDivisionCode,
          final String financialReportingGroupCode,
          Integer adjustBy,
          Integer initialQty,
          int reasonCode) {
    final InventoryAdjustRequest adjustRequest =
            InventoryAdjustRequest.builder()
                    .adjustmentData(
                            AdjustmentData.builder()
                                    .trackingId(trackingId)
                                    .adjustBy(adjustBy)
                                    .currentQty(initialQty)
                                    .reasonCode(reasonCode)
                                    .client(SOURCE_MOBILE)
                                    .itemDetails(
                                            ItemDetails.builder()
                                                    .itemIdentifierType(INVENTORY_V2_ITEM_NUMBER)
                                                    .itemIdentifierValue(itemNumber)
                                                    .baseDivisionCode(baseDivisionCode)
                                                    .financialReportingGroup(financialReportingGroupCode)
                                                    .build())
                                    .build())
                    .build();

    return gson.toJson(adjustRequest);
  }

  public void adjustQuantity(
          String cId,
          String trackingId,
          Integer adjustBy,
          HttpHeaders httpHeaders,
          ContainerItem ci,
          int initialQty,
          int reasonCode)
          throws ReceivingException {
    String url;
    String request;
    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    ResponseEntity<String> response;
    url = appConfig.getInventoryCoreBaseUrl() + INVENTORY_ITEMS_URI_ADJUST_V2;
    request =
            createInventoryAdjustRequest(
                    trackingId,
                    ci.getItemNumber().toString(),
                    ci.getBaseDivisionCode(),
                    ci.getFinancialReportingGroupCode(),
                    adjustBy,
                    initialQty,
                    reasonCode);
    httpHeaders.add(IDEM_POTENCY_KEY, cId + "-" + trackingId);
    httpHeaders.add(FLOW_NAME, ADJUSTMENT_FLOW);
    LOGGER.info(
            "step:8 cId={}, adjust inventory Service for Request={}, Headers={}",
            cId,
            request,
            httpHeaders);
    response =
            simpleRestConnector.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request, httpHeaders), String.class);

    if (HttpStatus.OK != response.getStatusCode()) {
      LOGGER.error(
              "error calling inventory Service for url={}  Request={}, response={}, Headers={}",
              url,
              request,
              response,
              httpHeaders);
      throw new ReceivingException(
              ADJUST_PALLET_QUANTITY_ERROR_MSG,
              BAD_REQUEST,
              ADJUST_PALLET_QUANTITY_ERROR_CODE,
              ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }
  }
}
