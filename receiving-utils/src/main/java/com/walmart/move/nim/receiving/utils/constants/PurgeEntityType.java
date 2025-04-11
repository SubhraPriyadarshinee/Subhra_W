package com.walmart.move.nim.receiving.utils.constants;

/** @author r0s01us Entity enums : name should be table name */
public enum PurgeEntityType {
  // receiving-core entities
  INSTRUCTION(ReceivingConstants.INSTRUCTION_PERSISTER_SERVICE),
  CONTAINER(ReceivingConstants.CONTAINER_PERSISTER_SERVICE),
  RECEIPT(ReceivingConstants.RECEIPT_SERVICE),
  PRINTJOB(ReceivingConstants.PRINTJOB_SERVICE),
  PROBLEM(ReceivingConstants.FIT_SERVICE),
  DELIVERY_METADATA(ReceivingConstants.ACC_DELIVERY_METADATA_SERVICE),
  DOCK_TAG(ReceivingConstants.DEFAULT_DOCK_TAG_SERVICE),
  ITEM_CATALOG_UPDATE_LOG(ReceivingConstants.ITEM_CATALOG_SERVICE),
  JMS_EVENT_RETRY(ReceivingConstants.RETRY_SERVICE),
  // receiving-acc entities
  DELIVERY_EVENT(ReceivingConstants.DELIVERY_EVENT_PERSISTER_SERVICE),
  LABEL_DATA(ReceivingConstants.LABEL_INSTRUCTION_DATA_SERVICE),
  NOTIFICATION_LOG(ReceivingConstants.ACC_NOTIFICATION_SERVICE),
  // receiving-rc entities
  CONTAINER_RLOG(ReceivingConstants.RC_CONTAINER_SERVICE),
  PACKAGE_RLOG(ReceivingConstants.RC_PACKAGE_TRACKER_SERVICE),
  ITEM_TRACKER(ReceivingConstants.ITEM_TRACKER_SERVICE);

  String beanName;

  PurgeEntityType(String beanName) {
    this.beanName = beanName;
  }

  public String getBeanName() {
    return beanName;
  }

  public static boolean contains(String entity) {
    for (PurgeEntityType purgeEntityType : values())
      if (purgeEntityType.name().equals(entity)) return true;
    return false;
  }
}
