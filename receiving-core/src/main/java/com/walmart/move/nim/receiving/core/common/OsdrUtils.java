package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.OSDR_EVENT_TYPE_VALUE;

import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.POLineOSDR;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrData;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.springframework.util.StringUtils;

public class OsdrUtils {

  private OsdrUtils() throws UnsupportedOperationException {}

  public static POLineOSDR createOsdr(Receipt receipt) {
    POLineOSDR osdr;
    osdr = new POLineOSDR();
    osdr.setVnpkQty(receipt.getVnpkQty());
    osdr.setWhpkQty(receipt.getWhpkQty());
    osdr.setDamageUOM(ReceivingConstants.Uom.EACHES);
    osdr.setShortageUOM(ReceivingConstants.Uom.EACHES);
    osdr.setOverageUOM(ReceivingConstants.Uom.EACHES);
    osdr.setRejectedUOM(ReceivingConstants.Uom.EACHES);
    osdr.setProblemUOM(ReceivingConstants.Uom.EACHES);
    osdr.setReceivedUOM(ReceivingConstants.Uom.EACHES);
    osdr.setConcealedShortageUOM(ReceivingConstants.Uom.EACHES);
    return osdr;
  }

  private static String getUom(String uom) {
    return StringUtils.isEmpty(uom) ? ReceivingConstants.Uom.EACHES : uom;
  }

  private static String getCode(OSDRCode osdrCode) {
    return Objects.isNull(osdrCode) ? null : osdrCode.getCode();
  }

  /**
   * This method is used to rejected quantity for po/pol
   *
   * @param receipt
   * @param osdr
   */
  public static POLineOSDR getRejectedQty(Receipt receipt, POLineOSDR osdr) {
    if (Objects.nonNull(receipt.getFbRejectedQty())) {

      Integer rejectEachQty =
          ReceivingUtils.conversionToEaches(
              receipt.getFbRejectedQty(),
              getUom(receipt.getFbRejectedQtyUOM()),
              receipt.getVnpkQty(),
              receipt.getWhpkQty());

      osdr.addRejectedQty(rejectEachQty);

      if (Objects.nonNull(receipt.getFbRejectedReasonCode()))
        osdr.setRejectedReasonCode(getCode(receipt.getFbRejectedReasonCode()));

      osdr.setRejectedComment(receipt.getFbRejectionComment());
    }
    return osdr;
  }

  /**
   * This method is used to get overage quantity for po/pol
   *
   * @param receipt
   * @param osdr
   */
  public static POLineOSDR populateFbOverQty(Receipt receipt, POLineOSDR osdr) {
    if (Objects.nonNull(receipt.getFbOverQty())) {

      Integer overEachQty =
          ReceivingUtils.conversionToEaches(
              receipt.getFbOverQty(),
              getUom(receipt.getFbOverQtyUOM()),
              receipt.getVnpkQty(),
              receipt.getWhpkQty());
      osdr.addOverageQty(overEachQty);
      osdr.setOverageReasonCode(getCode(receipt.getFbOverReasonCode()));
    }
    return osdr;
  }

  /**
   * This method is used to get shortage quantity for po/pol
   *
   * @param receipt
   * @param osdr
   */
  public static POLineOSDR populateFbShortQty(Receipt receipt, POLineOSDR osdr) {
    if (Objects.nonNull(receipt.getFbShortQty())) {

      Integer shortEachQty =
          ReceivingUtils.conversionToEaches(
              receipt.getFbShortQty(),
              getUom(receipt.getFbShortQtyUOM()),
              receipt.getVnpkQty(),
              receipt.getWhpkQty());
      osdr.addShortageQty(shortEachQty);
      osdr.setShortageReasonCode(getCode(receipt.getFbShortReasonCode()));
    }
    return osdr;
  }

