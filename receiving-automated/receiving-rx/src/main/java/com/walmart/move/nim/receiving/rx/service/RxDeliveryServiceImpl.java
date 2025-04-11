package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE_v4;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GDM_UNIT_SERIAL_INFO_NOT_FOUND;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.UnitSerialRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.rx.builders.RxDeliveryLabelBuilder;
import com.walmart.move.nim.receiving.rx.common.TwoDScanAsnDeliveryDocumentMapper;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

public class RxDeliveryServiceImpl extends DeliveryService {

  private static final Logger log = LoggerFactory.getLogger(RxDeliveryServiceImpl.class);

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  @Autowired private TwoDScanAsnDeliveryDocumentMapper twoDScanAsnDeliveryDocumentMapper;
  @Autowired private RxDeliveryLabelBuilder rxDeliveryLabelBuilder;
  @Autowired private Gson gson;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Counted(
      name = "findDeliveryDocumentBySSCCWithLatestShipmentLinkingHitCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocumentBySSCCWithLatestShipmentLinking")
  @Timed(
      name = "findDeliveryDocumentBySSCCWithLatestShipmentLinking",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocumentBySSCCWithLatestShipmentLinking")
  @ExceptionCounted(
      name = "findDeliveryDocumentBySSCCWithLatestShipmentLinkingExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocumentBySSCCWithLatestShipmentLinking")
  public Optional<List<DeliveryDocument>> findDeliveryDocumentBySSCCWithLatestShipmentLinking(
      String deliveryNumber, String sscc, HttpHeaders httpHeaders) throws ReceivingException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());

    Optional<List<DeliveryDocument>> deliveryDocumentsBySSCCOptional;
    Shipment searchShipment = null;
    try {
      log.info(
          "Searching for latest shipments in problem flow by SSCC for delivery {} SSCC {}",
          deliveryNumber,
          sscc);
      searchShipment = searchShipment(deliveryNumber, sscc, httpHeaders);
    } catch (Exception e) {
      log.info(
          "No new shipments have found for delivery : {}, ssccCode : {}", deliveryNumber, sscc);
      return Optional.empty();
    }

