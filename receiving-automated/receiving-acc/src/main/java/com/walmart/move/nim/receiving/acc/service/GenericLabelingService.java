package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.LabelingConstants;
import com.walmart.move.nim.receiving.acc.util.PrintableUtils;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.contract.prelabel.LabelingService;
import com.walmart.move.nim.receiving.core.contract.prelabel.model.GDMDeliveryDocument;
import com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintableLabelDataRequest;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/** @author g0k0072 Use the labelling library to generate store firendly ZEBRA label */
public class GenericLabelingService implements LabelingService {
  private static final Logger LOGGER = LoggerFactory.getLogger(GenericLabelingService.class);

  @Autowired private LabelingHelperService labelingHelperService;

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private PrintableUtils labellingUtils;
  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  @Override
  public LabelData generateLabel(GDMDeliveryDocument gdmDeliveryDocument, long quantity) {
    LOGGER.warn("No Implementation for GenericLabelingService#generateLabel");
    return null;
  }

  @Override
  public String send(LabelData data) {
    LOGGER.warn("No Implementation for GenericLabelingService#send");
    return null;
  }

  @Override
  public void persistLabel(LabelData labelData) {
    LOGGER.warn("No Implementation for GenericLabelingService#persistLabel");
  }

  /**
   * Prepare printable label format
   *
   * @param deliveryDocument delivery document
   * @param deliveryDocumentLine delivery document line
   * @param lpn LPN
   * @param ttlInHours ttl in hrs
   * @return
   */
  @Override
  public PrintableLabelDataRequest getPrintableLabelDataRequest(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      String lpn,
      int ttlInHours,
      String inboundDoor) {
    PrintableLabelDataRequest printableLabelDataRequest = new PrintableLabelDataRequest();
    printableLabelDataRequest.setClientId(LabelingConstants.CLIENT_ID);
    printableLabelDataRequest.setFormatName(appConfig.getPreLabelFormatName());
    printableLabelDataRequest.setLabelIdentifier(LabelingConstants.LABEL_ID_PLACEHOLDER);
    printableLabelDataRequest.setPrintRequest(Boolean.FALSE);
    List<Pair<String, String>> labelData = new ArrayList<>();
    if (!StringUtils.isEmpty(lpn)) {
      printableLabelDataRequest.setLabelIdentifier(lpn);
      printableLabelDataRequest.setFormatVersion(null);
      printableLabelDataRequest.setTtlInHours(ttlInHours);
      labelData.add(new Pair<>(LabelingConstants.LPN, lpn));
    } else {
      labelData.add(new Pair<>(LabelingConstants.LPN, LabelingConstants.LABEL_ID_PLACEHOLDER));
      printableLabelDataRequest.setFormatVersion(0);
    }
    labelData.add(
        new Pair<>(LabelingConstants.LABEL_DATE, LabelingConstants.LABEL_DATE_PLACEHOLDER));
    labelData.add(new Pair<>(LabelingConstants.FULLUSERID, LabelingConstants.FULLUSERID_SYS_ACL));
    String hazMat =
        (deliveryDocumentLine.getIsHazmat() != null && deliveryDocumentLine.getIsHazmat())
            ? LabelingConstants.HAZMAT_H
            : LabelingConstants.EMPTY_STRING;
    labelData.add(new Pair<>(LabelingConstants.HAZMAT, hazMat));
    labelData.add(
        new Pair<>(
            LabelingConstants.POCODE,
            labelingHelperService.getPOCode(deliveryDocument.getPurchaseReferenceLegacyType())));
    labelData.add(
        new Pair<>(LabelingConstants.VENDORID, deliveryDocumentLine.getVendorStockNumber()));
    labelData.add(new Pair<>(LabelingConstants.COLOR, deliveryDocumentLine.getColor()));
    labelData.add(new Pair<>(LabelingConstants.QTY, LabelingConstants.EMPTY_STRING));
    labelData.add(new Pair<>(LabelingConstants.DEPT, deliveryDocument.getDeptNumber()));
    labelData.add(
        new Pair<>(LabelingConstants.CPQTY, String.valueOf(deliveryDocumentLine.getVnpkQty())));
    labelData.add(new Pair<>(LabelingConstants.DESC1, deliveryDocumentLine.getItemDescription1()));
    labelData.add(new Pair<>(LabelingConstants.DESC2, deliveryDocumentLine.getItemDescription2()));
    if (deliveryDocumentLine.getVnpkQty().equals(deliveryDocumentLine.getWhpkQty())) {
      labelData.add(new Pair<>(LabelingConstants.PACKTYPE, LabelingConstants.PACKTYPE_CP));
    } else {
      labelData.add(new Pair<>(LabelingConstants.PACKTYPE, LabelingConstants.PACKTYPE_BP));
    }
    labelData.add(new Pair<>(LabelingConstants.SIZE, deliveryDocumentLine.getSize()));
    labelData.add(new Pair<>(LabelingConstants.DSDC, LabelingConstants.EMPTY_STRING));
    labelData.add(new Pair<>(LabelingConstants.POEVENT, deliveryDocumentLine.getEvent()));
    labelData.add(new Pair<>(LabelingConstants.EVENTCHAR, LabelingConstants.EMPTY_STRING));
    labelData.add(
        new Pair<>(
            LabelingConstants.STOREZONE,
            LabelingConstants.STORE_MAP.get(Integer.valueOf(deliveryDocument.getDeptNumber()))));
    labelData.add(new Pair<>(LabelingConstants.REPRINT, LabelingConstants.EMPTY_STRING));
    labelData.add(new Pair<>(LabelingConstants.ITEM, deliveryDocumentLine.getItemNbr().toString()));
    labelData.add(new Pair<>(LabelingConstants.PRINTER, LabelingConstants.EMPTY_STRING));
    labelData.add(
        new Pair<>(
            LabelingConstants.POLINE,
            String.valueOf(deliveryDocumentLine.getPurchaseReferenceLineNumber())));
    labelData.add(new Pair<>(LabelingConstants.DESTINATION, LabelingConstants.EMPTY_STRING));
    labelData.add(new Pair<>(LabelingConstants.CHANNEL, LabelingConstants.CHANNEL_VAL));
    labelData.add(new Pair<>(LabelingConstants.ORIGIN, deliveryDocument.getPoDCNumber()));
    labelData.add(
        new Pair<>(LabelingConstants.DELIVERY, deliveryDocument.getDeliveryNumber().toString()));
    labelData.add(
        new Pair<>(
            LabelingConstants.DOOR,
            StringUtils.isEmpty(inboundDoor) ? LabelingConstants.EMPTY_STRING : inboundDoor));
    LOGGER.trace(
        "Item UPC = {} and Case UPC = {}",
        deliveryDocumentLine.getItemUPC(),
        deliveryDocumentLine.getCaseUPC());
    labelData.add(
        new Pair<>(
            LabelingConstants.UPCBAR,
            Objects.isNull(deliveryDocumentLine.getItemUPC())
                ? deliveryDocumentLine.getCaseUPC()
                : deliveryDocumentLine.getItemUPC()));
    labelData.add(
        new Pair<>(LabelingConstants.PACK, String.valueOf(deliveryDocumentLine.getVnpkQty())));
    labelData.add(new Pair<>(LabelingConstants.PO, deliveryDocument.getPurchaseReferenceNumber()));
    printableLabelDataRequest.setLabelData(labelData);
    return printableLabelDataRequest;
  }

  /**
   * This method generates formatted label data with LPN and LABEL DATETIME
   *
   * @param deliveryDocument PO
   * @param deliveryDocumentLine PO LINE
   * @param printerType print type like ZEBRA, MONARCH etc.
   * @param printerMode print mode like Continuous, Peel
   * @return formatted label data for provided printer type and mode
   */
  @Override
  public String getFormattedLabelData(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      String printerType,
      String printerMode,
      String inboundDoor) {
    List<PrintableLabelDataRequest> printableLabelDataRequests = new ArrayList<>();
    PrintableLabelDataRequest printableLabelDataRequest =
        getPrintableLabelDataRequest(deliveryDocument, deliveryDocumentLine, null, 0, inboundDoor);
    printableLabelDataRequests.add(printableLabelDataRequest);

    String accLabelDataString =
        labellingUtils.getLabelData(printableLabelDataRequests, printerType, printerMode);

    return accLabelDataString;
  }
}
