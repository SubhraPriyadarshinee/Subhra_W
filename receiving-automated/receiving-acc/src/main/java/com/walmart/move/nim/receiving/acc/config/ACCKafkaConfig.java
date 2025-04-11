package com.walmart.move.nim.receiving.acc.config;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.acc.message.listener.ACLVerificationKafkaListener;
import com.walmart.move.nim.receiving.acc.message.listener.HawkeyeLpnSwapListener;
import com.walmart.move.nim.receiving.acc.message.listener.KafkaNotificationListener;
import com.walmart.move.nim.receiving.acc.message.listener.RoboDepalContainerStatusListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@ConditionalOnExpression("${enable.listening.kafka.message:true}")
@Configuration
public class ACCKafkaConfig {

  @Profile("!test")
  @ConditionalOnExpression("${enable.acc.hawkeye.queue:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public ACLVerificationKafkaListener aclVerificationKafkaListener() {
    return new ACLVerificationKafkaListener();
  }

  @Profile("!test")
  @ConditionalOnExpression("${enable.acc.hawkeye.queue:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public KafkaNotificationListener kafkaNotificationListener() {
    return new KafkaNotificationListener();
  }

  @Profile("!test")
  @ConditionalOnExpression("${enable.acc.hawkeye.queue:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public HawkeyeLpnSwapListener hawkeyeLpnSwapListener() {
    return new HawkeyeLpnSwapListener();
  }

  @Profile("!test")
  @ConditionalOnExpression("${robo.depal.feature.enabled:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public RoboDepalContainerStatusListener roboDepalContainerStatusListener() {
    return new RoboDepalContainerStatusListener();
  }
}
