package com.walmart.move.nim.receiving.core.builder;

import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class POHashKeyBuilder {

  @Autowired private ReceiptService receiptService;

  public String build(Long deliveryNumber, String poRefNumber) {

    List<Receipt> receipts =
        receiptService.findByDeliveryNumberAndPurchaseReferenceNumber(deliveryNumber, poRefNumber);
    String payload = receipts.stream().map(this::buildReceiptPayload).collect(Collectors.joining());
    return DigestUtils.md5Hex(payload).toUpperCase();
  }

  private String buildReceiptPayload(Receipt receipt) {

    StringBuilder linePayloadBuilder = new StringBuilder();
    linePayloadBuilder
        .append("deliveryNumber=")
        .append(receipt.getDeliveryNumber())
        .append(",poRefNumber=")
        .append(receipt.getPurchaseReferenceNumber())
        .append(",poRefLineNumber=")
        .append(receipt.getPurchaseReferenceLineNumber())
        .append(",quantity=")
        .append(receipt.getQuantity())
        .append(",quantityUom=")
        .append(receipt.getQuantityUom())
        .append(",fbOverQty=")
        .append(receipt.getFbOverQty())
        .append(",fbShortQty=")
        .append(receipt.getFbShortQty())
        .append(",fbDamagedQty=")
        .append(receipt.getFbDamagedQty())
        .append(",fbRejectedQty=")
        .append(receipt.getFbRejectedQty())
        .append(",fbProblemQty=")
        .append(receipt.getFbProblemQty());

    return linePayloadBuilder.toString();
  }
}
