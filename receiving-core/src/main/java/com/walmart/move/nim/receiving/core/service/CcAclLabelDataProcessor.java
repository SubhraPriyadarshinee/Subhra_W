package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.EMPTY_STRING;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_BU_NUMBER;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_COLOR;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DELIVERY_NUMBER;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DEPT;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DESCRITPION;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DESTINATION;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_EVENT_CHAR;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_GTIN;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_IS_HAZMAT;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_ITEM_NUMBER;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_LOCATION;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_ORIGIN;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_PACK_TYPE;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_PO_CODE;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_PO_EVENT;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_PURCHASE_REF_LINE;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_PURCHASE_REF_NUMBER;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_QTY;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_SECONDARY_DESCRIPTION;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_SIZE;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_STORE_ZONE;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_TRACKING_ID;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_USER_ID;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_VENDOR_ID;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_VNPK;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.VALUE_BP;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.VALUE_CP;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.VALUE_HAZMAT_INDICATOR;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.model.ContainerMetaData;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForCaseLabel;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import freemarker.template.Template;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.CC_ACL_LABEL_DATA_PROCESSOR)
public class CcAclLabelDataProcessor extends LabelDataProcessor {
  Logger logger = LoggerFactory.getLogger(CcAclLabelDataProcessor.class);
  @Autowired Gson gson;

  @Override
  public Map<String, ContainerMetaData> getContainersMetaDataByTrackingIds(
      List<String> trackingIds) {
    Map<String, ContainerMetaData> containerMetaDataMap = new HashMap<>();
    List<ContainerMetaDataForCaseLabel> containerMetaDataForCaseLabels =
        containerService.getContainerMetaDataForCaseLabelByTrackingIds(trackingIds);
    for (ContainerMetaDataForCaseLabel containerMetaData : containerMetaDataForCaseLabels) {
      containerMetaDataMap.put(containerMetaData.getTrackingId(), containerMetaData);
    }
    return containerMetaDataMap;
  }

  @Override
  public String populateLabelData(Template jsonTemplate, ContainerMetaData containerMetaData) {
    ContainerMetaDataForCaseLabel containerDetails =
        (ContainerMetaDataForCaseLabel) containerMetaData;
    Map<String, Object> placeholders = new HashMap<>();
    placeholders.put(LABEL_FIELD_TRACKING_ID, containerDetails.getTrackingId());
    Map<String, String> destination = (Map<String, String>) containerDetails.getDestination();
    placeholders.put(
        LABEL_FIELD_DESTINATION,
        destination.get(LABEL_FIELD_BU_NUMBER) + " " + destination.get(LABEL_FIELD_COUNTRY_CODE));
    placeholders.put(LABEL_FIELD_ITEM_NUMBER, containerDetails.getItemNumber().toString());
    placeholders.put(LABEL_FIELD_GTIN, containerDetails.getGtin());
    placeholders.put(
        LABEL_FIELD_DESCRITPION, ReprintUtils.truncateDesc(containerDetails.getDescription()));
    placeholders.put(
        LABEL_FIELD_SECONDARY_DESCRIPTION,
        ReprintUtils.truncateDesc(containerDetails.getSecondaryDescription()));
    placeholders.put(LABEL_FIELD_VNPK, containerDetails.getVnpkQty().toString());
    placeholders.put(
        LABEL_FIELD_PURCHASE_REF_NUMBER, containerDetails.getPurchaseReferenceNumber());
    placeholders.put(
        LABEL_FIELD_PURCHASE_REF_LINE, containerDetails.getPurchaseReferenceLineNumber());
    placeholders.put(
        LABEL_FIELD_DEPT,
        Objects.nonNull(containerDetails.getPoDeptNumber())
            ? containerDetails.getPoDeptNumber()
            : "");
    placeholders.put(
        LABEL_FIELD_PACK_TYPE,
        containerDetails.getVnpkQty().equals(containerDetails.getWhpkQty()) ? VALUE_CP : VALUE_BP);
    placeholders.put(
        LABEL_FIELD_VENDOR_ID,
        Objects.nonNull(containerDetails.getVendorNumber())
            ? containerDetails.getVendorNumber().toString()
            : EMPTY_STRING);
    placeholders.put(
        LABEL_FIELD_USER_ID, ReprintUtils.truncateUser(containerDetails.getCreateUser()));
    placeholders.put(LABEL_FIELD_DELIVERY_NUMBER, containerDetails.getDeliveryNumber().toString());
    placeholders.put(LABEL_FIELD_LOCATION, containerDetails.getLocation());
    placeholders.put(LABEL_FIELD_ORIGIN, TenantContext.getFacilityNum().toString());
    placeholders.put(
        LABEL_FIELD_PO_CODE,
        ReprintUtils.computePoCode(containerDetails.getInboundChannelMethod()));
    placeholders.put(
        LABEL_FIELD_STORE_ZONE,
        Objects.nonNull(containerDetails.getPoDeptNumber())
            ? ReprintUtils.computeStoreZone(Integer.parseInt(containerDetails.getPoDeptNumber()))
            : EMPTY_STRING);
    placeholders.put(LABEL_FIELD_QTY, EMPTY_STRING);

    Map<String, String> containerItemMiscInfo =
        (Map<String, String>) containerDetails.getContainerItemMiscInfo();

    placeholders.put(
        LABEL_FIELD_SIZE,
        Objects.nonNull(containerItemMiscInfo)
                && containerItemMiscInfo.containsKey(LABEL_FIELD_SIZE)
            ? ReprintUtils.truncateSize(containerItemMiscInfo.get(LABEL_FIELD_SIZE))
            : EMPTY_STRING);
    placeholders.put(
        LABEL_FIELD_COLOR,
        Objects.nonNull(containerItemMiscInfo)
                && containerItemMiscInfo.containsKey(LABEL_FIELD_COLOR)
            ? ReprintUtils.truncateColor(containerItemMiscInfo.get(LABEL_FIELD_COLOR))
            : EMPTY_STRING);
    placeholders.put(
        LABEL_FIELD_IS_HAZMAT,
        Objects.nonNull(containerItemMiscInfo)
                && containerItemMiscInfo.containsKey(LABEL_FIELD_COLOR)
                && VALUE_HAZMAT_INDICATOR.equalsIgnoreCase(
                    containerItemMiscInfo.get(LABEL_FIELD_IS_HAZMAT))
            ? VALUE_HAZMAT_INDICATOR
            : EMPTY_STRING);

    placeholders.put(
        LABEL_FIELD_PO_EVENT,
        Objects.nonNull(containerItemMiscInfo)
                && containerItemMiscInfo.containsKey(LABEL_FIELD_PO_EVENT)
            ? containerItemMiscInfo.get(LABEL_FIELD_PO_EVENT)
            : EMPTY_STRING);
    placeholders.put(
        LABEL_FIELD_EVENT_CHAR,
        Objects.nonNull(containerItemMiscInfo)
                && containerItemMiscInfo.containsKey(LABEL_FIELD_PO_EVENT)
            ? ReprintUtils.computeEventChar(containerItemMiscInfo.get(LABEL_FIELD_PO_EVENT))
            : " ");

    Writer labelData = ReprintUtils.getPopulatedLabelDataInTemplate(jsonTemplate, placeholders);
    if (Objects.isNull(labelData)) return null;
    return labelData.toString();
  }
}
