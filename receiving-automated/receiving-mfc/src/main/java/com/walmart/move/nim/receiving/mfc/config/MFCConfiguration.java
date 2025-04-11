package com.walmart.move.nim.receiving.mfc.config;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.TRAILER_OPEN_INSTRUCTION_REQUEST_HANDLER;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.service.CompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.message.DamageInventoryReportListener;
import com.walmart.move.nim.receiving.mfc.message.HawkeyeAdjustmentListener;
import com.walmart.move.nim.receiving.mfc.message.StoreNGRFinalizationEventListener;
import com.walmart.move.nim.receiving.mfc.message.publisher.ManualFinalizationPublisher;
import com.walmart.move.nim.receiving.mfc.message.publisher.ShipmentArrivalPublisher;
import com.walmart.move.nim.receiving.mfc.processor.*;
import com.walmart.move.nim.receiving.mfc.processor.v2.StoreBulkReceivingProcessor;
import com.walmart.move.nim.receiving.mfc.processor.v2.StoreInboundCreateContainerProcessorV2;
import com.walmart.move.nim.receiving.mfc.processor.v3.StoreInboundCreateContainerProcessorV3;
import com.walmart.move.nim.receiving.mfc.service.*;
import com.walmart.move.nim.receiving.mfc.service.InventoryAdjustmentHelper;
import com.walmart.move.nim.receiving.mfc.service.problem.fixit.FixitProblemService;
import com.walmart.move.nim.receiving.mfc.service.problem.lq.LoadQualityProblemService;
import com.walmart.move.nim.receiving.mfc.transformer.ContainerDTOEventTransformer;
import com.walmart.move.nim.receiving.mfc.transformer.HawkeyeReceiptTransformer;
import com.walmart.move.nim.receiving.mfc.transformer.InventoryReceiptTransformer;
import com.walmart.move.nim.receiving.mfc.transformer.NGRAsnTransformer;
import com.walmart.move.nim.receiving.mfc.transformer.NGRShipmentTransformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@ConditionalOnExpression("${enable.mfc.app:false}")
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.walmart.move.nim.receiving.mfc.repositories")
public class MFCConfiguration {

  @Bean(MFCConstant.MFC_DELIVERY_SERVICE)
  public MFCDeliveryService deliveryService() {
    return new MFCDeliveryService();
  }

  @Bean(MFCConstant.MFC_DELIVERY_METADATA_SERVICE)
  public MFCDeliveryMetadataService deliveryMetaDataService() {
    return new MFCDeliveryMetadataService();
  }

  @Bean(MFCConstant.AUTO_MFC_PROCESSOR)
  public EventProcessor autoEventProcessor() {
    return new AutoMFCProcessor();
  }

  @Bean(MFCConstant.MANUAL_MFC_PROCESSOR)
  public EventProcessor manualEventProcessor() {
    return new ManualMFCProcessor();
  }

  @Bean(MFCConstant.MFC_INVENTORY_ADJUSTMENT_PROCESSOR)
  public MFCInventoryAdjustmentProcessor mFCInventoryAdjustmentProcessor() {
    return new MFCInventoryAdjustmentProcessor();
  }

  @Bean(MFCConstant.MFC_HAWKEYE_DECANT_ADJUSTMENT_LISTENER)
  @ConditionalOnExpression("${enable.hawkeye.adjustment.listen:false}")
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public HawkeyeAdjustmentListener hawkeyeAdjustmentListener() {
    return new HawkeyeAdjustmentListener();
  }

  @Bean(MFCConstant.HAWKEYE_RECEIPT_TRANSFORMER)
  public HawkeyeReceiptTransformer hawkeyeReceiptTransformer() {
    return new HawkeyeReceiptTransformer();
  }

  @Bean(MFCConstant.INVENTORY_RECEIPT_TRANSFORMER)
  public InventoryReceiptTransformer inventoryReceiptTransformer() {
    return new InventoryReceiptTransformer();
  }

  @Bean(MFCConstant.NGR_SHIPMENT_TRANSFORMER)
  public NGRShipmentTransformer ngrShipmentTransformer() {
    return new NGRShipmentTransformer();
  }

  @Bean(MFCConstant.KAFKA_SHIPMENT_ARRIVAL_PUBLISHER)
  public ShipmentArrivalPublisher kafkaShipmentArrivalPublisher() {
    return new ShipmentArrivalPublisher();
  }

  @Bean(MFCConstant.INVENTORY_ADJUSTMENT_HELPER)
  public InventoryAdjustmentHelper inventoryAdjustmentHelper() {
    return new InventoryAdjustmentHelper();
  }

  @Bean(MFCConstant.DAMAGE_INVENTORY_REPORT_LISTENER)
  @ConditionalOnExpression("${enable.damage.inventory.update:false}")
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public DamageInventoryReportListener damageInventoryReportListener() {
    return new DamageInventoryReportListener();
  }

  @Bean
  public MFCReceiptService mfcReceiptService() {
    return new MFCReceiptService();
  }

  @Bean
  public DecantAuditService decantAuditService() {
    return new DecantAuditService();
  }

  @Bean("mfcOSDRService")
  public MFCOSDRService mfcOSDRService() {
    return new MFCOSDRService();
  }

  @Bean("mfcCompleteDeliveryProcessor")
  public CompleteDeliveryProcessor completeDeliveryProcessor() {
    return new MFCCompleteDeliveryProcessor();
  }

  @Bean
  public MFCContainerService mfcContainerService() {
    return new MFCContainerService();
  }

  @Bean("sibCreateContainerProcessor")
  public StoreInboundCreateContainerProcessor createContainerProcessor() {
    return new StoreInboundCreateContainerProcessor();
  }

