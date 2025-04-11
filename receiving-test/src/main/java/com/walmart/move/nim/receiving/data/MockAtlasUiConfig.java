package com.walmart.move.nim.receiving.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MockAtlasUiConfig {
  public static String ATLAS_UI_CONFIG;

  private MockAtlasUiConfig() {
    // doNothing. Utility classes, which are collections of static members, are not meant to be
    // instantiated.
  }

  static {
    String getPath =
        new File("../receiving-test/src/main/resources/json/AtlasUIConfig.json").getAbsolutePath();
    try {
      ATLAS_UI_CONFIG = new String(Files.readAllBytes(Paths.get(getPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
