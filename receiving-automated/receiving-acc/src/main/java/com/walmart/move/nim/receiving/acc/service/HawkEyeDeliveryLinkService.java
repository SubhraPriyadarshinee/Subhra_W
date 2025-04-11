package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.model.acl.label.ACLLabelCount;
import com.walmart.move.nim.receiving.acc.model.acl.notification.DeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.acc.model.acl.notification.HawkEyeDeliveryAndLocationMessage;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class HawkEyeDeliveryLinkService implements DeliveryLinkService {

  private static final Logger LOGGER = LoggerFactory.getLogger(HawkEyeDeliveryLinkService.class);

  @Autowired private HawkEyeService hawkeyeService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private LabelDataService labelDataService;

  @Override
  public void updateDeliveryLink(
      List<DeliveryAndLocationMessage> deliveryAndLocationMessage, HttpHeaders headers) {
    List<HawkEyeDeliveryAndLocationMessage> hawkEyeDeliveryAndLocationMessage =
        deliveryAndLocationMessage
            .stream()
            .map(
                dlm ->
                    new HawkEyeDeliveryAndLocationMessage(
                        Long.valueOf(dlm.getDeliveryNbr()), dlm.getLocation(), dlm.getUserId()))
            .collect(Collectors.toList());

    List<ACLLabelCount> aclLabelCounts =
        hawkeyeService.deliveryLink(hawkEyeDeliveryAndLocationMessage);

    LOGGER.info(
        "Successfully published location and delivery information over Kafka to Hawkeye(ACL) {}",
        hawkEyeDeliveryAndLocationMessage);

    validateLabelCounts(aclLabelCounts);
  }

  public void validateLabelCounts(List<ACLLabelCount> aclLabelCounts) {
    LOGGER.info("Received ACL Label Count for confirmation: {}", aclLabelCounts);

    for (ACLLabelCount aclLabelCount : aclLabelCounts) {
      Integer numberOfLabelsForDelivery =
          labelDataService.countByDeliveryNumber(aclLabelCount.getDeliveryNumber());
      if ((Objects.nonNull(numberOfLabelsForDelivery)
              && !numberOfLabelsForDelivery.equals(aclLabelCount.getLabelsCount()))
          || aclLabelCount.getLabelsCount().equals(0) // redundant
          || aclLabelCount.getLocation().equals("NOT_LINKED")
          || aclLabelCount.getEquipmentName().equals("NOT_LINKED")) {
        LOGGER.info(
            "FALLBACK: Republishing labels to ACL since no labels were found for delivery {}",
            aclLabelCount.getDeliveryNumber());
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.LABEL_GENERATOR_SERVICE,
                GenericLabelGeneratorService.class)
            .publishACLLabelDataForDelivery(
                aclLabelCount.getDeliveryNumber(), ReceivingUtils.getHeaders());
      }
    }
  }
}
