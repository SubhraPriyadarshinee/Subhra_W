package com.walmart.move.nim.receiving;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(
    scanBasePackageClasses = Application.class,
    exclude = {MessageSourceAutoConfiguration.class})
@EnableKafka
@ComponentScan(
    basePackages = {
      "com.walmart.move.nim.receiving",
      "com.walmart.atlas",
      "com.walmart.platform.txn.springboot.interceptor"
    })
public class Application extends SpringBootServletInitializer {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.sources(Application.class);
  }
}
