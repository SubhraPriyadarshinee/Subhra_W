package com.walmart.move.nim.receiving.rc.validator;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVALID_WORKFLOW_REQUEST;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.WORKFLOW_NOT_FOUND;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants.INVALID_ATTR_ERR_MSG;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants.PUBLISH_EVENTS_FOR_NON_FRAUD_NOT_SUPPORTED_ERROR_MSG;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.contants.WorkflowAction;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowCreateRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowSearchRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowStatsRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowUpdateRequest;
import java.util.Date;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class ReceivingWorkflowValidator {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReceivingWorkflowValidator.class);

  public void validateWorkflowCreateRequest(
      RcWorkflowCreateRequest rcWorkflowCreateRequest,
      boolean publishEvents,
      ReceivingWorkflow existingWorkflow) {
    if (publishEvents) {
      LOGGER.info("Publishing events is enabled. Checking if request has all required attributes");
      if (Objects.isNull(rcWorkflowCreateRequest.getSalesOrder())
          || StringUtils.isEmpty(rcWorkflowCreateRequest.getSalesOrder().getSoNumber())
          || CollectionUtils.isEmpty(rcWorkflowCreateRequest.getSalesOrder().getLines())) {
        String errorDescription = String.format(INVALID_ATTR_ERR_MSG, "salesOrder");
        LOGGER.error(errorDescription);
        throw new ReceivingBadDataException(INVALID_WORKFLOW_REQUEST, errorDescription);
      }

      if (StringUtils.isEmpty(rcWorkflowCreateRequest.getPackageBarcodeType())) {
        String errorDescription = String.format(INVALID_ATTR_ERR_MSG, "packageBarcodeType");
        LOGGER.error(errorDescription);
        throw new ReceivingBadDataException(INVALID_WORKFLOW_REQUEST, errorDescription);
      }
    }
    LOGGER.info(
        "Checking if a workflow already exists for workflow ID: {}",
        rcWorkflowCreateRequest.getWorkflowId());
    if (Objects.nonNull(existingWorkflow)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.WORKFLOW_ALREADY_EXISTS_FOR_ID_ERROR_MSG,
              rcWorkflowCreateRequest.getWorkflowId());
      LOGGER.error(errorDescription);
      throw new ReceivingBadDataException(INVALID_WORKFLOW_REQUEST, errorDescription);
    }
  }

  public void validateWorkflowUpdateRequest(
      String workflowId,
      RcWorkflowUpdateRequest rcWorkflowUpdateRequest,
      boolean publishEvents,
      ReceivingWorkflow receivingWorkflow) {
    // events can be published only for FRAUD actions via workflow update API
    if (publishEvents) {
      LOGGER.info("Publishing events is enabled. Checking if request has all required attributes");
      boolean hasNonFraudItems =
          rcWorkflowUpdateRequest
              .getWorkflowItems()
              .stream()
              .anyMatch(workflowItem -> !WorkflowAction.FRAUD.equals(workflowItem.getAction()));
      if (hasNonFraudItems) {
        LOGGER.error(
            PUBLISH_EVENTS_FOR_NON_FRAUD_NOT_SUPPORTED_ERROR_MSG + " Request: {}",
            rcWorkflowUpdateRequest);
        throw new ReceivingNotImplementedException(
            INVALID_WORKFLOW_REQUEST, PUBLISH_EVENTS_FOR_NON_FRAUD_NOT_SUPPORTED_ERROR_MSG);
      }

      if (Objects.isNull(rcWorkflowUpdateRequest.getSalesOrder())
          || StringUtils.isEmpty(rcWorkflowUpdateRequest.getSalesOrder().getSoNumber())
          || CollectionUtils.isEmpty(rcWorkflowUpdateRequest.getSalesOrder().getLines())) {
        String errorDescription = String.format(INVALID_ATTR_ERR_MSG, "salesOrder");
        LOGGER.error(errorDescription);
        throw new ReceivingBadDataException(INVALID_WORKFLOW_REQUEST, errorDescription);
      }

      if (StringUtils.isEmpty(rcWorkflowUpdateRequest.getPackageBarcodeType())) {
        String errorDescription = String.format(INVALID_ATTR_ERR_MSG, "packageBarcodeType");
        LOGGER.error(errorDescription);
        throw new ReceivingBadDataException(INVALID_WORKFLOW_REQUEST, errorDescription);
      }
    }

    if (Objects.isNull(receivingWorkflow)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.WORKFLOW_NOT_FOUND_FOR_ID_ERROR_MSG, workflowId);
      LOGGER.error(errorDescription);
      throw new ReceivingDataNotFoundException(WORKFLOW_NOT_FOUND, errorDescription);
    }
  }

  public void validateWorkflowSearchRequest(RcWorkflowSearchRequest searchRequest) {
    if (Objects.isNull(searchRequest.getCriteria())) return;
    validateWorkflowSearchDates(
        searchRequest.getCriteria().getFromCreateTs(), searchRequest.getCriteria().getToCreateTs());
  }

  public void validateWorkflowStatsRequest(RcWorkflowStatsRequest statsRequest) {
    if (Objects.isNull(statsRequest)) return;
    validateWorkflowSearchDates(statsRequest.getFromCreateTs(), statsRequest.getToCreateTs());
  }

  public void validateWorkflowSearchDates(Date fromDate, Date toDate) {
    if (Objects.nonNull(toDate) && Objects.isNull(fromDate)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_WORKFLOW_REQUEST,
          String.format(
              ExceptionDescriptionConstants.INVALID_ATTR_ERR_MSG, RcConstants.FROM_CREATE_TS));
    }
    if (Objects.nonNull(toDate) && Objects.nonNull(fromDate) && fromDate.after(toDate)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_WORKFLOW_REQUEST,
          String.format(
              ExceptionDescriptionConstants.INVALID_ATTR_ERR_MSG, RcConstants.TO_CREATE_TS));
    }
  }
}
