package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.annotation.Resource;

public class ACLNotificationProcessor implements EventProcessor {
  @Resource(name = ReceivingConstants.ACC_NOTIFICATION_SERVICE)
  private ACLNotificationService aclNotificationService;

  @Override
  public void processEvent(MessageData messageData) {
    aclNotificationService.sendNotificationToSumo(
        (ACLNotification) messageData,
        TenantContext.getFacilityNum(),
        TenantContext.getFacilityCountryCode());
  }
}
