package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class ContainerAdjustmentValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerAdjustmentValidator.class);

  @Autowired private ContainerRepository containerRepository;
  @Autowired private ContainerPersisterService containerPersisterService;

  /**
   * @param container
   * @return CancelContainerResponse
   */
  public CancelContainerResponse validateContainerForAdjustment(Container container) {
    CancelContainerResponse cancelContainerResponse =
        validateContainerForParentContainer(container);
    if (!CollectionUtils.isEmpty(container.getChildContainers())) {
      cancelContainerResponse =
          new CancelContainerResponse(
              container.getTrackingId(),
              ReceivingException.CONTAINER_WITH_CHILD_ERROR_CODE,
              ReceivingException.CONTAINER_WITH_CHILD_ERROR_MSG);
    } else if (container.getParentTrackingId() != null) {
      Container parentContainer =
          containerRepository.findByTrackingId(container.getParentTrackingId());
      if (parentContainer.getPublishTs() == null) {
        cancelContainerResponse =
            new CancelContainerResponse(
                container.getTrackingId(),
                ReceivingException.CONTAINER_ON_UNFINISHED_PALLET_ERROR_CODE,
                ReceivingException.CONTAINER_ON_UNFINISHED_PALLET_ERROR_MSG);
      }
    }
    if (cancelContainerResponse != null) {
      LOGGER.error(
          "Container: {} failed validation for adjustments as err={}",
          container.getTrackingId(),
          cancelContainerResponse);
    }
    return cancelContainerResponse;
  }

  /**
   * This method perform validations on the container status & throws exception if the container is
   * not allowed for adjustments. This allows a container with child containers also a valid
   * container for adjustments
   *
   * @param container
   * @return
   */
  public CancelContainerResponse validateContainerAdjustmentForParentContainer(
      Container container) {
    CancelContainerResponse cancelContainerResponse =
        validateContainerForParentContainer(container);
    if (cancelContainerResponse != null) {
      LOGGER.error(
          "Container: {} failed validation for adjustments as err={}",
          container.getTrackingId(),
          cancelContainerResponse);
    }
    return cancelContainerResponse;
  }

  private CancelContainerResponse validateContainerForParentContainer(Container container) {
    CancelContainerResponse cancelContainerResponse = null;
    if (ReceivingConstants.STATUS_BACKOUT.equalsIgnoreCase(container.getContainerStatus())) {
      cancelContainerResponse =
          new CancelContainerResponse(
              container.getTrackingId(),
              ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_CODE,
              ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG);
    } else if (ReceivingConstants.STATUS_PUTAWAY_COMPLETE.equalsIgnoreCase(
        container.getContainerStatus())) {
      cancelContainerResponse =
          new CancelContainerResponse(
              container.getTrackingId(),
              ReceivingException.CONTAINER_ALREADY_SLOTTED_ERROR_CODE,
              ReceivingException.CONTAINER_ALREADY_SLOTTED_ERROR_MSG);
    } else if (CollectionUtils.isEmpty(container.getContainerItems())) {
      cancelContainerResponse =
          new CancelContainerResponse(
              container.getTrackingId(),
              ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_CODE,
              ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_MSG);
    }
    return cancelContainerResponse;
  }

  public Container getValidContainer(String trackingId) throws ReceivingException {
    /*
     * Get container details
     */
    Container container =
        containerPersisterService.getContainerWithChildContainersExcludingChildContents(trackingId);

    /*
     * Run through the validations
     */
    if (container == null) {
      LOGGER.warn(ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
      throw new ReceivingException(
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE);
    }

    CancelContainerResponse response = validateContainerForAdjustment(container);
    if (response != null) {
      LOGGER.error(
          "throwing error as ContainerAdjustmentValidator.validateContainerForAdjustment returned err response={}",
          response);
      throw new ReceivingException(
          response.getErrorMessage(), HttpStatus.BAD_REQUEST, response.getErrorCode());
    }
    return container;
  }
}
