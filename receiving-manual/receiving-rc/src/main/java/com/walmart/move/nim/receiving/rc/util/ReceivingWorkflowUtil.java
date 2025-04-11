package com.walmart.move.nim.receiving.rc.util;

import static com.walmart.move.nim.receiving.rc.contants.RcConstants.POTENTIAL_FRAUD;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.rc.model.dto.request.ReceiveContainerRequest;
import com.walmart.move.nim.receiving.rc.model.gad.Disposition;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Helper class for methods related to receiving workflow
 *
 * @author m0s0mqs
 */
public class ReceivingWorkflowUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReceivingWorkflowUtil.class);

  public boolean isWorkflowCreationRequired(Disposition disposition) {
    return Objects.nonNull(disposition)
        && POTENTIAL_FRAUD.equalsIgnoreCase(disposition.getFinalDisposition());
  }

  public void validateWorkflowCreationAttributes(ReceiveContainerRequest containerRequest) {
    if (StringUtils.isEmpty(containerRequest.getWorkflowId())) {
      LOGGER.error(
          "Container cannot be created without workflowId for [dispositionType={}].",
          containerRequest.getDisposition().getFinalDisposition());
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_CREATE_CONTAINER_REQUEST,
          ExceptionDescriptionConstants.INVALID_CREATE_CONTAINER_REQUEST_WORKFLOW_ID);
    }
    if (StringUtils.isEmpty(containerRequest.getWorkflowCreateReason())) {
      LOGGER.error(
          "Container cannot be created without workflowCreateReason for [dispositionType={}].",
          containerRequest.getDisposition().getFinalDisposition());
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_CREATE_CONTAINER_REQUEST,
          ExceptionDescriptionConstants.INVALID_CREATE_CONTAINER_REQUEST_WORKFLOW_CREATE_REASON);
    }
    if (StringUtils.isEmpty(containerRequest.getScannedLabel())) {
      LOGGER.error(
          "Container cannot be created without scannedLabel for [dispositionType={}].",
          containerRequest.getDisposition().getFinalDisposition());
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_CREATE_CONTAINER_REQUEST,
          ExceptionDescriptionConstants.INVALID_CREATE_CONTAINER_REQUEST_SCANNED_LABEL);
    }
    if (StringUtils.isEmpty(containerRequest.getScannedItemLabel())) {
      LOGGER.error(
          "Container cannot be created without scannedItemLabel for [dispositionType={}].",
          containerRequest.getDisposition().getFinalDisposition());
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_CREATE_CONTAINER_REQUEST,
          ExceptionDescriptionConstants.INVALID_CREATE_CONTAINER_REQUEST_ITEM_LABEL);
    }
  }
}
