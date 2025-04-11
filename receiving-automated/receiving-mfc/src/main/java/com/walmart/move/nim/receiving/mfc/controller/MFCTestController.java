package com.walmart.move.nim.receiving.mfc.controller;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.message.HawkeyeAdjustmentListener;
import com.walmart.move.nim.receiving.mfc.message.StoreNGRFinalizationEventListener;
import com.walmart.move.nim.receiving.mfc.processor.MFCInventoryAdjustmentProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Collections;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnExpression("${enable.mfc.app:false}")
@RestController
@RequestMapping("/mfc/test")
public class MFCTestController {

  @Autowired(required = false)
  private HawkeyeAdjustmentListener hawkeyeAdjustmentListener;

  @Autowired(required = false)
  private MFCInventoryAdjustmentProcessor mfcInventoryAdjustmentProcessor;

  @Autowired
  @Qualifier(MFCConstant.MIXED_PALLET_PROCESSOR)
  private EventProcessor storeInboundMixedPalletProcessor;

  @Autowired(required = false)
  private StoreNGRFinalizationEventListener storeNgrFinalizationEventListener;

  @Value("${enable.mfc.mock.endpoint:false}")
  boolean isMFCMockEnabled;

  @PostMapping("/inventory")
  public void doReceiptCreationInventory(@RequestBody String payload) throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();

    InventoryAdjustmentTO inventoryAdjustmentTO = new InventoryAdjustmentTO();
    JsonObject _payload = new JsonParser().parse(payload).getAsJsonObject();
    inventoryAdjustmentTO.setJsonObject(_payload);

    if (isMFCMockEnabled) {
      mfcInventoryAdjustmentProcessor.processEvent(inventoryAdjustmentTO);
    }
  }

  @PostMapping("/hawkeye")
  public void doReceiptCreation(@RequestBody String payload) {
    ReceivingUtils.validateApiAccessibility();

    if (isMFCMockEnabled) {
      TenantContext.setMessageId(UUID.randomUUID().toString());
      hawkeyeAdjustmentListener.listen(sanitize(payload), "DECANTING".getBytes());
    }
  }

  @PostMapping("/rejectMixedPallet")
  public void doRejectMixedPallet(@RequestBody DeliveryUpdateMessage payload)
      throws ReceivingException {
    ReceivingUtils.validateApiAccessibility();
    if (isMFCMockEnabled) {
      storeInboundMixedPalletProcessor.processEvent(payload);
    }
  }

  @PostMapping("/dsdReceiving")
  public void doPerformDSDReceiving(@RequestBody String payload) {
    ReceivingUtils.validateApiAccessibility();
    if (isMFCMockEnabled) {
      storeNgrFinalizationEventListener.listen(payload, Collections.emptyMap());
    }
  }
}
