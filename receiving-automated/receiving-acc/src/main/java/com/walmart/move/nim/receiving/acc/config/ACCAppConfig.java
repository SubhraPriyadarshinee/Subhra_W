package com.walmart.move.nim.receiving.acc.config;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.job.AccSchedulerJobs;
import com.walmart.move.nim.receiving.acc.message.publisher.JMSDeliveryLinkPublisher;
import com.walmart.move.nim.receiving.acc.message.publisher.JMSLabelDataPublisher;
import com.walmart.move.nim.receiving.acc.message.publisher.KafkaDockTagInfoPublisher;
import com.walmart.move.nim.receiving.acc.message.publisher.KafkaSorterPublisher;
import com.walmart.move.nim.receiving.acc.service.*;
import com.walmart.move.nim.receiving.acc.util.PrintableUtils;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * ACC receiving application config goes here
 *
 * @author sitakant
 */
@ConditionalOnExpression("${enable.acc.app:false}")
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.walmart.move.nim.receiving.acc.repositories")
public class ACCAppConfig {

  @Bean(ACCConstants.ACC_DELIVERY_EVENT_PROCESSOR)
  public EventProcessor deliveryEventProcessor() {
    return new ACCDeliveryEventProcessor();
  }

  @Bean
  public UserLocationService userLocationService() {
    return new UserLocationService();
  }

  @Bean
  public ACLService aclService() {
    return new ACLService();
  }

  @Bean
  public HawkeyeLpnSwapService hawkeyeLpnSwapService() {
    return new HawkeyeLpnSwapService();
  }

  @Bean
  public UpdateUserLocationService updateUserLocationService() {
    return new UpdateUserLocationService();
  }

  @Bean(ReceivingConstants.JMS_DELIVERY_LINK_PUBLISHER)
  public JMSDeliveryLinkPublisher jmsDeliveryLinkPublisher() {
    return new JMSDeliveryLinkPublisher();
  }

  @Bean(ReceivingConstants.ACC_NOTIFICATION_SERVICE)
  public ACLNotificationService aclNotificationService() {
    return new ACLNotificationService();
  }

  @Bean
  @Conditional(EnableInPrimaryRegionNodeCondition.class)
  public AccSchedulerJobs accSchedulerJobs() {
    return new AccSchedulerJobs();
  }

  @Bean
  public PreLabelDeliveryService preLabelDeliveryService() {
    return new PreLabelDeliveryService();
  }

  @Bean
  public GenericLabelingService genericLabelingService() {
    return new GenericLabelingService();
  }

  @Bean(ReceivingConstants.GENERIC_LABEL_GENERATOR_SERVICE)
  public GenericLabelGeneratorService genericLabelGeneratorService() {
    return new GenericLabelGeneratorService();
  }

  @Bean(ReceivingConstants.HAWK_EYE_LABEL_GENERATOR_SERVICE)
  public GenericLabelGeneratorService hawkEyeLabelGeneratorService() {
    return new HawkEyeLabelGeneratorService();
  }

  @Bean
  public RetryableDeliveryService retryableDeliveryService() {
    return new RetryableDeliveryService();
  }

  @Bean(ACCConstants.ACC_VERIFICATION_PROCESSOR)
  public ACLVerificationProcessor aclVerificationService() {
    return new ACLVerificationProcessor();
  }

  @Bean(ACCConstants.HAWKEYE_LPN_SWAP_PROCESSOR)
  public HawkeyeLPNSwapProcessor hawkeyeLpnSwapProcessor() {
    return new HawkeyeLPNSwapProcessor();
  }

  @Bean(ACCConstants.ACC_NOTIFICATION_PROCESSOR)
  public ACLNotificationProcessor aclNotificationProcessor() {
    return new ACLNotificationProcessor();
  }

  @Bean(ACCConstants.ACC_INSTRUCTION_SERVICE)
  public InstructionService accInstructionService() {
    return new ACCInstructionService();
  }

