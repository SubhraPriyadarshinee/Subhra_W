package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.move.nim.receiving.core.common.validators.DRUtils;
import com.walmart.move.nim.receiving.core.config.CommonDbConfig;
import com.walmart.move.nim.receiving.core.config.InfraConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.strati.configuration.annotation.ManagedConfiguration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(value = "enable_wm_datasource", havingValue = "false", matchIfMissing = true)
public class ReceivingDataSourceConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReceivingDataSourceConfig.class);

  @Value("${secrets.key}")
  private String secretKey;

  @Value("${db.pwd.akeyless:}")
  private String dbPwdAkeyless;

  @Value("${db.dr.pwd.akeyless:}")
  private String dbDrPwdAkeyless;

  @ManagedConfiguration private CommonDbConfig commonDbConfig;

  @ManagedConfiguration private InfraConfig infraConfig;

  @ManagedConfiguration private DRConfig drConfig;

  @Bean
  @Primary
  public DataSource dataSource() throws Exception {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig =
        DRUtils.getDcDbProp(
            hikariConfig, secretKey, drConfig, infraConfig, dbPwdAkeyless, dbDrPwdAkeyless);
    LOGGER.info("Atlas DB Connection: JDBC URL:{};", hikariConfig.getJdbcUrl());
    hikariConfig.setDriverClassName(ReceivingConstants.DB_DRIVER);
    hikariConfig.setIdleTimeout(commonDbConfig.getHikariIdleTimeoutMs());
    hikariConfig.setMinimumIdle(commonDbConfig.getHikariMinIdle());
    hikariConfig.setMaximumPoolSize(commonDbConfig.getHikariMaxPoolSize());
    hikariConfig.setAutoCommit(false);
    hikariConfig.setLeakDetectionThreshold(commonDbConfig.getHikariLeakDetectionThreshold());
    return new HikariDataSource(hikariConfig);
  }
}
