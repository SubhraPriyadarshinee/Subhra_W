package com.walmart.move.nim.receiving.mfc.service.problem.fixit;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.NA;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUTOMATION;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CLOSE_EXCEPTION_ID_JSON_PATH;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CREATE_EXCEPTION_ID_JSON_PATH;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RECEIVING;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WMT_CHANNEL;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WMT_EXCEPTION_CATEGORY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WMT_EXCEPTION_TYPE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WMT_SELLER_TYPE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WMT_SOURCE;

import com.jayway.jsonpath.JsonPath;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingForwardedException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.mfc.common.ProblemType;
import com.walmart.move.nim.receiving.mfc.model.problem.CreateExceptionResponse;
import com.walmart.move.nim.receiving.mfc.model.problem.fixit.Delivery;
import com.walmart.move.nim.receiving.mfc.model.problem.fixit.Detail;
import com.walmart.move.nim.receiving.mfc.model.problem.fixit.FixitCloseExceptionRequest;
import com.walmart.move.nim.receiving.mfc.model.problem.fixit.FixitCreateExceptionRequest;
import com.walmart.move.nim.receiving.mfc.model.problem.fixit.Invoice;
import com.walmart.move.nim.receiving.mfc.model.problem.fixit.Item;
import com.walmart.move.nim.receiving.mfc.model.problem.fixit.PurchaseOrder;
import com.walmart.move.nim.receiving.mfc.service.problem.ProblemRegistrationService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

