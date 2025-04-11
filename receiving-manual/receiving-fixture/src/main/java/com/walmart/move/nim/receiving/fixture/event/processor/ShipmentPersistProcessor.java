package com.walmart.move.nim.receiving.fixture.event.processor;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.fixture.client.ItemREPServiceClient;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.mapper.ContainerAndItemMapper;
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import com.walmart.move.nim.receiving.fixture.service.FixtureDeliveryMetadataService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

public class ShipmentPersistProcessor extends AbstractEventProcessor<ShipmentEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShipmentPersistProcessor.class);

  @Resource(name = FixtureConstants.SHIPMENT_PERSIST_PROCESSOR_BEAN)
  private ShipmentPersistProcessor selfReferenceShipmentPersistProcessor;

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private ItemREPServiceClient itemREPServiceClient;

  @Resource(name = ReceivingConstants.FIXTURE_DELIVERY_METADATA_SERVICE)
  private FixtureDeliveryMetadataService fixtureDeliveryMetadataService;

  @Override
  protected AbstractEventProcessor<ShipmentEvent> getSelfReference() {
    return selfReferenceShipmentPersistProcessor;
  }

  @Override
  protected boolean canExecute(ShipmentEvent shipmentEvent) {
    return true;
  }

  @Override
  public void executeStep(ShipmentEvent shipmentEvent) {
    removeAlreadyPersistedPacks(shipmentEvent.getPackList());
    if (CollectionUtils.isEmpty(shipmentEvent.getPackList())) {
      LOGGER.info(
          "No new pack found for shipment {} of hashcode {}. Continue...",
          shipmentEvent.getShipment().getShipmentNumber(),
          shipmentEvent.getShipment().getShipmentNumber().hashCode());
      return;
    }
    List<Container> containerList = new ArrayList<>();
    shipmentEvent
        .getPackList()
        .forEach(
            pack ->
                containerList.add(
                    ContainerAndItemMapper.createContainerFromPacks(
                        shipmentEvent.getShipment(), pack)));
    enrichItemAttributes(containerList);
    persistContainersAndItems(containerList);
    fixtureDeliveryMetadataService.persistsShipmentMetadata(shipmentEvent.getShipment());
  }

  private void removeAlreadyPersistedPacks(List<Pack> packList) {
    if (CollectionUtils.isEmpty(packList)) {
      return;
    }
    List<String> packIds = packList.stream().map(Pack::getPackNumber).collect(Collectors.toList());
    List<String> existingParentTrackingIds =
        containerPersisterService.getExistingParentTrackingIds(packIds);
    if (CollectionUtils.isEmpty(existingParentTrackingIds)) {
      LOGGER.info("All new {} packs are present", packList.size());
      return;
    }
    List<Pack> alreadyPersistedPacks =
        packList
            .stream()
            .filter(pack -> existingParentTrackingIds.contains(pack.getPackNumber()))
            .collect(Collectors.toList());
    LOGGER.info(
        "{} new and {} old packs are present",
        packList.size() - alreadyPersistedPacks.size(),
        alreadyPersistedPacks.size());
    packList.removeAll(alreadyPersistedPacks);
  }
}
