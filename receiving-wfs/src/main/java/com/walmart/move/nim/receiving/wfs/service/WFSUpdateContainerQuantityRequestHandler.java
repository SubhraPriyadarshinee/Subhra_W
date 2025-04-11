package com.walmart.move.nim.receiving.wfs.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.UpdateContainerQuantityRequestHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import com.walmart.move.nim.receiving.wfs.label.LabelConstants;
import java.util.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

public class WFSUpdateContainerQuantityRequestHandler
    implements UpdateContainerQuantityRequestHandler {

  private static final Logger log =
      LoggerFactory.getLogger(WFSUpdateContainerQuantityRequestHandler.class);

  @Autowired private ContainerService containerService;
  @Autowired private ReceiptService receiptService;
  @Autowired private WFSContainerService wfsContainerService;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private Gson gson;

  /**
   * @param trackingId
   * @param containerUpdateRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @Override
  @Transactional
  @InjectTenantFilter
  public ContainerUpdateResponse updateQuantityByTrackingId(
      String trackingId, ContainerUpdateRequest containerUpdateRequest, HttpHeaders httpHeaders)
      throws ReceivingException {

    Integer newQuantityInUI = containerUpdateRequest.getAdjustQuantity();
    final Integer printerId = containerUpdateRequest.getPrinterId();
    final String cId = httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    ContainerUpdateResponse response = new ContainerUpdateResponse();
    try {
      // pallet/receiving correction using inventory Data(quantity)
      final Integer currentQuantityInUI = containerUpdateRequest.getInventoryQuantity();
      final int diffQuantityInUI = newQuantityInUI - currentQuantityInUI;
      Container updatedContainer = containerService.getContainerByTrackingId(trackingId);
      containerService.isBackoutContainer(trackingId, updatedContainer.getContainerStatus());

      // Validate delivery status. Finalised delivery will not be allowed for receiving correction
      CancelContainerResponse validateDeliveryStatusResponse =
          containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
              updatedContainer.getTrackingId(), updatedContainer.getDeliveryNumber(), httpHeaders);
      if (Objects.nonNull(validateDeliveryStatusResponse)) {
        throw new ReceivingException(
            validateDeliveryStatusResponse.getErrorMessage(),
            HttpStatus.BAD_REQUEST,
            validateDeliveryStatusResponse.getErrorCode(),
            ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER);
      }

      final Long deliveryNumber = updatedContainer.getDeliveryNumber();
      ContainerItem containerItem = containerService.getContainerItem(cId, updatedContainer);
      final Integer initialQtyInEa = containerItem.getQuantity();

      Integer diffQuantityInEaches =
          wfsContainerService.adjustContainerItemQuantityAndGetDiff(
              cId,
              userId,
              newQuantityInUI,
              response,
              updatedContainer,
              containerItem,
              currentQuantityInUI,
              containerUpdateRequest.getAdjustQuantityUOM());

      // We want INV fail to rollback RCV as we have logic for qty diff for possible damages
      if (!containerUpdateRequest.isInventoryReceivingCorrection()) {
        containerService.adjustQuantityByEachesInInventoryService(
            cId, trackingId, diffQuantityInEaches, httpHeaders, containerItem, initialQtyInEa);
      }

      // Create negative receipt
      Receipt adjustedReceipt =
          wfsContainerService.createDiffReceipt(
              updatedContainer, containerItem, diffQuantityInEaches, userId);
      receiptService.saveReceipt(adjustedReceipt);

      // Publish receipt update to SCT
      receiptPublisher.publishReceiptUpdate(trackingId, httpHeaders, Boolean.TRUE);
      Map<String, Object> printJob =
          getPrintJobForReceivingCorrection(newQuantityInUI, updatedContainer, httpHeaders);
      response.setPrintJob(printJob);
    } catch (Exception e) {
      log.error(
          "cId={}, updateQuantityByTrackingId unknown error for lpn={}, newQuantityInUI={}, printerId={} , errorMsg={}, StackTrace={}",
          cId,
          trackingId,
          newQuantityInUI,
          printerId,
          e.getMessage(),
          ExceptionUtils.getStackTrace(e));

      if (ReceivingException.LABEL_QUANTITY_ADJUSTMENT_ERROR_MSG_FOR_FINALIZED_DELIVERY.equals(
          e.getMessage())) throw e;

      throw new ReceivingException(
          ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }
    log.info(
        "cId={}, successfully adjusted Quantity={}, printerId={} for lpn={}",
        cId,
        newQuantityInUI,
        printerId,
        trackingId);
    return response;
  }

  public Map<String, Object> getPrintJobForReceivingCorrection(
      Integer quantityToBeAdjusted, Container container, HttpHeaders httpHeaders) {
    Map<String, Object> printJob = new HashMap<>();
    Optional<Instruction> instruction =
        instructionRepository.findById(container.getInstructionId());
    if (instruction.isPresent()) {
      printJob = getPrintJobForAdjustedLabel(quantityToBeAdjusted, instruction.get(), httpHeaders);
    }
    return printJob;
  }

  public Map<String, Object> getPrintJobForAdjustedLabel(
      Integer adjustedQty, Instruction instruction, HttpHeaders httpHeaders) {

    Map<String, Object> ctrLabel = instruction.getContainer().getCtrLabel();
    JsonArray jsonPrintRequests =
        gson.toJsonTree(ctrLabel)
            .getAsJsonObject()
            .getAsJsonArray(ReceivingConstants.PRINT_REQUEST_KEY);
    List<PrintLabelRequest> printLabelRequestList = new ArrayList<>();
    printLabelRequestList.addAll(
        gson.fromJson(
            jsonPrintRequests, new TypeToken<ArrayList<PrintLabelRequest>>() {}.getType()));

    String labelTimeStamp =
        ReceivingUtils.getLabelFormatDateAndTime(
            ReceivingUtils.getDCDateTime(configUtils.getDCTimeZone(TenantContext.getFacilityNum())),
            WFSConstants.WFS_LABEL_TIMESTAMP_PATTERN);

    for (PrintLabelRequest printLabelRequest : printLabelRequestList) {
      List<LabelData> labelDataList = printLabelRequest.getData();
      boolean isLblQtyUpdated = false;
      boolean isLblUserUpdated = false;
      boolean isLblTimeStampUpdated = false;
      for (LabelData label : labelDataList) {
        if (!isLblQtyUpdated && label.getKey().equalsIgnoreCase(LabelConstants.LBL_QTY)) {
          label.setValue(String.valueOf(adjustedQty));
          isLblQtyUpdated = true;
        }
        if (!isLblUserUpdated && label.getKey().equalsIgnoreCase(LabelConstants.LBL_FULLUSERID)) {
          // if userId exists, it is createUserId. now userId in headers is last changed userId
          // - in case this key exists (createUserId exists), don't update its value to newest
          // userId,
          //   and mark it updated in flag, (so new key does not get created)
          // - in case this key does not exist, put its value to the latest user.
          isLblUserUpdated = true;
        }
        if (!isLblTimeStampUpdated
            && label.getKey().equalsIgnoreCase(LabelConstants.LBL_LABELTIMESTAMP)) {
          label.setValue(labelTimeStamp);
          isLblTimeStampUpdated = true;
        }
        if (isLblQtyUpdated && isLblUserUpdated && isLblTimeStampUpdated) break;
      }

      // in case key not found in LabelData, (add the key into LabelData entries)
      if (!isLblQtyUpdated) {
        labelDataList.add(
            LabelData.builder()
                .key(LabelConstants.LBL_QTY)
                .value(String.valueOf(adjustedQty))
                .build());
      }
      if (!isLblUserUpdated) {
        labelDataList.add(
            LabelData.builder()
                .key(LabelConstants.LBL_FULLUSERID)
                .value(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY))
                .build());
      }
      if (!isLblTimeStampUpdated) {
        labelDataList.add(
            LabelData.builder()
                .key(LabelConstants.LBL_LABELTIMESTAMP)
                .value(labelTimeStamp)
                .build());
      }
    }

    Map<String, Object> printJob = new HashMap<>();
    printJob.put(
        ReceivingConstants.PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
    printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, ReceivingConstants.ATLAS_RECEIVING);
    printJob.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequestList);

    return printJob;
  }
}
