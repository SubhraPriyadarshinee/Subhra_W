package com.walmart.move.nim.receiving.fixture.service;

import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FixtureDeliveryMetadataService extends DeliveryMetaDataService {

  private final Logger LOGGER = LoggerFactory.getLogger(FixtureDeliveryMetadataService.class);

  @Override
  public void updateAuditInfo(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList) {
    LOGGER.info("not implemented method doing nothing");
  }

  @Override
  public void updateDeliveryMetaDataForItemOverrides(
      DeliveryMetaData deliveryMetaData,
      String itemNumber,
      String rotateDate,
      String divertDestination) {
    LOGGER.info("not implemented method doing nothing");
  }

  @Override
  public List<DeliveryMetaData> findAndUpdateForOsdrProcessing(
      int allowedNoOfDaysAfterUnloadingComplete,
      long frequencyIntervalInMinutes,
      int pageSize,
      DeliveryPOMap deliveryPOMap) {
    LOGGER.info("not implemented method doing nothing");
    return null;
  }

  @Override
  public boolean updateAuditInfoInDeliveryMetaData(
      List<PurchaseOrder> purchaseOrders, int receivedQty, long deliveryNumber) {
    LOGGER.info("not implemented method doing nothing");
    return false;
  }

  @Override
  public int getReceivedQtyFromMetadata(Long itemNumber, long deliveryNumber) {
    LOGGER.info("not implemented method doing nothing");
    return 0;
  }

  @Override
  public DeliveryDoorSummary findDoorStatus(
      Integer facilityNumber, String countryCode, String doorNumber) {
    LOGGER.info("not implemented method doing nothing");
    return null;
  }

  public void persistsShipmentMetadata(Shipment shipment) {
    long shipmentHashcode = shipment.getShipmentNumber().hashCode();
    Optional<DeliveryMetaData> byShipment = findByDeliveryNumber(String.valueOf(shipmentHashcode));
    DeliveryMetaData deliveryMetaData;
    if (byShipment.isPresent()) {
      LOGGER.info(
          "Found shipment {} with hashcode {}, updating ",
          shipment.getShipmentNumber(),
          shipmentHashcode);
      deliveryMetaData = byShipment.get();
      deliveryMetaData.setTrailerNumber(shipment.getShipmentDetail().getLoadNumber());
    } else {
      LOGGER.info(
          "Saving shipment {} of hashcode {} with loadNumber {} ",
          shipment.getShipmentNumber(),
          shipmentHashcode,
          shipment.getShipmentDetail().getLoadNumber());
      deliveryMetaData =
          DeliveryMetaData.builder()
              .deliveryNumber(String.valueOf(shipmentHashcode))
              .trailerNumber(shipment.getShipmentDetail().getLoadNumber())
              .build();
    }
    save(deliveryMetaData);
  }
}
