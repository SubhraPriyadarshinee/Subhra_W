package com.walmart.move.nim.receiving.core.mock.data;

import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MockACLNotificationSentUsingMQTT {

  private static String getFileAsString(String filePath) {

    try {
      String dataPath = new File(filePath).getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getACLNotificationPublishedUsingMqtt() {
    return getFileAsString(
        "../receiving-test/src/main/resources/" + "json/ACLNotificationPublishedUsingMqtt.json");
  }
}
