package com.walmart.move.nim.receiving.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/** @author sks0013 */
public class MockReportConfig {
  public static String REPORT_CONFIG;

  private MockReportConfig() {
    // doNothing. Utility classes, which are collections of static members, are not meant to be
    // instantiated.
  }

  static {
    String getPath =
        new File("../receiving-test/src/main/resources/json/ReportConfig.json").getAbsolutePath();
    try {
      REPORT_CONFIG = new String(Files.readAllBytes(Paths.get(getPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
