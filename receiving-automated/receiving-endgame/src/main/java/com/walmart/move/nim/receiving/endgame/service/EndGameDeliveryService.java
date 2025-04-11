package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.ASN_DETAILS_NOT_FOUND;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_SERVICE_DOWN;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GDM_RESTUTILS_INFO_MESSAGE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RESTUTILS_ERROR_MESSAGE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE;
import static java.util.Objects.nonNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.EventStore;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.GdmAsnDeliveryResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.EventStoreService;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.model.Location;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.EventStoreType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * This class is responsible to implement the unimplemented method using Retryable rest connector.
 *
 * @author a0b02ft
 */
public class EndGameDeliveryService extends DeliveryService {
  private static final Logger LOGGER = LoggerFactory.getLogger(EndGameDeliveryService.class);

  @ManagedConfiguration AppConfig appConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  @Autowired private EndGameLabelingService endGameLabelingService;
  @Autowired private EndgameDeliveryStatusPublisher endgameDeliveryStatusPublisher;

  @Resource(name = ReceivingConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  protected DeliveryMetaDataService deliveryMetaDataService;

  @Autowired private EventStoreService eventStoreService;
  @Autowired private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Autowired private TenantSpecificConfigReader configUtils;

  /**
   * This method is fetch PO/PO line as response from GDM. If GDM call fails for IO Exception or any
   * 5xx error, then it will retry for a maximum value defined in CCM.
   *
   * @param deliveryNumber delivery number
   * @param upcNumber upc number
   * @param headers http headers
   * @return PO/PO line as response
   * @throws ReceivingException
   */
  // TODO: Will throw EndGameException instead of ReceivingException which will be a subclass of
  // ReceivingException.
  @Timed(
      name = "Endgame-ScanUPC",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @ExceptionCounted(
      name = "Endgame-ScanUPC-Exception",
      level1 = "uwms-receiving",
      level2 = "Endgame-ScanUPC-Exception",
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
                appConfig.getGdmBaseUrl() + EndgameConstants.GDM_DOCUMENT_SEARCH_URI_V3, pathParams)
            .toString();
    headers.set(HttpHeaders.ACCEPT, EndgameConstants.GDM_DOCUMENT_SEARCH_V3_ACCEPT_TYPE);
    ResponseEntity<String> response = null;
    try {
      response =
          restConnector.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

    } catch (RestClientResponseException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          ReceivingException.CREATE_INSTRUCTION_NO_PO_LINE,
          HttpStatus.NOT_FOUND,
          GDM_SEARCH_DOCUMENT_ERROR_CODE);

    } catch (ResourceAccessException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          GDM_SERVICE_DOWN, HttpStatus.INTERNAL_SERVER_ERROR, GDM_SEARCH_DOCUMENT_ERROR_CODE);
    }
    if (Objects.isNull(response) || response.getBody().isEmpty()) {
      LOGGER.error(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, "", "");
      throw new ReceivingException(
          ReceivingException.CREATE_INSTRUCTION_NO_PO_LINE,
          HttpStatus.NOT_FOUND,
          GDM_SEARCH_DOCUMENT_ERROR_CODE);
    }
    LOGGER.debug(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, "", response.getBody());
    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());
    return response.getBody();
  }

  @Override
  @Timed(name = "GDM-Get-Delivery", level1 = "uwms-receiving", level2 = "GDM-Get-Delivery")
  @ExceptionCounted(
      name = "GDM-Get-Delivery-Exception",
      level1 = "uwms-receiving",
      level2 = "GDM-Get-Delivery-Exception")
  @TimeTracing(
      component = AppComponent.ENDGAME,
      executionFlow = "GDM-Get-Delivery",
      type = Type.REST,
      externalCall = true)
  public Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryUpdateMessage.getDeliveryNumber());

    String uri =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + EndgameConstants.GDM_DOCUMENT_GET_BY_DELIVERY_V3,
                pathParams)
            .toString();
    httpHeaders.set(
        HttpHeaders.ACCEPT, EndgameConstants.GDM_DOCUMENT_GET_BY_DELIVERY_V3_ACCEPT_TYPE);

    ResponseEntity<String> deliveryResponseEntity = null;
    try {
      deliveryResponseEntity =
          restConnector.exchange(uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          ReceivingException.DELIVERY_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          GDM_SEARCH_DOCUMENT_ERROR_CODE);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          GDM_SERVICE_DOWN, HttpStatus.INTERNAL_SERVER_ERROR, GDM_SEARCH_DOCUMENT_ERROR_CODE);
    }

    if (Objects.isNull(deliveryResponseEntity) || !deliveryResponseEntity.hasBody()) {
      LOGGER.error(ReceivingConstants.RESTUTILS_INFO_MESSAGE, uri, "", "");
      throw new ReceivingException(
          ReceivingException.DELIVERY_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          GDM_SEARCH_DOCUMENT_ERROR_CODE);
    }
    Delivery delivery = gson.fromJson(deliveryResponseEntity.getBody(), Delivery.class);
    LOGGER.debug(
        ReceivingConstants.RESTUTILS_INFO_MESSAGE,
        uri,
        deliveryUpdateMessage.getDeliveryNumber(),
        deliveryResponseEntity.getBody());
    return delivery;
  }

  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.MESSAGE,
      executionFlow = "GDM-Del-WRK",
      externalCall = true)
  public void publishWorkingEventIfApplicable(Long deliveryNumber) {

    List<PreLabelData> preLabelData =
        endGameLabelingService.findByDeliveryNumberAndStatus(deliveryNumber, LabelStatus.SCANNED);
    /*
     Getting the first scanEvent . If not the first, then dont publish working event
    */
    if (!CollectionUtils.isEmpty(preLabelData)) {
      LOGGER.warn(
          "There is already item got scanned for [delivery={}]. So not going to publish the working event",
          deliveryNumber);
      return;
    }
    buildAndPublishDeliveryInfo(deliveryNumber);
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-29 - Publish the delivery info / working event to GDM
   *
   * @param deliveryNumber the delivery number of container
   * @param deliveryStatus the delivery status
   */
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.MESSAGE,
      executionFlow = "GDM-Del-WRK",
      externalCall = true)
  public void publishNonSortWorkingEvent(Long deliveryNumber, DeliveryStatus deliveryStatus) {
    if (DeliveryStatus.OPN.name().equals(deliveryStatus.name())) {
      LOGGER.info(
          "Delivery Status [deliveryStatus={}] building the delivery info to publish",
          deliveryStatus);
      buildAndPublishDeliveryInfo(deliveryNumber);
      return;
    }
    LOGGER.warn(
        "Delivery Status [deliveryStatus={}] so not going to publish the working event",
        deliveryStatus);
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-29 - Create delivery info and publish to Maas
   *
   * @param deliveryNumber the delivery number
   */
  public void buildAndPublishDeliveryInfo(Long deliveryNumber) {
    DeliveryMetaData deliveryMetaData =
        endGameLabelingService
            .findDeliveryMetadataByDeliveryNumber(String.valueOf(deliveryNumber))
            .orElse(new DeliveryMetaData());

    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(deliveryNumber);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.WORKING.name());
    deliveryInfo.setUserId(EndgameConstants.HAWKEYE_SCAN_USER);
    deliveryInfo.setTrailerNumber(deliveryMetaData.getTrailerNumber());
    deliveryInfo.setDoorNumber(deliveryMetaData.getDoorNumber());
    deliveryInfo.setTs(new Date());
    // TODO need to retrieve the Kafka Headers for userId. As of now, hardcoding to hawkeye
    endgameDeliveryStatusPublisher.publishMessage(
        deliveryInfo, EndGameUtils.createMaaSHeaders("Hawkeye-Scanned"));
  }

  public void publishWorkingEventIfApplicable(
      Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine, long deliveryNumber) {

    LOGGER.info(
        "Got DeliveryStatus = {} for deliveryNumber = {} ",
        selectedPoAndLine.getKey().getStatusInformation().getStatus(),
        deliveryNumber);

    if (!(StringUtils.equalsIgnoreCase(
            selectedPoAndLine.getKey().getStatusInformation().getStatus(),
            DeliveryStatus.OPN.toString())
        || StringUtils.equalsIgnoreCase(
            selectedPoAndLine.getKey().getStatusInformation().getStatus(),
            DeliveryStatus.WRK.toString()))) {
      raiseAlert(
          String.format("Delivery=%s is not open. However Receiving started", deliveryNumber));
    }

    if (StringUtils.endsWithIgnoreCase(
        selectedPoAndLine.getKey().getStatusInformation().getStatus(), "OPN")) {
      LOGGER.info(
          "Delivery Status [deliveryStatus={}] and hence sending working event for [deliveryNumber={}]",
          selectedPoAndLine.getKey().getStatusInformation().getStatus(),
          deliveryNumber);
      buildAndPublishDeliveryInfo(deliveryNumber);
    }
  }

  public void raiseAlert(String message) {
    LOGGER.warn(message);
  }

  @Override
  public DeliveryDoorSummary getDoorStatus(String doorNumber) throws ReceivingException {
    return deliveryMetaDataService.findDoorStatus(
        getFacilityNum(), getFacilityCountryCode(), doorNumber);
  }

  @Transactional
  @InjectTenantFilter
  public ResponseEntity<HttpStatus> processPendingDeliveryEvent(Location location)
      throws ReceivingException {
    LOGGER.info(
        "Got Delivery pending delivery process details for door number = {} ",
        location.getLocation());
    Optional<EventStore> pendingDelivery =
        eventStoreService.getEventStoreByKeyStatusAndEventType(
            location.getLocation(), EventTargetStatus.PENDING, EventStoreType.DOOR_ASSIGNMENT);
    if (pendingDelivery.isPresent()) {
      Optional<DeliveryMetaData> deliveryMetaData =
          deliveryMetaDataRepository.findByDeliveryNumber(
              String.valueOf(pendingDelivery.get().getDeliveryNumber()));
      if (deliveryMetaData.isPresent()
          && (Objects.isNull(deliveryMetaData.get().getUnloadingCompleteDate())
              || deliveryMetaData
                  .get()
                  .getUnloadingCompleteDate()
                  .after(pendingDelivery.get().getCreatedDate()))) {
        return new ResponseEntity(HttpStatus.OK);
      }
      EventStore deliveryEvent = pendingDelivery.get();
      DeliveryUpdateMessage deliveryUpdateMessage =
          ReceivingUtils.convertStringToObject(
              deliveryEvent.getPayload(), new TypeReference<DeliveryUpdateMessage>() {});
      EventProcessor deliveryEventProcessor =
          configUtils.getDeliveryEventProcessor(String.valueOf(getFacilityNum()));
      deliveryEventProcessor.processEvent(deliveryUpdateMessage);
      deliveryEvent.setStatus(EventTargetStatus.SUCCESSFUL);
      eventStoreService.saveEventStoreEntity(deliveryEvent);
    }
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Timed(name = "GDM-Get-ASN-Data", level1 = "uwms-receiving", level2 = "GDM-Get-ASN-Data")
  @ExceptionCounted(
      name = "GDM-Get-ASN-Data-Exception",
      level1 = "uwms-receiving",
      level2 = "GDM-Get-ASN-Data-Exception")
  @TimeTracing(
      component = AppComponent.ENDGAME,
      executionFlow = "GDM-Get-ASN-Data",
      type = Type.REST,
      externalCall = true)
  public GdmAsnDeliveryResponse getASNDataFromGDM(Long deliveryNumber, String boxId)
      throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, String.valueOf(deliveryNumber));
    pathParams.put(ReceivingConstants.BOX_ID, boxId);

    String uri =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + EndgameConstants.GDM_DOCUMENT_GET_BY_BOX_ID, pathParams)
            .toString();
    httpHeaders.set(HttpHeaders.ACCEPT, EndgameConstants.GDM_DOCUMENT_GET_BY_SHIPMENT_ACCEPT_TYPE);

    GdmAsnDeliveryResponse asnDeliveryResponse = null;
    try {
      ResponseEntity<String> deliveryResponseEntity =
          restConnector.exchange(uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
      if (nonNull(deliveryResponseEntity) && deliveryResponseEntity.hasBody()) {
        asnDeliveryResponse =
            gson.fromJson(deliveryResponseEntity.getBody(), GdmAsnDeliveryResponse.class);
      }
    } catch (RestClientResponseException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          ASN_DETAILS_NOT_FOUND, HttpStatus.NOT_FOUND, GDM_SEARCH_DOCUMENT_ERROR_CODE);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          GDM_SERVICE_DOWN, HttpStatus.INTERNAL_SERVER_ERROR, GDM_SEARCH_DOCUMENT_ERROR_CODE);
    }
    LOGGER.debug(GDM_RESTUTILS_INFO_MESSAGE, uri, deliveryNumber, asnDeliveryResponse);
    return asnDeliveryResponse;
  }

  @Override
  public List<DeliveryDocument> findDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }
}
