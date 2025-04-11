package com.walmart.move.nim.receiving.reporting.config;

import com.walmart.move.nim.receiving.core.common.validators.DRUtils;
import com.walmart.move.nim.receiving.core.config.CommonDbConfig;
import com.walmart.move.nim.receiving.core.config.InfraConfig;
import com.walmart.move.nim.receiving.core.config.app.DRConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.strati.configuration.annotation.ManagedConfiguration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@ConditionalOnProperty(value = "enable_wm_datasource", havingValue = "false", matchIfMissing = true)
public class ReceivingDataSourceConfigSecondary {

  @ManagedConfiguration private CommonDbConfig commonDbConfig;

  @Value("${db.pwd.akeyless:}")
  private String dbPwdAkeyless;

  @Value("${db.dr.pwd.akeyless:}")
  private String dbDrPwdAkeyless;

  @ManagedConfiguration private DRConfig drConfig;

  @ManagedConfiguration private InfraConfig infraConfig;

  @Value("${secrets.key}")
  private String secretKey;

  @Bean(name = ReportingConstants.SECONDARY_DATA_SOURCE)
  public DataSource secondaryDataSource() throws Exception {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setDriverClassName(ReceivingConstants.DB_DRIVER);
    DRUtils.getReportingDcDbProp(hikariConfig, secretKey, drConfig, infraConfig, dbPwdAkeyless);
    hikariConfig.setIdleTimeout(commonDbConfig.getHikariIdleTimeoutMsSecondary());
    hikariConfig.setMinimumIdle(commonDbConfig.getHikariMinIdleSecondary());
    hikariConfig.setMaximumPoolSize(commonDbConfig.getHikariMaxPoolSizeSecondary());
    hikariConfig.setAutoCommit(false);
    hikariConfig.setReadOnly(true);
    hikariConfig.setLeakDetectionThreshold(
        commonDbConfig.getHikariLeakDetectionThresholdSecondary());
    return new HikariDataSource(hikariConfig);
  }
}
