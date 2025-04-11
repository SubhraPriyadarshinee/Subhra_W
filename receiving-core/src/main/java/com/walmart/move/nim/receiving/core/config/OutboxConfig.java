package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.DefaultValue;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "outboxConfig")
@Getter
public class OutboxConfig {
  @Property(propertyName = "kafka.publisher.policy.inventory")
  private String kafkaPublisherPolicyInventory;

  @Property(propertyName = "kafka.publisher.policy.putaway.hawkeye")
  private String kafkaPublisherPolicyPutawayHawkeye;

  @Property(propertyName = "kafka.publisher.policy.sorter")
  private String kafkaPublisherPolicySorter;

  @Property(propertyName = "kafka.publisher.policy.wft")
  private String kafkaPublisherPolicyWFT;

  @Property(propertyName = "kafka.publisher.policy.ei.dc.receive.event")
  private String kafkaPublisherPolicyEIDCReceiveEvent;

  @Property(propertyName = "kafka.publisher.policy.ei.dc.pick.event")
  private String kafkaPublisherPolicyEIDCPickEvent;

  @Property(propertyName = "kafka.publisher.policy.ei.dc.void.event")
  private String kafkaPublisherPolicyEIDCVoidEvent;

  @Property(propertyName = "kafka.publisher.hawkeye.label.data")
  private String kafkaPublisherHawkeyeLabelData;

  @Property(propertyName = "http.publisher.policy.for.dsdc.receiving")
  private String httpPublisherPolicyForDsdcReceiving;

  @Property(propertyName = "outbox.policy.http.pendingContainers")
  @DefaultValue.String("URN::ATLAS::RECEIVING::HttpEventPublisher::100")
  private String outboxPolicyHttpPendingContainers;

  @Property(propertyName = "outbox.policy.http.pendingContainersV2")
  @DefaultValue.String("URN::ATLAS::RECEIVING::HttpEventPublisher::pendingContainersV2")
  private String outboxPolicyHttpPendingContainersV2;

  @Property(propertyName = "outbox.policy.http.eachesDetail")
  @DefaultValue.String("URN::ATLAS::RECEIVING::HttpEventPublisher::101")
  private String outboxPolicyHttpEachesDetail;

  @Property(propertyName = "outbox.policy.http.eachesDetailV2")
  @DefaultValue.String("URN::ATLAS::RECEIVING::HttpEventPublisher::eachesDetailV2")
  private String outboxPolicyHttpEachesDetailV2;

  @Property(propertyName = "outbox.policy.http.psV3Capture")
  @DefaultValue.String("URN::ATLAS::RECEIVING::HttpEventPublisher::102")
  private String outboxPolicyHttpPsV3Capture;

  @Property(propertyName = "outbox.policy.http.psV2CaptureMany")
  @DefaultValue.String("URN::ATLAS::RECEIVING::HttpEventPublisher::103")
  private String outboxPolicyHttpPsV2CaptureMany;

  @Property(propertyName = "outbox.policy.http.psV3CaptureMany")
  @DefaultValue.String("URN::ATLAS::RECEIVING::HttpEventPublisher::104")
  private String outboxPolicyHttpPsV3CaptureMany;

  @Property(propertyName = "outbox.policy.http.psV1Serialize")
  @DefaultValue.String("URN::ATLAS::RECEIVING::HttpEventPublisher::psV1Serialize")
  private String outboxPolicyHttpPsV1Serialize;

  @Property(propertyName = "outbox.policy.http.psV1SerializeClubbed")
  @DefaultValue.String("URN::ATLAS::RECEIVING::HttpEventPublisher::psV1SerializeClubbed")
  private String outboxPolicyHttpPsV1SerializeClubbed;

  @Property(propertyName = "outbox.policy.kafka.inventory")
  @DefaultValue.String("URN::ATLAS::RECEIVING::KafkaEventPublisher::200")
  private String outboxPolicyKafkaInventory;

  @Property(propertyName = "outbox.policy.http.createMoves")
  @DefaultValue.String("URN::ATLAS::RECEIVING::HttpEventPublisher::105")
  private String outboxPolicyHttpCreateMoves;
}
