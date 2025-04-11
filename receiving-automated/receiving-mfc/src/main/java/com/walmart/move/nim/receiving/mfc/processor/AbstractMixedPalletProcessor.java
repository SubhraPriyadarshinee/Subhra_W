package com.walmart.move.nim.receiving.mfc.processor;

import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;

public abstract class AbstractMixedPalletProcessor extends AbstractMFCDeliveryProcessor {
  public abstract void handleMixedPalletOperation(DeliveryUpdateMessage deliveryUpdateMessage);

  public abstract void handleMixedPalletCreation(DeliveryUpdateMessage deliveryUpdateMessage);
}
