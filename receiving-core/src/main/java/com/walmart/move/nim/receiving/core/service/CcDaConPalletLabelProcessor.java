package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_BU_NUMBER;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DELIVERY_NUMBER;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DESCRITPION;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_DESTINATION;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_GTIN;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_ITEM_NUMBER;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_LOCATION;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_ORIGIN;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_QTY;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_TRACKING_ID;
import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.LABEL_FIELD_USER_ID;

import com.walmart.move.nim.receiving.core.common.ReprintUtils;
import com.walmart.move.nim.receiving.core.model.ContainerMetaData;
import com.walmart.move.nim.receiving.core.model.ContainerMetaDataForPalletLabel;
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
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.CC_DA_CON_PALLET_LABEL_DATA_PROCESSOR)
public class CcDaConPalletLabelProcessor extends LabelDataProcessor {
  Logger logger = LoggerFactory.getLogger(CcDaNonConPalletLabelProcessor.class);

  @Override
  public Map<String, ContainerMetaData> getContainersMetaDataByTrackingIds(
      List<String> trackingIds) {
    Map<String, ContainerMetaData> containerMetaDataMap = new HashMap<>();
    List<ContainerMetaDataForPalletLabel> containerMetaDataForPalletLabels =
        containerService.getContainerMetaDataForPalletLabelByTrackingIds(trackingIds);
    List<ContainerMetaDataForPalletLabel> containerItemMetaDataForPalletLabels =
        containerService.getContainerItemMetaDataForPalletLabelByTrackingIds(trackingIds);

    if (Objects.nonNull(containerMetaDataForPalletLabels)
        && Objects.nonNull(containerItemMetaDataForPalletLabels)) {
      Map<String, ContainerMetaDataForPalletLabel> containerDetails = new HashMap<>();
      for (ContainerMetaDataForPalletLabel containerMetaData : containerMetaDataForPalletLabels) {
        containerDetails.put(containerMetaData.getTrackingId(), containerMetaData);
      }

      for (ContainerMetaDataForPalletLabel containerItemMetaData :
          containerItemMetaDataForPalletLabels) {
        ContainerMetaDataForPalletLabel containerMetaDataForPalletLabel =
            new ContainerMetaDataForPalletLabel();
        ContainerMetaDataForPalletLabel containerMetaData =
            containerDetails.get(containerItemMetaData.getTrackingId());
        containerMetaDataForPalletLabel.setTrackingId(containerItemMetaData.getTrackingId());
        containerMetaDataForPalletLabel.setDestination(containerMetaData.getDestination());
        containerMetaDataForPalletLabel.setItemNumber(containerItemMetaData.getItemNumber());
        containerMetaDataForPalletLabel.setGtin(containerItemMetaData.getGtin());
        containerMetaDataForPalletLabel.setDescription(containerItemMetaData.getDescription());
        containerMetaDataForPalletLabel.setCreateUser(containerMetaData.getCreateUser());
        containerMetaDataForPalletLabel.setDeliveryNumber(containerMetaData.getDeliveryNumber());
        containerMetaDataForPalletLabel.setLocation(containerMetaData.getLocation());
        containerMetaDataForPalletLabel.setNoOfChildContainers(
            containerItemMetaData.getNoOfChildContainers());
        containerMetaDataMap.put(
            containerItemMetaData.getTrackingId(), containerMetaDataForPalletLabel);
      }
    }
    return containerMetaDataMap;
  }

  @Override
  public String populateLabelData(Template jsonTemplate, ContainerMetaData containerMetaData) {
    ContainerMetaDataForPalletLabel containerDetails =
        (ContainerMetaDataForPalletLabel) containerMetaData;
    Map<String, Object> placeholders = new HashMap<>();

    placeholders.put(LABEL_FIELD_TRACKING_ID, containerDetails.getTrackingId());
    placeholders.put(
        LABEL_FIELD_DESTINATION,
        containerDetails.getDestination().get(LABEL_FIELD_BU_NUMBER)
            + " "
            + containerDetails.getDestination().get(LABEL_FIELD_COUNTRY_CODE));
    placeholders.put(LABEL_FIELD_ITEM_NUMBER, containerDetails.getItemNumber().toString());
    placeholders.put(LABEL_FIELD_GTIN, containerDetails.getGtin());
    placeholders.put(
        LABEL_FIELD_DESCRITPION, ReprintUtils.truncateDesc(containerDetails.getDescription()));
    placeholders.put(
        LABEL_FIELD_USER_ID, ReprintUtils.truncateUser(containerDetails.getCreateUser()));
    placeholders.put(LABEL_FIELD_DELIVERY_NUMBER, containerDetails.getDeliveryNumber().toString());
    placeholders.put(LABEL_FIELD_LOCATION, containerDetails.getLocation());
    placeholders.put(LABEL_FIELD_ORIGIN, TenantContext.getFacilityNum().toString());

    placeholders.put(LABEL_FIELD_QTY, containerDetails.getNoOfChildContainers());

    Writer labelData = ReprintUtils.getPopulatedLabelDataInTemplate(jsonTemplate, placeholders);
    if (Objects.isNull(labelData)) return null;
    return labelData.toString();
  }
}
