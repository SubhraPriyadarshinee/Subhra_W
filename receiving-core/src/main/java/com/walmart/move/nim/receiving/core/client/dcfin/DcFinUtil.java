package com.walmart.move.nim.receiving.core.client.dcfin;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVALID_DATA;
import static com.walmart.move.nim.receiving.utils.common.GdmToDCFinChannelMethodResolver.getDCFinChannelMethod;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.BASE_DIVISION_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.COUNTRY_CODE_US;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INVENTORY_RECEIVING_CORRECTION_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RC_DESC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.DCFIN_LB_ZA;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR_REASON_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR_REASON_DESC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WHPK;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.util.StringUtils.isEmpty;

import com.walmart.move.nim.receiving.core.client.dcfin.model.DcFinAdjustRequest;
import com.walmart.move.nim.receiving.core.client.dcfin.model.TransactionsItem;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility method for DcFin Rest Client. Use Transformation for request, response and preparing
 * objects without taking RestClient or CallingPrograms business space
 *
 * @author k0c0e5k
 */
public class DcFinUtil {
  private static final Logger LOG = LoggerFactory.getLogger(DcFinUtil.class);

  public static final String CARRIER_NAME = "carrierName";
  public static final String CARRIER_SCAC_CODE = "carrierScacCode";
  public static final String TRAILER_NBR = "trailerNbr";
  public static final String BILL_CODE = "billCode";
  public static final String FREIGHT_BILL_QTY = "freightBillQty";

  public static final String MISSING_REQUIRED_FIELD = "Missing required field %s(%s)";
  public static final String VALID_TYPES_PO_ASN_CO_STO_0_9_$ = "PO|ASN|CO|STO|^[0-9]+$";
  public static final String INVALID_DOC_TYPE_MSG =
      "Invalid docType(%s), should be one amongst PO|ASN|CO|STO or Any Number";
  public static final String VALID_BILL_CODES = "COL|PPD|COLL|PRP|UN";
  public static final String INVALID_BILL_CODE_MSG =
      "Invalid billCode(%s), should be one amongst COL|PPD|COLL|PRP|UN";
  public static final String VALID_PURCHASE_REF_TYPE = "Staplestock|Crossdock|DSDC|POCON";
  public static final String INVALID_PURCHASE_REF_TYPE_MSG =
      "Invalid inboundChannelMethod(%s), should be one amongst Staplestock|Crossdock|DSDC|POCON";
  public static final String VALID_FINANCIAL_REPORTING_GROUP = "US|UK|CN";
  public static final String INVALID_FINANCIAL_REPORT_GRP_MSG =
      "Invalid financialReportGrpCode(%s), should be one amongst US|UK|CN";
  public static final String BASE_DIVISION_CODE1 = "BaseDivisionCode";
  public static final String VALID_QTY_UOM = "EA|PH|ZA";
  public static final String INVALID_QTY_UOM_MSG =
      "Invalid QtyUOM(%s), should be one amongst EA|PH|ZA";
  public static final String QUANTITY = "Quantity";
  public static final String INVALID_QTY_VNPK_WHPK_MSG =
      "Quantity(%s) should be divisible by (%s)%s";
  public static final String BR = "<br/>";

  /**
   * Creates Request object for DcFin Adjust call for both VTR and Receiving Correction as per its
   * contract
   *
   * @param container
   * @param txnId
   * @param reasonCode
   * @return
   */
  public static DcFinAdjustRequest createDcFinAdjustRequest(
      Container container, String txnId, int reasonCode, Integer quantityDiff) {
    final DcFinAdjustRequest dcFinAdjustRequest = new DcFinAdjustRequest();
    dcFinAdjustRequest.setTxnId(txnId);
    dcFinAdjustRequest.setTransactions(getTransactionsItems(container, reasonCode, quantityDiff));
    return dcFinAdjustRequest;
  }

  private static List<TransactionsItem> getTransactionsItems(
      Container container, int reasonCode, Integer quantityDiff) {
    List<TransactionsItem> transactionsItems = new ArrayList<>(1);
    final ContainerItem ci = container.getContainerItems().get(0);
    TransactionsItem ti = new TransactionsItem();
    // required
    ti.setItemNumber(ci.getItemNumber());
    ti.setBaseDivCode(
        isEmpty(ci.getBaseDivisionCode()) ? BASE_DIVISION_CODE : ci.getBaseDivisionCode());
    if (VTR_REASON_CODE == reasonCode) {
      ti.setPrimaryQty(-1 * ci.getQuantity()); // same quantity as -ve
      ti.setReasonCodeDesc(VTR_REASON_DESC);
    } else if (INVENTORY_RECEIVING_CORRECTION_REASON_CODE == reasonCode) {
      ti.setPrimaryQty(quantityDiff); // quantityDiff
      ti.setReasonCodeDesc(RC_DESC);
    }
    ti.setDeliveryNum(container.getDeliveryNumber().toString());
    ti.setDocumentNum(ci.getPurchaseReferenceNumber());
    ti.setDocumentLineNo(ci.getPurchaseReferenceLineNumber());
    ti.setReasonCode(valueOf(reasonCode));
    ti.setItemNumber(ci.getItemNumber());
    ti.setBaseDivCode(
        isEmpty(ci.getBaseDivisionCode()) ? BASE_DIVISION_CODE : ci.getBaseDivisionCode());
    ti.setPrimaryQtyUOM(ci.getQuantityUOM());
    ti.setInboundChannelMethod(getDCFinChannelMethod(ci.getInboundChannelMethod()));
    ti.setDateAdjusted(new Date());
    ti.setSecondaryQty(ci.getVnpkWgtQty());
    ti.setSecondaryQtyUOM(DCFIN_LB_ZA);
    ti.setWeightFormatType(ci.getWeightFormatTypeCode());
    ti.setContainerId(ci.getTrackingId());
    ti.setFinancialReportGrpCode(
        isEmpty(ci.getFinancialReportingGroupCode())
            ? COUNTRY_CODE_US
            : ci.getFinancialReportingGroupCode());

    // optional
    ti.setWarehousePackQty(ci.getWhpkQty());
    ti.setVendorPackQty(ci.getVnpkQty());
    ti.setPromoBuyInd(ci.getPromoBuyInd());
    ti.setBaseDivCode(
        isEmpty(ci.getBaseDivisionCode()) ? BASE_DIVISION_CODE : ci.getBaseDivisionCode());

    transactionsItems.add(ti);
    return transactionsItems;
  }

