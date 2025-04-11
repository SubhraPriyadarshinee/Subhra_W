package com.walmart.move.nim.receiving.rc.specification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.rc.contants.*;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowSearchCriteria;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowSearchRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowStatsRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import javax.persistence.criteria.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.jpa.domain.Specification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WorkflowSearchSpecificationTest {

  @Mock private CriteriaBuilder criteriaBuilderMock;
  @Mock private CriteriaQuery criteriaQueryMock;
  @Mock private Root<ReceivingWorkflow> receivingWorkflowRoot;

  @Mock private Path path;

  @Mock private Join join;

  private RcWorkflowSearchRequest rcWorkflowSearchRequest;

  private RcWorkflowSearchRequest rcWorkflowSearchByID;
  private RcWorkflowStatsRequest rcWorkflowStatsRequest;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    Date d = new Date();
    rcWorkflowSearchRequest =
        RcWorkflowSearchRequest.builder()
            .criteria(
                RcWorkflowSearchCriteria.builder()
                    .fromCreateTs(d)
                    .toCreateTs(d)
                    .actionIn(Arrays.asList(WorkflowAction.FRAUD))
                    .statusIn(Arrays.asList(WorkflowStatus.CREATED))
                    .build())
            .sortBy(Collections.singletonMap(WorkflowSortColumn.createTs, SortOrder.ASC))
            .build();
    rcWorkflowSearchByID =
        RcWorkflowSearchRequest.builder()
            .criteria(RcWorkflowSearchCriteria.builder().workflowId("e09074000100020003").build())
            .sortBy(Collections.singletonMap(WorkflowSortColumn.createTs, SortOrder.DESC))
            .build();
    rcWorkflowStatsRequest =
        RcWorkflowStatsRequest.builder()
            .fromCreateTs(d)
            .toCreateTs(d)
            .type(WorkflowType.FRAUD)
            .build();
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(criteriaBuilderMock, criteriaQueryMock, receivingWorkflowRoot, join, path);
  }

  @Test
  public void testWorkflowByCriteria() {
    when(receivingWorkflowRoot.get(anyString())).thenReturn(path);
    when(receivingWorkflowRoot.join(anyString())).thenReturn(join);
    when(join.get(anyString())).thenReturn(path);
    Specification<ReceivingWorkflow> workflowSpecification =
        WorkflowSearchSpecification.workflowByCriteria(rcWorkflowSearchRequest);
    workflowSpecification.toPredicate(
        receivingWorkflowRoot, criteriaQueryMock, criteriaBuilderMock);
    verify(receivingWorkflowRoot, times(4)).get(anyString());
    verify(path, times(2)).in(anyList());
    verify(criteriaBuilderMock, times(1)).greaterThanOrEqualTo(any(), any(Date.class));
    verify(criteriaBuilderMock, times(1)).lessThanOrEqualTo(any(), any(Date.class));
    verify(criteriaQueryMock, times(1)).orderBy(anyList());
  }

  @Test
  public void testWorkflowByCriteriaWithWorkflowID() {
    when(receivingWorkflowRoot.get(anyString())).thenReturn(path);
    Specification<ReceivingWorkflow> workflowSpecification =
        WorkflowSearchSpecification.workflowByCriteria(rcWorkflowSearchByID);
    workflowSpecification.toPredicate(
        receivingWorkflowRoot, criteriaQueryMock, criteriaBuilderMock);
    verify(receivingWorkflowRoot, times(2)).get(anyString());
    verify(criteriaQueryMock, times(1)).orderBy(anyList());
  }

  @Test
  public void testWorkflowCountByCriteria() {
    when(receivingWorkflowRoot.get(anyString())).thenReturn(path);
    when(receivingWorkflowRoot.join(anyString())).thenReturn(join);
    when(join.get(anyString())).thenReturn(path);
    Specification<ReceivingWorkflow> workflowSpecification =
        WorkflowSearchSpecification.workflowCountByCriteria(rcWorkflowStatsRequest);
    workflowSpecification.toPredicate(
        receivingWorkflowRoot, criteriaQueryMock, criteriaBuilderMock);
    verify(receivingWorkflowRoot, times(3)).get(anyString());
    verify(criteriaBuilderMock, times(1)).greaterThanOrEqualTo(any(), any(Date.class));
    verify(criteriaBuilderMock, times(1)).lessThanOrEqualTo(any(), any(Date.class));
  }

  @Test
  public void testWorkflowByCriteriaByValue() {
    when(receivingWorkflowRoot.get(anyString())).thenReturn(path);
    when(receivingWorkflowRoot.join(anyString())).thenReturn(join);
    when(join.get(anyString())).thenReturn(path);
    rcWorkflowSearchRequest.getCriteria().setWorkflowId("3300830814837940");
    Specification<ReceivingWorkflow> workflowSpecification =
        WorkflowSearchSpecification.workflowByCriteria(rcWorkflowSearchRequest);
    workflowSpecification.toPredicate(
        receivingWorkflowRoot, criteriaQueryMock, criteriaBuilderMock);
    verify(receivingWorkflowRoot, times(2)).get(anyString());
  }

  @Test
  public void testWorkflowByCriteriaByType() {
    RcWorkflowSearchRequest rcWorkflowSearchRequest =
        RcWorkflowSearchRequest.builder()
            .criteria(
                RcWorkflowSearchCriteria.builder()
                    .type(WorkflowType.FRAUD)
                    .itemTrackingId("d091530000200000000048077")
                    .packageBarcodeValue("200001212434235")
                    .build())
            .sortBy(Collections.singletonMap(WorkflowSortColumn.createTs, SortOrder.ASC))
            .build();
    when(receivingWorkflowRoot.get(anyString())).thenReturn(path);
    when(receivingWorkflowRoot.join(anyString())).thenReturn(join);
    when(join.get(anyString())).thenReturn(path);
    Specification<ReceivingWorkflow> workflowSpecification =
        WorkflowSearchSpecification.workflowByCriteria(rcWorkflowSearchRequest);
    workflowSpecification.toPredicate(
        receivingWorkflowRoot, criteriaQueryMock, criteriaBuilderMock);
    verify(receivingWorkflowRoot, times(3)).get(anyString());
    verify(criteriaQueryMock, times(1)).orderBy(anyList());
  }
}
