package com.walmart.move.nim.receiving.core.client.slotting;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.SLOTTING_NOT_ACCESSIBLE;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_MANUAL_GDC_ENABLED;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Error;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.slotting.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for Slotting Rest API
 *
 * @author v0k00fe
 */
@Component
public class SlottingRestApiClient {

  private static final String ERROR_TAG = "error";
  private static final Logger LOGGER = LoggerFactory.getLogger(SlottingRestApiClient.class);
  private static final String PALLET_BUILD =
      "/smartslotting/witron/divert/getDivertLocations"; // Automation DCs
  private static final String SLOTTING_ENDPOINT = "/smartslotting/api/container/closedivert";
  private static final String FREE_SLOT = "/smartslotting/api/pharmacy/reconsileSlot";

  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private Gson gson;
  @Autowired private SlottingErrorHandler slottingErrorHandler;
  @ManagedConfiguration private AppConfig appConfig;
  private Gson gsonDateAdaptor =
      new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  String NO_SLOTS_AVAILABLE = "There are no slots available for some of the pallets";
  String SLOTTING_BAD_RESPONSE_ERROR_MSG =
      "Client exception from Slotting. HttpResponseStatus= %s ResponseBody = %s";
  String SLOTTING_RESOURCE_RESPONSE_ERROR_MSG = "Resource exception from Slotting. Error MSG = %s";
  String SLOTTING_ERROR_MSG_503 = "Smart Slotting service is down. Error MSG = %s";

  @Resource(name = "restConnector")
  private RestConnector restConnector;

