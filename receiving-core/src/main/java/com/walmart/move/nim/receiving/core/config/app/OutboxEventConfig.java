package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.atlas.global.fc.service.OutboxPublisherService;
import com.walmart.platform.repositories.OutboxEventSinkRepository;
import com.walmart.platform.repositories.OutboxEventSinkRepositoryImpl;
import com.walmart.platform.service.OutboxEventSinkService;
import com.walmart.platform.service.OutboxEventSinkServiceImpl;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Slf4j
@Configuration
public class OutboxEventConfig {

  @Bean
  public OutboxEventSinkRepository outboxEventSinkRepository(
      NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
    return new OutboxEventSinkRepositoryImpl(namedParameterJdbcTemplate);
  }

  @Bean
  public OutboxEventSinkService outboxEventSinkService(
      OutboxEventSinkRepository outboxEventSinkRepositoryImpl) {
    return new OutboxEventSinkServiceImpl(outboxEventSinkRepositoryImpl);
  }

  @Bean
  public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }

  @Bean
  public IOutboxPublisherService getOutboxPublisherService(
      OutboxEventSinkService outboxEventSinkService) {
    return new OutboxPublisherService(outboxEventSinkService);
  }
}
