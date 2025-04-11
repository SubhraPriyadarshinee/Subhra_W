package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import java.util.Properties;
import javax.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * sample format for sevice availability dashboard
 *
 * <p>{ "status": "UP", "details": { "app": { "name": "GDM_Core", "version": "0.0.99", "dc ": "6938,
 * 6094" (if all dc’s are supported, then just “*” ) }, "server": { "assembly": "prod-gdm", "host":
 * "ms-sf-tomcat_prod-gdm_tomcat_prod_prod-az-southcentralus-3_os-527225856-3", "datacenter":
 * "prod-az-southcentralus-3", "ip": "10.0.0.1" }, "maas": { "status": "UP" }, "db": { "status":
 * "UP", "details": { "database": "Microsoft SQL Server" }, "kafka": { "status": "UP" } } }
 *
 * @author a0s01qi
 */
@RestController
@Tag(name = "Version Service", description = "Find application version from Manifest file")
@Hidden
public class VersionCheckController {

  private static final Logger LOGGER = LoggerFactory.getLogger(VersionCheckController.class);

  @Autowired private ServletContext servletContext;

  @GetMapping("/version")
  @Operation(
      summary = "Returns application maven version",
      description = "This will return a 200 when alive.")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "This is a triumph!")})
  @Counted(name = "versionHitCount", level1 = "uwms-receiving", level2 = "versionCheckController")
  @Timed(name = "versionTimed", level1 = "uwms-receiving", level2 = "versionCheckController")
  @ExceptionCounted(
      name = "versionExceptionCount",
      level1 = "uwms-receiving",
      level2 = "versionCheckController")
  public Properties getAppVersion() {
    Properties prop = new Properties();
    try {
      InputStream is = servletContext.getResourceAsStream("/META-INF/MANIFEST.MF");
      ;
      prop.load(is);
    } catch (Exception exception) {
      LOGGER.error(exception.getMessage(), exception);
      throw new ReceivingInternalException(
          ReceivingException.INVALID_MANIFEST_FILE_CODE,
          ReceivingException.INVALID_MANIFEST_FILE_ERROR);
    }
    return prop;
  }
}
