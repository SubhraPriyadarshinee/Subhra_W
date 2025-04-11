package com.walmart.move.nim.receiving.mfc.transformer;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.common.Quantity;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.DecantItem;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.HawkeyeAdjustment;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class HawkeyeReceiptTransformer implements Transformer<HawkeyeAdjustment, CommonReceiptDTO> {
  private static final Logger LOGGER = LoggerFactory.getLogger(HawkeyeReceiptTransformer.class);

  @Autowired private AsyncPersister asyncPersister;

  @Override
  public void observe() {
    Transformer.super.observe();
  }

  @Override
  public CommonReceiptDTO transform(HawkeyeAdjustment hawkeyeAdjustment) {

    DecantItem decantItem =
        hawkeyeAdjustment
            .getItems()
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ReceivingInternalException(
                        ExceptionCodes.HAWK_EYE_ERROR,
                        "No Decant Item Present in Hawkeye payload"));
    List<Quantity> quantities = convertStockItem(decantItem);
    return CommonReceiptDTO.builder().gtin(decantItem.getGtin()).quantities(quantities).build();
  }

  private List<Quantity> convertStockItem(DecantItem decantItem) {

    List<Quantity> quantities = new ArrayList<>();
    decantItem
        .getStockStateChange()
        .stream()
        .forEach(
            stockStateExchange ->
                quantities.add(
                    new Quantity(
                        Long.valueOf(stockStateExchange.getQuantity()),
                        convertUOM(decantItem.getQuantityUom()),
                        mapReasonCode(stockStateExchange.getReasonCode()))));
    return quantities;
  }

  private String convertUOM(String quantityUom) {

    if (!StringUtils.equalsIgnoreCase(quantityUom, MFCConstant.UOM_EA)) {
      LOGGER.warn("Hawkeye UOM is not = {} . Hawkeye UOM={} ", MFCConstant.UOM_EA, quantityUom);
      asyncPersister.publishMetric(
          "wrongUOM_detected", "uwms-receiving", "auto-mfc", "hawkeye_decant");
    }
    return MFCConstant.UOM_EA;
  }

  private QuantityType mapReasonCode(String reasonCode) {

    switch (reasonCode) {
      case "DECANTED":
        return QuantityType.DECANTED;
      case "EXPIRED":
        return QuantityType.REJECTED;
      case "DAMAGED":
        return QuantityType.DAMAGE;
      case "COLDCHAINISSUE":
        return QuantityType.COLD_CHAIN_REJECT;
      case "NOTMFCASSORTMENT":
        return QuantityType.NOTMFCASSORTMENT;
      case "FRESHNESSEXPIRATION":
        return QuantityType.FRESHNESSEXPIRATION;
      case "MFCOVERSIZE":
        return QuantityType.MFCOVERSIZE;
      case "WRONGTEMPZONE":
        return QuantityType.WRONG_TEMP_ZONE;

      default:
        LOGGER.warn(
            "Unable to process reasonCode = {} and hence, falling back to {}",
            reasonCode,
            QuantityType.DECANTED);
        return QuantityType.DECANTED;
    }
  }

  @Override
  public List<CommonReceiptDTO> transformList(List<HawkeyeAdjustment> hawkeyeAdjustments) {
    return null;
  }

  @Override
  public HawkeyeAdjustment reverseTransform(CommonReceiptDTO receipt) {
    return null;
  }

  @Override
  public List<HawkeyeAdjustment> reverseTransformList(List<CommonReceiptDTO> d) {
    return null;
  }
}
