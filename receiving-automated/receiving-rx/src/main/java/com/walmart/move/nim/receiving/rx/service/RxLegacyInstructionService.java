package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isSinglePO;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isSinglePoLine;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ENABLE_EPCIS_SERVICES_FEATURE_FLAG;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EMPTY_STRING;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.GtinHierarchy;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rx.builders.RxContainerLabelBuilder;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.publisher.RxCancelInstructionReceiptPublisher;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;

public class RxLegacyInstructionService extends InstructionService {

  private static final Logger LOG = LoggerFactory.getLogger(RxInstructionService.class);

  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private RxManagedConfig rxManagedConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private RxInstructionPersisterService rxInstructionPersisterService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private EpcisService epcisService;
  @Autowired private RxContainerLabelBuilder containerLabelBuilder;
  @Autowired private Gson gson;
  @Autowired private NimRdsServiceImpl nimRdsServiceImpl;
  @Autowired private RxDeliveryServiceImpl rxDeliveryService;
  @Autowired private ContainerService containerService;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private RxSlottingServiceImpl rxSlottingServiceImpl;
  @Autowired private RxInstructionHelperService rxInstructionHelperService;
  @Autowired private InstructionSetIdGenerator instructionSetIdGenerator;
  @Autowired private RxReceiptsBuilder rxReceiptsBuilder;
  @Autowired private RxCancelInstructionReceiptPublisher rxCancelInstructionReceiptsPublisher;
  @Autowired private ShipmentSelectorService shipmentSelector;

