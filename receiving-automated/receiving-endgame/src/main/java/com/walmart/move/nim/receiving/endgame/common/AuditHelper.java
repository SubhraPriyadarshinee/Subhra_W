package com.walmart.move.nim.receiving.endgame.common;

import static com.google.common.collect.ImmutableList.of;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.WHPK;
import static java.lang.String.valueOf;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagRequest;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagRequestItem;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.service.AuditService;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import java.util.*;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class AuditHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditHelper.class);

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  protected DeliveryMetaDataService deliveryMetaDataService;

  @Autowired protected AuditService auditService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  public Map<String, Boolean> fetchAndSaveAuditInfo(AuditFlagRequest auditFlagRequest) {
    List<AuditFlagResponse> auditFlagResponseList;
    try {
      auditFlagResponseList =
          auditService.retrieveItemAuditInfo(auditFlagRequest, ReceivingUtils.getHeaders());
    } catch (ReceivingBadDataException e) {
      LOGGER.error("Audit Info not found");
      return Collections.emptyMap();
    }
    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(auditFlagRequest.getDeliveryNumber()))
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
                        String.format(
                            EndgameConstants.DELIVERY_METADATA_NOT_FOUND_ERROR_MSG,
                            auditFlagRequest.getDeliveryNumber())));
    deliveryMetaDataService.updateAuditInfo(deliveryMetaData, auditFlagResponseList);
    return prepareAuditResponseMap(auditFlagResponseList);
  }

  public static AuditFlagRequestItem prepareAuditFlagRequestItem(
      PurchaseOrderLine poLine, Integer vendorNumber, Boolean isFrequentlyReceivedQtyRequired) {
    Long itemNumber = poLine.getItemDetails().getNumber();
    return AuditFlagRequestItem.builder()
        .itemNumber(itemNumber)
        .vendorNumber(vendorNumber)
        .qty(100)
        .qtyUom(getQtyUom(poLine.getOrdered().getUom()))
        .vnpkRatio(poLine.getVnpk().getQuantity())
        .whpkRatio(poLine.getWhpk().getQuantity())
        .orderedQty(poLine.getOrdered().getQuantity())
        .isFrequentlyReceivedQuantityRequired(isFrequentlyReceivedQtyRequired)
        .build();
  }

  private static String getQtyUom(String uom) {
    switch (uom) {
      case VNPK:
        return VENDORPACK;
      case WHPK:
        return WAREHOUSEPACK;
      default:
        return EACHES;
    }
  }

  public static boolean isNonTrustedVendor(
      AuditFlagResponse auditFlagResponse, boolean auditV2Enabled) {
    return auditV2Enabled
        ? !auditFlagResponse.getVendorType().equalsIgnoreCase(TRUSTED_VENDOR)
        : (!isNull(auditFlagResponse.getFlaggedQty())
            && auditFlagResponse.getFlaggedQty().equals(100));
  }

  private Map<String, Boolean> prepareAuditResponseMap(
      List<AuditFlagResponse> auditFlagResponseList) {
    Map<String, Boolean> auditInfoMap = new HashMap<>();
    auditFlagResponseList.forEach(
        auditResponse ->
            auditInfoMap.put(
                prepareAuditKey(auditResponse.getVendorNumber(), auditResponse.getItemNumber()),
                isNonTrustedVendor(
                    auditResponse,
                    tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED))));
    return auditInfoMap;
  }

  public static String prepareAuditKey(Integer vendorNumber, Long itemNumber) {
    return vendorNumber + DELIM_DASH + itemNumber;
  }

  public boolean isAuditRequired(
      long deliveryNumber,
      PurchaseOrderLine poLine,
      Integer vendorNumber,
      String baseDivisionCode) {
    LOGGER.debug(
        "checking audit required flag for [deliveryNumber={}], [poLineNumber={}] and [vendorNumber={}]",
        deliveryNumber,
        poLine.getPoLineNumber(),
        vendorNumber);
    boolean isAuditRequired = false;
    if (nonNull(poLine.getItemDetails())) {
      Long itemNumber = poLine.getItemDetails().getNumber();
      DeliveryMetaData deliveryMetaData =
          deliveryMetaDataService.findByDeliveryNumber(valueOf(deliveryNumber)).orElse(null);
      LinkedTreeMap<String, String> itemDetails =
          getItemDetails(deliveryMetaData, valueOf(itemNumber));
      if (hasAuditRequiredFlag(itemDetails)) {
        LOGGER.info(
            "Found audit flag in delivery metadata for [deliveryNumber={}] and [itemNumber={}]",
            deliveryNumber,
            itemNumber);

        return Boolean.parseBoolean(itemDetails.get(IS_AUDIT_REQUIRED));
      } else {
        AuditFlagRequest auditFlagRequest =
            prepareAuditFlagRequest(deliveryNumber, poLine, vendorNumber, baseDivisionCode);
        Map<String, Boolean> auditInfo = fetchAndSaveAuditInfo(auditFlagRequest);
        isAuditRequired =
            auditInfo.getOrDefault(prepareAuditKey(vendorNumber, itemNumber), Boolean.FALSE);
      }
    }
    return isAuditRequired;
  }

  private AuditFlagRequest prepareAuditFlagRequest(
      long deliveryNumber,
      PurchaseOrderLine poLine,
      Integer vendorNumber,
      String baseDivisionCode) {
    Boolean isFrequentlyReceivedQuantityRequired =
        EndGameUtils.isVnpkPalletItem(tenantSpecificConfigReader, poLine, baseDivisionCode);
    return AuditFlagRequest.builder()
        .deliveryNumber(deliveryNumber)
        .items(
            of(
                prepareAuditFlagRequestItem(
                    poLine, vendorNumber, isFrequentlyReceivedQuantityRequired)))
        .build();
  }

  private LinkedTreeMap<String, String> getItemDetails(
      DeliveryMetaData deliveryMetaData, String itemNumber) {
    LinkedTreeMap<String, String> itemDetails;
    if (isNull(deliveryMetaData.getItemDetails())) {
      itemDetails = new LinkedTreeMap<>();
      LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap = new LinkedTreeMap<>();
      itemDetailsMap.put(itemNumber, itemDetails);
      deliveryMetaData.setItemDetails(itemDetailsMap);
    } else {
      LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap =
          deliveryMetaData.getItemDetails();
      if (itemDetailsMap.containsKey(itemNumber)) {
        itemDetails = itemDetailsMap.get(itemNumber);
      } else {
        itemDetails = new LinkedTreeMap<>();
        itemDetailsMap.put(itemNumber, itemDetails);
      }
    }
    return itemDetails;
  }

  private boolean hasAuditRequiredFlag(LinkedTreeMap<String, String> itemDetails) {
    return itemDetails.containsKey(IS_AUDIT_REQUIRED);
  }
}
