package com.walmart.move.nim.receiving.core.contract.prelabel;

import com.walmart.move.nim.receiving.core.contract.prelabel.model.GDMDeliveryDocument;
import com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintableLabelData;

/**
 * *
 *
 * <p>This is the unified interface for all kind of pre-label generation.
 *
 * @author sitakant
 */
public interface LabelingService {
  /**
   * * This method will generate the label
   *
   * @param gdmDeliveryDocument
   * @param quantity
   * @return
   */
  LabelData generateLabel(GDMDeliveryDocument gdmDeliveryDocument, long quantity);

  /**
   * * Method to send the data to downstream data based on downstream implementation
   *
   * @param data
   * @return
   */
  String send(LabelData data);

  /**
   * Method to save labels into the database
   *
   * @param labelData
   */
  void persistLabel(LabelData labelData);

  /**
   * Method to get formatted label data
   *
   * @param deliveryDocument PO
   * @param deliveryDocumentLine PO Line
   * @param printerType printer type e.g. ZEBRA
   * @param printerMode printer mode e.g. continuous
   * @return formatted label data
   */
  String getFormattedLabelData(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      String printerType,
      String printerMode,
      String inboundDoor);

  /**
   * Prepares printebale label data request for labelling service
   *
   * @param deliveryDocument delivery document
   * @param deliveryDocumentLine delivery document line
   * @param lpn LPN
   * @return printable data request
   */
  PrintableLabelData getPrintableLabelDataRequest(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      String lpn,
      int ttlInHours,
      String inboundDoor);
}
