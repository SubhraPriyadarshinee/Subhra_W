package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.InstructionUtils.getDeliveryDocumentLine;
import static com.walmart.move.nim.receiving.core.common.InstructionUtils.getPrintJobWithWitronAttributes;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.GLS_RCV_INSTRUCTION_COMPLETED;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.InstructionStatus.COMPLETED;
import static com.walmart.move.nim.receiving.utils.constants.MoveEvent.CREATE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.RequestType.CANCEL;
import static com.walmart.move.nim.receiving.utils.constants.RequestType.COMPLETE;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.*;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingPalletBuildResponse;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import com.walmart.move.nim.receiving.witron.builder.ContainerLabelBuilder;
import com.walmart.move.nim.receiving.witron.config.WitronManagedConfig;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import com.walmart.move.nim.receiving.witron.model.ContainerLabel;
import com.walmart.move.nim.receiving.witron.model.GdcInstructionType;
import com.walmart.move.nim.receiving.witron.model.HaccpError;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

public class WitronInstructionService extends InstructionService {

  private static final Logger log = LoggerFactory.getLogger(WitronInstructionService.class);

  @Autowired private GdcSlottingServiceImpl slottingService;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private InstructionPersisterService instructionPersistService;
  @Autowired private DeliveryItemOverrideService deliveryItemOverrideService;
  @Autowired private PurchaseReferenceValidator purchaseReferenceValidator;
  @Autowired private ContainerLabelBuilder containerLabelBuilder;
  @Autowired private MovePublisher movePublisher;
  @Autowired private AsyncPersister asyncPersister;
  @Autowired private ReceiptService receiptService;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private DeliveryCacheServiceInMemoryImpl deliveryCacheServiceInMemoryImpl;
  @Autowired private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private WitronManagedConfig witronManagedConfig;
  @Autowired DeliveryDocumentHelper deliveryDocumentHelper;
  @Autowired GdcPutawayPublisher gdcPutawayPublisher;

  @Resource(name = "WitronLPNCacheService")
  private LPNCacheService lpnCacheService;

