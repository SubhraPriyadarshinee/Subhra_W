package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_ONE_ATLAS_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_RDS_RECEIPT_ENABLED;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.CancelContainerRequest;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.SwapContainerRequest;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.CancelContainerProcessor;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class RxCancelContainerProcessor implements CancelContainerProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RxCancelContainerProcessor.class);

  @ManagedConfiguration AppConfig appConfig;
  @ManagedConfiguration RxManagedConfig rxManagedConfig;
  @Autowired private ContainerItemRepository containerItemRepository;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private RxContainerAdjustmentValidator rxContainerAdjustmentValidator;
  @Autowired private NimRdsServiceImpl nimRdsServiceImpl;
  @Autowired private RxSlottingServiceImpl rxSlottingServiceImpl;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private RxCancelContainerHelper rxCancelContainerHelper;
  @Autowired private RxReceiptsBuilder rxReceiptsBuilder;
  @Autowired private InventoryRestApiClient inventoryRestApiClient;
  @Autowired private SlottingRestApiClient slottingRestApiClient;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * @param cancelContainerRequest
   * @param httpHeaders
   * @return List<CancelContainerResponse>
   * @throws ReceivingException
   */
  public List<CancelContainerResponse> cancelContainers(
      CancelContainerRequest cancelContainerRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.info(
        "Enter txCancelContainers() with trackingIds :{}", cancelContainerRequest.getTrackingIds());

    List<CancelContainerResponse> responseList = new ArrayList<>();
    for (String trackingId : cancelContainerRequest.getTrackingIds()) {
      CancelContainerResponse response = cancelContainer(trackingId, httpHeaders);
      if (response != null) {
        responseList.add(response);
      }
    }

    LOGGER.info("Exit txCancelContainers() with list of failure responses :{}", responseList);
    return responseList;
  }

  @Override
  public List<CancelContainerResponse> swapContainers(
      List<SwapContainerRequest> swapContainerRequest, HttpHeaders httpHeaders) {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  /**
   * @param trackingId
   * @param httpHeaders
   * @return CancelContainerResponse
   * @throws ReceivingException
   */
  private CancelContainerResponse cancelContainer(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException {
    CancelContainerResponse response = null;
    Boolean isDCOneAtlasEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);

    Boolean enableRDSReceipt =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_DC_RDS_RECEIPT_ENABLED, false);

    Container palletContainer =
        containerPersisterService.getContainerWithChildContainersExcludingChildContents(trackingId);

    if (Objects.isNull(palletContainer)) {
      return new CancelContainerResponse(
          trackingId,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE,
          ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
    }
    response =
        rxContainerAdjustmentValidator.validateContainerForAdjustment(palletContainer, httpHeaders);
    if (Objects.nonNull(response)) {
      return response;
    }

    List<Container> modifiedContainers = new ArrayList<>();
    List<ContainerItem> modifiedContainerItems = new ArrayList<>();
    long timestamp = System.currentTimeMillis();
    palletContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    modifiedContainers.add(palletContainer);
    for (Container caseContainer : palletContainer.getChildContainers()) {
      Set<Container> wareHousePacks =
          containerPersisterService.getContainerDetailsByParentTrackingId(
              caseContainer.getTrackingId());
      if (CollectionUtils.isNotEmpty(wareHousePacks)) {
        for (Container eachContainer : wareHousePacks) {
          enrichTimestampToTrackingIds(eachContainer, timestamp, true);
          eachContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

          modifiedContainers.add(eachContainer);
          modifiedContainerItems.addAll(eachContainer.getContainerItems());
        }
      }

      caseContainer.setContainerItems(
          containerItemRepository.findByTrackingId(caseContainer.getTrackingId()));
      enrichTimestampToTrackingIds(caseContainer, timestamp, false);
      caseContainer.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

      modifiedContainers.add(caseContainer);
      modifiedContainerItems.addAll(caseContainer.getContainerItems());
    }

    // Check Inventory Integration , then call in Sync (Not Outbox)
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RxConstants.ENABLE_INV_LABEL_BACKOUT, false)
        || isDCOneAtlasEnabled) {
      inventoryRestApiClient.notifyVtrToInventory(trackingId, httpHeaders);
    }

    if (!isDCOneAtlasEnabled) {
      nimRdsServiceImpl.quantityChange(0, trackingId, httpHeaders);
    } else {
      slottingRestApiClient.cancelPalletMoves(trackingId, httpHeaders);
      if (enableRDSReceipt) {
        nimRdsServiceImpl.quantityChange(0, trackingId, httpHeaders);
      }
    }

    Instruction instruction =
        instructionPersisterService.getInstructionById(palletContainer.getInstructionId());
    if (appConfig.isSlotUnlockingEnabled()) {
      LinkedTreeMap<String, Object> moveMap = instruction.getMove();
      Object slotId = moveMap.get(ReceivingConstants.MOVE_TO_LOCATION);
      List<ContainerItem> containerItems = palletContainer.getContainerItems();
      if (Objects.nonNull(slotId)
          && StringUtils.isNotBlank(slotId.toString())
          && CollectionUtils.isNotEmpty(containerItems)
          && Objects.nonNull(containerItems.get(0).getItemNumber())) {

        rxSlottingServiceImpl.freeSlot(
            containerItems.get(0).getItemNumber(), slotId.toString(), httpHeaders);
      }
    }

    if (rxManagedConfig.isRollbackReceiptsByShipment()) {
      List<Receipt> rollbackReceiptsWithShipment;
      if (CollectionUtils.isNotEmpty(modifiedContainers) && modifiedContainers.size() > 1) {
        HashMap<String, Receipt> receiptsByShipment =
            new HashMap<>(); // D40 items will only have 1 container.
        rollbackReceiptsWithShipment =
            rxReceiptsBuilder.constructRollbackReceiptsWithShipment(
                modifiedContainers, receiptsByShipment, instruction);
      } else {
        rollbackReceiptsWithShipment = Arrays.asList(RxUtils.resetRecieptQty(palletContainer));
      }
      rxCancelContainerHelper.persistCancelledContainers(
          modifiedContainers, modifiedContainerItems, rollbackReceiptsWithShipment);
    } else {
      rxCancelContainerHelper.persistCancelledContainers(
          modifiedContainers, modifiedContainerItems, RxUtils.resetRecieptQty(palletContainer));
    }
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RxConstants.BACKOUT_PUBLISHER_FLAG, false)) {
      rxCancelContainerHelper.publishCancelledContainers(modifiedContainers);
    }

    return response;
  }

  private void enrichTimestampToTrackingIds(Container container, long timestamp, boolean isEaches) {
    if (isEaches) {
      String parentTrackingId = container.getParentTrackingId();
      container.setParentTrackingId(parentTrackingId + "_" + timestamp);
    }
    container.setTrackingId(container.getTrackingId() + "_" + timestamp);
    List<ContainerItem> containerItems = container.getContainerItems();
    if (CollectionUtils.isNotEmpty(containerItems)) {
      containerItems.get(0).setTrackingId(containerItems.get(0).getTrackingId() + "_" + timestamp);
    }
  }
}
