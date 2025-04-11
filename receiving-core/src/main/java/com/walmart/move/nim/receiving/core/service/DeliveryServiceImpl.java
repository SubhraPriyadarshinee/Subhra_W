package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.GDMServiceUnavailableException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.DeliveryHeaderSearchDetails;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.PageDetails;
import com.walmart.move.nim.receiving.core.model.delivery.DeliveryDetailsCriteria;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchByStatusRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.RequestBodyWithDeliveryNumPoTypeAndLegacyType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * This class is responsible to implement unimplemented method using SimpleRestConnector. Earlier we
 * used RestUtil now since it is deprecated we are slowly moving towards RestConnector.
 *
 * @author a0b02ft
 */
@Service(ReceivingConstants.DELIVERY_SERVICE)
public class DeliveryServiceImpl extends DeliveryService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryServiceImpl.class);

  @Resource(name = "restConnector")
  private RestConnector simpleRestConnector;

  @ManagedConfiguration AppConfig appConfig;
  @Autowired TenantSpecificConfigReader tenantSpecificConfig;

  /**
   * This method is fetch PO/PO line as response from GDM. If GDM call fails for IO Exception or any
   * 5xx error, then it will retry for a maximum value defined in CCM.
   *
   * @param deliveryNumber delivery number
   * @param upcNumber upc number
   * @param headers http headers
   * @return PO/PO line as response
   * @throws ReceivingException Exception thrown in the case of GDM call failure
   */
  @Timed(
      name = "idmCallTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @ExceptionCounted(
      name = "idmCallExceptionCount",
      cause = GDMServiceUnavailableException.class,
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @Override
  public String findDeliveryDocument(long deliveryNumber, String upcNumber, HttpHeaders headers)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, Long.toString(deliveryNumber));
    pathParams.put(ReceivingConstants.UPC_NUMBER, upcNumber);
    String url =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_DOCUMENT_SEARCH_URI, pathParams)
            .toString();
    return getDeliveryDocumentsByGtin(url, headers, simpleRestConnector);
  }

  @Override
  public Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }

  /** Get all poLine for PO for given delivery number */
  @Timed(
      name = "getPOInfoFromDeliveryTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @ExceptionCounted(
      name = "getPOInfoFromDeliveryExceptionCount",
      cause = GDMServiceUnavailableException.class,
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
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
   * @param deliveryNumbers
   * @param channelType
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @Timed(
      name = "getDeliveryDocumentByPOLegacyTypeTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @ExceptionCounted(
      name = "getDeliveryDocumentByPOLegacyTypeExceptionCounted",
      cause = GDMServiceUnavailableException.class,
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @Override
  public String getDeliveryDocumentByPOChannelType(
      List<Long> deliveryNumbers, List<String> channelType, HttpHeaders httpHeaders)
      throws ReceivingException {
    HttpHeaders headers = ReceivingUtils.getHeaderForGDMV3API();
    ResponseEntity<String> response;
    RequestBodyWithDeliveryNumPoTypeAndLegacyType requestBody =
        new RequestBodyWithDeliveryNumPoTypeAndLegacyType();
    requestBody.setDeliveryNumbers(deliveryNumbers);
    requestBody.setChannels(channelType);
    requestBody.setPageSize(ReceivingConstants.DEFAULT_PAGESIZE);

    String gdmUrl = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_V3_DOCUMENT_SEARCH_BY_POTYPE;

    HttpEntity httpEntity = new HttpEntity<>(gson.toJson(requestBody), headers);

    try {
      response = simpleRestConnector.exchange(gdmUrl, HttpMethod.POST, httpEntity, String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          gdmUrl,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.CREATE_INSTRUCTION_NO_PO_LINE)
              .errorCode(ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE)
              .errorKey(ExceptionCodes.CREATE_INSTRUCTION_NO_PO_LINE)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.NOT_FOUND)
          .errorResponse(errorResponse)
          .build();
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          gdmUrl,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.GDM_SERVICE_DOWN)
              .errorCode(ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE)
              .errorKey(ExceptionCodes.GDM_SERVICE_DOWN)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }

    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.GDM_SERVICE_DOWN)
                .errorCode(ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE)
                .errorKey(ExceptionCodes.GDM_SERVICE_DOWN)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      } else {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.DELIVERY_NOT_FOUND)
                .errorCode(ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE)
                .errorKey(ExceptionCodes.NO_DELIVERY_FOUND)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }
    } else {
      if (response.getBody().isEmpty()) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.DELIVERY_NOT_FOUND)
                .errorCode(ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE)
                .errorKey(ExceptionCodes.NO_DELIVERY_FOUND)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }
    }
    return response.getBody();
  }

  @Timed(
      name = "getDeliveryDocumentBySearchCriteriaTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @ExceptionCounted(
      name = "getDeliveryDocumentBySearchCriteriaExceptionCounted",
      cause = GDMServiceUnavailableException.class,
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @Override
  public String getDeliveryDocumentBySearchCriteria(String searchCriteria)
      throws ReceivingException {
    HttpHeaders headers = ReceivingUtils.getHeaderForGDMV3SearchAPI();
    HttpEntity<String> httpEntity = new HttpEntity<>(searchCriteria, headers);
    ResponseEntity<String> response =
        simpleRestConnector.exchange(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_SEARCH_HEADER_URI,
            HttpMethod.POST,
            httpEntity,
            String.class);

    handleErrorFromGDM(response);
    return response.getBody();
  }

  public String fetchDeliveriesByStatus(String facilityNumber, int pageNumber)
      throws ReceivingException {
    List<String> deliveryStatusList = new ArrayList<>();
    deliveryStatusList.add(DeliveryStatus.WRK.name());
    String response =
        fetchDeliveriesByStatus(
            deliveryStatusList, Collections.emptyList(), facilityNumber, pageNumber);
    return response;
  }

  public String fetchDeliveriesByStatus(
      List<String> deliveryStatusList,
      List<String> statusReasonCodeList,
      String facilityNumber,
      int pageNumber)
      throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, facilityNumber);
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, ReceivingConstants.COUNTRY_CODE_US);
    httpHeaders.set(
        ReceivingConstants.CONTENT_TYPE,
        ReceivingConstants.GDM_SEARCH_DELIVERY_HEADER_CONTENT_TYPE);
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, UUID.randomUUID().toString());

    ResponseEntity<String> response = null;

    int pageSize = appConfig.getAutoCompleteDeliveryPageSize();
    GdmDeliverySearchByStatusRequest deliverySearchByStatusRequest =
        GdmDeliverySearchByStatusRequest.builder()
            .criteria(
                DeliveryHeaderSearchDetails.builder()
                    .deliveryStatusList(deliveryStatusList)
                    .statusReasonCodes(statusReasonCodeList)
                    .build())
            .page(PageDetails.builder().size(pageSize).number(pageNumber).build())
            .build();

    HttpEntity httpEntity =
        new HttpEntity<>(gson.toJson(deliverySearchByStatusRequest), httpHeaders);

    response =
        simpleRestConnector.exchange(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_SEARCH_HEADER_URI,
            HttpMethod.POST,
            httpEntity,
            String.class);

    handleErrorFromGDM(response);

    return response.getBody();
  }

  @Override
  public String fetchDeliveriesByStatusAndUpc(
          List<String> deliveryStatusList, String upcNumber, String facilityNumber, int pageNumber)
      throws ReceivingException {
    HttpHeaders httpHeaders =
        getHttpHeadersForDeliverySearch(
            facilityNumber, ReceivingConstants.GDM_SEARCH_CONSOLIDATED_DELIVERY_DETAILS_TYPE);

    ResponseEntity<String> response = null;

    GdmDeliverySearchByStatusRequest deliverySearchByStatusRequest =
        GdmDeliverySearchByStatusRequest.builder()
            .criteria(
                DeliveryHeaderSearchDetails.builder()
                    .deliveryStatusList(deliveryStatusList)
                    .upcs(Arrays.asList(upcNumber))
                    .build())
            .build();

    HttpEntity httpEntity =
        new HttpEntity<>(gson.toJson(deliverySearchByStatusRequest), httpHeaders);

    response =
        simpleRestConnector.exchange(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_SEARCH_HEADER_URI,
            HttpMethod.POST,
            httpEntity,
            String.class);
    handleErrorFromGDM(response);
    return response.getBody();
  }

  @Override
  public String fetchDeliveriesByStatusUpcAndPoNumber(
          List<String> deliveryStatusList, String upcNumber, String facilityNumber, int pageNumber, List<String> poNumberList)
          throws ReceivingException {
    HttpHeaders httpHeaders =
            getHttpHeadersForDeliverySearch(
                    facilityNumber, ReceivingConstants.GDM_SEARCH_CONSOLIDATED_DELIVERY_DETAILS_TYPE);

    ResponseEntity<String> response = null;

    GdmDeliverySearchByStatusRequest deliverySearchByStatusRequest =
            GdmDeliverySearchByStatusRequest.builder()
                    .criteria(
                            DeliveryHeaderSearchDetails.builder()
                                    .deliveryStatusList(deliveryStatusList)
                                    .upcs(Arrays.asList(upcNumber))
                                    .poNumbers(poNumberList)
                                    .build())
                    .build();

    HttpEntity httpEntity =
            new HttpEntity<>(gson.toJson(deliverySearchByStatusRequest), httpHeaders);

    LOGGER.info("API endpoint called: {}, correlation id: {}", appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_SEARCH_HEADER_URI, TenantContext.getCorrelationId());
    LOGGER.info("Headers and Request Body for GDM Request to fetch delivery documents: {}, correlation id: {}", httpEntity, TenantContext.getCorrelationId());

    response =
            simpleRestConnector.exchange(
                    appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_SEARCH_HEADER_URI,
                    HttpMethod.POST,
                    httpEntity,
                    String.class);

    handleErrorFromGDM(response);
    LOGGER.info("Delivery documents from GDM: {}, correlation id: {}", response.getBody(), TenantContext.getCorrelationId());
    return response.getBody();
  }

  @Override
  public String fetchDeliveries(
      List<Long> deliveryList, String facilityNumber, String countryCode, int pageNumber)
      throws ReceivingException {
    HttpHeaders httpHeaders =
        getHttpHeadersForDeliverySearch(
            facilityNumber, ReceivingConstants.GDM_DOCUMENT_GET_BY_POLEGACY_V3_CONTENT_TYPE);

    ResponseEntity<String> response = null;

    int pageSize = appConfig.getAutoCompleteDeliveryPageSize();

    DeliveryDetailsCriteria deliverySearchByStatusRequest =
        DeliveryDetailsCriteria.builder()
            .deliveryNumbers(deliveryList)
            .pageSize(pageSize)
            .pageNumber(0)
            .build();

    HttpEntity httpEntity =
        new HttpEntity<>(gson.toJson(deliverySearchByStatusRequest), httpHeaders);

    response =
        simpleRestConnector.exchange(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_SEARCH_HEADER_URI,
            HttpMethod.POST,
            httpEntity,
            String.class);
    handleErrorFromGDM(response);
    return response.getBody();
  }

  private static HttpHeaders getHttpHeadersForDeliverySearch(
      String facilityNumber, String contentType) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, facilityNumber);
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, ReceivingConstants.COUNTRY_CODE_US);
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, contentType);
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, UUID.randomUUID().toString());
    return httpHeaders;
  }

  private static void handleErrorFromGDM(ResponseEntity<String> response)
      throws ReceivingException {
    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (response.getStatusCode().is5xxServerError()) {
        throw new ReceivingException(
            ReceivingException.GDM_SERVICE_DOWN,
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.GDM_GET_DELIVERY_BY_STATUS_CODE_ERROR);
      } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new ReceivingException(
            ReceivingException.GDM_GET_DELIVERY_BY_STATUS_CODE_ERROR, HttpStatus.NOT_FOUND);
      } else if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
        throw new ReceivingException(
            ReceivingException.BAD_REQUEST,
            HttpStatus.BAD_REQUEST,
            ReceivingException.GDM_GET_DELIVERY_BY_STATUS_CODE_ERROR);
      }
    }
    if (Objects.isNull(response) || Objects.isNull(response.getBody())) {
      throw new ReceivingException(
          ReceivingException.GDM_SERVICE_DOWN,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GDM_GET_DELIVERY_BY_STATUS_CODE_ERROR);
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
