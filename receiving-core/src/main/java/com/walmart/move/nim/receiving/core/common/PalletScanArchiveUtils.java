package com.walmart.move.nim.receiving.core.common;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.message.common.FireflyEvent;
import com.walmart.move.nim.receiving.core.model.decant.DecantMessagePublishRequest;
import com.walmart.move.nim.receiving.core.model.decant.PalletScanArchiveMessage;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * common utils to be used for PalletScan Archive.
 *
 * @author a0s01qi
 */
public class PalletScanArchiveUtils {

  private PalletScanArchiveUtils() {}

  public static PalletScanArchiveMessage createPalletScanArchiveMessage(
      ContainerScanRequest containerScanRequest, FireflyEvent fireflyEvent, String type) {

    String trackingId = null;
    String scannedTime = null;
    String storeNumber = null;
    boolean isReceivedThroughAutomatedSignal =
        StringUtils.equals(
            ReceivingConstants.USER_ID_AUTO_FINALIZED, ReceivingUtils.retrieveUserId());

    if (containerScanRequest != null) {
      trackingId = containerScanRequest.getTrackingId();
      scannedTime = Instant.now().toString();
      storeNumber = String.valueOf(TenantContext.getFacilityNum());
    } else if (fireflyEvent != null) {
      trackingId = fireflyEvent.getAssetId();
      scannedTime = fireflyEvent.getEventTime();
      storeNumber = fireflyEvent.getBusinessUnitNumber().toString();
    }

    return PalletScanArchiveMessage.builder()
        .scannedTrackingId(trackingId)
        .scannedAt(ReceivingUtils.convertToTimestampWithMillisecond(scannedTime))
        .scannedBy(ReceivingUtils.retrieveUserId())
        .type(type)
        .storeNumber(storeNumber)
        .build();
  }

  public static List<DecantMessagePublishRequest> createDecantMessagePublishRequests(
      PalletScanArchiveMessage message, Gson gson) {
    Map<String, String> additionalHeaders = new HashMap<>();
    additionalHeaders.put("requestOriginator", "RECEIVING_APP");
    additionalHeaders.put("eventType", "PALLET_SCAN");

    DecantMessagePublishRequest request =
        DecantMessagePublishRequest.builder()
            .scenario(ReceivingConstants.STORE_APP_METRICS)
            .additionalHeaders(additionalHeaders)
            .message(gson.toJson(message))
            .build();

    return Collections.singletonList(request);
  }
}