  /**
   * This method is used to get damage quantity for po/pol
   *
   * @param receipt
   * @param osdr
   */
  public static POLineOSDR populateFbDamageQty(Receipt receipt, POLineOSDR osdr) {
    if (Objects.nonNull(receipt.getFbDamagedQty())) {

      Integer damageEachQty =
          ReceivingUtils.conversionToEaches(
              receipt.getFbDamagedQty(),
              getUom(receipt.getFbDamagedQtyUOM()),
              receipt.getVnpkQty(),
              receipt.getWhpkQty());
      osdr.addDamageQty(damageEachQty);

      osdr.setDamageReasonCode(getCode(receipt.getFbDamagedReasonCode()));
      osdr.setDamageClaimType(receipt.getFbDamagedClaimType());
    }
    return osdr;
  }
  /**
   * This method is used to get concealedShortageQty
   *
   * @param receipt
   * @param osdr
   */
  public static POLineOSDR populateFbConcealedShortageQty(Receipt receipt, POLineOSDR osdr) {
    if (Objects.nonNull(receipt.getFbConcealedShortageQty())) {
      osdr.addConcealedShortageQty(receipt.getFbConcealedShortageQty());

      if (receipt.getFbConcealedShortageQty() < 0) {
        osdr.setOverageReasonCode(getCode(receipt.getFbConcealedShortageReasonCode()));
      } else {
        osdr.setShortageReasonCode(getCode(receipt.getFbConcealedShortageReasonCode()));
      }
    }
    return osdr;
  }

  /**
   * This method is used to get total received quantity of po/pol
   *
   * @param receipt
   * @param osdr
   * @return
   */
  public static POLineOSDR populateReceivedQty(Receipt receipt, POLineOSDR osdr) {
    if (Objects.nonNull(receipt.getQuantity())) {
      Integer receivedEachQty =
          ReceivingUtils.conversionToEaches(
              receipt.getQuantity(),
              getUom(receipt.getQuantityUom()),
              receipt.getVnpkQty(),
              receipt.getWhpkQty());
      osdr.addReceivedQty(receivedEachQty);
      if (Objects.nonNull(receipt.getPalletQty())) {
        osdr.setPalletQty(Objects.nonNull(osdr.getPalletQty()) ? osdr.getPalletQty() : 0);
        osdr.addPalletQty(receipt.getPalletQty());
      }
    }
    return osdr;
  }

