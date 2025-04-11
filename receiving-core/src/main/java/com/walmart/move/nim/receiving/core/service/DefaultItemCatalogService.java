package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.itemupdate.ItemUpdateRestApiClient;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.model.ItemCatalogDeleteRequest;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateRequest;
import com.walmart.move.nim.receiving.core.repositories.ItemCatalogRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * Service class for Item cataloging
 *
 * @author s0g015w
 */
@Primary
@Service(ReceivingConstants.DEFAULT_ITEM_CATALOG_SERVICE)
public class DefaultItemCatalogService implements ItemCatalogService, Purge {
  public static final Logger LOGGER = LoggerFactory.getLogger(DefaultItemCatalogService.class);

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Autowired private ItemMDMService itemMDMService;
  @Autowired private ItemCatalogRepository itemCatalogRepository;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private ItemUpdateRestApiClient itemUpdateRestApiClient;
  @Autowired private ItemUpdateUtils itemUpdateUtils;

  private Gson gson;

  public DefaultItemCatalogService() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Timed(
      name = "updateVendorUPCTimed",
      level1 = "uwms-receiving",
      level2 = "DefaultItemCatalogService",
      level3 = "updateVendorUPC")
  @ExceptionCounted(
      name = "updateVendorUPCExceptionCount",
      level1 = "uwms-receiving",
      level2 = "DefaultItemCatalogService",
      level3 = "updateVendorUPC")
  @Override
  public String updateVendorUPC(
      ItemCatalogUpdateRequest itemCatalogUpdateRequest, HttpHeaders httpHeaders) {
    String vendorUPC = itemCatalogUpdateRequest.getNewItemUPC();
    String convertedVendorUPC = null;

    if (configUtils.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM)) {
      deliveryService.updateVendorUPC(
          itemCatalogUpdateRequest.getDeliveryNumber(),
          itemCatalogUpdateRequest.getItemNumber(),
          itemCatalogUpdateRequest.getNewItemUPC(),
          httpHeaders);
    }

    if (configUtils.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM)) {
      itemMDMService.updateVendorUPC(itemCatalogUpdateRequest, httpHeaders);
    }

    if (configUtils.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED)) {
      if (configUtils.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.CATALOG_UPC_CONVERSION_ENABLED,
          false)) {
        if (itemCatalogUpdateRequest.getNewItemUPC().length() < ReceivingConstants.GTIN14_LENGTH) {
          convertedVendorUPC =
              org.apache.commons.lang3.StringUtils.leftPad(
                  itemCatalogUpdateRequest.getNewItemUPC(),
                  ReceivingConstants.GTIN14_LENGTH,
                  ReceivingConstants.ZERO_STRING);
          LOGGER.info(
              "Given vendorUPC: {} and convertedVendorUPC: {}", vendorUPC, convertedVendorUPC);
          itemCatalogUpdateRequest.setNewItemUPC(convertedVendorUPC);
        }
      }
      ItemUpdateRequest itemUpdateRequest =
          itemUpdateUtils.createItemUpdateRequest(itemCatalogUpdateRequest, httpHeaders);
      itemUpdateRestApiClient.updateItem(itemUpdateRequest, httpHeaders);
    }

    if (configUtils.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.ITEM_VENDOR_UPC_UPDATE_ENABLED_FOR_GDM)) {
      deliveryService.updateVendorUpcItemV3(
          itemCatalogUpdateRequest.getItemNumber(),
          Objects.nonNull(convertedVendorUPC)
              ? convertedVendorUPC
              : itemCatalogUpdateRequest.getNewItemUPC(),
          httpHeaders);
    }

    ItemCatalogUpdateLog itemCatalogUpdateLog =
        ItemCatalogUpdateLog.builder()
            .itemNumber(itemCatalogUpdateRequest.getItemNumber())
            .deliveryNumber(Long.parseLong(itemCatalogUpdateRequest.getDeliveryNumber()))
            .oldItemUPC(itemCatalogUpdateRequest.getOldItemUPC())
            .newItemUPC(vendorUPC)
            .createUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY))
            .itemInfoHandKeyed(itemCatalogUpdateRequest.isItemInfoHandKeyed())
            .vendorNumber(itemCatalogUpdateRequest.getVendorNumber())
            .vendorStockNumber(itemCatalogUpdateRequest.getVendorStockNumber())
            .build();

    return gson.toJson(saveItemCatalogUpdateLog(itemCatalogUpdateLog), ItemCatalogUpdateLog.class);
  }

  @InjectTenantFilter
  private ItemCatalogUpdateLog saveItemCatalogUpdateLog(ItemCatalogUpdateLog itemCatalogUpdateLog) {
    return itemCatalogRepository.save(itemCatalogUpdateLog);
  }

  /**
   * This method will delete item catalog update logs which are created for integration test
   *
   * @param deliveryNumber delivery number
   */
  @Transactional
  @InjectTenantFilter
  public void deleteItemCatalogUpdatelogs(Long deliveryNumber) {
    itemCatalogRepository.deleteAllByDeliveryNumber(deliveryNumber);
  }

  /**
   * This method will gets item catalog update logs which are created for integration test
   *
   * @param deliveryNumber delivery number
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<ItemCatalogUpdateLog> getItemCatalogUpdatelogs(Long deliveryNumber) {
    return itemCatalogRepository.findByDeliveryNumber(deliveryNumber);
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<ItemCatalogUpdateLog> itemCatalogUpdateLogList =
        itemCatalogRepository.findByIdGreaterThanEqual(purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    itemCatalogUpdateLogList =
        itemCatalogUpdateLogList
            .stream()
            .filter(item -> item.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(ItemCatalogUpdateLog::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(itemCatalogUpdateLogList)) {
      LOGGER.info("Purge ITEM_CATALOG_UPDATE_LOG: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = itemCatalogUpdateLogList.get(itemCatalogUpdateLogList.size() - 1).getId();

    LOGGER.info(
        "Purge ITEM_CATALOG_UPDATE_LOG: {} records : ID {} to {} : START",
        itemCatalogUpdateLogList.size(),
        itemCatalogUpdateLogList.get(0).getId(),
        lastDeletedId);
    itemCatalogRepository.deleteAll(itemCatalogUpdateLogList);
    LOGGER.info("Purge ITEM_CATALOG_UPDATE_LOG: END");
    return lastDeletedId;
  }

  /**
   * Deletes ItemCatalog Entry by deliveryNumber and NewItemUpc
   *
   * @param itemCatalogUpdateRequest
   */
  @Override
  @Transactional
  @InjectTenantFilter
  public void deleteItemCatalog(ItemCatalogDeleteRequest itemCatalogUpdateRequest) {
    itemCatalogRepository.deleteByDeliveryNumberAndNewItemUPC(
        Long.valueOf(itemCatalogUpdateRequest.getDeliveryNumber()),
        itemCatalogUpdateRequest.getNewItemUPC());
  }
}
