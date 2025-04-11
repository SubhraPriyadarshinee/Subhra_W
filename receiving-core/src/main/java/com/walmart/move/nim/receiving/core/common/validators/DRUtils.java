package com.walmart.move.nim.receiving.core.common.validators;

import com.walmart.move.nim.receiving.core.common.SecurityUtil;
import com.walmart.move.nim.receiving.core.config.InfraConfig;
import com.walmart.move.nim.receiving.core.config.app.DRConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.zaxxer.hikari.HikariConfig;
import java.util.Map;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DRUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(DRUtils.class);
  /**
   * This class will return database properties
   *
   * @param hikariConfig
   * @param secretKey
   * @param infraConfig
   * @param dbPwdAkeyless
   * @param dbDrPwdAkeyless
   * @return
   * @throws Exception
   */
  public static HikariConfig getDcDbProp(
      HikariConfig hikariConfig,
      String secretKey,
      DRConfig drConfig,
      InfraConfig infraConfig,
      String dbPwdAkeyless,
      String dbDrPwdAkeyless)
      throws Exception {

    if (!ObjectUtils.allNotNull(hikariConfig)) {
      return null;
    }

    if (!(drConfig.isEnableDR() || infraConfig.isEnableDBDR())) {
      hikariConfig.setJdbcUrl(infraConfig.getActiveDBURL());
      hikariConfig.setUsername(infraConfig.getActiveDBUsername());
      hikariConfig.setPassword(
          getDataSourcePwd(infraConfig.getActiveDBPassword(), dbPwdAkeyless, secretKey));
    } else {
      hikariConfig.setJdbcUrl(infraConfig.getDrDBURL());
      hikariConfig.setUsername(infraConfig.getDrDBUsername());
      hikariConfig.setPassword(
          getDataSourcePwd(infraConfig.getDrDBPassword(), dbDrPwdAkeyless, secretKey));
    }

    return hikariConfig;
  }

  public static HikariConfig getReportingDcDbProp(
      HikariConfig hikariConfig,
      String secretKey,
      DRConfig drConfig,
      InfraConfig infraConfig,
      String dbPwdAkeyless)
      throws Exception {

    if (!ObjectUtils.allNotNull(hikariConfig)) {
      return null;
    }

    String dbUrl =
        infraConfig.getActiveDBURL()
            + ReceivingConstants.SEMI_COLON
            + ReceivingConstants.APPLICATION_INTENT_READ_ONLY;

    hikariConfig.setJdbcUrl(dbUrl);
    hikariConfig.setUsername(infraConfig.getActiveDBUsername());
    hikariConfig.setPassword(
        getDataSourcePwd(infraConfig.getActiveDBPassword(), dbPwdAkeyless, secretKey));

    LOGGER.info("Reporting module connected with DB url = {}", dbUrl);

    return hikariConfig;
  }

  public static void setDCHawkeyeSecureKafkaProperties(
      Map<String, Object> property, DRConfig drConfig, InfraConfig infraConfig) {
    if (!(drConfig.isEnableDR() || infraConfig.isEnableAtlasSecureKafkaDR())) {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
          infraConfig.getActiveHawkeyeSecureKafkaBrokers());
    } else {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getDrKafkaOffsetMode());
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getDrHawkeyeSecureKafkaBrokers());
    }
  }

  public static void setDCHawkeyeSecureKafkaPropertiesEUS(
      Map<String, Object> property, DRConfig drConfig, InfraConfig infraConfig) {
    if (!(drConfig.isEnableDR() || infraConfig.isEnableAtlasSecureKafkaDR())) {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
          infraConfig.getActiveHawkeyeSecureKafkaBrokersEUS());
    } else {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getDrKafkaOffsetMode());
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getDrHawkeyeSecureKafkaBrokersEUS());
    }
  }

  public static void setDCHawkeyeSecureKafkaPropertiesSCUS(
      Map<String, Object> property, DRConfig drConfig, InfraConfig infraConfig) {
    if (!(drConfig.isEnableDR() || infraConfig.isEnableAtlasSecureKafkaDR())) {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
          infraConfig.getActiveHawkeyeSecureKafkaBrokersSCUS());
    } else {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getDrKafkaOffsetMode());
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
          infraConfig.getDrHawkeyeSecureKafkaBrokersSCUS());
    }
  }

  public static void setDCAtlasKafkaProperties(
      Map<String, Object> property, DRConfig drConfig, InfraConfig infraConfig) {
    setDCAtlasKafkaBrokers(property, drConfig, infraConfig);
    if (!(drConfig.isEnableDR() || infraConfig.isEnableAtlasKafkaDR())) {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
    } else {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getDrKafkaOffsetMode());
    }
  }

  public static void setDCAtlasKafkaBrokers(
      Map<String, Object> property, DRConfig drConfig, InfraConfig infraConfig) {
    if (!(drConfig.isEnableDR() || infraConfig.isEnableAtlasKafkaDR())) {
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getActiveAtlasKafkaBrokers());
    } else {
      property.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getDrAtlasKafkaBrokers());
    }
  }

  public static void setAtlasSecureKafkaProperties(
      Map<String, Object> property, DRConfig drConfig, InfraConfig infraConfig) {
    setAtlasSecureKafkaBrokers(property, drConfig, infraConfig);
    if (!(drConfig.isEnableDR() || infraConfig.isEnableAtlasSecureKafkaDR())) {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
    } else {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getDrKafkaOffsetMode());
    }
  }

  public static void setAtlasSecureKafkaBrokers(
      Map<String, Object> property, DRConfig drConfig, InfraConfig infraConfig) {
    if (!(drConfig.isEnableDR() || infraConfig.isEnableAtlasSecureKafkaDR())) {
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getActiveSecureAtlasKafkaBrokers());
    } else {
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getDrSecureAtlasKafkaBrokers());
    }
  }

  private static String getDataSourcePwd(String dbPwd, String dbPwdAkeyless, String secretKey)
      throws Exception {
    if (StringUtils.isEmpty(dbPwdAkeyless)) {
      return SecurityUtil.decryptValue(secretKey, dbPwd);
    }
    return dbPwdAkeyless;
  }
  /**
   * Setting EI kafka brokers
   *
   * @param property
   * @param drConfig
   * @param infraConfig
   */
  public static void setEIKafkaBrokers(
      Map<String, Object> property, DRConfig drConfig, InfraConfig infraConfig) {
    if (drConfig.isEnableDR()) {
      property.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getEiDRKafkaBrokers());
    } else {
      property.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getEiActiveKafkaBrokers());
    }
  }

  public static void setDCFireflySecureKafkaProperties(
      Map<String, Object> property, DRConfig drConfig, InfraConfig infraConfig) {
    if (!(drConfig.isEnableDR() || infraConfig.isEnableFireflySecureKafkaDR())) {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
          infraConfig.getActiveFireflySecureKafkaBrokers());
    } else {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getDrKafkaOffsetMode());
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getDrFireflySecureKafkaBrokers());
    }
  }

  public static void setNGRSecureKafkaProperties(
      Map<String, Object> property, DRConfig drConfig, InfraConfig infraConfig) {
    if (!(drConfig.isEnableDR() || infraConfig.isEnableNgrSecureKafkaDR())) {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getActiveNgrSecureKafkaBrokers());
    } else {
      property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getDrKafkaOffsetMode());
      property.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getDrNgrSecureKafkaBrokers());
    }
  }
}
