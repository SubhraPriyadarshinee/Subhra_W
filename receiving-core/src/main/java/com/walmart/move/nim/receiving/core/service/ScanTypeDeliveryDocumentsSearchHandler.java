package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EMPTY_STRING;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component("scanTypeDeliveryDocumentsSearchHandler")
public class ScanTypeDeliveryDocumentsSearchHandler implements DeliveryDocumentsSearchHandler {

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Autowired private Gson gson;

  @Override
  public List<DeliveryDocument> fetchDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments_gdm = null;
    String scanType =
        Objects.isNull(instructionRequest.getReceivingType())
            ? EMPTY_STRING
            : instructionRequest.getReceivingType();
    switch (scanType) {
      case ReceivingConstants.SSCC:
        deliveryDocuments_gdm = findDeliveryDocumentsBySscc(instructionRequest, httpHeaders);
        // Check if vendor is trusted or not
        break;
      case ReceivingConstants.UPC:
      default:
        deliveryDocuments_gdm = deliveryDocumentSearchByUpc(instructionRequest, httpHeaders);
        break;
        // Post Clients switch to instructionRequest scan type, default can either throw exception
        // or
        // can be left as is.
    }

    return deliveryDocuments_gdm;
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByUpc(
      long deliveryNumber, String upcNumber, HttpHeaders httpHeaders) throws ReceivingException {
    String deliveryDocumentResponseString_gdm =
        deliveryService.findDeliveryDocument(deliveryNumber, upcNumber, httpHeaders);
    // Request doesn't have delivery document.
    return Arrays.asList(
        gson.fromJson(deliveryDocumentResponseString_gdm, DeliveryDocument[].class));
  }

  protected List<DeliveryDocument> deliveryDocumentSearchByUpc(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    String deliveryDocumentResponseString_gdm =
        deliveryService.findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);
    // Request doesn't have delivery document.
    return Arrays.asList(
        gson.fromJson(deliveryDocumentResponseString_gdm, DeliveryDocument[].class));
  }

  protected List<DeliveryDocument> findDeliveryDocumentsBySscc(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    return deliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
        instructionRequest.getDeliveryNumber(), instructionRequest.getSscc(), httpHeaders);
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }
}
