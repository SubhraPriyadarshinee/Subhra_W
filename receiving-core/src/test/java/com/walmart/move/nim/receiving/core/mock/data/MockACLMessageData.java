package com.walmart.move.nim.receiving.core.mock.data;

import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MockACLMessageData {

  private static String getFileAsString(String filePath) {

    try {
      String dataPath = new File(filePath).getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getBuildContainerRequestForACLReceiving() {
    return getFileAsString(
        "../receiving-test/src/main/resources/" + "json/FDERequestForACLReceiving.json");
  }

  public static String getBuildContainerResponseForACLReceiving() {
    return getFileAsString(
        "../receiving-test/src/main/resources/" + "json/FDEResponseForACLReceiving.json");
  }

  public static String getDeliveryDocuments() {
    return getFileAsString("../receiving-test/src/main/resources/" + "json/DeliveryDocuments.json");
  }

  public static String getMockInstruction() {
    return getFileAsString("../receiving-test/src/main/resources/" + "json/MockInstruction.json");
  }
}
