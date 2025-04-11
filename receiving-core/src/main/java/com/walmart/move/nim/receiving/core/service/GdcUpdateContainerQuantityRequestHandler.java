package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.conversionToEaches;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardablHeaderWithTenantData;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.hasInductedIntoMech;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isMechContainer;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PUBLISH_TO_WITRON_DISABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.client.gdm.AsyncGdmRestApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.move.AsyncMoveRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.DeliveryEvent;
import com.walmart.move.nim.receiving.core.model.GdmDeliveryHistoryResponse;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.GDC_UPDATE_CONTAINER_QUANTITY_REQUEST_HANDLER)
public class GdcUpdateContainerQuantityRequestHandler
    implements UpdateContainerQuantityRequestHandler {

  private static final Logger log =
      LoggerFactory.getLogger(GdcUpdateContainerQuantityRequestHandler.class);

  @Autowired private ContainerService containerService;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Autowired private MovePublisher movePublisher;
  @Autowired @Lazy private ItemConfigApiClient itemConfig;

  @Autowired
  DefaultUpdateContainerQuantityRequestHandler defaultUpdateContainerQuantityRequestHandler;

  @Autowired AsyncInventoryService asyncInventoryService;
  @Autowired AsyncMoveRestApiClient asyncMoveRestApiClient;
  @Autowired AsyncGdmRestApiClient asyncGdmRestApiClient;

  @Autowired ReceiptService receiptService;
  @Autowired LocationService locationService;

  @Override
  public ContainerUpdateResponse updateQuantityByTrackingId(
      String trackingId, ContainerUpdateRequest containerUpdateRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    // Automation vs Manual Dc, One Atlas, GLS Flags start
    final boolean isManualGdc =
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
    final boolean automatedDc = !isManualGdc;
    boolean isOneAtlas =
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);
    final boolean isFullGls = isManualGdc && !isOneAtlas;
    StringBuilder itemState = new StringBuilder("");
    // One Atlas, GLS Flags end
    defaultUpdateContainerQuantityRequestHandler.validateTrackingId(automatedDc, trackingId);

    Integer newQuantityFromUI = containerUpdateRequest.getAdjustQuantity();
    final Integer printerId = containerUpdateRequest.getPrinterId();
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    final String facilityNum = getFacilityNum().toString();

    ContainerUpdateResponse response = new ContainerUpdateResponse();
    try {
      // pallet/receiving correction using inventory Data(quantity)
      final Integer quantityInVnkp_UI = containerUpdateRequest.getInventoryQuantity();
      final int diffQuantityInVnpk_UI = newQuantityFromUI - quantityInVnkp_UI;
      Container updatedContainer = containerService.getContainerByTrackingId(trackingId);
      final Container initialContainer = SerializationUtils.clone(updatedContainer);
      final Integer initialQtyInEa = initialContainer.getContainerItems().get(0).getQuantity();
      containerService.isBackoutContainer(trackingId, updatedContainer.getContainerStatus());
      final Long deliveryNumber = updatedContainer.getDeliveryNumber();
      ContainerItem updatedCi = containerService.getContainerItem(cId, updatedContainer);
      final Integer purchaseReferenceLineNumber = updatedCi.getPurchaseReferenceLineNumber();
      final String purchaseReferenceNumber = updatedCi.getPurchaseReferenceNumber();
      final Long itemNumber = updatedCi.getItemNumber();

      final Integer quantityInEaches_INV =
          conversionToEaches(
              quantityInVnkp_UI, VNPK, updatedCi.getVnpkQty(), updatedCi.getWhpkQty());
      // is Quantity in sync with RCV and INV
      final boolean isQuantityInSync_RCV_vs_INV =
          updatedCi.getQuantity() - quantityInEaches_INV == 0;

      final Instruction instruction =
          containerService.getInstruction(cId, updatedContainer.getInstructionId());
      final DeliveryDocumentLine deliveryDocumentLine =
          InstructionUtils.getDeliveryDocumentLine(instruction);

      // Get Master receipt
      Receipt masterReceipt =
          receiptService
              .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                  deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);
      // Validated if po closed
      containerService.validatePOClose(
          updatedCi, deliveryNumber, purchaseReferenceNumber, masterReceipt);

      containerService.adjustQuantityValidation(
          deliveryNumber, cId, newQuantityFromUI, deliveryDocumentLine);

      Integer diffQuantityInEaches =
          containerService.adjustContainerItemQuantityAndGetDiff(
              cId, newQuantityFromUI, response, updatedContainer, updatedCi, quantityInVnkp_UI);

      // Validated against Move,Inv,GDM,Location
      if (itemConfig.isOneAtlasConvertedItem(isOneAtlas, itemState, itemNumber, httpHeaders)) {
        validateReceivingCorrection(
            updatedContainer.getContainerMiscInfo(),
            deliveryNumber,
            trackingId,
            containerUpdateRequest,
            httpHeaders);
      }

      // adjust after validation(DcFin closed)
      containerService.adjustQuantityInReceiptUseInventoryData(
          cId,
          newQuantityFromUI,
          updatedContainer,
          updatedCi,
          deliveryDocumentLine.getFreightBillQty(),
          diffQuantityInEaches,
          masterReceipt);

      // create request for FinalizePO with OSDR data for GDM ahead of ext api calls
      FinalizePORequestBody finalizePORequestBody =
          finalizePORequestBodyBuilder.buildFrom(
              deliveryNumber,
              purchaseReferenceNumber,
              getForwardablHeaderWithTenantData(httpHeaders));

      // We want INV fail to rollback RCV as we have logic for qty diff for possible damages
      if (!containerUpdateRequest.isInventoryReceivingCorrection()
          && (automatedDc
              || itemConfig.isOneAtlasConvertedItem(
                  isOneAtlas, itemState, itemNumber, httpHeaders))) {

        containerService.adjustQuantityInInventoryService(
            cId, trackingId, diffQuantityInVnpk_UI, httpHeaders, updatedCi, initialQtyInEa);
      }

      // RTU
      if (!configUtils.getConfiguredFeatureFlag(
          getFacilityNum().toString(), PUBLISH_TO_WITRON_DISABLED, false)) {
        containerService.adjustQuantityInPutawayService(
            cId,
            newQuantityFromUI,
            httpHeaders,
            initialContainer,
            updatedContainer,
            facilityNum,
            quantityInVnkp_UI,
            isQuantityInSync_RCV_vs_INV);
      }

      // GLS http post Receive Correction if isFullGls OR isOneAtlasAndNotConverted
      if (isFullGls
          || itemConfig.isOneAtlasNotConvertedItem(
              isOneAtlas, itemState, itemNumber, httpHeaders)) {
        defaultUpdateContainerQuantityRequestHandler.adjustInGls(
            trackingId, httpHeaders, newQuantityFromUI, quantityInVnkp_UI);
      }

      // post to GDM Finalize PO with OSDR
      containerService.postFinalizePoOsdrToGdm(
          httpHeaders, deliveryNumber, purchaseReferenceNumber, finalizePORequestBody);

      // Publish receipt update to SCT - automatedDc || isOneAtlasAndConverted
      if (automatedDc
          || itemConfig.isOneAtlasConvertedItem(isOneAtlas, itemState, itemNumber, httpHeaders)) {
        receiptPublisher.publishReceiptUpdate(trackingId, httpHeaders, Boolean.TRUE);
      }
      // dcFin publish - isOneAtlasAndNotConverted (eg 6085 ) or if flag enabled
      if (itemConfig.isOneAtlasNotConvertedItem(isOneAtlas, itemState, itemNumber, httpHeaders)
          || configUtils.getConfiguredFeatureFlag(
              String.valueOf(getFacilityNum()), PUBLISH_TO_DCFIN_ADJUSTMENTS_ENABLED, false)) {
        defaultUpdateContainerQuantityRequestHandler.notifyReceivingCorrectionToDcFin(
            updatedContainer, diffQuantityInEaches, httpHeaders);
      }
      // move publish CancelMove for automationDc
      if (0 == newQuantityFromUI
          && (configUtils.getConfiguredFeatureFlag(
                  getFacilityNum().toString(), PUBLISH_CANCEL_MOVE_ENABLED, false)
              || itemConfig.isOneAtlasConvertedItem(
                  isOneAtlas, itemState, itemNumber, httpHeaders))) {
        movePublisher.publishCancelMove(trackingId, httpHeaders);
      }
    } catch (ReceivingException re) {
      log.error(
          "updateQuantityByTrackingId ReceivingException for lpn={}, newQuantityFromUI={}, errorMsg={}, cause={}",
          trackingId,
          newQuantityFromUI,
          ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE.equals(
                      re.getErrorResponse().getErrorCode())
                  && containerUpdateRequest.isInventoryReceivingCorrection()
              ? "PO is NOT finalized in RCV but finalized in INV/DcFin" // Splunk alert monitoring
              : re.getMessage(),
          re.getCause());
      throw re;
    } catch (ReceivingDataNotFoundException receivingDataNotFoundException) {
      throw receivingDataNotFoundException;
    } catch (Exception e) {
      log.error(
          "Unknown error for lpn={}, newQuantityUI={}, printerId={} , errorMsg={}, cause= StackTrace={}",
          trackingId,
          newQuantityFromUI,
          printerId,
          e.getMessage(),
          e.getCause(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          isBlank(e.getMessage()) ? ADJUST_PALLET_QUANTITY_ERROR_MSG : e.getMessage(),
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }
    log.info(
        "successfully adjusted Quantity={}, printerId={} for lpn={}",
        newQuantityFromUI,
        printerId,
        trackingId);
    return response;
  }

  @SneakyThrows
  private void validateReceivingCorrection(
      Map<String, Object> containerMiscInfo,
      Long deliveryNumber,
      String trackingId,
      ContainerUpdateRequest containerUpdateRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    final Integer newQtyUi = containerUpdateRequest.getAdjustQuantity();
    final Integer oldQtyInv = containerUpdateRequest.getInventoryQuantity();
    log.info(
        "validateReceivingCorrection for deliveryNumber={}, TrackingId={}, AdjustQty={}, oldQtyInv={}, miscInfo={}",
        deliveryNumber,
        trackingId,
        newQtyUi,
        oldQtyInv,
        containerMiscInfo);
    // check if new qty = old qty then throw error
    throwIfTrue(newQtyUi.compareTo(oldQtyInv) == 0, ADJUST_PALLET_QUANTITY_SAME_ERROR_MSG);

    boolean isNegativeCorrection = newQtyUi.compareTo(oldQtyInv) < 0;
    String invalidInventoryStatusCcm =
        configUtils.getCcmValue(getFacilityNum(), INVALID_INVENTORY_STATUS_TO_ADJUST, EMPTY_STRING);
    String invalidMoveStatusCcm =
        configUtils.getCcmValue(getFacilityNum(), INVALID_MOVE_STATUS_CORRECTION, EMPTY_STRING);
    CompletableFuture<InventoryContainerDetails> asyncInventoryContainerDetails =
        asyncInventoryService.getInventoryContainerDetails(trackingId, httpHeaders);
    CompletableFuture<List<String>> asyncMoveContainerDetails =
        asyncMoveRestApiClient.getMoveContainerDetails(trackingId, httpHeaders);
    CompletableFuture<GdmDeliveryHistoryResponse> asyncGdmDeliveryHistoryResponse =
        asyncGdmRestApiClient.getDeliveryHistory(deliveryNumber, httpHeaders);
    CompletableFuture.allOf(
            asyncInventoryContainerDetails,
            asyncMoveContainerDetails,
            asyncGdmDeliveryHistoryResponse)
        .join();
    InventoryContainerDetails inventoryContainerDetails = asyncInventoryContainerDetails.get();

    validateMechRestrictions(containerMiscInfo, inventoryContainerDetails.getLocationName());

    List<String> containerMoveTypeStatusList = asyncMoveContainerDetails.get();
    GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse = asyncGdmDeliveryHistoryResponse.get();

    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    if (isNegativeCorrection) {
      negativeCorrectionValidation(
          cId,
          newQtyUi,
          inventoryContainerDetails,
          containerMoveTypeStatusList,
          gdmDeliveryHistoryResponse,
          invalidInventoryStatusCcm,
          invalidMoveStatusCcm);
    } else {
      positiveCorrectionValidation(
          inventoryContainerDetails,
          containerMoveTypeStatusList,
          gdmDeliveryHistoryResponse,
          invalidInventoryStatusCcm,
          invalidMoveStatusCcm);
    }
  }

  public void validateMechRestrictions(Map<String, Object> miscInfo, String locationName)
      throws ReceivingException {
    if (!isMechContainer(miscInfo)) return;

    LocationInfo locationInfo = locationService.getLocationInfo(locationName);
    final String automationType = locationInfo.getAutomationType();
    final Boolean isPrimeSlot = locationInfo.getIsPrimeSlot();
    if (!hasInductedIntoMech(automationType, isPrimeSlot)) return;
    log.error(
        PALLET_HAS_BEEN_INDUCTED_INTO_MECH + " location name={}, automationType={}, isPrimeSlot={}",
        locationName,
        automationType,
        isPrimeSlot);
    throwIfTrue(true, PALLET_HAS_BEEN_INDUCTED_INTO_MECH);
  }

  /**
   * inv.status =ALLOCATED,PICKED,LOADED or status.AVAILABLE & allocatedQty >0
   *
   * @param cId
   * @param newQtyUi
   * @param inventoryContainerDetails
   * @param moveTypeStatusList
   * @param gdmDeliveryHistoryResponse
   * @param invalidInventoryStatusCcm
   * @param invalidMoveStatusCcm
   * @throws ReceivingException
   */
  private void negativeCorrectionValidation(
      String cId,
      Integer newQtyUi,
      InventoryContainerDetails inventoryContainerDetails,
      List<String> moveTypeStatusList,
      GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse,
      String invalidInventoryStatusCcm,
      String invalidMoveStatusCcm)
      throws ReceivingException {
    log.info(
        "negativeCorrectionValidation inventoryInfo={} moveInfo={}",
        inventoryContainerDetails.getContainerStatus(),
        moveTypeStatusList);
    // check inventory status is invalid then throw error
    throwIfTrue(
        invalidInventoryStatusCcm.contains(inventoryContainerDetails.getContainerStatus()),
        PALLET_NOT_AVAILABLE_ERROR_MSG);
    // Check if status is available and allocated qty is greater than 0
    throwIfTrue(
        AVAILABLE.equalsIgnoreCase(inventoryContainerDetails.getContainerStatus())
            && nonNull(inventoryContainerDetails.getAllocatedQty())
            && inventoryContainerDetails.getAllocatedQty() > 0,
        PALLET_NOT_AVAILABLE_ERROR_MSG);

    // check move status is invalid then throw error
    throwIfTrue(
        ReceivingUtils.isInvalidMovePresent(moveTypeStatusList, invalidMoveStatusCcm),
        MOVE_INVALID_STATUS_MSG);

    // if BillSigned for -ve RC don't allow
    if (isBillSigned(cId, gdmDeliveryHistoryResponse)) {
      throwIfTrue(true, BILL_SIGNED_ERROR_MSG, "Can't do negative RC when as BillSigned");
    } else {
      // check is not BillSigned and move is putawayCompleted .ccm type+status is invalid
      throwIfTrue(
          ReceivingUtils.isInvalidMovePresent(
              moveTypeStatusList,
              configUtils.getCcmValue(
                  getFacilityNum(), INVALID_MOVE_IF_BILL_NOT_SIGNED, EMPTY_STRING)),
          NEGATIVE_RC_CANNOT_BE_DONE_PUTAWAY_COMPLETE,
          "can't do -ve RC as Bill not Signed and move putaway complete as value set in ccm"
              + INVALID_MOVE_IF_BILL_NOT_SIGNED);
    }
  }

  private void positiveCorrectionValidation(
      InventoryContainerDetails inventoryContainerDetails,
      List<String> containerMoveTypeStatusList,
      GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse,
      String invalidInventoryStatusCcm,
      String invalidMoveTypeAndStatusCcm)
      throws ReceivingException {
    final String containerStatus = inventoryContainerDetails.getContainerStatus();
    log.info(
        "positiveCorrectionValidation inv.Status={} moveContainerDetail={}",
        containerStatus,
        containerMoveTypeStatusList);
    // check inventory status is invalid then throw error
    throwIfTrue(
        invalidInventoryStatusCcm.contains(containerStatus), PALLET_NOT_AVAILABLE_ERROR_MSG);
    // Check if inventory.status is available and allocated qty is greater than 0
    throwIfTrue(
        AVAILABLE.equalsIgnoreCase(containerStatus)
            && nonNull(inventoryContainerDetails.getAllocatedQty())
            && inventoryContainerDetails.getAllocatedQty() > 0,
        PALLET_NOT_AVAILABLE_ERROR_MSG);

    // 3.Delivery finalize Date > 30 Days
    final String allowedDaysAfterFinalised =
        configUtils.getCcmValue(
            getFacilityNum(),
            ALLOWED_DAYS_FOR_CORRECTION_AFTER_FINALIZED,
            DEFAULT_ALLOWED_DAYS_FOR_CORRECTION_AFTER_FINALIZED);
    throwIfTrue(
        ReceivingUtils.isReceiveCorrectionPastThreshold(
            getFinalizedDate(gdmDeliveryHistoryResponse), allowedDaysAfterFinalised),
        String.format(RECEIPT_ERROR_MSG, allowedDaysAfterFinalised));
    // check move status is invalid then throw error
    throwIfTrue(
        ReceivingUtils.isInvalidMovePresent(
            containerMoveTypeStatusList, invalidMoveTypeAndStatusCcm),
        MOVE_INVALID_STATUS_MSG);
  }

  private void throwIfTrue(boolean condition, String errorMessage) throws ReceivingException {
    throwIfTrue(condition, errorMessage, null);
  }

  private void throwIfTrue(boolean condition, String errorMessage, String devMsg)
      throws ReceivingException {
    if (condition) {
      if (nonNull(devMsg)) log.info(devMsg);
      log.error("Validation Error during RCV correction, Error message={}", errorMessage);
      throw new ReceivingException(
          errorMessage,
          BAD_REQUEST,
          ADJUST_PALLET_VALIDATION_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }
  }

  private static boolean isBillSigned(
      String correlationId, GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse) {
    return gdmDeliveryHistoryResponse != null
        && gdmDeliveryHistoryResponse
            .getDeliveryEvents()
            .stream()
            .anyMatch(
                deliveryEvent -> {
                  if (EVENT_TYPE_BILL_SIGNED.equalsIgnoreCase(deliveryEvent.getEvent())) {
                    log.info(
                        "cId={} Bill is signed as Delivery Event={}", correlationId, deliveryEvent);
                    return true;
                  }
                  return false;
                });
  }

  private LocalDate getFinalizedDate(GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse) {
    LocalDate finalizedLocalDate = null;
    DeliveryEvent finalisedEvent =
        gdmDeliveryHistoryResponse
            .getDeliveryEvents()
            .stream()
            .filter(
                deliveryEvent -> deliveryEvent.getEvent().equalsIgnoreCase(EVENT_TYPE_FINALIZED))
            .findFirst()
            .orElse(null);
    if (nonNull(finalisedEvent)) {
      Date finalizedDate = finalisedEvent.getTimestamp();
      finalizedLocalDate = ReceivingUtils.convertToLocalDateViaInstant(finalizedDate);
    }
    return finalizedLocalDate;
  }
}
