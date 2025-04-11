package com.walmart.move.nim.receiving.acc.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.model.acl.label.ACLLabelCount;
import com.walmart.move.nim.receiving.acc.model.acl.notification.HawkEyeDeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** This class is for all REST based interactions with the HawkEye */
public class HawkEyeService {

  private static final Logger LOGGER = LoggerFactory.getLogger(HawkEyeService.class);

  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  @Autowired protected Gson gson;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = "restConnector")
  private RestConnector simpleRestConnector;

  /**
   * Calls Hawk eye to perform delivery link
   *
   * @param deliveryLinkPayload
   * @return
   */
  @Timed(
      name = "deliveryLinkHawkEyeTimed",
      level1 = "uwms-receiving",
      level2 = "hawkEyeService",
      level3 = "deliveryLinkHawkEye")
  @ExceptionCounted(
      name = "deliveryLinkHawkEyeExceptionCount",
      level1 = "uwms-receiving",
      level2 = "hawkEyeService",
      level3 = "fdeliveryLinkHawkEye")
  public List<ACLLabelCount> deliveryLink(
      List<HawkEyeDeliveryAndLocationMessage> deliveryLinkPayload) {
    HttpHeaders headers = ReceivingUtils.getHeaders();
    headers.add(ReceivingConstants.EVENT_TYPE, ACCConstants.DELIVERY_LINK);
    headers.add(ReceivingConstants.VERSION, ACCConstants.DELIVERY_LINK_VERSION);
    headers.add(ReceivingConstants.MSG_TIMESTAMP, new Date().toString());
    String url =
        tenantSpecificConfigReader.getHawkEyeRoninUrlOrDefault(
                () -> accManagedConfig.getHawkEyeBaseUrl())
            + ACCConstants.HAWK_EYE_DELIVERY_LINK;

    LOGGER.info(
        "Hawkeye service : Publishing delivery link message. Payload: {}", deliveryLinkPayload);

    ResponseEntity<ACLLabelCount[]> response = null;
    List<ACLLabelCount> aclLabelCount = null;
    try {
      response = simpleRestConnector.post(url, deliveryLinkPayload, headers, ACLLabelCount[].class);
    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == HttpStatus.CONFLICT.value()) {
        LOGGER.error("Error: Couldn't fetch label count for delivery {}.", "deliveryNumber", e);
        throw new ReceivingConflictException(
            ExceptionCodes.HAWK_EYE_ERROR, ACCConstants.SYSTEM_BUSY);
      } else {
        LOGGER.error("Error: Couldn't fetch label count for delivery {}.", "deliveryNumber");
        return Collections.emptyList();
      }
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(ExceptionCodes.HAWK_EYE_ERROR, ACCConstants.SYSTEM_BUSY);
    }
    if (Objects.nonNull(response) && Objects.nonNull(response.getBody())) {
      aclLabelCount = Arrays.asList(response.getBody());
    }
    LOGGER.info("HawkEye service responded with response: {}", aclLabelCount);
    return aclLabelCount;
  }
}
