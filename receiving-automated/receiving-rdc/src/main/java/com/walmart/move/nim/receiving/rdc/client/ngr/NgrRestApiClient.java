package com.walmart.move.nim.receiving.rdc.client.ngr;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getOsdrDefaultSummaryResponse;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.HazmatVerificationRequest;
import com.walmart.move.nim.receiving.rdc.model.RdcItemUpdateRequest;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import javax.annotation.Resource;
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
 * Rest Client for NGR Services
 *
 * @author s1b041i
 */
@Component
public class NgrRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(NgrRestApiClient.class);

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private Gson gson;

  /**
   * Fetch Delivery Receipts from Receiving-Load. If delivery is not found, returns empty receipts
   *
   * @param deliveryNumber
   * @param httpHeaders
   * @return OsdrSummary
   * @throws ReceivingException
   */
  @Timed(
      name = "deliveryNGRReceiptsTimed",
      level1 = "uwms-receiving",
      level2 = "ngrRestApiClient",
      level3 = "getDeliveryReceipts")
  @ExceptionCounted(
      name = "deliveryNGRReceiptsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "ngrRestApiClient",
      level3 = "getDeliveryReceipts")
  public OsdrSummary getDeliveryReceipts(Long deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    String siteId = StringUtils.leftPad(TenantContext.getFacilityNum().toString(), 5, "0");
    String baseUrl =
        String.format(
            RdcUtils.getExternalServiceBaseUrlByTenant(rdcManagedConfig.getNgrBaseUrl()), siteId);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.NGR_TENANT_MAPPING_ENABLED,
        false)) {
      siteId = RdcUtils.getMappedTenant(baseUrl);
    }
    String uri = String.format(RdcConstants.DELIVERY_RECEIPTS, baseUrl, siteId, deliveryNumber);
    LOGGER.info("Get Delivery Receipts URI = {}", uri);
    try {
      ResponseEntity<String> receiptsResponseEntity =
          retryableRestConnector.exchange(
              uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
      LOGGER.info(
          "Get Delivery Receipts URI = {}, response body {}",
          uri,
          receiptsResponseEntity.getBody());
      return gson.fromJson(receiptsResponseEntity.getBody(), OsdrSummary.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          StringUtils.EMPTY,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      return getOsdrDefaultSummaryResponse(deliveryNumber);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          StringUtils.EMPTY,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      return getOsdrDefaultSummaryResponse(deliveryNumber);
    }
  }

  public void updateHazmatVerificationTsInItemCache(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {

    String siteId = StringUtils.leftPad(TenantContext.getFacilityNum().toString(), 5, "0");
    String baseUrl =
        String.format(
            RdcUtils.getExternalServiceBaseUrlByTenant(rdcManagedConfig.getNgrBaseUrl()), siteId);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.NGR_TENANT_MAPPING_ENABLED,
        false)) {
      siteId = RdcUtils.getMappedTenant(baseUrl);
    }
    String uri =
        baseUrl + String.format(RdcConstants.HAZMAT_VERIFICATION_TS_UPDATE_URL, siteId, siteId);
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    HazmatVerificationRequest hazmatVerificationRequest =
        HazmatVerificationRequest.builder()
            .itemNumber(deliveryDocumentLine.getItemNbr().toString())
            .itemUPC(deliveryDocumentLine.getItemUpc())
            .caseUPC(deliveryDocumentLine.getCaseUpc())
            .deliveryNumber(String.valueOf(deliveryDocument.getDeliveryNumber()))
            .purchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber())
            .build();

    LOGGER.info(
        "Calling item cache api with url = {} and request body = {} ",
        uri,
        hazmatVerificationRequest);
    ResponseEntity<String> responseEntity = null;
    try {
      responseEntity =
          retryableRestConnector.exchange(
              uri,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(hazmatVerificationRequest), httpHeaders),
              String.class);
      LOGGER.info(
          "Successfully received response from item cache api with status = {}",
          responseEntity.getStatusCode());
    } catch (RestClientResponseException e) {
      String errorCode = RdcConstants.ITEM_CACHE_API_INTERNAL_SERVER_ERROR;
      if (HttpStatus.BAD_REQUEST.value() == e.getRawStatusCode()) {
        errorCode = RdcConstants.INVALID_ITEM_CACHE_REQUEST;
      }
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          StringUtils.EMPTY,
          e.getResponseBodyAsString(),
          errorCode);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          StringUtils.EMPTY,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
    }
  }

  /**
   * Update the Item attributes in NGR/RDS
   *
   * @param itemOverrideRequest
   * @param httpHeaders
   */
  @Timed(
      name = "updateDeliveryItemTimed",
      level1 = "uwms-receiving-api",
      level2 = "ngrRestApiClient",
      level3 = "updateItemProperties")
  @ExceptionCounted(
      name = "updateDeliveryItemExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "ngrRestApiClient",
      level3 = "updateItemProperties")
  public void updateItemProperties(
      ItemOverrideRequest itemOverrideRequest, HttpHeaders httpHeaders) {
    RdcItemUpdateRequest rdcItemUpdateRequest = convertToRdcItemUpdateRequest(itemOverrideRequest);
    httpHeaders.add(ReceivingConstants.REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    LOGGER.info("Rdc item update request: {}", rdcItemUpdateRequest);
    String siteId = RdcUtils.getSiteId();
    String baseUrl =
        String.format(
            RdcUtils.getExternalServiceBaseUrlByTenant(rdcManagedConfig.getNgrBaseUrl()), siteId);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.NGR_TENANT_MAPPING_ENABLED,
        false)) {
      siteId = RdcUtils.getMappedTenant(baseUrl);
    }
    String loadUrl = String.format(RdcConstants.ITEM_UPDATE_URL_FOR_ITEM_CACHE, baseUrl, siteId);
    HttpEntity httpEntity = new HttpEntity(gson.toJson(rdcItemUpdateRequest), httpHeaders);
    LOGGER.info(
        "Request to item cache endpoint with url:{} and request:{} for updating temporary pack type and handling code",
        loadUrl,
        rdcItemUpdateRequest);
    try {
      ResponseEntity<String> responseEntity =
          retryableRestConnector.exchange(loadUrl, HttpMethod.POST, httpEntity, String.class);
      LOGGER.info(
          "Response from item cache with url:{} and response:{}",
          loadUrl,
          responseEntity.getBody());
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          loadUrl,
          httpEntity,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.ITEM_CACHE_GENERIC_ERROR,
          ReceivingConstants.ERROR_WHILE_UPDATING_ITEM_PROPERTIES_IN_RDS);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          loadUrl,
          httpEntity,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.ITEM_CACHE_INTERNAL_SERVER_ERROR,
          ReceivingConstants.UNABLE_TO_UPDATE_ITEM_DETAILS_IN_RDS);
    }
  }

  private RdcItemUpdateRequest convertToRdcItemUpdateRequest(
      ItemOverrideRequest itemOverrideRequest) {
    RdcItemUpdateRequest rdcItemCacheRequest =
        new RdcItemUpdateRequest()
            .builder()
            .itemNumber(itemOverrideRequest.getItemNumber())
            .delivery(String.valueOf(itemOverrideRequest.getDeliveryNumber()))
            .temporaryPackCode(itemOverrideRequest.getTemporaryPackTypeCode())
            .temporaryHandlingType(itemOverrideRequest.getTemporaryHandlingMethodCode())
            .purchaseReferenceNumber(itemOverrideRequest.getPurchaseReferenceNumber())
            .purchaseReferenceLineNumber(itemOverrideRequest.getPurchaseReferenceLineNumber())
            .build();
    return rdcItemCacheRequest;
  }
}
