package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.CONTAINER_ADJUSTMENT_NOT_ALLOWED_FOR_DA_CONTAINER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.InventoryReceivingCorrectionRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

public class RdcUpdateContainerQuantityHandler implements UpdateContainerQuantityRequestHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RdcUpdateContainerQuantityHandler.class);

  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ContainerService containerService;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private InventoryRestApiClient inventoryRestApiClient;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private Gson gson;
  @Autowired private LocationService locationService;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private ReceiptService receiptService;
  @Autowired private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @ManagedConfiguration private AppConfig appConfig;

  @Override
  @InjectTenantFilter
  @Transactional(
      rollbackFor = {
        ReceivingException.class,
        ReceivingBadDataException.class,
        ReceivingInternalException.class
      })
  public ContainerUpdateResponse updateQuantityByTrackingId(
      String trackingId, ContainerUpdateRequest containerUpdateRequest, HttpHeaders httpHeaders) {
    Integer newContainerQty = containerUpdateRequest.getAdjustQuantity();
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    LOGGER.info(
        "Received updateQuantityByTrackingId request for lpn:{} with adjustQuantity:{}",
        trackingId,
        newContainerQty);

    ContainerUpdateResponse containerUpdateResponse = new ContainerUpdateResponse();

    try {
      Container container4mDB = containerService.getContainerByTrackingId(trackingId);
      containerService.isBackoutContainer(trackingId, container4mDB.getContainerStatus());
      CancelContainerResponse validateDeliveryStatusResponse =
          containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
              container4mDB.getTrackingId(), container4mDB.getDeliveryNumber(), httpHeaders);
      if (Objects.nonNull(validateDeliveryStatusResponse)) {
        throw new ReceivingException(
            validateDeliveryStatusResponse.getErrorMessage(),
            HttpStatus.BAD_REQUEST,
            validateDeliveryStatusResponse.getErrorCode(),
            ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER);
      }

      ContainerItem currentContainerItem = container4mDB.getContainerItems().get(0);
      final Integer newContainerQuantityInEaches =
          ReceivingUtils.conversionToEaches(
              newContainerQty,
              ReceivingConstants.Uom.VNPK,
              currentContainerItem.getVnpkQty(),
              currentContainerItem.getWhpkQty());

      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
              false)
          && ContainerUtils.isAtlasConvertedItem(currentContainerItem)) {
        validateContainerAdjustmentForDAContainer(currentContainerItem);
        LOGGER.info(
            "Inventory integration is enabled and item is atlas converted, triggering receiving correction call to inventory for lpn: {}",
            trackingId);
        InventoryReceivingCorrectionRequest inventoryReceivingCorrectionRequest =
            getReceivingCorrectionRequest(
                trackingId, newContainerQuantityInEaches, currentContainerItem, userId);
        inventoryRestApiClient.notifyReceivingCorrectionAdjustment(
            inventoryReceivingCorrectionRequest, httpHeaders);
        Receipt adjustedReceipt =
            containerAdjustmentHelper.adjustQuantityInReceipt(
                newContainerQty, ReceivingConstants.Uom.VNPK, container4mDB, userId);
        receiptService.saveReceipt(adjustedReceipt);
        // publish pallet adjustment message to hawkeye
        boolean isSymPutAwayEligible =
            SymboticUtils.isValidForSymPutaway(
                currentContainerItem.getAsrsAlignment(),
                appConfig.getValidSymAsrsAlignmentValues(),
                currentContainerItem.getSlotType());
        if (isSymPutAwayEligible) {
          symboticPutawayPublishHelper.publishSymPutawayUpdateOrDeleteMessage(
              container4mDB.getTrackingId(),
              currentContainerItem,
              ReceivingConstants.PUTAWAY_UPDATE_ACTION,
              adjustedReceipt.getEachQty(),
              httpHeaders);
        }
      } else {
        // Call RDS service to notify the receiving correction
        nimRdsService.quantityChange(newContainerQty, trackingId, httpHeaders);

        final Integer currentContainerQtyInVnpk =
            ReceivingUtils.conversionToVendorPack(
                currentContainerItem.getQuantity(),
                ReceivingConstants.Uom.EACHES,
                currentContainerItem.getVnpkQty(),
                currentContainerItem.getWhpkQty());

        LabelAction action;
        String freightType = container4mDB.getContainerItems().get(0).getInboundChannelMethod();
        if (StringUtils.isNotBlank(freightType)
            && ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(freightType)) {
          action = LabelAction.DA_BACKOUT;
        } else {
          action = LabelAction.CORRECTION;
        }
        rdcInstructionUtils.publishInstructionToWft(
            container4mDB, currentContainerQtyInVnpk, newContainerQty, action, httpHeaders);
      }
      Map<String, Object> printJob =
          getPrintJobForReceivingCorrection(newContainerQty, container4mDB, httpHeaders);

      // Update container and container table with adjusted quantity
      Container adjustedContainer =
          containerAdjustmentHelper.adjustPalletQuantity(
              newContainerQuantityInEaches, container4mDB, userId);
      containerPersisterService.saveContainer(adjustedContainer);

      // Update adjusted quantity in instruction table
      rdcInstructionUtils.updateInstructionQuantity(
          container4mDB.getInstructionId(), newContainerQty);

      containerUpdateResponse.setContainer(adjustedContainer);
      containerUpdateResponse.setPrintJob(printJob);
    } catch (ReceivingBadDataException rbde) {
      LOGGER.error(
          "UpdateQuantityByTrackingId unknown error for lpn: {}, adjustQuantity: {}, errorMsg: {}, StackTrace: {}",
          trackingId,
          newContainerQty,
          rbde.getMessage(),
          ExceptionUtils.getStackTrace(rbde));

      throw rbde;
    } catch (ReceivingException e) {
      LOGGER.error(
          "UpdateQuantityByTrackingId for lpn={}, adjustQuantity: {}, errorMsg: {}, StackTrace: {}",
          trackingId,
          newContainerQty,
          e.getMessage(),
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingBadDataException(
          e.getErrorResponse().getErrorCode(), e.getErrorResponse().getErrorMessage().toString());
    } catch (ReceivingInternalException receivingInternalException) {
      throw receivingInternalException;
    } catch (Exception e) {
      LOGGER.error(
          "UpdateQuantityByTrackingId unknown error for lpn: {}, adjustQuantity: {}, errorMsg: {}, StackTrace: {}",
          trackingId,
          newContainerQty,
          e.getMessage(),
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_DO_PALLET_CORRECTION, ADJUST_PALLET_QUANTITY_ERROR_MSG);
    }
    LOGGER.info(
        "Successfully adjusted pallet quantity of {} for lpn: {}", newContainerQty, trackingId);
    return containerUpdateResponse;
  }

  /**
   * Validate container inbound channel method, if its DA then we do not allow container adjustment
   * on DA containers
   */
  private void validateContainerAdjustmentForDAContainer(ContainerItem containerItem) {
    if (Objects.nonNull(containerItem)
        && ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
            containerItem.getInboundChannelMethod())) {
      throw new ReceivingInternalException(
          String.format(ExceptionCodes.LABEL_CORRECTION_NOT_ALLOWED_FOR_DA),
          CONTAINER_ADJUSTMENT_NOT_ALLOWED_FOR_DA_CONTAINER,
          containerItem.getTrackingId());
    }
  }

  private Map<String, Object> getPrintJobForReceivingCorrection(
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
    List<LabelData> data = printLabelRequestList.get(0).getData();
    boolean isLblQtyUpdated = false;
    boolean isLblDateUpdated = false;
    boolean isLblUserUpdated = false;
    for (LabelData label : data) {
      if (!isLblQtyUpdated && label.getKey().equalsIgnoreCase(LabelConstants.LBL_PICK)) {
        label.setValue(String.valueOf(adjustedQty));
        isLblQtyUpdated = true;
      }
      if (!isLblUserUpdated && label.getKey().equalsIgnoreCase(LabelConstants.LBL_USERID)) {
        label.setValue(httpHeaders.getFirst(USER_ID_HEADER_KEY));
        isLblUserUpdated = true;
      }
      if (!isLblDateUpdated && label.getKey().equalsIgnoreCase(LabelConstants.LBL_LBLDATE)) {
        String dcTimeZone =
            tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
        String date = RdcUtils.getLabelFormatDateAndTime(ReceivingUtils.getDCDateTime(dcTimeZone));
        label.setValue(date);
        isLblDateUpdated = true;
      }
      if (isLblQtyUpdated && isLblUserUpdated && isLblDateUpdated) break;
    }

    printLabelRequestList.add(SerializationUtils.clone(printLabelRequestList.get(0)));

    Map<String, Object> printJob = new HashMap<>();
    printJob.put(
        ReceivingConstants.PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(httpHeaders));
    printJob.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, ReceivingConstants.ATLAS_RECEIVING);
    printJob.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequestList);

    return printJob;
  }

  private InventoryReceivingCorrectionRequest getReceivingCorrectionRequest(
      String trackingId,
      Integer newContainerQtyInEaches,
      ContainerItem currentContainerItem,
      String userId) {
    InventoryReceivingCorrectionRequest inventoryReceivingCorrectionRequest =
        new InventoryReceivingCorrectionRequest();

    inventoryReceivingCorrectionRequest.setTrackingId(trackingId);
    inventoryReceivingCorrectionRequest.setItemNumber(currentContainerItem.getItemNumber());
    inventoryReceivingCorrectionRequest.setItemUpc(currentContainerItem.getItemUPC());
    inventoryReceivingCorrectionRequest.setCurrentQty(currentContainerItem.getQuantity());
    inventoryReceivingCorrectionRequest.setAdjustBy(
        newContainerQtyInEaches - currentContainerItem.getQuantity());
    inventoryReceivingCorrectionRequest.setCurrentQuantityUOM(ReceivingConstants.EACHES);
    inventoryReceivingCorrectionRequest.setAdjustedQuantityUOM(ReceivingConstants.EACHES);
    Integer quantityAdjustmentReasonCode =
        Objects.nonNull(rdcManagedConfig.getQuantityAdjustmentReasonCode())
            ? rdcManagedConfig.getQuantityAdjustmentReasonCode()
            : ReceivingConstants.RDC_RECEIVING_CORRECTION_REASON_CODE;
    inventoryReceivingCorrectionRequest.setReasonCode(quantityAdjustmentReasonCode);
    inventoryReceivingCorrectionRequest.setReasonDesc(ReceivingConstants.VTR_COMMENT);
    inventoryReceivingCorrectionRequest.setComment(ReceivingConstants.VTR_COMMENT);
    inventoryReceivingCorrectionRequest.setFinancialReportingGroup(
        currentContainerItem.getFinancialReportingGroupCode());
    inventoryReceivingCorrectionRequest.setBaseDivisionCode(
        currentContainerItem.getBaseDivisionCode().toUpperCase());
    inventoryReceivingCorrectionRequest.setCreateUserId(userId);

    return inventoryReceivingCorrectionRequest;
  }
}
