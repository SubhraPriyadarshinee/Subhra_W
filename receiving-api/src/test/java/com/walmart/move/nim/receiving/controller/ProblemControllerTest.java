package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.model.InboundDocument;
import com.walmart.move.nim.receiving.core.model.Item;
import com.walmart.move.nim.receiving.core.model.Problem;
import com.walmart.move.nim.receiving.core.model.ProblemTagResponse;
import com.walmart.move.nim.receiving.core.model.ProblemTicketResponseCount;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProblemControllerTest extends TenantSpecificConfigReaderTestBase {
  @Autowired private MockMvc mockMvc;

  @Autowired
  @Mock
  @Qualifier(value = "fitService")
  ProblemService problemService;

  private ProblemTagResponse problemTagResponse = new ProblemTagResponse();
  private ProblemTicketResponseCount problemTicketCount = new ProblemTicketResponseCount();
  private Problem problem = new Problem();
  private InboundDocument inboundDocument = new InboundDocument();
  private Item item = new Item();
  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {

    problem.setProblemTagId("99999");
    problem.setDeliveryNumber("87436843");
    problem.setIssueId("11111");
    problem.setResolutionId("22222");
    problem.setResolutionQty(10);
    problem.setSlotId("S1234");

    inboundDocument.setPurchaseReferenceNumber("985454");
    inboundDocument.setPurchaseReferenceLineNumber(1);
    inboundDocument.setExpectedQty(50);

    item.setNumber(4282l);
    item.setDescription("SAMPLE ITEM");
    item.setPalletTi(4);
    item.setPalletHi(5);

    problemTagResponse.setProblem(problem);
    problemTagResponse.setInboundDocument(inboundDocument);
    problemTagResponse.setItem(item);

    problemTicketCount.setTicketCount(0);
  }

  @Test
  public void getProblemTagDetails_returnSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(problemService);
    when(problemService.txGetProblemTagInfo(anyString(), any(HttpHeaders.class)))
        .thenReturn(problemTagResponse);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/problems/99999").headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
  }

  @Test
  public void completeProblemTag_returnSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(problemService);
    when(problemService.completeProblemTag(anyString(), any(Problem.class), any(HttpHeaders.class)))
        .thenReturn("problemTag completed successfully");

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/problems/99999/complete")
                .content(gson.toJson(problem))
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
  }

  @Test
  public void test_getProblemsForDelivery_returnSuccess() throws Exception {

    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(problemService);
    doReturn("success for test")
        .when(problemService)
        .getProblemsForDelivery(anyInt(), any(HttpHeaders.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/problems/deliveryNumber/123")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());

    verify(problemService, times(1)).getProblemsForDelivery(anyInt(), any());
  }

  @Test
  public void createProblemTag_returnSuccess() throws Exception {
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(problemService);
    when(problemService.createProblemTag(anyString()))
        .thenReturn("problemTag created successfully");

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/problems")
                .content(gson.toJson("create problem payload"))
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());
    verify(problemService, times(1)).createProblemTag(anyString());
  }

  @Test
  public void test_getProblemTicketsForPO_returnSuccess() throws Exception {

    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(problemService);
    when(problemService.getProblemTicketsForPo(anyString(), any(HttpHeaders.class)))
        .thenReturn(problemTicketCount);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/problems/po/123456/problemTicketCount")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk());

    verify(problemService, times(1)).getProblemTicketsForPo(anyString(), any());
  }
}
