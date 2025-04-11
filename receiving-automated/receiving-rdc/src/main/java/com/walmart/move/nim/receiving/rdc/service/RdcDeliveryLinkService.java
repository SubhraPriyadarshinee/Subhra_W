package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.UUID.randomUUID;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeLabelGroupUpdateRequest;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.Label;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.LabelReadinessRequest;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.LabelReadinessResponse;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryLinkRequest;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.rdc.constants.GroupType;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class RdcDeliveryLinkService {

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired LabelDataService labelDataService;
  @Autowired private Gson gson;
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  @Autowired
  private DeliveryService deliveryService;

  private static final Logger logger = LoggerFactory.getLogger(RdcDeliveryLinkService.class);

  /**
   * This method validates label readiness for the given location and sends Label Group Update to
   * Hawkeye for linking the current delivery to the location provided location is available.
   * Success response will be given to the caller when user tried to link same delivery to that
   * location. But exception will be thrown with appropriate error message to the caller when
   * location is already linked to a different delivery OR no label exists in receiving DB for the
   * delivery.
   *
   * @param deliveryLinkRequest - delivery link request body
   * @param httpHeaders http headers passed
   * @return ResponseEntity
   */
  public ResponseEntity<String> validateReadinessAndLinkDelivery(
      DeliveryLinkRequest deliveryLinkRequest, HttpHeaders httpHeaders) {
    logger.info("Delivery Link for request:{}", deliveryLinkRequest);
    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    // Change delivery to OPEN status if it is in ARV status
    if (DeliveryStatus.ARV.toString().equalsIgnoreCase(deliveryLinkRequest.getDeliveryStatus())
        && Objects.nonNull(deliveryLinkRequest.getDeliveryNumber())) {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          IS_DELIVERY_UPDATE_STATUS_ENABLED_BY_HTTP,
          false)) {
        deliveryService.updateDeliveryStatusToOpen(
            Long.parseLong(deliveryLinkRequest.getDeliveryNumber()), forwardableHeaders);
      } else {
        deliveryStatusPublisher.publishDeliveryStatus(
            Long.parseLong(deliveryLinkRequest.getDeliveryNumber()),
            DeliveryStatus.OPEN.toString(),
            null,
            forwardableHeaders);
      }
    }

    // Label readiness check for locationId
    ResponseEntity<String> readinessResponse =
        hawkeyeRestApiClient.checkLabelGroupReadinessStatus(
            buildLabelReadinessRequest(deliveryLinkRequest),
            buildHttpHeadersForHawkeye(httpHeaders));
    if (readinessResponse.getStatusCode() == HttpStatus.CONFLICT) {
      LabelReadinessResponse labelReadinessResponse =
          gson.fromJson(String.valueOf(readinessResponse.getBody()), LabelReadinessResponse.class);
      if (Objects.nonNull(labelReadinessResponse.getGroupNumber())
          && deliveryLinkRequest
              .getDeliveryNumber()
              .equals(labelReadinessResponse.getGroupNumber())) {
        logger.info(
            "Delivery: {} is already linked to location: {}",
            deliveryLinkRequest.getDeliveryNumber(),
            deliveryLinkRequest.getLocationId());
        return new ResponseEntity<>(HttpStatus.OK);
      }
      logger.info(
          "Delivery {} link error. Another delivery {} is still linked in SYM HMI.",
          deliveryLinkRequest.getDeliveryNumber(),
          labelReadinessResponse.getGroupNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.CONFLICT_LABEL_DETAILS,
          String.format(
              ReceivingConstants.LABEL_READINESS_CONFLICT_DESCRIPTION,
              labelReadinessResponse.getGroupNumber(),
              labelReadinessResponse.getGroupNumber()),
          labelReadinessResponse.getGroupNumber(),
          labelReadinessResponse.getGroupNumber());
    }
    boolean isAtlasLabelsGenerated =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false);
    Integer labelCount = 0;
    Integer itemCount = 0;
    if (isAtlasLabelsGenerated) {
      // Delivery Link Request for delivery and locationId
      labelCount =
          labelDataService.fetchLabelCountByDeliveryNumber(
              Long.valueOf(deliveryLinkRequest.getDeliveryNumber()));
      itemCount =
          labelDataService.fetchItemCountByDeliveryNumber(
              Long.valueOf(deliveryLinkRequest.getDeliveryNumber()));
      // Check for duplicate lpn scenario
      if (labelCount == 0 && itemCount == 0) {
        labelCount =
            labelDataService.fetchLabelCountByDeliveryNumberInLabelDownloadEvent(
                Long.valueOf(deliveryLinkRequest.getDeliveryNumber()));
        itemCount =
            labelDataService.fetchItemCountByDeliveryNumberInLabelDownloadEvent(
                Long.valueOf(deliveryLinkRequest.getDeliveryNumber()));
      }
    }

    /**
     * This condition is needed for complete Atlas, For Labels completely generated in NGR, we need
     * to allow linking too Removing this condition for now, we will add it in complete Atlas
     */
    //    if (!isAtlasLabelsGenerated || labelCount != 0) {
    HawkeyeLabelGroupUpdateRequest hawkeyeLabelGroupUpdateRequest =
        buildHawkeyeLabelGroupUpdateRequest(deliveryLinkRequest, labelCount, itemCount);
    return hawkeyeRestApiClient.sendLabelGroupUpdateToHawkeye(
        hawkeyeLabelGroupUpdateRequest,
        Long.valueOf(deliveryLinkRequest.getDeliveryNumber()),
        buildHttpHeadersForHawkeye(httpHeaders));
    //    } else {
    //      throw new ReceivingBadDataException(
    //          ExceptionCodes.LABELS_NOT_FOUND,
    //          String.format(
    //              ReceivingException.LABELS_NOT_FOUND, deliveryLinkRequest.getDeliveryNumber()),
    //          deliveryLinkRequest.getDeliveryNumber());
    //    }
  }

  private LabelReadinessRequest buildLabelReadinessRequest(
      DeliveryLinkRequest deliveryLinkRequest) {
    return LabelReadinessRequest.builder().locationId(deliveryLinkRequest.getLocationId()).build();
  }

  private HawkeyeLabelGroupUpdateRequest buildHawkeyeLabelGroupUpdateRequest(
      DeliveryLinkRequest deliveryLinkRequest, Integer labelCount, Integer itemCount) {
    Label label = Label.builder().itemsCount(itemCount).labelsCount(labelCount).build();
    return HawkeyeLabelGroupUpdateRequest.builder()
        .status(START_STATUS)
        .locationId(deliveryLinkRequest.getLocationId())
        .groupType(GroupType.RCV_MULTI_PO.toString())
        .label(label)
        .build();
  }

  private HttpHeaders buildHttpHeadersForHawkeye(HttpHeaders headers) {
    HttpHeaders hawkeyeHeaders = new HttpHeaders();
    String correlationId = headers.getFirst(CORRELATION_ID_HEADER_KEY);
    hawkeyeHeaders.add(
        CORRELATION_ID_HEADER_KEY,
        org.springframework.util.StringUtils.hasText(correlationId)
            ? correlationId
            : randomUUID().toString());
    hawkeyeHeaders.add(
        ReceivingConstants.WMT_FACILITY_NUM, String.valueOf(TenantContext.getFacilityNum()));
    hawkeyeHeaders.add(
        ReceivingConstants.WMT_FACILITY_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    hawkeyeHeaders.add(WMT_MSG_TIMESTAMP, ReceivingUtils.dateConversionToUTC(new Date()));
    hawkeyeHeaders.add(ReceivingConstants.CONTENT_TYPE, ReceivingConstants.APPLICATION_JSON);
    return hawkeyeHeaders;
  }
}
