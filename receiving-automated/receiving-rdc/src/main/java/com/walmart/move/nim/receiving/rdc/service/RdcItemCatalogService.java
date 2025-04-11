package com.walmart.move.nim.receiving.rdc.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.itemupdate.ItemUpdateRestApiClient;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.core.service.DefaultItemCatalogService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.ItemCatalogRequestToNGR;
import com.walmart.move.nim.receiving.rdc.utils.RdcAutoReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Service class for item cataloging
 *
 * @author s0g015w
 */
public class RdcItemCatalogService extends DefaultItemCatalogService {

  public static final Logger LOGGER = LoggerFactory.getLogger(RdcItemCatalogService.class);

  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private RetryableRestConnector retryableRestConnector;
  @Autowired private Gson gson;
  @Autowired private ItemUpdateRestApiClient itemUpdateRestApiClient;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private LabelDataService labelDataService;

  @Autowired private RdcAutoReceivingUtils rdcAutoReceivingUtils;

  @Autowired private RdcReceivingUtils rdcReceivingUtils;

  /**
   * Makes a call to NG Receiving and GDM to update vendor UPC for a given delivery and item number.
   * Update Item Cache only for GTINs having length greater than 8
   *
   * @param itemCatalogUpdateRequest
   * @param httpHeaders
   * @return String
   */
  public String updateVendorUPC(
      ItemCatalogUpdateRequest itemCatalogUpdateRequest, HttpHeaders httpHeaders) {
    String scannedUpc = itemCatalogUpdateRequest.getNewItemUPC();
    String itemCatalogedUpcResponse = super.updateVendorUPC(itemCatalogUpdateRequest, httpHeaders);

    // Need to pass scanned UPC to receiving item cache, so resetting newItemUpc with scannedUpc
    // value
    itemCatalogUpdateRequest.setNewItemUPC(scannedUpc);
    if (rdcReceivingUtils.isNGRServicesEnabled()) {
      updateVendorUpcInNGR(getItemCatalogRequest(itemCatalogUpdateRequest), httpHeaders);
    }
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false)
        && rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(
            Long.valueOf(itemCatalogUpdateRequest.getDeliveryNumber()),
            itemCatalogUpdateRequest.getItemNumber())) {
      rdcAutoReceivingUtils.updateCatalogInHawkeye(itemCatalogUpdateRequest, httpHeaders);
    }
    return itemCatalogedUpcResponse;
  }

  private ItemCatalogRequestToNGR getItemCatalogRequest(
      ItemCatalogUpdateRequest itemCatalogUpdateRequest) {
    ItemCatalogRequestToNGR itemCatalogRequestToNGR = new ItemCatalogRequestToNGR();
    itemCatalogRequestToNGR.setDeliveryNumber(itemCatalogUpdateRequest.getDeliveryNumber());
    itemCatalogRequestToNGR.setNumber(itemCatalogUpdateRequest.getItemNumber().toString());
    itemCatalogRequestToNGR.setCaseUPC(itemCatalogUpdateRequest.getNewItemUPC());
    return itemCatalogRequestToNGR;
  }

  /**
   * Makes a http call to NG Receiving load to update vendor upc in receiving item cache
   *
   * @param itemCatalogRequestToNGR
   * @param httpHeaders
   * @return String
   */
  @Timed(
      name = "updateVendorUpcInNGRTimed",
      level1 = "uwms-receiving",
      level2 = "catalogUPCInNGR",
      level3 = "updateVendorUpcInNGR")
  @ExceptionCounted(
      name = "updateVendorUpcInNGRCount",
      level1 = "uwms-receiving",
      level2 = "catalogUPCInNGR",
      level3 = "updateVendorUpcInNGR")
  public String updateVendorUpcInNGR(
      ItemCatalogRequestToNGR itemCatalogRequestToNGR, HttpHeaders httpHeaders) {
    String siteId = StringUtils.leftPad(TenantContext.getFacilityNum().toString(), 5, "0");
    String baseUrl =
        String.format(
            RdcUtils.getExternalServiceBaseUrlByTenant(rdcManagedConfig.getNgrBaseUrl()), siteId);
    httpHeaders.add(ReceivingConstants.REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.NGR_TENANT_MAPPING_ENABLED,
        false)) {
      siteId = RdcUtils.getMappedTenant(baseUrl);
    }
    String loadUrl = String.format(RdcConstants.ITEM_CATALOG_REQUEST_URL, baseUrl, siteId);
    HttpEntity httpEntity = new HttpEntity(gson.toJson(itemCatalogRequestToNGR), httpHeaders);
    LOGGER.info(
        "Making a call to NGR load service with url:{} and request:{} for UPC catalog",
        loadUrl,
        itemCatalogRequestToNGR);
    try {
      ResponseEntity<String> responseEntity =
          retryableRestConnector.exchange(loadUrl, HttpMethod.POST, httpEntity, String.class);
      if (responseEntity.getStatusCodeValue() == HttpStatus.OK.value()) {
        LOGGER.info(
            "Received response:{} from NGR for updating vendorUPC:{} against " + "and item:{}",
            responseEntity.getStatusCodeValue(),
            itemCatalogRequestToNGR.getCaseUPC(),
            itemCatalogRequestToNGR.getNumber());

        if (Objects.isNull(responseEntity) || !responseEntity.hasBody()) {
          LOGGER.error(
              ReceivingConstants.RESTUTILS_INFO_MESSAGE,
              loadUrl,
              itemCatalogRequestToNGR,
              responseEntity);
          throw new ReceivingBadDataException(
              ExceptionCodes.ITEM_NOT_FOUND,
              String.format(
                  ReceivingConstants.INVALID_ITEM_CATALOG_RESPONSE_FROM_NGR,
                  RdcConstants.NGR_LOAD,
                  itemCatalogRequestToNGR.getCaseUPC(),
                  itemCatalogRequestToNGR.getNumber()));
        }
        return responseEntity.getBody();
      }
    } catch (RestClientResponseException e) {
      LOGGER.error(
          String.format(
              ReceivingConstants.BAD_RESPONSE_ERROR_MSG,
              RdcConstants.NGR_LOAD,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_ITEM_DETAILS,
          String.format(
              ReceivingConstants.NOT_A_VALID_ITEM_CATALOG_REQUEST,
              RdcConstants.NGR_LOAD,
              itemCatalogRequestToNGR.getCaseUPC(),
              itemCatalogRequestToNGR.getNumber()));

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          loadUrl,
          itemCatalogRequestToNGR,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.ITEM_NOT_FOUND,
          String.format(
              ExceptionDescriptionConstants.RECEIVING_LOAD_SERVICE_DOWN_ERROR_MSG, e.getMessage()));
    }
    return null;
  }
}
