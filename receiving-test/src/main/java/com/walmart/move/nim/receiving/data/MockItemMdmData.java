package com.walmart.move.nim.receiving.data;

import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MockItemMdmData {
  public static String getMockItemMdmData(String path) {
    String mdmResponse = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      mdmResponse = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return mdmResponse;
  }
}
