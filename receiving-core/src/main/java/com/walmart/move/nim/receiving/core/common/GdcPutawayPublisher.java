package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_MANUAL_GDC_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REQUEST_ORIGINATOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SOURCE_APP_NAME_WITRON;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaWitronPutawayMessagePublisher;
import com.walmart.move.nim.receiving.core.service.PutawayService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class GdcPutawayPublisher {
  private static final Logger LOG = LoggerFactory.getLogger(GdcPutawayPublisher.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private KafkaWitronPutawayMessagePublisher kafkaWitronPutawayMessagePublisher;

  public void publishMessage(Container container, String action, HttpHeaders httpHeaders)
      throws ReceivingException {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false)
        || SOURCE_APP_NAME_WITRON.equalsIgnoreCase(httpHeaders.getFirst(REQUEST_ORIGINATOR))) {
      LOG.info("No Publish to hawkeye trackingId: {}", container.getTrackingId());
      return;
    }
    final String requestOriginator = httpHeaders.getFirst(REQUEST_ORIGINATOR);
    if (SOURCE_APP_NAME_WITRON.equalsIgnoreCase(requestOriginator)) {
      LOG.info("stop putaway/rtu message to witron as requestOriginator is witron");
      return;
    }

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.WITRON_HAWKEYE_PUBLISH_PUTAWAY_ENABLED,
        Boolean.FALSE)) {
      // lancaster
      LOG.info(
          "Publish putaway message to Hawkeye with action: {} trackingId: {}",
          action,
          container.getTrackingId());
      kafkaWitronPutawayMessagePublisher.publish(
          GdcPutawayUtils.prepareWitronPutawayMessage(container, action),
          GdcPutawayUtils.prepareWitronPutawayHeaders(httpHeaders));
    } else {
      LOG.info(
          "Publish putaway message to Witron with action: {} trackingId: {}",
          action,
          container.getTrackingId());
      // witron
      PutawayService putawayService =
          tenantSpecificConfigReader.getPutawayServiceByFacility(
              TenantContext.getFacilityNum().toString());
      putawayService.publishPutaway(container, action, httpHeaders);
    }
  }
}
