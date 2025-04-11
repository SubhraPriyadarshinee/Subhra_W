package com.walmart.move.nim.receiving.rx.builders;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class RxSSCCValidator {

  @Autowired private RxDeliveryServiceImpl rxDeliveryServiceImpl;
  private static final Logger LOGGER = LoggerFactory.getLogger(RxSSCCValidator.class);

  public Optional<List<DeliveryDocumentLine>> validateScannedSSCC(
      Long deliveryNumber, String scannedSscc, HttpHeaders headers) throws ReceivingException {
    LOGGER.info(
        "Entering into validateScannedSSCC to call GDM API with delivery: {} SSCC: {}",
        deliveryNumber,
        scannedSscc);
    List<DeliveryDocument> deliveryDocumentsBySSCC =
        rxDeliveryServiceImpl.findDeliveryDocumentBySSCCWithShipmentLinking(
            deliveryNumber.toString(), scannedSscc, headers);

    if (RxUtils.isMultiSKUPallet(deliveryDocumentsBySSCC)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.MULTI_SKU_PALLET, ReceivingConstants.MULTI_SKU_PALLET);
    }

    String palletSSCC =
        deliveryDocumentsBySSCC.get(0).getDeliveryDocumentLines().get(0).getPalletSSCC();
    LOGGER.info(
        "Received response from GDM with delivery: {} SSCC: {}", deliveryNumber, scannedSscc);

    if (scannedSscc.equals(palletSSCC)) {
      LOGGER.error("Scanned SSCC: {} is pallet sscc : {}", scannedSscc, palletSSCC);
      throw new ReceivingBadDataException(
          ExceptionCodes.SCANNED_SSCC_NOT_VALID,
          String.format(RxConstants.SCANNED_SSCC_NOT_VALID, scannedSscc),
          scannedSscc);
    } else {
      LOGGER.info(
          "sending rx details to client for delivery: {} SSCC: {}", deliveryNumber, scannedSscc);

      return Optional.ofNullable(deliveryDocumentsBySSCC.get(0).getDeliveryDocumentLines());
    }
  }
}
