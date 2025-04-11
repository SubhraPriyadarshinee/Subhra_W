package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.createDcFinAdjustRequest;
import static com.walmart.move.nim.receiving.core.client.move.Move.*;
import static com.walmart.move.nim.receiving.core.client.move.Move.isMoveNullOpenPendingOnHoldCancelled;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.dcfin.model.DcFinAdjustRequest;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.move.Move;
import com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import io.strati.libs.commons.lang.StringUtils;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

public class GdcCancelContainerProcessor implements CancelContainerProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(GdcCancelContainerProcessor.class);
  @Autowired private GDCFlagReader gdcFlagReader;
  @Autowired private MovePublisher movePublisher;
  @Autowired private ReceiptService receiptService;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private GlsRestApiClient glsRestApiClient;
  @Autowired private DCFinRestApiClient dcFinRestApiClient;
  @Autowired private GdcPutawayPublisher gdcPutawayPublisher;
  @Autowired private ItemConfigApiClient itemConfigApiClient;
  @Autowired private InventoryRestApiClient inventoryRestApiClient;
  @Autowired private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private ContainerAdjustmentValidator containerAdjustmentValidator;
  @Autowired private MoveRestApiClient mvClient;
  @Autowired private TenantSpecificConfigReader configUtils;

  @Autowired protected InstructionPersisterService instructionPersisterService;

  @Autowired private InstructionHelperService instructionHelperService;

  @Autowired private ContainerRepository containerRepository;
  @Autowired InventoryService inventoryService;
  @Autowired LocationService locationService;

  @Autowired private GDMRestApiClient gdmRestApiClient;

  @Transactional
  @InjectTenantFilter
  public List<CancelContainerResponse> cancelContainers(
      CancelContainerRequest cancelContainerRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<CancelContainerResponse> responseList = new ArrayList<>();
    final String trackingId = cancelContainerRequest.getTrackingIds().get(0);
    LOGGER.info("Enter GdcCancelContainer with trackingId:{}", trackingId);

    // Get the container details from DB
    Container container = containerPersisterService.getContainerDetailsWithoutChild(trackingId);

    if (!gdcFlagReader.publishVtrToWFTDisabled()) {

      List<Long> instructionIds =
          containerRepository.getInstructionIdsByTrackingIds(
              cancelContainerRequest.getTrackingIds(), getFacilityNum(), getFacilityCountryCode());

      // Get instruction details from DB
      Instruction instruction =
          instructionPersisterService.getInstructionById(instructionIds.get(0));

      UpdateInstructionRequest updateInstructionRequest =
          InstructionUtils.constructUpdateInstructionRequestForCancelContainer(
              instruction, container);
      Integer qtyReceived =
          -instruction.getReceivedQuantity(); // Received qty from Instruction table

      instructionHelperService.publishInstruction(
          instruction,
          updateInstructionRequest,
          qtyReceived,
          container,
          InstructionStatus.UPDATED,
          httpHeaders);
    }

    if (Objects.isNull(container)) {
      return handleCancelContainerResponse(
          new CancelContainerResponse(
              trackingId, CONTAINER_NOT_FOUND_ERROR_CODE, CONTAINER_NOT_FOUND_ERROR_MSG));
    }

    // Validate container for VTR
    CancelContainerResponse cancelContainerResponse = validateContainer(container);
    if (nonNull(cancelContainerResponse)) {
      return handleCancelContainerResponse(cancelContainerResponse);
    }

    if (gdcFlagReader.isManualGdcEnabled()) { // Manual DC
      cancelContainerResponse = handleManualGdcVTR(container, httpHeaders);
      if (nonNull(cancelContainerResponse)) {
        return handleCancelContainerResponse(cancelContainerResponse);
      }
    } else { // Automated DC
      LOGGER.info("Invoking automated GDC VTR with trackingId:{}", trackingId);
      cancelContainerResponse = handleAutomatedGdcVTR(container, httpHeaders);
      if (nonNull(cancelContainerResponse)) {
        return handleCancelContainerResponse(cancelContainerResponse);
      }
    }

    LOGGER.info("Exit GdcCancelContainer with trackingId:{} response:{}", trackingId, responseList);
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
   * Manual GDC flow
   *
   * @param container
   * @param httpHeaders
   * @return CancelContainerResponse
   * @throws ReceivingException
   */
  private CancelContainerResponse handleManualGdcVTR(Container container, HttpHeaders httpHeaders)
      throws ReceivingException {
    final String trackingId = container.getTrackingId();
    final boolean isOneAtlas = gdcFlagReader.isDCOneAtlasEnabled();
    // final ContainerItem containerItem = container.getContainerItems().get(0);
    CancelContainerResponse cancelContainerResponse = null;

    if (isOneAtlas) { // Site converted to OneAtlas
      LOGGER.info("OneAtlas - trackingId:{}", trackingId);
      cancelContainerResponse = handleOneAtlasVTR(container, httpHeaders);
      if (nonNull(cancelContainerResponse)) {
        return cancelContainerResponse;
      }
    } else { // Site not yet converted to OneAtlas, cancel the label in GLS
      LOGGER.info("FullGLS - trackingId:{}", trackingId);
      cancelContainerResponse = handleFullGlsVTR(container, httpHeaders);
      if (nonNull(cancelContainerResponse)) {
        return cancelContainerResponse;
      }
    }

    try {
      if (configUtils.getConfiguredFeatureFlag(
          httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
          ReceivingConstants.SEND_UPDATE_EVENTS_TO_GDM,
          false)) {
        sendVTREventToGdm(container, httpHeaders);
      }
    } catch (Exception ex) {
      // Cosmetic error so skip even if it fails
      LOGGER.error(
          "Failed to cancel label in GLS with error={}, stackTrace={}",
          ex.getMessage(),
          getStackTrace(ex));
    }
    return null;
  }

  private void sendVTREventToGdm(Container container, HttpHeaders httpHeaders) {
    Map<String, Object> forwarderHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    ReceiveEventRequestBody receiveEventRequestBody = null;
    try {
      // Prepare receive event body
      ContainerItem containerItem = container.getContainerItems().get(0);
      Integer cancelQty =
          conversionToVendorPack(
              containerItem.getQuantity(),
              containerItem.getQuantityUOM(),
              containerItem.getVnpkQty(),
              containerItem.getWhpkQty());

      ReceiveData receiveData =
          ReceiveData.builder()
              .eventType(ReceivingConstants.RECEIVE_VTR)
              .containerId(container.getTrackingId())
              .qty(cancelQty)
              .build();
      receiveEventRequestBody =
          ReceiveEventRequestBody.builder()
              .eventType(ReceivingConstants.RECEIVE_VTR)
              .deliveryNumber(container.getDeliveryNumber())
              .poNumber(containerItem.getPurchaseReferenceNumber())
              .line(String.valueOf(containerItem.getPurchaseReferenceLineNumber()))
              .receiveData(receiveData)
              .build();
      // Send event to GDM
      gdmRestApiClient.receivingToGDMEvent(receiveEventRequestBody, forwarderHeaders);
    } catch (GDMRestApiClientException e) {
      LOGGER.error(
          "Failed to call GDM to RECEIVE_TI_HI_UPDATES event for forwarderHeaders {} , payload {}",
          forwarderHeaders,
          receiveEventRequestBody);
    }
  }

  /**
   * Full GLS flow
   *
   * @param container
   * @param httpHeaders
   * @return CancelContainerResponse
   */
  private CancelContainerResponse handleFullGlsVTR(Container container, HttpHeaders httpHeaders) {
    CancelContainerResponse cancelContainerResponse =
        cancelGlsPallet(
            container.getTrackingId(), container.getContainerItems().get(0), httpHeaders);
    if (nonNull(cancelContainerResponse)) {
      return cancelContainerResponse;
    }

    // Create negative receipt in the DB
    adjustReceiptAndContainer(container);
    return null;
  }

  /**
   * Adjust receipt and container
   *
   * @param container
   */
  private void adjustReceiptAndContainer(Container container) {
    Receipt adjustedReceipt = containerAdjustmentHelper.adjustReceipts(container);
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    containerAdjustmentHelper.persistAdjustedReceiptsAndContainer(adjustedReceipt, container);
  }

  /**
   * OneAtlas flow
   *
   * @param container
   * @param httpHeaders
   * @return CancelContainerResponse
   * @throws ReceivingException
   */
  private CancelContainerResponse handleOneAtlasVTR(Container container, HttpHeaders httpHeaders)
      throws ReceivingException {
    CancelContainerResponse cancelContainerResponse = null;
    final Long itemNbr = container.getContainerItems().get(0).getItemNumber();
    if (itemConfigApiClient.isAtlasConvertedItem(itemNbr, httpHeaders)) { // OneAtlas converted item
      LOGGER.info("OneAtlas converted-item:{} trackingId:{}", itemNbr, container.getTrackingId());
      cancelContainerResponse = handleOneAtlasConvertedItem(container, httpHeaders);
      if (nonNull(cancelContainerResponse)) {
        return cancelContainerResponse;
      }
    } else { // OneAtlas site but item not yet converted to atlas
      LOGGER.info(
          "OneAtlas non-converted-item:{} trackingId:{}", itemNbr, container.getTrackingId());
      cancelContainerResponse = handleOneAtlasNonConvertedItem(container, httpHeaders);
      if (nonNull(cancelContainerResponse)) {
        return cancelContainerResponse;
      }
    }

    return null;
  }

  /**
   * Prepare error response
   *
   * @param cancelContainerResponse
   * @return List<CancelContainerResponse>
   */
  private List<CancelContainerResponse> handleCancelContainerResponse(
      CancelContainerResponse cancelContainerResponse) {
    List<CancelContainerResponse> responseList = new ArrayList<>();
    responseList.add(cancelContainerResponse);

    return responseList;
  }

  /**
   * Check for valid container
   *
   * @param container
   * @return CancelContainerResponse
   */
  private CancelContainerResponse validateContainer(Container container) {
    // Block VTR if container is not a valid state
    CancelContainerResponse cancelContainerResponse =
        containerAdjustmentValidator.validateContainerForAdjustment(container);
    if (nonNull(cancelContainerResponse)) {
      return cancelContainerResponse;
    }

    // Block VTR if PO in finalized state
    if (receiptService.isPOFinalized(
        container.getDeliveryNumber().toString(),
        container.getContainerItems().get(0).getPurchaseReferenceNumber())) {
      return new CancelContainerResponse(
          container.getTrackingId(), CONFIRM_PO_ERROR_CODE, PO_ALREADY_FINALIZED);
    }

    return null;
  }

  /**
   * Automated(Witron) flow
   *
   * @param container
   * @param httpHeaders
   * @return CancelContainerResponse
   * @throws ReceivingException
   */
  private CancelContainerResponse handleAutomatedGdcVTR(
      Container container, HttpHeaders httpHeaders) throws ReceivingException {
    // Submit VTR to Atlas Inventory
    CancelContainerResponse cancelContainerResponse = submitVtrToInventory(container, httpHeaders);
    if (nonNull(cancelContainerResponse)) {
      return cancelContainerResponse;
    }

    // Create negative receipt in the DB
    adjustReceiptAndContainer(container);

    // Send putaway delete to Hawkeye
    gdcPutawayPublisher.publishMessage(container, PUTAWAY_DELETE_ACTION, httpHeaders);

    // Submit updated receipt to SCT
    receiptPublisher.publishReceiptUpdate(container, httpHeaders);

    // Cancel move
    movePublisher.publishCancelMove(container.getTrackingId(), httpHeaders);
    // Submit VTR to dcFin
    if (configUtils.getConfiguredFeatureFlag(
        String.valueOf(getFacilityNum()), PUBLISH_TO_DCFIN_ADJUSTMENTS_ENABLED, false)) {
      notifyVtrToDcFin(container, httpHeaders);
    }

    return null;
  }

  /**
   * OneAtlas non-converted item flow
   *
   * @param container
   * @param httpHeaders
   * @return CancelContainerResponse
   */
  private CancelContainerResponse handleOneAtlasNonConvertedItem(
      Container container, HttpHeaders httpHeaders) {
    // Cancel the label in GLS
    CancelContainerResponse cancelContainerResponse =
        cancelGlsPallet(
            container.getTrackingId(), container.getContainerItems().get(0), httpHeaders);
    if (nonNull(cancelContainerResponse)) {
      return cancelContainerResponse;
    }

    // Submit VTR to DCFIN
    cancelContainerResponse = notifyVtrToDcFin(container, httpHeaders);
    if (nonNull(cancelContainerResponse)) {
      return cancelContainerResponse;
    }

    // Create negative receipt in the DB
    adjustReceiptAndContainer(container);

    return null;
  }

  /**
   * OneAtlas converted item flow
   *
   * @param container
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  private CancelContainerResponse handleOneAtlasConvertedItem(
      Container container, HttpHeaders httpHeaders) throws ReceivingException {

    // VTR Rules per Inv
    CancelContainerResponse cancelContainerResponse;

    cancelContainerResponse = validateAgainstInventory(container, httpHeaders);
    if (nonNull(cancelContainerResponse)) {
      return cancelContainerResponse;
    }

    // VTR Rules per Moves
    cancelContainerResponse = validateAgainstMoves(container, httpHeaders);
    if (nonNull(cancelContainerResponse)) {
      return cancelContainerResponse;
    }

    // Submit VTR to Atlas Inventory
    cancelContainerResponse = submitVtrToInventory(container, httpHeaders);
    if (nonNull(cancelContainerResponse)) {
      return cancelContainerResponse;
    }
    // Create negative receipt in the DB
    adjustReceiptAndContainer(container);

    // Submit VTR to dcFin
    if (configUtils.getConfiguredFeatureFlag(
        String.valueOf(getFacilityNum()), PUBLISH_TO_DCFIN_ADJUSTMENTS_ENABLED, false)) {
      // Submit VTR to dcFin
      notifyVtrToDcFin(container, httpHeaders);
    }

    // Submit VTR to SCT
    receiptPublisher.publishReceiptUpdate(container, httpHeaders);

    // Cancel move
    movePublisher.publishCancelMove(container.getTrackingId(), httpHeaders);

    return null;
  }

  private CancelContainerResponse validateMechRestrictions(
      Container container, HttpHeaders httpHeaders) throws ReceivingException {
    final Map<String, Object> miscInfo = container.getContainerMiscInfo();
    if (!isMechContainer(miscInfo)) return null;

    final String trackingId = container.getTrackingId();
    final InventoryContainerDetails inventoryContainerDetails =
        inventoryService.getInventoryContainerDetails(trackingId, httpHeaders);
    final String locationName = inventoryContainerDetails.getLocationName();
    final LocationInfo locationInfo = locationService.getLocationInfo(locationName);
    final String automationType = locationInfo.getAutomationType();
    final Boolean isPrimeSlot = locationInfo.getIsPrimeSlot();
    // if not hasInductedIntoMech allow vtr else err
    if (!hasInductedIntoMech(automationType, isPrimeSlot)) return null;
    LOGGER.error(
        PALLET_HAS_BEEN_INDUCTED_INTO_MECH + " location name={}, automationType={}, isPrimeSlot={}",
        locationName,
        automationType,
        isPrimeSlot);
    return new CancelContainerResponse(
        trackingId, VTR_ERROR_CODE, PALLET_HAS_BEEN_INDUCTED_INTO_MECH);
  }

  private CancelContainerResponse submitVtrToInventory(
      Container container, HttpHeaders httpHeaders) {
    String trackingId = container.getTrackingId();
    Map<String, String> containerItemMiscInfo =
        container.getContainerItems().get(0).getContainerItemMiscInfo();

    // Transfer PO validations
    boolean isTransferFromOssToMain =
        ReceivingUtils.isTransferMerchandiseFromOssToMain(containerItemMiscInfo);
    CancelContainerResponse cancelContainerResponse =
        validateTransferMerchandiseFromOSS(trackingId, isTransferFromOssToMain);
    if (nonNull(cancelContainerResponse)) {
      return cancelContainerResponse;
    }

    if (isTransferFromOssToMain) {
      return inventoryRestApiClient.notifyOssVtrToInventory(
          trackingId, containerItemMiscInfo, httpHeaders);
    } else {
      return inventoryRestApiClient.notifyVtrToInventory(trackingId, httpHeaders);
    }
  }

  private CancelContainerResponse validateTransferMerchandiseFromOSS(
      String trackingId, boolean isTransferFromOssToMain) {
    LOGGER.info("trackingId: {} isTransferFromOssToMain: {}", trackingId, isTransferFromOssToMain);
    CancelContainerResponse cancelContainerResponse = null;
    if (isTransferFromOssToMain && !gdcFlagReader.isOssVtrEnabled()) {
      cancelContainerResponse =
          new CancelContainerResponse(trackingId, VTR_ERROR_CODE, OSS_TRANSFER_PO_VTR_ERROR);
    }

    return cancelContainerResponse;
  }

  /**
   *
   *
   * <pre>
   * case	doVTR	haulStat		putawayStat		testCase?
   * 1		allow		NoHaul		NoPutaway
   * 2		allow		NoHaul		Pending		x
   * 3		allow		NoHaul		Open
   * 4		allow		NoHaul		OnHold
   * 5		allow		NoHaul		Cancelled
   * 1		allow		Open		NoPutaway
   * 2		allow		Open		Pending
   * 3		allow		Open		Open
   * 4		allow		Open		OnHold
   * 5		allow		Open		Cancelled		x
   * 1		allow		Pending		NoPutaway		x
   * 2		allow		Pending		Pending
   * 3		allow		Pending		Open
   * 4		allow		Pending		OnHold		x
   * 5		allow		Pending		Cancelled
   * 1		allow		OnHold		NoPutaway		X
   * 2		allow		OnHold		Pending
   * 3		allow		OnHold		Open
   * 4		allow		OnHold		OnHold
   * 5		allow		OnHold		Cancelled
   * 1		allow		Cancelled		NoPutaway
   * 2		allow		Cancelled		Pending
   * 3		allow		Cancelled		Open
   * 4		allow		Cancelled		OnHold
   * 5		allow		Cancelled		Cancelled		x
   * </pre>
   *
   * @param container
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  private CancelContainerResponse validateAgainstMoves(Container container, HttpHeaders httpHeaders)
      throws ReceivingException {
    // Check if the ENFORCE_MOVES_CHECK_FOR_VTR feature flag is enabled
    if (!configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ENFORCE_MOVES_CHECK_FOR_VTR, false)) return null;

    final String trackingId = container.getTrackingId();
    final List<Move> moves = mvClient.getMovesByContainerId(trackingId, httpHeaders);

    // Early return if there are no moves
    if (moves == null || moves.isEmpty()) return null;

    // Find haul and putaway moves
    final Move haul =
        moves.stream().filter(mv -> HAUL.equalsIgnoreCase(mv.getType())).findFirst().orElse(null);
    final Move putAway =
        moves
            .stream()
            .filter(mv -> PUTAWAY.equalsIgnoreCase(mv.getType()))
            .findFirst()
            .orElse(null);

    if (isMoveNullOpenPendingOnHoldCancelled(haul)
        && isMoveNullOpenPendingOnHoldCancelled(putAway)) {
      return null;
    }

    // Log error and return CancelContainerResponse if none of the conditions were met
    LOGGER.error(
        "Cannot VTR trackingId={} as pallet off the dock per Moves[{} & putAway={}]",
        trackingId,
        isMoveNullOpenPendingOnHoldCancelled(haul)
            ? "noHaul"
            : haul.getType() + "-" + haul.getStatus(),
        isMoveNullOpenPendingOnHoldCancelled(putAway)
            ? "noPutaway"
            : putAway.getType() + "-" + putAway.getStatus());

    return new CancelContainerResponse(
        trackingId, VTR_MOVE_INVALID_STATUS_CODE, VTR_MOVE_INVALID_STATUS_MSG);
  }

  /**
   * @param container
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  private CancelContainerResponse validateAgainstInventory(
      Container container, HttpHeaders httpHeaders) throws ReceivingException {
    if (!configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), ENFORCE_INVENTORY_CHECK_FOR_VTR, false)) return null;

    final String trackingId = container.getTrackingId();
    final InventoryContainerDetails inventoryContainerDetails =
        inventoryService.getInventoryContainerDetails(trackingId, httpHeaders);

    if (AVAILABLE.equalsIgnoreCase(inventoryContainerDetails.getContainerStatus())
        && (isNull(inventoryContainerDetails.getAllocatedQty())
            || inventoryContainerDetails.getAllocatedQty() == 0)) return null;

    LOGGER.error(
        "VTR validation failed against inventory as status is in available and qty not zero invInfo={}",
        inventoryContainerDetails);
    return new CancelContainerResponse(
        trackingId,
        VTR_MOVE_INVALID_STATUS_CODE,
        CANNOT_CANCEL_PALLET_AVAILABLE_ALLOCATED_QTY_NOT_ZERO_ERROR_MSG);
  }

  /**
   * Cancel label in GLS
   *
   * @param trackingId
   * @param containerItem
   * @param httpHeaders
   * @return CancelContainerResponse
   */
  private CancelContainerResponse cancelGlsPallet(
      String trackingId, ContainerItem containerItem, HttpHeaders httpHeaders) {
    try {
      glsRestApiClient.adjustOrCancel(
          glsRestApiClient.createGlsAdjustPayload(
              VTR,
              trackingId,
              0,
              conversionToVendorPack(
                  containerItem.getQuantity(),
                  containerItem.getQuantityUOM(),
                  containerItem.getVnpkQty(),
                  containerItem.getWhpkQty()),
              httpHeaders.get(USER_ID_HEADER_KEY).get(0)),
          httpHeaders);
    } catch (ReceivingException ex) {
      LOGGER.error(
          "Failed to call GLS errorCode {}, errorMsg {}",
          ex.getErrorResponse().getErrorCode(),
          ex.getErrorResponse().getErrorMessage());
      String errorMsg =
          StringUtils.isNotBlank(String.valueOf(ex.getErrorResponse().getErrorMessage()))
              ? String.valueOf(ex.getErrorResponse().getErrorMessage())
              : ReceivingException.VTR_ERROR_MSG;
      return new CancelContainerResponse(trackingId, ReceivingException.VTR_ERROR_CODE, errorMsg);
    } catch (Exception ex) {
      LOGGER.error(
          "{}Failed to cancel label in GLS with error={}, stackTrace={}",
          SPLUNK_ALERT,
          ex.getMessage(),
          getStackTrace(ex));
      return new CancelContainerResponse(
          trackingId, ReceivingException.VTR_ERROR_CODE, ReceivingException.VTR_ERROR_MSG);
    }

    return null;
  }

  /**
   * Send an adjustment to DCFIN
   *
   * @param container
   * @param httpHeaders
   * @return CancelContainerResponse
   */
  public CancelContainerResponse notifyVtrToDcFin(Container container, HttpHeaders httpHeaders) {
    final String txnId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    final DcFinAdjustRequest vtrRequest =
        createDcFinAdjustRequest(container, txnId, VTR_REASON_CODE, null);
    final Map<String, Object> header = getForwardablHeaderWithTenantData(httpHeaders);

    LOGGER.info("Calling dcfin adjust api for trackingId:{}", container.getTrackingId());
    dcFinRestApiClient.adjustOrVtr(vtrRequest, header);

    return null;
  }
}
