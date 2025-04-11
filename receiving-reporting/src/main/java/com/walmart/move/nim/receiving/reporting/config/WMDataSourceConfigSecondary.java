package com.walmart.move.nim.receiving.reporting.config;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import com.walmart.platform.data.azuresql.AzureSQLUtil;
import com.walmart.platform.data.azuresql.AzureSqlDriverConfigurations;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Slf4j
@Profile("!test")
@ConditionalOnProperty(value = "enable_wm_datasource", havingValue = "true", matchIfMissing = false)
public class WMDataSourceConfigSecondary {

  @Bean(name = ReportingConstants.SECONDARY_DATA_SOURCE)
  DataSource dataSource(
      @Qualifier("azureSqlDriverConfigurations")
          AzureSqlDriverConfigurations azureSqlDriverConfigurations) {

    String dbUrl =
        azureSqlDriverConfigurations.getUrl()
            + ReceivingConstants.SEMI_COLON
            + ReceivingConstants.APPLICATION_INTENT_READ_ONLY;
    azureSqlDriverConfigurations.setUrl(dbUrl);
    azureSqlDriverConfigurations.setApplicationIntent("ReadOnly");
    try {
      Class.forName(ReceivingConstants.DB_DRIVER);
    } catch (ClassNotFoundException ex) {
      log.error("Driver not found : {}", ex.getMessage());
    }
    return AzureSQLUtil.createDataSource(azureSqlDriverConfigurations);
  }
}
