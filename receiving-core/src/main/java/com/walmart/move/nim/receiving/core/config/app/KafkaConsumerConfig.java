package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.core.message.listener.SecureDeliveryShipmentUpdateListener;
import com.walmart.move.nim.receiving.core.message.listener.kafka.InstructionDownloadListener;
import com.walmart.move.nim.receiving.core.message.listener.kafka.OfflineInstructionDownloadListener;
import com.walmart.move.nim.receiving.core.message.listener.kafka.SecureGdmDeliveryUpdateListener;
import com.walmart.move.nim.receiving.core.message.listener.kafka.SecureGdmItemUpdateListener;
import com.walmart.move.nim.receiving.core.message.listener.kafka.SecureKafkaInventoryAdjustmentListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@ConditionalOnExpression("${enable.listening.kafka.message:true}")
@Configuration
public class KafkaConsumerConfig {

  @ConditionalOnExpression("${is.inventory.on.secure.kafka:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public SecureKafkaInventoryAdjustmentListener secureKafkaInventoryAdjustmentListener() {
    return new SecureKafkaInventoryAdjustmentListener();
  }

  @Profile("!test")
  @ConditionalOnExpression("${enable.secure.kafka.delivery.shipment:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public SecureDeliveryShipmentUpdateListener secureDeliveryShipmentUpdateListener() {
    return new SecureDeliveryShipmentUpdateListener();
  }

  @Profile("!test")
  @ConditionalOnExpression("${enable.secure.kafka.delivery.update:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public SecureGdmDeliveryUpdateListener getSecureGdmDeliveryUpdateListener() {
    return new SecureGdmDeliveryUpdateListener();
  }

  @Profile("!test")
  @ConditionalOnExpression("${enable.secure.kafka.instruction.download:false}")
  @Bean
  public InstructionDownloadListener instructionDownloadListener() {
    return new InstructionDownloadListener();
  }

  @Profile("!test")
  @ConditionalOnExpression("${enable.secure.kafka.offline.instruction.download:false}")
  @Bean
  public OfflineInstructionDownloadListener offlineInstructionDownloadListener() {
    return new OfflineInstructionDownloadListener();
  }

  @Profile("!test")
  @ConditionalOnExpression("${enable.secure.kafka.item.update:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public SecureGdmItemUpdateListener secureGdmItemUpdateListener() {
    return new SecureGdmItemUpdateListener();
  }
}
