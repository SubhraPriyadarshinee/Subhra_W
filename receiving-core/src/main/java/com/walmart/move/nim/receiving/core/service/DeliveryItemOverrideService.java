package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.DeliveryItemOverrideRepository;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** @author lkotthi */
@Service
public class DeliveryItemOverrideService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryItemOverrideService.class);

  @Autowired private DeliveryItemOverrideRepository deliveryItemOverrideRepo;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private GDMRestApiClient gdmRestApiClient;

  @Resource(name = ReceivingConstants.WITRON_DELIVERY_METADATA_SERVICE)
  private WitronDeliveryMetaDataService witronDeliveryMetaDataService;

  /**
   * Creates temporary PalletTi/PalletHi for a given delivery and item
   *
   * @param deliveryNumber
   * @param itemNumber
   * @param temporaryPalletTiHiRequest
   * @return DeliveryItemOverride˙
   */
  @Transactional
  @InjectTenantFilter
  public DeliveryItemOverride saveTemporaryPalletTiHi(
      Long deliveryNumber,
      Long itemNumber,
      TemporaryPalletTiHiRequest temporaryPalletTiHiRequest,
      HttpHeaders httpHeaders) {

    DeliveryItemOverride deliveryItemOverride = new DeliveryItemOverride();
    deliveryItemOverride.setDeliveryNumber(deliveryNumber);
    deliveryItemOverride.setItemNumber(itemNumber);
    deliveryItemOverride.setTempPalletTi(temporaryPalletTiHiRequest.getPalletTi());
    deliveryItemOverride.setTempPalletHi(temporaryPalletTiHiRequest.getPalletHi());
    deliveryItemOverride.setLastChangedUser(
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    deliveryItemOverride.setLastChangedTs(new Date());
    deliveryItemOverride.setVersion(temporaryPalletTiHiRequest.getVersion());
    DeliveryItemOverride deliveryItemOverrideFromDb =
        deliveryItemOverrideRepo.saveAndFlush(deliveryItemOverride);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        ReceivingConstants.SEND_UPDATE_EVENTS_TO_GDM,
        false))
      sendTiHIUpdateEventToGdm(deliveryNumber, itemNumber, temporaryPalletTiHiRequest, httpHeaders);
    return deliveryItemOverrideFromDb;
  }

  private void sendTiHIUpdateEventToGdm(
      Long deliveryNumber,
      Long itemNumber,
      TemporaryPalletTiHiRequest temporaryPalletTiHiRequest,
      HttpHeaders httpHeaders) {
    Map<String, Object> forwarderHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    ReceiveEventRequestBody receiveEventRequestBody = null;
    try {
      // Prepare receive event body
      ReceiveData receiveData =
          ReceiveData.builder()
              .eventType(ReceivingConstants.RECEIVE_TI_HI_UPDATES)
              .itemNumber(String.valueOf(itemNumber))
              .tiQty(temporaryPalletTiHiRequest.getPalletTi())
              .hiQty(temporaryPalletTiHiRequest.getPalletHi())
              .build();
      receiveEventRequestBody =
          ReceiveEventRequestBody.builder()
              .eventType(ReceivingConstants.RECEIVE_TI_HI_UPDATES)
              .deliveryNumber(deliveryNumber)
              .receiveData(receiveData)
              .build();
      // Send event to GDM
      gdmRestApiClient.receivingToGDMEvent(receiveEventRequestBody, forwarderHeaders);
    } catch (GDMRestApiClientException e) {
      LOGGER.error(
          "Failed to call GDM to RECEIVE_TI_HI_UPDATES event for forwarderHeaders {} , payload {}",
          forwarderHeaders,
          receiveEventRequestBody);
    }
  }
  /**
   * Find DeliveryItemOverride by Delivery Number and Item Number
   *
   * @param deliveryNumber
   * @param itemNumber
   * @return DeliveryItemOverride
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Optional<DeliveryItemOverride> findByDeliveryNumberAndItemNumber(
      Long deliveryNumber, Long itemNumber) {
    return deliveryItemOverrideRepo.findByDeliveryNumberAndItemNumber(deliveryNumber, itemNumber);
  }

  /**
   * Find DeliveryItemOverride by Delivery Number and Item Number
   *
   * @param itemNumber
   * @return DeliveryItemOverride
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Optional<DeliveryItemOverride> findByItemNumber(Long itemNumber) {
    return deliveryItemOverrideRepo.findTopByItemNumberOrderByLastChangedTsDesc(itemNumber);
  }

  /**
   * Delete DeliveryItemOverride by Delivery Number and Item Number
   *
   * @param deliveryNumber
   * @param itemNumber
   * @return void
   */
  @Transactional
  @InjectTenantFilter
  public void deleteByDeliveryNumberAndItemNumber(Long deliveryNumber, Long itemNumber) {

    deliveryItemOverrideRepo.deleteByDeliveryNumberAndItemNumber(deliveryNumber, itemNumber);
  }

  /**
   * Manager overrides
   *
   * @param action
   * @param deliveryNumber
   * @param overrideRequest
   * @return deliveryMetaData
   */
  public DeliveryMetaData override(
      String action, String deliveryNumber, OverrideRequest overrideRequest) {
    DeliveryMetaData deliveryMetaData = null;
    if (ReceivingConstants.EXPIRY.equalsIgnoreCase(action)) {
      deliveryMetaData =
          witronDeliveryMetaDataService.doManagerOverride(
              overrideRequest.getUserId(),
              deliveryNumber,
              overrideRequest.getPurchaseReferenceNumber(),
              overrideRequest.getPurchaseReferenceLineNumber(),
              ReceivingConstants.IGNORE_EXPIRY);
    } else if (ReceivingConstants.OVERAGES.equalsIgnoreCase(action)) {
      deliveryMetaData =
          witronDeliveryMetaDataService.doManagerOverride(
              overrideRequest.getUserId(),
              deliveryNumber,
              overrideRequest.getPurchaseReferenceNumber(),
              overrideRequest.getPurchaseReferenceLineNumber(),
              ReceivingConstants.IGNORE_OVERAGE);
    } else if (ReceivingConstants.HACCP.equalsIgnoreCase(action)) {
      deliveryMetaData =
          witronDeliveryMetaDataService.doManagerOverride(
              overrideRequest.getUserId(),
              deliveryNumber,
              overrideRequest.getPurchaseReferenceNumber(),
              overrideRequest.getPurchaseReferenceLineNumber(),
              ReceivingConstants.APPROVED_HACCP);
    }
    return deliveryMetaData;
  }

  /**
   * Updates the DeliveryItemOverride
   *
   * @param itemOverrideRequest
   * @param httpHeaders
   */
  @Transactional
  @InjectTenantFilter
  public void updateDeliveryItemOverride(
      ItemOverrideRequest itemOverrideRequest, HttpHeaders httpHeaders) {
    DeliveryItemOverride deliveryItemOverride = null;
    if (Objects.isNull(itemOverrideRequest.getIsAtlasItem())) {
      itemOverrideRequest.setIsAtlasItem(Boolean.FALSE);
    }
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        itemOverrideRequest.getIsAtlasItem()
            ? findByDeliveryNumberAndItemNumber(
                itemOverrideRequest.getDeliveryNumber(), itemOverrideRequest.getItemNumber())
            : findByItemNumber(itemOverrideRequest.getItemNumber());
    Map<String, String> itemMiscInfo = new HashMap<>();
    if (deliveryItemOverrideOptional.isPresent()) {
      deliveryItemOverride = deliveryItemOverrideOptional.get();
      LOGGER.info("Delivery Item Override by item number: {}", deliveryItemOverride);
      itemMiscInfo = deliveryItemOverride.getItemMiscInfo();
    } else {
      deliveryItemOverride = new DeliveryItemOverride();
      deliveryItemOverride.setDeliveryNumber(itemOverrideRequest.getDeliveryNumber());
      deliveryItemOverride.setItemNumber(itemOverrideRequest.getItemNumber());
    }
    deliveryItemOverride.setLastChangedUser(
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    deliveryItemOverride.setLastChangedTs(new Date());
    if (StringUtils.isNotBlank(itemOverrideRequest.getPackTypeCode())) {
      itemMiscInfo.put(ReceivingConstants.PACK_TYPE_CODE, itemOverrideRequest.getPackTypeCode());
    }
    if (StringUtils.isNotBlank(itemOverrideRequest.getTemporaryPackTypeCode())) {
      itemMiscInfo.put(
          ReceivingConstants.TEMPORARY_PACK_TYPE_CODE,
          itemOverrideRequest.getTemporaryPackTypeCode());
    }
    if (StringUtils.isNotBlank(itemOverrideRequest.getHandlingMethodCode())) {
      itemMiscInfo.put(
          ReceivingConstants.HANDLING_CODE, itemOverrideRequest.getHandlingMethodCode());
    }
    if (StringUtils.isNotBlank(itemOverrideRequest.getTemporaryHandlingMethodCode())) {
      itemMiscInfo.put(
          ReceivingConstants.TEMPORARY_HANDLING_METHOD_CODE,
          itemOverrideRequest.getTemporaryHandlingMethodCode());
    }
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    LOGGER.info("Update Delivery item override with record: {}", deliveryItemOverride);
    deliveryItemOverrideRepo.save(deliveryItemOverride);
  }

  /**
   * Validate and Update OriginCountryCode & WHPK/VNPK Acknowledge Info
   *
   * @param saveConfirmationRequest
   * @param httpHeaders
   * @return void
   */
  @Transactional
  @InjectTenantFilter
  public void updateCountryOfOriginAndPackAknowlegementInfo(
      SaveConfirmationRequest saveConfirmationRequest, HttpHeaders httpHeaders) {
    DeliveryItemOverride deliveryItemOverride = null;
    Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
        findByDeliveryNumberAndItemNumber(
            saveConfirmationRequest.getDeliveryNumber(), saveConfirmationRequest.getItemNumber());
    Map<String, String> itemMiscInfo = new HashMap<>();
    if (deliveryItemOverrideOptional.isPresent()) {
      deliveryItemOverride = deliveryItemOverrideOptional.get();
      LOGGER.info("Delivery Item Override by item number: {}", deliveryItemOverride);
      itemMiscInfo = deliveryItemOverride.getItemMiscInfo();
    } else {
      deliveryItemOverride = new DeliveryItemOverride();
      deliveryItemOverride.setDeliveryNumber(saveConfirmationRequest.getDeliveryNumber());
      deliveryItemOverride.setItemNumber(saveConfirmationRequest.getItemNumber());
    }
    deliveryItemOverride.setLastChangedUser(
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    deliveryItemOverride.setLastChangedTs(new Date());
    validateOriginOfCountryAknowledgeInfo(itemMiscInfo, saveConfirmationRequest);
    validatePackAknowledgeInfo(itemMiscInfo, saveConfirmationRequest);
    deliveryItemOverride.setItemMiscInfo(itemMiscInfo);
    deliveryItemOverrideRepo.save(deliveryItemOverride);
    LOGGER.info("Updated Delivery item override with OCC record: {}", deliveryItemOverride);
  }

  /**
   * Helper method that validates OriginCountryCode Acknowledge Info
   *
   * @param itemMiscInfo
   * @param saveConfirmationRequest
   * @return void
   */
  private void validateOriginOfCountryAknowledgeInfo(
      Map<String, String> itemMiscInfo, SaveConfirmationRequest saveConfirmationRequest) {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED)) {
      if (!CollectionUtils.isEmpty(saveConfirmationRequest.getOriginCountryCode())) {
        itemMiscInfo.put(
            ReceivingConstants.ORIGIN_COUNTRY_CODE,
            saveConfirmationRequest.getOriginCountryCode().toString());
        itemMiscInfo.put(
            ReceivingConstants.ORIGIN_COUNTRY_CODE_RESPONSE,
            String.valueOf(saveConfirmationRequest.getIsOriginCountryCodeAcknowledged()));
        itemMiscInfo.put(
            ReceivingConstants.ORIGIN_COUNTRY_CODE_CONDITIONAL_ACK,
            String.valueOf(
                saveConfirmationRequest.getIsOriginCountryCodeConditionalAcknowledged()));
      } else {
        LOGGER.info(
            "originCountryCode is empty in request for Delivery: {} and Item: {} ",
            saveConfirmationRequest.getDeliveryNumber(),
            saveConfirmationRequest.getItemNumber());
        throw new ReceivingBadDataException(
            ExceptionCodes.MISSING_MANDATORY_FIELDS, ReceivingConstants.OCC_FIELDS_MISSING);
      }
    }
  }

  /**
   * Helper method that validates pack Acknowledge Info
   *
   * @param itemMiscInfo
   * @param saveConfirmationRequest
   * @return void
   */
  private void validatePackAknowledgeInfo(
      Map<String, String> itemMiscInfo, SaveConfirmationRequest saveConfirmationRequest) {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED)) {
      if (saveConfirmationRequest.getWhpkQty() != null
          && saveConfirmationRequest.getVnpkQty() != null) {
        itemMiscInfo.put(
            ReceivingConstants.PACK_TYPE_RESPONSE,
            String.valueOf(saveConfirmationRequest.getIsPackTypeAcknowledged()));
        itemMiscInfo.put(
            ReceivingConstants.VNPK, String.valueOf(saveConfirmationRequest.getVnpkQty()));
        itemMiscInfo.put(
            ReceivingConstants.WHPK, String.valueOf(saveConfirmationRequest.getWhpkQty()));
      } else {
        LOGGER.info(
            "WHPK or VNPK is empty in request for Delivery: {} and Item: {} ",
            saveConfirmationRequest.getDeliveryNumber(),
            saveConfirmationRequest.getItemNumber());
        throw new ReceivingBadDataException(
            ExceptionCodes.MISSING_MANDATORY_FIELDS, ReceivingConstants.PACK_FIELDS_MISSING);
      }
    }
  }
}
