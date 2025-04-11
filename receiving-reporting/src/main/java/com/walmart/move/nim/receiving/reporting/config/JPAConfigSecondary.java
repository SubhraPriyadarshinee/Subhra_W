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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** @author sks0013 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = {"com.walmart.move.nim.receiving.reporting.repositories"},
    entityManagerFactoryRef = ReportingConstants.SECONDARY_ENTITY_MANAGER_FACTORY,
    transactionManagerRef = ReportingConstants.SECONDARY_TRANSACTION_MANAGER)
@Profile("!test")
public class JPAConfigSecondary {

  @ManagedConfiguration private CommonDbConfig commonDbConfig;

  @ManagedConfiguration private DRConfig drConfig;

  @ManagedConfiguration private InfraConfig infraConfig;

  @Value("${secrets.key}")
  private String secretKey;

  @Value("${db.pwd.akeyless:}")
  private String dbPwdAkeyless;

  @Value("${db.dr.pwd.akeyless:}")
  private String dbDrPwdAkeyless;

  @Bean(name = "secondaryEntityManagerFactory")
  public LocalContainerEntityManagerFactoryBean secondaryEntityManagerFactory() throws Exception {
    LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
    em.setPersistenceUnitName(ReportingConstants.SECONDARY_PERSISTENCE_UNIT);
    em.setJpaProperties(additionalProperties());
    em.setDataSource(secondaryDataSource());
    em.setPackagesToScan(getPackages());
    JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
    em.setJpaVendorAdapter(vendorAdapter);
    return em;
  }

  private String[] getPackages() {
    List<String> entityPackages = new ArrayList<>();
    entityPackages.add("com.walmart.move.nim.receiving.core.entity");

    String[] entityPackageNames = new String[entityPackages.size()];

    for (int i = 0; i <= entityPackages.size() - 1; i++) {
      entityPackageNames[i] = entityPackages.get(i);
    }

    return entityPackageNames;
  }

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

  @Bean(name = ReportingConstants.SECONDARY_TRANSACTION_MANAGER)
  public PlatformTransactionManager secondaryTransactionManager() throws Exception {
    JpaTransactionManager secondaryTransactionManager = new JpaTransactionManager();
    secondaryTransactionManager.setEntityManagerFactory(
        secondaryEntityManagerFactory().getObject());
    return secondaryTransactionManager;
  }

  private Properties additionalProperties() {
    Properties properties = new Properties();
    properties.put("hibernate.dialect", ReceivingConstants.HIBERNATE_DIALECT);
    properties.put("hibernate.hbm2ddl.auto", ReceivingConstants.HBM2DDL_AUTO_NONE);
    properties.put("hibernate.generate_statistics", commonDbConfig.isHibernateSQLStatsEnabled());
    properties.put("hibernate.jdbc.batch_size", commonDbConfig.getJdbcBatchSize());
    return properties;
  }
}
