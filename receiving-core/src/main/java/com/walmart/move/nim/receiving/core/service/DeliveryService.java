package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceiptUtils.isPoFinalized;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.exception.ConfirmPurchaseOrderError.DCFIN_ERROR;
import static com.walmart.move.nim.receiving.core.common.exception.ConfirmPurchaseOrderErrorCode.DEFAULT_ERROR;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.DeliveryStatus.ARV;
import static com.walmart.move.nim.receiving.utils.constants.DeliveryStatus.COMPLETE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.atlas.argus.metrics.annotations.CaptureMethodMetric;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.builder.ConfirmPoResponseBuilder;
import com.walmart.move.nim.receiving.core.builder.DeliveryWithOSDRResponseBuilder;
import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.builder.RecordOSDRResponseBuilder;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSDeliveryDetailsResponse;
import com.walmart.move.nim.receiving.core.client.gls.model.POS;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.exception.Error;
import com.walmart.move.nim.receiving.core.common.exception.GDMServiceUnavailableException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.event.processor.unload.DeliveryUnloaderProcessor;
import com.walmart.move.nim.receiving.core.event.processor.unload.DeliveryUnloadingProcessor;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.ShipmentInfo;
import com.walmart.move.nim.receiving.core.message.common.ShipmentRequest;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.delivery.UnloaderInfoDTO;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsSearchFields;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.VendorComplianceRequestDates;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.CatalogGtinUpdate;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.osdr.service.OsdrService;
import com.walmart.move.nim.receiving.core.repositories.*;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * This is a abstract class of DeliveryService As of now we have two implementation 1.
 * DeliveryServiceImpl 2. EndGameDeliveryService
 *
 * <p>Developers has to inject DeliveryServiceImpl if they want rest call with out retry in case of
 * certain failure.
 *
 * <p>Developers has to inject EndGameDeliveryService if they want rest call with retry in case of
 * certain failure.
 *
 * @author a0b02ft
 */
