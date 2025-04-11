package com.walmart.move.nim.receiving.wfs.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.GetContainerRequestHandler;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class WFSContainerRequestHandler implements GetContainerRequestHandler {
  private static final Logger LOG = LoggerFactory.getLogger(WFSContainerRequestHandler.class);
  @Autowired private ContainerService containerService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Autowired private InventoryService inventoryService;

  @Override
  public Container getContainerByTrackingId(
      String trackingId,
      boolean includeChilds,
      String uom,
      boolean isReEngageDecantFlow,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    try {
      Container container =
          containerService.getContainerWithChildsByTrackingId(trackingId, includeChilds, uom);

      if (ReceivingUtils.isKotlinEnabled(httpHeaders, tenantSpecificConfigReader)) {
        validateContainerForMobile(container);
      } else {
        validateContainerForWebUI(container, isReEngageDecantFlow, httpHeaders);
      }
      return container;
    } catch (ReceivingException receivingException) {
      if (ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE.equals(
          receivingException.getErrorResponse().getErrorCode())) {
        // not found container in db, check inventory status of container by lpn
        checkInventoryStatusIfNotFoundInDB(trackingId, isReEngageDecantFlow, httpHeaders);
      }
      String errorCode =
          StringUtils.isNotEmpty(receivingException.getErrorResponse().getErrorCode())
              ? receivingException.getErrorResponse().getErrorCode()
              : ExceptionCodes.RECEIVING_INTERNAL_ERROR;
      throw new ReceivingBadDataException(
          errorCode, receivingException.getErrorResponse().getErrorMessage().toString());
    }
  }

  public void validateContainerForMobile(Container container) throws ReceivingException {
    // TODO: also, check why "PALLET" is being set in containerType in WFS code, as Enum member
    // ContainerType.PALLET.getText() should be used, instead ContainerType.PALLET.name() is being
    // used. In case of VendorPack container type, ContainerType.VENDORPACK.getText() is used
    // correctly

    // Mobile UI Validations for Scan LPN
    if (!(InventoryStatus.PICKED.name().equals(container.getInventoryStatus())
        && ContainerType.PALLET.name().equals(container.getContainerType()))) {
      LOG.error(
          "Invalid container for WFS with trackingId={}, source={}",
          container.getTrackingId(),
          WFSConstants.SOURCE_MOBILE);
      throw new ReceivingBadDataException(
          ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_PICKED_PALLET,
          WFSConstants.INVALID_CONTAINER_FOR_WFS_INV_STATUS_AND_CTR_TYPE_EXCEPTION_MSG,
          container.getInventoryStatus(),
          container.getContainerType());
    }
    if (ReceivingConstants.STATUS_BACKOUT.equals(container.getContainerStatus())) {
      LOG.error(
          "Invalid container for WFS, status BACKOUT with lpn={}, source={} ",
          container.getTrackingId(),
          WFSConstants.SOURCE_MOBILE);
      throw new ReceivingBadDataException(
          ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_BACKOUT_CTR,
          WFSConstants.INVALID_CONTAINER_FOR_WFS_CTR_STATUS_EXCEPTION_MSG,
          container.getContainerStatus());
    }
  }

  public void validateContainerForWebUI(
      Container container, boolean isReEngageDecantFlow, HttpHeaders httpHeaders)
      throws ReceivingException {
    if (isReEngageDecantFlow) {
      // Re-Engage Decant Flow
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.IS_DELIVERY_STATUS_VALIDATION_ENABLED_REENGAGE_DECANT)) {
        // 1. Validate delivery status. FNL delivery will be invalid here
        CancelContainerResponse validateDeliveryStatusResponse =
            containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
                container.getTrackingId(), container.getDeliveryNumber(), httpHeaders);

        if (Objects.nonNull(validateDeliveryStatusResponse)) {
          // check exception code for FNL delivery error, then change error message to new one
          if (ExceptionCodes.LABEL_CORRECTION_ERROR_FOR_FINALIZED_DELIVERY.equals(
              validateDeliveryStatusResponse.getErrorCode())) {
            LOG.error(
                "Invalid Container for Re-engage Decant. Delivery is FNL. lpn={}, deliveryNumber={}, source={}",
                container.getTrackingId(),
                container.getDeliveryNumber(),
                WFSConstants.SOURCE_WEB);
            throw new ReceivingBadDataException(
                ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_RESTART_DCNT_DELIVERY_FNL,
                WFSConstants.INVALID_CONTAINER_FOR_WFS_DELIVERY_STATUS_EXCEPTION_MSG,
                DeliveryStatus.FNL.name());
          }
          // throw default error in case delivery not finalized
          throw new ReceivingException(
              validateDeliveryStatusResponse.getErrorMessage(),
              HttpStatus.BAD_REQUEST,
              validateDeliveryStatusResponse.getErrorCode(),
              ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER);
        }
      }
      // Check if Inventory Status is Available, and ContainerType is Vendor Pack
      if (!(InventoryStatus.AVAILABLE.name().equals(container.getInventoryStatus())
          && ContainerType.VENDORPACK.getText().equals(container.getContainerType()))) {
        LOG.error(
            "Invalid Container for Re-engage Decant. InventoryStatus={}, ContainerType={}, lpn={}, source={}",
            container.getInventoryStatus(),
            container.getContainerType(),
            container.getTrackingId(),
            WFSConstants.SOURCE_WEB);
        throw new ReceivingBadDataException(
            ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_RESTART_DCNT_INV_STATUS_CTR_TYPE,
            WFSConstants.INVALID_CONTAINER_FOR_WFS_INV_STATUS_AND_CTR_TYPE_EXCEPTION_MSG,
            container.getInventoryStatus(),
            container.getContainerType());
      }

      // Check if container is not BACKOUT
      if (ReceivingConstants.STATUS_BACKOUT.equals(container.getContainerStatus())) {
        LOG.error(
            "Invalid Container for Re-engage Decant. Status BACKOUT. lpn={}, source={}",
            container.getTrackingId(),
            WFSConstants.SOURCE_WEB);
        throw new ReceivingBadDataException(
            ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_RESTART_DCNT_CTR_STATUS_BACKOUT,
            WFSConstants.INVALID_CONTAINER_FOR_WFS_RESTART_DCNT_CTR_STATUS_BACKOUT);
      }

      // Call INV API, and check inventory status of container. If not AVAILABLE, throw error
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.IS_CONTAINER_STATUS_VALIDATION_ENABLED_REENGAGE_DECANT)) {
        String inventoryContainerStatus =
            getContainerStatusFromInventory(container.getTrackingId(), true, httpHeaders)
                .getContainerStatus();
        if (!InventoryStatus.AVAILABLE.name().equalsIgnoreCase(inventoryContainerStatus)) {
          LOG.error(
              "Inventory is not in correct status for Re-engage decant. Inv status={}, lpn={}, source={}",
              inventoryContainerStatus,
              container.getTrackingId(),
              WFSConstants.SOURCE_WEB);
          throw new ReceivingBadDataException(
              ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_RESTART_DCNT_INV_STATUS,
              ReceivingConstants.WFS_INVALID_LABEL_FOR_RESTART_DECANT_INV_STATUS_ERROR_MSG,
              inventoryContainerStatus);
        }
      }

    } else {
      // Receiving Corrections Flow

      if (ContainerType.VENDORPACK.getText().equals(container.getContainerType())) {
        if (!InventoryStatus.PICKED.name().equals(container.getInventoryStatus())
            && !InventoryStatus.AVAILABLE.name().equals(container.getInventoryStatus())) {
          LOG.error(
              "Invalid container for WFS Correction with is neither PICKED not AVAILABLE. lpn={}, inventoryStatus={}, source={}",
              container.getTrackingId(),
              container.getContainerStatus(),
              WFSConstants.SOURCE_WEB);
          throw new ReceivingBadDataException(
              ExceptionCodes
                  .WFS_INVALID_GET_CONTAINER_REQUEST_FOR_CORRECTION_CTR_STATUS_NOT_PICKED_OR_AVAILABLE,
              WFSConstants.INVALID_CONTAINER_FOR_WFS_CTR_STATUS);
        }
      } else {
        LOG.error(
            "Invalid container for WFS Correction, containerType={} is not VendorPack. with lpn={}, source={}.",
            container.getContainerType(),
            container.getTrackingId(),
            WFSConstants.SOURCE_WEB);
        throw new ReceivingBadDataException(
            ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_FOR_CORRECTION_CTR_TYPE_NOT_VENDORPACK,
            WFSConstants.INVALID_CONTAINER_FOR_WFS_CTR_STATUS);
      }

      // check for backout status in container
      if (ReceivingConstants.STATUS_BACKOUT.equals(container.getContainerStatus())) {
        LOG.error(
            "Invalid container for WFS, status BACKOUT with lpn={}, source={} ",
            container.getTrackingId(),
            WFSConstants.SOURCE_WEB);
        throw new ReceivingBadDataException(
            ExceptionCodes.WFS_INVALID_GET_CONTAINER_REQUEST_BACKOUT_CTR,
            WFSConstants.INVALID_CONTAINER_FOR_WFS_CTR_STATUS_EXCEPTION_MSG,
            container.getContainerStatus());
      }
    }
  }

  private InventoryContainerDetails getContainerStatusFromInventory(
      String trackingId, boolean isReEngageDecantFlow, HttpHeaders httpHeaders)
      throws ReceivingException {
    try {
      return inventoryService.getInventoryContainerDetails(trackingId, httpHeaders);
    } catch (ReceivingDataNotFoundException exc) {
      if (ExceptionCodes.INVENTORY_NOT_FOUND.equals(exc.getErrorCode())) {
        LOG.error("Inventory Not Found error for lpn={}", trackingId);
        if (isReEngageDecantFlow) {
          throw new ReceivingDataNotFoundException(
              ExceptionCodes.WFS_INVALID_LABEL_FOR_RESTART_DCNT_INV_NOT_FOUND,
              ReceivingConstants.WFS_INVALID_LABEL_FOR_RESTART_DECANT_INV_NOT_FOUND_ERROR_MSG);
        } else {
          throw new ReceivingDataNotFoundException(
              ExceptionCodes.WFS_INVALID_LABEL_FOR_CORRECTION_INV_NOT_FOUND,
              ReceivingConstants.WFS_INVALID_LABEL_FOR_CORRECTION_INV_NOT_FOUND_ERROR_MSG);
        }
      } else throw exc;
    }
  }

  public void checkInventoryStatusIfNotFoundInDB(
      String trackingId, boolean isReEngageDecantFlow, HttpHeaders httpHeaders)
      throws ReceivingException {
    InventoryContainerDetails inventoryContainerDetails;
    inventoryContainerDetails =
        getContainerStatusFromInventory(trackingId, isReEngageDecantFlow, httpHeaders);

    if (inventoryContainerDetails.getDestinationLocationId() != 0) {
      // if this is true, then it is a destination container
      // throw error to please scan an induct LPN
      LOG.error(
          "Invalid induct container. lpn={}, destinationLocationId={}",
          trackingId,
          inventoryContainerDetails.getDestinationLocationId());
      throw new ReceivingBadDataException(
          ExceptionCodes.WFS_INVALID_INDUCT_LPN_DESTINATION_CTR,
          ReceivingConstants.WFS_INVALID_INDUCT_LPN_DESTINATION_CTR_ERROR_MSG);
    } else {
      // if flow gets here, container found in inventory is invalid.
      // throw the same, container not found in db exception
      throw new ReceivingException(
          ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE,
          HttpStatus.NOT_FOUND,
          ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE,
          ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_HEADER);
    }
  }
}
