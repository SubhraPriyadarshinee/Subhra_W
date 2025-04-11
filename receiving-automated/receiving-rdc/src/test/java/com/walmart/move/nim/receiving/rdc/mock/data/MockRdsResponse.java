package com.walmart.move.nim.receiving.rdc.mock.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Error;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

public class MockRdsResponse {

  private static final Gson gson = new Gson();

  public static RdsReceiptsResponse getRdsSuccessResponseForSinglePoAndPoLine() throws IOException {
    File resource = new ClassPathResource("RdsSuccessResponseForSinglePoAndPoLine.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    RdsReceiptsResponse rdsReceiptsResponse =
        (gson.fromJson(mockResponse, RdsReceiptsResponse.class));
    return rdsReceiptsResponse;
  }

  public static RdsReceiptsResponse getRdsErrorResponseForSinglePoAndPoLine() {
    List<Error> errorList = new ArrayList<>();
    RdsReceiptsResponse rdsReceiptsResponse = new RdsReceiptsResponse();
    Error error = new Error();
    error.setPoNumber("8458708162");
    error.setPoLine(1);
    error.setErrorCode("NIMRDS-025");
    error.setMessage("PO line cancelled");
    errorList.add(error);
    rdsReceiptsResponse.setErrors(errorList);
    return rdsReceiptsResponse;
  }

  public static RdsReceiptsResponse getRdsErrorResponseForMultiPoPoLines() {
    List<Error> errorList = new ArrayList<>();
    RdsReceiptsResponse rdsReceiptsResponse = new RdsReceiptsResponse();
    Error error1 = new Error();
    error1.setPoNumber("8458708162");
    error1.setPoLine(1);
    error1.setErrorCode("NIMRDS-025");
    error1.setMessage("PO line cancelled");
    errorList.add(error1);
    Error error2 = new Error();
    error2.setPoNumber("8458708162");
    error2.setPoLine(2);
    error2.setErrorCode("NIMRDS-025");
    error2.setMessage("PO line cancelled");
    errorList.add(error2);
    rdsReceiptsResponse.setErrors(errorList);
    return rdsReceiptsResponse;
  }

  public static RdsReceiptsResponse getRdsErrorResponseForMultiPoPoLinesPoNotFound() {
    List<Error> errorList = new ArrayList<>();
    RdsReceiptsResponse rdsReceiptsResponse = new RdsReceiptsResponse();
    Error error1 = new Error();
    error1.setPoNumber("8458708162");
    error1.setPoLine(1);
    error1.setErrorCode("NIMRDS-024");
    error1.setMessage("PO Not Found");
    errorList.add(error1);
    Error error2 = new Error();
    error2.setPoNumber("8458708162");
    error2.setPoLine(2);
    error2.setErrorCode("NIMRDS-024");
    error2.setMessage("PO Not Found");
    errorList.add(error2);
    rdsReceiptsResponse.setErrors(errorList);
    return rdsReceiptsResponse;
  }

  public static RdsReceiptsResponse getRdsMixedErrorResponseForMultiPoPoLines() {
    List<Error> errorList = new ArrayList<>();
    RdsReceiptsResponse rdsReceiptsResponse = new RdsReceiptsResponse();
    Error error1 = new Error();
    error1.setPoNumber("8458708162");
    error1.setPoLine(1);
    error1.setErrorCode("NIMRDS-023");
    error1.setMessage("No Receiving information found");
    errorList.add(error1);
    Error error2 = new Error();
    error2.setPoNumber("8458708162");
    error2.setPoLine(2);
    error2.setErrorCode("NIMRDS-025");
    error2.setMessage("PO line cancelled");
    errorList.add(error2);
    rdsReceiptsResponse.setErrors(errorList);
    return rdsReceiptsResponse;
  }

  public static RdsReceiptsResponse getRdsErrorResponseForSinglePoAndPoLinePoNotFound() {
    List<Error> errorList = new ArrayList<>();
    RdsReceiptsResponse rdsReceiptsResponse = new RdsReceiptsResponse();
    Error error = new Error();
    error.setPoNumber("8458708162");
    error.setPoLine(1);
    error.setErrorCode("NIMRDS-024");
    error.setMessage("PO Not Found");
    errorList.add(error);
    rdsReceiptsResponse.setErrors(errorList);
    return rdsReceiptsResponse;
  }

  public static RdsReceiptsResponse getLineNotFoundErrorResponseFromRdsForSinglePoAndPoLine() {
    List<Error> errorList = new ArrayList<>();
    RdsReceiptsResponse rdsReceiptsResponse = new RdsReceiptsResponse();
    Error error = new Error();
    error.setPoNumber("8458708162");
    error.setPoLine(1);
    error.setErrorCode("NIMRDS-023");
    error.setMessage("PO line found");
    errorList.add(error);
    rdsReceiptsResponse.setErrors(errorList);
    return rdsReceiptsResponse;
  }

  public static RdsReceiptsResponse getRdsReceivedQtySuccessResponseForDeliveryDocument()
      throws IOException {
    File resource =
        new ClassPathResource("RdcCurrentReceivedQtyResponseByDeliveryDocuments.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    RdsReceiptsResponse rdsReceiptsResponse =
        (gson.fromJson(mockResponse, RdsReceiptsResponse.class));
    return rdsReceiptsResponse;
  }

  public static RdsReceiptsResponse getRdsSuccessAndErrorResponseForDeliveryDocument()
      throws IOException {
    File resource = new ClassPathResource("RdsSuccessAndErrorResponseForMultiPo.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    RdsReceiptsResponse rdsReceiptsResponse =
        (gson.fromJson(mockResponse, RdsReceiptsResponse.class));
    return rdsReceiptsResponse;
  }

  public static ItemDetailsResponseBody getRdsItemDetailResponse() throws IOException {
    File resource = new ClassPathResource("RdsSuccessItemDetailsResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ItemDetailsResponseBody itemDetailsResponse =
        (gson.fromJson(mockResponse, ItemDetailsResponseBody.class));
    return itemDetailsResponse;
  }

  public static ItemDetailsResponseBody getRdsItemDetailResponse_NoPrimeDetails()
      throws IOException {
    File resource = new ClassPathResource("RdsSuccessItemDetailsResponseDA.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ItemDetailsResponseBody itemDetailsResponse =
        (gson.fromJson(mockResponse, ItemDetailsResponseBody.class));
    return itemDetailsResponse;
  }

  public static ItemDetailsResponseBody getRdsItemDetailResponse_HazmatItem() throws IOException {
    File resource =
        new ClassPathResource("RdsSuccessItemDetailsResponseForHazmatItem.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ItemDetailsResponseBody itemDetailsResponse =
        (gson.fromJson(mockResponse, ItemDetailsResponseBody.class));
    return itemDetailsResponse;
  }

  public static ReceiveContainersResponseBody getReceiveContainersSuccessResponse()
      throws IOException {
    File resource = new ClassPathResource("RdsReceiveContainersSuccessResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ReceiveContainersResponseBody receiveContainersResponseBody =
        (gson.fromJson(mockResponse, ReceiveContainersResponseBody.class));
    return receiveContainersResponseBody;
  }

  public static ReceiveContainersResponseBody getReceiveContainersErrorResponse()
      throws IOException {
    File resource = new ClassPathResource("RdsReceiveContainersErrorResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ReceiveContainersResponseBody receiveContainersResponseBody =
        (gson.fromJson(mockResponse, ReceiveContainersResponseBody.class));
    return receiveContainersResponseBody;
  }

  public static ItemDetailsResponseBody getRdsItemDetailNotFoundResponse() throws IOException {
    File resource = new ClassPathResource("RdsItemDetailsNotFoundResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ItemDetailsResponseBody itemDetailsResponse =
        (gson.fromJson(mockResponse, ItemDetailsResponseBody.class));
    return itemDetailsResponse;
  }

  public static ReceiveContainersResponseBody getRdsResponseForDACasePack() throws IOException {
    File resource = new ClassPathResource("RdsDACasePackContainerResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ReceiveContainersResponseBody receiveContainersResponseBody =
        (gson.fromJson(mockResponse, ReceiveContainersResponseBody.class));
    return receiveContainersResponseBody;
  }

  public static ReceiveContainersResponseBody getRdsResponseForDABreakPack() throws IOException {
    File resource = new ClassPathResource("RdsDABreakPackContainerResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ReceiveContainersResponseBody receiveContainersResponseBody =
        (gson.fromJson(mockResponse, ReceiveContainersResponseBody.class));
    return receiveContainersResponseBody;
  }

  public static ReceiveContainersResponseBody getRdsResponseForDABreakConveyPacks()
      throws IOException {
    File resource =
        new ClassPathResource("RdsDABreakPackConveryPicksContainerResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ReceiveContainersResponseBody receiveContainersResponseBody =
        (gson.fromJson(mockResponse, ReceiveContainersResponseBody.class));
    return receiveContainersResponseBody;
  }

  public static RdsReceiptsResponse getRdsSuccessResponseForMultiplePOAndLine_AtlasConverted()
      throws IOException {
    File resource =
        new ClassPathResource("RdsSuccessResponseForMultiplePoAndPoLine_AtlasConverted.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    RdsReceiptsResponse rdsReceiptsResponse =
        (gson.fromJson(mockResponse, RdsReceiptsResponse.class));
    return rdsReceiptsResponse;
  }

  public static RdsReceiptsResponse getRdsErrorResponseForMultiplePOAndLine_AtlasConverted()
      throws IOException {
    File resource =
        new ClassPathResource("RdsErrorResponseForMultiplePoAndPoLine_AtlasConverted.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    RdsReceiptsResponse rdsReceiptsResponse =
        (gson.fromJson(mockResponse, RdsReceiptsResponse.class));
    return rdsReceiptsResponse;
  }
}
