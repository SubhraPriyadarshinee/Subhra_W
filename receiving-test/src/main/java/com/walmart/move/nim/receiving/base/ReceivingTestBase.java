package com.walmart.move.nim.receiving.base;

import com.walmart.atlas.global.config.AtlasGlobalConfigInitializer;
import com.walmart.move.nim.receiving.config.AppConfigUT;
import com.walmart.move.nim.receiving.config.CommonBeansUT;
import com.walmart.move.nim.receiving.config.JMSAppConfigUT;
import com.walmart.move.nim.receiving.config.JPAConfigUT;
import com.walmart.move.nim.receiving.helper.JsonSchemaValidator;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;

@ContextConfiguration(
    classes = {JPAConfigUT.class, JMSAppConfigUT.class, AppConfigUT.class, CommonBeansUT.class},
    initializers = {AtlasGlobalConfigInitializer.class})
@ActiveProfiles("test")
@EnableKafka
public class ReceivingTestBase extends AbstractTestNGSpringContextTests {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReceivingTestBase.class);

  static {
    System.setProperty("runtime.context.appName", "receiving-api");
    System.setProperty("runtime.context.appVersion", "INTL-FC-NON-PROD-1.0");
    System.setProperty("runtime.context.cellName", "dev-cell032");
    System.setProperty("runtime.context.cloud", "wcnp");
    System.setProperty("runtime.context.environment", "dev");
    System.setProperty("runtime.context.environmentName", "dev");
    System.setProperty("runtime.context.environmentType", "dev");
    System.setProperty("runtime.context.envProfile", "dev");
    System.setProperty("tunr.enabled", "false");
    System.setProperty("runtime.context.system.property.override.enabled", "true");
    System.setProperty("scm.snapshot.enabled", "true");
    System.setProperty("scm.root.dir", "/tmp/scm");
    System.setProperty("scm.print.summary.onchange", "true");
    System.setProperty("scm.print.detailed.summary", "true");
    System.setProperty("spring.config.use-legacy-processing", "true");
    System.setProperty(
        "spring.config.additional-location", "/etc/secrets/receiving-secrets.properties");
    System.setProperty("stageName", "dev");
    System.setProperty("platform", "wcnp");
    System.setProperty("spring.main.allow-circular-references", "true");
    System.setProperty("scm.scope.template", "/dev/dev");
  }

  public boolean validateContract(String jsonSchema, String jsonMessage) {
    return JsonSchemaValidator.validateContract(jsonSchema, jsonMessage);
  }

  public File readFile(String fileName) {
    ClassLoader classLoader = getClass().getClassLoader();

    URL resource = classLoader.getResource(fileName);
    if (resource == null) {
      throw new IllegalArgumentException("file is not found!");
    } else {
      return new File(resource.getFile());
    }
  }

  /**
   * @param filePath
   * @return
   */
  public static String getFileAsString(String filePath) {
    try {
      final File file = new File(filePath);
      String dataPath = file.getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {

      LOGGER.error("Unable to read file {}", e.getMessage());
    }
    return null;
  }

  public static String readFileFromCp(String fileName) {
    try {
      File resource = null;
      resource = new ClassPathResource(fileName).getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));
      return mockResponse;
    } catch (Exception e) {
      LOGGER.error("Unable to read file {}, errorMessage={}", fileName, e.getMessage());
    }
    return "";
  }
}
