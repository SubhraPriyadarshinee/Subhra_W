/** */
package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.atlas.global.config.data.infra.IBMMQConnectionProperties;
import com.walmart.atlas.global.config.service.AtlasConfigServiceFactory;
import com.walmart.atlas.global.config.service.AtlasConfigurationService;
import com.walmart.atlas.global.config.service.AtlasInfraConfigurationService;
import com.walmart.move.nim.receiving.core.common.ExtendedIBMMQNativeConfigHandler;
import com.walmart.move.nim.receiving.core.common.SecurityUtil;
import com.walmart.move.nim.receiving.core.config.InfraConfig;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.messaging.jms.AxonConnectionFactory;
import io.strati.messaging.jms.MessagingJMSService;
import io.strati.messaging.jms.spi.AxonMessagingJMSServiceImpl;
import java.util.Properties;
import javax.jms.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsTemplate;

/**
 * JMS Bean configurations
 *
 * @author sitakant
 */

/**
 * The defaultLockAtMostFor parameter specifies the default amount of time the lock should be kept
 * in case the executing node dies. It uses the ISO8601 Duration format. In this case it means
 * 0seconds
 */
@Configuration
@EnableJms
@Profile("!test")
public class JMSAppConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(JMSAppConfig.class);

  @ManagedConfiguration private DRConfig drConfig;

  @ManagedConfiguration private InfraConfig infraConfig;

  @Value("${secrets.key}")
  private String secretKey;

  @Value("${tunr.enabled:false}")
  private boolean tunrEnabled;

  @Bean
  public MessagingJMSService messagingJMSService() {
    return new AxonMessagingJMSServiceImpl();
  }
  /**
   * Instantiating the adapter for connection factory using the instance of
   * UserCredentialsConnectionFactoryAdapter
   *
   * @return userCredentialsConnectionFactoryAdapter
   * @throws Exception
   */
  @Bean
  public UserCredentialsConnectionFactoryAdapter userCredentialsConnectionFactoryAdapter()
      throws Exception {
    UserCredentialsConnectionFactoryAdapter connectionFactoryAdapter =
        new UserCredentialsConnectionFactoryAdapter();

    if (tunrEnabled) {
      IBMMQConnectionProperties configurationProperties =
          (IBMMQConnectionProperties)
              AtlasConfigServiceFactory.getInstance()
                  .getService(AtlasInfraConfigurationService.class)
                  .getMaasPropertiesConfig()
                  .getConfigProperties();
      Properties clientProperties = configurationProperties.getClientProperties();
      Properties nativeProperties = configurationProperties.getNativeProperties();
      com.walmart.atlas.global.config.data.DRConfig atlasDrConfig =
              AtlasConfigServiceFactory.getInstance()
                      .getService(AtlasConfigurationService.class)
                      .getDrConfig();
      Properties brokerProperties = (atlasDrConfig != null && !atlasDrConfig.isPrimaryRegionActive())
              ? configurationProperties.getBrokerPropertiesSecondary()
              : configurationProperties.getBrokerPropertiesPrimary();
      connectionFactoryAdapter.setTargetConnectionFactory(
          messagingJMSService()
              .getConnectionFactory(clientProperties, brokerProperties, nativeProperties));
    } else {
      if (!(drConfig.isEnableDR() || infraConfig.isEnableMaaSDR())) {
        connectionFactoryAdapter.setUsername(infraConfig.getActiveMaaSUsername());
        connectionFactoryAdapter.setPassword(
            SecurityUtil.decryptValue(secretKey, infraConfig.getActiveMaaSPassword()));
        connectionFactoryAdapter.setTargetConnectionFactory(
            messagingJMSService().getConnectionFactory(infraConfig.getActiveMaaSCCMName()));
        LOGGER.info(
            "Atlas MaaS Connection: CCM:{}; DREnabled:{}",
            infraConfig.getActiveMaaSCCMName(),
            drConfig.isEnableDR() || infraConfig.isEnableMaaSDR());
      } else {
        connectionFactoryAdapter.setUsername(infraConfig.getDrMaaSUsername());
        connectionFactoryAdapter.setPassword(
            SecurityUtil.decryptValue(secretKey, infraConfig.getDrMaaSPassword()));
        connectionFactoryAdapter.setTargetConnectionFactory(
            messagingJMSService().getConnectionFactory(infraConfig.getDrMaaSCCMName()));
        LOGGER.info(
            "Atlas MaaS Connection: CCM:{}; DREnabled:{}",
            infraConfig.getDrMaaSCCMName(),
            drConfig.isEnableDR() || infraConfig.isEnableMaaSDR());
      }
    }

    return connectionFactoryAdapter;
  }

  /**
   * Creating the instance for CachingConnectionFactory
   *
   * @return connectionFactory
   */
  @Bean
  public CachingConnectionFactory connectionFactory(
      @Value("${jms.session.cache.size}") int jmsSessionCacheSize,
      UserCredentialsConnectionFactoryAdapter userCredentialsConnectionFactoryAdapter)
      throws Exception {
    CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();

    if (infraConfig.isEnableAtlasMaaSSecure()) {
      // Establishing Secure MQ Connection through SSL
      ConnectionFactory secureConnectionFactory = null;

      if (!(drConfig.isEnableDR() || infraConfig.isEnableMaaSDR())) {
        secureConnectionFactory =
            messagingJMSService().getConnectionFactory(infraConfig.getActiveSecureMaaSCCMName());
      } else {
        secureConnectionFactory =
            messagingJMSService().getConnectionFactory(infraConfig.getDrMaaSSecureCCMName());
      }
      ((AxonConnectionFactory) secureConnectionFactory)
          .registerNativeConfigHandler(
              "com.ibm.mq.jms.MQConnectionFactory",
              new ExtendedIBMMQNativeConfigHandler(
                  infraConfig.getActiveMaaSKeyStoreLocation(),
                  infraConfig.getActiveMaaSKeyStorePassword(),
                  secretKey));
      cachingConnectionFactory.setTargetConnectionFactory(secureConnectionFactory);

      LOGGER.info(
          "Atlas MaaS Secure Connection: CCM:{}; DREnabled:{}",
          infraConfig.getActiveSecureMaaSCCMName(),
          drConfig.isEnableDR() || infraConfig.isEnableMaaSDR());
    } else {
      // Establishing MQ Connection through User Credentials Authentication
      cachingConnectionFactory.setTargetConnectionFactory(userCredentialsConnectionFactoryAdapter);
    }

    cachingConnectionFactory.setReconnectOnException(true);
    cachingConnectionFactory.setSessionCacheSize(jmsSessionCacheSize);

    return cachingConnectionFactory;
  }

  /**
   * This is to accommodate topic publish.
   *
   * @return will create a bean of {@link JmsTemplate}
   */
  @Bean
  public JmsTemplate jmsTopicTemplate(CachingConnectionFactory cachingConnectionFactory) {
    JmsTemplate jmsTemplate = new JmsTemplate(cachingConnectionFactory);
    jmsTemplate.setPubSubDomain(Boolean.TRUE);
    return jmsTemplate;
  }

  /**
   * This is to accommodate queue publishing.
   *
   * @return will create a bean of {@link JmsTemplate}
   */
  @Bean
  public JmsTemplate jmsQueueTemplate(CachingConnectionFactory cachingConnectionFactory) {
    JmsTemplate jmsTemplate = new JmsTemplate(cachingConnectionFactory);
    jmsTemplate.setPubSubDomain(Boolean.FALSE);
    return jmsTemplate;
  }
}
