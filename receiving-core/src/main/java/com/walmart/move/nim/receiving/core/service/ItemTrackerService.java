package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.ItemTracker;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.model.ItemTrackerRequest;
import com.walmart.move.nim.receiving.core.repositories.ItemTrackerRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ItemTrackerCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.EnumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service(ReceivingConstants.ITEM_TRACKER_SERVICE)
public class ItemTrackerService implements Purge {

  private static final Logger LOGGER = LoggerFactory.getLogger(ItemTrackerService.class);
  @Autowired private ItemTrackerRepository itemTrackerRepository;

  @Transactional
  @InjectTenantFilter
  public void trackItem(ItemTrackerRequest itemTrackerRequest) {
    if (!EnumUtils.isValidEnum(ItemTrackerCode.class, itemTrackerRequest.getReasonCode())) {
      LOGGER.error(
          "Item cannot be tracked without reason code [itemTrackerRequest={}]", itemTrackerRequest);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_ITEM_TRACKER_REQUEST,
          ExceptionDescriptionConstants.INVALID_ITEM_TRACKER_REQUEST_REASON_CODE);
    }
    ItemTracker itemTracker = new ItemTracker();
    itemTracker.setParentTrackingId(itemTrackerRequest.getParentTrackingId());
    itemTracker.setTrackingId(itemTrackerRequest.getTrackingId());
    itemTracker.setGtin(itemTrackerRequest.getGtin());
    itemTracker.setItemTrackerCode(ItemTrackerCode.valueOf(itemTrackerRequest.getReasonCode()));
    String userId =
        String.valueOf(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));
    itemTracker.setCreateUser(userId);
    itemTrackerRepository.save(itemTracker);
  }

  @Transactional
  @InjectTenantFilter
  public List<ItemTracker> trackItems(List<ItemTrackerRequest> itemTrackerRequests) {
    if (!CollectionUtils.isEmpty(itemTrackerRequests)) {
      List<ItemTracker> itemTrackerList = new ArrayList<>();
      for (ItemTrackerRequest itemTrackerRequest : itemTrackerRequests) {
        if (!EnumUtils.isValidEnum(ItemTrackerCode.class, itemTrackerRequest.getReasonCode())) {
          LOGGER.error(
              "Item cannot be tracked without reason code [itemTrackerRequest={}]",
              itemTrackerRequest);
          throw new ReceivingBadDataException(
              ExceptionCodes.INVALID_ITEM_TRACKER_REQUEST,
              ExceptionDescriptionConstants.INVALID_ITEM_TRACKER_REQUEST_REASON_CODE);
        }
        ItemTracker itemTracker = new ItemTracker();
        itemTracker.setParentTrackingId(itemTrackerRequest.getParentTrackingId());
        itemTracker.setTrackingId(itemTrackerRequest.getTrackingId());
        itemTracker.setGtin(itemTrackerRequest.getGtin());
        itemTracker.setItemTrackerCode(ItemTrackerCode.valueOf(itemTrackerRequest.getReasonCode()));
        String userId =
            String.valueOf(
                TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));
        itemTracker.setCreateUser(userId);
        itemTrackerList.add(itemTracker);
      }
      return itemTrackerRepository.saveAll(itemTrackerList);
    }
    return Collections.emptyList();
  }

  @Transactional
  @InjectTenantFilter
  public List<ItemTracker> getTrackedItemByTrackingId(String trackingId) {
    List<ItemTracker> itemTrackerList = itemTrackerRepository.findByTrackingId(trackingId);
    if (CollectionUtils.isEmpty(itemTrackerList)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.ITEM_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG, trackingId);
      throw new ReceivingDataNotFoundException(ExceptionCodes.ITEM_NOT_FOUND, errorDescription);
    }
    return itemTrackerList;
  }

  @Transactional
  @InjectTenantFilter
  public List<ItemTracker> getTrackedItemByGtin(String gtin) {
    List<ItemTracker> itemTrackerList = itemTrackerRepository.findByGtin(gtin);
    if (CollectionUtils.isEmpty(itemTrackerList)) {
      String errorDescription =
          String.format(ExceptionDescriptionConstants.ITEM_NOT_FOUND_FOR_GTIN_ERROR_MSG, gtin);
      throw new ReceivingDataNotFoundException(ExceptionCodes.ITEM_NOT_FOUND, errorDescription);
    }
    return itemTrackerList;
  }

  @Transactional
  @InjectTenantFilter
  public void deleteTrackedItemByTrackingId(String trackingId) {
    List<ItemTracker> itemTrackerList = itemTrackerRepository.findByTrackingId(trackingId);
    if (CollectionUtils.isEmpty(itemTrackerList)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.ITEM_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG, trackingId);
      throw new ReceivingDataNotFoundException(ExceptionCodes.ITEM_NOT_FOUND, errorDescription);
    }
    itemTrackerRepository.deleteByTrackingId(trackingId);
  }

  @Transactional
  @InjectTenantFilter
  public void deleteTrackedItemByGtin(String gtin) {
    List<ItemTracker> itemTrackerList = itemTrackerRepository.findByGtin(gtin);
    if (CollectionUtils.isEmpty(itemTrackerList)) {
      String errorDescription =
          String.format(ExceptionDescriptionConstants.ITEM_NOT_FOUND_FOR_GTIN_ERROR_MSG, gtin);
      throw new ReceivingDataNotFoundException(ExceptionCodes.ITEM_NOT_FOUND, errorDescription);
    }
    itemTrackerRepository.deleteByGtin(gtin);
  }

  @Override
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<ItemTracker> itemTrackerList =
        itemTrackerRepository.findByIdGreaterThanEqual(purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    itemTrackerList =
        itemTrackerList
            .stream()
            .filter(itemTracker -> itemTracker.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(ItemTracker::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(itemTrackerList)) {
      LOGGER.info("Purge ITEM_TRACKER: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = itemTrackerList.get(itemTrackerList.size() - 1).getId();

    LOGGER.info(
        "Purge ITEM_TRACKER: {} records : ID {} to {} : START",
        itemTrackerList.size(),
        itemTrackerList.get(0).getId(),
        lastDeletedId);
    itemTrackerRepository.deleteAll(itemTrackerList);
    LOGGER.info("Purge ITEM_TRACKER: END");
    return lastDeletedId;
  }
}
