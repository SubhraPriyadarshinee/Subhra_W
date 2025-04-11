package com.walmart.move.nim.receiving.rc.specification;

import static com.walmart.move.nim.receiving.rc.contants.RcConstants.*;

import com.walmart.move.nim.receiving.rc.contants.SortOrder;
import com.walmart.move.nim.receiving.rc.contants.WorkflowSortColumn;
import com.walmart.move.nim.receiving.rc.contants.WorkflowStatus;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowSearchCriteria;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowSearchRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowStatsRequest;
import java.util.*;
import javax.persistence.criteria.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

public class WorkflowSearchSpecification {

  /**
   * Builds predicates for dynamic workflow search queries and returns specification
   *
   * @param request Workflow search request
   * @return specification to query workflow based on various criteria
   */
  public static Specification<ReceivingWorkflow> workflowByCriteria(
      final RcWorkflowSearchRequest request) {
    // overrides ToPredicate method of specification
    return (root, query, cb) -> {
      Predicate predicate = cb.equal(cb.literal(Boolean.TRUE), Boolean.TRUE);

      if (Objects.isNull(request)) return predicate;

      if (Objects.nonNull(request.getCriteria())) {
        List<Predicate> filterPredicate = buildFilter(root, cb, request.getCriteria());
        predicate = cb.and(filterPredicate.toArray(new Predicate[filterPredicate.size()]));
      }

      if (Objects.nonNull(request.getSortBy()) && !request.getSortBy().isEmpty()) {
        query.orderBy(buildOrderBy(root, cb, request.getSortBy()));
      }

      return predicate;
    };
  }

  public static Specification<ReceivingWorkflow> workflowCountByCriteria(
      final RcWorkflowStatsRequest statsRequest) {
    return workflowByCriteria(buildSearchRequest(statsRequest, null));
  }

  public static Specification<ReceivingWorkflow> workflowCountByStatus(
      RcWorkflowStatsRequest statsRequest, WorkflowStatus status) {
    return workflowByCriteria(buildSearchRequest(statsRequest, status));
  }

  private static RcWorkflowSearchRequest buildSearchRequest(
      RcWorkflowStatsRequest statsRequest, WorkflowStatus status) {
    return Objects.nonNull(statsRequest)
        ? RcWorkflowSearchRequest.builder()
            .criteria(
                RcWorkflowSearchCriteria.builder()
                    .fromCreateTs(statsRequest.getFromCreateTs())
                    .toCreateTs(statsRequest.getToCreateTs())
                    .type(statsRequest.getType())
                    .statusIn(Objects.nonNull(status) ? Arrays.asList(status) : null)
                    .build())
            .build()
        : null;
  }

  private static List<Order> buildOrderBy(
      Root<ReceivingWorkflow> root, CriteriaBuilder cb, Map<WorkflowSortColumn, SortOrder> sortBy) {
    List<Order> orders = new ArrayList<>();
    sortBy.forEach(
        (field, sortDirection) ->
            orders.add(
                (sortDirection.equals(SortOrder.ASC)
                    ? cb.asc(root.get(field.toString()))
                    : cb.desc(root.get(field.toString())))));
    return orders;
  }

  private static List<Predicate> buildFilter(
      Root<ReceivingWorkflow> root, CriteriaBuilder cb, RcWorkflowSearchCriteria searchCriteria) {
    List<Predicate> predicates = new ArrayList<>();
    if (StringUtils.isNotBlank(searchCriteria.getWorkflowId())) {
      String patterntoDifferentiate = "^[A-Za-z].*";
      if (searchCriteria.getWorkflowId().matches(patterntoDifferentiate)) {
        predicates.add(cb.equal(root.get(WORKFLOW_ID), searchCriteria.getWorkflowId()));
      } else {
        predicates.add(cb.equal(root.get(PACKAGE_BARCODE_VALUE), searchCriteria.getWorkflowId()));
      }
      // if workflow ID is one of the search criteria, other criteria is not relevant
      return predicates;
    }

    if (Objects.nonNull(searchCriteria.getType())) {
      predicates.add(cb.equal(root.get(TYPE), searchCriteria.getType()));
    }
    if (CollectionUtils.isNotEmpty(searchCriteria.getStatusIn())) {
      predicates.add(root.get(STATUS).in(searchCriteria.getStatusIn()));
    }
    if (CollectionUtils.isNotEmpty(searchCriteria.getActionIn())) {
      Join<ReceivingWorkflow, ReceivingWorkflowItem> workFlowItems = root.join(WORKFLOW_ITEMS);
      predicates.add(workFlowItems.get(ACTION).in(searchCriteria.getActionIn()));
    }
    if (Objects.nonNull(searchCriteria.getFromCreateTs())) {
      predicates.add(
          cb.greaterThanOrEqualTo(root.get(CREATE_TS), searchCriteria.getFromCreateTs()));
      // if To date is missing, then toDate=currentDate
      predicates.add(
          cb.lessThanOrEqualTo(
              root.get(CREATE_TS),
              Objects.nonNull(searchCriteria.getToCreateTs())
                  ? searchCriteria.getToCreateTs()
                  : new Date()));
    }
    if (StringUtils.isNotBlank(searchCriteria.getItemTrackingId())) {
      Join<ReceivingWorkflow, ReceivingWorkflowItem> workFlowItems = root.join(WORKFLOW_ITEMS);
      predicates.add(
          cb.equal(workFlowItems.get(ITEM_TRACKING_ID), (searchCriteria.getItemTrackingId())));
    }
    if (StringUtils.isNotBlank(searchCriteria.getPackageBarcodeValue())) {
      predicates.add(
          cb.equal(root.get(PACKAGE_BARCODE_VALUE), searchCriteria.getPackageBarcodeValue()));
    }
    return predicates;
  }
}
