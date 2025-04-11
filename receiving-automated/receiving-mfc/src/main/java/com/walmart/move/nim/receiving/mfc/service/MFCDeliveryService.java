package com.walmart.move.nim.receiving.mfc.service;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.RECEIVED_OVG_TYPE;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.framework.consumer.BiParameterConsumer;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.gdm.GDMShipmentHeaderSearchResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GDMShipmentSearchResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchByStatusRequest;
import com.walmart.move.nim.receiving.core.model.gdm.GdmShipmentSearchRequest;
import com.walmart.move.nim.receiving.core.model.gdm.ShipmentCriteria;
import com.walmart.move.nim.receiving.core.model.gdm.ShipmentRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.model.gdm.v3.InventoryDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.InvoiceDetail;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.utils.UomUtils;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.gdm.ScanPalletRequest;
import com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Item;
import com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.NewInvoiceLine;
import com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Pack;
import com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Pallet;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

public class MFCDeliveryService extends DeliveryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MFCDeliveryService.class);

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  @SecurePublisher private KafkaTemplate kafkaTemplate;

  private Gson gson;

  @Value("${gdm.invoice.changes.topic:abc}")
  private String invoiceOperationTopic;

  @Value("${mixed.pallet.mfc.only.reject:true}")
  private String onlyMFCReject;

  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  @Autowired private ProcessInitiator processInitiator;

  public MFCDeliveryService() {
    this.gson =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  public ASNDocument getGDMData(String deliveryNumber, String shipmentDocumentId) {
    StringBuilder urlBuilder =
        new StringBuilder(appConfig.getGdmBaseUrl())
            .append("/api")
            .append("/deliveries/")
            .append(deliveryNumber)
            .append("/shipments/")
            .append(shipmentDocumentId);

    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(HttpHeaders.ACCEPT, "application/vnd.DeliveryShipmentSearchZipResponse1+json");
    headers.add("Compression-Type", "gzip");

    ASNDocument shipmentDocs =
        restConnector
            .exchange(
                urlBuilder.toString(), HttpMethod.GET, new HttpEntity<>(headers), ASNDocument.class)
            .getBody();

    if (Objects.isNull(shipmentDocs)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format(
              "Shipment = %s from delivery = %s is not available",
              deliveryNumber, shipmentDocumentId));
    }
    return shipmentDocs;
  }

  @Override
  public String findDeliveryDocument(long deliveryNumber, String upcNumber, HttpHeaders headers)
      throws ReceivingException {
    return null;
  }

  @Override
  public Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {

    StringBuilder urlBuilder =
        new StringBuilder(appConfig.getGdmBaseUrl())
            .append("/api")
            .append("/deliveries/")
            .append(deliveryUpdateMessage.getDeliveryNumber());

    return restConnector
        .exchange(
            urlBuilder.toString(),
            HttpMethod.GET,
            new HttpEntity<>(ReceivingUtils.getHeaders()),
            Delivery.class)
        .getBody();
  }

  @Override
  public DeliveryDoorSummary getDoorStatus(String doorNumber) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  public void publishNewInvoice(
      Container container,
      List<ContainerItem> containerItems,
      String flow,
      BiParameterConsumer<Container, Pack, Pack> packEnricher) {
    final List<Item> items = new ArrayList<>();

    final List<Pack> packs = new ArrayList<>();

    containerItems.forEach(
        containerItem -> {
          InventoryDetail inventoryDetail = new InventoryDetail();
          inventoryDetail.setReportedQuantity(Double.valueOf(containerItem.getQuantity()));
          inventoryDetail.setReportedUom(containerItem.getQuantityUOM());
          inventoryDetail.setVendorCaseQuantity(containerItem.getVnpkQty());
          inventoryDetail.setWarehouseCaseQuantity(containerItem.getWhpkQty());

          Pair<Double, String> baseUnitQuantity =
              UomUtils.getBaseUnitQuantity(
                  containerItem.getQuantity(), containerItem.getQuantityUOM());
          inventoryDetail.setDerivedQuantity(baseUnitQuantity.getFirst());
          inventoryDetail.setDerivedUom(baseUnitQuantity.getSecond());

          InvoiceDetail invoiceDetail = new InvoiceDetail();
          invoiceDetail.setInvoiceNumber(containerItem.getInvoiceNumber());
          invoiceDetail.setInvoiceLineNumber(containerItem.getInvoiceLineNumber());

          items.add(
              Item.builder()
                  .itemNumber(containerItem.getItemNumber())
                  .gtin(containerItem.getGtin())
                  .itemDescription(containerItem.getDescription())
                  .invoice(invoiceDetail)
                  .inventoryDetail(inventoryDetail)
                  .replenishmentCode(MFCConstant.MARKET_FULFILLMENT_CENTER)
                  .itemDepartment(String.valueOf(containerItem.getDeptNumber()))
                  .hybridStorageFlag(containerItem.getHybridStorageFlag())
                  .build());

          if (!Objects.isNull(packEnricher)) {
            List<Item> _item = new ArrayList<>();
            _item.add(
                Item.builder()
                    .itemNumber(containerItem.getItemNumber())
                    .gtin(containerItem.getGtin())
                    .itemDescription(containerItem.getDescription())
                    .invoice(invoiceDetail)
                    .inventoryDetail(inventoryDetail)
                    .replenishmentCode(MFCConstant.MARKET_FULFILLMENT_CENTER)
                    .itemDepartment(String.valueOf(containerItem.getDeptNumber()))
                    .hybridStorageFlag(containerItem.getHybridStorageFlag())
                    .build());
            Pack pack =
                Pack.builder()
                    .packNumber(container.getTrackingId())
                    .palletNumber(container.getTrackingId())
                    .items(_item)
                    .build();
            Map<String, String> containerItemMisc = containerItem.getContainerItemMiscInfo();
            if (Objects.nonNull(containerItemMisc)
                && Objects.nonNull(containerItemMisc.get(MFCConstant.PACK_NUMBER))) {
              pack.setPackNumber(containerItemMisc.get(MFCConstant.PACK_NUMBER));
              LOGGER.info("Set the packNumber as {} ", pack.getPackNumber());
            } else {
              pack.setPackNumber(String.valueOf(System.currentTimeMillis()));
              LOGGER.info("Set the packNumber as {} : systemValue", pack.getPackNumber());
            }
            packs.add(packEnricher.apply(container, pack));
          }
        });

    Pack pack =
        Pack.builder()
            .packNumber(container.getTrackingId())
            .palletNumber(container.getTrackingId())
            .items(items)
            .build();

    if (packs.isEmpty()) {
      packs.add(pack);
    }

    List<Pack> _packs = groupPacksByPackNumber(packs);

    Pallet pallet =
        Pallet.builder()
            .palletNumber(container.getTrackingId())
            .receivedOvgType(
                Objects.nonNull(container.getContainerMiscInfo())
                        && container.getContainerMiscInfo().containsKey(RECEIVED_OVG_TYPE)
                    ? container.getContainerMiscInfo().get(RECEIVED_OVG_TYPE).toString()
                    : null)
            .build();
    if (!StringUtils.equalsIgnoreCase(container.getSsccNumber(), container.getTrackingId())) {
      pallet =
          Pallet.builder()
              .palletNumber(container.getSsccNumber())
              .receivedOvgType(
                  Objects.nonNull(container.getContainerMiscInfo())
                          && container.getContainerMiscInfo().containsKey(RECEIVED_OVG_TYPE)
                      ? container.getContainerMiscInfo().get(RECEIVED_OVG_TYPE).toString()
                      : null)
              .build();
    }

    NewInvoiceLine newInvoiceLine =
        NewInvoiceLine.builder()
            .eventType(flow)
            .shipmentDocumenId(container.getShipmentId())
            .packs(_packs)
            .pallets(Arrays.asList(pallet))
            .userId(ReceivingUtils.retrieveUserId())
            .ts(new Date())
            .build();

    // Send to GDM

    Map<String, Object> headers = new HashMap<>();
    headers.put("eventType", newInvoiceLine.getEventType());
    Message<String> message =
        KafkaHelper.buildKafkaMessage(
            container.getTrackingId(), gson.toJson(newInvoiceLine), invoiceOperationTopic, headers);

    kafkaTemplate.send(message);

    LOGGER.info(
        "Successfully publish new invoice addition flow to GDM. payload = {}",
        gson.toJson(newInvoiceLine));
  }

  public List<Pack> groupPacksByPackNumber(List<Pack> packs) {

    if (packs.isEmpty() || packs.size() == 1) {
      return packs;
    }

    Map<String, Pack> packMap = new HashMap<>();

    packs.forEach(
        pack -> {
          if (Objects.isNull(packMap.get(pack.getPackNumber()))) {
            packMap.put(pack.getPackNumber(), pack);
          } else {

            Pack pack1 = packMap.get(pack.getPackNumber());
            pack1.getItems().addAll(pack.getItems());
            packMap.put(pack.getPackNumber(), pack1);
          }
        });

    return new ArrayList<>(packMap.values());
  }

  public ASNDocument findDeliveryDocumentByPalletAndDelivery(
      ScanPalletRequest scanPalletRequest, Boolean includePalletRelations) {
    StringBuilder urlBuilder =
        new StringBuilder(appConfig.getGdmBaseUrl())
            .append("/api")
            .append("/deliveries/")
            .append(scanPalletRequest.getDeliveryNumber())
            .append("/shipments/")
            .append(scanPalletRequest.getPalletNumber())
            .append("?includePalletRelations=")
            .append(includePalletRelations);

    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(HttpHeaders.ACCEPT, "application/vnd.DeliveryShipmentScanZipResponse3+json");
    headers.add("Compression-Type", "gzip");

    final ParameterizedTypeReference<ASNDocument> typeRef =
        new ParameterizedTypeReference<ASNDocument>() {};

    ASNDocument shipmentDocument = null;

    try {
      LOGGER.info("Going to retrieve delivery document for payload = {}", scanPalletRequest);

      shipmentDocument =
          restConnector
              .exchange(urlBuilder.toString(), HttpMethod.GET, new HttpEntity<>(headers), typeRef)
              .getBody();
    } catch (Exception exception) {
      LOGGER.error("Delivery document not found with payload = {} ", scanPalletRequest, exception);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format(
              "ASN = %s from delivery = %d is not available",
              scanPalletRequest.getPalletNumber(), scanPalletRequest.getDeliveryNumber()));
    }

    LOGGER.info(
        "Retrieved ASN Documents for ASN={} and Delivery={} is {}",
        scanPalletRequest.getPalletNumber(),
        scanPalletRequest.getDeliveryNumber(),
        shipmentDocument);

    if (Objects.isNull(shipmentDocument)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format(
              "ASN = %s from delivery = %d is not available",
              scanPalletRequest.getPalletNumber(), scanPalletRequest.getDeliveryNumber()));
    }
    return shipmentDocument;
  }

  public ASNDocument findMixedContainerFromASN(Long deliveryNumber, String asnDocId) {
    if (Objects.isNull(asnDocId)) {
      LOGGER.info(
          "Fetching delivery data to get document Id for delivery number {}", deliveryNumber);
      asnDocId = retrieveASNDocumentId(deliveryNumber);
      LOGGER.info("Got document Id {} for delivery number {}", asnDocId, deliveryNumber);
    }
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, String.valueOf(deliveryNumber));
    pathParams.put(ReceivingConstants.SHIPMENT_NUMBER, asnDocId);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("includeOnlyMfcItems", onlyMFCReject);

    String url =
        ReceivingUtils.replacePathParamsAndQueryParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_LINK_SHIPMENT_DELIVERY,
                pathParams,
                queryParams)
            .toString();

    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(
        HttpHeaders.ACCEPT, "application/vnd.DeliveryShipmentSearchMixedPackZipResponse1+json");
    headers.add("Compression-Type", "gzip");

    ASNDocument asnDocument = null;

    try {
      asnDocument =
          restConnector
              .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), ASNDocument.class)
              .getBody();
      LOGGER.info(
          "Got information for asn on mixed pallet delivery {} shipment id {} ",
          deliveryNumber,
          asnDocId);

    } catch (Exception ex) {
      LOGGER.error("Unable to fetch mixed Item from GDM ", ex);
    }

    return asnDocument;
  }

  public ASNDocument getShipmentDataFromGDM(Long deliveryNumber, String asnDocId) {
    if (Objects.isNull(asnDocId)) {
      LOGGER.info(
          "Fetching delivery data to get document Id for delivery number {}", deliveryNumber);
      asnDocId = retrieveASNDocumentId(deliveryNumber);
      LOGGER.info("Got document Id {} for delivery number {}", asnDocId, deliveryNumber);
    }

    return getGDMData(String.valueOf(deliveryNumber), asnDocId);
  }

  private String retrieveASNDocumentId(Long deliveryNumber) {
    try {
      Delivery delivery =
          getGDMData(
              DeliveryUpdateMessage.builder()
                  .deliveryNumber(String.valueOf(deliveryNumber))
                  .build());
      if (Objects.nonNull(delivery) && CollectionUtils.isNotEmpty(delivery.getShipments())) {
        return delivery.getShipments().get(0).getDocumentId();
      }
    } catch (Exception e) {
      LOGGER.info("Data not found for delivery {}", deliveryNumber);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format("Delivery data not found for delivery number %s", deliveryNumber));
    }
    return null;
  }

  public DeliveryList fetchDeliveries(GdmDeliverySearchByStatusRequest deliverySearchRequest)
      throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.set(
        ReceivingConstants.CONTENT_TYPE,
        ReceivingConstants.GDM_SEARCH_DELIVERY_HEADER_CONTENT_TYPE);

    DeliveryList deliveryList = null;
    String url =
        new StringBuilder(appConfig.getGdmBaseUrl())
            .append(ReceivingConstants.GDM_SEARCH_HEADER_URI)
            .toString();
    try {
      LOGGER.info("Invoking GDM Header API for request {}", deliverySearchRequest);
      ResponseEntity<String> response =
          simpleRestConnector.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(deliverySearchRequest), httpHeaders),
              String.class);
      LOGGER.info(
          "Got response {} with status {} for GDM Header API",
          response.getBody(),
          response.getStatusCode());
      validateResponse(response);
      deliveryList = gson.fromJson(response.getBody(), DeliveryList.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          StringUtils.EMPTY,
          e.getResponseBodyAsString(),
          getStackTrace(e));
      throw new ReceivingException(
          ExceptionCodes.GDM_ERROR,
          INTERNAL_SERVER_ERROR,
          ReceivingConstants.UNABLE_TO_GET_DELIVERY_FROM_GDM);

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          StringUtils.EMPTY,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));

      throw new ReceivingException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE,
          INTERNAL_SERVER_ERROR,
          ReceivingConstants.GDM_SERVICE_DOWN);
    }

    return deliveryList;
  }

  private void validateResponse(ResponseEntity<String> response) throws ReceivingException {
    if (!response.getStatusCode().is2xxSuccessful()) {
      LOGGER.error("GDM delivery header search with error {}", response.getBody());
      throw new ReceivingException(
          ExceptionCodes.GDM_ERROR,
          response.getStatusCode(),
          ReceivingConstants.UNABLE_TO_GET_DELIVERY_FROM_GDM);
    }
  }

  public List<GDMShipmentHeaderSearchResponse> getShipmentDetails(String documentNumber)
      throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.set(
        ReceivingConstants.CONTENT_TYPE, ReceivingConstants.GDM_SEARCH_SHIPMENT_HEADER_V1);

    String url =
        new StringBuilder(appConfig.getGdmBaseUrl())
            .append(ReceivingConstants.GDM_SHIPMENT_SEARCH_URI)
            .toString();
    try {

      GdmShipmentSearchRequest gdmShipmentSearchRequest =
          GdmShipmentSearchRequest.builder()
              .criteria(
                  ShipmentCriteria.builder()
                      .shipment(ShipmentRequest.builder().shipmentNumber(documentNumber).build())
                      .build())
              .build();

      LOGGER.info("Invoking GDM Shipment Header API for request {}", gdmShipmentSearchRequest);
      ResponseEntity<GDMShipmentSearchResponse> response =
          simpleRestConnector.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(gdmShipmentSearchRequest), httpHeaders),
              GDMShipmentSearchResponse.class);
      LOGGER.info(
          "Got response with status {} for GDM Shipment Header API", response.getStatusCode());
      if (!response.getStatusCode().is2xxSuccessful()) {
        LOGGER.error("GDM shipment header search with error {}", response.getBody());
        throw new ReceivingException(
            ReceivingConstants.UNABLE_TO_GET_SHIPMENT_FROM_GDM,
            response.getStatusCode(),
            ExceptionCodes.GDM_ERROR);
      }
      if (Objects.isNull(response.getBody())) {
        throw new ReceivingDataNotFoundException(
            ExceptionCodes.GDM_NOT_FOUND,
            String.format("No shipment flow found for documentId %s", documentNumber));
      }
      return response.getBody().getData();
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          StringUtils.EMPTY,
          e.getResponseBodyAsString(),
          e.getRawStatusCode(),
          e);
      throw new ReceivingException(
          ReceivingConstants.UNABLE_TO_GET_DELIVERY_FROM_GDM,
          INTERNAL_SERVER_ERROR,
          ExceptionCodes.GDM_ERROR);

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          StringUtils.EMPTY,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          getStackTrace(e));

      throw new ReceivingException(
          ReceivingConstants.GDM_SERVICE_DOWN,
          INTERNAL_SERVER_ERROR,
          ExceptionCodes.GDM_NOT_ACCESSIBLE);
    }
  }

  @Override
  public List<DeliveryDocument> findDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }
}