  /**
   * Pallet Build
   *
   * @return
   * @throws GDMRestApiClientException
   */
  @Timed(
      name = "palletBuildTimed",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "palletBuild")
  @ExceptionCounted(
      name = "palletBuildExceptionCount",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "palletBuild")
  public @Valid SlottingPalletBuildResponse palletBuild(
      @Valid SlottingPalletBuildRequest slottingPalletBuildRequest, Map<String, Object> httpHeaders)
      throws SlottingRestApiClientException {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.set(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString());
    requestHeaders.set(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE).toString());
    final String cId = httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString();
    requestHeaders.set(ReceivingConstants.CORRELATION_ID_HEADER_KEY, cId);
    requestHeaders.set(
        ReceivingConstants.USER_ID_HEADER_KEY,
        httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).toString());
    requestHeaders.set("orgUnitId", "224");
    requestHeaders.set(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    requestHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    String uri = appConfig.getSlottingBaseUrl() + PALLET_BUILD;

    String palletBuildRequestBody = gson.toJson(slottingPalletBuildRequest);

    HttpEntity<String> request = new HttpEntity<>(palletBuildRequestBody, requestHeaders);
    ResponseEntity<String> slottingResponseEntity = null;
    try {
      LOGGER.info("Slotting for correlationId={}, url={}, request={}", cId, uri, request);
      slottingResponseEntity = restConnector.exchange(uri, HttpMethod.POST, request, String.class);
      LOGGER.info(
          "Slotting for correlationId={}, response={}", cId, slottingResponseEntity.getBody());
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          slottingResponseEntity,
          ExceptionUtils.getStackTrace(e));

      SlottingPalletBuildErrorResponse response =
          gson.fromJson(e.getResponseBodyAsString(), SlottingPalletBuildErrorResponse.class);

      final int rawStatusCode = e.getRawStatusCode();
      if (SERVICE_UNAVAILABLE.value() == rawStatusCode)
        throw new ReceivingInternalException(
            SLOTTING_NOT_ACCESSIBLE, String.format(SLOTTING_ERROR_MSG_503, e.getMessage()));

      throw buildSlottingRestApiClientException(response, rawStatusCode);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new SlottingRestApiClientException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
    SlottingPalletBuildErrorResponse response =
        gson.fromJson(slottingResponseEntity.getBody(), SlottingPalletBuildErrorResponse.class);
    if (CollectionUtils.isNotEmpty(response.getMessages())) {
      throw buildSlottingRestApiClientException(response, HttpStatus.BAD_REQUEST.value());
    }

    return gson.fromJson(slottingResponseEntity.getBody(), SlottingPalletBuildResponse.class);
  }

  private SlottingRestApiClientException buildSlottingRestApiClientException(
      SlottingPalletBuildErrorResponse response, int statusCode) {
    SlottingRestApiClientException slottingRestApiClientException =
        new SlottingRestApiClientException();
    slottingRestApiClientException.setHttpStatus(HttpStatus.valueOf(statusCode));
    List<SlottingPalletBuildErrorResponseMessage> responseMessages = response.getMessages();
    if (CollectionUtils.isNotEmpty(responseMessages)) {
      ErrorResponse errorResponse =
          new ErrorResponse(responseMessages.get(0).getCode(), responseMessages.get(0).getDesc());
      slottingRestApiClientException.setErrorResponse(errorResponse);
    }
    return slottingRestApiClientException;
  }

  /**
   * Smart Slotting
   *
   * @return
   * @throws SlottingRestApiClientException
   */
  @Timed(
      name = "getSlotTimed",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "getSlot")
  @ExceptionCounted(
      name = "getSlotExceptionCount",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "getSlot")
  public SlottingPalletResponse getSlot(
      SlottingPalletRequest slottingPalletRequest, HttpHeaders httpHeaders) {

    final String cId = httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString();
    String uri = appConfig.getSlottingBaseUrl() + SLOTTING_ENDPOINT;
    String slottingPalletRequestBody = gson.toJson(slottingPalletRequest);
    SlottingPalletResponse response = null;

    // This configuration is only or Test to acquire Slot from 32709 for RDS System.
    Boolean isRxSmartSlottingTestOnlyEnabled =
        configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), "isRxSmartSlottingTestOnlyEnabled");
    if (isRxSmartSlottingTestOnlyEnabled) {
      httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32709");
    }

    httpHeaders.set(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    LOGGER.info(
        "Invoking Smart Slotting for correlationId={}, url={}, request={}  headers={}",
        cId,
        uri,
        slottingPalletRequestBody,
        httpHeaders);
    ResponseEntity<String> responseEntity = null;
    try {
      responseEntity =
          restConnector.exchange(
              uri,
              HttpMethod.POST,
              new HttpEntity<>(slottingPalletRequestBody, httpHeaders),
              String.class);
      LOGGER.info(
          "Smart Slotting response for featureType: {} and correlationId={}, response={}",
          httpHeaders.getFirst(ReceivingConstants.SLOTTING_FEATURE_TYPE),
          cId,
          responseEntity.getBody());

      if (slottingPalletRequest instanceof SlottingPalletRequestWithRdsPayLoad) {
        response =
            gson.fromJson(responseEntity.getBody(), SlottingPalletResponseWithRdsResponse.class);
      } else {
        response = gson.fromJson(responseEntity.getBody(), SlottingPalletResponse.class);
      }

      // Return response is isManualGdc flag enabled
      if (configUtils.getConfiguredFeatureFlag(
          getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false)) {
        return response;
      }

      if (!httpHeaders.containsKey(ReceivingConstants.SOURCE)
          || httpHeaders.containsKey(ReceivingConstants.SOURCE)
              && !httpHeaders
                  .getFirst(ReceivingConstants.SOURCE)
                  .equals(ReceivingConstants.DECANT_API)) {
        validateSlottingResponse(response, uri, slottingPalletRequest, httpHeaders);
      }
      return response;
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          slottingPalletRequestBody,
          responseEntity,
          ExceptionUtils.getStackTrace(e));
      slottingErrorHandler.handle(e);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          slottingPalletRequestBody,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE
              + " and response="
              + responseEntity,
          ExceptionUtils.getStackTrace(e));
      slottingErrorHandler.handle(e);
    }
    return response;
  }

  /**
   * This method validates Smart Slotting Response and throws error. If Slotting returns RDS Error
   * response then will throw RDS specific error message. Ignore error message validations on
   * Slotting response for findPrimeSlot feature type with multiple items/locations on SSTK(RDC)
   * Receiving (i.e.Re-Induct Exception Receiving fetches prime slot for multiple items & no
   * validation required on slotting locations error response)
   *
   * @param slottingPalletResponse
   * @param slottingUri
   * @param slottingPalletRequest
   */
  private void validateSlottingResponse(
      SlottingPalletResponse slottingPalletResponse,
      String slottingUri,
      SlottingPalletRequest slottingPalletRequest,
      HttpHeaders httpHeaders) {
    if (CollectionUtils.isNotEmpty(slottingPalletResponse.getLocations())) {
      if (isFeatureTypeFindPrimeSlot(slottingPalletResponse, slottingPalletRequest, httpHeaders)) {
        return;
      }
      if (slottingPalletResponse instanceof SlottingPalletResponseWithRdsResponse) {
        SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse =
            (SlottingPalletResponseWithRdsResponse) slottingPalletResponse;
        SlottingPalletRequestWithRdsPayLoad slottingPalletRequestWithRdsPayLoad =
            (SlottingPalletRequestWithRdsPayLoad) slottingPalletRequest;
        validateRdsResponseFromSlotting(
            slottingPalletResponseWithRdsResponse, slottingPalletRequestWithRdsPayLoad);
      }
      for (SlottingDivertLocations location : slottingPalletResponse.getLocations()) {
        if (ERROR_TAG.equals(location.getType())) {
          LOGGER.error(
              "Error response from Smart Slotting for URI {}, response body = {}",
              slottingUri,
              slottingPalletResponse);
          validateSlottingErrorResponse(slottingPalletRequest, location);
        }
      }
    }
  }

  private boolean isFeatureTypeFindPrimeSlot(
      SlottingPalletResponse slottingPalletResponse,
      SlottingPalletRequest slottingPalletRequest,
      HttpHeaders httpHeaders) {
    if (slottingPalletResponse.getLocations().size() > 1
        && slottingPalletRequest
            .getReceivingMethod()
            .equalsIgnoreCase(ReceivingConstants.SLOTTING_SSTK_RECEIVING_METHOD)) {
      if (Objects.nonNull(httpHeaders.get(ReceivingConstants.SLOTTING_FEATURE_TYPE))) {
        String featureType = httpHeaders.getFirst(ReceivingConstants.SLOTTING_FEATURE_TYPE);
        if (Objects.nonNull(featureType)
            && featureType.equalsIgnoreCase(ReceivingConstants.SLOTTING_FIND_PRIME_SLOT)) {
          return true;
        }
      }
    }
    return false;
  }

  private void validateSlottingErrorResponse(
      SlottingPalletRequest slottingPalletRequest, SlottingDivertLocations location) {
    Object[] errorMessageValues = new Object[] {location.getCode(), location.getDesc()};
    switch (location.getCode()) {
      case ReceivingConstants.SLOTTING_PRIME_SLOT_NOT_FOUND:
        throw new ReceivingBadDataException(
            ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
            String.valueOf(
                slottingPalletRequest
                    .getContainerDetails()
                    .get(0)
                    .getContainerItemsDetails()
                    .get(0)
                    .getItemNbr()));
      case ReceivingConstants.SLOTTING_AUTO_SLOT_NOT_AVAILABLE:
        throw new ReceivingBadDataException(
            ExceptionCodes.AUTO_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues));
      case ReceivingConstants.SLOTTING_INVENTORY_AVAILABLE_FOR_DIFFERENT_ITEM_IN_PRIMESLOT:
        throw new ReceivingBadDataException(
            ExceptionCodes.INVENTORY_AVAILABLE_FOR_DIFFERENT_ITEM_IN_PRIMESLOT,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
            errorMessageValues);
      case ReceivingConstants.SLOTTING_MANUAL_SLOT_NOT_AVAILABLE:
        String manualSlot = slottingPalletRequest.getContainerDetails().get(0).getLocationName();
        throw new ReceivingBadDataException(
            ExceptionCodes.MANUAL_SLOT_NOT_AVAILABLE_IN_SMART_SLOTTING,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
            manualSlot);
      case ReceivingConstants.BULK_SLOTTING_DELIVERY_NOT_FOUND:
        throw new ReceivingBadDataException(
            ExceptionCodes.BULK_SLOT_DELIVERY_NOT_FOUND_409,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues));
      case ReceivingConstants.BULK_SLOT_CAPACITY_NOT_AVAILABLE_FOR_DELIVERY:
        throw new ReceivingBadDataException(
            ExceptionCodes.BULK_SLOT_CAPACITY_NOT_AVAILABLE_FOR_DELIVERY_404,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
            slottingPalletRequest.getContainerDetails().get(0).getLocationName());
      case ReceivingConstants.SLOTTING_PO_TYPE_MISMATCH_ERROR_CODE:
        if (location.getDesc().equals(ReceivingConstants.SLOTTING_PO_TYPE_DA_MISMATCH_ERROR_DESC)) {
          throw new ReceivingBadDataException(
              ExceptionCodes.SLOT_PO_TYPE_MISMATCH_ERROR_400,
              String.format(
                  ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                  errorMessageValues),
              ReceivingConstants.PURCHASE_REF_TYPE_SSTK);
        } else {
          throw new ReceivingBadDataException(
              ExceptionCodes.SLOT_PO_TYPE_MISMATCH_ERROR_400,
              String.format(
                  ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                  errorMessageValues),
              ReceivingConstants.PURCHASE_REF_TYPE_DA);
        }
      case ReceivingConstants.SLOTTING_INACTIVE_SLOT_ERROR_CODE:
        throw new ReceivingBadDataException(
            ExceptionCodes.SLOTTING_INACTIVE_SLOT_ERROR_400,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
            slottingPalletRequest.getContainerDetails().get(0).getLocationName());
      case ReceivingConstants.SLOTTING_FROZEN_SLOT_ERROR_CODE:
        throw new ReceivingBadDataException(
            ExceptionCodes.SLOTTING_FROZEN_SLOT_ERROR_ERROR_400,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
            slottingPalletRequest.getContainerDetails().get(0).getLocationName());
      case ReceivingConstants.SLOTTING_LOCATION_NOT_CONFIGURED_ERROR_CODE:
        throw new ReceivingBadDataException(
            ExceptionCodes.SLOTTING_LOCATION_NOT_CONFIGURED_ERROR_400,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
            slottingPalletRequest.getContainerDetails().get(0).getLocationName());
      case ReceivingConstants.SLOTTING_CONTAINER_ITEM_LOCATION_NOT_FOUND_ERROR_CODE:
        throw new ReceivingBadDataException(
            ExceptionCodes.SLOTTING_CONTAINER_ITEM_LOCATION_NOT_FOUND_ERROR_400,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
            slottingPalletRequest.getContainerDetails().get(0).getLocationName());
      case ReceivingConstants.BULK_SLOT_NOT_AVAILABLE_FOR_DELIVERY_ERROR_CODE_43:
      case ReceivingConstants.BULK_SLOT_NOT_AVAILABLE_FOR_DELIVERY_ERROR_CODE_45:
      case ReceivingConstants.BULK_SLOT_NOT_AVAILABLE_FOR_DELIVERY_ERROR_CODE_46:
        throw new ReceivingBadDataException(
            ExceptionCodes.BULK_SLOT_NOT_AVAILABLE_FOR_DELIVERY_409,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
            slottingPalletRequest.getContainerDetails().get(0).getLocationName());
      case ReceivingConstants.MANUAL_SLOTTING_NOT_SUPPORTED:
        throw new ReceivingBadDataException(
            ExceptionCodes.MANUAL_SLOTTING_NOT_SUPPORTED_FOR_ATLAS_ITEMS,
            ReceivingConstants.MANUAL_SLOTTING_NOT_SUPPORTED_FOR_ATLAS_ITEMS);
      default:
        throw new ReceivingBadDataException(
            ExceptionCodes.SMART_SLOT_NOT_FOUND,
            String.format(
                ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG, errorMessageValues),
            errorMessageValues);
    }
  }

  private void validateRdsResponseFromSlotting(
      SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse,
      SlottingPalletRequestWithRdsPayLoad slottingPalletRequestWithRdsPayLoad) {
    String purchaseReferenceNumber =
        slottingPalletRequestWithRdsPayLoad.getRds().getContainerOrders().get(0).getPoNumber();
    Integer purchaseReferenceLineNumber =
        slottingPalletRequestWithRdsPayLoad.getRds().getContainerOrders().get(0).getPoLine();

    if (Objects.nonNull(slottingPalletResponseWithRdsResponse.getRds())) {
      if (CollectionUtils.isNotEmpty(slottingPalletResponseWithRdsResponse.getRds().getErrors())) {
        if (slottingPalletRequestWithRdsPayLoad.getRds().getContainerOrders().size() > 1) {
          LOGGER.error(
              "Error while receiving multiple split pallet containers in RDS from Smart Slotting");
          throw new ReceivingBadDataException(
              ExceptionCodes.NIM_RDS_MULTI_LABEL_GENERIC_ERROR,
              ReceivingConstants.NIM_RDS_MULTI_LABEL_GENERIC_ERROR);
        } else {
          Error error = slottingPalletResponseWithRdsResponse.getRds().getErrors().get(0);
          LOGGER.error(
              "Error while receiving containers in RDS from Smart Slotting for PO: {} and POL: {}; "
                  + "ErrorCode : {} and Error message: {}",
              purchaseReferenceNumber,
              purchaseReferenceLineNumber,
              error.getErrorCode(),
              error.getMessage());
          throw new ReceivingBadDataException(
              ExceptionCodes.RECEIVE_CONTAINERS_RDS_ERROR_MSG,
              String.format(
                  ReceivingConstants.RECEIVE_CONTAINERS_RDS_ERROR_MSG,
                  purchaseReferenceNumber,
                  purchaseReferenceLineNumber,
                  error.getMessage()),
              purchaseReferenceNumber,
              purchaseReferenceLineNumber,
              error.getMessage());
        }
      }

      if (CollectionUtils.isEmpty(slottingPalletResponseWithRdsResponse.getRds().getReceived())) {
        LOGGER.error(
            "Error while receiving containers in RDS from Smart Slotting for PO: {} and POL: {}",
            purchaseReferenceNumber,
            purchaseReferenceLineNumber);
        throw new ReceivingBadDataException(
            ExceptionCodes.RECEIVE_CONTAINERS_RDS_ERROR,
            String.format(
                ReceivingConstants.NO_CONTAINERS_RECEIVED_IN_RDS,
                purchaseReferenceNumber,
                purchaseReferenceLineNumber),
            purchaseReferenceNumber,
            purchaseReferenceLineNumber);
      }
    } else {
      LOGGER.error(
          "Error while receiving containers in Smart Slotting for PO: {} and POL: {}",
          purchaseReferenceNumber,
          purchaseReferenceLineNumber);
    }
  }

  /**
   * @param slottingPalletRequest
   * @param itemDataList
   * @param httpHeaders
   * @return SlottingPalletResponse
   * @throws SlottingRestApiClientException
   */
  @Counted(
      name = "getPrimeSlotForSplitPalletCount",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "getPrimeSlotForSplitPallet")
  @Timed(
      name = "getPrimeSlotForSplitPalletTimed",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "getPrimeSlotForSplitPallet")
  @ExceptionCounted(
      name = "getPrimeSlotForSplitPalletExceptionCount",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "getPrimeSlotForSplitPallet")
  public SlottingPalletResponse getPrimeSlotForSplitPallet(
      SlottingPalletRequest slottingPalletRequest,
      List<ItemData> itemDataList,
      HttpHeaders httpHeaders) {
    SlottingPalletResponse response = null;
    String uri = appConfig.getSlottingBaseUrl() + SLOTTING_ENDPOINT;
    String splitPalletRequestBody = gson.toJson(slottingPalletRequest);

    httpHeaders.set(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    LOGGER.info(
        "Invoking Smart slotting url: {} with request:{}, headers: {} to get prime slot information for items that are added in split pallet",
        uri,
        splitPalletRequestBody,
        httpHeaders);
    try {
      ResponseEntity<String> slottingResponseEntity =
          restConnector.exchange(
              uri,
              HttpMethod.POST,
              new HttpEntity<>(splitPalletRequestBody, httpHeaders),
              String.class);
      LOGGER.info(
          "Smart slotting response for getPrimeSlotForSplitPallet:{}",
          slottingResponseEntity.getBody());

      response = gson.fromJson(slottingResponseEntity.getBody(), SlottingPalletResponse.class);

      if (CollectionUtils.isNotEmpty(response.getLocations())) {
        SlottingDivertLocations location = response.getLocations().get(0);
        String locationType = location.getType();
        if (ERROR_TAG.equals(locationType)) {
          LOGGER.error(
              "Error response from Smart Slotting for post URI {}, response body = {}",
              uri,
              response);
          Object[] errorMessageValues = new Object[] {location.getCode(), location.getDesc()};
          if (location
              .getCode()
              .equalsIgnoreCase(ReceivingConstants.SLOTTING_PRIME_SLOT_NOT_FOUND)) {
            throw new ReceivingBadDataException(
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    errorMessageValues),
                String.valueOf(
                    slottingPalletRequest
                        .getContainerDetails()
                        .get(0)
                        .getContainerItemsDetails()
                        .get(0)
                        .getItemNbr()));
          } else if (location
              .getCode()
              .equalsIgnoreCase(
                  ReceivingConstants.SLOTTING_SPLIT_PALLET_PRIMES_COMPATIBLE_ERROR_CODE)) {
            List<String> primeSlots =
                itemDataList
                    .stream()
                    .parallel()
                    .map(ItemData::getPrimeSlot)
                    .collect(Collectors.toList());
            throw new ReceivingBadDataException(
                ExceptionCodes.PRIME_SLOT_NOT_COMPATIBLE_ERROR_FROM_SMART_SLOTTING,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    errorMessageValues),
                String.valueOf(
                    slottingPalletRequest
                        .getContainerDetails()
                        .get(0)
                        .getContainerItemsDetails()
                        .get(0)
                        .getItemNbr()),
                StringUtils.join(primeSlots, ", "));
          } else {
            throw new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    errorMessageValues),
                errorMessageValues);
          }
        }
      }
      return response;
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          splitPalletRequestBody,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      slottingErrorHandler.handle(e);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          splitPalletRequestBody,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      slottingErrorHandler.handle(e);
    }
    return response;
  }

  /**
   * Free Slot
   *
   * @return
   * @throws SlottingRestApiClientException
   */
  @Counted(
      name = "rxFreeSlotHitCount",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "rxFreeSlot")
  @Timed(
      name = "rxFreeSlotTimed",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "rxFreeSlot")
  @ExceptionCounted(
      name = "rxFreeSlotExceptionCount",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "rxFreeSlot")
  public boolean freeSlot(Long itemNumber, String slotId, HttpHeaders httpHeaders) {

    final String cId = httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString();
    String uri = appConfig.getSlottingBaseUrl() + FREE_SLOT;

    // This configuration is only or Test to acquire Slot from 32709 for RDS System.
    boolean isRxSmartSlottingTestOnlyEnabled =
        configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), "isRxSmartSlottingTestOnlyEnabled");
    if (isRxSmartSlottingTestOnlyEnabled) {
      httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32709");
    }

    httpHeaders.set(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set("itemNbr", String.valueOf(itemNumber));
    httpHeaders.set("slotId", slotId);

    LOGGER.info("Rx freeSlot for correlationId={}, url={},  headers={}", cId, uri, httpHeaders);
    try {
      ResponseEntity<String> freeSlotResponseEntity =
          restConnector.exchange(uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
      LOGGER.info(
          "Rx freeSlot for correlationId={}, response={}", cId, freeSlotResponseEntity.getBody());

      return freeSlotResponseEntity.getStatusCode().is2xxSuccessful();
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SMART_SLOT,
          String.format(ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_MSG, e.getMessage()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.SMART_SLOT_NOT_FOUND,
          String.format(ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_MSG, e.getMessage()));
    }
  }

  /** This api frees slot for the given trackingId and item information */
  @Counted(
      name = "rdcFreeSlotHitCount",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "rdcFreeSlot")
  @Timed(
      name = "rdcFreeSlotTimed",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "rdcFreeSlot")
  @ExceptionCounted(
      name = "rdcFreeSlotExceptionCount",
      level1 = "uwms-receiving",
      level2 = "slottingRestApiClient",
      level3 = "rdcFreeSlot")
  public boolean freeSlot(SlottingPalletRequest slottingPalletRequest, HttpHeaders httpHeaders) {

    final String cId = httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString();
    String uri = appConfig.getSlottingBaseUrl() + ReceivingConstants.SLOTTING_GET_SLOT_URL;

    httpHeaders.set(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    LOGGER.info(
        "Making a call to freeSlot api with correlationId={}, url={},  headers={}",
        cId,
        uri,
        httpHeaders);
    try {
      ResponseEntity<String> freeSlotResponseEntity =
          restConnector.exchange(
              uri,
              HttpMethod.POST,
              new HttpEntity<>(slottingPalletRequest, httpHeaders),
              String.class);
      LOGGER.info(
          "Triggered freeSlot api with correlationId={}, response={}",
          cId,
          freeSlotResponseEntity.getBody());

      return freeSlotResponseEntity.getStatusCode().is2xxSuccessful();
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SMART_SLOT,
          String.format(ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_MSG, e.getMessage()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.SMART_SLOT_NOT_FOUND,
          String.format(ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_MSG, e.getMessage()));
    }
  }

  public SlottingPalletResponse multipleSlotsFromSlotting(
      SlottingPalletRequest slotRequest, Boolean isOverboxingPallet) {
    SlottingPalletResponse palletSlotResponse = null;
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    StringBuilder slottingUrlBuilder =
        new StringBuilder(appConfig.getSlottingBaseUrl())
            .append(ReceivingConstants.SLOTTING_GET_SLOT_URL);
    httpHeaders.set(
        ReceivingConstants.HEADER_FEATURE_TYPE, ReceivingConstants.FEATURE_PALLET_RECEIVING);
    httpHeaders.set(
        ReceivingConstants.ACCEPT_PARTIAL_RESERVATION, ReceivingConstants.IS_PARTIAL_RESERVATION);
    httpHeaders.set(ReceivingConstants.OVERBOXING_REQUIRED, String.valueOf(isOverboxingPallet));
    try {
      LOGGER.info(
          "Slotting request for [slot number:{}]", ReceivingUtils.stringfyJson(slotRequest));
      ResponseEntity<SlottingPalletResponse> slotResponseResponseEntity =
          restConnector.post(
              slottingUrlBuilder.toString(),
              gsonDateAdaptor.toJson(slotRequest),
              httpHeaders,
              SlottingPalletResponse.class);
      palletSlotResponse = slotResponseResponseEntity.getBody();
      LOGGER.info(
          ReceivingConstants.RESTUTILS_INFO_MESSAGE,
          slottingUrlBuilder,
          ReceivingUtils.stringfyJson(slotRequest),
          ReceivingUtils.stringfyJson(palletSlotResponse));
    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == HttpStatus.CONFLICT.value()) {
        LOGGER.error(
            ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
            slottingUrlBuilder,
            slotRequest,
            e.getResponseBodyAsString(),
            ExceptionUtils.getStackTrace(e));
        throw new ReceivingInternalException(ExceptionCodes.INVALID_SMART_SLOT, NO_SLOTS_AVAILABLE);
      }
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          slottingUrlBuilder,
          slotRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_SLOTTING_REQ,
          String.format(
              SLOTTING_BAD_RESPONSE_ERROR_MSG, e.getRawStatusCode(), e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          SLOTTING_NOT_ACCESSIBLE,
          String.format(SLOTTING_RESOURCE_RESPONSE_ERROR_MSG, e.getMessage()));
    }
    return palletSlotResponse;
  }

  public void cancelPalletMoves(String palletId, HttpHeaders httpHeaders) {
    final String cId = httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString();
    String uri =
        appConfig.getSlottingBaseUrl() + ReceivingConstants.SLOTTING_MOVES_CANCEL_PALLET + palletId;

    httpHeaders.set(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(
        ReceivingConstants.USER_ID_HEADER_KEY,
        httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY.toLowerCase()).get(0));

    LOGGER.info(
        "Making a call to cancelPalletMoves api with correlationId={}, url={},  headers={}",
        cId,
        uri,
        httpHeaders);
    try {
      ResponseEntity<String> freeSlotResponseEntity =
          restConnector.exchange(
              uri, HttpMethod.PATCH, new HttpEntity<>(httpHeaders), String.class);
      LOGGER.info(
          "Triggered cancelPalletMoves api with correlationId={}, response={}",
          cId,
          freeSlotResponseEntity.getBody());
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SMART_SLOT,
          String.format(ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_MSG, e.getMessage()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.SMART_SLOT_NOT_FOUND,
          String.format(ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_MSG, e.getMessage()));
    }
  }

  public void adjustMovesQty(
      String palletId,
      SlottingInstructionUpdateRequest slottingInstructionUpdateRequest,
      HttpHeaders httpHeaders) {
    final String cId =
        httpHeaders.containsKey(ReceivingConstants.CORRELATION_ID_HEADER_KEY)
            ? httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString()
            : UUID.randomUUID().toString();

    String uri =
        appConfig.getSlottingBaseUrl() + ReceivingConstants.SLOTTING_MOVES_INSTRUCTION + palletId;

    httpHeaders.set(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(
        ReceivingConstants.USER_ID_HEADER_KEY,
        httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY.toLowerCase()).get(0));

    String request = gsonDateAdaptor.toJson(slottingInstructionUpdateRequest);
    LOGGER.info(
        "Making a call to updatePalletMoves api with correlationId={}, url={}, request={},  headers={}",
        cId,
        uri,
        request,
        httpHeaders);
    try {
      ResponseEntity<String> freeSlotResponseEntity =
          restConnector.exchange(
              uri, HttpMethod.PATCH, new HttpEntity<>(request, httpHeaders), String.class);
      LOGGER.info(
          "Triggered updatePalletMoves api with correlationId={}, response={}",
          cId,
          freeSlotResponseEntity.getBody());
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SMART_SLOT,
          String.format(ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_MSG, e.getMessage()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.SMART_SLOT_NOT_FOUND,
          String.format(ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_MSG, e.getMessage()));
    }
  }
}
