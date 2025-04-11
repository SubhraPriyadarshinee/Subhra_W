package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component
public class ASNReceivingAuditLogger {

  private static final Logger LOG = LoggerFactory.getLogger(ASNReceivingAuditLogger.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  public void log(
      List<DeliveryDocument> gdmDeliveryDocuments, InstructionRequest instructionRequest) {
    String vendorNumber = gdmDeliveryDocuments.get(0).getVendorNumber();
    DeliveryDocumentLine deliveryDocumentLine =
        gdmDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    LOG.info(
        "RECEIVING_BY_SCANNING_UPC upc={}, delivery={}, vendorId={}, isPilotVendor={}, SSCC={}, "
            + "purchaseReferenceNumber={}, purchaseReferenceLineNumber={}, ti={}, hi={}, overageQtyLimit={}, poQuantity={}, ",
        instructionRequest.getUpcNumber(),
        gdmDeliveryDocuments.get(0).getDeliveryNumber(),
        vendorNumber,
        getVendorValidator().isPilotVendorForAsnReceiving(vendorNumber),
        instructionRequest.getSscc(),
        deliveryDocumentLine.getPurchaseReferenceNumber(),
        deliveryDocumentLine.getPurchaseReferenceLineNumber(),
        deliveryDocumentLine.getPalletTie(),
        deliveryDocumentLine.getPalletHigh(),
        deliveryDocumentLine.getOverageQtyLimit(),
        deliveryDocumentLine.getMaxReceiveQty());
  }

  public boolean isVendorEnabledForAsnReceiving(
      DeliveryDocument deliveryDocument, InstructionRequest instructionRequest) {
    Long deliveryNumber = deliveryDocument.getDeliveryNumber();
    String vendorNumber = deliveryDocument.getVendorNumber();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    if (getVendorValidator().isPilotVendorForAsnReceiving(vendorNumber)) {
      LOG.info(
          "RECEIVING_BY_SCANNING_SSCC delivery={}, vendorId={}, isPilotVendor={}, SSCC={}, asnQuantity={}, asnQuantityUom={}, purchaseReferenceNumber={}, "
              + "purchaseReferenceLineNumber={}, ti={}, hi={}, overageQtyLimit={},  ",
          deliveryNumber,
          vendorNumber,
          true,
          instructionRequest.getSscc(),
          deliveryDocumentLine.getShippedQty(),
          deliveryDocumentLine.getShippedQtyUom(),
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber(),
          deliveryDocumentLine.getPalletTie(),
          deliveryDocumentLine.getPalletHigh(),
          deliveryDocumentLine.getOverageQtyLimit());
      return true;
    } else {
      LOG.info(
          "RECEIVING_BY_SCANNING_SSCC_NOT_ALLOWED_NON_PILOT_VENDOR delivery={}, vendorId={}, isPilotVendor={}, SSCC={}, asnQuantity={}, asnQuantityUom={}, ",
          deliveryNumber,
          vendorNumber,
          false,
          instructionRequest.getSscc(),
          deliveryDocumentLine.getShippedQty(),
          deliveryDocumentLine.getShippedQtyUom());

      throw new ReceivingBadDataException(
          ExceptionCodes.SSCC_SCAN_RECEIVING_NOT_ALLOWED,
          ReceivingConstants.SSCC_SCAN_RECEIVING_NOT_ALLOWED);
    }
  }

  /**
   * This method will check if Atlas DSDC Receiving enabled for all vendor or not. If the fag is
   * enabled then return true, else if will check for the pilot vendor config to check if the given
   * vendor number is whitelisted in the config or not. If the vendor number matches then it returns
   * true for the pilot vendor DSDC receiving in Atlas
   *
   * @param deliveryDocument
   * @param instructionRequest
   * @return
   */
  public boolean isVendorEnabledForAtlasDsdcAsnReceiving(
      DeliveryDocument deliveryDocument, InstructionRequest instructionRequest) {
    boolean isVendorEnabledForAtlasDsdcReceiving = false;
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        Objects.requireNonNull(TenantContext.getFacilityNum()).toString(),
        ReceivingConstants.IS_ATLAS_DSDC_RECEIVING_ENABLED_FOR_ALL_VENDORS,
        false)) {
      isVendorEnabledForAtlasDsdcReceiving = true;
    } else {
      Long deliveryNumber = deliveryDocument.getDeliveryNumber();
      String vendorNumber = deliveryDocument.getVendorNumber();
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      if (deliveryDocument.getPoTypeCode().equals(ReceivingConstants.DSDC_PO_TYPE_CODE)) {
        if (getVendorValidator().isPilotVendorForDsdcAsnReceiving(vendorNumber)) {
          LOG.info(
              "RECEIVING_BY_SCANNING_DSDC_SSCC delivery={}, vendorId={}, isPilotVendor={}, SSCC={}, asnQuantity={}, asnQuantityUom={}, purchaseReferenceNumber={}, "
                  + "purchaseReferenceLineNumber={}, overageQtyLimit={},  ",
              deliveryNumber,
              vendorNumber,
              true,
              instructionRequest.getSscc(),
              deliveryDocumentLine.getShippedQty(),
              deliveryDocumentLine.getShippedQtyUom(),
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber(),
              deliveryDocumentLine.getOverageQtyLimit());
          isVendorEnabledForAtlasDsdcReceiving = true;
        } else {
          LOG.info(
              "RECEIVING_BY_SCANNING_DSDC_SSCC delivery={}, vendorId={}, isPilotVendor={}, SSCC={} ",
              deliveryNumber,
              vendorNumber,
              false,
              instructionRequest.getSscc());
        }
      }
    }
    return isVendorEnabledForAtlasDsdcReceiving;
  }

  private VendorValidator getVendorValidator() {
    return tenantSpecificConfigReader.getConfiguredInstance(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.VENDOR_VALIDATOR,
        VendorValidator.class);
  }
}
