package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.core.common.InstructionUtils.checkIfProblemTagPresent;
import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.*;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ATLAS_COMPLETE_MIGRATED_DC_LIST;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DSDC_IDENTIFIER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PURCHASE_REF_TYPE_DA;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PURCHASE_REF_TYPE_SSTK;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigRestApiClientException;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedQuantityResponseFromRDS;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.move.MoveType;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.model.RdcReceivingType;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.service.NimRdsService;
import com.walmart.move.nim.receiving.rdc.service.RdcDeliveryService;
import com.walmart.move.nim.receiving.rdc.service.RdcQuantityCalculator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class RdcInstructionUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcInstructionUtils.class);

  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private InstructionSetIdGenerator instructionSetIdGenerator;
  @Autowired private SlottingServiceImpl slottingService;
  @Autowired private ReceiptService receiptService;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private LocationService locationService;
  @Autowired private ItemConfigApiClient itemConfigApiClient;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcQuantityCalculator rdcQuantityCalculator;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private RdcProblemUtils rdcProblemUtils;
  @Autowired private Gson gson;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private RdcDeliveryService rdcDeliveryService;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;

  private static final Map<String, List<PoType>> matchScannedItemsMap = new HashMap<>();

  static {
    List<PoType> daAllowedPoTypeList = Arrays.asList(PoType.DSDC, PoType.RTS);
    matchScannedItemsMap.put(PoType.CROSSDOCK.getpoType().toUpperCase(), daAllowedPoTypeList);

    List<PoType> dsdcAllowedPoTypeList =
        Arrays.asList(
            PoType.CROSSDOCK,
            PoType.CROSSU,
            PoType.CROSSMU,
            PoType.CROSSNA,
            PoType.CROSSNMA,
            PoType.MULTI,
            PoType.RTS);
    matchScannedItemsMap.put(PoType.DSDC.name().toUpperCase(), dsdcAllowedPoTypeList);

    List<PoType> rtsAllowedPoTypeList =
        Arrays.asList(
            PoType.CROSSDOCK,
            PoType.CROSSU,
            PoType.CROSSMU,
            PoType.CROSSNA,
            PoType.CROSSNMA,
            PoType.MULTI,
            PoType.DSDC);
    matchScannedItemsMap.put(PoType.RTS.name().toUpperCase(), rtsAllowedPoTypeList);
  }

  /*
   * This method filters all the Non DA delivery documents from the delivery documents
   * retrieved from GDM. If all the given POs are DA then it throws an exception
   * to the caller method.
   *
   * @param deliveryDocuments
   * @return List<DeliveryDocument>
   */
  public List<DeliveryDocument> filterNonDADeliveryDocuments(
      List<DeliveryDocument> deliveryDocuments, InstructionRequest instructionRequest) {
    List<DeliveryDocument> filteredNonDADocuments =
        deliveryDocuments.stream().filter(doc -> !isDADocument(doc)).collect(Collectors.toList());

    if (CollectionUtils.isEmpty(filteredNonDADocuments)) {
      LOGGER.error(
          "Found DA PO for the given delivery:{} and UPC:{}",
          instructionRequest.getDeliveryNumber(),
          instructionRequest.getUpcNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.DA_PURCHASE_REF_TYPE, RdcConstants.DA_PURCHASE_REF_TYPE_MSG);
    }
    return filteredNonDADocuments;
  }

  /**
   * This method filters all the DA delivery documents from the delivery documents retrieved from
   * GDM. If there's no DA PO found then it throws an exception to block receiving of other freight
   * types
   *
   * @param deliveryDocuments
   * @param instructionRequest
   * @return
   */
  public List<DeliveryDocument> filterDADeliveryDocuments(
      List<DeliveryDocument> deliveryDocuments, InstructionRequest instructionRequest) {
    List<DeliveryDocument> filteredDSDCDocuments =
        deliveryDocuments
            .stream()
            .filter(ReceivingUtils::isDSDCDocument)
            .collect(Collectors.toList());

    if (!CollectionUtils.isEmpty(filteredDSDCDocuments)) {
      LOGGER.error(
          "Found DSDC PO for the given delivery:{} and UPC:{}",
          instructionRequest.getDeliveryNumber(),
          instructionRequest.getUpcNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.DSDC_PURCHASE_REF_TYPE, NON_DA_PURCHASE_REF_TYPE_MSG);
    }

    List<DeliveryDocument> filteredDADocuments =
        deliveryDocuments.stream().filter(doc -> isDADocument(doc)).collect(Collectors.toList());

    if (CollectionUtils.isEmpty(filteredDADocuments)) {
      LOGGER.error(
          "Found Non DA PO for the given delivery:{} and UPC:{}",
          instructionRequest.getDeliveryNumber(),
          instructionRequest.getUpcNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.NON_DA_PURCHASE_REF_TYPE, NON_DA_PURCHASE_REF_TYPE_MSG);
    }
    return deliveryDocuments;
  }

  /**
   * This method filters out all the SSTK delivery documents from the delivery documents retrieved
   * from GDM.
   *
   * @param deliveryDocuments
   * @return List<DeliveryDocument>
   */
  public List<DeliveryDocument> filterSSTKDeliveryDocuments(
      List<DeliveryDocument> deliveryDocuments) {
    List<DeliveryDocument> filteredSSTKDocuments =
        deliveryDocuments.stream().filter(doc -> isSSTKDocument(doc)).collect(Collectors.toList());

    LOGGER.info(
        "Found {} SSTK PO for the given delivery:{} and UPC:{}",
        deliveryDocuments.size(),
        deliveryDocuments.get(0).getDeliveryNumber(),
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemUpc());

    return filteredSSTKDocuments;
  }

  /**
   * This method filters out all the DA delivery documents from the delivery documents retrieved
   * from GDM.
   *
   * @param deliveryDocuments
   * @return List<DeliveryDocument>
   */
  public List<DeliveryDocument> getDADeliveryDocumentsFromGDMDeliveryDocuments(
      List<DeliveryDocument> deliveryDocuments) {
    List<DeliveryDocument> filteredDSDCDocuments =
        deliveryDocuments
            .stream()
            .filter(ReceivingUtils::isDSDCDocument)
            .collect(Collectors.toList());

    if (!CollectionUtils.isEmpty(filteredDSDCDocuments)
        && filteredDSDCDocuments.size() == deliveryDocuments.size()) {
      LOGGER.error(
          "Found DSDC PO for the given delivery:{} and UPC:{}",
          deliveryDocuments.get(0).getDeliveryNumber(),
          deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemUpc());
      throw new ReceivingBadDataException(
          ExceptionCodes.DSDC_PURCHASE_REF_TYPE, NON_DA_PURCHASE_REF_TYPE_MSG);
    }
    List<DeliveryDocument> filteredDADocuments =
        deliveryDocuments.stream().filter(this::isDADocument).collect(Collectors.toList());

    if (CollectionUtils.isEmpty(filteredDADocuments)) {
      LOGGER.error(
          "Found Non DA PO for the given delivery:{} and UPC:{}",
          deliveryDocuments.get(0).getDeliveryNumber(),
          deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemUpc());
      throw new ReceivingBadDataException(
          ExceptionCodes.NON_DA_PURCHASE_REF_TYPE, NON_DA_PURCHASE_REF_TYPE_MSG);
    }
    LOGGER.info(
        "Found {} DA PO for the given delivery:{} and UPC:{}",
        deliveryDocuments.size(),
        deliveryDocuments.get(0).getDeliveryNumber(),
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemUpc());

    return filteredDADocuments;
  }

  /*
   * This method checks delivery status to determine that item is receivable or not.
   * It will be allowed to receive when delivery is in WORKING or OPEN status and
   * it should not have any open problem tag's attached to the delivery.
   *
   * @param deliveryDocuments
   * @throws ReceivingException
   */
  public void checkIfDeliveryStatusReceivable(DeliveryDocument deliveryDocument)
      throws ReceivingException {
    String deliveryStatus = deliveryDocument.getDeliveryStatus().toString();
    if (DeliveryStatus.WRK.name().equals(deliveryStatus)
        || DeliveryStatus.OPN.name().equals(deliveryStatus)) {
      return;
    }

    String errorMessage =
        String.format(ReceivingException.DELIVERY_STATE_NOT_RECEIVABLE_REOPEN, deliveryStatus);
    LOGGER.error(errorMessage);
    throw new ReceivingException(
        errorMessage, BAD_REQUEST, errorMessage, ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
  }

  public boolean isDADocument(DeliveryDocument deliveryDocument) {
    List<DeliveryDocumentLine> filteredDADocumentLines =
        deliveryDocument
            .getDeliveryDocumentLines()
            .stream()
            .filter(
                line ->
                    ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
                        line.getPurchaseRefType()))
            .collect(Collectors.toList());
    return filteredDADocumentLines.size() == deliveryDocument.getDeliveryDocumentLines().size();
  }

  public boolean isSSTKDocument(DeliveryDocument deliveryDocument) {
    List<DeliveryDocumentLine> filteredSSTKDocumentLines =
        deliveryDocument
            .getDeliveryDocumentLines()
            .stream()
            .filter(
                line ->
                    ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(
                        line.getPurchaseRefType()))
            .collect(Collectors.toList());
    return !filteredSSTKDocumentLines.isEmpty();
  }

  public boolean isSSTKDocument(
      com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument) {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine>
        filteredSSTKDocumentLines =
            deliveryDocument
                .getDeliveryDocumentLines()
                .stream()
                .filter(
                    line ->
                        ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(
                            line.getPurchaseRefType()))
                .collect(Collectors.toList());
    return !filteredSSTKDocumentLines.isEmpty();
  }

  /*
   * 1. Throws Exception when Delivery is not in receivable (OPN, WRK) status
   * 2. Throws exception if delivery document contains all DA freights
   * 3. Throws exception if no active PO or PO line is available to receive
   *
   * @param gdmDeliveryDocumentList, instructionRequest
   * @throws ReceivingException, ReceivingBadDataException
   */
  public List<DeliveryDocument> validateAndProcessGdmDeliveryDocuments(
      List<DeliveryDocument> gdmDeliveryDocumentList, InstructionRequest instructionRequest)
      throws ReceivingException, ReceivingBadDataException {
    checkIfDeliveryStatusReceivable(gdmDeliveryDocumentList.get(0));
    List<DeliveryDocument> nonHistoryDeliveryDocuments =
        filterNonHistoryDeliveryDocuments(gdmDeliveryDocumentList, instructionRequest);
    return filterInvalidPoLinesFromDocuments(instructionRequest, nonHistoryDeliveryDocuments);
  }

  /**
   * This method filters out all the history PO's from the delivery document list that was received
   * from GDM
   *
   * @param deliveryDocumentList
   * @param instructionRequest
   * @return List<DeliveryDocument>
   */
  public List<DeliveryDocument> filterNonHistoryDeliveryDocuments(
      List<DeliveryDocument> deliveryDocumentList, InstructionRequest instructionRequest)
      throws ReceivingBadDataException {
    List<DeliveryDocument> filteredDeliveryDocuments =
        deliveryDocumentList
            .stream()
            .filter(
                document ->
                    !POStatus.HISTORY
                        .name()
                        .equalsIgnoreCase(document.getPurchaseReferenceStatus()))
            .collect(Collectors.toList());
    if (CollectionUtils.isEmpty(filteredDeliveryDocuments)) {
      LOGGER.error(
          "After filtering out History PO's, no active PO's are available to receive for the UPC: {} and delivery: {} combination",
          instructionRequest.getUpcNumber(),
          instructionRequest.getDeliveryNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.NO_ACTIVE_PO_AVAILABLE_TO_RECEIVE,
          String.format(
              ReceivingException.NO_ACTIVE_PO_AVAILABLE_TO_RECEIVE,
              instructionRequest.getUpcNumber(),
              instructionRequest.getDeliveryNumber()),
          instructionRequest.getUpcNumber(),
          instructionRequest.getDeliveryNumber());
    }
    return filteredDeliveryDocuments;
  }

  /**
   * Create instruction for SSTK UPC
   *
   * @param instructionRequest
   * @param totalReceivedQty
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.INTERNAL,
      executionFlow = "createInstructionForStapleStockUpcReceiving")
  public InstructionResponse createInstructionForStapleStockUpcReceiving(
      InstructionRequest instructionRequest, Long totalReceivedQty, HttpHeaders httpHeaders)
      throws ReceivingException {

    TenantContext.get().setCreateInstr4UpcReceivingCallStart(System.currentTimeMillis());
    InstructionResponse instructionResponse = new InstructionResponseImplNew();

    boolean IQS_INTEGRATION_ENABLED =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false);
    boolean SMART_SLOTTING_INTEGRATION_ENABLED =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false);

    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    instructionResponse.setDeliveryStatus(deliveryDocument.getDeliveryLegacyStatus());
    // Check if any problem tag exists => throw exception
    if (isProblemTagValidationApplicable(instructionRequest.getDeliveryDocuments())) {
      checkIfProblemTagPresent(
          instructionRequest.getUpcNumber(),
          instructionRequest.getDeliveryDocuments().get(0),
          appConfig.getProblemTagTypesList());
    }
    Instruction instruction =
        fetchExistingInstruction(deliveryDocument, instructionRequest, httpHeaders);
    if (Objects.isNull(instruction)) {
      String purchaseReferenceNumber = deliveryDocument.getPurchaseReferenceNumber();
      int purchaseReferenceLineNumber = deliveryDocumentLine.getPurchaseReferenceLineNumber();

      validatePoLineIsCancelledOrClosedOrRejected(deliveryDocumentLine);
      isNewItem(deliveryDocumentLine);

      instructionResponse =
          rdcReceivingUtils.checkIfVendorComplianceRequired(
              instructionRequest, deliveryDocument, instructionResponse);
      if (!CollectionUtils.isEmpty(instructionResponse.getDeliveryDocuments())) {
        return instructionResponse;
      }

      // Update additional item details from GDM when IQS integration enabled else get it from RDS
      if (IQS_INTEGRATION_ENABLED) {
        updateAdditionalItemDetailsFromGDM(Collections.singletonList(deliveryDocument));
      }

      if (!IQS_INTEGRATION_ENABLED || !SMART_SLOTTING_INTEGRATION_ENABLED) {
        nimRdsService.updateAdditionalItemDetails(
            Collections.singletonList(deliveryDocument), httpHeaders);
      }

      validateItemHandlingMethod(deliveryDocumentLine);
      validateInstructionCreationForSplitPallet(instructionRequest, deliveryDocumentLine);

      if (SMART_SLOTTING_INTEGRATION_ENABLED) {
        populatePrimeSlotFromSmartSlotting(deliveryDocument, httpHeaders, instructionRequest);
      }

      validateCreateSplitPalletInstructionForSymItem(instructionRequest, deliveryDocumentLine);

      if (isItemXBlocked(deliveryDocumentLine)) {
        LOGGER.error(
            "Given item:{} and handlingCode:{} matches with X-blocked item criteria.",
            deliveryDocumentLine.getItemNbr(),
            deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
        throw new ReceivingBadDataException(
            ExceptionCodes.CREATE_INSTRUCTION_ERROR,
            String.format(
                RdcConstants.X_BLOCK_ITEM_ERROR_MSG,
                String.valueOf(deliveryDocumentLine.getItemNbr())),
            String.valueOf(deliveryDocumentLine.getItemNbr()));
      }

      validateTiHiFromRdsAndGdm(deliveryDocumentLine);

      // need to consider open instruction received qty as well
      final int maxReceiveQty =
          deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();

      Integer receivedQtyByOtherInstructions =
          instructionPersisterService
              .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
                  deliveryDocument.getDeliveryNumber(),
                  deliveryDocumentLine.getPurchaseReferenceNumber(),
                  deliveryDocumentLine.getPurchaseReferenceLineNumber());
      totalReceivedQty = totalReceivedQty + receivedQtyByOtherInstructions;
      deliveryDocumentLine.setTotalReceivedQty(totalReceivedQty.intValue());

      if (Objects.isNull(deliveryDocumentLine.getOpenQty())) {
        final int openQty = deliveryDocumentLine.getTotalOrderQty() - totalReceivedQty.intValue();
        deliveryDocumentLine.setOpenQty(openQty);
      }

      deliveryDocumentLine.setMaxReceiveQty(maxReceiveQty);
      deliveryDocumentLine.setGtin(instructionRequest.getUpcNumber());

      Pair<Boolean, Long> pendingInstructions =
          checkIfNewInstructionCanBeCreated(
              instructionRequest,
              deliveryDocumentLine,
              receivedQtyByOtherInstructions,
              httpHeaders);
      if (!pendingInstructions.getKey()) {
        LOGGER.info(
            "Reached maximum receivable quantity threshold for PO:{} and POL:{}, returning overage alert instruction response",
            purchaseReferenceNumber,
            purchaseReferenceLineNumber);
        instruction = getOverageAlertInstruction(instructionRequest, httpHeaders);
      } else {
        instruction =
            InstructionUtils.mapDeliveryDocumentToInstruction(
                deliveryDocument,
                InstructionUtils.mapHttpHeaderToInstruction(
                    httpHeaders, InstructionUtils.createInstruction(instructionRequest)));

        LinkedTreeMap<String, Object> moveTreeMap =
            moveDetailsForInstruction(
                instructionRequest.getDoorNumber(), deliveryDocument, httpHeaders);

        instruction.setCreateUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
        instruction.setLastChangeUserId(
            httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
        instruction.setMove(moveTreeMap);

        // persist problem tag information in instruction table
        if (Objects.nonNull(instructionRequest.getProblemTagId())) {
          instruction.setProblemTagId(instructionRequest.getProblemTagId());
        }

        instruction.setGtin(instructionRequest.getUpcNumber());
        if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
          instruction.setSsccNumber(instructionRequest.getSscc());
          if (StringUtils.isBlank(instruction.getGtin())) {
            instruction.setGtin(deliveryDocumentLine.getOrderableGTIN());
          }
        }
        populateInstructionSetId(instructionRequest, instruction);
        instruction =
            persistInstruction(
                instruction,
                deliveryDocument,
                pendingInstructions.getValue(),
                instructionRequest.getProblemTagId(),
                instructionRequest);
      }
      instructionRequest.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
    } else {
      Pair<Instruction, List<DeliveryDocument>> existingInstruction =
          validateExistingInstruction(instruction, instructionRequest, httpHeaders);
      instructionResponse.setInstruction(existingInstruction.getKey());
      instructionResponse.setDeliveryDocuments(existingInstruction.getValue());
      TenantContext.get().setCreateInstr4UpcReceivingCallEnd(System.currentTimeMillis());
      return instructionResponse;
    }
    instructionResponse.setInstruction(instruction);
    instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
    TenantContext.get().setCreateInstr4UpcReceivingCallEnd(System.currentTimeMillis());
    return instructionResponse;
  }

  public boolean isProblemTagValidationApplicable(List<DeliveryDocument> deliveryDocuments) {
    return appConfig.isProblemTagCheckEnabled() && !CollectionUtils.isEmpty(deliveryDocuments);
  }

  /**
   * Validate and create instruction for 3 Scan Docktag
   *
   * @param instructionRequest
   * @param instructionResponse
   * @return instructionResponse
   */
  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.INTERNAL,
      executionFlow = "createInstructionForThreeScanDocktag")
  public InstructionResponse createInstructionForThreeScanDocktag(
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse,
      boolean isFreightIdentificationRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    List<DeliveryDocument> gdmDeliveryDocuments = instructionRequest.getDeliveryDocuments();
    boolean isSsccThreeScanEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_THREE_SCAN_DOCKTAG_ENABLED_FOR_SSCC,
            false);
    String sscc = instructionRequest.getSscc();
    if (isSsccThreeScanEnabled
        && StringUtils.isNotEmpty(sscc)
        && sscc.startsWith(DSDC_IDENTIFIER)
        && sscc.length() == ReceivingConstants.SSCC_LENGTH_20) {
      if (Objects.nonNull(instructionRequest.getLastScannedFreightType())
          && instructionRequest
              .getLastScannedFreightType()
              .equalsIgnoreCase(PoType.SSTK.getpoType())) {
        throw new ReceivingBadDataException(
            ExceptionCodes.THREE_SCAN_DOCKTAG_MIXED_ITEM_ERROR,
            String.format(
                ReceivingException.THREE_SCAN_DOCKTAG_MIXED_ITEM_ERROR_MSG,
                PoType.DSDC.getpoType()),
            PoType.DSDC.getpoType());
      }
      return getDSDCInstructionFor3ScanDockTag(instructionRequest, instructionResponse);
    }

    // validate if DSDC UPC is scanned and block user scan for UPCs
    List<DeliveryDocument> dsdcDeliveryDocuments =
        gdmDeliveryDocuments
            .stream()
            .filter(ReceivingUtils::isDSDCDocument)
            .collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(dsdcDeliveryDocuments)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.THREE_SCAN_DOCKTAG_DSDC_SCAN_UPC,
          ReceivingException.THREE_SCAN_DOCKTAG_DSDC_SCAN_UPC_ERROR_MSG);
    }

    boolean IQS_INTEGRATION_ENABLED =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false);
    boolean SMART_SLOTTING_INTEGRATION_ENABLED =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false);
    // Update additional item details from GDM when IQS integration enabled else get it from RDS
    if (IQS_INTEGRATION_ENABLED) {
      updateAdditionalItemDetailsFromGDM(gdmDeliveryDocuments);
    }
    if (!IQS_INTEGRATION_ENABLED || !SMART_SLOTTING_INTEGRATION_ENABLED) {
      nimRdsService.updateAdditionalItemDetails(gdmDeliveryDocuments, httpHeaders);
    }

    // freight identification scenario
    if (hasMoreUniqueItems(gdmDeliveryDocuments)) {
      if (!isFreightIdentificationRequest) {
        LOGGER.error(
            "Freight identification scenario identified for UPC: {} and delivery: {} ,so returning delivery documents",
            instructionRequest.getUpcNumber(),
            instructionRequest.getDeliveryNumber());
        instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
        return instructionResponse;
      } else {
        sortDeliveryDocumentLinesByLine(gdmDeliveryDocuments.get(0));
      }
    }

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = null;

    for (DeliveryDocument document : gdmDeliveryDocuments) {
      if (!CollectionUtils.isEmpty(document.getDeliveryDocumentLines())) {
        Optional<DeliveryDocumentLine> optionalDocumentLine;
        if (StringUtils.isNotBlank(instructionRequest.getLastScannedFreightType())) {
          optionalDocumentLine =
              document
                  .getDeliveryDocumentLines()
                  .stream()
                  .filter(
                      docLine ->
                          isValidFreightType(
                              instructionRequest.getLastScannedFreightType(), docLine))
                  .findFirst();
        } else {
          // prioritize DA if it is first scan
          optionalDocumentLine =
              document
                  .getDeliveryDocumentLines()
                  .stream()
                  .filter(
                      docLine ->
                          ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
                              docLine.getPurchaseRefType()))
                  .findFirst();
        }
        if (optionalDocumentLine.isPresent()) {
          deliveryDocument = document;
          deliveryDocumentLine = optionalDocumentLine.get();
          break;
        }
      }
    }

    /**
     * If no PO line found from above then it should be SSTK freight. So fetching the first PO and
     * POL from GDM delivery documents
     */
    if (Objects.isNull(deliveryDocumentLine)) {
      deliveryDocument = gdmDeliveryDocuments.get(0);
      deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    }

    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));

    validateThreeScanDocktagInstruction(
        instructionRequest.getLastScannedFreightType(),
        deliveryDocument.getPurchaseReferenceStatus(),
        deliveryDocumentLine);

    Instruction instruction = new Instruction();
    instruction.setInstructionCode(RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_CODE);
    instruction.setInstructionMsg(RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_MESSAGE);
    instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
    instructionResponse.setInstruction(instruction);

    return instructionResponse;
  }

  private InstructionResponse getDSDCInstructionFor3ScanDockTag(
      InstructionRequest instructionRequest, InstructionResponse instructionResponse) {
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_CODE);
    instruction.setInstructionMsg(RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_MESSAGE);
    ItemData additionalInfo = new ItemData();
    additionalInfo.setHandlingCode(RdcConstants.CONVEYABLE_HANDLING_CODE);
    additionalInfo.setPackTypeCode(RdcConstants.CASE_PACK_TYPE_CODE);
    DeliveryDocumentLine delDocLine = new DeliveryDocumentLine();
    delDocLine.setAdditionalInfo(additionalInfo);
    delDocLine.setPurchaseRefType(PoType.DSDC.name());
    delDocLine.setItemNbr(Long.valueOf(instructionRequest.getDeliveryNumber()));
    DeliveryDocument deliveryDoc = new DeliveryDocument();
    deliveryDoc.setDeliveryDocumentLines(Collections.singletonList(delDocLine));
    instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDoc));
    instructionResponse.setInstruction(instruction);
    return instructionResponse;
  }

  public void validateItemXBlocked(DeliveryDocumentLine deliveryDocumentLine) {
    if (isItemXBlocked(deliveryDocumentLine)) {
      LOGGER.error(
          "Given item:{} and handlingCode:{} matches with X-blocked item criteria.",
          deliveryDocumentLine.getItemNbr(),
          deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
      throw new ReceivingBadDataException(
          ExceptionCodes.CREATE_INSTRUCTION_ERROR,
          String.format(RdcConstants.X_BLOCK_ITEM_ERROR_MSG, deliveryDocumentLine.getItemNbr()),
          String.valueOf(deliveryDocumentLine.getItemNbr()));
    }
  }

  private void validateThreeScanDocktagInstruction(
      String lastScannedFreightType, String poStatus, DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    // validate mixed freight types
    if (StringUtils.isNotBlank((lastScannedFreightType))
        && !isValidFreightType(lastScannedFreightType, deliveryDocumentLine)) {
      LOGGER.error(
          "Freight type of item: {} does not match with lastScannedFreightType, so it is not eligible for 3 scan docktag",
          deliveryDocumentLine.getItemNbr());
      throw new ReceivingBadDataException(
          ExceptionCodes.THREE_SCAN_DOCKTAG_MIXED_ITEM_ERROR,
          String.format(
              ReceivingException.THREE_SCAN_DOCKTAG_MIXED_ITEM_ERROR_MSG,
              PoType.valueOf(deliveryDocumentLine.getPurchaseRefType()).getpoType()),
          PoType.valueOf(deliveryDocumentLine.getPurchaseRefType()).getpoType());
    }
    // validate cancelled, closed or rejected PO line status
    try {
      validatePoLineIsCancelledOrClosedOrRejected(deliveryDocumentLine);
    } catch (ReceivingException exception) {
      LOGGER.error(
          "Item: {} belongs to PO line that is cancelled, closed or rejected, so it is not eligible for 3 scan docktag",
          deliveryDocumentLine.getItemNbr());
      throw new ReceivingBadDataException(
          ExceptionCodes.THREE_SCAN_DOCKTAG_CANCELLED_REJECTED_POL_ERROR,
          String.format(
              ReceivingException.THREE_SCAN_DOCKTAG_CANCELLED_POL_ERROR_MSG,
              deliveryDocumentLine.getItemNbr()),
          String.valueOf(deliveryDocumentLine.getItemNbr()));
    }
    // check history PO
    if (POStatus.HISTORY.name().equalsIgnoreCase(poStatus)) {
      LOGGER.error(
          "Item: {} belongs to history PO, so it is not eligible for 3 scan docktag",
          deliveryDocumentLine.getItemNbr());
      throw new ReceivingBadDataException(
          ExceptionCodes.THREE_SCAN_DOCKTAG_HISTORY_PO_ERROR,
          String.format(
              ReceivingException.THREE_SCAN_DOCKTAG_HISTORY_PO_ERROR_MSG,
              deliveryDocumentLine.getItemNbr()),
          String.valueOf(deliveryDocumentLine.getItemNbr()));
    }
    // check blocked item
    if (isItemXBlocked(deliveryDocumentLine)) {
      LOGGER.error(
          "Item: {} is XBlocked, so it is not eligible for 3 scan docktag",
          deliveryDocumentLine.getItemNbr());
      throw new ReceivingBadDataException(
          ExceptionCodes.THREE_SCAN_DOCKTAG_XBLOCK_ITEM_ERROR,
          String.format(
              ReceivingException.THREE_SCAN_DOCKTAG_XBLOCK_ITEM_ERROR_MSG,
              deliveryDocumentLine.getItemNbr()),
          String.valueOf(deliveryDocumentLine.getItemNbr()));
    }
  }

  /**
   * This method gets prime slot from smart slotting. In case of Split pallet Atlas items receiving,
   * prime slot compatibility check is being handled within smart slotting itself. If the prime
   * slots are not compatible for the given items, error message displayed to the user. If the prime
   * slots are compatible then an instruction will be created with the prime slot returned for the
   * given item number
   *
   * @param deliveryDocument
   * @param httpHeaders
   * @param instructionRequest
   */
  public void populatePrimeSlotFromSmartSlotting(
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders,
      InstructionRequest instructionRequest) {
    SlottingPalletResponse slottingPalletResponse = null;

    if (rdcManagedConfig.isSplitPalletEnabled()
        && isSplitPalletInstruction(instructionRequest)
        && Objects.nonNull(instructionRequest.getInstructionSetId())) {
      List<String> existingSplitPalletInstructionDeliveryDocuments =
          instructionPersisterService.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
              Long.valueOf(instructionRequest.getDeliveryNumber()),
              instructionRequest.getInstructionSetId());

      if (!CollectionUtils.isEmpty(existingSplitPalletInstructionDeliveryDocuments)) {
        List<DeliveryDocument> deliveryDocuments =
            existingSplitPalletInstructionDeliveryDocuments
                .stream()
                .parallel()
                .map(
                    existingInstructionDeliveryDocument ->
                        gson.fromJson(existingInstructionDeliveryDocument, DeliveryDocument.class))
                .collect(Collectors.toList());

        TenantContext.get()
            .setCreateInstrGetPrimeSlotForSplitPalletCallStart(System.currentTimeMillis());
        slottingPalletResponse =
            slottingService.getPrimeSlotForSplitPallet(
                deliveryDocument.getDeliveryDocumentLines().get(0),
                deliveryDocuments,
                instructionRequest.getMessageId(),
                httpHeaders);
        TenantContext.get()
            .setCreateInstrGetPrimeSlotForSplitPalletCallEnd(System.currentTimeMillis());
      } else {
        LOGGER.info(
            "Split pallet instructions does not exist for instructionSetId:{}",
            instructionRequest.getInstructionSetId());
      }

    } else {
      TenantContext.get().setCreateInstrGetPrimeSlotCallStart(System.currentTimeMillis());
      slottingPalletResponse =
          slottingService.getPrimeSlot(
              deliveryDocument.getDeliveryDocumentLines().get(0),
              instructionRequest.getMessageId(),
              httpHeaders);
      TenantContext.get().setCreateInstrGetPrimeSlotCallEnd(System.currentTimeMillis());
    }

    if (Objects.nonNull(slottingPalletResponse)
        && !CollectionUtils.isEmpty(slottingPalletResponse.getLocations())) {
      SlottingDivertLocations slottingDivertLocations = null;
      Optional<SlottingDivertLocations> primeSlotLocation =
          slottingPalletResponse
              .getLocations()
              .stream()
              .filter(
                  divertLocation ->
                      divertLocation.getItemNbr()
                          == deliveryDocument
                              .getDeliveryDocumentLines()
                              .get(0)
                              .getItemNbr()
                              .intValue())
              .findFirst();
      if (primeSlotLocation.isPresent()) {
        slottingDivertLocations = primeSlotLocation.get();
        ItemData itemData = deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo();
        itemData.setPrimeSlot(slottingDivertLocations.getLocation());
        itemData.setPrimeSlotSize((int) slottingDivertLocations.getLocationSize());
        itemData.setSlotType(slottingDivertLocations.getSlotType());
        if (StringUtils.isNotEmpty(slottingDivertLocations.getAsrsAlignment())) {
          itemData.setAsrsAlignment(slottingDivertLocations.getAsrsAlignment());
        }
        deliveryDocument.getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
      }
    }
  }

  /**
   * Validate existing instruction for overage alert/warning
   *
   * @param instruction
   * @param instructionRequest
   * @param httpHeaders
   * @return Pair<Instruction, List < DeliveryDocument>>
   */
  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.INTERNAL,
      executionFlow = "validateExistingInstruction")
  public Pair<Instruction, List<DeliveryDocument>> validateExistingInstruction(
      Instruction instruction, InstructionRequest instructionRequest, HttpHeaders httpHeaders) {

    TenantContext.get().setRefreshInstrValidateExistringInstrCallStart(System.currentTimeMillis());
    boolean IQS_INTEGRATION_ENABLED =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false);
    boolean SMART_SLOTTING_INTEGRATION_ENABLED =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false);
    Pair<DeliveryDocument, Long> autoSelectedDeliveryDocument =
        autoSelectDocumentAndDocumentLine(
            instructionRequest.getDeliveryDocuments(),
            RdcConstants.QTY_TO_RECEIVE,
            instructionRequest.getUpcNumber(),
            httpHeaders);
    DeliveryDocument deliveryDocument = autoSelectedDeliveryDocument.getKey();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    DeliveryDocument deliveryDocument4mInstruction =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine4mInstruction =
        deliveryDocument4mInstruction.getDeliveryDocumentLines().get(0);
    if (!Objects.isNull(deliveryDocumentLine4mInstruction.getAutoPoSelectionOverageIncluded())) {
      deliveryDocumentLine.setAutoPoSelectionOverageIncluded(
          deliveryDocumentLine4mInstruction.getAutoPoSelectionOverageIncluded());
    }

    TenantContext.get().setAtlasRcvChkNewInstCanBeCreatedStart(System.currentTimeMillis());
    int pendingInstructionsCumulativeProjectedReceivedQty =
        instructionPersisterService
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                instruction.getDeliveryNumber(),
                instruction.getPurchaseReferenceNumber(),
                instruction.getPurchaseReferenceLineNumber());
    pendingInstructionsCumulativeProjectedReceivedQty =
        Objects.isNull(pendingInstructionsCumulativeProjectedReceivedQty)
            ? 0
            : pendingInstructionsCumulativeProjectedReceivedQty;
    TenantContext.get().setAtlasRcvChkNewInstCanBeCreatedEnd(System.currentTimeMillis());

    // This value will be only available for split pallet in RDC
    int pendingInstructionsCumulativeReceivedQty =
        instructionPersisterService
            .getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
                instruction.getDeliveryNumber(),
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber());
    int pendingInstructionsQty =
        pendingInstructionsCumulativeProjectedReceivedQty
            + pendingInstructionsCumulativeReceivedQty;

    int currentReceivedQuantityInVnpk = autoSelectedDeliveryDocument.getValue().intValue();
    int projectedReceiveQty = instruction.getProjectedReceiveQty();
    int maxReceiveQuantity =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    int totalInstructionQty = currentReceivedQuantityInVnpk + projectedReceiveQty;
    final int openQty =
        Objects.isNull(instruction.getProblemTagId())
            ? deliveryDocumentLine.getTotalOrderQty() - currentReceivedQuantityInVnpk
            : instruction.getProjectedReceiveQty();

    // Update additional item details from GDM when IQS integration enabled
    if (IQS_INTEGRATION_ENABLED) {
      updateAdditionalItemDetailsFromGDM(Collections.singletonList(deliveryDocument));
    }

    if (!IQS_INTEGRATION_ENABLED || !SMART_SLOTTING_INTEGRATION_ENABLED) {
      nimRdsService.updateAdditionalItemDetails(
          Collections.singletonList(deliveryDocument), httpHeaders);
    }

    if (SMART_SLOTTING_INTEGRATION_ENABLED) {
      populatePrimeSlotFromSmartSlotting(deliveryDocument, httpHeaders, instructionRequest);
    }

    validateTiHiFromRdsAndGdm(deliveryDocumentLine);

    deliveryDocumentLine.setOpenQty(openQty);
    deliveryDocumentLine.setTotalReceivedQty(currentReceivedQuantityInVnpk);
    deliveryDocumentLine.setMaxReceiveQty(maxReceiveQuantity);
    deliveryDocumentLine.setMaxAllowedOverageQtyIncluded(false);

    int sumOfPendingInstructionQtyFromOtherUsers =
        pendingInstructionsQty - instruction.getProjectedReceiveQty();

    if (sumOfPendingInstructionQtyFromOtherUsers == maxReceiveQuantity) {
      LOGGER.info(
          "Reached maximum instructions receive quantity for PO:{} and POL:{}, setting MaxAllowedOverageQtyIncluded flag to true",
          instruction.getPurchaseReferenceNumber(),
          instruction.getPurchaseReferenceLineNumber());
      deliveryDocumentLine.setMaxAllowedOverageQtyIncluded(true);
    } else if ((currentReceivedQuantityInVnpk + sumOfPendingInstructionQtyFromOtherUsers)
        > maxReceiveQuantity) {
      LOGGER.error(
          "Reached maximum receivable quantity threshold for this PO: {} and POL: {}",
          instruction.getPurchaseReferenceNumber(),
          instruction.getPurchaseReferenceLineNumber());
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      instruction.setLastChangeUserId(userId);
      instruction.setCompleteUserId(userId);
      instruction.setCompleteTs(new Date());
      instructionPersisterService.saveInstruction(instruction);
      instruction = getOverageAlertInstruction(instructionRequest, httpHeaders);
      deliveryDocument.getDeliveryDocumentLines().clear();
      deliveryDocument.getDeliveryDocumentLines().add(deliveryDocumentLine);
      instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
      TenantContext.get().setRefreshInstrValidateExistringInstrCallEnd(System.currentTimeMillis());
      return new Pair<>(instruction, Collections.singletonList(deliveryDocument));
    } else if (totalInstructionQty
        > deliveryDocument.getDeliveryDocumentLines().get(0).getTotalOrderQty()) {
      LOGGER.info(
          "Received all of the order quantity for PO:{} and POL:{}, setting MaxAllowedOverageQtyIncluded flag to true",
          instruction.getPurchaseReferenceNumber(),
          instruction.getPurchaseReferenceLineNumber());
      deliveryDocumentLine.setMaxAllowedOverageQtyIncluded(true);
    }
    LinkedTreeMap<String, Object> existingMoveTreeMap = instruction.getMove();
    if (!CollectionUtils.isEmpty(existingMoveTreeMap)) {
      existingMoveTreeMap.put(
          ReceivingConstants.MOVE_PRIME_LOCATION,
          deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
      existingMoveTreeMap.put(
          ReceivingConstants.MOVE_PRIME_LOCATION_SIZE,
          deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize());
      LOGGER.info(
          "Setting latest move information in move object:{} on instruction: {}",
          existingMoveTreeMap,
          instruction.getId());
      instruction.setMove(existingMoveTreeMap);
    }

    instruction.setProjectedReceiveQty(
        rdcQuantityCalculator.getProjectedReceiveQtyByTiHiBasedOnScanType(
            deliveryDocumentLine, (long) sumOfPendingInstructionQtyFromOtherUsers, instruction));
    deliveryDocument.getDeliveryDocumentLines().clear();
    deliveryDocument.getDeliveryDocumentLines().add(deliveryDocumentLine);
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    instruction = instructionPersisterService.saveInstruction(instruction);
    TenantContext.get().setRefreshInstrValidateExistringInstrCallEnd(System.currentTimeMillis());
    return new Pair<>(instruction, Collections.singletonList(deliveryDocument));
  }

  private boolean isItemXBlocked(DeliveryDocumentLine deliveryDocumentLine) {
    return Arrays.asList(X_BLOCK_ITEM_HANDLING_CODES)
        .contains(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
  }

  public InstructionResponse checkIfInstructionExistsWithSsccAndValidateInstruction(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
    InstructionResponse instructionResponse = null;
    if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      List<Instruction> instructions =
          instructionPersisterService.checkIfInstructionExistsWithSscc(instructionRequest);
      Instruction instruction = null;
      if (CollectionUtils.isEmpty(instructions)) {
        if (Objects.isNull(instructionRequest.getDeliveryDocuments())) {
          checkIfContainerWithSsccReceived(instructionRequest.getSscc());
        } else {
          Long itemNumberFromRequest =
              instructionRequest
                  .getDeliveryDocuments()
                  .get(0)
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getItemNbr();
          checkIfContainerWithSsccAndItemReceived(
              instructionRequest.getSscc(), itemNumberFromRequest);
        }

      } else {
        if (Objects.isNull(instructionRequest.getDeliveryDocuments())) {
          if (instructions.size() > 1) {
            return instructionResponse;
          } else {
            instruction = instructions.get(0);
          }
        } else {

          Long itemNumberFromRequest =
              instructionRequest
                  .getDeliveryDocuments()
                  .get(0)
                  .getDeliveryDocumentLines()
                  .get(0)
                  .getItemNbr();
          checkIfContainerWithSsccAndItemReceived(
              instructionRequest.getSscc(), itemNumberFromRequest);

          for (Instruction ins : instructions) {
            List<DeliveryDocumentLine> deliveryDocumentLines =
                new Gson()
                    .fromJson(ins.getDeliveryDocument(), DeliveryDocument.class)
                    .getDeliveryDocumentLines();
            Long itemNumberFromExistingInstruction = deliveryDocumentLines.get(0).getItemNbr();
            if (Objects.equals(itemNumberFromRequest, itemNumberFromExistingInstruction)) {
              instruction = ins;
              break;
            }
          }
        }
      }

      if (!Objects.isNull(instruction)) {
        if (userId.equalsIgnoreCase(instruction.getLastChangeUserId())) {

          instructionResponse = new InstructionResponseImplNew();
          instructionRequest.setDeliveryDocuments(
              Arrays.asList(
                  gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class)));
          Pair<Instruction, List<DeliveryDocument>> existingInstruction =
              validateExistingInstruction(instruction, instructionRequest, httpHeaders);
          instructionResponse.setInstruction(existingInstruction.getKey());
          instructionResponse.setDeliveryDocuments(existingInstruction.getValue());
        } else {
          throw new ReceivingBadDataException(
              ExceptionCodes.MULTI_INSTRUCTION_NOT_SUPPORTED,
              ReceivingConstants.REQUEST_TRANSFTER_INSTR_ERROR_CODE);
        }
      }
    }
    return instructionResponse;
  }

  public Instruction fetchExistingInstruction(
      DeliveryDocument deliveryDocument,
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders) {
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Instruction instruction = null;
    instruction = instructionPersisterService.fetchExistingInstructionIfexists(instructionRequest);
    if (Objects.nonNull(instruction)) {
      LOGGER.info(
          "Found existing OPEN instruction for messageId: {}", instructionRequest.getMessageId());
      return instruction;
    } else if (Objects.nonNull(instructionRequest.getProblemTagId())) {
      instruction =
          instructionPersisterService
              .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId(
                  instructionRequest, userId, instructionRequest.getProblemTagId());
      if (Objects.nonNull(instruction)) {
        LOGGER.info(
            "Found existing OPEN instruction for problemTagId: {}",
            instructionRequest.getProblemTagId());
        return instruction;
      }
    } else {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      if (ObjectUtils.allNotNull(
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber())
          && !InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
        if (isSplitPalletInstruction(instructionRequest)) {
          instruction =
              fetchExistingSplitPalletInstruction(deliveryDocument, instructionRequest, userId);
        } else {
          instruction =
              instructionPersisterService
                  .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                      deliveryDocument, instructionRequest, userId);
        }
        if (Objects.nonNull(instruction)) {
          LOGGER.info(
              "Found existing OPEN instruction for delivery: {}, GTIN: {}, SSCC:{}, userId: {}, PO: {} and POL: {}",
              instructionRequest.getDeliveryNumber(),
              instructionRequest.getUpcNumber(),
              instructionRequest.getSscc(),
              userId,
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
          return instruction;
        }
      }
    }
    return null;
  }

  private Instruction fetchExistingSplitPalletInstruction(
      DeliveryDocument deliveryDocument, InstructionRequest instructionRequest, String userId) {
    if (Objects.nonNull(instructionRequest.getInstructionSetId())) {
      return instructionPersisterService
          .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
              deliveryDocument,
              instructionRequest,
              userId,
              instructionRequest.getInstructionSetId());
    } else {
      return instructionPersisterService
          .fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
              deliveryDocument, instructionRequest, userId);
    }
  }

  public int getProjectedReceiveQtyByTiHi(
      DeliveryDocumentLine deliveryDocumentLine,
      Long pendingInstructionsCumulativeProjectedReceivedQty,
      Instruction instruction4mDB) {
    final int palletTi = deliveryDocumentLine.getPalletTie();
    final int palletHi = deliveryDocumentLine.getPalletHigh();
    final int maxReceivedQty =
        Objects.isNull(deliveryDocumentLine.getAutoPoSelectionOverageIncluded())
                || deliveryDocumentLine.getAutoPoSelectionOverageIncluded()
            ? deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit()
            : deliveryDocumentLine.getTotalOrderQty();
    int quantityCanBeReceived;

    int tiHiProjectedQty = 0;
    if (palletTi > 0 && palletHi > 0) {
      tiHiProjectedQty = palletTi * palletHi;
    }

    if (Objects.nonNull(instruction4mDB.getProblemTagId())) {
      quantityCanBeReceived =
          deliveryDocumentLine.getOpenQty() < tiHiProjectedQty
              ? deliveryDocumentLine.getOpenQty()
              : tiHiProjectedQty;
      LOGGER.info(
          "Projected instruction quantity for problemTagId:{} is {}",
          instruction4mDB.getProblemTagId(),
          quantityCanBeReceived);

      ProblemLabel problemLabel =
          tenantSpecificConfigReader
              .getConfiguredInstance(
                  TenantContext.getFacilityNum().toString(),
                  ReceivingConstants.PROBLEM_SERVICE,
                  ProblemService.class)
              .findProblemLabelByProblemTagId(instruction4mDB.getProblemTagId());
      FitProblemTagResponse fitProblemTagResponse =
          gson.fromJson(problemLabel.getProblemResponse(), FitProblemTagResponse.class);
      Integer resolutionQty = fitProblemTagResponse.getResolutions().get(0).getQuantity();

      Integer totalReceivedProblemQty =
          instructionPersisterService
              .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                  instruction4mDB.getPurchaseReferenceNumber(),
                  instruction4mDB.getPurchaseReferenceLineNumber(),
                  instruction4mDB.getProblemTagId())
              .intValue();

      // override total order qty & total receive qty as specific to problem receiving
      deliveryDocumentLine.setTotalOrderQty(resolutionQty);
      deliveryDocumentLine.setTotalReceivedQty(totalReceivedProblemQty);
      return quantityCanBeReceived;
    }

    // in case of creating new instruction
    if (instruction4mDB.getProjectedReceiveQty() == 0) {
      quantityCanBeReceived =
          (deliveryDocumentLine.getTotalReceivedQty() == 0)
              ? maxReceivedQty - pendingInstructionsCumulativeProjectedReceivedQty.intValue()
              : maxReceivedQty - deliveryDocumentLine.getTotalReceivedQty();
    } else {
      if (deliveryDocumentLine.getTotalReceivedQty() == 0) {
        quantityCanBeReceived =
            tiHiProjectedQty < maxReceivedQty ? tiHiProjectedQty : maxReceivedQty;
      } else {
        quantityCanBeReceived = maxReceivedQty - deliveryDocumentLine.getTotalReceivedQty();
      }
    }

    final int projectedReceiveQty =
        (tiHiProjectedQty > 0 && tiHiProjectedQty < quantityCanBeReceived)
            ? tiHiProjectedQty
            : quantityCanBeReceived;

    return projectedReceiveQty;
  }

  private Instruction persistInstruction(
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      Long pendingInstructionsCumulativeProjectedReceivedQty,
      String problemTagId,
      InstructionRequest instructionRequest) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    instruction.setPrintChildContainerLabels(false);
    instruction.setProjectedReceiveQty(
        rdcQuantityCalculator.getProjectedReceiveQtyByTiHiBasedOnScanType(
            deliveryDocumentLine, pendingInstructionsCumulativeProjectedReceivedQty, instruction));
    instruction.setInstructionMsg(RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setActivityName(WFTInstruction.SSTK.getActivityName());

    final int totalInstructionQty =
        deliveryDocumentLine.getTotalReceivedQty()
            + instruction.getProjectedReceiveQty()
            + pendingInstructionsCumulativeProjectedReceivedQty.intValue();
    if (totalInstructionQty > deliveryDocumentLine.getTotalOrderQty()
        && Objects.isNull(problemTagId)) {
      LOGGER.info(
          "Received all of the order quantity for PO:{} and POL:{}, setting MaxAllowedOverageQtyIncluded flag to true",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      deliveryDocumentLine.setMaxAllowedOverageQtyIncluded(true);
    }
    deliveryDocument.getDeliveryDocumentLines().clear();
    deliveryDocument.getDeliveryDocumentLines().add(deliveryDocumentLine);
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    return instructionPersisterService.saveInstruction(instruction);
  }

  public LinkedTreeMap<String, Object> moveDetailsForInstruction(
      String doorNumber, DeliveryDocument deliveryDocument, HttpHeaders httpHeaders) {

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();

    moveTreeMap.put(ReceivingConstants.MOVE_FROM_LOCATION, doorNumber);
    moveTreeMap.put(
        ReceivingConstants.MOVE_CORRELATION_ID,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_ON, new Date());
    moveTreeMap.put(
        ReceivingConstants.MOVE_LAST_CHANGED_BY,
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    if (deliveryDocumentLine.getAdditionalInfo() != null) {
      moveTreeMap.put(
          ReceivingConstants.MOVE_PRIME_LOCATION,
          deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
      moveTreeMap.put(
          ReceivingConstants.MOVE_PRIME_LOCATION_SIZE,
          deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize());
    }

    return moveTreeMap;
  }

  /**
   * @param receiveInstructionRequest
   * @param trackingId
   * @param slot
   * @param httpHeaders
   * @return
   */
  public LinkedTreeMap<String, Object> moveDetailsForInstruction(
      ReceiveInstructionRequest receiveInstructionRequest,
      String trackingId,
      String slot,
      HttpHeaders httpHeaders) {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(
        ReceivingConstants.MOVE_FROM_LOCATION, receiveInstructionRequest.getDoorNumber());
    moveTreeMap.put(
        ReceivingConstants.MOVE_CORRELATION_ID,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_ON, new Date());
    moveTreeMap.put(
        ReceivingConstants.MOVE_LAST_CHANGED_BY,
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, slot);
    moveTreeMap.put(ReceivingConstants.MOVE_CONTAINER_TAG, trackingId);
    MoveType moveType =
        MoveType.builder()
            .code(rdcManagedConfig.getMoveTypeCode())
            .desc(rdcManagedConfig.getMoveTypeDesc())
            .build();
    moveTreeMap.put(ReceivingConstants.MOVE_TYPE, moveType);
    return moveTreeMap;
  }

  /**
   * Auto select PO/POL based on PO's must arrive by date value. 1. Exception will be thrown when
   * RDS returned an error response while fetching received quantity for all PO and PO lines
   * combinations. 2. If delivery documents from GDM has single PO then auto PO select logic will
   * not be applied. 3. If delivery documents from GDM has single PO with multiple PO lines OR more
   * than one delivery document then auto PO selection logic will be applied. 4. Auto PO selection
   * logic will be applied first for all the lines open quantity and then allowed overage quantity.
   * 5. Once all open and allowed overage quantity is fulfilled, exception will be thrown to report
   * overage when user tried to receive any more items on these PO's.
   *
   * @param deliveryDocuments list of delivery documents
   * @param qtyToReceive quantity to be received
   * @return selected PO POL if any otherwise null
   */
  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.INTERNAL,
      executionFlow = "MULTI-PO-AND-POL-SELECT")
  public Pair<DeliveryDocument, Long> autoSelectDocumentAndDocumentLine(
      List<DeliveryDocument> deliveryDocuments,
      int qtyToReceive,
      String upcNumber,
      HttpHeaders httpHeaders) {
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, httpHeaders, upcNumber);
    LOGGER.info(
        "Fetched received qty from RDS by list of PO and POL {}", receivedQuantityResponseFromRDS);
    return autoSelectPoPoLine(
        deliveryDocuments, receivedQuantityResponseFromRDS, qtyToReceive, upcNumber);
  }

  /**
   * @param deliveryDocuments
   * @param receivedQuantityResponseFromRDS
   * @param qtyToReceive
   * @param upcNumber
   * @return
   */
  public Pair<DeliveryDocument, Long> autoSelectPoPoLine(
      List<DeliveryDocument> deliveryDocuments,
      ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS,
      int qtyToReceive,
      String upcNumber) {
    if (isSinglePoAndPoLine(deliveryDocuments)) {
      LOGGER.info(
          "Processing single delivery document, so auto PO selection logic is not required. Returning the given delivery document with PO:{} and POL:{}",
          deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber(),
          deliveryDocuments
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getPurchaseReferenceLineNumber());
      return getReceiptsFromRDSForSinglePoAndPoLine(
          deliveryDocuments.get(0), receivedQuantityResponseFromRDS);
    } else {
      LOGGER.info(
          "Applying auto selection PO logic for deliveryDocuments of size:{}",
          deliveryDocuments.size());
      return autoSelectDocumentForMultiPoOrPoLines(
          deliveryDocuments, qtyToReceive, receivedQuantityResponseFromRDS, upcNumber);
    }
  }

  /**
   * This method validates GDM delivery documents and filters out only active and receivable
   * delivery document line
   *
   * @param deliveryDocument
   * @param receivedQtyResponseMap
   */
  boolean filterActivePoLinesFromRDSResponse(
      DeliveryDocument deliveryDocument, Map<String, Long> receivedQtyResponseMap) {
    boolean activePoLineExists = false;

    List<DeliveryDocumentLine> filteredDeliveryDocumentLines =
        deliveryDocument
            .getDeliveryDocumentLines()
            .stream()
            .filter(
                line -> {
                  String key =
                      line.getPurchaseReferenceNumber()
                          + "-"
                          + line.getPurchaseReferenceLineNumber();
                  return receivedQtyResponseMap.containsKey(key);
                })
            .collect(Collectors.toList());

    if (!CollectionUtils.isEmpty(filteredDeliveryDocumentLines)) {
      activePoLineExists = true;
      if (deliveryDocument.getDeliveryDocumentLines().size()
          != filteredDeliveryDocumentLines.size()) {
        deliveryDocument.getDeliveryDocumentLines().clear();
        deliveryDocument.setDeliveryDocumentLines(filteredDeliveryDocumentLines);
      }
    }

    return activePoLineExists;
  }

  public Pair<DeliveryDocumentLine, Long> getReceiptsFromRDSByDocumentLine(
      List<DeliveryDocumentLine> documentLines, HttpHeaders httpHeaders) {
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocumentLine(documentLines.get(0), httpHeaders);
    LOGGER.info(
        "Fetched received qty from RDS by delivery document line for PO and POL {}",
        receivedQuantityResponseFromRDS);

    Map<String, Long> receivedQtyMapFromRds =
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine();
    Map<String, String> errorMapFromRds =
        receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine();

    if (CollectionUtils.isEmpty(receivedQtyMapFromRds)
        && !CollectionUtils.isEmpty(errorMapFromRds)) {
      LOGGER.error(
          "No receipts found in RDS for the given delivery document line, throwing an exception");
      String key =
          documentLines.get(0).getPurchaseReferenceNumber()
              + ReceivingConstants.DELIM_DASH
              + documentLines.get(0).getPurchaseReferenceLineNumber();
      String errorMessage = errorMapFromRds.get(key);
      throw new ReceivingBadDataException(
          ExceptionCodes.GET_RECEIPTS_ERROR_RESPONSE_IN_RDS,
          String.format(ReceivingConstants.GET_RECEIPTS_ERROR_RESPONSE_IN_RDS, errorMessage),
          errorMessage);

    } else {
      LOGGER.info(
          "Receipts found in RDS for the given delivery document line with PO:{} and POL:{}",
          documentLines.get(0).getPurchaseReferenceNumber(),
          documentLines.get(0).getPurchaseReferenceLineNumber());
      return getReceivedQtyFromRDSResponse(documentLines.get(0), receivedQuantityResponseFromRDS);
    }
  }

  public Boolean isSinglePoAndPoLine(List<DeliveryDocument> deliveryDocuments) {
    return deliveryDocuments.size() == 1
        && deliveryDocuments.get(0).getDeliveryDocumentLines().size() == 1;
  }

  public Pair<DeliveryDocument, Long> getReceiptsFromRDSForSinglePoAndPoLine(
      DeliveryDocument deliveryDocument,
      ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS) {
    Map<String, Long> receivedQtyMapFromRds =
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    String poNumber = deliveryDocumentLine.getPurchaseReferenceNumber();
    int poLineNumber = deliveryDocumentLine.getPurchaseReferenceLineNumber();
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;
    return new Pair<>(deliveryDocument, receivedQtyMapFromRds.get(key));
  }

  private Pair<DeliveryDocumentLine, Long> getReceivedQtyFromRDSResponse(
      DeliveryDocumentLine documentLine,
      ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS) {
    Map<String, Long> receivedQtyMapFromRds =
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine();
    Map<String, String> errorMapFromRds =
        receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine();
    String poNumber = documentLine.getPurchaseReferenceNumber();
    Integer poLineNumber = documentLine.getPurchaseReferenceLineNumber();
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;
    Long totalReceivedQty = 0L;

    if (!CollectionUtils.isEmpty(receivedQtyMapFromRds)) {
      totalReceivedQty = receivedQtyMapFromRds.get(key);
      return new Pair<>(documentLine, totalReceivedQty);
    }
    String errorMessage = errorMapFromRds.get(key);
    LOGGER.error(
        "Received error response: {} from RDS for PO:{} and POL:{}",
        errorMessage,
        poNumber,
        poLineNumber);
    throw new ReceivingBadDataException(
        ExceptionCodes.QUANTITY_RCVD_ERROR_FROM_RDS,
        String.format(RdcConstants.QUANTITY_RECEIVED_ERROR_FROM_RDS, errorMessage),
        errorMessage);
  }

  public Pair<DeliveryDocument, Long> autoSelectDocumentForMultiPoOrPoLines(
      List<DeliveryDocument> deliveryDocuments,
      int qtyToReceive,
      ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS,
      String upcNumber) {
    DeliveryDocument selectedPOAgainstAllowedOverages = null;
    DeliveryDocument selectedDeliveryDocument = null;
    DeliveryDocumentLine selectedPOLAgainstAllowedOverages = null;
    DeliveryDocumentLine selectedDeliveryDocumentLine = null;
    Long totalReceivedQtyInVnpk = 0L;
    Long totalReceivedQtyInVnpkAgainstAllowedOverages = 0L;
    boolean autoPoSelectionOverageIncluded = false;

    populatePurchaseRefTypeInDeliveryDocument(deliveryDocuments);
    deliveryDocuments =
        deliveryDocuments
            .stream()
            .sorted(
                Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                    .thenComparing(DeliveryDocument::getPurchaseRefType))
            .collect(Collectors.toList());
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      List<DeliveryDocumentLine> deliveryDocumentLines =
          ListUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines());
      deliveryDocumentLines =
          deliveryDocumentLines
              .stream()
              .sorted(Comparator.comparing(DeliveryDocumentLine::getPurchaseReferenceLineNumber))
              .collect(Collectors.toList());
      for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocumentLines) {
        String key =
            deliveryDocument.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        totalReceivedQtyInVnpk =
            receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key);

        if (Objects.nonNull(totalReceivedQtyInVnpk)) {
          if (totalReceivedQtyInVnpk + qtyToReceive <= deliveryDocumentLine.getTotalOrderQty()) {
            selectedDeliveryDocument = deliveryDocument;
            selectedDeliveryDocumentLine = deliveryDocumentLine;
            break;
          } else if (Objects.isNull(selectedPOAgainstAllowedOverages)) {
            if (totalReceivedQtyInVnpk + qtyToReceive
                <= deliveryDocumentLine.getTotalOrderQty()
                    + deliveryDocumentLine.getOverageQtyLimit()) {
              selectedPOAgainstAllowedOverages = deliveryDocument;
              selectedPOLAgainstAllowedOverages = deliveryDocumentLine;
              totalReceivedQtyInVnpkAgainstAllowedOverages = totalReceivedQtyInVnpk;
            }
          }
        }
      }
      if (Objects.nonNull(selectedDeliveryDocument)) break;
    }
    if (Objects.nonNull(selectedDeliveryDocument)) {
      LOGGER.info(
          "Selected PO {} and POL {} against ordered qty",
          selectedDeliveryDocument.getPurchaseReferenceNumber(),
          selectedDeliveryDocumentLine.getPurchaseReferenceLineNumber());
      selectedDeliveryDocument.getDeliveryDocumentLines().clear();
      selectedDeliveryDocument.getDeliveryDocumentLines().add(selectedDeliveryDocumentLine);
      selectedDeliveryDocumentLine.setAutoPoSelectionOverageIncluded(
          autoPoSelectionOverageIncluded);
      return new Pair<>(selectedDeliveryDocument, totalReceivedQtyInVnpk);
    } else if (Objects.nonNull(selectedPOAgainstAllowedOverages)) {
      LOGGER.info(
          "Selected PO {} and POL {} against allowed ovg qty",
          selectedPOAgainstAllowedOverages.getPurchaseReferenceNumber(),
          selectedPOLAgainstAllowedOverages.getPurchaseReferenceLineNumber());
      selectedPOAgainstAllowedOverages.getDeliveryDocumentLines().clear();
      autoPoSelectionOverageIncluded = true;
      selectedPOLAgainstAllowedOverages.setAutoPoSelectionOverageIncluded(
          autoPoSelectionOverageIncluded);
      selectedPOAgainstAllowedOverages
          .getDeliveryDocumentLines()
          .add(selectedPOLAgainstAllowedOverages);
      return new Pair<>(
          selectedPOAgainstAllowedOverages, totalReceivedQtyInVnpkAgainstAllowedOverages);
    }
    LOGGER.error(
        "Maximum allowable quantity has been received for delivery: {} and UPC: {}",
        deliveryDocuments.get(0).getDeliveryNumber(),
        upcNumber);
    DeliveryDocument autoSelectedDeliveryDocumentResponse =
        getDeliveryDocumentForOverageReporting(deliveryDocuments);
    Integer totalReceivedQtyFromRdsByPoAndPoLine =
        getReceivedQtyByPoAndPoline(
            autoSelectedDeliveryDocumentResponse, receivedQuantityResponseFromRDS);
    return new Pair<>(
        autoSelectedDeliveryDocumentResponse, totalReceivedQtyFromRdsByPoAndPoLine.longValue());
  }

  private void populatePurchaseRefTypeInDeliveryDocument(List<DeliveryDocument> deliveryDocuments) {
    deliveryDocuments.forEach(
        deliveryDocument -> {
          if (isDADocument(deliveryDocument)) {
            deliveryDocument.setPurchaseRefType(ReceivingConstants.PURCHASE_REF_TYPE_DA);
          } else if (isSSTKDocument(deliveryDocument)) {
            deliveryDocument.setPurchaseRefType(ReceivingConstants.PURCHASE_REF_TYPE_SSTK);
          } else {
            deliveryDocument.setPurchaseRefType(ReceivingConstants.PURCHASE_REF_TYPE_DSDC);
          }
        });
  }

  /**
   * Returns false when user is trying to create instruction for more than maximum receive quantity
   * Returns true when user is trying to create instruction within maximum receive quantity
   *
   * @param deliveryDocumentLine
   * @return Pair<Boolean, Long>
   */
  public Pair<Boolean, Long> checkIfNewInstructionCanBeCreated(
      InstructionRequest instructionRequest,
      DeliveryDocumentLine deliveryDocumentLine,
      int receivedQtyByOtherInstructions,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    Long defaultPendingInstructionsQty = 0L;
    if (deliveryDocumentLine.getTotalReceivedQty() >= deliveryDocumentLine.getMaxReceiveQty()) {

      if (Objects.nonNull(instructionRequest.getProblemTagId())) {
        rdcProblemUtils.reportErrorForProblemReceiving(
            instructionRequest.getProblemTagId(),
            null,
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            httpHeaders);
      } else {
        LOGGER.error(
            "Have already received maximum allowable quantity threshold for PO:{} and POL:{}",
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
        return new Pair<>(false, defaultPendingInstructionsQty);
      }
    } else {
      TenantContext.get().setAtlasRcvChkNewInstCanBeCreatedStart(System.currentTimeMillis());
      // not split pallet
      int pendingInstructionsCumulativeProjectedReceivedQty =
          instructionPersisterService
              .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  deliveryDocumentLine.getPurchaseReferenceNumber(),
                  deliveryDocumentLine.getPurchaseReferenceLineNumber());
      TenantContext.get().setAtlasRcvChkNewInstCanBeCreatedEnd(System.currentTimeMillis());

      int pendingInstructionsQty =
          pendingInstructionsCumulativeProjectedReceivedQty + receivedQtyByOtherInstructions;

      if (pendingInstructionsCumulativeProjectedReceivedQty
              + deliveryDocumentLine.getTotalReceivedQty()
          >= deliveryDocumentLine.getMaxReceiveQty()) {
        LOGGER.error(
            "Sum of open instruction quantity and current received quantity exceeds maxReceiveQty for PO:{} and POL:{}",
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
        InstructionError instructionError;
        String errorCode;
        // if openInstruction is only split pallet, throw different error code and message so that
        // client will not show transfer instructions dialog
        int nonSplitPalletInstructionCount =
            instructionPersisterService.findNonSplitPalletInstructionCount(
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber());
        if (nonSplitPalletInstructionCount == 0 || isSplitPalletInstruction(instructionRequest)) {
          instructionError =
              InstructionErrorCode.getErrorValue(ReceivingException.MUTLI_USER_ERROR_SPLIT_PALLET);
          errorCode = ReceivingConstants.MULTI_INSTR_ERROR_CODE;
        } else if (Objects.nonNull(instructionRequest.getProblemTagId())) {
          // MUTLI_USER_ERROR for problem receiving
          instructionError =
              InstructionErrorCode.getErrorValue(
                  ReceivingException.MUTLI_USER_ERROR_FOR_PROBLEM_RECEIVING);
          errorCode = ReceivingConstants.MULTI_INSTR_PROBLEM_ERROR_CODE;
        } else {
          // else below MUTLI_USER_ERROR
          instructionError =
              InstructionErrorCode.getErrorValue(ReceivingException.MULTI_USER_ERROR);
          errorCode = ReceivingConstants.REQUEST_TRANSFTER_INSTR_ERROR_CODE;
        }
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            errorCode,
            instructionError.getErrorHeader());
      }
      return new Pair<>(true, Long.valueOf(pendingInstructionsQty));
    }
    return null;
  }

  /**
   * Method prepares an overage alert instruction response
   *
   * @param instructionRequest
   * @param httpHeaders
   * @return instruction
   */
  public Instruction getOverageAlertInstruction(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
    throwExceptionForAsnReceiving(
        instructionRequest); // Fixit create ticket not supported with SSCC, throwing exception so
    // that client can capture UPC
    Instruction instruction = new Instruction();
    instruction.setDeliveryNumber(Long.valueOf(instructionRequest.getDeliveryNumber()));
    instruction.setInstructionCode(RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionCode());
    instruction.setInstructionMsg(RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionMsg());
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setGtin(instructionRequest.getUpcNumber());
    instruction.setSsccNumber(instructionRequest.getSscc());

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    LinkedTreeMap<String, Object> moveTreeMap =
        moveDetailsForInstruction(
            instructionRequest.getDoorNumber(), deliveryDocument, httpHeaders);
    instruction.setMove(moveTreeMap);

    return instruction;
  }

  private void throwExceptionForAsnReceiving(InstructionRequest instructionRequest) {
    if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.ASN_PO_NO_OPEN_QTY, ReceivingException.ASN_PO_NO_OPEN_QTY);
    }
  }

  /**
   * This method validates TixHi values between RDS and GDM, if it's different then updating the
   * delivery document lines TixHi with RDS TixHi
   *
   * @param deliveryDocumentLine
   * @return deliveryDocumentLine
   */
  public DeliveryDocumentLine validateTiHiFromRdsAndGdm(DeliveryDocumentLine deliveryDocumentLine) {
    ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
    final Integer rdsPalletTi = itemData.getPalletTi();
    final Integer rdsPalletHi = itemData.getPalletHi();
    final Integer gdmPalletTi = deliveryDocumentLine.getPalletTie();
    final Integer gdmPalletHi = deliveryDocumentLine.getPalletHigh();
    if (ObjectUtils.allNotNull(rdsPalletTi, rdsPalletHi)
        && !isSamePalletTiHi(rdsPalletTi, rdsPalletHi, gdmPalletTi, gdmPalletHi)) {
      LOGGER.info(
          "Pallet TixHi values are different between RDS and GDM, updating RDS TixHi: {}x{} value in delivery document line object",
          rdsPalletTi,
          rdsPalletHi);
      deliveryDocumentLine.setPalletTie(rdsPalletTi);
      deliveryDocumentLine.setPalletHigh(rdsPalletHi);
    }
    LOGGER.info("Populating GDM TixHi value under additionInfo object in delivery document line");
    itemData.setGdmPalletTi(gdmPalletTi);
    itemData.setGdmPalletHi(gdmPalletHi);
    deliveryDocumentLine.setAdditionalInfo(itemData);
    return deliveryDocumentLine;
  }

  /**
   * Compares GDM and RDS TixHi value and returns TRUE if both are same, otherwise FALSE will be
   * returned.
   *
   * @param rdsPalletTi
   * @param rdsPalletHi
   * @param gdmPalletTi
   * @param gdmPalletHi
   * @return boolean
   */
  private boolean isSamePalletTiHi(
      Integer rdsPalletTi, Integer rdsPalletHi, Integer gdmPalletTi, Integer gdmPalletHi) {
    return Objects.equals(rdsPalletTi, gdmPalletTi) && Objects.equals(rdsPalletHi, gdmPalletHi);
  }

  public void validatePoLineIsCancelledOrClosedOrRejected(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    boolean invalidPoLine = false;
    GdmError gdmError = null;
    String errorMessage = null;
    String purchaseRefLineStatus = deliveryDocumentLine.getPurchaseReferenceLineStatus();
    if (Objects.nonNull(purchaseRefLineStatus)) {
      if (purchaseRefLineStatus.equalsIgnoreCase(POLineStatus.CANCELLED.name())) {
        LOGGER.error(
            "PO: {} and POL: {} is in CANCELLED status",
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
        invalidPoLine = true;
        gdmError = GdmErrorCode.getErrorValue(ReceivingException.PO_POL_CANCELLED_ERROR);
        errorMessage =
            String.format(
                gdmError.getErrorMessage(),
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber());
      } else if (purchaseRefLineStatus.equalsIgnoreCase(POLineStatus.CLOSED.name())) {
        LOGGER.error(
            "PO: {} and POL: {} is in CLOSED status",
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
        invalidPoLine = true;
        gdmError = GdmErrorCode.getErrorValue(ReceivingException.PO_LINE_CLOSED_ERROR);
        errorMessage =
            String.format(
                gdmError.getErrorMessage(), deliveryDocumentLine.getPurchaseReferenceLineNumber());
      } else {
        OperationalInfo operationalInfo = deliveryDocumentLine.getOperationalInfo();
        if (!Objects.isNull(operationalInfo)
            && !StringUtils.isBlank(operationalInfo.getState())
            && operationalInfo.getState().equalsIgnoreCase(POLineStatus.REJECTED.name())) {
          LOGGER.error(
              "PO: {} and POL:{} is in REJECTED status",
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
          invalidPoLine = true;
          gdmError = GdmErrorCode.getErrorValue(ReceivingException.PO_LINE_REJECTION_ERROR);
          errorMessage =
              String.format(
                  gdmError.getErrorMessage(),
                  deliveryDocumentLine.getPurchaseReferenceLineNumber());
        }
      }
    }
    if (invalidPoLine) {
      throw new ReceivingException(
          errorMessage,
          HttpStatus.INTERNAL_SERVER_ERROR,
          gdmError.getErrorCode(),
          gdmError.getErrorHeader());
    }
  }

  /**
   * Prepare instruction summary message payload to publish to WFT
   *
   * @param instruction
   * @param updateInstructionRequest
   * @param quantity
   * @param httpHeaders
   * @return PublishInstructionSummary
   */
  public PublishInstructionSummary prepareInstructionMessage(
      Instruction instruction,
      UpdateInstructionRequest updateInstructionRequest,
      Integer quantity,
      HttpHeaders httpHeaders) {

    PublishInstructionSummary publishInstructionSummary = new PublishInstructionSummary();
    PublishInstructionSummary.UserInfo userInfo =
        new PublishInstructionSummary.UserInfo(
            httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY),
            httpHeaders.getFirst(ReceivingConstants.SECURITY_HEADER_KEY));
    publishInstructionSummary.setUserInfo(userInfo);
    publishInstructionSummary.setMessageId(instruction.getMessageId());
    publishInstructionSummary.setInstructionCode(instruction.getInstructionCode());
    publishInstructionSummary.setInstructionMsg(instruction.getInstructionMsg());
    publishInstructionSummary.setActivityName(instruction.getActivityName());
    publishInstructionSummary.setPrintChildContainerLabels(
        instruction.getPrintChildContainerLabels());
    publishInstructionSummary.setInstructionExecutionTS(new Date());
    publishInstructionSummary.setInstructionStatus(
        InstructionStatus.UPDATED.getInstructionStatus());
    publishInstructionSummary.setUpdatedQty(quantity);
    publishInstructionSummary.setUpdatedQtyUOM(ReceivingConstants.Uom.VNPK);
    publishInstructionSummary.setVnpkQty(
        updateInstructionRequest.getDeliveryDocumentLines().get(0).getVnpkQty());
    publishInstructionSummary.setWhpkQty(
        updateInstructionRequest.getDeliveryDocumentLines().get(0).getWhpkQty());

    publishInstructionSummary.setLocation(
        new PublishInstructionSummary.Location(
            httpHeaders.getFirst(RdcConstants.WFT_LOCATION_ID),
            httpHeaders.getFirst(RdcConstants.WFT_LOCATION_TYPE),
            httpHeaders.getFirst(RdcConstants.WFT_SCC_CODE)));

    return publishInstructionSummary;
  }

  public PublishInstructionSummary prepareInstructionMessage(
      Instruction instruction,
      Integer quantity,
      HttpHeaders httpHeaders,
      int vnpkQty,
      int whpkQty) {

    PublishInstructionSummary publishInstructionSummary = new PublishInstructionSummary();
    PublishInstructionSummary.UserInfo userInfo =
        new PublishInstructionSummary.UserInfo(
            httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY),
            httpHeaders.getFirst(ReceivingConstants.SECURITY_HEADER_KEY));
    publishInstructionSummary.setUserInfo(userInfo);
    publishInstructionSummary.setMessageId(instruction.getMessageId());
    publishInstructionSummary.setInstructionCode(instruction.getInstructionCode());
    publishInstructionSummary.setInstructionMsg(instruction.getInstructionMsg());
    publishInstructionSummary.setActivityName(instruction.getActivityName());
    publishInstructionSummary.setPrintChildContainerLabels(
        instruction.getPrintChildContainerLabels());
    publishInstructionSummary.setInstructionExecutionTS(new Date());
    publishInstructionSummary.setInstructionStatus(
        InstructionStatus.COMPLETED.getInstructionStatus());
    publishInstructionSummary.setUpdatedQty(quantity);
    publishInstructionSummary.setUpdatedQtyUOM(ReceivingConstants.Uom.VNPK);
    publishInstructionSummary.setVnpkQty(vnpkQty);
    publishInstructionSummary.setWhpkQty(whpkQty);

    publishInstructionSummary.setLocation(
        new PublishInstructionSummary.Location(
            httpHeaders.getFirst(RdcConstants.WFT_LOCATION_ID),
            httpHeaders.getFirst(RdcConstants.WFT_LOCATION_TYPE),
            httpHeaders.getFirst(RdcConstants.WFT_SCC_CODE)));

    return publishInstructionSummary;
  }

  public PublishInstructionSummary prepareInstructionMessage(
      ContainerItem containerItem, LabelAction action, Integer quantity, HttpHeaders httpHeaders) {

    PublishInstructionSummary publishInstructionSummary = new PublishInstructionSummary();
    PublishInstructionSummary.UserInfo userInfo =
        new PublishInstructionSummary.UserInfo(
            httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY),
            httpHeaders.getFirst(ReceivingConstants.SECURITY_HEADER_KEY));
    publishInstructionSummary.setUserInfo(userInfo);

    publishInstructionSummary.setMessageId(UUID.randomUUID().toString());
    publishInstructionSummary.setInstructionCode(
        WFTInstruction.valueOf(action.getAction()).getCode());
    publishInstructionSummary.setInstructionMsg(
        WFTInstruction.valueOf(action.getAction()).getMessage());
    publishInstructionSummary.setActivityName(
        WFTInstruction.valueOf(action.getAction()).getActivityName());
    publishInstructionSummary.setPrintChildContainerLabels(Boolean.FALSE);
    publishInstructionSummary.setInstructionExecutionTS(new Date());
    publishInstructionSummary.setInstructionStatus(
        InstructionStatus.UPDATED.getInstructionStatus());
    publishInstructionSummary.setUpdatedQty(quantity);
    publishInstructionSummary.setUpdatedQtyUOM(ReceivingConstants.Uom.VNPK);
    publishInstructionSummary.setVnpkQty(containerItem.getVnpkQty());
    publishInstructionSummary.setWhpkQty(containerItem.getWhpkQty());

    publishInstructionSummary.setLocation(
        new PublishInstructionSummary.Location(
            httpHeaders.getFirst(RdcConstants.WFT_LOCATION_ID),
            httpHeaders.getFirst(RdcConstants.WFT_LOCATION_TYPE),
            httpHeaders.getFirst(RdcConstants.WFT_SCC_CODE)));

    return publishInstructionSummary;
  }

  private boolean isSplitPalletInstruction(Instruction instruction) {
    return Objects.nonNull(instruction.getInstructionSetId())
        && instruction.getInstructionSetId() > 0;
  }

  public Boolean isCancelInstructionAllowed(Instruction instruction, String userId)
      throws ReceivingException {
    if (Objects.nonNull(instruction.getCompleteTs())) {
      LOGGER.error("Instruction: {} is already complete", instruction.getId());
      throw new ReceivingBadDataException(
          ExceptionCodes.INSTRUCITON_IS_ALREADY_COMPLETED,
          ReceivingException.INSTRUCTION_IS_ALREADY_COMPLETED);
    } else if (!isSplitPalletInstruction(instruction) && instruction.getReceivedQuantity() > 0) {
      LOGGER.error("Instruction: {} is partially received", instruction.getId());
      throw new ReceivingBadDataException(
          ExceptionCodes.INSTRUCTION_PARTIALLY_RECEIVED,
          ReceivingException.PARTIAL_INSTRUCTION_CANCEL_ERROR);
    } else {
      ReceivingUtils.verifyUser(instruction, userId, RequestType.CANCEL);
    }
    return true;
  }

  public Boolean hasMoreUniqueItems(List<DeliveryDocument> deliveryDocuments) {
    return deliveryDocuments
            .stream()
            .flatMap(deliveryDocument -> deliveryDocument.getDeliveryDocumentLines().stream())
            .map(DeliveryDocumentLine::getItemNbr)
            .distinct()
            .count()
        > 1;
  }

  List<DeliveryDocument> sortDeliveryDocumentByMABD(List<DeliveryDocument> deliveryDocuments) {
    if (!CollectionUtils.isEmpty(deliveryDocuments)) {
      deliveryDocuments
          .stream()
          .sorted(
              Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate)
                  .thenComparing(DeliveryDocument::getPurchaseReferenceNumber))
          .collect(Collectors.toList());
    }
    return deliveryDocuments;
  }

  DeliveryDocument sortDeliveryDocumentLinesByLine(DeliveryDocument deliveryDocument) {
    List<DeliveryDocumentLine> sortedDeliveryDocumentLines;
    if (Objects.nonNull(deliveryDocument)
        && !CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())
        && deliveryDocument.getDeliveryDocumentLines().size() > 1) {
      sortedDeliveryDocumentLines =
          deliveryDocument
              .getDeliveryDocumentLines()
              .stream()
              .sorted(Comparator.comparing(DeliveryDocumentLine::getPurchaseReferenceLineNumber))
              .collect(Collectors.toList());
      deliveryDocument.getDeliveryDocumentLines().clear();
      deliveryDocument.setDeliveryDocumentLines(sortedDeliveryDocumentLines);
    }
    return deliveryDocument;
  }

  DeliveryDocument getDeliveryDocumentForOverageReporting(
      List<DeliveryDocument> deliveryDocumentList) {

    List<DeliveryDocument> filteredSSTKDocuments =
        filterSSTKDeliveryDocuments(deliveryDocumentList);

    List<DeliveryDocument> sortedDeliveryDocument =
        deliveryDocumentList.size() > 1
            ? sortDeliveryDocumentByMABD(
                CollectionUtils.isEmpty(filteredSSTKDocuments)
                    ? deliveryDocumentList
                    : filteredSSTKDocuments)
            : deliveryDocumentList;

    DeliveryDocument deliveryDocumentResponse = sortedDeliveryDocument.get(0);

    List<DeliveryDocumentLine> deliveryDocumentLineList =
        sortedDeliveryDocument.get(0).getDeliveryDocumentLines();
    if (deliveryDocumentLineList.size() == 1) {
      deliveryDocumentResponse.setDeliveryDocumentLines(deliveryDocumentLineList);
    } else {
      DeliveryDocument sortedDeliveryDocumentByLineNumber =
          sortDeliveryDocumentLinesByLine(sortedDeliveryDocument.get(0));
      deliveryDocumentResponse.setDeliveryDocumentLines(
          Collections.singletonList(
              sortedDeliveryDocumentByLineNumber.getDeliveryDocumentLines().get(0)));
    }
    return deliveryDocumentResponse;
  }

  private Integer getReceivedQtyByPoAndPoline(
      DeliveryDocument deliveryDocument,
      ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    String key =
        deliveryDocumentLine.getPurchaseReferenceNumber()
            + ReceivingConstants.DELIM_DASH
            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
    return receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key).intValue();
  }

  public List<DeliveryDocument> filterInvalidPoLinesFromDocuments(
      InstructionRequest instructionRequest, List<DeliveryDocument> deliveryDocuments) {
    Iterator<DeliveryDocument> deliveryDocumentsIterator = deliveryDocuments.iterator();
    while (deliveryDocumentsIterator.hasNext()) {
      DeliveryDocument deliveryDocument = deliveryDocumentsIterator.next();
      Iterator<DeliveryDocumentLine> deliveryDocumentLineIterator =
          deliveryDocument.getDeliveryDocumentLines().iterator();
      while (deliveryDocumentLineIterator.hasNext()) {
        DeliveryDocumentLine deliveryDocumentLine = deliveryDocumentLineIterator.next();
        if (POLineStatus.CANCELLED
                .name()
                .equalsIgnoreCase(deliveryDocumentLine.getPurchaseReferenceLineStatus())
            || POLineStatus.CLOSED
                .name()
                .equalsIgnoreCase(deliveryDocumentLine.getPurchaseReferenceLineStatus())) {
          deliveryDocumentLineIterator.remove();
        }
      }
      if (CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())) {
        deliveryDocumentsIterator.remove();
      }
    }

    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      LOGGER.error(
          "There is no ACTIVE po lines to receive for the scanned UPC:{} and delivery:{}",
          instructionRequest.getUpcNumber(),
          instructionRequest.getDeliveryNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.RDC_INVALID_PO_PO_LINE_STATUS,
          String.format(
              ReceivingException.NO_ACTIVE_PO_LINES_TO_RECEIVE,
              instructionRequest.getDeliveryNumber(),
              instructionRequest.getUpcNumber()),
          instructionRequest.getUpcNumber(),
          instructionRequest.getDeliveryNumber());
    }
    return deliveryDocuments;
  }

  public boolean isSplitPalletInstruction(InstructionRequest instructionRequest) {
    Optional<RdcReceivingType> receivingTypeOptional =
        RdcReceivingType.fromString(instructionRequest.getReceivingType());
    return (receivingTypeOptional.isPresent() && receivingTypeOptional.get().isSplitPalletGroup());
  }

  private void populateInstructionSetId(
      InstructionRequest instructionRequest, Instruction instruction) {
    if (rdcManagedConfig.isSplitPalletEnabled() && isSplitPalletInstruction(instructionRequest)) {
      if (Objects.isNull(instructionRequest.getInstructionSetId())) {
        instruction.setInstructionSetId(instructionSetIdGenerator.generateInstructionSetId());
      } else {
        instruction.setInstructionSetId(instructionRequest.getInstructionSetId());
      }
    }
  }

  /**
   * This method in invoked only Multi Po or Multi PoLines delivery document & validates all SSTK
   * POs on the delivery documents are fulfilled or not. 1. Get Received Qty from RDS/Receipts for
   * given Po/PoLines 2. Filter Active Po/PoLines by comparing GDM delivery documents with RDS
   * Received Qty map. 3.Validates currentReceivedQtyInVnpk against maxReceiveQty on the Po/PoLines
   * to determine all SSTK POs are fulfilled or not.
   *
   * @param deliveryDocuments
   * @param receivedQuantityResponseFromRDS
   * @return Pair<Boolean, List < DeliveryDocument>>
   */
  public Pair<Boolean, List<DeliveryDocument>> isAllSSTKPoFulfilled(
      List<DeliveryDocument> deliveryDocuments,
      ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS) {
    Boolean isSSTKPOFulfilled = true;

    Map<String, Long> receivedQtyMapFromRds =
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine();

    List<DeliveryDocument> activeDeliveryDocuments =
        deliveryDocuments
            .stream()
            .filter(
                deliveryDocument ->
                    filterActivePoLinesFromRDSResponse(deliveryDocument, receivedQtyMapFromRds))
            .collect(Collectors.toList());

    for (DeliveryDocument deliveryDocument : activeDeliveryDocuments) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        String key =
            deliveryDocumentLine.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        Integer maxReceiveQty =
            deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();

        if (Objects.nonNull(receivedQtyMapFromRds.get(key))) {
          int currentReceivedQtyInVnpk = receivedQtyMapFromRds.get(key).intValue();
          isSSTKPOFulfilled &= currentReceivedQtyInVnpk >= maxReceiveQty;
        }
      }
    }

    return new Pair<>(isSSTKPOFulfilled, activeDeliveryDocuments);
  }

  public void validateOverage(
      List<DeliveryDocumentLine> deliveryDocumentLines,
      int quantityToBeReceived,
      Instruction instruction,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    Integer currentReceiveQuantity = null;
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocumentLines.get(0);
    checkIfContainerAlreadyReceived(
        instruction.getSsccNumber(),
        deliveryDocumentLine.getItemNbr(),
        quantityToBeReceived,
        instruction.getProjectedReceiveQty());
    if (deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()) {
      currentReceiveQuantity =
          receiptService
              .getReceivedQtyByPoAndPoLine(
                  deliveryDocumentLine.getPurchaseReferenceNumber(),
                  deliveryDocumentLine.getPurchaseReferenceLineNumber())
              .intValue();
    } else {
      TenantContext.get().setReceiveInstrGetReceiptsNimRdsCallStart(System.currentTimeMillis());
      Pair<DeliveryDocumentLine, Long> receivedQuantityFromRDS =
          getReceiptsFromRDSByDocumentLine(deliveryDocumentLines, httpHeaders);
      currentReceiveQuantity =
          ReceivingUtils.conversionToVendorPack(
              receivedQuantityFromRDS.getValue().intValue(),
              ReceivingConstants.Uom.WHPK,
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack());
      TenantContext.get().setReceiveInstrGetReceiptsNimRdsCallEnd(System.currentTimeMillis());
    }
    Integer maxReceiveQuantity = deliveryDocumentLine.getMaxReceiveQty();
    Integer totalQuantityValueAfterReceiving = currentReceiveQuantity + quantityToBeReceived;

    if (Objects.equals(currentReceiveQuantity, maxReceiveQuantity)) {
      if (Objects.isNull(instruction.getProblemTagId())) {
        LOGGER.error(
            "Reached maximum receivable quantity threshold for this PO: {}, POL: {} and problemTagId: {}",
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            instruction.getProblemTagId());
        throw new ReceivingException(
            String.format(
                RdcConstants.RDC_OVERAGE_EXCEED_ERROR_MESSAGE,
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber()),
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.OVERAGE_ERROR_CODE);

      } else {
        rdcProblemUtils.reportErrorForProblemReceiving(
            instruction.getProblemTagId(),
            null,
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            httpHeaders);
      }
    }

    if (totalQuantityValueAfterReceiving > maxReceiveQuantity) {
      if (Objects.isNull(instruction.getProblemTagId())) {
        LOGGER.error(
            String.format(
                ReceivingException.RECEIVE_INSTRUCTION_EXCEEDS_OVERAGE_LIMIT,
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber()));
        throw new ReceivingException(
            String.format(
                ReceivingException.RECEIVE_INSTRUCTION_EXCEEDS_OVERAGE_LIMIT,
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber()),
            HttpStatus.INTERNAL_SERVER_ERROR,
            ReceivingException.TOTAL_RECEIVE_QTY_EXCEEDS_OVERAGE_LIMIT);
      } else {
        rdcProblemUtils.reportErrorForProblemReceiving(
            instruction.getProblemTagId(),
            null,
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            httpHeaders);
      }
    }
  }

  private void validateRequestIsForBreakBackItem(DeliveryDocumentLine deliveryDocumentLine) {
    LOGGER.info(
        "Entering into validateRequestIsForBreakBackItem with item number:{}",
        deliveryDocumentLine.getItemNbr());
    if (!isBreakPackItem(deliveryDocumentLine)) {
      LOGGER.error(
          "Item: {} is not a break pack item, so it cannot be added into split pallet",
          deliveryDocumentLine.getItemNbr());
      throw new ReceivingBadDataException(
          ExceptionCodes.INSTR_CREATE_SPLIT_PALLET_NOT_BREAK_PACK_ITEM,
          ReceivingConstants.INSTR_CREATE_SPLIT_PALLET_NOT_BREAK_PACK_ITEM);
    }
  }

  private boolean isBreakPackItem(DeliveryDocumentLine deliveryDocumentLine) {
    return RdcConstants.BREAK_PACK_TYPE_CODE.equals(
        deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
  }

  public void validatePrimeSlotCompatibility(
      InstructionRequest instructionRequest, DeliveryDocumentLine deliveryDocumentLine) {
    ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
    if (Objects.nonNull(instructionRequest.getInstructionSetId())) {
      String primeSlot = additionalInfo.getPrimeSlot();
      List<String> instructionSlots =
          instructionPersisterService.getSlotDetailsByDeliveryNumberAndInstructionSetId(
              instructionRequest.getDeliveryNumber(), instructionRequest.getInstructionSetId());

      // Prefix of Prime slots of other items on this Instruction Set should match.
      if (!CollectionUtils.isEmpty(instructionSlots)
          && !StringUtils.equals(
              primeSlot.subSequence(0, 2), instructionSlots.get(0).subSequence(0, 2))) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INSTR_CREATE_SPLIT_PALLET_PRIMES_COMPATIBLE,
            ReceivingConstants.INSTR_CREATE_SPLIT_PALLET_PRIMES_COMPATIBLE,
            primeSlot,
            String.valueOf(deliveryDocumentLine.getItemNbr()),
            StringUtils.join(instructionSlots, ", "));
      }
    }
  }

  public void populateOpenAndReceivedQtyInDeliveryDocuments(
      List<DeliveryDocument> deliveryDocumentList, HttpHeaders httpHeaders, String upcNumber) {
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(
            deliveryDocumentList, httpHeaders, upcNumber);

    Map<String, Long> receivedQuantityMapFromRds =
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine();

    deliveryDocumentList.forEach(
        document ->
            document
                .getDeliveryDocumentLines()
                .forEach(
                    line -> {
                      String key =
                          line.getPurchaseReferenceNumber()
                              + ReceivingConstants.DELIM_DASH
                              + line.getPurchaseReferenceLineNumber();
                      int currentReceivedQtyInVnpk =
                          !CollectionUtils.isEmpty(receivedQuantityMapFromRds)
                                  && Objects.nonNull(receivedQuantityMapFromRds.get(key))
                              ? receivedQuantityMapFromRds.get(key).intValue()
                              : ReceivingConstants.ZERO_QTY;
                      line.setOpenQty(line.getTotalOrderQty() - currentReceivedQtyInVnpk);
                      line.setTotalReceivedQty(currentReceivedQtyInVnpk);
                    }));
  }

  public List<DeliveryDocument> checkAllSSTKPoFulfilled(
      List<DeliveryDocument> filteredSSTKDocuments,
      InstructionRequest instructionRequest,
      ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS) {
    Pair<Boolean, List<DeliveryDocument>> activeSSTKPoLinesPair =
        isAllSSTKPoFulfilled(filteredSSTKDocuments, receivedQuantityResponseFromRDS);

    if (activeSSTKPoLinesPair.getKey()) {
      List<DeliveryDocument> filteredDADocuments =
          instructionRequest
              .getDeliveryDocuments()
              .stream()
              .filter(doc -> isDADocument(doc))
              .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(filteredDADocuments)) {
        LOGGER.error(
            "All SSTK PO's are fulfilled for the scanned UPC:{} and delivery:{}, please try receiving DA POs in NGR app",
            instructionRequest.getUpcNumber(),
            instructionRequest.getDeliveryNumber());
        throw new ReceivingBadDataException(
            ExceptionCodes.DA_PURCHASE_REF_TYPE, RdcConstants.DA_PURCHASE_REF_TYPE_MSG);
      }
    }
    return activeSSTKPoLinesPair.getValue();
  }

  /**
   * This method validates atlas converted items for DA. Based on CCM config item config will be
   * used or CCM driven pack & handling codes will be used to consider if given items are atlas
   * converted or not.
   *
   * @param deliveryDocuments
   * @param httpHeaders
   */
  public void checkAtlasConvertedItemForDa(
      List<DeliveryDocument> deliveryDocuments, HttpHeaders httpHeaders) throws ReceivingException {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
        false)) {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          RdcConstants.IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS,
          false)) {
        validateAtlasConvertedItems(deliveryDocuments, httpHeaders);
      } else {
        /* validate item config for certain handling codes (BC, CN)until we ramp all other type of
        handling codes */
        if (isValidPackHandlingCodeForItemConfigApi(deliveryDocuments)) {
          validateAtlasConvertedItems(deliveryDocuments, httpHeaders);
        } else {
          deliveryDocuments.forEach(
              deliveryDocument ->
                  deliveryDocument
                      .getDeliveryDocumentLines()
                      .forEach(this::validateAtlasConvertedItemByCcmConfigs));
        }
      }
    }
  }

  /**
   * @param deliveryDocument
   * @return
   */
  private boolean isItemConfigEligibleDeliveryDocument(DeliveryDocument deliveryDocument) {
    List<DeliveryDocumentLine> deliveryDocumentLines =
        deliveryDocument
            .getDeliveryDocumentLines()
            .stream()
            .filter(
                deliveryDocumentLine ->
                    deliveryDocumentLine.getAdditionalInfo().isItemConfigEligibleItem())
            .collect(Collectors.toList());
    return !CollectionUtils.isEmpty(deliveryDocumentLines);
  }

  /**
   * @param deliveryDocumentLine
   * @return
   */
  private void isValidPackHandlingCodeForItemConfig(DeliveryDocumentLine deliveryDocumentLine) {
    if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
        deliveryDocumentLine.getPurchaseRefType())) {
      if (!CollectionUtils.isEmpty(
              rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCodeWithItemConfig())
          && rdcManagedConfig
              .getDaAtlasItemEnabledPackHandlingCodeWithItemConfig()
              .contains(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode())) {
        deliveryDocumentLine.getAdditionalInfo().setItemConfigEligibleItem(true);
      }
    }
  }

  /**
   * @param deliveryDocuments
   * @return
   */
  public boolean isValidPackHandlingCodeForItemConfigApi(List<DeliveryDocument> deliveryDocuments) {
    deliveryDocuments.forEach(
        deliveryDocument ->
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(this::isValidPackHandlingCodeForItemConfig));
    deliveryDocuments =
        deliveryDocuments
            .stream()
            .filter(this::isItemConfigEligibleDeliveryDocument)
            .collect(Collectors.toList());
    return !CollectionUtils.isEmpty(deliveryDocuments);
  }

  private void validateAtlasConvertedItemByCcmConfigs(DeliveryDocumentLine deliveryDocumentLine) {
    if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
        deliveryDocumentLine.getPurchaseRefType())) {
      if (rdcManagedConfig
          .getDaAtlasItemEnabledPackHandlingCode()
          .contains(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode())) {
        deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
      }
    }
  }

  public void validateAtlasConvertedItems(
      List<DeliveryDocument> deliveryDocuments, HttpHeaders httpHeaders) throws ReceivingException {
    if (isAtlasConversionEnabledForAllSstkItems(deliveryDocuments)) {
      return;
    }
    Set<Long> itemConfigRequest =
        deliveryDocuments
            .stream()
            .flatMap(doc -> doc.getDeliveryDocumentLines().stream())
            .map(DeliveryDocumentLine::getItemNbr)
            .collect(Collectors.toSet());

    try {
      TenantContext.get().setFetchItemConfigServiceCallStart(System.currentTimeMillis());
      Set<String> atlasConvertedItems =
          itemConfigApiClient
              .searchAtlasConvertedItems(itemConfigRequest, httpHeaders)
              .parallelStream()
              .map(ItemConfigDetails::getItem)
              .collect(Collectors.toSet());
      TenantContext.get().setFetchItemConfigServiceCallEnd(System.currentTimeMillis());
      List<String> freightSpecificType =
          tenantSpecificConfigReader.getFreightSpecificType(getFacilityNum());
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(), ATLAS_COMPLETE_MIGRATED_DC_LIST, false)) {

        Set<Long> nonAtlasConvertedItems =
            deliveryDocuments
                .stream()
                .filter(
                    deliveryDocument ->
                        (isSSTKDocument(deliveryDocument)
                                && freightSpecificType.contains(PURCHASE_REF_TYPE_SSTK))
                            || (isDADocument(deliveryDocument)
                                && freightSpecificType.contains(PURCHASE_REF_TYPE_DA)))
                .flatMap(deliveryDocument -> deliveryDocument.getDeliveryDocumentLines().stream())
                .map(DeliveryDocumentLine::getItemNbr)
                .filter(itemNumber -> !atlasConvertedItems.contains(String.valueOf(itemNumber)))
                .collect(Collectors.toSet());
        if (!CollectionUtils.isEmpty(nonAtlasConvertedItems)) {
          itemConfigApiClient.addAsAtlasItems(nonAtlasConvertedItems, httpHeaders);
          Set<String> nonAtlasConvertedToString =
              nonAtlasConvertedItems.stream().map(String::valueOf).collect(Collectors.toSet());
          atlasConvertedItems.addAll(nonAtlasConvertedToString);
        }
      }
      deliveryDocuments.forEach(
          deliveryDocument -> {
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(
                    deliveryDocumentLine -> {
                      String itemNumber = String.valueOf(deliveryDocumentLine.getItemNbr());
                      ItemData itemData = deliveryDocumentLine.getAdditionalInfo();
                      if (Objects.isNull(itemData)) {
                        itemData = new ItemData();
                      }
                      if (!CollectionUtils.isEmpty(atlasConvertedItems)
                          && atlasConvertedItems.contains(itemNumber)) {
                        if (ReceivingConstants.SSTK_CHANNEL_METHODS_FOR_RDC.contains(
                            deliveryDocumentLine.getPurchaseRefType())) {
                          LOGGER.info("Item {} is Atlas converted SSTK item", itemNumber);
                          itemData.setAtlasConvertedItem(true);
                        } else if (isDeliveryEligibleForAtlasDaReceiving(
                            deliveryDocument.getDeliveryNumber(), deliveryDocumentLine)) {
                          LOGGER.info("Item {} is Atlas converted DA item", itemNumber);
                          itemData.setAtlasConvertedItem(true);
                        }
                      } else {
                        /* if the item is not set up in item config for DA, use pack handling code combination
                        to determine atlas item eligibility to set atlasConvertedItem as true */
                        if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
                                deliveryDocumentLine.getPurchaseRefType())
                            && rdcManagedConfig
                                .getDaAtlasItemEnabledPackHandlingCode()
                                .contains(
                                    deliveryDocumentLine
                                        .getAdditionalInfo()
                                        .getItemPackAndHandlingCode())) {
                          itemData.setAtlasConvertedItem(true);
                        }
                      }
                      deliveryDocumentLine.setAdditionalInfo(itemData);
                    });
          });
    } catch (ItemConfigRestApiClientException e) {
      LOGGER.error(
          "Error when searching atlas converted items errorCode = {} and error message = {} ",
          e.getHttpStatus(),
          ExceptionUtils.getMessage(e));
      throw new ReceivingException(
          e.getErrorResponse().getErrorMessage(),
          e.getHttpStatus(),
          e.getErrorResponse().getErrorCode());
    }
  }

  /**
   * Convert all items as Atlas items when the flag enabled
   *
   * @param deliveryDocuments
   * @return
   */
  private boolean isAtlasConversionEnabledForAllSstkItems(
      List<DeliveryDocument> deliveryDocuments) {
    boolean isAtlasConversionEnabledForAllSstkItems =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            IS_ATLAS_CONVERSION_ENABLED_FOR_ALL_SSTK_ITEMS,
            false);
    if (isAtlasConversionEnabledForAllSstkItems) {
      deliveryDocuments.forEach(
          deliveryDocument -> {
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(
                    deliveryDocumentLine -> {
                      if (Objects.nonNull(deliveryDocumentLine.getAdditionalInfo()))
                        deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(true);
                    });
          });
    }
    return isAtlasConversionEnabledForAllSstkItems;
  }

  /**
   * This method returns true if item is Atlas converted DA item based on 1. if delivery is a Pilot
   * delivery 2. if delivery is not in progress status
   *
   * @param deliveryNumber
   * @param deliveryDocumentLine
   * @return boolean
   */
  public boolean isDeliveryEligibleForAtlasDaReceiving(
      Long deliveryNumber, DeliveryDocumentLine deliveryDocumentLine) {
    boolean isAtlasDaDelivery = false;
    boolean daPilotDeliveryConfigEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_PILOT_DELIVERY_ENABLED,
            false);
    if (isAtlasConvertedDaItem(deliveryDocumentLine)) {
      if (daPilotDeliveryConfigEnabled) {
        if (isAtlasDaPilotDelivery(deliveryNumber)) {
          isAtlasDaDelivery = true;
        }
      } else if (!isLegacyInProgressDelivery(deliveryNumber)) {
        isAtlasDaDelivery = true;
      }
    }
    return isAtlasDaDelivery;
  }

  public boolean isAtlasDaPilotDelivery(Long deliveryNumber) {
    return !rdcManagedConfig.getAtlasDaPilotDeliveries().isEmpty()
        && rdcManagedConfig.getAtlasDaPilotDeliveries().contains(String.valueOf(deliveryNumber));
  }

  public boolean isLegacyInProgressDelivery(Long deliveryNumber) {
    boolean enableCutOffDaDeliveriesInAtlas =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CUTOFF_ENABLED_FOR_ATLAS_DA_DELIVERIES,
            false);
    if (enableCutOffDaDeliveriesInAtlas) {
      return !rdcManagedConfig.getInProgressCutOffDeliveriesList().isEmpty()
          && rdcManagedConfig
              .getInProgressCutOffDeliveriesList()
              .contains(deliveryNumber.toString());
    }
    return false;
  }

  /**
   * This method determines if the item is DA or not based on the following. 1. if the item is DA or
   * not based on the purchaseRefType 2. if Atlas DA item conversion is enabled or not 3. The DA
   * item packHandlingCode pair is within the valid list of handlingCodes
   *
   * @param deliveryDocumentLine
   * @return
   */
  private boolean isAtlasConvertedDaItem(DeliveryDocumentLine deliveryDocumentLine) {
    return ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
                deliveryDocumentLine.getPurchaseRefType())
            && tenantSpecificConfigReader.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
                false)
            && rdcManagedConfig
                .getDaAtlasItemEnabledPackHandlingCode()
                .contains(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode())
        || (!CollectionUtils.isEmpty(
                rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCodeWithItemConfig())
            && rdcManagedConfig
                .getDaAtlasItemEnabledPackHandlingCodeWithItemConfig()
                .contains(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode()));
  }

  public void updateAdditionalItemDetailsFromGDM(List<DeliveryDocument> deliveryDocumentList) {
    deliveryDocumentList.forEach(
        document -> {
          document
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    ItemData additionalItemInfo = deliveryDocumentLine.getAdditionalInfo();
                    // Set Pallet Ti-Hi to 100 if they are coming as zero/null from GDM
                    Integer palletTi =
                        (Objects.isNull(deliveryDocumentLine.getPalletTie())
                                || deliveryDocumentLine.getPalletTie() == 0)
                            ? 100
                            : deliveryDocumentLine.getPalletTie();
                    Integer palletHi =
                        (Objects.isNull(deliveryDocumentLine.getPalletHigh())
                                || deliveryDocumentLine.getPalletHigh() == 0)
                            ? 100
                            : deliveryDocumentLine.getPalletHigh();
                    if (Objects.isNull(additionalItemInfo)) {
                      additionalItemInfo = new ItemData();
                    }
                    additionalItemInfo.setPalletTi(palletTi);
                    additionalItemInfo.setPalletHi(palletHi);

                    // default ti x hi pop up message needed only for SSTK
                    if (isSSTKDocument(document)) {
                      if (palletTi == 100 && palletHi == 100) {
                        additionalItemInfo.setIsDefaultTiHiUsed(true);
                      }
                    }
                    additionalItemInfo.setGdmPalletTi(deliveryDocumentLine.getPalletTie());
                    additionalItemInfo.setGdmPalletHi(deliveryDocumentLine.getPalletHigh());
                    deliveryDocumentLine.setIsHazmat(isHazmatItem(deliveryDocumentLine));
                    if (Objects.isNull(additionalItemInfo.getPackTypeCode())
                        || Objects.isNull(additionalItemInfo.getHandlingCode())) {
                      String packTypeCode =
                          RdcUtils.getBreakPackRatio(deliveryDocumentLine) > 1
                              ? BREAK_PACK_TYPE_CODE
                              : CASE_PACK_TYPE_CODE;
                      additionalItemInfo.setPackTypeCode(packTypeCode);
                      String itemHandlingCode = deliveryDocumentLine.getHandlingCode();
                      if (StringUtils.isEmpty(itemHandlingCode)) {
                        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                            TenantContext.getFacilityNum().toString(),
                            ReceivingConstants.DEFAULT_ITEM_HANDLING_CODE_ENABLED,
                            false)) {
                          itemHandlingCode = CONVEYABLE_HANDLING_CODE;
                        } else {
                          String errorMessage =
                              String.format(
                                  ReceivingConstants.MISSING_ITEM_HANDLING_CODE,
                                  deliveryDocumentLine.getItemNbr());
                          LOGGER.error(errorMessage);
                          throw new ReceivingBadDataException(
                              ExceptionCodes.MISSING_ITEM_HANDLING_CODE,
                              errorMessage,
                              String.valueOf(deliveryDocumentLine.getItemNbr()));
                        }
                      }
                      additionalItemInfo.setHandlingCode(itemHandlingCode);

                      String itemPackAndHandlingCode =
                          StringUtils.join(
                              additionalItemInfo.getPackTypeCode(),
                              additionalItemInfo.getHandlingCode());
                      additionalItemInfo.setItemPackAndHandlingCode(itemPackAndHandlingCode);
                      String itemHandlingMethod =
                          ReceivingUtils.PACKTYPE_HANDLINGCODE_MAP.get(itemPackAndHandlingCode);
                      additionalItemInfo.setItemHandlingMethod(
                          Objects.isNull(itemHandlingMethod)
                              ? ReceivingUtils.INVALID_HANDLING_METHOD_OR_PACK_TYPE
                              : itemHandlingMethod);
                    }
                    deliveryDocumentLine.setAdditionalInfo(additionalItemInfo);
                  });
        });
  }

  public void publishInstructionToWft(
      Container container,
      Integer currentContainerQtyInVnpk,
      Integer newContainerQty,
      LabelAction action,
      HttpHeaders httpHeaders) {
    if (!ObjectUtils.allNotNull(
        httpHeaders.getFirst(RdcConstants.WFT_LOCATION_ID),
        httpHeaders.getFirst(RdcConstants.WFT_LOCATION_TYPE),
        httpHeaders.getFirst(RdcConstants.WFT_SCC_CODE))) {
      LocationInfo locationInfo =
          locationService.getLocationInfoByIdentifier(container.getLocation(), httpHeaders);
      rdcContainerUtils.setLocationHeaders(httpHeaders, locationInfo);
    }
    Integer differenceInAdjustedQty = currentContainerQtyInVnpk - newContainerQty;
    instructionHelperService.publishInstruction(
        httpHeaders,
        prepareInstructionMessage(
            container.getContainerItems().get(0), action, differenceInAdjustedQty, httpHeaders));
  }

  public Instruction updateInstructionQuantity(Long id, Integer quantity)
      throws ReceivingException {
    Instruction instruction = instructionPersisterService.getInstructionById(id);
    instruction.setReceivedQuantity(quantity);
    return instructionPersisterService.saveInstruction(instruction);
  }

  public void isNewItem(DeliveryDocumentLine deliveryDocumentLine) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.FTS_ITEM_CHECK_ENABLED,
        false)) {
      if (deliveryDocumentLine.isNewItem()) {
        String errorMsg =
            String.format(ReceivingException.NEW_ITEM_ERROR_MSG, deliveryDocumentLine.getItemNbr());
        LOGGER.error(errorMsg);
        throw new ReceivingBadDataException(
            ExceptionCodes.NEW_ITEM, errorMsg, String.valueOf(deliveryDocumentLine.getItemNbr()));
      }
    }
  }

  public void checkIfContainerAlreadyReceived(InstructionRequest instructionRequest) {
    if (Objects.nonNull(instructionRequest.getSscc())
        && !CollectionUtils.isEmpty(instructionRequest.getDeliveryDocuments())) {
      checkIfContainerWithSsccAndItemReceived(
          instructionRequest.getSscc(),
          instructionRequest
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getItemNbr());
    } else {
      checkIfContainerWithSsccReceived(instructionRequest.getSscc());
    }
  }

  private void checkIfContainerWithSsccReceived(String ssccNumber) {
    if (rdcManagedConfig.isAsnReceivingEnabled() && Objects.nonNull(ssccNumber)) {
      int receivedQuantityForSSCC = rdcContainerUtils.receivedContainerQuantityBySSCC(ssccNumber);
      if (receivedQuantityForSSCC > 0) {
        throw new ReceivingBadDataException(
            ExceptionCodes.SSCC_RECEIVED_ALREADY,
            String.format(ReceivingException.RECEIVE_INSTRUCTION_EXCEEDS_SSCC_LIMIT, ssccNumber),
            ssccNumber);
      }
    }
  }

  public void checkIfContainerWithSsccAndItemReceived(String sscc, Long itemNumber) {
    boolean isContainerReceivedBySSCCAndItem =
        rdcContainerUtils.isContainerReceivedBySSCCAndItem(sscc, itemNumber);
    if (isContainerReceivedBySSCCAndItem) {
      throw new ReceivingBadDataException(
          ExceptionCodes.SSCC_RECEIVED_ALREADY,
          String.format(ReceivingException.RECEIVE_INSTRUCTION_EXCEEDS_SSCC_LIMIT, sscc),
          sscc);
    }
  }

  public void checkIfContainerAlreadyReceived(
      String ssccNumber, Long itemNumber, int quantityToBeReceived, int projectedReceiveQty) {
    if (rdcManagedConfig.isAsnReceivingEnabled() && Objects.nonNull(ssccNumber)) {
      boolean receivedQuantityForSSCCAndItemNumber =
          rdcContainerUtils.isContainerReceivedBySSCCAndItem(ssccNumber, itemNumber);
      if (receivedQuantityForSSCCAndItemNumber || quantityToBeReceived > projectedReceiveQty) {
        throw new ReceivingBadDataException(
            ExceptionCodes.SSCC_RECEIVED_ALREADY,
            String.format(ReceivingException.RECEIVE_INSTRUCTION_EXCEEDS_SSCC_LIMIT, ssccNumber),
            ssccNumber);
      }
    }
  }

  /**
   * This method validates whether the same type of items are going to be added in the split pallet
   * instruction creation. Exception will be thrown when instruction request is received for
   * different types of items. As a short term fix to support Atlas 3.4 ramp up plan, we don't mix
   * multiple PO items into Split Pallet only for Non Atlas items.
   *
   * @param instructionRequest
   * @param deliveryDocumentLine
   */
  public void validateRequestIsForSameItemTypes(
      InstructionRequest instructionRequest, DeliveryDocumentLine deliveryDocumentLine) {
    LOGGER.info("Validating all items added on this split pallet are same item type ....");
    if (Objects.nonNull(instructionRequest.getInstructionSetId())) {
      List<String> deliveryDocuments =
          instructionPersisterService.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
              Long.valueOf(instructionRequest.getDeliveryNumber()),
              instructionRequest.getInstructionSetId());
      if (!CollectionUtils.isEmpty(deliveryDocuments)) {
        for (String document : deliveryDocuments) {
          DeliveryDocument deliveryDocument = gson.fromJson(document, DeliveryDocument.class);
          DeliveryDocumentLine existingDeliveryDocumentLine =
              deliveryDocument.getDeliveryDocumentLines().get(0);
          if (existingDeliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()
              != deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()) {
            LOGGER.error(
                "Create instruction for split pallet is not allowed for different type of items ...");
            throw new ReceivingBadDataException(
                ExceptionCodes.INSTR_CREATE_SPLIT_PALLET_DIFFERENT_ITEM_TYPES_400,
                String.format(
                    ReceivingConstants.MIXED_ITEM_TYPES_IN_SPLIT_PALLET_CREATION,
                    deliveryDocumentLine.getItemNbr()),
                String.valueOf(deliveryDocumentLine.getItemNbr()));
          }
          if (!existingDeliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()
              && !deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()) {
            if (!existingDeliveryDocumentLine
                .getPurchaseReferenceNumber()
                .equals(deliveryDocumentLine.getPurchaseReferenceNumber())) {
              LOGGER.error(
                  "Create instruction for split pallet is not allowed for different PO numbers for Non Atlas items.");
              throw new ReceivingBadDataException(
                  ExceptionCodes.MIXED_PO_NUMBERS_NOT_ALLOWED_IN_SPLIT_PALLET,
                  String.format(
                      ReceivingConstants.MIXED_PO_NUMBERS_NOT_ALLOWED_IN_SPLIT_PALLET,
                      deliveryDocumentLine.getItemNbr()),
                  String.valueOf(deliveryDocumentLine.getItemNbr()));
            }
          }
        }
      } else {
        LOGGER.info(
            "No instructions found for the deliveryNumber: {} and instruction set id: {}",
            instructionRequest.getDeliveryNumber(),
            instructionRequest.getInstructionSetId());
        return;
      }
    } else {
      LOGGER.info(
          "This is the very first item: {} for which split pallet instruction is going to be created, skipping mixed item type and prime slot compatibility validation",
          deliveryDocumentLine.getItemNbr());
      return;
    }
  }

  /**
   * For a split pallet instruction, we need to validate the following. 1. Item must be a break pack
   * item but not symbotic. 2. Items are in the split pallet must be of same item type. Meaning all
   * items must be either atlas or non-atlas converted. 3. Prime slot of all items added into this
   * split pallet must be on the same module level. If any of these validation fails then server
   * will throw ReceivingBadDataException with appropriate error message.
   *
   * @param instructionRequest
   * @param deliveryDocumentLine
   */
  public void validateInstructionCreationForSplitPallet(
      InstructionRequest instructionRequest, DeliveryDocumentLine deliveryDocumentLine) {
    if (rdcManagedConfig.isSplitPalletEnabled() && isSplitPalletInstruction(instructionRequest)) {
      validateRequestIsForBreakBackItem(deliveryDocumentLine);
      validateRequestIsForSameItemTypes(instructionRequest, deliveryDocumentLine);
      if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
          false)) {
        LOGGER.info(
            "Validate prime slot compatibility for Non Atlas Item:{}",
            deliveryDocumentLine.getItemNbr());
        validatePrimeSlotCompatibility(instructionRequest, deliveryDocumentLine);
      }
    } else {
      LOGGER.info("Split pallet capability is not enabled ....");
    }
  }

  public void validateCreateSplitPalletInstructionForSymItem(
      InstructionRequest instructionRequest, DeliveryDocumentLine deliveryDocumentLine) {
    if (rdcManagedConfig.isSplitPalletEnabled() && isSplitPalletInstruction(instructionRequest)) {
      if (isAtlasItemSymEligible(deliveryDocumentLine)
          || isNonAtlasItemSymEligible(deliveryDocumentLine)) {
        LOGGER.error(
            "Item: {} is Symbotic break pack item, so it cannot be added into split pallet",
            deliveryDocumentLine.getItemNbr());
        throw new ReceivingBadDataException(
            ExceptionCodes.INSTR_CREATE_SPLIT_PALLET_SYMBOTIC_BREAK_PACK_ITEM,
            ReceivingConstants.INSTR_CREATE_SPLIT_PALLET_SYMBOTIC_BREAK_PACK_ITEM);
      }
    }
  }

  public boolean isAtlasItemSymEligible(DeliveryDocumentLine deliveryDocumentLine) {
    return Objects.nonNull(deliveryDocumentLine.getAdditionalInfo().getAsrsAlignment())
        && !CollectionUtils.isEmpty(appConfig.getValidSymAsrsAlignmentValues())
        && appConfig
            .getValidSymAsrsAlignmentValues()
            .contains(deliveryDocumentLine.getAdditionalInfo().getAsrsAlignment())
        && deliveryDocumentLine
            .getAdditionalInfo()
            .getSlotType()
            .equalsIgnoreCase(ReceivingConstants.PRIME_SLOT_TYPE);
  }

  public boolean isNonAtlasItemSymEligible(DeliveryDocumentLine deliveryDocumentLine) {
    return Objects.nonNull(deliveryDocumentLine.getAdditionalInfo().getSymEligibleIndicator())
        && ReceivingConstants.RDS_SYM_ELIGIBLE_INDICATOR.contains(
            deliveryDocumentLine.getAdditionalInfo().getSymEligibleIndicator());
  }

  /**
   * This method validates item handling code and pack type combination that we received from GDM is
   * valid or not. If not valid then it will throw ReceivingBadDataException with appropriate error
   * message.
   *
   * @param deliveryDocumentLine
   */
  public void validateItemHandlingMethod(DeliveryDocumentLine deliveryDocumentLine) {
    String itemHandlingMethod = deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod();
    String itemHandlingCode = deliveryDocumentLine.getAdditionalInfo().getHandlingCode();

    if (StringUtils.isEmpty(itemHandlingCode)) {
      String errorMessage =
          String.format(
              ReceivingConstants.MISSING_ITEM_HANDLING_CODE, deliveryDocumentLine.getItemNbr());
      LOGGER.error(errorMessage);
      throw new ReceivingBadDataException(
          ExceptionCodes.MISSING_ITEM_HANDLING_CODE,
          errorMessage,
          String.valueOf(deliveryDocumentLine.getItemNbr()));
    }

    boolean isValidItemHandlingMethod = false;
    if (Objects.nonNull(itemHandlingMethod)) {
      if (ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(
          deliveryDocumentLine.getPurchaseRefType())) {
        isValidItemHandlingMethod = isValidPackTypeHandlingCodeForDAReceiving(deliveryDocumentLine);
      } else {
        isValidItemHandlingMethod =
            !itemHandlingMethod.equals(ReceivingUtils.INVALID_HANDLING_METHOD_OR_PACK_TYPE);
      }
    }

    if (!isValidItemHandlingMethod) {
      String errorMessage =
          String.format(
              ReceivingConstants.INVALID_ITEM_HANDLING_METHOD, deliveryDocumentLine.getItemNbr());
      LOGGER.error(errorMessage);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_ITEM_HANDLING_METHOD,
          errorMessage,
          String.valueOf(deliveryDocumentLine.getItemNbr()));
    }
  }

  private boolean isValidPackTypeHandlingCodeForDAReceiving(
      DeliveryDocumentLine deliveryDocumentLine) {
    return DA_VALID_PACKTYPE_HANDLING_METHODS_MAP.containsValue(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod());
  }

  public boolean isAtlasConvertedInstruction(Instruction instruction) {
    return Objects.nonNull(instruction)
        && gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem();
  }

  /**
   * This method simply compares the scanned UPC against all possible UPC's that are available in
   * the delivery document line. Returns true if scanned UPC matches with any one of the UPC's
   * available in delivery document line, otherwise false will be returned.
   *
   * @param upcNumber
   * @param deliveryDocumentLine
   * @return boolean
   */
  public boolean isSameUpc(String upcNumber, DeliveryDocumentLine deliveryDocumentLine) {
    List<String> possibleUpcList = new ArrayList<>();

    if (!org.springframework.util.ObjectUtils.isEmpty(deliveryDocumentLine.getCaseUpc())) {
      possibleUpcList.add(deliveryDocumentLine.getCaseUpc());
    }
    if (!org.springframework.util.ObjectUtils.isEmpty(deliveryDocumentLine.getItemUpc())) {
      possibleUpcList.add(deliveryDocumentLine.getItemUpc());
    }
    if (!org.springframework.util.ObjectUtils.isEmpty(deliveryDocumentLine.getVendorUPC())) {
      possibleUpcList.add(deliveryDocumentLine.getVendorUPC());
    }

    return !CollectionUtils.isEmpty(possibleUpcList) && possibleUpcList.contains(upcNumber);
  }

  /**
   * ProDate is mandatory for DC Fin, so we will validate and send the same to DC Fin if it exists
   * in GDM delivery documents. Otherwise, exception will be thrown to the caller.
   */
  public void verifyAndPopulateProDateInfo(
      DeliveryDocument deliveryDocument, Instruction instruction4mDB, HttpHeaders httpHeaders)
      throws ReceivingException {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    boolean isAtlasConvertedItem = deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem();
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()),
            RdcConstants.IS_DCFIN_INTEGRATION_ENABLED,
            false)
        && isAtlasConvertedItem
        && Objects.isNull(deliveryDocument.getProDate())) {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          String.valueOf(TenantContext.getFacilityNum()),
          IS_DEFAULT_PRO_DATE_ENABLED_FOR_SSTK,
          false)) {
        deliveryDocument.setProDate(new Date());
      } else {
        fetchProDateFromGDM(deliveryDocument, deliveryDocumentLine, instruction4mDB, httpHeaders);
      }
    }
  }

  /**
   * This method will fetch Get Delivery document by Po/PoLine and get the proDate from GDM. If
   * proDate is not available then it will throw error message as proDate is mandatory for DCFin
   *
   * @param deliveryDocument
   * @param deliveryDocumentLine
   * @param instruction4mDB
   * @param httpHeaders
   * @throws ReceivingException
   */
  private void fetchProDateFromGDM(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      Instruction instruction4mDB,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.info(
        "Missing proDate information in delivery document for instruction: {}, going to fetch from GDM",
        instruction4mDB.getId());
    GdmPOLineResponse gdmPOLineResponse =
        rdcDeliveryService.getPOLineInfoFromGDM(
            String.valueOf(instruction4mDB.getDeliveryNumber()),
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            httpHeaders);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    if (!CollectionUtils.isEmpty(deliveryDocumentList)) {
      if (Objects.nonNull(deliveryDocumentList.get(0).getProDate())) {
        deliveryDocument.setProDate(deliveryDocumentList.get(0).getProDate());
      } else {
        LOGGER.error(
            "Missing proDate information in delivery document for delivery: {}, PO: {}, POL: {} and delivery status: {}",
            instruction4mDB.getDeliveryNumber(),
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber(),
            deliveryDocumentList.get(0).getDeliveryStatus().name());
        throw new ReceivingInternalException(
            ExceptionCodes.PRODATE_NOT_FOUND_IN_PO_ERROR,
            String.format(ReceivingConstants.PRODATE_NOT_FOUND_IN_PO, instruction4mDB.getId()),
            String.valueOf(instruction4mDB.getDeliveryNumber()),
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
      }
    } else {
      LOGGER.error(
          "No delivery documents found in GDM for delivery: {}, PO: {}, POL: {} combinations",
          instruction4mDB.getDeliveryNumber(),
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.GDM_DELIVERY_DOCUMENTS_NOT_FOUND,
          String.format(
              ReceivingException.DELIVERY_DOCUMENT_NOT_FOUND_FOR_DELIVERY_PO_POL_ERROR,
              instruction4mDB.getDeliveryNumber(),
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber()),
          instruction4mDB.getDeliveryNumber(),
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
    }
  }

  /**
   * This method returns received quantity for the given po/poLine number. Incase of SSTK, if the
   * item is a non atlas item number then get received qty from Nim RDS else get received qty from
   * receipts. In case of DA, get received qty from Nim RDS
   *
   * @param deliveryDocumentLine
   * @return
   * @throws ReceivingException
   */
  public Long getReceivedQtyByPoAndPoLine(
      DeliveryDocumentLine deliveryDocumentLine, String poNumber, int poLineNumber)
      throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    deliveryDocumentList.add(deliveryDocument);

    // validate atlas & non atlas item only for SSTK
    if (PoType.STAPLESTOCK
            .getpoType()
            .equalsIgnoreCase(PoType.valueOf(deliveryDocumentLine.getPurchaseRefType()).getpoType())
        && enableAtlasConvertedItemValidationForSSTKReceiving()) {
      validateAtlasConvertedItems(deliveryDocumentList, httpHeaders);
    } else if (isDADocument(deliveryDocument)) {
      populateItemPackAndHandlingCode(deliveryDocumentList);
      checkAtlasConvertedItemForDa(deliveryDocumentList, httpHeaders);
    }

    Pair<DeliveryDocument, Long> autoSelectedDeliveryDocument =
        autoSelectDocumentAndDocumentLine(
            deliveryDocumentList,
            RDC_AUTO_RECEIVE_QTY,
            deliveryDocumentLine.getCaseUpc(),
            httpHeaders);

    return autoSelectedDeliveryDocument.getValue();
  }

  /** @return */
  public boolean enableAtlasConvertedItemValidationForSSTKReceiving() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false)
        || tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_CONVERSION_ENABLED_FOR_ALL_SSTK_ITEMS,
            false);
  }

  /**
   * This method populates pack type & handling code for the item. This is needed when we want to
   * check if the item is Atlas or not based on item pack handling codes
   *
   * @param deliveryDocuments
   * @return
   */
  private void populateItemPackAndHandlingCode(List<DeliveryDocument> deliveryDocuments) {
    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    if (Objects.isNull(deliveryDocumentLine.getAdditionalInfo())
        || Objects.isNull(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode())) {
      deliveryDocuments.clear();
      ItemData itemData = new ItemData();
      itemData.setPackTypeCode(deliveryDocumentLine.getPackType());
      itemData.setHandlingCode(deliveryDocumentLine.getHandlingCode());
      String itemPackAndHandlingCode =
          StringUtils.join(
              deliveryDocumentLine.getPackType(), deliveryDocumentLine.getHandlingCode());
      itemData.setItemPackAndHandlingCode(itemPackAndHandlingCode);
      deliveryDocumentLine.setAdditionalInfo(itemData);
      deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
      deliveryDocuments.add(deliveryDocument);
    }
  }

  /**
   * * This function validates if the hazmat item validation is enabled in GDM, return isHazmat else
   * Validate for hazmat item with below condition Hazmat Class != ORM-D, reg_code="UN",
   * transportation_mode=1 & dot_nbr is not null
   *
   * @param deliveryDocumentLine
   * @return true - If it is hazmat item false - otherwise
   */
  public Boolean isHazmatItem(DeliveryDocumentLine deliveryDocumentLine) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED,
        false)) {
      LOGGER.info(
          "Hazmat Information retrieved from GDM for Item: [{}], HazmatValidation:[{}]",
          deliveryDocumentLine.getItemNbr(),
          deliveryDocumentLine.getIsHazmat());
      return deliveryDocumentLine.getIsHazmat();
    }

    Boolean isItemTransportationModeValidatedForHazmat =
        InstructionUtils.isItemTransportationModeValidatedForHazmat(
            deliveryDocumentLine.getTransportationModes());
    LOGGER.info(
        "Hazmat Information retrieved from Receiving using RDS Validations for Item:[{}], HazmatValidation:[{}]",
        deliveryDocumentLine.getItemNbr(),
        isItemTransportationModeValidatedForHazmat);
    return isItemTransportationModeValidatedForHazmat;
  }

  /**
   * Validate last scanned freight type to the current scanned item delivery document if current and
   * last scanned freight type is matching then proced with the docktag creation If not matched with
   * the freight types then return the current scanned delivery documents are not a valid freight
   *
   * @param lastScannedFreightType
   * @param docLine
   * @return
   */
  private boolean isValidFreightType(String lastScannedFreightType, DeliveryDocumentLine docLine) {
    boolean isSameFreightType =
        PoType.valueOf(docLine.getPurchaseRefType())
            .getpoType()
            .equalsIgnoreCase(lastScannedFreightType);
    if (isSameFreightType) {
      return true;
    }
    List<PoType> poTypeList =
        matchScannedItemsMap.getOrDefault(
            lastScannedFreightType.toUpperCase(), Collections.emptyList());
    boolean isScannedFreightTypeMatched =
        poTypeList
            .stream()
            .anyMatch(poType -> poType.equals(PoType.valueOf(docLine.getPurchaseRefType())));
    return isScannedFreightTypeMatched;
  }

  public List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
      validateAndProcessGdmDeliveryDocuments(
          List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
              gdmDeliveryDocumentList) {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
        nonHistoryDeliveryDocuments = filterNonHistoryDeliveryDocuments(gdmDeliveryDocumentList);
    return filterInvalidPoLinesFromDocuments(nonHistoryDeliveryDocuments);
  }

  public List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
      filterNonHistoryDeliveryDocuments(
          List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
              deliveryDocumentList)
          throws ReceivingBadDataException {
    return deliveryDocumentList
        .stream()
        .filter(
            document -> {
              if (document.getPurchaseReferenceStatus().equalsIgnoreCase(POStatus.HISTORY.name())) {
                LOGGER.info("PO {} is History PO", document.getPurchaseReferenceNumber());
                return false;
              }
              return true;
            })
        .collect(Collectors.toList());
  }

  public List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
      filterInvalidPoLinesFromDocuments(
          List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
              deliveryDocuments) {
    Iterator<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument>
        deliveryDocumentsIterator = deliveryDocuments.iterator();
    while (deliveryDocumentsIterator.hasNext()) {
      com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument deliveryDocument =
          deliveryDocumentsIterator.next();
      Iterator<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine>
          deliveryDocumentLineIterator = deliveryDocument.getDeliveryDocumentLines().iterator();
      while (deliveryDocumentLineIterator.hasNext()) {
        com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine deliveryDocumentLine =
            deliveryDocumentLineIterator.next();
        if (POLineStatus.CANCELLED
                .name()
                .equalsIgnoreCase(deliveryDocumentLine.getPurchaseReferenceLineStatus())
            || POLineStatus.CLOSED
                .name()
                .equalsIgnoreCase(deliveryDocumentLine.getPurchaseReferenceLineStatus())) {
          LOGGER.info(
              "PO {} line {} is cancelled or closed.",
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
          deliveryDocumentLineIterator.remove();
        }
      }
      if (CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())) {
        deliveryDocumentsIterator.remove();
      }
    }
    return deliveryDocuments;
  }

  public void validateAndAddAsAtlasConvertedItemsV2(
      List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments,
      HttpHeaders httpHeaders) {
    try {
      Set<Long> sstkItems =
          deliveryDocuments
              .stream()
              .flatMap(deliveryDocument -> deliveryDocument.getDeliveryDocumentLines().stream())
              .map(
                  com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine::getItemNbr)
              .collect(Collectors.toSet());
      itemConfigApiClient.checkAndAddAsAtlasItems(sstkItems, httpHeaders);
    } catch (ItemConfigRestApiClientException e) {
      LOGGER.error(
          "Error when searching and adding atlas converted items errorCode = {} and error message = {} ",
          e.getHttpStatus(),
          ExceptionUtils.getMessage(e));
    }
  }

  /**
   * Validates if DSDC Instruction already exists by checking activity name as "DSDC"
   *
   * @param instructionRequest
   * @return
   */
  public Boolean checkIfDsdcInstructionAlreadyExists(InstructionRequest instructionRequest) {
    if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      TenantContext.get()
          .setFindInstructionByDeliveryNumberAndSsccStart(System.currentTimeMillis());
      List<Instruction> instructions =
          instructionPersisterService.findInstructionByDeliveryNumberAndSscc(instructionRequest);
      TenantContext.get().setFindInstructionByDeliveryNumberAndSsccEnd(System.currentTimeMillis());
      if (!CollectionUtils.isEmpty(instructions)) {
        Instruction instruction = instructions.get(0);
        LOGGER.info(
            "Existing Instruction fetched for delivery {} and SSCC {}",
            instructionRequest.getDeliveryNumber(),
            instructionRequest.getSscc());
        if (DSDC_ACTIVITY_NAME.equalsIgnoreCase(instruction.getActivityName())) {
          return Boolean.TRUE;
        }
      }
    }
    return Boolean.FALSE;
  }

  public boolean isCasePackPalletReceiving(DeliveryDocumentLine deliveryDocumentLine) {
    return RdcConstants.CASE_PACK_TYPE_CODE.equalsIgnoreCase(
            deliveryDocumentLine.getAdditionalInfo().getPackTypeCode())
        && RdcConstants.PALLET_RECEIVING_HANDLING_CODE.equalsIgnoreCase(
            deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
  }
}