  /**
   * This method is responsible for converting the aggregated OSDR details to the provided unit of
   * measurements and performing the rounding off operation.
   *
   * @param receiptSummary
   * @param uom
   * @return @{@link List<ReceivingCountSummary>}
   */
  public static List<ReceivingCountSummary> getReceivingCountSummary(
      Map<POPOLineKey, POLineOSDR> receiptSummary, String uom) {
    List<ReceivingCountSummary> receivingCountSummaries = new ArrayList<>();
    receiptSummary.forEach(
        (poPoLineKey, osdr) -> {
          Integer receivedQty =
              ReceivingUtils.calculateUOMSpecificQuantity(
                  osdr.getReceivedQty(), uom, osdr.getVnpkQty(), osdr.getWhpkQty());
          Integer damageQty =
              ReceivingUtils.calculateUOMSpecificQuantity(
                  osdr.getDamageQty(), uom, osdr.getVnpkQty(), osdr.getWhpkQty());
          Integer shortQty =
              ReceivingUtils.calculateUOMSpecificQuantity(
                  osdr.getShortageQty(), uom, osdr.getVnpkQty(), osdr.getWhpkQty());
          Integer overQty =
              ReceivingUtils.calculateUOMSpecificQuantity(
                  osdr.getOverageQty(), uom, osdr.getVnpkQty(), osdr.getWhpkQty());
          Integer rejectQty =
              ReceivingUtils.calculateUOMSpecificQuantity(
                  osdr.getRejectedQty(), uom, osdr.getVnpkQty(), osdr.getWhpkQty());
          Integer concealedShortageQty =
              ReceivingUtils.calculateUOMSpecificQuantity(
                  osdr.getConcealedShortageQty(), uom, osdr.getVnpkQty(), osdr.getWhpkQty());
          ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
          receivingCountSummary.setDamageQty(damageQty);
          receivingCountSummary.setDamageQtyUOM(uom);
          receivingCountSummary.setDamageReasonCode(osdr.getDamageReasonCode());
          receivingCountSummary.setDamageClaimType(osdr.getDamageClaimType());
          receivingCountSummary.addShortageQty(shortQty);
          receivingCountSummary.setShortageQtyUOM(uom);
          receivingCountSummary.addOverageQty(overQty);
          receivingCountSummary.setOverageQtyUOM(uom);
          receivingCountSummary.setRejectedQty(rejectQty);
          receivingCountSummary.setRejectedQtyUOM(uom);
          receivingCountSummary.setRejectedReasonCode(osdr.getRejectedReasonCode());
          receivingCountSummary.setRejectedComment(osdr.getRejectedComment());
          receivingCountSummary.setReceiveQty(receivedQty);
          receivingCountSummary.setReceiveQtyUOM(uom);
          receivingCountSummary.setPalletQty(osdr.getPalletQty());
          /*
           concealedShortageQty < 0 means overage
           concealedShortageQty > 0 means shortage
          */
          if (concealedShortageQty < 0) {
            receivingCountSummary.addOverageQty(-1 * concealedShortageQty);
          } else {
            receivingCountSummary.addShortageQty(concealedShortageQty);
          }

          /*
           Setting overage reason if there is a overage
          */
          if (receivingCountSummary.getOverageQty() > 0) {
            receivingCountSummary.setOverageReasonCode(osdr.getOverageReasonCode());
          }
          /*
           Setting shortage reason if there is a shortage
          */
          if (receivingCountSummary.getShortageQty() > 0) {
            receivingCountSummary.setShortageReasonCode(osdr.getShortageReasonCode());
          }

          receivingCountSummary.setPurchaseReferenceNumber(
              poPoLineKey.getPurchaseReferenceNumber());
          receivingCountSummary.setPurchaseReferenceLineNumber(
              poPoLineKey.getPurchaseReferenceLineNumber());

          receivingCountSummaries.add(receivingCountSummary);
        });
    return receivingCountSummaries;
  }

  /**
   * This method is used to build osdr details for overage and shortage at po/pol level
   *
   * @param qty
   * @param uom
   * @param reasonCode
   * @return
   */
  public static OsdrData buildOsdrPoDtlsForOverageAndShortage(
      Integer qty, String uom, String reasonCode) {
    return OsdrData.builder().quantity(qty).uom(uom).code(reasonCode).build();
  }
  /**
   * This method is used to build osdr details for damage at Po/pol level
   *
   * @param receivingCountSummary
   * @return
   */
  public static OsdrData buildOsdrPoDtlsForDamage(ReceivingCountSummary receivingCountSummary) {
    return OsdrData.builder()
        .quantity(receivingCountSummary.getDamageQty())
        .uom(receivingCountSummary.getDamageQtyUOM())
        .code(receivingCountSummary.getDamageReasonCode())
        .claimType(receivingCountSummary.getDamageClaimType())
        .build();
  }

  /**
   * This method is used to build osdr details for reject at Po/pol level
   *
   * @param receivingCountSummary
   * @return
   */
  public static OsdrData buildOsdrPoDtlsForReject(ReceivingCountSummary receivingCountSummary) {
    return OsdrData.builder()
        .quantity(receivingCountSummary.getRejectedQty())
        .uom(receivingCountSummary.getRejectedQtyUOM())
        .code(receivingCountSummary.getRejectedReasonCode())
        .comment(receivingCountSummary.getRejectedComment())
        .build();
  }

  public static OsdrSummary newOsdrSummary(Long deliveryNumber, String userId) {
    OsdrSummary osdrSummary = new OsdrSummary();
    osdrSummary.setEventType(OSDR_EVENT_TYPE_VALUE);
    osdrSummary.setDeliveryNumber(deliveryNumber);
    osdrSummary.setUserId(userId);
    osdrSummary.setTs(new Date());
    return osdrSummary;
  }
}
