package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** This service will talk to GDM V3 */
@Service(ReceivingConstants.DELIVERY_SERVICE_V3)
public class DeliveryServiceV3Impl extends DeliveryServiceImpl {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryServiceV3Impl.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = "restConnector")
  private RestConnector restConnector;

  /**
   * This will connect to GDM V3
   *
   * @param deliveryUpdateMessage
   * @return
   * @throws ReceivingException
   */
  @Override
  public Delivery getGDMData(DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryUpdateMessage.getDeliveryNumber());

    String uri =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_DOCUMENT_GET_BY_DELIVERY_V3,
                pathParams)
            .toString();
    httpHeaders.set(
        HttpHeaders.ACCEPT, ReceivingConstants.GDM_DOCUMENT_GET_BY_DELIVERY_V3_ACCEPT_TYPE);

    ResponseEntity<String> deliveryResponseEntity = null;
    try {
      deliveryResponseEntity =
          restConnector.exchange(uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND, ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.GDM_NOT_ACCESSIBLE, ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE);
    }

    if (Objects.isNull(deliveryResponseEntity) || !deliveryResponseEntity.hasBody()) {
      LOGGER.error(ReceivingConstants.RESTUTILS_INFO_MESSAGE, uri, "", "");
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND, ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE);
    }
    Delivery delivery = gson.fromJson(deliveryResponseEntity.getBody(), Delivery.class);
    LOGGER.info(
        ReceivingConstants.RESTUTILS_INFO_MESSAGE,
        uri,
        deliveryUpdateMessage.getDeliveryNumber(),
        deliveryResponseEntity.getBody());
    return delivery;
  }
}
