package com.walmart.move.nim.receiving.sib.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchRequest;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.sib.model.gdm.Criteria;
import com.walmart.move.nim.receiving.sib.model.gdm.GDMDeliveryDataRequest;
import com.walmart.move.nim.receiving.sib.model.gdm.GDMDeliveryDataResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

public class StoreDeliveryService extends DeliveryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoreDeliveryService.class);

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  private Gson gson;

  @ManagedConfiguration private AppConfig appConfig;

  public StoreDeliveryService() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  public List<ASNDocument> getAsnDocuments(Long deliveryNumber) {
    List<ASNDocument> asnDocumentList = new ArrayList<>();

    Set<String> shipmentIds;
    try {
      Delivery delivery = getGDMData(deliveryNumber);
      shipmentIds =
          delivery.getShipments().stream().map(Shipment::getDocumentId).collect(Collectors.toSet());
      LOGGER.info("Retrieved shipmentIds for delivery={} are {}", delivery, shipmentIds);
    } catch (Exception e) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format("Unable to fetch delivery from GDM, deliveryNumber=%s", deliveryNumber));
    }

    shipmentIds.forEach(
        shipmentId -> {
          asnDocumentList.add(getGDMData(deliveryNumber, shipmentId));
        });
    if (CollectionUtils.isEmpty(asnDocumentList)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.NO_DELIVERY_FOUND, "Document not found for delivery:" + deliveryNumber);
    }

    return asnDocumentList;
  }

  public ASNDocument getGDMData(Long deliveryNumber, String shipmentDocumentId) {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(DELIVERY_NUMBER, deliveryNumber.toString());
    pathParams.put(SHIPMENT_NUMBER, shipmentDocumentId);
    URI urlBuilder =
        UriComponentsBuilder.fromUriString(appConfig.getGdmBaseUrl() + GDM_LINK_SHIPMENT_DELIVERY)
            .buildAndExpand(pathParams)
            .toUri();

    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(HttpHeaders.ACCEPT, "application/vnd.DeliveryShipmentSearchZipResponse1+json");
    headers.add("Compression-Type", "gzip");

    String shipmentDocuments =
        restConnector
            .exchange(
                urlBuilder.toString(), HttpMethod.GET, new HttpEntity<>(headers), String.class)
            .getBody();

    ASNDocument shipmentDocs = gson.fromJson(shipmentDocuments, ASNDocument.class);
    if (Objects.isNull(shipmentDocs)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format(
              "Shipment = %s from delivery = %s is not available",
              deliveryNumber, shipmentDocumentId));
    }
    return shipmentDocs;
  }

  public Delivery getGDMData(Long deliveryNumber) {

    StringBuilder urlBuilder =
        new StringBuilder(appConfig.getGdmBaseUrl())
            .append("/api")
            .append("/deliveries/")
            .append(deliveryNumber);

    return restConnector
        .exchange(
            urlBuilder.toString(),
            HttpMethod.GET,
            new HttpEntity<>(ReceivingUtils.getHeaders()),
            Delivery.class)
        .getBody();
  }

  public GDMDeliveryDataResponse getDeliveryMetadata(Long deliveryNumber) {
    GDMDeliveryDataRequest gdmDeliveryDataRequest = new GDMDeliveryDataRequest();
    Criteria criteria = new Criteria();
    criteria.setDeliveryNumbers(Arrays.asList(deliveryNumber));
    gdmDeliveryDataRequest.setCriteria(criteria);

    StringBuilder urlBuilder =
        new StringBuilder(appConfig.getGdmBaseUrl())
            .append("/api")
            .append("/deliveries/")
            .append("/search/");

    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    headers.remove(HttpHeaders.CONTENT_TYPE);
    headers.add(HttpHeaders.CONTENT_TYPE, "application/vnd.SearchDeliveryHeader1+json");
    headers.add("Compression-Type", "gzip");

    try {
      ResponseEntity<String> gdmDeliveryDocuments =
          restConnector.exchange(
              urlBuilder.toString(),
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(gdmDeliveryDataRequest), headers),
              String.class);

      GDMDeliveryDataResponse gdmDeliveryData =
          gson.fromJson(gdmDeliveryDocuments.getBody(), GDMDeliveryDataResponse.class);
      if (Objects.isNull(gdmDeliveryDocuments)) {
        throw new ReceivingDataNotFoundException(
            ExceptionCodes.DELIVERY_NOT_FOUND,
            String.format("delivery = %s is not available", deliveryNumber));
      }
      return gdmDeliveryData;
    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == HttpStatus.BAD_REQUEST.value()) {
        LOGGER.error(
            ReceivingConstants.BAD_RESPONSE_ERROR_MSG,
            urlBuilder,
            deliveryNumber,
            e.getResponseBodyAsString(),
            ExceptionUtils.getStackTrace(e));
        throw new ReceivingBadDataException(
            ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
            String.format(
                "Delivery Information not found",
                e.getRawStatusCode(),
                e.getResponseBodyAsString()));
      } else {
        throw e;
      }
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(ExceptionCodes.GDM_ERROR, "Error accessing GDM API");
    }
  }

  public GdmDeliverySearchResponse searchDelivery(
      GdmDeliverySearchRequest gdmDeliverySearchRequest) {
    StringBuilder uri =
        new StringBuilder(appConfig.getGdmBaseUrl())
            .append("/api")
            .append("/deliveries")
            .append("/shipments");
    LOGGER.info("Delivery Search URI = {}", uri);

    HttpHeaders requestHeaders = ReceivingUtils.getHeaders();
    requestHeaders.set(
        HttpHeaders.ACCEPT, "application/vnd.PalletPackDeliveryShipmentSearchResponse2+json");

    ResponseEntity<String> deliveryResponseEntity = null;
    try {
      deliveryResponseEntity =
          restConnector.exchange(
              uri.toString(),
              HttpMethod.POST,
              new HttpEntity<>(gdmDeliverySearchRequest, requestHeaders),
              String.class);
    } catch (RestClientResponseException ex) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestHeaders,
          ex.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(ex));
      if (204 == ex.getRawStatusCode()) {
        throw new ReceivingDataNotFoundException(
            ReceivingException.GDM_SEARCH_DELIVERY_ERROR_CODE,
            ReceivingException.DELIVERY_NOT_FOUND);
      }
      throw new ReceivingInternalException(
          ReceivingException.GDM_SEARCH_DELIVERY_ERROR_CODE, ReceivingException.GDM_SERVICE_DOWN);
    } catch (ResourceAccessException ex) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestHeaders,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(ex));
      throw new ReceivingInternalException(
          ReceivingException.GDM_SEARCH_DELIVERY_ERROR_CODE, ReceivingException.GDM_SERVICE_DOWN);
    }
    LOGGER.info(
        "Response from GDM for uri : {}, response body : {}",
        uri,
        deliveryResponseEntity.getBody());
    if (deliveryResponseEntity.getBody() != null) {
      return gson.fromJson(deliveryResponseEntity.getBody(), GdmDeliverySearchResponse.class);
    }
    return null;
  }

  @Override
  public String findDeliveryDocument(long deliveryNumber, String upcNumber, HttpHeaders headers)
      throws ReceivingException {
    throw new ReceivingException("NOT_IMPLEMENTED", HttpStatus.BAD_REQUEST);
  }

  @Override
  public Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {
    throw new ReceivingException("NOT_IMPLEMENTED", HttpStatus.BAD_REQUEST);
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
