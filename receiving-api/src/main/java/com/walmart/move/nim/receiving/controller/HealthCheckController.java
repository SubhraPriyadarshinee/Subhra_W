package com.walmart.move.nim.receiving.controller;

import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.HealthCheckUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@Tag(
    name = "Heartbeat Service",
    description =
        "Check if the applciation is alive. This is used by k8's to track application health.")
@Hidden
public class HealthCheckController {
  private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckController.class);

  @Autowired HealthCheckUtils healthCheckUtils;

  JsonObject serviceAvailabilityData = new JsonObject();
  private Map<Integer, String> map = new HashMap<>();

  @PostConstruct
  public void init() throws IOException {
    fillMap(1, "1kb.json");
    fillMap(10, "10kb.json");
    fillMap(50, "50kb.json");
    fillMap(100, "100kb.json");
    fillMap(1000, "1000kb.json");
  }

  private void fillMap(Integer key, String fileName) throws IOException {
    File resource = new ClassPathResource("perf-test-data/" + fileName).getFile();
    String content = new String(Files.readAllBytes(resource.toPath()));
    map.put(key, content);
  }

  public HealthCheckController() {
    serviceAvailabilityData.addProperty("status", "UP");
    serviceAvailabilityData.add("details", new JsonObject());
    JsonObject appDetails = new JsonObject();
    // this to be made dynamic from appconfig
    appDetails.addProperty("name", "receiving-api");
    appDetails.addProperty("version", "latest");
    appDetails.addProperty("dc", "*");

    serviceAvailabilityData.get("details").getAsJsonObject().add("app", appDetails);
    serviceAvailabilityData.add("db", new JsonObject());
    serviceAvailabilityData.add("maas", new JsonObject());
    serviceAvailabilityData.get("db").getAsJsonObject().addProperty("status", "UP");
    serviceAvailabilityData.get("maas").getAsJsonObject().addProperty("status", "UP");
  }

  @GetMapping("/heartbeat")
  @Operation(
      summary = "Return a health check response",
      description = "This will return a 200 when alive.")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "This is a triumph!")})
  @Counted(name = "heartBeatHitCount", level1 = "uwms-receiving", level2 = "healthCheckController")
  @Timed(name = "heartBeatTimed", level1 = "uwms-receiving", level2 = "healthCheckController")
  @ExceptionCounted(
      name = "heartBeatExceptionCount",
      level1 = "uwms-receiving",
      level2 = "healthCheckController")
  public String getHeartBeat() throws ReceivingException {
    try {
      healthCheckUtils.checkDatabaseHealth();
    } catch (Exception e) {
      throw new ReceivingException("Oops!,something went wrong", HttpStatus.SERVICE_UNAVAILABLE);
    }
    return "This is a triumph! Java runtime Version: " + System.getProperty("java.version");
  }

  @GetMapping(path = "/heartbeat1/{size}/{randomVal}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getHeartBeat1(
      @PathVariable(value = "size", required = true) Integer size,
      @PathVariable(value = "randomVal", required = true) Integer randomVal) {
    long startTime = System.currentTimeMillis();
    ResponseEntity<String> response = null;
    if (map.get(size) == null) {
      response =
          new ResponseEntity<>(
              "Try with one of the following 1,10,50,100,1000.", HttpStatus.BAD_REQUEST);
    } else {
      String str = map.get(size);
      str = "random_value=" + randomVal + str;
      response = new ResponseEntity<>(str, HttpStatus.OK);
    }
    LOGGER.info("LatencyCheck timeTakenInEntireFlow={}", System.currentTimeMillis() - startTime);
    return response;
  }

  /**
   * this method is useful in testing LB etc in case it does automatic status code translation. L7
   * LB does it. also useful in testing HA
   *
   * @param code
   * @return
   */
  @GetMapping(path = "/getcode/{code}")
  public ResponseEntity<String> getCode(
      @PathVariable(value = "code", required = true) Integer code) {
    return new ResponseEntity<>("Here is what you asked for", HttpStatus.valueOf(code));
  }
}
