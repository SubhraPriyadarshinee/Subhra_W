package com.walmart.move.nim.receiving.core.config.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.GsonUTCInstantAdapter;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.item.rules.LimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonLimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonRule;
import com.walmart.move.nim.receiving.core.item.rules.RuleSet;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.metrics.spring.CountedAnnotationBeanPostProcessor;
import com.walmart.platform.metrics.spring.ExceptionCountedAnnotationBeanPostProcessor;
import com.walmart.platform.metrics.spring.TimedAnnotationBeanPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Instant;
import java.util.Date;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT0S")
@EnableRetry
public class CommonBeans {

  @ManagedConfiguration private AppConfig appConfig;

  @Bean
  @Primary
  public Gson gson() {
    return new Gson();
  }

  @Bean
  public Gson gsonForInstantAdapter() {
    return new GsonBuilder()
        .registerTypeAdapter(Instant.class, new GsonUTCInstantAdapter())
        .create();
  }

  @Bean(name = ReceivingConstants.GSON_UTC_ADAPTER)
  public Gson gsonUTCDateAdapter() {
    return new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Bean(name = ReceivingConstants.DEFAULT_PUTAWAY_HANDLER)
  public PutawayService getDefaultPutawayHandler() {
    return new DefaultPutawayHandler();
  }

  @Bean(name = ReceivingConstants.WITRON_PUTAWAY_HANDLER)
  public PutawayService getWitronPutawayHandler() {
    return new WitronPutawayHandler();
  }

  // Medusa beans
  @Bean
  public ExceptionCountedAnnotationBeanPostProcessor
      getExceptionCountedAnnotationBeanPostProcessor() {
    return new ExceptionCountedAnnotationBeanPostProcessor();
  }

  @Bean
  public TimedAnnotationBeanPostProcessor getTimedAnnotationBeanPostProcessor() {
    return new TimedAnnotationBeanPostProcessor();
  }

  @Bean
  public CountedAnnotationBeanPostProcessor getCountedAnnotationBeanPostProcessor() {
    return new CountedAnnotationBeanPostProcessor();
  }

  @Bean
  public ResourceBundleMessageSource ResourceBundleMessageSource() {
    ResourceBundleMessageSource resourceBundleMessageSource = new ResourceBundleMessageSource();
    resourceBundleMessageSource.setBasenames(
        ReceivingConstants.MESSAGES,
        ReceivingConstants.HEADER_MESSAGES,
        ReceivingConstants.LABEL_FORMAT);
    return resourceBundleMessageSource;
  }

  @Primary
  @Bean(ReceivingConstants.DEFAULT_LPN_SERVICE)
  public LPNService getLPNService() {
    return new LPNServiceImpl();
  }

  @Primary
  @Bean(ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
  public LPNCacheService getLPNCacheService(
      @Qualifier(ReceivingConstants.DEFAULT_LPN_SERVICE) LPNService lpnService) {

    LPNCacheServiceInMemoryImpl lpnCacheService = new LPNCacheServiceInMemoryImpl();
    lpnCacheService.setLpnService(lpnService);
    return lpnCacheService;
  }

  @Bean(ReceivingConstants.MCC_DELIVERY_EVENT_PROCESSOR)
  public EventProcessor deliveryEventProcessor() {
    return new MccDeliveryEventProcessor();
  }

  @Primary
  @Bean(ReceivingConstants.DEFAULT_DC_FIN_SERVICE)
  public DCFinService dcFinService() {
    return new DCFinService();
  }

  @Bean(ReceivingConstants.DC_FIN_SERVICE_V2)
  public DCFinServiceV2 dcFinService2() {
    return new DCFinServiceV2();
  }

  @Bean("itemCategoryRuleSet")
  public RuleSet ruleSet(
      LithiumIonLimitedQtyRule lithiumIonLimitedQtyRule,
      LithiumIonRule lithiumIonRule,
      LimitedQtyRule limitedQtyRule) {
    return new RuleSet(lithiumIonLimitedQtyRule, lithiumIonRule, limitedQtyRule);
  }

  @Bean(name = ReceivingConstants.CC_RECEIVE_INSTRUCTION_HANDLER)
  public ReceiveInstructionHandler getCcReceiveInstructionHandler() {
    return new CCReceiveInstructionHandler();
  }

  @Bean
  public MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }
}
