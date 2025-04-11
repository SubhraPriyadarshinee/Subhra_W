package com.walmart.move.nim.receiving.rx.config;

import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.*;
import io.strati.configuration.annotation.ManagedConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Rx receiving application config goes here
 *
 * @author s0d05gy
 */
@Configuration
@EnableTransactionManagement
@ConditionalOnExpression("${enable.rx.app:false}")
@EnableJpaRepositories(basePackages = "com.walmart.move.nim.receiving.rx.repositories")
public class RxAppConfig {

  @ManagedConfiguration private AppConfig appConfig;

  @Bean
  public RxInstructionService rxInstructionService() {
    return new RxInstructionService();
  }

  @Bean(name = RxConstants.RX_INSTRUCTION_SERVICE)
  public InstructionService getRxInstructionsHandler() {
    return new RxInstructionService();
  }

  @Bean(name = RxConstants.RX_LEGACY_INSTRUCTION_SERVICE)
  public InstructionService getRxLegacyInstructionsHandler() {
    return new RxLegacyInstructionService();
  }

  @Bean(name = RxConstants.RX_DELIVERY_SERVICE)
  public RxDeliveryServiceImpl getRxDeliveryService() {
    return new RxDeliveryServiceImpl();
  }

  @Bean(name = RxConstants.RX_UPDATE_INSTRUCTION_HANDLER)
  public UpdateInstructionHandler rxUpdateInstructionHandler() {
    return new RxUpdateInstructionHandler();
  }

  @Bean(name = RxConstants.RX_UPDATE_CONTAINER_QUANTITY_REQUEST_HANDLER)
  public UpdateContainerQuantityRequestHandler rxUpdateContainerQuantityRequestHandler() {
    return new RxUpdateContainerQuantityRequestHandler();
  }

  @Bean(value = RxConstants.RX_COMPLETE_DELIVERY_PROCESSOR)
  public RxCompleteDeliveryProcessor rxCompleteDeliveryProcessor() {
    return new RxCompleteDeliveryProcessor();
  }

  @Bean(value = RxConstants.RX_CANCEL_CONTAINER_PROCESSOR)
  public RxCancelContainerProcessor rxCancelContainerProcessor() {
    return new RxCancelContainerProcessor();
  }

  @Bean(value = RxConstants.RX_CONTAINER_ADJUSTMENT_VALIDATOR)
  public RxContainerAdjustmentValidator rxContainerAdjustmentValidator() {
    return new RxContainerAdjustmentValidator();
  }

  @Bean(value = RxConstants.RX_DELETE_CONTAINERS_REQUEST_HANDLER)
  public DeleteContainersRequestHandler rxDeleteContainersRequestHandler() {
    return new RxDeleteContainersRequestHandler();
  }

  @Bean(value = RxConstants.RX_RECEIPT_SUMMARY_PROCESSOR)
  public RxReceiptSummaryProcessor rxReceiptSummaryProcessor() {
    return new RxReceiptSummaryProcessor();
  }

  @Bean(value = RxConstants.RX_INSTRUCTION_SEARCH_HANDLER)
  public RxInstructionSearchRequestHandler rxInstructionSearchRequestHandler() {
    return new RxInstructionSearchRequestHandler();
  }

  @Bean(value = RxConstants.TWOD_BARCODE_DELIVERY_DOCUMENTS_SEARCH_HANDLER)
  public TwoDBarcodeScanTypeDocumentsSearchHandler twoDBarcodeScanTypeDocumentsSearchHandler() {
    return new TwoDBarcodeScanTypeDocumentsSearchHandler();
  }

  @Bean(RxConstants.RX_LPN_CACHE_SERVICE)
  public LPNCacheService getRdcLpnCacheService(
      @Qualifier(RxConstants.RX_LPN_SERVICE) LPNService lpnService) {
    LPNCacheServiceInMemoryImpl lpnCacheService = new LPNCacheServiceInMemoryImpl();
    lpnCacheService.setLpnService(lpnService);
    return lpnCacheService;
  }
}
