package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isKotlinEnabled;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.yms.v2.DefaultYms2UnloadEventProcessor;
import com.walmart.move.nim.receiving.core.model.yms.v2.Yms2UnloadEventProcessor;
import com.walmart.move.nim.receiving.core.service.DefaultCompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.scm.client.shared.logging.Logger;
import com.walmart.platform.scm.client.shared.logging.LoggerFactory;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component(value = ACCConstants.ACC_COMPLETE_DELIVERY_PROCESSOR)
public class ACCCompleteDeliveryProcessor extends DefaultCompleteDeliveryProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(ACCCompleteDeliveryProcessor.class);

  @Override
  public DeliveryInfo completeDelivery(
      Long deliveryNumber, boolean performUnload, HttpHeaders headers) throws ReceivingException {
    validateMasterReceiptsForPoConfirmation(deliveryNumber);
    validateForOpenInstructions(deliveryNumber);
    final boolean isKotlinEnabled = isKotlinEnabled(headers, tenantSpecificConfigReader);

    List<ReceiptSummaryResponse> receiptSummaryResponses =
        receiptService.getReceivedQtySummaryByPOForDelivery(
            deliveryNumber, ReceivingConstants.Uom.EACHES);

    Integer countOfDockTags =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .countOfOpenDockTags(deliveryNumber);

    Map<String, Object> deliveryCompleteHeaders =
        constructDeliveryCompleteHeaders(deliveryNumber, headers);

    DeliveryInfo deliveryInfo =
        deliveryStatusPublisher.publishDeliveryStatus(
            deliveryNumber,
            DeliveryStatus.COMPLETE.name(),
            receiptSummaryResponses,
            countOfDockTags,
            deliveryCompleteHeaders);
    if (isKotlinEnabled) {
      List<ReceiptSummaryResponse> receiptSummaryResponsesInVnpk =
          receiptService.getReceivedQtySummaryByPOForDelivery(
              deliveryNumber, ReceivingConstants.Uom.VNPK);
      deliveryInfo.setReceipts(receiptSummaryResponsesInVnpk);
    }
    // yms update complete hooks
    updateUnloadingProgressIfApplicable(deliveryNumber);
    return deliveryInfo;
  }

  void updateUnloadingProgressIfApplicable(Long deliveryNumber) throws ReceivingException {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.ENABLE_PUBLISH_UNLOAD_PROGRESS_AT_DELIVERY_COMPLETE)) {
      Yms2UnloadEventProcessor ymsUnloadProcessor =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DEFAULT_YMS2_UNLOAD_EVENT_PROCESSOR,
              DefaultYms2UnloadEventProcessor.class);
      ymsUnloadProcessor.processYMSUnloadingEvent(deliveryNumber);
    } else {
      LOGGER.info("Sending Unloading message to yms2 during deliveryComplete is not enabled");
    }
  }
}
