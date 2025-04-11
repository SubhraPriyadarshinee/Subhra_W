package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.ContainerItemRequest;
import com.walmart.move.nim.receiving.core.model.ContainerRequest;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Common utility for container request validations
 *
 * @author lkotthi
 */
public class ContainerValidationUtils {

  private ContainerValidationUtils() {}

  /**
   * Validate input request
   *
   * @param containerRequest
   * @throws ReceivingException
   */
  public static void validateContainerRequest(ContainerRequest containerRequest)
      throws ReceivingException {
    String errorMessage = null;

    if (StringUtils.isEmpty(containerRequest.getTrackingId())) {
      errorMessage =
          String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "trackingId");
      throw new ReceivingException(
          errorMessage,
          HttpStatus.BAD_REQUEST,
          ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_CODE);
    }

    if (ContainerType.getTypeEnum(containerRequest.getCtrType()) == null) {
      throw new ReceivingException(
          ReceivingException.INVALID_CONTAINER_TYPE_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_CODE);
    }

    if (CollectionUtils.isEmpty(containerRequest.getContents())) {
      errorMessage =
          String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "contents");
      throw new ReceivingException(
          errorMessage,
          HttpStatus.BAD_REQUEST,
          ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_CODE);
    }

    validateContents(containerRequest.getContents());
  }

  /**
   * Validate the container items
   *
   * @param contents
   * @throws ReceivingException
   */
  public static void validateContents(List<ContainerItemRequest> contents)
      throws ReceivingException {
    String errorMessage = null;

    for (ContainerItemRequest containerItemRequest : contents) {
      if (StringUtils.isEmpty(containerItemRequest.getPurchaseReferenceNumber())) {
        errorMessage =
            String.format(
                ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "purchaseReferenceNumber");
        throw new ReceivingException(
            errorMessage,
            HttpStatus.BAD_REQUEST,
            ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_CODE);
      }
      if (StringUtils.isEmpty(containerItemRequest.getPurchaseReferenceLineNumber())) {
        errorMessage =
            String.format(
                ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG,
                "purchaseReferenceLineNumber");
        throw new ReceivingException(
            errorMessage,
            HttpStatus.BAD_REQUEST,
            ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_CODE);
      }

      if (containerItemRequest.getQuantity() == null) {
        errorMessage =
            String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "quantity");
        throw new ReceivingException(
            errorMessage,
            HttpStatus.BAD_REQUEST,
            ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_CODE);
      }

      if (StringUtils.isEmpty(containerItemRequest.getQuantityUom())) {
        errorMessage =
            String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "quantityUom");
        throw new ReceivingException(
            errorMessage,
            HttpStatus.BAD_REQUEST,
            ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_CODE);
      }
      if (containerItemRequest.getVnpkQty() == null) {
        errorMessage =
            String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "vnpkQty");
        throw new ReceivingException(
            errorMessage,
            HttpStatus.BAD_REQUEST,
            ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_CODE);
      }
      if (containerItemRequest.getWhpkQty() == null) {
        errorMessage =
            String.format(ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_MSG, "whpkQty");
        throw new ReceivingException(
            errorMessage,
            HttpStatus.BAD_REQUEST,
            ReceivingException.INVALID_CONTAINER_REQUEST_ERROR_CODE);
      }
    }
  }

  public static void checkIfContainerIsParentContainer(Container parentContainer)
      throws ReceivingException {
    if (parentContainer == null) {
      throw new ReceivingException(
          ReceivingException.CONTAINER_IS_NULL, HttpStatus.INTERNAL_SERVER_ERROR);
    } else if (!StringUtils.isEmpty(parentContainer.getParentTrackingId())) {
      throw new ReceivingException(
          String.format(
              ReceivingException.CONTAINER_IS_NOT_A_PARENT_CONTAINER_ERROR,
              parentContainer.getTrackingId()),
          HttpStatus.INTERNAL_SERVER_ERROR);
    } else if (parentContainer.getCompleteTs() == null) {
      throw new ReceivingException(
          String.format(
              ReceivingException.CONTAINER_IS_NOT_COMPLETE_ERROR, parentContainer.getTrackingId()),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
