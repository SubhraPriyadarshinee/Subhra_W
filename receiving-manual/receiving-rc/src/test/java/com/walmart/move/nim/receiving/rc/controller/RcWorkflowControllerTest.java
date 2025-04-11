package com.walmart.move.nim.receiving.rc.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.PaginatedResponse;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rc.contants.*;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.dto.request.*;
import com.walmart.move.nim.receiving.rc.model.dto.response.FraudWorkflowStats;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowResponse;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowStatsResponse;
import com.walmart.move.nim.receiving.rc.service.RcItemImageService;
import com.walmart.move.nim.receiving.rc.service.RcWorkflowService;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RcWorkflowControllerTest extends ReceivingControllerTestBase {
  @InjectMocks private RcWorkflowController rcWorkflowController;
  @Mock private RcWorkflowService rcWorkflowService;

  @Mock private RcItemImageService itemImageService;
  @Autowired private ResourceBundleMessageSource resourceBundleMessageSource;
  private RestResponseExceptionHandler restResponseExceptionHandler;
  private MockMvc mockMvc;
  private Gson gson;
  private RcWorkflowCreateRequest rcWorkflowCreateRequest;
  private RcWorkflowUpdateRequest rcWorkflowUpdateRequest;

  @BeforeClass
  public void init() throws ParseException {
    gson = new GsonBuilder().setDateFormat(RcConstants.UTC_DATE_FORMAT).create();
    MockitoAnnotations.initMocks(this);
    restResponseExceptionHandler = new RestResponseExceptionHandler();
    ReflectionTestUtils.setField(
        restResponseExceptionHandler, "resourceBundleMessageSource", resourceBundleMessageSource);
    mockMvc =
        MockMvcBuilders.standaloneSetup(rcWorkflowController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @BeforeMethod
  public void reset() {

    Mockito.reset(rcWorkflowService);
    Mockito.reset(itemImageService);
  }

  /*
   Positive test cases for create workflow
  */
  @Test
  public void testCreateWorkflow_validRequestBody() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("200001212434234")
            .packageBarcodeType("SO")
            .workflowId("e09074000100020003")
            .createReason("ITEM_MISSING")
            .type(WorkflowType.FRAUD)
            .items(Collections.singletonList(RcWorkflowItem.builder().gtin("000129081232").build()))
            .build();
    when(rcWorkflowService.createWorkflow(any(RcWorkflowCreateRequest.class), any(), any()))
        .thenReturn(
            ReceivingWorkflow.builder()
                .id(10001L)
                .workflowItems(Collections.singletonList(ReceivingWorkflowItem.builder().build()))
                .build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isCreated());

    verify(rcWorkflowService, times(1))
        .createWorkflow(any(RcWorkflowCreateRequest.class), any(), any());
  }

  @Test
  public void testCreateWorkflow_ValidRMA() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("130333476460158736")
            .packageBarcodeType("RMA")
            .workflowId("e09074000100020003")
            .createReason("ITEM_MISSING")
            .type(WorkflowType.FRAUD)
            .items(Collections.singletonList(RcWorkflowItem.builder().gtin("000129081232").build()))
            .build();
    when(rcWorkflowService.createWorkflow(any(RcWorkflowCreateRequest.class), any(), any()))
        .thenReturn(
            ReceivingWorkflow.builder()
                .id(10001L)
                .workflowItems(Collections.singletonList(ReceivingWorkflowItem.builder().build()))
                .build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isCreated());

    verify(rcWorkflowService, times(1))
        .createWorkflow(any(RcWorkflowCreateRequest.class), any(), any());
  }

  @Test
  public void testCreateWorkflow_ValidPO() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("183300351130302774")
            .packageBarcodeType("PO")
            .workflowId("e09074000100020003")
            .createReason("ITEM_MISSING")
            .type(WorkflowType.FRAUD)
            .items(Collections.singletonList(RcWorkflowItem.builder().gtin("000129081232").build()))
            .build();
    when(rcWorkflowService.createWorkflow(any(RcWorkflowCreateRequest.class), any(), any()))
        .thenReturn(
            ReceivingWorkflow.builder()
                .id(10001L)
                .workflowItems(Collections.singletonList(ReceivingWorkflowItem.builder().build()))
                .build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isCreated());

    verify(rcWorkflowService, times(1))
        .createWorkflow(any(RcWorkflowCreateRequest.class), any(), any());
  }

  @Test
  public void testCreateWorkflow_eventPublishingDisabled() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("200001212434234")
            .packageBarcodeType("SO")
            .workflowId("e09074000100020003")
            .createReason("ITEM_MISSING")
            .type(WorkflowType.FRAUD)
            .items(Collections.singletonList(RcWorkflowItem.builder().gtin("000129081232").build()))
            .build();
    when(rcWorkflowService.createWorkflow(any(RcWorkflowCreateRequest.class), any(), any()))
        .thenReturn(
            ReceivingWorkflow.builder()
                .id(10001L)
                .workflowItems(Collections.singletonList(ReceivingWorkflowItem.builder().build()))
                .build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow?publishEvents=false")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isCreated());

    verify(rcWorkflowService, times(1))
        .createWorkflow(any(RcWorkflowCreateRequest.class), any(), any());
  }

  @Test
  public void testCreateWorkflow_eventPublishingEnabled() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("200001212434234")
            .packageBarcodeType("SO")
            .workflowId("e09074000100020003")
            .createReason("ITEM_MISSING")
            .type(WorkflowType.FRAUD)
            .items(Collections.singletonList(RcWorkflowItem.builder().gtin("000129081232").build()))
            .build();
    when(rcWorkflowService.createWorkflow(any(RcWorkflowCreateRequest.class), any(), any()))
        .thenReturn(
            ReceivingWorkflow.builder()
                .id(10001L)
                .workflowItems(Collections.singletonList(ReceivingWorkflowItem.builder().build()))
                .build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow?publishEvents=true")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isCreated());

    verify(rcWorkflowService, times(1))
        .createWorkflow(any(RcWorkflowCreateRequest.class), any(), any());
  }

  /*
   Negative test cases for create workflow
  */
  @Test
  public void testCreateWorkflow_missingPackageBarcodeValue() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .createReason("ITEM_MISSING")
            .type(WorkflowType.FRAUD)
            .items(Collections.singletonList(RcWorkflowItem.builder().gtin("000129081232").build()))
            .build();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateWorkflow_missingPackageBarcodeType() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("183300440817030592")
            .createReason("ITEM_MISSING")
            .items(Collections.singletonList(RcWorkflowItem.builder().gtin("000129081232").build()))
            .build();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateWorkflow_missingCreateReason() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("200001212434234")
            .packageBarcodeType("SO")
            .type(WorkflowType.FRAUD)
            .items(Collections.singletonList(RcWorkflowItem.builder().gtin("000129081232").build()))
            .build();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateWorkflow_missingType() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("200001212434234")
            .createReason("ITEM_MISSING")
            .items(Collections.singletonList(RcWorkflowItem.builder().gtin("000129081232").build()))
            .build();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateWorkflow_missingItems() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("200001212434234")
            .packageBarcodeType("SO")
            .type(WorkflowType.FRAUD)
            .createReason("ITEM_MISSING")
            .build();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateWorkflow_missingGtin() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("200001212434234")
            .packageBarcodeType("SO")
            .createReason("ITEM_MISSING")
            .type(WorkflowType.FRAUD)
            .items(Collections.singletonList(RcWorkflowItem.builder().build()))
            .build();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateWorkflow_withTrackingId() throws Exception {
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("200001212434234")
            .packageBarcodeType("SO")
            .createReason("ITEM_MISSING")
            .workflowId("e09074000100020003")
            .type(WorkflowType.FRAUD)
            .items(
                Collections.singletonList(
                    RcWorkflowItem.builder()
                        .gtin("000128981231")
                        .itemTrackingId("100012L")
                        .build()))
            .build();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow")
                .content(gson.toJson(rcWorkflowCreateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().is5xxServerError());
  }

  /*
   Test cases for get workflow by ID
  */
  @Test
  public void testGetWorkflowById() throws Exception {
    when(rcWorkflowService.getWorkflowById(any()))
        .thenReturn(RcWorkflowResponse.builder().id(1L).build());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/returns/workflow/10001")
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());

    verify(rcWorkflowService, times(1)).getWorkflowById(any());
  }

  /*
   Test cases for update workflow
  */
  @Test
  public void testUpdateWorkflow_validRequestBody() throws Exception {
    rcWorkflowUpdateRequest =
        RcWorkflowUpdateRequest.builder()
            .workflowItems(
                Collections.singletonList(
                    RcWorkflowItemUpdateRequest.builder()
                        .id(1L)
                        .action(WorkflowAction.FRAUD)
                        .build()))
            .build();
    doNothing()
        .when(rcWorkflowService)
        .updateWorkflow(any(), any(RcWorkflowUpdateRequest.class), any(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/returns/workflow/10001")
                .content(gson.toJson(rcWorkflowUpdateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());

    verify(rcWorkflowService, times(1))
        .updateWorkflow(any(), any(RcWorkflowUpdateRequest.class), any(), any());
  }

  @Test
  public void testUpdateWorkflow_eventPublishingEnabled() throws Exception {
    rcWorkflowUpdateRequest =
        RcWorkflowUpdateRequest.builder()
            .workflowItems(
                Collections.singletonList(
                    RcWorkflowItemUpdateRequest.builder()
                        .id(1L)
                        .action(WorkflowAction.FRAUD)
                        .build()))
            .build();
    doNothing()
        .when(rcWorkflowService)
        .updateWorkflow(any(), any(RcWorkflowUpdateRequest.class), any(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/returns/workflow/10001?publishEvents=true")
                .content(gson.toJson(rcWorkflowUpdateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());

    verify(rcWorkflowService, times(1))
        .updateWorkflow(any(), any(RcWorkflowUpdateRequest.class), any(), any());
  }

  @Test
  public void testUpdateWorkflow_eventPublishingDisabled() throws Exception {
    rcWorkflowUpdateRequest =
        RcWorkflowUpdateRequest.builder()
            .workflowItems(
                Collections.singletonList(
                    RcWorkflowItemUpdateRequest.builder()
                        .id(1L)
                        .action(WorkflowAction.FRAUD)
                        .build()))
            .build();
    doNothing()
        .when(rcWorkflowService)
        .updateWorkflow(any(), any(RcWorkflowUpdateRequest.class), any(), any());
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/returns/workflow/10001?publishEvents=false")
                .content(gson.toJson(rcWorkflowUpdateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk());

    verify(rcWorkflowService, times(1))
        .updateWorkflow(any(), any(RcWorkflowUpdateRequest.class), any(), any());
  }

  @Test
  public void testUpdateWorkflow_missingWorkflowItems() throws Exception {
    rcWorkflowUpdateRequest = RcWorkflowUpdateRequest.builder().build();
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/returns/workflow/10001")
                .content(gson.toJson(rcWorkflowUpdateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testUpdateWorkflow_missingWorkflowItemId() throws Exception {
    rcWorkflowUpdateRequest =
        RcWorkflowUpdateRequest.builder()
            .workflowItems(
                Collections.singletonList(
                    RcWorkflowItemUpdateRequest.builder().action(WorkflowAction.FRAUD).build()))
            .build();
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/returns/workflow/10001")
                .content(gson.toJson(rcWorkflowUpdateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testUpdateWorkflow_missingWorkflowAction() throws Exception {
    rcWorkflowUpdateRequest =
        RcWorkflowUpdateRequest.builder()
            .workflowItems(
                Collections.singletonList(RcWorkflowItemUpdateRequest.builder().id(10001L).build()))
            .build();
    mockMvc
        .perform(
            MockMvcRequestBuilders.patch("/returns/workflow/10001")
                .content(gson.toJson(rcWorkflowUpdateRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testSearchWorkflows() throws Exception {
    Date currentDate = new Date();
    RcWorkflowSearchRequest rcWorkflowSearchRequest =
        RcWorkflowSearchRequest.builder()
            .criteria(
                RcWorkflowSearchCriteria.builder()
                    .workflowId("e09074000100020003")
                    .fromCreateTs(currentDate)
                    .toCreateTs(currentDate)
                    .actionIn(Arrays.asList(WorkflowAction.FRAUD))
                    .statusIn(Arrays.asList(WorkflowStatus.CREATED))
                    .build())
            .sortBy(Collections.singletonMap(WorkflowSortColumn.createTs, SortOrder.ASC))
            .build();
    PaginatedResponse response =
        PaginatedResponse.<RcWorkflowResponse>builder()
            .pageOffset(1)
            .pageSize(1)
            .totalCount(1)
            .totalPages(1)
            .results(Arrays.asList(RcWorkflowResponse.builder().id(1L).build()))
            .build();
    when(rcWorkflowService.searchWorkflows(anyInt(), anyInt(), any())).thenReturn(response);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow/search")
                .content(gson.toJson(rcWorkflowSearchRequest))
                .requestAttr("pageOffset", 1)
                .requestAttr("pageSize", 100)
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk())
        .andExpect(content().json(gson.toJson(response)));

    verify(rcWorkflowService, times(1))
        .searchWorkflows(anyInt(), anyInt(), any(RcWorkflowSearchRequest.class));
  }

  @Test
  public void testSearchWorkflowsFailure() throws Exception {
    RcWorkflowSearchRequest rcWorkflowSearchRequest = RcWorkflowSearchRequest.builder().build();
    when(rcWorkflowService.searchWorkflows(anyInt(), anyInt(), any()))
        .thenThrow(new ReceivingBadDataException("errorCode", "message"));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow/search")
                .content(gson.toJson(rcWorkflowSearchRequest))
                .requestAttr("pageOffset", 1)
                .requestAttr("pageSize", 100)
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
    verify(rcWorkflowService, times(1))
        .searchWorkflows(anyInt(), anyInt(), any(RcWorkflowSearchRequest.class));
  }

  @Test
  public void testGetWorkflowStats() throws Exception {
    Date currentDate = new Date();
    RcWorkflowStatsRequest rcWorkflowStatsRequest =
        RcWorkflowStatsRequest.builder()
            .fromCreateTs(currentDate)
            .toCreateTs(currentDate)
            .type(WorkflowType.FRAUD)
            .build();
    RcWorkflowStatsResponse response =
        RcWorkflowStatsResponse.<FraudWorkflowStats>builder()
            .totalWorkflowItems(1)
            .totalPendingWorkflowItems(1)
            .totalOpenWorkflows(1)
            .totalWorkflows(1)
            .statsByWorkflowType(
                FraudWorkflowStats.builder()
                    .totalFraudItems(1)
                    .totalRegradedItems(0)
                    .totalNonFraudItems(0)
                    .build())
            .build();
    when(rcWorkflowService.getWorkflowStats(any(RcWorkflowStatsRequest.class)))
        .thenReturn(response);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow/stats")
                .content(gson.toJson(rcWorkflowStatsRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isOk())
        .andExpect(content().json(gson.toJson(response)));

    verify(rcWorkflowService, times(1)).getWorkflowStats(any(RcWorkflowStatsRequest.class));
  }

  @Test
  public void testGetWorkflowsStatsFailure() throws Exception {
    RcWorkflowSearchRequest rcWorkflowSearchRequest = RcWorkflowSearchRequest.builder().build();
    when(rcWorkflowService.getWorkflowStats(any(RcWorkflowStatsRequest.class)))
        .thenThrow(new ReceivingBadDataException("errorCode", "message"));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/returns/workflow/stats")
                .content(gson.toJson(rcWorkflowSearchRequest))
                .headers(MockHttpHeaders.getHeaders("9074", "US")))
        .andExpect(status().isBadRequest());
    verify(rcWorkflowService, times(1)).getWorkflowStats(any(RcWorkflowStatsRequest.class));
  }

  @Test
  public void downloadItemImageTest() throws Exception {

    mockMvc.perform(
        MockMvcRequestBuilders.get("/returns/workflow/1234567/image/image1.Name")
            .headers(MockHttpHeaders.getHeaders("9074", "US")));

    final String workFlowId = "1234567";
    final String fileName = "image1.Name";
    final byte[] content = "Hello World".getBytes();
    when(itemImageService.downloadItemImage(workFlowId, fileName)).thenReturn(content);
  }

  @Test
  public void testSaveComment() throws Exception {
    final String workFlowId = "e09074000100020003";
    final String comment = "comment";

    mockMvc.perform(
        MockMvcRequestBuilders.post("/returns/workflow/e09074000100020003/comment")
            .content(comment)
            .headers(MockHttpHeaders.getHeaders("9074", "US")));

    doNothing().when(rcWorkflowService).saveComment(workFlowId, comment);

    verify(rcWorkflowService, times(1)).saveComment(workFlowId, comment);
  }

  public void uploadItemImagesTest() throws Exception {

    final String workFlowId = "1234567";
    final String fileName = "image1.Name";
    final byte[] content = "Hello World".getBytes();
    MockMultipartFile itemImageFiles =
        new MockMultipartFile("content", fileName, "text/plain", content);
    MultipartFile multipartFile =
        new MockMultipartFile("sourceFile1.tmp", "Hello World".getBytes());
    MultipartFile[] imageFiles = {multipartFile};
    String url = "/returns/workflow/1234567/image";
    mockMvc.perform(
        MockMvcRequestBuilders.post("/returns/workflow/1234567/image")
            .content(String.valueOf(itemImageFiles))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .headers(MockHttpHeaders.getHeaders("9074", "US")));

    when(itemImageService.uploadItemImages(workFlowId, 2, imageFiles)).thenReturn(2);
    when(rcWorkflowService.getWorkflowImageCount(workFlowId)).thenReturn(2);
    doNothing().when(rcWorkflowService).updateWorkflowImageCount(workFlowId, 2);

    verify(itemImageService, times(1)).uploadItemImages(workFlowId, 2, imageFiles);
    verify(rcWorkflowService, times(1)).getWorkflowImageCount(workFlowId);
    verify(rcWorkflowService, times(1)).updateWorkflowImageCount(workFlowId, 2);
  }

  @Test
  public void testFetchWorkFlowId() throws Exception {
    final String itemTrackingId = "j090740000200000000002060";
    final String expectedWorkFlowId = "IJ09074000020001013";

    doReturn(expectedWorkFlowId).when(rcWorkflowService).fetchByItemTrackingId(itemTrackingId);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/returns/workflow/receivingWorkflow/{itemTrackingId}", itemTrackingId)
                .headers(MockHttpHeaders.getHeaders("9074", "US"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(expectedWorkFlowId));

    verify(rcWorkflowService, times(1)).fetchByItemTrackingId(itemTrackingId);
  }
}
