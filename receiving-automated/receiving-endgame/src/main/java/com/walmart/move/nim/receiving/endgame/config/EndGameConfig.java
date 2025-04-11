package com.walmart.move.nim.receiving.endgame.config;

import com.walmart.move.nim.receiving.core.config.app.KafkaProducerConfig;
import com.walmart.move.nim.receiving.core.event.processor.summary.ReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.core.event.processor.update.DefaultDeliveryProcessor;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.endgame.common.AuditHelper;
import com.walmart.move.nim.receiving.endgame.common.DeliveryHelper;
import com.walmart.move.nim.receiving.endgame.common.DivertAckHelper;
import com.walmart.move.nim.receiving.endgame.common.PrintingAckHelper;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.service.EndGameAsnReceivingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameAttachPOService;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryEventProcessor;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryMetaDataService;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryService;
import com.walmart.move.nim.receiving.endgame.service.EndGameDivertAckEventProcessor;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameManualReceivingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameOsdrProcessor;
import com.walmart.move.nim.receiving.endgame.service.EndGameOsdrService;
import com.walmart.move.nim.receiving.endgame.service.EndGameReceivingHelperService;
import com.walmart.move.nim.receiving.endgame.service.EndGameReceivingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameSlottingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameUnloadingCompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.endgame.service.EndgameContainerService;
import com.walmart.move.nim.receiving.endgame.service.EndgameDecantService;
import com.walmart.move.nim.receiving.endgame.service.EndgameDeliveryStatusPublisher;
import com.walmart.move.nim.receiving.endgame.service.EndgameExpiryDateProcessor;
import com.walmart.move.nim.receiving.endgame.service.EndgameItemUpdateProcessor;
import com.walmart.move.nim.receiving.endgame.service.EndgameManualDeliveryProcessor;
import com.walmart.move.nim.receiving.endgame.service.EndgameReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.endgame.service.NonSortEndgameDeliveryStatusPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@ConditionalOnExpression("${enable.endgame.app:false}")
@Configuration
@Import({KafkaProducerConfig.class})
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.walmart.move.nim.receiving.endgame.repositories")
public class EndGameConfig {

  @Bean(EndgameConstants.ENDGAME_LABELING_SERVICE)
  public EndGameLabelingService labelingService() {
    return new EndGameLabelingService();
  }

  @Bean(EndgameConstants.ENDGAME_SLOTTING_SERVICE)
  public EndGameSlottingService slottingService() {
    return new EndGameSlottingService();
  }

  @Bean(EndgameConstants.ENDGAME_DELIVERY_EVENT_PROCESSOR)
  public EventProcessor deliveryEventProcessor() {
    return new EndGameDeliveryEventProcessor();
  }

  @Bean(EndgameConstants.ENDGAME_MANUAL_DELIVERY_PROCESSOR)
  public EventProcessor endgameManualDeliveryProcessor() {
    return new EndgameManualDeliveryProcessor();
  }

  @Bean(EndgameConstants.ENDGAME_DIVERT_ACK_EVENT_PROCESSOR)
  public EventProcessor scanEvenProcessor() {
    return new EndGameDivertAckEventProcessor();
  }

  @Bean(EndgameConstants.ENDGAME_RECEIVING_SERVICE)
  public EndGameReceivingService endGameReceivingService() {
    return new EndGameReceivingService();
  }

  @Bean(EndgameConstants.ENDGAME_MANUAL_RECEIVING_SERVICE)
  public EndGameReceivingService endGameManualReceivingService() {
    return new EndGameManualReceivingService();
  }

  @Bean(EndgameConstants.ENDGAME_RECEIVING_HELPER_SERVICE)
  public EndGameReceivingHelperService endGameReceivingHelperService() {
    return new EndGameReceivingHelperService();
  }

  @Bean(EndgameConstants.ENDGAME_ASN_RECEIVING_SERVICE)
  public EndGameReceivingService endGameAsnReceivingService() {
    return new EndGameAsnReceivingService();
  }

  @Bean(EndgameConstants.ENDGAME_ATTACH_PO_SERVICE)
  public EndGameAttachPOService endGameAttachPOService() {
    return new EndGameAttachPOService();
  }

  @Bean
  public EndgameContainerService endgameContainerService() {
    return new EndgameContainerService();
  }

  @Bean(EndgameConstants.ENDGAME_ITEM_UPDATE_PROCESSOR)
  public EventProcessor endgameItemUpdateProcessor() {
    return new EndgameItemUpdateProcessor();
  }

  @Bean(EndgameConstants.ENDGAME_EXPIRY_DATE_PROCESSOR)
  public EventProcessor endgameExpiryDateProcessor() {
    return new EndgameExpiryDateProcessor();
  }

  @Bean(EndgameConstants.ENDGAME_DELIVERY_STATUS_EVENT_PROCESSOR)
  @Primary
  public EndgameDeliveryStatusPublisher deliveryStatusEventProcessor() {
    return new EndgameDeliveryStatusPublisher();
  }

  @Bean(EndgameConstants.ENDGAME_OSDR_PROCESSOR)
  public EndGameOsdrProcessor endGameOsdrProcessor() {
    return new EndGameOsdrProcessor();
  }

  @Bean(EndgameConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  public DeliveryMetaDataService deliveryMetaDataService() {
    return new EndGameDeliveryMetaDataService();
  }

  @Bean(EndgameConstants.ENDGAME_RECEIPT_SUMMARY_PROCESSOR)
  public ReceiptSummaryProcessor receiptSummaryProcessor() {
    return new EndgameReceiptSummaryProcessor();
  }

  @Bean(EndgameConstants.ENDGAME_DELIVERY_SERVICE)
  public DeliveryService endGameDeliveryService() {
    return new EndGameDeliveryService();
  }

  @Bean(EndgameConstants.ENDGAME_OSDR_SERIVCE)
  public EndGameOsdrService endGameOsdrService() {
    return new EndGameOsdrService();
  }

  @Bean
  public EndgameDecantService endgameDecantService() {
    return new EndgameDecantService();
  }

  /** configuration for NonSort Endgame DC */
  @Bean(EndgameConstants.NONSORT_ENDGAME_DELIVERY_STATUS_EVENT_PROCESSOR)
  public NonSortEndgameDeliveryStatusPublisher nonSortDeliveryStatusEventProcessor() {
    return new NonSortEndgameDeliveryStatusPublisher();
  }

  @Bean(EndgameConstants.END_GAME_UNLOADING_COMPLETE_DELIVERY_PROCESSOR)
  public EndGameUnloadingCompleteDeliveryProcessor endGameUnloadingCompleteDeliveryProcessor() {
    return new EndGameUnloadingCompleteDeliveryProcessor();
  }

  @Bean(EndgameConstants.DEFAULT_DELIVERY_PROCESSOR)
  public EventProcessor defaultDeliveryProcessor() {
    return new DefaultDeliveryProcessor();
  }

  @Bean
  public DivertAckHelper divertAckHelper() {
    return new DivertAckHelper();
  }

  @Bean
  public AuditHelper auditHelper() {
    return new AuditHelper();
  }

  @Bean
  public PrintingAckHelper printingAckHelper() {
    return new PrintingAckHelper();
  }

  @Bean
  public DeliveryHelper deliveryHelper() {
    return new DeliveryHelper();
  }
}
