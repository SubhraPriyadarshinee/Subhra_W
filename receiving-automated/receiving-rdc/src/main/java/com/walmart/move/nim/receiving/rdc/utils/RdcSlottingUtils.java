package com.walmart.move.nim.receiving.rdc.utils;

import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersRequestBody;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.SlottingServiceImpl;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class RdcSlottingUtils {

  @Autowired private SlottingServiceImpl slottingService;

  public String getStockType(String purchaseRefType) {
    String stockType = StringUtils.EMPTY;
    if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(purchaseRefType)) {
      stockType = RdcConstants.SLOTTING_SSTK_RECEIVING_METHOD;
    }
    if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(purchaseRefType)) {
      stockType = RdcConstants.SLOTTING_DA_RECEIVING_METHOD;
    }
    return stockType;
  }

  /**
   * Receive containers in Slotting
   *
   * @param receiveInstructionRequest
   * @param labelTrackingId
   * @param httpHeaders
   * @param receiveContainersRequestBody
   * @return
   */
  public SlottingPalletResponse receiveContainers(
      ReceiveInstructionRequest receiveInstructionRequest,
      String labelTrackingId,
      HttpHeaders httpHeaders,
      ReceiveContainersRequestBody receiveContainersRequestBody) {
    return slottingService.receivePallet(
        receiveInstructionRequest, labelTrackingId, httpHeaders, receiveContainersRequestBody);
  }
}
