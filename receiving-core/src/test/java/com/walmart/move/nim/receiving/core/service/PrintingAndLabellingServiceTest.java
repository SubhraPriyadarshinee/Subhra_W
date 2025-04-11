package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintableLabelDataRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class PrintingAndLabellingServiceTest extends ReceivingTestBase {
  @InjectMocks private PrintingAndLabellingService printingAndLabellingService;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RestConnector restConnector;
  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset(restConnector);
    reset(tenantSpecificConfigReader);
  }

  @BeforeMethod
  public void setUp() {
    doReturn("labellingUrl")
        .when(tenantSpecificConfigReader)
        .getLabellingServiceUrlOrDefault(any(), any());
    doReturn("labellingUrl").when(appConfig).getLabellingServiceBaseUrl();
  }

  @Test
  public void testPostToLabelling() throws IOException {
    doReturn(new ResponseEntity<>(null, HttpStatus.OK))
        .when(restConnector)
        .post(anyString(), anyString(), any(HttpHeaders.class), eq(String.class));
    PrintableLabelDataRequest printableLabelDataRequest = getPrintableLabelDataRequest();
    printingAndLabellingService.postToLabelling(
        Arrays.asList(printableLabelDataRequest), MockHttpHeaders.getHeaders());
    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(restConnector, times(1))
        .post(eq("labellingUrl"), any(), headerCaptor.capture(), eq(String.class));
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get("WMT_ReqOriginTs").size(), 1);
    assertEquals(headers.get("WMT_UserId").size(), 1);
  }

  @Test
  public void testPostToLabelling_Exception() throws IOException {
    when(restConnector.post(anyString(), anyString(), any(HttpHeaders.class), eq(String.class)))
        .thenThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null));
    PrintableLabelDataRequest printableLabelDataRequest = getPrintableLabelDataRequest();
    printingAndLabellingService.postToLabelling(
        Arrays.asList(printableLabelDataRequest), MockHttpHeaders.getHeaders());
    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(restConnector, times(1))
        .post(eq("labellingUrl"), any(), headerCaptor.capture(), eq(String.class));
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get("WMT_ReqOriginTs").size(), 1);
    assertEquals(headers.get("WMT_UserId").size(), 1);
  }

  @Test
  public void testPostToLabelling_ResourceAccessException() throws IOException {
    when(restConnector.post(anyString(), anyString(), any(HttpHeaders.class), eq(String.class)))
        .thenThrow(new ResourceAccessException("Error"));
    PrintableLabelDataRequest printableLabelDataRequest = getPrintableLabelDataRequest();
    printingAndLabellingService.postToLabelling(
        Arrays.asList(printableLabelDataRequest), MockHttpHeaders.getHeaders());
    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(restConnector, times(1))
        .post(eq("labellingUrl"), any(), headerCaptor.capture(), eq(String.class));
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get("WMT_ReqOriginTs").size(), 1);
    assertEquals(headers.get("WMT_UserId").size(), 1);
  }

  private PrintableLabelDataRequest getPrintableLabelDataRequest() {
    PrintableLabelDataRequest printableLabelDataRequest = new PrintableLabelDataRequest();
    printableLabelDataRequest.setFormatName("case_lpn_format");
    printableLabelDataRequest.setLabelData(new ArrayList<>());
    return printableLabelDataRequest;
  }

  @Test
  public void testPostToLabellingInBatches() throws IOException {
    doReturn(1).when(appConfig).getLabellingServiceCallBatchCount();
    doReturn(new ResponseEntity<>(null, HttpStatus.OK))
        .when(restConnector)
        .post(anyString(), anyString(), any(HttpHeaders.class), eq(String.class));
    List<PrintableLabelDataRequest> list = new ArrayList<>();
    list.add(getPrintableLabelDataRequest());
    list.add(getPrintableLabelDataRequest());
    printingAndLabellingService.postToLabellingInBatches(list, MockHttpHeaders.getHeaders());
    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(restConnector, times(2))
        .post(eq("labellingUrl"), any(), headerCaptor.capture(), eq(String.class));
    HttpHeaders headers = headerCaptor.getValue();
    assertEquals(headers.get("WMT_ReqOriginTs").size(), 1);
    assertEquals(headers.get("WMT_UserId").size(), 1);
  }
}
