package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.InstructionUtils.getDeliveryDocumentLine;
import static com.walmart.move.nim.receiving.core.common.InstructionUtils.getPrintJobWithWitronAttributes;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.InstructionStatus.COMPLETED;
import static com.walmart.move.nim.receiving.utils.constants.MoveEvent.CREATE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.RequestType.CANCEL;
import static com.walmart.move.nim.receiving.utils.constants.RequestType.COMPLETE;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.GDC_PROVIDER_ID;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.GLS;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.VIRTUAL_PRIME_SLOT_KEY_INTO_OSS;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.WITRON;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.ObjectUtils.anyNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsLpnRequest;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsLpnResponse;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingPalletBuildResponse;
import com.walmart.move.nim.receiving.core.common.DeliveryDocumentHelper;
import com.walmart.move.nim.receiving.core.common.GdcPutawayPublisher;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceiptPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceiveAllRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveAllResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.core.service.DCFinService;
import com.walmart.move.nim.receiving.core.service.DefaultDeliveryDocumentSelector;
import com.walmart.move.nim.receiving.core.service.DeliveryItemOverrideService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.ReceiveInstructionHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import com.walmart.move.nim.receiving.witron.builder.ContainerLabelBuilder;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.common.GdcUtil;
import com.walmart.move.nim.receiving.witron.config.WitronManagedConfig;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import com.walmart.move.nim.receiving.witron.model.ContainerLabel;
import com.walmart.move.nim.receiving.witron.model.GdcInstructionType;
import com.walmart.move.nim.receiving.witron.model.HaccpError;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@SuppressWarnings("SpellCheckingInspection")
public class GdcInstructionService extends InstructionService {

  private static final Logger log = LoggerFactory.getLogger(GdcInstructionService.class);

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
  @Autowired private Gson gson;
  @Autowired private DefaultDeliveryDocumentSelector defaultDeliveryDocumentSelector;
  @Autowired private ProblemReceivingHelper problemReceivingHelper;
  @Autowired private GDCFlagReader gdcFlagReader;
  @Autowired private InventoryRestApiClient inventoryRestApiClient;

  @Resource(name = ReceivingConstants.GDC_RECEIVE_INSTRUCTION_HANDLER)
  private ReceiveInstructionHandler gdcReceiveInstructionHandler;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Resource(name = "WitronLPNCacheService")
  private LPNCacheService lpnCacheService;

  /**
   * @param instructionRequestString request object containing delivery, upc or po line details
   * @param httpHeaders headers that need to be forwarded
   * @return PO line or instruction
   * @throws ReceivingException
   */
  public InstructionResponse serveInstructionRequest(
      String instructionRequestString, HttpHeaders httpHeaders) throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(instructionRequestString, InstructionRequest.class);

