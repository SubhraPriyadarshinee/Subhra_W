package com.walmart.move.nim.receiving.fixture.event.processor;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static java.util.stream.Collectors.groupingBy;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.InventoryItemPODetailUpdateRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.entity.ControlTowerTracker;
import com.walmart.move.nim.receiving.fixture.mapper.InventoryItemPODetailDTOMapper;
import com.walmart.move.nim.receiving.fixture.mapper.PutAwayDTOMapper;
import com.walmart.move.nim.receiving.fixture.model.PutAwayInventory;
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import com.walmart.move.nim.receiving.fixture.service.ControlTowerService;
import com.walmart.move.nim.receiving.fixture.service.FixtureDeliveryMetadataService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

public class ShipmentReconcileProcessor extends AbstractEventProcessor<ShipmentEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShipmentReconcileProcessor.class);

  @Autowired private TenantSpecificConfigReader configUtils;

  @Resource(name = FixtureConstants.SHIPMENT_RECONCILE_PROCESSOR_BEAN)
  private ShipmentReconcileProcessor selfReferenceShipmentReconcileProcessor;

  @Resource(name = ReceivingConstants.FIXTURE_DELIVERY_METADATA_SERVICE)
  private FixtureDeliveryMetadataService fixtureDeliveryMetadataService;

  @Autowired private ContainerPersisterService containerPersisterService;

  @Autowired private ControlTowerService controlTowerService;

  @Autowired private InventoryService inventoryService;

  @Autowired private ContainerService containerService;

  @Resource(name = ReceivingConstants.CONTAINER_TRANSFORMER_BEAN)
  private Transformer<Container, ContainerDTO> transformer;

  @Override
  protected AbstractEventProcessor<ShipmentEvent> getSelfReference() {
    return selfReferenceShipmentReconcileProcessor;
  }

  @Override
  protected boolean canExecute(ShipmentEvent shipmentEvent) {
    return true;
  }

  @Override
  public void executeStep(ShipmentEvent shipmentEvent) {
    if (CollectionUtils.isEmpty(shipmentEvent.getPackList())) {
      LOGGER.info(
          "No pack found for shipment {} of hashcode {}. Abort",
          shipmentEvent.getShipment().getShipmentNumber(),
          shipmentEvent.getShipment().getShipmentNumber().hashCode());
      return;
    }
    List<Container> containerReceivedWithoutASN =
        containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            shipmentEvent
                .getPackList()
                .stream()
                .map(Pack::getPackNumber)
                .collect(Collectors.toList()),
            FixtureConstants.CONTAINER_STATUS_WO_ASN);
    if (CollectionUtils.isEmpty(containerReceivedWithoutASN)) {
      LOGGER.info("No existing containers found for this shipment");
    } else {
      LOGGER.info(
          "No. of existing containers that are received without ASN {}",
          containerReceivedWithoutASN.size());
      reconcileContainers(
          containerReceivedWithoutASN, shipmentEvent.getShipment(), shipmentEvent.getPackList());
      fixtureDeliveryMetadataService.persistsShipmentMetadata(shipmentEvent.getShipment());
    }
  }

  private boolean isCTPostingEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), FixtureConstants.IS_CT_ENABLED, false);
  }

  private String getReplacedContainerStatusForWithoutASN(String containerStatus) {
    switch (containerStatus) {
      case ReceivingConstants.STATUS_ACTIVE_NO_ASN:
        return ReceivingConstants.STATUS_ACTIVE;
      case ReceivingConstants.STATUS_COMPLETE_NO_ASN:
        return ReceivingConstants.STATUS_COMPLETE;
      default:
        return containerStatus;
    }
  }

  private int reconcileContainerItems(List<ContainerItem> containerItems, List<Item> items) {
    int reconciledContainerItemCount = 0;
    Map<Long, List<Item>> packItemMap = items.stream().collect(groupingBy(Item::getItemNumber));
    for (ContainerItem containerItem : containerItems) {
      List<Item> itemList = packItemMap.get(containerItem.getItemNumber());
      if (CollectionUtils.isEmpty(itemList)) {
        LOGGER.info("No item info found for {}. Skipping item.", containerItem.getItemNumber());
      } else {
        LOGGER.info("Reconciling item {}.", containerItem.getItemNumber());
        Item item = itemList.get(0);
        containerItem.setPurchaseReferenceNumber(
            Objects.nonNull(item.getPurchaseOrder())
                ? item.getPurchaseOrder().getPoNumber()
                : null);
        containerItem.setPurchaseReferenceLineNumber(
            Objects.nonNull(item.getPurchaseOrder())
                    && Objects.nonNull(item.getPurchaseOrder().getPoLineNumber())
                ? Integer.parseInt(item.getPurchaseOrder().getPoLineNumber())
                : 0);
        containerItem.setOrderableQuantity(
            Objects.nonNull(item.getInventoryDetail())
                    && Objects.nonNull(item.getInventoryDetail().getReportedQuantity())
                ? item.getInventoryDetail().getReportedQuantity().intValue()
                : 0);
        LOGGER.info(
            "PO info after recon for tracking id {} and item {} - PO {} POL {} order able qty {}",
            containerItem.getTrackingId(),
            containerItem.getItemNumber(),
            containerItem.getPurchaseReferenceNumber(),
            containerItem.getPurchaseReferenceLineNumber(),
            containerItem.getOrderableQuantity());
        reconciledContainerItemCount++;
      }
    }
    return reconciledContainerItemCount;
  }

  private List<Container> reconciledContainerAndItems(
      int reconciledContainerItemCount,
      Container container,
      List<Pack> packList,
      List<Pack> matchedPackList) {
    List<Container> reconciledContainer = new ArrayList<>();
    int totalContainerItems = container.getContainerItems().size();
    if (reconciledContainerItemCount == 0) {
      LOGGER.warn("Non of the item matched for pallet {}", container.getParentTrackingId());
    } else {
      if (reconciledContainerItemCount == totalContainerItems) {
        LOGGER.warn(
            "{} no. of item matched out of {} for pallet {}",
            reconciledContainerItemCount,
            totalContainerItems,
            container.getParentTrackingId());
        container.setContainerStatus(
            getReplacedContainerStatusForWithoutASN(container.getContainerStatus()));
      }
      reconciledContainer.add(container);
      packList.removeAll(matchedPackList);
    }
    return reconciledContainer;
  }

  private void reconcileContainers(
      List<Container> containerReceivedWithoutASN, Shipment shipment, List<Pack> packList) {
    List<Container> reconciledContainerAndItems = new ArrayList<>();
    Map<String, List<Pack>> packListByPackNumber =
        packList
            .stream()
            .collect(
                groupingBy(
                    Pack::getPackNumber,
                    () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER),
                    Collectors.toList()));
    for (Container container : containerReceivedWithoutASN) {
      long shipmentHashCode = shipment.getShipmentNumber().hashCode();
      List<Pack> matchingPackList = packListByPackNumber.get(container.getParentTrackingId());
      if (CollectionUtils.isEmpty(matchingPackList)) {
        LOGGER.info(
            "Pack not found for pallet {} for shipment {} of hashcode {}. Continue...",
            container.getParentTrackingId(),
            shipment.getShipmentNumber(),
            shipmentHashCode);
        continue;
      }
      LOGGER.info(
          "Match found for pallet {} for shipment {} of hashcode {}. Reconciling...",
          container.getParentTrackingId(),
          shipment.getShipmentNumber(),
          shipmentHashCode);
      Pack pack = matchingPackList.get(0);
      Map<String, String> destination = new HashMap<>();
      destination.put(
          ReceivingConstants.COUNTRY_CODE, pack.getHandledOnBehalfOf().getCountryCode());
      destination.put(ReceivingConstants.BU_NUMBER, pack.getHandledOnBehalfOf().getNumber());
      container.setDestination(destination);
      container.setDeliveryNumber(shipmentHashCode);
      container.setLastChangedUser(ReceivingConstants.DEFAULT_USER);
      int reconciledContainerItemCount =
          reconcileContainerItems(container.getContainerItems(), pack.getItems());
      reconciledContainerAndItems.addAll(
          reconciledContainerAndItems(
              reconciledContainerItemCount, container, packList, matchingPackList));
    }
    if (CollectionUtils.isEmpty(reconciledContainerAndItems)) {
      LOGGER.error("No match for shipment {}", shipment.getShipmentNumber());
    } else {
      LOGGER.info(
          "{} no. of container match found out of {} and shipment size {} for shipment {}",
          reconciledContainerAndItems.size(),
          containerReceivedWithoutASN.size(),
          shipment.getTotalPacks(),
          shipment.getShipmentNumber());
      saveAndReconcileContainersToDownstream(reconciledContainerAndItems);
    }
  }

  private void saveAndReconcileContainersToDownstream(List<Container> reconciledContainerAndItems) {
    LOGGER.info(
        "Reconciling containers in DB {} ",
        reconciledContainerAndItems.stream().map(Container::getParentTrackingId));
    List<ContainerItem> containerItems = new ArrayList<>();
    reconciledContainerAndItems.forEach(c -> containerItems.addAll(c.getContainerItems()));
    containerPersisterService.saveContainerAndContainerItems(
        reconciledContainerAndItems, containerItems);

    // post to CT
    for (Container container : reconciledContainerAndItems) {
      LOGGER.info("Reconciling container {} with downstream.", container.getParentTrackingId());

      if (isCTPostingEnabled()) {
        PutAwayInventory putAwayInventory =
            PutAwayDTOMapper.preparePutAwayPayloadFromContainer(container);

        // update lpn in DB to track later
        ControlTowerTracker controlTowerTracker =
            controlTowerService.resetForTracking(container.getTrackingId());
        controlTowerService.putAwayInventory(
            Collections.singletonList(putAwayInventory), controlTowerTracker);
      } else {
        InventoryItemPODetailUpdateRequest inventoryItemPODetailUpdateRequest =
            InventoryItemPODetailDTOMapper.getInventoryItemPODetailUpdateRequest(container);
        inventoryService.updateInventoryPoDetails(inventoryItemPODetailUpdateRequest);
      }
      // post receipts
      containerService.publishMultipleContainersToInventory(
          transformer.transformList(Collections.singletonList(container)));
    }
  }
}
