package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;

/**
 * * This class provides labelId or processor for any format We have classified these formats based
 * on tenants, 1xx series for consolidation centers, 2xx series is for Witron, 3xx Series is for Rx
 * 4xx Series is for RDC If you add new tenant, please specify here and add labelId accordingly
 */
public enum LabelFormatId {
  CC_DA_NON_CON_PALLET_LABEL_FORMAT(101, ReceivingConstants.CC_DA_NON_CON_LABEL_DATA_PROCESSOR),
  CC_DA_CON_PALLET_LABEL_FORMAT(102, ReceivingConstants.CC_DA_CON_PALLET_LABEL_DATA_PROCESSOR),
  CC_DA_CON_CASE_LABEL_FORMAT(103, ReceivingConstants.CC_DA_CON_CASE_LABEL_DATA_PROCESSOR),
  CC_SSTK_PALLET_LABEL_FORMAT(104, ReceivingConstants.CC_SSTK_LABEL_DATA_PROCESSOR),
  CC_PBYL_PALLET_LABEL_FORMAT(105, ReceivingConstants.CC_PBYL_LABEL_DATA_PROCESSOR),
  CC_NON_NATIONAL_PALLET_LABLE_FORMAT(106, ReceivingConstants.CC_NON_NATIONAL_LABEL_DATA_PROCESSOR),
  CC_ACL_LABLE_FORMAT(107, ReceivingConstants.CC_ACL_LABEL_DATA_PROCESSOR),
  CC_DOCK_TAG_LABEL_FORMAT(108, ReceivingConstants.CC_DOCKTAG_LABEL_DATA_PROCESSOR),
  WITRON_PALLET_LABEL_FORMAT(201, ReceivingConstants.WITRON_LABEL_DATA_PROCESSOR),
  RX_PALLET_LABEL_FORMAT(301, ReceivingConstants.RX_LABEL_DATA_PROCESSOR),
  RDC_PALLET_LABEL_FORMAT(401, ReceivingConstants.RDC_LABEL_DATA_PROCESSOR),
  WFS_PALLET_LABEL_FORMAT(501, ReceivingConstants.WFS_LABEL_DATA_PROCESSOR),
  WFS_CASEPACK_LABEL_FORMAT(502, ReceivingConstants.WFS_LABEL_DATA_PROCESSOR);

  private Integer labelId;
  private String labelProcessor;

  LabelFormatId(Integer labelId, String labelProcessor) {
    this.labelId = labelId;
    this.labelProcessor = labelProcessor;
  }

  public Integer getLabelId() {
    return labelId;
  }

  public String getLabelProcessor() {
    return labelProcessor;
  }

  public static String getProcessorByLabelId(Integer labelId) {
    for (LabelFormatId labelFormatId : LabelFormatId.values()) {
      if (labelFormatId.getLabelId().equals(labelId)) {
        return labelFormatId.getLabelProcessor();
      }
    }
    return null;
  }
}
