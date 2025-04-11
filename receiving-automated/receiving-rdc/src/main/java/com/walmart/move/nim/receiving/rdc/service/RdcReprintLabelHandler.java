package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.model.ContainerItemDetails;
import com.walmart.move.nim.receiving.core.model.LabelAttributes;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.service.ReprintLabelHandler;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.RDC_REPRINT_LABEL_HANDLER)
public class RdcReprintLabelHandler implements ReprintLabelHandler {
  @Override
  public PrintLabelRequest populateReprintLabel(
      ContainerItemDetails containerItemDetails, LabelAttributes labelAttributes) {
    return LabelGenerator.populateReprintPalletSSTKLabel(
        containerItemDetails, labelAttributes, true);
  }
}
