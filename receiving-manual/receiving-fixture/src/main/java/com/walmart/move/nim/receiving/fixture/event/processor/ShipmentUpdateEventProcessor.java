package com.walmart.move.nim.receiving.fixture.event.processor;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.mapper.ContainerAndItemMapper;
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

public class ShipmentUpdateEventProcessor extends AbstractEventProcessor<ShipmentEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShipmentUpdateEventProcessor.class);

  @Resource(name = FixtureConstants.SHIPMENT_UPDATE_PROCESSOR_BEAN)
  private ShipmentUpdateEventProcessor selfReferenceShipmentUpdateProcessor;

  @Override
  protected AbstractEventProcessor<ShipmentEvent> getSelfReference() {
    return selfReferenceShipmentUpdateProcessor;
  }

  @Autowired private ContainerPersisterService containerPersisterService;

  @Override
  protected boolean canExecute(ShipmentEvent shipmentEvent) {
    return true;
  }

  @Override
  public void executeStep(ShipmentEvent shipmentEvent) {
    if (CollectionUtils.isEmpty(shipmentEvent.getPackList())) {
      return;
    }
    List<Container> alreadyPersistedContainers =
        getAlreadyPersistedContainers(shipmentEvent.getPackList());
    if (CollectionUtils.isEmpty(alreadyPersistedContainers)) {
      LOGGER.info(
          "No update. All new {} packs are present in the shipment",
          alreadyPersistedContainers.size());
    } else {
      LOGGER.info(
          "{} new and {} old packs are present",
          shipmentEvent.getPackList().size() - alreadyPersistedContainers.size(),
          alreadyPersistedContainers.size());
      updateContainers(alreadyPersistedContainers, shipmentEvent);
    }
  }

  private List<Container> getAlreadyPersistedContainers(List<Pack> packList) {
    List<String> packIds = packList.stream().map(Pack::getPackNumber).collect(Collectors.toList());
    return containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
        packIds, Collections.singletonList(ReceivingConstants.STATUS_PENDING_COMPLETE));
  }

  private void updateContainers(
      List<Container> alreadyPersistedContainers, ShipmentEvent shipmentEvent) {
    long shipmentHashCode = shipmentEvent.getShipment().getShipmentNumber().hashCode();
    Map<String, Container> containerMapByParentTrackingID =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    alreadyPersistedContainers.forEach(
        container ->
            containerMapByParentTrackingID.put(container.getParentTrackingId(), container));
    List<Pack> updatedPacks = new ArrayList<>();
    List<Container> updatedContainerList = new ArrayList<>();
    shipmentEvent
        .getPackList()
        .forEach(
            pack -> {
              Container container = containerMapByParentTrackingID.get(pack.getPackNumber());
              if (Objects.nonNull(container)) {
                // Update container which is not received yet
                LOGGER.info(
                    "Received event {} for container {} for shipment {} of hashcode {} not in received status",
                    shipmentEvent.getEventType(),
                    pack.getPackNumber(),
                    shipmentEvent.getShipment().getShipmentNumber(),
                    shipmentHashCode);
                updateContainerFromPack(
                    container, shipmentEvent.getShipment(), pack, shipmentHashCode);
                updatedPacks.add(pack);
                updatedContainerList.add(container);
              }
            });
    if (!CollectionUtils.isEmpty(updatedPacks)) {
      LOGGER.info("Matching no. of packs that are updated {}", updatedPacks.size());
      // enriching item attributes as new container item can be added or item attribute can change
      enrichItemAttributes(updatedContainerList);
      persistContainersAndItems(updatedContainerList);
      shipmentEvent.getPackList().removeAll(updatedPacks);
    }
  }

  private void updateContainerFromPack(
      Container container, Shipment shipment, Pack pack, long shipmentHashCode) {
    LOGGER.info(
        "Update event for container {} and delivery {} came in shipment no. {} of hashcode {}",
        container.getDeliveryNumber(),
        container.getParentTrackingId(),
        shipment.getShipmentNumber(),
        shipmentHashCode);
    ContainerAndItemMapper.setContainerAttributes(
        container,
        ReceivingConstants.DEFAULT_USER,
        shipment.getShipmentNumber().hashCode(),
        pack.getPackNumber(),
        pack.getHandledOnBehalfOf());
    Map<Long, ContainerItem> containerItemMap = new HashMap<>();
    container
        .getContainerItems()
        .forEach(
            containerItem -> containerItemMap.put(containerItem.getItemNumber(), containerItem));

    List<ContainerItem> newItemList = new ArrayList<>();
    pack.getItems()
        .forEach(
            item -> {
              ContainerItem containerItem = containerItemMap.get(item.getItemNumber());
              if (Objects.nonNull(containerItem)) {
                LOGGER.info(
                    "Updating containerItem {} for pallet {} and shipment {} of hashcode {}.",
                    item.getItemNumber(),
                    pack.getPackNumber(),
                    shipment.getShipmentNumber(),
                    (long) shipment.getShipmentNumber().hashCode());
                ContainerAndItemMapper.setContainerItemAttributes(
                    item, pack.getPackNumber(), containerItem);
              } else {
                LOGGER.info(
                    "ContainerItem {} doesn't exists for pallet {} and shipment {} of hashcode {}. Creating a new container.",
                    item.getItemNumber(),
                    pack.getPackNumber(),
                    shipment.getShipmentNumber(),
                    (long) shipment.getShipmentNumber().hashCode());

                newItemList.add(
                    ContainerAndItemMapper.setContainerItemAttributes(
                        item, pack.getPackNumber(), new ContainerItem()));
              }
            });
    container.getContainerItems().addAll(newItemList);
  }
}
