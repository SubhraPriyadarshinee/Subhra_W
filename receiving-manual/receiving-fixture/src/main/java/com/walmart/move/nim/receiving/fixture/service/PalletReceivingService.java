package com.walmart.move.nim.receiving.fixture.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.message.common.DeliveryPayload;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DestinationLocation;
import com.walmart.move.nim.receiving.core.model.InventoryItemPODetailUpdateRequest;
import com.walmart.move.nim.receiving.core.model.InventoryLocationUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ItemPODetails;
import com.walmart.move.nim.receiving.core.model.SearchCriteria;
import com.walmart.move.nim.receiving.core.model.UpdateAttributes;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Destination;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.fixture.client.ItemMDMServiceClient;
import com.walmart.move.nim.receiving.fixture.client.ItemREPServiceClient;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.entity.ControlTowerTracker;
import com.walmart.move.nim.receiving.fixture.model.*;
import com.walmart.move.nim.receiving.fixture.utils.FixtureMMUtils;
import com.walmart.move.nim.receiving.fixture.utils.FixtureSlottingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class PalletReceivingService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PalletReceivingService.class);
  @Autowired private ContainerPersisterService containerPersisterService;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @ManagedConfiguration FixtureManagedConfig fixtureManagedConfig;

  @Autowired ControlTowerService controlTowerService;

  @Autowired ContainerService containerService;

  @Resource(name = ReceivingConstants.FIXTURE_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService deliveryMetaDataService;

  @Autowired private TenantSpecificConfigReader configUtils;

  @Autowired InventoryService inventoryService;

  @Resource(name = "containerTransformer")
  private Transformer<Container, ContainerDTO> transformer;

  @Autowired private ItemREPServiceClient itemREPServiceClient;

  @Autowired ItemMDMServiceClient itemMDMServiceClient;

  @Autowired private SlottingRestApiClient slottingRestApiClient;

  @Autowired private ContainerItemService containerItemService;

  @Autowired private MovePublisher movePublisher;

  @Resource(name = FixtureConstants.RFC_LPN_CACHE_SERVICE)
  private LPNCacheService lpnCacheService;

  private boolean isCTPostingEnabled() {
    return configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), FixtureConstants.IS_CT_ENABLED, false);
  }

  @Timed(
      name = "processShipmentTimed",
      level1 = "uwms-receiving",
      level2 = "palletReceivingService",
      level3 = "processShipmentEvent")
  @ExceptionCounted(
      name = "processShipmentExceptionCount",
      level1 = "uwms-receiving",
      level2 = "palletReceivingService",
      level3 = "processShipmentEvent")
  public void processShipmentEvent(DeliveryUpdateMessage deliveryUpdateMessage) {
    String eventType = deliveryUpdateMessage.getEvent().getType();
    DeliveryPayload payload = deliveryUpdateMessage.getPayload();
    List<Pack> packList = payload.getPacks();
    Shipment shipment = payload.getShipment();
    long shipmentHashcode = shipment.getShipmentNumber().hashCode();

    List<Container> containerByDeliveryNumber =
        containerPersisterService.getContainerByDeliveryNumber(shipmentHashcode);

    // check if pallets of this shipment were received without ASN
    List<Container> containerReceivedWithoutASN =
        containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            packList.stream().map(Pack::getPackNumber).collect(Collectors.toList()),
            FixtureConstants.CONTAINER_STATUS_WO_ASN);

    if (!CollectionUtils.isEmpty(containerReceivedWithoutASN)) {
      LOGGER.info(
          "Reconciling containers received without ASN {} for shipment {} of hashcode {}",
          containerReceivedWithoutASN.stream().map(Container::getParentTrackingId),
          shipment.getShipmentNumber(),
          shipmentHashcode);
      persistsShipmentMetadata(shipment);
      reconcileContainers(containerReceivedWithoutASN, shipment, packList);
      return;
    }

    if (!CollectionUtils.isEmpty(containerByDeliveryNumber)) {
      LOGGER.info(
          "Container already exists for shipment {} of hashcode {}",
          shipment.getShipmentNumber(),
          shipmentHashcode);
      if (eventType.equalsIgnoreCase(ReceivingConstants.SHIPMENT_UPDATED)) {
        LOGGER.info(
            "Updating containers for shipment {} of hashcode {}",
            shipment.getShipmentNumber(),
            shipmentHashcode);
        updateContainers(containerByDeliveryNumber, shipment, packList);
      }
      return;
    }

    List<Container> containerList = new ArrayList<>();
    packList.forEach(
        pack -> containerList.add(createContainerFromPacks(payload.getShipment(), pack)));

    List<ContainerItem> containerItems = new ArrayList<>();
    containerList.forEach(container -> containerItems.addAll(container.getContainerItems()));
    LOGGER.info("Saving container in DB {}", containerList);
    containerPersisterService.saveContainerAndContainerItems(containerList, containerItems);
    persistsShipmentMetadata(shipment);
  }

  private void persistsShipmentMetadata(Shipment shipment) {
    long shipmentHashcode = shipment.getShipmentNumber().hashCode();
    Optional<DeliveryMetaData> byShipment =
        deliveryMetaDataService.findByDeliveryNumber(String.valueOf(shipmentHashcode));
    DeliveryMetaData deliveryMetaData = null;
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
    deliveryMetaDataService.save(deliveryMetaData);
  }

  private void reconcileContainers(
      List<Container> containerReceivedWithoutASN, Shipment shipment, List<Pack> packList) {
    for (Container container : containerReceivedWithoutASN) {
      long shipmentHashCode = shipment.getShipmentNumber().hashCode();
      LOGGER.info(
          "Reconciling pallet {} for shipment {} of hashcode {}",
          container.getParentTrackingId(),
          shipment.getShipmentNumber(),
          shipmentHashCode);
      container.setDeliveryNumber(shipmentHashCode);
      container.setLastChangedUser(ReceivingConstants.DEFAULT_USER);
      Map<String, String> destination = new HashMap<>();
      Destination dest =
          packList
              .stream()
              .filter(p -> container.getParentTrackingId().equalsIgnoreCase(p.getPackNumber()))
              .collect(Collectors.toList())
              .get(0)
              .getHandledOnBehalfOf();
      destination.put(ReceivingConstants.COUNTRY_CODE, dest.getCountryCode());
      destination.put(ReceivingConstants.BU_NUMBER, dest.getNumber());
      container.setDestination(destination);
      boolean areAllItemsReconciled =
          checkAndReconcileContainerItems(
              container.getContainerItems(),
              packList
                  .stream()
                  .filter(p -> container.getParentTrackingId().equalsIgnoreCase(p.getPackNumber()))
                  .collect(Collectors.toList())
                  .get(0)
                  .getItems());
      if (areAllItemsReconciled) {
        container.setContainerStatus(
            getReplacedContainerStatusForWithoutASN(container.getContainerStatus()));
      }
    }
    saveAndReconcileContainersToDownstream(containerReceivedWithoutASN);
  }

  private void saveAndReconcileContainersToDownstream(List<Container> containerReceivedWithoutASN) {
    // save container
    LOGGER.info(
        "Reconciling containers in DB {} ",
        containerReceivedWithoutASN.stream().map(Container::getParentTrackingId));
    List<ContainerItem> containerItems = new ArrayList<>();
    containerReceivedWithoutASN.forEach(c -> containerItems.addAll(c.getContainerItems()));
    containerPersisterService.saveContainerAndContainerItems(
        containerReceivedWithoutASN, containerItems);

    // post to CT
    for (Container container : containerReceivedWithoutASN) {
      if (container.getContainerStatus().equals(ReceivingConstants.STATUS_COMPLETE)) {
        LOGGER.info("Reconciling container {} with downstream.", container.getParentTrackingId());

        if (isCTPostingEnabled()) {
          PutAwayInventory putAwayInventory = preparePutAwayPayloadFromContainer(container);

          // update lpn in DB to track later
          ControlTowerTracker controlTowerTracker =
              controlTowerService.resetForTracking(container.getTrackingId());
          controlTowerService.putAwayInventory(
              Collections.singletonList(putAwayInventory), controlTowerTracker);
        } else {
          InventoryItemPODetailUpdateRequest inventoryItemPODetailUpdateRequest =
              prepareCriteriaPayloadForUpdatePODetails(container);
          inventoryService.updateInventoryPoDetails(inventoryItemPODetailUpdateRequest);
        }
        // post receipts
        containerService.publishMultipleContainersToInventory(
            transformer.transformList(Collections.singletonList(container)));
      }
    }
  }

  /** Set the itemPODetails Map for inventoryPurchaseDetails Updates. */
  private Map<String, ItemPODetails> prepareItemPODetails(Container container) {
    Map<String, ItemPODetails> itemPODetails = new HashMap<>();
    String destNbr = container.getDestination().get(ReceivingConstants.BU_NUMBER);
    String destCC = container.getDestination().get(ReceivingConstants.COUNTRY_CODE);
    container
        .getContainerItems()
        .forEach(
            item ->
                itemPODetails.put(
                    item.getItemNumber().toString(),
                    ItemPODetails.builder()
                        .poNum(item.getPurchaseReferenceNumber())
                        .purchaseReferenceLineNumber(
                            Objects.nonNull(item.getPurchaseReferenceLineNumber())
                                ? item.getPurchaseReferenceLineNumber()
                                : null)
                        .poQty(item.getOrderableQuantity())
                        .destNbr(destNbr)
                        .destCC(destCC)
                        .build()));
    return itemPODetails;
  }

  /** Set the Serach Criateria for inventoryPurchaseDetails Updates. */
  private SearchCriteria prepareSearchCriteria(
      String trackingId, String baseDivisionCode, String financialReportingGroup) {
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add(trackingId);
    return SearchCriteria.builder()
        .trackingIds(trackingIds)
        .baseDivisionCode(baseDivisionCode)
        .financialReportingGroup(financialReportingGroup)
        .build();
  }

  /** Set the request Payload for inventoryPurchaseDetails Updates. */
  private InventoryItemPODetailUpdateRequest prepareCriteriaPayloadForUpdatePODetails(
      Container container) {

    return InventoryItemPODetailUpdateRequest.builder()
        .updateAttributes(
            UpdateAttributes.builder().updatePODetails(prepareItemPODetails(container)).build())
        .searchCriteria(
            prepareSearchCriteria(
                container.getTrackingId(),
                ReceivingConstants.BASE_DIVISION_CODE,
                TenantContext.getFacilityCountryCode()))
        .build();
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

  private boolean checkAndReconcileContainerItems(
      List<ContainerItem> containerItems, List<Item> items) {
    boolean areAllItemsReconciled = true;
    for (ContainerItem containerItem : containerItems) {
      List<Item> itemList =
          items
              .stream()
              .filter(item -> containerItem.getItemNumber().equals(item.getItemNumber()))
              .collect(Collectors.toList());
      if (CollectionUtils.isEmpty(itemList)) {
        areAllItemsReconciled = false;
        LOGGER.info(
            "No item info found for {}. Skipping reconciliation.", containerItem.getItemNumber());
      }
      LOGGER.info("Reconciling item {}.", containerItem.getItemNumber());
      containerItem.setPurchaseReferenceNumber(itemList.get(0).getPurchaseOrder().getPoNumber());
      containerItem.setPurchaseReferenceLineNumber(
          Integer.valueOf(itemList.get(0).getPurchaseOrder().getPoLineNumber()));
      containerItem.setOrderableQuantity(
          itemList.get(0).getInventoryDetail().getReportedQuantity().intValue());
    }
    return areAllItemsReconciled;
  }

  private Container createContainerFromPacks(Shipment shipment, Pack pack) {

    Container container = new Container();
    setContainerAttributes(shipment, pack, container);
    List<ContainerItem> containerItemList = new ArrayList<>();
    pack.getItems().forEach(item -> containerItemList.add(mapItemInfo(item, pack.getPackNumber())));
    container.setContainerItems(containerItemList);
    setFixturesItemAttribute(containerItemList);
    return container;
  }

  private void setContainerAttributes(Shipment shipment, Pack pack, Container container) {
    String defaultUser = ReceivingConstants.DEFAULT_USER;
    String packNumber = pack.getPackNumber();
    long deliveryNumber = shipment.getShipmentNumber().hashCode();
    Destination destination = pack.getHandledOnBehalfOf();

    setContainerAttributes(container, defaultUser, deliveryNumber, packNumber, destination);
  }

  private void setContainerAttributes(
      Container container,
      String defaultUser,
      long deliveryNumber,
      String packNumber,
      Destination destination) {
    container.setTrackingId(packNumber);
    container.setMessageId(packNumber);
    container.setParentTrackingId(packNumber);
    container.setCreateUser(defaultUser);
    container.setContainerStatus(ReceivingConstants.STATUS_PENDING_COMPLETE);
    container.setDeliveryNumber(deliveryNumber);

    Map<String, String> lpnMap = new HashMap<>();
    lpnMap.put(ReceivingConstants.COUNTRY_CODE, destination.getCountryCode());
    lpnMap.put(ReceivingConstants.BU_NUMBER, destination.getNumber());
    container.setDestination(lpnMap);

    Map<String, String> facility = new HashMap<>();
    facility.put(ReceivingConstants.BU_NUMBER, TenantContext.getFacilityNum().toString());
    facility.put(ReceivingConstants.COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    container.setFacility(facility);

    container.setCtrReusable(false);
    container.setCtrShippable(true);
    container.setOnConveyor(false);
    container.setIsConveyable(false);
    container.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    container.setContainerType(ContainerType.PALLET.getText());
  }

  private ContainerItem mapItemInfo(Item item, String palletId) {
    ContainerItem containerItem = new ContainerItem();
    setContainerItemAttributes(item, palletId, containerItem);

    return containerItem;
  }

  private void setContainerItemAttributes(Item item, String palletId, ContainerItem containerItem) {
    containerItem.setTrackingId(palletId);
    containerItem.setItemNumber(item.getItemNumber());
    containerItem.setDescription(item.getItemDescription());
    containerItem.setPurchaseReferenceNumber(item.getPurchaseOrder().getPoNumber());
    containerItem.setPurchaseReferenceLineNumber(
        Integer.valueOf(item.getPurchaseOrder().getPoLineNumber()));
    containerItem.setQuantityUOM(item.getInventoryDetail().getReportedUom());
    containerItem.setQuantity(item.getInventoryDetail().getReportedQuantity().intValue());
    containerItem.setOrderableQuantity(item.getInventoryDetail().getReportedQuantity().intValue());
    setMandatoryFieldForCreationOfInvetoryContainer(containerItem);
  }

  private void updateContainers(
      List<Container> containerByDeliveryNumber, Shipment shipment, List<Pack> packList) {
    Map<String, Container> containerMap = new HashMap<>();
    containerByDeliveryNumber.forEach(
        container -> containerMap.put(container.getTrackingId(), container));

    Map<String, Container> containerMapByParentTrackingID = new HashMap<>();
    containerByDeliveryNumber.forEach(
        container ->
            containerMapByParentTrackingID.put(container.getParentTrackingId(), container));
    List<Container> newPalletList = new ArrayList<>();

    packList.forEach(
        pack -> {
          Container container = containerMap.get(pack.getPackNumber());
          if (Objects.nonNull(container)) {
            LOGGER.info(
                "Updating container {} for shipment {} of hashcode {}.",
                pack.getPackNumber(),
                shipment.getShipmentNumber(),
                (long) shipment.getShipmentNumber().hashCode());
            updateContainerFromPacks(container, shipment, pack);

          } else if (Objects.isNull(containerMapByParentTrackingID.get(pack.getPackNumber()))) {

            // create a new container
            LOGGER.info(
                "Container {} doesn't exists for shipment {} of hashcode {}. Creating a new container.",
                pack.getPackNumber(),
                shipment.getShipmentNumber(),
                (long) shipment.getShipmentNumber().hashCode());
            newPalletList.add(createContainerFromPacks(shipment, pack));
          } else {
            LOGGER.info(
                "Container {} is already received for shipment {} of hashcode {}. Skipping update.",
                pack.getPackNumber(),
                shipment.getShipmentNumber(),
                (long) shipment.getShipmentNumber().hashCode());
          }
        });

    containerByDeliveryNumber.addAll(newPalletList);

    List<ContainerItem> containerItems = new ArrayList<>();
    containerByDeliveryNumber.forEach(
        container -> containerItems.addAll(container.getContainerItems()));
    LOGGER.info(
        "Updating containers in DB {} with {} new containers",
        containerByDeliveryNumber,
        newPalletList.size());
    containerPersisterService.saveContainerAndContainerItems(
        containerByDeliveryNumber, containerItems);
  }

  private void updateContainerFromPacks(Container container, Shipment shipment, Pack pack) {
    setContainerAttributes(shipment, pack, container);
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
                setContainerItemAttributes(item, pack.getPackNumber(), containerItem);
              } else {
                LOGGER.info(
                    "ContainerItem {} doesn't exists for pallet {} and shipment {} of hashcode {}. Creating a new container.",
                    item.getItemNumber(),
                    pack.getPackNumber(),
                    shipment.getShipmentNumber(),
                    (long) shipment.getShipmentNumber().hashCode());

                newItemList.add(mapItemInfo(item, pack.getPackNumber()));
              }
            });
    container.getContainerItems().addAll(newItemList);
  }

  public PalletReceiveResponse receive(
      PalletReceiveRequest palletReceiveRequest, HttpHeaders headers) {
    String packNumber = palletReceiveRequest.getPackNumber();
    String userId = headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Container containerDetails = null;
    if (!palletReceiveRequest.isReceiveWithoutASN()) {
      boolean isAuditRequest = isAuditRequest(palletReceiveRequest);
      LOGGER.info("Receive pallet {}. Is auditRequest {}.", packNumber, isAuditRequest);
      containerDetails = containerPersisterService.getContainerDetails(packNumber);

      if (!Objects.isNull(containerDetails)) {
        validateContainerNotReceived(containerDetails);
        /**
         * send status as LPN_MAPPPED if LPN is already mapped for this container. This means pallet
         * is received and putaway is pending.
         */
        if (!containerDetails
            .getTrackingId()
            .equalsIgnoreCase(containerDetails.getParentTrackingId())) {
          return mapResponseForPalletAlreadyMapped(containerDetails, headers);
        }
      }

      boolean saveContainer = false;
      if (Objects.isNull(containerDetails)) {
        /**
         * check if there is a pending pallet by parentTrackingId, this means palletId is being
         * scanned again after lpn is mapped and putaway is not done.
         */
        Set<Container> containerDetailsByParentTrackingId =
            containerPersisterService.getContainerDetailsByParentTrackingId(packNumber);
        List<Container> activeContainersByParentTrackingId =
            containerDetailsByParentTrackingId
                .stream()
                .filter(
                    container ->
                        FixtureConstants.CONTAINER_STATUS_WO_ACTIVE_LIST.contains(
                            container.getContainerStatus()))
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(activeContainersByParentTrackingId)) {
          containerDetails = activeContainersByParentTrackingId.get(0);
          return mapResponseForPalletAlreadyMapped(containerDetails, headers);
        }

        // check if it is present in GDM
        LOGGER.info("Pallet {} not fund in Receiving, checking in GDM.", packNumber);
        SsccScanResponse response = searchPackInGDM(headers, packNumber);
        saveContainer = true;
        containerDetails =
            createContainerFromPacks(response.getShipments().get(0), response.getPacks().get(0));
        persistsShipmentMetadata(response.getShipments().get(0));
      }

      boolean isAuditRequired = false;
      if (!isAuditRequest) {
        isAuditRequired = isAuditRequired(containerDetails);
      } else {
        // audit pallet
        auditContainerAndContainerItems(containerDetails, palletReceiveRequest);
      }
      if (isAuditRequired) {
        if (saveContainer) {
          containerDetails.setCreateUser(userId);
          containerPersisterService.saveContainer(containerDetails);
        }
        PalletReceiveResponse palletReceiveResponse = mapResponseFromContainer(containerDetails);
        palletReceiveResponse.setAuditRequired(true);
        return palletReceiveResponse;
      }
    } else {
      LOGGER.info("Receive Pallet {} without ASN", packNumber);
      validateRequestForReceiveWithoutASN(palletReceiveRequest);
      containerDetails = createContainerForNonASNReceiving(palletReceiveRequest, userId);
    }

    containerDetails.setContainerStatus(
        palletReceiveRequest.isReceiveWithoutASN()
            ? ReceivingConstants.STATUS_ACTIVE_NO_ASN
            : ReceivingConstants.STATUS_ACTIVE);
    containerDetails.setCreateUser(userId);

    containerPersisterService.saveContainer(containerDetails);
    LOGGER.info("Received Pallet {} : {}", packNumber, containerDetails);
    return mapResponseFromContainer(containerDetails);
  }

  private PalletReceiveResponse mapResponseForPalletAlreadyMapped(
      Container containerDetails, HttpHeaders headers) {
    PalletReceiveResponse palletReceiveResponse = mapResponseFromContainer(containerDetails);
    palletReceiveResponse.setStatus(FixtureConstants.CONTAINER_STATUS_MAPPED_LPN);
    palletReceiveResponse.setLpn(containerDetails.getTrackingId());
    palletReceiveResponse.setPrintRequests(
        Collections.singletonList(buildPrintRequest(containerDetails)));
    return palletReceiveResponse;
  }

  private void validateContainerNotReceived(Container containerDetails) {
    if (Objects.nonNull(containerDetails.getCompleteTs())) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_RECEIVE, ReceivingException.ERROR_HEADER_PALLET_COMPLETED);
    }
  }

  private void auditContainerAndContainerItems(
      Container containerDetails, PalletReceiveRequest palletReceiveRequest) {
    LOGGER.info(
        "Pallet {}, audited : {}",
        palletReceiveRequest.getPackNumber(),
        palletReceiveRequest.isAudited());
    if (!StringUtils.isEmpty(palletReceiveRequest.getStoreNumber())) {
      containerDetails
          .getDestination()
          .put(ReceivingConstants.BU_NUMBER, palletReceiveRequest.getStoreNumber());
    }
    Map<Long, ContainerItem> containerItemMap = new HashMap<>();
    containerDetails
        .getContainerItems()
        .forEach(
            containerItem -> containerItemMap.put(containerItem.getItemNumber(), containerItem));

    List<ContainerItem> newItemList = new ArrayList<>();
    palletReceiveRequest
        .getItems()
        .forEach(
            item -> {
              ContainerItem containerItem = containerItemMap.get(item.getItemNumber());
              if (Objects.nonNull(containerItem)) {
                LOGGER.info(
                    "Audit : Setting audit qty for- Pallet:{},item:{}, receivedQty:{}, orderedQty:{}",
                    containerDetails.getTrackingId(),
                    containerItem.getItemNumber(),
                    item.getReceivedQty(),
                    containerItem.getOrderableQuantity());
                containerItem.setQuantity(item.getReceivedQty());
              } else {
                LOGGER.info(
                    "Audit: ContainerItem {} doesn't exists for pallet {}. Creating a new containerItem.",
                    item.getItemNumber(),
                    containerDetails.getTrackingId());
                newItemList.add(
                    mapItemInfoForNonASNReceiving(item, palletReceiveRequest.getPackNumber()));
              }
            });
    containerDetails.getContainerItems().addAll(newItemList);

    // check for items to be deleted
    List<ContainerItem> removeItemList = new ArrayList<>();
    HashMap<Long, PalletItem> auditItemMap = new HashMap<>();
    palletReceiveRequest.getItems().forEach(item -> auditItemMap.put(item.getItemNumber(), item));
    containerDetails
        .getContainerItems()
        .forEach(
            containerItem -> {
              if (Objects.isNull(auditItemMap.get(containerItem.getItemNumber()))) {
                LOGGER.info(
                    "Audit: Removing ContainerItem {} for pallet {}.",
                    containerItem.getItemNumber(),
                    containerDetails.getTrackingId());
                removeItemList.add(containerItem);
              }
            });

    if (!CollectionUtils.isEmpty(removeItemList)) {
      containerDetails.getContainerItems().removeAll(removeItemList);
      containerPersisterService.deleteContainerItems(removeItemList);
    }
  }

  private void validateRequestForReceiveWithoutASN(PalletReceiveRequest palletReceiveRequest) {
    if (StringUtils.isEmpty(palletReceiveRequest.getStoreNumber())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_REQUEST, FixtureConstants.INVALID_DESTINATION);
    }
    if (CollectionUtils.isEmpty(palletReceiveRequest.getItems())
        || palletReceiveRequest
            .getItems()
            .stream()
            .anyMatch(palletItem -> Objects.isNull(palletItem.getReceivedQty()))
        || palletReceiveRequest
            .getItems()
            .stream()
            .anyMatch(palletItem -> Objects.isNull(palletItem.getItemDescription()))) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_REQUEST, FixtureConstants.INVALID_ITEM_DETAILS);
    }
  }

  private Container createContainerForNonASNReceiving(
      PalletReceiveRequest palletReceiveRequest, String userId) {
    Container container = new Container();
    Destination destination = new Destination();
    destination.setCountryCode("US");
    destination.setNumber(palletReceiveRequest.getStoreNumber());

    // since we don't have shipment number at this stage, populate it with currentTimestamp
    long deliveryNumber = System.currentTimeMillis();
    setContainerAttributes(
        container, userId, deliveryNumber, palletReceiveRequest.getPackNumber(), destination);
    List<ContainerItem> containerItemList = new ArrayList<>();
    palletReceiveRequest
        .getItems()
        .forEach(
            item ->
                containerItemList.add(
                    mapItemInfoForNonASNReceiving(item, palletReceiveRequest.getPackNumber())));
    container.setContainerItems(containerItemList);
    return container;
  }

  private ContainerItem mapItemInfoForNonASNReceiving(PalletItem item, String packNumber) {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(packNumber);
    containerItem.setItemNumber(item.getItemNumber());
    containerItem.setDescription(item.getItemDescription());
    containerItem.setQuantityUOM(
        StringUtils.isEmpty(item.getQuantityUOM()) ? "EA" : item.getQuantityUOM());
    containerItem.setQuantity(item.getReceivedQty());
    containerItem.setOrderableQuantity(0);
    if (fixtureManagedConfig.isItemMdmEnabled()) {
      containerItem.setItemUPC(
          itemMDMServiceClient.getItemUpc(Collections.singleton(containerItem.getItemNumber())));
    }
    setMandatoryFieldForCreationOfInvetoryContainer(containerItem);
    return containerItem;
  }

  private boolean isAuditRequest(PalletReceiveRequest palletReceiveRequest) {
    return !(CollectionUtils.isEmpty(palletReceiveRequest.getItems())
        || palletReceiveRequest
            .getItems()
            .stream()
            .allMatch(palletItem -> Objects.isNull(palletItem.getReceivedQty())));
  }

  private PalletReceiveResponse mapResponseFromContainer(Container containerDetails) {
    List<PalletItem> palletItemList = new ArrayList<>();
    containerDetails
        .getContainerItems()
        .forEach(
            containerItem ->
                palletItemList.add(
                    PalletItem.builder()
                        .itemNumber(containerItem.getItemNumber())
                        .itemDescription(containerItem.getDescription())
                        .receivedQty(containerItem.getQuantity())
                        .orderedQty(containerItem.getOrderableQuantity())
                        .purchaseReferenceNumber(containerItem.getPurchaseReferenceNumber())
                        .purchaseReferenceLineNumber(containerItem.getPurchaseReferenceLineNumber())
                        .quantityUOM(containerItem.getQuantityUOM())
                        .build()));

    String loadNumber = null;
    if (!containerDetails
        .getContainerStatus()
        .equalsIgnoreCase(ReceivingConstants.STATUS_ACTIVE_NO_ASN)) {
      Optional<DeliveryMetaData> byShipmentNumber =
          deliveryMetaDataService.findByDeliveryNumber(
              String.valueOf(containerDetails.getDeliveryNumber()));
      if (byShipmentNumber.isPresent()) loadNumber = byShipmentNumber.get().getTrailerNumber();
    }

    return PalletReceiveResponse.builder()
        .packNumber(containerDetails.getMessageId())
        .storeNumber(containerDetails.getDestination().get(ReceivingConstants.BU_NUMBER))
        .items(palletItemList)
        .loadNumber(loadNumber)
        .build();
  }

  private SsccScanResponse searchPackInGDM(HttpHeaders headers, @NotEmpty String packNumber) {
    SsccScanResponse response = deliveryService.globalPackSearch(packNumber, headers);
    if (Objects.isNull(response.getShipments())
        || Objects.isNull(response.getShipments().get(0))
        || Objects.isNull(response.getPacks())
        || Objects.isNull(response.getPacks().get(0))) {
      LOGGER.error("Pack {} not found in GDM", packNumber);

      throw new ReceivingDataNotFoundException(
          ExceptionCodes.GDM_PACK_NOT_FOUND,
          String.format(ReceivingConstants.GDM_SEARCH_PACK_NOT_FOUND, packNumber),
          packNumber);
    }
    // filter out the received pallets
    List<Pack> filteredPacks =
        response
            .getPacks()
            .stream()
            .filter(pack -> !ReceivingConstants.PACK_STATUS_RECEIVED.equals(pack.getStatus()))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(filteredPacks)) {
      // pallet is already received
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_RECEIVE, ReceivingException.ERROR_HEADER_PALLET_COMPLETED);
    }

    // filter out the shipment for received pallets
    List<Shipment> filteredShipments =
        response
            .getShipments()
            .stream()
            .filter(
                shipment ->
                    filteredPacks
                        .stream()
                        .anyMatch(
                            pack -> shipment.getShipmentNumber().equals(pack.getShipmentNumber())))
            .collect(Collectors.toList());
    response.setShipments(filteredShipments);
    response.setPacks(filteredPacks);
    if (response.getShipments().size() > 1 || response.getPacks().size() > 1) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_RECEIVE,
          String.format(FixtureConstants.MULTIPLE_PALLETS_FOUND, packNumber));
    }
    return response;
  }

  private boolean isAuditRequired(Container containerDetails) {

    int receivableItemCountThreshold = fixtureManagedConfig.getReceivableItemCountThreshold();
    boolean isAuditRequired =
        containerDetails
            .getContainerItems()
            .stream()
            .anyMatch(
                containerItem ->
                    containerItem.getOrderableQuantity() > receivableItemCountThreshold);
    LOGGER.info(
        "Is audit required for pallet {} :{}", containerDetails.getTrackingId(), isAuditRequired);
    return isAuditRequired;
  }

  public PalletPutAwayResponse putAway(
      PalletPutAwayRequest palletPutAwayRequest, HttpHeaders headers) {

    String lpn = palletPutAwayRequest.getLpn();
    Container containerDetails = containerPersisterService.getContainerDetails(lpn);
    if (Objects.isNull(containerDetails)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
              lpn);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription);
    }
    validateContainerNotReceived(containerDetails);
    LOGGER.info("PutAway Pallet {} ", containerDetails);
    validateContainerNotReceived(containerDetails);
    containerDetails.setContainerStatus(
        containerDetails.getContainerStatus().equals(ReceivingConstants.STATUS_ACTIVE_NO_ASN)
            ? ReceivingConstants.STATUS_COMPLETE_NO_ASN
            : ReceivingConstants.STATUS_COMPLETE);
    containerDetails.setLocation(palletPutAwayRequest.getLocation());
    containerDetails.setPublishTs(new Date());
    containerDetails.setCompleteTs(new Date());
    containerDetails.setLastChangedUser(headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    // save container in DB
    containerPersisterService.saveContainer(containerDetails);
    if (isCTPostingEnabled()) {
      PutAwayInventory putAwayInventory = preparePutAwayPayloadFromContainer(containerDetails);
      // save lpn in DB to track later
      ControlTowerTracker controlTowerTracker =
          controlTowerService.putForTracking(containerDetails.getTrackingId());
      controlTowerService.putAwayInventory(
          Collections.singletonList(putAwayInventory), controlTowerTracker);
    }

    InventoryLocationUpdateRequest inventoryLocationUpdateRequest =
        InventoryLocationUpdateRequest.builder()
            .trackingIds(Collections.singletonList(containerDetails.getTrackingId()))
            .destinationLocation(
                DestinationLocation.builder()
                    .locationName(palletPutAwayRequest.getLocation())
                    .orgUnitId(0)
                    .build())
            .build();

    inventoryService.updateLocation(inventoryLocationUpdateRequest, headers);
    LOGGER.info("End: PutAway Pallet {} ", containerDetails);
    return PalletPutAwayResponse.builder()
        .packNumber(containerDetails.getParentTrackingId())
        .storeNumber(containerDetails.getDestination().get(ReceivingConstants.BU_NUMBER))
        .lpn(containerDetails.getTrackingId())
        .location(containerDetails.getLocation())
        .build();
  }

  private PutAwayInventory preparePutAwayPayloadFromContainer(Container container) {
    return PutAwayInventory.builder()
        .palletId(container.getParentTrackingId())
        .lpn(container.getTrackingId())
        .destination(container.getDestination().get(ReceivingConstants.BU_NUMBER))
        .putAwayLocation(container.getLocation())
        .weight(0)
        .items(prepareItemDetails(container))
        .build();
  }

  private List<ItemDetails> prepareItemDetails(Container container) {
    String destination = container.getDestination().get(ReceivingConstants.BU_NUMBER);
    List<ItemDetails> itemDetails = new ArrayList<>();
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    container
        .getContainerItems()
        .forEach(
            item ->
                itemDetails.add(
                    ItemDetails.builder()
                        .id(item.getItemNumber().toString())
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .destination(destination)
                        .purchaseOrder(item.getPurchaseReferenceNumber())
                        .poLineNumber(
                            Objects.nonNull(item.getPurchaseReferenceLineNumber())
                                ? item.getPurchaseReferenceLineNumber().toString()
                                : null)
                        .promiseDate(dateFormat.format(container.getCompleteTs()))
                        .build()));
    return itemDetails;
  }

  public void checkAndRetryCTInventory() {
    List<ControlTowerTracker> cTentitiesToValidate = controlTowerService.getCTEntitiesToValidate();
    if (CollectionUtils.isEmpty(cTentitiesToValidate)) {
      LOGGER.info("Nothing to check. Returning.");
      return;
    }

    Iterator<ControlTowerTracker> controlTowerTrackerIterator = cTentitiesToValidate.iterator();
    while (controlTowerTrackerIterator.hasNext()) {
      ControlTowerTracker ctTracker = controlTowerTrackerIterator.next();
      TenantContext.setFacilityNum(ctTracker.getFacilityNum());
      TenantContext.setFacilityCountryCode(ctTracker.getFacilityCountryCode());
      LOGGER.info("Checking status in CT for LPN {}", ctTracker.getLpn());
      if (!StringUtils.isEmpty(ctTracker.getAckKey())
          && EventTargetStatus.PENDING.equals(ctTracker.getSubmissionStatus())) {
        // get status from CT
        LOGGER.info(
            "Getting status in CT for LPN {} with ack key {}",
            ctTracker.getLpn(),
            ctTracker.getAckKey());
        CTWarehouseResponse warehouseResponse =
            controlTowerService.getInventoryStatus(ctTracker.getAckKey());
        if (!Objects.isNull(warehouseResponse)) {
          if (!StringUtils.isEmpty(warehouseResponse.getStatus())
              && warehouseResponse.getStatus().equals(ReceivingConstants.SUCCESS)) {
            LOGGER.info("Marking status in CT for LPN {} as SUCCESS", ctTracker.getLpn());
            ctTracker.setSubmissionStatus(EventTargetStatus.DELETE);
          } else {

            ctTracker.setSubmissionStatus(EventTargetStatus.FAILED);
            if (!CollectionUtils.isEmpty(warehouseResponse.getErrors())) {
              StringBuilder warehouseResponseErrors = new StringBuilder();
              for (CTErrorsReceivingInventoryErrors errors : warehouseResponse.getErrors()) {
                List<String> errorList =
                    errors
                        .getErrors()
                        .stream()
                        .map(CTInventoryError::getError)
                        .collect(Collectors.toList());
                warehouseResponseErrors.append(errors.getLpn() + ":" + errorList.toString());
              }
              LOGGER.error(
                  "Marking status in CT for LPN {} as FAILED. Reason {}",
                  ctTracker.getLpn(),
                  warehouseResponseErrors);
            }
          }
        }
      } else {
        // Either Ack key is not present or submission status is failed, try posting again
        LOGGER.info(
            "Either Ack Key is empty or posting to CT failed, Retrying again for LPN {}",
            ctTracker.getLpn());
        Container containerDetails =
            containerPersisterService.getContainerDetails(ctTracker.getLpn());
        PutAwayInventory putAwayInventory = preparePutAwayPayloadFromContainer(containerDetails);
        controlTowerService.putAwayInventory(
            Collections.singletonList(putAwayInventory), ctTracker);
        // no need to update entities which are being posted again
        controlTowerTrackerIterator.remove();
      }
    }
    if (!CollectionUtils.isEmpty(cTentitiesToValidate))
      controlTowerService.saveManagedObjectsOnly(cTentitiesToValidate);
  }

  public PalletPutAwayResponse mapLpn(
      PalletMapLPNRequest palletMapLPNRequest, HttpHeaders headers) {
    String packNumber = palletMapLPNRequest.getPackNumber();
    String lpn = palletMapLPNRequest.getLpn();
    Container containerDetails = containerPersisterService.getContainerDetails(packNumber);

    if (Objects.isNull(containerDetails)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
              packNumber);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription);
    }
    validateContainerNotReceived(containerDetails);

    if (!containerDetails.getTrackingId().equals(containerDetails.getParentTrackingId())) {
      String errorDescription =
          String.format(
              FixtureConstants.LPN_ALREADY_MAPPED,
              containerDetails.getTrackingId(),
              containerDetails.getMessageId());
      throw new ReceivingBadDataException(ExceptionCodes.INVALID_REQUEST, errorDescription);
    }

    if (containerPersisterService.checkIfContainerExist(lpn)) {
      String errorDescription =
          String.format(FixtureConstants.LPN_ALREADY_MAPPED_TO_DIFF_PALLET, lpn);
      throw new ReceivingBadDataException(ExceptionCodes.INVALID_REQUEST, errorDescription);
    }

    LOGGER.info("Mapping LPN {} to pallet {}.", lpn, packNumber);
    // Replace trackingID by LPN
    containerDetails.setTrackingId(lpn);
    containerDetails.getContainerItems().forEach(containerItem -> containerItem.setTrackingId(lpn));
    containerDetails.setLastChangedUser(headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    // save container in DB
    containerPersisterService.saveContainer(containerDetails);

    // publish container creation
    containerService.publishMultipleContainersToInventory(
        transformer.transformList(Collections.singletonList(containerDetails)));

    PalletReceiveRequest palletReceiveRequest = new PalletReceiveRequest();
    palletReceiveRequest.setContainerName("");
    // call slotting for slot suggestion
    processSlotSuggestion(containerDetails, headers, palletReceiveRequest);

    return PalletPutAwayResponse.builder()
        .packNumber(containerDetails.getParentTrackingId())
        .storeNumber(containerDetails.getDestination().get(ReceivingConstants.BU_NUMBER))
        .lpn(containerDetails.getTrackingId())
        .build();
  }

  private void processMMEvent(
      Container containerDetails,
      SlottingPalletResponse slottingPalletResponse,
      HttpHeaders headers) {

    Map<String, Object> payload =
        FixtureMMUtils.constructMMPayload(
            fixtureManagedConfig.getReceivingDock(),
            containerDetails,
            slottingPalletResponse,
            headers,
            fixtureManagedConfig);
    movePublisher.publishMove(
        containerDetails.getContainerItems().get(0).getQuantity(),
        fixtureManagedConfig.getReceivingDock(),
        headers,
        (LinkedTreeMap<String, Object>) payload,
        MoveEvent.CREATE.getMoveEvent());
    LOGGER.info(
        "Published move successfully for container id ::{}", containerDetails.getTrackingId());
  }

  public String postInventoryToCT(String trackingId) {
    if (!isCTPostingEnabled()) return null;
    Container containerDetails = getContainerAndValidate(trackingId);
    PutAwayInventory putAwayInventory = preparePutAwayPayloadFromContainer(containerDetails);
    return controlTowerService.putAwayInventory(Collections.singletonList(putAwayInventory));
  }

  public void publishToInventory(String trackingId) {
    Container containerDetails = getContainerAndValidate(trackingId);
    containerService.publishMultipleContainersToInventory(
        transformer.transformList(Collections.singletonList(containerDetails)));
  }

  private Container getContainerAndValidate(String trackingId) {
    Container containerDetails = containerPersisterService.getContainerDetails(trackingId);
    if (Objects.isNull(containerDetails)) {
      String errorDescription =
          String.format(
              ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG,
              trackingId);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND, errorDescription);
    }

    if (Objects.isNull(containerDetails.getCompleteTs())) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PROCESS_INVENTORY,
          String.format(ReceivingException.CONTAINER_IS_NOT_COMPLETE_ERROR, trackingId));
    }
    return containerDetails;
  }

  private void setMandatoryFieldForCreationOfInvetoryContainer(ContainerItem containerItem) {
    containerItem.setVnpkQty(FixtureConstants.DEFAULT_VNPK_QTY);
    containerItem.setWhpkQty(FixtureConstants.DEFAULT_WHPK_QTY);
    containerItem.setBaseDivisionCode(ReceivingConstants.BASE_DIVISION_CODE);
    containerItem.setFinancialReportingGroupCode(TenantContext.getFacilityCountryCode());
    containerItem.setInboundChannelMethod(PurchaseReferenceType.SSTKU.name());
    containerItem.setOutboundChannelMethod(PurchaseReferenceType.SSTKU.name());
  }

  private void setFixturesItemAttribute(List<ContainerItem> containerItems) {
    // enriching item attributes from REP
    Map<Integer, FixturesItemAttribute> fixturesItemAttributeMap =
        itemREPServiceClient.getItemDetailsOfItemNumbersFromREP(
            containerItems.stream().map(ContainerItem::getItemNumber).collect(Collectors.toSet()));
    containerItems
        .stream()
        .forEach(
            containerItem -> {
              FixturesItemAttribute item =
                  fixturesItemAttributeMap.get(containerItem.getItemNumber().intValue());
              // setting item attributes
              containerItem.setVnpkWgtQty(FixtureConstants.DEFAULT_VNPK_WEIGHT_QTY);
              containerItem.setVnpkWgtUom(
                  (Objects.isNull(item) || StringUtils.isEmpty(item.getWeightUnit()))
                      ? FixtureConstants.DEFAULT_VNPK_WEIGHT_UOM
                      : item.getWeightUnit());
              containerItem.setVnpkcbqty(
                  (Objects.isNull(item)
                          || (item.getArticleVolume() != null && item.getArticleVolume() == 0))
                      ? FixtureConstants.DEFAULT_VNPK_CUBE_QTY
                      : item.getArticleVolume().floatValue());
              containerItem.setVnpkcbuomcd(
                  (Objects.isNull(item) || StringUtils.isEmpty(item.getVolumeUnit()))
                      ? FixtureConstants.DEFAULT_VNPK_CUBE_UOM
                      : item.getVolumeUnit());
              if (fixtureManagedConfig.isItemMdmEnabled()) {
                containerItem.setItemUPC(
                    itemMDMServiceClient.getItemUpc(
                        Collections.singleton(containerItem.getItemNumber())));
              }
            });
  }

  private SlottingPalletResponse suggestSlot(
      Container containerDetails,
      List<Map<String, Object>> foundItems,
      PalletReceiveRequest palletReceiveRequest) {
    SlottingPalletResponse response = null;
    try {
      List<ContainerItem> containerItemListDetails = containerDetails.getContainerItems();

      SlottingPalletRequest slottingPalletRequest =
          FixtureSlottingUtils.slottingPalletRequest(
              foundItems,
              containerDetails,
              containerItemListDetails,
              fixtureManagedConfig,
              palletReceiveRequest);
      response = slottingRestApiClient.multipleSlotsFromSlotting(slottingPalletRequest, false);
    } catch (Exception e) {
      LOGGER.error("Exception occur while calling the SuggestSlot API from Receiving ", e);
    }
    return response;
  }

  public PalletReceiveResponse receiveV2(
      PalletReceiveRequest palletReceiveRequest, HttpHeaders headers) {
    String packNumber = palletReceiveRequest.getPackNumber();
    String userId = headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Container containerDetails = null;
    boolean isAuditRequired = false;
    PalletReceiveResponse palletReceiveResponse;
    if (!palletReceiveRequest.isReceiveWithoutASN()) {
      boolean isAuditRequest = isAuditRequest(palletReceiveRequest);
      LOGGER.info("Receive pallet {}. Is auditRequest {}.", packNumber, isAuditRequest);
      containerDetails = containerPersisterService.getContainerDetails(packNumber);

      if (!Objects.isNull(containerDetails)) {
        validateContainerNotReceived(containerDetails);
        /**
         * send status as LPN_MAPPPED if LPN is already mapped for this container. This means pallet
         * is received and putaway is pending.
         */
        if (!containerDetails
            .getTrackingId()
            .equalsIgnoreCase(containerDetails.getParentTrackingId())) {
          return mapResponseForPalletAlreadyMapped(containerDetails, headers);
        }
      } else {
        /**
         * check if there is a pending pallet by parentTrackingId, this means palletId is being
         * scanned again after lpn is mapped and putaway is not done.
         */
        Set<Container> containerDetailsByParentTrackingId =
            containerPersisterService.getContainerDetailsByParentTrackingId(packNumber);
        List<Container> activeContainersByParentTrackingId =
            containerDetailsByParentTrackingId
                .stream()
                .filter(
                    container ->
                        FixtureConstants.CONTAINER_STATUS_WO_ACTIVE_LIST.contains(
                            container.getContainerStatus()))
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(activeContainersByParentTrackingId)) {
          containerDetails = activeContainersByParentTrackingId.get(0);
          return mapResponseForPalletAlreadyMapped(containerDetails, headers);
        }
        // check if it is present in GDM
        LOGGER.info("Pallet {} not fund in Receiving, checking in GDM.", packNumber);
        SsccScanResponse response = searchPackInGDM(headers, packNumber);
        containerDetails =
            createContainerFromPacks(response.getShipments().get(0), response.getPacks().get(0));
        persistsShipmentMetadata(response.getShipments().get(0));
      }

      if (!isAuditRequest) {
        isAuditRequired = isAuditRequired(containerDetails);
      } else {
        // audit pallet
        auditContainerAndContainerItems(containerDetails, palletReceiveRequest);
      }

    } else {
      LOGGER.info("Receive Pallet {} without ASN", packNumber);
      validateRequestForReceiveWithoutASN(palletReceiveRequest);
      containerDetails = createContainerForNonASNReceiving(palletReceiveRequest, userId);
    }

    containerDetails.setContainerStatus(
        palletReceiveRequest.isReceiveWithoutASN()
            ? ReceivingConstants.STATUS_ACTIVE_NO_ASN
            : ReceivingConstants.STATUS_ACTIVE);

    // Get Pallet Label from LPN Service
    String lpn = lpnCacheService.getLPNBasedOnTenant(headers);

    if (containerPersisterService.checkIfContainerExist(lpn)) {
      String errorDescription =
          String.format(FixtureConstants.LPN_ALREADY_MAPPED_TO_DIFF_PALLET, lpn);
      throw new ReceivingBadDataException(ExceptionCodes.INVALID_REQUEST, errorDescription);
    }

    // Replace trackingID by LPN
    containerDetails.setTrackingId(lpn);
    containerDetails.getContainerItems().forEach(containerItem -> containerItem.setTrackingId(lpn));
    containerDetails.setLastChangedUser(userId);
    containerDetails.setCreateUser(userId);

    // save container in DB
    containerPersisterService.saveContainer(containerDetails);

    LOGGER.info("Mapping LPN {} to pallet {} : {}.", lpn, packNumber, containerDetails);

    // publish container creation
    containerService.publishMultipleContainersToInventory(
        transformer.transformList(Collections.singletonList(containerDetails)));

    if (!palletReceiveRequest.isMultiPallet()) {
      // call slotting for slot suggestion
      processSlotSuggestion(containerDetails, headers, palletReceiveRequest);
    }

    updateContainerStatusCompleted(containerDetails);

    palletReceiveResponse = mapResponseFromContainer(containerDetails);
    palletReceiveResponse.setLpn(lpn);
    palletReceiveResponse.setAuditRequired(isAuditRequired);
    palletReceiveResponse.setPrintRequests(
        Collections.singletonList(buildPrintRequest(containerDetails)));
    return palletReceiveResponse;
  }

  private void processSlotSuggestion(
      Container containerDetails, HttpHeaders headers, PalletReceiveRequest palletReceiveRequest) {

    if (fixtureManagedConfig.isSlottingEnabledRFC()) {
      try {
        List<Map<String, Object>> foundItems = invokeItemMDM(containerDetails, headers);
        SlottingPalletResponse slottingPalletResponse =
            suggestSlot(containerDetails, foundItems, palletReceiveRequest);
        if (slottingPalletResponse != null && fixtureManagedConfig.isMoveRequired()) {
          processMMEvent(containerDetails, slottingPalletResponse, headers);
        } else {
          LOGGER.error(
              "No suggested slot found for container id ::{}", containerDetails.getTrackingId());
        }
      } catch (Exception e) {
        LOGGER.error("Exception occur while calling Item MDM ", e);
      }
    }
  }

  private void updateContainerStatusCompleted(Container containerDetails) {
    if (fixtureManagedConfig.isSlottingEnabledRFC()) {
      try {
        containerDetails.setPublishTs(new Date());
        containerDetails.setCompleteTs(new Date());
        containerDetails.setContainerStatus(
            containerDetails.getContainerStatus().equals(ReceivingConstants.STATUS_ACTIVE_NO_ASN)
                ? ReceivingConstants.STATUS_COMPLETE_NO_ASN
                : ReceivingConstants.STATUS_COMPLETE);
        containerPersisterService.saveContainer(containerDetails);
      } catch (Exception e) {
        LOGGER.error("Exception occur while updating status to done :{}", e);
      }
    }
  }

  private PrintLabelRequest buildPrintRequest(Container container) {

    SimpleDateFormat dateFormat =
        new SimpleDateFormat(fixtureManagedConfig.getPrintingLabelDateFormat());
    List<LabelData> printingData = new ArrayList<>();
    container
        .getContainerItems()
        .forEach(
            containerItem -> {
              printingData.add(
                  new LabelData(
                      FixtureConstants.PrintingConstants.ITEM_NBR,
                      String.valueOf(containerItem.getItemNumber())));
              printingData.add(
                  new LabelData(
                      FixtureConstants.PrintingConstants.ITEM_DESCRIPTION,
                      containerItem.getDescription()));
              printingData.add(
                  new LabelData(
                      FixtureConstants.PrintingConstants.PO_NBR,
                      org.apache.commons.lang3.StringUtils.isNotBlank(
                              containerItem.getPurchaseReferenceNumber())
                          ? containerItem.getPurchaseReferenceNumber()
                          : fixtureManagedConfig.getWithoutAsnPoNbr()));
              printingData.add(
                  new LabelData(
                      FixtureConstants.PrintingConstants.QTY,
                      String.valueOf(containerItem.getQuantity())));
            });
    printingData.add(
        new LabelData(FixtureConstants.PrintingConstants.LPN, container.getTrackingId()));
    printingData.add(
        new LabelData(
            FixtureConstants.PrintingConstants.USER,
            String.valueOf(container.getLastChangedUser())));
    printingData.add(
        new LabelData(
            FixtureConstants.PrintingConstants.DATE_RECEIVED, dateFormat.format(new Date())));

    return PrintLabelRequest.builder()
        .formatName(fixtureManagedConfig.getPrinterFormatName())
        .labelIdentifier(container.getTrackingId())
        .ttlInHours(fixtureManagedConfig.getPrinterPalletTTL())
        .data(printingData)
        .build();
  }

  private List<Map<String, Object>> invokeItemMDM(Container containerDetails, HttpHeaders headers) {

    // Calling Item MDM service to get Item UPC
    headers = ReceivingUtils.getForwardableHttpHeaders(headers);
    headers.set(ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_SLOT);
    headers.setContentType(MediaType.APPLICATION_JSON);

    // ItemFoundSupplySection Response
    Map<String, Object> itemMDMDetailsList =
        itemMDMServiceClient.retrieveItemDetails(
            containerDetails
                .getContainerItems()
                .stream()
                .map(ContainerItem::getItemNumber)
                .collect(Collectors.toSet()),
            headers,
            false);

    return (List<Map<String, Object>>)
        itemMDMDetailsList.get(ReceivingConstants.ITEM_FOUND_SUPPLY_ITEM);
  }
}
