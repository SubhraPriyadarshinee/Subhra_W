package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.model.ItemInfoResponse;
import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.core.model.SaveConfirmationRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class ItemService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryItemOverrideService.class);
  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ContainerRepository containerRepository;

  public List<Long> findLatestItemByUPC(String upc) {
    List<Long> itemNumberList =
        containerRepository.findLatestItemByUPC(
            upc, TenantContext.getFacilityCountryCode(), TenantContext.getFacilityNum());
    if (CollectionUtils.isEmpty(itemNumberList)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.UPC_NOT_FOUND,
          String.format(
              "UPC=%s is not found on tenant facilityCountryCode=%s facilityNum=%s",
              upc, TenantContext.getFacilityCountryCode(), TenantContext.getFacilityNum()));
    }
    return itemNumberList;
  }

  public List<ItemInfoResponse> findItemBaseDivCodesByUPC(String upc) {
    List<ItemInfoResponse> itemBaseDivCodeList =
        containerRepository.findItemBaseDivCodesByUPC(
            upc, TenantContext.getFacilityCountryCode(), getFacilityNum());
    return itemBaseDivCodeList;
  }

  /**
   * Update item properties and persists in Delivery Item Override table
   *
   * @param itemOverrideRequest
   * @param httpHeaders
   */
  public void itemOverride(ItemOverrideRequest itemOverrideRequest, HttpHeaders httpHeaders) {
    ItemServiceHandler itemServiceHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.ITEM_SERVICE_HANDLER,
            ItemServiceHandler.class);
    itemServiceHandler.updateItemProperties(itemOverrideRequest, httpHeaders);
  }

  /**
   * Throw Exception in response to client if user confirms mismatch in OriginCountryCode or
   * WHPK/VNPK
   *
   * @param saveOriginAndPackConfirmationRequest
   */
  public ResponseEntity<Object> saveConfirmationRequest(
      SaveConfirmationRequest saveOriginAndPackConfirmationRequest) {
    if ((tenantSpecificConfigReader.isFeatureFlagEnabled(
                ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED)
            && !saveOriginAndPackConfirmationRequest.getIsOriginCountryCodeAcknowledged()
            && !saveOriginAndPackConfirmationRequest
                .getIsOriginCountryCodeConditionalAcknowledged())
        && (tenantSpecificConfigReader.isFeatureFlagEnabled(
                ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED)
            && !saveOriginAndPackConfirmationRequest.getIsPackTypeAcknowledged())) {
      LOGGER.info(
          "Country of Origin & WHPK/VNPK on pack does not match with system. Please contact support to proceed with Delivery: {} and Item: {} ",
          saveOriginAndPackConfirmationRequest.getDeliveryNumber(),
          saveOriginAndPackConfirmationRequest.getItemNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.GLS_RCV_ITEM_SAVE_CONFIRMATION_CODE,
          ReceivingConstants.GLS_RCV_ITEM_SAVE_COO_PACK_CONFIRMATION_DESCRIPTION);
    } else if (tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED)
        && !saveOriginAndPackConfirmationRequest.getIsOriginCountryCodeAcknowledged()
        && !saveOriginAndPackConfirmationRequest.getIsOriginCountryCodeConditionalAcknowledged()) {
      LOGGER.info(
          "Country of Origin on pack does not match with system. Please contact support to proceed with Delivery: {} and Item: {} ",
          saveOriginAndPackConfirmationRequest.getDeliveryNumber(),
          saveOriginAndPackConfirmationRequest.getItemNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.GLS_RCV_ITEM_SAVE_COO_CONFIRMATION_CODE,
          ReceivingConstants.GLS_RCV_ITEM_SAVE_COO_CONFIRMATION_DESCRIPTION);
    } else if (tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_VALIDATE_PACK_TYPE_ACK_ENABLED)
        && !saveOriginAndPackConfirmationRequest.getIsPackTypeAcknowledged()) {
      LOGGER.info(
          " WHPK/VNPK on pack does not match with system. Please contact support to proceed with Delivery: {} and Item: {} ",
          saveOriginAndPackConfirmationRequest.getDeliveryNumber(),
          saveOriginAndPackConfirmationRequest.getItemNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.GLS_RCV_ITEM_SAVE_PACK_CONFIRMATION_CODE,
          ReceivingConstants.GLS_RCV_ITEM_SAVE_PACK_CONFIRMATION_DESCRIPTION);
    } else return new ResponseEntity<>(HttpStatus.OK);
  }
}
