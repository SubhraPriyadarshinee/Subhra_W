package com.walmart.move.nim.receiving.acc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.exception.GDMServiceUnavailableException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

/** @author g0k0072 Delivery service which can retry rest calls based on policy */
public class RetryableDeliveryService extends DeliveryService {

  private static Logger LOGGER = LoggerFactory.getLogger(RetryableDeliveryService.class);

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  @Autowired private ObjectMapper objectMapper;

  @Override
  protected ResponseEntity<SsccScanResponse> getSsccScanResponseResponseEntity(
      HttpHeaders headers, String url) {
    return restConnector.exchange(
        url, HttpMethod.GET, new HttpEntity<>(headers), SsccScanResponse.class);
  }

  @Override
  public String findDeliveryDocument(long deliveryNumber, String upcNumber, HttpHeaders headers)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }

  @Override
  public Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }

  @Override
  public String getPOInfoFromDelivery(
      long deliveryNumber, String purchaseReferenceNumber, HttpHeaders headers)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, Long.toString(deliveryNumber));

    Map<String, String> queryParameters = new HashMap<>();
    queryParameters.put(ReceivingConstants.PO_NUMBER, purchaseReferenceNumber);

    String url =
        ReceivingUtils.replacePathParamsAndQueryParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI,
                pathParams,
                queryParameters)
            .toString();
    String response = getDeliveryDocumentsByGtin(url, headers, simpleRestConnector);
    return response;
  }

  /**
   * Gets delivery details based on URL in delivery message and retries in case of failure
   *
   * @param url url which needs to be hit for the delivery document
   * @param deliveryNumber the delivery number
   * @return {@link DeliveryDetails} delivery details based in v2 contract
   */
  @Timed(
      name = "getDeliveryDetailsTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @ExceptionCounted(
      name = "getDeliveryDetailsExceptionCount",
      cause = GDMServiceUnavailableException.class,
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @Override
  public DeliveryDetails getDeliveryDetails(String url, Long deliveryNumber)
      throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put(ReceivingConstants.INCLUDE_ACTIVE_CHANNEL_METHODS, Boolean.TRUE.toString());

    String uri = ReceivingUtils.replacePathParamsAndQueryParams(url, null, queryParams).toString();

    ResponseEntity<String> deliveryResponseEntity = null;
    try {
      deliveryResponseEntity =
          restConnector.exchange(uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          ReceivingException.DELIVERY_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ExceptionCodes.DELIVERY_NOT_FOUND);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          ReceivingException.GDM_SERVICE_DOWN,
          HttpStatus.SERVICE_UNAVAILABLE,
          ExceptionCodes.GDM_NOT_ACCESSIBLE);
    }
    if (Objects.isNull(deliveryResponseEntity) || !deliveryResponseEntity.hasBody()) {
      LOGGER.error(ReceivingConstants.RESTUTILS_INFO_MESSAGE, uri, "", "");
      throw new ReceivingException(
          ReceivingException.DELIVERY_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ExceptionCodes.DELIVERY_NOT_FOUND);
    }
    DeliveryDetails deliveryDetails =
        JacksonParser.convertJsonToObject(deliveryResponseEntity.getBody(), DeliveryDetails.class);
    LOGGER.info(
        ReceivingConstants.RESTUTILS_INFO_MESSAGE,
        uri,
        deliveryNumber,
        deliveryResponseEntity.getBody());
    return deliveryDetails;
  }

  @Override
  public DeliveryDoorSummary getDoorStatus(String doorNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public List<DeliveryDocument> findDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }
}
