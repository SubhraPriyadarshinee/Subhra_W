package com.walmart.move.nim.receiving.core.osdr.service;

import com.walmart.move.nim.receiving.core.common.OsdrUtils;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrData;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.CC_OSDR_SERIVCE)
public class CcOsdrService extends OsdrService {

  /**
   * This method is responsible for creating Osdr Po
   *
   * @param receivingCountSummary
   * @return
   */
  @Override
  public OsdrPo createOsdrPo(ReceivingCountSummary receivingCountSummary) {
    OsdrData reject = null;
    Integer palletQty = null;
    reject = OsdrUtils.buildOsdrPoDtlsForReject(receivingCountSummary);
    palletQty = receivingCountSummary.getPalletQty();
    return OsdrPo.builder()
        .purchaseReferenceNumber(receivingCountSummary.getPurchaseReferenceNumber())
        .rcvdQty(receivingCountSummary.getReceiveQty())
        .rcvdQtyUom(receivingCountSummary.getReceiveQtyUOM())
        .overage(null)
        .shortage(null)
        .damage(null)
        .reject(reject)
        .palletQty(palletQty)
        .build();
  }
}
