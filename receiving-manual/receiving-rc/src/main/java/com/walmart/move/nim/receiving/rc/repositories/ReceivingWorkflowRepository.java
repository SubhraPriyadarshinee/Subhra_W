package com.walmart.move.nim.receiving.rc.repositories;

import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Crud repository for ReceivingWorkflow entity
 *
 * @author m0s0mqs
 */
public interface ReceivingWorkflowRepository
    extends JpaRepository<ReceivingWorkflow, Long>, JpaSpecificationExecutor<ReceivingWorkflow> {
  /**
   * Get workflow and workflow item details based on workflow ID. Join fetch ensures only one query
   * is fired to the database instead of one query per workflow and workflow item.
   *
   * @param workflowId unique workflow identifier
   * @return workflow details with eagerly fetched workflow item details
   */
  @Query(
      "SELECT rc FROM ReceivingWorkflow rc LEFT JOIN FETCH rc.workflowItems where rc.workflowId = :workflowId")
  ReceivingWorkflow getWorkflowDetailsByWorkflowId(String workflowId);

  /**
   * Get workflow based on workflow ID. Used in usecases where items are not needed. Ex: Fraud image
   * capture
   *
   * @param workflowId unique workflow identifier
   * @return workflow details without workflow item details
   */
  ReceivingWorkflow getWorkflowByWorkflowId(String workflowId);
}
