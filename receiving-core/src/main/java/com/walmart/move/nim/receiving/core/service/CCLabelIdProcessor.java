package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.LabelFormatId;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.CC_LABEL_ID_PROCESSOR)
public class CCLabelIdProcessor implements LabelIdProcessor {
  @Override
  public Integer getLabelId(String activityName, String containerType) {

    Integer labelId = null;

    switch (containerType) {
      case "PALLET":
        switch (activityName) {
          case "DACon":
            labelId = LabelFormatId.CC_DA_CON_PALLET_LABEL_FORMAT.getLabelId();
            break;
          case "PBYL":
            labelId = LabelFormatId.CC_PBYL_PALLET_LABEL_FORMAT.getLabelId();
            break;
          case "SSTK":
            labelId = LabelFormatId.CC_SSTK_PALLET_LABEL_FORMAT.getLabelId();
            break;
          case "DANonCon":
            labelId = LabelFormatId.CC_DA_NON_CON_PALLET_LABEL_FORMAT.getLabelId();
            break;
          case "Dock Tag":
            labelId = LabelFormatId.CC_DOCK_TAG_LABEL_FORMAT.getLabelId();
            break;
          case "DSDC":
          case "POCON":
            labelId = LabelFormatId.CC_NON_NATIONAL_PALLET_LABLE_FORMAT.getLabelId();
            break;
        }
        break;
      case "Vendor Pack":
        switch (activityName) {
          case "DACon":
            labelId = LabelFormatId.CC_DA_CON_CASE_LABEL_FORMAT.getLabelId();
            break;
          case "ACL":
            labelId = LabelFormatId.CC_ACL_LABLE_FORMAT.getLabelId();
            break;
        }
        break;
    }
    return labelId;
  }
}
