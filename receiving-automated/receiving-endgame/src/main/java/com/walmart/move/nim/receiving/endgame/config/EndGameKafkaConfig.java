package com.walmart.move.nim.receiving.endgame.config;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.endgame.message.listener.SecureDivertAckListener;
import com.walmart.move.nim.receiving.endgame.message.listener.SecureDivertAckListenerEUS;
import com.walmart.move.nim.receiving.endgame.message.listener.SecureDivertAckListenerSCUS;
import com.walmart.move.nim.receiving.endgame.message.listener.SecurePrintingAckListener;
import com.walmart.move.nim.receiving.endgame.message.listener.SecurePrintingAckListenerEUS;
import com.walmart.move.nim.receiving.endgame.message.listener.SecurePrintingAckListenerSCUS;
import com.walmart.move.nim.receiving.endgame.message.listener.SecureUpdateAttributeListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@ConditionalOnExpression("${enable.endgame.app:false}")
@Configuration
@Profile("!test")
public class EndGameKafkaConfig {

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public SecureUpdateAttributeListener secureUpdateAttributeListener() {
    return new SecureUpdateAttributeListener();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  @ConditionalOnExpression("${hawkeye.consumer.enable:true}")
  public SecurePrintingAckListener printingAckSecureKafkaListener() {
    return new SecurePrintingAckListener();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  @ConditionalOnExpression("${hawkeye.consumer.enable.eus:false}")
  public SecurePrintingAckListenerEUS printingAckSecureKafkaListenerEUS() {
    return new SecurePrintingAckListenerEUS();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  @ConditionalOnExpression("${hawkeye.consumer.enable.scus:false}")
  public SecurePrintingAckListenerSCUS printingAckSecureKafkaListenerSCUS() {
    return new SecurePrintingAckListenerSCUS();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  @ConditionalOnExpression("${hawkeye.consumer.enable:true}")
  public SecureDivertAckListener divertAckSecureKafkaListener() {
    return new SecureDivertAckListener();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  @ConditionalOnExpression("${hawkeye.consumer.enable.eus:false}")
  public SecureDivertAckListenerEUS divertAckSecureKafkaListenerEUS() {
    return new SecureDivertAckListenerEUS();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  @ConditionalOnExpression("${hawkeye.consumer.enable.scus:false}")
  public SecureDivertAckListenerSCUS divertAckSecureKafkaListenerSCUS() {
    return new SecureDivertAckListenerSCUS();
  }
}
