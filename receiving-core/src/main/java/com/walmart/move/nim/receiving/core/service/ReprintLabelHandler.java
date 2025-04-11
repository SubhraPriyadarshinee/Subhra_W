package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.ContainerItemDetails;
import com.walmart.move.nim.receiving.core.model.LabelAttributes;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;

public interface ReprintLabelHandler {

  PrintLabelRequest populateReprintLabel(
      ContainerItemDetails containerItemDetails, LabelAttributes labelAttributes);
}
