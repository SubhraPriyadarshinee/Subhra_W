package com.walmart.move.nim.receiving.core.mock.data;

import com.walmart.move.nim.receiving.core.entity.LabelMetaData;
import com.walmart.move.nim.receiving.core.model.LabelFormatId;
import java.util.ArrayList;
import java.util.List;

public class MockLabelMetaData {

  static String pallet_lpn_format_jsonTemplate =
      "{\"labelIdentifier\": \"trackingId\",\"formatName\": \"pallet_lpn_format\",\"data\": [{\"key\": \"LPN\",\"value\": \"trackingId\"}],\"ttlInHours\": 72.0}";
  static String case_lpn_format_jsonTemplate =
      "{\"labelIdentifier\": \"trackingId\",\"formatName\": \"case_lpn_format\",\"data\": [{\"key\": \"LPN\",\"value\": \"trackingId\"}],\"ttlInHours\": 72.0}";

  public static List<LabelMetaData> getLabelMetaData() {
    List<LabelMetaData> labelMetaDataList = new ArrayList<>();
    LabelMetaData labelMetaData1 = new LabelMetaData();
    labelMetaData1.setId(1L);
    labelMetaData1.setJsonTemplate(case_lpn_format_jsonTemplate);
    labelMetaData1.setLabelId(101);
    labelMetaData1.setLpaasFormatName("case_lpn_format");
    labelMetaData1.setLabelName(LabelFormatId.CC_DA_NON_CON_PALLET_LABEL_FORMAT);
    labelMetaData1.setFacilityCountryCode("US");
    labelMetaData1.setFacilityNum(32835);

    LabelMetaData labelMetaData2 = new LabelMetaData();
    labelMetaData2.setId(2L);
    labelMetaData2.setJsonTemplate(pallet_lpn_format_jsonTemplate);
    labelMetaData2.setLabelId(102);
    labelMetaData2.setLpaasFormatName("pallet_lpn_format");
    labelMetaData2.setLabelName(LabelFormatId.CC_NON_NATIONAL_PALLET_LABLE_FORMAT);
    labelMetaData2.setFacilityCountryCode("US");
    labelMetaData2.setFacilityNum(32835);

    labelMetaDataList.add(labelMetaData1);
    labelMetaDataList.add(labelMetaData2);
    return labelMetaDataList;
  }
}
