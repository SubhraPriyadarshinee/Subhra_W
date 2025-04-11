package com.walmart.move.nim.receiving.rx.service.v2.validation.data;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionPersisterService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;

@Component
public class CreateInstructionDataValidator {

  private static final Logger LOG = LoggerFactory.getLogger(CreateInstructionDataValidator.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private RxInstructionPersisterService rxInstructionPersisterService;
  @Autowired private RxInstructionHelperService rxInstructionHelperService;
  public static String GDM_EXPIRY_NOT_MATCHING_SCAN_EXPIRY = "GDM_EXPIRY_NOT_MATCHING_SCAN_EXPIRY";
  public static String GDM_LOT_NOT_MATCHING_SCAN_LOT = "GDM_LOT_NOT_MATCHING_SCAN_LOT";

  public void validateScanForEPCISUnit2DRecv(
      DeliveryDocumentLine autoSelectedDeliveryDocumentLine, RxReceivingType receivingType)
      throws ReceivingException {
    if (Objects.nonNull(receivingType)) {
      if (Objects.isNull(autoSelectedDeliveryDocumentLine.getAdditionalInfo().getIsSerUnit2DScan())
              && RxReceivingType.TWOD_BARCODE_PARTIALS == receivingType) {
        LOG.error(
                "Case 2D not allowed inside the partial case flow");
        throw new ReceivingBadDataException(
                ExceptionCodes.CASE_2D_NOT_ALLOWED_PARTIAL_FLOW,
                ExceptionDescriptionConstants.CASE_2D_NOT_ALLOWED_PARTIAL_FLOW);
      }
    }
  }

  public void performDeliveryDocumentLineValidations(DeliveryDocumentLine deliveryDocumentLine) {
    if (null == deliveryDocumentLine) {
      LOG.error(
              "No Delivery Document lines");
      throw new ReceivingBadDataException(
              ExceptionCodes.AUTO_SELECT_PO_NO_OPEN_QTY, RxConstants.AUTO_SELECT_PO_NO_OPEN_QTY);
    }
    if (StringUtils.isEmpty(deliveryDocumentLine.getDeptNumber())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.DEPT_UNAVAILABLE, RxConstants.DEPT_TYPE_UNAVAILABLE);
    }
    if (CollectionUtils.isEmpty(deliveryDocumentLine.getShipmentDetailsList())) {
      LOG.error(
              "No shipments in the Delivery Document line");
      throw new ReceivingBadDataException(
          ExceptionCodes.SHIPMENT_UNAVAILABLE, RxConstants.SHIPMENT_DETAILS_UNAVAILABLE);
    }
    if (RxUtils.isControlledSubstance(deliveryDocumentLine)) {
      LOG.error(
          "Given item {} belonging is a controlled substance. Don't receive",
          deliveryDocumentLine.getItemNbr());
      throw new ReceivingBadDataException(
          ExceptionCodes.CREATE_INSTRUCTION_CONTROLLED_SUBSTANCE_ERROR,
          String.format(
              ReceivingConstants.CONTROLLED_SUBSTANCE_ERROR, deliveryDocumentLine.getItemNbr()));
    }

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(RX_XBLOCK_FEATURE_FLAG)
        && RxUtils.isItemXBlocked(deliveryDocumentLine)) {
      LOG.error(
          "Given item:{} and handlingCode:{} matches with X-blocked item criteria.",
          deliveryDocumentLine.getItemNbr(),
          deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
      throw new ReceivingBadDataException(
          ExceptionCodes.CREATE_INSTRUCTION_ERROR,
          String.format(
              ReceivingConstants.X_BLOCK_ITEM_ERROR_MSG, deliveryDocumentLine.getItemNbr()),
          String.valueOf(deliveryDocumentLine.getItemNbr()));
    }
  }

