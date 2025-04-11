package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.GdmPOLineResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.message.publisher.RdcMessagePublisher;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.StringUtils;

public class RdcDeliveryService extends DeliveryService {

  private static final Logger log = LoggerFactory.getLogger(RdcDeliveryService.class);

  @Resource(name = RdcConstants.RDC_MESSAGE_PUBLISHER)
  private RdcMessagePublisher rdcMessagePublisher;

  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired GDMRestApiClient gdmRestApiClient;

  @Override
  public void publishDeliveryStatus(DeliveryInfo deliveryInfo, HttpHeaders headers) {
    log.info("Publishing delivery status for the delivery {}", deliveryInfo.getDeliveryNumber());
    Map<String, Object> messageHeaders = ReceivingUtils.getForwardablHeaderWithTenantData(headers);
    String source = headers.getFirst(ReceivingConstants.WMT_REQ_SOURCE);
    if (!StringUtils.isEmpty(source)) messageHeaders.put(ReceivingConstants.WMT_REQ_SOURCE, source);
    rdcMessagePublisher.publishDeliveryStatus(deliveryInfo, messageHeaders);
  }

  @Override
  public DeliveryDoorSummary getDoorStatus(String doorNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public String findDeliveryDocument(long deliveryNumber, String upcNumber, HttpHeaders headers)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, String.valueOf(deliveryNumber));

    String getDeliveryDocumentsUrl = "";
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
        false)) {
      Map<String, String> queryParams = new HashMap<>();
      queryParams.put(ReceivingConstants.QUERY_GTIN, upcNumber);
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.IS_IQS_ITEM_SCAN_ACTIVE_CHANNELS_ENABLED))
        queryParams.put(
            ReceivingConstants.INCLUDE_ACTIVE_CHANNEL_METHODS, ReceivingConstants.TRUE_STRING);

      headers.set(HttpHeaders.ACCEPT, ReceivingConstants.GDM_DOCUMENT_SEARCH_V3_ACCEPT_TYPE);
      getDeliveryDocumentsUrl =
          ReceivingUtils.replacePathParamsAndQueryParams(
                  appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_DOCUMENT_SEARCH_URI_V3,
                  pathParams,
                  queryParams)
              .toString();

    } else {
      pathParams.put(ReceivingConstants.UPC_NUMBER, upcNumber);
      getDeliveryDocumentsUrl =
          ReceivingUtils.replacePathParams(
                  appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_DOCUMENT_SEARCH_URI,
                  pathParams)
              .toString();
    }
    return getDeliveryDocumentsByGtin(getDeliveryDocumentsUrl, headers, retryableRestConnector);
  }

  @Override
  public Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }

  /**
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param httpHeaders
   * @return
   */
  public List<DeliveryDocument> getDeliveryDocumentsByPoAndPoLineFromGDM(
      String deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments;
    log.info(
        "Get delivery document from GDM for deliveryNumber:{}, POL:{}, POLine:{}",
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber);
    try {
      GdmPOLineResponse gdmPOLineResponse =
          getPOLineInfoFromGDM(
              deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, httpHeaders);
      deliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();
      String deliveryStatus = gdmPOLineResponse.getDeliveryStatus();
      List<String> stateReasonCodes = gdmPOLineResponse.getStateReasonCodes();
      if (org.apache.commons.lang3.StringUtils.isNotBlank(deliveryStatus)
          || CollectionUtils.isNotEmpty(stateReasonCodes)) {
        for (DeliveryDocument deliveryDocument : deliveryDocuments) {
          if (org.apache.commons.lang3.StringUtils.isNotBlank(deliveryStatus)) {
            deliveryDocument.setDeliveryStatus(DeliveryStatus.valueOf(deliveryStatus));
          }
          if (CollectionUtils.isNotEmpty(stateReasonCodes)) {
            deliveryDocument.setStateReasonCodes(stateReasonCodes);
          }
        }
      }
    } catch (ReceivingException receivingException) {
      if (receivingException.getHttpStatus().is4xxClientError()) {
        log.error(
            "No delivery documents found in GDM for delivery: {}, PO: {}, POL: {} combinations",
            deliveryNumber,
            purchaseReferenceNumber,
            purchaseReferenceLineNumber);
        throw new ReceivingBadDataException(
            ExceptionCodes.GDM_DELIVERY_DOCUMENTS_NOT_FOUND,
            String.format(
                ReceivingException.DELIVERY_DOCUMENT_NOT_FOUND_FOR_DELIVERY_PO_POL_ERROR,
                deliveryNumber,
                purchaseReferenceNumber,
                purchaseReferenceLineNumber),
            deliveryNumber,
            purchaseReferenceNumber,
            purchaseReferenceLineNumber);
      }
      throw receivingException;
    }
    return deliveryDocuments;
  }

  public List<DeliveryDocument> findDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments;
    log.info(
        "Fetching delivery documents for delivery number {} and item number {}",
        deliveryNumber,
        itemNumber);
    String gdmDeliveryDocumentsResponse =
        gdmRestApiClient.getDeliveryDocumentsByItemNumber(deliveryNumber, itemNumber, headers);
    deliveryDocuments =
        new ArrayList<>(
            Arrays.asList(gson.fromJson(gdmDeliveryDocumentsResponse, DeliveryDocument[].class)));
    return deliveryDocuments;
  }

  @Override
  public DeliveryDetails getDeliveryDetails(String url, Long deliveryNumber)
      throws ReceivingException {
    log.info("Fetching delivery details for delivery number {}", deliveryNumber);
    return gdmRestApiClient.getDeliveryDetails(url, deliveryNumber);
  }
}
