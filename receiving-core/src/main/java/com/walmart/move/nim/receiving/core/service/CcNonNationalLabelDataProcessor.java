package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.DEFAULT_CHANNEL_METHOD;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.EMPTY_STRING;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_BU_NUMBER;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_CHANNEL_METHOD;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DELIVERY_NUMBER;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DESTINATION;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_LOCATION;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_MULTIPO;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_ORIGIN;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_OUTBOUND_CHANNEL_METHOD;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_PO1;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_PO2;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_PO3;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_QTY;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_TRACKING_ID;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_USER_ID;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.ORIGINAL_CHANNEL;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.VALUE_MULTI_PO;

import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.model.ContainerMetaData;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForNonNationalPoLabel;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import freemarker.template.Template;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.CC_NON_NATIONAL_LABEL_DATA_PROCESSOR)
public class CcNonNationalLabelDataProcessor extends LabelDataProcessor {

  Logger logger = LoggerFactory.getLogger(CcNonNationalLabelDataProcessor.class);

  @Override
  public Map<String, ContainerMetaData> getContainersMetaDataByTrackingIds(
      List<String> trackingIds) {
    Map<String, ContainerMetaData> containerMetaDataMap = new HashMap<>();
    List<ContainerMetaDataForNonNationalPoLabel> containerMetaDataForNonNationalPoLabels =
        containerService.getContainerItemMetaDataForNonNationalLabelByTrackingIds(trackingIds);
    Map<String, ContainerMetaDataForNonNationalPoLabel> trackingIdToContainerDetailMap =
        new HashMap<>();
    for (ContainerMetaDataForNonNationalPoLabel containerDetail :
        containerMetaDataForNonNationalPoLabels) {
      if (!trackingIdToContainerDetailMap.containsKey(containerDetail.getTrackingId())) {
        containerDetail.setPoList(new ArrayList<>());
        containerDetail.getPoList().add(containerDetail.getPurchaseReferenceNumber());
        containerDetail.setQuantity(containerDetail.getQuantity());
        trackingIdToContainerDetailMap.put(containerDetail.getTrackingId(), containerDetail);
      } else {
        ContainerMetaDataForNonNationalPoLabel containerDtls =
            trackingIdToContainerDetailMap.get(containerDetail.getTrackingId());
        containerDtls.getPoList().add(containerDetail.getPurchaseReferenceNumber());
        containerDtls.setQuantity(containerDetail.getQuantity() + containerDtls.getQuantity());
      }
    }
    for (Map.Entry<String, ContainerMetaDataForNonNationalPoLabel>
        containerMetaDataForNonNationalPoLabelEntry : trackingIdToContainerDetailMap.entrySet()) {
      containerMetaDataMap.put(
          containerMetaDataForNonNationalPoLabelEntry.getKey(),
          containerMetaDataForNonNationalPoLabelEntry.getValue());
    }
    return containerMetaDataMap;
  }

  @Override
  public String populateLabelData(Template jsonTemplate, ContainerMetaData containerMetaData) {
    ContainerMetaDataForNonNationalPoLabel containerDetails =
        (ContainerMetaDataForNonNationalPoLabel) containerMetaData;
    Map<String, Object> placeholders = new HashMap<>();
    placeholders.put(LABEL_FIELD_TRACKING_ID, containerDetails.getTrackingId());
    placeholders.put(
        LABEL_FIELD_OUTBOUND_CHANNEL_METHOD, containerDetails.getOutboundChannelMethod());

    placeholders.put(
        LABEL_FIELD_DESTINATION,
        containerDetails.getDestination().get(LABEL_FIELD_BU_NUMBER)
            + " "
            + containerDetails.getDestination().get(LABEL_FIELD_COUNTRY_CODE));
    placeholders.put(
        LABEL_FIELD_USER_ID, ReprintUtils.truncateUser(containerDetails.getCreateUser()));
    placeholders.put(LABEL_FIELD_DELIVERY_NUMBER, containerDetails.getDeliveryNumber().toString());
    placeholders.put(LABEL_FIELD_LOCATION, containerDetails.getLocation());
    placeholders.put(LABEL_FIELD_ORIGIN, TenantContext.getFacilityNum().toString());
    placeholders.put(
        LABEL_FIELD_PO1,
        containerDetails.getPoList().size() >= 1
            ? containerDetails.getPoList().get(0)
            : EMPTY_STRING);
    placeholders.put(
        LABEL_FIELD_PO2,
        containerDetails.getPoList().size() >= 2
            ? containerDetails.getPoList().get(1)
            : EMPTY_STRING);
    placeholders.put(
        LABEL_FIELD_PO3,
        containerDetails.getPoList().size() >= 3
            ? containerDetails.getPoList().get(2)
            : EMPTY_STRING);
    placeholders.put(
        LABEL_FIELD_MULTIPO,
        containerDetails.getPoList().size() > 1 ? VALUE_MULTI_PO : EMPTY_STRING);

    placeholders.put(
        LABEL_FIELD_CHANNEL_METHOD,
        Objects.nonNull(containerDetails.getContainerMiscInfo())
                && containerDetails.getContainerMiscInfo().containsKey(ORIGINAL_CHANNEL)
            ? containerDetails.getContainerMiscInfo().get(ORIGINAL_CHANNEL)
            : DEFAULT_CHANNEL_METHOD);

    placeholders.put(
        LABEL_FIELD_QTY,
        Objects.nonNull(containerDetails.getQuantity()) ? containerDetails.getQuantity() : "NA");

    Writer labelData = ReprintUtils.getPopulatedLabelDataInTemplate(jsonTemplate, placeholders);
    if (Objects.isNull(labelData)) return null;
    return labelData.toString();
  }
}
