package com.walmart.move.nim.receiving.base;

import com.walmart.move.nim.receiving.config.AppConfigUT;
import com.walmart.move.nim.receiving.config.CommonBeansUT;
import com.walmart.move.nim.receiving.config.JPAConfigUT;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;

@Import(AopAutoConfiguration.class)
@ContextConfiguration(classes = {AppConfigUT.class, JPAConfigUT.class, CommonBeansUT.class})
public class ReceivingControllerTestBase extends AbstractTestNGSpringContextTests {
  //  static {
  //    System.setProperty("runtime.context.appName", "receiving-api");
  //    System.setProperty("runtime.context.appVersion", "US-WM-STORE-1.0");
  //    System.setProperty("runtime.context.cellName", "dev-cell002");
  //    System.setProperty("runtime.context.cloud", "wcnp");
  //    System.setProperty("runtime.context.environment", "dev");
  //    System.setProperty("runtime.context.environmentName", "dev");
  //    System.setProperty("runtime.context.environmentType", "dev");
  //    System.setProperty("runtime.context.envProfile", "dev");
  //    System.setProperty("tunr.enabled", "false");
  //    System.setProperty("atlas-global-config-bootstrap", "default");
  //  }
}
