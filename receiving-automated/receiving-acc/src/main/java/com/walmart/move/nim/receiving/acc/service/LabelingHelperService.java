package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.constants.LabelingConstants;
import com.walmart.move.nim.receiving.acc.model.hawkeye.label.HawkEyeScanItem;
import com.walmart.move.nim.receiving.acc.util.ACCUtils;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ItemGroupType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author g0k0072 Helper service for label generation */
public class LabelingHelperService {
  private static final Logger LOGGER = LoggerFactory.getLogger(LabelingHelperService.class);

  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  /**
   * This method will retrieve the PO CODE based on purchaseRefType. <a
   * href="https://collaboration.wal-mart.com/display/NDOF/OF+-+Labelling+Contract">Click Here</a>
   * to go to confluence page
   *
   * @param purchaseRefType purchase reference type
   * @return String
   */
  public String getPOCode(String purchaseRefType) {
    String returnCode;
    switch (purchaseRefType) {
      case "23":
      case "33":
      case "73":
      case "83":
      case "93":
        returnCode = "AD";
        break;
      case "20":
      case "22":
      case "40":
      case "42":
      case "50":
        returnCode = "WR";
        break;
      case "10":
      case "11":
      case "13":
      case "14":
      case "18":
        returnCode = "WPM";
        break;

      default:
        returnCode = "GO";
        break;
    }
    return returnCode;
  }

  /**
   * From exception label URL
   *
   * @param deliveryNumber delivery number
   * @param upcNumber UPC
   * @return exception label URL
   */
  String formExceptionURL(long deliveryNumber, String upcNumber) {
    LOGGER.info(
        "LG: Forming exception label URL for delivery {}, UPC {}", deliveryNumber, upcNumber);
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, Long.toString(deliveryNumber));
    pathParams.put(ReceivingConstants.UPC_NUMBER, upcNumber);
    return ReceivingUtils.replacePathParams(
            accManagedConfig.getRcvBaseUrl() + ACCConstants.RCV_EXCEPTION_LABEL_URI, pathParams)
        .toString();
  }

  public ScanItem buildScanItemFromLabelData(Long deliveryNumber, LabelData labelData) {
    ScanItem scanItem;
    PossibleUPC possibleUPC =
        JacksonParser.convertJsonToObject(labelData.getPossibleUPC(), PossibleUPC.class);
    scanItem =
        ScanItem.builder()
            .item(labelData.getItemNumber())
            .possibleUPC(possibleUPC)
            .reject(
                Boolean.TRUE.equals(labelData.getIsDAConveyable())
                    ? null
                    : ACCConstants.ACL_REJECT_ERROR_CODE)
            .exceptionLabels(
                FormattedLabels.builder()
                    .seqNo("10000000")
                    .purchaseReferenceNumber(labelData.getPurchaseReferenceNumber())
                    .labelData(labelData.getLabel())
                    .lpns(new ArrayList<>())
                    .build())
            .labels(new ArrayList<>())
            .exceptionLabelURL(formExceptionURL(deliveryNumber, possibleUPC.getOrderableGTIN()))
            .build();
    return scanItem;
  }

  public HawkEyeScanItem buildHawkEyeScanItemFromLabelDataAndPoLine(
      Long deliveryNumber, DeliveryDocumentLine documentLine, LabelData labelData) {
    PossibleUPC possibleUPC =
        JacksonParser.convertJsonToObject(labelData.getPossibleUPC(), PossibleUPC.class);
    return HawkEyeScanItem.builder()
        .item(labelData.getItemNumber())
        .possibleUPC(possibleUPC)
        // setting reject reason code for scan item which are not DA conveyable
        .reject(
            Objects.nonNull(labelData.getRejectReason())
                ? labelData.getRejectReason().getRejectCode()
                : ACCUtils.getRejectCodeForNullRejectReason(labelData.getIsDAConveyable()))
        .exceptionLabels(
            FormattedLabels.builder()
                .seqNo("10000000")
                .purchaseReferenceNumber(labelData.getPurchaseReferenceNumber())
                .purchaseReferenceLineNumber(labelData.getPurchaseReferenceLineNumber())
                .labelData(null)
                .lpns(new ArrayList<>())
                .poCode(getPOCode(documentLine.getPurchaseReferenceLegacyType().toString()))
                .poEvent(documentLine.getEvent())
                .poTypeCode(documentLine.getPurchaseReferenceLegacyType().toString())
                .build())
        .labels(new ArrayList<>())
        .exceptionLabelURL(formExceptionURL(deliveryNumber, possibleUPC.getOrderableGTIN()))
        .channel(LabelingConstants.CHANNEL_VAL)
        .color(documentLine.getColor())
        .containerType(ContainerType.VENDORPACK.getText())
        .deptNumber(Integer.valueOf(documentLine.getDepartment()))
        .desc1(documentLine.getItemDescription1())
        .desc2(documentLine.getItemDescription2())
        .event(LabelingConstants.EMPTY_STRING)
        .fullUserId(LabelingConstants.FULLUSERID_SYS_ACL)
        .hazmat(
            (documentLine.getIsHazmat() != null && documentLine.getIsHazmat())
                ? LabelingConstants.HAZMAT_H
                : LabelingConstants.EMPTY_STRING)
        .itemGroupType(ItemGroupType.DA)
        .origin(documentLine.getPoDCNumber().toString())
        .packType(
            documentLine.getVnpkQty().equals(documentLine.getWhpkQty())
                ? LabelingConstants.PACKTYPE_CP
                : LabelingConstants.PACKTYPE_BP)
        .possibleUPCs(possibleUPC.getPossibleUpcAsList())
        .size(documentLine.getSize())
        .storeZone(LabelingConstants.STORE_MAP.get(Integer.valueOf(documentLine.getDepartment())))
        .vendorNumber(Integer.valueOf(documentLine.getVendorNumber()))
        .vnpkQty(documentLine.getVnpkQty())
        .build();
  }

  public FormattedLabels buildFormattedLabel(LabelData labelData) {
    return FormattedLabels.builder()
        .seqNo(labelData.getSequenceNo().toString())
        .purchaseReferenceNumber(labelData.getPurchaseReferenceNumber())
        .labelData(labelData.getLabel())
        .lpns(Arrays.asList(JacksonParser.convertJsonToObject(labelData.getLpns(), String[].class)))
        .build();
  }

  public FormattedLabels buildHawkEyeFormattedLabel(
      LabelData labelData, DeliveryDocumentLine deliveryDocumentLine) {
    return FormattedLabels.builder()
        .seqNo(labelData.getSequenceNo().toString())
        .purchaseReferenceNumber(labelData.getPurchaseReferenceNumber())
        .purchaseReferenceLineNumber(labelData.getPurchaseReferenceLineNumber())
        .labelData(null)
        .lpns(Arrays.asList(JacksonParser.convertJsonToObject(labelData.getLpns(), String[].class)))
        .poCode(getPOCode(deliveryDocumentLine.getPurchaseReferenceLegacyType().toString()))
        .poEvent(deliveryDocumentLine.getEvent())
        .poTypeCode(deliveryDocumentLine.getPurchaseReferenceLegacyType().toString())
        .build();
  }
}
