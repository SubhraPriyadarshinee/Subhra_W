package com.walmart.move.nim.receiving.mfc.service.problem.lq;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.ARRIVAL_TS;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.COST_CURRENCY;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.PACK_NUMBER;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.PALLET_TYPE;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.RECEIVING_COMPLETE_TS;
import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.REPLENISHMENT_CODE;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingForwardedException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Stop;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.mfc.common.ProblemType;
import com.walmart.move.nim.receiving.mfc.model.problem.CreateExceptionResponse;
import com.walmart.move.nim.receiving.mfc.model.problem.lq.ChannelAttributes;
import com.walmart.move.nim.receiving.mfc.model.problem.lq.Contact;
import com.walmart.move.nim.receiving.mfc.model.problem.lq.Container;
import com.walmart.move.nim.receiving.mfc.model.problem.lq.Item;
import com.walmart.move.nim.receiving.mfc.model.problem.lq.LQExceptionCloseRequest;
import com.walmart.move.nim.receiving.mfc.model.problem.lq.LQExceptionRequest;
import com.walmart.move.nim.receiving.mfc.model.problem.lq.LQExceptionResponse;
import com.walmart.move.nim.receiving.mfc.model.problem.lq.Pallet;
import com.walmart.move.nim.receiving.mfc.model.problem.lq.Reason;
import com.walmart.move.nim.receiving.mfc.model.problem.lq.ReasonAttributes;
import com.walmart.move.nim.receiving.mfc.service.problem.ProblemRegistrationService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

