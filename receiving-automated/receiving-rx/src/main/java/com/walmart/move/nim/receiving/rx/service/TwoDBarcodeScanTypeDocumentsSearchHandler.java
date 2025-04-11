package com.walmart.move.nim.receiving.rx.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.service.DeliveryDocumentsSearchHandler;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import java.util.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;

public class TwoDBarcodeScanTypeDocumentsSearchHandler implements DeliveryDocumentsSearchHandler {

  @Autowired private RxDeliveryServiceImpl rxDeliveryService;
  @Autowired private Gson gson;

  @Override
  public List<DeliveryDocument> fetchDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    List<DeliveryDocument> responseDeliveryDocuments = null;
    String sscc = instructionRequest.getSscc();
    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    if (StringUtils.isNotBlank(sscc)) {
      return rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
          instructionRequest.getDeliveryNumber(), sscc, httpHeaders);
    } else if (!CollectionUtils.isEmpty(scannedDataMap)) {
      ScannedData barcodeScanScannedData = scannedDataMap.get(RxConstants.BARCODE_SCAN);
      if (Objects.nonNull(barcodeScanScannedData)
          && StringUtils.equals(
              barcodeScanScannedData.getValue(), instructionRequest.getUpcNumber())) {
        long deliveryNumber = Long.parseLong(instructionRequest.getDeliveryNumber());
        String deliveryDocumentResponseByUpc =
            rxDeliveryService.findDeliveryDocument(
                deliveryNumber, instructionRequest.getUpcNumber(), httpHeaders);
        responseDeliveryDocuments =
            new ArrayList<>(
                Arrays.asList(
                    gson.fromJson(deliveryDocumentResponseByUpc, DeliveryDocument[].class)));
      } else {
        RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
        return rxDeliveryService.findDeliveryDocumentByGtinAndLotNumberWithShipmentLinking(
            instructionRequest.getDeliveryNumber(), scannedDataMap, httpHeaders);
      }
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_CREATE_INSTRUCTION_REQUEST,
          RxConstants.INVALID_CREATE_INSTRUCTION_REQUEST);
    }
    return responseDeliveryDocuments;
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByUpc(
      long deliveryNumber, String upcNumber, HttpHeaders httpHeaders) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    throw new ReceivingException(
        ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED);
  }
}
