package com.walmart.move.nim.receiving.rc.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.PaginatedResponse;
import com.walmart.move.nim.receiving.rc.contants.SortOrder;
import com.walmart.move.nim.receiving.rc.contants.WorkflowAction;
import com.walmart.move.nim.receiving.rc.contants.WorkflowSortColumn;
import com.walmart.move.nim.receiving.rc.contants.WorkflowStatus;
import com.walmart.move.nim.receiving.rc.contants.WorkflowType;
import com.walmart.move.nim.receiving.rc.entity.ContainerRLog;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.dto.request.*;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowResponse;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowStatsResponse;
import com.walmart.move.nim.receiving.rc.model.gdm.GDMItemDetails;
import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrder;
import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrderLine;
import com.walmart.move.nim.receiving.rc.repositories.ContainerRLogRepository;
import com.walmart.move.nim.receiving.rc.repositories.ReceivingWorkflowItemRepository;
import com.walmart.move.nim.receiving.rc.repositories.ReceivingWorkflowRepository;
import com.walmart.move.nim.receiving.rc.transformer.ReceivingWorkflowTransformer;
import com.walmart.move.nim.receiving.rc.util.OrderLinesEnrichmentUtil;
import com.walmart.move.nim.receiving.rc.validator.ReceivingWorkflowValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RcWorkflowServiceTest {
  @InjectMocks private RcWorkflowService workflowService;
  @Mock private ReceivingWorkflowRepository receivingWorkflowRepository;
  @Mock private ReceivingWorkflowItemRepository receivingWorkflowItemRepository;
  @Mock private ContainerRLogRepository containerRLogRepository;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private MaasTopics maasTopics;
  @Spy private ReceivingWorkflowTransformer receivingWorkflowTransformer;
  @Spy private ReceivingWorkflowValidator receivingWorkflowValidator;
  @Spy private OrderLinesEnrichmentUtil orderLinesEnrichmentUtil;
  private Gson gson;
  private RcWorkflowCreateRequest rcWorkflowCreateRequest;
  private RcWorkflowUpdateRequest rcWorkflowUpdateRequest;
  private RcWorkflowUpdateRequest rcWorkflowUpdateRequestWithItemTrackingId;
  private ReceivingWorkflow receivingWorkflow;
  private Date currentDate;

  @BeforeClass
  public void initMocksAndFields() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(9074);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setAdditionalParams(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    gson = new Gson();
    currentDate = new Date();
    rcWorkflowCreateRequest =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("200001212434234")
            .packageBarcodeType("SO")
            .workflowId("e09074000100020003")
            .createReason("ITEM_MISSING")
            .type(WorkflowType.FRAUD)
            .items(Collections.singletonList(RcWorkflowItem.builder().gtin("000129081232").build()))
            .salesOrder(
                SalesOrder.builder()
                    .soNumber("200001212434234")
                    .lines(
                        Collections.singletonList(
                            SalesOrderLine.builder()
                                .lineNumber(10001)
                                .itemDetails(
                                    GDMItemDetails.builder().consumableGTIN("000129081232").build())
                                .build()))
                    .build())
            .build();
    rcWorkflowUpdateRequest =
        RcWorkflowUpdateRequest.builder()
            .packageBarcodeType("SO")
            .createReason("fraud reason")
            .salesOrder(
                SalesOrder.builder()
                    .soNumber("200001212434234")
                    .lines(
                        Collections.singletonList(
                            SalesOrderLine.builder()
                                .lineNumber(10001)
                                .itemDetails(
                                    GDMItemDetails.builder().consumableGTIN("00012123123").build())
                                .build()))
                    .build())
            .workflowItems(
                Collections.singletonList(
                    RcWorkflowItemUpdateRequest.builder()
                        .id(1L)
                        .action(WorkflowAction.FRAUD)
                        .build()))
            .build();
    rcWorkflowUpdateRequestWithItemTrackingId =
        RcWorkflowUpdateRequest.builder()
            .workflowItems(
                Collections.singletonList(
                    RcWorkflowItemUpdateRequest.builder()
                        .id(1L)
                        .action(WorkflowAction.FRAUD)
                        .itemTrackingId("e090740001000200030004")
                        .build()))
            .build();
    receivingWorkflow =
        ReceivingWorkflow.builder()
            .id(10001L)
            .workflowId("e09074000100020003")
            .createReason("ITEM_MISSING")
            .imageCount(0)
            .imageComment("")
            .createTs(new Date())
            .createUser("m0s0mqs")
            .lastChangedTs(new Date())
            .lastChangedUser("m0s0mqs")
            .packageBarcodeValue("200014189234108")
            .packageBarcodeType("SO")
            .status(WorkflowStatus.IN_PROGRESS)
            .type(WorkflowType.FRAUD)
            .workflowItems(
                Collections.singletonList(
                    ReceivingWorkflowItem.builder()
                        .action(WorkflowAction.FRAUD)
                        .gtin("00012123123")
                        .id(1L)
                        .createTs(new Date())
                        .createUser("m0s0mqs")
                        .itemTrackingId("12121L")
                        .lastChangedTs(new Date())
                        .lastChangedUser("m0s0mqs")
                        .build()))
            .build();
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    when(maasTopics.getPubReceiptsTopic()).thenReturn("TOPIC/RECEIVE/RECEIPTS");
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(
        receivingWorkflowRepository,
        receivingWorkflowItemRepository,
        containerRLogRepository,
        jmsPublisher);
  }

  @Test
  public void testCreateWorkflow() {
    when(receivingWorkflowRepository.save(any(ReceivingWorkflow.class)))
        .thenReturn(receivingWorkflow);
    workflowService.createWorkflow(rcWorkflowCreateRequest, HttpHeaders.EMPTY, false);
    verify(receivingWorkflowRepository, times(1)).save(any(ReceivingWorkflow.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "A receiving workflow already exists for workflowId=e09074000100020003")
  public void testCreateWorkflow_workflowAlreadyExists() {
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any()))
        .thenReturn(receivingWorkflow);
    workflowService.createWorkflow(rcWorkflowCreateRequest, HttpHeaders.EMPTY, false);
    verify(receivingWorkflowRepository, times(0)).save(any(ReceivingWorkflow.class));
  }

  @Test
  public void testCreateWorkflow_eventPublishingEnabled() {
    when(receivingWorkflowRepository.save(any(ReceivingWorkflow.class)))
        .thenReturn(receivingWorkflow);
    workflowService.createWorkflow(rcWorkflowCreateRequest, HttpHeaders.EMPTY, true);
    verify(receivingWorkflowRepository, times(1)).save(any(ReceivingWorkflow.class));
    verify(jmsPublisher, times(1)).publish(any(), any(), any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Attribute packageBarcodeType value is empty or invalid")
  public void testCreateWorkflow_eventPublishingEnabledInvalidPackageBarcodeType() {
    RcWorkflowCreateRequest requestWithoutPackageBarcodeType =
        gson.fromJson(gson.toJson(rcWorkflowCreateRequest), RcWorkflowCreateRequest.class);
    requestWithoutPackageBarcodeType.setPackageBarcodeType(null);
    workflowService.createWorkflow(requestWithoutPackageBarcodeType, HttpHeaders.EMPTY, true);
    verify(receivingWorkflowRepository, times(0)).save(any(ReceivingWorkflow.class));
    verify(jmsPublisher, times(0)).publish(any(), any(), any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Attribute salesOrder value is empty or invalid")
  public void testCreateWorkflow_eventPublishingEnabledInvalidSalesOrder() {
    RcWorkflowCreateRequest requestWithoutSalesOrder =
        gson.fromJson(gson.toJson(rcWorkflowCreateRequest), RcWorkflowCreateRequest.class);
    requestWithoutSalesOrder.setSalesOrder(null);
    workflowService.createWorkflow(requestWithoutSalesOrder, HttpHeaders.EMPTY, true);
    verify(receivingWorkflowRepository, times(0)).save(any(ReceivingWorkflow.class));
    verify(jmsPublisher, times(0)).publish(any(), any(), any());
  }

  @Test
  public void testGetWorkflowById() {
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any()))
        .thenReturn(receivingWorkflow);
    workflowService.getWorkflowById("e09074000100020003");
    verify(receivingWorkflowRepository, times(1)).getWorkflowDetailsByWorkflowId(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No receiving workflow found for ID=e09074000100020003")
  public void testGetWorkflowById_notFound() {
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any())).thenReturn(null);
    workflowService.getWorkflowById("e09074000100020003");
  }

  @Test
  public void testUpdateWorkflow() {
    when(receivingWorkflowRepository.save(any(ReceivingWorkflow.class)))
        .thenReturn(receivingWorkflow);
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any()))
        .thenReturn(receivingWorkflow);
    workflowService.updateWorkflow(
        "e09074000100020003", rcWorkflowUpdateRequest, HttpHeaders.EMPTY, false);
    verify(receivingWorkflowRepository, times(1)).save(any(ReceivingWorkflow.class));
    verify(containerRLogRepository, times(0)).findByTrackingId(any());
    verify(receivingWorkflowRepository, times(1)).getWorkflowDetailsByWorkflowId(any());
  }

  @Test
  public void testUpdateWorkflow_withItemTrackingId() {
    when(containerRLogRepository.findByTrackingId(any()))
        .thenReturn(Optional.of(new ContainerRLog()));
    when(receivingWorkflowRepository.save(any(ReceivingWorkflow.class)))
        .thenReturn(receivingWorkflow);
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any()))
        .thenReturn(receivingWorkflow);
    workflowService.updateWorkflow(
        "e09074000100020003", rcWorkflowUpdateRequestWithItemTrackingId, HttpHeaders.EMPTY, false);
    verify(receivingWorkflowRepository, times(1)).save(any(ReceivingWorkflow.class));
    verify(containerRLogRepository, times(1)).findByTrackingId(any());
    verify(receivingWorkflowRepository, times(1)).getWorkflowDetailsByWorkflowId(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Container not found for Tracking Id=e090740001000200030004")
  public void testUpdateWorkflow_itemTrackingIdNotFound() {
    when(containerRLogRepository.findByTrackingId(any())).thenReturn(Optional.empty());
    when(receivingWorkflowRepository.save(any(ReceivingWorkflow.class)))
        .thenReturn(receivingWorkflow);
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any()))
        .thenReturn(receivingWorkflow);
    workflowService.updateWorkflow(
        "e09074000100020003", rcWorkflowUpdateRequestWithItemTrackingId, HttpHeaders.EMPTY, false);
    verify(receivingWorkflowRepository, times(0)).save(any(ReceivingWorkflow.class));
    verify(containerRLogRepository, times(1)).findByTrackingId(any());
    verify(receivingWorkflowRepository, times(1)).getWorkflowDetailsByWorkflowId(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No receiving workflow found for ID=e09074000100020003")
  public void testUpdateWorkflow_workflowNotFound() {
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any())).thenReturn(null);
    workflowService.updateWorkflow(
        "e09074000100020003", rcWorkflowUpdateRequest, HttpHeaders.EMPTY, false);
    verify(receivingWorkflowRepository, times(0)).save(any(ReceivingWorkflow.class));
    verify(receivingWorkflowRepository, times(1)).getWorkflowDetailsByWorkflowId(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No receiving workflow item found for ID=2")
  public void testUpdateWorkflow_workflowItemNotFound() {
    rcWorkflowUpdateRequest =
        RcWorkflowUpdateRequest.builder()
            .workflowItems(
                Collections.singletonList(
                    RcWorkflowItemUpdateRequest.builder()
                        .id(2L)
                        .action(WorkflowAction.FRAUD)
                        .build()))
            .build();
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any()))
        .thenReturn(receivingWorkflow);
    workflowService.updateWorkflow(
        "e09074000100020003", rcWorkflowUpdateRequest, HttpHeaders.EMPTY, false);
    verify(receivingWorkflowRepository, times(0)).save(any(ReceivingWorkflow.class));
    verify(receivingWorkflowRepository, times(1)).getWorkflowDetailsByWorkflowId(any());
  }

  @Test
  public void testUpdateWorkflow_eventPublishingEnabled() {
    when(receivingWorkflowRepository.save(any(ReceivingWorkflow.class)))
        .thenReturn(receivingWorkflow);
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any()))
        .thenReturn(receivingWorkflow);
    workflowService.updateWorkflow(
        "e09074000100020003", rcWorkflowUpdateRequest, HttpHeaders.EMPTY, true);
    verify(receivingWorkflowRepository, times(1)).save(any(ReceivingWorkflow.class));
    verify(containerRLogRepository, times(0)).findByTrackingId(any());
    verify(receivingWorkflowRepository, times(1)).getWorkflowDetailsByWorkflowId(any());
    verify(jmsPublisher, times(1)).publish(any(), any(), any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Attribute packageBarcodeType value is empty or invalid")
  public void testUpdateWorkflow_eventPublishingEnabledInvalidPackageBarcodeType() {
    RcWorkflowUpdateRequest requestWithoutPackageBarcodeType =
        gson.fromJson(gson.toJson(rcWorkflowUpdateRequest), RcWorkflowUpdateRequest.class);
    requestWithoutPackageBarcodeType.setPackageBarcodeType(null);
    workflowService.updateWorkflow(
        "e09074000100020003", requestWithoutPackageBarcodeType, HttpHeaders.EMPTY, true);
    verify(receivingWorkflowRepository, times(0)).save(any(ReceivingWorkflow.class));
    verify(jmsPublisher, times(0)).publish(any(), any(), any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Attribute salesOrder value is empty or invalid")
  public void testUpdateWorkflow_eventPublishingEnabledInvalidSalesOrder() {
    RcWorkflowUpdateRequest requestWithoutSalesOrder =
        gson.fromJson(gson.toJson(rcWorkflowUpdateRequest), RcWorkflowUpdateRequest.class);
    requestWithoutSalesOrder.setSalesOrder(null);
    workflowService.updateWorkflow(
        "e09074000100020003", requestWithoutSalesOrder, HttpHeaders.EMPTY, true);
    verify(receivingWorkflowRepository, times(0)).save(any(ReceivingWorkflow.class));
    verify(jmsPublisher, times(0)).publish(any(), any(), any());
  }

  @Test(
      expectedExceptions = ReceivingNotImplementedException.class,
      expectedExceptionsMessageRegExp = "Publishing events for non fraud action is not supported!")
  public void testUpdateWorkflow_eventPublishingEnabledWithNonFraudItems() {
    RcWorkflowUpdateRequest requestWithNonFraudItems =
        gson.fromJson(gson.toJson(rcWorkflowUpdateRequest), RcWorkflowUpdateRequest.class);
    requestWithNonFraudItems.setWorkflowItems(
        Collections.singletonList(
            RcWorkflowItemUpdateRequest.builder().id(1L).action(WorkflowAction.NOT_FRAUD).build()));
    workflowService.updateWorkflow(
        "e09074000100020003", requestWithNonFraudItems, HttpHeaders.EMPTY, true);
    verify(receivingWorkflowRepository, times(0)).save(any(ReceivingWorkflow.class));
    verify(jmsPublisher, times(0)).publish(any(), any(), any());
  }

  @Test
  public void testDeleteWorkflowById() {
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any()))
        .thenReturn(receivingWorkflow);
    doNothing().when(receivingWorkflowRepository).delete(any(ReceivingWorkflow.class));
    workflowService.deleteWorkflowById("e09074000100020003");
    verify(receivingWorkflowRepository, times(1)).delete(any(ReceivingWorkflow.class));
    verify(receivingWorkflowRepository, times(1)).getWorkflowDetailsByWorkflowId(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No receiving workflow found for ID=e09074000100020003")
  public void testDeleteWorkflowById_notFound() {
    when(receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(any())).thenReturn(null);
    workflowService.deleteWorkflowById("e09074000100020003");
    verify(receivingWorkflowRepository, times(1)).getWorkflowDetailsByWorkflowId(any());
  }

  @Test
  public void testDeleteWorkflowItemById() {
    when(receivingWorkflowItemRepository.getById(any()))
        .thenReturn(ReceivingWorkflowItem.builder().build());
    doNothing().when(receivingWorkflowItemRepository).delete(any(ReceivingWorkflowItem.class));
    workflowService.deleteWorkflowItemById(10001L);
    verify(receivingWorkflowItemRepository, times(1)).delete(any(ReceivingWorkflowItem.class));
    verify(receivingWorkflowItemRepository, times(1)).getById(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No receiving workflow item found for ID=10001")
  public void testDeleteWorkflowItemById_notFound() {
    when(receivingWorkflowItemRepository.getById(any())).thenReturn(null);
    workflowService.deleteWorkflowItemById(10001L);
    verify(receivingWorkflowItemRepository, times(1)).getById(any());
  }

  @Test
  public void testSearchWorkflows() {
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
    when(receivingWorkflowRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl(Arrays.asList(receivingWorkflow), PageRequest.of(1, 10), 1L));
    PaginatedResponse<RcWorkflowResponse> response =
        workflowService.searchWorkflows(1, 10, rcWorkflowSearchRequest);
    verify(receivingWorkflowRepository, times(1))
        .findAll(any(Specification.class), any(Pageable.class));
    assertNotNull(response);
    assertEquals(10, response.getPageSize());
    assertEquals(1, response.getPageOffset());
    assertNotNull(response.getResults());
    assertEquals(1, response.getResults().size());
    assertEquals(
        "e09074000100020003", response.getResults().stream().findFirst().get().getWorkflowId());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Attribute fromCreateTs value is empty or invalid")
  public void testSearchWorkflowsFromDateMissing() {
    // Throw exception if fromDate is missing but toDate is present in request
    RcWorkflowSearchRequest rcWorkflowSearchRequest =
        RcWorkflowSearchRequest.builder()
            .criteria(
                RcWorkflowSearchCriteria.builder()
                    .workflowId("e09074000100020003")
                    .toCreateTs(currentDate)
                    .actionIn(Arrays.asList(WorkflowAction.FRAUD))
                    .statusIn(Arrays.asList(WorkflowStatus.CREATED))
                    .build())
            .build();
    workflowService.searchWorkflows(10, 10, rcWorkflowSearchRequest);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Attribute toCreateTs value is empty or invalid")
  public void testSearchWorkflowsInvalidFromDate() {
    Date tomorrow = new Date(currentDate.getTime() + (1000 * 60 * 60 * 24));
    // Throw exception if toDate is less than fromDate
    RcWorkflowSearchRequest rcWorkflowSearchRequest =
        RcWorkflowSearchRequest.builder()
            .criteria(
                RcWorkflowSearchCriteria.builder()
                    .workflowId("e09074000100020003")
                    .fromCreateTs(tomorrow)
                    .toCreateTs(currentDate)
                    .actionIn(Arrays.asList(WorkflowAction.FRAUD))
                    .statusIn(Arrays.asList(WorkflowStatus.CREATED))
                    .build())
            .build();
    workflowService.searchWorkflows(10, 10, rcWorkflowSearchRequest);
  }

  @Test
  public void testGetWorkflowStats() {
    Date currentDate = new Date();
    RcWorkflowStatsRequest rcWorkflowStatsRequest =
        RcWorkflowStatsRequest.builder()
            .fromCreateTs(currentDate)
            .toCreateTs(currentDate)
            .type(WorkflowType.FRAUD)
            .build();
    when(receivingWorkflowItemRepository.count(any(Specification.class))).thenReturn(2L);
    when(receivingWorkflowRepository.count(any(Specification.class))).thenReturn(1L);
    RcWorkflowStatsResponse response = workflowService.getWorkflowStats(rcWorkflowStatsRequest);
    verify(receivingWorkflowRepository, times(2)).count(any(Specification.class));
    verify(receivingWorkflowItemRepository, times(5)).count(any(Specification.class));
    assertNotNull(response);
    assertEquals(0, response.getTotalOpenWorkflows());
    assertEquals(1, response.getTotalWorkflows());
    assertEquals(2, response.getTotalWorkflowItems());
    assertNotNull(response.getStatsByWorkflowType());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Attribute toCreateTs value is empty or invalid")
  public void testGetWorkflowStatsFailure() {
    Date tomorrow = new Date(currentDate.getTime() + (1000 * 60 * 60 * 24));
    // Throw exception if toDate is less than fromDate
    RcWorkflowStatsRequest rcWorkflowStatsRequest =
        RcWorkflowStatsRequest.builder()
            .fromCreateTs(tomorrow)
            .toCreateTs(currentDate)
            .type(WorkflowType.FRAUD)
            .build();
    workflowService.getWorkflowStats(rcWorkflowStatsRequest);
  }

  @Test
  public void testGetWorkflowImageCount() {
    final String workFlowId = "e09074000100020003";
    when(receivingWorkflowRepository.getWorkflowByWorkflowId(workFlowId))
        .thenReturn(receivingWorkflow);

    workflowService.getWorkflowImageCount(workFlowId);

    verify(receivingWorkflowRepository, times(1)).getWorkflowByWorkflowId(workFlowId);
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No receiving workflow found for ID=e09074000100020003")
  public void testGetWorkflowImageCountWhenWorkFlowIsNull() {
    final String workFlowId = "e09074000100020003";
    when(receivingWorkflowRepository.getWorkflowByWorkflowId(workFlowId)).thenReturn(null);

    workflowService.getWorkflowImageCount(workFlowId);

    verify(receivingWorkflowRepository, times(1)).getWorkflowByWorkflowId(workFlowId);
  }

  @Test
  public void testUpdateWorkflowImageCount() {
    final String workFlowId = "e09074000100020003";
    when(receivingWorkflowRepository.getWorkflowByWorkflowId(workFlowId))
        .thenReturn(receivingWorkflow);

    workflowService.updateWorkflowImageCount(workFlowId, 2);

    verify(receivingWorkflowRepository, times(1)).getWorkflowByWorkflowId(workFlowId);
    verify(receivingWorkflowRepository, times(1)).save(receivingWorkflow);
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No receiving workflow found for ID=e09074000100020003")
  public void testUpdateWorkflowImageCountWhenWorkFlowIsNull() {
    final String workFlowId = "e09074000100020003";
    when(receivingWorkflowRepository.getWorkflowByWorkflowId(workFlowId)).thenReturn(null);

    workflowService.updateWorkflowImageCount(workFlowId, 2);

    verify(receivingWorkflowRepository, times(1)).getWorkflowByWorkflowId(workFlowId);
    verify(receivingWorkflowRepository, times(0)).save(receivingWorkflow);
  }

  @Test
  public void testSaveComment() {
    final String workFlowId = "e09074000100020003";
    final String comment = "comment";
    when(receivingWorkflowRepository.getWorkflowByWorkflowId(workFlowId))
        .thenReturn(receivingWorkflow);

    workflowService.saveComment(workFlowId, comment);

    verify(receivingWorkflowRepository, times(1)).getWorkflowByWorkflowId(workFlowId);
    verify(receivingWorkflowRepository, times(1)).save(receivingWorkflow);
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No receiving workflow found for ID=e09074000100020003")
  public void testSaveCommentWhenWorkFlowIsNull() {
    final String workFlowId = "e09074000100020003";
    final String comment = "comment";
    when(receivingWorkflowRepository.getWorkflowByWorkflowId(workFlowId)).thenReturn(null);

    workflowService.saveComment(workFlowId, comment);

    verify(receivingWorkflowRepository, times(1)).getWorkflowByWorkflowId(workFlowId);
    verify(receivingWorkflowRepository, times(0)).save(receivingWorkflow);
  }

  @Test
  public void testFetchByItemTrackingId() {
    final String itemTrackingId = "j090740000200000000002060";
    final String expectedWorkflowId = "IJ09074000020001013";

    ReceivingWorkflow mockReceivingWorkflow = new ReceivingWorkflow();
    mockReceivingWorkflow.setWorkflowId(expectedWorkflowId);

    ReceivingWorkflowItem mockReceivingWorkflowItem = new ReceivingWorkflowItem();
    mockReceivingWorkflowItem.setReceivingWorkflow(mockReceivingWorkflow);

    when(receivingWorkflowItemRepository.findByItemTrackingId(itemTrackingId))
        .thenReturn(mockReceivingWorkflowItem);

    String actualWorkflowId = workflowService.fetchByItemTrackingId(itemTrackingId);

    verify(receivingWorkflowItemRepository, times(1)).findByItemTrackingId(itemTrackingId);
    assertEquals(expectedWorkflowId, actualWorkflowId);
  }

  @Test
  public void testFetchByItemTrackingId_WorkflowIdNotFound() {
    final String itemTrackingId = "j090740000200000000002060";

    ReceivingWorkflow mockReceivingWorkflow = new ReceivingWorkflow();
    mockReceivingWorkflow.setWorkflowId(null);

    ReceivingWorkflowItem mockReceivingWorkflowItem = new ReceivingWorkflowItem();
    mockReceivingWorkflowItem.setReceivingWorkflow(mockReceivingWorkflow);

    when(receivingWorkflowItemRepository.findByItemTrackingId(itemTrackingId))
        .thenReturn(mockReceivingWorkflowItem);

    assertThrows(
        ReceivingDataNotFoundException.class,
        () -> {
          workflowService.fetchByItemTrackingId(itemTrackingId);
        });

    verify(receivingWorkflowItemRepository, times(1)).findByItemTrackingId(itemTrackingId);
  }

  @Test
  public void testSearchWorkflowsByPackageBarcodeValue() {
    RcWorkflowSearchRequest rcWorkflowSearchRequest =
        RcWorkflowSearchRequest.builder()
            .criteria(
                RcWorkflowSearchCriteria.builder()
                    .type(WorkflowType.FRAUD)
                    .packageBarcodeValue("200001212434235")
                    .itemTrackingId("e090740001000200224")
                    .build())
            .sortBy(Collections.singletonMap(WorkflowSortColumn.createTs, SortOrder.ASC))
            .build();
    when(receivingWorkflowRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl(Arrays.asList(receivingWorkflow), PageRequest.of(1, 10), 1L));
    PaginatedResponse<RcWorkflowResponse> response =
        workflowService.searchWorkflows(1, 10, rcWorkflowSearchRequest);
    verify(receivingWorkflowRepository, times(1))
        .findAll(any(Specification.class), any(Pageable.class));
    assertNotNull(response);
    assertEquals(10, response.getPageSize());
    assertEquals(1, response.getPageOffset());
    assertNotNull(response.getResults());
    assertEquals(1, response.getResults().size());
    assertEquals(
        "e09074000100020003", response.getResults().stream().findFirst().get().getWorkflowId());
  }

  @Test
  public void testCreateWorkflowWithAdditionalAttributes() {
    RcWorkflowAdditionalAttributes additionalAttribute = new RcWorkflowAdditionalAttributes();
    additionalAttribute.setGtin("123456");
    RcWorkflowCreateRequest rcWorkflowCreateRequestWithAdditionalAttributes =
        RcWorkflowCreateRequest.builder()
            .packageBarcodeValue("1000980784528")
            .packageBarcodeType("SO")
            .workflowId("e09074000100045673")
            .createReason("Item Mismatch")
            .type(WorkflowType.FRAUD)
            .additionalAttributes(additionalAttribute)
            .build();
    when(receivingWorkflowRepository.save(any(ReceivingWorkflow.class)))
        .thenReturn(receivingWorkflow);
    workflowService.createWorkflow(
        rcWorkflowCreateRequestWithAdditionalAttributes, HttpHeaders.EMPTY, false);
    verify(receivingWorkflowRepository, times(1)).save(any(ReceivingWorkflow.class));
  }
}
