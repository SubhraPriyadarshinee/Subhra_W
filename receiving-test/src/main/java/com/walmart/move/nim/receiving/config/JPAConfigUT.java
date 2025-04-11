/** */
package com.walmart.move.nim.receiving.config;

import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** @author a0b02ft */
@TestConfiguration
@Import(AppConfigUT.class)
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = {
      "com.walmart.move.nim.receiving.core.repositories",
      "com.walmart.move.nim.receiving.acc.repositories",
      "com.walmart.move.nim.receiving.endgame.repositories",
      "com.walmart.move.nim.receiving.reporting.repositories",
      "com.walmart.move.nim.receiving.rc.repositories",
      "com.walmart.move.nim.receiving.fixture.repositories"
    })
@EnableAutoConfiguration
@Profile("test")
public class JPAConfigUT {

  @Autowired private AppConfigUT appConfig;

  @Bean
  @Primary
  public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
    LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
    em.setPersistenceUnitName("primaryPersistenceUnit");
    em.setJpaProperties(additionalProperties());
    em.setDataSource(dataSource());
    em.setPackagesToScan(
        "com.walmart.move.nim.receiving.core.entity",
        "com.walmart.move.nim.receiving.endgame.entity",
        "com.walmart.move.nim.receiving.acc.entity",
        "com.walmart.move.nim.receiving.rc.entity",
        "com.walmart.move.nim.receiving.fixture.entity");
    JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
    em.setJpaVendorAdapter(vendorAdapter);
    return em;
  }

  @Bean
  public LocalContainerEntityManagerFactoryBean secondaryEntityManagerFactory() {
    LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
    em.setPersistenceUnitName(ReportingConstants.SECONDARY_PERSISTENCE_UNIT);
    em.setJpaProperties(additionalProperties());
    em.setDataSource(dataSource());
    em.setPackagesToScan("com.walmart.move.nim.receiving.core.entity");
    JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
    em.setJpaVendorAdapter(vendorAdapter);
    return em;
  }

  @Bean
  public DataSource dataSource() {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setDriverClassName("org.h2.Driver");
    hikariConfig.setJdbcUrl("jdbc:h2:mem:atlasrecv;DB_CLOSE_DELAY=-1");
    hikariConfig.setUsername("");
    hikariConfig.setPassword("");
    return new HikariDataSource(hikariConfig);
  }

  public Properties additionalProperties() {
    Properties properties = new Properties();
    properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
    properties.put("hibernate.hbm2ddl.auto", "create");
    return properties;
  }
}
