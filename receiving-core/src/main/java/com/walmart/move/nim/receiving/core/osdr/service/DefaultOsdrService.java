package com.walmart.move.nim.receiving.core.osdr.service;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPoLine;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.DEFAULT_OSDR_SERIVCE)
public class DefaultOsdrService extends OsdrService {
  @Override
  public OsdrSummary getOsdrDetails(
      Long deliveryNumber, List<Receipt> receipts, String uom, String userId) {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public OsdrPo addToOsdrPo(ReceivingCountSummary receivingCountSummary, OsdrPo osdrPo) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public OsdrPo createOsdrPo(ReceivingCountSummary receivingCountSummary) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public OsdrPoLine createOsdrPoLine(ReceivingCountSummary receivingCountSummary) {
    // TODO Auto-generated method stub
    return null;
  }
}
