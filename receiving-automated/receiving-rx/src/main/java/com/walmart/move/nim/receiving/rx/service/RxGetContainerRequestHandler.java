package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_HEADER;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.WHPK;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.ContainerSummary;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.GetContainerRequestHandler;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

/** @author v0k00fe */
@Component(RxConstants.RX_GET_CONTAINER_REQUEST_HANDLER)
public class RxGetContainerRequestHandler implements GetContainerRequestHandler {
  private static final Logger log = LoggerFactory.getLogger(RxGetContainerRequestHandler.class);

  @Autowired private ContainerService containerService;
  @Autowired private ContainerRepository containerRepository;
  @Autowired private ContainerItemRepository containerItemRepository;

  /**
   * @param trackingId
   * @return
   * @throws ReceivingException
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Container getContainerByTrackingId(String trackingId, String quantityUOM)
      throws ReceivingException {

    Container container = containerRepository.findByTrackingId(trackingId);
    log.info(
        "getContainerByTrackingId lpn={}, deliveryNumber={}",
        trackingId,
        container != null ? container.getDeliveryNumber() : null);

    if (container == null) {
      log.error("no container found for lpn={}", trackingId);
      throw new ReceivingException(
          LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE,
          NOT_FOUND,
          LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE,
          LPN_NOT_FOUND_VERIFY_LABEL_ERROR_HEADER);
    }

    List<ContainerItem> containerItems =
        containerItemRepository.findByTrackingId(container.getTrackingId());
    if (!CollectionUtils.isEmpty(containerItems)) {
      ContainerItem containerItem = containerItems.get(0);
      if (!ObjectUtils.isEmpty(containerItem)) {
        switch (quantityUOM) {
          case VNPK:
            containerItem.setQuantity(containerItem.getQuantity() / containerItem.getVnpkQty());
            containerItem.setQuantityUOM(quantityUOM);
            break;
          case WHPK:
            containerItem.setQuantity(containerItem.getQuantity() / containerItem.getWhpkQty());
            containerItem.setQuantityUOM(quantityUOM);
            break;
          case EACHES:
            break;
          default:
            Integer receivedQty =
                ReceivingUtils.conversionToVendorPack(
                    containerItem.getQuantity(),
                    containerItem.getQuantityUOM(),
                    containerItem.getVnpkQty(),
                    containerItem.getWhpkQty());
            if (receivedQty < 1) {
              containerItem.setQuantity(containerItem.getQuantity() / containerItem.getWhpkQty());
              containerItem.setQuantityUOM(WHPK);
            } else {
              containerItem.setQuantity(receivedQty);
              containerItem.setQuantityUOM(VNPK);
            }
            break;
        }
      }
    }
    container.setContainerItems(containerItems);
    return container;
  }

  @Override
  public Container getContainerByTrackingId(
      String trackingId,
      boolean includeChilds,
      String quantityUOM,
      boolean isReEngageDecantFlow,
      HttpHeaders httpHeaders) {

    log.info(
        "Entering getContainerWithChildsByTrackingId() with trackingId: {} includeChilds: {}, quantityUOM: {}",
        trackingId,
        includeChilds,
        quantityUOM);

    ReceivingUtils.validateTrackingId(trackingId);
    try {
      Container container = getContainerByTrackingId(trackingId, quantityUOM);

      if (quantityUOM.equalsIgnoreCase(RxConstants.AUTO)
          && null != container.getContainerMiscInfo()) {
        Map<String, Object> containerMiscInfo = container.getContainerMiscInfo();
        if (null != containerMiscInfo.get(ReceivingConstants.IS_EPCIS_ENABLED_VENDOR)) {
          Boolean isEpcisEnabled =
              (Boolean) containerMiscInfo.get(ReceivingConstants.IS_EPCIS_ENABLED_VENDOR);
          if (isEpcisEnabled) {
            throw new ReceivingConflictException(
                ExceptionCodes.PALLET_CORRECTION_NOTALLOWED_EPCIS_VENDOR_409,
                ExceptionDescriptionConstants.PALLET_CORRECTION_NOTALLOWED_EPCIS_VENDOR);
          }
        }
      }

      if (includeChilds) {
        container = containerService.getContainerIncludingChild(container, quantityUOM);
        if (!CollectionUtils.isEmpty(container.getChildContainers())) {
          container.setHasChildContainers(true);
        }
      } else {
        container.setChildContainers(Collections.emptySet());
      }

      log.info(
          "Exiting getContainerWithChildsByTrackingId() with trackingId {} includeChilds {}",
          trackingId,
          includeChilds);

      return container;
    } catch (ReceivingException receivingException) {
      throw RxUtils.convertToReceivingBadDataException(receivingException);
    }
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<ContainerSummary> getContainersSummary(
      long instructionId, String serial, String lotNumber) {
    return containerRepository.findByInstructionIdLotSerial(instructionId, serial, lotNumber);
  }
}