  public void validatePartialsInSplitPallet(
          InstructionRequest instructionRequest, boolean lessThanCase) throws ReceivingBadDataException {
    if (lessThanCase && RxUtils.isSplitPalletInstructionRequest(instructionRequest)) {
      throw new ReceivingBadDataException(
              ExceptionCodes.PARTIALS_NOT_ALLOWED_IN_SPLIT_PALLET,
              RxConstants.PARTIALS_NOT_ALLOWED_IN_SPLIT_PALLET);
    }
  }

  public void validateScannedData(Map<String, ScannedData> scannedDataMap,
                                         String applicationIdentifier,
                                         String errorMessage) {
    if (org.springframework.util.CollectionUtils.isEmpty(scannedDataMap)) {
      throw new ReceivingBadDataException(
              ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA);
    }
    ScannedData scannedData = scannedDataMap.get(applicationIdentifier);
    if (Objects.isNull(scannedData) || org.apache.commons.lang.StringUtils.isEmpty(scannedData.getValue())) {
      throw new ReceivingBadDataException(
              ExceptionCodes.INVALID_SCANNED_DATA, errorMessage);
    }
  }


  public void throwEPCISProblemDataNotFound() throws ReceivingException {
    throw new ReceivingBadDataException(
        ExceptionCodes.PROBLEM_NOT_FOUND, ReceivingException.PROBLEM_NOT_FOUND);
  }

  public void isNewInstructionCanBeCreated(
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber,
      int maxReceiveQty,
      long totalReceivedQty,
      boolean isProblemReceiving,
      boolean isSplitPalletInstruction,
      String userId)
      throws ReceivingException {
    rxInstructionPersisterService.checkIfNewInstructionCanBeCreated(
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        totalReceivedQty,
        maxReceiveQty,
        isSplitPalletInstruction,
        userId);
  }

  /**
   * @param currentNodeResponse GDM currentNode API response
   * @return false if [isEpcisDataNotFound && isAutoSwitchEpcisToAsn] || !isEpcisFlagEnabled
   */
  public boolean validateCurrentNodeResponse(SsccScanResponse currentNodeResponse, boolean isMultiSkuReceivingType) {

    boolean isEpcisDataNotFound = validateGdmResponse(currentNodeResponse);

    if (isMultiSkuReceivingType && isEpcisDataNotFound){
      LOG.info("[LT] Error::No EPCIS data for EPCIS vendor and unable to switch to ASN flow");
      throw new ReceivingBadDataException(
              ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND, ReceivingException.EPCIS_DATA_UNAVAILABLE);
    }

    boolean isAutoSwitchEpcisToAsn =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.AUTO_SWITCH_EPCIS_TO_ASN,
            false);

    boolean isEpcisFlagEnabled = validateSerialInfoEnabled(currentNodeResponse);

