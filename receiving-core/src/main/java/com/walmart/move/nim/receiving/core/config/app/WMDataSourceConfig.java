package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.data.azuresql.AzureSQLUtil;
import com.walmart.platform.data.azuresql.AzureSqlDriverConfigurations;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "enable_wm_datasource", havingValue = "true", matchIfMissing = false)
public class WMDataSourceConfig {

  @Bean(name = "azureSqlDriverConfigurations")
  @Scope("prototype")
  @ConfigurationProperties("wm.spring.datasource")
  public AzureSqlDriverConfigurations azureSqlDriverConfigurations() {
    return new AzureSqlDriverConfigurations();
  }

  @Bean(name = "dataSource")
  @Primary
  DataSource dataSource(
      @Qualifier("azureSqlDriverConfigurations")
          AzureSqlDriverConfigurations azureSqlDriverConfigurations) {
    log.info(
        "Initializing datasource through Walmart Azure SQL Library with configurations : {}",
        azureSqlDriverConfigurations.toString());

    try {
      Class.forName(ReceivingConstants.DB_DRIVER);
    } catch (ClassNotFoundException ex) {
      log.error("Driver not found : {}", ex.getMessage());
    }
    return AzureSQLUtil.createDataSource(azureSqlDriverConfigurations);
  }
}
