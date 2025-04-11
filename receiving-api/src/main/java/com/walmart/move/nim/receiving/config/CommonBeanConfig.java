package com.walmart.move.nim.receiving.config;

import com.walmart.move.nim.receiving.core.service.DefaultPutOnHoldService;
import com.walmart.move.nim.receiving.core.service.PutOnHoldService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CommonBeanConfig {

  @Primary
  @Bean(name = ReceivingConstants.DEFAULT_PUT_ON_HOLD_SERVICE)
  public PutOnHoldService getDefaultPutOnHoldHandler() {
    return new DefaultPutOnHoldService();
  }
}