    if (!isEpcisFlagEnabled) {   //EPCIS Vendor Flag disabled
      LOG.info("[LT] Info::EPCIS flag is Disabled for Vendor - Navigating to ASN flow");
      return false;
    } else { //EPCIS Vendor Flag enabled
      if (!isEpcisDataNotFound) {
        return true;
      } else {
        if (!isAutoSwitchEpcisToAsn) {
          LOG.info("[LT] Error::No EPCIS data for EPCIS vendor and isAutoSwitchEpcisToAsn is disabled");
          throw new ReceivingBadDataException(
                  ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND, ReceivingException.EPCIS_DATA_UNAVAILABLE);
        } else {
          LOG.info("[LT] Info::EPCIS flag is Enabled for Vendor and isAutoSwitchEpcisToAsn is enabled -- No EPCIS data. Navigating to ASN flow");
          return false;
        }
      }
    }
  }

  /**
   * @param ssccScanResponse GDM API response
   * @return whether GDM_EPCIS_DATA_404
   */
  public boolean validateGdmResponse(SsccScanResponse ssccScanResponse) {

    List<String> errorCodes = Arrays.asList(ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND,
            ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND_FOR_LEVEL_0 ,
            ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND_FOR_PO,
            ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND_FOR_PO_LINE);

    return null == ssccScanResponse
            || (!CollectionUtils.isEmpty(ssccScanResponse.getErrors())
            && ssccScanResponse
            .getErrors()
            .stream()
            .anyMatch( x -> errorCodes.contains(x.getErrorCode())));
  }

  /**
   * @param ssccScanResponse GDM API response
   * @return whether vendor is serialInfoEnabled
   */
  public boolean validateSerialInfoEnabled(SsccScanResponse ssccScanResponse) {
    if (!CollectionUtils.isEmpty(ssccScanResponse.getPurchaseOrders())){
      List<PurchaseOrder> purchaseOrders = ssccScanResponse.getPurchaseOrders();
      if(Objects.nonNull(purchaseOrders.get(0).getVendorInformation())){
          return purchaseOrders.get(0).getVendorInformation().isSerialInfoEnabled();
      }
    }
    return false;
  }

  public void validateNodesReceivingStatus(String status) {
    if (status.equalsIgnoreCase(RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS)) {
      LOG.error(
              "Barcode already received");
      throw new ReceivingBadDataException(
              ExceptionCodes.BARCODE_ALREADY_RECEIVED,
              ExceptionDescriptionConstants.BARCODE_ALREADY_RECEIVED);
    }
  }
  public void validatePartiallyReceivedContainers(String status) {
    if (status.equalsIgnoreCase(PARTIALLY_RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS)) {
      throw new ReceivingBadDataException(
              ExceptionCodes.BARCODE_ALREADY_RECEIVED,
              ReceivingException.BARCODE_PARTIALLY_RECEIVED);
    }
  }

  public void validateCurrentNodeExpiryAndLot(
          InstructionRequest instructionRequest,
          SsccScanResponse currentNodeGdmResponse
  ) {
    SsccScanResponse.Container currentNodeGdmResponseContainer = currentNodeGdmResponse.getContainers().get(0);
    List<ScannedData> scannedDataList = instructionRequest.getScannedDataList();
    Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(scannedDataList);

    String simpleDateFormatExpiry = null;
    try {
        ScannedData expDateScannedData = scannedDataMap.get(ReceivingConstants.KEY_EXPIRY_DATE);
        Date expDate = DateUtils.parseDate(expDateScannedData.getValue(), ReceivingConstants.EXPIRY_DATE_FORMAT);
        simpleDateFormatExpiry = new SimpleDateFormat(ReceivingConstants.SIMPLE_DATE).format(expDate);
    } catch (ParseException e) {
        throw new RuntimeException(e);
    }

    if (ObjectUtils.anyNull(simpleDateFormatExpiry, currentNodeGdmResponseContainer,
            currentNodeGdmResponseContainer.getExpiryDate())
            || !currentNodeGdmResponseContainer.getExpiryDate().equalsIgnoreCase(simpleDateFormatExpiry)
    ) {
      throw new ReceivingBadDataException(
              ExceptionCodes.SCANNED_EXPIRY_DATE_DETAILS_DO_NOT_MATCH, GDM_EXPIRY_NOT_MATCHING_SCAN_EXPIRY);
    }
    if (ObjectUtils.anyNull(currentNodeGdmResponseContainer, currentNodeGdmResponseContainer.getLotNumber()) ||
            !currentNodeGdmResponseContainer.getLotNumber()
                    .equalsIgnoreCase(scannedDataMap.get(ReceivingConstants.KEY_LOT).getValue())) {
      throw new ReceivingBadDataException(
              ExceptionCodes.SCANNED_LOT_DETAILS_DO_NOT_MATCH, GDM_LOT_NOT_MATCHING_SCAN_LOT);
    }
    if (Objects.isNull(instructionRequest.getProblemTagId())) {
      rxInstructionHelperService.checkIfContainerIsCloseDated(scannedDataMap);
    }
  }
}
