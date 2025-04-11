package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.model.ContainerItemDetails;
import com.walmart.move.nim.receiving.core.model.LabelAttributes;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.DEFAULT_REPRINT_LABEL_HANDLER)
public class DefaultReprintLabelHandler implements ReprintLabelHandler {
  @Override
  public PrintLabelRequest populateReprintLabel(
      ContainerItemDetails containerItemDetails, LabelAttributes labelAttributes) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED,
        ReceivingConstants.FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE);
  }
}
