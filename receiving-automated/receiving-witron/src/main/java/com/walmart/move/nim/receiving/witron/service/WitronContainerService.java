package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ContainerUtils.adjustContainerByQty;
import static com.walmart.move.nim.receiving.core.common.ContainerUtils.isAtlasConvertedItem;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.LABEL_ON_HOLD_REQUEST_IN_GLS_INSTEAD_OF_ATLAS;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.conversionToEaches;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVALID_REQUEST;
import static com.walmart.move.nim.receiving.utils.constants.DeliveryStatus.COMPLETE;
import static com.walmart.move.nim.receiving.utils.constants.DeliveryStatus.WRK;
import static com.walmart.move.nim.receiving.utils.constants.InventoryStatus.AVAILABLE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GDM_DELIVERY_STATUS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PUTAWAY_DELETE_ACTION;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PUTAWAY_UPDATE_ACTION;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SLOTTING_PALLET_OFF_HOLD;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.STATUS_BACKOUT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingPalletBuildResponse;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.ReceiptHelper;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.AbstractContainerService;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentValidator;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.MoveEvent;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import io.strati.metrics.annotation.ExceptionCounted;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Container service for Witron specific features
 *
 * @author lkotthi
 */
@Service
public class WitronContainerService extends AbstractContainerService {
  private static final Logger log = LoggerFactory.getLogger(WitronContainerService.class);

  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private InventoryService inventoryService;
  @Autowired private ContainerRepository containerRepository;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private GdcSlottingServiceImpl slottingService;
  @Autowired private MovePublisher movePublisher;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private GdcPutawayPublisher gdcPutawayPublisher;

  @Autowired private ReceiptHelper receiptHelper;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Autowired private ReceiptService receiptService;

  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;
  @Autowired private ContainerAdjustmentValidator containerAdjustmentValidator;
  @Autowired private GDCFlagReader gdcFlagReader;

  public WitronContainerService() {
    this.gson =
        new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create();
  }

  private final Gson gson;

