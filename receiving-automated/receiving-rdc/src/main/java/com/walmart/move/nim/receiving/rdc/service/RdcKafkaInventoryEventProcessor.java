package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.InventoryLabelType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class RdcKafkaInventoryEventProcessor implements EventProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RdcKafkaInventoryEventProcessor.class);

  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private ReceiptService receiptService;
  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcCancelContainerProcessor rdcCancelContainerProcessor;
  @ManagedConfiguration private AppConfig appConfig;

  /**
   * Process the inventory events for adjustment and deleted containers
   *
   * @param messageData
   */
  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    try {
      InventoryAdjustmentTO inventoryAdjustmentTO = (InventoryAdjustmentTO) messageData;

      HttpHeaders headers = inventoryAdjustmentTO.getHttpHeaders();
      JsonObject eventObject =
          inventoryAdjustmentTO
              .getJsonObject()
              .getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_OBJECT);

      String trackingId =
          eventObject.get(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID).getAsString();

      String labelType = fetchInventoryLabelType(eventObject);

      /**
       * Find label type from the JsonObject eventObject - and check if XDK1 / XDK2 - consider it as
       * offline receiving flow
       */
      if (Objects.nonNull(headers)
          && isInventoryDeleteEvent(headers)
          && Objects.nonNull(labelType)
          && RdcConstants.OFFLINE_LABEL_TYPE.contains(labelType)) {
        LOGGER.info("Inventory delete event triggered for tracking id : {} ", trackingId);
        processDeleteEventForOfflineRcv(headers, eventObject, trackingId, labelType);
        LOGGER.info("Inventory delete event completed for tracking id : {} ", trackingId);
        return;
      }

      if (!isValidInventoryAdjustmentMessage(inventoryAdjustmentTO)) {
        LOGGER.info("Invalid inventory adjustment/delete message");
        return;
      }

      if (isItemListAndTrackingIdExists(eventObject)) {

        JsonObject item =
            (JsonObject)
                eventObject
                    .getAsJsonArray(ReceivingConstants.INVENTORY_ADJUSTMENT_ITEM_LIST)
                    .get(0);

        // Filter only Atlas containers to ignore legacy container updates events
        if (checkIfAtlasContainer(eventObject)) {
          Container container = null;
          if (Objects.nonNull(labelType) && RdcConstants.OFFLINE_LABEL_TYPE.contains(labelType)) {
            container =
                getContainerForOffline(
                    rdcContainerUtils.convertEighteenToSixteenDigitLabel(trackingId));
          }
          if (Objects.isNull(container)) {
            container = getContainer(trackingId);
          }
          if (ReceivingUtils.isValidLpn(trackingId)) {
            // putaway complete updates based on sorter divert events
            if (isInventoryMovedEvent(headers)) {
              LOGGER.info(
                  "Received container moved event from inventory with header invevent:{}",
                  headers.getFirst(ReceivingConstants.INVENTORY_EVENT));
              updateContainerStatus(eventObject, container);
              return;
            }
          }

          // inventory adjustment updates for Lpns & Smart Labels (Break pack inner picks SHP_VOID)
          boolean isInventoryAdjustmentProcessed =
              processInventoryAdjustmentMessage(item, trackingId, headers, labelType, container);
          if (isInventoryAdjustmentProcessed) {
            return;
          }

          String inboundChannelMethod =
              item.has(INVENTORY_ADJUSTMENT_INBOUND_CHANNEL_TYPE)
                  ? item.get(INVENTORY_ADJUSTMENT_INBOUND_CHANNEL_TYPE).getAsString()
                  : null;
          if (Objects.nonNull(inboundChannelMethod)
              && DA_CHANNEL_METHODS_FOR_RDC.contains(inboundChannelMethod)) {
            // EI updates for DA Picked containers
            if (container.getInventoryStatus().equals(InventoryStatus.ALLOCATED.name())) {
              publishPickedEventUpdatesToEIForAtlasDAContainers(eventObject, container, trackingId);
            }

            // putaway complete status update on loaded events for Non Con Cases
            if (container.getInventoryStatus().equals(InventoryStatus.PICKED.name())) {
              putawayCompleteForNonConCases(trackingId, container, eventObject, item, headers);
            }
            return;
          }
        }
      }

      /* putaway complete status update on loaded events for Non Con Pallet(Pallet Pull) */
      putawayCompleteForNonConPallet(trackingId, eventObject, headers);
    } catch (Exception e) {
      LOGGER.error("Exception encountered in RdcKafkaInventoryEventProcessor : {}", e);
    }
  }

  /**
   * Fetch inventory label type from the event object
   *
   * @param eventObject
   * @return
   */
  private static String fetchInventoryLabelType(JsonObject eventObject) {
    String labelType =
        eventObject.get(ReceivingConstants.INVENTORY_LABEL_TYPE) != null
            ? eventObject.get(ReceivingConstants.INVENTORY_LABEL_TYPE).getAsString()
            : null;
    return labelType;
  }

  /**
   * Processing DC_VOIDS for offline receiving flow
   *
   * @param headers
   * @param eventObject
   * @param trackingId
   * @throws ReceivingException
   */
  private void processDeleteEventForOfflineRcv(
      HttpHeaders headers, JsonObject eventObject, String trackingId, String labelType)
      throws ReceivingException {

    LOGGER.info(
        "Label Type for processing delete event : {} for tracking id : {} ", labelType, trackingId);
    // Executing 18 to 16 digit conversion for Imports and then querying in the database.
    Container container =
        getContainerForOffline(rdcContainerUtils.convertEighteenToSixteenDigitLabel(trackingId));
    if (Objects.isNull(container)) {
      container = getContainer(trackingId);
    }
    LOGGER.info(
        "[XDK] Received container deleted event from inventory with header invevent:{}",
        headers.getFirst(ReceivingConstants.INVENTORY_EVENT));
    container.setLabelType(labelType);

    /**
     * Send DC_VOID event to EI only if it does not have child container and is coming through
     * adjustment flow
     */
    if (CollectionUtils.isEmpty(container.getChildContainers())
        && headers.containsKey(ReceivingConstants.FLOW_DESCRIPTOR)
        && ReceivingConstants.ADJUSTMENT_FLOW.equalsIgnoreCase(
            headers.getFirst(ReceivingConstants.FLOW_DESCRIPTOR))) {
      LOGGER.info(
          "[XDK] Starting to publish DC_VOID event to EI for label type: {}, and tracking id: {}",
          labelType,
          trackingId);
      rdcCancelContainerProcessor.publishInvDeleteEventsToEI(container, ReceivingConstants.DC_VOID);
      LOGGER.info(
          "[XDK] Processed DC_VOIDS to EI for trackingId: {},  label type: {}",
          eventObject.get(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID).getAsString(),
          labelType);
    }
  }

  /**
   * This method will validate inventory source system & if its Atlas Source systems or not.
   *
   * @param eventObject
   * @return
   */
  private boolean checkIfAtlasContainer(JsonObject eventObject) {
    return eventObject.has(ReceivingConstants.INVENTORY_EVENT_SOURCE_SYSTEM)
        && eventObject.get(ReceivingConstants.INVENTORY_EVENT_SOURCE_SYSTEM).getAsInt()
            == ReceivingConstants.INVENTORY_EVENT_SOURCE_SYSTEM_ATLAS;
  }

  /**
   * This method validate inventory container update event & update the container status as Putaway
   * based on the below conditions. if the container source is Atlas Container with valid LPN status
   * & container type is Pallet & labelType as DQRL & Since the pallet has the child containers we
   * will validate if the child container exists and very first child container eventObject to check
   * if the destinationSlotId as R8001 (do not need check for all other child containers as all
   * should be having the same destinationSlotId). If the container updated event is loaded event
   * then we will update the container status as Putaway complete.
   *
   * @param trackingId
   * @param eventObject
   * @param headers
   */
  private void putawayCompleteForNonConPallet(
      String trackingId, JsonObject eventObject, HttpHeaders headers) throws ReceivingException {
    LOGGER.info("Item list is empty in the adjustment message for trackingId: {}", trackingId);
    if (validateInventoryMovedEventAndUpdateContainerStatus(trackingId, eventObject, headers)) {
      return;
    }
    if (checkIfAtlasContainer(eventObject)
        && ReceivingUtils.isValidLpn(trackingId)
        && isChildContainersAndTrackingIdExists(eventObject)) {
      JsonObject firstChildContainerObject =
          (JsonObject) eventObject.getAsJsonArray(INVENTORY_ADJUSTMENT_CHILD_CONTAINERS).get(0);
      if (Objects.nonNull(firstChildContainerObject)) {
        JsonObject firstChildContainerItem =
            firstChildContainerObject.has(ReceivingConstants.INVENTORY_ADJUSTMENT_ITEM_LIST)
                ? (JsonObject)
                    firstChildContainerObject
                        .getAsJsonArray(ReceivingConstants.INVENTORY_ADJUSTMENT_ITEM_LIST)
                        .get(0)
                : null;
        if (Objects.nonNull(firstChildContainerItem)) {
          String containerType = eventObject.get(INVENTORY_ADJUSTMENT_CONTAINER_TYPE).getAsString();
          String labelType =
              eventObject.has(INVENTORY_LABEL_TYPE)
                  ? eventObject.get(INVENTORY_LABEL_TYPE).getAsString()
                  : null;
          String destinationSlotId =
              firstChildContainerItem.has(ReceivingConstants.INVENTORY_ITEM_DEST_SLOT_ID)
                  ? firstChildContainerItem
                      .get(ReceivingConstants.INVENTORY_ITEM_DEST_SLOT_ID)
                      .getAsString()
                  : null;
          if (ObjectUtils.allNotNull(destinationSlotId, containerType, labelType)
              && RdcConstants.DA_R8001_SLOT.equals(destinationSlotId)
              && ContainerType.PALLET.getText().equals(containerType)
              && Objects.equals(labelType, InventoryLabelType.R8000_DA_FULL_CASE.getType())
              && isInventoryLoadedEvent(headers)) {
            LOGGER.info(
                "Received container loaded event from inventory with header invevent:{}",
                headers.getFirst(ReceivingConstants.INVENTORY_EVENT));
            Container container = getContainer(trackingId);
            if (container.getInventoryStatus().equals(InventoryStatus.PICKED.name())) {
              LOGGER.info(
                  "Update putaway complete status for Non Conveyable Pallet:{}", trackingId);
              updatePutawayCompleteStatus(trackingId, container);
            }
          }
        }
      }
    }
  }

  /**
   * This method validate inventory adjustment payload and if the destinationSlotId is R8001 & the
   * invevent is loaded then it will consider the putaway is complete for the container.
   *
   * @param trackingId
   * @param item
   * @param headers
   * @throws ReceivingException
   */
  private void putawayCompleteForNonConCases(
      String trackingId,
      Container container,
      JsonObject eventObject,
      JsonObject item,
      HttpHeaders headers) {
    String labelType =
        eventObject.has(ReceivingConstants.INVENTORY_LABEL_TYPE)
            ? eventObject.get(ReceivingConstants.INVENTORY_LABEL_TYPE).getAsString()
            : null;
    String destinationSlotId =
        item.has(ReceivingConstants.INVENTORY_ITEM_DEST_SLOT_ID)
            ? item.get(ReceivingConstants.INVENTORY_ITEM_DEST_SLOT_ID).getAsString()
            : null;
    if (ObjectUtils.allNotNull(labelType, destinationSlotId)
        && Objects.equals(labelType, InventoryLabelType.R8000_DA_FULL_CASE.getType())
        && Objects.equals(destinationSlotId, RdcConstants.DA_R8001_SLOT)
        && isInventoryLoadedEvent(headers)) {
      LOGGER.info(
          "Received container loaded event from inventory with header invevent:{}",
          headers.getFirst(ReceivingConstants.INVENTORY_EVENT));
      LOGGER.info("Update putaway complete status for Non Conveyable container:{}", trackingId);
      updatePutawayCompleteStatus(trackingId, container);
    }
  }

  private void updatePutawayCompleteStatus(String trackingId, Container container) {
    if (Objects.nonNull(container)
        && container.getContainerStatus().equals(ReceivingConstants.STATUS_COMPLETE)) {
      LOGGER.info("PutAway is completed for container with trackingId:{}", trackingId);
      container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
      containerPersisterService.saveContainer(container);
    }
  }

  private Container getContainer(String trackingId) throws ReceivingException {
    Container container =
        containerPersisterService.getContainerWithChildContainersExcludingChildContents(trackingId);
    if (Objects.isNull(container)) {
      LOGGER.warn(ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
      throw new ReceivingException(
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE);
    }
    return container;
  }

  /**
   * Fetch the containers for offline flow
   *
   * @param trackingId - tracking ID
   * @return Container
   */
  private Container getContainerForOffline(String trackingId) {
    Container container = null;
    try {
      container =
          containerPersisterService.getContainerWithChildContainersExcludingChildContents(
              trackingId);
    } catch (Exception e) {
      LOGGER.info(
          "Not able to fetch the container details for tracking ID: {} , exception {}",
          trackingId,
          e);
    }
    return container;
  }

  private boolean isValidInventoryAdjustmentMessage(InventoryAdjustmentTO inventoryAdjustmentTO) {
    LOGGER.info("RdcKafkaInventoryEventProcessor - validating inventory adjustment message ....");
    if (!isValidInventoryAdjustment(inventoryAdjustmentTO.getJsonObject())) {
      LOGGER.info("Ignoring inventory adjustment because invalid eventObject");
      TenantContext.clear();
      return false;
    }

    String trackingId =
        inventoryAdjustmentTO
            .getJsonObject()
            .getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_OBJECT)
            .get(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID)
            .getAsString();
    if (!validateMessageHeaders(inventoryAdjustmentTO.getHttpHeaders())) {
      LOGGER.info(
          "Missing mandatory headers in the adjustment message for trackingId: {}, so ignoring this message",
          trackingId);
      return false;
    }
    if (isValidInventoryAdjustmentSource(inventoryAdjustmentTO.getHttpHeaders())) {
      LOGGER.info(
          "Source of this adjustment message is receiving app, so skipping adjustment message for trackingId: {}",
          trackingId);
      return false;
    }
    return true;
  }

  private boolean isValidInventoryAdjustment(JsonObject inventoryAdjustment) {
    return inventoryAdjustment.has(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT)
        && inventoryAdjustment
            .get(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT)
            .getAsString()
            .equalsIgnoreCase(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED)
        && inventoryAdjustment.has(ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_OBJECT);
  }

  private boolean validateMessageHeaders(HttpHeaders headers) {
    return (Objects.nonNull(headers))
        && headers.containsKey(ReceivingConstants.INVENTORY_EVENT)
        && headers.containsKey(ReceivingConstants.REQUEST_ORIGINATOR);
  }

  private boolean isValidInventoryAdjustmentSource(HttpHeaders headers) {
    return headers.containsKey(ReceivingConstants.REQUEST_ORIGINATOR)
        && ReceivingConstants.APP_NAME_VALUE.equals(
            headers.getFirst(ReceivingConstants.REQUEST_ORIGINATOR));
  }

  private boolean isInventoryMovedEvent(HttpHeaders headers) {
    return headers.containsKey(ReceivingConstants.INVENTORY_EVENT)
        && ReceivingConstants.INVENTORY_EVENT_MOVED.equalsIgnoreCase(
            headers.getFirst(ReceivingConstants.INVENTORY_EVENT));
  }

  private boolean isInventoryLoadedEvent(HttpHeaders headers) {
    return headers.containsKey(ReceivingConstants.INVENTORY_EVENT)
        && ReceivingConstants.INVENTORY_EVENT_LOADED.equalsIgnoreCase(
            headers.getFirst(ReceivingConstants.INVENTORY_EVENT));
  }

  private boolean isLocationNameExist(JsonObject eventObject) {
    return eventObject.has(ReceivingConstants.INVENTORY_LOCATION_NAME)
        && !eventObject.get(ReceivingConstants.INVENTORY_LOCATION_NAME).isJsonNull();
  }

  private boolean isLabelTypeExists(JsonObject eventObject) {
    return eventObject.has(ReceivingConstants.INVENTORY_DA_ATLAS_ITEM_LABEL_TYPE)
        && !eventObject.get(ReceivingConstants.INVENTORY_DA_ATLAS_ITEM_LABEL_TYPE).isJsonNull();
  }

  private boolean isBreakPackInductLabel(JsonObject eventObject) {
    return isLabelTypeExists(eventObject)
        && eventObject
            .get(ReceivingConstants.INVENTORY_DA_ATLAS_ITEM_LABEL_TYPE)
            .getAsString()
            .equals(InventoryLabelType.DA_BREAK_PACK_PUT_INDUCT.getType());
  }

  private boolean isBreakPackInnerPick(JsonObject eventObject) {
    return isLabelTypeExists(eventObject)
        && eventObject
            .get(ReceivingConstants.INVENTORY_DA_ATLAS_ITEM_LABEL_TYPE)
            .getAsString()
            .equals(InventoryLabelType.DA_BREAK_PACK_INNER_PICK.getType());
  }

  private boolean isItemListAndTrackingIdExists(JsonObject eventObject) {
    return eventObject.has(ReceivingConstants.INVENTORY_ADJUSTMENT_ITEM_LIST)
        && eventObject.has(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID)
        && eventObject.getAsJsonArray(ReceivingConstants.INVENTORY_ADJUSTMENT_ITEM_LIST).size() > 0;
  }

  private boolean isChildContainersAndTrackingIdExists(JsonObject eventObject) {
    return eventObject.has(ReceivingConstants.INVENTORY_ADJUSTMENT_CHILD_CONTAINERS)
        && eventObject
                .getAsJsonArray(ReceivingConstants.INVENTORY_ADJUSTMENT_CHILD_CONTAINERS)
                .size()
            > 0
        && eventObject.has(ReceivingConstants.INVENTORY_ADJUSTMENT_TRACKING_ID)
        && eventObject.has(INVENTORY_ADJUSTMENT_CONTAINER_TYPE);
  }

  public void updateContainerStatus(JsonObject eventObject, Container container) {
    if (container.getContainerStatus().equals(ReceivingConstants.STATUS_COMPLETE)) {
      if (isLocationNameExist(eventObject)) {
        // Atlas SSTK containers
        if (container.getContainerType().equals(ContainerType.PALLET.getText())
            && (isSSTKFreightType(container)
                || (isDAFreightType(container)
                    && container.getInventoryStatus().equals(InventoryStatus.ALLOCATED.name())))) {
          String inventoryLocationName =
              eventObject.get(ReceivingConstants.INVENTORY_LOCATION_NAME).getAsString();
          String containerDestinationSlot = container.getDestination().get(ReceivingConstants.SLOT);
          LOGGER.info(
              "Inventory locationName:{} and container destination slot:{}",
              inventoryLocationName,
              containerDestinationSlot);
          if (inventoryLocationName.equals(containerDestinationSlot)) {
            updatePutawayQtyAndStatus(container, container.getTrackingId());
          }
          return;
        }

        /* Atlas DA Containers to filter out only the Shipping labels for the sorter divert.
        Ignore Sym Eligible Routing labels (Allocated status) & Break Pack Induct labels */
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_DIVERT_INFO,
            false)) {
          if (isDAFreightType(container)
              && container.getInventoryStatus().equals(InventoryStatus.PICKED.name())
              && isSorterDivertedForDAContainer(eventObject)) {
            updatePutawayQtyAndStatus(container, container.getTrackingId());
          }
          if (isDSDCFreightType(container)) {
            updatePutawayStatusForDSDCContainer(eventObject, container);
          }
        }
      }
    }
  }

  private boolean isDSDCFreightType(Container container) {
    return CollectionUtils.isNotEmpty(container.getContainerItems())
        && RdcConstants.DSDC_CHANNEL_METHODS_FOR_RDC.equalsIgnoreCase(
            container.getContainerItems().get(0).getInboundChannelMethod());
  }

  /**
   * This method will update containerStatus to PUTAWAY_COMPLETE for all child containers of DSDC
   * Container on receipt of sorter divert message
   *
   * @param eventObject
   * @param container
   */
  private void updatePutawayStatusForDSDCContainer(JsonObject eventObject, Container container) {
    if (isSorterDivertedForDAContainer(eventObject)
        && !CollectionUtils.isEmpty(container.getChildContainers())) {
      List<Container> childContainers = new ArrayList<>(container.getChildContainers());
      if (ReceivingConstants.STATUS_COMPLETE.equals(childContainers.get(0).getContainerStatus())) {
        LOGGER.info(
            "Updating PUTAWAY_COMPLETE to all child containers for DSDC Container : SSCC {}  Container {}",
            container.getSsccNumber(),
            container.getTrackingId());
        childContainers.forEach(
            child -> {
              child.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
            });
        if (!CollectionUtils.isEmpty(childContainers))
          containerPersisterService.saveContainers(childContainers);
      }
    }
  }

  private boolean isDAFreightType(Container container) {
    return CollectionUtils.isNotEmpty(container.getContainerItems())
        && ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
            container.getContainerItems().get(0).getInboundChannelMethod());
  }

  private boolean isSSTKFreightType(Container container) {
    return CollectionUtils.isNotEmpty(container.getContainerItems())
        && ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(
            container.getContainerItems().get(0).getInboundChannelMethod());
  }

  private void updatePutawayQtyAndStatus(Container container, String trackingId) {
    LOGGER.info("PutAway is completed for container with trackingId:{}", trackingId);
    container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED,
        false)) {
      containerPersisterService.saveContainer(container);
    } else {
      Receipt receipt = receiptService.updateOrderFilledQuantityInReceipts(container);
      containerAdjustmentHelper.persistAdjustedReceiptsAndContainer(receipt, container);
    }
  }

  /**
   * This method validate if the DA Atlas container is sorter diverted or not. If the container is
   * Sorter diverted then this is considered as putaway completed. If the container location item
   * details has sorterId information then we consider the container is Sorter Diverted. Sorter
   * values can be either lower or upper. (upper-psc-server, lower-psc-server).
   *
   * @param eventObject
   * @return
   */
  private boolean isSorterDivertedForDAContainer(JsonObject eventObject) {
    boolean isSorterDiverted = false;
    if (eventObject.has(ReceivingConstants.INVENTORY_DA_ATLAS_ITEM_CONTAINER_LOCATION_DETAILS)) {
      JsonObject sorterDivertLocationDetails =
          eventObject
              .get(ReceivingConstants.INVENTORY_DA_ATLAS_ITEM_CONTAINER_LOCATION_DETAILS)
              .getAsJsonObject();
      isSorterDiverted =
          sorterDivertLocationDetails.has(
              ReceivingConstants.INVENTORY_CONTAINER_LOCATION_DETAILS_SORTER_ID);
    }
    return isSorterDiverted;
  }

  /**
   * This method will send DC_PICK events for DA Atlas containers when the inventory status gets
   * changed to PICKED from ALLOCATED. This is applicable for DA Case pack (ROUTING) labels & Break
   * Pack Child inner picked containers. We do not send to send any EI updates for SSTK containers
   *
   * @param eventObject
   * @param trackingId
   * @throws ReceivingException
   */
  private void publishPickedEventUpdatesToEIForAtlasDAContainers(
      JsonObject eventObject, Container container, String trackingId) throws ReceivingException {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS,
        false)) {
      if (isDAFreightType(container)) {
        String inventoryContainerStatus =
            eventObject
                .get(ReceivingConstants.INVENTORY_DA_ATLAS_ITEM_CONTAINER_STATUS)
                .getAsString();

        /** Find label type from the JsonObject eventObject - and set it in container */
        String labelType = eventObject.get(ReceivingConstants.INVENTORY_LABEL_TYPE).getAsString();
        container.setLabelType(labelType);
        LOGGER.info(
            "Publishing pick event to EI for label type: {}, and tracking id: {}",
            labelType,
            trackingId);

        boolean isEligibleForEIUpdates =
            inventoryContainerStatus.equals(InventoryStatus.PICKED.name());
        if (isEligibleForEIUpdates) {
          LOGGER.info("Publishing Pick events to EI for container:{}", trackingId);
          rdcContainerUtils.publishContainerToEI(container, ReceivingConstants.EI_DC_PICKED_EVENT);
          /**
           * BreakPackInductLabel update for Putaway Complete Status is not needed for offline flow
           */
          if (!RdcConstants.OFFLINE_LABEL_TYPE.contains(labelType)) {
            updatePutawayCompleteStatusForBreakPackInductLabel(container, eventObject);
          }
        }
      }
    }
  }

  /**
   * When inner picks are picked by PUT system, inventory will move the container status as Picked.
   * Once the very first inner pick container is picked, we need to update the parent container
   * status as PUTAWAY_COMPLETE if it's not already in COMPLETED status. Here after VTR is not
   * allowed for parent container & VTR allowed until the very first inner pick is Picked by PUT
   * system for Break Pack order filling.
   *
   * @param childContainer
   * @throws ReceivingException
   */
  private void updatePutawayCompleteStatusForBreakPackInductLabel(
      Container childContainer, JsonObject eventObject) throws ReceivingException {
    if (Objects.nonNull(childContainer.getParentTrackingId())
        && isBreakPackInnerPick(eventObject)) {
      String parentTrackingId = childContainer.getParentTrackingId();
      Container parentContainer = getContainer(parentTrackingId);
      if (parentContainer.getContainerStatus().equals(ReceivingConstants.STATUS_COMPLETE)) {
        LOGGER.info(
            "Update putaway status as complete for Break pack Induct label:{} as InnerPick:{} "
                + " is in PICKED status",
            parentTrackingId,
            childContainer.getTrackingId());
        updatePutawayQtyAndStatus(parentContainer, parentTrackingId);
      }
    }
  }

  public boolean processInventoryAdjustmentMessage(
      JsonObject item,
      String trackingId,
      HttpHeaders headers,
      String labelType,
      Container container)
      throws ReceivingException {

    /** If the adjustment event is for offline and if invevent is not picked, then skip */
    if (Objects.nonNull(labelType)
        && RdcConstants.OFFLINE_LABEL_TYPE.contains(labelType)
        && headers.containsKey(ReceivingConstants.INVENTORY_EVENT)
        && !ReceivingConstants.INVENTORY_EVENT_PICKED.equalsIgnoreCase(
            headers.getFirst(ReceivingConstants.INVENTORY_EVENT))) {
      LOGGER.info(
          "[xdk] Label Type: {} for invevent : {}, and tracking Id: {} skipped inventory adjustments",
          labelType,
          headers.getFirst(ReceivingConstants.INVENTORY_EVENT),
          trackingId);
      return true;
    }

    boolean isInventoryAdjustmentProcessed = false;
    if (item.has(ReceivingConstants.INVENTORY_ADJUSTMENT_ADJUSTMENT_TO)) {
      LOGGER.info(
          "RdcKafkaInventoryEventProcessor - processing inventory adjustment message for trackingId: {}",
          container.getTrackingId());
      JsonObject adjustmentTO =
          item.getAsJsonObject(ReceivingConstants.INVENTORY_ADJUSTMENT_ADJUSTMENT_TO);

      if (adjustmentTO.has(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE)) {
        String reasonCode =
            adjustmentTO.get(ReceivingConstants.INVENTORY_ADJUSTMENT_REASON_CODE).toString();
        switch (Integer.parseInt(reasonCode)) {
          case ReceivingConstants.VTR_REASON_CODE:
            LOGGER.info(
                "Valid VTR message is received with trackingId:{}", container.getTrackingId());
            rdcContainerUtils.backoutContainer(container, headers);
            break;
          case ReceivingConstants.RDC_INVENTORY_RECEIVE_ERROR_REASON_CODE:
            Integer updatedContainerQty =
                adjustmentTO.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY).getAsInt();
            LOGGER.info(
                "Valid receiving correction message is received with trackingId:{}, updatedContainerQty:{}",
                container.getTrackingId(),
                updatedContainerQty);
            rdcContainerUtils.applyReceivingCorrections(container, updatedContainerQty, headers);
            ContainerItem containerItem = container.getContainerItems().get(0);
            final Integer currentContainerQtyInVnpk =
                ReceivingUtils.conversionToVendorPack(
                    containerItem.getQuantity(),
                    ReceivingConstants.Uom.EACHES,
                    containerItem.getVnpkQty(),
                    containerItem.getWhpkQty());
            LabelAction action;
            String freightType = container.getContainerItems().get(0).getInboundChannelMethod();
            if (StringUtils.isNotBlank(freightType)
                && ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(freightType)) {
              action = LabelAction.DA_BACKOUT;
            } else {
              action = LabelAction.CORRECTION;
            }
            rdcInstructionUtils.publishInstructionToWft(
                container, currentContainerQtyInVnpk, updatedContainerQty, action, headers);
            break;
          case ReceivingConstants.DAMAGE_REASON_CODE:
            Integer damageQuantity =
                adjustmentTO.get(ReceivingConstants.INVENTORY_ADJUSTMENT_QTY).getAsInt();
            LOGGER.info(
                "Valid warehouse damage adjustment message is received with trackingId:{}, damageQuantity:{}",
                container.getTrackingId(),
                damageQuantity);
            rdcContainerUtils.processWarehouseDamageAdjustments(container, damageQuantity, headers);
            break;
          case ReceivingConstants.SHIP_VOID:
            LOGGER.info("Publish to EI with trackingId:{}", container.getTrackingId());
            TenantContext.get().setPublishEICallStart(System.currentTimeMillis());
            rdcContainerUtils.publishContainerToEI(container, ReceivingConstants.DC_SHIP_VOID);
            TenantContext.get().setPublishEICallEnd(System.currentTimeMillis());
            break;
          case ReceivingConstants.TRUE_OUT:
            LOGGER.info("Publish to EI with trackingId:{}", container.getTrackingId());
            TenantContext.get().setPublishEICallStart(System.currentTimeMillis());
            rdcContainerUtils.publishContainerToEI(container, ReceivingConstants.DC_TRUE_OUT);
            TenantContext.get().setPublishEICallEnd(System.currentTimeMillis());
            break;
          case ReceivingConstants.XDK_VOID:
            LOGGER.info("Publish to EI with trackingId:{}", container.getTrackingId());
            TenantContext.get().setPublishEICallStart(System.currentTimeMillis());
            rdcContainerUtils.publishContainerToEI(container, ReceivingConstants.DC_XDK_VOID);
            TenantContext.get().setPublishEICallEnd(System.currentTimeMillis());
            break;
          default:
            LOGGER.info(
                "Ignoring inventory adjustment message because of invalid reasonCode: {}",
                reasonCode);
        }
        isInventoryAdjustmentProcessed = true;
      }
    }
    return isInventoryAdjustmentProcessed;
  }

  private boolean isInventoryDeleteEvent(HttpHeaders headers) {
    return headers.containsKey(ReceivingConstants.EVENT_TYPE)
        && ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_CONTAINER_DELETED.equalsIgnoreCase(
            headers.getFirst(ReceivingConstants.EVENT_TYPE));
  }

  private boolean validateInventoryMovedEventAndUpdateContainerStatus(
      String trackingId, JsonObject eventObject, HttpHeaders headers) throws ReceivingException {
    if (checkIfAtlasContainer(eventObject) && ReceivingUtils.isValidLpn(trackingId)) {
      // isMoved Event here
      if (isInventoryMovedEvent(headers)) {
        Container container = getContainer(trackingId);
        updateContainerStatus(eventObject, container);
        return true;
      }
    }
    return false;
  }
}
