package com.walmart.move.nim.receiving.acc.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.model.VendorUpcUpdateRequest;
import com.walmart.move.nim.receiving.acc.model.acl.label.ACLLabelCount;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** This class is for all interactions with the ACL. */
public class ACLService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ACLService.class);

  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  @Autowired protected Gson gson;

  @Resource(name = "restConnector")
  private RestConnector simpleRestConnector;

  /**
   * Calls ACL to check for labels
   *
   * @param deliveryNumber
   * @return
   */
  @Timed(
      name = "fetchLabelsFromACLTimed",
      level1 = "uwms-receiving",
      level2 = "aclService",
      level3 = "fetchLabelsFromACL")
  @ExceptionCounted(
      name = "fetchLabelsFromACLExceptionCount",
      level1 = "uwms-receiving",
      level2 = "aclService",
      level3 = "fetchLabelsFromACL")
  public ACLLabelCount fetchLabelsFromACL(Long deliveryNumber) {
    String url = accManagedConfig.getAclBaseUrl() + ACCConstants.READINESS + deliveryNumber;

    LOGGER.info("ACL service : Fetching label count for {}", deliveryNumber);

    ResponseEntity<ACLLabelCount> response = null;
    ACLLabelCount aclLabelCount = null;
    try {
      response = simpleRestConnector.get(url, ACLLabelCount.class);
    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == HttpStatus.NOT_FOUND.value()) {
        LOGGER.error("Error: Labels not found for delivery {}", deliveryNumber, e);
        return null;
      } else if (e.getRawStatusCode() == HttpStatus.CONFLICT.value()) {
        LOGGER.error("Error: Couldn't fetch label count for delivery {}.", deliveryNumber, e);
        throw new ReceivingInternalException(ExceptionCodes.ACL_ERROR, ACCConstants.SYSTEM_BUSY);
      } else {
        LOGGER.error("Error: Couldn't fetch label count for delivery {}.", deliveryNumber);
        return new ACLLabelCount();
      }
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(ExceptionCodes.ACL_ERROR, ACCConstants.SYSTEM_BUSY);
    }
    if (Objects.nonNull(response) && Objects.nonNull(response.getBody())) {
      aclLabelCount = response.getBody();
    }
    LOGGER.info("ACL service responded with response: {}", aclLabelCount);
    return aclLabelCount;
  }

  /**
   * This method makes a request to ACL for vendor upc update
   *
   * @param vendorUpcUpdateRequest
   * @param httpHeaders
   */
  @Timed(
      name = "updateVendorUpcTimed",
      level1 = "uwms-receiving",
      level2 = "aclService",
      level3 = "updateVendorUpc")
  @ExceptionCounted(
      name = "updateVendorUpcExceptionCount",
      level1 = "uwms-receiving",
      level2 = "aclService",
      level3 = "updateVendorUpc")
  public void updateVendorUpc(
      VendorUpcUpdateRequest vendorUpcUpdateRequest, HttpHeaders httpHeaders) {

    String url = accManagedConfig.getAclBaseUrl() + ACCConstants.VENDOR_UPC_UPDATE_TO_ACL;

    try {
      ResponseEntity<String> response =
          simpleRestConnector.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(vendorUpcUpdateRequest), httpHeaders),
              String.class);
      LOGGER.info(
          "Received response:{} from ACL for updating vendorUPC:{} against "
              + "delivery:{} and item:{}",
          response.getStatusCodeValue(),
          vendorUpcUpdateRequest.getCatalogGTIN(),
          vendorUpcUpdateRequest.getDeliveryNumber(),
          vendorUpcUpdateRequest.getItemNumber());
    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == HttpStatus.BAD_REQUEST.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_ITEM_DETAILS,
            String.format(
                ACCConstants.ACL_CATALOG_BAD_REQUEST,
                vendorUpcUpdateRequest.getDeliveryNumber(),
                vendorUpcUpdateRequest.getItemNumber()));
      }
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(ExceptionCodes.ACL_ERROR, ACCConstants.SYSTEM_BUSY);
    }
  }
}
