package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CATALOG_MESSAGE_PUBLISH_FLOW;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.model.HawkEyeVendorUpcUpdateRequest;
import com.walmart.move.nim.receiving.acc.util.ACCUtils;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.service.DefaultItemCatalogService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.KAFKA_ACC_ITEM_CATALOG_SERVICE)
public class KafkaACCItemCatalogService extends DefaultItemCatalogService {
  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaACCItemCatalogService.class);

  @Autowired private Gson gson;

  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @SecurePublisher private KafkaTemplate securePublisher;

  @Value("${hawkeye.catalog.topic}")
  protected String hawkeyeCatalogTopic;

  @Override
  public String updateVendorUPC(
      ItemCatalogUpdateRequest itemCatalogUpdateRequest, HttpHeaders httpHeaders) {

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.PUBLISH_ON_HAWKEYE_CATALOG_TOPIC)) {
      publishCatalogUpdateToHawkeye(itemCatalogUpdateRequest, httpHeaders);
    }
    return super.updateVendorUPC(itemCatalogUpdateRequest, httpHeaders);
  }

  public void publishCatalogUpdateToHawkeye(
      ItemCatalogUpdateRequest itemCatalogUpdateRequest, HttpHeaders httpHeaders) {
    LocationInfo locationInfo = itemCatalogUpdateRequest.getLocationInfo();

    if (ACCUtils.checkIfLocationIsEitherOnlineOrFloorLine(locationInfo)) {
      HawkEyeVendorUpcUpdateRequest hawkEyeVendorUpcUpdateRequest =
          HawkEyeVendorUpcUpdateRequest.builder()
              .deliveryNumber(Long.valueOf(itemCatalogUpdateRequest.getDeliveryNumber()))
              .itemNumber(itemCatalogUpdateRequest.getItemNumber())
              .locationId(itemCatalogUpdateRequest.getLocationId())
              .orderableGTIN(itemCatalogUpdateRequest.getOldItemUPC())
              .catalogGTIN(itemCatalogUpdateRequest.getNewItemUPC())
              .build();

      Map<String, Object> messageHeader =
          ReceivingUtils.getForwardableHeaderWithEventType(
              httpHeaders,
              ACCConstants
                  .UPC_CATALOG_GLOBAL); // TODO once item mdm comes up with API to search by UPC,
      // use that logic to decide the header

      try {
        Message<String> message =
            KafkaHelper.buildKafkaMessage(
                itemCatalogUpdateRequest.getDeliveryNumber(),
                gson.toJson(hawkEyeVendorUpcUpdateRequest),
                hawkeyeCatalogTopic,
                messageHeader);
        securePublisher.send(message);
        LOGGER.info(
            "Successfully sent the catalog update message  = {} on topic = {}",
            gson.toJson(hawkEyeVendorUpcUpdateRequest),
            hawkeyeCatalogTopic);
      } catch (Exception exception) {
        LOGGER.error(
            "Unable to publish catalog update message {}", ExceptionUtils.getStackTrace(exception));
        throw new ReceivingInternalException(
            ExceptionCodes.KAFKA_NOT_ACCESSABLE,
            String.format(KAFKA_NOT_ACCESSIBLE_ERROR_MSG, CATALOG_MESSAGE_PUBLISH_FLOW));
      }
    }
  }
}