  @Bean("onlySibCreateContainerProcessor")
  public OnlyStoreCreateContainerProcessor createContainerProcessorOnlyStore() {
    return new OnlyStoreCreateContainerProcessor();
  }

  @Bean
  public MFCReceivingService mfcReceivingService() {
    return new MFCReceivingService();
  }

  @Bean(TRAILER_OPEN_INSTRUCTION_REQUEST_HANDLER)
  public TrailerOpenInstructionRequestHandler trailerOpenInstructionRequestHandler() {
    return new TrailerOpenInstructionRequestHandler();
  }

  @Bean(MFCConstant.DELIVERY_COMPLETE_FLOW_EXECUTOR)
  public MFCPostDeliveryCompleteFlowProcessor deliveryCompleteFLowExecutor() {
    return new MFCPostDeliveryCompleteFlowProcessor();
  }

  @Bean(MFCConstant.MIXED_PALLET_PROCESSOR)
  public EventProcessor storeInboundMixedPalletProcessor() {
    return new StoreInboundMixedPalletProcessor();
  }

  @Bean(MFCConstant.MFC_PROBLEM_SERVICE)
  public MFCProblemService problemService() {
    return new MFCProblemService();
  }

  @Bean(MFCConstant.FIXIT_PROBLEM_SERVICE)
  public FixitProblemService fixitProblemService() {
    return new FixitProblemService();
  }

  @Bean(MFCConstant.LOAD_QUALITY_PROBLEM_SERVICE)
  public LoadQualityProblemService loadQualityProblemService() {
    return new LoadQualityProblemService();
  }

  @Bean(MFCConstant.CONTAINER_DTO_EVENT_TRANSFORMER)
  public ContainerDTOEventTransformer containerEventTransformer() {
    return new ContainerDTOEventTransformer();
  }

  @Bean(MFCConstant.STORE_DELIVERY_UNLOADING_PROCESSOR)
  public StoreDeliveryUnloadingProcessor storeDeliveryUnloadingProcessor() {
    return new StoreDeliveryUnloadingProcessor();
  }

  @Bean(MFCConstant.STORE_DELIVERY_UNLOADING_PROCESSOR_V2)
  public StoreDeliveryUnloadingProcessorV2 storeDeliveryUnloadingProcessorV2() {
    return new StoreDeliveryUnloadingProcessorV2();
  }

  @Bean(MFCConstant.STORE_CONTAINER_SHORTAGE_PROCESSOR)
  public StoreProblemHandingProcessor storeContainerShortageProcessor() {
    return new StoreProblemHandingProcessor();
  }

  @Bean(MFCConstant.STORE_BULK_RECEIVING_PROCESSOR)
  public StoreBulkReceivingProcessor storeManualFinaliseProcessor() {
    return new StoreBulkReceivingProcessor();
  }

  @Bean(MFCConstant.STORE_INBOUND_CONTAINER_CREATION_V2)
  public StoreInboundCreateContainerProcessorV2 storeInboundCreateContainerProcessorV2() {
    return new StoreInboundCreateContainerProcessorV2();
  }

  @Bean(MFCConstant.STORE_INBOUND_CONTAINER_CREATION_V3)
  public StoreInboundCreateContainerProcessorV3 storeInboundCreateContainerProcessorV3() {
    return new StoreInboundCreateContainerProcessorV3();
  }

  @Bean
  public ContainerEventService getContainerEventService() {
    return new ContainerEventService();
  }

  @Bean("kafkaShipmentArrivalPublisher")
  public ShipmentArrivalPublisher createKafkaShipmentArrivalPublisher() {
    return new ShipmentArrivalPublisher();
  }

  @Bean("ngrShipmentTransformer")
  public Transformer createNGRShipmentTransformer() {
    return new NGRShipmentTransformer();
  }

  @Bean(MFCConstant.SHIPMENT_FINANCE_PROCESSOR)
  public ShipmentFinanceProcessor createShipmentFinanceProcessor() {
    return new ShipmentFinanceProcessor();
  }

  @Bean(MFCConstant.KAFKA_MANUAL_FINALIZATION_PUBLISHER)
  public ManualFinalizationPublisher kafkaManualFinalizationPublisher() {
    return new ManualFinalizationPublisher();
  }

  @Bean(MFCConstant.MANUAL_FINALIZATION_PROCESSOR)
  public ManualFinalizationProcessor createManualFinalizationProcessor() {
    return new ManualFinalizationProcessor();
  }

  @Bean(MFCConstant.NGR_FINALIZATION_EVENT_LISTENER)
  @ConditionalOnExpression("${enable.ngr.store.finalization.event.listen:false}")
  public StoreNGRFinalizationEventListener getNGRFinalizationEventListener() {
    return new StoreNGRFinalizationEventListener();
  }

  @Bean(MFCConstant.NGR_FINALIZATION_EVENT_PROCESSOR)
  @ConditionalOnExpression("${enable.ngr.store.finalization.event.listen:false}")
  public StoreNGRFinalizationEventProcessor getNGRFinalizationEventProcessor() {
    return new StoreNGRFinalizationEventProcessor();
  }

  @Bean(MFCConstant.POST_DSD_CONTAINER_CREATE_PROCESSOR)
  public DSDContainerCreatePostProcessor getDSDContainerCreatePostProcessor() {
    return new DSDContainerCreatePostProcessor();
  }

  @Bean
  public MixedPalletRejectService getMixedPalletRejectService() {
    return new MixedPalletRejectService();
  }

  @Bean(MFCConstant.NGR_ASN_TRANSFORMER)
  public NGRAsnTransformer getNGRAsnTransformer() {
    return new NGRAsnTransformer();
  }
}
