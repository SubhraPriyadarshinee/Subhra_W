package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EMPTY_STRING;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class VendorBasedDeliveryDocumentsSearchHandler implements DeliveryDocumentsSearchHandler {

  private static final Logger LOG =
      LoggerFactory.getLogger(VendorBasedDeliveryDocumentsSearchHandler.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private Gson gson;
  @Autowired private ASNReceivingAuditLogger asnReceivingAuditLogger;

  protected List<DeliveryDocument> deliveryDocumentSearchByUpc(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
    List<DeliveryDocument> gdmDeliveryDocuments;
    try {
      String gdmDeliveryDocumentsResponse =
          getDeliveryService()
              .findDeliveryDocument(
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  instructionRequest.getUpcNumber(),
                  httpHeaders);
      gdmDeliveryDocuments =
          new ArrayList<>(
              Arrays.asList(gson.fromJson(gdmDeliveryDocumentsResponse, DeliveryDocument[].class)));
    } catch (ReceivingException e) {
      throw new ReceivingBadDataException(e.getErrorResponse().getErrorCode(), e.getMessage());
    }
    asnReceivingAuditLogger.log(gdmDeliveryDocuments, instructionRequest);
    return gdmDeliveryDocuments;
  }

  protected List<DeliveryDocument> findDeliveryDocumentBySSCCWithShipmentLinking(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
    if (getVendorValidator().isAsnReceivingEnabled()) {
      List<DeliveryDocument> deliveryDocuments = null;
      try {
        deliveryDocuments =
            getDeliveryService()
                .findDeliveryDocumentBySSCCWithShipmentLinking(
                    instructionRequest.getDeliveryNumber(),
                    instructionRequest.getSscc(),
                    httpHeaders);
      } catch (ReceivingException e) {
        throw new ReceivingBadDataException(
            ExceptionCodes.RDC_INVALID_PO_PO_LINE_STATUS,
            String.format(
                ReceivingException.NO_ACTIVE_PO_LINES_TO_RECEIVE,
                instructionRequest.getDeliveryNumber(),
                instructionRequest.getUpcNumber()),
            instructionRequest.getUpcNumber(),
            instructionRequest.getDeliveryNumber());
      }
      // other than DSDC Packs (i.e. SSTK Vendor ASN)
      if (!ReceivingUtils.isDsdcDeliveryDocuments(deliveryDocuments)) {
        checkForPilotVendor(deliveryDocuments, instructionRequest);
        // Populate autoPopulateReceivingQty flag for a set of vendors,
        // so that client can populate receive qty instead of user entering it.
        autoPopulateReceivingQtyFlag(deliveryDocuments);
      }
      return deliveryDocuments;
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.SSCC_SCAN_RECEIVING_NOT_ALLOWED,
          ReceivingConstants.SSCC_SCAN_RECEIVING_NOT_ALLOWED);
    }
  }

  protected List<DeliveryDocument> findDeliveryDocumentBySSCC(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
    if (getVendorValidator().isAsnReceivingEnabled()) {
      try {
        Optional<List<DeliveryDocument>> deliveryDocumentsBySSCCOptional =
            getDeliveryService()
                .getContainerSsccDetails(
                    instructionRequest.getDeliveryNumber(),
                    instructionRequest.getSscc(),
                    httpHeaders);
        if (!deliveryDocumentsBySSCCOptional.isPresent()) {
          LOG.info(
              "RECEIVING_BY_SCANNING_SSCC_ASN_NOT_FOUND delivery={}, SSCC={}, ",
              instructionRequest.getDeliveryNumber(),
              instructionRequest.getSscc());
          throw new ReceivingBadDataException(
              ExceptionCodes.GDM_SSCC_NOT_FOUND,
              String.format(
                  ReceivingConstants.GDM_SHIPMENT_NOT_FOUND, instructionRequest.getSscc()),
              instructionRequest.getSscc());
        }
        List<DeliveryDocument> deliveryDocuments = deliveryDocumentsBySSCCOptional.get();
        checkForPilotVendor(deliveryDocuments, instructionRequest);
        // Populate autoPopulateReceivingQty flag for a set of vendors,
        // so that client can populate receive qty instead of user entering it.
        autoPopulateReceivingQtyFlag(deliveryDocuments);
        return deliveryDocuments;
      } catch (ReceivingException e) {
        throw new ReceivingBadDataException(
            ExceptionCodes.RDC_INVALID_PO_PO_LINE_STATUS,
            String.format(
                ReceivingException.NO_ACTIVE_PO_LINES_TO_RECEIVE,
                instructionRequest.getDeliveryNumber(),
                instructionRequest.getUpcNumber()),
            instructionRequest.getUpcNumber(),
            instructionRequest.getDeliveryNumber());
      }
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.SSCC_SCAN_RECEIVING_NOT_ALLOWED,
          ReceivingConstants.SSCC_SCAN_RECEIVING_NOT_ALLOWED);
    }
  }

  private void checkForPilotVendor(
      List<DeliveryDocument> deliveryDocuments, InstructionRequest instructionRequest) {

    Optional<DeliveryDocument> deliveryDocumentOptional =
        deliveryDocuments
            .stream()
            .filter(
                deliveryDocument ->
                    checkIfInternalAsnOrPilotVendor(deliveryDocument, instructionRequest))
            .findAny();
    if (!deliveryDocumentOptional.isPresent()) {
      throw new ReceivingBadDataException(
          ExceptionCodes.SSCC_SCAN_RECEIVING_NOT_ALLOWED,
          ReceivingConstants.SSCC_SCAN_RECEIVING_NOT_ALLOWED);
    }
  }

  private boolean checkIfInternalAsnOrPilotVendor(
      DeliveryDocument deliveryDocument, InstructionRequest instructionRequest) {
    List<String> internalAsnSourceTypesList = getVendorValidator().getInternalAsnSourceTypes();
    return internalAsnSourceTypesList.contains(deliveryDocument.getSourceType())
        || asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            deliveryDocument, instructionRequest);
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
    List<DeliveryDocument> deliveryDocuments_gdm = null;
    String scanType =
        Objects.isNull(instructionRequest.getReceivingType())
            ? EMPTY_STRING
            : instructionRequest.getReceivingType();
    switch (scanType) {
      case ReceivingConstants.SSCC:
      case ReceivingConstants.WORK_STATION_SSCC:
      case ReceivingConstants.SCAN_TO_PRINT_SSCC:
        deliveryDocuments_gdm =
            findDeliveryDocumentBySSCCWithShipmentLinking(instructionRequest, httpHeaders);
        break;
      case ReceivingConstants.LPN:
        deliveryDocuments_gdm = findDeliveryDocumentBySSCC(instructionRequest, httpHeaders);
        break;
      case ReceivingConstants.UPC:
      default:
        deliveryDocuments_gdm = deliveryDocumentSearchByUpc(instructionRequest, httpHeaders);
        break;
    }

    return deliveryDocuments_gdm;
  }

  private void autoPopulateReceivingQtyFlag(List<DeliveryDocument> deliveryDocumentList) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_AUTO_POPULATE_RECEIVE_QTY_ENABLED,
        false)) {
      deliveryDocumentList
          .stream()
          .filter(this::isWhiteListedVendor4QtyAutoPopulation)
          .map(deliveryDocument -> deliveryDocument.getDeliveryDocumentLines())
          .flatMap(List::stream)
          .forEach(deliveryDocumentLine -> deliveryDocumentLine.setAutoPopulateReceivingQty(true));
    }
  }

  private boolean isWhiteListedVendor4QtyAutoPopulation(DeliveryDocument deliveryDocument) {
    return getVendorValidator()
        .getAutoPopulateReceiveQtyVendorList()
        .contains(deliveryDocument.getVendorNumber());
  }

  private VendorValidator getVendorValidator() {
    return tenantSpecificConfigReader.getConfiguredInstance(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.VENDOR_VALIDATOR,
        VendorValidator.class);
  }

  private DeliveryService getDeliveryService() {
    return tenantSpecificConfigReader.getConfiguredInstance(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.DELIVERY_SERVICE_KEY,
        DeliveryService.class);
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByUpc(
      long deliveryNumber, String upcNumber, HttpHeaders httpHeaders) throws ReceivingException {
    List<DeliveryDocument> gdmDeliveryDocuments;
    try {
      String gdmDeliveryDocumentsResponse =
          getDeliveryService().findDeliveryDocument(deliveryNumber, upcNumber, httpHeaders);
      gdmDeliveryDocuments =
          new ArrayList<>(
              Arrays.asList(gson.fromJson(gdmDeliveryDocumentsResponse, DeliveryDocument[].class)));
    } catch (ReceivingException e) {
      throw new ReceivingBadDataException(e.getErrorResponse().getErrorCode(), e.getMessage());
    }
    return gdmDeliveryDocuments;
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocumentByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    return getDeliveryService()
        .findDeliveryDocumentByItemNumber(deliveryNumber, itemNumber, headers);
  }
}
