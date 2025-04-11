package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.LabelFormatId;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.RDC_LABEL_ID_PROCESSOR)
public class RDCLabelIdProcessor implements LabelIdProcessor {
  @Override
  public Integer getLabelId(String activityName, String containerType) {
    Integer labelId = null;
    if (ReceivingConstants.SSTK_ACTIVITY_NAME.equalsIgnoreCase(activityName)) {
      labelId = LabelFormatId.RDC_PALLET_LABEL_FORMAT.getLabelId();
    }
    return labelId;
  }
}
