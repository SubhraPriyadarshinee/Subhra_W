/** */
package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.move.nim.receiving.core.config.CommonDbConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.hibernate.cfg.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** @author a0b02ft */
@Primary
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = {"com.walmart.move.nim.receiving.core.repositories"})
@Profile("!test")
public class JPAConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(JPAConfig.class);

  @ManagedConfiguration private CommonDbConfig commonDbConfig;

  @Value("${enable.endgame.app:false}")
  private boolean isEndgameEnabled;

  @Value("${enable.acc.app:false}")
  private boolean isAccEnabled;

  @Value("${enable.rc.app:false}")
  private boolean isRcEnabled;

  @Value("${enable.fixture.app:false}")
  private boolean isFixtureEnabled;

  @Value("${enable.mfc.app:false}")
  private boolean isMFCEnabled;

  @Value("${enable.sib.app:false}")
  private boolean isSIBEnabled;

  @Value("${db.interceptor.enabled:false}")
  private boolean isTracingDbInterceptorEnabled;

  @Autowired private DataSource dataSource;

  @Bean
  @Primary
  public LocalContainerEntityManagerFactoryBean entityManagerFactory() throws Exception {
    LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
    em.setPersistenceUnitName(ReceivingConstants.PRIMARY_PERSISTENCE_UNIT);
    em.setJpaProperties(additionalProperties());
    em.setDataSource(dataSource);
    em.setPackagesToScan(getPackages());
    JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
    em.setJpaVendorAdapter(vendorAdapter);
    return em;
  }

  private String[] getPackages() {
    List<String> entityPackages = new ArrayList<>();
    entityPackages.add("com.walmart.move.nim.receiving.core.entity");

    if (isEndgameEnabled) {
      entityPackages.add("com.walmart.move.nim.receiving.endgame.entity");
    }
    if (isAccEnabled) {
      entityPackages.add("com.walmart.move.nim.receiving.acc.entity");
    }
    if (isRcEnabled) {
      entityPackages.add("com.walmart.move.nim.receiving.rc.entity");
    }
    if (isFixtureEnabled) {
      entityPackages.add("com.walmart.move.nim.receiving.fixture.entity");
    }

    if (isMFCEnabled) {
      entityPackages.add("com.walmart.move.nim.receiving.mfc.entity");
    }

    if (isSIBEnabled) {
      entityPackages.add("com.walmart.move.nim.receiving.sib.entity");
    }

    String[] entityPackageNames = new String[entityPackages.size()];

    for (int i = 0; i <= entityPackages.size() - 1; i++) {
      entityPackageNames[i] = entityPackages.get(i);
    }

    return entityPackageNames;
  }

  @Bean
  @Primary
  public PlatformTransactionManager transactionManager() throws Exception {
    JpaTransactionManager transactionManager = new JpaTransactionManager();
    transactionManager.setEntityManagerFactory(entityManagerFactory().getObject());
    return transactionManager;
  }

  public Properties additionalProperties() {
    Properties properties = new Properties();
    properties.put("hibernate.dialect", ReceivingConstants.HIBERNATE_DIALECT);
    properties.put("hibernate.hbm2ddl.auto", ReceivingConstants.HBM2DDL_AUTO_NONE);
    properties.put("hibernate.generate_statistics", commonDbConfig.isHibernateSQLStatsEnabled());
    properties.put("hibernate.jdbc.batch_size", commonDbConfig.getJdbcBatchSize());
    return properties;
  }

  @Bean
  public LockProvider provider() throws Exception {
    return new JdbcTemplateLockProvider(dataSource);
  }
}
