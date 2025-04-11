package com.walmart.move.nim.receiving.wfs.service;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse;
import com.walmart.move.nim.receiving.core.model.ReceivingType;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WFSTclFreeHandlerTest {
  @InjectMocks private WFSTclFreeHandler wfsTclFreeHandler;

  @Mock private DeliveryService deliveryServiceImpl;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private RestConnector restConnector;
  @Mock private AppConfig appConfig;

  @Mock private ReceiptCustomRepository receiptCustomRepository;

  @BeforeClass
  public void initMocks() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(configUtils);
    reset(deliveryServiceImpl);
    reset(restConnector);
    reset(appConfig);
  }

  public List<ScannedData> getScannedDataList() {
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

  public List<String> getPreviouslyScannedDataList() {
    String upcNumber = "00815489023378";
    return Collections.singletonList(upcNumber);
  }

  public InstructionRequest getInstructionRequest() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId(UUID.randomUUID().toString());
    instructionRequest.setDeliveryNumber("891100");
    instructionRequest.setDeliveryStatus("SCH");
    instructionRequest.setUpcNumber("00815489023378"); // UpcNumber == GTIN
    instructionRequest.setScannedDataList(getScannedDataList());
    instructionRequest.setReceivingType(ReceivingType.UPC.getReceivingType());
    instructionRequest.setPreviouslyScannedDataList(getPreviouslyScannedDataList());
    return instructionRequest;
  }

  private String getJSONStringResponse(String path) {
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

  @Test
  public void testFetchDeliveryDocument_via_TCL_Free_Receiving() throws ReceivingException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryNumber("0");
    instructionRequest.setUpcNumber("06159265687229");
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "4093");
    String dataPath = "src/test/resources/GDMDeliveryAcceptedStatus.json";
    String gdmListOfDeliveries = getJSONStringResponse(dataPath);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0042084GDM", 1, new Long(10));
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponseTwo =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0042084GDM", 1, new Long(10));
    when(receiptCustomRepository.receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt()))
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponse)
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponseTwo);
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    when(configUtils.getTenantConfigurationAsList(anyString()))
        .thenReturn(Arrays.asList("WRK", "OPN", "PNDFNL"));
    when(deliveryServiceImpl.fetchDeliveriesByStatusUpcAndPoNumber(
            anyList(), anyString(), anyString(), anyInt(), anyList()))
        .thenReturn(gdmListOfDeliveries);
    wfsTclFreeHandler.getDeliveryNumberByTCLFree(instructionRequest, new Long(0), "", "4093");
  }

  @Test
  public void
      testFetchDeliveryDocument_via_TCL_Free_Receiving_Delivery_Accepted_Status_Api_Exception()
          throws ReceivingException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryNumber("0");
    instructionRequest.setUpcNumber("06159265687229");
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "4093");
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    when(configUtils.getTenantConfigurationAsList(anyString()))
        .thenReturn(Arrays.asList("WRK", "OPN", "PNDFNL"));
    when(deliveryServiceImpl.fetchDeliveriesByStatusUpcAndPoNumber(
            anyList(), anyString(), anyString(), anyInt(), anyList()))
        .thenThrow(new ReceivingException("Not Valid"));
    Assertions.assertThrows(
        ReceivingException.class,
        () ->
            wfsTclFreeHandler.getDeliveryNumberByTCLFree(
                instructionRequest, new Long(0), "", "4093"));
  }

  @Test
  public void testFetchDeliveryDocument_via_TCL_Free_Receiving_One_Delivery_Accepted_Status()
      throws ReceivingException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryNumber("0");
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "4093");
    String dataPath = "src/test/resources/GDMDeliveryAcceptedStatusOneDelivery.json";
    String consolidatedDeliveryList = getJSONStringResponse(dataPath);
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    when(configUtils.getTenantConfigurationAsList(anyString()))
        .thenReturn(Arrays.asList("WRK", "OPN", "PNDFNL"));
    when(deliveryServiceImpl.fetchDeliveriesByStatusUpcAndPoNumber(
            anyList(), anyString(), anyString(), anyInt(), anyList()))
        .thenReturn(consolidatedDeliveryList);
    wfsTclFreeHandler.getDeliveryNumberByTCLFree(instructionRequest, new Long(0), "", "4093");
  }

  @Test
  public void testFetchDeliveryDocument_via_TCL_Free_Receiving_Two_Deliveries_Same_Status()
      throws ReceivingException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryNumber("0");
    instructionRequest.setUpcNumber("06159265687229");
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "4093");
    String dataPath = "src/test/resources/GDMDeliveryAcceptedStatusTwoDeliveriesSameStatus.json";
    String consolidatedDeliveryList = getJSONStringResponse(dataPath);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0042084GDM", 1, new Long(10));
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponseTwo =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0042084GDM", 1, new Long(10));
    when(receiptCustomRepository.receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt()))
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponse)
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponseTwo);
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    when(configUtils.getTenantConfigurationAsList(anyString()))
        .thenReturn(Arrays.asList("WRK", "OPN", "PNDFNL"));
    when(deliveryServiceImpl.fetchDeliveriesByStatusUpcAndPoNumber(
            anyList(), anyString(), anyString(), anyInt(), anyList()))
        .thenReturn(consolidatedDeliveryList);
    wfsTclFreeHandler.getDeliveryNumberByTCLFree(
        instructionRequest, new Long(0), "0240511GDM", "4093");
  }

  @Test
  public void testFetchDeliveryDocument_via_TCL_Free_Receiving_Two_Deliveries_Different_Seller()
      throws ReceivingException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryNumber("0");
    instructionRequest.setUpcNumber("06159265687229");
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "4093");
    String dataPath = "src/test/resources/GDMDeliveryAcceptedStatusTwoDeliveriesSameStatus.json";
    String consolidatedDeliveries = getJSONStringResponse(dataPath);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0042084GDM", 1, new Long(10));
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponseTwo =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0042084GDM", 1, new Long(10));
    when(receiptCustomRepository.receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt()))
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponse)
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponseTwo);
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    when(configUtils.getTenantConfigurationAsList(anyString()))
        .thenReturn(Arrays.asList("WRK", "OPN", "PNDFNL"));
    when(deliveryServiceImpl.fetchDeliveriesByStatusUpcAndPoNumber(
            anyList(), anyString(), anyString(), anyInt(), anyList()))
        .thenReturn(consolidatedDeliveries);
    wfsTclFreeHandler.getDeliveryNumberByTCLFree(
        instructionRequest, new Long(0), "0240511GDM", "4093");
  }

  @Test
  public void
      testFetchDeliveryDocument_via_TCL_Free_Receiving_Two_Deliveries_Different_Seller_Invalid_PO()
          throws ReceivingException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryNumber("0");
    instructionRequest.setUpcNumber("06159265687229");
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "4093");
    String dataPath =
        "src/test/resources/GDMDeliveryAcceptedStatusTwoDeliveriesDifferentStatus.json";
    String gdmDeliveryStatusResponseString = getJSONStringResponse(dataPath);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0042084GDM", 1, new Long(10));
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponseTwo =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0042084GDM", 1, new Long(10));
    when(receiptCustomRepository.receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt()))
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponse)
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponseTwo);
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    when(configUtils.getTenantConfigurationAsList(anyString()))
        .thenReturn(Arrays.asList("WRK", "OPN", "PNDFNL"));
    when(deliveryServiceImpl.fetchDeliveriesByStatusUpcAndPoNumber(
            anyList(), anyString(), anyString(), anyInt(), anyList()))
        .thenReturn(gdmDeliveryStatusResponseString);
    Assertions.assertThrows(
        ReceivingException.class,
        () ->
            wfsTclFreeHandler.getDeliveryNumberByTCLFree(
                instructionRequest, new Long(0), "121313GDM", "4093"));
  }

  @Test
  public void testFetchDeliveryDocument_via_TCL_Free_Receiving_Two_Deliveries_Same_SameStatus()
      throws ReceivingException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryNumber("0");
    instructionRequest.setUpcNumber("06159265687229");
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    mockHttpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "4093");
    String dataPath = "src/test/resources/GDMDeliveryAcceptedStatusTwoDeliveriesSameStatus.json";
    String gdmDeliveryStatusResponseString = getJSONStringResponse(dataPath);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0042084GDM", 1, new Long(10));
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponseTwo =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0042084GDM", 1, new Long(10));
    when(receiptCustomRepository.receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt()))
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponse)
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponseTwo);
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    when(configUtils.getTenantConfigurationAsList(anyString()))
        .thenReturn(Arrays.asList("WRK", "OPN", "PNDFNL"));
    when(deliveryServiceImpl.fetchDeliveriesByStatusUpcAndPoNumber(
            anyList(), anyString(), anyString(), anyInt(), anyList()))
        .thenReturn(gdmDeliveryStatusResponseString);

    Assertions.assertEquals(
        60502460,
        wfsTclFreeHandler.getDeliveryNumberByTCLFree(instructionRequest, new Long(0), "", "4093"));
  }
}
