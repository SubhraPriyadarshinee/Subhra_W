package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.ENABLE_INVENTORY_CONTAINER_REMOVAL;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.osdr.v2.OSDRPayload;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCOSDRService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class MFCPostDeliveryCompleteFlowProcessor implements ProcessExecutor {

  private final Logger LOGGER = LoggerFactory.getLogger(MFCPostDeliveryCompleteFlowProcessor.class);

  @Autowired protected MFCOSDRService mfcosdrService;

  @Autowired protected MFCContainerService mfcContainerService;

  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;

  @ManagedConfiguration MFCManagedConfig mfcManagedConfig;

  @Override
  public boolean isAsync() {
    return mfcManagedConfig.isMfcPostDeliveryFlowAsyncEnabled();
  }

  @Timed(
      name = "postDeliveryCompleteFlowProcessingTimed",
      level1 = "uwms-receiving-api",
      level2 = "postDeliveryCompleteFlowProcessor")
  @ExceptionCounted(
      name = "postDeliveryCompleteFlowProcessingExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "postDeliveryCompleteFlowProcessor")
  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    if (StringUtils.isEmpty(receivingEvent.getPayload())) {
      LOGGER.info("Event payload cannot be empty for eventType {}", receivingEvent.getName());
    }
    Long deliveryNumber = Long.parseLong(receivingEvent.getPayload());

    OSDRPayload osdrPayload = mfcosdrService.createOSDRv2Payload(deliveryNumber, null);
    LOGGER.info(
        "OSDR Payload for MFC Delivery={} and payload = {}",
        deliveryNumber,
        ReceivingUtils.stringfyJson(osdrPayload));

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_INVENTORY_CONTAINER_REMOVAL)) {
      mfcContainerService.initiateContainerRemoval(osdrPayload);
    }

    Map<String, Object> headers = new HashMap<>(ReceivingUtils.getHeaders().toSingleValueMap());
    String corrId = (String) headers.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    headers.put(
        ReceivingConstants.MESSAGE_ID_HEADER,
        Objects.nonNull(corrId) ? corrId : UUID.randomUUID().toString());

    mfcosdrService.publishOSDR(osdrPayload, headers);
  }
}