  public static void checkViolations(DeliveryDocument delDoc) {
    final List<String> dcFinViolations = getDcFinViolations(delDoc);
    if (dcFinViolations.size() > 0) {
      LOG.error("DcFin kafka receipt will fail for Violations={}", dcFinViolations);
      throw new ReceivingBadDataException(INVALID_DATA, dcFinViolations.toString());
    }
  }

  public static List<String> getDcFinViolations(DeliveryDocument delDoc) {
    ArrayList<String> violations = new ArrayList<>();

    checkSpecificFormatField(
        delDoc.getPoTypeCode(), VALID_TYPES_PO_ASN_CO_STO_0_9_$, INVALID_DOC_TYPE_MSG, violations);
    checkSpecificFormatField(
        delDoc.getFreightTermCode(), VALID_BILL_CODES, INVALID_BILL_CODE_MSG, violations);
    checkRequiredField(FREIGHT_BILL_QTY, delDoc.getTotalBolFbq(), violations);
    checkRequiredField(BASE_DIVISION_CODE1, delDoc.getBaseDivisionCode(), violations);
    checkSpecificFormatField(
        delDoc.getFinancialReportingGroup(),
        VALID_FINANCIAL_REPORTING_GROUP,
        INVALID_FINANCIAL_REPORT_GRP_MSG,
        violations);

    // Line
    final DeliveryDocumentLine delDocLn = delDoc.getDeliveryDocumentLines().get(0);
    checkSpecificFormatField(
        getDCFinChannelMethod(delDocLn.getPurchaseRefType()),
        VALID_PURCHASE_REF_TYPE,
        INVALID_PURCHASE_REF_TYPE_MSG,
        violations);
    checkSpecificFormatField(delDocLn.getQtyUOM(), VALID_QTY_UOM, INVALID_QTY_UOM_MSG, violations);

    return violations;
  }

  /** primaryQty: Should be divisible by whpk and vnpk ratios */
  public static void checkQuantityDivisible(Integer quantity, Integer vnpkQty, Integer whpkQty) {
    String errMsg = null;
    if (quantity == null) {
      errMsg = format(MISSING_REQUIRED_FIELD, QUANTITY, null);
      if (isNotBlank(errMsg)) {
        LOG.error("Quantity can not be null. vnpk={},whpk={}, errMsg={}", vnpkQty, whpkQty, errMsg);
        throw new ReceivingBadDataException(INVALID_DATA, errMsg);
      }
    }
    if (quantity % vnpkQty != 0) {
      errMsg = format(INVALID_QTY_VNPK_WHPK_MSG, quantity, vnpkQty, VNPK);
    }
    if (quantity % whpkQty != 0) {
      errMsg =
          isBlank(errMsg)
              ? format(INVALID_QTY_VNPK_WHPK_MSG, quantity, whpkQty, WHPK)
              : errMsg + BR + format(INVALID_QTY_VNPK_WHPK_MSG, quantity, whpkQty, WHPK);
    }
    if (isNotBlank(errMsg)) {
      LOG.error(
          "Quantity({}) is NOT divisible. vnpk={},whpk={}, errMsg={}",
          quantity,
          vnpkQty,
          whpkQty,
          errMsg);
      throw new ReceivingBadDataException(INVALID_DATA, errMsg);
    }
  }

  public static void checkSpecificFormatField(
      final Object actualValue, String validValues, String errMsg, ArrayList<String> violations) {
    if (actualValue == null) {
      violations.add(violations.size() > 0 ? BR + format(errMsg, null) : format(errMsg, null));
    } else {
      Matcher matcher = compile(validValues, CASE_INSENSITIVE).matcher(actualValue.toString());
      if (!matcher.find())
        violations.add(
            violations.size() > 0 ? BR + format(errMsg, actualValue) : format(errMsg, actualValue));
    }
  }

  public static void checkRequiredField(
      String fieldName, Object actualObjValue, ArrayList<String> violations) {
    if (actualObjValue == null) {
      violations.add(
          violations.size() > 0
              ? BR + format(MISSING_REQUIRED_FIELD, fieldName, null)
              : format(MISSING_REQUIRED_FIELD, fieldName, null));
    } else if (isBlank(actualObjValue.toString())) {
      violations.add(
          violations.size() > 0
              ? BR + format(MISSING_REQUIRED_FIELD, fieldName, actualObjValue)
              : format(MISSING_REQUIRED_FIELD, fieldName, actualObjValue));
    }
  }
}
