package com.walmart.move.nim.receiving.rc.service;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.WORKFLOW_ITEM_NOT_FOUND;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.WORKFLOW_NOT_FOUND;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.FRAUD;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.POTENTIAL_FRAUD;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.PaginatedResponse;
import com.walmart.move.nim.receiving.rc.contants.*;
import com.walmart.move.nim.receiving.rc.entity.ContainerRLog;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflowItem;
import com.walmart.move.nim.receiving.rc.model.container.RcContainer;
import com.walmart.move.nim.receiving.rc.model.dto.request.*;
import com.walmart.move.nim.receiving.rc.model.dto.response.FraudWorkflowStats;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowResponse;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowStatsResponse;
import com.walmart.move.nim.receiving.rc.model.dto.response.WorkflowTypeStats;
import com.walmart.move.nim.receiving.rc.repositories.ContainerRLogRepository;
import com.walmart.move.nim.receiving.rc.repositories.ReceivingWorkflowItemRepository;
import com.walmart.move.nim.receiving.rc.repositories.ReceivingWorkflowRepository;
import com.walmart.move.nim.receiving.rc.specification.WorkflowItemsSearchSpecification;
import com.walmart.move.nim.receiving.rc.specification.WorkflowSearchSpecification;
import com.walmart.move.nim.receiving.rc.transformer.ReceivingWorkflowTransformer;
import com.walmart.move.nim.receiving.rc.util.OrderLinesEnrichmentUtil;
import com.walmart.move.nim.receiving.rc.validator.ReceivingWorkflowValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class RcWorkflowService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RcWorkflowService.class);

  @Autowired private JmsPublisher jmsPublisher;
  @Autowired private ReceivingWorkflowRepository receivingWorkflowRepository;
  @Autowired private ContainerRLogRepository containerRLogRepository;
  @Autowired private ReceivingWorkflowItemRepository receivingWorkflowItemRepository;
  @Autowired private ReceivingWorkflowTransformer receivingWorkflowTransformer;
  @Autowired private OrderLinesEnrichmentUtil orderLinesEnrichmentUtil;
  @Autowired private ReceivingWorkflowValidator workflowValidator;
  @ManagedConfiguration private MaasTopics maasTopics;
  private Gson gsonWithDateAdapter;

  public RcWorkflowService() {
    gsonWithDateAdapter =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Transactional
  @InjectTenantFilter
  public ReceivingWorkflow createWorkflow(
      RcWorkflowCreateRequest rcWorkflowCreateRequest,
      HttpHeaders httpHeaders,
      Boolean publishEvents) {
    ReceivingWorkflow existingWorkflow =
        receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(
            rcWorkflowCreateRequest.getWorkflowId());
    workflowValidator.validateWorkflowCreateRequest(
        rcWorkflowCreateRequest, publishEvents, existingWorkflow);
    LOGGER.info("Creating new receiving workflow with request: {}", rcWorkflowCreateRequest);
    String userId =
        String.valueOf(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));

    ReceivingWorkflow receivingWorkflow =
        ReceivingWorkflow.builder()
            .packageBarcodeValue(rcWorkflowCreateRequest.getPackageBarcodeValue())
            .packageBarcodeType(rcWorkflowCreateRequest.getPackageBarcodeType())
            .createReason(rcWorkflowCreateRequest.getCreateReason())
            .workflowId(rcWorkflowCreateRequest.getWorkflowId())
            .status(WorkflowStatus.CREATED)
            .type(rcWorkflowCreateRequest.getType())
            .createUser(userId)
            .build();

    // Set the additional attributes
    if (Objects.nonNull(rcWorkflowCreateRequest.getAdditionalAttributes())) {
      receivingWorkflow.setAdditionalAttributes(
          JacksonParser.writeValueAsString(rcWorkflowCreateRequest.getAdditionalAttributes()));
    }
    if (!CollectionUtils.isEmpty(rcWorkflowCreateRequest.getItems())) {
      rcWorkflowCreateRequest
          .getItems()
          .stream()
          .filter(Objects::nonNull)
          .forEach(
              rcWorkflowItem ->
                  receivingWorkflow.addWorkflowItem(
                      ReceivingWorkflowItem.builder()
                          .gtin(rcWorkflowItem.getGtin())
                          .itemTrackingId(rcWorkflowItem.getItemTrackingId())
                          .createUser(userId)
                          .build()));
    }

    receivingWorkflowRepository.save(receivingWorkflow);
    if (publishEvents) {
      LOGGER.info("Going to publish events for workflowID: {}", receivingWorkflow.getWorkflowId());
      List<RcContainer> eventsList = createEvents(receivingWorkflow, rcWorkflowCreateRequest);
      publishEvents(eventsList, httpHeaders, ActionType.ITEM_MISSING);
    }
    return receivingWorkflow;
  }

  @Transactional
  public String fetchByItemTrackingId(String itemTrackingId) {
    ReceivingWorkflowItem receivingWorkflowItem =
        receivingWorkflowItemRepository.findByItemTrackingId(itemTrackingId);

    String workflowId = receivingWorkflowItem.getReceivingWorkflow().getWorkflowId();

    if (workflowId == null) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.WORKFLOW_NOT_FOUND_FOR_ID_ERROR_MSG, itemTrackingId);
      LOGGER.error(errorDescription);
      throw new ReceivingDataNotFoundException(WORKFLOW_NOT_FOUND, errorDescription);
    }

    return workflowId;
  }

  private List<RcContainer> createEvents(
      ReceivingWorkflow receivingWorkflow, RcWorkflowCreateRequest rcWorkflowCreateRequest) {
    List<RcContainer> rcContainerList = new ArrayList<>();
    receivingWorkflow
        .getWorkflowItems()
        .forEach(
            receivingWorkflowItem -> {
              RcContainer rcContainer =
                  receivingWorkflowTransformer.transformWorkflowToContainerEvent(
                      receivingWorkflow, receivingWorkflowItem, POTENTIAL_FRAUD);
              orderLinesEnrichmentUtil.enrichEventWithOrderLines(
                  rcWorkflowCreateRequest.getSalesOrder(),
                  rcWorkflowCreateRequest.getPackageBarcodeValue(),
                  rcWorkflowCreateRequest.getPackageBarcodeType(),
                  receivingWorkflowItem.getGtin(),
                  rcContainer);
              rcContainerList.add(rcContainer);
            });

    return rcContainerList;
  }

  private void publishEvents(
      List<RcContainer> eventsList, HttpHeaders httpHeaders, ActionType actionType) {
    eventsList.forEach(
        rcContainer -> {
          LOGGER.info("Publishing event for tracking ID: {}", rcContainer.getTrackingId());
          Map<String, Object> messageHeaders =
              ReceivingUtils.getForwardableHeadersWithRequestOriginator(httpHeaders);
          messageHeaders.put(
              ReceivingConstants.JMS_EVENT_TYPE, RcConstants.RETURNS_RECEIPT_EVENT_TYPE);
          messageHeaders.put(ReceivingConstants.ACTION_TYPE, actionType);
          messageHeaders.put(ReceivingConstants.IGNORE_SCT, true);

          String jsonObject = gsonWithDateAdapter.toJson(rcContainer);
          ReceivingJMSEvent jmsEvent = new ReceivingJMSEvent(messageHeaders, jsonObject);

          jmsPublisher.publish(maasTopics.getPubReceiptsTopic(), jmsEvent, Boolean.TRUE);
        });
  }

  @Transactional
  @InjectTenantFilter
  public RcWorkflowResponse getWorkflowById(String workflowId) {
    LOGGER.info("Fetching workflow details for workflowID: {}", workflowId);
    ReceivingWorkflow receivingWorkflow =
        receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(workflowId);
    if (Objects.isNull(receivingWorkflow)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.WORKFLOW_NOT_FOUND_FOR_ID_ERROR_MSG, workflowId);
      LOGGER.error(errorDescription);
      throw new ReceivingDataNotFoundException(WORKFLOW_NOT_FOUND, errorDescription);
    }
    LOGGER.info("Found workflow details for workflowID: {}", workflowId);
    return receivingWorkflowTransformer.transformWorkflowEntityToDTO(receivingWorkflow);
  }

  @InjectTenantFilter
  public int getWorkflowImageCount(String workflowId) {
    ReceivingWorkflow receivingWorkflow =
        receivingWorkflowRepository.getWorkflowByWorkflowId(workflowId);
    if (Objects.isNull(receivingWorkflow)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.WORKFLOW_NOT_FOUND_FOR_ID_ERROR_MSG, workflowId);
      LOGGER.error(errorDescription);
      throw new ReceivingDataNotFoundException(WORKFLOW_NOT_FOUND, errorDescription);
    }
    return receivingWorkflow.getImageCount();
  }

  @InjectTenantFilter
  public void updateWorkflowImageCount(String workflowId, int newImageCount) {
    ReceivingWorkflow receivingWorkflow =
        receivingWorkflowRepository.getWorkflowByWorkflowId(workflowId);
    if (Objects.isNull(receivingWorkflow)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.WORKFLOW_NOT_FOUND_FOR_ID_ERROR_MSG, workflowId);
      LOGGER.error(errorDescription);
      throw new ReceivingDataNotFoundException(WORKFLOW_NOT_FOUND, errorDescription);
    }
    receivingWorkflow.setImageCount(receivingWorkflow.getImageCount() + newImageCount);
    receivingWorkflowRepository.save(receivingWorkflow);
  }

  @Transactional
  @InjectTenantFilter
  public void updateWorkflow(
      String workflowId,
      RcWorkflowUpdateRequest rcWorkflowUpdateRequest,
      HttpHeaders httpHeaders,
      Boolean publishEvents) {
    LOGGER.info(
        "Beginning to update workflow with workflow ID - {}, request: {}",
        workflowId,
        rcWorkflowUpdateRequest);

    ReceivingWorkflow receivingWorkflow =
        receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(workflowId);

    workflowValidator.validateWorkflowUpdateRequest(
        workflowId, rcWorkflowUpdateRequest, publishEvents, receivingWorkflow);

    String userId =
        String.valueOf(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));

    rcWorkflowUpdateRequest
        .getWorkflowItems()
        .forEach(
            workflowItem -> {
              ReceivingWorkflowItem receivingWorkflowItem =
                  receivingWorkflow
                      .getWorkflowItems()
                      .stream()
                      .filter(
                          rcWorkflowItem ->
                              Objects.equals(rcWorkflowItem.getId(), workflowItem.getId()))
                      .findFirst()
                      .orElse(null);

              if (Objects.isNull(receivingWorkflowItem)) {
                String errorDescription =
                    String.format(
                        ExceptionDescriptionConstants.WORKFLOW_ITEM_NOT_FOUND_FOR_ID_ERROR_MSG,
                        workflowItem.getId());
                LOGGER.error(errorDescription);
                throw new ReceivingDataNotFoundException(WORKFLOW_ITEM_NOT_FOUND, errorDescription);
              }
              // update workflow items
              receivingWorkflowItem.setAction(workflowItem.getAction());
              receivingWorkflowItem.setLastChangedUser(userId);

              // if request has itemTrackingId, validate and update that as well
              if (!StringUtils.isEmpty(workflowItem.getItemTrackingId())) {
                Optional<ContainerRLog> optionalContainerRLog =
                    containerRLogRepository.findByTrackingId(workflowItem.getItemTrackingId());
                String errorDescription =
                    String.format(
                        ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG,
                        workflowItem.getItemTrackingId());

                optionalContainerRLog.orElseThrow(
                    () ->
                        new ReceivingDataNotFoundException(
                            ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription));
                receivingWorkflowItem.setItemTrackingId(workflowItem.getItemTrackingId());
              }
            });

    // If the request has additional attributes
    if (Objects.nonNull(rcWorkflowUpdateRequest.getAdditionalAttributes())) {
      receivingWorkflow.setAdditionalAttributes(
          JacksonParser.writeValueAsString(rcWorkflowUpdateRequest.getAdditionalAttributes()));
    }

    if (receivingWorkflow
        .getWorkflowItems()
        .stream()
        .allMatch(rcWorkflowItem -> Objects.nonNull(rcWorkflowItem.getAction()))) {
      LOGGER.info(
          "All workflow items have been actioned upon. Updating parent workflow status to CLOSED.");
      receivingWorkflow.setStatus(WorkflowStatus.CLOSED);
      receivingWorkflow.setCreateReason(rcWorkflowUpdateRequest.getCreateReason());
      receivingWorkflow.setLastChangedUser(userId);
    } else if (receivingWorkflow.getStatus() == WorkflowStatus.CREATED) {
      LOGGER.info("A workflow item was actioned upon. Moving parent workflow to IN_PROGRESS.");
      receivingWorkflow.setStatus(WorkflowStatus.IN_PROGRESS);
      receivingWorkflow.setLastChangedUser(userId);
    }

    receivingWorkflowRepository.save(receivingWorkflow);
    LOGGER.info(
        "Successfully updated workflow items with workflow ID - {}, updated entity - {}",
        workflowId,
        receivingWorkflow);

    if (publishEvents) {
      LOGGER.info("Going to publish events for workflowID: {}", receivingWorkflow.getWorkflowId());
      List<RcContainer> eventsList = createEvents(receivingWorkflow, rcWorkflowUpdateRequest);
      publishEvents(eventsList, httpHeaders, ActionType.ITEM_MISSING_UPDATE);
    }
  }

  private List<RcContainer> createEvents(
      ReceivingWorkflow receivingWorkflow, RcWorkflowUpdateRequest rcWorkflowUpdateRequest) {
    List<RcContainer> rcContainerList = new ArrayList<>();
    rcWorkflowUpdateRequest
        .getWorkflowItems()
        .forEach(
            rcWorkflowItemRequest -> {
              ReceivingWorkflowItem receivingWorkflowItem =
                  receivingWorkflow
                      .getWorkflowItems()
                      .stream()
                      .filter(
                          rcWorkflowItem ->
                              Objects.equals(rcWorkflowItem.getId(), rcWorkflowItemRequest.getId()))
                      .findFirst()
                      .get();
              RcContainer rcContainer =
                  receivingWorkflowTransformer.transformWorkflowToContainerEvent(
                      receivingWorkflow, receivingWorkflowItem, FRAUD);

              orderLinesEnrichmentUtil.enrichEventWithOrderLines(
                  rcWorkflowUpdateRequest.getSalesOrder(),
                  receivingWorkflow.getPackageBarcodeValue(),
                  rcWorkflowUpdateRequest.getPackageBarcodeType(),
                  receivingWorkflowItem.getGtin(),
                  rcContainer);
              rcContainerList.add(rcContainer);
            });
    return rcContainerList;
  }

  @Transactional
  @InjectTenantFilter
  public void deleteWorkflowById(String workflowId) {
    ReceivingWorkflow receivingWorkflow =
        receivingWorkflowRepository.getWorkflowDetailsByWorkflowId(workflowId);
    if (Objects.isNull(receivingWorkflow)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.WORKFLOW_NOT_FOUND_FOR_ID_ERROR_MSG, workflowId);
      LOGGER.error(errorDescription);
      throw new ReceivingDataNotFoundException(WORKFLOW_NOT_FOUND, errorDescription);
    }
    receivingWorkflowRepository.delete(receivingWorkflow);
  }

  @Transactional
  @InjectTenantFilter
  public void deleteWorkflowItemById(Long workflowItemId) {
    ReceivingWorkflowItem receivingWorkflowItem =
        receivingWorkflowItemRepository.getById(workflowItemId);
    if (Objects.isNull(receivingWorkflowItem)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.WORKFLOW_ITEM_NOT_FOUND_FOR_ID_ERROR_MSG,
              workflowItemId);
      LOGGER.error(errorDescription);
      throw new ReceivingDataNotFoundException(WORKFLOW_ITEM_NOT_FOUND, errorDescription);
    }
    receivingWorkflowItemRepository.delete(receivingWorkflowItem);
  }

  @Transactional
  @InjectTenantFilter
  public PaginatedResponse<RcWorkflowResponse> searchWorkflows(
      int pageOffset, int pageSize, RcWorkflowSearchRequest searchRequest) {
    workflowValidator.validateWorkflowSearchRequest(searchRequest);
    Pageable pageable = PageRequest.of(pageOffset - 1, pageSize);
    Page<ReceivingWorkflow> pages =
        receivingWorkflowRepository.findAll(
            WorkflowSearchSpecification.workflowByCriteria(searchRequest), pageable);
    return PaginatedResponse.<RcWorkflowResponse>builder()
        .pageOffset(pageOffset)
        .pageSize(pageSize)
        .totalCount(pages.getTotalElements())
        .totalPages(pages.getTotalPages())
        .results(receivingWorkflowTransformer.transformWorkflowEntitiesToDTO(pages.getContent()))
        .build();
  }

  @InjectTenantFilter
  @Transactional
  public RcWorkflowStatsResponse getWorkflowStats(RcWorkflowStatsRequest statsRequest) {
    workflowValidator.validateWorkflowStatsRequest(statsRequest);
    // workflow level stats
    long totalWorkflow =
        receivingWorkflowRepository.count(
            WorkflowSearchSpecification.workflowCountByCriteria(statsRequest));
    long totalClosedWorkflow =
        receivingWorkflowRepository.count(
            WorkflowSearchSpecification.workflowCountByStatus(statsRequest, WorkflowStatus.CLOSED));
    long totalOpenWorkflow = totalWorkflow - totalClosedWorkflow;
    // workflow item level stats
    long totalWorkFlowItems =
        receivingWorkflowItemRepository.count(
            WorkflowItemsSearchSpecification.workflowItemsCountByCriteria(statsRequest));
    long totalOpenWorkFlowItems =
        receivingWorkflowItemRepository.count(
            WorkflowItemsSearchSpecification.workflowItemsNotActioned(statsRequest));
    return RcWorkflowStatsResponse.builder()
        .totalWorkflows(totalWorkflow)
        .totalOpenWorkflows(totalOpenWorkflow)
        .totalClosedWorkflows(totalClosedWorkflow)
        .totalWorkflowItems(totalWorkFlowItems)
        .totalPendingWorkflowItems(totalOpenWorkFlowItems)
        .statsByWorkflowType(populateStatsByWorkflowType(statsRequest))
        .build();
  }

  private WorkflowTypeStats populateStatsByWorkflowType(RcWorkflowStatsRequest statsRequest) {
    if (WorkflowType.FRAUD.equals(statsRequest.getType())) {
      long totalFraudItems =
          receivingWorkflowItemRepository.count(
              WorkflowItemsSearchSpecification.workflowItemsCountByAction(
                  statsRequest, WorkflowAction.FRAUD));
      long totalNonFraudItem =
          receivingWorkflowItemRepository.count(
              WorkflowItemsSearchSpecification.workflowItemsCountByAction(
                  statsRequest, WorkflowAction.NOT_FRAUD));
      long totalItemsRegraded =
          receivingWorkflowItemRepository.count(
              WorkflowItemsSearchSpecification.workflowItemsRegraded(statsRequest));
      return FraudWorkflowStats.builder()
          .totalFraudItems(totalFraudItems)
          .totalRegradedItems(totalItemsRegraded)
          .totalNonFraudItems(totalNonFraudItem)
          .build();
    }

    return null;
  }

  @Transactional
  @InjectTenantFilter
  public void saveComment(String workflowId, String comment) {
    ReceivingWorkflow receivingWorkflow =
        receivingWorkflowRepository.getWorkflowByWorkflowId(workflowId);
    if (Objects.isNull(receivingWorkflow)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.WORKFLOW_NOT_FOUND_FOR_ID_ERROR_MSG, workflowId);
      LOGGER.error(errorDescription);
      throw new ReceivingDataNotFoundException(WORKFLOW_NOT_FOUND, errorDescription);
    }
    receivingWorkflow.setImageComment(comment);
    receivingWorkflowRepository.save(receivingWorkflow);
  }
}
