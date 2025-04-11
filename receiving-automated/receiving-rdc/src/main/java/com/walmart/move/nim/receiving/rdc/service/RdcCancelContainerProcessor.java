package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR_REASON_CODE;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.CancelContainerRequest;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.InventoryExceptionRequest;
import com.walmart.move.nim.receiving.core.model.SwapContainerRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.InventoryTransformer;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.repositories.OutboxEvent;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

public class RdcCancelContainerProcessor implements CancelContainerProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcCancelContainerProcessor.class);

  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private LabelDataService labelDataService;
  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private InventoryRestApiClient inventoryRestApiClient;
  @Autowired private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private LocationService locationService;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private ReceiptService receiptService;
  @Autowired private Gson gson;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private RdcLabelGenerationService rdcLabelGenerationService;
  @Autowired private InventoryTransformer inventoryTransformer;
  @Autowired private InstructionPersisterService instructionPersisterService;

  @Autowired(required = false)
  private EIService eiService;

  @Autowired private RdcReceivingUtils rdcReceivingUtils;

  /**
   * @param swapContainerRequest
   * @return
   * @throws ReceivingException
   */
  @Override
  public List<CancelContainerResponse> swapContainers(
      List<SwapContainerRequest> swapContainerRequest, HttpHeaders httpHeaders) {
    List<CancelContainerResponse> responseList = new ArrayList<>();
    for (SwapContainerRequest swapContainer : swapContainerRequest) {
      CancelContainerResponse response =
          swapContainer(swapContainer.getSourceLpn(), swapContainer.getTargetLpn(), httpHeaders);
      if (Objects.nonNull(response)) {
        responseList.add(response);
      }
    }
    return responseList;
  }

  private CancelContainerResponse swapContainer(
      String sourceLpn, String targetLpn, HttpHeaders httpHeaders) {
    CancelContainerResponse cancelContainerResponse = null;
    LabelData labelData = labelDataService.findByTrackingId(sourceLpn);
    if (Objects.isNull(labelData)) {
      return new CancelContainerResponse(
          sourceLpn,
          ExceptionCodes.SOURCE_CONTAINER_NOT_FOUND,
          String.format(ReceivingException.SOURCE_CONTAINER_NOT_FOUND, sourceLpn));
    }
    if (labelData.getStatus().equals(LabelInstructionStatus.CANCELLED.name())) {
      labelData.setTrackingId(targetLpn);
      labelData.getAllocation().getContainer().setTrackingId(targetLpn);
      labelData.setStatus(ReceivingConstants.AVAILABLE);
      labelDataService.save(labelData);
      rdcLabelGenerationService.publishNewLabelToHawkeye(labelData, httpHeaders);
    } else {
      LOGGER.error(
          "Source container:{} status is not eligible for the swap container request", sourceLpn);
      cancelContainerResponse =
          new CancelContainerResponse(
              sourceLpn,
              ExceptionCodes.SOURCE_CONTAINER_NOT_ELIGIBLE,
              String.format(ReceivingException.SOURCE_CONTAINER_NOT_ELIGIBLE, sourceLpn));
    }
    return cancelContainerResponse;
  }

  @Override
  @Transactional(
      rollbackFor = {
        ReceivingException.class,
        ReceivingBadDataException.class,
        ReceivingInternalException.class
      })
  public List<CancelContainerResponse> cancelContainers(
      CancelContainerRequest cancelContainerRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.info(
        "Entering cancelContainers() with trackingIds :{}",
        cancelContainerRequest.getTrackingIds());

    List<CancelContainerResponse> responseList = new ArrayList<>();
    for (String trackingId : cancelContainerRequest.getTrackingIds()) {
      CancelContainerResponse response = cancelContainer(trackingId, httpHeaders);
      if (response != null) {
        responseList.add(response);
      }
    }

    LOGGER.info("Exit cancelContainers() with list of failure responses :{}", responseList);
    return responseList;
  }

  /**
   * This method validates whether the given container is eligible for cancellation or not. If all
   * the validations are satisfied, makes a call to to RDS and inventory with the corrected
   * quantity. Adjust quantity in receipts, container status will be set to BACKOUT in container
   * table
   *
   * @param trackingId
   * @param httpHeaders
   * @return CancelContainerResponse
   * @throws ReceivingException
   */
  private CancelContainerResponse cancelContainer(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException {
    CancelContainerResponse cancelContainerResponse = null;
    Container container =
        containerPersisterService.getContainerWithChildContainersExcludingChildContents(trackingId);
    if (Objects.isNull(container)) {
      return new CancelContainerResponse(
          trackingId,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
    }

    if (Objects.nonNull(container.getParentTrackingId())) {
      return new CancelContainerResponse(
          trackingId,
          ReceivingException.CONTAINER_WITH_PARENT_ERROR_CODE,
          String.format(
              ReceivingException.CONTAINER_WITH_PARENT_ERROR_MSG, container.getParentTrackingId()));
    }
    cancelContainerResponse =
        containerAdjustmentHelper.validateContainerForAdjustment(container, httpHeaders);
    cancelContainerResponse =
        validateContainerAdjustmentForDaAndDSDCLabel(container, cancelContainerResponse);
    if (Objects.isNull(cancelContainerResponse)) {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
              false)
          && ContainerUtils.isAtlasConvertedItem(container.getContainerItems().get(0))) {
        LOGGER.info(
            "Inventory integration enabled and item is atlas converted, going to notify VTR adjustments to inventory for lpn:{}",
            trackingId);
        InventoryExceptionRequest inventoryExceptionRequest = new InventoryExceptionRequest();
        inventoryExceptionRequest.setTrackingId(trackingId);
        inventoryExceptionRequest.setComment(ReceivingConstants.VTR_COMMENT);
        Integer vtrReasonCode =
            Objects.nonNull(rdcManagedConfig.getVtrReasonCode())
                ? rdcManagedConfig.getVtrReasonCode()
                : VTR_REASON_CODE;
        inventoryExceptionRequest.setReasonCode(String.valueOf(vtrReasonCode));
        inventoryRestApiClient.notifyBackoutAdjustment(inventoryExceptionRequest, httpHeaders);
        boolean outBoxEnabled = isOutBoxEnabled();
        if (outBoxEnabled) {
          Collection<OutboxEvent> outboxEvents =
              rdcReceivingUtils.buildOutboxEventsForCancelContainers(
                  container, httpHeaders, getLabelAction(container));
          cancelContainerUpdates(container, outboxEvents);
        } else {
          cancelContainerWithoutOutbox(container, httpHeaders);
        }
      } else {
        String freightType = container.getContainerItems().get(0).getInboundChannelMethod();
        if (isDaContainer(container)) {
          if (container.getContainerType().equals(ContainerType.PALLET.getText())) {
            LOGGER.info("Invoking label backout for DA Pallet label: {}", trackingId);
            nimRdsService.quantityChange(0, trackingId, httpHeaders);
          } else {
            LOGGER.info("Invoking label backout for DA Case label: {}", trackingId);
            nimRdsService.backoutDALabels(Arrays.asList(trackingId), httpHeaders);
          }
        } else if (isSstkContainer(container)) {
          LOGGER.info("Invoking label backout for SSTK label: {}", trackingId);
          nimRdsService.quantityChange(0, trackingId, httpHeaders);
        } else {
          LOGGER.error(
              "Container has channel method either NULL or other than DA/SSTK, so no action will be performed");
          return new CancelContainerResponse(
              trackingId,
              ReceivingException.INVALID_CHANNEL_METHOD_FOR_LABEL_BACKOUT_CODE,
              String.format(
                  ReceivingException.INVALID_CHANNEL_METHOD_FOR_LABEL_BACKOUT_MSG, freightType));
        }

        // update container status
        updateContainerStatus(container, null, null, null);

        // publish container updates to WFT
        publishCancelContainerUpdatesToWft(container, getLabelAction(container), httpHeaders);
      }
    }
    return cancelContainerResponse;
  }

  /**
   * This method validates the received qty for the container. If the container is received less
   * than a vendor pack then the container is not eligible for any adjustments. This is applicable
   * for DA Atlas Break Pack items & we do not allow adjustments for DSDC freights.
   *
   * @param container
   * @param cancelContainerResponse
   */
  private CancelContainerResponse validateContainerAdjustmentForDaAndDSDCLabel(
      Container container, CancelContainerResponse cancelContainerResponse) {
    if (Objects.isNull(cancelContainerResponse)
        && !CollectionUtils.isEmpty(container.getContainerItems())) {
      ContainerItem containerItem = container.getContainerItems().get(0);
      if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
              container.getContainerItems().get(0).getInboundChannelMethod())
          && ContainerUtils.isAtlasConvertedItem(containerItem)
          && ObjectUtils.allNotNull(
              containerItem.getQuantity(),
              containerItem.getVnpkQty(),
              containerItem.getWhpkQty())) {
        if (!isTimeAllowedForCancellingDAContainerItem(container)) {
          LOGGER.error(
              "Case can not have a VTR completed for :lpn{} because it is older than {} hours to allow this process."
                  + " Please adjust if needed using another process.",
              container.getTrackingId(),
              RdcConstants.MAX_ALLOWED_TIME_FOR_CONTAINER_BACKOUT);
          return new CancelContainerResponse(
              container.getTrackingId(),
              ExceptionCodes.MAX_ALLOWED_TIME_FOR_CONTAINER_BACKOUT_EXPIRED,
              ReceivingException.MAX_ALLOWED_TIME_FOR_CONTAINER_BACKOUT_EXPIRED);
        }
      } else if (ReceivingConstants.DSDC_CHANNEL_METHODS_FOR_RDC.contains(
          containerItem.getInboundChannelMethod())) {
        cancelContainerResponse =
            new CancelContainerResponse(
                container.getTrackingId(),
                ExceptionCodes.CONTAINER_ADJUSTMENT_NOT_ALLOWED_FOR_DSDC_CONTAINER,
                ReceivingException.CONTAINER_ADJUSTMENT_NOT_ALLOWED_FOR_DSDC_CONTAINER);
      }
    }
    return cancelContainerResponse;
  }

  private boolean isTimeAllowedForCancellingDAContainerItem(Container container) {
    boolean isTimeAllowedForDeletion = true;
    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    JsonElement maxAllowedHours =
        tenantSpecificConfigReader.getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.MAX_ALLOWED_HOURS_FOR_CONTAINER_BACKOUT);
    int maxAllowedHoursForBackout =
        Objects.nonNull(maxAllowedHours)
            ? maxAllowedHours.getAsInt()
            : RdcConstants.MAX_ALLOWED_TIME_FOR_CONTAINER_BACKOUT;
    ZonedDateTime containerCreatedTs =
        ReceivingUtils.convertUTCToZoneDateTime(container.getCreateTs(), dcTimeZone);
    ZonedDateTime maxAllowedTimeForCancellingContainer =
        containerCreatedTs.plusHours(maxAllowedHoursForBackout);

    ZonedDateTime currentTime = ReceivingUtils.getDCDateTime(dcTimeZone);

    if (currentTime.isAfter(maxAllowedTimeForCancellingContainer)) {
      isTimeAllowedForDeletion = false;
    }
    return isTimeAllowedForDeletion;
  }

  /**
   * This method returns the label action which is needed for WFT
   *
   * @param container
   * @return
   */
  private LabelAction getLabelAction(Container container) {
    String freightType = container.getContainerItems().get(0).getInboundChannelMethod();
    LabelAction labelAction = null;
    if (StringUtils.isNotBlank(freightType)
        && ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(freightType)) {
      if (container.getContainerType().equals(ContainerType.PALLET.getText())) {
        labelAction = LabelAction.DA_CORRECTION;
      } else {
        labelAction = LabelAction.DA_BACKOUT;
      }
    } else if (StringUtils.isNotBlank(freightType)
        && ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(freightType)) {
      labelAction = LabelAction.CORRECTION;
    }
    return labelAction;
  }

  /**
   * @param container
   * @param labelAction
   * @param httpHeaders
   */
  private void publishCancelContainerUpdatesToWft(
      Container container, LabelAction labelAction, HttpHeaders httpHeaders) {
    final Integer currentContainerQtyInVnpk =
        ReceivingUtils.conversionToVendorPack(
            container.getContainerItems().get(0).getQuantity(),
            ReceivingConstants.Uom.EACHES,
            container.getContainerItems().get(0).getVnpkQty(),
            container.getContainerItems().get(0).getWhpkQty());

    rdcInstructionUtils.publishInstructionToWft(
        container, currentContainerQtyInVnpk, 0, labelAction, httpHeaders);
  }

  private boolean isDaContainer(Container container) {
    String freightType = container.getContainerItems().get(0).getInboundChannelMethod();
    return StringUtils.isNotBlank(freightType)
        && ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(freightType);
  }

  private boolean isSstkContainer(Container container) {
    String freightType = container.getContainerItems().get(0).getInboundChannelMethod();
    return StringUtils.isNotBlank(freightType)
        && ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(freightType);
  }

  /**
   * Returns outbox flag
   *
   * @return
   */
  private boolean isOutBoxEnabled() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_CANCEL_CONTAINER_OUTBOX_PATTERN_ENABLED,
        false);
  }

  /**
   * Cancel container DB updates. In case of DA Atlas items we need to update the lpn/label status
   * as CANCELLED. If CP/BP container exists in the Label data then we will cancel the container. If
   * we have any parent container created by receiving (Pallet Pull / DA slotting / Non Con RTS PUT)
   * those parent LPN won't exist in Label data as receiving generates the parent LPNs, we need to
   * look up the child container LPNS and cancel them in label data table.
   *
   * @param container
   * @param outboxEvents
   */
  private void cancelContainerUpdates(Container container, Collection<OutboxEvent> outboxEvents)
      throws ReceivingException {
    // update receipt table with adjusted receipts
    ContainerItem containerItem = container.getContainerItems().get(0);
    boolean isAtlasItem = ContainerUtils.isAtlasConvertedItem(containerItem);
    Receipt adjustedReceipt;
    boolean isDAItem =
        ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
            container.getContainerItems().get(0).getInboundChannelMethod());
    boolean isBreakPackItem =
        RdcUtils.isBreakPackItem(containerItem.getVnpkQty(), containerItem.getWhpkQty());
    boolean isContainerReceivedByWhpkQty =
        isBreakPackItem
            && ReceivingUtils.conversionToVendorPack(
                    containerItem.getQuantity(),
                    ReceivingConstants.Uom.EACHES,
                    containerItem.getVnpkQty(),
                    containerItem.getWhpkQty())
                < 1;
    if (isDAItem && isAtlasItem && isContainerReceivedByWhpkQty) {
      adjustedReceipt = getReceiptsForWhpk(container);
    } else {
      adjustedReceipt = containerAdjustmentHelper.adjustReceipts(container);
    }
    // update label data status as cancelled only for DA containers
    List<LabelData> labelDataList = new ArrayList<>();
    if (isDaContainer(container)) {
      labelDataList =
          labelDataService.findByTrackingIdIn(Collections.singletonList(container.getTrackingId()));
      if (CollectionUtils.isEmpty(labelDataList)
          && CollectionUtils.isNotEmpty(container.getChildContainers())) {
        List<String> childLpns =
            container
                .getChildContainers()
                .stream()
                .map(Container::getTrackingId)
                .collect(Collectors.toList());
        labelDataList = labelDataService.findByTrackingIdIn(childLpns);
      }
      labelDataList.forEach(
          labelData -> labelData.setStatus(LabelInstructionStatus.CANCELLED.name()));
    }
    updateContainerStatus(container, adjustedReceipt, labelDataList, outboxEvents);
  }

  /**
   * This method updates the container status as Backout and update other tables
   *
   * @param container
   * @param receipt
   * @param labelDataList
   * @param outboxEvents
   * @throws ReceivingException
   */
  private void updateContainerStatus(
      Container container,
      Receipt receipt,
      List<LabelData> labelDataList,
      Collection<OutboxEvent> outboxEvents)
      throws ReceivingException {
    // update container and container item table
    container.getContainerItems().get(0).setQuantity(0);
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    Instruction instruction =
        instructionPersisterService.getInstructionById(container.getInstructionId());
    instruction.setReceivedQuantity(ReceivingConstants.ZERO_QTY);
    rdcReceivingUtils.postCancelContainersUpdates(
        receipt, labelDataList, container, instruction, outboxEvents);
  }

  /**
   * Cancel container without outbox
   *
   * @param container
   * @param httpHeaders
   */
  @SneakyThrows
  private void cancelContainerWithoutOutbox(Container container, HttpHeaders httpHeaders) {
    String trackingId = container.getTrackingId();
    // publish putaway cancellation message to hawkeye
    boolean isSymPutawayEligible =
        SymboticUtils.isValidForSymPutaway(
            container.getContainerItems().get(0).getAsrsAlignment(),
            appConfig.getValidSymAsrsAlignmentValues(),
            container.getContainerItems().get(0).getSlotType());

    if (isSymPutawayEligible) {
      symboticPutawayPublishHelper.publishSymPutawayUpdateOrDeleteMessage(
          container.getTrackingId(),
          container.getContainerItems().get(0),
          ReceivingConstants.PUTAWAY_DELETE_ACTION,
          ReceivingConstants.ZERO_QTY,
          httpHeaders);
    }

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RdcConstants.IS_EI_INTEGRATION_ENABLED, false)) {
      Container consolidatedContainer =
          containerPersisterService.getConsolidatedContainerForPublish(trackingId);
      if (isDaContainer(container)) {
        TenantContext.get().setPublishEICallStart(System.currentTimeMillis());
        rdcContainerUtils.publishContainerToEI(consolidatedContainer, ReceivingConstants.DC_VOID);
        TenantContext.get().setPublishEICallEnd(System.currentTimeMillis());
      }
    }

    // DB updates
    cancelContainerUpdates(container, null);

    // publish container updates to WFT
    publishCancelContainerUpdatesToWft(container, getLabelAction(container), httpHeaders);
  }

  public void publishInvDeleteEventsToEI(Container container, String... transformTypeInput)
      throws ReceivingException {
    try {
      // Publish to EI
      rdcContainerUtils.publishContainerToEI(container, ReceivingConstants.DC_VOID);
      // Commenting out below DB updates as are not needed by offline flow
      // cancelContainerUpdates(container, null);
      LOGGER.error(
          "Suppressing DB Updates for XDK Containers in publishInvDeleteEventsToEI:{}",
          container.getTrackingId());
    } catch (Exception exception) {
      LOGGER.error(
          "Exception occurred in publishInvDeleteEventsToEI with container : {}",
          container.getTrackingId());
    }
  }

  /**
   * Total Containers Count - Already Cancelled Count
   *
   * <p>
   *
   * <p>EX:
   *
   * <p>First Container Cancel Request = 4 - 0 Second Container Cancel Request = 4 - 1 Third
   * Container Cancel Request = 4 - 2 Fourth Container Cancel Request = 4 - 3 Last Container Cancel
   * Request
   *
   * @param container
   * @return
   */
  private Receipt getReceiptsForWhpk(Container container) {
    ContainerItem containerItem = container.getContainerItems().get(0);
    Receipt adjustedReceipt = null;
    List<Container> containers =
        containerPersisterService.getContainersByInstructionId(container.getInstructionId());
    long cancelledCount =
        containers
            .stream()
            .filter(
                cntr ->
                    ReceivingConstants.STATUS_BACKOUT.equalsIgnoreCase(cntr.getContainerStatus()))
            .count();
    boolean isLastContainerCancel = (containers.size() - cancelledCount) == 1;
    LOGGER.info(
        "Cancel container, tracking id={}, total containers count={}, isLastContainerCancel={}",
        container.getInstructionId(),
        containers.size(),
        isLastContainerCancel);
    if (isLastContainerCancel) {
      int aggregatedQuantity =
          containers.stream().mapToInt(cntr -> cntr.getContainerItems().get(0).getWhpkQty()).sum();
      int finalVnpkQty =
          ReceivingUtils.conversionToVendorPack(
              aggregatedQuantity,
              containerItem.getQuantityUOM(),
              containerItem.getVnpkQty(),
              containerItem.getWhpkQty());
      LOGGER.info("Cancel container, breakPack convey picks, finalVnpkQty={}", finalVnpkQty);
      if (finalVnpkQty > 0) {
        adjustedReceipt =
            containerAdjustmentHelper.adjustReceipts(container, finalVnpkQty, aggregatedQuantity);
      }
    }
    return adjustedReceipt;
  }
}
