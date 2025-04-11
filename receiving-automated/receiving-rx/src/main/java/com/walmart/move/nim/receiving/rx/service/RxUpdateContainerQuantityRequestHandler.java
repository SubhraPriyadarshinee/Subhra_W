package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_MSG;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_ONE_ATLAS_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_RDS_RECEIPT_ENABLED;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingInstructionUpdateRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.UpdateContainerQuantityRequestHandler;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class RxUpdateContainerQuantityRequestHandler
    implements UpdateContainerQuantityRequestHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RxUpdateContainerQuantityRequestHandler.class);

  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Autowired private ContainerService containerService;
  @Autowired private NimRdsServiceImpl nimRdsService;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private Gson gson;
  @Autowired private RxReceivingCorrectionPrintJobBuilder rxReceivingCorrectionPrintJobBuilder;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private InventoryRestApiClient inventoryRestApiClient;
  @Autowired private SlottingRestApiClient slottingRestApiClient;

  @Override
  public ContainerUpdateResponse updateQuantityByTrackingId(
      String trackingId, ContainerUpdateRequest containerUpdateRequest, HttpHeaders httpHeaders) {
    Integer newQuantityUI = containerUpdateRequest.getAdjustQuantity();
    String newQuantityUomUI =
        StringUtils.defaultIfBlank(
            containerUpdateRequest.getAdjustQuantityUOM(), ReceivingConstants.Uom.VNPK);
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    LOGGER.info(
        "Received updateQuantityByTrackingId request for lpn:{} with adjustQuantity:{} and adjustQuantityUOM:{}",
        trackingId,
        newQuantityUI,
        newQuantityUomUI);

    if (newQuantityUI <= 0) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_PALLET_CORRECTION_QTY,
          ReceivingException.INVALID_PALLET_CORRECTION_QTY);
    }
    Boolean isDCOneAtlasEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);

    Boolean enableRDSReceipt =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_DC_RDS_RECEIPT_ENABLED, false);

    ContainerUpdateResponse containerUpdateResponse = new ContainerUpdateResponse();

    try {
      Container container4mDB = containerService.getContainerByTrackingId(trackingId);
      // Throw Exception if the container is already backed-out
      containerService.isBackoutContainer(trackingId, container4mDB.getContainerStatus());

      Optional<Instruction> instructionOptional =
          instructionRepository.findById(container4mDB.getInstructionId());
      if (instructionOptional.isPresent()) {
        Instruction instruction4mDB = instructionOptional.get();
        // Temporary check to enable pallet correction only for exempt items
        exemptItemCheck(instruction4mDB, container4mDB);

        ContainerItem currentContainerItem = container4mDB.getContainerItems().get(0);
        final Integer newContainerQuantityInEaches =
            ReceivingUtils.conversionToEaches(
                newQuantityUI,
                newQuantityUomUI,
                currentContainerItem.getVnpkQty(),
                currentContainerItem.getWhpkQty());

        if (!isDCOneAtlasEnabled || enableRDSReceipt) {
          // Call RDS service to notify the receiving correction
          nimRdsService.quantityChange(newQuantityUI, trackingId, httpHeaders);
        }
        // Check Inventory Integration , then call in Sync (Not Outbox)
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                RxConstants.ENABLE_INV_LABEL_BACKOUT,
                false)
            || isDCOneAtlasEnabled) {
          Integer initialQty =
              ReceivingUtils.conversionToUOM(
                  currentContainerItem.getQuantity(),
                  currentContainerItem.getQuantityUOM(),
                  newQuantityUomUI,
                  currentContainerItem.getVnpkQty(),
                  currentContainerItem.getWhpkQty());
          int adjustBy = newQuantityUI - initialQty;
          if (!newQuantityUomUI.equals(ReceivingConstants.Uom.WHPK)) {
            initialQty =
                ReceivingUtils.conversionToWHPK(
                    initialQty,
                    newQuantityUomUI,
                    currentContainerItem.getVnpkQty(),
                    currentContainerItem.getWhpkQty());
            adjustBy =
                ReceivingUtils.conversionToWHPK(
                    adjustBy,
                    newQuantityUomUI,
                    currentContainerItem.getVnpkQty(),
                    currentContainerItem.getWhpkQty());
          }

          inventoryRestApiClient.adjustQuantity(
              cId,
              trackingId,
              adjustBy,
              httpHeaders,
              currentContainerItem,
              initialQty,
              ReceivingConstants.INVENTORY_RECEIVING_CORRECTION_REASON_CODE_28);
        }

        // Update Moves
        SlottingInstructionUpdateRequest slottingInstructionUpdateRequest =
            new SlottingInstructionUpdateRequest(newQuantityUI);
        slottingRestApiClient.adjustMovesQty(
            trackingId, slottingInstructionUpdateRequest, httpHeaders);

        Receipt receipt =
            containerAdjustmentHelper.adjustQuantityInReceipt(
                newQuantityUI, newQuantityUomUI, container4mDB, userId);

        Container adjustedContainer =
            containerAdjustmentHelper.adjustPalletQuantity(
                newContainerQuantityInEaches, container4mDB, userId);

        containerAdjustmentHelper.persistAdjustedReceiptsAndContainer(receipt, adjustedContainer);

        Map<String, Object> printJob =
            rxReceivingCorrectionPrintJobBuilder.getPrintJobForReceivingCorrection(
                newQuantityUI, newQuantityUomUI, instruction4mDB);

        containerUpdateResponse.setContainer(adjustedContainer);
        containerUpdateResponse.setPrintJob(printJob);
      } else {
        throw new ReceivingBadDataException(
            ExceptionCodes.INSTRUCTION_NOT_FOUND_FOR_CONTAINER,
            RxConstants.INSTRUCTION_NOT_FOUND_FOR_CONTAINER);
      }
    } catch (ReceivingBadDataException rbde) {
      LOGGER.error(
          "UpdateQuantityByTrackingId unknown error for lpn: {}, adjustQuantity: {}, errorMsg: {}, StackTrace: {}",
          trackingId,
          newQuantityUI,
          rbde.getMessage(),
          ExceptionUtils.getStackTrace(rbde));

      throw rbde;
    } catch (ReceivingException e) {
      LOGGER.error(
          "UpdateQuantityByTrackingId for lpn={}, adjustQuantity: {}, errorMsg: {}, StackTrace: {}",
          trackingId,
          newQuantityUI,
          e.getMessage(),
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingBadDataException(
          e.getErrorResponse().getErrorCode(), e.getErrorResponse().getErrorMessage().toString());
    } catch (Exception e) {
      LOGGER.error(
          "UpdateQuantityByTrackingId unknown error for lpn: {}, adjustQuantity: {}, errorMsg: {}, StackTrace: {}",
          trackingId,
          newQuantityUI,
          e.getMessage(),
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_DO_PALLET_CORRECTION, ADJUST_PALLET_QUANTITY_ERROR_MSG);
    }
    LOGGER.info(
        "Successfully adjusted pallet quantity of {} for lpn: {}", newQuantityUI, trackingId);
    return containerUpdateResponse;
  }

  private void exemptItemCheck(Instruction instruction4mDB, Container container4mDB) {
    DeliveryDocument deliveryDocument4mDB =
        gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class);
    if (!RxUtils.isDscsaExemptionIndEnabled(
        deliveryDocument4mDB.getDeliveryDocumentLines().get(0), true)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.D38_PALLET_CORRECTION_NOT_ALLWED,
          ReceivingException.D38_PALLET_CORRECTION_NOT_ALLOWED);
    }
  }
}
