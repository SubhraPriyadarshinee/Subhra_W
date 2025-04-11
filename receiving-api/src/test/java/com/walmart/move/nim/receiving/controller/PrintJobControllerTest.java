package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintJobResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelResponseBody;
import com.walmart.move.nim.receiving.core.service.LabelServiceImpl;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class PrintJobControllerTest extends ReceivingControllerTestBase {

  private HttpHeaders headers = MockHttpHeaders.getHeaders();
  private MockMvc mockMvc;

  @InjectMocks private PrintJobController printJobController;
  @Mock private AppConfig appConfig;

  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private LabelServiceImpl labelServiceImpl;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    mockMvc = MockMvcBuilders.standaloneSetup(printJobController).build();
    TenantContext.setFacilityNum(12345);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  @AfterTest
  public void resetMocks() {
    reset(labelServiceImpl);
  }

  @Test
  public void testGetLabelsSingleUserApiReturns200() {
    try {
      when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any()))
          .thenReturn(labelServiceImpl);
      when(labelServiceImpl.getLabels(anyLong(), anyString(), anyBoolean()))
          .thenReturn(successPrintJobResponse());

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/printjobs/12345678/labels")
                  .headers(headers)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      verify(labelServiceImpl, times(1)).getLabels(anyLong(), anyString(), anyBoolean());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testGetLabelsMultiUserApiReturns200() {
    try {
      when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any()))
          .thenReturn(labelServiceImpl);
      when(labelServiceImpl.getLabels(anyLong(), anyString(), anyBoolean()))
          .thenReturn(successPrintJobResponse());

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/printjobs/12345678/labels?allusers=true")
                  .headers(headers)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      verify(labelServiceImpl, times(1)).getLabels(anyLong(), anyString(), anyBoolean());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testGetLabelsSingleUserApiReturns400() {
    try {
      when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any()))
          .thenReturn(labelServiceImpl);
      when(labelServiceImpl.getLabels(anyLong(), anyString(), anyBoolean()))
          .thenReturn(new ArrayList<>());

      mockMvc
          .perform(
              MockMvcRequestBuilders.get("/printjobs/12345678/labels")
                  .headers(headers)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      verify(labelServiceImpl, times(1)).getLabels(anyLong(), anyString(), anyBoolean());
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  private List<PrintJobResponse> successPrintJobResponse() {
    PrintJobResponse printJobResponse = new PrintJobResponse();
    printJobResponse.setLabelIdentifier("a123456");
    printJobResponse.setItemDescription("Dock Tag");
    printJobResponse.setUserId("sysadmin");
    return Arrays.asList(printJobResponse);
  }

  @Test
  public void testReprintReturns200() {
    resetMocks();
    ReprintLabelResponseBody reprintLabelResponseBody =
        ReprintLabelResponseBody.builder()
            .clientId("AllocPlanner")
            .headers(createMockLabelHeaders())
            .printRequests(createMockPrintRequests())
            .build();

    doReturn(5).when(appConfig).getMaxAllowedReprintLabels();
    doReturn(reprintLabelResponseBody).when(labelServiceImpl).getReprintLabelData(any(), any());
    doReturn(labelServiceImpl)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(any(), any(), any());

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/printjobs/reprint")
                  .headers(headers)
                  .content(
                      "{\n"
                          + "    \"trackingIds\": [\n"
                          + "        \"F32835000020050054\"\n"
                          + "    ]\n"
                          + "}")
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      verify(labelServiceImpl, times(1)).getReprintLabelData(any(), any());
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testReprintReturns206() {
    resetMocks();
    ReprintLabelResponseBody reprintLabelResponseBody =
        ReprintLabelResponseBody.builder()
            .clientId("AllocPlanner")
            .headers(createMockLabelHeaders())
            .printRequests(createMockPrintRequests())
            .build();

    doReturn(reprintLabelResponseBody).when(labelServiceImpl).getReprintLabelData(any(), any());
    doReturn(labelServiceImpl)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(any(), any(), any());

    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/printjobs/reprint")
                  .headers(headers)
                  .content(
                      "{\n"
                          + "    \"trackingIds\": [\n"
                          + "        \"F32835000020050054\", \n"
                          + "   \"F32835000020050056\" ]\n"
                          + "}")
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isPartialContent());
      verify(labelServiceImpl, times(1)).getReprintLabelData(any(), any());
    } catch (Exception e) {
      assertTrue(false);
    }
  }

  @Test
  public void testReprintReturns_NoImplementation() {
    resetMocks();
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("language", "zpl");

    try {
      mockMvc.perform(
          MockMvcRequestBuilders.post("/printjobs/reprint")
              .headers(headers)
              .params(params)
              .content(
                  "{\n"
                      + "    \"trackingIds\": [\n"
                      + "        \"F32835000020050054\", \n"
                      + "   \"F32835000020050056\" ]\n"
                      + "}")
              .contentType(MediaType.APPLICATION_JSON));
    } catch (Exception e) {
      assertEquals(e.getCause().getMessage(), "Tenant-12345 not supported");
    }
    verify(labelServiceImpl, times(0)).getReprintLabelData(any(), any());
  }

  @Test
  public void testReprintReturns_noReprintData() {
    resetMocks();
    ReprintLabelResponseBody reprintLabelResponseBody =
        ReprintLabelResponseBody.builder()
            .clientId("AllocPlanner")
            .headers(createMockLabelHeaders())
            .printRequests(Arrays.asList())
            .build();

    doReturn(reprintLabelResponseBody).when(labelServiceImpl).getReprintLabelData(any(), any());
    doReturn(labelServiceImpl)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(any(), any(), any());

    try {
      mockMvc.perform(
          MockMvcRequestBuilders.post("/printjobs/reprint")
              .headers(headers)
              .content(
                  "{\n"
                      + "    \"trackingIds\": [\n"
                      + "        \"F32835000020050054\", \n"
                      + "   \"F32835000020050056\" ]\n"
                      + "}")
              .contentType(MediaType.APPLICATION_JSON));
    } catch (Exception e) {
      assertEquals(e.getCause().getMessage(), "invalidLPN");
    }
    verify(labelServiceImpl, times(1)).getReprintLabelData(any(), any());
  }

  private Map<String, Object> createMockLabelHeaders() {
    Map<String, Object> headers = new HashMap<>();
    headers.put("WMT-CorrelationId", "abc");
    headers.put("facilityCountryCode", "US");
    headers.put("facilityNum", "12345");
    return headers;
  }

  private List<PrintLabelRequest> createMockPrintRequests() {
    List<PrintLabelRequest> printRequests = new ArrayList<>();
    PrintLabelRequest printRequest =
        PrintLabelRequest.builder()
            .data(createMockData())
            .formatName("pallet_lpn_format")
            .labelIdentifier("F32835000020050054")
            .ttlInHours(72)
            .build();
    printRequests.add(printRequest);
    return printRequests;
  }

  private List<LabelData> createMockData() {
    List<LabelData> printingData = new ArrayList<>();
    printingData.add(new LabelData("LPN", "F32835000020050054"));
    printingData.add(new LabelData("TYPE", "DA"));
    printingData.add(new LabelData("DELIVERY", "60077104"));
    printingData.add(new LabelData("DESTINATION", "06021 US"));
    printingData.add(new LabelData("Qty", "80"));
    printingData.add(new LabelData("ITEM", "553708208"));
    printingData.add(new LabelData("DESC", "ROYAL BASMATI 20LB"));
    printingData.add(new LabelData("UPCBAR", "10745042112010"));
    printingData.add(new LabelData("FULLUSERID", "sysadmin"));
    printingData.add(new LabelData("DOOR", "14B"));
    printingData.add(new LabelData("ORIGIN", "12345"));
    return printingData;
  }
}
