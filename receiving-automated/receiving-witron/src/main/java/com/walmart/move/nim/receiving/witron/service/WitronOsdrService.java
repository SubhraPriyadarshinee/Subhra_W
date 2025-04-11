package com.walmart.move.nim.receiving.witron.service;

import com.walmart.move.nim.receiving.core.common.OsdrUtils;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrData;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.osdr.service.OsdrService;

public class WitronOsdrService extends OsdrService {

  /**
   * This method is responsible for creating Osdr Po
   *
   * @param receivingCountSummary
   * @return
   */
  @Override
  public OsdrPo createOsdrPo(ReceivingCountSummary receivingCountSummary) {
    OsdrData reject = null;
    OsdrData damage;
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
}
