package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;

public interface DeliveryDocumentsSearchHandler {

  List<DeliveryDocument> fetchDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException;

  List<DeliveryDocument> fetchDeliveryDocumentByUpc(
      long deliveryNumber, String upcNumber, HttpHeaders httpHeaders) throws ReceivingException;

  List<DeliveryDocument> fetchDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders httpHeaders) throws ReceivingException;
}
