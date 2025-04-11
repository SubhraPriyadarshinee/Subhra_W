package com.walmart.move.nim.receiving.rx.mock.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockGdmResponseData {
  private static final Logger LOGGER = LoggerFactory.getLogger(MockGdmResponseData.class);

  private MockGdmResponseData() {}

  private static String getFileAsString(String filePath) {

    try {
      String dataPath = new File(filePath).getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      LOGGER.error("Unable to read file {}", e.getMessage());
    }
    return null;
  }

  public static String getGdmResponse() {
    return getFileAsString(
        "../../uwms-receiving/receiving-api/src/main/resources/gdm.v3" + "/GdmResponse.json");
  }
}