  @Bean
  public LPNReceivingService lpnReceivingService() {
    return new LPNReceivingService();
  }

  @Bean(ReceivingConstants.ACC_DELIVERY_METADATA_SERVICE)
  public ACCDeliveryMetaDataService accDeliveryMetaDataService() {
    return new ACCDeliveryMetaDataService();
  }

  @Bean(ACCConstants.ACC_DOCK_TAG_SERVICE)
  public DockTagService accDockTagService() {
    return new ACCDockTagService();
  }

  @Bean(ReceivingConstants.ACC_ITEM_CATALOG_SERVICE)
  public AccItemCatalogService accItemCatalogService() {
    return new AccItemCatalogService();
  }

  @Bean(ACCConstants.ACC_REPORT_SERVICE)
  public ReportService accReportService() {
    return new ACCReportService();
  }

  @Bean(ACCConstants.ACC_ASYNC_LABEL_PERSISTER_SERVICE)
  public AsyncLabelPersisterService accAsyncLabelPersisterService() {
    return new AsyncLabelPersisterService();
  }

  @Bean(ACCConstants.ACC_FACILITY_MDM_SERVICE)
  public FacilityMDM accFacilityMDMService() {
    return new FacilityMDMImpl();
  }

  @Bean
  public PrintableUtils labellingUtils() {
    return new PrintableUtils();
  }

  @Bean
  public LabelingHelperService labelingHelperService() {
    return new LabelingHelperService();
  }

  @Bean(ReceivingConstants.JMS_LABEL_DATA_PUBLISHER)
  public JMSLabelDataPublisher jmsLabelDataPublisher() {
    return new JMSLabelDataPublisher();
  }

  @Bean(ACCConstants.ACL_DELIVERY_LINK_SERVICE)
  public ACLDeliveryLinkService aclDeliveryLinkService() {
    return new ACLDeliveryLinkService();
  }

  @Bean(ACCConstants.HAWK_EYE_DELIVERY_LINK_SERVICE)
  public HawkEyeDeliveryLinkService hawkEyeDeliveryLinkService() {
    return new HawkEyeDeliveryLinkService();
  }

  @Bean(ACCConstants.HAWK_EYE_SERVICE)
  public HawkEyeService hawkEyeService() {
    return new HawkEyeService();
  }

  @Bean(ACCConstants.KAFKA_SORTER_PUBLISHER)
  public KafkaSorterPublisher kafkaSorterPublisher() {
    return new KafkaSorterPublisher();
  }

  @Bean(ACCConstants.ACC_ITEM_UPDATE_PROCESSOR)
  public ACCItemUpdateProcessor accItemUpdateProcessor() {
    return new ACCItemUpdateProcessor();
  }

  @Bean(ACCConstants.ACC_ITEM_UPDATE_SERVICE)
  public ACCItemUpdateService accItemUpdateService() {
    return new ACCItemUpdateService();
  }

  @ConditionalOnExpression("${robo.depal.feature.enabled:false}")
  @Bean(ReceivingConstants.KAFKA_DOCKTAG_INFO_PUBLISHER)
  public KafkaDockTagInfoPublisher kafkaDocktagPublisher() {
    return new KafkaDockTagInfoPublisher();
  }

  @ConditionalOnExpression("${robo.depal.feature.enabled:false}")
  @Bean(ACCConstants.ROBO_DEPAL_EVENT_PROCESSOR)
  public RoboDepalEventProcessor roboDepalEventProcessor() {
    return new RoboDepalEventProcessor();
  }

  @ConditionalOnExpression("${robo.depal.feature.enabled:false}")
  @Bean(ACCConstants.ROBO_DEPAL_EVENT_SERVICE)
  public RoboDepalEventService roboDepalEventService() {
    return new RoboDepalEventService();
  }

  @Bean(ACCConstants.OVERFLOW_LPN_RECEIVING_SERVICE)
  public OverflowLPNReceivingService overflowLPNReceivingService() {
    return new OverflowLPNReceivingService();
  }
}
