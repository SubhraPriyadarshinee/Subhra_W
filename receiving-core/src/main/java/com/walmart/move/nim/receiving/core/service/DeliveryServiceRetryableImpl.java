package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.DELIVERY_HEADER_DETAILS_NOT_FOUND_BY_DELIVERY_NUMBERS;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_GET_DELIVERY_BY_URI;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_GET_DELIVERY_ERROR;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_SERVICE_DOWN;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.GDM_ERROR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.GdmPOLineResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsPageRequest;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsPageResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsSearchFields;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.net.URI;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class DeliveryServiceRetryableImpl extends DeliveryService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryServiceRetryableImpl.class);

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  /**
   * This method fetches details from GDM based on delivery and UPC
   *
   * @param deliveryNumber
   * @param headers
   * @return String
   * @throws ReceivingException
   */
  @Timed(
      name = "findDeliveryDocumentTimed",
      level1 = "uwms-receiving",
      level2 = "retryDeliveryService",
      level3 = "findDeliveryDocument")
  @ExceptionCounted(
      name = "findDeliveryDocumentExceptionCount",
      level1 = "uwms-receiving",
      level2 = "retryDeliveryService",
      level3 = "findDeliveryDocument")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.REST,
      executionFlow = "GDM(Retry)-GetByUPC",
      externalCall = true)
  @Override
  public String findDeliveryDocument(long deliveryNumber, String upcNumber, HttpHeaders headers)
      throws ReceivingException {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, Long.toString(deliveryNumber));
    pathParams.put(ReceivingConstants.UPC_NUMBER, upcNumber);
    String url =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_DOCUMENT_SEARCH_URI, pathParams)
            .toString();
    return getDeliveryDocumentsByGtin(url, headers, retryableRestConnector);
  }

  @Override
  public Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }

  /**
   * This method fetches poline details from GDM
   *
   * @param deliveryNumber
   * @param poNumber
   * @param poLineNumber
   * @param headers
   * @return GdmPOLineResponse
   * @throws ReceivingException
   */
  @Timed(
      name = "getPOLineInfoTimed",
      level1 = "uwms-receiving",
      level2 = "retryDeliveryService",
      level3 = "getPOLineInfo")
  @ExceptionCounted(
      name = "getPOLineInfoExceptionCount",
      level1 = "uwms-receiving",
      level2 = "retryDeliveryService",
      level3 = "getPOLineInfo")
  @Override
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.REST,
      executionFlow = "GDM-GetPOPOL",
      externalCall = true)
  public GdmPOLineResponse getPOLineInfoFromGDM(
      String deliveryNumber, String poNumber, Integer poLineNumber, HttpHeaders headers)
      throws ReceivingException {

    String gdmBaseUri = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI;

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put(ReceivingConstants.PO_NUMBER, poNumber);
    queryParams.put(ReceivingConstants.PO_LINE_NUMBER, Integer.toString(poLineNumber));
    queryParams.put(ReceivingConstants.INCLUDE_ACTIVE_CHANNEL_METHODS, Boolean.TRUE.toString());

    String uri =
        ReceivingUtils.replacePathParamsAndQueryParams(gdmBaseUri, pathParams, queryParams)
            .toString();

    String response = getDeliveryDocumentsByGtin(uri, headers, retryableRestConnector);
    return gson.fromJson(response, GdmPOLineResponse.class);
  }

  public List<DeliveryDocument> getDeliveryDocumentsByDeliveryAndGtin(
      long deliveryNumber, String orderableGTIN, HttpHeaders httpHeaders) {
    String deliveryDocumentResponseString;
    try {
      deliveryDocumentResponseString =
          findDeliveryDocument(deliveryNumber, orderableGTIN, httpHeaders);
    } catch (ReceivingException receivingException) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.PO_LINE_NOT_FOUND,
          String.format(ReceivingException.PO_POLINE_NOT_FOUND, orderableGTIN, deliveryNumber));
    }
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(deliveryDocumentResponseString, DeliveryDocument[].class));
    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.PO_LINE_NOT_FOUND,
          String.format(ReceivingException.PO_POLINE_NOT_FOUND, orderableGTIN, deliveryNumber));
    }
    return deliveryDocuments;
  }

  /**
   * This method call GDM delivery header details API to get delivery status for the given
   * deliveries. GDM currently sets max page offset limit as 100 deliveries per request.
   *
   * @param deliveryNumbers
   * @return
   * @throws ReceivingException
   */
  @TimeTracing(
      component = AppComponent.CORE,
      flow = "getDeliveryHeaderDetailsByDeliveryNumbers",
      externalCall = true,
      type = com.walmart.move.nim.receiving.core.advice.Type.REST)
  public GdmDeliveryHeaderDetailsPageResponse getDeliveryHeaderDetailsByDeliveryNumbers(
      List<Long> deliveryNumbers) throws ReceivingException {

    int pageNumber = 0;
    GdmDeliveryHeaderDetailsPageResponse gdmDeliveryHeaderDetailsPaginationResponse = null;
    GdmDeliveryHeaderDetailsPageRequest gdmDeliveryHeaderDetailsPageRequest =
        GdmDeliveryHeaderDetailsPageRequest.builder()
            .number(pageNumber)
            .size(ReceivingConstants.DELIVERY_NUMBERS_MAX_PAGE_OFFSET)
            .build();
    GdmDeliveryHeaderDetailsSearchFields gdmDeliveryHeaderDetailsSearchFields =
        GdmDeliveryHeaderDetailsSearchFields.builder()
            .deliveryNumbers(deliveryNumbers)
            .page(gdmDeliveryHeaderDetailsPageRequest)
            .build();

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.replace(
        CONTENT_TYPE,
        Collections.singletonList(GDM_DELIVERY_HEADER_DETAILS_BY_DELIVERY_NUMBERS_ACCEPT_TYPE));
    String url = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_DELIVERY_HEADERS_DETAILS_SEARCH;

    try {
      ResponseEntity<String> gdmHeaderDetailsResponse =
          retryableRestConnector.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(gdmDeliveryHeaderDetailsSearchFields), httpHeaders),
              String.class);

      gdmDeliveryHeaderDetailsPaginationResponse =
          gson.fromJson(
              gdmHeaderDetailsResponse.getBody(), GdmDeliveryHeaderDetailsPageResponse.class);

      if (nonNull(gdmDeliveryHeaderDetailsPaginationResponse)
          && CollectionUtils.isEmpty(gdmDeliveryHeaderDetailsPaginationResponse.getData())) {
        throw new ReceivingException(
            String.format(DELIVERY_HEADER_DETAILS_NOT_FOUND_BY_DELIVERY_NUMBERS, deliveryNumbers),
            NOT_FOUND,
            GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
      }

      if (nonNull(gdmDeliveryHeaderDetailsPaginationResponse)
          && nonNull(gdmDeliveryHeaderDetailsPaginationResponse.getPage())
          && gdmDeliveryHeaderDetailsPaginationResponse.getPage().getNumberOfElements()
              != deliveryNumbers.size()) {
        List<Long> missingDeliveries = new ArrayList<>();
        List<GdmDeliveryHeaderDetailsResponse> gdmDeliveriesList =
            gdmDeliveryHeaderDetailsPaginationResponse.getData();

        for (Long deliveryNumber : deliveryNumbers) {
          Optional<GdmDeliveryHeaderDetailsResponse> deliveryNumberExists =
              gdmDeliveriesList
                  .stream()
                  .parallel()
                  .filter(gdmDelivery -> gdmDelivery.getDeliveryNumber().equals(deliveryNumber))
                  .findAny();
          if (!deliveryNumberExists.isPresent()) {
            missingDeliveries.add(deliveryNumber);
          }
        }
        LOGGER.info("Delivery numbers not available in GDM are: {}", missingDeliveries);
      }
    } catch (RestClientResponseException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          EMPTY,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          String.format(DELIVERY_HEADER_DETAILS_NOT_FOUND_BY_DELIVERY_NUMBERS, deliveryNumbers),
          NOT_FOUND,
          GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          String.format(DELIVERY_HEADER_DETAILS_NOT_FOUND_BY_DELIVERY_NUMBERS, deliveryNumbers),
          url,
          EMPTY,
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          GDM_SERVICE_DOWN, INTERNAL_SERVER_ERROR, GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
    }
    return gdmDeliveryHeaderDetailsPaginationResponse;
  }

  /**
   * This method gets delivery details based on the provided GDM endpoint
   *
   * @param uri
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @Timed(
      name = "getDeliveryByURITimed",
      level1 = "uwms-receiving",
      level2 = "retryDeliveryService",
      level3 = "getDeliveryByURI")
  @ExceptionCounted(
      name = "getDeliveryByURIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "retryDeliveryService",
      level3 = "getDeliveryByURI")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.REST,
      executionFlow = "GDM-getDeliveryByURI",
      externalCall = true)
  public String getDeliveryByURI(URI uri, HttpHeaders httpHeaders)
      throws ReceivingException, ReceivingInternalException {
    String gdmDeliveryURI = uri.toString();
    try {
      LOGGER.info("Get delivery details for delivery URL: {}", gdmDeliveryURI);
      ResponseEntity<String> deliveryResponse =
          retryableRestConnector.exchange(
              gdmDeliveryURI, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);

      if (isNull(deliveryResponse) || isBlank(deliveryResponse.getBody())) {
        LOGGER.error("Get delivery details null response");
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(GDM_GET_DELIVERY_ERROR)
                .errorCode(GDM_GET_DELIVERY_BY_URI)
                .errorKey(ExceptionCodes.GDM_GET_DELIVERY_ERROR)
                .build();
        throw ReceivingException.builder()
            .httpStatus(NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }

      LOGGER.info("Get delivery details response: {}", deliveryResponse.getBody());
      return deliveryResponse.getBody();

    } catch (RestClientResponseException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          gdmDeliveryURI,
          EMPTY,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(GDM_GET_DELIVERY_ERROR)
              .errorCode(GDM_GET_DELIVERY_BY_URI)
              .errorKey(ExceptionCodes.GDM_GET_DELIVERY_ERROR)
              .build();
      throw ReceivingException.builder().httpStatus(NOT_FOUND).errorResponse(errorResponse).build();

    } catch (ResourceAccessException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          gdmDeliveryURI,
          EMPTY,
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(GDM_ERROR, ReceivingConstants.GDM_SERVICE_DOWN);
    }
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