public class LoadQualityProblemService implements ProblemRegistrationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoadQualityProblemService.class);

  @ManagedConfiguration protected AppConfig appConfig;

  @Resource(name = ReceivingConstants.BEAN_REST_CONNECTOR)
  private RestConnector simpleRestConnector;

  @Autowired protected ProblemRepository problemRepository;

  @Override
  public CreateExceptionResponse createProblem(
      Shipment shipment, ContainerDTO containerDTO, ProblemType problemType) {
    LQExceptionRequest exceptionRequest = getExceptionRequest(shipment, containerDTO, problemType);

    LQExceptionResponse exceptionResponse = createException(exceptionRequest);

    CreateExceptionResponse createExceptionResponse = new CreateExceptionResponse();
    createExceptionResponse.setResponse(ReceivingUtils.stringfyJson(exceptionResponse));
    createExceptionResponse.setIssueId(exceptionResponse.getId());
    return createExceptionResponse;
  }

  @Override
  public void closeProblem(ProblemLabel problemLabel, ProblemType problemType) {
    closeException(problemLabel.getIssueId());
  }

  private LQExceptionResponse createException(LQExceptionRequest request) {
    URI uri =
        URI.create(appConfig.getLoadQualityBaseUrl() + ReceivingConstants.LQ_INCIDENT_API_PATH);

    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(ReceivingConstants.WM_SVC_ENV, appConfig.getLoadQualityServiceEnv());
    headers.add(ReceivingConstants.WM_SVC_NAME, appConfig.getLoadQualityServiceName());
    headers.add(ReceivingConstants.WM_CONSUMER_ID, appConfig.getReceivingConsumerId());
    headers.add(ReceivingConstants.WMT_TENANT, ReceivingConstants.LOAD_QUALITY_TENANT);
    headers.add(ReceivingConstants.WMT_SOURCE, ReceivingConstants.LOAD_QUALITY_SOURCE);
    headers.add(ReceivingConstants.WMT_CHANNEL, ReceivingConstants.RECEIVING.toUpperCase());
    headers.add(
        ReceivingConstants.WMT_INCIDENT_DOMAIN, ReceivingConstants.LOAD_QUALITY_INCIDENT_DOMAIN);

    LOGGER.info("Calling LoadQuality to create exception with request: {}", request);
    ResponseEntity<LQExceptionResponse> responseEntity;
    try {
      responseEntity =
          simpleRestConnector.exchange(
              uri.toString(),
              HttpMethod.POST,
              new HttpEntity<>(request, headers),
              LQExceptionResponse.class);
    } catch (RestClientResponseException e) {
      LOGGER.error("Something failed while closing exception in LoadQuality", e);
      throw new ReceivingForwardedException(
          HttpStatus.OK,
          e.getResponseBodyAsString(),
          "Something failed while creating exception in LoadQuality");
    } catch (ResourceAccessException e) {
      LOGGER.error("LoadQuality not accessible", e);
      throw new ReceivingBadDataException(
          ExceptionCodes.LOAD_QUALITY_NOT_ACCESSIBLE, "LoadQuality not accessible");
    }
    LOGGER.info("Successfully closed LoadQuality exception: {}", responseEntity.getBody());
    return responseEntity.getBody();
  }

  private LQExceptionResponse closeException(String issueId) {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.EXCEPTION_ID, issueId);
    URI uri =
        UriComponentsBuilder.fromUriString(
                appConfig.getLoadQualityBaseUrl() + ReceivingConstants.LQ_EXCEPTION_API_PATH)
            .buildAndExpand(pathParams)
            .toUri();

    LQExceptionCloseRequest request = new LQExceptionCloseRequest();
    request.setStatus(ReceivingConstants.CLOSED);

    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(ReceivingConstants.WM_SVC_ENV, appConfig.getLoadQualityServiceEnv());
    headers.add(ReceivingConstants.WM_SVC_NAME, appConfig.getLoadQualityServiceName());
    headers.add(ReceivingConstants.WM_CONSUMER_ID, appConfig.getReceivingConsumerId());
    headers.add(ReceivingConstants.WMT_TENANT, ReceivingConstants.LOAD_QUALITY_TENANT);
    headers.add(ReceivingConstants.WMT_SOURCE, ReceivingConstants.LOAD_QUALITY_SOURCE);
    headers.add(ReceivingConstants.WMT_CHANNEL, ReceivingConstants.RECEIVING.toUpperCase());
    headers.add(
        ReceivingConstants.WMT_INCIDENT_DOMAIN, ReceivingConstants.LOAD_QUALITY_INCIDENT_DOMAIN);

    LOGGER.info("Calling LoadQuality to close exception with request: {}", request);
    ResponseEntity<LQExceptionResponse> responseEntity;
    try {
      responseEntity =
          simpleRestConnector.exchange(
              uri.toString(),
              HttpMethod.PUT,
              new HttpEntity<>(request, headers),
              LQExceptionResponse.class);
    } catch (RestClientResponseException e) {
      LOGGER.error("Something failed while closing exception in LoadQuality", e);
      throw new ReceivingForwardedException(
          HttpStatus.OK,
          e.getResponseBodyAsString(),
          "Something failed while closing exception in LoadQuality");
    } catch (ResourceAccessException e) {
      LOGGER.error("LoadQuality not accessible", e);
      throw new ReceivingBadDataException(
          ExceptionCodes.LOAD_QUALITY_NOT_ACCESSIBLE, "LoadQuality not accessible");
    }
    LOGGER.info("Successfully closed LoadQuality exception: {}", responseEntity.getBody());
    return responseEntity.getBody();
  }

  private LQExceptionRequest getExceptionRequest(
      Shipment shipment, ContainerDTO containerDTO, ProblemType problemType) {
    LQExceptionRequest exceptionRequest = new LQExceptionRequest();
    exceptionRequest.setCreatedBy(TenantContext.getUserId());
    exceptionRequest.setStatus(ReceivingConstants.READY_TO_REVIEW);

    Contact contact = new Contact();
    contact.setLanguage(ReceivingConstants.ENGLISH);

    ChannelAttributes channelAttributes = new ChannelAttributes();
    channelAttributes.setDestFacilityCountryCode(shipment.getDestination().getCountryCode());
    channelAttributes.setDestFacilityNum(shipment.getDestination().getNumber());
    channelAttributes.setDestFacilityType(shipment.getDestination().getType());
    channelAttributes.setOriginFacilityCountryCode(shipment.getSource().getCountryCode());
    channelAttributes.setOriginFacilityNum(shipment.getSource().getNumber());
    channelAttributes.setOriginFacilityType(shipment.getSource().getType());
    contact.setChannelAttributes(channelAttributes);

    Reason reason = new Reason();
    reason.setReasonDesc(ReceivingConstants.LOAD_EXCEPTION);

    ReasonAttributes reasonAttributes = new ReasonAttributes();
    reasonAttributes.setCarrierId(shipment.getShipmentDetail().getCarrierId());
    reasonAttributes.setDeliveryNumber(containerDTO.getDeliveryNumber().toString());
    reasonAttributes.setDocumentId(shipment.getDocumentId());
    reasonAttributes.setDocumentType(shipment.getDocumentType());
    reasonAttributes.setExceptionCategory(problemType.getName().toUpperCase());
    reasonAttributes.setExceptionType(problemType.getName().toUpperCase());
    reasonAttributes.setLoadNbr(shipment.getShipmentDetail().getLoadNumber());
    reasonAttributes.setRecvCompletedTs(
        ReceivingUtils.getString(
            containerDTO.getAdditionalInformation().get(RECEIVING_COMPLETE_TS)));
    reasonAttributes.setTrailerArrivalTs(
        ReceivingUtils.getString(containerDTO.getAdditionalInformation().get(ARRIVAL_TS)));
    reasonAttributes.setShipmentNumber(shipment.getShipmentNumber());
    reasonAttributes.setShippingDate(shipment.getShipmentDate());
    List<Stop> stops = shipment.getAdditionalInfo().getStops();
    Integer stopSequenceNbr = null;
    if (!CollectionUtils.isEmpty(stops)) {
      stopSequenceNbr =
          stops
              .stream()
              .filter(
                  stop ->
                      stop.getStopId()
                          .equals(ReceivingUtils.getString(TenantContext.getFacilityNum())))
              .findFirst()
              .orElse(new Stop())
              .getStopSequenceNbr();
    }
    reasonAttributes.setStopSquenceNbr(ReceivingUtils.getString(stopSequenceNbr));
    reasonAttributes.setTrailer(shipment.getShipmentDetail().getTrailerNumber());
    reasonAttributes.setTrailerSealNumber(shipment.getShipmentDetail().getTrailerSealNumber());

    reason.setReasonAttributes(reasonAttributes);
    contact.setReason(reason);
    exceptionRequest.setContact(contact);

    Container container = new Container();
    List<Item> items = new ArrayList<>();

    Pallet pallet = new Pallet();
    pallet.setLabel(containerDTO.getSsccNumber());
    pallet.setPalletType((String) containerDTO.getContainerMiscInfo().get(PALLET_TYPE));

    if (!CollectionUtils.isEmpty(containerDTO.getContainerItems())) {
      containerDTO
          .getContainerItems()
          .forEach(
              containerItem -> {
                Item item = new Item();
                item.setInvoiceLineNumber(
                    ReceivingUtils.getString(containerItem.getInvoiceLineNumber()));
                item.setInvoiceNumber(containerItem.getInvoiceNumber());
                item.setCostCurrency(
                    ReceivingUtils.getString(
                        containerItem.getAdditionalInformation().get(COST_CURRENCY)));
                item.setItemCost(ReceivingUtils.getString(containerItem.getWhpkSell()));
                item.setReplenishmentCode(
                    ReceivingUtils.getString(
                        containerItem.getAdditionalInformation().get(REPLENISHMENT_CODE)));
                item.setDepartmentNumber(ReceivingUtils.getString(containerItem.getDeptNumber()));
                item.setItemDescription(containerItem.getDescription());
                item.setItemNumber(ReceivingUtils.getString(containerItem.getItemNumber()));
                item.setItemUpc(containerItem.getItemUPC());
                item.setPackNumber(containerItem.getContainerItemMiscInfo().get(PACK_NUMBER));
                item.setPalletNumber(containerDTO.getSsccNumber());
                item.setQty(ReceivingUtils.getString(containerItem.getQuantity()));
                item.setQtyUOM(containerItem.getQuantityUOM());
                items.add(item);
              });
    }

    container.setPallets(Collections.singletonList(pallet));
    container.setItems(items);

    exceptionRequest.setContainers(container);
    return exceptionRequest;
  }
}
