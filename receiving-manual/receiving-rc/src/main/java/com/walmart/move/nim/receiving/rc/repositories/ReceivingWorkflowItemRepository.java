package com.walmart.move.nim.receiving.rc.repositories;

import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;


/**
 * Crud repository for ReceivingWorkflowItem entity
 *
 * @author m0s0mqs
 */
public interface ReceivingWorkflowItemRepository
    extends JpaRepository<ReceivingWorkflowItem, Long>,
        JpaSpecificationExecutor<ReceivingWorkflowItem> {
  ReceivingWorkflowItem getById(Long workflowItemId);

  ReceivingWorkflowItem findByItemTrackingId(String itemTrackingId);

  List<ReceivingWorkflowItem> findByItemTrackingIdIn(List<String> itemTrackingIds);
}