  private final JsonParser parser = new JsonParser();

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public void palletOnHold(String trackingId, HttpHeaders httpHeaders) throws ReceivingException {
    // Get the container details
    Container container = getContainer(trackingId);
    validateRequestForGls(container.getContainerItems().get(0));
    // Check if the container already on-hold
    if (InventoryStatus.WORK_IN_PROGRESS.toString().equals(container.getInventoryStatus())) {
      throw new ReceivingException(
          ReceivingException.LABEL_ALREADY_ONHOLD_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.LABEL_ALREADY_ONHOLD_ERROR_CODE);
    }
    final String sourceLocationInInventory =
        inventoryService.getContainerLocation(trackingId, httpHeaders);

    // Set the container as on-hold(INVENTORY_STATUS=WORK_IN_PROGRESS)
    container.setInventoryStatus(InventoryStatus.WORK_IN_PROGRESS.toString());
    container.setLastChangedUser(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    container.setLastChangedTs(new Date());

    // Get the destination for staging area
    SlottingPalletBuildResponse slottingResponse =
        slottingService.getDivertLocation(container, httpHeaders, container.getInventoryStatus());
    final String toLocation_FromSlotting = slottingResponse.getDivertLocation();

    // Update the container
    containerRepository.save(container);

    // Notify Inventory to hold the container
    inventoryService.onHold(container, httpHeaders);

    // Publish putaway message
    gdcPutawayPublisher.publishMessage(container, PUTAWAY_UPDATE_ACTION, httpHeaders);

    // Prepare move info
    LinkedTreeMap<String, Object> newMove =
        createNewMove(httpHeaders, container, toLocation_FromSlotting);

    // Cancel existing move and create the new one to ripening area
    movePublisher.publishMove(
        InstructionUtils.getMoveQuantity(container),
        sourceLocationInInventory,
        httpHeaders,
        newMove,
        MoveEvent.REPLACE.getMoveEvent());

    // Publish receipt update to SCT
    receiptPublisher.publishReceiptUpdate(trackingId, httpHeaders, Boolean.TRUE);
  }

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public void palletOffHold(String trackingId, HttpHeaders httpHeaders) throws ReceivingException {

    // Get the container details
    Container container = getContainer(trackingId);
    validateRequestForGls(container.getContainerItems().get(0));
    validateContainerIfAlreadyOffHold(container);
    // Set status from wip to available
    container.setInventoryStatus(AVAILABLE.toString());
    container.setLastChangedUser(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    container.setLastChangedTs(new Date());

    containerRepository.save(container);

    // Notify Inventory as receivedQty
    inventoryService.palletOffHold(trackingId, httpHeaders);

    // Publish putaway message as AVAILABLE
    gdcPutawayPublisher.publishMessage(container, PUTAWAY_UPDATE_ACTION, httpHeaders);

    String sourceLocationInInventory =
        inventoryService.getContainerLocation(trackingId, httpHeaders);

    // Prepare move : from previous source location to new destination location
    // Get the destination for staging area
    SlottingPalletBuildResponse slottingResponse =
        slottingService.getDivertLocation(container, httpHeaders, SLOTTING_PALLET_OFF_HOLD);
    final String toLocation_Slotting = slottingResponse.getDivertLocation();
    LinkedTreeMap<String, Object> newMove =
        createNewMove(httpHeaders, container, toLocation_Slotting);
    movePublisher.publishMove(
        InstructionUtils.getMoveQuantity(container),
        sourceLocationInInventory,
        httpHeaders,
        newMove,
        MoveEvent.REPLACE.getMoveEvent());

    // Publish receipt update to SCT
    receiptPublisher.publishReceiptUpdate(trackingId, httpHeaders, Boolean.TRUE);
  }

  public void validateContainerIfAlreadyOffHold(Container container) throws ReceivingException {
    // if trying to off-hold when lpn NOT really on-hold i.e already offHold
    if (!InventoryStatus.WORK_IN_PROGRESS.toString().equals(container.getInventoryStatus())) {
      throw new ReceivingException(
          ReceivingException.LABEL_ALREADY_OFF_HOLD_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.LABEL_ALREADY_OFF_HOLD_ERROR_CODE);
    }
  }

  public Container getContainer(String trackingId) throws ReceivingException {
    Container container = containerPersisterService.getContainerDetails(trackingId);
    if (container == null || container.getContainerItems().isEmpty()) {
      throw new ReceivingException(
          ReceivingException.MATCHING_CONTAINER_NOT_FOUND,
          HttpStatus.BAD_REQUEST,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE);
    }
    return container;
  }

  private LinkedTreeMap<String, Object> createNewMove(
      HttpHeaders httpHeaders, Container container, String toLocation) {
    LinkedTreeMap<String, Object> newMove = new LinkedTreeMap<>();
    newMove.put(ReceivingConstants.MOVE_CONTAINER_TAG, container.getTrackingId());
    newMove.put(ReceivingConstants.MOVE_TO_LOCATION, toLocation);
    newMove.put(
        ReceivingConstants.MOVE_CORRELATION_ID,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    newMove.put(ReceivingConstants.MOVE_SEQUENCE_NBR, 1);
    newMove.put("lastChangedBy", "RECEIVING");
    newMove.put("lastChangedOn", new Date());
    return newMove;
  }

  /**
   * Partial VTR the container, created for Witron initiated flow via Inventory)
   *
   * @param trackingId
   * @param headers
   * @param invNewQuantity
   * @param adjustmentToQty_INV will be -ve for VTR representing Quantity being cancelled
   * @param adjustmentToQtyUOM_INV 0 or more reflecting the current INV quantity
   * @throws ReceivingException
   */
  @ExceptionCounted(
      name = "processVTRExceptionCount",
      level1 = "uwms-receiving",
      level2 = "witronContainerService",
      level3 = "processVTR")
  public void processVTR(
      String trackingId,
      HttpHeaders headers,
      int adjustmentToQty_INV,
      String adjustmentToQtyUOM_INV,
      int invNewQuantity)
      throws ReceivingException {
    Container container_RCV = containerAdjustmentValidator.getValidContainer(trackingId);
    Container container_RTU = SerializationUtils.clone(container_RCV);
    final ContainerItem ci =
        container_RCV.getContainerItems().get(0); // witron has only 1 container_RCV item
    final Integer adjustmentToQuantityInEaches_INV =
        conversionToEaches(
            adjustmentToQty_INV, adjustmentToQtyUOM_INV, ci.getVnpkQty(), ci.getWhpkQty());
    final int rcvNewQuantity = ci.getQuantity() + adjustmentToQuantityInEaches_INV;
    final Long deliveryNumber = container_RCV.getDeliveryNumber();
    final String userId = headers.get(USER_ID_HEADER_KEY).get(0);
    String rtuAction;

    log.info(
        "processing VTR trackingId={}, invNewQuantity={}, rcvNewQuantity={}",
        trackingId,
        invNewQuantity,
        rcvNewQuantity);
    if (invNewQuantity != 0) {
      container_RCV = adjustContainerByQty(false, container_RCV, rcvNewQuantity);
      container_RTU = adjustContainerByQty(false, container_RTU, invNewQuantity);

      // Updating container_RCV Status and saving receipts
      final List<Receipt> receipts =
          getReceipts(userId, ci, adjustmentToQuantityInEaches_INV, deliveryNumber);
      containerPersisterService.updateContainerContainerItemReceipt(
          container_RCV, ci, userId, receipts);

      // putaway action
      rtuAction = PUTAWAY_UPDATE_ACTION;
    } else {
      // Full VTR : Updating container_RCV Status and saving receipts
      final List<Receipt> receipts =
          receiptHelper.getReceipts(userId, ci, adjustmentToQuantityInEaches_INV, deliveryNumber);
      containerPersisterService.updateContainerStatusAndSaveReceipts(
          trackingId, STATUS_BACKOUT, userId, receipts);

      // putaway action
      rtuAction = PUTAWAY_DELETE_ACTION;
    }
    // notify putaway
    gdcPutawayPublisher.publishMessage(container_RTU, rtuAction, headers);

    // Publish receipt update to SCT
    receiptPublisher.publishReceiptUpdate(trackingId, headers, Boolean.TRUE);

    try {
      // notify GDM delivery status with list of received currentQuantityInEaches
      String deliveryResponse_gdm =
          deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, headers);
      String deliveryStatus_gdm =
          parser
              .parse(deliveryResponse_gdm)
              .getAsJsonObject()
              .get(GDM_DELIVERY_STATUS)
              .getAsString();
      if (!WRK.name().equalsIgnoreCase(deliveryStatus_gdm)) {
        deliveryStatusPublisher.publishDeliveryStatus(
            deliveryNumber,
            COMPLETE.name(),
            receiptService.getReceivedQtySummaryByPOForDelivery(deliveryNumber, EACHES),
            ReceivingUtils.getForwardablHeader(headers));
      }
    } catch (Exception e) {
      log.error(
          "error processing for gdm, TrackingId={}, error={}",
          container_RCV.getTrackingId(),
          ExceptionUtils.getStackTrace(e));
    }

    log.info("VTR success for TrackingId={}", container_RCV.getTrackingId());
  }

  /**
   * vtrQuantityInEaches for VTR it will be -ve
   *
   * @param userId
   * @param ci
   * @param vtrQuantityInEaches
   * @param deliveryNumber
   * @return
   */
  private List<Receipt> getReceipts(
      String userId, ContainerItem ci, Integer vtrQuantityInEaches, Long deliveryNumber) {
    Receipt receipt = createNewReceipt(userId, ci, vtrQuantityInEaches, deliveryNumber);
    final List<Receipt> receipts = new ArrayList<>(1);
    receipts.add(receipt);
    return receipts;
  }

  private Receipt createNewReceipt(
      String userId, ContainerItem ci, Integer vtrQuantityInEaches, Long deliveryNumber) {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setPurchaseReferenceNumber(ci.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(ci.getPurchaseReferenceLineNumber());
    receipt.setVnpkQty(ci.getVnpkQty());
    receipt.setWhpkQty(ci.getWhpkQty());
    receipt.setEachQty(vtrQuantityInEaches);
    // if partial should be diff else if full vtr should be full -ve as current
    final int receiptQuantityInVNKP =
        ReceivingUtils.conversionToVendorPack(
            vtrQuantityInEaches, EACHES, ci.getVnpkQty(), ci.getWhpkQty());
    receipt.setQuantity(receiptQuantityInVNKP);
    receipt.setQuantityUom(VNPK);
    receipt.setCreateUserId(userId);
    receipt.setCreateTs(new Date());
    return receipt;
  }

  public void validateRequestForGls(final ContainerItem containerItem) throws ReceivingException {
    final boolean isOneAtlas = gdcFlagReader.isDCOneAtlasEnabled();
    if (!isOneAtlas && gdcFlagReader.isManualGdcEnabled() // isFullGls
        || (isOneAtlas && !isAtlasConvertedItem(containerItem))) {
      throw new ReceivingException(
          LABEL_ON_HOLD_REQUEST_IN_GLS_INSTEAD_OF_ATLAS, BAD_REQUEST, INVALID_REQUEST);
    }
  }
}
