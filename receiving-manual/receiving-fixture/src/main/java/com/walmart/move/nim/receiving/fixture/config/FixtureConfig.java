package com.walmart.move.nim.receiving.fixture.config;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.core.service.LPNCacheServiceInMemoryImpl;
import com.walmart.move.nim.receiving.core.service.LPNService;
import com.walmart.move.nim.receiving.fixture.common.CTToken;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.event.processor.FixtureDeliveryEventProcessor;
import com.walmart.move.nim.receiving.fixture.event.processor.ShipmentPersistProcessor;
import com.walmart.move.nim.receiving.fixture.event.processor.ShipmentReconcileProcessor;
import com.walmart.move.nim.receiving.fixture.event.processor.ShipmentUpdateEventProcessor;
import com.walmart.move.nim.receiving.fixture.job.FixtureSchedulerJob;
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import com.walmart.move.nim.receiving.fixture.orchestrator.IOrchestratorStrategy;
import com.walmart.move.nim.receiving.fixture.orchestrator.ShipmentEventProcessStrategy;
import com.walmart.move.nim.receiving.fixture.service.ControlTowerService;
import com.walmart.move.nim.receiving.fixture.service.FixtureDeliveryMetadataService;
import com.walmart.move.nim.receiving.fixture.service.FixtureItemService;
import com.walmart.move.nim.receiving.fixture.service.PalletReceivingService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@ConditionalOnExpression("${enable.fixture.app:false}")
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.walmart.move.nim.receiving.fixture.repositories")
public class FixtureConfig {

  @Bean
  public ControlTowerService controlTowerService() {
    return new ControlTowerService();
  }

  @Bean
  public CTToken ctToken() {
    return new CTToken();
  }

  @Bean
  public PalletReceivingService palletReceivingService() {
    return new PalletReceivingService();
  }

  @Bean(FixtureConstants.FIXTURE_DELIVERY_EVENT_PROCESSOR)
  public FixtureDeliveryEventProcessor fixtureDeliveryEventProcessor() {
    return new FixtureDeliveryEventProcessor();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public FixtureSchedulerJob fixtureSchedulerJob() {
    return new FixtureSchedulerJob();
  }

  @Bean
  public FixtureItemService fixtureItemService() {
    return new FixtureItemService();
  }

  @Bean(ReceivingConstants.FIXTURE_DELIVERY_METADATA_SERVICE)
  public FixtureDeliveryMetadataService fixtureDeliveryMetaDataService() {
    return new FixtureDeliveryMetadataService();
  }

  @Bean(FixtureConstants.SHIPMENT_RECONCILE_PROCESSOR_BEAN)
  public ShipmentReconcileProcessor shipmentReconcileProcessor() {
    return new ShipmentReconcileProcessor();
  }

  @Bean(FixtureConstants.SHIPMENT_PERSIST_PROCESSOR_BEAN)
  public ShipmentPersistProcessor shipmentPersistProcessor() {
    return new ShipmentPersistProcessor();
  }

  @Bean(FixtureConstants.SHIPMENT_UPDATE_PROCESSOR_BEAN)
  public ShipmentUpdateEventProcessor shipmentUpdateEventProcessor() {
    return new ShipmentUpdateEventProcessor();
  }

  @Bean(FixtureConstants.SHIPMENT_EVENT_PROCESSOR_STRATEGY)
  public IOrchestratorStrategy<ShipmentEvent> shipmentEventProcessStrategy() {
    return new ShipmentEventProcessStrategy();
  }

  @Bean(FixtureConstants.RFC_LPN_CACHE_SERVICE)
  public LPNCacheService getWitronLPNCacheService(
      @Qualifier(FixtureConstants.RFC_LPN_SERVICE) LPNService lpnService) {

    LPNCacheServiceInMemoryImpl lpnCacheService = new LPNCacheServiceInMemoryImpl();
    lpnCacheService.setLpnService(lpnService);
    return lpnCacheService;
  }
}
