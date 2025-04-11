package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_MSG;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerItemService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.model.SerializedContainerUpdateRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component
public class RxUpdateSerializedContainerQtyRequestHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RxUpdateSerializedContainerQtyRequestHandler.class);

  @Autowired private RxContainerAdjustmentValidator rxContainerAdjustmentValidator;
  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Autowired private ContainerService containerService;
  @Autowired private NimRdsServiceImpl nimRdsService;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private Gson gson;
  @Autowired private RxReceivingCorrectionPrintJobBuilder rxReceivingCorrectionPrintJobBuilder;
  @Autowired private ContainerItemService containerItemService;

  public ContainerUpdateResponse updateQuantityByTrackingId(
      String trackingId,
      SerializedContainerUpdateRequest containerUpdateRequest,
      HttpHeaders httpHeaders) {

    Integer newQuantityUI = containerUpdateRequest.getAdjustQuantity();
    String newQuantityUomUI =
        StringUtils.defaultIfBlank(
            containerUpdateRequest.getAdjustQuantityUOM(), ReceivingConstants.Uom.VNPK);

    LOGGER.info(
        "Received updateQuantityByTrackingId request for lpn:{} with adjustQuantity:{} and adjustQuantityUOM:{}",
        trackingId,
        newQuantityUI,
        newQuantityUomUI);

    ContainerUpdateResponse containerUpdateResponse = new ContainerUpdateResponse();

    try {
      Container container4mDB = containerService.getContainerByTrackingId(trackingId);
      if (Objects.isNull(container4mDB)) {
        throw new ReceivingBadDataException(
            ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE,
            ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
      }
      int newContainerQuantityInEaches =
          getQuantityInEA(container4mDB, newQuantityUI, newQuantityUomUI);
      if (newQuantityUI <= 0 || newContainerQuantityInEaches >= getQuantityInEA(container4mDB)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_PALLET_CORRECTION_QTY,
            ReceivingException.INVALID_PALLET_CORRECTION_QTY);
      }
      rxContainerAdjustmentValidator.validateContainerForAdjustment(container4mDB, httpHeaders);

      Optional<Instruction> instructionOptional =
          instructionRepository.findById(container4mDB.getInstructionId());
      if (instructionOptional.isPresent()) {
        Instruction instruction4mDB = instructionOptional.get();

        // Call RDS service to notify the receiving correction
        nimRdsService.quantityChange(newQuantityUI, trackingId, httpHeaders);

        Receipt receipt =
            containerAdjustmentHelper.adjustQuantityInReceipt(
                newQuantityUI, newQuantityUomUI, container4mDB, ReceivingUtils.retrieveUserId());

        Container adjustedContainer =
            containerAdjustmentHelper.adjustPalletQuantity(
                newContainerQuantityInEaches, container4mDB, ReceivingUtils.retrieveUserId());

        Set<Container> containersByTrackingIdSet =
            containerService.getContainerListByTrackingIdList(
                containerUpdateRequest.getTrackingIds());

        List<Container> adjustedContainers = new ArrayList();
        List<ContainerItem> adjustedContainerItems = new ArrayList();
        adjustedContainers.add(adjustedContainer);
        containersByTrackingIdSet.forEach(
            childContainers4mDB -> {
              childContainers4mDB.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
              adjustedContainers.add(childContainers4mDB);
            });

        // This step is required to adjust intermediate container's quantity
        adjustIntermediateContainer(
            newContainerQuantityInEaches,
            instruction4mDB,
            containersByTrackingIdSet,
            adjustedContainers);

        containerAdjustmentHelper.persistAdjustedReceiptsAndContainers(receipt, adjustedContainers);
        Map<String, Object> printJob =
            rxReceivingCorrectionPrintJobBuilder.getPrintJobForReceivingCorrection(
                getQuantityInWhpk(container4mDB, newQuantityUI, newQuantityUomUI),
                ReceivingConstants.Uom.WHPK,
                instruction4mDB);

        containerUpdateResponse.setPrintJob(printJob);
      } else {
        throw new ReceivingBadDataException(
            ExceptionCodes.INSTRUCTION_NOT_FOUND_FOR_CONTAINER,
            RxConstants.INSTRUCTION_NOT_FOUND_FOR_CONTAINER);
      }
    } catch (ReceivingBadDataException | ReceivingInternalException rbde) {
      LOGGER.error(
          "UpdateQuantityByTrackingId for lpn={}, adjustQuantity: {}, errorMsg: {}, StackTrace: {}",
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

  private void adjustIntermediateContainer(
      int newContainerQuantityInEaches,
      Instruction instruction4mDB,
      Set<Container> containersByTrackingIdSet,
      List<Container> adjustedContainers)
      throws ReceivingException {
    if (RxInstructionType.BUILD_PARTIAL_CONTAINER
            .getInstructionType()
            .equals(instruction4mDB.getInstructionCode())
        || RxInstructionType.BUILDCONTAINER_CASES_SCAN
            .getInstructionType()
            .equals(instruction4mDB.getInstructionCode())) {
      Optional<Container> firstContainer = containersByTrackingIdSet.stream().findFirst();
      if (firstContainer.isPresent()) {
        String intermediateTrackingId = firstContainer.get().getParentTrackingId();
        Container intermediateContainer4mDB =
            containerService.getContainerByTrackingId(intermediateTrackingId);
        Container intermediateAdjustedContainer =
            containerAdjustmentHelper.adjustPalletQuantity(
                newContainerQuantityInEaches,
                intermediateContainer4mDB,
                ReceivingUtils.retrieveUserId());
        adjustedContainers.add(intermediateAdjustedContainer);
      }
    }
  }

  private int getQuantityInEA(Container container) {
    ContainerItem containerItem = container.getContainerItems().get(0);
    return ReceivingUtils.conversionToEaches(
        containerItem.getQuantity(),
        containerItem.getQuantityUOM(),
        containerItem.getVnpkQty(),
        containerItem.getWhpkQty());
  }

  private int getQuantityInEA(Container container, Integer newQty, String newQtyUOM) {
    ContainerItem containerItem = container.getContainerItems().get(0);
    return ReceivingUtils.conversionToEaches(
        newQty, newQtyUOM, containerItem.getVnpkQty(), containerItem.getWhpkQty());
  }

  private int getQuantityInWhpk(Container container, Integer newQty, String newQtyUOM) {
    ContainerItem containerItem = container.getContainerItems().get(0);
    return ReceivingUtils.conversionToWareHousePack(
        newQty, newQtyUOM, containerItem.getVnpkQty(), containerItem.getWhpkQty());
  }
}
