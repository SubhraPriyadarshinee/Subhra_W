package com.walmart.move.nim.receiving.rc.specification;

import static com.walmart.move.nim.receiving.rc.contants.RcConstants.*;

import com.walmart.move.nim.receiving.rc.contants.WorkflowAction;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowItemSearchCriteria;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowStatsRequest;
import java.util.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.jpa.domain.Specification;

public class WorkflowItemsSearchSpecification {

  /**
   * Builds predicates for dynamic workflow items search queries and returns specification
   *
   * @param criteria Workflow items search criteria
   * @return specification to query workflow items based on various criteria
   */
  public static Specification<ReceivingWorkflowItem> workflowItemsByCriteria(
      RcWorkflowItemSearchCriteria criteria) {
    // overrides ToPredicate method of specification
    return (root, query, cb) -> {
      Predicate predicate = cb.equal(cb.literal(Boolean.TRUE), Boolean.TRUE);

      if (Objects.isNull(criteria)) return predicate;

      List<Predicate> filterPredicate = buildFilter(root, cb, criteria);
      predicate = cb.and(filterPredicate.toArray(new Predicate[filterPredicate.size()]));

      return predicate;
    };
  }

  public static Specification<ReceivingWorkflowItem> workflowItemsCountByCriteria(
      RcWorkflowStatsRequest statsRequest) {
    RcWorkflowItemSearchCriteria workflowItemSearchCriteria =
        buildWorkflowItemSearchCriteria(statsRequest);
    return workflowItemsByCriteria(workflowItemSearchCriteria);
  }

  public static Specification<ReceivingWorkflowItem> workflowItemsNotActioned(
      RcWorkflowStatsRequest statsRequest) {
    RcWorkflowItemSearchCriteria workflowItemSearchCriteria =
        buildWorkflowItemSearchCriteria(statsRequest);
    return workflowItemsByCriteria(workflowItemSearchCriteria).and(workflowItemActionIsNull());
  }

  public static Specification<ReceivingWorkflowItem> workflowItemsCountByAction(
      RcWorkflowStatsRequest statsRequest, WorkflowAction action) {
    RcWorkflowItemSearchCriteria workflowItemSearchCriteria =
        buildWorkflowItemSearchCriteria(statsRequest, action);
    return workflowItemsByCriteria(workflowItemSearchCriteria);
  }

  public static Specification<ReceivingWorkflowItem> workflowItemsRegraded(
      RcWorkflowStatsRequest statsRequest) {
    RcWorkflowItemSearchCriteria workflowItemSearchCriteria =
        buildWorkflowItemSearchCriteria(statsRequest, WorkflowAction.NOT_FRAUD);
    return workflowItemsByCriteria(workflowItemSearchCriteria)
        .and(workflowItemTrackingIdIsNotNull());
  }

  public static Specification<ReceivingWorkflowItem> workflowItemActionIsNull() {
    return (root, query, cb) -> root.get(ACTION).isNull();
  }

  public static Specification<ReceivingWorkflowItem> workflowItemTrackingIdIsNotNull() {
    return (root, query, cb) -> root.get(ITEM_TRACKING_ID).isNotNull();
  }

  private static RcWorkflowItemSearchCriteria buildWorkflowItemSearchCriteria(
      RcWorkflowStatsRequest statsRequest) {
    return buildWorkflowItemSearchCriteria(statsRequest, null);
  }

  private static RcWorkflowItemSearchCriteria buildWorkflowItemSearchCriteria(
      RcWorkflowStatsRequest statsRequest, WorkflowAction action) {
    RcWorkflowItemSearchCriteria workflowItemSearchCriteria =
        Objects.nonNull(statsRequest)
            ? RcWorkflowItemSearchCriteria.builder()
                .fromCreateTs(statsRequest.getFromCreateTs())
                .toCreateTs(statsRequest.getToCreateTs())
                .type(statsRequest.getType())
                .actionIn(Objects.nonNull(action) ? Arrays.asList(action) : null)
                .build()
            : null;
    return workflowItemSearchCriteria;
  }

  private static List<Predicate> buildFilter(
      Root<ReceivingWorkflowItem> root,
      CriteriaBuilder cb,
      RcWorkflowItemSearchCriteria searchCriteria) {
    List<Predicate> predicates = new ArrayList<>();
    Join<ReceivingWorkflowItem, ReceivingWorkflow> workFlows = root.join(RECEIVING_WORKFLOW);
    if (Objects.nonNull(searchCriteria.getType())) {
      predicates.add(cb.equal(workFlows.get(TYPE), searchCriteria.getType()));
    }
    if (CollectionUtils.isNotEmpty(searchCriteria.getStatusIn())) {
      predicates.add(workFlows.get(STATUS).in(searchCriteria.getStatusIn()));
    }
    if (CollectionUtils.isNotEmpty(searchCriteria.getActionIn())) {
      predicates.add(root.get(ACTION).in(searchCriteria.getActionIn()));
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
    return predicates;
  }
}
