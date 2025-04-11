package com.walmart.move.nim.receiving.sib.config;

import static com.walmart.move.nim.receiving.sib.utils.Constants.STORE_CONTAINER_OVERAGE_EVENT_PROCESSOR;
import static com.walmart.move.nim.receiving.sib.utils.Constants.STORE_DELIVERY_METADATA_SERVICE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.sib.event.processing.*;
import com.walmart.move.nim.receiving.sib.job.EventProcessingJob;
import com.walmart.move.nim.receiving.sib.job.EventPublisherJob;
import com.walmart.move.nim.receiving.sib.messsage.listener.*;
import com.walmart.move.nim.receiving.sib.messsage.publisher.KafkaContainerEventPublisher;
import com.walmart.move.nim.receiving.sib.processor.*;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.service.*;
import com.walmart.move.nim.receiving.sib.transformer.ContainerDataTransformer;
import com.walmart.move.nim.receiving.sib.transformer.EventTransformer;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@ConditionalOnExpression("${enable.sib.app:false}")
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.walmart.move.nim.receiving.sib.repositories")
public class SIBAppConfig {

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public EventPublisherJob eventPublisherJob() {
    return new EventPublisherJob();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public EventProcessingJob eventProcessingJob() {
    return new EventProcessingJob();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public PalletScanContainerListener secureKafkaPalletScanContainerListener() {
    return new PalletScanContainerListener();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public DeliveryStatusListener deliveryStatusListener() {
    return new DeliveryStatusListener();
  }

  @Bean
  public PackContainerService packContainerService() {
    return new PackContainerService();
  }

  @Bean
  public StoreDeliveryService storeDeliveryService() {
    return new StoreDeliveryService();
  }

  @Bean
  public StoreInventoryService storeInventoryService() {
    return new StoreInventoryService();
  }

  @Bean
  public KafkaContainerEventPublisher kafkaContainerEventPublisher() {
    return new KafkaContainerEventPublisher();
  }

  @Bean
  public EventPublisherService eventPublisherService() {
    return new EventPublisherService();
  }

  @Bean
  public EventProcessingService eventProcessingService() {
    return new EventProcessingService();
  }

  @Bean
  public ContainerEventListener containerEventListener() {
    return new ContainerEventListener();
  }

  @Bean
  public CleanupEventListener cleanupEventListener() {
    return new CleanupEventListener();
  }

  @Bean(name = STORE_CONTAINER_OVERAGE_EVENT_PROCESSOR)
  public OverageContainerEventProcessor overageContainerEventProcessor() {
    return new OverageContainerEventProcessor();
  }

  @Bean(name = CORRECTION_CONTAINER_EVENT_PROCESSOR)
  public CorrectionContainerEventProcessor correctionContainerEventProcessor() {
    return new CorrectionContainerEventProcessor();
  }

  @Bean(name = STORE_DELIVERY_METADATA_SERVICE)
  public StoreDeliveryMetadataService storeDeliveryMetadataService() {
    return new StoreDeliveryMetadataService();
  }

  @Bean
  public ContainerDataTransformer containerDataTransformer() {
    return new ContainerDataTransformer();
  }

  @Bean
  public EventRegistrationService eventResistrationService() {
    return new EventRegistrationService();
  }

  @Bean
  public EventProcessingResolver eventProcessingResolver() {
    return new EventProcessingResolver();
  }

  @Bean
  public NHMEventProcessing nhmEventProcessing() {
    return new NHMEventProcessing();
  }

  @Bean
  public MeatProduceEventProcessing meatProduceProcessing() {
    return new MeatProduceEventProcessing();
  }

  @Bean
  public SuperCentreEventProcessing superCentreEventProcessing() {
    return new SuperCentreEventProcessing();
  }

  @Bean
  public DeliveryStatusEventProcessor deliveryStatusEventProcessor() {
    return new DeliveryStatusEventProcessor();
  }

  @Bean
  public FireflyEventProcessor fireflyEventProcessor() {
    return new FireflyEventProcessor();
  }

  @Bean
  public DefaultEventProcessing defaultEventProcessing() {
    return new DefaultEventProcessing();
  }

  @ConditionalOnExpression("${enable.ngr.parity.for.store:true}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public GdmDeliveryUpdateListener gdmDeliveryUpdateListener() {
    return new GdmDeliveryUpdateListener();
  }

  @ConditionalOnExpression("${firefly.consumer.enable:false}")
  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public FireflyEventListener fireflyEventListener() {
    return new FireflyEventListener();
  }

  @Bean
  public EIStockEventListener eiStockEventListener() {
    return new EIStockEventListener();
  }

  @Bean
  public ManualFinalizationEventListener manualFinalizationEventListener() {
    return new ManualFinalizationEventListener();
  }

  @Bean
  public StoreAutoInitializationEventListener storeAutoInitializationEventListener() {
    return new StoreAutoInitializationEventListener();
  }

  @Bean
  public ManualFinalizationService manualFinalizationService() {
    return new ManualFinalizationService();
  }

  @Bean
  public EventTransformer eventTransformer() {
    return new EventTransformer();
  }

  @Bean
  public EventService eventService(
      EventTransformer eventTransformer, EventRepository eventRepository) {
    return new EventService(eventRepository, eventTransformer);
  }

  @Bean(name = AUTO_DELIVERY_COMPLETE_EVENT_PROCESSOR)
  public AutoDeliveryCompleteEventProcessor autoDeliveryCompleteEventProcessor() {
    return new AutoDeliveryCompleteEventProcessor();
  }

  @Bean
  public DeliveryAutoCompleteEventListener deliveryAutoCompleteEventListener() {
    return new DeliveryAutoCompleteEventListener();
  }

  @Bean
  public DeliveryUnloadCompleteEventListener deliveryUnloadCompleteEventListener() {
    return new DeliveryUnloadCompleteEventListener();
  }

  @Bean(name = ReceivingConstants.DELIVERY_UNLOAD_COMPLETE_CREATE_EVENT_PROCESSOR)
  public DeliveryUnloadCompleteEventProcessor deliveryUnloadCompleteEventProcessor() {
    return new DeliveryUnloadCompleteEventProcessor();
  }
}
