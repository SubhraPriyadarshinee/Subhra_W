package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Primary
@Component(value = ReceivingConstants.DEFAULT_DOCUMENTS_SEARCH_HANDLER)
public class DefaultDeliveryDocumentsSearchHandler implements DeliveryDocumentsSearchHandler {

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Autowired private Gson gson;

  @Override
  public List<DeliveryDocument> fetchDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    // Request doesn't have delivery document.
    return fetchDeliveryDocumentByUpc(
        Long.parseLong(instructionRequest.getDeliveryNumber()),
        instructionRequest.getUpcNumber(),
        httpHeaders);
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByUpc(
      long deliveryNumber, String upcNumber, HttpHeaders httpHeaders) throws ReceivingException {
    String deliveryDocumentResponseString_gdm =
        deliveryService.findDeliveryDocument(deliveryNumber, upcNumber, httpHeaders);
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(deliveryDocumentResponseString_gdm, DeliveryDocument[].class));
    return deliveryDocuments;
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }
}
