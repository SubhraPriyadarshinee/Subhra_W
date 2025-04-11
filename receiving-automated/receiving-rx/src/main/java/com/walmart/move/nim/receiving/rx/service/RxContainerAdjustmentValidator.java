package com.walmart.move.nim.receiving.rx.service;

import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class RxContainerAdjustmentValidator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RxContainerAdjustmentValidator.class);

  @Autowired private ContainerRepository containerRepository;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  private JsonParser parser;

  public RxContainerAdjustmentValidator() {
    parser = new JsonParser();
  }

  /**
   * @param container
   * @return CancelContainerResponse
   */
  public CancelContainerResponse validateContainerForAdjustment(
      Container container, HttpHeaders httpHeaders) {
    LOGGER.info("Validate the container for cancel - trackingId :{}", container.getTrackingId());
    CancelContainerResponse cancelContainerResponse = null;
    try {
      String deliveryResponse =
          deliveryService.getDeliveryByDeliveryNumber(container.getDeliveryNumber(), httpHeaders);
      String deliveryStatus =
          parser.parse(deliveryResponse).getAsJsonObject().get("deliveryStatus").getAsString();
      if (DeliveryStatus.FNL.name().equals(deliveryStatus)
          || DeliveryStatus.PNDFNL.name().equals(deliveryStatus)) {
        return new CancelContainerResponse(
            container.getTrackingId(),
            ReceivingException.CANCEL_LABEL_NOT_ALLOWED_BY_DELIVERY_STATUS_ERROR_CODE,
            ReceivingException.CANCEL_LABEL_NOT_ALLOWED_BY_DELIVERY_STATUS_ERROR_MESSAGE);
      }
    } catch (ReceivingException e) {
      LOGGER.error(
          "Error while fetching delivery from GDM by deliveryNumber :{}",
          container.getDeliveryNumber(),
          e);
      return new CancelContainerResponse(
          container.getTrackingId(),
          ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE,
          ReceivingException.GDM_SERVICE_DOWN);
    }

    if (ReceivingConstants.STATUS_BACKOUT.equalsIgnoreCase(container.getContainerStatus())) {
      cancelContainerResponse =
          new CancelContainerResponse(
              container.getTrackingId(),
              ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_CODE,
              ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG);
    } else if (CollectionUtils.isEmpty(container.getContainerItems())) {
      cancelContainerResponse =
          new CancelContainerResponse(
              container.getTrackingId(),
              ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_CODE,
              ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_MSG);
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

    return cancelContainerResponse;
  }
}