  @Override
  public InstructionResponse serveInstructionRequest(
      String instructionRequestString, HttpHeaders httpHeaders) throws ReceivingException {

    if (!appConfig.isOverrideServeInstrMethod()) {
      return super.serveInstructionRequest(instructionRequestString, httpHeaders);
    }

    InstructionRequest instructionRequest =
        gson.fromJson(instructionRequestString, InstructionRequest.class);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();

    Instruction instruction = null;
    if (StringUtils.isEmpty(instructionRequest.getUpcNumber())
        && StringUtils.isEmpty(instructionRequest.getSscc())) {
      if (StringUtils.isEmpty(instructionRequest.getUpcNumber())) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.NO_UPC_ERROR);
        LOG.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.BAD_REQUEST,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
      } else if (StringUtils.isEmpty(instructionRequest.getSscc())) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.NO_SSCC_ERROR);
        LOG.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.BAD_REQUEST,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
      }
    }

    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    // if client sends delivery documents, use that
    if (CollectionUtils.isEmpty(deliveryDocuments)) {

      // Get DeliveryDocument from GDM
      deliveryDocuments = fetchDeliveryDocument(instructionRequest, httpHeaders);

      // enrich incoming instruction request with deliveryDocs from GDM
      instructionRequest.setDeliveryDocuments(deliveryDocuments);
    }

    // Check if Delivery is in receivable state
    if (appConfig.isCheckDeliveryStatusReceivable()) {
      InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocuments.get(0));
    }
    filterInvalidPOs(deliveryDocuments);
    // update delivery docs
    deliveryDocuments = deliveryDocumentHelper.updateDeliveryDocuments(deliveryDocuments);

    if (isSinglePO(deliveryDocuments)) {
      validateDocumentLineExists(deliveryDocuments);

      if (isSinglePoLine(deliveryDocuments.get(0))) {

        checkIfLineIsRejected(deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));

        instruction = createInstructionForUpcReceiving(instructionRequest, httpHeaders);
        instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
        instructionResponse.setInstruction(instruction);

      } else {
        isSingleItemMultiPoPoLine(
            deliveryDocuments, instructionRequest, instructionResponse, httpHeaders);
      }

    } else {
      isSingleItemMultiPoPoLine(
          deliveryDocuments, instructionRequest, instructionResponse, httpHeaders);
    }
    return instructionResponse;
  }

  @Override
  protected Instruction createInstructionForUpcReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {

    boolean is2dBarcodeRequest =
        RxUtils.is2DScanInstructionRequest(instructionRequest.getScannedDataList());

    if (isUpcScanRequest(instructionRequest) && !is2dBarcodeRequest) {
      return createInstructionForUpcDscsaExempted(instructionRequest, httpHeaders);
    }

    // this fetch existing instruction can be done with out even calling GDM
    // Push this to serveInstructionRequest method
    Instruction instructionBySSCC = null;
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    if (isSSCCScanRequest(instructionRequest)) {
      validateScannedData(scannedDataMap);
      instructionBySSCC =
          StringUtils.isNotBlank(instructionRequest.getProblemTagId())
              ? instructionPersisterService
                  .fetchInstructionByDeliveryNumberSSCCAndUserIdAndProblemTagId(
                      instructionRequest, userId)
              : rxInstructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(
                  instructionRequest, userId);
      if (Objects.nonNull(instructionBySSCC)) {
        instructionRequest.setDeliveryDocuments(
            Arrays.asList(
                gson.fromJson(instructionBySSCC.getDeliveryDocument(), DeliveryDocument.class)));
        return instructionBySSCC;
      }
    }

    // this fetch existing instruction can be done with out even calling GDM
    // Push this to serveInstructionRequest method
    String deliveryNumber = instructionRequest.getDeliveryNumber();
    Instruction existing2dBarcodeInstruction = null;
    if (is2dBarcodeRequest) {
      existing2dBarcodeInstruction =
          StringUtils.isNotBlank(instructionRequest.getProblemTagId())
              ? instructionPersisterService
                  .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdAndProblemTagId(
                      Long.valueOf(deliveryNumber),
                      scannedDataMap,
                      userId,
                      instructionRequest.getProblemTagId())
              : rxInstructionPersisterService
                  .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
                      instructionRequest, userId);
      if (Objects.nonNull(existing2dBarcodeInstruction)) {
        instructionRequest.setDeliveryDocuments(
            Arrays.asList(
                gson.fromJson(
                    existing2dBarcodeInstruction.getDeliveryDocument(), DeliveryDocument.class)));
        return existing2dBarcodeInstruction;
      }
    }

    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    if (StringUtils.isNotEmpty(instructionRequest.getProblemTagId())) {

      Optional<List<DeliveryDocument>> latestDeliveryDocuments =
          checkForLatestShipments(instructionRequest, httpHeaders, scannedDataMap);
      if (latestDeliveryDocuments.isPresent()) {
        deliveryDocuments = latestDeliveryDocuments.get();
      }
    }
    if (appConfig.isFilteringInvalidposEnabled()) {
      filterInvalidPOs(deliveryDocuments);
    }
    Pair<DeliveryDocument, Long> autoSelectedDocument =
        instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            deliveryDocuments, 1, EMPTY_STRING);
    if (Objects.isNull(autoSelectedDocument)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.AUTO_SELECT_PO_NO_OPEN_QTY, RxConstants.AUTO_SELECT_PO_NO_OPEN_QTY);
    }

    DeliveryDocument autoSelectedDeliveryDocument = autoSelectedDocument.getKey();
    DeliveryDocumentLine autoSelectedDeliveryDocumentLine =
        autoSelectedDeliveryDocument.getDeliveryDocumentLines().get(0);
    logMatchedGtin(autoSelectedDeliveryDocumentLine, scannedDataMap);
    String purchaseReferenceNumber = autoSelectedDeliveryDocument.getPurchaseReferenceNumber();
    int purchaseReferenceLineNumber =
        autoSelectedDeliveryDocumentLine.getPurchaseReferenceLineNumber();

    // Temporary patch till a permanent fix made on the client side
    if (autoSelectedDeliveryDocument.getDeliveryNumber() == 0) {
      autoSelectedDeliveryDocument.setDeliveryNumber(Long.parseLong(deliveryNumber));
    }

    // push this to calling method to serveInstructionRequest
    // if any instruction already exists with message id, return back that immediately
    Instruction instruction =
        instructionPersisterService.fetchExistingInstructionIfexists(instructionRequest);
    if (Objects.nonNull(instruction)) {

      // send back only the selected Po Po-Lines to the client
      autoSelectedDeliveryDocument.setDeliveryDocumentLines(
          Arrays.asList(autoSelectedDeliveryDocumentLine));
      instructionRequest.setDeliveryDocuments(Arrays.asList(autoSelectedDeliveryDocument));
      return instruction;
    }

    FitProblemTagResponse fitProblemTagResponse = null;
    if (rxManagedConfig.isProblemItemCheckEnabled()) { // Feature Flag
      if (StringUtils.isNotBlank(instructionRequest.getProblemTagId())) {
        Optional<FitProblemTagResponse> fitProblemTagResponseOptional =
            rxInstructionHelperService.getFitProblemTagResponse(
                instructionRequest.getProblemTagId());
        if (fitProblemTagResponseOptional.isPresent()) {
          fitProblemTagResponse = fitProblemTagResponseOptional.get();
          rxInstructionHelperService.sameItemOnProblem(
              fitProblemTagResponse, autoSelectedDeliveryDocumentLine);
          if (is2dBarcodeRequest) {
            rxInstructionHelperService.checkIfContainerIsCloseDated(
                fitProblemTagResponse, scannedDataMap);
          }
        }
      } else {
        if (is2dBarcodeRequest) {
          rxInstructionHelperService.checkIfContainerIsCloseDated(scannedDataMap);
        }
      }
    }
    // this is validation has to be called in serveInstructionRequest
    verifyDeliverDocumentLine(autoSelectedDeliveryDocumentLine);

    boolean dscsaExemptionIndEnabled =
        RxUtils.isDscsaExemptionIndEnabled(
            autoSelectedDeliveryDocumentLine,
            configUtils.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG));
    if (dscsaExemptionIndEnabled
        || RxUtils.isTransferredPo(autoSelectedDeliveryDocument.getPoTypeCode())
        || isRepackagedVendor(autoSelectedDeliveryDocument)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVLID_D40_RECEIVING_FLOW,
          ReceivingException.INVLID_D40_RECEIVING_FLOW_DESC);
    }
    ShipmentDetails shipmentDetails =
        shipmentSelector.autoSelectShipment(autoSelectedDeliveryDocumentLine);
    epcisService.verifySerializedData(
        scannedDataMap, shipmentDetails, autoSelectedDeliveryDocumentLine, httpHeaders);

    Pair<Integer, Long> receivedQtyDetails =
        instructionHelperService.getReceivedQtyDetailsAndValidate(
            instructionRequest.getProblemTagId(),
            autoSelectedDeliveryDocument,
            deliveryNumber,
            false,
            false);

    long totalReceivedQty = receivedQtyDetails.getValue();

    autoSelectedDeliveryDocumentLine.setOpenQty(
        autoSelectedDeliveryDocumentLine.getTotalOrderQty() - (int) totalReceivedQty);

    int projectedReceiveQty = 0;
    boolean isPartialCase =
        RxUtils.isPartialCase(
            autoSelectedDeliveryDocumentLine.getManufactureDetails(),
            autoSelectedDeliveryDocumentLine.getVendorPack(),
            autoSelectedDeliveryDocumentLine.getWarehousePack());
    rxInstructionHelperService.validatePartialsInSplitPallet(instructionRequest, isPartialCase);
    if (isPartialCase) {
      projectedReceiveQty = 1;
      LOG.info(
          "Delivery:{}, SSCC Number:{} is identified as Partial Case",
          deliveryNumber,
          instructionRequest.getSscc());
    } else {
      if (StringUtils.isBlank(instructionRequest.getProblemTagId())) { // regular receiving
        projectedReceiveQty =
            RxUtils.getProjectedReceivedQty(autoSelectedDeliveryDocumentLine, totalReceivedQty);
      } else { // Problem receiving
        if (rxManagedConfig.isProblemItemCheckEnabled()) { // Feature flag
          if (Objects.nonNull(fitProblemTagResponse)
              && org.apache.commons.collections4.CollectionUtils.isNotEmpty(
                  fitProblemTagResponse.getResolutions())) {
            int problemResolutionQty = fitProblemTagResponse.getResolutions().get(0).getQuantity();
            projectedReceiveQty =
                RxUtils.getProjectedReceivedQtyForProblem(
                    autoSelectedDeliveryDocumentLine, totalReceivedQty, problemResolutionQty);
          } else {
            throw new ReceivingBadDataException(
                ExceptionCodes.PROBLEM_NOT_FOUND, ReceivingException.PROBLEM_NOT_FOUND);
          }
        } else {
          projectedReceiveQty =
              RxUtils.getProjectedReceivedQty(autoSelectedDeliveryDocumentLine, totalReceivedQty);
        }
      }
    }

    if (projectedReceiveQty <= 0) {
      instructionError =
          InstructionErrorCode.getErrorValue(ReceivingException.ALLOWED_CASES_RECEIVED);

      throw new ReceivingBadDataException(
          ExceptionCodes.CREATE_INSTRUCTION_ERROR_NO_OPEN_QTY,
          RxConstants.INVALID_ALLOWED_CASES_RECEIVED);
    }

    isNewInstructionCanBeCreated(
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        projectedReceiveQty,
        totalReceivedQty,
        StringUtils.isNotBlank(instructionRequest.getProblemTagId()),
        RxUtils.isSplitPalletInstructionRequest(instructionRequest),
        userId);

    instruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            autoSelectedDeliveryDocument,
            InstructionUtils.mapHttpHeaderToInstruction(
                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));
    instruction.setSsccNumber(instructionRequest.getSscc());

    LinkedTreeMap<String, Object> moveTreeMap =
        moveDetailsForInstruction(instructionRequest, autoSelectedDeliveryDocument, httpHeaders);

    instruction.setMove(moveTreeMap);

    instruction.setProjectedReceiveQty(projectedReceiveQty);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);

    RxInstructionType rxInstructionType =
        getRxInstructionType(instructionRequest, autoSelectedDeliveryDocumentLine, isPartialCase);
    instruction.setInstructionCode(rxInstructionType.getInstructionType());
    instruction.setInstructionMsg(rxInstructionType.getInstructionMsg());

    instruction.setProviderId(ReceivingConstants.RX_STK);
    instruction.setActivityName(ReceivingConstants.RX_STK);
    if (Objects.nonNull(instructionRequest.getProblemTagId())) {
      instruction.setProblemTagId(instructionRequest.getProblemTagId());
    }

    // send back only the selected Po Po-Lines to the client
    autoSelectedDeliveryDocument.setDeliveryDocumentLines(
        Arrays.asList(autoSelectedDeliveryDocumentLine));
    instructionRequest.setDeliveryDocuments(Arrays.asList(autoSelectedDeliveryDocument));
    if (StringUtils.isNotEmpty(instructionRequest.getUpcNumber())) {
      instruction.setGtin(instructionRequest.getUpcNumber());
    }
    populateInstructionSetId(instructionRequest, instruction);
    return instructionPersisterService.saveInstruction(instruction);
  }

  private void populateInstructionSetId(
      InstructionRequest instructionRequest, Instruction instruction) {
    if (rxManagedConfig.isSplitPalletEnabled()) {
      Optional<RxReceivingType> rxReceivingTypeOptional =
          RxReceivingType.fromString(instructionRequest.getReceivingType());
      if (rxReceivingTypeOptional.isPresent()
          && rxReceivingTypeOptional.get().isSplitPalletGroup()) {
        if (Objects.isNull(instructionRequest.getInstructionSetId())) {
          instruction.setInstructionSetId(instructionSetIdGenerator.generateInstructionSetId());
        } else {
          instruction.setInstructionSetId(instructionRequest.getInstructionSetId());
        }
      }
    }
  }

  public Optional<List<DeliveryDocument>> checkForLatestShipments(
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders,
      Map<String, ScannedData> scannedDataMap)
      throws ReceivingException {
    Optional<List<DeliveryDocument>> oDeliveryDocuments = Optional.empty();
    if (appConfig.isAttachLatestShipments()) {
      LOG.info("Checking for latest shipment, delivery {}", instructionRequest.getDeliveryNumber());
      String sscc = instructionRequest.getSscc();
      if (StringUtils.isNotBlank(sscc)) {
        oDeliveryDocuments =
            rxDeliveryService.findDeliveryDocumentBySSCCWithLatestShipmentLinking(
                instructionRequest.getDeliveryNumber(), sscc, httpHeaders);
      } else {
        oDeliveryDocuments =
            rxDeliveryService.linkDeliveryAndShipmentByGtinAndLotNumber(
                instructionRequest.getDeliveryNumber(), scannedDataMap, httpHeaders);
      }
    }
    return oDeliveryDocuments;
  }

  private void isNewInstructionCanBeCreated(
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber,
      int maxReceiveQty,
      long totalReceivedQty,
      boolean isProblemReceiving,
      boolean isSplitPalletInstruction,
      String userId)
      throws ReceivingException {
    try {
      rxInstructionPersisterService.checkIfNewInstructionCanBeCreated(
          purchaseReferenceNumber,
          purchaseReferenceLineNumber,
          totalReceivedQty,
          maxReceiveQty,
          isSplitPalletInstruction,
          userId);
    } catch (ReceivingException e) {
      if (!isProblemReceiving) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.MULTI_USER_ERROR);

        if (instructionError.getErrorMessage().equals(e.getErrorResponse().getErrorMessage())
            && instructionError.getErrorCode().equals(e.getErrorResponse().getErrorCode())
            && instructionError.getErrorHeader().equals(e.getErrorResponse().getErrorHeader())) {

          e.getErrorResponse().setErrorCode(RxConstants.REQUEST_TRANSFTER_INSTR_ERROR_CODE);
        }
      }
      throw e;
    }
  }

  private void logMatchedGtin(
      DeliveryDocumentLine deliveryDocumentLine, Map<String, ScannedData> scannedDataMap) {
    Map<String, String> gtinHierarchyMap = new HashMap<>();
    if (Objects.nonNull(deliveryDocumentLine.getGtinHierarchy())) {
      for (GtinHierarchy gtinHierarchy : deliveryDocumentLine.getGtinHierarchy()) {
        gtinHierarchyMap.put(gtinHierarchy.getGtin(), gtinHierarchy.getType());
      }
    }
    gtinHierarchyMap.put(RxConstants.VENDOR_UPC, deliveryDocumentLine.getVendorUPC());

    ScannedData gtinScannedData = scannedDataMap.get(ReceivingConstants.KEY_GTIN);
    if (Objects.nonNull(gtinScannedData)) {
      String inputGtin = gtinScannedData.getValue();

      String matchedGtinType = gtinHierarchyMap.get(inputGtin);
      if (StringUtils.isNotBlank(matchedGtinType)) {
        LOG.info(
            "Scanned Gtin : {}, Gtin Hierarchy : {}, Matched Gtin Type : {}",
            inputGtin,
            gtinHierarchyMap,
            matchedGtinType);
      } else {
        LOG.info(
            "Scanned Gtin : {}, Gtin Hierarchy : {}, Matched Gtin Type not found",
            inputGtin,
            gtinHierarchyMap);
      }
    }
  }

  private void filterInvalidPOs(List<DeliveryDocument> deliveryDocuments) {
    StringJoiner errorStatusStringJoiner = new StringJoiner(",");
    StringJoiner cancelledStatusStringJoiner = new StringJoiner(",");
    StringJoiner rejectedStatusStringJoiner = new StringJoiner(",");
    Iterator<DeliveryDocument> deliveryDocumentsIterator = deliveryDocuments.iterator();
    while (deliveryDocumentsIterator.hasNext()) {
      DeliveryDocument deliveryDocument = deliveryDocumentsIterator.next();
      if (POStatus.CNCL.toString().equals(deliveryDocument.getPurchaseReferenceStatus())) {
        cancelledStatusStringJoiner.add(
            String.format(
                RxConstants.INVALID_PO_STATUS_ERROR_FORMAT_PREFIX,
                deliveryDocument.getPurchaseReferenceNumber()));
        deliveryDocumentsIterator.remove();
      } else {
        Iterator<DeliveryDocumentLine> deliveryDocumentLineIterator =
            deliveryDocument.getDeliveryDocumentLines().iterator();
        while (deliveryDocumentLineIterator.hasNext()) {
          DeliveryDocumentLine deliveryDocumentLine = deliveryDocumentLineIterator.next();
          if (POLineStatus.CANCELLED
              .toString()
              .equals(deliveryDocumentLine.getPurchaseReferenceLineStatus())) {
            cancelledStatusStringJoiner.add(
                String.format(
                    RxConstants.INVALID_PO_PO_LINE_STATUS_ERROR_FORMAT_PREFIX,
                    deliveryDocumentLine.getPurchaseReferenceNumber(),
                    deliveryDocumentLine.getPurchaseReferenceLineNumber()));
            deliveryDocumentLineIterator.remove();
          } else if (POLineStatus.REJECTED
              .toString()
              .equals(deliveryDocumentLine.getPurchaseReferenceLineStatus())) {
            rejectedStatusStringJoiner.add(
                String.format(
                    RxConstants.INVALID_PO_PO_LINE_STATUS_ERROR_FORMAT_PREFIX,
                    deliveryDocumentLine.getPurchaseReferenceNumber(),
                    deliveryDocumentLine.getPurchaseReferenceLineNumber()));
            deliveryDocumentLineIterator.remove();
          }
        }
        if (CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())) {
          deliveryDocumentsIterator.remove();
        }
      }
    }
    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      if (StringUtils.isNotBlank(cancelledStatusStringJoiner.toString())) {
        errorStatusStringJoiner.add(
            String.format(
                RxConstants.INVALID_PO_STATUS_ERROR_FORMAT,
                cancelledStatusStringJoiner.toString(),
                POLineStatus.CANCELLED.toString()));
      }
      if (StringUtils.isNotBlank(rejectedStatusStringJoiner.toString())) {
        errorStatusStringJoiner.add(
            String.format(
                RxConstants.INVALID_PO_STATUS_ERROR_FORMAT,
                rejectedStatusStringJoiner.toString(),
                POLineStatus.REJECTED.toString()));
      }
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_PO_PO_LINE_STATUS,
          RxConstants.INVALID_PO_PO_LINE_STATUS,
          errorStatusStringJoiner.toString());
    }
  }

  private RxInstructionType getRxInstructionType(
      InstructionRequest instructionRequest,
      DeliveryDocumentLine deliveryDocumentLine,
      boolean isPartialCase) {

    if (isPartialCase) {
      return RxInstructionType.BUILD_PARTIAL_CONTAINER;
    } else if (Objects.nonNull(deliveryDocumentLine.getPalletSSCC())
        && instructionRequest.getSscc().equalsIgnoreCase(deliveryDocumentLine.getPalletSSCC())) {
      return RxInstructionType.BUILD_CONTAINER;
    } else if (StringUtils.isBlank(instructionRequest.getSscc())
        && !CollectionUtils.isEmpty(instructionRequest.getScannedDataList())) {
      Map<String, ScannedData> scannedDataMap =
          RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
      String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
      String lotNumber = scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue();
      if (StringUtils.isNotBlank(gtin) && StringUtils.isNotBlank(lotNumber)) {
        return RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT;
      }
    }
    LOG.info("Entering into default" + "" + "" + " case");
    return RxInstructionType.BUILDCONTAINER_CASES_SCAN;
  }

  private void verifyDeliverDocumentLine(DeliveryDocumentLine deliveryDocumentLine) {
    if (StringUtils.isEmpty(deliveryDocumentLine.getDeptNumber())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.DEPT_UNAVAILABLE, RxConstants.DEPT_TYPE_UNAVAILABLE);
    }
    if (CollectionUtils.isEmpty(deliveryDocumentLine.getShipmentDetailsList())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.SHIPMENT_UNAVAILABLE, RxConstants.SHIPMENT_DETAILS_UNAVAILABLE);
    }
  }

  private void validateScannedData(Map<String, ScannedData> scannedDataMap) {
    if (CollectionUtils.isEmpty(scannedDataMap)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA);
    }
    ScannedData ssccScannedData = scannedDataMap.get(ApplicationIdentifier.SSCC.getKey());
    if (Objects.isNull(ssccScannedData) || StringUtils.isEmpty(ssccScannedData.getValue())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA_SSCC_NOT_AVAILABLE);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public InstructionResponse completeInstruction(
      Long instructionId,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    try {
      TenantContext.get().setCompleteInstrStart(System.currentTimeMillis());

      // Getting instruction from DB.
      Instruction instructionFromDB = instructionPersisterService.getInstructionById(instructionId);
      validateInstructionStatus(instructionFromDB);

      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      String instructionOwner =
          StringUtils.isNotBlank(instructionFromDB.getLastChangeUserId())
              ? instructionFromDB.getLastChangeUserId()
              : instructionFromDB.getCreateUserId();
      verifyCompleteUser(instructionFromDB, instructionOwner, userId);

      DeliveryDocument deliveryDocument =
          gson.fromJson(instructionFromDB.getDeliveryDocument(), DeliveryDocument.class);
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);

      TenantContext.get().setCompleteInstrSlottingCallStart(System.currentTimeMillis());
      findSlotFromSmartSlotting(
          completeInstructionRequest, httpHeaders, instructionFromDB, deliveryDocumentLine);
      TenantContext.get().setCompleteInstrSlottingCallEnd(System.currentTimeMillis());

      TenantContext.get().setCompleteInstrNimRdsCallStart(System.currentTimeMillis());
      // Response from RDS with slot information
      ReceiveContainersResponseBody receiveContainersResponseBody =
          getLabelFromRDS(instructionFromDB, completeInstructionRequest, httpHeaders);
      TenantContext.get().setCompleteInstrNimRdsCallEnd(System.currentTimeMillis());

      String oldLabelTrackingId = instructionFromDB.getContainer().getTrackingId();
      String newLabelTrackingId =
          Long.valueOf(receiveContainersResponseBody.getReceived().get(0).getLabelTrackingId())
              .toString();

      Container parentContainer =
          containerService.getContainerWithChildsByTrackingId(
              instructionFromDB.getContainer().getTrackingId(), true);
      parentContainer.setTrackingId(newLabelTrackingId);
      ContainerItem parentContainerItem = parentContainer.getContainerItems().get(0);
      parentContainerItem.setTrackingId(newLabelTrackingId);

      Set<Container> containerList = new HashSet<>();
      parentContainer
          .getChildContainers()
          .forEach(
              childContainer -> {
                childContainer.setParentTrackingId(newLabelTrackingId);
                containerList.add(childContainer);
              });
      containerList.add(parentContainer);
      parentContainer.setChildContainers(containerList);

      instructionFromDB.getContainer().setTrackingId(newLabelTrackingId);
      List<ContainerDetails> childContainers = instructionFromDB.getChildContainers();
      if (!CollectionUtils.isEmpty(childContainers)) {
        for (ContainerDetails childContainer : childContainers) {
          if (oldLabelTrackingId.equals(childContainer.getParentTrackingId())) {
            childContainer.setParentTrackingId(newLabelTrackingId);
          }
        }
      }

      instructionFromDB.setChildContainers(childContainers);

      LinkedTreeMap<String, Object> moveTreeMap = instructionFromDB.getMove();
      moveTreeMap.put(
          ReceivingConstants.MOVE_TO_LOCATION,
          receiveContainersResponseBody.getReceived().get(0).getDestinations().get(0).getSlot());
      moveTreeMap.put(
          "correlationID", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
      moveTreeMap.put("lastChangedOn", new Date());
      moveTreeMap.put("lastChangedBy", userId);
      instructionFromDB.setMove(moveTreeMap);

      PrintLabelData containerLabel =
          containerLabelBuilder.generateContainerLabel(
              receiveContainersResponseBody.getReceived().get(0),
              deliveryDocumentLine,
              httpHeaders,
              parentContainer,
              instructionFromDB);

      if (!configUtils.isPrintingAndroidComponentEnabled()) {
        instructionFromDB.getContainer().setCtrLabel(getOldCtrlLabel(containerLabel));
      } else {
        instructionFromDB.getContainer().setCtrLabel(getNewCtrLabel(containerLabel, httpHeaders));
      }

      instructionFromDB.setCompleteUserId(userId);
      instructionFromDB.setCompleteTs(new Date());

      containerService.setDistributionAndComplete(userId, parentContainer);

      TenantContext.get().setCompleteInstrPersistDBCallStart(System.currentTimeMillis());
      rxInstructionHelperService.persist(parentContainer, instructionFromDB, userId);
      TenantContext.get().setCompleteInstrPersistDBCallEnd(System.currentTimeMillis());

      TenantContext.get().setCompleteInstrEpcisCallStart(System.currentTimeMillis());
      publishSerializedData(
          instructionFromDB, deliveryDocumentLine, completeInstructionRequest, httpHeaders);
      TenantContext.get().setCompleteInstrEpcisCallEnd(System.currentTimeMillis());

      TenantContext.get().setCompleteInstrCompleteProblemCallStart(System.currentTimeMillis());
      completeProblem(instructionFromDB, receiveContainersResponseBody, httpHeaders);
      TenantContext.get().setCompleteInstrCompleteProblemCallEnd(System.currentTimeMillis());

      if (rxManagedConfig.isTrimCompleteInstructionResponseEnabled()) {
        instructionFromDB.setChildContainers(Collections.emptyList());
      }

      InstructionResponse response = null;
      if (!configUtils.isPrintingAndroidComponentEnabled()) {
        response =
            new InstructionResponseImplOld(
                null,
                null,
                instructionFromDB,
                ReceivingUtils.getOldPrintJobWithAdditionalAttributes(
                    instructionFromDB, "-", configUtils));
      } else {
        response =
            new InstructionResponseImplNew(
                null, null, instructionFromDB, instructionFromDB.getContainer().getCtrLabel());
      }
      TenantContext.get().setCompleteInstrEnd(System.currentTimeMillis());
      calculateAndLogElapsedTimeSummary();

      return response;
    } catch (ReceivingBadDataException rbde) {
      LOG.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(rbde));
      throw rbde;
    } catch (ReceivingException receivingException) {
      LOG.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(receivingException));
      throw RxUtils.convertToReceivingBadDataException(receivingException);
    } catch (Exception e) {
      LOG.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVING_INTERNAL_ERROR,
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          e);
    }
  }

  private void calculateAndLogElapsedTimeSummary() {
    long timeTakeForCompleteInstrSlottingCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCompleteInstrSlottingCallStart(),
            TenantContext.get().getCompleteInstrSlottingCallEnd());

    long timeTakeForCompleteInstrNimRdsCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCompleteInstrNimRdsCallStart(),
            TenantContext.get().getCompleteInstrNimRdsCallEnd());

    long timeTakeForCompleteInstrEpcisCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCompleteInstrEpcisCallStart(),
            TenantContext.get().getCompleteInstrEpcisCallEnd());

    long timeTakeForCompleteInstrPersistDB =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCompleteInstrPersistDBCallStart(),
            TenantContext.get().getCompleteInstrPersistDBCallEnd());

    long timeTakeForCompleteInstrCompleteProblemCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCompleteInstrCompleteProblemCallStart(),
            TenantContext.get().getCompleteInstrCompleteProblemCallEnd());

    long totalTimeTakeforCompleteInstr =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCompleteInstrStart(), TenantContext.get().getCompleteInstrEnd());

    LOG.warn(
        "LatencyCheck completeInstruction at ts={} time in timeTakeForCompleteInstrSlottingCall={}, "
            + "timeTakeForCompleteInstrNimRdsCall={}, timeTakeForCompleteInstrEpcisCall={}, timeTakeForCompleteInstrPersistDB={}, "
            + "timeTakeForCompleteInstrCompleteProblemCall={}, totalTimeTakeforCompleteInstr={}, and correlationId={}",
        TenantContext.get().getCompleteInstrStart(),
        timeTakeForCompleteInstrSlottingCall,
        timeTakeForCompleteInstrNimRdsCall,
        timeTakeForCompleteInstrEpcisCall,
        timeTakeForCompleteInstrPersistDB,
        timeTakeForCompleteInstrCompleteProblemCall,
        totalTimeTakeforCompleteInstr,
        TenantContext.getCorrelationId());
  }

  private void findSlotFromSmartSlotting(
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders,
      Instruction instructionFromDB,
      DeliveryDocumentLine deliveryDocumentLine) {
    boolean isRxSmartSlottingEnabled =
        configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RxConstants.SMART_SLOTING_RX_FEATURE_FLAG);

    // Auto-Slotting with Smart Slotting flag enabled.
    // Partial Receiving - Slot to prime to be ignored here
    // Manual Slotting - No need to call Smart slotting
    if (isRxSmartSlottingEnabled
        && !completeInstructionRequest.isPartialContainer()
        && (Objects.isNull(completeInstructionRequest.getSlotDetails())
            || Objects.isNull(completeInstructionRequest.getSlotDetails().getSlot()))) {
      SlotDetails slotDetails = new SlotDetails();

      SlottingPalletResponse slottingRxPalletResponse =
          rxSlottingServiceImpl.acquireSlot(
              instructionFromDB.getMessageId(),
              Arrays.asList(deliveryDocumentLine.getItemNbr()),
              (Objects.isNull(completeInstructionRequest.getSlotDetails())
                      || Objects.isNull(completeInstructionRequest.getSlotDetails().getSlotSize()))
                  ? 0
                  : completeInstructionRequest.getSlotDetails().getSlotSize(),
              ReceivingConstants.SLOTTING_FIND_SLOT,
              httpHeaders);

      if (Objects.nonNull(slottingRxPalletResponse)
          && Objects.nonNull(slottingRxPalletResponse.getLocations())
          && StringUtils.isNotBlank(slottingRxPalletResponse.getLocations().get(0).getLocation())) {
        LOG.info(
            "Slot: {} is returned from smart-slotting",
            slottingRxPalletResponse.getLocations().get(0).getLocation());
        slotDetails.setSlot(
            slottingRxPalletResponse.getLocations().isEmpty()
                ? ""
                : slottingRxPalletResponse.getLocations().get(0).getLocation());
        completeInstructionRequest.setSlotDetails(slotDetails);
      }
    }
  }

  private void completeProblem(
      Instruction instruction,
      ReceiveContainersResponseBody receiveContainersResponseBody,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    if (org.apache.commons.lang3.StringUtils.isNotBlank(instruction.getProblemTagId())) {

      // removing host header as fixit k8 lb is not routing properly if the host header is present
      httpHeaders.remove(ReceivingConstants.HOST);

      ProblemService configuredProblemService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.PROBLEM_SERVICE,
              ProblemService.class);

      ProblemLabel problemLabelByProblemTagId =
          configuredProblemService.findProblemLabelByProblemTagId(instruction.getProblemTagId());

      Problem problem = new Problem();
      problem.setProblemTagId(instruction.getProblemTagId());
      problem.setDeliveryNumber(String.valueOf(instruction.getDeliveryNumber()));
      problem.setResolutionId(problemLabelByProblemTagId.getResolutionId());
      problem.setIssueId(problemLabelByProblemTagId.getIssueId());
      problem.setResolutionQty(instruction.getReceivedQuantity());
      problem.setSlotId(
          receiveContainersResponseBody.getReceived().get(0).getDestinations().get(0).getSlot());

      configuredProblemService.completeProblemTag(
          instruction.getProblemTagId(), problem, httpHeaders);
    }
  }

  private void verifyCompleteUser(
      Instruction instructionFromDB, String instructionOwner, String currentUserId) {
    try {
      ReceivingUtils.verifyUser(instructionFromDB, currentUserId, RequestType.COMPLETE);
    } catch (ReceivingException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INSTRUCTION_MULTI_USER_ERROR_MESSAGE,
          e.getMessage(),
          new Object[] {instructionOwner});
    }
  }

  private void validateInstructionStatus(Instruction instructionFromDB) {
    try {
      validateInstructionCompleted(instructionFromDB);
    } catch (ReceivingException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INSTRUCTION_COMPLETE_ALREADY,
          e.getMessage(),
          instructionFromDB.getCompleteUserId());
    }
  }

  private void publishSerializedData(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), ENABLE_EPCIS_SERVICES_FEATURE_FLAG)) {
      epcisService.publishSerializedData(
          instruction, deliveryDocumentLine, completeInstructionRequest, httpHeaders);
    }
  }

  private Map<String, Object> getOldCtrlLabel(PrintLabelData containerLabel) {
    Map<String, Object> ctrLabel = new HashMap<>();
    List<Map<String, Object>> printLabelRequestMapList =
        transformToPrintLabelRequestMap(containerLabel.getPrintRequests());
    if (!CollectionUtils.isEmpty(printLabelRequestMapList)) {
      Map<String, Object> printLabelRequestMap = printLabelRequestMapList.get(0);
      ctrLabel.put("labelData", printLabelRequestMap.get("data"));
      ctrLabel.put("formatId", printLabelRequestMap.get("formatName"));
      ctrLabel.put("formatID", printLabelRequestMap.get("formatName"));
      ctrLabel.put("ttlInHours", printLabelRequestMap.get("ttlInHours"));
      ctrLabel.put("labelIdentifier", printLabelRequestMap.get("labelIdentifier"));
      ctrLabel.put("clientId", containerLabel.getClientId());
      ctrLabel.put("clientID", containerLabel.getClientId());
    }
    return ctrLabel;
  }

  private List<Map<String, Object>> transformToPrintLabelRequestMap(
      List<PrintLabelRequest> printRequests) {

    return gson.fromJson(gson.toJson(printRequests), List.class);
  }

  private Map<String, Object> getNewCtrLabel(
      PrintLabelData containerLabel, HttpHeaders httpHeaders) {
    Map<String, String> headers = new HashMap<>();
    headers.put(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    headers.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    headers.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    Map<String, Object> ctrLabel = new HashMap<>();
    ctrLabel.put("clientId", containerLabel.getClientId());
    ctrLabel.put("headers", headers);
    ctrLabel.put(
        "printRequests", transformToPrintLabelRequestMap(containerLabel.getPrintRequests()));

    return ctrLabel;
  }

  private ReceiveContainersResponseBody getLabelFromRDS(
      Instruction instruction, CompleteInstructionRequest slotDetails, HttpHeaders httpHeaders) {
    return nimRdsServiceImpl.acquireSlot(instruction, slotDetails, httpHeaders);
  }

  @Override
  public List<DeliveryDocument> fetchDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    List<DeliveryDocument> responseDeliveryDocuments = null;
    String sscc = instructionRequest.getSscc();
    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    if (StringUtils.isNotBlank(sscc)) {
      return rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
          instructionRequest.getDeliveryNumber(), sscc, httpHeaders);
    } else if (!CollectionUtils.isEmpty(scannedDataMap)) {
      ScannedData barcodeScanScannedData = scannedDataMap.get(RxConstants.BARCODE_SCAN);
      if (Objects.nonNull(barcodeScanScannedData)
          && StringUtils.equals(
              barcodeScanScannedData.getValue(), instructionRequest.getUpcNumber())) {
        long deliveryNumber = Long.parseLong(instructionRequest.getDeliveryNumber());
        String deliveryDocumentResponseByUpc =
            rxDeliveryService.findDeliveryDocument(
                deliveryNumber, instructionRequest.getUpcNumber(), httpHeaders);
        responseDeliveryDocuments =
            Arrays.asList(gson.fromJson(deliveryDocumentResponseByUpc, DeliveryDocument[].class));
      } else {
        RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
        return rxDeliveryService.findDeliveryDocumentByGtinAndLotNumberWithShipmentLinking(
            instructionRequest.getDeliveryNumber(), scannedDataMap, httpHeaders);
      }
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_CREATE_INSTRUCTION_REQUEST,
          RxConstants.INVALID_CREATE_INSTRUCTION_REQUEST);
    }
    validateDocument(responseDeliveryDocuments);
    return responseDeliveryDocuments;
  }

  /**
   * This method is responsible for canceling an instruction
   *
   * @param instructionId
   * @param httpHeaders
   * @return Instruction
   * @throws ReceivingException
   */
  @Override
  public InstructionSummary cancelInstruction(Long instructionId, HttpHeaders httpHeaders)
      throws ReceivingException {
    Instruction instruction = instructionPersisterService.getInstructionById(instructionId);
    try {
      if (Objects.nonNull(instruction.getCompleteTs())) {
        LOG.error("Instruction: {} is already complete", instruction.getId());
        throw new ReceivingBadDataException(
            ExceptionCodes.INSTRUCITON_IS_ALREADY_COMPLETED,
            ReceivingException.INSTRUCTION_IS_ALREADY_COMPLETED);
      }
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

      int backOutQuantity = instruction.getReceivedQuantity();

      ReceivingUtils.verifyUser(instruction, userId, RequestType.CANCEL);
      // Complete instruction with received quantity as ZERO
      instruction.setReceivedQuantity(0);
      instruction.setCompleteUserId(userId);
      instruction.setCompleteTs(new Date());

      // Publish the receipt
      if (instruction.getContainer() != null) {

        List<String> trackingIds = new ArrayList<>();
        List<Container> containersByInstruction =
            containerService.getContainerByInstruction(instructionId);
        Optional<Container> parentContainerOptional =
            containersByInstruction
                .stream()
                .filter(container -> Objects.isNull(container.getParentTrackingId()))
                .findFirst();
        int backoutQtyInEa = 0;
        if (parentContainerOptional.isPresent()) {
          Container parentContainer = parentContainerOptional.get();
          ContainerItem parentContainerItems = parentContainer.getContainerItems().get(0);
          backoutQtyInEa = parentContainerItems.getQuantity();
          backOutQuantity =
              ReceivingUtils.conversionToVendorPackRoundUp(
                  parentContainerItems.getQuantity(),
                  parentContainerItems.getQuantityUOM(),
                  parentContainerItems.getVnpkQty(),
                  parentContainerItems.getWhpkQty());
        }

        if (!CollectionUtils.isEmpty(containersByInstruction)) {
          for (Container container : containersByInstruction) {
            trackingIds.add(container.getTrackingId());
          }
        }

        Receipt cancelledReceipt =
            rxReceiptsBuilder.buildReceipt(instruction, userId, backOutQuantity, backoutQtyInEa);
        // Delete all the persisted containers, receipts & instructions
        rxInstructionHelperService.rollbackContainers(trackingIds, cancelledReceipt, instruction);
        rxCancelInstructionReceiptsPublisher.publishReceipt(instruction, httpHeaders);
      }
      return InstructionUtils.convertToInstructionSummary(instruction);

    } catch (ReceivingBadDataException receivingBadDataException) {
      LOG.error(receivingBadDataException.getDescription(), receivingBadDataException);
      throw receivingBadDataException;
    } catch (ReceivingException receivingException) {
      if (ReceivingException.MULTI_USER_ERROR_CODE.equals(
          receivingException.getErrorResponse().getErrorCode())) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INSTRUCTION_MULTI_USER_ERROR_MESSAGE,
            receivingException.getMessage(),
            new Object[] {ReceivingUtils.getInstructionOwner(instruction)});
      } else {
        throw RxUtils.convertToReceivingBadDataException(receivingException);
      }
    } catch (Exception exception) {
      LOG.error("{}", ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE, exception);
      throw new ReceivingBadDataException(
          ExceptionCodes.CANCEL_PALLET_ERROR, ReceivingException.CANCEL_INSTRUCTION_ERROR_MSG);
    }
  }

  private boolean isSSCCScanRequest(InstructionRequest instructionRequest) {
    return StringUtils.isNotBlank(instructionRequest.getSscc());
  }

  private boolean isUpcScanRequest(InstructionRequest instructionRequest) {
    return StringUtils.isNotBlank(instructionRequest.getUpcNumber());
  }

  public Instruction createInstructionForUpcDscsaExempted(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Instruction instruction =
        instructionPersisterService.fetchExistingInstructionIfexists(instructionRequest);
    if (Objects.nonNull(instruction)) {
      return instruction;
    }

    if (StringUtils.isBlank(instructionRequest.getProblemTagId())) {
      instruction =
          rxInstructionPersisterService
              .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
                  instructionRequest, userId);
    } else {
      instruction =
          instructionPersisterService
              .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId(
                  instructionRequest, userId, instructionRequest.getProblemTagId());
    }
    if (Objects.nonNull(instruction)) {
      instructionRequest.setDeliveryDocuments(
          Arrays.asList(gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class)));
      return instruction;
    }

    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    Pair<DeliveryDocument, Long> autoSelectedDocument =
        instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            deliveryDocuments, 1, EMPTY_STRING);
    if (Objects.isNull(autoSelectedDocument)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.AUTO_SELECT_PO_NO_OPEN_QTY, RxConstants.AUTO_SELECT_PO_NO_OPEN_QTY);
    }

    DeliveryDocument deliveryDocument = autoSelectedDocument.getKey();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    String deliveryNumber = instructionRequest.getDeliveryNumber();
    // Temporary patch till a permanent fix made on the client side
    if (deliveryDocument.getDeliveryNumber() == 0) {
      deliveryDocument.setDeliveryNumber(Long.parseLong(deliveryNumber));
    }

    boolean isItemDscsaExempted =
        RxUtils.isDscsaExemptionIndEnabled(
            deliveryDocumentLine,
            configUtils.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG));
    if (isItemDscsaExempted
        || isGrandFathered(instructionRequest)
        || RxUtils.isTransferredPo(deliveryDocument.getPoTypeCode())
        || isRepackagedVendor(deliveryDocument)) {

      final String purchaseReferenceNumber = deliveryDocument.getPurchaseReferenceNumber();
      final int purchaseReferenceLineNumber = deliveryDocumentLine.getPurchaseReferenceLineNumber();

      Pair<Integer, Long> receivedQtyDetails =
          instructionHelperService.getReceivedQtyDetailsAndValidate(
              instructionRequest.getProblemTagId(), deliveryDocument, deliveryNumber, false, false);

      int maxReceiveQty = receivedQtyDetails.getKey();
      long totalReceivedQty = receivedQtyDetails.getValue();

      deliveryDocumentLine.setOpenQty(
          deliveryDocumentLine.getTotalOrderQty() - (int) totalReceivedQty);

      isNewInstructionCanBeCreated(
          purchaseReferenceNumber,
          purchaseReferenceLineNumber,
          maxReceiveQty,
          totalReceivedQty,
          StringUtils.isNotBlank(instructionRequest.getProblemTagId()),
          RxUtils.isSplitPalletInstructionRequest(instructionRequest),
          userId);

      instruction =
          InstructionUtils.mapDeliveryDocumentToInstruction(
              deliveryDocument,
              InstructionUtils.mapHttpHeaderToInstruction(
                  httpHeaders, InstructionUtils.createInstruction(instructionRequest)));
      int calculatedQtyBasedOnGdmQty =
          (deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit())
              - (int) totalReceivedQty;

      LinkedTreeMap<String, Object> moveTreeMap =
          moveDetailsForInstruction(instructionRequest, deliveryDocument, httpHeaders);

      instruction.setMove(moveTreeMap);

      instruction.setProjectedReceiveQty(calculatedQtyBasedOnGdmQty);

      instruction.setPrintChildContainerLabels(false);
      instruction.setInstructionMsg(
          RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionMsg());
      instruction.setInstructionCode(
          RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType());
      instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
      instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
      instruction.setProviderId("RxSSTK");
      instruction.setActivityName("SSTK");
      instruction.setMove(moveTreeMap);

      if (Objects.nonNull(instructionRequest.getProblemTagId())) {
        instruction.setProblemTagId(instructionRequest.getProblemTagId());
      }

      if (StringUtils.isNotEmpty(instructionRequest.getUpcNumber())) {
        instruction.setGtin(instructionRequest.getUpcNumber());
      }
      populateInstructionSetId(instructionRequest, instruction);
      instruction = instructionPersisterService.saveInstruction(instruction);
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.SERIALIZED_PRODUCT_UPC_NOT_SUPPORTED,
          RxConstants.SERIALIZED_PRODUCT_UPC_NOT_SUPPORTED);
    }
    return instruction;
  }

  private boolean isGrandFathered(InstructionRequest instructionRequest) {
    if (StringUtils.isNotEmpty(instructionRequest.getProblemTagId())
        && Objects.nonNull(instructionRequest.getAdditionalParams())) {
      String resolutionType =
          (String)
              instructionRequest
                  .getAdditionalParams()
                  .get(ReceivingConstants.PROBLEM_RESOLUTION_KEY);
      return ReceivingConstants.PROBLEM_RESOLUTION_SUBTYPE_GRANDFATHERED.equals(resolutionType);
    }
    return false;
  }

  private Receipt createReceipts(
      Instruction instruction, String userId, int receivedQuantity, int containerQtyInEa) {

    String ssccNumber = RxUtils.getSSCCFromInstruction(instruction);
    DeliveryDocumentLine deliveryDocumentLine = RxUtils.getDeliveryDocumentLine(instruction);

    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(instruction.getDeliveryNumber());
    receipt.setDoorNumber(
        instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString());
    receipt.setPurchaseReferenceNumber(instruction.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(instruction.getPurchaseReferenceLineNumber());
    receipt.setSsccNumber(ssccNumber);
    int backOutQuantity =
        ReceivingUtils.conversionToVendorPackRoundUp(
                receivedQuantity,
                instruction.getReceivedQuantityUOM(),
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack())
            * -1;
    receipt.setQuantity(backOutQuantity);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setVnpkQty(deliveryDocumentLine.getVendorPack());
    receipt.setProblemId(instruction.getProblemTagId());
    receipt.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    int receivedQtyInEA = 0;
    if (instruction
        .getInstructionCode()
        .equalsIgnoreCase(
            RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType())) {
      receivedQtyInEA =
          ReceivingUtils.conversionToEaches(
              receivedQuantity,
              instruction.getReceivedQuantityUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack());
    } else {
      receivedQtyInEA = containerQtyInEa > 0 ? containerQtyInEa : findEachQtySummary(instruction);
    }
    int backOutQuantityInEA = receivedQtyInEA * -1;
    receipt.setEachQty(backOutQuantityInEA);
    receipt.setCreateUserId(userId);

    return receipt;
  }

  private int findEachQtySummary(Instruction instruction) {
    int receivedQtyInEA = 0;
    String trackingId = instruction.getContainer().getTrackingId();
    List<ContainerDetails> childContainers = instruction.getChildContainers();
    if (!CollectionUtils.isEmpty(childContainers)) {
      for (ContainerDetails containerDetails : childContainers) {
        if (trackingId.equals(containerDetails.getParentTrackingId())) {
          receivedQtyInEA += containerDetails.getContents().get(0).getQty();
        }
      }
    }
    return receivedQtyInEA;
  }

  /**
   * This method fetches instruction by instruction id. If the retrieved instruction is already
   * completed or instruction is not found then it will throw an exception with appropriate error
   * message. If instruction is not complete then it will update gtin in instruction and add
   * catalogGtin if it does not exists in deliveryDocumentLines and ManufactureDetails object which
   * is available in instruction's deliveryDocument.
   *
   * @param id
   * @param patchInstructionRequest
   * @param httpHeaders
   * @return Instruction
   * @throws ReceivingBadDataException
   */
  public Instruction patchInstruction(
      Long id, PatchInstructionRequest patchInstructionRequest, HttpHeaders httpHeaders)
      throws ReceivingBadDataException {
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    String gtin = patchInstructionRequest.getUpcNumber();
    Instruction updatedInstruction = new Instruction();
    try {
      Instruction instructionResponse = instructionPersisterService.getInstructionById(id);
      if (Objects.nonNull(instructionResponse)) {
        if (!Objects.isNull(instructionResponse.getCompleteTs())) {
          LOG.error("Instruction: {} is already completed", id);
          throw new ReceivingBadDataException(
              ExceptionCodes.INSTRUCTION_COMPLETE_ALREADY,
              ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED);
        }
        DeliveryDocument deliveryDocument =
            gson.fromJson(instructionResponse.getDeliveryDocument(), DeliveryDocument.class);
        if (Objects.nonNull(deliveryDocument)) {
          DeliveryDocumentLine documentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
          documentLine.setCatalogGTIN(
              StringUtils.isNotEmpty(documentLine.getCatalogGTIN())
                  ? documentLine.getCatalogGTIN()
                  : gtin);
          List<GtinHierarchy> gtinHierarchies = documentLine.getGtinHierarchy();
          if (CollectionUtils.isEmpty(gtinHierarchies)) {
            gtinHierarchies = new ArrayList<>();
          }
          Optional<GtinHierarchy> catalogGtin =
              gtinHierarchies
                  .stream()
                  .filter(x -> x.getType().equalsIgnoreCase("catalogGTIN"))
                  .findFirst();
          if (catalogGtin.isPresent()) {
            catalogGtin.get().setGtin(gtin);
          } else {
            gtinHierarchies.add(new GtinHierarchy(gtin, ReceivingConstants.ITEM_CATALOG_GTIN));
          }
          List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
          deliveryDocumentLines.add(documentLine);
          deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
          instructionResponse.setDeliveryDocument(gson.toJson(deliveryDocument));
          instructionResponse.setGtin(gtin);
          instructionResponse.setLastChangeUserId(userId);
          updatedInstruction = instructionPersisterService.saveInstruction(instructionResponse);
        }
      }
    } catch (Exception error) {
      LOG.error("Error in updating the gtin:{} in instruction id:{}", gtin, id);
      throw new ReceivingBadDataException(
          ExceptionCodes.ERROR_IN_PATCHING_INSTRUCTION,
          String.format(RxConstants.ERROR_IN_PATCHING_INSTRUCTION, id.toString()),
          id.toString());
    }
    return updatedInstruction;
  }

  private LinkedTreeMap<String, Object> moveDetailsForInstruction(
      InstructionRequest instructionRequest,
      DeliveryDocument deliveryDocument,
      HttpHeaders httpHeaders) {

    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    Boolean isRxSmartSlottingEnabled =
        configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RxConstants.SMART_SLOTING_RX_FEATURE_FLAG);

    if (!StringUtils.isEmpty(instructionRequest.getProblemTagId())) {
      moveTreeMap.put(ReceivingConstants.MOVE_FROM_LOCATION, appConfig.getRxProblemDefaultDoor());
    } else {
      moveTreeMap.put(ReceivingConstants.MOVE_FROM_LOCATION, instructionRequest.getDoorNumber());
    }
    moveTreeMap.put(
        "correlationID", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_ON, new Date());
    moveTreeMap.put(
        ReceivingConstants.MOVE_LAST_CHANGED_BY,
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    if (isRxSmartSlottingEnabled) {
      // Add Prime Slot info
      SlottingPalletResponse slottingRxPalletResponse = null;
      try {
        slottingRxPalletResponse =
            rxSlottingServiceImpl.acquireSlot(
                instructionRequest.getMessageId(),
                Arrays.asList(deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr()),
                0,
                ReceivingConstants.SLOTTING_FIND_PRIME_SLOT,
                httpHeaders);

        if (Objects.nonNull(slottingRxPalletResponse)
            && !CollectionUtils.isEmpty(slottingRxPalletResponse.getLocations())) {
          moveTreeMap.put(
              ReceivingConstants.MOVE_PRIME_LOCATION,
              slottingRxPalletResponse.getLocations().get(0).getLocation());
          moveTreeMap.put(
              ReceivingConstants.MOVE_PRIME_LOCATION_SIZE,
              slottingRxPalletResponse.getLocations().get(0).getLocationSize());
        }
      } catch (ReceivingBadDataException e) {
        // Ignore the exception - Chances of no prime slot for Item
        LOG.error(
            "Prime Slot not found for the item {}, "
                + "Got Error from Slotting service errorCode {} and description {}",
            deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr(),
            e.getErrorCode(),
            e.getDescription());

        throw e;
      }
    }
    return moveTreeMap;
  }

  private boolean isRepackagedVendor(DeliveryDocument deliveryDocument) {

    String[] repackageVendors = StringUtils.split(appConfig.getRepackageVendors(), ",");
    return Arrays.asList(repackageVendors)
        .contains(String.valueOf(deliveryDocument.getVendorNumber()));
  }

  @Override
  public Instruction getInstructionById(Long instructionId) throws ReceivingException {
    return RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(
        instructionPersisterService.getInstructionById(instructionId));
  }
}