    linkDeliveryWithShipment(
        deliveryNumber,
        searchShipment.getShipmentNumber(),
        searchShipment.getDocumentId(),
        httpHeaders);
    deliveryDocumentsBySSCCOptional = getContainerSsccDetails(deliveryNumber, sscc, httpHeaders);
    if (!deliveryDocumentsBySSCCOptional.isPresent()) {
      log.error(
          "Got 204 response code from GDM even after linking shipment for delivery : {}, ssccCode : {}",
          deliveryNumber,
          sscc);
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SSCC_NOT_FOUND,
          String.format(ReceivingConstants.GDM_SHIPMENT_NOT_FOUND, sscc),
          sscc);
    }
    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());
    return deliveryDocumentsBySSCCOptional;
  }

  @Override
  @Counted(
      name = "findDeliveryDocumentHitCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocument")
  @Timed(
      name = "findDeliveryDocumentTimed",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocument")
  @ExceptionCounted(
      name = "findDeliveryDocumentExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocument")
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
    return getDeliveryDocumentsByGtin(url, headers, restConnector);
  }

  @Override
  public Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Override
  public DeliveryDoorSummary getDoorStatus(String doorNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  /**
   * This api returns delivery documents for the given delivery number, gtin and lot number.
   *
   * @param deliveryNumber
   * @param scannedDataMap
   * @param headers
   * @return List<DeliveryDocument>
   * @throws ReceivingException
   */
  @Counted(
      name = "findDeliveryDocumentsByGtinAndLotNumberHitCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocumentsByGtinAndLotNumber")
  @Timed(
      name = "findDeliveryDocumentsByGtinAndLotNumberTimed",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocumentsByGtinAndLotNumber")
  @ExceptionCounted(
      name = "findDeliveryDocumentsByGtinAndLotNumberExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocumentsByGtinAndLotNumber")
  public List<DeliveryDocument> findDeliveryDocumentsByGtinAndLotNumber(
      String deliveryNumber, Map<String, ScannedData> scannedDataMap, HttpHeaders headers)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    ScannedData gtinScannedData = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey());
    ScannedData lotNumberScannedData = scannedDataMap.get(ApplicationIdentifier.LOT.getKey());
    ScannedData serialScannedData = scannedDataMap.get(ApplicationIdentifier.SERIAL.getKey());

    String gtin = gtinScannedData.getValue();
    String lotNumber = lotNumberScannedData.getValue();
    String serialNumber = serialScannedData.getValue();
    String getShipmentsByGtinAndLotNumberResponseFromGdm =
        getShipmentsByGtinAndLotNumberFromGdm(
            deliveryNumber, gtin, lotNumber, serialNumber, headers);
    SsccScanResponse gtinAndLotNumberSearchResponse =
        gson.fromJson(getShipmentsByGtinAndLotNumberResponseFromGdm, SsccScanResponse.class);
    gtinAndLotNumberSearchResponse =
        getGtinAndSerialDetailsWithDocTypeASN(
            deliveryNumber, gtin, lotNumber, serialNumber, headers, gtinAndLotNumberSearchResponse);
    if (Objects.nonNull(gtinAndLotNumberSearchResponse)) {
      twoDScanAsnDeliveryDocumentMapper.checkIfPartialContent(
          gtinAndLotNumberSearchResponse.getErrors());
      deliveryDocumentList =
          twoDScanAsnDeliveryDocumentMapper.mapGdmResponse(
              gtinAndLotNumberSearchResponse, null, headers);
    }
    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());
    return deliveryDocumentList;
  }

  private SsccScanResponse getGtinAndSerialDetailsWithDocTypeASN(
      String deliveryNumber,
      String gtin,
      String lotNumber,
      String serialNumber,
      HttpHeaders headers,
      SsccScanResponse gtinAndLotNumberSearchResponse)
      throws ReceivingException {
    String gtinAndLotNumberStringResponse = null;
    if (Objects.nonNull(gtinAndLotNumberSearchResponse)
        && !CollectionUtils.isEmpty(gtinAndLotNumberSearchResponse.getErrors())) {
      if (gtinAndLotNumberSearchResponse
          .getErrors()
          .get(0)
          .getErrorCode()
          .equals(ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND)) {
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.AUTO_SWITCH_EPCIS_TO_ASN,
            false)) {
          headers.set(
              ReceivingConstants.Rx_ASN_RCV_OVER_RIDE_KEY,
              ReceivingConstants.Rx_ASN_RCV_OVER_RIDE_VALUE);
          gtinAndLotNumberStringResponse =
              getShipmentsByGtinAndLotNumberFromGdm(
                  deliveryNumber, gtin, lotNumber, serialNumber, headers);
        }
      }
    }
    if (null != gtinAndLotNumberStringResponse) {
      gtinAndLotNumberSearchResponse =
          gson.fromJson(gtinAndLotNumberStringResponse, SsccScanResponse.class);
    }
    return gtinAndLotNumberSearchResponse;
  }

  /**
   * This api gets shipment information from GDM for the given delivery number, gtin and lot number
   *
   * @param deliveryNumber
   * @param gtin
   * @param lotNumber
   * @param headers
   * @return String
   * @throws ReceivingBadDataException
   */
  @Counted(
      name = "getShipmentsByGtinAndLotNumberFromGdmHitCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "getShipmentsByGtinAndLotNumberFromGdm")
  @Timed(
      name = "getShipmentsByGtinAndLotNumberFromGdmTimed",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "getShipmentsByGtinAndLotNumberFromGdm")
  @ExceptionCounted(
      name = "getShipmentsByGtinAndLotNumberFromGdmExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "getShipmentsByGtinAndLotNumberFromGdm")
  public String getShipmentsByGtinAndLotNumberFromGdm(
      String deliveryNumber,
      String gtin,
      String lotNumber,
      String serialNumber,
      HttpHeaders headers)
      throws ReceivingBadDataException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
    headers.remove(ReceivingConstants.ACCEPT);
    headers.add(
        ReceivingConstants.ACCEPT, ReceivingConstants.GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RxConstants.ENABLE_ASN_SEARCH_GTIN_ONLY)) {
      headers.replace(
          ReceivingConstants.ACCEPT,
          Arrays.asList(ReceivingConstants.GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE_v3));
    }

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put(ReceivingConstants.KEY_GTIN, gtin);
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RxConstants.ENABLE_ASN_SEARCH_GTIN_ONLY)) {
      queryParams.put(ReceivingConstants.KEY_LOT_NUMBER, lotNumber);
    }
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED, false)) {
      headers.set(ReceivingConstants.ACCEPT, GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE_v4);
      queryParams.put(ReceivingConstants.KEY_SERIAL, serialNumber);
    }
    String uri = ReceivingConstants.GDM_FETCH_DELIVERY_DOC_BY_GTIN_AND_LOT_URI;

    if (ReceivingUtils.isAsnReceivingOverrideEligible(headers)) {
      headers.replace(
          ReceivingConstants.ACCEPT,
          Arrays.asList(ReceivingConstants.GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE_v3));
      queryParams.put(ReceivingConstants.KEY_LOT_NUMBER, lotNumber);
      queryParams.remove(ReceivingConstants.KEY_SERIAL);
    }

    String url =
        ReceivingUtils.replacePathParamsAndQueryParams(
                appConfig.getGdmBaseUrl() + uri, pathParams, queryParams)
            .toString();

    log.info("Going to get shipments details from GDM by using url: {}", url);
    ResponseEntity<String> response;
    try {
      response =
          restConnector.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SEARCH_FOR_DELIVERY_DOC_BY_GTIN_AND_LOT_FAILURE,
          String.format(
              RxConstants.GDM_SEARCH_FOR_DELIVERY_DOC_BY_GTIN_AND_LOT_FAILURE,
              deliveryNumber,
              gtin,
              lotNumber),
          deliveryNumber,
          gtin,
          lotNumber);
    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }
    log.info(
        "Received GDM response: {} for deliveryNumber: {}, gtin: {} and lotNumber search: {}",
        response.getBody(),
        deliveryNumber,
        gtin,
        lotNumber);

    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());
    return response.getBody();
  }

  public PrintLabelData prepareDeliveryLabelData(
      Long deliveryNumber, int count, HttpHeaders httpHeaders) {
    return rxDeliveryLabelBuilder.generateDeliveryLabel(deliveryNumber, count, httpHeaders);
  }

  /**
   * This api performs the following, 1. Get shipment information from GDM for the given delivery
   * number, gtin and lot number 2. If no shipment information is available then once again we
   * request GDM to do a global search shipment by delivery, gtin and lot number 3. Once the
   * delivery is linked with the shipment, we will fetch shipments for the same delivery number,
   * gtin and lot number 4. With these info, we will prepare delivery documents response and return
   * back to the caller. 5. Exception will be returned if there is any error or exception
   * encountered when processing above logic.
   *
   * @param deliveryNumber
   * @param scannedDataMap
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @Counted(
      name = "findDeliveryDocumentByGtinAndLotNumberWithShipmentLinkingHitCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocumentByGtinAndLotNumberWithShipmentLinking")
  @Timed(
      name = "findDeliveryDocumentByGtinAndLotNumberWithShipmentLinkingTimed",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocumentByGtinAndLotNumberWithShipmentLinking")
  @ExceptionCounted(
      name = "findDeliveryDocumentByGtinAndLotNumberWithShipmentLinkingExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "findDeliveryDocumentByGtinAndLotNumberWithShipmentLinking")
  public List<DeliveryDocument> findDeliveryDocumentByGtinAndLotNumberWithShipmentLinking(
      String deliveryNumber, Map<String, ScannedData> scannedDataMap, HttpHeaders httpHeaders)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList =
        findDeliveryDocumentsByGtinAndLotNumber(deliveryNumber, scannedDataMap, httpHeaders);
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    String lotNumber = scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue();
    if (CollectionUtils.isEmpty(deliveryDocumentList)) {
      List<Shipment> shipments =
          searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
              deliveryNumber, gtin, lotNumber, httpHeaders);
      for (Shipment shipment : shipments) {
        log.info(
            "Linking shipment: {} with deliveryNumber:{} for gtin:{} and lotNumber:{}",
            shipment.getShipmentNumber(),
            deliveryNumber,
            gtin,
            lotNumber);
        linkDeliveryWithShipment(
            deliveryNumber, shipment.getShipmentNumber(), shipment.getDocumentId(), httpHeaders);
      }
      deliveryDocumentList =
          findDeliveryDocumentsByGtinAndLotNumber(deliveryNumber, scannedDataMap, httpHeaders);
      if (CollectionUtils.isEmpty(deliveryDocumentList)) {
        log.error(
            "Got 204 response from GDM even after linking shipment with delivery: {}, gtin: {} and lotNumber:{}",
            deliveryNumber,
            gtin,
            lotNumber);
        throw new ReceivingBadDataException(
            ExceptionCodes.GDM_SEARCH_FOR_DELIVERY_DOC_BY_GTIN_AND_LOT_FAILURE,
            String.format(
                RxConstants.GDM_SEARCH_FOR_DELIVERY_DOC_BY_GTIN_AND_LOT_FAILURE,
                deliveryNumber,
                gtin,
                lotNumber),
            deliveryNumber,
            gtin,
            lotNumber);
      }
    }
    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());
    return deliveryDocumentList;
  }

  /**
   * This api performs the following, 1.Request GDM to do a global search shipment by delivery, gtin
   * and lot number 2. Once the delivery is linked with the shipment, we will fetch shipments for
   * the same delivery number, gtin and lot number 3. With these info, we will prepare delivery
   * documents response and return back to the caller. 4. Exception will be returned if there is any
   * error or exception encountered when processing above logic.
   *
   * @param deliveryNumber
   * @param scannedDataMap
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @Counted(
      name = "linkDeliveryAndShipmentByGtinAndLotNumberHitCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "linkDeliveryAndShipmentByGtinAndLotNumber")
  @Timed(
      name = "linkDeliveryAndShipmentByGtinAndLotNumberTimed",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "linkDeliveryAndShipmentByGtinAndLotNumber")
  @ExceptionCounted(
      name = "linkDeliveryAndShipmentByGtinAndLotNumberExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "linkDeliveryAndShipmentByGtinAndLotNumber")
  public Optional<List<DeliveryDocument>> linkDeliveryAndShipmentByGtinAndLotNumber(
      String deliveryNumber, Map<String, ScannedData> scannedDataMap, HttpHeaders httpHeaders)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
    List<DeliveryDocument> deliveryDocumentList;
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    String lotNumber = scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue();
    List<Shipment> shipments = new ArrayList<>();
    try {
      log.info("Searching for latest shipments by SSCC for delivery {}", deliveryNumber);
      shipments =
          searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
              deliveryNumber, gtin, lotNumber, httpHeaders);
    } catch (Exception e) {
      log.info(
          "No new Shipments have found to link for delivery: {}, gtin: {} and lotNumber:{}",
          deliveryNumber,
          gtin,
          lotNumber);
      return Optional.empty();
    }

    for (Shipment shipment : shipments) {
      log.info(
          "Linking shipment: {} with deliveryNumber:{} for gtin:{} and lotNumber:{}",
          shipment.getShipmentNumber(),
          deliveryNumber,
          gtin,
          lotNumber);
      linkDeliveryWithShipment(
          deliveryNumber, shipment.getShipmentNumber(), shipment.getDocumentId(), httpHeaders);
    }
    deliveryDocumentList =
        findDeliveryDocumentsByGtinAndLotNumber(deliveryNumber, scannedDataMap, httpHeaders);
    if (CollectionUtils.isEmpty(deliveryDocumentList)) {
      log.error(
          "Got 204 response from GDM even after linking shipment with delivery: {}, gtin: {} and lotNumber:{}",
          deliveryNumber,
          gtin,
          lotNumber);
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SEARCH_FOR_DELIVERY_DOC_BY_GTIN_AND_LOT_FAILURE,
          String.format(
              RxConstants.GDM_SEARCH_FOR_DELIVERY_DOC_BY_GTIN_AND_LOT_FAILURE,
              deliveryNumber,
              gtin,
              lotNumber),
          deliveryNumber,
          gtin,
          lotNumber);
    }
    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());
    return Optional.of(deliveryDocumentList);
  }

  /**
   * This api fetches shipment information from GDM for the given delivery number, gtin and lot
   * number
   *
   * @param deliveryNumber
   * @param gtin
   * @param lotNumber
   * @param headers
   * @return List<Shipment>
   * @throws ReceivingBadDataException
   */
  @Counted(
      name = "searchShipmentsByDeliveryAndGtinAndLotNumberFromGdmHitCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm")
  @Timed(
      name = "searchShipmentsByDeliveryAndGtinAndLotNumberFromGdmTimed",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm")
  @ExceptionCounted(
      name = "searchShipmentsByDeliveryAndGtinAndLotNumberFromGdmExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rxDeliveryServiceImpl",
      level3 = "searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm")
  public List<Shipment> searchShipmentsByDeliveryAndGtinAndLotNumberFromGdm(
      String deliveryNumber, String gtin, String lotNumber, HttpHeaders headers)
      throws ReceivingBadDataException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
    headers.remove(ReceivingConstants.ACCEPT);
    headers.add(
        ReceivingConstants.ACCEPT, ReceivingConstants.GDM_SEARCH_SHIPMENT_BY_GTIN_LOT_ACCEPT_TYPE);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RxConstants.ENABLE_ASN_SEARCH_GTIN_ONLY)) {
      headers.replace(
          ReceivingConstants.ACCEPT,
          Arrays.asList(ReceivingConstants.GDM_SEARCH_SHIPMENT_BY_GTIN_LOT_ACCEPT_TYPE_V2));
    }

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    queryParams.put(ReceivingConstants.KEY_GTIN, gtin);
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RxConstants.ENABLE_ASN_SEARCH_GTIN_ONLY)) {
      queryParams.put(ReceivingConstants.KEY_LOT_NUMBER, lotNumber);
    }

    String url =
        ReceivingUtils.replacePathParamsAndQueryParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_FETCH_SHIPMENT_BASE_URI,
                null,
                queryParams)
            .toString();

    log.info("Going to search shipments details in GDM by using url: {}", url);
    ResponseEntity<String> response;
    try {
      response =
          restConnector.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SEARCH_SHIPMENT_FAILED,
          String.format(
              RxConstants.GDM_SHIPMENT_NOT_FOUND_FOR_DELIVERY_GTIN_AND_LOT,
              deliveryNumber,
              gtin,
              lotNumber),
          deliveryNumber,
          gtin,
          lotNumber);
    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }
    log.info(
        "Received GDM response: {} for deliveryNumber: {}, gtin: {} and lotNumber search: {}",
        response.getBody(),
        deliveryNumber,
        gtin,
        lotNumber);
    if (StringUtils.isBlank(response.getBody())) {
      log.error(
          "Received response: {} from GDM for deliveryNumber: {}, gtin: {} and lotNumber: {}",
          response.getBody(),
          deliveryNumber,
          gtin,
          lotNumber);
      List<ItemCatalogUpdateLog> itemCatalogUpdateLogList =
          getItemCatalogUpdateLogByDeliveryAndUpc(Long.valueOf(deliveryNumber), gtin);
      if (!CollectionUtils.isEmpty(itemCatalogUpdateLogList)) {
        log.info(
            "Shipment information is not available in GDM after cataloging GTIN:{} in delivery:{}",
            gtin,
            deliveryNumber);
        throw new ReceivingBadDataException(
            ExceptionCodes.GDM_SEARCH_SHIPMENT_FAILURE_AFTER_UPC_CATALOG,
            String.format(
                RxConstants.GDM_SEARCH_SHIPMENT_FAILURE_AFTER_UPC_CATALOG, gtin, deliveryNumber),
            gtin,
            deliveryNumber);
      } else {
        log.info(
            "GTIN: {} is not cataloged in delivery: {}, please proceed with item cataloging this UPC",
            gtin,
            deliveryNumber);
        throw new ReceivingBadDataException(
            ExceptionCodes.GDM_SEARCH_SHIPMENT_BY_GTIN_LOT_FAILURE,
            String.format(
                RxConstants.GDM_SHIPMENT_NOT_FOUND_FOR_DELIVERY_GTIN_AND_LOT,
                deliveryNumber,
                gtin,
                lotNumber),
            deliveryNumber,
            gtin,
            lotNumber);
      }
    }

    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());
    List<Shipment> shipments =
        gson.fromJson(response.getBody(), new TypeReference<List<Shipment>>() {}.getType());
    return CollectionUtils.isEmpty(shipments) ? new ArrayList<>() : shipments;
  }

  @Counted(
      name = "getUnitSerializedInfoHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryServiceImpl",
      level3 = "getUnitSerializedInfo")
  @Timed(
      name = "getUnitSerializedInfoTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "getUnitSerializedInfo")
  @ExceptionCounted(
      name = "getUnitSerializedInfoExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "getUnitSerializedInfo")
  public PackItemResponse getUnitSerializedInfo(
      UnitSerialRequest unitSerialRequest, HttpHeaders headers) throws ReceivingException {
    headers.add("Compression-Type", "gzip");
    String url =
        appConfig.getGdmBaseUrl()
            + ReceivingConstants.GDM_SHIPMENT_DETAILS_WITH_UNIT_SERIAL_INFO_URI;
    PackItemResponse packItemResponse;
    try {
      String gdmPackItem =
          restConnector
              .exchange(
                  url, HttpMethod.POST, new HttpEntity<>(unitSerialRequest, headers), String.class)
              .getBody();
      packItemResponse = gson.fromJson(gdmPackItem, PackItemResponse.class);
    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          unitSerialRequest,
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SEARCH_PACK_UNIT_SERIAL_FAILURE,
          ReceivingConstants.GDM_GET_UNIT_SERIAL_DETAIL_FAILURE);

    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          unitSerialRequest,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));

      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }
    log.info(
        "For request {} got serialized info response= {} from GDM",
        unitSerialRequest,
        packItemResponse);
    if (Objects.isNull(packItemResponse)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SSCC_NOT_FOUND, GDM_UNIT_SERIAL_INFO_NOT_FOUND);
    }
    return packItemResponse;
  }

  /**
   * @param shipmentsContainersV2Request GDM CurrentNode API request
   * @param headers headers
   * @param queryParams query params
   * @return GDM CurrentNode API response OR NULL if 204
   */
  public SsccScanResponse getCurrentNode(
      ShipmentsContainersV2Request shipmentsContainersV2Request,
      HttpHeaders headers,
      Map<String, String> queryParams) {
    String url = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_CURRENT_NODE_URI;
    url =
        ReceivingUtils.replacePathParamsAndQueryParams(url, Collections.emptyMap(), queryParams)
            .toString();
    SsccScanResponse ssccScanResponse;

    try {
      TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
      log.info("[LT] GDM CurrentNode API Request {} Url {}", shipmentsContainersV2Request, url);

      ResponseEntity<SsccScanResponse> gdmResponseEntity =
          restConnector.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(shipmentsContainersV2Request, headers),
              SsccScanResponse.class);

      if (HttpStatus.NO_CONTENT.equals(gdmResponseEntity.getStatusCode())) {
        log.info("[LT] Got 204 response code from GDM CurrentNode API");
        return null;
      }

      ssccScanResponse = gdmResponseEntity.getBody();

    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          shipmentsContainersV2Request,
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SEARCH_EPCIS_DATA_FAILED,
              ReceivingConstants.GDM_DATA_NOT_FOUND);

    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          shipmentsContainersV2Request,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));

      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }

    log.info("[LT] GDM CurrentNode API response {} ", ssccScanResponse);

    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());

    return ssccScanResponse;
  }

  /**
   * @param currentAndSiblingsRequest GDM CurrentAndSiblings API request
   * @param httpHeaders headers
   * @param queryParams query params
   * @return GDM CurrentAndSiblings API response OR NULL if 204
   */
  public SsccScanResponse getCurrentAndSiblings(
      ShipmentsContainersV2Request currentAndSiblingsRequest,
      HttpHeaders httpHeaders,
      Map<String, String> queryParams) {
    String url = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_CURRENT_AND_SIBLINGS_URI;
    url =
        ReceivingUtils.replacePathParamsAndQueryParams(url, Collections.emptyMap(), queryParams)
            .toString();
    SsccScanResponse ssccScanResponse;

    try {
      TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
      log.info("[LT] GDM CurrentAndSiblings API Request {} Url {}", currentAndSiblingsRequest, url);

      ResponseEntity<SsccScanResponse> gdmResponseEntity =
          restConnector.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(currentAndSiblingsRequest, httpHeaders),
              SsccScanResponse.class);

      if (HttpStatus.NO_CONTENT.equals(gdmResponseEntity.getStatusCode())) {
        log.info("[LT] Got 204 response code from GDM CurrentAndSiblings API");
        return null;
      }

      ssccScanResponse = gdmResponseEntity.getBody();

    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          currentAndSiblingsRequest,
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingBadDataException(
              ExceptionCodes.GDM_SEARCH_EPCIS_DATA_FAILED,
              ReceivingConstants.ERROR_GETTING_SIBLINGS_DATA);

    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          currentAndSiblingsRequest,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));

      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }

    log.info("[LT] GDM CurrentAndSiblings API response {} ", ssccScanResponse);

    if (Objects.isNull(ssccScanResponse)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SSCC_NOT_FOUND, ReceivingConstants.GDM_SHIPMENT_NOT_FOUND);
    }
    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());

    return ssccScanResponse;
  }

  /**
   * @param unitLevelContainersRequest GDM UnitLevelContainersRequest API request
   * @param httpHeaders headers
   * @return GDM UnitLevelContainers API response OR NULL if 204
   */
  public SsccScanResponse getUnitLevelContainers(
      ShipmentsContainersV2Request unitLevelContainersRequest, HttpHeaders httpHeaders) {
    String url = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_UNIT_LEVEL_CONTAINERS_URI;
    SsccScanResponse ssccScanResponse;

    try {
      log.info(
          "[LT] GDM UnitLevelContainers API Request {} Url {}", unitLevelContainersRequest, url);

      ResponseEntity<SsccScanResponse> gdmResponseEntity =
          restConnector.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(unitLevelContainersRequest, httpHeaders),
              SsccScanResponse.class);

      if (HttpStatus.NO_CONTENT.equals(gdmResponseEntity.getStatusCode())) {
        log.info("[LT] Got 204 response code from GDM UnitLevelContainers API");
        return null;
      }

      ssccScanResponse = gdmResponseEntity.getBody();

    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          unitLevelContainersRequest,
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SEARCH_PACK_UNIT_SERIAL_FAILURE,
          ReceivingConstants.GDM_GET_UNIT_SERIAL_DETAIL_FAILURE);

    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          unitLevelContainersRequest,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }

    log.info("[LT] GDM UnitLevelContainers API response {} ", ssccScanResponse);

    if (Objects.isNull(ssccScanResponse)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_SSCC_NOT_FOUND, ReceivingConstants.GDM_SHIPMENT_NOT_FOUND);
    }

    return ssccScanResponse;
  }

  /**
   * @param updateGdmStatusV2Request GDM UpdateEpcisReceivingStatus API request
   * @param httpHeaders headers
   * @return GDM UpdateEpcisReceivingStatus API response status code
   */
  public HttpStatus updateEpcisReceivingStatus(
      List<UpdateGdmStatusV2Request> updateGdmStatusV2Request, HttpHeaders httpHeaders) {
    String url = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_UPDATE_EPCIS_POSTING_STATUS_URI;

    try {
      log.info(
          "[LT] GDM updateEpcisReceivingStatus API Request {} Url {}", updateGdmStatusV2Request, url);

      ResponseEntity<String> gdmResponseEntity =
          restConnector.exchange(
              url,
              HttpMethod.PUT,
              new HttpEntity<>(updateGdmStatusV2Request, httpHeaders),
              String.class);

      HttpStatus responseStatusCode = gdmResponseEntity.getStatusCode();
      log.info(
          "[LT] Got response code {} from GDM updateEpcisReceivingStatus API",
          responseStatusCode.value());

      return responseStatusCode;

    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          updateGdmStatusV2Request,
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingBadDataException(
              ExceptionCodes.ERROR_UPDATING_GDM,
              ReceivingConstants.ERROR_UPDATING_GDM);

    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          updateGdmStatusV2Request,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingConstants.GDM_SERVICE_DOWN);
    }
  }

  @Override
  public List<DeliveryDocument> findDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }
}