  @Override
  protected Instruction createInstructionForUpcReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    final String purchaseReferenceNumber = deliveryDocument.getPurchaseReferenceNumber();
    deliveryDocumentHelper.validateDeliveryDocument(deliveryDocument);

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    Instruction existingOpenInstruction = null;
    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, configUtils);
    throwExceptionIfContainerWithSsccReceived(instructionRequest);
    if (isKotlinEnabled) {
      existingOpenInstruction =
          instructionPersistService.fetchExistingOpenInstruction(
              deliveryDocument, instructionRequest, httpHeaders);
    } else {
      existingOpenInstruction =
          instructionPersistService.fetchExistingInstructionIfexists(instructionRequest);
    }
    if (nonNull(existingOpenInstruction)) return existingOpenInstruction;

    final String deliveryNumber = instructionRequest.getDeliveryNumber();
    final int purchaseReferenceLineNumber = deliveryDocumentLine.getPurchaseReferenceLineNumber();

    // ProblemReceiveFlow
    final String problemTagId = instructionRequest.getProblemTagId();
    boolean isGroceryProblemReceive = isGroceryProblemReceive(problemTagId);

    // validate Po line
    validateDocLine(deliveryNumber, deliveryDocumentLine, isGroceryProblemReceive, isKotlinEnabled);

    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), RECEIVE_AS_CORRECTION_FEATURE, false)) {
      // Check if we can receive as correction after PO confirmation
      purchaseReferenceValidator.validateReceiveAsCorrection(
          deliveryNumber, purchaseReferenceNumber, isGroceryProblemReceive, instructionRequest);
    } else {
      // PO state validation
      purchaseReferenceValidator.validatePOConfirmation(deliveryNumber, purchaseReferenceNumber);
      instructionRequest.setReceiveAsCorrection(false);
    }

    // Check BOL weight for line item
    if (ReceivingConstants.VARIABLE_WEIGHT_FORMAT_TYPE_CODE.equalsIgnoreCase(
        deliveryDocumentLine.getAdditionalInfo().getWeightFormatTypeCode())) {
      purchaseReferenceValidator.validateVariableWeight(deliveryDocumentLine);
    }

    // Get maxLimitToReceive and totalReceivedQty
    Pair<Integer, Long> receivedQtyDetails =
        instructionHelperService.getReceivedQtyDetails(problemTagId, deliveryDocumentLine);

    // Overage validations
    validateOverages(receivedQtyDetails, deliveryDocument, instructionRequest, isKotlinEnabled);

    long totalReceivedQty = receivedQtyDetails.getValue();
    // For kotlin send totalReceivedQty for given po, poLine in create instruction as well
    if (isKotlinEnabled) {
      deliveryDocumentLine.setTotalReceivedQty((int) totalReceivedQty);
    }
    final Integer totalOrderQty = deliveryDocumentLine.getTotalOrderQty();
    int openQty = totalOrderQty - (int) totalReceivedQty;

    // Grocery problems flow always display the line openQty
    long poLineReceivedQty = 0;
    if (isGroceryProblemReceive) {
      poLineReceivedQty =
          receiptService.getReceivedQtyByPoAndPoLine(
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
      log.info(
          "PTAG:{} poLineQty:{} poLineReceivedQty:{}",
          problemTagId,
          totalOrderQty,
          poLineReceivedQty);

      int poLineOpenQty = totalOrderQty - (int) poLineReceivedQty;
      openQty = poLineOpenQty < 0 ? 0 : poLineOpenQty;

      // Grocery problems flow always display the line receivedQty
      deliveryDocumentLine.setTotalReceivedQty((int) poLineReceivedQty);
    } else {
      int maxLimitToReceive =
          nonNull(deliveryDocumentLine.getOverageQtyLimit())
              ? totalOrderQty + deliveryDocumentLine.getOverageQtyLimit()
              : totalOrderQty;
      boolean isManagerOverrideIgnoreOverage =
          instructionHelperService.isManagerOverrideIgnoreOverage(
              deliveryNumber,
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
      if (!isManagerOverrideIgnoreOverage) {
        multiUserValidationWithPendingInstructions(
            purchaseReferenceNumber,
            purchaseReferenceLineNumber,
            totalReceivedQty,
            maxLimitToReceive,
            isKotlinEnabled);
      }
    }

    // Set calculated open quantity
    deliveryDocumentLine.setOpenQty(openQty);
    log.info(
        "deliveryNumber:{} poNbr:{} poLineNbr:{} problemTagId: {} totalReceivedQty:{} totalOrderQty:{} openQty:{}",
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        problemTagId,
        totalReceivedQty,
        totalOrderQty,
        openQty);

    // Get LPN
    TenantContext.get().setAtlasRcvLpnCallStart(System.currentTimeMillis());
    String lpn = lpnCacheService.getLPNBasedOnTenant(httpHeaders);
    TenantContext.get().setAtlasRcvLpnCallEnd(System.currentTimeMillis());

    if (StringUtils.isBlank(lpn)) {
      invalidLpnException();
    }

    Container container = containerService.findByTrackingId(lpn);
    if (nonNull(container)) {
      invalidLpnException();
    }

    // Get divert location
    Map<String, Object> headers = ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    SlottingPalletBuildResponse palletBuildResponse =
        slottingService.acquireSlot(instructionRequest, "AVAILABLE", lpn, headers);

    // Enrich the PalletTi from local DB if it's available.
    if (configUtils.isDeliveryItemOverrideEnabled(getFacilityNum())) {
      deliveryItemOverrideService
          .findByDeliveryNumberAndItemNumber(
              Long.parseLong(deliveryNumber), deliveryDocumentLine.getItemNbr())
          .ifPresent(
              deliveryItemOverride -> {
                deliveryDocumentLine.setPalletTie(deliveryItemOverride.getTempPalletTi());
              });
    }

    Instruction instruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            deliveryDocument,
            InstructionUtils.mapHttpHeaderToInstruction(
                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));

    // Container and containerLabel
    ContainerDetails containerDetails = getContainerDetails(lpn);
    ContainerLabel containerLabel =
        containerLabelBuilder.generateContainerLabel(
            lpn, palletBuildResponse.getDivertLocation(), deliveryDocumentLine, httpHeaders);
    updateContainerLabel(containerDetails, containerLabel);

    int projectedReceiveQty =
        getProjectedReceiveQty(instructionRequest, deliveryDocumentLine, problemTagId);

    final Integer projectedReceiveQtyInVnkp =
        ReceivingUtils.conversionToVendorPack(projectedReceiveQty, VNPK, 1, 1);
    instruction.setProjectedReceiveQty(projectedReceiveQtyInVnkp);
    instruction.setProjectedReceiveQtyUOM(VNPK);
    instruction.setPrintChildContainerLabels(false);
    instruction.setReceivedQuantityUOM(VNPK);
    instruction.setContainer(containerDetails);
    instruction.setProviderId("Witron");
    instruction.setActivityName("SSTK");
    instruction.setIsReceiveCorrection(instructionRequest.isReceiveAsCorrection());
    instruction.setLastChangeUserId(httpHeaders.getFirst(USER_ID_HEADER_KEY));
    if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      instruction.setSsccNumber(instructionRequest.getSscc());
    }

    // move
    LinkedTreeMap<String, Object> moveTreeMap =
        createMoveForInstruction(httpHeaders, lpn, palletBuildResponse);
    instruction.setMove(moveTreeMap);

    if (isKotlinEnabled) {
      instruction.setInstructionCode(
          GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionCode());
      instruction.setInstructionMsg(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionMsg());
    } else {
      instruction.setInstructionCode("Build Container");
      instruction.setInstructionMsg("Build Container");
    }

    // save
    instruction = instructionPersistService.saveInstruction(instruction);
    instructionHelperService.publishInstruction(
        instruction, null, null, null, InstructionStatus.CREATED, httpHeaders);

    return instruction;
  }

  /**
   * @param receivedQtyDetails
   * @param deliveryDocument
   * @param instructionRequest
   * @param isKotlinEnabled
   * @throws ReceivingException
   */
  private void validateOverages(
      Pair<Integer, Long> receivedQtyDetails,
      DeliveryDocument deliveryDocument,
      InstructionRequest instructionRequest,
      boolean isKotlinEnabled)
      throws ReceivingException {
    int maxLimitToReceive = receivedQtyDetails.getKey();
    long totalReceivedQty = receivedQtyDetails.getValue();
    String problemTagId = instructionRequest.getProblemTagId();
    String deliveryNbr = instructionRequest.getDeliveryNumber();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    String poNbr = deliveryDocumentLine.getPurchaseReferenceNumber();
    int poLineNbr = deliveryDocumentLine.getPurchaseReferenceLineNumber();
    log.info(
        "Validate overage for deliveryNbr:{} poNbr:{} poLineNbr:{} totalReceivedQty:{} maxLimitToReceive:{} isKotlinEnabled:{}",
        deliveryNbr,
        poNbr,
        poLineNbr,
        totalReceivedQty,
        maxLimitToReceive,
        isKotlinEnabled);
    if (isBlank(problemTagId)
        && (totalReceivedQty >= maxLimitToReceive)
        && !instructionHelperService.isManagerOverrideIgnoreOverage(
            deliveryNbr, poNbr, poLineNbr)) {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OVERAGE_ERROR);
      log.error(instructionError.getErrorMessage());
      if (isKotlinEnabled) {
        if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
          throw new ReceivingBadDataException(
              ExceptionCodes.ASN_PO_OVERAGES, ReceivingException.ASN_PO_OVERAGES);
        } else {
          throw new ReceivingException(
              instructionError.getErrorMessage(),
              INTERNAL_SERVER_ERROR,
              String.format(ExceptionCodes.AUTO_GROC_OVERAGE_ERROR, poNbr, poLineNbr));
        }

      } else {
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            (int) totalReceivedQty,
            maxLimitToReceive,
            deliveryDocument);
      }
    }
  }

  private boolean isGroceryProblemReceive(String problemTagId) {
    if (configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), GROCERY_PROBLEM_RECEIVE_FEATURE, false)
        && isNotBlank(problemTagId)) {
      return true;
    }
    return false;
  }

  /**
   * This method is responsible for witron specific completing the instruction flow. If already
   * instruction is already completed throws a exception with message.
   *
   * <p>It will completes instruction. It will completes container. It will publishes container. It
   * will publishes move. It will publish instruction to wfm.
   *
   * @param instructionId
   * @param httpHeaders
   * @return InstructionResponse
   * @throws ReceivingException
   */
  @SuppressWarnings("unchecked")
  public InstructionResponse completeInstruction(
      Long instructionId,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    // Getting instruction from DB.
    Instruction instructionFromDB = instructionPersisterService.getInstructionById(instructionId);
    final Boolean isReceiveAsCorrection = instructionFromDB.getIsReceiveCorrection();

    validateInstructionCompleted(instructionFromDB);

    final Long deliveryNumber = instructionFromDB.getDeliveryNumber();
    final String purchaseReferenceNumber = instructionFromDB.getPurchaseReferenceNumber();
    if (!isReceiveAsCorrection) {
      purchaseReferenceValidator.validatePOConfirmation(
          deliveryNumber.toString(), purchaseReferenceNumber);
    }

    final String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);
    ReceivingUtils.verifyUser(instructionFromDB, userId, COMPLETE);

    // Validate the deliveryCacheValue
    DeliveryCacheValue deliveryCacheValue =
        deliveryCacheServiceInMemoryImpl.getDeliveryDetailsByPoPoLine(
            deliveryNumber,
            purchaseReferenceNumber,
            instructionFromDB.getPurchaseReferenceLineNumber(),
            httpHeaders);

    if (isNull(deliveryCacheValue)) {
      log.error(
          "Something went wrong with DeliveryCacheValue for DELIVERY :{}, PO :{}, POLINE :{}",
          deliveryNumber,
          purchaseReferenceNumber,
          instructionFromDB.getPurchaseReferenceLineNumber());
      throw new ReceivingException(
          ReceivingException.GDM_SERVICE_DOWN,
          INTERNAL_SERVER_ERROR,
          ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE);
    }

    try {
      // Finalize PO RequestBody
      FinalizePORequestBody finalizePORequestBody = null;
      if (isReceiveAsCorrection) {
        // create request for FinalizePO with OSDR data for GDM ahead of ext api calls
        finalizePORequestBody =
            finalizePORequestBodyBuilder.buildFrom(
                deliveryNumber,
                purchaseReferenceNumber,
                getForwardablHeaderWithTenantData(httpHeaders));
      }
      // update RCV db, create print job
      instructionFromDB.setCompleteUserId(userId);
      instructionFromDB.setCompleteTs(new Date());
      java.util.Map<String, Object> instructionContainerMap =
          instructionPersisterService.completeAndCreatePrintJob(httpHeaders, instructionFromDB);

      Instruction instruction = (Instruction) instructionContainerMap.get(INSTRUCTION);

      Container parentContainer = (Container) instructionContainerMap.get(CONTAINER);
      // publish receipts to Inventory, Getting consolidated container and publish Container
      Container consolidatedContainer =
          containerService.getContainerIncludingChild(parentContainer);
      if (isReceiveAsCorrection) {
        // to inventory (inv to dcFin)
        postReceiptsReceiveAsCorrection(consolidatedContainer, httpHeaders);

        // post PO Finalize with OSDR data to GDM
        containerService.postFinalizePoOsdrToGdm(
            httpHeaders,
            consolidatedContainer.getDeliveryNumber(),
            consolidatedContainer.getContainerItems().get(0).getPurchaseReferenceNumber(),
            finalizePORequestBody);

        // Publish receipt update to SCT
        receiptPublisher.publishReceiptUpdate(
            consolidatedContainer.getTrackingId(), httpHeaders, Boolean.TRUE);
      } else {
        publishConsolidatedContainer(consolidatedContainer, httpHeaders, TRUE);
      }

      // Publishing move.
      if (instruction.getMove() != null && !instruction.getMove().isEmpty()) {
        movePublisher.publishMove(
            InstructionUtils.getMoveQuantity(consolidatedContainer),
            consolidatedContainer.getLocation(),
            httpHeaders,
            instruction.getMove(),
            CREATE.getMoveEvent());
      }
      String rotateDate =
          (!CollectionUtils.isEmpty(consolidatedContainer.getContainerItems())
                  && nonNull(consolidatedContainer.getContainerItems().get(0))
                  && nonNull(consolidatedContainer.getContainerItems().get(0).getRotateDate()))
              ? new SimpleDateFormat(PRINT_LABEL_ROTATE_DATE_MM_DD_YYYY)
                  .format(consolidatedContainer.getContainerItems().get(0).getRotateDate())
              : "-";
      String printerName =
          completeInstructionRequest != null ? completeInstructionRequest.getPrinterName() : "";
      String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(getFacilityNum());

      java.util.Map<String, Object> printJob =
          getPrintJobWithWitronAttributes(
              instructionFromDB, rotateDate, userId, printerName, dcTimeZone);

      // Publishing instruction. Instruction will be published based on feature flag. WFT
      instructionHelperService.publishInstruction(
          instruction, null, null, consolidatedContainer, COMPLETED, httpHeaders);

      // Send putaway request message
      gdcPutawayPublisher.publishMessage(consolidatedContainer, PUTAWAY_ADD_ACTION, httpHeaders);

      // Post receipts to DCFin backed by persistence
      if (!isReceiveAsCorrection) {
        DCFinService managedDcFinService =
            tenantSpecificConfigReader.getConfiguredInstance(
                String.valueOf(getFacilityNum()), DC_FIN_SERVICE, DCFinService.class);
        managedDcFinService.postReceiptsToDCFin(consolidatedContainer, httpHeaders, true);
      }

      return new InstructionResponseImplNew(null, null, instruction, printJob);

    } catch (Exception e) {
      log.error(
          "complete Instruction for Id={}, {} {}",
          instructionId,
          COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          COMPLETE_INSTRUCTION_ERROR_MSG, BAD_REQUEST, COMPLETE_INSTRUCTION_ERROR_CODE);
    }
  }

  /**
   * Http call to Inventory posting receipts. if http call fails it will retry 5 times
   *
   * @param container
   * @param httpHeaders
   */
  private void postReceiptsReceiveAsCorrection(Container container, HttpHeaders httpHeaders) {
    String url;
    if (configUtils.getConfiguredFeatureFlag(getFacilityNum().toString(), INV_V2_ENABLED, false)) {
      url = appConfig.getInventoryCoreBaseUrl() + INVENTORY_RECEIPT_RECEIVE_AS_CORRECTION;
    } else {
      url = appConfig.getInventoryBaseUrl() + INVENTORY_RECEIPT_RECEIVE_AS_CORRECTION;
    }

    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    httpHeaders.add(IDEM_POTENCY_KEY, container.getTrackingId());

    final Gson gson_inv = new GsonBuilder().setDateFormat(INVENTORY_DATE_FORMAT).create();
    final String receiptJsonValueAsString_inv = gson_inv.toJson(container);
    Map<String, Object> receiptRequestMap = new HashMap<>();
    receiptRequestMap.put(INVENTORY_RECEIPT, receiptJsonValueAsString_inv);
    final String jsonPayload = gson_inv.toJson(receiptRequestMap);
    asyncPersister.persistAsyncHttp(
        POST, url, jsonPayload, httpHeaders, RetryTargetFlow.INVENTORY_RECEIPT_RECEIVE_CORRECTION);
  }

  /**
   * Server calculates Projected Receive quantity gives Client to display on UI for receiving flow
   * its determined based on MaxTiHi Qty for Problems flow its determined based on MaxTiHi Qty and
   * ResolutionQty for the ProblemTagId#
   *
   * @param instructionRequest
   * @param deliveryDocumentLine
   * @param problemTagId
   * @return
   */
  private int getProjectedReceiveQty(
      InstructionRequest instructionRequest,
      DeliveryDocumentLine deliveryDocumentLine,
      String problemTagId) {
    int projectedReceiveQty;
    int calculatedQtyBasedOnTiHi =
        deliveryDocumentLine.getPalletHigh() * deliveryDocumentLine.getPalletTie();
    if (StringUtils.isBlank(problemTagId)) {
      if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
        projectedReceiveQty =
            Math.min(
                Math.min(calculatedQtyBasedOnTiHi, deliveryDocumentLine.getOpenQty()),
                deliveryDocumentLine.getShippedQty());
      } else {
        // Give always the Ti*Hi for efficient pallet
        projectedReceiveQty = calculatedQtyBasedOnTiHi;
      }
    } else {
      // PTAG flow : Build pallet with fixit's minimum resolutionQty or TiHi
      final int resolutionQty = instructionRequest.getResolutionQty();
      projectedReceiveQty =
          resolutionQty < calculatedQtyBasedOnTiHi ? resolutionQty : calculatedQtyBasedOnTiHi;
      log.info(
          "PTAG={} projectedReceiveQty={} TixHi={} fixit's minimum resolutionQty={}",
          problemTagId,
          projectedReceiveQty,
          calculatedQtyBasedOnTiHi,
          resolutionQty);
    }
    return projectedReceiveQty;
  }

  private void updateContainerLabel(
      ContainerDetails containerDetails, ContainerLabel containerLabel) {
    Map<String, Object> ctrLabel = new HashMap<>();
    ctrLabel.put("clientId", containerLabel.getClientId());
    ctrLabel.put("printRequests", containerLabel.getPrintRequests());
    containerDetails.setCtrLabel(ctrLabel);
  }

  private ContainerDetails getContainerDetails(String lpn) {
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setTrackingId(lpn);
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("AVAILABLE");
    containerDetails.setCtrReusable(false);
    containerDetails.setCtrShippable(false);
    containerDetails.setOutboundChannelMethod(GdcConstants.OUTBOUND_SSTK);
    return containerDetails;
  }

  private LinkedTreeMap<String, Object> createMoveForInstruction(
      HttpHeaders httpHeaders, String lpn, SlottingPalletBuildResponse palletBuildResponse) {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put("toLocation", palletBuildResponse.getDivertLocation());
    moveTreeMap.put(
        "correlationID", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    moveTreeMap.put("containerTag", lpn);
    moveTreeMap.put("lastChangedOn", new Date());
    moveTreeMap.put("lastChangedBy", httpHeaders.getFirst(USER_ID_HEADER_KEY));
    return moveTreeMap;
  }

  private void invalidLpnException() throws ReceivingException {
    InstructionError lpnInstructionError =
        InstructionErrorCode.getErrorValue(ReceivingException.INVALID_LPN_ERROR);
    throw new ReceivingException(
        lpnInstructionError.getErrorMessage(),
        HttpStatus.CONFLICT,
        lpnInstructionError.getErrorCode(),
        lpnInstructionError.getErrorHeader());
  }

  /**
   * Sanity check for deliveryDocumentLine
   *
   * @param deliveryNumber
   * @param deliveryDocumentLine
   * @param isGroceryProblemReceive
   * @param isKotlinEnabled
   * @throws ReceivingException
   */
  private void validateDocLine(
      String deliveryNumber,
      DeliveryDocumentLine deliveryDocumentLine,
      boolean isGroceryProblemReceive,
      boolean isKotlinEnabled)
      throws ReceivingException {

    validateWeightFormatType(deliveryDocumentLine);
    validatePromoBuyInd(deliveryDocumentLine);
    deliveryDocumentHelper.validatePoLineStatus(deliveryDocumentLine);
    deliveryDocumentHelper.validateTiHi(deliveryDocumentLine);
    validateHaccp(deliveryNumber, deliveryDocumentLine, isGroceryProblemReceive, isKotlinEnabled);
  }

  private void validateHaccp(
      String deliveryNumber,
      DeliveryDocumentLine deliveryDocumentLine,
      boolean isGroceryProblemReceive,
      boolean isKotlinEnabled)
      throws ReceivingException {

    if (isGroceryProblemReceive
        || !configUtils.getConfiguredFeatureFlag(getFacilityNum().toString(), HACCP_ENABLED, false))
      return; // Do nothing

    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    if (nonNull(itemData.getIsHACCP()) && itemData.getIsHACCP()) {

      final String purchaseReferenceNumber = deliveryDocumentLine.getPurchaseReferenceNumber();
      final int purchaseReferenceLineNumber = deliveryDocumentLine.getPurchaseReferenceLineNumber();

      if (configUtils.getConfiguredFeatureFlag(
          getFacilityNum().toString(), MANAGER_OVERRIDE_V2, false)) {
        if (witronDeliveryMetaDataService.isManagerOverrideV2(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, APPROVED_HACCP))
          return; // Do nothing
      } else {
        if (witronDeliveryMetaDataService.isManagerOverride(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, APPROVED_HACCP))
          return; // Do nothing
      }

      // throw exception
      final Long itemNbr = deliveryDocumentLine.getItemNbr();
      InstructionError haccpErr = InstructionErrorCode.getErrorValue(HACCP_ITEM_ALERT);

      final HaccpError haccpError =
          new HaccpError(
              deliveryNumber,
              purchaseReferenceNumber,
              purchaseReferenceLineNumber,
              itemNbr,
              deliveryDocumentLine.getDescription(),
              deliveryDocumentLine.getSecondaryDescription());
      final Gson gson = new Gson();
      final String haccpErrorJson = gson.toJson(haccpError);
      final String haccpErrMsg =
          String.format(haccpErr.getErrorMessage(), itemNbr, purchaseReferenceNumber);
      if (isKotlinEnabled) {
        throw new ReceivingException(
            haccpErrMsg,
            BAD_REQUEST,
            String.format(
                ExceptionCodes.AUTO_GROC_HACCP_ERROR,
                purchaseReferenceNumber,
                purchaseReferenceLineNumber),
            haccpErr.getErrorHeader(),
            haccpErrorJson);
      } else {
        throw new ReceivingException(
            haccpErrMsg,
            BAD_REQUEST,
            haccpErr.getErrorCode(),
            haccpErr.getErrorHeader(),
            haccpErrorJson);
      }
    }
  }

  /**
   * Sanity check for promoBuyInd
   *
   * @param deliveryDocumentLine
   * @throws ReceivingException
   */
  private void validatePromoBuyInd(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    if (deliveryDocumentLine.getPromoBuyInd() == null
        || StringUtils.isEmpty(deliveryDocumentLine.getPromoBuyInd())) {
      InstructionError instructionError =
          InstructionErrorCode.getErrorValue(ReceivingException.MISSING_ITEM_DETAILS);

      String errorMessage =
          String.format(instructionError.getErrorMessage(), deliveryDocumentLine.getItemNbr());
      log.error("Invalid promoBuyInd - {}", errorMessage);
      throw new ReceivingException(
          errorMessage,
          BAD_REQUEST,
          instructionError.getErrorCode(),
          instructionError.getErrorHeader());
    }
  }

  /**
   * Sanity check for item with changed weight format indicator
   *
   * @param deliveryDocumentLine
   * @throws ReceivingException
   */
  private void validateWeightFormatType(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    // Restrict Receiving an item with invalid data
    if (itemData == null) {
      InstructionError instructionError = InstructionErrorCode.getErrorValue("ITEM_DATA_MISSING");
      throw new ReceivingException(
          instructionError.getErrorMessage(),
          BAD_REQUEST,
          instructionError.getErrorCode(),
          instructionError.getErrorHeader());
    }
    // Validate item information
    if (StringUtils.isBlank(itemData.getProfiledWarehouseArea())
        || "NONE".equalsIgnoreCase(itemData.getProfiledWarehouseArea())) {
      InstructionError instructionError =
          InstructionErrorCode.getErrorValue(ReceivingException.NEW_ITEM_ERROR);
      String newItemErrorMessage =
          String.format(instructionError.getErrorMessage(), deliveryDocumentLine.getItemNbr());
      throw new ReceivingException(
          newItemErrorMessage,
          BAD_REQUEST,
          instructionError.getErrorCode(),
          instructionError.getErrorHeader());
    }

    if (itemData.getWeightFormatTypeCode() == null) {
      InstructionError instructionError =
          InstructionErrorCode.getErrorValue("WEIGHT_FORMAT_TYPE_CODE_MISSING");
      throw new ReceivingException(
          instructionError.getErrorMessage(),
          BAD_REQUEST,
          instructionError.getErrorCode(),
          instructionError.getErrorHeader());
    }

    // For fixed weight, block receiving when Item and PO do not agree on pack size
    if (ReceivingConstants.FIXED_WEIGHT_FORMAT_TYPE_CODE.equalsIgnoreCase(
            itemData.getWeightFormatTypeCode())
        && (deliveryDocumentLine.getVendorPack().intValue()
                != deliveryDocumentLine.getOrderableQuantity().intValue()
            || deliveryDocumentLine.getWarehousePack().intValue()
                != deliveryDocumentLine.getWarehousePackQuantity().intValue())) {
      InstructionError instructionError = InstructionErrorCode.getErrorValue("PO_ITEM_PACK_ERROR");
      throw new ReceivingException(
          instructionError.getErrorMessage(),
          BAD_REQUEST,
          instructionError.getErrorCode(),
          instructionError.getErrorHeader());
    }

    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), WEIGHT_FORMAT_TYPE_CHECK_FEATURE, false)) {
      final String omsOrMdmWeightFormatType =
          StringUtils.isNotEmpty(itemData.getOmsWeightFormatTypeCode())
              ? itemData.getOmsWeightFormatTypeCode()
              : itemData.getWeightFormatTypeCode();
      final String dcWeightFormatTypeCode = itemData.getDcWeightFormatTypeCode();

      if (StringUtils.isNotEmpty(dcWeightFormatTypeCode)
          && !dcWeightFormatTypeCode.equalsIgnoreCase(omsOrMdmWeightFormatType)) {
        InstructionError instructionError =
            InstructionErrorCode.getErrorValue(WEIGHT_FORMAT_TYPE_CODE_MISMATCH);

        String errorMessage =
            ReceivingConstants.FIXED_WEIGHT_FORMAT_TYPE_CODE.equalsIgnoreCase(
                    dcWeightFormatTypeCode)
                ? String.format(instructionError.getErrorMessage(), FIXED, VARIABLE)
                : String.format(instructionError.getErrorMessage(), VARIABLE, FIXED);
        throw new ReceivingException(
            errorMessage,
            BAD_REQUEST,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
      }
    }
  }

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public InstructionSummary cancelInstruction(Long instructionId, HttpHeaders httpHeaders)
      throws ReceivingException {
    try {
      Instruction instruction = instructionPersisterService.getInstructionById(instructionId);
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(), IS_GDC_CANCEL_INSTRUCTION_ERROR_ENABLED, false)) {
        instructionStateValidator.validate(instruction, GLS_RCV_INSTRUCTION_COMPLETED, BAD_REQUEST);
      } else {
        instructionStateValidator.validate(instruction);
      }

      final int cancelQuantity = instruction.getReceivedQuantity();
      final String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);
      ReceivingUtils.verifyUser(instruction, userId, CANCEL);
      // Complete instruction with received quantity as ZERO
      instruction.setReceivedQuantity(0);
      instruction.setCompleteUserId(httpHeaders.getFirst(USER_ID_HEADER_KEY));
      instruction.setCompleteTs(new Date());
      Instruction cancelledInstruction = instructionRepository.save(instruction);

      addReceiptForCancelInstruction(instruction, cancelQuantity, userId);

      return InstructionUtils.convertToInstructionSummary(cancelledInstruction);

    } catch (ReceivingException re) {
      Object errorMessage = re.getErrorResponse().getErrorMessage();
      String errorCode = re.getErrorResponse().getErrorCode();
      String errorHeader = re.getErrorResponse().getErrorHeader();
      final HttpStatus httpStatus = re.getHttpStatus();
      throw new ReceivingException(
          !org.springframework.util.StringUtils.isEmpty(errorMessage)
              ? errorMessage
              : ReceivingException.CANCEL_INSTRUCTION_ERROR_MSG,
          httpStatus != null ? httpStatus : INTERNAL_SERVER_ERROR,
          !org.springframework.util.StringUtils.isEmpty(errorCode)
              ? errorCode
              : ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE,
          !org.springframework.util.StringUtils.isEmpty(errorHeader)
              ? errorHeader
              : ReceivingException.CANCEL_INSTRUCTION_ERROR_HEADER);
    } catch (Exception exception) {
      log.error("{} {}", ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE, exception);
      throw new ReceivingException(
          ReceivingException.CANCEL_INSTRUCTION_ERROR_MSG,
          INTERNAL_SERVER_ERROR,
          ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE);
    }
  }

  private void addReceiptForCancelInstruction(
      Instruction instruction, int cancelQuantity, String userId) {
    // if cancel instructions received quantity > 0 then add new receipt with -ve qty
    if (cancelQuantity > 0) {
      final Long deliveryNumber = instruction.getDeliveryNumber();
      final String purchaseReferenceNumber = instruction.getPurchaseReferenceNumber();
      final Integer purchaseReferenceLineNumber = instruction.getPurchaseReferenceLineNumber();
      log.info(
          "add -ve receipt for cancel InstructionId={} with qty={} by user={} for delivery={}, po={}, poLine={}",
          instruction.getId(),
          cancelQuantity,
          userId,
          deliveryNumber,
          purchaseReferenceNumber,
          purchaseReferenceLineNumber);

      DeliveryDocumentLine deliveryDoc = getDeliveryDocumentLine(instruction);
      final Integer vendorPack = deliveryDoc.getVendorPack();
      final Integer warehousePack = deliveryDoc.getWarehousePack();
      int quantityInEA = conversionToEaches(cancelQuantity, VNPK, vendorPack, warehousePack);

      Receipt receipt = new Receipt();
      receipt.setDeliveryNumber(deliveryNumber);
      receipt.setPurchaseReferenceNumber(purchaseReferenceNumber);
      receipt.setPurchaseReferenceLineNumber(purchaseReferenceLineNumber);
      receipt.setEachQty(-1 * quantityInEA);
      receipt.setQuantity(-1 * cancelQuantity);
      receipt.setQuantityUom(VNPK);
      receipt.setVnpkQty(vendorPack);
      receipt.setWhpkQty(warehousePack);
      receipt.setCreateUserId(userId);
      receipt.setProblemId(instruction.getProblemTagId());

      receiptService.saveReceipt(receipt);
    }
  }

  /**
   * @param poNumber
   * @param poLineNumber
   * @param totalReceivedQty
   * @param maxLimitToReceive
   * @param isKotlinEnabled
   * @throws ReceivingException
   */
  private void multiUserValidationWithPendingInstructions(
      String poNumber,
      int poLineNumber,
      long totalReceivedQty,
      int maxLimitToReceive,
      boolean isKotlinEnabled)
      throws ReceivingException {
    Long totalOpenProjectedReceiveQty =
        instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            poNumber, poLineNumber);

    if (totalOpenProjectedReceiveQty != null
        && totalOpenProjectedReceiveQty > 0
        && (totalOpenProjectedReceiveQty + totalReceivedQty) >= maxLimitToReceive) {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.MULTI_USER_ERROR);
      log.error(instructionError.getErrorMessage());
      if (isKotlinEnabled) {
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            INTERNAL_SERVER_ERROR,
            REQUEST_TRANSFTER_INSTR_ERROR_CODE,
            instructionError.getErrorHeader());
      } else {
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
      }
    }
  }

  private void throwExceptionIfContainerWithSsccReceived(InstructionRequest instructionRequest) {
    if (!InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      return;
    }
    String sscc = instructionRequest.getSscc();
    if (StringUtils.isNotEmpty(sscc)) {
      int receivedQuantityForSSCC = containerService.receivedContainerQuantityBySSCC(sscc);
      if (receivedQuantityForSSCC > 0) {
        throw new ReceivingBadDataException(
            ExceptionCodes.SSCC_RECEIVED_ALREADY,
            String.format(ReceivingException.RECEIVE_INSTRUCTION_EXCEEDS_SSCC_LIMIT, sscc),
            sscc);
      }
    }
  }
}
