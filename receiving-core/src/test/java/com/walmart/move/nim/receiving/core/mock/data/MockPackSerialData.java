package com.walmart.move.nim.receiving.core.mock.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PackSerialDetail;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

public class MockPackSerialData {

  public static List<PackSerialDetail> mockPackSerialDataPalletSSCCResponse() throws IOException {
    File resource = new ClassPathResource("gdm_packserial_info_pallet_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return Arrays.asList(new Gson().fromJson(mockResponse, PackSerialDetail[].class));
  }
}