public abstract class DeliveryService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryService.class);
  @Autowired protected Gson gson;

  @Autowired
  @Qualifier("gsonForInstantAdapter")
  private Gson gsonForInstantAdapter;

  @Autowired private ReceiptService receiptService;

  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;

  @ManagedConfiguration protected AppConfig appConfig;
  /*
  Injecting repository instead of service to avoid  circular dependency. Need to redesign
  */
  @Autowired private InstructionRepository instructionRepository;
  @Autowired protected ReceiptCustomRepository receiptCustomRepository;
  @Autowired private ReceiptRepository receiptRepository;
  @Autowired protected ContainerItemCustomRepository containerItemCustomRepository;

  @Autowired private RestUtils restUtils;

  @Autowired RetryableRestConnector restConnector;

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private DeliveryWithOSDRResponseBuilder deliveryWithOSDRResponseBuilder;

  @Resource(name = "restConnector")
  protected RestConnector simpleRestConnector;

  @Autowired private RecordOSDRResponseBuilder recordOSDRResponseBuilder;

  @Autowired private ConfirmPoResponseBuilder confirmPoResponseBuilder;

  @Autowired private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;

  @Autowired private ItemCatalogRepository itemCatalogRepository;

  @Autowired private GDMRestApiClient gdmRestApiClient;

  @Autowired private GlsRestApiClient glsRestApiClient;

  @Autowired private OSDRRecordCountAggregator osdrRecordCountAggregator;

  @Autowired private RejectionsRepository rejectionsRepository;

  @Resource(name = ReceivingConstants.WITRON_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService deliveryMetaDataService;

  private GdmError gdmError;

  public SsccScanResponse getSsccScanDetails(
      String deliveryNumber, String sscc, HttpHeaders headers) {
    headers.set(
        ReceivingConstants.ACCEPT, ReceivingConstants.GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE);
    if (ReceivingConstants.VERSION_V1.equalsIgnoreCase(appConfig.getShipmentSearchVersion())) {
      headers.set(
          ReceivingConstants.ACCEPT,
          ReceivingConstants.GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE_V1);
    }

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED, false)) {
      headers.set(ReceivingConstants.ACCEPT, GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE_v4);
    }
    if ((ReceivingConstants.Rx_ASN_RCV_OVER_RIDE_VALUE)
        .equalsIgnoreCase(headers.getFirst(ReceivingConstants.Rx_ASN_RCV_OVER_RIDE_KEY))) {
      headers.replace(
          ReceivingConstants.ACCEPT,
          Arrays.asList(ReceivingConstants.GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE));
    }
    String url =
        getSsccScanDetailsUrl(
            deliveryNumber, sscc, ReceivingUtils.isAsnReceivingOverrideEligible(headers));

    try {
      ResponseEntity<SsccScanResponse> gdmResponseEntity =
          getSsccScanResponseResponseEntity(headers, url);
      if (HttpStatus.NO_CONTENT.equals(gdmResponseEntity.getStatusCode())) {
        LOGGER.info(
            "Got 204 response code from GDM for delivery : {}, ssccCode : {}, url : {}",
            deliveryNumber,
            sscc,
            url);
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false)) {
          if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(), IS_ALL_VENDORS_ENABLED_IN_GDM, false)) {
            return null;
          } else {
            return getDsdcSsccNotFoundResponse(deliveryNumber);
          }
        }
        return null;
      }
      LOGGER.info(
          "For deliveryNumber = {} and sscc = {} got response={} from GDM",
          deliveryNumber,
          sscc,
          gdmResponseEntity.getBody());
      SsccScanResponse ssccScanResponse = gdmResponseEntity.getBody();
      if (isNull(ssccScanResponse)) {
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false)) {
          if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(), IS_ALL_VENDORS_ENABLED_IN_GDM, false)) {
            throwSsccNotFoundErrorMessage(sscc);
          } else {
            ssccScanResponse = getDsdcSsccNotFoundResponse(deliveryNumber);
          }
        } else {
          throwSsccNotFoundErrorMessage(sscc);
        }
      }
      return ssccScanResponse;
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SSCC_NOT_FOUND,
          String.format(ReceivingConstants.GDM_SHIPMENT_NOT_FOUND, sscc),
          sscc);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }
  }

  protected ResponseEntity<SsccScanResponse> getSsccScanResponseResponseEntity(
      HttpHeaders headers, String url) {
    return simpleRestConnector.exchange(
        url, HttpMethod.GET, new HttpEntity<>(headers), SsccScanResponse.class);
  }

  private String getSsccScanDetailsUrl(
      String deliveryNumber, String sscc, boolean asnOverrideEligible) {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    pathParams.put(ReceivingConstants.SHIPMENT_IDENTIFIER, sscc);
    String uri = ReceivingConstants.GDM_SHIPMENT_DETAILS_URI;
    if (asnOverrideEligible) {
      uri = GDM_SHIPMENT_DETAILS_WITH_ASN_URI;
    }

    return ReceivingUtils.replacePathParams(appConfig.getGdmBaseUrl() + uri, pathParams).toString();
  }

  @Counted(
      name = "searchShipmentHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "searchShipment")
  @Timed(
      name = "searchShipmentTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "searchShipment")
  @ExceptionCounted(
      name = "searchShipmentExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "searchShipment")
  public Shipment searchShipment(String deliveryNumber, String sscc, HttpHeaders requestHeaders) {
    requestHeaders.remove(ReceivingConstants.ACCEPT);
    requestHeaders.add(
        ReceivingConstants.ACCEPT, ReceivingConstants.GDM_SEARCH_SHIPMENT_BY_DELIVERY_ACCEPT_TYPE);

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.SHIPMENT_IDENTIFIER, sscc);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);

    String url =
        ReceivingUtils.replacePathParamsAndQueryParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_SEARCH_SHIPMENT,
                pathParams,
                queryParams)
            .toString();
    ResponseEntity<String> response;
    try {
      response =
          simpleRestConnector.exchange(
              url, HttpMethod.GET, new HttpEntity<>(requestHeaders), String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SEARCH_SHIPMENT_FAILED, ReceivingConstants.GDM_SEARCH_SHIPMENT_FAILED);

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));

      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }
    LOGGER.info(
        "For deliveryNumber = {} and sscc = {} got searchShipment response={} from GDM",
        deliveryNumber,
        sscc,
        response.getBody());
    if (StringUtils.isBlank(response.getBody())) {
      LOGGER.error(
          "For deliveryNumber = {} and sscc = {} got invalid searchShipment response={} from GDM",
          deliveryNumber,
          sscc,
          response.getBody());
      throwSsccNotFoundErrorMessage(sscc);
    }
    return gson.fromJson(response.getBody(), Shipment.class);
  }

  public SsccScanResponse globalPackSearch(String packNumber, HttpHeaders requestHeaders) {
    requestHeaders.set(
        ReceivingConstants.ACCEPT, ReceivingConstants.GDM_SHIPMENT_GET_BY_PACK_NUM_ACCEPT_TYPE_V2);

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.SHIPMENT_IDENTIFIER, packNumber);

    String url =
        ReceivingUtils.replacePathParamsAndQueryParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_SEARCH_PACK, pathParams, null)
            .toString();
    ResponseEntity<SsccScanResponse> response;
    try {
      response =
          simpleRestConnector.exchange(
              url, HttpMethod.GET, new HttpEntity<>(requestHeaders), SsccScanResponse.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SEARCH_PACK_FAILED,
          String.format(ReceivingConstants.GDM_SEARCH_PACK_FAILED, packNumber));

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));

      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }
    LOGGER.info(
        "For packNumber = {} got searchShipment response={} from GDM",
        packNumber,
        response.getBody());
    if (isNull(response.getBody())) {
      LOGGER.error(
          "For packNumber = {} got invalid searchShipment response={} from GDM",
          packNumber,
          response.getBody());
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_PACK_NOT_FOUND,
          String.format(ReceivingConstants.GDM_SEARCH_PACK_NOT_FOUND, packNumber),
          packNumber);
    }
    return response.getBody();
  }

  /**
   * This is abstract method it will fetch PO/PO line as response from GDM.
   *
   * @param deliveryNumber delivery number
   * @param upcNumber upc number
   * @param headers http headers
   * @return PO/PO line as response
   * @throws ReceivingException
   */
  public abstract String findDeliveryDocument(
      long deliveryNumber, String upcNumber, HttpHeaders headers) throws ReceivingException;

  /**
   * Gets GDM delivery using delivery number.
   *
   * @param deliveryUpdateMessage the delivery update message
   * @return the gdm data
   * @throws ReceivingException
   */
  public abstract Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException;

  /**
   * Gets delivery details based on URL provided in delivery update message.
   *
   * @param url url which needs to be hit
   * @param deliveryNumber the delivery number
   * @return the delivery details
   */
  public DeliveryDetails getDeliveryDetails(String url, Long deliveryNumber)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingConstants.METHOD_NOT_ALLOWED,
        HttpStatus.METHOD_NOT_ALLOWED,
        ExceptionCodes.METHOD_NOT_ALLOWED);
  }

  /**
   * @param deliveryNumber
   * @param headers
   * @return deliveryResponse
   * @throws ReceivingException
   */
  public String getDeliveryByDeliveryNumber(Long deliveryNumber, HttpHeaders headers)
      throws ReceivingException {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, Long.toString(deliveryNumber));
    ResponseEntity<String> deliveryResponse = null;

    deliveryResponse =
        restUtils.get(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI,
            headers,
            pathParams);

    if (deliveryResponse.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (deliveryResponse.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
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
                .errorKey(ExceptionCodes.DELIVERY_NOT_FOUND)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }
    } else {
      if (deliveryResponse.getBody().isEmpty()) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.DELIVERY_NOT_FOUND)
                .errorCode(ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE)
                .errorKey(ExceptionCodes.DELIVERY_NOT_FOUND)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }
    }

    return deliveryResponse.getBody();
  }

  /**
   * Gets LPN details from GDM which include, delivery, shipment and container info
   *
   * @param lpnNumber the lpn number
   * @param headers httpHeaders
   * @return the lpn details
   */
  public String getLpnDetailsByLpnNumber(String lpnNumber, HttpHeaders headers)
      throws ReceivingException {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.LPN_NUMBER, lpnNumber);
    ResponseEntity<String> lpnDetailsResponse;
    lpnDetailsResponse =
        restUtils.get(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_LPN_DETAILS_URI,
            headers,
            pathParams);

    if (lpnDetailsResponse.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (lpnDetailsResponse.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.GDM_SERVICE_DOWN)
                .errorCode(ReceivingException.GDM_GET_LPN_DETAILS_BY_LPN_NUMBER_ERROR_CODE)
                .errorKey(ExceptionCodes.GDM_SERVICE_DOWN)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      } else {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.LPN_DETAILS_NOT_FOUND)
                .errorCode(ReceivingException.GDM_GET_LPN_DETAILS_BY_LPN_NUMBER_ERROR_CODE)
                .errorKey(ExceptionCodes.GDM_LPN_DATA_NOT_FOUND)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }
    } else {
      if (lpnDetailsResponse.getBody() == null || lpnDetailsResponse.getBody().isEmpty()) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.LPN_DETAILS_NOT_FOUND)
                .errorCode(ReceivingException.GDM_GET_LPN_DETAILS_BY_LPN_NUMBER_ERROR_CODE)
                .errorKey(ExceptionCodes.GDM_LPN_DATA_NOT_FOUND)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      }
    }

    return lpnDetailsResponse.getBody();
  }

  /**
   * This method is used to get delivery document by using PO legacyType
   *
   * @param deliveryNumbers
   * @param poLegacyType
   * @param httpHeaders
   * @return Delivery with all POs having provided POReferenceLegacyType
   */
  @Transactional
  @InjectTenantFilter
  public String getDeliveryDocumentByPOChannelType(
      List<Long> deliveryNumbers, List<String> poLegacyType, HttpHeaders httpHeaders)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * This method is used to get delivery document by search criteria
   *
   * @param searchCriteriaString
   * @return Delivery with having all criteria provided
   */
  @Transactional
  @InjectTenantFilter
  public String getDeliveryDocumentBySearchCriteria(String searchCriteriaString)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * it should be in jms transaction : TBD Rollback on ReceivingException not required as not doing
   * any database update operation.
   *
   * @param deliveryNumber
   * @param performUnload
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public DeliveryInfo completeDelivery(
      Long deliveryNumber, boolean performUnload, HttpHeaders headers) throws ReceivingException {

    return tenantSpecificConfigReader
        .getConfiguredInstance(
            getFacilityNum().toString(),
            COMPLETE_DELIVERY_PROCESSOR,
            CompleteDeliveryProcessor.class)
        .completeDelivery(deliveryNumber, performUnload, headers);
  }

  /**
   * This method invokes GDM API to post the audit status for the given ASN & List of Packs
   *
   * @param shipmentInfo
   * @param httpHeaders
   * @throws ReceivingException
   */
  public void callGdmToUpdatePackStatus(List<ShipmentInfo> shipmentInfo, HttpHeaders httpHeaders)
      throws ReceivingException {
    String request;
    httpHeaders = ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    ResponseEntity<String> response;
    String url = appConfig.getGdmBaseUrl() + GDM_UPDATE_STATUS_V2_URL;
    try {
      request = gson.toJson(ShipmentRequest.builder().shipments(shipmentInfo).build());
      LOGGER.info("callGdmToUpdatePackStatus: For  Request={}, Headers={}", request, httpHeaders);
      Map<String, String> httpHeadersMap = new HashMap<>();
      for (Map.Entry<String, List<String>> entry : httpHeaders.entrySet()) {
        httpHeadersMap.put(entry.getKey(), entry.getValue().get(0).toString());
      }
      response = simpleRestConnector.put(url, request, httpHeadersMap, String.class);
      if (OK != response.getStatusCode()) {
        LOGGER.error(
            "Error calling GDM update status API: url={}  Request={}, response={}, Headers={}",
            url,
            request,
            response,
            httpHeaders);
      }
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          getStackTrace(e));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));
    }
  }
  /**
   * @param asnBarcode asn barcode scanned in client
   * @param headers http headers
   * @return GdmContainerResponse info
   * @throws ReceivingException
   */
  @Timed(
      name = "idmCallByAsnTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "getContainerInfoByAsnBarcode")
  @ExceptionCounted(
      name = "idmCallByAsnExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "getContainerInfoByAsnBarcode")
  public ShipmentResponseData getContainerInfoByAsnBarcode(String asnBarcode, HttpHeaders headers)
      throws ReceivingException {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put("asnBarcode", asnBarcode);
    ResponseEntity<String> response =
        restUtils.get(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_ASN_DOCUMENT_SEARCH,
            headers,
            pathParams);

    if (response.getStatusCode() != HttpStatus.OK) {
      LOGGER.error("GDM call failed with error: {}", response.getBody());
      if (response.getStatusCode() == HttpStatus.NOT_FOUND) {

        ExternalErrorResponse gdmErrorResponse =
            gson.fromJson(response.getBody(), ExternalErrorResponse.class);

        if (gdmErrorResponse != null
            && !CollectionUtils.isEmpty(gdmErrorResponse.getMessages())
            && gdmErrorResponse.getMessages().get(0).getDesc().equals("No container Found")) {
          throw new ReceivingException(
              ReceivingException.CREATE_INSTRUCTION_FOR_NO_MATCHING_ASN_ERROR_MESSAGE,
              HttpStatus.INTERNAL_SERVER_ERROR,
              ReceivingException.CREATE_INSTRUCTION_FOR_NO_MATCHING_ASN_ERROR_CODE);
        }

        if (gdmErrorResponse != null
            && !CollectionUtils.isEmpty(gdmErrorResponse.getMessages())
            && gdmErrorResponse
                .getMessages()
                .get(0)
                .getDesc()
                .equals(ReceivingException.FREIGHT_ALREADY_RCVD)) {
          throw new ReceivingException(
              String.format(
                  ReceivingException
                      .CREATE_INSTRUCTION_FOR_ASN_LABEL_ALREADY_RECEIVED_ERROR_MESSAGE,
                  asnBarcode),
              HttpStatus.INTERNAL_SERVER_ERROR,
              ReceivingException.FREIGHT_ALREADY_RCVD);
        }

        throw new ReceivingException(
            response.getBody(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.CREATE_INSTRUCTION_FOR_ASN_ERROR_CODE);
      } else if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        throw new ReceivingException(
            ReceivingException.GDM_SERVICE_DOWN,
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.CREATE_INSTRUCTION_FOR_ASN_ERROR_CODE);
      } else {
        throw new ReceivingException(
            response.getBody(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.CREATE_INSTRUCTION_FOR_ASN_ERROR_CODE);
      }
    }

    return gson.fromJson(response.getBody(), ShipmentResponseData.class);
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
  @Counted(
      name = "getPOLineInfoFromGdmHitCount",
      level1 = "uwms-receiving",
      level2 = "DeliveryService",
      level3 = "getPOLineInfoFromGDM")
  @Timed(
      name = "getPOLineInfoFromGdmAPITimed",
      level1 = "uwms-receiving",
      level2 = "DeliveryService",
      level3 = "getPOLineInfoFromGDM")
  @ExceptionCounted(
      name = "getPOLineInfoFromGdmAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "DeliveryService",
      level3 = "getPOLineInfoFromGDM")
  public GdmPOLineResponse getPOLineInfoFromGDM(
      String deliveryNumber, String poNumber, Integer poLineNumber, HttpHeaders headers)
      throws ReceivingException {
    ResponseEntity<String> response;
    String gdmBaseUri = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI;

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);

    URI gdmGetDeliveryUri =
        UriComponentsBuilder.fromUriString(gdmBaseUri).buildAndExpand(pathParams).toUri();
    gdmGetDeliveryUri =
        UriComponentsBuilder.fromUriString(gdmGetDeliveryUri.toString())
            .queryParam(ReceivingConstants.PO_NUMBER, poNumber)
            .queryParam(ReceivingConstants.PO_LINE_NUMBER, Integer.toString(poLineNumber))
            .queryParam(ReceivingConstants.INCLUDE_ACTIVE_CHANNEL_METHODS, Boolean.TRUE.toString())
            .build()
            .toUri();
    response = restUtils.get(gdmGetDeliveryUri.toString(), headers, new HashMap<>());

    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.GDM_SERVICE_DOWN)
                .errorCode(ReceivingException.GET_PTAG_ERROR_CODE)
                .errorKey(ExceptionCodes.GDM_SERVICE_DOWN)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      } else {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.PTAG_NOT_READY_TO_RECEIVE)
                .errorCode(ReceivingException.GET_PTAG_ERROR_CODE)
                .errorKey(ExceptionCodes.PTAG_NOT_READY_TO_RECEIVE)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.CONFLICT)
            .errorResponse(errorResponse)
            .build();
      }
    } else {
      if (response.getBody().isEmpty()) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.PTAG_NOT_READY_TO_RECEIVE)
                .errorCode(ReceivingException.GET_PTAG_ERROR_CODE)
                .errorKey(ExceptionCodes.PTAG_NOT_READY_TO_RECEIVE)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.CONFLICT)
            .errorResponse(errorResponse)
            .build();
      }
    }

    return gson.fromJson(response.getBody(), GdmPOLineResponse.class);
  }

  /**
   * This method will call GDM to update delivery status to working
   *
   * @param deliveryNumber
   * @param headers
   * @return
   * @throws ReceivingException
   */
  public GdmDeliveryStatusUpdateEvent updateDeliveryStatusToWorking(
      Long deliveryNumber, HttpHeaders headers) {
    ResponseEntity<String> response = null;
    GdmDeliveryStatusUpdateEvent gdmDeliveryStatusUpdateEvent = new GdmDeliveryStatusUpdateEvent();
    gdmDeliveryStatusUpdateEvent.setDeliveryNumber(deliveryNumber);
    gdmDeliveryStatusUpdateEvent.setReceiverUserId(
        headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    String gdmDeliveryStatusUpdateUrl =
        appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_OPEN_TO_WORKING_URI;
    LOGGER.info(
        "GDM delivery status update from Open to Working status for delivery number: {}, GDM delivery status update URL:{}",
        deliveryNumber,
        gdmDeliveryStatusUpdateUrl);
    try {
      response =
          restConnector.exchange(
              gdmDeliveryStatusUpdateUrl,
              HttpMethod.PUT,
              new HttpEntity<>(gson.toJson(gdmDeliveryStatusUpdateEvent), headers),
              String.class);

      LOGGER.info(
          "Successfully updated delivery with status = {} delivery number: {} and user id = {}",
          response.getStatusCode(),
          gdmDeliveryStatusUpdateEvent.getDeliveryNumber(),
          gdmDeliveryStatusUpdateEvent.getReceiverUserId());
    } catch (RestClientResponseException e) {
      String errorCode = ExceptionCodes.DELIVERY_WORKING_STATUS_EVENT_INTERNAL_SERVER_ERROR;
      if (HttpStatus.BAD_REQUEST.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.INVALID_WORKING_STATUS_EVENT_REQUEST;
      } else if (HttpStatus.SERVICE_UNAVAILABLE.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.DELIVERY_WORKING_STATUS_EVENT_UNAVAILABLE;
      } else if (HttpStatus.NOT_FOUND.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.DELIVERY_WORKING_STATUS_EVENT_NOT_FOUND;
      }

      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          gdmDeliveryStatusUpdateUrl,
          StringUtils.EMPTY,
          e.getResponseBodyAsString(),
          errorCode);
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ReceivingConstants.GDM_INTERNAL_SERVER_ERROR,
          String.format(
              ReceivingConstants.DELIVERY_WORKING_STATUS_EVENT_RESPONSE_ERROR_MSG, e.getMessage()));
    }
    return gdmDeliveryStatusUpdateEvent;
  }

  /**
   * This method will call GDM to update delivery status to doorOpen
   *
   * @param deliveryNumber
   * @param headers
   * @return
   * @throws ReceivingException
   */
  public GdmDeliveryStatusUpdateEvent updateDeliveryStatusToOpen(
      Long deliveryNumber, Map<String, Object> headers) {
    ResponseEntity<String> response = null;
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(TENENT_FACLITYNUM, headers.get(TENENT_FACLITYNUM).toString());
    httpHeaders.add(USER_ID_HEADER_KEY, headers.get(USER_ID_HEADER_KEY).toString());
    httpHeaders.add(TENENT_COUNTRY_CODE, headers.get(TENENT_COUNTRY_CODE).toString());
    httpHeaders.add(CORRELATION_ID_HEADER_KEY, headers.get(CORRELATION_ID_HEADER_KEY).toString());
    httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    GdmDeliveryStatusUpdateEvent gdmDeliveryStatusUpdateEvent =
        new GdmDeliveryStatusUpdateEvent(
            deliveryNumber, headers.get(ReceivingConstants.USER_ID_HEADER_KEY).toString());
    String gdmDeliveryStatusUpdateUrl =
        appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_DOOR_OPEN_URI;
    LOGGER.info(
        "GDM delivery status update for Open delivery status for delivery number: {}, GDM delivery status update URL:{}",
        deliveryNumber,
        gdmDeliveryStatusUpdateUrl);
    try {
      response =
          restConnector.exchange(
              gdmDeliveryStatusUpdateUrl,
              HttpMethod.PUT,
              new HttpEntity<>(gson.toJson(gdmDeliveryStatusUpdateEvent), httpHeaders),
              String.class);
      LOGGER.info(
          "Successfully updated delivery with status = {} delivery number: {} and user id: {}",
          response.getStatusCode(),
          gdmDeliveryStatusUpdateEvent.getDeliveryNumber(),
          gdmDeliveryStatusUpdateEvent.getReceiverUserId());
    } catch (RestClientResponseException e) {
      String errorCode = ExceptionCodes.DELIVERY_OPEN_STATUS_EVENT_INTERNAL_SERVER_ERROR;
      if (HttpStatus.BAD_REQUEST.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.INVALID_OPEN_STATUS_EVENT_REQUEST;
      } else if (HttpStatus.SERVICE_UNAVAILABLE.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.DELIVERY_OPEN_STATUS_EVENT_UNAVAILABLE;
      } else if (HttpStatus.NOT_FOUND.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.DELIVERY_OPEN_STATUS_EVENT_NOT_FOUND;
      }

      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          gdmDeliveryStatusUpdateUrl,
          StringUtils.EMPTY,
          e.getResponseBodyAsString(),
          errorCode);
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_OPEN_STATUS_EVENT_REQUEST,
          String.format(
              ReceivingConstants.DELIVERY_OPEN_STATUS_EVENT_RESPONSE_ERROR_MSG, e.getMessage()));
    }
    return gdmDeliveryStatusUpdateEvent;
  }

  /**
   * This method will be call GDM for delivery to re-open
   *
   * @param deliveryNumber
   * @param headers
   * @return
   * @throws ReceivingException
   */
  public ReOpenDeliveryInfo reOpenDelivery(Long deliveryNumber, HttpHeaders headers)
      throws ReceivingException {
    ResponseEntity<String> response = null;

    ReOpenDeliveryInfo reOpenDeliveryInfo = new ReOpenDeliveryInfo();
    reOpenDeliveryInfo.setDeliveryNumber(deliveryNumber);
    reOpenDeliveryInfo.setReceiverUserId(headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    response =
        restUtils.put(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_RE_OPEN_DELIVERY_URI,
            headers,
            new HashMap<>(),
            gson.toJson(reOpenDeliveryInfo));

    if (!response.getStatusCode().is2xxSuccessful()) {
      if (response.getStatusCode() == BAD_REQUEST) {
        throw new ReceivingException(
            ReceivingException.UNABLE_TO_REOPEN_DELIVERY,
            response.getStatusCode(),
            ReceivingException.RE_OPEN_DELIVERY_ERROR_CODE,
            ReceivingException.ERROR_HEADER_REOPEN_DELIVERY_FAILED);

      } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new ReceivingException(
            ReceivingException.UNABLE_TO_FIND_DELIVERY_TO_REOPEN,
            response.getStatusCode(),
            ReceivingException.RE_OPEN_DELIVERY_ERROR_CODE,
            ReceivingException.ERROR_HEADER_REOPEN_DELIVERY_FAILED);

      } else if (response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        throw new ReceivingException(
            ReceivingException.GDM_SERVICE_DOWN,
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.RE_OPEN_DELIVERY_ERROR_CODE);
      } else {
        throw new ReceivingException(
            response.getBody(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.RE_OPEN_DELIVERY_ERROR);
      }
    }
    return reOpenDeliveryInfo;
  }

  /**
   * This method will be call GDM for delivery to re-open Validations: hasOneZone should not be
   * undefined. if hasOneZone is true, zones array should only contain id = 1. uom should not be
   * undefined. Default should be "F" zones array should not be empty purchase orders array should
   * not be empty
   *
   * @param deliveryNumber
   * @param deliveryTrailerTemperatureInfo
   * @param headers
   * @return
   */
  public GDMDeliveryTrailerTemperatureInfo updateDeliveryTrailerTemperature(
      Long deliveryNumber,
      GDMDeliveryTrailerTemperatureInfo deliveryTrailerTemperatureInfo,
      HttpHeaders headers) {
    // validate the temperature info object while handling the exceptions
    validateTrailerTemperatureRequest(deliveryTrailerTemperatureInfo);
    LOGGER.info(
        "Temperature recorder found: {} for delivery: {}",
        !deliveryTrailerTemperatureInfo.getIsNoRecorderFound(),
        deliveryNumber);
    // call the gdm client to save the temperature zone -
    ResponseEntity<GDMTemperatureResponse> response =
        gdmRestApiClient.saveZoneTrailerTemperature(
            deliveryNumber, deliveryTrailerTemperatureInfo, headers);

    postProcessGDMResponse(response, deliveryTrailerTemperatureInfo);
    return deliveryTrailerTemperatureInfo;
  }

  private void postProcessGDMResponse(
      ResponseEntity<GDMTemperatureResponse> response, GDMDeliveryTrailerTemperatureInfo request) {

    Set<String> purchaseOrders = new HashSet<>();

    for (TrailerZoneTemperature zone : request.getZones()) {
      purchaseOrders.addAll(zone.getPurchaseOrders());
    }

    switch (response.getStatusCode().value()) {
      case 200:
        return;
      case 400:
      case 401:
      case 403:
      case 404:
        throw new GDMTrailerTemperatureBadRequestException(
            GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_CODE,
            GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_DESCRIPTION,
            GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_MESSAGE);
      case 206:
        handleGDMPOExceptionScenario(response, purchaseOrders);
        break;
      default:
        throw new GDMTrailerTemperatureServiceFailedException(
            GDM_DELIVERY_TRAILER_TEMPERATURE_SERVICE_FAILED_ERROR_CODE,
            GDM_DELIVERY_TRAILER_TEMPERATURE_SERVICE_FAILED_ERROR_DESCRIPTION,
            GDM_DELIVERY_TRAILER_TEMPERATURE_SERVICE_FAILED_ERROR_MESSAGE);
    }
  }

  private void handleGDMPOExceptionScenario(
      ResponseEntity<GDMTemperatureResponse> response, Set<String> purchaseOrders) {
    GDMTemperatureResponse responseBody = Objects.requireNonNull(response.getBody());
    String gdmErrorCode = responseBody.getReasonCode();
    Set<String> finalizedPos = responseBody.getFinalizedPos();
    String errorMessage = null;

    if (GDM_DELIVERY_TRAILER_TEMPERATURE_PO_FINALIZED_ERROR_CODE.equals(gdmErrorCode)) {
      if (purchaseOrders.equals(finalizedPos)) {
        // all of the pos are finalized, fully finalized scenario (409)
        throw new GDMTrailerTemperatureAllPoFinalizedException(
            GDM_DELIVERY_TRAILER_TEMPERATURE_ALL_PO_FINALIZED_ERROR_CODE,
            GDM_DELIVERY_TRAILER_TEMPERATURE_ALL_PO_FINALIZED_ERROR_DESCRIPTION,
            GDM_DELIVERY_TRAILER_TEMPERATURE_ALL_PO_FINALIZED_ERROR_MESSAGE);
      } else {
        // some of the pos are finalized, partial finalized scenario (409)
        errorMessage =
            String.format(
                GDM_DELIVERY_TRAILER_TEMPERATURE_PARTIAL_PO_FINALIZED_ERROR_MESSAGE,
                String.join(",", finalizedPos));
        throw new GDMTrailerTemperaturePartialPoFinalizedException(
            GDM_DELIVERY_TRAILER_TEMPERATURE_PARTIAL_PO_FINALIZED_ERROR_CODE,
            GDM_DELIVERY_TRAILER_TEMPERATURE_PARTIAL_PO_FINALIZED_ERROR_DESCRIPTION,
            errorMessage);
      }
    }
  }

  private void validateTrailerTemperatureRequest(GDMDeliveryTrailerTemperatureInfo request) {
    StringBuilder errorMessage = new StringBuilder();
    Set<TrailerZoneTemperature> zones = request.getZones();

    if (zones == null || zones.isEmpty()) {
      errorMessage.append("Invalid zone input \n");
    } else {

      Set<String> invalidPosMessage = new HashSet<>();

      for (TrailerZoneTemperature zone : zones) {
        Set<String> pos = zone.getPurchaseOrders();
        if (pos == null || pos.isEmpty()) {
          invalidPosMessage.add("Invalid pos input");
        }
      }
      if (!(invalidPosMessage.isEmpty())) {
        errorMessage.append(String.join("", invalidPosMessage));
      }

      Set<String> uomValues = new HashSet<>();

      for (TrailerZoneTemperature zone : zones) {
        uomValues.add(zone.getTemperature().getUom());
      }

      int uomValuesSize = uomValues.size();

      if (uomValuesSize > 1) {
        errorMessage.append("Invalid uom - Multiple temperature uom values \n");
      } else if (uomValuesSize == 1) {

        Optional<String> value = uomValues.stream().findFirst();
        boolean isSingleUom = value.isPresent();
        if (isSingleUom) {
          String uom = value.get();
          if (!("F".equals(uom))) {
            errorMessage.append("Invalid uom value. Must be F \n");
          }
        }
      }

      Set<String> emptyZoneTempMessage = new HashSet<>();
      for (TrailerZoneTemperature zone : zones) {
        String zoneTemperature = zone.getTemperature().getValue();

        if (zoneTemperature == null || zoneTemperature.isEmpty()) {
          emptyZoneTempMessage.add(
              ReceivingConstants.GDM_DELIVERY_TRAILER_ZONE_TEMPERATURE_EMPTY_ZONE_VALUE);
        } else {
          float temperature = Float.parseFloat(zoneTemperature);
          if (!(temperature <= ReceivingConstants.GDM_DELIVERY_TRAILER_ZONE_TEMPERATURE_MAX_VALUE
              && temperature
                  >= ReceivingConstants.GDM_DELIVERY_TRAILER_ZONE_TEMPERATURE_MIN_VALUE)) {
            emptyZoneTempMessage.add(
                String.format(
                    ReceivingConstants.GDM_DELIVERY_TRAILER_ZONE_TEMPERATURE_INVALID_VALUE,
                    ReceivingConstants.GDM_DELIVERY_TRAILER_ZONE_TEMPERATURE_MIN_VALUE,
                    ReceivingConstants.GDM_DELIVERY_TRAILER_ZONE_TEMPERATURE_MAX_VALUE));
          }
        }
      }

      if (!(emptyZoneTempMessage.isEmpty())) {
        errorMessage.append(String.join("", emptyZoneTempMessage));
      }
    }

    if (errorMessage.length() > 0) {
      LOGGER.error(errorMessage.toString());
      throw new GDMTrailerTemperatureBadRequestException(
          GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_CODE,
          GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_DESCRIPTION,
          GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_MESSAGE);
    }
  }

  /**
   * This method will call GDM for delivery header details
   *
   * @param afterDate
   * @param httpHeaders
   * @return
   */
  public List<GdmDeliveryHeaderDetailsResponse> getDeliveryHeaderDetails(
      Date afterDate, Date toDate, List<Long> deliveryNumbers, HttpHeaders httpHeaders)
      throws ReceivingException {
    ResponseEntity<String> response;
    Type deliveryHeaderDetailsResponseType =
        new TypeToken<List<GdmDeliveryHeaderDetailsResponse>>() {}.getType();

    GdmDeliveryHeaderDetailsSearchFields gdmDeliveryHeaderDetailsSearchFields =
        GdmDeliveryHeaderDetailsSearchFields.builder()
            .deliveryNumbers(deliveryNumbers)
            .receivingCompletedStartTime(afterDate.toInstant())
            .receivingCompletedEndTime(toDate.toInstant())
            .build();

    String url = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_DELIVERY_HEADERS_DETAILS_SEARCH;

    try {
      response =
          simpleRestConnector.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(
                  gsonForInstantAdapter.toJson(gdmDeliveryHeaderDetailsSearchFields), httpHeaders),
              String.class);

    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingException(
          ReceivingException.DELIVERY_HEADER_DETAILS_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));
      throw new ReceivingException(
          ReceivingException.GDM_SERVICE_DOWN,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
    }

    List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse =
        gsonForInstantAdapter.fromJson(response.getBody(), deliveryHeaderDetailsResponseType);

    if (CollectionUtils.isEmpty(deliveryHeaderDetailsResponse)) {
      LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, "", "");
      throw new ReceivingException(
          ReceivingException.DELIVERY_HEADER_DETAILS_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
    } else {
      LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, "", response.getBody());
    }

    return deliveryHeaderDetailsResponse;
  }

  /**
   * This method will call GDM for updating the vendor UPC in the delivery
   *
   * @param deliveryNumber
   * @param itemNumber
   * @param vendorUPC
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "updateVendorUPCHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "updateVendorUPC")
  @Timed(
      name = "updateVendorUPCTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "updateVendorUPC")
  @ExceptionCounted(
      name = "updateVendorUPCExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "updateVendorUPC")
  public void updateVendorUPC(
      String deliveryNumber, Long itemNumber, String vendorUPC, HttpHeaders httpHeaders) {

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    pathParams.put(ReceivingConstants.ITEM_NUMBER, itemNumber.toString());

    String url =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_UPDATE_UPC, pathParams)
            .toString();
    GdmItemCatalogUpdateRequest gdmItemCatalogUpdateRequest =
        GdmItemCatalogUpdateRequest.builder().vendorUPC(vendorUPC).build();

    try {
      ResponseEntity<String> response =
          simpleRestConnector.exchange(
              url,
              HttpMethod.PUT,
              new HttpEntity<>(gson.toJson(gdmItemCatalogUpdateRequest), httpHeaders),
              String.class);
      LOGGER.info(
          "Received response:{} from GDM to update vendorUPC:{} "
              + "for delivery:{} and itemNumber:{}",
          response.getStatusCodeValue(),
          vendorUPC,
          deliveryNumber,
          itemNumber);
    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == BAD_REQUEST.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_ITEM_DETAILS,
            String.format(ReceivingConstants.GDM_CATALOG_BAD_REQUEST, deliveryNumber, itemNumber));
      } else if (e.getRawStatusCode() == HttpStatus.NOT_FOUND.value()) {
        throw new ReceivingDataNotFoundException(
            ExceptionCodes.DELIVERY_NOT_FOUND,
            String.format(ReceivingConstants.GDM_CATALOG_NOT_FOUND, deliveryNumber));
      }
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.GDM_ERROR, ReceivingConstants.GDM_SERVICE_DOWN);
    }
  }

  /**
   * This method will call GDM for updating the vendor UPC for the given item number. Vendor UPC
   * will be updated at item level
   *
   * @param itemNumber
   * @param vendorUPC
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "updateVendorUpcItemV3CHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "updateVendorUpcItemV3")
  @Timed(
      name = "updateVendorUpcItemV3Timed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "updateVendorUpcItemV3")
  @ExceptionCounted(
      name = "updateVendorUpcItemV3ExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "updateVendorUpcItemV3")
  public void updateVendorUpcItemV3(Long itemNumber, String vendorUPC, HttpHeaders httpHeaders) {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.ITEM_NUMBER, itemNumber.toString());
    String url =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + GDM_VENDOR_UPC_UPDATE_ITEM_V3, pathParams)
            .toString();
    CatalogGtinUpdate catalogGtinUpdate = new CatalogGtinUpdate();
    ItemDetails itemData = new ItemDetails();
    itemData.setNumber(itemNumber);
    itemData.setCatalogGTIN(vendorUPC);
    catalogGtinUpdate.setItemData(itemData);
    try {
      ResponseEntity<String> response =
          simpleRestConnector.exchange(
              url,
              HttpMethod.PUT,
              new HttpEntity<>(gson.toJson(catalogGtinUpdate), httpHeaders),
              String.class);
      LOGGER.info(
          "GDM vendorUPC update response:{} for vendorUPC:{} and itemNumber:{}",
          response.getStatusCodeValue(),
          vendorUPC,
          itemNumber);
    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == BAD_REQUEST.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_ITEM_DETAILS,
            String.format(ReceivingConstants.GDM_VENDOR_UPC_UPDATE_ITEM_BAD_REQUEST, itemNumber));
      } else if (e.getRawStatusCode() == HttpStatus.NOT_FOUND.value()) {
        throw new ReceivingDataNotFoundException(
            ExceptionCodes.GDM_VENDOR_UPC_UPDATE_ITEM_NOT_FOUND,
            String.format(ReceivingConstants.GDM_VENDOR_UPC_UPDATE_ITEM_NOT_FOUND, itemNumber),
            itemNumber);
      }
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.GDM_ERROR, ReceivingConstants.GDM_SERVICE_DOWN);
    }
  }
  /**
   * This is a service method to update exiting Receipt with OSDR details from
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param recordOSDRReasonCodesRequestBody
   * @param httpHeaders
   * @throws ReceivingException
   */
  public RecordOSDRResponse recordOSDR(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      RecordOSDRRequest recordOSDRReasonCodesRequestBody,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    return recordOSDRResponseBuilder.build(
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        recordOSDRReasonCodesRequestBody,
        httpHeaders);
  }

  /**
   * Get Delivery by deliveryNumber including OSDR & Problem counts.
   *
   * @param deliveryNumber
   * @param forwardableHeaders
   * @param includeOSDR
   * @param poNumber filter results only for given PO to reduce client/server payload
   * @return
   * @throws ReceivingException
   */
  public DeliveryWithOSDRResponse getDeliveryWithOSDRByDeliveryNumber(
      Long deliveryNumber,
      Map<String, Object> forwardableHeaders,
      boolean includeOSDR,
      String poNumber)
      throws ReceivingException {

    return deliveryWithOSDRResponseBuilder.build(
        deliveryNumber, forwardableHeaders, includeOSDR, poNumber);
  }

  /**
   * get Po/POL information for given delivery number
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public String getPOInfoFromDelivery(
      long deliveryNumber, String purchaseReferenceNumber, HttpHeaders headers)
      throws ReceivingException {
    // TODO Auto-generated method stub
    return null;
  }

  public DeliveryInfo unloadComplete(
      long deliveryNumber, String doorNumber, String action, HttpHeaders headers) {

    List<ReceiptSummaryResponse> receiptSummaryResponses =
        receiptService.getReceivedQtySummaryByPOForDelivery(
            deliveryNumber, ReceivingConstants.Uom.EACHES);

    DeliveryInfo deliveryInfo =
        deliveryStatusPublisher.publishDeliveryStatus(
            deliveryNumber,
            DeliveryStatus.UNLOADING_COMPLETE.toString(),
            doorNumber,
            receiptSummaryResponses,
            ReceivingUtils.getForwardablHeader(headers),
            action);

    // hook for unloading complete market specific execution
    DeliveryUnloadingProcessor deliveryUnloadingProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_UNLOADING_PROCESOR,
            DEFAULT_DELIVERY_UNLOADING_PROCESSOR,
            DeliveryUnloadingProcessor.class);
    deliveryUnloadingProcessor.doProcess(deliveryInfo);
    return deliveryInfo;
  }

  /**
   * Confirm multiple PO
   *
   * @param deliveryNumber
   * @param confirmPOsRequest
   * @param forwardableHeaders
   * @return confirmPOsResponse with list of errors
   */
  public ConfirmPurchaseOrdersResponse confirmPOs(
      Long deliveryNumber,
      ConfirmPurchaseOrdersRequest confirmPOsRequest,
      Map<String, Object> forwardableHeaders) {
    final long confirmPosTs = System.currentTimeMillis();
    final List<String> purchaseReferenceNumbers = confirmPOsRequest.getPurchaseReferenceNumbers();
    LOGGER.info("Start confirmPOs list {}", purchaseReferenceNumbers);
    List<ConfirmPurchaseOrdersError> confirmPOsErrorList = new ArrayList<>();
    ConfirmPurchaseOrdersResponse confirmPOsResponse = new ConfirmPurchaseOrdersResponse();
    final ArrayList<String> poQuantityMisMatchList = getPoQuantityMisMatch(deliveryNumber);
    final ArrayList<String> poQuantityMisMatchManualGDCList =
        getQuantityMisMatchManualGDC(deliveryNumber, confirmPOsRequest, forwardableHeaders);

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_CONFIRM_POS_PERF_V1_ENABLED, false)) {
      try {
        final Map<POPOLineKey, ReceivingCountSummary> receivingCountSummaryMap =
            osdrRecordCountAggregator.getReceivingCountSummary(deliveryNumber, forwardableHeaders);
        // TODO IS_CONFIRM_POS_PERF_V2_ENABLED
        // List<ConfirmPurchaseOrdersError> l = Collections.synchronizedList...
        // Map<String, ConfirmPurchaseOrdersError> m = new HashMap<>();
        purchaseReferenceNumbers
            .stream() // parallel IS_CONFIRM_POS_PERF_V2_ENABLED
            .forEach(
                po -> {
                  try {
                    final ConfirmPurchaseOrdersError confirmPOsError =
                        confirmPo(
                            deliveryNumber,
                            forwardableHeaders,
                            poQuantityMisMatchList,
                            poQuantityMisMatchManualGDCList,
                            po,
                            receivingCountSummaryMap);
                    if (confirmPOsError != null
                        && nonNull(confirmPOsError.getPurchaseReferenceNumber())) {
                      // m.put(po,confirmPOsError);
                      confirmPOsErrorList.add(confirmPOsError);
                    }
                  } catch (Exception e) {
                    LOGGER.error(
                        "Error processing confirmPO={}, confirmPOsErrorList={}",
                        po,
                        confirmPOsErrorList,
                        e);
                    throw new ReceivingInternalException(
                        ConfirmPurchaseOrderError.DEFAULT_ERROR.getErrorCode(),
                        ConfirmPurchaseOrderError.DEFAULT_ERROR.getErrorMessage());
                  }
                });
      } catch (ReceivingException re) {
        LOGGER.error(
            "Error={}, processing confirmPOs list {}",
            confirmPOsErrorList,
            purchaseReferenceNumbers,
            re);
        throw new ReceivingInternalException(
            ConfirmPurchaseOrderError.DEFAULT_ERROR.getErrorCode(),
            ConfirmPurchaseOrderError.DEFAULT_ERROR.getErrorMessage());
      }

    } else {
      for (String purchaseReferenceNumber : purchaseReferenceNumbers) {
        ConfirmPurchaseOrdersError confirmPOsError =
            confirmPo(
                deliveryNumber,
                forwardableHeaders,
                poQuantityMisMatchList,
                poQuantityMisMatchManualGDCList,
                purchaseReferenceNumber,
                null);
        if (confirmPOsError != null && nonNull(confirmPOsError.getPurchaseReferenceNumber())) {
          confirmPOsErrorList.add(confirmPOsError);
        }
      }
    }
    confirmPOsResponse.setErrors(confirmPOsErrorList);
    LOGGER.info(
        "End confirmPOs completed in {} ms. Return response={}",
        System.currentTimeMillis() - confirmPosTs,
        confirmPOsResponse);

    return confirmPOsResponse;
  }

  private ConfirmPurchaseOrdersError confirmPo(
      Long deliveryNumber,
      Map<String, Object> forwardableHeaders,
      ArrayList<String> misMatchList,
      ArrayList<String> poQuantityMisMatchManualGDCList,
      String po,
      Map<POPOLineKey, ReceivingCountSummary> receivingCountSummaryMap) {
    final long confirmPoTs = System.currentTimeMillis();
    LOGGER.info("Start confirmPO {}", po);
    String userId = forwardableHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).toString();
    Date finalizedTimeStamp = new Date();
    ConfirmPurchaseOrdersError confirmPOsError = null;
    final boolean dcFinPoCloseAsyncEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), DC_FIN_PO_CLOSE_ASYNC_ENABLED, false);
    try {
      // Validate request for each PO
      checkQuantityMisMatch(misMatchList, po);
      checkQuantityMisMatchManual(poQuantityMisMatchManualGDCList, po);
      // TODO IS_CONFIRM_POS_PERF_V2_ENABLED  do below checks like above line
      checkOpenInstructions(deliveryNumber, po);
      checkPoConfirmed(deliveryNumber, po);

      // Prepare Requests for finalize PO //Gdm finalize PO Request
      FinalizePORequestBody finalizePORequestBody =
          buildGdmFinalizePORequestBody(
              deliveryNumber,
              po,
              finalizedTimeStamp,
              userId,
              forwardableHeaders,
              receivingCountSummaryMap);
      // DcFin PoClose Request
      // TODO: Optimize this call for full gls site
      DCFinPOCloseRequestBody dcFinPOCloseRequestBody =
          confirmPoResponseBuilder.getDcFinPOCloseRequestBody(
              deliveryNumber, po, forwardableHeaders, finalizePORequestBody.getTotalBolFbq());

      // GDM finalize PO
      confirmPoResponseBuilder.finalizePO(
          deliveryNumber, po, forwardableHeaders, finalizePORequestBody);
      LOGGER.info("GDM finalized PO={} by user={}.", po, userId);

      // RCV finalize PO (master receipt)
      finalizeAllMasterReceipts(deliveryNumber, po, userId, finalizedTimeStamp);
      LOGGER.info("Receiving finalized PO={} by user={}. Calling DcFin closePO", po, userId);

      // Disable dcFin po close post for Manual Grocery
      // This flag only applicable for gls site only ex 6097 = true
      if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(), IS_DCFIN_API_DISABLED, false)) {
        LOGGER.info("Calling DCFin for PO Close");
        // DcFin POST poClose
        confirmPoResponseBuilder.closePO(
            dcFinPOCloseRequestBody, forwardableHeaders, dcFinPoCloseAsyncEnabled);
      }
    } catch (ReceivingException re) {
      final ErrorResponse errorResponse = re.getErrorResponse();
      LOGGER.error(
          "Error working on PO={} by user={}, errMsg={} {}",
          po,
          userId,
          re.getMessage(),
          errorResponse);
      confirmPOsError = new ConfirmPurchaseOrdersError();
      confirmPOsError.setPurchaseReferenceNumber(po);
      if (ConfirmPurchaseOrderError.containsCode(errorResponse.getErrorCode())) {
        confirmPOsError.setErrorCode(errorResponse.getErrorCode());
        confirmPOsError.setErrorMessage(errorResponse.getErrorMessage().toString());

        if (DCFIN_ERROR.getErrorCode().equals(errorResponse.getErrorCode())
            && !dcFinPoCloseAsyncEnabled) {
          LOGGER.error(
              "{}Do manually post to DcFin for PO={} user={} as DcFin/poClose failed but RCV, GDM successfully finalized",
              SPLUNK_ALERT,
              po,
              userId);
        }
      } else {
        LOGGER.error("{}For PO={} by user={} returning default error", SPLUNK_ALERT, po, userId);
        ConfirmPurchaseOrderError confirmPOError =
            ConfirmPurchaseOrderErrorCode.getErrorValue(DEFAULT_ERROR);
        confirmPOsError.setErrorCode(confirmPOError.getErrorCode());
        confirmPOsError.setErrorMessage(confirmPOError.getErrorMessage());
      }
    } catch (ObjectOptimisticLockingFailureException olf) {
      LOGGER.error(
          "{}Version mismatch while marking master receipts as finalized for PO={} by user={}. Do finalize rcv db. Stack={}",
          SPLUNK_ALERT,
          po,
          userId,
          getStackTrace(olf));
      confirmPOsError = new ConfirmPurchaseOrdersError();
      confirmPOsError.setPurchaseReferenceNumber(po);
      ConfirmPurchaseOrderError confirmPOError =
          ConfirmPurchaseOrderErrorCode.getErrorValue(DEFAULT_ERROR);
      confirmPOsError.setErrorCode(confirmPOError.getErrorCode());
      confirmPOsError.setErrorMessage(PO_VERSION_MISMATCH);
    } catch (Exception e) {
      LOGGER.error(
          "{}Unknown Exception processing PO={} user={} Stack={}",
          SPLUNK_ALERT,
          po,
          userId,
          getStackTrace(e));
      confirmPOsError = new ConfirmPurchaseOrdersError();
      confirmPOsError.setPurchaseReferenceNumber(po);
      ConfirmPurchaseOrderError confirmPOError =
          ConfirmPurchaseOrderErrorCode.getErrorValue(DEFAULT_ERROR);
      confirmPOsError.setErrorCode(confirmPOError.getErrorCode());
      confirmPOsError.setErrorMessage(confirmPOError.getErrorMessage());
    }
    LOGGER.info(
        "End confirmPO {} completed in {} ms", po, System.currentTimeMillis() - confirmPoTs);
    return confirmPOsError;
  }

  /**
   * GDM Prepare the request payload for finalize PO
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param finalizedTimeStamp
   * @param userId
   * @param forwardableHeaders
   * @param receivingCountSummaryMap
   * @return
   * @throws ReceivingException
   */
  private FinalizePORequestBody buildGdmFinalizePORequestBody(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Date finalizedTimeStamp,
      String userId,
      Map<String, Object> forwardableHeaders,
      Map<POPOLineKey, ReceivingCountSummary> receivingCountSummaryMap)
      throws ReceivingException {
    FinalizePORequestBody finalizePORequestBody =
        finalizePORequestBodyBuilder.buildFrom(
            deliveryNumber, purchaseReferenceNumber, forwardableHeaders, receivingCountSummaryMap);
    finalizePORequestBody.setUserId(userId);
    finalizePORequestBody.setFinalizedTime(finalizedTimeStamp);
    LOGGER.info(
        "finalizePORequestBody's RcvdQty={}, returning totalBolFbq={}",
        finalizePORequestBody.getRcvdQty(),
        finalizePORequestBody.getTotalBolFbq());
    return finalizePORequestBody;
  }

  public void checkPoConfirmed(Long deliveryNumber, String purchaseReferenceNumber)
      throws ReceivingException {
    if (isNull(deliveryNumber)
        || isNull(purchaseReferenceNumber)
        || !tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), CHECK_PO_CONFIRMED_ENABLED, false)) return;

    final List<Receipt> osdrMasterList =
        receiptService.findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(
            deliveryNumber, purchaseReferenceNumber);

    if (osdrMasterList == null) return;

    for (Receipt osdrMaster : osdrMasterList) {
      if (isPoFinalized(osdrMaster)) {
        LOGGER.error(
            "masterReceipt is already finalized for po={}, poLine={} by UserId={} at ts={}",
            osdrMaster.getPurchaseReferenceNumber(),
            osdrMaster.getPurchaseReferenceLineNumber(),
            osdrMaster.getFinalizedUserId(),
            osdrMaster.getFinalizeTs());
        ConfirmPurchaseOrderError confirmPOError =
            ConfirmPurchaseOrderErrorCode.getErrorValue(
                ConfirmPurchaseOrderErrorCode.ALREADY_CONFIRMED);
        throw new ReceivingException(
            confirmPOError.getErrorMessage(), BAD_REQUEST, confirmPOError.getErrorCode());
      }
    }
  }

  public void checkQuantityMisMatch(
      ArrayList<String> poQuantityMisMatchList, String purchaseReferenceNumber)
      throws ReceivingException {
    if (poQuantityMisMatchList != null
        && poQuantityMisMatchList.size() > 0
        && poQuantityMisMatchList.contains(purchaseReferenceNumber)) {
      throw new ReceivingException(
          String.format(PO_QTY_MIS_MATCH_ERROR, purchaseReferenceNumber), INTERNAL_SERVER_ERROR);
    }
  }

  public void checkQuantityMisMatchManual(
      ArrayList<String> poQuantityMisMatchManualList, String purchaseReferenceNumber)
      throws ReceivingException {

    if (!CollectionUtils.isEmpty(poQuantityMisMatchManualList)
        && poQuantityMisMatchManualList.contains(purchaseReferenceNumber)) {
      LOGGER.error(
          "{}PO={} has quantity mismatch between GLS and Atlas",
          SPLUNK_ALERT,
          purchaseReferenceNumber);
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH, false)) {
        ConfirmPurchaseOrderError confirmPOError =
            ConfirmPurchaseOrderErrorCode.getErrorValue(
                ConfirmPurchaseOrderErrorCode.GLS_QUANTITY_MISMATCH);
        throw new ReceivingException(
            confirmPOError.getErrorMessage(), INTERNAL_SERVER_ERROR, confirmPOError.getErrorCode());
      }
    }
  }

  /**
   * @param deliveryNbr
   * @param poNbr
   * @param userId
   * @param timestamp
   */
  private void finalizeAllMasterReceipts(
      Long deliveryNbr, String poNbr, String userId, Date timestamp) {
    // Get receipts with OSDR_MASTER = 1
    List<Receipt> osdrMasterList =
        receiptService.findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(
            deliveryNbr, poNbr);

    confirmPoResponseBuilder.updateReceiptsWithFinalizedDetails(osdrMasterList, userId, timestamp);
  }

  /**
   * @param deliveryNumber
   * @param poNumber
   * @throws ReceivingException
   */
  private void checkOpenInstructions(Long deliveryNumber, String poNumber)
      throws ReceivingException {
    List<Instruction> openInstructions =
        instructionRepository
            .findByDeliveryNumberAndPurchaseReferenceNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                deliveryNumber, poNumber);

    Set<String> instructionOwners = new HashSet<>();
    for (Instruction instruction : openInstructions) {
      instructionOwners.add(ReceivingUtils.getInstructionOwner(instruction));
    }

    if (!instructionOwners.isEmpty()) {
      String errorMessage =
          instructionOwners.size() == 1
              ? String.format(
                  ConfirmPurchaseOrderErrorCode.getErrorValue(
                          ConfirmPurchaseOrderErrorCode.SINGLE_USER_OPEN_INSTRUCTIONS)
                      .getErrorMessage(),
                  instructionOwners.iterator().next())
              : String.format(
                  ConfirmPurchaseOrderErrorCode.getErrorValue(
                          ConfirmPurchaseOrderErrorCode.MULTI_USER_OPEN_INSTRUCTIONS)
                      .getErrorMessage(),
                  instructionOwners.iterator().next(),
                  instructionOwners.size() - 1);

      throw new ReceivingException(
          errorMessage,
          BAD_REQUEST,
          ConfirmPurchaseOrderErrorCode.getErrorValue(
                  ConfirmPurchaseOrderErrorCode.SINGLE_USER_OPEN_INSTRUCTIONS)
              .getErrorCode());
    }
  }

  /**
   * @param deliveryNumber
   * @throws ReceivingException
   * @return
   */
  public ArrayList<String> getPoQuantityMisMatch(Long deliveryNumber) {
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), CHECK_QUANTITY_MATCH_ENABLED, false)) return null;

    ArrayList<String> poQuantityMisMatchList = new ArrayList<>();

    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        receiptCustomRepository.receivedQtySummaryInEachesByDelivery(deliveryNumber);
    final List<ContainerPoLineQuantity> containerPoLineQuantities =
        containerItemCustomRepository.getContainerQuantity(deliveryNumber);

    receiptSummaryResponseList.forEach(
        receiptSummary -> {
          final String receiptPo = receiptSummary.getPurchaseReferenceNumber();
          final Integer receiptLine = receiptSummary.getPurchaseReferenceLineNumber();
          final Long receivedQty = receiptSummary.getReceivedQty();
          containerPoLineQuantities.forEach(
              containerPoLineQuantity -> {
                final String containerPo = containerPoLineQuantity.getPo();
                final int containerLine = containerPoLineQuantity.getLine();
                final Long containerQuantity = containerPoLineQuantity.getQuantity();
                if (receiptPo.equals(containerPo)
                    && receiptLine == containerLine
                    && containerQuantity.intValue() != receivedQty) {
                  LOGGER.error(
                      "deliveryNumber={} PO={} line={} has quantity Mismatch(container's={}, receipt's={})",
                      deliveryNumber,
                      receiptPo,
                      receiptLine,
                      containerQuantity,
                      receivedQty);

                  poQuantityMisMatchList.add(containerPo);
                }
              });
        });
    return poQuantityMisMatchList;
  }

  private ArrayList<String> getQuantityMisMatchManualGDC(
      Long deliveryNumber,
      ConfirmPurchaseOrdersRequest confirmPOsRequest,
      Map<String, Object> forwardableHeaders) {

    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_GLS_API_ENABLED, false)) {
      return null;
    }

    List<ReceiptSummaryResponse> rcvReceiptSummaryResponseList =
        receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(deliveryNumber);

    // Remove all PO's from receipt which are not in request
    List<ReceiptSummaryResponse> rcvReceiptSummaryOfRequestedPO =
        rcvReceiptSummaryResponseList
            .stream()
            .filter(
                receipt ->
                    confirmPOsRequest
                        .getPurchaseReferenceNumbers()
                        .contains(receipt.getPurchaseReferenceNumber()))
            .collect(Collectors.toList());

    LOGGER.info(
        "Validate PO line quantity mismatch GLS - Atlas deliveryNumber {} on receipt's {}",
        deliveryNumber,
        rcvReceiptSummaryOfRequestedPO);
    HttpHeaders requestHeader = ReceivingUtils.convertMapToHeader(forwardableHeaders);

    ArrayList<String> poQuantityMisMatchList = new ArrayList<>();

    GLSDeliveryDetailsResponse glsDeliveryDetailsResponse = null;
    try {
      glsDeliveryDetailsResponse =
          glsRestApiClient.deliveryDetails(String.valueOf(deliveryNumber), requestHeader);
    } catch (ReceivingException ex) {
      LOGGER.error(
          "{}GLS service to validated qty is down errorCode={} errorMsg={}",
          SPLUNK_ALERT,
          ex.getErrorResponse().getErrorCode(),
          ex.getErrorResponse().getErrorMessage());
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(), BLOCK_RECEIVING_ON_GLS_DOWN, false)) {
        throw new ReceivingBadDataException(
            ReceivingException.GLS_SERVICE_DOWN_CODE, ReceivingException.GLS_SERVICE_DOWN_MSG);
      }
    }
    // compare GLS and Receiving for quantity match
    if (nonNull(glsDeliveryDetailsResponse)
        && !CollectionUtils.isEmpty(glsDeliveryDetailsResponse.getPos())) {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(), CHECK_QUANTITY_MATCH_BY_GLS_BASELINE_ENABLED, false)) {
        buildPoQuantityMisMatchListWithGlsAsBaseLine(
            deliveryNumber,
            rcvReceiptSummaryOfRequestedPO,
            poQuantityMisMatchList,
            glsDeliveryDetailsResponse);
      } else {
        buildPoQuantityMisMatchListWithRcvAsBaseLine(
            deliveryNumber,
            rcvReceiptSummaryOfRequestedPO,
            poQuantityMisMatchList,
            glsDeliveryDetailsResponse);
      }
    }

    return poQuantityMisMatchList;
  }

  /**
   * Get GLS Response POs List and from there check if matching with Receiving PO's Qty
   *
   * @param deliveryNumber
   * @param rcvReceiptSummaryResponseList
   * @param poQuantityMisMatchList
   * @param glsDeliveryDetailsResponse
   */
  private void buildPoQuantityMisMatchListWithGlsAsBaseLine(
      Long deliveryNumber,
      List<ReceiptSummaryResponse> rcvReceiptSummaryResponseList,
      ArrayList<String> poQuantityMisMatchList,
      GLSDeliveryDetailsResponse glsDeliveryDetailsResponse) {
    if (isNull(glsDeliveryDetailsResponse)) {
      return;
    }
    final List<POS> glsPOSList = glsDeliveryDetailsResponse.getPos();
    for (POS aGlsPOS : glsPOSList) {
      final String glsPoNumber = aGlsPOS.getPoNumber();
      final Optional<ReceiptSummaryResponse> oReceiptSummaryForMatchingGlsPo =
          rcvReceiptSummaryResponseList
              .parallelStream()
              .filter(r -> glsPoNumber.equals(r.getPurchaseReferenceNumber()))
              .findFirst();
      if (oReceiptSummaryForMatchingGlsPo.isPresent()) {
        final ReceiptSummaryResponse receiptSummaryForMatchingGlsPo =
            oReceiptSummaryForMatchingGlsPo.get();
        buildPoQuantityMisMatchListForMatchedPo(
            deliveryNumber,
            poQuantityMisMatchList,
            receiptSummaryForMatchingGlsPo.getPurchaseReferenceNumber(),
            receiptSummaryForMatchingGlsPo.getPurchaseReferenceLineNumber(),
            receiptSummaryForMatchingGlsPo.getReceivedQty(),
            aGlsPOS);
      } else {
        LOGGER.error(
            "deliveryNumber={} GLS PO={} POS={} missing in Receiving receipts",
            deliveryNumber,
            glsPoNumber,
            aGlsPOS);
        poQuantityMisMatchList.add(glsPoNumber);
      }
    }
  }

  /**
   * Get Receiving POs List and from there check if its matching with GLS's PO's Qty
   *
   * @param deliveryNumber
   * @param receiptSummaryOfRequestedPO
   * @param poQuantityMisMatchList
   * @param glsDeliveryDetailsResponse
   */
  private void buildPoQuantityMisMatchListWithRcvAsBaseLine(
      Long deliveryNumber,
      List<ReceiptSummaryResponse> receiptSummaryOfRequestedPO,
      ArrayList<String> poQuantityMisMatchList,
      GLSDeliveryDetailsResponse glsDeliveryDetailsResponse) {
    for (ReceiptSummaryResponse receiptSummary : receiptSummaryOfRequestedPO) {
      final String receiptPo = receiptSummary.getPurchaseReferenceNumber();
      final Integer poLine = receiptSummary.getPurchaseReferenceLineNumber();
      final Long receivedQty = receiptSummary.getReceivedQty();
      Optional<POS> glsPOs =
          glsDeliveryDetailsResponse
              .getPos()
              .parallelStream()
              .filter(po -> receiptPo.equalsIgnoreCase(po.getPoNumber()))
              .findFirst();

      if (glsPOs.isPresent()) {
        buildPoQuantityMisMatchListForMatchedPo(
            deliveryNumber, poQuantityMisMatchList, receiptPo, poLine, receivedQty, glsPOs.get());
      } else {
        LOGGER.error(
            "deliveryNumber={} PO={} line={} Qty={} has quantity Mismatch(gls response missing PO and POLine)",
            deliveryNumber,
            receiptPo,
            poLine,
            receivedQty);

        if (receivedQty > 0) {
          poQuantityMisMatchList.add(receiptPo);
        }
      }
    }
  }

  private void buildPoQuantityMisMatchListForMatchedPo(
      Long deliveryNumber,
      ArrayList<String> poQuantityMisMatchList,
      String receiptPo,
      int receiptLine,
      Long receivedQty,
      POS pos) {
    AtomicReference<Long> glsQty = new AtomicReference<>();
    boolean hasQuantityMismatchForPoPoline =
        pos.getPolines()
            .stream()
            .anyMatch(
                line -> {
                  glsQty.set(line.getReceivedQty());
                  return line.getPoLineNumber() == receiptLine
                      && receivedQty.intValue() != line.getReceivedQty();
                });
    if (hasQuantityMismatchForPoPoline) {
      LOGGER.error(
          "deliveryNumber={} PO={} line={} has quantity Mismatch(gls={}, RCV receipt={})",
          deliveryNumber,
          receiptPo,
          receiptLine,
          glsQty.get(),
          receivedQty);

      poQuantityMisMatchList.add(receiptPo);
    }
  }

  protected String getDeliveryDocumentsByGtin(
      String url, HttpHeaders headers, RestConnector restConnector) throws ReceivingException {
    ResponseEntity<String> response;
    response = gmdRestCallResponse(url, headers, restConnector);
    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());
    return response.getBody();
  }

  public ResponseEntity<String> gmdRestCallResponse(
      String url, HttpHeaders headers, RestConnector restConnector) throws ReceivingException {
    ResponseEntity<String> response;
    try {
      response =
          restConnector.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
      LOGGER.info(
          ReceivingConstants.RESTUTILS_INFO_MESSAGE,
          url,
          "",
          isNull(response) ? "null" : response.getBody());
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          getStackTrace(e));
      gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(gdmError.getErrorMessage())
              .errorCode(gdmError.getErrorCode())
              .errorHeader(gdmError.getLocalisedErrorHeader())
              .errorKey(ExceptionCodes.PO_LINE_NOT_FOUND)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.NOT_FOUND)
          .errorResponse(errorResponse)
          .build();

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));
      gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);

      throw new GDMServiceUnavailableException(
          gdmError.getErrorMessage(), gdmError.getErrorCode(), gdmError.getErrorHeader());
    }
    if (isNull(response) || StringUtils.isEmpty(response.getBody())) {
      LOGGER.error("gdm response is null or empty body");
      gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(gdmError.getErrorMessage())
              .errorCode(gdmError.getErrorCode())
              .errorHeader(gdmError.getLocalisedErrorHeader())
              .errorKey(ExceptionCodes.PO_LINE_NOT_FOUND)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.NOT_FOUND)
          .errorResponse(errorResponse)
          .build();
    }
    return response;
  }

  /**
   * This method is responsible to get OSDR details for a delivery Number. The uom for all the OSDR
   * details is calculated based on the uom provided in method argument.
   *
   * @param deliveryNumber
   * @param uom
   * @param include
   * @return
   */
  public OsdrSummary getOsdrInformation(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      String userId,
      String uom,
      String include) {

    if (StringUtils.isBlank(include)) {
      return getOsdrSummary(
          deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, userId, uom);
    } else {
      return getOsdrSummaryWithIncludes(
          deliveryNumber,
          purchaseReferenceNumber,
          purchaseReferenceLineNumber,
          userId,
          uom,
          include);
    }
  }

  private OsdrSummary getOsdrSummaryWithIncludes(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      String userId,
      String uom,
      String include) {
    OsdrSummary osdrSummary;
    try {
      osdrSummary =
          getOsdrSummary(
              deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, userId, uom);
    } catch (Exception e) {
      LOGGER.warn(
          "error getting OSRD for deliveryNumber={}, po={}, poLine={}",
          deliveryNumber,
          purchaseReferenceNumber,
          purchaseReferenceLineNumber);
      osdrSummary = OsdrUtils.newOsdrSummary(deliveryNumber, userId);
    }
    processOsdrSummaryIncludes(deliveryNumber, include, osdrSummary);
    return osdrSummary;
  }

  private OsdrSummary getOsdrSummary(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      String userId,
      String uom) {

    boolean isRdcEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RDC_ENABLED, false);
    List<Receipt> receipts = null;
    if (!isRdcEnabled) {
      receipts =
          receiptService.getReceiptSummary(
              deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);
    }

    final OsdrService osdrService =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(), OSDR_SERVICE, OsdrService.class);
    OsdrSummary osdrSummary = osdrService.getOsdrDetails(deliveryNumber, receipts, uom, userId);
    return osdrSummary;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  void processOsdrSummaryIncludes(Long deliveryNumber, String include, OsdrSummary osdrSummary) {
    switch (include) {
      case RCV_GDM_INCLUDE_OPEN_INSTRUCTIONS:
        LOGGER.info("osdrSummary include={} for deliveryNumber={}", include, deliveryNumber);
        List<Instruction> openInstructions =
            instructionRepository
                .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(deliveryNumber);
        List<Map<String, Object>> openInstructionPoAndPoLines = new ArrayList<>();
        for (Instruction instruction : openInstructions) {
          Map<String, Object> openInstructionPoAndPoLine = new HashMap<>();
          openInstructionPoAndPoLine.put(RCV_GDM_PO, instruction.getPurchaseReferenceNumber());
          openInstructionPoAndPoLine.put(
              RCV_GDM_PO_LINE, instruction.getPurchaseReferenceLineNumber());
          openInstructionPoAndPoLines.add(openInstructionPoAndPoLine);
        }
        osdrSummary.setOpenInstructions(openInstructionPoAndPoLines);
        break;
      default:
        LOGGER.warn("No implementation for include={}, deliveryNumber={}", include, deliveryNumber);
    }
  }

  public void recordPalletReject(RejectPalletRequest rejectPalletRequest) {
    LOGGER.info("Got Pallet Reject Request . body = {} ", gson.toJson(rejectPalletRequest));
    Receipt receipt = receiptService.saveReceipt(rejectReceipt(rejectPalletRequest));
    LOGGER.info("Receipt is successfully created for the reject : {}", gson.toJson(receipt));
  }

  private Receipt rejectReceipt(RejectPalletRequest rejectPalletRequest) {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(rejectPalletRequest.getDeliveryNumber());
    receipt.setDoorNumber(rejectPalletRequest.getDoorNumber());
    receipt.setPurchaseReferenceNumber(rejectPalletRequest.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(rejectPalletRequest.getPurchaseReferenceLineNumber());
    receipt.setVnpkQty(rejectPalletRequest.getVnpkQty());
    receipt.setWhpkQty(rejectPalletRequest.getWhpkQty());
    receipt.setQuantity(rejectPalletRequest.getRejectedQty());
    receipt.setQuantityUom(rejectPalletRequest.getRejectedUOM());
    // Receiving Qty == RejectedQty . Hence, sum of qty receiving in each = 0
    receipt.setEachQty(0);
    receipt.setFbRejectedQty(rejectPalletRequest.getRejectedQty());
    receipt.setFbRejectedQtyUOM(rejectPalletRequest.getRejectedUOM());
    receipt.setFbRejectedReasonCode(OSDRCode.valueOf(rejectPalletRequest.getRejectedReasonCode()));
    rejectPalletRequest.setRejectionComment(rejectPalletRequest.getRejectionComment());
    return receipt;
  }

  public void setVendorComplianceDateOnGDM(
      String itemNumber, VendorComplianceRequestDates requestBody) throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.set(
        ReceivingConstants.CONTENT_TYPE,
        ReceivingConstants.GDM_UPADATE_ITEM_VERIFIED_ON_ACCEPT_TYPE);

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.ITEM_NUMBER, itemNumber);

    ResponseEntity<String> response = null;

    response =
        restUtils.put(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_REQUEST_UPDATE_ITEM_VERIFIED_ON,
            httpHeaders,
            pathParams,
            gson.toJson(requestBody));

    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
      if (response.getStatusCode().is5xxServerError()) {
        throw new ReceivingException(
            ReceivingException.GDM_SERVICE_DOWN,
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE);
      } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
        gdmError = GdmErrorCode.getErrorValue(ReceivingException.VENDOR_COMPLAINT_ITEM_MISSING);
        throw new ReceivingException(
            gdmError.getErrorMessage(),
            HttpStatus.NOT_FOUND,
            gdmError.getErrorCode(),
            gdmError.getErrorHeader());
      } else if (response.getStatusCode() == BAD_REQUEST) {
        throw new ReceivingException(
            ReceivingException.VENDOR_UPDATE_DATE_BAD_REQUEST,
            BAD_REQUEST,
            ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
      }
    }
  }

  /**
   * This method fetches item catalog update logs by delivery and upc number
   *
   * @param deliveryNumber
   * @param itemUPC
   * @return List<ItemCatalogUpdateLog>
   */
  @InjectTenantFilter
  @Transactional
  public List<ItemCatalogUpdateLog> getItemCatalogUpdateLogByDeliveryAndUpc(
      Long deliveryNumber, String itemUPC) {
    return itemCatalogRepository.findByDeliveryNumberAndNewItemUPC(deliveryNumber, itemUPC);
  }

  /**
   * This method can be used to publish delivery status
   *
   * @param deliveryInfo
   * @param headers
   * @throws ReceivingException
   */
  public void publishDeliveryStatus(DeliveryInfo deliveryInfo, HttpHeaders headers)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }

  @Counted(
      name = "getContainerSsccDetailsHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryServiceImpl",
      level3 = "getContainerSsccDetails")
  @Timed(
      name = "getContainerSsccDetailsTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryServiceImpl",
      level3 = "getContainerSsccDetails")
  @ExceptionCounted(
      name = "getContainerSsccDetailsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "getContainerSsccDetails")
  public Optional<List<DeliveryDocument>> getContainerSsccDetails(
      String deliveryNumber, String sscc, HttpHeaders headers) throws ReceivingException {
    SsccScanResponse ssccScanResponse = getSsccScanDetails(deliveryNumber, sscc, headers);

    ssccScanResponse = getSsccDetailsWithDocTypeASN(deliveryNumber, sscc, headers, ssccScanResponse);

    if (ssccScanResponse == null) return Optional.empty();

    if (!CollectionUtils.isEmpty(ssccScanResponse.getErrors())
        && ssccScanResponse
            .getErrors()
            .get(0)
            .getErrorCode()
            .equals(ReceivingException.GDM_SSCC_SCAN_NOT_FOUND_ERROR_CODE)) {
      return ssccNotAvailableInGDM(deliveryNumber);
    }

    AsnToDeliveryDocumentsCustomMapper asnToDeliveryDocumentsCustomMapper =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.ASN_CUSTOM_MAPPER,
            AsnToDeliveryDocumentsCustomMapper.class);
    asnToDeliveryDocumentsCustomMapper.checkIfPartialContent(ssccScanResponse.getErrors());
    return Optional.of(
        asnToDeliveryDocumentsCustomMapper.mapGdmResponse(ssccScanResponse, sscc, headers));
  }


  public SsccScanResponse getSsccDetailsWithDocTypeASN(
      String deliveryNumber, String sscc, HttpHeaders headers, SsccScanResponse ssccScanResponse) {
    if (null != ssccScanResponse
        && !CollectionUtils.isEmpty(ssccScanResponse.getErrors())
        && ssccScanResponse
            .getErrors()
            .get(0)
            .getErrorCode()
            .equals(ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND)
        && tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.AUTO_SWITCH_EPCIS_TO_ASN,
            false)) {
      headers.set(
          ReceivingConstants.Rx_ASN_RCV_OVER_RIDE_KEY,
          ReceivingConstants.Rx_ASN_RCV_OVER_RIDE_VALUE);
      ssccScanResponse = getSsccScanDetails(deliveryNumber, sscc, headers);

    }
    return ssccScanResponse;
  }

  @Counted(
      name = "linkDeliveryWithShipmentHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryServiceImpl",
      level3 = "linkDeliveryWithShipment")
  @Timed(
      name = "linkDeliveryWithShipmentTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "linkDeliveryWithShipment")
  @ExceptionCounted(
      name = "linkDeliveryWithShipmentExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "linkDeliveryWithShipment")
  public String linkDeliveryWithShipment(
      String deliveryNumber,
      String shipmentNumber,
      String shipmentDocumentId,
      HttpHeaders requestHeaders) {
    Map<String, Object> httpHeadersMapWithTenantData =
        ReceivingUtils.getForwardablHeaderWithTenantData(requestHeaders);
    Map<String, String> httpHeadersMap = new HashMap<>();
    for (Map.Entry<String, Object> entry : httpHeadersMapWithTenantData.entrySet()) {
      httpHeadersMap.put(entry.getKey(), entry.getValue().toString());
    }

    httpHeadersMap.put(ReceivingConstants.ACCEPT, MediaType.ALL_VALUE);
    httpHeadersMap.put(
        ReceivingConstants.CONTENT_TYPE,
        ReceivingConstants.GDM_LINK_DELIVERY_WITH_SHIPMENT_ACCEPT_TYPE);

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    pathParams.put(ReceivingConstants.SHIPMENT_NUMBER, shipmentNumber);
    String url =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_LINK_SHIPMENT_DELIVERY,
                pathParams)
            .toString();
    ResponseEntity<String> response;
    try {
      response =
          simpleRestConnector.put(
              url,
              gson.toJson(
                  ShipmentDocument.builder().shipmentDocumentId(shipmentDocumentId).build()),
              httpHeadersMap,
              String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SHIPMENT_DELIVERY_LINK_FAILURE,
          ReceivingConstants.GDM_SHIPMENT_DELIVERY_LINK_FAILURE);

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));

      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }
    return response.getBody();
  }

  @Counted(
      name = "findDeliveryDocumentBySSCCWithShipmentLinkingHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "findDeliveryDocumentBySSCCWithShipmentLinking")
  @Timed(
      name = "findDeliveryDocumentBySSCCWithShipmentLinkingTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "findDeliveryDocumentBySSCCWithShipmentLinking")
  @ExceptionCounted(
      name = "findDeliveryDocumentBySSCCWithShipmentLinkinExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "findDeliveryDocumentBySSCCWithShipmentLinking")
  public List<DeliveryDocument> findDeliveryDocumentBySSCCWithShipmentLinking(
      String deliveryNumber, String sscc, HttpHeaders httpHeaders) throws ReceivingException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());

    Optional<List<DeliveryDocument>> deliveryDocumentsBySSCCOptional =
        getContainerSsccDetails(deliveryNumber, sscc, httpHeaders);
    if (!deliveryDocumentsBySSCCOptional.isPresent()) {
      Shipment searchShipment = searchShipment(deliveryNumber, sscc, httpHeaders);
      linkDeliveryWithShipment(
          deliveryNumber,
          searchShipment.getShipmentNumber(),
          searchShipment.getDocumentId(),
          httpHeaders);
      deliveryDocumentsBySSCCOptional = getContainerSsccDetails(deliveryNumber, sscc, httpHeaders);
      if (!deliveryDocumentsBySSCCOptional.isPresent()) {
        LOGGER.error(
            "Got 204 response code from GDM even after linking shipment for delivery : {}, ssccCode : {}",
            deliveryNumber,
            sscc);
        LOGGER.info(
            "RECEIVING_BY_SCANNING_SSCC_ASN_NOT_FOUND delivery={}, SSCC={}, ",
            deliveryNumber,
            sscc);
        throwSsccNotFoundErrorMessage(sscc);
      }
    }
    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());
    return deliveryDocumentsBySSCCOptional.get();
  }

  private void throwSsccNotFoundErrorMessage(String sscc) {
    boolean isDsdcSsccPacksAvailableInGdm =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false);
    String ssccNotFoundErrorCode =
        isDsdcSsccPacksAvailableInGdm
            ? ExceptionCodes.GDM_DSDC_OR_SSTK_SSCC_NOT_FOUND
            : ExceptionCodes.GDM_SSCC_NOT_FOUND;
    throw new ReceivingBadDataException(
        ssccNotFoundErrorCode,
        String.format(ReceivingConstants.GDM_SHIPMENT_NOT_FOUND, sscc),
        sscc);
  }
  /**
   * Get TrailerZone temperature by delivery number
   *
   * @param deliveryNumber
   * @return
   * @throws ReceivingException
   */
  public GDMDeliveryTrailerTemperatureInfo getTrailerZoneTemperature(
      Long deliveryNumber, HttpHeaders forwardableHttpHeaders) throws ReceivingException {

    return gdmRestApiClient.buildTrailerZoneTemperatureResponse(
        deliveryNumber, forwardableHttpHeaders);
  }

  /**
   * Publishes "OPEN" delivery status when it is not null and "ARV"
   *
   * @param deliveryNumber
   * @param deliveryStatus
   * @param headers
   */
  @CaptureMethodMetric
  public void publishArrivedDeliveryStatusToOpen(
      long deliveryNumber, String deliveryStatus, HttpHeaders headers) {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_DELIVERY_STATUS_PUBLISH_IN_INSTRUCTION_SEARCH_DISABLED)
        && Objects.nonNull(deliveryStatus)
        && ARV.toString().equalsIgnoreCase(deliveryStatus)) {
      deliveryStatusPublisher.publishDeliveryStatus(
          deliveryNumber,
          DeliveryStatus.OPEN.toString(),
          null,
          ReceivingUtils.getForwardablHeader(headers));
    }
  }

  /**
   * @param deliveryNumber
   * @param headers
   * @return DeliverySummary
   * @throws ReceivingException
   */
  public DeliverySummary getDeliverySummary(Long deliveryNumber, HttpHeaders headers)
      throws ReceivingException {
    DeliverySummary deliverySummary = new DeliverySummary();

    try {
      Set<String> poList = new HashSet<>();
      Set<Long> itemList = new HashSet<>();
      Set<String> finalizedPoList = new HashSet<>();
      DeliveryWithOSDRResponse deliveryWithOSDRResponse = null;

      boolean isReceiveAllEnabled =
          tenantSpecificConfigReader.getConfiguredFeatureFlag(
              getFacilityNum().toString(), IS_RECEIVE_ALL_ENABLED, false);

      // Get delivery info from GDM
      deliveryWithOSDRResponse =
          gdmRestApiClient.getDelivery(
              deliveryNumber, ReceivingUtils.getForwardablHeaderWithTenantData(headers));
      if (deliveryWithOSDRResponse != null
          && !CollectionUtils.isEmpty(deliveryWithOSDRResponse.getPurchaseOrders())) {
        for (PurchaseOrderWithOSDRResponse po : deliveryWithOSDRResponse.getPurchaseOrders()) {
          poList.add(po.getPoNumber());
          if (isReceiveAllEnabled) {
            for (PurchaseOrderLineWithOSDRResponse poLine : po.getLines()) {
              itemList.add(poLine.getItemDetails().getNumber());
            }
          }
        }
      }

      // Get trailerTempZonesRecorded
      Integer trailerTempZonesRecorded =
          gdmRestApiClient.getTrailerTempZonesRecorded(deliveryNumber, headers);

      // Get osdrMaster receipts
      List<Receipt> osdrMasterReceipts =
          receiptRepository.findByDeliveryNumberAndOsdrMaster(deliveryNumber, 1);
      for (Receipt masterReceipt : osdrMasterReceipts) {
        if (isPoFinalized(masterReceipt)) {
          finalizedPoList.add(masterReceipt.getPurchaseReferenceNumber());
        }
      }

      // Prepare the deliverySummary response
      deliverySummary.setTotalTrailerTempZones(DEFAULT_TRAILER_TEMP_ZONES);
      deliverySummary.setTrailerTempZonesRecorded(trailerTempZonesRecorded);
      deliverySummary.setTotalPOsCount(poList.size());
      deliverySummary.setConfirmedPOsCount(finalizedPoList.size());
      deliverySummary.setIsReceiveAll(Boolean.FALSE);
      // Given delivery with single item on one or more PO's for entire delivery
      // And delivery and PO not in finalized state
      // When user gets the delivery summary
      // Then return "isReceiveAll":true
      if (isReceiveAllEnabled && itemList.size() == 1) {
        String deliveryStatus = deliveryWithOSDRResponse.getStatusInformation().getStatus();
        LOGGER.info(
            "deliveryNumber: {} deliveryStatus: {} totalPos: {} finalizedPos: {}",
            deliveryNumber,
            deliveryStatus,
            poList.size(),
            finalizedPoList.size());

        // Delivery and PO should not be in finalized state
        if (poList.size() != finalizedPoList.size()
            && !DeliveryStatus.FNL.name().equalsIgnoreCase(deliveryStatus)) {
          deliverySummary.setIsReceiveAll(Boolean.TRUE);
        }
      }
    } catch (GDMRestApiClientException e) {
      LOGGER.error("Error while getting delivery info :{}", e.getMessage());
      throw new ReceivingDataNotFoundException(
          ReceivingException.DELIVERY_NOT_FOUND,
          String.format(ReceivingException.DELIVERY_NOT_FOUND_ERROR_MESSAGE, deliveryNumber));
    }

    return deliverySummary;
  }

  public void closeTrailer(long deliveryNumber, HttpHeaders headers) {
    // prepare messageHeaders
    Map<String, Object> messageHeaders = ReceivingUtils.getForwardablHeader(headers);
    messageHeaders.put(DELIVERY_STATUS, DeliveryStatus.TRAILER_CLOSE.name());

    // prepare messageBody
    DeliveryInfo messageBody = new DeliveryInfo();
    messageBody.setDeliveryNumber(deliveryNumber);
    messageBody.setDeliveryStatus(DeliveryStatus.TRAILER_CLOSE.name());
    messageBody.setUserId(headers.getFirst(USER_ID_HEADER_KEY));

    // publish the message
    deliveryStatusPublisher.publishDeliveryStatusMessage(messageHeaders, messageBody);
  }

  public DeliveryStatusSummary getDeliveryStatusSummary(Long deliveryNumber)
      throws ReceivingDataNotFoundException {
    DeliveryStatusSummary deliveryStatusSummary = new DeliveryStatusSummary();
    Optional<DeliveryMetaData> _deliveryMetaData =
        deliveryMetaDataService.findByDeliveryNumber(deliveryNumber.toString());
    if (_deliveryMetaData.isPresent()) {
      DeliveryMetaData deliveryMetaData = _deliveryMetaData.get();
      List<DeliveryLifeCycleInformation> deliveryLifeCycleInformation = new ArrayList<>();
      if (deliveryMetaData.getDeliveryStatus().equals(DeliveryStatus.UNLOADING_COMPLETE)) {
        deliveryLifeCycleInformation.add(
            new DeliveryLifeCycleInformation(
                deliveryMetaData.getDeliveryStatus(),
                deliveryMetaData.getUnloadingCompleteDate(),
                deliveryMetaData.getUpdatedBy()));
      } else if (deliveryMetaData.getDeliveryStatus().equals(COMPLETE)) {
        deliveryLifeCycleInformation.add(
            new DeliveryLifeCycleInformation(
                DeliveryStatus.UNLOADING_COMPLETE,
                deliveryMetaData.getUnloadingCompleteDate(),
                deliveryMetaData.getUpdatedBy()));
        deliveryLifeCycleInformation.add(
            new DeliveryLifeCycleInformation(
                deliveryMetaData.getDeliveryStatus(),
                deliveryMetaData.getLastUpdatedDate(),
                deliveryMetaData.getUpdatedBy()));
      } else {
        deliveryLifeCycleInformation.add(
            new DeliveryLifeCycleInformation(
                deliveryMetaData.getDeliveryStatus(),
                deliveryMetaData.getLastUpdatedDate(),
                deliveryMetaData.getUpdatedBy()));
      }
      deliveryStatusSummary.setStatus(200);
      deliveryStatusSummary.setLifeCycleInformation(deliveryLifeCycleInformation);
    } else {
      throw new ReceivingDataNotFoundException(
          ReceivingException.DELIVERY_NOT_FOUND,
          String.format(ReceivingException.DELIVERY_NOT_FOUND_ERROR_MESSAGE, deliveryNumber));
    }
    return deliveryStatusSummary;
  }

  /**
   * Completes the given delivery and POs on it
   *
   * @param deliveryNumber
   * @param httpHeaders
   * @return DeliveryInfo
   * @throws ReceivingException
   */
  public DeliveryInfo completeAll(Long deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    return tenantSpecificConfigReader
        .getConfiguredInstance(
            getFacilityNum().toString(),
            COMPLETE_DELIVERY_PROCESSOR,
            CompleteDeliveryProcessor.class)
        .completeDeliveryAndPO(deliveryNumber, httpHeaders);
  }

  public ReceiveIntoOssResponse receiveIntoOss(
      Long deliveryNumber, ReceiveIntoOssRequest receiveIntoOssRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    return tenantSpecificConfigReader
        .getConfiguredInstance(
            getFacilityNum().toString(),
            RECEIVE_INSTRUCTION_HANDLER_KEY,
            ReceiveInstructionHandler.class)
        .receiveIntoOss(deliveryNumber, receiveIntoOssRequest, httpHeaders);
  }

  public void deliveryEventTypePublisher(
      long deliveryNumber, String deliveryEventType, HttpHeaders headers)
      throws ReceivingBadDataException {

    // hook for deliveryEventType publisher market specific execution
    DeliveryUnloaderProcessor deliveryUnloaderProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_UNLOADER_PROCESOR,
            DEFAULT_DELIVERY_UNLOADER_PROCESSOR,
            DeliveryUnloaderProcessor.class);
    deliveryUnloaderProcessor.publishDeliveryEvent(deliveryNumber, deliveryEventType, headers);
  }

  public void saveUnloaderInfo(UnloaderInfoDTO unloaderInfo, HttpHeaders headers)
      throws ReceivingBadDataException {

    // hook for unloaderInfo persist market specific execution
    DeliveryUnloaderProcessor deliveryUnloaderProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_UNLOADER_PROCESOR,
            DEFAULT_DELIVERY_UNLOADER_PROCESSOR,
            DeliveryUnloaderProcessor.class);
    deliveryUnloaderProcessor.saveUnloaderInfo(unloaderInfo, headers);
  }

  public List<UnloaderInfo> getUnloaderInfo(
      Long deliveryNumber, String poNumber, Integer poLineNumber) throws ReceivingBadDataException {

    // hook for get unloaderInfo market specific execution
    DeliveryUnloaderProcessor deliveryUnloaderProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_UNLOADER_PROCESOR,
            DEFAULT_DELIVERY_UNLOADER_PROCESSOR,
            DeliveryUnloaderProcessor.class);
    return deliveryUnloaderProcessor.getUnloaderInfo(deliveryNumber, poNumber, poLineNumber);
  }

  /**
   * This is abstract method it will fetch PO/PO line as response from GDM.
   *
   * @param deliveryNumber delivery number
   * @param itemNumber itemNumber
   * @param headers http headers
   * @return GDM Documents as response
   * @throws ReceivingException
   */
  public abstract List<DeliveryDocument> findDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException;

  /**
   * This method provides a dsdc sscc response with error code if SSCC is not available in GDM.
   *
   * @param deliveryNumber delivery number
   * @return ssccScanResponse as response
   */
  private SsccScanResponse getDsdcSsccNotFoundResponse(String deliveryNumber) {

    SsccScanResponse ssccScanResponse = new SsccScanResponse();
    List<Error> errors = new ArrayList<>();
    errors.add(
        new Error(
            ReceivingException.GDM_SSCC_SCAN_NOT_FOUND_ERROR_CODE,
            Arrays.asList(ReceivingConstants.GDM_SSCC_SCAN_NOT_FOUND_ERROR_MESSAGE)));
    ssccScanResponse.setErrors(errors);
    Delivery delivery = new Delivery();
    delivery.setDeliveryNumber(Long.valueOf(deliveryNumber));
    List<PurchaseOrder> purchaseOrders = new ArrayList<>();
    delivery.setPurchaseOrders(purchaseOrders);
    List<Shipment> shipments = new ArrayList<>();
    Shipment shipment = new Shipment();
    shipments.add(shipment);
    List<Pack> packs = new ArrayList<>();
    Pack pack = new Pack();
    packs.add(pack);
    ssccScanResponse.setDelivery(delivery);
    ssccScanResponse.setShipments(shipments);
    ssccScanResponse.setPacks(packs);
    return ssccScanResponse;
  }

  /**
   * This method gives a delivery with ASN number 'NOT FOUND' which acts as a check to fall back to
   * rds flow for dsdc if no response from GDM
   *
   * @param deliveryNumber delivery number
   * @return delivery documents as response
   */
  private Optional<List<DeliveryDocument>> ssccNotAvailableInGDM(String deliveryNumber) {
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseRefType(ReceivingConstants.DSDC_CHANNEL_METHODS_FOR_RDC);
    deliveryDocumentLine.setQtyUOM(ReceivingConstants.Uom.VNPK);
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));
    deliveryDocument.setDeliveryNumber(Long.parseLong(deliveryNumber));
    deliveryDocument.setPoTypeCode(DSDC_PO_TYPE_CODE);
    deliveryDocument.setAsnNumber(GDM_SSCC_SCAN_ASN_NOT_FOUND);
    deliveryDocument.setDeliveryStatus(DeliveryStatus.WRK);
    deliveryDocuments.add(deliveryDocument);
    return Optional.of(deliveryDocuments);
  }

  public String fetchDeliveriesByStatusUpcAndPoNumber(
          List<String> deliveryStatusList, String upcNumber, String facilityNumber, int pageNumber, List<String> poNumberList)
          throws ReceivingException {
    throw new ReceivingNotImplementedException(
            ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
            ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  public String fetchDeliveriesByStatusAndUpc(
          List<String> deliveryStatusList, String upcNumber, String facilityNumber, int pageNumber)      throws ReceivingException {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  public String fetchDeliveries(
      List<Long> deliveryList, String facilityNumber, String countryCode, int pageNumber)
      throws ReceivingException {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }

  public RejectionMetadata getRejectionMetadata(Long deliveryNumber)
      throws ReceivingBadDataException {
    Rejections rejections =
        rejectionsRepository
            .findTopByDeliveryNumberAndFacilityNumAndFacilityCountryCodeOrderByLastChangedTsDesc(
                deliveryNumber, getFacilityNum(), getFacilityCountryCode());
    LOGGER.info("Fetch Rejection from DB {}", rejections);
    if (Objects.nonNull(rejections) && rejections.getEntireDeliveryReject())
      return buildRejectionMetadataResponse(rejections);
    return new RejectionMetadata();
  }

  private RejectionMetadata buildRejectionMetadataResponse(Rejections rejections) {
    return RejectionMetadata.builder()
        .claimType(rejections.getClaimType())
        .isFullLoadProduceRejection(
            Objects.nonNull(rejections.getFullLoadProduceRejection())
                ? rejections.getFullLoadProduceRejection()
                : Boolean.FALSE)
        .isRejectEntireDelivery(
            Objects.nonNull(rejections.getEntireDeliveryReject())
                ? rejections.getEntireDeliveryReject()
                : Boolean.FALSE)
        .rejectionReason(rejections.getReason())
        .build();
  }

  public abstract DeliveryDoorSummary getDoorStatus(String doorNumber) throws ReceivingException;
}
