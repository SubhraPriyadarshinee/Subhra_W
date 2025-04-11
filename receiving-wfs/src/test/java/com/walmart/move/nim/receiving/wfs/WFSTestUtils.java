package com.walmart.move.nim.receiving.wfs;

import com.walmart.move.nim.receiving.core.model.ScannedData;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WFSTestUtils {
  public static String getJSONStringResponse(String path) {
    String payload = null;
    try {
      String filePath = new File(path).getCanonicalPath();
      payload = new String(Files.readAllBytes(Paths.get(filePath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (Objects.nonNull(payload)) {
      return payload;
    }
    return null;
  }

  public static List<ScannedData> getScannedDataList() {
    List<ScannedData> scannedDataList = new ArrayList();

    ScannedData scannedData1 = new ScannedData();
    scannedData1.setKey("GTIN");
    scannedData1.setApplicationIdentifier("01");
    scannedData1.setValue("00815489023378"); // GTIN == UpcNumber

    ScannedData scannedData2 = new ScannedData();
    scannedData2.setKey("PO");
    scannedData2.setApplicationIdentifier("400");
    scannedData2.setValue("7868521124");

    scannedDataList.add(scannedData1);
    scannedDataList.add(scannedData2);
    return scannedDataList;
  }
}
