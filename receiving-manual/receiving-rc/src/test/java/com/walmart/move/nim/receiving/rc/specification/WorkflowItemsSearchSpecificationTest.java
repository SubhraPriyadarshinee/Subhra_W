package com.walmart.move.nim.receiving.rc.specification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.rc.contants.WorkflowAction;
import com.walmart.move.nim.receiving.rc.contants.WorkflowType;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowStatsRequest;
import java.util.Date;
import javax.persistence.criteria.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.jpa.domain.Specification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WorkflowItemsSearchSpecificationTest {

  @Mock private CriteriaBuilder criteriaBuilderMock;
  @Mock private CriteriaQuery criteriaQueryMock;
  @Mock private Root<ReceivingWorkflowItem> receivingWorkflowItemRoot;
  @Mock private Path path;

  @Mock private Join join;

  private RcWorkflowStatsRequest rcWorkflowStatsRequest;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    Date d = new Date();
    rcWorkflowStatsRequest =
        RcWorkflowStatsRequest.builder()
            .fromCreateTs(d)
            .toCreateTs(d)
            .type(WorkflowType.FRAUD)
            .build();
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(criteriaBuilderMock, criteriaQueryMock, receivingWorkflowItemRoot, join, path);
  }

  @Test
  public void testWorkflowItemCountByCriteria() {
    when(receivingWorkflowItemRoot.get(anyString())).thenReturn(path);
    when(receivingWorkflowItemRoot.join(anyString())).thenReturn(join);
    when(join.get(anyString())).thenReturn(path);
    Specification<ReceivingWorkflowItem> workflowItemSpecification =
        WorkflowItemsSearchSpecification.workflowItemsCountByCriteria(rcWorkflowStatsRequest);
    workflowItemSpecification.toPredicate(
        receivingWorkflowItemRoot, criteriaQueryMock, criteriaBuilderMock);
    verify(receivingWorkflowItemRoot, times(2)).get(anyString());
    verify(criteriaBuilderMock, times(1)).greaterThanOrEqualTo(any(), any(Date.class));
    verify(criteriaBuilderMock, times(1)).lessThanOrEqualTo(any(), any(Date.class));
  }

  @Test
  public void testWorkflowItemCountBySearchCriteriaAndAction() {
    when(receivingWorkflowItemRoot.get(anyString())).thenReturn(path);
    when(receivingWorkflowItemRoot.join(anyString())).thenReturn(join);
    when(join.get(anyString())).thenReturn(path);
    Specification<ReceivingWorkflowItem> workflowItemSpecification =
        WorkflowItemsSearchSpecification.workflowItemsCountByAction(
            rcWorkflowStatsRequest, WorkflowAction.FRAUD);
    workflowItemSpecification.toPredicate(
        receivingWorkflowItemRoot, criteriaQueryMock, criteriaBuilderMock);
    verify(receivingWorkflowItemRoot, times(3)).get(anyString());
    verify(path, times(1)).in(anyList());
    verify(criteriaBuilderMock, times(1)).greaterThanOrEqualTo(any(), any(Date.class));
    verify(criteriaBuilderMock, times(1)).lessThanOrEqualTo(any(), any(Date.class));
  }

  @Test
  public void testWorkflowItemsCountNotActioned() {
    when(receivingWorkflowItemRoot.get(anyString())).thenReturn(path);
    when(receivingWorkflowItemRoot.join(anyString())).thenReturn(join);
    when(join.get(anyString())).thenReturn(path);
    Specification<ReceivingWorkflowItem> workflowItemSpecification =
        WorkflowItemsSearchSpecification.workflowItemsNotActioned(rcWorkflowStatsRequest);
    workflowItemSpecification.toPredicate(
        receivingWorkflowItemRoot, criteriaQueryMock, criteriaBuilderMock);
    verify(receivingWorkflowItemRoot, times(3)).get(anyString());
    verify(path, times(1)).isNull();
    verify(criteriaBuilderMock, times(1)).greaterThanOrEqualTo(any(), any(Date.class));
    verify(criteriaBuilderMock, times(1)).lessThanOrEqualTo(any(), any(Date.class));
  }

  @Test
  public void testWorkflowItemsRegraded() {
    when(receivingWorkflowItemRoot.get(anyString())).thenReturn(path);
    when(receivingWorkflowItemRoot.join(anyString())).thenReturn(join);
    when(join.get(anyString())).thenReturn(path);
    Specification<ReceivingWorkflowItem> workflowItemSpecification =
        WorkflowItemsSearchSpecification.workflowItemsRegraded(rcWorkflowStatsRequest);
    workflowItemSpecification.toPredicate(
        receivingWorkflowItemRoot, criteriaQueryMock, criteriaBuilderMock);
    verify(receivingWorkflowItemRoot, times(4)).get(anyString());
    verify(path, times(1)).in(anyList());
    verify(path, times(1)).isNotNull();
    verify(criteriaBuilderMock, times(1)).greaterThanOrEqualTo(any(), any(Date.class));
    verify(criteriaBuilderMock, times(1)).lessThanOrEqualTo(any(), any(Date.class));
  }
}
