package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "maasTopics")
@Getter
public class MaasTopics {
  @Property(propertyName = "queue.gdm.delivery.update")
  private String queueGdmDeliveryUpdate;

  @Property(propertyName = "queue.inventory.adjustment")
  private String queueInventoryAdjustment;

  @Property(propertyName = "queue.acl.notification")
  private String queueAclNotification;

  @Property(propertyName = "queue.acl.verification")
  private String queueAclVerification;

  @Property(propertyName = "pub.move.topic")
  private String pubMoveTopic;

  @Property(propertyName = "pub.receipts.topic")
  private String pubReceiptsTopic;

  @Property(propertyName = "pub.container.update.topic")
  private String pubContainerUpdateTopic;

  @Property(propertyName = "pub.delivery.status.topic")
  private String pubDeliveryStatusTopic;

  @Property(propertyName = "jms.sorter.divert.topic")
  private String sorterDivertTopic;

  @Property(propertyName = "pub.exception.container.topic")
  private String pubExceptionContainerTopic;
}
