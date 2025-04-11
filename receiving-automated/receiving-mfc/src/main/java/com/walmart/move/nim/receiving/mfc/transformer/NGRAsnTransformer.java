package com.walmart.move.nim.receiving.mfc.transformer;

import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.ngr.NGRPack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

public class NGRAsnTransformer implements BiFunction<NGRPack, String, ASNDocument> {

  @Override
  public ASNDocument apply(NGRPack ngrPack, String packType) {
    ASNDocument asnDocument = new ASNDocument();
    Pack pack = new Pack();
    pack.setPackNumber(ngrPack.getPackNumber());
    pack.setPackType(packType);
    List<Item> items = new ArrayList<>();
    ngrPack
        .getItems()
        .forEach(
            ngrPackItem -> {
              Item item = new Item();
              item.setReplenishmentCode(ngrPackItem.getReplenishmentCode());
              item.setGtin(ngrPackItem.getGtin());
              item.setInvoice(
                  InvoiceDetail.builder().invoiceNumber(ngrPack.getInvoiceNumber()).build());
              InventoryDetail inventoryDetail = new InventoryDetail();
              inventoryDetail.setReportedUom(
                  ngrPackItem.getInventoryDetail().getReceivedQuantityUom());
              inventoryDetail.setReportedQuantity(
                  Double.parseDouble(ngrPackItem.getInventoryDetail().getReceivedQuantity()));
              item.setInventoryDetail(inventoryDetail);
              items.add(item);
            });
    pack.setItems(items);
    asnDocument.setPacks(Arrays.asList(pack));
    asnDocument.setDelivery(
        Delivery.builder().deliveryNumber(Long.valueOf(ngrPack.getReceivingDeliveryId())).build());
    return asnDocument;
  }
}