    // Request payload validation
    if (StringUtils.isEmpty(instructionRequest.getUpcNumber())
        && StringUtils.isEmpty(instructionRequest.getSscc())) {
      if (StringUtils.isEmpty(instructionRequest.getUpcNumber())) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.NO_UPC_ERROR);
        log.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
      } else if (StringUtils.isEmpty(instructionRequest.getSscc())) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.NO_SSCC_ERROR);
        log.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            InstructionError.NO_SSCC_ERROR.getErrorMessage(),
            HttpStatus.BAD_REQUEST,
            InstructionError.NO_SSCC_ERROR.getErrorCode(),
            InstructionError.NO_SSCC_ERROR.getErrorHeader());
      }
    }

    // PTAG handler
    if (Objects.nonNull(instructionRequest.getProblemTagId())) {
      return servePtagInstructionRequest(instructionRequest, httpHeaders);
    }

    Instruction instruction = null;
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    if (!CollectionUtils.isEmpty(instructionRequest.getDeliveryDocuments())) {
      // Request payload contains deliveryDocuments
      // (Assumption: There will be only one document and document line for now)
      log.info("Calling fetchSpecificDeliveryDocument to get the latest deliveryDocument from GDM");
      DeliveryDocument deliveryDocument =
          fetchSpecificDeliveryDocument(instructionRequest, httpHeaders);
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);

      // Check delivery ownership
      checkDeliveryOwnership(deliveryDocument);

      // Check if Delivery is in receivable state
      GdcUtil.checkIfDeliveryStatusReceivable(deliveryDocument);

      // Check if Line is in receivable state
      checkIfLineIsRejected(deliveryDocument.getDeliveryDocumentLines().get(0));

      // Set the latest deliveryDocument to request paylaod
      instructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));

      // Override & get existing/new instruction for GDC
      instruction = createInstructionForUpcReceiving(instructionRequest, httpHeaders);

      // Set the response
      instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
      instructionResponse.setInstruction(instruction);

      // Returning the response back to client
      return instructionResponse;
    }

    // Request payload doesn't contains deliveryDocument
    log.info("Calling fetchDeliveryDocument to get the line item details from GDM");
    List<DeliveryDocument> gdmDeliveryDocuments =
        fetchDeliveryDocument(instructionRequest, httpHeaders);
    DeliveryDocument gdmDeliveryDocument = gdmDeliveryDocuments.get(0);

    // Check delivery ownership
    checkDeliveryOwnership(gdmDeliveryDocument);

    // Check if Delivery is in receivable state
    GdcUtil.checkIfDeliveryStatusReceivable(gdmDeliveryDocument);

    // Update delivery docs
    gdmDeliveryDocuments = deliveryDocumentHelper.updateDeliveryDocuments(gdmDeliveryDocuments);

    // Check for single/multi PO
    if (isSinglePO(gdmDeliveryDocuments)) {
      validateDocumentLineExists(gdmDeliveryDocuments);
      // Check for single/multi PoLine
      if (isSinglePoLine(gdmDeliveryDocument)
          && !InstructionUtils.isCancelledPOOrPOLine(gdmDeliveryDocument)) {
        // Single PO/PoLine scenario: continue to create an instruction
        log.info(
            "gdmDeliveryDocument is a Single PO/PoLine and not in cancelled PO or Line status");
        // Check if Line is in receivable state
        checkIfLineIsRejected(gdmDeliveryDocument.getDeliveryDocumentLines().get(0));

        // Set the latest deliveryDocument to request paylaod
        instructionRequest.setDeliveryDocuments(gdmDeliveryDocuments);

        // Override & get existing/new instruction for GDC
        instruction = createInstructionForUpcReceiving(instructionRequest, httpHeaders);

        // Set total received quantity before returning the response
        instructionResponse.setInstruction(instruction);
        DeliveryDocument document =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine documentLine = document.getDeliveryDocumentLines().get(0);
        gdmDeliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .setTotalReceivedQty(documentLine.getTotalReceivedQty());
        instructionResponse.setDeliveryDocuments(gdmDeliveryDocuments);
      } else {
        log.info("Multiple PO lines found for scanned UPC: {}", instructionRequest.getUpcNumber());
        // Multi PoLines scenario: send the lines back to client and ask for PoLine selection
        enrichDataForManualPoLineSelection(instructionResponse, gdmDeliveryDocuments);
      }
    } else {
      log.info("Multiple PO's found for scanned UPC: {}", instructionRequest.getUpcNumber());
      // Multi PO scenario: send the PO's back to client and ask for PO/PoLine selection
      enrichDataForManualPoLineSelection(instructionResponse, gdmDeliveryDocuments);
    }

    return instructionResponse;
  }

  /**
   * Enrich TotalReceivedQty and InstructionCode
   *
   * @param instructionResponse
   * @param gdmDeliveryDocuments
   */
  private void enrichDataForManualPoLineSelection(
      InstructionResponse instructionResponse, List<DeliveryDocument> gdmDeliveryDocuments) {
    // Set TotalReceivedQty
    for (DeliveryDocument gdmDeliveryDoc : gdmDeliveryDocuments) {
      for (DeliveryDocumentLine gdmDeliveryDocLine : gdmDeliveryDoc.getDeliveryDocumentLines()) {
        Long receiveQty =
            receiptService.receivedQtyByDeliveryPoAndPoLine(
                gdmDeliveryDoc.getDeliveryNumber(),
                gdmDeliveryDocLine.getPurchaseReferenceNumber(),
                gdmDeliveryDocLine.getPurchaseReferenceLineNumber());
        gdmDeliveryDocLine.setTotalReceivedQty(receiveQty.intValue());
      }
    }
    instructionResponse.setDeliveryDocuments(gdmDeliveryDocuments);

    // Set InstructionCode
    if (isNull(instructionResponse.getInstruction())) {
      final Instruction newInstruction = new Instruction();
      newInstruction.setInstructionCode(MANUAL_PO_SELECTION);
      instructionResponse.setInstruction(newInstruction);
    } else {
      instructionResponse.getInstruction().setInstructionCode(MANUAL_PO_SELECTION);
    }
  }

  public InstructionResponse serveInstructionRequestIntoOss(
      Long deliveryNumber, HttpHeaders httpHeaders, List<DeliveryDocument> gdmOnePoOneLineDoc)
      throws ReceivingException {

    Instruction instruction = null;
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    validateDocument(gdmOnePoOneLineDoc);
    DeliveryDocument gdmDeliveryDocument = gdmOnePoOneLineDoc.get(0);

    // Check if Delivery is in receivable state
    InstructionUtils.checkIfDeliveryStatusReceivable(gdmDeliveryDocument);

    // Update delivery docs
    gdmOnePoOneLineDoc = deliveryDocumentHelper.updateDeliveryDocuments(gdmOnePoOneLineDoc);

    // Check for single/multi PO
    validateDocumentLineExists(gdmOnePoOneLineDoc);
    // isSinglePoLine
    if (InstructionUtils.isCancelledPOOrPOLine(gdmDeliveryDocument)) {
      throw new ReceivingBadDataException("CANCELLED-LINE", "can't receive a cancelled line");
    }
    // Check if Line is in receivable state
    checkIfLineIsRejected(gdmDeliveryDocument.getDeliveryDocumentLines().get(0));

    // createInstructionRequest with updated deliveryDocument
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    instructionRequest.setDeliveryNumber(deliveryNumber.toString());
    instructionRequest.setDeliveryDocuments(gdmOnePoOneLineDoc);

    // Override & get existing/new instruction for GDC
    instruction = createInstructionIntoOss(instructionRequest, httpHeaders);

    // Set total received quantity before returning the response
    instructionResponse.setInstruction(instruction);
    final Integer totalReceivedQty =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalReceivedQty();
    gdmOnePoOneLineDoc
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTotalReceivedQty(totalReceivedQty);
    instructionResponse.setDeliveryDocuments(gdmOnePoOneLineDoc);

    return instructionResponse;
  }

  /**
   * @param instructionRequest instruction request object from client
   * @param httpHeaders http headers received from client
   * @return Instruction
   * @throws ReceivingException
   */
  protected Instruction createInstructionForUpcReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    // Check if SSCC_RECEIVED_ALREADY
    throwExceptionIfContainerWithSsccReceived(instructionRequest);

    final List<DeliveryDocument> deliveryDocumentsUi = instructionRequest.getDeliveryDocuments();
    DeliveryDocument deliveryDocument = deliveryDocumentsUi.get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    final Long itemNbr = deliveryDocumentLine.getItemNbr();
    deliveryDocumentHelper.validateDeliveryDocument(deliveryDocument);

    // Check for any open instruction
    Instruction existingOpenInstruction =
        instructionPersistService.fetchExistingOpenInstruction(
            deliveryDocument, instructionRequest, httpHeaders);
    if (nonNull(existingOpenInstruction)) return existingOpenInstruction;

    final String deliveryNumber = instructionRequest.getDeliveryNumber();
    final String purchaseReferenceNumber = deliveryDocument.getPurchaseReferenceNumber();
    final int purchaseReferenceLineNumber = deliveryDocumentLine.getPurchaseReferenceLineNumber();
    final String problemTagId = instructionRequest.getProblemTagId();
    boolean isGroceryProblemReceive = isGroceryProblemReceive(problemTagId);
    String inventoryBohQty = null;
    // validate Po line
    validateDocLine(deliveryNumber, deliveryDocumentLine, isGroceryProblemReceive);

    // Update delivery docs with ItemData metaData
    final boolean isOneAtlas = gdcFlagReader.isDCOneAtlasEnabled();
    if (isOneAtlas) {
      instructionUtils.updateDeliveryDocForAtlasConvertedItems(
          deliveryDocumentsUi, httpHeaders, gdcFlagReader.isItemConfigApiEnabled());
    }

    // Block Receiving from OSS if PO finalized
    isOSSPOAlreadyFinalized(deliveryDocument, deliveryDocumentLine);

    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), RECEIVE_AS_CORRECTION_FEATURE, false)) {
      purchaseReferenceValidator.validateReceiveAsCorrection(
          deliveryNumber, purchaseReferenceNumber, isGroceryProblemReceive, instructionRequest);
    } else {
      // PO state validation
      purchaseReferenceValidator.validatePOConfirmation(deliveryNumber, purchaseReferenceNumber);
      instructionRequest.setReceiveAsCorrection(false);
    }

    // Check if Line Not on BOL
    purchaseReferenceValidator.checkIfPOLineNotOnBOL(
        deliveryDocumentLine, instructionRequest.isReceiveAsCorrection());

    // Check BOL weight for line item
    ReceivingUtils.validateVariableWeightForVariableItem(deliveryDocumentLine);

    // Get maxLimitToReceive and totalReceivedQty
    Pair<Integer, Long> receivedQtyDetails =
        instructionHelperService.getReceivedQtyDetails(problemTagId, deliveryDocumentLine);
    // If OSS transfer - check INV Boh qty
    if (GdcUtil.isDCCanReceiveOssPO(
        deliveryDocument, deliveryDocumentLine, configUtils, gdcFlagReader)) {
      if (isBlank(deliveryDocumentLine.getFromOrgUnitId())) {
        InstructionError invalidSubcenterID =
            InstructionErrorCode.getErrorValue(INVALID_SUBCENTER_ID);
        final String errorMessage = invalidSubcenterID.getErrorMessage();
        log.error(errorMessage);
        throw new ReceivingBadDataException(invalidSubcenterID.getErrorCode(), errorMessage);
      }
      inventoryBohQty =
          inventoryRestApiClient.getInventoryBohQtyByItem(
              itemNbr,
              deliveryDocument.getBaseDivisionCode(),
              deliveryDocument.getFinancialReportingGroup(),
              deliveryDocumentLine.getFromOrgUnitId(),
              httpHeaders);
      validateOveragesForOSS(inventoryBohQty);
      // add boh qty in documentline used for UI validation
      deliveryDocumentLine.setInvBohQty(
          conversionToVendorPackRoundUp(
              Integer.valueOf(inventoryBohQty),
              ReceivingConstants.Uom.EACHES,
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
    } else {
      // Overage validations
      validateOverages(receivedQtyDetails, deliveryDocument, instructionRequest);
    }

    // Calculate open quantity
    final Integer totalOrderQty = deliveryDocumentLine.getTotalOrderQty();
    long totalReceivedQty = receivedQtyDetails.getValue();
    int openQty = totalOrderQty - (int) totalReceivedQty;

    // Send totalReceivedQty for given po/poLine
    deliveryDocumentLine.setTotalReceivedQty((int) totalReceivedQty);

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
      openQty = Math.max(poLineOpenQty, 0);

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
            maxLimitToReceive);
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

    // Enrich the PalletTi and High from local DB if it's available.
    deliveryDocumentLine.setActualHi(deliveryDocumentLine.getPalletHigh());
    deliveryDocumentLine.setActualTi(deliveryDocumentLine.getPalletTie());
    if (configUtils.isDeliveryItemOverrideEnabled(getFacilityNum())) {
      deliveryItemOverrideService
          .findByDeliveryNumberAndItemNumber(Long.parseLong(deliveryNumber), itemNbr)
          .ifPresent(
              deliveryItemOverride -> {
                deliveryDocumentLine.setPalletTiHiVersion(deliveryItemOverride.getVersion());
                deliveryDocumentLine.setPalletTie(deliveryItemOverride.getTempPalletTi());
                // For backward compatibility
                if (Objects.nonNull(deliveryItemOverride.getTempPalletHi())) {
                  deliveryDocumentLine.setPalletHigh(deliveryItemOverride.getTempPalletHi());
                }
              });
    }

    // Prepare new instruction
    Instruction instruction = constructInstruction(instructionRequest, httpHeaders);

    // Set projected receive quantity
    instruction.setProjectedReceiveQty(
        getProjectedReceiveQty(instructionRequest, deliveryDocumentLine, problemTagId));
    instruction.setProjectedReceiveQtyUOM(VNPK);

    // Given delivery with single item on one or more PO's for entire delivery
    // When user selects "Receive All" option from context menu
    // Then return dummy instruction without persit into DB
    if (instructionRequest.isReceiveAll()) {
      return instruction;
    }

    // Get and Set lpn/slot from Atlas or Gls len generator with slotting integration
    setLPNAndSlottingData(instruction, instructionRequest, isOneAtlas, httpHeaders);

    // Set manual gdc specific instruction data
    setGDCInstructionData(
        instruction, deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);

    // Persist instruction
    instruction = instructionPersistService.saveInstruction(instruction);

    // Publish instruction to WFT
    if (!gdcFlagReader.publishToWFTDisabled())
      instructionHelperService.publishInstruction(
          instruction, null, null, null, InstructionStatus.CREATED, httpHeaders);

    return instruction;
  }

  protected Instruction createInstructionIntoOss(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {

    final List<DeliveryDocument> deliveryDocumentsUi = instructionRequest.getDeliveryDocuments();
    DeliveryDocument deliveryDocument = deliveryDocumentsUi.get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentHelper.validatePoStatus(deliveryDocument);
    final String deliveryNumber = instructionRequest.getDeliveryNumber();
    final String po = deliveryDocument.getPurchaseReferenceNumber();
    final int lineNum = deliveryDocumentLine.getPurchaseReferenceLineNumber();

    instructionPersistService.cancelOpenInstructionsIfAny(
        Long.valueOf(deliveryNumber), po, lineNum, httpHeaders);

    // validate Po line
    validateDocLineIntoOss(deliveryDocumentLine);

    // Update delivery docs with ItemData metaData
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    if (isNull(itemData)) {
      itemData = new ItemData();
    }
    itemData.setAtlasConvertedItem(true);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    // PO state validation
    purchaseReferenceValidator.validatePOConfirmation(deliveryNumber, po);
    instructionRequest.setReceiveAsCorrection(false);

    // Check BOL weight for line item
    if (VARIABLE_WEIGHT_FORMAT_TYPE_CODE.equalsIgnoreCase(
        deliveryDocumentLine.getAdditionalInfo().getWeightFormatTypeCode())) {
      purchaseReferenceValidator.validateVariableWeight(deliveryDocumentLine);
    }

    // Prepare new instruction
    Instruction instruction = constructInstruction(instructionRequest, httpHeaders);

    // create ContainerDetails
    log.info(
        "deliveryNumber:{} poNbr:{} poLineNbr:{}",
        instructionRequest.getDeliveryNumber(),
        deliveryDocument.getPurchaseReferenceNumber(),
        lineNum);
    ContainerDetails newContainerDetails = createNewContainerDetails(null);
    newContainerDetails.setCtrDestination(
        createSlotForIntoOss(
            httpHeaders.containsKey(ORG_UNIT_ID_HEADER)
                    && isNotBlank(httpHeaders.getFirst(ORG_UNIT_ID_HEADER))
                ? httpHeaders.getFirst(ORG_UNIT_ID_HEADER)
                : httpHeaders.getFirst(SUBCENTER_ID_HEADER)));
    instruction.setContainer(newContainerDetails);

    // Persist instruction
    instruction = instructionPersistService.saveInstruction(instruction);
    final Long instructionId = instruction.getId();
    log.info(
        "created Instruction intoOss id={} for delivery={}, po={}, lineNum={}",
        instructionId,
        deliveryNumber,
        po,
        lineNum);
    return instruction;
  }

  private Map<String, String> createSlotForIntoOss(String orgUnitId) {
    Map<String, String> destinationMap = new HashMap<>();
    destinationMap.put(SLOT_TYPE, VIRTUAL_PRIME_SLOT_KEY_INTO_OSS);
    destinationMap.put(SLOT, gdcFlagReader.getVirtualPrimeSlotForIntoOss(orgUnitId));
    return destinationMap;
  }

  private void setLPNAndSlottingData(
      Instruction instruction,
      InstructionRequest instructionRequest,
      boolean isOneAtlas,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    final List<DeliveryDocument> deliveryDocumentsUi = instructionRequest.getDeliveryDocuments();
    DeliveryDocument deliveryDocument = deliveryDocumentsUi.get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    final Long itemNbr = deliveryDocumentLine.getItemNbr();

    final boolean isAtlasLpnGenDisabled = gdcFlagReader.isLpnGenApiDisabled();
    final boolean isAtlasConvertedItem =
        deliveryDocumentHelper.isAtlasConvertedItemInFirstDocFirstLine(deliveryDocumentsUi);
    log.info(
        "deliveryNumber:{} poNbr:{} poLineNbr:{} isAtlasLpnGenDisabled:{} isOneAtlas:{} isAtlasConvertedItem:{}",
        instructionRequest.getDeliveryNumber(),
        deliveryDocument.getPurchaseReferenceNumber(),
        deliveryDocumentLine.getPurchaseReferenceLineNumber(),
        isAtlasLpnGenDisabled,
        isOneAtlas,
        isAtlasConvertedItem);
    if (!isAtlasLpnGenDisabled) {
      // Atlas lpn generator enabled, get the palletTagId
      String lpn = getPalletTag(httpHeaders);

      final boolean isSmartSlottingDisabled = gdcFlagReader.isSmartSlottingApiDisabled();
      log.info(
          "deliveryNumber:{} poNbr:{} poLineNbr:{} isSmartSlottingDisabled: {}",
          instructionRequest.getDeliveryNumber(),
          deliveryDocument.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber(),
          isSmartSlottingDisabled);

      SlottingPalletBuildResponse getDivertLocationsResponse = null;
      if (!isSmartSlottingDisabled) {
        // Atlas smart slotting enabled, get the divert location
        getDivertLocationsResponse =
            slottingService.acquireSlot(
                instructionRequest,
                AVAILABLE,
                lpn,
                ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders));

        // Prepare the move
        LinkedTreeMap<String, Object> moveTreeMap =
            createMoveForInstruction(httpHeaders, lpn, getDivertLocationsResponse);
        instruction.setMove(moveTreeMap);
      }

      // Prepare the container label based on witron label format
      ContainerDetails containerDetails = createNewContainerDetails(lpn);
      String slotId =
          nonNull(getDivertLocationsResponse) ? getDivertLocationsResponse.getDivertLocation() : "";
      ContainerLabel containerLabel =
          containerLabelBuilder.generateContainerLabel(
              lpn, slotId, deliveryDocumentLine, httpHeaders);
      updateContainerLabel(containerDetails, containerLabel);
      instruction.setContainer(containerDetails);
    } else {
      if (isOneAtlas && isAtlasConvertedItem) {
        ContainerDetails containerDetails =
            createContainerWithGlsLpn(
                instructionRequest.getDeliveryNumber(),
                deliveryDocument.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                itemNbr,
                httpHeaders);
        ContainerLabel containerLabel =
            containerLabelBuilder.generateContainerLabel(
                containerDetails.getTrackingId(), "", deliveryDocumentLine, httpHeaders);
        updateContainerLabel(containerDetails, containerLabel);
        instruction.setContainer(containerDetails);
      }
    }
  }

  private void setGDCInstructionData(
      Instruction instruction,
      String deliveryNumber,
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber) {
    final boolean isManualGdcEnabled = gdcFlagReader.isManualGdcEnabled();
    log.info(
        "deliveryNumber:{} poNbr:{} poLineNbr:{} isManualGdcEnabled: {}",
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        isManualGdcEnabled);

    // Set manual gdc specific instruction data
    if (isManualGdcEnabled) {
      instruction.setProviderId(GDC_PROVIDER_ID);
      instruction.setInstructionCode(
          GdcInstructionType.MANL_GROC_BUILD_PALLET.getInstructionCode());
      instruction.setInstructionMsg(GdcInstructionType.MANL_GROC_BUILD_PALLET.getInstructionMsg());
    }
  }

  private ContainerDetails createContainerWithGlsLpn(
      String deliveryNumber,
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber,
      Long itemNbr,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    // Create new Gls lpn request
    GlsLpnRequest request =
        new GlsLpnRequest(
            Long.valueOf(deliveryNumber),
            purchaseReferenceNumber,
            purchaseReferenceLineNumber,
            itemNbr);
    final GlsLpnResponse response = glsRestApiClient.createGlsLpn(request, httpHeaders);

    isTrackingIdExistInDB(response.getPalletTagId());
    return createNewContainerDetails(response.getPalletTagId());
  }

  /**
   * @param httpHeaders
   * @return lpn
   * @throws ReceivingException
   */
  private String getPalletTag(HttpHeaders httpHeaders) throws ReceivingException {
    TenantContext.get().setAtlasRcvLpnCallStart(System.currentTimeMillis());
    String lpn = lpnCacheService.getLPNBasedOnTenant(httpHeaders);
    TenantContext.get().setAtlasRcvLpnCallEnd(System.currentTimeMillis());

    if (isBlank(lpn)) {
      invalidLpnException();
    }

    isTrackingIdExistInDB(lpn);
    return lpn;
  }

  public List<String> getMultiplePalletTag(int count, HttpHeaders httpHeaders)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvLpnCallStart(System.currentTimeMillis());
    List<String> lpn = lpnCacheService.getLPNSBasedOnTenant(count, httpHeaders);
    TenantContext.get().setAtlasRcvLpnCallEnd(System.currentTimeMillis());

    if (CollectionUtils.isEmpty(lpn) || lpn.size() != count) {
      invalidLpnException();
    }

    Set<Container> container = containerService.getContainerListByTrackingIdList(lpn);
    if (!CollectionUtils.isEmpty(container)) {
      invalidLpnException();
    }
    return lpn;
  }

  // Check DB to see if tracking ID already exist
  private void isTrackingIdExistInDB(String lpn) throws ReceivingException {
    Container container = containerService.findByTrackingId(lpn);
    if (nonNull(container)) {
      invalidLpnException();
    }
  }

  /**
   * @param instructionRequest
   * @param httpHeaders
   * @return Instruction
   */
  private Instruction constructInstruction(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
    Instruction newInstruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            instructionRequest.getDeliveryDocuments().get(0),
            InstructionUtils.mapHttpHeaderToInstruction(
                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));

    // Set necessary data
    newInstruction.setPrintChildContainerLabels(false);
    newInstruction.setReceivedQuantityUOM(VNPK);
    newInstruction.setProviderId(WITRON);
    newInstruction.setActivityName(SSTK_ACTIVITY_NAME);
    newInstruction.setIsReceiveCorrection(instructionRequest.isReceiveAsCorrection());
    newInstruction.setLastChangeUserId(newInstruction.getCreateUserId());
    newInstruction.setInstructionCode(
        GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionCode());
    newInstruction.setInstructionMsg(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionMsg());

    if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      newInstruction.setSsccNumber(instructionRequest.getSscc());
    }

    String orgUnitId = tenantSpecificConfigReader.getOrgUnitId();
    if (nonNull(orgUnitId)) newInstruction.setOrgUnitId(Integer.valueOf(orgUnitId));

    return newInstruction;
  }

  /**
   * @param receivedQtyDetails
   * @param deliveryDocument
   * @param instructionRequest
   * @throws ReceivingException
   */
  private void validateOverages(
      Pair<Integer, Long> receivedQtyDetails,
      DeliveryDocument deliveryDocument,
      InstructionRequest instructionRequest)
      throws ReceivingException {
    int maxLimitToReceive = receivedQtyDetails.getKey();
    long totalReceivedQty = receivedQtyDetails.getValue();
    String problemTagId = instructionRequest.getProblemTagId();
    String deliveryNbr = instructionRequest.getDeliveryNumber();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    String poNbr = deliveryDocumentLine.getPurchaseReferenceNumber();
    int poLineNbr = deliveryDocumentLine.getPurchaseReferenceLineNumber();
    log.info(
        "Validate overage for deliveryNbr:{} poNbr:{} poLineNbr:{} totalReceivedQty:{} maxLimitToReceive:{}",
        deliveryNbr,
        poNbr,
        poLineNbr,
        totalReceivedQty,
        maxLimitToReceive);
    if (isBlank(problemTagId)
        && (totalReceivedQty >= maxLimitToReceive)
        && !instructionHelperService.isManagerOverrideIgnoreOverage(
            deliveryNbr, poNbr, poLineNbr)) {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OVERAGE_ERROR);
      log.error(instructionError.getErrorMessage());
      if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.ASN_PO_OVERAGES, ReceivingException.ASN_PO_OVERAGES);
      } else {
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            INTERNAL_SERVER_ERROR,
            String.format(ExceptionCodes.AUTO_GROC_OVERAGE_ERROR, poNbr, poLineNbr));
      }
    }
  }

  private boolean isGroceryProblemReceive(String problemTagId) {
    if (isNotBlank(problemTagId)) {
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
  @Deprecated
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
      // backward compatible uri is same but new INVENTORY_RECEIPT_RECEIVE_AS_CORRECTION_V2 then new
      // contract
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
    if (isBlank(problemTagId)) {
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
    ctrLabel.put(
        "printRequests", gson.fromJson(gson.toJson(containerLabel.getPrintRequests()), List.class));
    containerDetails.setCtrLabel(ctrLabel);
  }

  private ContainerDetails createNewContainerDetails(String lpn) {
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
   * @throws ReceivingException
   */
  private void validateDocLine(
      String deliveryNumber,
      DeliveryDocumentLine deliveryDocumentLine,
      boolean isGroceryProblemReceive)
      throws ReceivingException {

    validateWeightFormatType(deliveryDocumentLine);
    validatePromoBuyInd(deliveryDocumentLine);
    deliveryDocumentHelper.validatePoLineStatus(deliveryDocumentLine);
    deliveryDocumentHelper.validateTiHi(deliveryDocumentLine);
    validateHaccp(deliveryNumber, deliveryDocumentLine, isGroceryProblemReceive);
  }

  private void validateDocLineIntoOss(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    validateWeightFormatType(deliveryDocumentLine);
    validatePromoBuyInd(deliveryDocumentLine);
    deliveryDocumentHelper.validatePoLineStatus(deliveryDocumentLine);
  }

  private void validateHaccp(
      String deliveryNumber,
      DeliveryDocumentLine deliveryDocumentLine,
      boolean isGroceryProblemReceive)
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

      throw new ReceivingException(
          haccpErrMsg,
          BAD_REQUEST,
          String.format(
              ExceptionCodes.AUTO_GROC_HACCP_ERROR,
              purchaseReferenceNumber,
              purchaseReferenceLineNumber),
          haccpErr.getErrorHeader(),
          haccpErrorJson);
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
        || isEmpty(deliveryDocumentLine.getPromoBuyInd())) {
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

    // Automated GDC(Witron), alert if item automation type not set
    if (!gdcFlagReader.isManualGdcEnabled()
        && (isBlank(itemData.getProfiledWarehouseArea())
            || "NONE".equalsIgnoreCase(itemData.getProfiledWarehouseArea()))) {
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
    if (FIXED_WEIGHT_FORMAT_TYPE_CODE.equalsIgnoreCase(itemData.getWeightFormatTypeCode())) {
      final Integer orderableQuantity = deliveryDocumentLine.getOrderableQuantity();
      final Integer vendorPack = deliveryDocumentLine.getVendorPack();
      final Integer warehousePack = deliveryDocumentLine.getWarehousePack();
      final Integer warehousePackQuantity = deliveryDocumentLine.getWarehousePackQuantity();
      if (anyNull(orderableQuantity, vendorPack, warehousePack, warehousePackQuantity)
          || vendorPack.intValue() != orderableQuantity.intValue()
          || warehousePack.intValue() != warehousePackQuantity.intValue()) {
        log.error(
            "error invalid values for orderableQuantity={},vendorPack={},warehousePack={},warehousePackQuantity={}",
            orderableQuantity,
            vendorPack,
            warehousePack,
            warehousePackQuantity);
        InstructionError instructionError =
            InstructionErrorCode.getErrorValue("PO_ITEM_PACK_ERROR");
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            BAD_REQUEST,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
      }
    }

    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), WEIGHT_FORMAT_TYPE_CHECK_FEATURE, false)) {
      final String omsOrMdmWeightFormatType =
          !StringUtils.isEmpty(itemData.getOmsWeightFormatTypeCode())
              ? itemData.getOmsWeightFormatTypeCode()
              : itemData.getWeightFormatTypeCode();
      final String dcWeightFormatTypeCode = itemData.getDcWeightFormatTypeCode();

      if (org.apache.commons.lang3.StringUtils.isNotEmpty(dcWeightFormatTypeCode)
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
      instructionStateValidator.validate(instruction);

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
      throw new ReceivingException(
          !org.springframework.util.StringUtils.isEmpty(errorMessage)
              ? errorMessage
              : ReceivingException.CANCEL_INSTRUCTION_ERROR_MSG,
          INTERNAL_SERVER_ERROR,
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
   * @throws ReceivingException
   */
  private void multiUserValidationWithPendingInstructions(
      String poNumber, int poLineNumber, long totalReceivedQty, int maxLimitToReceive)
      throws ReceivingException {
    Long totalOpenProjectedReceiveQty =
        instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            poNumber, poLineNumber);

    if (totalOpenProjectedReceiveQty != null
        && totalOpenProjectedReceiveQty > 0
        && (totalOpenProjectedReceiveQty + totalReceivedQty) >= maxLimitToReceive) {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.MULTI_USER_ERROR);
      log.error(instructionError.getErrorMessage());
      throw new ReceivingException(
          instructionError.getErrorMessage(),
          INTERNAL_SERVER_ERROR,
          REQUEST_TRANSFTER_INSTR_ERROR_CODE,
          instructionError.getErrorHeader());
    }
  }

  private void throwExceptionIfContainerWithSsccReceived(InstructionRequest instructionRequest) {
    if (!InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      return;
    }
    String sscc = instructionRequest.getSscc();
    if (org.apache.commons.lang3.StringUtils.isNotEmpty(sscc)) {
      int receivedQuantityForSSCC = containerService.receivedContainerQuantityBySSCC(sscc);
      if (receivedQuantityForSSCC > 0) {
        throw new ReceivingBadDataException(
            ExceptionCodes.SSCC_RECEIVED_ALREADY,
            String.format(ReceivingException.RECEIVE_INSTRUCTION_EXCEEDS_SSCC_LIMIT, sscc),
            sscc);
      }
    }
  }

  /**
   * Block Receiving in Atlas if the delivery marked for GLS
   *
   * @param deliveryDocument
   */
  private void checkDeliveryOwnership(DeliveryDocument deliveryDocument) {
    if (GLS.equalsIgnoreCase(deliveryDocument.getDeliveryOwnership())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.GLS_DELIVERY_ERROR, ReceivingException.GLS_DELIVERY_ERROR_MSG);
    }
  }

  /**
   * This method is mainly used to receive all pallets in for gdc market where we create necessary
   * containers, receipts and printJob.
   *
   * @param receiveAllRequestString request from client
   * @param httpHeaders http headers passed
   * @return print job
   * @throws ReceivingException receiving exception
   */
  @Override
  @Transactional(rollbackFor = ReceivingException.class)
  public ReceiveAllResponse receiveAll(String receiveAllRequestString, HttpHeaders httpHeaders)
      throws ReceivingException {

    ReceiveAllRequest receiveAllRequest =
        gson.fromJson(receiveAllRequestString, ReceiveAllRequest.class);

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDoorNumber(receiveAllRequest.getDoorNumber());
    instructionRequest.setDeliveryDocuments(receiveAllRequest.getDeliveryDocuments());
    instructionRequest.setMessageId(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    instructionRequest.setReceiveAsCorrection(false);

    final List<DeliveryDocument> deliveryDocumentsUi = instructionRequest.getDeliveryDocuments();
    DeliveryDocument deliveryDocument = deliveryDocumentsUi.get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    instructionRequest.setDeliveryNumber(String.valueOf(deliveryDocument.getDeliveryNumber()));

    Instruction instruction = constructInstruction(instructionRequest, httpHeaders);

    // get required Flag
    final boolean isOneAtlas = gdcFlagReader.isDCOneAtlasEnabled();

    // Set projected receive quantity
    instruction.setProjectedReceiveQty(
        getProjectedReceiveQty(instructionRequest, deliveryDocumentLine, ""));
    instruction.setProjectedReceiveQtyUOM(VNPK);

    // Get and Set lpn/slot from Atlas or Gls len generator with slotting integration
    setLPNAndSlottingData(instruction, instructionRequest, isOneAtlas, httpHeaders);

    // Set manual gdc specific instruction data
    setGDCInstructionData(
        instruction,
        instructionRequest.getDeliveryNumber(),
        deliveryDocument.getPurchaseReferenceNumber(),
        deliveryDocumentLine.getPurchaseReferenceLineNumber());

    // Persist instruction
    instruction = instructionPersistService.saveInstruction(instruction);

    return gdcReceiveInstructionHandler.receiveAll(
        instruction.getId(), receiveAllRequest, httpHeaders);
  }

  public void validateOveragesForOSS(String inventoryBohQty) throws ReceivingException {
    if (isBlank(inventoryBohQty) || Integer.valueOf(inventoryBohQty) <= ZERO_QTY)
      throw new ReceivingException(
          String.format(OVERAGE_ERROR_OSS, inventoryBohQty),
          BAD_REQUEST,
          OVERAGE_ERROR_CODE,
          OVERAGE_ERROR_CODE);
  }

  public void isOSSPOAlreadyFinalized(
      DeliveryDocument deliveryDocument, DeliveryDocumentLine deliveryDocumentLine) {
    // Block OSS receiving into Main building
    if (GdcUtil.isDCCanReceiveOssPO(
            deliveryDocument, deliveryDocumentLine, configUtils, gdcFlagReader)
        && POStatus.CLOSED
            .toString()
            .equalsIgnoreCase(deliveryDocument.getPurchaseReferenceStatus())) {
      log.error(
          "Block receiving as PO Finalized for PO {}",
          deliveryDocument.getPurchaseReferenceNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_REQUEST, OSS_TRANSFER_PO_FINALIZED_CORRECTION_ERROR);
    }
  }
}
