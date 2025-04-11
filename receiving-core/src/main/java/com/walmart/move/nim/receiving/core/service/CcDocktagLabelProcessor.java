package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DATE;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DELIVERY_NUMBER;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_LOCATION;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_TRACKING_ID;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_USER_ID;

import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.model.ContainerMetaData;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForDockTagLabel;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import freemarker.template.Template;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.CC_DOCKTAG_LABEL_DATA_PROCESSOR)
public class CcDocktagLabelProcessor extends LabelDataProcessor {

  Logger logger = LoggerFactory.getLogger(CcDocktagLabelProcessor.class);

  @Override
  public Map<String, ContainerMetaData> getContainersMetaDataByTrackingIds(
      List<String> trackingIds) {
    Map<String, ContainerMetaData> containerMetaDataMap = new HashMap<>();
    List<ContainerMetaDataForDockTagLabel> containerMetaDataForDockTagLabels =
        containerService.getContainerItemMetaDataForDockTagLabelByTrackingIds(trackingIds);
    for (ContainerMetaDataForDockTagLabel containerMetaData : containerMetaDataForDockTagLabels) {
      containerMetaDataMap.put(containerMetaData.getTrackingId(), containerMetaData);
    }
    return containerMetaDataMap;
  }

  @Override
  public String populateLabelData(Template jsonTemplate, ContainerMetaData containerMetaData) {
    ContainerMetaDataForDockTagLabel containerDetails =
        (ContainerMetaDataForDockTagLabel) containerMetaData;

    Map<String, Object> placeholders = new HashMap<>();
    placeholders.put(LABEL_FIELD_TRACKING_ID, containerDetails.getTrackingId());
    placeholders.put(
        LABEL_FIELD_USER_ID, ReprintUtils.truncateUser(containerDetails.getCreateUser()));
    placeholders.put(LABEL_FIELD_DELIVERY_NUMBER, containerDetails.getDeliveryNumber().toString());
    placeholders.put(LABEL_FIELD_LOCATION, containerDetails.getLocation());
    placeholders.put(
        LABEL_FIELD_DATE, new SimpleDateFormat("MM/dd/yy").format(containerDetails.getCreateTs()));

    Writer labelData = ReprintUtils.getPopulatedLabelDataInTemplate(jsonTemplate, placeholders);
    if (Objects.isNull(labelData)) return null;
    return labelData.toString();
  }
}
