package com.walmart.move.nim.receiving.rdc.config;

import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.service.*;
import io.strati.configuration.annotation.ManagedConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * RDC receiving application config goes here
 *
 * @author s1b041i
 */
@Configuration
@EnableTransactionManagement
@ConditionalOnExpression("${enable.rdc.app:false}")
@EnableJpaRepositories(basePackages = "com.walmart.move.nim.receiving.rdc.repositories")
public class RdcAppConfig {

  @ManagedConfiguration private AppConfig appConfig;

  @Bean(name = RdcConstants.RDC_DOCKTAG_SERVICE)
  public DockTagService getRdcDockTagService() {
    return new RdcDockTagService();
  }

  @Bean(name = RdcConstants.RDC_INSTRUCTION_SERVICE)
  public InstructionService getRdcInstructionService() {
    return new RdcInstructionService();
  }

  @Bean(name = RdcConstants.RDC_RECEIVE_INSTRUCTION_HANDLER)
  public ReceiveInstructionHandler getRdcReceiveInstructionHandler() {
    return new RdcReceiveInstructionHandler();
  }

  @Bean(name = RdcConstants.RDC_RECEIVE_EXCEPTION_HANDLER)
  public ReceiveExceptionHandler getRdcReceiveExceptionHandler() {
    return new RdcReceiveExceptionHandler();
  }

  @Bean(name = RdcConstants.RDC_REFRESH_INSTRUCTION_HANDLER)
  public RefreshInstructionHandler getRdcRefreshInstructionHandler() {
    return new RdcRefreshInstructionHandler();
  }

  @Bean(name = RdcConstants.RDC_ITEM_CATALOG_SERVICE)
  public RdcItemCatalogService getRdcItemCatalogService() {
    return new RdcItemCatalogService();
  }

  @Bean(name = RdcConstants.RDC_ITEM_SERVICE_HANDLER)
  public RdcItemServiceHandler getRdcItemServiceHandler() {
    return new RdcItemServiceHandler();
  }

  @Bean(name = RdcConstants.RDC_COMPLETE_DELIVERY_PROCESSOR)
  public RdcCompleteDeliveryProcessor getRdcCompleteDeliveryProcessor() {
    return new RdcCompleteDeliveryProcessor();
  }

  @Bean(name = RdcConstants.RDC_OSDR_SERVICE)
  public RdcOsdrService getRdcOsdrService() {
    return new RdcOsdrService();
  }

  @Bean(name = RdcConstants.RDC_OSDR_PROCESSOR)
  public RdcOsdrProcessor getRdcOsdrProcessor() {
    return new RdcOsdrProcessor();
  }

  @Bean(name = RdcConstants.RDC_DELIVERY_METADATA_SERVICE)
  public RdcDeliveryMetaDataService getRdcDeliveryMetaDataService() {
    return new RdcDeliveryMetaDataService();
  }

  @Bean(name = RdcConstants.RDC_CANCEL_CONTAINER_PROCESSOR)
  public RdcCancelContainerProcessor getRdcCancelContainerProcessor() {
    return new RdcCancelContainerProcessor();
  }

  @Bean(name = RdcConstants.RDC_UPDATE_CONTAINER_QUANTITY_HANDLER)
  public RdcUpdateContainerQuantityHandler getRdcUpdateContainerQuantityHandler() {
    return new RdcUpdateContainerQuantityHandler();
  }

  @Bean(name = RdcConstants.RDC_DELIVERY_SERVICE)
  public RdcDeliveryService getRdcDeliveryService() {
    return new RdcDeliveryService();
  }

  @Bean(name = RdcConstants.RDC_RECEIPT_SUMMARY_PROCESSOR)
  public RdcReceiptSummaryProcessor getRdcReceiptSummaryProcessor() {
    return new RdcReceiptSummaryProcessor();
  }

  @Bean(RdcConstants.RDC_LPN_CACHE_SERVICE)
  public LPNCacheService getRdcLpnCacheService(
      @Qualifier(RdcConstants.RDC_LPN_SERVICE) LPNService lpnService) {
    LPNCacheServiceInMemoryImpl lpnCacheService = new LPNCacheServiceInMemoryImpl();
    lpnCacheService.setLpnService(lpnService);
    return lpnCacheService;
  }

  @Bean(name = RdcConstants.RDC_KAFKA_INVENTORY_ADJ_EVENT_PROCESSOR)
  public RdcKafkaInventoryEventProcessor getRdcKafkaInventoryProcessor() {
    return new RdcKafkaInventoryEventProcessor();
  }

  @Bean(name = RdcConstants.RDC_FIXIT_PROBLEM_SERVICE)
  public RdcFixitProblemService getRdcFixitProblemService() {
    return new RdcFixitProblemService();
  }

  @Bean(name = RdcConstants.RDC_FIT_PROBLEM_SERVICE)
  public RdcFitProblemService getRdcFitProblemService() {
    return new RdcFitProblemService();
  }

  @Bean(name = RdcConstants.VENDOR_DELIVERY_DOCUMENT_SEARCH_HANDLER)
  public VendorBasedDeliveryDocumentsSearchHandler getVendorBasedDeliveryDocumentsSearchHandler() {
    return new VendorBasedDeliveryDocumentsSearchHandler();
  }

  @Bean(name = RdcConstants.RDC_ITEM_UPDATE_PROCESSOR)
  public RdcItemUpdateProcessor getItemUpdateProcessor() {
    return new RdcItemUpdateProcessor();
  }
}
