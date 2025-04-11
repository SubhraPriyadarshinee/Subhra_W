package com.walmart.move.nim.receiving.rdc.mock.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.FitProblemTagResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.core.io.ClassPathResource;

public class MockProblemResponse {

  private static Gson gson = new Gson();

  public static FitProblemTagResponse getProlemDetails() throws IOException {
    File resource = new ClassPathResource("MockProblemResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    FitProblemTagResponse fitProblemTagResponse =
        gson.fromJson(mockResponse, FitProblemTagResponse.class);
    return fitProblemTagResponse;
  }

  public static ProblemLabel getMockProblemLabel() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setProblemTagId("5343435");
    problemLabel.setResolutionId("2323-2323-32323");
    String problemResponse =
        "{\"id\":\"0469db26-43bf-4499-9723-7a6244e7e2e0\",\"label\":\"06001969468600\",\"slot\":\"M5019\",\"status\":\"PARTIALLY_RECEIVED\",\"remainingQty\":0,\"reportedQty\":335,\"issue\":{\"id\":\"dffcc688-8550-4196-9387-9d6d3fc4409b\",\"identifier\":\"210820-46422-2559-0000\",\"type\":\"DI\",\"subType\":\"PO_ISSUE\",\"deliveryNumber\":\"95334888\",\"upc\":\"331722632317\",\"itemNumber\":563045609,\"quantity\":335,\"status\":\"ANSWERED\",\"businessStatus\":\"READY_TO_RECEIVE\",\"resolutionStatus\":\"COMPLETE_RESOLUTON\"},\"resolutions\":[{\"id\":\"b0d1719e-c7f9-4f8f-adf8-0237d42ab4c4\",\"provider\":\"Manual\",\"quantity\":335,\"acceptedQuantity\":0,\"rejectedQuantity\":0,\"remainingQty\":335,\"type\":\"RECEIVE_AGAINST_ORIGINAL_LINE\",\"resolutionPoNbr\":\"8458709170\",\"resolutionPoLineNbr\":1,\"state\":\"PARTIAL\"}]}";
    problemLabel.setProblemResponse(problemResponse);
    return problemLabel;
  }
}
