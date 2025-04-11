package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.LabelDataConstants.EMPTY_STRING;
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
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.CC_DA_NON_CON_LABEL_DATA_PROCESSOR)
public class CcDaNonConPalletLabelProcessor extends LabelDataProcessor {

  Logger logger = LoggerFactory.getLogger(CcDaNonConPalletLabelProcessor.class);

  @Override
  public String populateLabelData(Template jsonTemplate, ContainerMetaData labelDataForReprint) {
    ContainerMetaDataForPalletLabel containerMetaData =
        (ContainerMetaDataForPalletLabel) labelDataForReprint;

    Map<String, Object> placeholders = new HashMap<>();
    placeholders.put(LABEL_FIELD_TRACKING_ID, containerMetaData.getTrackingId());

    placeholders.put(
        LABEL_FIELD_DESTINATION,
        containerMetaData.getDestination().get(LABEL_FIELD_BU_NUMBER)
            + " "
            + containerMetaData.getDestination().get(LABEL_FIELD_COUNTRY_CODE));
    placeholders.put(LABEL_FIELD_ITEM_NUMBER, containerMetaData.getItemNumber().toString());
    placeholders.put(LABEL_FIELD_GTIN, containerMetaData.getGtin());
    placeholders.put(
        LABEL_FIELD_DESCRITPION, ReprintUtils.truncateDesc(containerMetaData.getDescription()));
    placeholders.put(
        LABEL_FIELD_USER_ID, ReprintUtils.truncateUser(containerMetaData.getCreateUser()));
    placeholders.put(LABEL_FIELD_DELIVERY_NUMBER, containerMetaData.getDeliveryNumber().toString());
    placeholders.put(LABEL_FIELD_LOCATION, containerMetaData.getLocation());
    placeholders.put(LABEL_FIELD_ORIGIN, TenantContext.getFacilityNum().toString());
    placeholders.put(
        LABEL_FIELD_QTY,
        containerMetaData.getVnpkQty() != 0
            ? containerMetaData.getQuantity() / containerMetaData.getVnpkQty()
            : EMPTY_STRING);

    Writer labelData = ReprintUtils.getPopulatedLabelDataInTemplate(jsonTemplate, placeholders);
    if (Objects.isNull(labelData)) return null;
    return labelData.toString();
  }
}
