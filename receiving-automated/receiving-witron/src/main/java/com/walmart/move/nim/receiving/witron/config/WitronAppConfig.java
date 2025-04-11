package com.walmart.move.nim.receiving.witron.config;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GDC_DELIVERY_UNLOADER_PROCESSOR;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.*;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import com.walmart.move.nim.receiving.witron.publisher.WitronDCFinServiceImpl;
import com.walmart.move.nim.receiving.witron.service.*;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Witron receiving application config goes here
 *
 * @author v0k00fe
 */
@Configuration
@EnableTransactionManagement
@ConditionalOnExpression("${enable.witron.app:false}")
@EnableJpaRepositories(basePackages = "com.walmart.move.nim.receiving.witron.repositories")
public class WitronAppConfig {

  @ManagedConfiguration private AppConfig appConfig;

  @Bean(name = ReceivingConstants.WITRON_PUT_ON_HOLD_SERVICE)
  public PutOnHoldService getWitronPutOnHoldHandler() {
    return new WitronPutOnHoldService();
  }

  @Bean(GdcConstants.WITRON_DC_FIN_SERVICE)
  public DCFinService witronDCFinService() {
    return new WitronDCFinServiceImpl();
  }

  @Bean(GdcConstants.WITRON_LPN_CACHE_SERVICE)
  public LPNCacheService getWitronLPNCacheService(
      @Qualifier(GdcConstants.WITRON_LPN_SERVICE) LPNService lpnService) {

    LPNCacheServiceInMemoryImpl lpnCacheService = new LPNCacheServiceInMemoryImpl();
    lpnCacheService.setLpnService(lpnService);
    return lpnCacheService;
  }

  @Bean(name = GdcConstants.WITRON_GDM_PO_LINE_RESPONSE_CACHE)
  public Cache<Long, Map<DeliveryCacheKey, DeliveryCacheValue>> gdmPoLineResponseCache() {

    return CacheBuilder.newBuilder()
        .maximumSize(appConfig.getDcFinDeliveryCacheSize())
        .expireAfterWrite(appConfig.getDcFinDeliveryCacheSizeTTLInSec(), TimeUnit.SECONDS)
        .build();
  }

  @Bean(name = GdcConstants.WITRON_INSTRUCTION_SERVICE)
  public InstructionService getWitronInstructionsHandler() {
    return new WitronInstructionService();
  }

  @Bean(name = GdcConstants.GDC_INSTRUCTION_SERVICE)
  public InstructionService getGdcInstructionsHandler() {
    return new GdcInstructionService();
  }

  @Bean(GdcConstants.WITRON_OSDR_SERIVCE)
  public WitronOsdrService witronOsdrService() {
    return new WitronOsdrService();
  }

  @Bean(GdcConstants.WITRON_UPDATE_INSTRUCTION_HANDLER)
  public WitronUpdateInstructionHandler getWitronUpdateInstructionHandler() {
    return new WitronUpdateInstructionHandler();
  }

  @Bean(GDC_INVENTORY_EVENT_PROCESSOR)
  public GdcInventoryEventProcessor getGdcInventoryEventProcessor() {
    return new GdcInventoryEventProcessor();
  }

  @Bean(GDC_COMPLETE_DELIVERY_PROCESSOR)
  public GdcCompleteDeliveryProcessor getGdcCompleteDeliveryProcessor() {
    return new GdcCompleteDeliveryProcessor();
  }

  @Bean(name = ReceivingConstants.GDC_RECEIVE_INSTRUCTION_HANDLER)
  public ReceiveInstructionHandler getGdcReceiveInstructionHandler() {
    return new GdcReceiveInstructionHandler();
  }

  @Bean(name = ReceivingConstants.GDC_REFRESH_INSTRUCTION_HANDLER)
  public RefreshInstructionHandler getGdcRefreshInstructionHandler() {
    return new GdcRefreshInstructionHandler();
  }

  @Bean(name = GdcConstants.GDC_FIXIT_PROBLEM_SERVICE)
  public GdcFixitProblemService getGdcFixitProblemService() {
    return new GdcFixitProblemService();
  }

  @Bean(name = GDC_CANCEL_CONTAINER_PROCESSOR)
  public GdcCancelContainerProcessor getGdcCancelContainerProcessor() {
    return new GdcCancelContainerProcessor();
  }

  @Bean(GDC_DELIVERY_UNLOADER_PROCESSOR)
  public GdcDeliveryUnloaderProcessor getGdcDeliveryUnloaderProcessor() {
    return new GdcDeliveryUnloaderProcessor();
  }
}
