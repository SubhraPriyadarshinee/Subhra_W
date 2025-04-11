package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component(ReceivingConstants.RETRYABLE_DELIVERY_DOCUMENTS_SEARCH_HANDLER)
public class RetryableDeliveryDocumentsSearchHandler implements DeliveryDocumentsSearchHandler {

  @Autowired private DeliveryServiceRetryableImpl deliveryServiceRetryable;
  @Autowired private Gson gson;

  @Override
  public List<DeliveryDocument> fetchDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    String deliveryDocumentResponseString_gdm =
        deliveryServiceRetryable.findDeliveryDocument(
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            httpHeaders);
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(deliveryDocumentResponseString_gdm, DeliveryDocument[].class));
    return deliveryDocuments;
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByUpc(
      long deliveryNumber, String upcNumber, HttpHeaders httpHeaders) throws ReceivingException {
    String deliveryDocumentResponseString_gdm;
    try {
      deliveryDocumentResponseString_gdm =
          deliveryServiceRetryable.findDeliveryDocument(deliveryNumber, upcNumber, httpHeaders);
    } catch (ReceivingException receivingException) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.PO_LINE_NOT_FOUND,
          String.format(ReceivingException.PO_POLINE_NOT_FOUND, upcNumber, deliveryNumber));
    }
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(deliveryDocumentResponseString_gdm, DeliveryDocument[].class));

    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.PO_LINE_NOT_FOUND,
          String.format(ReceivingException.PO_POLINE_NOT_FOUND, upcNumber, deliveryNumber));
    }
    return deliveryDocuments;
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }
}
