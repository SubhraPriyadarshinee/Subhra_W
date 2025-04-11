package com.walmart.move.nim.receiving.wfs.service;

import com.walmart.move.nim.receiving.core.model.LabelFormatId;
import com.walmart.move.nim.receiving.core.service.LabelIdProcessor;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;

public class WFSLabelIdProcessor implements LabelIdProcessor {

  @Override
  public Integer getLabelId(String activityName, String containerType) {
    // TODO: need to confirm if this needs to exist, so as to isolate responsibility for WFS Labels
    // from CC LabelId Processor (as other markets are doing the same)
    Integer labelId = null;

    if (ContainerType.PALLET.name().equals(containerType)) {
      if (WFSConstants.WFS_PALLET.equals(activityName)) {
        labelId = LabelFormatId.WFS_PALLET_LABEL_FORMAT.getLabelId();
        return labelId;
      }
      if (ReceivingConstants.DOCK_TAG.equals(activityName)) {
        labelId = LabelFormatId.CC_DOCK_TAG_LABEL_FORMAT.getLabelId();
        return labelId;
      }
    }
    // Here in CasePack flow, the containerType is coming from
    // com/walmart/move/nim/receiving/core/service/ContainerService.java:2763
    // needs to check containerType value with get text, instead of name
    // in WFSDocktagService, ContainerType.PALLET.name() is being used, so above has to be the same
    // or that change can be made
    if (ContainerType.VENDORPACK.getText().equals(containerType)) {
      if (WFSConstants.WFS_CASEPACK.equals(activityName)) {
        labelId = LabelFormatId.WFS_CASEPACK_LABEL_FORMAT.getLabelId();
        return labelId;
      }
    }

    return labelId;
  }
}
