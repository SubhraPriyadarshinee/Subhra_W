package com.walmart.move.nim.receiving.core.common;

import static java.util.Objects.*;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods operating on Receipts
 *
 * @author vn50o7n
 */
public class ReceiptUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptUtils.class);

  private ReceiptUtils() {}

  public static Receipt populateValidVnpkAndWnpk(
      Receipt receipt, ReceivingCountSummary gdmSummary, String poNumber, Integer poLineNumber) {
    if (isNull(gdmSummary)) {
      LOGGER.error(
          "can't get vnpk, whpk from null gdmSummary for po = {}, poline = {} ",
          poNumber,
          poLineNumber);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_VNPK_WHPK_QTY,
          String.format(
              "can't get vnpk, whpk from null gdmSummary for po = {}, poline = {}",
              poNumber,
              poLineNumber));
    }

    final Integer vnpkQty = gdmSummary.getVnpkQty();
    final Integer whpkQty = gdmSummary.getWhpkQty();
    if (isNull(vnpkQty) || vnpkQty == 0) {
      LOGGER.error(
          "Invalid vnpkQty = {} for po = {}, poline = {} ", vnpkQty, poNumber, poLineNumber);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_VNPK_WHPK_QTY,
          String.format(
              "Invalid vnpk = %s quantity for po = %s, poline = %s",
              vnpkQty, poNumber, poLineNumber));
    }
    if (isNull(whpkQty) || whpkQty == 0) {
      LOGGER.error(
          "Invalid whpkQty = {} for po = {}, poline = {} ", whpkQty, poNumber, poLineNumber);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_VNPK_WHPK_QTY,
          String.format(
              "Invalid whpk = %s quantity for po = %s, poline = %s",
              whpkQty, poNumber, poLineNumber));
    }

    receipt.setVnpkQty(vnpkQty);
    receipt.setWhpkQty(whpkQty);

    return receipt;
  }

  public static boolean isPoFinalized(Receipt receipt) {
    return nonNull(receipt)
        && receipt.getFinalizeTs() != null
        && receipt.getFinalizedUserId() != null;
  }
}
