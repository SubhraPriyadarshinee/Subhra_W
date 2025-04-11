package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.applyDefaultValue;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static java.util.stream.Collectors.groupingBy;

import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.common.OsdrUtils;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.POLineOSDR;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrData;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPoLine;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.osdr.service.OsdrService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.endgame.model.LineStatusInfo;
import com.walmart.move.nim.receiving.endgame.model.PoLineReceipt;
import com.walmart.move.nim.receiving.endgame.model.PoReceipt;
import com.walmart.move.nim.receiving.endgame.model.PoReceiptEventPayload;
import java.time.Instant;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class EndGameOsdrService extends OsdrService {

  @Autowired ReceiptService receiptService;
  @Autowired GDMRestApiClient gdmRestApiClient;

  @Override
  public OsdrSummary getOsdrDetails(
      Long deliveryNumber, List<Receipt> receipts, String uom, String userId) {
    uom = getUomInfo(uom);
    Map<POPOLineKey, POLineOSDR> receiptSummary = new HashMap<>();
    for (Receipt receipt : receipts) {
      POLineOSDR osdr = getpoPolineWithSummary(receiptSummary, receipt);
      OsdrUtils.populateReceivedQty(receipt, osdr);
      if (Objects.nonNull(receipt.getOsdrMaster()) && receipt.getOsdrMaster() == 1) {
        OsdrUtils.populateFbOverQty(receipt, osdr);
        OsdrUtils.populateFbShortQty(receipt, osdr);
        OsdrUtils.getRejectedQty(receipt, osdr);
        OsdrUtils.populateFbConcealedShortageQty(receipt, osdr);
        OsdrUtils.populateFbDamageQty(receipt, osdr);
      }
    }
    adjustReceivedQuantity(receiptSummary);
    return createOsdrSummary(
        getReceivingCountSummary(receiptSummary, deliveryNumber, uom), deliveryNumber, userId);
  }

  private void adjustReceivedQuantity(Map<POPOLineKey, POLineOSDR> receiptSummary) {
    receiptSummary
        .values()
        .forEach(
            osdr -> {
              osdr.setReceivedQty(
                  osdr.getReceivedQty()
                      + osdr.getOverageQty()
                      - osdr.getShortageQty()
                      - osdr.getConcealedShortageQty()
                      - osdr.getDamageQty());
              osdr.setDamageQty(0);
            });
  }

  /**
   * This method is responsible for creating Osdr Po
   *
   * @param receivingCountSummary
   * @return
   */
  @Override
  public OsdrPo createOsdrPo(ReceivingCountSummary receivingCountSummary) {
    OsdrData damage;
    OsdrData reject;
    reject = OsdrUtils.buildOsdrPoDtlsForReject(receivingCountSummary);
    damage = OsdrUtils.buildOsdrPoDtlsForDamage(receivingCountSummary);
    return OsdrPo.builder()
        .purchaseReferenceNumber(receivingCountSummary.getPurchaseReferenceNumber())
        .rcvdQty(receivingCountSummary.getReceiveQty())
        .rcvdQtyUom(receivingCountSummary.getReceiveQtyUOM())
        .overage(null)
        .shortage(null)
        .damage(damage)
        .reject(reject)
        .palletQty(null)
        .build();
  }

  /**
   * This method is responsible for adding OsdrPoLine info to OsdrPo along with osdr details at po
   * level
   *
   * @param receivingCountSummary
   * @param osdrPo
   */
  @Override
  public OsdrPo addToOsdrPo(ReceivingCountSummary receivingCountSummary, OsdrPo osdrPo) {
    osdrPo.addReceiveQty(receivingCountSummary.getReceiveQty());

    if (Objects.nonNull(osdrPo.getOverage())) {
      osdrPo.getOverage().addQuantity(receivingCountSummary.getOverageQty());
    }

    if (Objects.nonNull(osdrPo.getShortage())) {
      osdrPo.getShortage().addQuantity(receivingCountSummary.getShortageQty());
    }

    if (Objects.nonNull(osdrPo.getDamage())) {
      osdrPo.getDamage().addQuantity(receivingCountSummary.getDamageQty());
    }

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
    if (receivingCountSummary.getDamageQty() > 0) {
      osdrPo.getDamage().setCode(receivingCountSummary.getDamageReasonCode());
      osdrPo.getDamage().setClaimType(receivingCountSummary.getDamageClaimType());
    }
    return osdrPo;
  }

  /**
   * This method is used to create OSDR details at PO line level
   *
   * @param receivingCountSummary
   * @return
   */
  @Override
  public OsdrPoLine createOsdrPoLine(ReceivingCountSummary receivingCountSummary) {
    OsdrData damage;
    OsdrData reject;
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

  public PoReceipt generatePoReceipt(String poNumber, List<Receipt> lineReceipt) {
    LOGGER.info("Generating Po Receipt for poNumber : {}", poNumber);
    List<PoLineReceipt> poLineReceipts = new ArrayList<>();

    PurchaseOrder purchaseOrder = gdmRestApiClient.getPurchaseOrder(poNumber);

    Map<Integer, List<Receipt>> poLineReceiptsMap =
        lineReceipt.stream().collect(groupingBy(Receipt::getPurchaseReferenceLineNumber));

    poLineReceiptsMap.forEach(
        (lineNumber, r) -> {
          Optional<PurchaseOrderLine> purchaseOrderLineOptional =
              purchaseOrder
                  .getLines()
                  .stream()
                  .filter(l -> l.getPoLineNumber().equals(lineNumber))
                  .findFirst();
          if (purchaseOrderLineOptional.isPresent()) {
            PurchaseOrderLine purchaseOrderLine = purchaseOrderLineOptional.get();
            PoLineReceipt poLineReceipt =
                PoLineReceipt.builder()
                    .poLineNbr(lineNumber)
                    .itemNbr(purchaseOrderLine.getItemDetails().getNumber())
                    .gtin(purchaseOrderLine.getItemDetails().getConsumableGTIN())
                    .orderedQty(purchaseOrderLine.getOrdered().getQuantity())
                    .qtyUom(purchaseOrderLine.getOrdered().getUom())
                    .lineStatusInfo(
                        getLineStatusInfo(poLineReceiptsMap.getOrDefault(lineNumber, null)))
                    .build();
            poLineReceipts.add(poLineReceipt);
          } else {
            LOGGER.debug(
                "generatePoReceipt :: No lines found in Purchase Order for Po Number : {}",
                poNumber);
          }
        });

    PoReceiptEventPayload receiptEventPayload =
        PoReceiptEventPayload.builder()
            .poNumber(purchaseOrder.getPoNumber())
            .sellerId(purchaseOrder.getSellerId())
            .shipNode(getFacilityNum())
            .shipNodeCountry(getFacilityCountryCode())
            .poLines(poLineReceipts)
            .build();
    return PoReceipt.builder()
        .eventId(UUID.randomUUID().toString())
        .eventSource("RECEIVING")
        .eventType("PO_RECEIPT")
        .eventTime(Instant.now().toString())
        .eventPayload(receiptEventPayload)
        .build();
  }

  private List<LineStatusInfo> getLineStatusInfo(List<Receipt> poLineReceipts) {
    int receivedQty = 0;
    if (CollectionUtils.isNotEmpty(poLineReceipts)) {

      for (Receipt receipt : poLineReceipts) {
        receivedQty = receivedQty + getReceivedQuantity(receipt);
      }
    }
    return Collections.singletonList(
        LineStatusInfo.builder().lineStatus("RECEIVED").lineQty(receivedQty).build());
  }

  private Integer getReceivedQuantity(Receipt receipt) {
    return applyDefaultValue(receipt.getQuantity()).intValue()
        + applyDefaultValue(receipt.getFbOverQty()).intValue()
        - applyDefaultValue(receipt.getFbShortQty()).intValue()
        - applyDefaultValue(receipt.getFbConcealedShortageQty()).intValue()
        - applyDefaultValue(receipt.getFbDamagedQty()).intValue();
  }
}