public class FixitProblemService implements ProblemRegistrationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(FixitProblemService.class);

  @ManagedConfiguration protected AppConfig appConfig;

  @Resource(name = ReceivingConstants.BEAN_REST_CONNECTOR)
  private RestConnector simpleRestConnector;

  @Override
  public CreateExceptionResponse createProblem(
      Shipment shipment, ContainerDTO containerDTO, ProblemType problemType) {
    FixitCreateExceptionRequest fixitCreateExceptionRequest = new FixitCreateExceptionRequest();
    fixitCreateExceptionRequest.setDetails(getDetails(shipment, containerDTO, problemType));
    fixitCreateExceptionRequest.setDelivery(getDeliveries(shipment, containerDTO));
    fixitCreateExceptionRequest.setStore(shipment.getDestination().getNumber());
    fixitCreateExceptionRequest.setItems(getItems(containerDTO.getContainerItems()));
    fixitCreateExceptionRequest.setPurchaseOrders(getPurchaseOrders(shipment, containerDTO));
    fixitCreateExceptionRequest.setInvoices(getInvoices(containerDTO));
    fixitCreateExceptionRequest.setExceptionType(problemType.getName());

    String user = ReceivingConstants.DEFAULT_USER;
    if (Objects.nonNull(TenantContext.getAdditionalParams())
        && Objects.nonNull(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY))) {
      user =
          String.valueOf(
              TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));
    }
    fixitCreateExceptionRequest.setUserId(user);
    HttpHeaders headers = getHeaders(problemType);
    String sourceFacilityNum = shipment.getSource().getNumber();
    headers.replace(TENENT_FACLITYNUM, Collections.singletonList(sourceFacilityNum));

    String fixitGraphQLURI =
        appConfig.getFixitPlatformBaseUrl() + ReceivingConstants.FIT_GRAPH_QL_URI;
    String response = null;
    try {
      LOGGER.info(
          "FixIT Mutation Create Request = {}, Headers = {}",
          fixitCreateExceptionRequest.getRequest(),
          headers);
      response =
          simpleRestConnector
              .exchange(
                  fixitGraphQLURI,
                  HttpMethod.POST,
                  new HttpEntity<>(fixitCreateExceptionRequest.getRequest(), headers),
                  String.class)
              .getBody();

      LOGGER.info("FixIT Mutation Create Response = {} ", response);
      JsonPath.parse(response).read(CREATE_EXCEPTION_ID_JSON_PATH);
    } catch (ResourceAccessException e) {
      LOGGER.error("Fixit not accessible", e);
      throw new ReceivingBadDataException(
          ExceptionCodes.FIXIT_NOT_ACCESSIBLE, "Fixit not accessible");
    } catch (Exception e) {
      LOGGER.error("Something failed while creating exception in Fixit", e);
      throw new ReceivingForwardedException(
          HttpStatus.OK, response, "Something failed while creating exception in Fixit");
    }
    CreateExceptionResponse createExceptionResponse = new CreateExceptionResponse();
    createExceptionResponse.setResponse(response);
    createExceptionResponse.setIssueId(getExceptionId(response));
    return createExceptionResponse;
  }

  @Override
  public void closeProblem(ProblemLabel problemLabel, ProblemType problemType) {
    FixitCloseExceptionRequest fixitCloseExceptionRequest = new FixitCloseExceptionRequest();
    fixitCloseExceptionRequest.setExceptionId(problemLabel.getIssueId());
    fixitCloseExceptionRequest.setStoreNumber(problemLabel.getFacilityNum().toString());

    String fixitGraphQLURI =
        appConfig.getFixitPlatformBaseUrl() + ReceivingConstants.FIT_GRAPH_QL_URI;
    String response = null;
    try {
      LOGGER.info("FixIT Mutation Close Request = {} ", fixitCloseExceptionRequest.getRequest());

      response =
          simpleRestConnector
              .exchange(
                  fixitGraphQLURI,
                  HttpMethod.POST,
                  new HttpEntity<>(
                      fixitCloseExceptionRequest.getRequest(), getHeaders(problemType)),
                  String.class)
              .getBody();

      LOGGER.info("FixIT Mutation Close Response = {} ", response);

      JsonPath.parse(response).read(CLOSE_EXCEPTION_ID_JSON_PATH);
    } catch (ResourceAccessException e) {
      LOGGER.error("Fixit not accessible", e);
      throw new ReceivingBadDataException(
          ExceptionCodes.FIXIT_NOT_ACCESSIBLE, "Fixit not accessible");
    } catch (Exception e) {
      LOGGER.error("Something failed while creating exception in Fixit", e);
      throw new ReceivingForwardedException(
          HttpStatus.OK, response, "Something failed while closing exception in Fixit");
    }
  }

  private List<Invoice> getInvoices(ContainerDTO containerDTO) {
    List<Invoice> invoices = new ArrayList<>();
    containerDTO
        .getContainerItems()
        .forEach(
            containerItem -> {
              Invoice invoice = new Invoice();
              invoice.setInvoiceLineNumber(containerItem.getInvoiceLineNumber().toString());
              invoice.setInvoiceNumber(containerItem.getInvoiceNumber());
              invoices.add(invoice);
            });
    return invoices;
  }

  private List<Item> getItems(List<ContainerItem> containerItems) {
    List<Item> items = new ArrayList<>();
    containerItems.forEach(
        containerItem -> {
          Item item = new Item();
          item.setItemNumber(containerItem.getItemNumber().toString());
          item.setItemUpc(containerItem.getItemUPC());
          item.setItemDescription(
              Objects.isNull(containerItem.getDescription()) ? "" : containerItem.getDescription());
          item.setDepartmentNumber(
              Objects.isNull(containerItem.getDeptNumber())
                  ? "0"
                  : containerItem.getDeptNumber().toString());
          items.add(item);
        });
    return items;
  }

  private List<Delivery> getDeliveries(Shipment shipment, ContainerDTO containerDTO) {
    Delivery delivery = new Delivery();
    delivery.setDeliveryNumber(containerDTO.getDeliveryNumber().toString());
    delivery.setLoad(shipment.getShipmentDetail().getLoadNumber());
    delivery.setTrailerNumber(shipment.getShipmentDetail().getTrailerNumber());
    delivery.setSource(shipment.getSource().getNumber());
    return Collections.singletonList(delivery);
  }

  private List<Detail> getDetails(
      Shipment shipment, ContainerDTO containerDTO, ProblemType problemType) {
    Detail detail = new Detail();
    detail.setSsccNumber(containerDTO.getSsccNumber());
    String exceptionType = ProblemType.OVERAGE.equals(problemType) ? "OVG" : "MISSING_PALLET";
    detail.setExceptionType(exceptionType);
    detail.setExceptionQty(
        String.valueOf(
            containerDTO
                .getContainerItems()
                .stream()
                .map(ContainerItem::getQuantity)
                .reduce(0, Integer::sum)));
    detail.setShipmentDocId(containerDTO.getShipmentId());
    detail.setStoreNumber(shipment.getDestination().getNumber());
    detail.setDestinationBuNumber(shipment.getSource().getNumber());
    return Collections.singletonList(detail);
  }

  private List<PurchaseOrder> getPurchaseOrders(Shipment shipment, ContainerDTO containerDTO) {
    PurchaseOrder purchaseOrder = new PurchaseOrder();
    // Putting shipment id as per fixIT UI Mapping
    purchaseOrder.setSsccNumber(containerDTO.getShipmentId());
    purchaseOrder.setShipmentDocId(containerDTO.getShipmentId());
    purchaseOrder.setStoreNumber(shipment.getDestination().getNumber());
    return Collections.singletonList(purchaseOrder);
  }

  private HttpHeaders getHeaders(ProblemType problemType) {
    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(WMT_CHANNEL, AUTOMATION);
    headers.add(WMT_SOURCE, RECEIVING.toUpperCase(Locale.ROOT));
    headers.add(WMT_SELLER_TYPE, "1P");
    headers.add(WMT_EXCEPTION_CATEGORY, "STORE_PROBLEM");
    String exceptionType = ProblemType.OVERAGE.equals(problemType) ? "OVG" : "MISSING_PALLET";
    headers.add(WMT_EXCEPTION_TYPE, exceptionType);
    ReceivingUtils.getServiceMeshHeaders(
        headers,
        appConfig.getReceivingConsumerId(),
        appConfig.getFixitServiceName(),
        appConfig.getFixitServiceEnv());
    return headers;
  }

  private String getExceptionId(String response) {
    String exceptionId;
    try {
      exceptionId = JsonPath.parse(response).read(CREATE_EXCEPTION_ID_JSON_PATH);
    } catch (Exception e) {
      exceptionId = NA;
    }
    return exceptionId;
  }
}
