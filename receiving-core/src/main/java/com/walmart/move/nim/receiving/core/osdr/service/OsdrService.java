package com.walmart.move.nim.receiving.core.osdr.service;

import com.walmart.move.nim.receiving.core.common.OsdrUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.POLineOSDR;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrData;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPoLine;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.model.osdr.v2.OSDRPayload;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

/**
 * This is the interface
 *
 * @author pcr000m
 */
public abstract class OsdrService {

  public static final Logger LOGGER = LoggerFactory.getLogger(OsdrService.class);

  /**
   * This method will create osdrdetails for po pol.
   *
   * @param receiptSummary
   * @param receipt
   * @return
   */
  protected POLineOSDR getpoPolineWithSummary(
      Map<POPOLineKey, POLineOSDR> receiptSummary, Receipt receipt) {
    POPOLineKey poPoLineKey =
        new POPOLineKey(
            receipt.getPurchaseReferenceNumber(), receipt.getPurchaseReferenceLineNumber());
    POLineOSDR osdr;
    if (receiptSummary.containsKey(poPoLineKey)) {
      osdr = receiptSummary.get(poPoLineKey);
    } else {
      osdr = OsdrUtils.createOsdr(receipt);
      receiptSummary.put(poPoLineKey, osdr);
    }
    return osdr;
  }

  /**
   * This method is responsible for creating Osdr summary
   *
   * @param receivingCountSummaries
   * @param deliveryNumber
   * @param userId
   * @return
   */
  protected OsdrSummary createOsdrSummary(
      List<ReceivingCountSummary> receivingCountSummaries, Long deliveryNumber, String userId) {
    Map<String, OsdrPo> osdrPoMap = new HashMap<>();
    for (ReceivingCountSummary receivingCountSummary : receivingCountSummaries) {
      OsdrPo osdrPo;
      if (osdrPoMap.containsKey(receivingCountSummary.getPurchaseReferenceNumber())) {
        osdrPo = osdrPoMap.get(receivingCountSummary.getPurchaseReferenceNumber());
        addToOsdrPo(receivingCountSummary, osdrPo);
      } else {
        osdrPo = createOsdrPo(receivingCountSummary);

        if (Objects.nonNull(receivingCountSummary.getPurchaseReferenceLineNumber())) {
          List<OsdrPoLine> osdrPoLines = new ArrayList<>();
          osdrPoLines.add(createOsdrPoLine(receivingCountSummary));

          osdrPo.setLines(osdrPoLines);
        }

        osdrPoMap.put(receivingCountSummary.getPurchaseReferenceNumber(), osdrPo);
      }
    }
    List<OsdrPo> osdrPos = new ArrayList<>();
    osdrPoMap.forEach((s, osdrPo) -> osdrPos.add(osdrPo));

    OsdrSummary osdrSummary = OsdrUtils.newOsdrSummary(deliveryNumber, userId);
    osdrSummary.setSummary(osdrPos);

    return osdrSummary;
  }

  protected abstract OsdrPo createOsdrPo(ReceivingCountSummary receivingCountSummary);

  public OSDRPayload createOSDRv2Payload(Long deliveryNumber, String include) {
    throw new ReceivingDataNotFoundException("NOT_SUPPORTED", " No implementation found");
  }

  /**
   * This method is used check uom information and return default one in case of no information.
   *
   * @param uom
   * @return
   */
  protected String getUomInfo(String uom) {
    if (!ReceivingUtils.isValidUnitOfMeasurementForQuantity(uom)) {
      LOGGER.info(
          "Not a valid unit of measurement: {} provided for OSDR calculation so using fallback uom as vendor pack.",
          uom);
      uom = ReceivingConstants.Uom.VNPK;
    }
    return uom;
  }

  /**
   * This method is used to return receiving Count Summary at po/pol level
   *
   * @param receiptSummary
   * @param uom
   * @param deliveryNumber
   * @return
   */
  protected List<ReceivingCountSummary> getReceivingCountSummary(
      Map<POPOLineKey, POLineOSDR> receiptSummary, Long deliveryNumber, String uom) {

    List<ReceivingCountSummary> receivingCountSummaries =
        OsdrUtils.getReceivingCountSummary(receiptSummary, uom);

    if (CollectionUtils.isEmpty(receivingCountSummaries)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.RECEIPTS_NOT_FOUND,
          String.format(
              ExceptionDescriptionConstants.OSDR_RECEIPTS_NOT_FOUND_ERROR_MSG, deliveryNumber));
    }
    return receivingCountSummaries;
  }

  /**
   * This method is used to get osdr details
   *
   * @param deliveryNumber
   * @param receipts
   * @param uom
   * @param userId
   * @return
   */
  public OsdrSummary getOsdrDetails(
      Long deliveryNumber, List<Receipt> receipts, String uom, String userId) {
    uom = getUomInfo(uom);
    Map<POPOLineKey, POLineOSDR> receiptSummary = new HashMap<>();
    for (Receipt receipt : receipts) {
      POLineOSDR osdr = getpoPolineWithSummary(receiptSummary, receipt);
      OsdrUtils.populateReceivedQty(receipt, osdr);
      OsdrUtils.getRejectedQty(receipt, osdr);
      OsdrUtils.populateFbDamageQty(receipt, osdr);
    }
    return createOsdrSummary(
        getReceivingCountSummary(receiptSummary, deliveryNumber, uom), deliveryNumber, userId);
  }

  protected OsdrPoLine createOsdrPoLine(ReceivingCountSummary receivingCountSummary) {
    OsdrData reject;
    OsdrData damage;
    reject = OsdrUtils.buildOsdrPoDtlsForReject(receivingCountSummary);
    damage = OsdrUtils.buildOsdrPoDtlsForDamage(receivingCountSummary);
    return OsdrPoLine.builder()
        .lineNumber(receivingCountSummary.getPurchaseReferenceLineNumber().longValue())
        .rcvdQty(receivingCountSummary.getReceiveQty())
        .rcvdQtyUom(receivingCountSummary.getReceiveQtyUOM())
        .overage(null)
        .shortage(null)
        .damage(damage)
        .reject(reject)
        .build();
  }

  /**
   * This method is responsible for adding OsdrPoLine info to OsdrPo along with osdr details at po
   * level
   *
   * @param receivingCountSummary
   * @param osdrPo
   * @return
   */
  protected OsdrPo addToOsdrPo(ReceivingCountSummary receivingCountSummary, OsdrPo osdrPo) {
    osdrPo.addReceiveQty(receivingCountSummary.getReceiveQty());

    if (Objects.nonNull(osdrPo.getReject())) {
      osdrPo.getReject().addQuantity(receivingCountSummary.getRejectedQty());
    }

    if (Objects.nonNull(osdrPo.getLines())) {
      osdrPo.getLines().add(createOsdrPoLine(receivingCountSummary));
    }

    if (receivingCountSummary.getRejectedQty() > 0) {
      osdrPo.getReject().setCode(receivingCountSummary.getRejectedReasonCode());
      osdrPo.getReject().setComment(receivingCountSummary.getRejectedComment());
    }

    // Damages
    if (Objects.nonNull(osdrPo.getDamage())) {
      osdrPo.getDamage().addQuantity(receivingCountSummary.getDamageQty());
    }

    if (receivingCountSummary.getDamageQty() > 0 && Objects.nonNull(osdrPo.getDamage())) {
      osdrPo.getDamage().setCode(receivingCountSummary.getDamageReasonCode());
      osdrPo.getDamage().setClaimType(receivingCountSummary.getDamageClaimType());
    }

    return osdrPo;
  }
}
