package com.walmart.move.nim.receiving.core.service;

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
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.CC_PBYL_LABEL_DATA_PROCESSOR)
public class CcPbyllabelProcessor extends LabelDataProcessor {

  Logger logger = LoggerFactory.getLogger(CcPbyllabelProcessor.class);

  @Override
  public String populateLabelData(Template jsonTemplate, ContainerMetaData containerMetaData) {
    ContainerMetaDataForPalletLabel containerDetails =
        (ContainerMetaDataForPalletLabel) containerMetaData;

    Map<String, Object> placeholders = new HashMap<>();
    placeholders.put(LABEL_FIELD_TRACKING_ID, containerDetails.getTrackingId());

    placeholders.put(LABEL_FIELD_DESTINATION, "");
    placeholders.put(LABEL_FIELD_ITEM_NUMBER, containerDetails.getItemNumber().toString());
    placeholders.put(LABEL_FIELD_GTIN, containerDetails.getGtin());
    placeholders.put(
        LABEL_FIELD_DESCRITPION, ReprintUtils.truncateDesc(containerDetails.getDescription()));
    placeholders.put(
        LABEL_FIELD_USER_ID, ReprintUtils.truncateUser(containerDetails.getCreateUser()));
    placeholders.put(LABEL_FIELD_DELIVERY_NUMBER, containerDetails.getDeliveryNumber().toString());
    placeholders.put(LABEL_FIELD_LOCATION, containerDetails.getLocation());
    placeholders.put(
        LABEL_FIELD_QTY,
        containerDetails.getVnpkQty() != 0
            ? containerDetails.getQuantity() / containerDetails.getVnpkQty()
            : "");
    placeholders.put(LABEL_FIELD_ORIGIN, TenantContext.getFacilityNum().toString());

    Writer labelData = ReprintUtils.getPopulatedLabelDataInTemplate(jsonTemplate, placeholders);
    if (Objects.isNull(labelData)) return null;
    return labelData.toString();
  }
}
