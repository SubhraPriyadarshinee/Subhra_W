package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.createDcFinAdjustRequest;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.conversionToEaches;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardablHeaderWithTenantData;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeaders;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static java.lang.String.valueOf;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.dcfin.model.DcFinAdjustRequest;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsAdjustPayload;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.DEFAULT_UPDATE_CONTAINER_QUANTITY_REQUEST_HANDLER)
public class DefaultUpdateContainerQuantityRequestHandler
    implements UpdateContainerQuantityRequestHandler {

  private static final Logger log =
      LoggerFactory.getLogger(DefaultUpdateContainerQuantityRequestHandler.class);

  @Autowired private ContainerService containerService;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Autowired private GlsRestApiClient glsRestApiClient;
  @Autowired private DCFinRestApiClient dcFinRestApiClient;
  @Autowired private MovePublisher movePublisher;
  @Autowired @Lazy private ItemConfigApiClient itemConfig;
  @Autowired private ReceiptService receiptService;

  /**
   * @param trackingId
   * @param containerUpdateRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
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
    validateTrackingId(automatedDc, trackingId);

    Integer newQuantityInVnpk_UI = containerUpdateRequest.getAdjustQuantity();
    final Integer printerId = containerUpdateRequest.getPrinterId();
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    final String facilityNum = getFacilityNum().toString();

    ContainerUpdateResponse response = new ContainerUpdateResponse();
    try {
      // pallet/receiving correction using inventory Data(quantity)
      final Integer quantityInVnkp_UI = containerUpdateRequest.getInventoryQuantity();
      final int diffQuantityInVnpk_UI = newQuantityInVnpk_UI - quantityInVnkp_UI;
      Container updatedContainer = containerService.getContainerByTrackingId(trackingId);
      final Container initialContainer = SerializationUtils.clone(updatedContainer);
      final Integer initialQtyInEa = initialContainer.getContainerItems().get(0).getQuantity();
      containerService.isBackoutContainer(trackingId, updatedContainer.getContainerStatus());
      final Long deliveryNumber = updatedContainer.getDeliveryNumber();
      ContainerItem updatedCi = containerService.getContainerItem(cId, updatedContainer);
      final String purchaseReferenceNumber = updatedCi.getPurchaseReferenceNumber();
      final Integer purchaseReferenceLineNumber = updatedCi.getPurchaseReferenceLineNumber();
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

      containerService.adjustQuantityValidation(
          deliveryNumber, cId, newQuantityInVnpk_UI, deliveryDocumentLine);

      Integer diffQuantityInEaches =
          containerService.adjustContainerItemQuantityAndGetDiff(
              cId, newQuantityInVnpk_UI, response, updatedContainer, updatedCi, quantityInVnkp_UI);

      // adjust after validation(DcFin closed)
      Receipt masterReceipt =
          receiptService
              .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                  deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);

      containerService.validatePOClose(
          updatedCi, deliveryNumber, purchaseReferenceNumber, masterReceipt);
      containerService.adjustQuantityInReceiptUseInventoryData(
          cId,
          newQuantityInVnpk_UI,
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
            newQuantityInVnpk_UI,
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
        adjustInGls(trackingId, httpHeaders, newQuantityInVnpk_UI, quantityInVnkp_UI);
      }

      // post to GDM Finalize PO with OSDR
      containerService.postFinalizePoOsdrToGdm(
          httpHeaders, deliveryNumber, purchaseReferenceNumber, finalizePORequestBody);

      // Publish receipt update to SCT - automatedDc || isOneAtlasAndConverted
      if (automatedDc
          || itemConfig.isOneAtlasConvertedItem(isOneAtlas, itemState, itemNumber, httpHeaders)) {
        receiptPublisher.publishReceiptUpdate(trackingId, httpHeaders, Boolean.TRUE);
      }
      // dcFin publish - isOneAtlasAndNotConverted (eg 6085 )
      if (itemConfig.isOneAtlasNotConvertedItem(isOneAtlas, itemState, itemNumber, httpHeaders)) {
        notifyReceivingCorrectionToDcFin(updatedContainer, diffQuantityInEaches, httpHeaders);
      }
      // move publish CancelMove
      if (0 == newQuantityInVnpk_UI
          && (configUtils.getConfiguredFeatureFlag(
                  getFacilityNum().toString(), PUBLISH_CANCEL_MOVE_ENABLED, false)
              || itemConfig.isOneAtlasConvertedItem(
                  isOneAtlas, itemState, itemNumber, httpHeaders))) {
        movePublisher.publishCancelMove(trackingId, httpHeaders);
      }
    } catch (ReceivingException re) {
      log.error(
          "cId={}, updateQuantityByTrackingId ReceivingException for lpn={}, newQuantityInVnpk_UI={}, errorMsg={}, StackTrace={}",
          cId,
          trackingId,
          newQuantityInVnpk_UI,
          ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE.equals(
                      re.getErrorResponse().getErrorCode())
                  && containerUpdateRequest.isInventoryReceivingCorrection()
              ? "PO is NOT finalized in RCV but finalized in INV/DcFin" // Splunk alert monitoring
              : re.getMessage(),
          ExceptionUtils.getStackTrace(re));
      throw re;
    } catch (Exception e) {
      log.error(
          "cId={}, updateQuantityByTrackingId unknown error for lpn={}, newQuantityInVnpk_UI={}, printerId={} , errorMsg={}, StackTrace={}",
          cId,
          trackingId,
          newQuantityInVnpk_UI,
          printerId,
          e.getMessage(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          ADJUST_PALLET_QUANTITY_ERROR_MSG,
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }
    log.info(
        "step:10 cId={}, successfully adjusted Quantity={}, printerId={} for lpn={}",
        cId,
        newQuantityInVnpk_UI,
        printerId,
        trackingId);
    return response;
  }

  /**
   * Async call with db persist support
   *
   * <pre>
   * VTR/Correction
   * # Prod (8852) > BAU we send to Inventory and inventory send to DcFin
   * # Full GLS(6097) >
   * ## BAU we send to GLS and GLS sends to DcFin
   * ## RCV don't send to Inventory -
   * # OneAtlas
   * ## ItemConverted
   * ### expected to work like BAU so RCV to send to Inventory, Inventory Send to DcFin
   * ##  ItemNotConverted
   * ### NEW Change, RCV send to GLS
   * ### NEW Change, RCV send to DcFin
   * ### NEW Change, RCV will NOT send to Inventory
   * </pre>
   *
   * @param updatedContainer
   * @param httpHeaders
   */
  public void notifyReceivingCorrectionToDcFin(
      Container updatedContainer, Integer quantityDiff, HttpHeaders httpHeaders)
      throws ReceivingException {
    final String txnId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    final DcFinAdjustRequest receivingCorrectionRequest =
        createDcFinAdjustRequest(
            updatedContainer, txnId, INVENTORY_RECEIVING_CORRECTION_REASON_CODE, quantityDiff);
    dcFinRestApiClient.adjustOrVtr(
        receivingCorrectionRequest, getForwardablHeaderWithTenantData(httpHeaders));
  }

  public void validateTrackingId(boolean automatedDc, String trackingId) {
    if (automatedDc) {
      ReceivingUtils.validateTrackingId(trackingId);
    }
  }

  public void adjustInGls(
      String trackingId,
      HttpHeaders httpHeaders,
      Integer newQuantityInVnpk_UI,
      Integer quantityInVnkp_UI)
      throws ReceivingException {
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_GLS_API_ENABLED, false)) {
      final GlsAdjustPayload glsAdjustPayload =
          glsRestApiClient.createGlsAdjustPayload(
              RECEIVING_CORRECTION,
              trackingId,
              newQuantityInVnpk_UI,
              quantityInVnkp_UI,
              valueOf(httpHeaders.getFirst(USER_ID_HEADER_KEY)));
      glsRestApiClient.adjustOrCancel(glsAdjustPayload, getForwardableHttpHeaders(httpHeaders));
    }
  }
}
