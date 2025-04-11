package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.contract.prelabel.LabelingService;
import com.walmart.move.nim.receiving.core.contract.prelabel.model.GDMDeliveryDocument;
import com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintableLabelData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * * This is the default implementation of Labeling Service
 *
 * @author Sitakant
 */
@Service(ReceivingConstants.DEFAULT_LABELING_SERVICE)
public class DefaultLabelingService implements LabelingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLabelingService.class);

  @Override
  public LabelData generateLabel(GDMDeliveryDocument gdmDeliveryDocument, long quantity) {
    LOGGER.warn("No Implementation for DefaultLabelingService#generateLabel");
    return null;
  }

  @Override
  public String send(LabelData data) {
    LOGGER.warn("No Implementation for DefaultLabelingService#send");
    return null;
  }

  @Override
  public void persistLabel(LabelData labelData) {
    LOGGER.warn("No Implementation for DefaultLabelingService#persistLabel");
  }

  @Override
  public String getFormattedLabelData(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      String printerType,
      String printerMode,
      String inboundDoor) {
    LOGGER.warn("No Implementation for DefaultLabelingService#getFormattedLabelData");
    return null;
  }

  @Override
  public PrintableLabelData getPrintableLabelDataRequest(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      String lpn,
      int ttlInHours,
      String inboundDoor) {
    LOGGER.warn("No Implementation for DefaultLabelingService#getPrintableLabelDataRequest");
    return null;
  }
}
