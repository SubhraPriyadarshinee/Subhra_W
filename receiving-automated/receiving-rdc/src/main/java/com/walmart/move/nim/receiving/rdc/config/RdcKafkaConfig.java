package com.walmart.move.nim.receiving.rdc.config;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.rdc.message.listener.*;
import com.walmart.move.nim.receiving.rdc.message.listener.SymPutawayConfirmationListener;
import com.walmart.move.nim.receiving.rdc.message.listener.SymPutawayNackListener;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@ConditionalOnExpression("${enable.listening.kafka.message:true}")
@Configuration
public class RdcKafkaConfig {

  @ConditionalOnExpression("${hawkeye.sym.listener.enabled:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public SymPutawayNackListener symPutawayNackListener() {
    return new SymPutawayNackListener();
  }

  @ConditionalOnExpression("${hawkeye.sym.listener.enabled:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public SymPutawayConfirmationListener symPutawayConfirmationListener() {
    return new SymPutawayConfirmationListener();
  }

  @ConditionalOnExpression("${hawkeye.sym.flib.listener.enabled:false}")
  @Bean(name = ReceivingConstants.RDC_VERIFICATION_MESSAGE_LISTENER)
  public RdcVerificationMessageListener rdcVerificationMessageListener() {
    return new RdcVerificationMessageListener();
  }

  @ConditionalOnExpression("${hawkeye.sym.flib.listener.enabled:false}")
  @Bean(name = ReceivingConstants.LABEL_GROUP_UPDATE_COMPLETED_EVENT_LISTENER)
  public LabelGroupUpdateCompletedEventListener labelGroupUpdateCompletedEventListener() {
    return new LabelGroupUpdateCompletedEventListener();
  }

  @ConditionalOnExpression("${enable.secure.kafka.slot.update:false}")
  @Bean(name = ReceivingConstants.RDC_SLOT_UPDATE_LISTENER)
  public RdcSlotUpdateListener rdcSlotUpdateListener() {
    return new RdcSlotUpdateListener();
  }
}
