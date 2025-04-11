package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ContainerUtils.adjustContainerByQty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WitronSplitPalletService {
  private static final Logger LOGGER = LoggerFactory.getLogger(WitronSplitPalletService.class);

  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private GdcPutawayPublisher gdcPutawayPublisher;

  public void splitPallet(
      String originalTrackingId,
      Integer originalAvailableToSellQty,
      String newTrackingId,
      String newContainerType,
      Integer adjustQty,
      HttpHeaders headers)
      throws ReceivingException {
    final String userId = headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    LOGGER.info(
        "originalTrackingId={} originalAvailableToSellQty={} newContainerTrackingId={}, adjustQty={} userId={}",
        originalTrackingId,
        originalAvailableToSellQty,
        newTrackingId,
        adjustQty,
        userId);

    Container originalContainer =
        containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            originalTrackingId);
    if (Objects.isNull(originalContainer)
        || CollectionUtils.isEmpty(originalContainer.getContainerItems())) {
      LOGGER.warn(ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
      throw new ReceivingException(
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE);
    }

    // Update original container - do not record any damages received from Inventory
    ContainerItem containerItem = originalContainer.getContainerItems().get(0);
    Integer remainingQty = containerItem.getQuantity() + adjustQty;
    containerItem.setQuantity(remainingQty);
    containerItem.setActualHi(ContainerUtils.calculateActualHi(containerItem));
    originalContainer.setWeight(ContainerUtils.calculateWeight(originalContainer));
    originalContainer.setWeightUOM(ContainerUtils.getDefaultWeightUOM(originalContainer));
    originalContainer.setLastChangedUser(userId);
    originalContainer.setLastChangedTs(new Date());
    containerItem.setLastChangedTs(new Date());
    LOGGER.info(
        "update original container {} with quantity {} weight {} actualHi {}",
        originalContainer.getTrackingId(),
        originalContainer.getContainerItems().get(0).getQuantity(),
        originalContainer.getWeight(),
        originalContainer.getContainerItems().get(0).getActualHi());
    containerPersisterService.saveContainer(originalContainer);

    // Send RTU for original container with the quantity received from Inventory
    Container rtuContainer =
        adjustContainerByQty(true, originalContainer, originalAvailableToSellQty);

    if (originalAvailableToSellQty == 0) {
      gdcPutawayPublisher.publishMessage(
          rtuContainer, ReceivingConstants.PUTAWAY_DELETE_ACTION, headers);
    } else {
      gdcPutawayPublisher.publishMessage(
          rtuContainer, ReceivingConstants.PUTAWAY_UPDATE_ACTION, headers);
    }

    // Add new container
    Container newContainer = clone(originalContainer);
    ContainerItem newContainerItem = newContainer.getContainerItems().get(0);

    newContainerItem.setTrackingId(newTrackingId);
    newContainerItem.setQuantity(adjustQty * -1);
    newContainerItem.setActualHi(ContainerUtils.calculateActualHi(newContainerItem));

    newContainer.setTrackingId(newTrackingId);
    newContainer.setWeight(ContainerUtils.calculateWeight(newContainer));
    newContainer.setWeightUOM(ContainerUtils.getDefaultWeightUOM(newContainer));
    if (isNotBlank(newContainerType)) newContainer.setContainerType(newContainerType);
    newContainer.setCreateUser(userId);
    newContainer.setCreateTs(new Date());
    newContainer.setLastChangedUser(userId);
    newContainer.setLastChangedTs(new Date());
    newContainerItem.setLastChangedTs(new Date());

    LOGGER.info(
        "Add new container {} with quantity {} weight {} actualHi {}",
        newContainer.getTrackingId(),
        newContainer.getContainerItems().get(0).getQuantity(),
        newContainer.getWeight(),
        newContainer.getContainerItems().get(0).getActualHi());

    containerPersisterService.saveContainer(newContainer);

    // Send RTU for new container
    gdcPutawayPublisher.publishMessage(
        newContainer, ReceivingConstants.PUTAWAY_ADD_ACTION, headers);

    // Publish receipt update to SCT
    receiptPublisher.publishReceiptUpdate(originalContainer.getTrackingId(), headers, Boolean.TRUE);
    receiptPublisher.publishReceiptUpdate(newContainer.getTrackingId(), headers, Boolean.TRUE);
  }

  private Container clone(Container container) {
    Container clonedContainer = SerializationUtils.clone(container);
    clonedContainer.setId(null);
    clonedContainer
        .getContainerItems()
        .stream()
        .forEach(
            containerItem -> {
              containerItem.setId(null);
              containerItem.setTrackingId(null);
            });

    return clonedContainer;
  }
}
