package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.computeEffectiveMaxReceiveQty;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.computeEffectiveTotalQty;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instructioncode.AccInstructionType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ActivityName;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class ManualInstructionService {

  private static final String EMPTY_STRING = "";

  @ManagedConfiguration private AppConfig appConfig;

  private static final Logger LOGGER = LoggerFactory.getLogger(ManualInstructionService.class);

  protected InstructionError instructionError;

  @Autowired private InstructionHelperService instructionHelperService;

  @Autowired private DeliveryDocumentHelper deliveryDocumentHelper;

  @Resource(name = ReceivingConstants.FDE_SERVICE)
  private FdeService fdeService;

  @Autowired private InstructionPersisterService instructionPersisterService;

  @Autowired private DCFinService dcFinService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private InstructionService instructionService;

  /**
   * Request FDE
   *
   * @param fdeCreateContainerRequest FDE create container request
   * @param httpHeaders headers
   * @return FDE instruction response
   * @throws ReceivingException receiving exception
   */
  private FdeCreateContainerResponse requestFDE(
      FdeCreateContainerRequest fdeCreateContainerRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    String instructionResponse;
    try {
      instructionResponse = fdeService.receive(fdeCreateContainerRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      LOGGER.error(
          String.format(
              ReceivingException.FDE_RECEIVE_FDE_CALL_FAILED, receivingException.getMessage()));
      throw receivingException;
    }

    return JacksonParser.convertJsonToObject(instructionResponse, FdeCreateContainerResponse.class);
  }

  /**
   * Persist instruction
   *
   * @param instructionRequest instruction request
   * @param fdeCreateContainerResponse response of fde call
   * @param httpHeaders http headers
   * @return persisted and published instruction
   */
  private Instruction createInstruction(
      InstructionRequest instructionRequest,
      FdeCreateContainerResponse fdeCreateContainerResponse,
      HttpHeaders httpHeaders) {
    Instruction instruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            instructionRequest.getDeliveryDocuments().get(0),
            InstructionUtils.mapHttpHeaderToInstruction(
                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));
    instruction =
        InstructionUtils.processInstructionResponse(
            instruction, instructionRequest, fdeCreateContainerResponse);

    if (ReceivingUtils.isKotlinEnabled(httpHeaders, tenantSpecificConfigReader)
        && ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
            instructionRequest.getFeatureType())) {
      // set instruction code for mobile scan to print flow
      instruction.setInstructionCode(ReceivingConstants.SCAN_TO_PRINT_INSTRUCTION_CODE);
    }
    instruction.setActivityName(
        getUpdatedActivityName(instruction.getActivityName(), tenantSpecificConfigReader));
    instruction = instructionPersisterService.saveInstruction(instruction);
    instructionHelperService.publishInstruction(
        instruction, null, null, null, InstructionStatus.CREATED, httpHeaders);
    return instruction;
  }

  public static String getUpdatedActivityName(
      String activityName, TenantSpecificConfigReader configUtils) {
    if (activityName.equalsIgnoreCase(ReceivingConstants.ACL_ACTIVITY_NAME)
        && configUtils.isFeatureFlagEnabled(
            ReceivingConstants.ENABLE_ACTIVITY_NAME_FROM_RECEIVING)) {
      return ActivityName.DA_CONVENYABLE.getActivityName();
    } else {
      return activityName;
    }
  }
  /**
   * Validate if mandatory fields required for manual instruction are presents
   *
   * @param deliveryDocumentLine delivery document line
   * @throws ReceivingException receiving exception
   */
  private void validateIfMandatoryFieldsPresent(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    if (deliveryDocumentLine.getWarehousePackSell() == null
        || deliveryDocumentLine.getVendorPackCost() == null) {
      LOGGER.error("Mandatory fields are missing in the request payload, please verify ...");
      instructionError =
          InstructionErrorCode.getErrorValue(ReceivingException.MANUAL_RCV_MANDATORY_FIELD_MISSING);
      LOGGER.error(instructionError.getErrorMessage());
      throw new ReceivingException(
          instructionError.getErrorMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR,
          instructionError.getErrorCode(),
          instructionError.getErrorHeader());
    }
  }

  public InstructionResponse getDeliveryDocumentWithOpenQtyForManualInstruction(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    Boolean isKotlinEnabled =
        ReceivingUtils.isKotlinEnabled(httpHeaders, tenantSpecificConfigReader);
    Long totalReceivedQty = 0L;
    int maxReceiveQty = 0;
    // Get the received qty against all PO lines
    // TODO dirty-received-qty-fix change to use received qty by delivery po poline map
    Map<String, Long> receivedQtyByPoAndPoLineMap =
        instructionHelperService.getReceivedQtyMapByPOPOL(deliveryDocuments, EMPTY_STRING);
    if (appConfig.isManualPoLineAutoSelectionEnabled()) {
      int totalOpenQty = 0;

      List<DeliveryDocument> activeDeliveryDocuments =
          tenantSpecificConfigReader.isFeatureFlagEnabled(
                  ReceivingConstants.ENABLE_FILTER_CANCELLED_PO)
              ? InstructionUtils.filterCancelledPoPoLine(deliveryDocuments)
              : deliveryDocuments;

      DeliveryDocument resultantDeliveryDocument = activeDeliveryDocuments.get(0);
      DeliveryDocumentLine resultantDeliveryDocumentLine =
          activeDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
      for (DeliveryDocument deliveryDocument : activeDeliveryDocuments) {
        for (DeliveryDocumentLine deliveryDocumentLine :
            deliveryDocument.getDeliveryDocumentLines()) {
          String key =
              deliveryDocument.getPurchaseReferenceNumber()
                  + ReceivingConstants.DELIM_DASH
                  + deliveryDocumentLine.getPurchaseReferenceLineNumber();
          totalReceivedQty = receivedQtyByPoAndPoLineMap.getOrDefault(key, 0L);
          // TODO dirty-received-qty-fix use fbq based on flag
          maxReceiveQty =
              computeEffectiveMaxReceiveQty(
                  deliveryDocumentLine,
                  deliveryDocument.getImportInd(),
                  tenantSpecificConfigReader);
          // set openQty for manual rendered on UI i.e. max receivable qty - total received qty
          int openQty = maxReceiveQty - totalReceivedQty.intValue();
          totalOpenQty += openQty;
        }
      }
      if (totalOpenQty <= 0) {
        createOverageAgainstLastDocumentLine(
            activeDeliveryDocuments, totalReceivedQty.intValue(), maxReceiveQty, httpHeaders);
        if (isKotlinEnabled) {
          Instruction instruction =
              InstructionUtils.getCCOverageAlertInstruction(
                  instructionRequest, instructionRequest.getDeliveryDocuments());
          instruction.setDeliveryDocument(null);
          instructionResponse.setInstruction(instruction);
          instructionResponse.setDeliveryDocuments(
              Collections.singletonList(resultantDeliveryDocument));
          return instructionResponse;
        }
      }
      resultantDeliveryDocumentLine.setOpenQty(
          Math.min(totalOpenQty, appConfig.getMaxAllowedLabelsAtOnce()));
      resultantDeliveryDocumentLine.setTotalReceivedQty(totalReceivedQty.intValue());
      resultantDeliveryDocument.setDeliveryDocumentLines(
          Collections.singletonList(resultantDeliveryDocumentLine));
      instructionResponse.setDeliveryDocuments(
          Collections.singletonList(resultantDeliveryDocument));
    } else {
      boolean isManualOverageReached = true;
      for (DeliveryDocument deliveryDocument : deliveryDocuments) {
        for (DeliveryDocumentLine deliveryDocumentLine :
            deliveryDocument.getDeliveryDocumentLines()) {
          String key =
              deliveryDocument.getPurchaseReferenceNumber()
                  + ReceivingConstants.DELIM_DASH
                  + deliveryDocumentLine.getPurchaseReferenceLineNumber();
          totalReceivedQty = receivedQtyByPoAndPoLineMap.getOrDefault(key, 0L);
          // TODO dirty-received-qty-fix use fbq
          maxReceiveQty =
              computeEffectiveMaxReceiveQty(
                  deliveryDocumentLine,
                  deliveryDocument.getImportInd(),
                  tenantSpecificConfigReader);
          // set openQty for manual instruction rendered on UI i.e. max receivable qty - total
          // received qty
          deliveryDocumentLine.setTotalReceivedQty(totalReceivedQty.intValue());
          int openQty =
              Math.min(
                  maxReceiveQty - totalReceivedQty.intValue(),
                  appConfig.getMaxAllowedLabelsAtOnce());
          if (openQty > 0) {
            isManualOverageReached = false;
            deliveryDocumentLine.setOpenQty(openQty);
          }
        }
      }
      if (isManualOverageReached) {
        createOverageAgainstLastDocumentLine(
            deliveryDocuments, totalReceivedQty.intValue(), maxReceiveQty, httpHeaders);
        if (isKotlinEnabled) {
          Instruction instruction =
              InstructionUtils.getCCOverageAlertInstruction(
                  instructionRequest, instructionRequest.getDeliveryDocuments());
          instruction.setDeliveryDocument(null);
          instructionResponse.setInstruction(instruction);
          instructionResponse.setDeliveryDocuments(deliveryDocuments);
          return instructionResponse;
        }
      }
      instructionResponse.setDeliveryDocuments(deliveryDocuments);
    }
    if (isKotlinEnabled) {
      Instruction instruction = new Instruction();
      instruction.setInstructionMsg(
          AccInstructionType.ACC_MANUAL_RCV_BUILD_PALLET.getInstructionMsg());
      instruction.setInstructionCode(
          AccInstructionType.ACC_MANUAL_RCV_BUILD_PALLET.getInstructionCode());
      instructionResponse.setInstruction(instruction);
      if (org.apache.commons.lang3.StringUtils.isBlank(instruction.getGtin())) {
        LOGGER.info(
            "Setting the GTIN value in the instruction to the scanned UPC value from the request, if GTIN is Null / Empty / Blank ");
        instruction.setGtin(instructionRequest.getUpcNumber());
      }
    }
    return instructionResponse;
  }

  private void createOverageAgainstLastDocumentLine(
      List<DeliveryDocument> deliveryDocuments,
      int totalReceivedQty,
      int maxReceiveQty,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    DeliveryDocument lastDeliveryDocument = deliveryDocuments.get(deliveryDocuments.size() - 1);
    DeliveryDocumentLine lastDeliveryDocumentLine =
        lastDeliveryDocument.getDeliveryDocumentLines().get(0);
    lastDeliveryDocument.getDeliveryDocumentLines().clear();
    lastDeliveryDocument.getDeliveryDocumentLines().add(lastDeliveryDocumentLine);
    if (!ReceivingUtils.isKotlinEnabled(httpHeaders, tenantSpecificConfigReader)) {
      handleOverageForManualReceiving(lastDeliveryDocument, totalReceivedQty, maxReceiveQty);
    }
  }

  private void handleOverageForManualReceiving(
      DeliveryDocument deliveryDocument, int totalReceivedQty, int maxReceiveQty)
      throws ReceivingException {
    instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OVERAGE_ERROR);
    LOGGER.error(instructionError.getErrorMessage());
    throw new ReceivingException(
        instructionError.getErrorMessage(),
        HttpStatus.INTERNAL_SERVER_ERROR,
        instructionError.getErrorCode(),
        totalReceivedQty,
        maxReceiveQty,
        deliveryDocument);
  }

  /**
   * Create manual instruction
   *
   * @param instructionRequest instruction request
   * @param httpHeaders headers
   * @return instruction response
   * @throws ReceivingException receiving exception
   */
  @TimeTracing(component = AppComponent.CORE, type = Type.REST, flow = "ManualReceiving")
  public InstructionResponse createManualInstruction(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    if (appConfig.isManualPoLineAutoSelectionEnabled()) {
      try {
        deliveryDocument =
            autoSelectPoLineForManualReceiving(deliveryDocument, deliveryDocumentLine, httpHeaders);
        deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
        // TODO: Check why this is required
        validateIfMandatoryFieldsPresent(deliveryDocumentLine);
        instructionRequest.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
      } catch (ReceivingException receivingException) {
        if (ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
            instructionRequest.getFeatureType())) {
          InstructionResponse instructionResponse = new InstructionResponseImplNew();
          instructionRequest
              .getDeliveryDocuments()
              .set(
                  0,
                  ((RangeErrorResponse) receivingException.getErrorResponse())
                      .getDeliveryDocument());
          LOGGER.info(
              "InstructionService: AUTO_CASE_RECEIVE Flow "
                  + "error msg: {} "
                  + "entering getCCOverageAlertInstruction() for request: {}",
              receivingException.getErrorResponse(),
              instructionRequest);

          Instruction instruction =
              InstructionUtils.getCCOverageAlertInstruction(
                  instructionRequest, instructionRequest.getDeliveryDocuments());
          instructionResponse.setDeliveryDocuments(
              instructionService.getUpdatedDeliveryDocumentsForAllowableOverage(
                  instruction, instructionRequest.getDeliveryDocuments()));
          instructionResponse.setInstruction(instruction);
          return instructionResponse;
        } else {
          throw receivingException;
        }
      }
    } else {
      // TODO: Check why this is required
      validateIfMandatoryFieldsPresent(deliveryDocumentLine);
      // Get Received Qty and validate if quantity reached/exceed the max allowed quantity
      Pair<Integer, Long> receivedQtyDetails =
          instructionHelperService.getReceivedQtyDetailsAndValidate(
              instructionRequest.getProblemTagId(),
              deliveryDocument,
              instructionRequest.getDeliveryNumber(),
              ReceivingUtils.isKotlinEnabled(httpHeaders, tenantSpecificConfigReader),
              ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
                  instructionRequest.getFeatureType()));

      int totalReceivedQty = receivedQtyDetails.getValue().intValue();
      deliveryDocumentLine.setTotalReceivedQty(totalReceivedQty);
      deliveryDocumentLine.setOpenQty(
          computeEffectiveTotalQty(
                  deliveryDocumentLine, deliveryDocument.getImportInd(), tenantSpecificConfigReader)
              - totalReceivedQty);
    }

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    // Prepare request payload to request FDE
    FdeCreateContainerRequest fdeCreateContainerRequest =
        InstructionUtils.prepareFdeCreateContainerRequestForOnConveyor(
            httpHeaders, instructionRequest, userId);
    // Request FDE and capture response
    FdeCreateContainerResponse fdeCreateContainerResponse =
        requestFDE(fdeCreateContainerRequest, httpHeaders);
    // Persist instruction
    Instruction instruction =
        createInstruction(instructionRequest, fdeCreateContainerResponse, httpHeaders);
    // Prepare update instruction request
    UpdateInstructionRequest updateInstructionRequest =
        InstructionUtils.getInstructionUpdateRequestForOnConveyor(
            instructionRequest.getDoorNumber(),
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            fdeCreateContainerRequest.getContainer().getContents().get(0).getPurchaseRefType(),
            deliveryDocument);
    // publish updated instruction to WFM
    instructionHelperService.publishUpdateInstructionToWFM(
        httpHeaders, instruction, updateInstructionRequest, instruction.getActivityName());
    // save container, receipts and instruction

    // set onConveyor based on feature flag ON_CONVEYOR_FLAG for imports scan to print
    boolean onConveyor =
        !tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.ON_CONVEYOR_FLAG);
    Pair<Container, Instruction> containersAndSavedInstruction =
        instructionPersisterService.createContainersReceiptsAndSaveInstruction(
            updateInstructionRequest, userId, instruction, onConveyor);

    Container consolidatedContainer = containersAndSavedInstruction.getKey();
    Set<Container> childContainerList = new HashSet<>();
    consolidatedContainer.setChildContainers(childContainerList);

    // Publish to inventory
    instructionHelperService.publishConsolidatedContainer(
        consolidatedContainer, httpHeaders, Boolean.TRUE);

    // Post receipts to DCFin
    dcFinService.postReceiptsToDCFin(consolidatedContainer, httpHeaders, true);

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.ENABLE_STORE_LABEL_SORTER_DIVERT)) {
      // Publish sorter divert
      tenantSpecificConfigReader
          .getConfiguredInstance(
              String.valueOf(TenantContext.getFacilityNum()),
              ReceivingConstants.SORTER_PUBLISHER,
              SorterPublisher.class)
          .publishStoreLabel(consolidatedContainer);
    }

    instruction = containersAndSavedInstruction.getValue();

    // Publishing instruction. Instruction will be published all the time.
    instructionHelperService.publishInstruction(
        instruction, null, null, consolidatedContainer, InstructionStatus.COMPLETED, httpHeaders);

    // Prepare response payload and return
    InstructionResponse instructionResponse =
        instructionHelperService.prepareInstructionResponse(
            instruction, consolidatedContainer, null, null);

    if (ReceivingUtils.isKotlinEnabled(httpHeaders, tenantSpecificConfigReader)
        && ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
            instructionRequest.getFeatureType())) {
      // Set totalReceivedQty and openQty in delivery documents if scan to print flow in mobile
      // TODO dirty-received-qty-fix use delivery received qty
      Pair<Integer, Long> receivedQtyDetails =
          tenantSpecificConfigReader.isFeatureFlagEnabled(
                      ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK)
                  && Boolean.TRUE.equals(deliveryDocument.getImportInd())
              ? instructionHelperService.getReceivedQtyDetailsByDeliveryNumber(
                  instruction.getDeliveryNumber(), "", deliveryDocumentLine)
              : instructionHelperService.getReceivedQtyDetails("", deliveryDocumentLine);
      // TODO dirty-received-qty-fix use fbq
      int maxReceiveQty =
          computeEffectiveMaxReceiveQty(
              deliveryDocumentLine, deliveryDocument.getImportInd(), tenantSpecificConfigReader);
      long totalReceivedQty = receivedQtyDetails.getValue();
      deliveryDocumentLine.setTotalReceivedQty((int) totalReceivedQty);
      deliveryDocumentLine.setOpenQty(maxReceiveQty - (int) totalReceivedQty);
      instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
    }
    return instructionResponse;
  }

  public DeliveryDocument autoSelectPoLineForManualReceiving(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    // Get GTIN and delivery number to fetch all PO lines for this item
    String gtin = deliveryDocumentLine.getItemUpc();
    long deliveryNumber = deliveryDocument.getDeliveryNumber();
    DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments =
        deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            deliveryNumber, gtin, httpHeaders);

    // Get the received qty against all PO lines

    // Use the same method used for ACL receiving to fetch auto selected doc line
    Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLinePair;
    ReceiptsAggregator receiptsAggregator = ReceiptsAggregator.empty();
    Map<String, Long> receivedQtyByPoAndPoLineMap = new HashMap<>();
    if (!CollectionUtils.isEmpty(deliveryDocuments)
        && (Objects.isNull(deliveryDocuments.stream().findAny().get().getImportInd())
            || !deliveryDocuments.stream().findAny().get().getImportInd())) {
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED)) {
        receiptsAggregator = instructionHelperService.getReceivedQtyByPoPol(deliveryDocuments);
        LOGGER.info("Fetched received qty by list of PO and POL {}", receiptsAggregator);
        autoSelectDocumentAndDocumentLinePair =
            instructionHelperService.autoSelectDocumentAndDocumentLineMABDGivenReceivedQty(
                deliveryDocuments, 1, receiptsAggregator, EMPTY_STRING);
      } else {
        receivedQtyByPoAndPoLineMap =
            instructionHelperService.getReceivedQtyMapByPOPOL(deliveryDocuments, EMPTY_STRING);
        LOGGER.info("Fetched received qty by list of PO and POL {}", receivedQtyByPoAndPoLineMap);
        autoSelectDocumentAndDocumentLinePair =
            instructionHelperService.autoSelectDocumentAndDocumentLineMABDGivenReceivedQty(
                deliveryDocuments, 1, receivedQtyByPoAndPoLineMap, EMPTY_STRING);
      }
    } else {
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED)) {
        receiptsAggregator =
            instructionHelperService.getReceivedQtyByDeliveryPoPol(deliveryDocuments);
        LOGGER.info("Fetched received qty by list of PO and POL {}", receiptsAggregator);
        autoSelectDocumentAndDocumentLinePair =
            instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobinGivenReceivedQty(
                deliveryDocuments, 1, receiptsAggregator);
      } else {
        receivedQtyByPoAndPoLineMap =
            instructionHelperService.getReceivedQtyMapByPOPOL(deliveryDocuments, EMPTY_STRING);
        LOGGER.info("Fetched received qty by list of PO and POL {}", receivedQtyByPoAndPoLineMap);
        autoSelectDocumentAndDocumentLinePair =
            instructionHelperService.autoSelectDocumentAndDocumentLineRoundRobinGivenReceivedQty(
                deliveryDocuments, 1, receivedQtyByPoAndPoLineMap);
      }
    }

    // Overage scenario
    if (Objects.isNull(autoSelectDocumentAndDocumentLinePair)) {
      LOGGER.error(
          "No PO line was found during auto selection, Creating overage exception against original line");
      Long totalReceivedQty =
          calculateTotalReceivedQty(
              deliveryDocument,
              deliveryDocumentLine,
              deliveryDocuments,
              receiptsAggregator,
              receivedQtyByPoAndPoLineMap);
      deliveryDocumentLine.setTotalReceivedQty(totalReceivedQty.intValue());
      deliveryDocumentLine.setOpenQty(
          computeEffectiveTotalQty(
                  deliveryDocumentLine, deliveryDocument.getImportInd(), tenantSpecificConfigReader)
              - totalReceivedQty.intValue());
      // TODO dirty-received-qty-fix use fbq
      int maxReceivableQty =
          computeEffectiveMaxReceiveQty(
              deliveryDocumentLine, deliveryDocument.getImportInd(), tenantSpecificConfigReader);
      handleOverageForManualReceiving(
          deliveryDocument,
          maxReceivableQty,
          totalReceivedQty == null ? 0 : totalReceivedQty.intValue());
    }

    DeliveryDocument autoSelectedDocument = autoSelectDocumentAndDocumentLinePair.getKey();

    DeliveryDocumentLine autoSelectedDocumentLine =
        autoSelectedDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentHelper.updateCommonFieldsInDeliveryDocLine(autoSelectedDocumentLine);

    // Set the open qty in the auto selected document line
    long totalReceivedQty = autoSelectDocumentAndDocumentLinePair.getValue();
    // TODO dirty-received-qty-fix use fbq
    autoSelectedDocumentLine.setOpenQty(
        computeEffectiveTotalQty(
                autoSelectedDocumentLine,
                autoSelectedDocument.getImportInd(),
                tenantSpecificConfigReader)
            - (int) totalReceivedQty);

    autoSelectedDocument.getDeliveryDocumentLines().set(0, autoSelectedDocumentLine);

    return autoSelectedDocument;
  }

  private Long calculateTotalReceivedQty(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      List<DeliveryDocument> deliveryDocuments,
      ReceiptsAggregator receiptsAggregator,
      Map<String, Long> receivedQtyByPoAndPoLineMap) {
    Long totalReceivedQty = 0L;
    String key =
        deliveryDocument.getPurchaseReferenceNumber()
            + ReceivingConstants.DELIM_DASH
            + deliveryDocumentLine.getPurchaseReferenceLineNumber();

    if (!CollectionUtils.isEmpty(deliveryDocuments)
        && (Objects.isNull(deliveryDocuments.stream().findAny().get().getImportInd())
            || !deliveryDocuments.stream().findAny().get().getImportInd())) {
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED)) {
        totalReceivedQty =
            receiptsAggregator.getByPoPolInZA(
                deliveryDocument.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack());
      } else {
        totalReceivedQty = receivedQtyByPoAndPoLineMap.getOrDefault(key, 0L);
      }
    } else {
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED)) {
        totalReceivedQty =
            receiptsAggregator.getByDeliveryPoLineInZA(
                deliveryDocument.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack());
      } else {
        totalReceivedQty = receivedQtyByPoAndPoLineMap.getOrDefault(key, 0L);
      }
    }

    return totalReceivedQty;
  }
}
