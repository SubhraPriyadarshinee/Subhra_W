package com.walmart.move.nim.receiving.rc.transformer;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.contants.WorkflowAction;
import com.walmart.move.nim.receiving.rc.contants.WorkflowStatus;
import com.walmart.move.nim.receiving.rc.contants.WorkflowType;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.container.RcContainer;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerAdditionalAttributes;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerItem;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowAdditionalAttributes;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowCreateRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.ReceiveContainerRequest;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Transformer for ReceivingWorkflow entities and DTOs
 *
 * @author m0s0mqs
 */
public class ReceivingWorkflowTransformer {

  /**
   * Transform a receiving workflow entity to a response DTO
   *
   * @param receivingWorkflow entity
   * @return response DTO
   */
  public RcWorkflowResponse transformWorkflowEntityToDTO(ReceivingWorkflow receivingWorkflow) {
    List<RcWorkflowItem> workflowItemList = new ArrayList<>();
    receivingWorkflow
        .getWorkflowItems()
        .forEach(
            receivingWorkflowItem ->
                workflowItemList.add(transformWorkflowItemEntityToDTO(receivingWorkflowItem)));
    return RcWorkflowResponse.builder()
        .id(receivingWorkflow.getId())
        .workflowId(receivingWorkflow.getWorkflowId())
        .type(Optional.ofNullable(receivingWorkflow.getType()).map(WorkflowType::name).orElse(null))
        .packageBarcodeValue(receivingWorkflow.getPackageBarcodeValue())
        .packageBarcodeType(receivingWorkflow.getPackageBarcodeType())
        .createReason(receivingWorkflow.getCreateReason())
        .status(
            Optional.ofNullable(receivingWorkflow.getStatus())
                .map(WorkflowStatus::name)
                .orElse(null))
        .images(
            Objects.nonNull(receivingWorkflow.getImageCount())
                ? buildImageUrls(
                    receivingWorkflow.getWorkflowId(), receivingWorkflow.getImageCount())
                : null)
        .comments(receivingWorkflow.getImageComment())
        .createUser(receivingWorkflow.getCreateUser())
        .createTs(receivingWorkflow.getCreateTs())
        .lastChangedUser(receivingWorkflow.getLastChangedUser())
        .lastChangedTs(receivingWorkflow.getLastChangedTs())
        .workflowItems(workflowItemList)
        .additionalAttributes(
            Objects.nonNull(receivingWorkflow.getAdditionalAttributes())
                ? JacksonParser.convertJsonToObject(
                    receivingWorkflow.getAdditionalAttributes(),
                    RcWorkflowAdditionalAttributes.class)
                : null)
        .build();
  }

  private List<String> buildImageUrls(String workflowId, int count) {
    if (count < 1) return null;
    return IntStream.rangeClosed(1, count)
        .mapToObj(
            i ->
                File.separator
                    + RcConstants.RETURNS_WORKFLOW_URI
                    + File.separator
                    + workflowId
                    + File.separator
                    + "image"
                    + File.separator
                    + "image"
                    + i
                    + ".jpg")
        // TODO: Discuss file extension. It'll be good to pick one as constant
        .collect(Collectors.toList());
  }

  /**
   * Transforms list of receiving workflow entity to response DTO
   *
   * @param receivingWorkflows - List of receiving Workflow entity
   * @return List of workflow DTO
   */
  public List<RcWorkflowResponse> transformWorkflowEntitiesToDTO(
      List<ReceivingWorkflow> receivingWorkflows) {
    return CollectionUtils.isNotEmpty(receivingWorkflows)
        ? receivingWorkflows
            .stream()
            .map(this::transformWorkflowEntityToDTO)
            .collect(Collectors.toList())
        : Collections.emptyList();
  }

  /** Transform a receiving container request into a workflow creation request */
  public RcWorkflowCreateRequest transformContainerToWorkflowRequest(
      ReceiveContainerRequest containerRequest, WorkflowType workflowType, String itemTrackingId) {
    return RcWorkflowCreateRequest.builder()
        .workflowId(containerRequest.getWorkflowId())
        .createReason(containerRequest.getWorkflowCreateReason())
        .type(workflowType)
        .packageBarcodeValue(containerRequest.getScannedLabel())
        .packageBarcodeType(containerRequest.getScannedLabelType())
        .items(
            Collections.singletonList(
                com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowItem.builder()
                    .gtin(containerRequest.getScannedItemLabel())
                    .itemTrackingId(itemTrackingId)
                    .build()))
        .build();
  }

  /**
   * Transform a receiving workflow item entity to a response DTO
   *
   * @param receivingWorkflowItem entity
   * @return response DTO
   */
  private RcWorkflowItem transformWorkflowItemEntityToDTO(
      ReceivingWorkflowItem receivingWorkflowItem) {
    return Optional.ofNullable(receivingWorkflowItem)
        .map(
            workflowItem ->
                RcWorkflowItem.builder()
                    .id(receivingWorkflowItem.getId())
                    .itemTrackingId(receivingWorkflowItem.getItemTrackingId())
                    .action(
                        Optional.ofNullable(receivingWorkflowItem.getAction())
                            .map(WorkflowAction::name)
                            .orElse(null))
                    .gtin(receivingWorkflowItem.getGtin())
                    .createUser(receivingWorkflowItem.getCreateUser())
                    .createTs(receivingWorkflowItem.getCreateTs())
                    .lastChangedUser(receivingWorkflowItem.getLastChangedUser())
                    .lastChangedTs(receivingWorkflowItem.getLastChangedTs())
                    .build())
        .orElse(null);
  }

  public RcContainer transformWorkflowToContainerEvent(
      ReceivingWorkflow receivingWorkflow,
      ReceivingWorkflowItem receivingWorkflowItem,
      String dispositionType) {
    String trackingId = receivingWorkflow.getWorkflowId() + receivingWorkflowItem.getId();
    return RcContainer.builder()
        .trackingId(trackingId)
        .dispositionType(dispositionType)
        .messageId(TenantContext.getCorrelationId())
        .completeTs(receivingWorkflowItem.getCreateTs())
        .publishTs(receivingWorkflowItem.getCreateTs())
        .createTs(receivingWorkflowItem.getCreateTs())
        .createUser(receivingWorkflowItem.getCreateUser())
        .lastChangedTs(receivingWorkflowItem.getLastChangedTs())
        .lastChangedUser(receivingWorkflowItem.getLastChangedUser())
        .contents(
            Collections.singletonList(
                RcContainerItem.builder()
                    .gtin(receivingWorkflowItem.getGtin())
                    .quantity(1)
                    .quantityUOM(ReceivingConstants.Uom.EACHES)
                    .additionalAttributes(
                        RcContainerAdditionalAttributes.builder()
                            .proposedDispositionType(dispositionType)
                            .finalDispositionType(dispositionType)
                            .potentialFraudReason(receivingWorkflow.getCreateReason())
                            .build())
                    .build()))
        .build();
  }
}
