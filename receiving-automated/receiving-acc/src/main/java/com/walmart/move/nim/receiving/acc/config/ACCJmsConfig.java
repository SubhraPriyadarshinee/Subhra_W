package com.walmart.move.nim.receiving.acc.config;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.acc.message.listener.ACLNotificationListener;
import com.walmart.move.nim.receiving.acc.message.listener.ACLVerificationListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@ConditionalOnExpression("${enable.jms.messages.listen:true}")
@Configuration
public class ACCJmsConfig {
  @ConditionalOnExpression("${enable.acl.queue:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public ACLNotificationListener aclNotificationListener() {
    return new ACLNotificationListener();
  }

  @ConditionalOnExpression("${enable.acl.queue:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public ACLVerificationListener aclVerificationListener() {
    return new ACLVerificationListener();
  }
}
