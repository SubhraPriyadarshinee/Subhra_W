package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isSinglePO;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isSinglePoLine;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ENABLE_EPCIS_SERVICES_FEATURE_FLAG;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.INSTRUCTION_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.springframework.http.HttpStatus.OK;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.*;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.GtinHierarchy;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PackItemResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.UnitRequestMap;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.UnitSerialRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rx.builders.RxContainerLabelBuilder;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.common.RxLpnUtils;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.publisher.RxCancelInstructionReceiptPublisher;
import com.walmart.move.nim.receiving.rx.validators.RxInstructionValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Resource;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

public class RxInstructionService extends InstructionService {

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
  @Autowired private RxInstructionValidator rxInstructionValidator;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private ShipmentSelectorService shipmentSelector;
  @Autowired private RxFixitProblemService rxFixitProblemService;
  private final JsonParser parser = new JsonParser();
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;
  @Autowired private ReceiptService receiptService;

  @Autowired private RestUtils restUtils;

  @Autowired private RxLpnUtils rxLpnUtils;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  protected DeliveryService deliveryService;

  @Resource RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;

  @Override
  public InstructionResponse serveInstructionRequest(
      String instructionRequestString, HttpHeaders httpHeaders) throws ReceivingException {

    if (!appConfig.isOverrideServeInstrMethod()) {
      return super.serveInstructionRequest(instructionRequestString, httpHeaders);
    }
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    try {
      InstructionRequest instructionRequest =
          gson.fromJson(instructionRequestString, InstructionRequest.class);

      Instruction instruction = null;
      if (StringUtils.isEmpty(instructionRequest.getUpcNumber())
          && StringUtils.isEmpty(instructionRequest.getSscc())) {
        if (StringUtils.isEmpty(instructionRequest.getUpcNumber())) {
          throw new ReceivingBadDataException(
              ExceptionCodes.UPC_NOT_AVAILABLE, RxConstants.UPC_NOT_AVAILABLE);
        } else if (StringUtils.isEmpty(instructionRequest.getSscc())) {
          throw new ReceivingBadDataException(
              ExceptionCodes.SSCC_NOT_AVAILABLE, RxConstants.SSCC_NOT_AVAILABLE);
        }
      }

      boolean isEpcisV4FeatureEnabled =
          tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED,
              false);

      if (StringUtils.isNotBlank(instructionRequest.getProblemTagId()) && isEpcisV4FeatureEnabled) {
        Optional<FitProblemTagResponse> fitProblemTagResponseOptional =
            rxInstructionHelperService.getFitProblemTagResponse(
                instructionRequest.getProblemTagId());
        if (fitProblemTagResponseOptional.isPresent()) {
          FitProblemTagResponse fitProblemTagResponse = fitProblemTagResponseOptional.get();
          instructionRequest.setFitProblemTagResponse(fitProblemTagResponse);
          if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                  TenantContext.getFacilityNum().toString(),
                  RxConstants.IS_EPCIS_PROBLEM_FALLBACK_TO_ASN,
                  true)
              || RxUtils.isASNReceivingOverrideEligible(fitProblemTagResponse)) {
            // set asn receiving override flag to true so that V3 GDM api will be called
            httpHeaders.set(
                ReceivingConstants.Rx_ASN_RCV_OVER_RIDE_KEY,
                ReceivingConstants.Rx_ASN_RCV_OVER_RIDE_VALUE);
          }
        }
      }

      List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
      // if client sends delivery documents, use that
      if (CollectionUtils.isEmpty(deliveryDocuments)) {

        // Get DeliveryDocument from GDM
        deliveryDocuments = fetchDeliveryDocument(instructionRequest, httpHeaders);

        // enrich incoming instruction request with deliveryDocs from GDM
        instructionRequest.setDeliveryDocuments(deliveryDocuments);
      } else if (!CollectionUtils.isEmpty(deliveryDocuments)
          && isEpcisV4FeatureEnabled
          && RxUtils.isEpcisEnabledVendor(
              deliveryDocuments.get(0).getDeliveryDocumentLines().get(0))
          && !ReceivingUtils.isAsnReceivingOverrideEligible(httpHeaders)
          && !deliveryDocuments
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo()
              .getAutoSwitchEpcisToAsn()) {

        DeliveryDocumentLine deliveryDocumentLineFromClient =
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
        String poFromClient = deliveryDocuments.get(0).getPurchaseReferenceNumber();
        String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
        Instruction instructionFromDB;
        DeliveryDocument deliveryDocument = null;
        if (null != instructionRequest.getSscc()
            && !ApplicationIdentifier.SSCC
                .getApplicationIdentifier()
                .equals(instructionRequest.getSscc())) {
          instructionFromDB =
              rxInstructionHelperService.fetchMultiSkuInstrDeliveryDocument(
                  instructionRequest.getDeliveryNumber(),
                  instructionRequest.getSscc(),
                  poFromClient,
                  userId);
        } else {
          instructionFromDB =
              rxInstructionHelperService.fetchMultiSkuInstrDeliveryDocumentByDelivery(
                  instructionRequest.getDeliveryNumber(), poFromClient, userId);
        }
        if (null != instructionFromDB.getInstructionCode()
            && instructionFromDB
                .getInstructionCode()
                .equalsIgnoreCase(
                    RxInstructionType.SERIALIZED_MULTISKU_PALLET.getInstructionType())) {
          deliveryDocument =
              gson.fromJson(instructionFromDB.getDeliveryDocument(), DeliveryDocument.class);

          Iterator<DeliveryDocumentLine> deliveryDocumentLineIterator =
              deliveryDocument.getDeliveryDocumentLines().iterator();
          while (deliveryDocumentLineIterator.hasNext()) {
            DeliveryDocumentLine deliveryDocumentLine = deliveryDocumentLineIterator.next();
            if (deliveryDocumentLineFromClient
                    .getPurchaseReferenceNumber()
                    .equals(deliveryDocumentLine.getPurchaseReferenceNumber())
                && deliveryDocumentLineFromClient.getPurchaseReferenceLineNumber()
                    == deliveryDocumentLine.getPurchaseReferenceLineNumber()) {
              deliveryDocumentLine.setPacks(
                  deliveryDocumentLine.getAdditionalInfo().getPacksOfMultiSkuPallet());
              deliveryDocumentLine.getAdditionalInfo().setPacksOfMultiSkuPallet(null);
              deliveryDocumentLine.getAdditionalInfo().setPartOfMultiSkuPallet(true);
            } else {
              deliveryDocumentLineIterator.remove();
            }
          }
          deliveryDocuments = Collections.singletonList(deliveryDocument);
          instructionRequest.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
          List<ScannedData> filteredScannedData = new ArrayList();
          List<ScannedData> insScannedDataList = instructionRequest.getScannedDataList();
          insScannedDataList
              .stream()
              .filter(scannedData -> !scannedData.getKey().equals("sscc"))
              .forEach(scannedData -> filteredScannedData.add(scannedData));
          instructionRequest.setScannedDataList(filteredScannedData);
          instructionRequest.setSscc(null);

          List<ScannedData> scannedDataList = instructionRequest.getScannedDataList();
          Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(scannedDataList);
          verifyScanned2DWithSerializedInfoForMultiSku( deliveryDocuments.get(0).getDeliveryDocumentLines().get(0), scannedDataMap,
                  ReceivingConstants.Uom.CA);
        }
      }

      filterInvalidPOs(deliveryDocuments);
      // update delivery docs
      deliveryDocuments = deliveryDocumentHelper.updateDeliveryDocuments(deliveryDocuments);
      rxInstructionHelperService.updateDeliveryDocumentLineAdditionalInfo(deliveryDocuments);
      List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
      deliveryDocuments.forEach(
          deliveryDocument ->
              deliveryDocument
                  .getDeliveryDocumentLines()
                  .forEach(documentLine -> deliveryDocumentLines.add(documentLine)));

      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED,
          false)) {
        Boolean multiDeliveryDocumentLine = !isSingleSku(deliveryDocumentLines);
        deliveryDocuments.forEach(
            deliveryDocument -> {
              deliveryDocument
                  .getDeliveryDocumentLines()
                  .forEach(
                      deliveryDocumentLine -> {
                        mapSerializedPackData(
                            deliveryDocumentLine,
                            instructionRequest,
                            httpHeaders,
                            multiDeliveryDocumentLine);
                      });
            });
        if (deliveryDocuments
                .stream()
                .map(DeliveryDocument::getPurchaseReferenceNumber)
                .distinct()
                .count()
            > 1) { // if more than 1 distinct POs
          deliveryDocuments.forEach(
              deliveryDocument ->
                  deliveryDocument
                      .getDeliveryDocumentLines()
                      .forEach(
                          deliveryDocumentLine ->
                              deliveryDocumentLine.getAdditionalInfo().setMultiPOPallet(true)));
        }
      }
      if (isSinglePO(deliveryDocuments)) {
        validateDocumentLineExists(deliveryDocuments);

        if (isSinglePoLine(deliveryDocuments.get(0))) {

          checkIfLineIsRejected(deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));

          instruction = createInstructionForUpcReceiving(instructionRequest, httpHeaders);
          instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
          instructionResponse.setInstruction(instruction);

        } else {
          this.isSingleItemMultiPoPoLine(
              deliveryDocuments, instructionRequest, instructionResponse, httpHeaders);
        }

      } else {
        this.isSingleItemMultiPoPoLine(
            deliveryDocuments, instructionRequest, instructionResponse, httpHeaders);
      }
    } catch (ReceivingException e) {
      throw RxUtils.convertToReceivingBadDataException(e);
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
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    String deliveryNumber = instructionRequest.getDeliveryNumber();

    Instruction existingInstruction = null;
    Optional<RxReceivingType> receivingTypeOptional =
        RxReceivingType.fromString(instructionRequest.getReceivingType());
    RxReceivingType receivingType = null;
    if (receivingTypeOptional.isPresent()) {
      receivingType = receivingTypeOptional.get();
    }

    if (RxReceivingType.TWOD_BARCODE_PARTIALS == receivingType) {
      existingInstruction =
          rxInstructionHelperService.checkIfInstructionExistsBeforeAllowingPartialReceiving(
              deliveryNumber,
              scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue(),
              userId);
    }

    // this fetch existing instruction can be done with out even calling GDM
    // Push this to serveInstructionRequest method
    if (Objects.isNull(existingInstruction)) {
      if (isSSCCScanRequest(instructionRequest)) {
        validateScannedData(scannedDataMap);
        existingInstruction =
            StringUtils.isNotBlank(instructionRequest.getProblemTagId())
                ? instructionPersisterService
                    .fetchInstructionByDeliveryNumberSSCCAndUserIdAndProblemTagId(
                        instructionRequest, userId)
                : rxInstructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(
                    instructionRequest, userId);
        if (Objects.nonNull(existingInstruction)) {
          instructionRequest.setDeliveryDocuments(
              Arrays.asList(
                  gson.fromJson(
                      existingInstruction.getDeliveryDocument(), DeliveryDocument.class)));
        }
      } else if (is2dBarcodeRequest) {
        existingInstruction =
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
        if (Objects.nonNull(existingInstruction)) {
          instructionRequest.setDeliveryDocuments(
              Arrays.asList(
                  gson.fromJson(
                      existingInstruction.getDeliveryDocument(), DeliveryDocument.class)));
        }
      } else {
        existingInstruction =
            instructionPersisterService.fetchExistingInstructionIfexists(instructionRequest);
        instructionRequest.setDeliveryDocuments(
            Arrays.asList(
                gson.fromJson(existingInstruction.getDeliveryDocument(), DeliveryDocument.class)));
      }
    }

    if (Objects.nonNull(existingInstruction)) {
      return RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(existingInstruction);
    }

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

    AtomicBoolean isEpcisUnit2D = new AtomicBoolean(false);
    deliveryDocuments.forEach(
        deliveryDoc ->
            deliveryDoc
                .getDeliveryDocumentLines()
                .forEach(
                    line -> {
                      if (Objects.nonNull(line.getAdditionalInfo().getIsSerUnit2DScan())) {
                        isEpcisUnit2D.set(true);
                      }
                    }));

    if (is2dBarcodeRequest && !isEpcisUnit2D.get()) {
      filterPOsByLot(
          deliveryDocuments, scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue());
      if (CollectionUtils.isEmpty(deliveryDocuments)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.SCANNED_DETAILS_DO_NOT_MATCH, RxConstants.SCANNED_DETAILS_DO_NOT_MATCH);
      }
    }
    Pair<DeliveryDocument, Long> autoSelectedDocument =
        instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            deliveryDocuments, 1, ReceivingConstants.Uom.EACHES);

    if (Objects.isNull(autoSelectedDocument)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.AUTO_SELECT_PO_NO_OPEN_QTY, RxConstants.AUTO_SELECT_PO_NO_OPEN_QTY);
    }
    DeliveryDocument autoSelectedDeliveryDocument = autoSelectedDocument.getKey();
    DeliveryDocumentLine autoSelectedDeliveryDocumentLine =
        autoSelectedDeliveryDocument.getDeliveryDocumentLines().get(0);

    // validate unit scanned lot and expiry matches with serial info
    if (is2dBarcodeRequest && isEpcisUnit2D.get()) {
      ManufactureDetail scannedDetails =
          RxUtils.convertScannedDataToManufactureDetail(scannedDataMap);
      List<ManufactureDetail> serializedInfo =
          autoSelectedDeliveryDocumentLine.getAdditionalInfo().getSerializedInfo();
      if (Objects.nonNull(serializedInfo)) {
        long count =
            serializedInfo
                .stream()
                .filter(
                    info ->
                        info.getReportedUom().equalsIgnoreCase(Uom.EACHES)
                            && info.getLot().equalsIgnoreCase(scannedDetails.getLot())
                            && info.getExpiryDate()
                                .equalsIgnoreCase(scannedDetails.getExpiryDate()))
                .count();
        if (count == 0) {
          throw new ReceivingBadDataException(
              ExceptionCodes.INVALID_SCANNED_DATA,
              ExceptionDescriptionConstants.SCANNED_2D_NOT_MATCHING_EPCIS);
        }
      }
    }

    logMatchedGtin(autoSelectedDeliveryDocumentLine, scannedDataMap);
    String purchaseReferenceNumber = autoSelectedDeliveryDocument.getPurchaseReferenceNumber();
    int purchaseReferenceLineNumber =
        autoSelectedDeliveryDocumentLine.getPurchaseReferenceLineNumber();

    // Temporary patch till a permanent fix made on the client side
    if (autoSelectedDeliveryDocument.getDeliveryNumber() == 0) {
      autoSelectedDeliveryDocument.setDeliveryNumber(Long.parseLong(deliveryNumber));
    }

    if (!isEpcisUnit2D.get()) {
      rxInstructionHelperService.verify2DBarcodeLotWithShipmentLot(
          is2dBarcodeRequest,
          autoSelectedDeliveryDocument,
          autoSelectedDeliveryDocumentLine,
          scannedDataMap);
    }
    // push this to calling method to serveInstructionRequest
    // if any instruction already exists with message id, return back that immediately

    FitProblemTagResponse fitProblemTagResponse = instructionRequest.getFitProblemTagResponse();
    if (rxManagedConfig.isProblemItemCheckEnabled()) { // Feature Flag
      if (StringUtils.isNotBlank(instructionRequest.getProblemTagId())) {
        Optional<FitProblemTagResponse> fitProblemTagResponseOptional;
        if (Objects.isNull(fitProblemTagResponse)) {
          fitProblemTagResponseOptional =
              rxInstructionHelperService.getFitProblemTagResponse(
                  instructionRequest.getProblemTagId());
          if (fitProblemTagResponseOptional.isPresent()) {
            fitProblemTagResponse = fitProblemTagResponseOptional.get();
          }
        }
        if (Objects.nonNull(fitProblemTagResponse)) {
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
    checkIfD40Item(autoSelectedDeliveryDocument, autoSelectedDeliveryDocumentLine);
    Map<String, Long> receivedQtyByShipmentNumber =
        shipmentSelector.receivedQuantityByShipmentPoPoLine(autoSelectedDeliveryDocumentLine);
    ShipmentDetails shipmentDetails =
        shipmentSelector.autoSelectShipment(
            autoSelectedDeliveryDocumentLine, receivedQtyByShipmentNumber);
    int selectedShipmentReceivedQty =
        receivedQtyByShipmentNumber
            .getOrDefault(shipmentDetails.getInboundShipmentDocId(), 0l)
            .intValue();

    epcisService.verifySerializedData(
        scannedDataMap, shipmentDetails, autoSelectedDeliveryDocumentLine, httpHeaders);

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(RX_XBLOCK_FEATURE_FLAG)
        && RxUtils.isItemXBlocked(autoSelectedDeliveryDocumentLine)) {
      LOG.error(
          "Given item:{} and handlingCode:{} matches with X-blocked item criteria.",
          autoSelectedDeliveryDocumentLine.getItemNbr(),
          autoSelectedDeliveryDocumentLine.getAdditionalInfo().getHandlingCode());
      throw new ReceivingBadDataException(
          ExceptionCodes.CREATE_INSTRUCTION_ERROR,
          String.format(
              ReceivingConstants.X_BLOCK_ITEM_ERROR_MSG,
              autoSelectedDeliveryDocumentLine.getItemNbr()),
          String.valueOf(autoSelectedDeliveryDocumentLine.getItemNbr()));
    }

    if (RxUtils.isControlledSubstance(autoSelectedDeliveryDocumentLine)) {
      LOG.error("Given item {} belonging to delivery: {} is a controlled substance. Don't receive",
              autoSelectedDeliveryDocumentLine.getItemNbr(),
              autoSelectedDeliveryDocument.getDeliveryNumber());

      throw new ReceivingBadDataException(
              ExceptionCodes.CREATE_INSTRUCTION_CONTROLLED_SUBSTANCE_ERROR,
              String.format(
                      ReceivingConstants.CONTROLLED_SUBSTANCE_ERROR,
                      autoSelectedDeliveryDocumentLine.getItemNbr()));

    }

    Pair<Integer, Long> receivedQtyDetails =
        instructionHelperService.getReceivedQtyDetailsInEaAndValidate(
            instructionRequest.getProblemTagId(), autoSelectedDeliveryDocument, deliveryNumber);

    long totalReceivedQtyInEaches = receivedQtyDetails.getValue();

    boolean isEpcisEnabledVendor =
        RxUtils.isEpcisEnabledVendor(autoSelectedDeliveryDocumentLine)
            && !ReceivingUtils.isAsnReceivingOverrideEligible(httpHeaders)
            && !autoSelectedDeliveryDocumentLine.getAdditionalInfo().getAutoSwitchEpcisToAsn()
            && (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED,
                false));
    Pair<Boolean, Integer> partialCaseDetails =
        RxUtils.isPartialCaseAndReportedQty(
            autoSelectedDeliveryDocumentLine.getManufactureDetails(),
            autoSelectedDeliveryDocumentLine.getVendorPack(),
            autoSelectedDeliveryDocumentLine.getWarehousePack());
    boolean isPartialCase = partialCaseDetails.getKey();
    if (isPartialCase
        && isEpcisEnabledVendor
        && Objects.isNull(autoSelectedDeliveryDocumentLine.getAdditionalInfo().getIsSerUnit2DScan())
        && is2dBarcodeRequest) {
      throw new ReceivingBadDataException(
          ExceptionCodes.CASE_2D_NOT_ALLOWED_PARTIAL_FLOW,
          ExceptionDescriptionConstants.CASE_2D_NOT_ALLOWED_PARTIAL_CASE);
    }
    if (isEpcisEnabledVendor
        && CollectionUtils.isEmpty(
            autoSelectedDeliveryDocumentLine.getAdditionalInfo().getSerializedInfo())
        && !autoSelectedDeliveryDocumentLine.getAdditionalInfo().getAutoSwitchEpcisToAsn()
        && !RxUtils.isASNReceivingOverrideEligible(fitProblemTagResponse)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.CREATE_INSTRUCTION_ERROR_NO_SERIALIZE_INFO,
          RxConstants.EPCIS_VALIDATION_UNAVAILABLE);
    }

    if (isEpcisEnabledVendor
        && RxUtils.isSplitPalletInstructionRequest(instructionRequest)
        && tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RxConstants.IS_BLOCK_EPCIS_SPLIT_PALLET,
            true)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.SPLIT_PALLET_NOT_ALLOWED_EPCIS_VENDOR_409,
          ExceptionDescriptionConstants.SPLIT_PALLET_NOT_ALLOWED_EPCIS_VENDOR);
    }

    if (isEpcisEnabledVendor
        && is2dBarcodeRequest
        && Objects.isNull(autoSelectedDeliveryDocumentLine.getAdditionalInfo().getIsSerUnit2DScan())
        && RxReceivingType.TWOD_BARCODE_PARTIALS == receivingType) {
      throw new ReceivingBadDataException(
          ExceptionCodes.CASE_2D_NOT_ALLOWED_PARTIAL_FLOW,
          ExceptionDescriptionConstants.CASE_2D_NOT_ALLOWED_PARTIAL_FLOW);
    }
    int attpQtyInEaches = autoSelectedDeliveryDocumentLine.getAdditionalInfo().getAttpQtyInEaches();

    if (RxReceivingType.TWOD_BARCODE_PARTIALS == receivingType) {
      isPartialCase = true;
    }
    rxInstructionHelperService.validatePartialsInSplitPallet(instructionRequest, isPartialCase);
    boolean isEpcisFlow =
        isEpcisEnabledVendor
            && !RxUtils.isASNReceivingOverrideEligible(fitProblemTagResponse)
            && !autoSelectedDeliveryDocumentLine.getAdditionalInfo().getAutoSwitchEpcisToAsn();
    int projectedReceiveQtyInEaches =
        getProjectedReceiveQtyInEaches(
            autoSelectedDeliveryDocumentLine,
            totalReceivedQtyInEaches,
            selectedShipmentReceivedQty,
            attpQtyInEaches,
            isEpcisFlow);

    if (partialCaseDetails
        .getKey()) { // If partial case reported in ASN, allowing only to receive reported Eaches.
      projectedReceiveQtyInEaches = partialCaseDetails.getValue();
      LOG.info(
          "Delivery:{}, SSCC Number:{} is identified as Partial Case",
          deliveryNumber,
          instructionRequest.getSscc());
    } else if (isPartialCase) { // Client requested for partial or openQty has only partial left.
      projectedReceiveQtyInEaches =
          projectedReceiveQtyInEaches > autoSelectedDeliveryDocumentLine.getVendorPack()
              ? autoSelectedDeliveryDocumentLine.getVendorPack()
              : projectedReceiveQtyInEaches;
    } else {
      if (StringUtils.isBlank(instructionRequest.getProblemTagId())) { // regular receiving
        projectedReceiveQtyInEaches =
            getProjectedReceiveQtyInEaches(
                autoSelectedDeliveryDocumentLine,
                totalReceivedQtyInEaches,
                selectedShipmentReceivedQty,
                attpQtyInEaches,
                isEpcisFlow);
      } else { // Problem receiving
        if (rxManagedConfig.isProblemItemCheckEnabled()) { // Feature flag
          Resolution activeResolution = RxUtils.getActiveResolution(fitProblemTagResponse);
          if (Objects.nonNull(activeResolution)) {
            String problemUom = fitProblemTagResponse.getIssue().getUom();
            if (isEpcisUnit2D.get() && problemUom.equalsIgnoreCase(Uom.WHPK)) {
              autoSelectedDeliveryDocumentLine
                  .getAdditionalInfo()
                  .setScannedCaseAttpQty(activeResolution.getQuantity());
            }
            Integer problemQty =
                Math.min(activeResolution.getQuantity(), fitProblemTagResponse.getReportedQty());
            problemQty =
                ReceivingUtils.conversionToEaches(
                    (int) problemQty,
                    getProblemTicketUom(fitProblemTagResponse),
                    autoSelectedDeliveryDocumentLine.getVendorPack(),
                    autoSelectedDeliveryDocumentLine.getWarehousePack());

            int problemResolutionQtyInEa = (int) (problemQty - totalReceivedQtyInEaches);

            if (isEpcisUnit2D.get()
                && (problemUom.equalsIgnoreCase(Uom.CA) || problemUom.equalsIgnoreCase((Uom.VNPK)))
                && problemResolutionQtyInEa > 0) {
              problemResolutionQtyInEa =
                  ReceivingUtils.conversionToEaches(
                      1,
                      ReceivingConstants.Uom.VNPK,
                      autoSelectedDeliveryDocumentLine.getVendorPack(),
                      autoSelectedDeliveryDocumentLine.getWarehousePack());
            }
            projectedReceiveQtyInEaches =
                RxUtils.getProjectedReceivedQtyInEaForProblem(
                    autoSelectedDeliveryDocumentLine,
                    totalReceivedQtyInEaches,
                    problemResolutionQtyInEa);
            if (isEpcisEnabledVendor) {
              autoSelectedDeliveryDocumentLine.getAdditionalInfo().setPartialPallet(true);
            }
          } else {
            throw new ReceivingBadDataException(
                ExceptionCodes.PROBLEM_NOT_FOUND, ReceivingException.PROBLEM_NOT_FOUND);
          }
        } else {
          totalReceivedQtyInEaches =
              RxUtils.deriveProjectedReceiveQtyInEaches(
                  autoSelectedDeliveryDocumentLine,
                  totalReceivedQtyInEaches,
                  selectedShipmentReceivedQty);
        }
      }
    }

    int totalOrderQuantityInEaches = autoSelectedDeliveryDocumentLine.getTotalOrderQty();
    autoSelectedDeliveryDocumentLine.setOpenQty(
        totalOrderQuantityInEaches - (int) totalReceivedQtyInEaches);
    autoSelectedDeliveryDocumentLine.setQtyUOM(ReceivingConstants.Uom.EACHES);

    if (projectedReceiveQtyInEaches <= 0) {
      instructionError =
          InstructionErrorCode.getErrorValue(ReceivingException.ALLOWED_CASES_RECEIVED);

      throw new ReceivingBadDataException(
          ExceptionCodes.CREATE_INSTRUCTION_ERROR_NO_OPEN_QTY,
          RxConstants.INVALID_ALLOWED_CASES_RECEIVED);
    }

    isNewInstructionCanBeCreated(
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        projectedReceiveQtyInEaches,
        totalReceivedQtyInEaches,
        StringUtils.isNotBlank(instructionRequest.getProblemTagId()),
        RxUtils.isSplitPalletInstructionRequest(instructionRequest),
        userId);

    Instruction instruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            autoSelectedDeliveryDocument,
            InstructionUtils.mapHttpHeaderToInstruction(
                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));
    instruction.setSsccNumber(instructionRequest.getSscc());

    LinkedTreeMap<String, Object> moveTreeMap =
        moveDetailsForInstruction(instructionRequest, autoSelectedDeliveryDocument, httpHeaders);

    instruction.setMove(moveTreeMap);
    int projectedReceiveQty =
        ReceivingUtils.conversionToVendorPack(
            projectedReceiveQtyInEaches,
            ReceivingConstants.Uom.EACHES,
            autoSelectedDeliveryDocumentLine.getVendorPack(),
            autoSelectedDeliveryDocumentLine.getWarehousePack());
    int quantityToBeReceived = projectedReceiveQty;
    String quantityToBeReceivedUom = ReceivingConstants.Uom.VNPK;
    if (projectedReceiveQty < 1
        || isPartialCase
        || (isEpcisEnabledVendor
            && Objects.nonNull(
                autoSelectedDeliveryDocumentLine.getAdditionalInfo().getIsSerUnit2DScan()))) {
      // todo handle partial case when request is for pallet sscc
      isPartialCase = true;
      quantityToBeReceived =
          ReceivingUtils.conversionToWareHousePack(
              projectedReceiveQtyInEaches,
              ReceivingConstants.Uom.EACHES,
              autoSelectedDeliveryDocumentLine.getVendorPack(),
              autoSelectedDeliveryDocumentLine.getWarehousePack());
      quantityToBeReceivedUom = ReceivingConstants.Uom.WHPK;
    }

    instruction.setProjectedReceiveQty(projectedReceiveQtyInEaches);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.EACHES);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.EACHES);

    RxInstructionType rxInstructionType =
        getRxInstructionType(
            instructionRequest,
            autoSelectedDeliveryDocumentLine,
            isPartialCase,
            isEpcisEnabledVendor);
    // changed from isEpcisFlow
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
    instruction = instructionPersisterService.saveInstruction(instruction);
    instruction.setProjectedReceiveQty(quantityToBeReceived);
    instruction.setProjectedReceiveQtyUOM(quantityToBeReceivedUom);
    return instruction;
  }

  private String getProblemTicketUom(FitProblemTagResponse fitProblemTagResponse) {
    String problemUom = fitProblemTagResponse.getIssue().getUom();
    if (StringUtils.isBlank(problemUom)) {
      problemUom = ReceivingConstants.Uom.VNPK;
    }
    return problemUom;
  }

  private int getProjectedReceiveQtyInEaches(
      DeliveryDocumentLine autoSelectedDeliveryDocumentLine,
      long totalReceivedQtyInEaches,
      int selectedShipmentReceivedQty,
      int attpQtyInEaches,
      boolean isEpcisEnabledVendor) {

    if (isEpcisEnabledVendor && attpQtyInEaches > 0) {
      return RxUtils.deriveProjectedReceiveQtyInEachesForEpcisEnabledFlow(
          autoSelectedDeliveryDocumentLine,
          totalReceivedQtyInEaches,
          attpQtyInEaches);
    } else {
      return RxUtils.deriveProjectedReceiveQtyInEaches(
          autoSelectedDeliveryDocumentLine, totalReceivedQtyInEaches, selectedShipmentReceivedQty);
    }
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

  private void checkIfD40Item(
      DeliveryDocument autoSelectedDeliveryDocument,
      DeliveryDocumentLine autoSelectedDeliveryDocumentLine) {
    boolean dscsaExemptionIndEnabled =
        RxUtils.isDscsaExemptionIndEnabled(
            autoSelectedDeliveryDocumentLine,
            configUtils.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG));
    if (dscsaExemptionIndEnabled
        || RxUtils.isTransferredPo(autoSelectedDeliveryDocument.getPoTypeCode())
        || (isRepackagedVendor(autoSelectedDeliveryDocument)
            && !RxUtils.isEpcisEnabledVendor(autoSelectedDeliveryDocumentLine))) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVLID_D40_RECEIVING_FLOW,
          ReceivingException.INVLID_D40_RECEIVING_FLOW_DESC);
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

  public void filterInvalidPOs(List<DeliveryDocument> deliveryDocuments) {
    StringJoiner errorStatusStringJoiner = new StringJoiner(",");
    StringJoiner cancelledStatusStringJoiner = new StringJoiner(",");
    StringJoiner rejectedStatusStringJoiner = new StringJoiner(",");
    StringJoiner closedStatusStringJoiner = new StringJoiner(",");
    Iterator<DeliveryDocument> deliveryDocumentsIterator = deliveryDocuments.iterator();
    while (deliveryDocumentsIterator.hasNext()) {
      DeliveryDocument deliveryDocument = deliveryDocumentsIterator.next();
      if (POStatus.CNCL.toString().equals(deliveryDocument.getPurchaseReferenceStatus())) {
        cancelledStatusStringJoiner.add(
            String.format(
                RxConstants.INVALID_PO_STATUS_ERROR_FORMAT_PREFIX,
                deliveryDocument.getPurchaseReferenceNumber()));
        deliveryDocumentsIterator.remove();
      } else if (POStatus.CLOSED.toString().equals(deliveryDocument.getPurchaseReferenceStatus())) {
        closedStatusStringJoiner.add(
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
          } else if (POLineStatus.CLOSED
              .toString()
              .equals(deliveryDocumentLine.getPurchaseReferenceLineStatus())) {
            closedStatusStringJoiner.add(
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
      if (StringUtils.isNotBlank(closedStatusStringJoiner.toString())) {
        errorStatusStringJoiner.add(
            String.format(
                RxConstants.INVALID_PO_STATUS_ERROR_FORMAT,
                closedStatusStringJoiner.toString(),
                POLineStatus.CLOSED.toString()));
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
      boolean isPartialCase,
      boolean isEpcisFlow) {

    if (isPartialCase) {
      return isEpcisFlow
          ? RxInstructionType.RX_SER_BUILD_UNITS_SCAN
          : RxInstructionType.BUILD_PARTIAL_CONTAINER;
    } else if (Objects.nonNull(deliveryDocumentLine.getPalletSSCC())
        && instructionRequest.getSscc().equalsIgnoreCase(deliveryDocumentLine.getPalletSSCC())) {
      return isEpcisFlow
          ? RxInstructionType.RX_SER_BUILD_CONTAINER
          : RxInstructionType.BUILD_CONTAINER;
    } else if (StringUtils.isBlank(instructionRequest.getSscc())
        && !CollectionUtils.isEmpty(instructionRequest.getScannedDataList())) {
      Map<String, ScannedData> scannedDataMap =
          RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
      String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
      String lotNumber = scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue();
      if (StringUtils.isNotBlank(gtin) && StringUtils.isNotBlank(lotNumber)) {
        if(deliveryDocumentLine.getAdditionalInfo().isPalletFlowInMultiSku() && isEpcisFlow ){
          return  RxInstructionType.RX_SER_BUILD_CONTAINER;
        }
        return isEpcisFlow
            ? RxInstructionType.RX_SER_CNTR_GTIN_AND_LOT
            : RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT;
      }
    }
    LOG.info("Entering into default" + "" + "" + " case");
    return isEpcisFlow
        ? RxInstructionType.RX_SER_CNTR_CASE_SCAN
        : RxInstructionType.BUILDCONTAINER_CASES_SCAN;
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
    String newLabelTrackingId = null;
    boolean rollbackForException = false;
    ReceiveContainersResponseBody receiveContainersResponseBody = null;
    try {

      Boolean isGdmShipmentGetByScanV4Enabled =
          tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED,
              false);

      Boolean isDCOneAtlasEnabled =
          tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);

      Boolean enableRDSReceipt =
              tenantSpecificConfigReader.getConfiguredFeatureFlag(
                      TenantContext.getFacilityNum().toString(), IS_DC_RDS_RECEIPT_ENABLED, false);

      TenantContext.get().setCompleteInstrStart(System.currentTimeMillis());

      // Getting instruction from DB.
      Instruction instructionFromDB = instructionPersisterService.getInstructionById(instructionId);
      rxInstructionValidator.validateInstructionStatus(instructionFromDB);

      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      String instructionOwner =
          StringUtils.isNotBlank(instructionFromDB.getLastChangeUserId())
              ? instructionFromDB.getLastChangeUserId()
              : instructionFromDB.getCreateUserId();
      rxInstructionValidator.verifyCompleteUser(instructionFromDB, instructionOwner, userId);

      DeliveryDocument deliveryDocument =
          gson.fromJson(instructionFromDB.getDeliveryDocument(), DeliveryDocument.class);
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);

      if (isDCOneAtlasEnabled && !enableRDSReceipt) {
        newLabelTrackingId = rxLpnUtils.get18DigitLPNs(1, httpHeaders).get(0);
      }

      // call slotting for new flow and auto slot only
      boolean isEnableOutboxCreateMoves = tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(), ENABLE_OUTBOX_CREATE_MOVES, false);
      boolean isManualSlot = Objects.nonNull(completeInstructionRequest.getSlotDetails())
              && Objects.nonNull(completeInstructionRequest.getSlotDetails().getSlot());
      if (!isEnableOutboxCreateMoves // bau flow
              || !isManualSlot) { // new flow
        findSlotFromSmartSlotting(
                completeInstructionRequest,
                httpHeaders,
                instructionFromDB,
                deliveryDocumentLine,
                enableRDSReceipt ? null : newLabelTrackingId,
                isDCOneAtlasEnabled);
      }
      TenantContext.get().setCompleteInstrSlottingCallStart(System.currentTimeMillis());
      TenantContext.get().setCompleteInstrSlottingCallEnd(System.currentTimeMillis());
      if (!isDCOneAtlasEnabled || enableRDSReceipt) {
        TenantContext.get().setCompleteInstrNimRdsCallStart(System.currentTimeMillis());
        // Response from RDS with slot information
        receiveContainersResponseBody =
            getLabelFromRDS(instructionFromDB, completeInstructionRequest, httpHeaders);
        TenantContext.get().setCompleteInstrNimRdsCallEnd(System.currentTimeMillis());

        newLabelTrackingId =
            Long.valueOf(receiveContainersResponseBody.getReceived().get(0).getLabelTrackingId())
                .toString();
      } else {
        receiveContainersResponseBody =
            mockRDSResponseObj(newLabelTrackingId, completeInstructionRequest.getSlotDetails());
      }
      if (enableRDSReceipt) {
        findSlotFromSmartSlotting(
                completeInstructionRequest,
                httpHeaders,
                instructionFromDB,
                deliveryDocumentLine,
                newLabelTrackingId,
                isDCOneAtlasEnabled
        );
      }

      String oldLabelTrackingId = instructionFromDB.getContainer().getTrackingId();
      Container parentContainer =
          containerService.getContainerWithChildsByTrackingId(
              instructionFromDB.getContainer().getTrackingId(), true);
      parentContainer.setTrackingId(newLabelTrackingId);
      parentContainer.setInventoryStatus(AVAILABLE);
      ContainerItem parentContainerItem = parentContainer.getContainerItems().get(0);
      parentContainerItem.setTrackingId(newLabelTrackingId);

      Set<Container> containerList =
          updateParentContainerTrackingId(parentContainer, newLabelTrackingId);
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

      if (!isDCOneAtlasEnabled || enableRDSReceipt) {
        LinkedTreeMap<String, Object> moveTreeMap = instructionFromDB.getMove();
        moveTreeMap.put(
            ReceivingConstants.MOVE_TO_LOCATION,
            receiveContainersResponseBody.getReceived().get(0).getDestinations().get(0).getSlot());
        moveTreeMap.put(
            "correlationID", httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
        moveTreeMap.put("lastChangedOn", new Date());
        moveTreeMap.put("lastChangedBy", userId);
        instructionFromDB.setMove(moveTreeMap);
      }
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

      if (StringUtils.isNotBlank(instructionFromDB.getProblemTagId())) {
        TenantContext.get().setCompleteInstrCompleteProblemCallStart(System.currentTimeMillis());
        rxFixitProblemService.completeProblem(instructionFromDB, httpHeaders, deliveryDocumentLine);
        publishDeliveryStatus(instructionFromDB.getDeliveryNumber(), httpHeaders);
        TenantContext.get().setCompleteInstrCompleteProblemCallEnd(System.currentTimeMillis());
      }

      if (isGdmShipmentGetByScanV4Enabled
          && null != deliveryDocumentLine
          && null != deliveryDocumentLine.getAdditionalInfo()
          && null != deliveryDocumentLine.getAdditionalInfo().getSerializedInfo()) {
        callGdmToUpdatePackStatus(
            instructionFromDB, parentContainer, httpHeaders, deliveryDocumentLine);
      }
      // only use outbox flow if flag is enabled and there is epcis data
      boolean isOutboxInvIntegration =
          tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              RxConstants.ENABLE_OUTBOX_INVENTORY_INTEGRATION,
              false);
      TenantContext.get().setCompleteInstrPersistDBCallStart(System.currentTimeMillis());

      enrichPayloadToPublishToSCT(instructionFromDB, parentContainer);
      containerService.enrichContainerForDcfin(deliveryDocument, parentContainer);

      LOG.info(
          "Entering into outbox integration :: isOutboxInvIntegration {} :: Is EPCIS flow ? :: ",
          isOutboxInvIntegration,
          null != deliveryDocumentLine.getAdditionalInfo().getSerializedInfo());

      SlotDetails slotDetails = completeInstructionRequest.getSlotDetails();
      if (isOutboxInvIntegration
          && null != deliveryDocumentLine.getAdditionalInfo().getSerializedInfo()) {
        updateParentMultiSkuInstruction(instructionFromDB,httpHeaders);
        rxCompleteInstructionOutboxHandler.outboxCompleteInstruction(
            parentContainer, instructionFromDB, userId, slotDetails, httpHeaders);
        updateParentMultiSkuInstruction(instructionFromDB,httpHeaders);
      } else if (isOutboxInvIntegration) {
        rxCompleteInstructionOutboxHandler.outboxCompleteInstructionAsnFlow(
            parentContainer, instructionFromDB, userId, slotDetails, httpHeaders);
      } else {
        rxInstructionHelperService.persist(parentContainer, instructionFromDB, userId);
      }
      TenantContext.get().setCompleteInstrPersistDBCallEnd(System.currentTimeMillis());

      TenantContext.get().setCompleteInstrEpcisCallStart(System.currentTimeMillis());
      if (null == deliveryDocumentLine.getAdditionalInfo().getSerializedInfo()) {
        publishSerializedData(
            instructionFromDB, deliveryDocumentLine, completeInstructionRequest, httpHeaders);
      }
      TenantContext.get().setCompleteInstrEpcisCallEnd(System.currentTimeMillis());
      if (!isOutboxInvIntegration) {
        rxInstructionHelperService.publishContainers(Arrays.asList(parentContainer));
      }
      if (rxManagedConfig.isTrimCompleteInstructionResponseEnabled()) {
        instructionFromDB.setChildContainers(Collections.emptyList());
      }
      instructionFromDB =
          RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(instructionFromDB);
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
      rollbackForException = true;
      LOG.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(rbde));
      throw rbde;
    } catch (ReceivingException receivingException) {
      rollbackForException = true;
      LOG.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(receivingException));
      throw RxUtils.convertToReceivingBadDataException(receivingException);
    } catch (Exception e) {
      rollbackForException = true;
      LOG.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVING_INTERNAL_ERROR,
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          e);
    } finally {
      if (rxManagedConfig.isRollbackNimRdsReceiptsEnabled()
          && rollbackForException
          && StringUtils.isNotBlank(newLabelTrackingId)
          && (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false)
      || tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(), IS_DC_RDS_RECEIPT_ENABLED, false))) {
        nimRdsServiceImpl.quantityChange(0, newLabelTrackingId, httpHeaders);
      }
    }
  }

  /**
   * Enriches payload to have information which is required for SCT integration
   *
   * @param instruction intruction
   * @param parentContainer container to publish to sct
   */
  public static void enrichPayloadToPublishToSCT(
      Instruction instruction, Container parentContainer) {
    Map<String, Object> containerInfo =
        Optional.ofNullable(parentContainer.getContainerMiscInfo()).orElse(new HashMap<>());

    String receivingType = getReceivingType(instruction.getInstructionCode());
    if (StringUtils.isNotEmpty(receivingType)) {
      containerInfo.put(RECEIVING_TYPE, receivingType);
    }

    if (StringUtils.isNotEmpty(instruction.getProblemTagId())) {
      containerInfo.put(PROBLEM_TAG_ID, instruction.getProblemTagId());
    }

    parentContainer.setContainerMiscInfo(containerInfo);
  }

  public Set<Container> updateParentContainerTrackingId(
      Container parentContainer, String newLabelTrackingId) {
    Set<Container> containerList = new HashSet<>();
    parentContainer
        .getChildContainers()
        .forEach(
            childContainer -> {
              childContainer.setParentTrackingId(newLabelTrackingId);
              childContainer.setInventoryStatus(AVAILABLE);
              containerList.add(childContainer);
            });
    return containerList;
  }

  public void calculateAndLogElapsedTimeSummary() {
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

  public void findSlotFromSmartSlotting(
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders,
      Instruction instructionFromDB,
      DeliveryDocumentLine deliveryDocumentLine,
      String newLabelTrackingId,
      Boolean isDCOneAtlasEnabled) {
    boolean isRxSmartSlottingEnabled =
        configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RxConstants.SMART_SLOTING_RX_FEATURE_FLAG);

    // Auto-Slotting with Smart Slotting flag enabled.
    // Partial Receiving - Slot to prime to be ignored here
    // Manual Slotting - No need to call Smart slotting
    String manualSlot =
              Objects.nonNull(completeInstructionRequest.getSlotDetails())
                && Objects.nonNull(completeInstructionRequest.getSlotDetails().getSlot())
            ? completeInstructionRequest.getSlotDetails().getSlot()
            : null;

    if (isDCOneAtlasEnabled
        || (isRxSmartSlottingEnabled
            && !completeInstructionRequest.isPartialContainer()
            && StringUtils.isBlank(manualSlot))) {
      SlotDetails slotDetails = new SlotDetails();

      Integer totalQty = null;
      String qtyUom = null;
      boolean partialInstruction =
          completeInstructionRequest.isPartialContainer()
              || RxUtils.isPartialInstruction(instructionFromDB.getInstructionCode());
      if (StringUtils.isNotBlank(newLabelTrackingId)) {
        qtyUom = partialInstruction ? Uom.WHPK : Uom.VNPK;
        totalQty =
            ReceivingUtils.conversionToUOM(
                instructionFromDB.getReceivedQuantity(),
                instructionFromDB.getReceivedQuantityUOM(),
                qtyUom,
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack());
      }

      String door =
          instructionFromDB.getMove().containsKey(ReceivingConstants.MOVE_FROM_LOCATION)
              ? (String) instructionFromDB.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION)
              : null;
      String findSlotType =
          partialInstruction
              ? ReceivingConstants.SLOTTING_FIND_PRIME_SLOT
              : ReceivingConstants.SLOTTING_FIND_SLOT;
      SlottingPalletResponse slottingRxPalletResponse =
          rxSlottingServiceImpl.acquireSlot(
              instructionFromDB.getMessageId(),
              Arrays.asList(deliveryDocumentLine.getItemNbr()),
              (Objects.isNull(completeInstructionRequest.getSlotDetails())
                      || Objects.isNull(completeInstructionRequest.getSlotDetails().getSlotSize()))
                  ? 0
                  : completeInstructionRequest.getSlotDetails().getSlotSize(),
              findSlotType,
              newLabelTrackingId,
              totalQty,
              qtyUom,
              deliveryDocumentLine.getItemUpc(),
              manualSlot,
              door,
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

  public Map<String, Object> getNewCtrLabel(
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
    DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> responseDeliveryDocuments =
        deliveryDocumentsSearchHandler.fetchDeliveryDocument(instructionRequest, httpHeaders);

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
              ReceivingUtils.conversionToVendorPack(
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

        Receipt cancelledReceipt;
        if (rxManagedConfig.isRollbackReceiptsByShipment()) {
          HashMap<String, Receipt> receiptsByShipment = new HashMap<>();
          List<Receipt> rollbackReceiptsWithShipment;
          if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(containersByInstruction)
              && containersByInstruction.size() > 1) { // D40 items will only have 1 container.
            rollbackReceiptsWithShipment =
                rxReceiptsBuilder.constructRollbackReceiptsWithShipment(
                    containersByInstruction, receiptsByShipment, instruction);
          } else {
            rollbackReceiptsWithShipment =
                Arrays.asList(
                    rxReceiptsBuilder.buildReceiptToRollbackInEaches(
                        instruction, userId, backOutQuantity, backoutQtyInEa));
          }
          rxInstructionHelperService.rollbackContainers(
              trackingIds, rollbackReceiptsWithShipment, instruction);
        } else {
          cancelledReceipt =
              rxReceiptsBuilder.buildReceiptToRollbackInEaches(
                  instruction, userId, backOutQuantity, backoutQtyInEa);
          rxInstructionHelperService.rollbackContainers(trackingIds, cancelledReceipt, instruction);
        }
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
    String deliveryNumber = instructionRequest.getDeliveryNumber();
    Instruction existingInstruction = null;
    Optional<RxReceivingType> receivingTypeOptional =
        RxReceivingType.fromString(instructionRequest.getReceivingType());
    RxReceivingType receivingType = null;
    if (receivingTypeOptional.isPresent()) {
      receivingType = receivingTypeOptional.get();
    }
    if (RxReceivingType.UPC_PARTIALS == receivingType) {
      existingInstruction =
          rxInstructionHelperService.checkIfInstructionExistsBeforeAllowingPartialReceiving(
              deliveryNumber, instructionRequest.getUpcNumber(), userId);
    }

    if (Objects.nonNull(existingInstruction)) {
      instructionRequest.setDeliveryDocuments(
          Arrays.asList(
              gson.fromJson(existingInstruction.getDeliveryDocument(), DeliveryDocument.class)));
      return RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(existingInstruction);
    }
    existingInstruction =
        instructionPersisterService.fetchExistingInstructionIfexists(instructionRequest);
    if (Objects.nonNull(existingInstruction)) {
      return RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(existingInstruction);
    }

    if (StringUtils.isBlank(instructionRequest.getProblemTagId())) {
      existingInstruction =
          rxInstructionPersisterService
              .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
                  instructionRequest, userId);
    } else {
      existingInstruction =
          instructionPersisterService
              .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId(
                  instructionRequest, userId, instructionRequest.getProblemTagId());
    }
    if (Objects.nonNull(existingInstruction)) {
      instructionRequest.setDeliveryDocuments(
          Arrays.asList(
              gson.fromJson(existingInstruction.getDeliveryDocument(), DeliveryDocument.class)));
      return RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(existingInstruction);
    }

    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    Pair<DeliveryDocument, Long> autoSelectedDocument =
        instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            deliveryDocuments, 1, ReceivingConstants.Uom.EACHES);
    if (Objects.isNull(autoSelectedDocument)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.AUTO_SELECT_PO_NO_OPEN_QTY, RxConstants.AUTO_SELECT_PO_NO_OPEN_QTY);
    }

    DeliveryDocument deliveryDocument = autoSelectedDocument.getKey();
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    // Temporary patch till a permanent fix made on the client side
    if (deliveryDocument.getDeliveryNumber() == 0) {
      deliveryDocument.setDeliveryNumber(Long.parseLong(deliveryNumber));
    }
    // check if ndc is null and populate vendorStockNumber if ndc is null
    if (StringUtils.isBlank(deliveryDocumentLine.getNdc())) {
      deliveryDocumentLine.setNdc(deliveryDocumentLine.getVendorStockNumber());
    }

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(RX_XBLOCK_FEATURE_FLAG)
        && RxUtils.isItemXBlocked(deliveryDocumentLine)) {
      LOG.error(
          "Given item:{} and handlingCode:{} matches with X-blocked item criteria.",
          deliveryDocumentLine.getItemNbr(),
          deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
      throw new ReceivingBadDataException(
          ExceptionCodes.CREATE_INSTRUCTION_ERROR,
          String.format(
              ReceivingConstants.X_BLOCK_ITEM_ERROR_MSG, deliveryDocumentLine.getItemNbr()),
          String.valueOf(deliveryDocumentLine.getItemNbr()));
    }

    if (RxUtils.isControlledSubstance(deliveryDocumentLine)) {
      LOG.error("Given item {} belonging to delivery: {} is a controlled substance. Don't receive",
              deliveryDocumentLine.getItemNbr(),
              deliveryDocument.getDeliveryNumber());

      throw new ReceivingBadDataException(
              ExceptionCodes.CREATE_INSTRUCTION_CONTROLLED_SUBSTANCE_ERROR,
              String.format(
                      ReceivingConstants.CONTROLLED_SUBSTANCE_ERROR,
                      deliveryDocumentLine.getItemNbr()));

    }

    boolean isItemDscsaExempted =
        RxUtils.isDscsaExemptionIndEnabled(
            deliveryDocumentLine,
            configUtils.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG));
    Instruction instruction;

    Optional<FitProblemTagResponse> fitProblemTagResponseOptional = null;
    if (StringUtils.isNotBlank(instructionRequest.getProblemTagId())) {
      fitProblemTagResponseOptional =
          rxInstructionHelperService.getFitProblemTagResponse(instructionRequest.getProblemTagId());
      if (fitProblemTagResponseOptional.isPresent()) {
        FitProblemTagResponse fitProblemTagResponse = fitProblemTagResponseOptional.get();
        if (isItemDscsaExempted && RxUtils.isASNReceivingOverrideEligible(fitProblemTagResponse)) {
          throw new ReceivingBadDataException(
              ExceptionCodes.SERIALIZED_PRODUCT_UPC_RCV_NOT_SUPPORTED,
              RxConstants.SERIALIZED_PRODUCT_UPC_RCV_NOT_SUPPORTED);
        }
      }
    }

    if (isItemDscsaExempted
        || isGrandFathered(instructionRequest)
        || RxUtils.isTransferredPo(deliveryDocument.getPoTypeCode())
        || (isRepackagedVendor(deliveryDocument) && !RxUtils.isEpcisEnabledVendor(deliveryDocumentLine))) {

      final String purchaseReferenceNumber = deliveryDocument.getPurchaseReferenceNumber();
      final int purchaseReferenceLineNumber = deliveryDocumentLine.getPurchaseReferenceLineNumber();
      deliveryDocumentLine.setTotalOrderQty(
          ReceivingUtils.conversionToEaches(
              deliveryDocumentLine.getTotalOrderQty(),
              deliveryDocumentLine.getQtyUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
      deliveryDocumentLine.setQtyUOM(ReceivingConstants.Uom.EACHES);
      Pair<Integer, Long> receivedQtyDetails =
          instructionHelperService.getReceivedQtyDetailsInEaAndValidate(
              instructionRequest.getProblemTagId(), deliveryDocument, deliveryNumber);

      int maxReceiveQty = receivedQtyDetails.getKey();
      long totalReceivedQty = receivedQtyDetails.getValue();

      deliveryDocumentLine.setOpenQty(
          deliveryDocumentLine.getTotalOrderQty() - (int) totalReceivedQty);
      deliveryDocumentLine.setDeptNumber(deliveryDocument.getDeptNumber());

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
      if (StringUtils.isNotBlank(instructionRequest.getProblemTagId())
          && fitProblemTagResponseOptional.isPresent()) {
        FitProblemTagResponse fitProblemTagResponse = fitProblemTagResponseOptional.get();
        Resolution activeResolution =
            RxUtils.getActiveResolution(fitProblemTagResponseOptional.get());
        if (Objects.nonNull(activeResolution)) {
          int problemQty =
              ReceivingUtils.conversionToEaches(
                  Math.min(activeResolution.getQuantity(), fitProblemTagResponse.getReportedQty()),
                  getProblemTicketUom(fitProblemTagResponse),
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack());
          int problemResolutionQty = (int) (problemQty - totalReceivedQty);
          calculatedQtyBasedOnGdmQty = Math.min(calculatedQtyBasedOnGdmQty, problemResolutionQty);
        }
      }

      instruction.setProjectedReceiveQty(calculatedQtyBasedOnGdmQty);

      int calculatedQtyBasedOnGdmQtyVnpk =
          ReceivingUtils.conversionToVendorPack(
              calculatedQtyBasedOnGdmQty,
              ReceivingConstants.Uom.EACHES,
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack());

      boolean isPartialCase =
          calculatedQtyBasedOnGdmQtyVnpk < 1
              || RxUtils.isInstructionRequestPartial(instructionRequest);
      if (isPartialCase) {
        calculatedQtyBasedOnGdmQty =
            calculatedQtyBasedOnGdmQtyVnpk < 1
                ? calculatedQtyBasedOnGdmQty
                : deliveryDocumentLine.getVendorPack();

        instruction.setProjectedReceiveQty(calculatedQtyBasedOnGdmQty);
      }

      instruction.setPrintChildContainerLabels(false);
      populateInstructionDetails(instruction, isPartialCase, instructionRequest);
      instruction.setMove(moveTreeMap);
      populateInstructionSetId(instructionRequest, instruction);
      instruction = instructionPersisterService.saveInstruction(instruction);
      instruction = RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(instruction);
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.SERIALIZED_PRODUCT_UPC_NOT_SUPPORTED,
          RxConstants.SERIALIZED_PRODUCT_UPC_NOT_SUPPORTED);
    }
    return instruction;
  }

  private void populateInstructionDetails(
      Instruction instruction, boolean isPartialCase, InstructionRequest instructionRequest) {
    if (isPartialCase) {
      instruction.setInstructionMsg(
          RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionMsg());
      instruction.setInstructionCode(
          RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionType());
    } else {
      instruction.setInstructionMsg(
          RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionMsg());
      instruction.setInstructionCode(
          RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType());
    }
    instruction.setProviderId("RxSSTK");
    instruction.setActivityName("SSTK");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.EACHES);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.EACHES);
    if (Objects.nonNull(instructionRequest.getProblemTagId())) {
      instruction.setProblemTagId(instructionRequest.getProblemTagId());
    }

    if (StringUtils.isNotEmpty(instructionRequest.getUpcNumber())) {
      instruction.setGtin(instructionRequest.getUpcNumber());
    }
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
   * catalogGtin if it does not exists in deliveryDocumentLines and manufactureDetails object which
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
      SlottingPalletResponse slottingPalletResponse = null;
      try {
        slottingPalletResponse =
            rxSlottingServiceImpl.acquireSlot(
                instructionRequest.getMessageId(),
                Arrays.asList(deliveryDocument.getDeliveryDocumentLines().get(0).getItemNbr()),
                0,
                ReceivingConstants.SLOTTING_FIND_PRIME_SLOT,
                httpHeaders);

        if (Objects.nonNull(slottingPalletResponse)
            && !CollectionUtils.isEmpty(slottingPalletResponse.getLocations())) {
          moveTreeMap.put(
              ReceivingConstants.MOVE_PRIME_LOCATION,
              slottingPalletResponse.getLocations().get(0).getLocation());
          moveTreeMap.put(
              ReceivingConstants.MOVE_PRIME_LOCATION_SIZE,
              slottingPalletResponse.getLocations().get(0).getLocationSize());
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

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<InstructionSummary> getInstructionSummaryByDeliveryAndInstructionSetId(
      Long deliveryNumber, Long instructionSetId) {
    List<InstructionSummary> instructionSummaryList;
    if (Objects.isNull(instructionSetId)) {
      instructionSummaryList =
          InstructionUtils.convertToInstructionSummaryResponseList(
              instructionRepository.findByDeliveryNumber(deliveryNumber));
    } else {
      instructionSummaryList =
          InstructionUtils.convertToInstructionSummaryResponseList(
              instructionRepository.findByDeliveryNumberAndInstructionSetIdOrderByCreateTs(
                  deliveryNumber, instructionSetId));
    }

    return RxUtils.updateProjectedQuantyInInstructionSummary(instructionSummaryList);
  }

  private void filterPOsByLot(List<DeliveryDocument> deliveryDocuments, String lotNumber4mLabel) {
    Iterator<DeliveryDocument> deliveryDocumentIterator = emptyIfNull(deliveryDocuments).iterator();
    while (deliveryDocumentIterator.hasNext()) {
      DeliveryDocument deliveryDocument = deliveryDocumentIterator.next();
      if (!RxUtils.isVendorWholesaler(
          deliveryDocument,
          rxManagedConfig.getWholesalerVendors(),
          rxManagedConfig.isWholesalerLotCheckEnabled())) {
        Iterator<DeliveryDocumentLine> deliveryDocumentLineIterator =
            emptyIfNull(deliveryDocument.getDeliveryDocumentLines()).iterator();
        while (deliveryDocumentLineIterator.hasNext()) {
          DeliveryDocumentLine deliveryDocumentLine = deliveryDocumentLineIterator.next();
          Optional<String> foundLotMatchOptional =
              emptyIfNull(deliveryDocumentLine.getManufactureDetails())
                  .stream()
                  .map(ManufactureDetail::getLot)
                  .filter(lotNumber -> StringUtils.equalsIgnoreCase(lotNumber, lotNumber4mLabel))
                  .findFirst();
          if (!foundLotMatchOptional.isPresent()) {
            deliveryDocumentLineIterator.remove();
          }
        }
        if (CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())) {
          deliveryDocumentIterator.remove();
        }
      }
    }
  }

  public void publishDeliveryStatus(Long deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    // Get received quantity for a given delivery
    List<ReceiptSummaryResponse> receiptSummaryResponses =
        receiptService.getReceivedQtySummaryByPOForDelivery(
            deliveryNumber, ReceivingConstants.Uom.EACHES);

    // Get delivery details
    String deliveryResponse =
        deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, httpHeaders);
    String deliveryStatus =
        parser
            .parse(deliveryResponse)
            .getAsJsonObject()
            .get("deliveryStatus")
            .toString()
            .replace("\"", "");
    // Publish updated receipts to GDM if delivery is not in working status
    if (!deliveryStatus.equalsIgnoreCase(DeliveryStatus.WRK.toString())) {
      deliveryStatusPublisher.publishDeliveryStatus(
          deliveryNumber,
          DeliveryStatus.COMPLETE.name(),
          receiptSummaryResponses,
          ReceivingUtils.getForwardablHeader(httpHeaders));
    }
  }

  protected void isSingleItemMultiPoPoLine(
      List<DeliveryDocument> deliveryDocuments4mGDM,
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocuments4mGDM.forEach(
        deliveryDocument ->
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(documentLine -> deliveryDocumentLines.add(documentLine)));
    if (isSingleSku(deliveryDocumentLines)) {
      instructionRequest.setDeliveryDocuments(deliveryDocuments4mGDM);
      Instruction instruction = createInstructionForUpcReceiving(instructionRequest, httpHeaders);

      if (appConfig.isOverrideServeInstrMethod()) {
        instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
      }

      instructionResponse.setInstruction(instruction);
    } else {
      if (rxManagedConfig.isMultiSkuInstructionViewEnabled()) {
        if (RxUtils.isEpcisEnabledVendor(
                deliveryDocuments4mGDM.get(0).getDeliveryDocumentLines().get(0))
            && (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED,
                false))
            && !ReceivingUtils.isAsnReceivingOverrideEligible(httpHeaders)) {
          if (!rxManagedConfig.isEpcisMultiSkuPalletEnabled) {
            throw new ReceivingBadDataException(
                ExceptionCodes.MULTI_SKU_CASE_EPCIS,
                ExceptionDescriptionConstants.MULTI_SKU_PALLET_NOT_ALLOWED_EPCIS_VENDOR);
          }

          Boolean enablePalletFlowInMultiSku = tenantSpecificConfigReader.getConfiguredFeatureFlag(
                  TenantContext.getFacilityNum().toString(),
                  ENABLE_PALLET_FLOW_IN_MULTI_SKU,
                  false);

          List<Pack> multiSkuPacks = new ArrayList();
          deliveryDocuments4mGDM.forEach(
              deliveryDocument -> {
                deliveryDocument
                    .getDeliveryDocumentLines()
                    .forEach(
                        line -> {
                          line.getAdditionalInfo().setPalletFlowInMultiSku(enablePalletFlowInMultiSku);
                          line.getAdditionalInfo().setPacksOfMultiSkuPallet(line.getPacks());
                          line.getAdditionalInfo()
                              .setPackCountInEaches(
                                  ReceivingUtils.conversionToEaches(
                                      line.getPacks().size(),
                                      ReceivingConstants.Uom.VNPK,
                                      line.getVendorPack(),
                                      line.getWarehousePack()));
                          multiSkuPacks.addAll(line.getPacks());
                        });
              });
          AtomicBoolean palletReceivedBefore = new AtomicBoolean(false);

          multiSkuPacks.forEach(
              pack -> {
                if (RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
                    pack.getReceivingStatus())) {
                  palletReceivedBefore.set(true);
                }
              });

          if (palletReceivedBefore.get()) {
            deliveryDocuments4mGDM.forEach(
                deliveryDoc -> {
                  deliveryDoc
                      .getDeliveryDocumentLines()
                      .forEach(
                          line -> {
                            line.getAdditionalInfo().setSkipEvents(true);
                            line.getAdditionalInfo().setPartialPallet(true);
                          });
                });
          }

          if (RxUtils.is2DScanInstructionRequest(instructionRequest.getScannedDataList())) {
            Map<String, ScannedData> scannedDataMap =
                RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
            ManufactureDetail scannedDetails =
                RxUtils.convertScannedDataToManufactureDetail(scannedDataMap);
            if (Objects.nonNull(scannedDetails)) {
              multiSkuPacks.removeIf(
                  pack ->
                      !(pack.getGtin().equalsIgnoreCase(scannedDetails.getGtin())
                          && pack.getExpiryDate().equalsIgnoreCase(scannedDetails.getExpiryDate())
                          && pack.getLotNumber().equalsIgnoreCase(scannedDetails.getLot())
                          && pack.getSerial().equalsIgnoreCase(scannedDetails.getSerial())));
              if (multiSkuPacks.isEmpty()) {
                throw new ReceivingBadDataException(
                    ExceptionCodes.MULTI_SKU_CASE_EPCIS,
                    ExceptionDescriptionConstants.SCANNED_2D_NOT_MATCHING_EPCIS);
              }
            }
          }
          deliveryDocuments4mGDM.forEach(
              deliveryDocument -> {
                Instruction instruction =
                    InstructionUtils.mapDeliveryDocumentToInstruction(
                        deliveryDocument,
                        InstructionUtils.mapHttpHeaderToInstruction(
                            httpHeaders, InstructionUtils.createInstruction(instructionRequest)));
                instruction.setSsccNumber(instructionRequest.getSscc());
                instruction.setCompleteTs(new Date());
                instruction.setCompleteUserId(
                    httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
                instruction.setInstructionMsg(
                    RxInstructionType.SERIALIZED_MULTISKU_PALLET.getInstructionMsg());
                instruction.setInstructionCode(
                    RxInstructionType.SERIALIZED_MULTISKU_PALLET.getInstructionType());
                instructionPersisterService.saveInstruction(instruction);
              });
          Instruction instruction = new Instruction();
          instruction.setInstructionMsg(
              RxInstructionType.SERIALIZED_MULTISKU_PALLET.getInstructionMsg());
          instruction.setInstructionCode(
              RxInstructionType.SERIALIZED_MULTISKU_PALLET.getInstructionType());
          instructionResponse.setInstruction(instruction);
          instructionResponse.setDeliveryDocuments(deliveryDocuments4mGDM);
        } else {
          Instruction instruction = new Instruction();
          instruction.setInstructionMsg(RxInstructionType.MULTISKU_ASN.getInstructionMsg());
          instruction.setInstructionCode(RxInstructionType.MULTISKU_ASN.getInstructionType());
          instructionResponse.setInstruction(instruction);
          instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
        }
      }
    }
  }

  @Deprecated
  private void mapSerializedPackData(
      DeliveryDocumentLine line,
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders,
      Boolean multiDeliveryDocumentLine) {
    LOG.info(
        "mapSerializedPackData ::   SSCC Number from request : {} ", instructionRequest.getSscc());
    LOG.debug(
        "mapSerializedPackData ::  Input Payload: {} :: multiDeliveryDocumentLine {}  :: DeliveryDocumentLine {} ",
        instructionRequest,
        multiDeliveryDocumentLine,
        line);

    List<Pack> packs = line.getPacks();
    ItemData additionalInfo = line.getAdditionalInfo();
    String scannedSSCC = instructionRequest.getSscc();
    String deliveryNumber = instructionRequest.getDeliveryNumber();
    String scannedGtin = StringUtils.EMPTY;
    String scannedSerial = StringUtils.EMPTY;
    String documentId = StringUtils.EMPTY;
    AtomicBoolean caseReceivedBefore = new AtomicBoolean(false);
    if (RxUtils.isEpcisEnabledVendor(line)
        && !ReceivingUtils.isAsnReceivingOverrideEligible(httpHeaders)
        && !line.getAdditionalInfo().getAutoSwitchEpcisToAsn()) {
      List<ManufactureDetail> serializedInfo = new ArrayList<>();
      ManufactureDetail scannedCase = new ManufactureDetail();
      Integer attpQtyInEaches = 0;
      Set<String> packGtinSet = new HashSet<>();
      Set<String> packLotSet = new HashSet<>();
      Set<String> unitGtinSet = new HashSet<>();
      Set<String> unitLotSet = new HashSet<>();

      if (StringUtils.isBlank(scannedSSCC)
          && !CollectionUtils.isEmpty(instructionRequest.getScannedDataList())) {
        Map<String, ScannedData> scannedDataMap =
            RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
        scannedGtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
        scannedSerial = scannedDataMap.get(ApplicationIdentifier.SERIAL.getKey()).getValue();
      }

      boolean isPalletSSCCRequest =
          StringUtils.isNotBlank(scannedSSCC) && scannedSSCC.equals(packs.get(0).getPalletNumber());
      LOG.info("mapSerializedPackData :: isPalletSSCCRequest : {} ", isPalletSSCCRequest);
      Pack unitSerialDetailofScannedCase = null;
      if (!isPalletSSCCRequest && isUnitSerialResponse(packs)) {
        unitSerialDetailofScannedCase = packs.get(0);
      }
      if (!isPalletSSCCRequest && Objects.isNull(unitSerialDetailofScannedCase)) {
        for (Pack pack : packs) {
          if ((StringUtils.isNotBlank(scannedSSCC) && scannedSSCC.equals(pack.getPackNumber()))
              || (StringUtils.isNotBlank(scannedGtin)
                  && scannedGtin.equals(pack.getGtin())
                  && StringUtils.isNotBlank(scannedSerial)
                  && scannedSerial.equals(pack.getSerial()))) {
            unitSerialDetailofScannedCase =
                getUnitSerializedData(
                    deliveryNumber, scannedSSCC, scannedGtin, scannedSerial, httpHeaders, false);
            if (StringUtils.isNotBlank(scannedSerial)) {
              additionalInfo.setSgtinScannedSerial(scannedSerial);
            }
            if (StringUtils.isNotBlank(pack.getPalletNumber())) {
              additionalInfo.setPalletOfCase(pack.getPalletNumber());
            }
            if (Objects.nonNull(unitSerialDetailofScannedCase)) {
              double packUnitCount =
                  (double)
                      unitSerialDetailofScannedCase
                          .getItems()
                          .stream()
                          .filter(scanPack -> !checkIfReceived(scanPack.getSerial(), line.getNdc()))
                          .count();

              Integer quantityInEaches = ReceivingUtils.conversionToWareHousePack(
                      line.getVendorPack(),
                      ReceivingConstants.Uom.EACHES,
                      line.getVendorPack(),
                      line.getWarehousePack());

              if(packUnitCount < quantityInEaches  && line.getAdditionalInfo().getPartOfMultiSkuPallet()){
                {
                  throw new ReceivingBadDataException(
                          ExceptionCodes.BARCODE_ALREADY_RECEIVED,
                          ExceptionDescriptionConstants.BARCODE_ALREADY_RECEIVED);
                }
              }
              unitSerialDetailofScannedCase.setUnitCount(packUnitCount);
            }
          }
        }
      }
      if (!isPalletSSCCRequest
          && multiDeliveryDocumentLine
          && Objects.isNull(unitSerialDetailofScannedCase)) {
        return;
      }
      if (!isPalletSSCCRequest && Objects.isNull(unitSerialDetailofScannedCase)) {
        if (StringUtils.isNotBlank(scannedGtin) && StringUtils.isNotBlank(scannedSerial)) {
          unitSerialDetailofScannedCase =
              getUnitSerializedData(
                  deliveryNumber,
                  packs.get(0).getPackNumber(),
                  packs.get(0).isMultiskuPack() ? scannedGtin : packs.get(0).getGtin(),
                  packs.get(0).getSerial(),
                  httpHeaders,
                  packs.get(0).isMultiskuPack());
          if (Objects.nonNull(unitSerialDetailofScannedCase)) {
            additionalInfo.setIsSerUnit2DScan(Boolean.TRUE);
            unitSerialDetailofScannedCase.setUnitCount(
                Objects.nonNull(unitSerialDetailofScannedCase.getItems())
                    ? (double) unitSerialDetailofScannedCase.getItems().size()
                    : 0);
            packs.get(0).setUnitCount(unitSerialDetailofScannedCase.getUnitCount());
            if (StringUtils.isNotBlank(unitSerialDetailofScannedCase.getPalletNumber())) {
              additionalInfo.setPalletOfCase(unitSerialDetailofScannedCase.getPalletNumber());
            }
            if(null!=packs && null!=packs.get(0) && StringUtils.isNotBlank(packs.get(0).getPalletNumber()) ){
              caseReceivedBefore = checkIfPacksOfPalletsAreReceived(deliveryNumber, packs.get(0).getPalletNumber(), httpHeaders);
            }
          }
        }
      }

      final String scannedGtinFinal = scannedGtin;
      final String scannedSerialFinal = scannedSerial;

      Integer receivedPacksCount = 0;
      if(caseReceivedBefore.get()){
        additionalInfo.setSkipEvents(true);
      }
      for (Pack pack : packs) {
        if (RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
            pack.getReceivingStatus())) {
          if (null != pack.getPalletNumber()) {
            additionalInfo.setSkipEvents(true);
          }
          if (StringUtils.isNotBlank(scannedSSCC)
              && scannedSSCC.equals(pack.getPalletNumber())
              && Boolean.FALSE.equals(multiDeliveryDocumentLine)) {
            receivedPacksCount++;
          }
          if (StringUtils.isNotBlank(scannedSSCC) && scannedSSCC.equals(pack.getPackNumber())
              || (StringUtils.isNotBlank(scannedGtinFinal)
                  && scannedGtinFinal.equals(pack.getGtin())
                  && StringUtils.isNotBlank(scannedSerialFinal)
                  && scannedSerialFinal.equals(pack.getSerial()))
              || (receivedPacksCount.equals(packs.size())) ) {
            throw new ReceivingBadDataException(
                ExceptionCodes.BARCODE_ALREADY_RECEIVED,
                ExceptionDescriptionConstants.BARCODE_ALREADY_RECEIVED);
          }
        }
      }

      for (Pack pack : packs) {
        if (StringUtils.isNotBlank(scannedSSCC) && scannedSSCC.equals(pack.getPackNumber())
            || (StringUtils.isNotBlank(scannedGtinFinal)
                && scannedGtinFinal.equals(pack.getGtin())
                && StringUtils.isNotBlank(scannedSerialFinal)
                && scannedSerialFinal.equals(pack.getSerial()))) {
          RxUtils.checkReceivingStatusReceivable(pack.getReceivingStatus());
        }
      }

      if (Objects.nonNull(unitSerialDetailofScannedCase)
          && ((!isFullCaseCase(unitSerialDetailofScannedCase, line)
                  || unitSerialDetailofScannedCase.isPartialPack())
              || unitSerialDetailofScannedCase.isMultiskuPack()
              || Objects.nonNull(additionalInfo.getIsSerUnit2DScan()))) {
        if (StringUtils.isNotBlank(scannedSSCC) && unitSerialDetailofScannedCase.isMultiskuPack()) {
          throw new ReceivingBadDataException(
              ExceptionCodes.MULTI_SKU_CASE_EPCIS, ReceivingException.MULTI_SKU_CASE_EPCIS_FLOW);
        }
        if (unitSerialDetailofScannedCase.isPartialPack()
            && Objects.isNull(additionalInfo.getIsSerUnit2DScan())) {
          throw new ReceivingBadDataException(
              ExceptionCodes.MULTI_LOT_PARTIAL_CASE, ReceivingException.MULTI_LOTS_CASE_EPCIS_FLOW);
        }
        scannedCase = RxUtils.getManufactureDetailByPack(unitSerialDetailofScannedCase);
        attpQtyInEaches += convertSerializedQtyToEaches(line, unitSerialDetailofScannedCase);
        // add all unit serial of scanned cases
        List<ManufactureDetail> unitSerialList = new ArrayList<>();
        populateUnitSerializedData(
            unitGtinSet,
            unitLotSet,
            unitSerialDetailofScannedCase,
            unitSerialList,
            additionalInfo,
            line.getNdc());
        serializedInfo.addAll(unitSerialList);
      } else {
        for (Pack pack : packs) {
          if (isValidSerializedPack(pack, line)) {
            documentId = pack.getDocumentId();
            if (RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
                pack.getReceivingStatus())) {
              continue;
            }
            attpQtyInEaches += convertSerializedQtyToEaches(line, pack);
            if (isScannedPack(scannedSSCC, scannedGtin, scannedSerial, pack) && !additionalInfo.isPalletFlowInMultiSku()) {
              // add all unit level serial of scanned cases and exclude scanned case
              List<ManufactureDetail> unitSerialList = new ArrayList<>();
              scannedCase = RxUtils.getManufactureDetailByPack(pack);
              populateUnitSerializedData(
                  unitGtinSet,
                  unitLotSet,
                  Objects.requireNonNull(unitSerialDetailofScannedCase),
                  unitSerialList,
                  additionalInfo,
                  line.getNdc());
              serializedInfo.addAll(unitSerialList);
            } else {
              packGtinSet.add(StringUtils.trim(pack.getGtin()));
              packLotSet.add(StringUtils.trim(pack.getLotNumber()));
              ManufactureDetail serializedPack = RxUtils.getManufactureDetailByPack(pack);
              if (serializedPack != null) {
                serializedInfo.add(serializedPack);
              }
            }
          } else {
            additionalInfo.setPartialPallet(true);
          }
        }
      }
      // override palletSSCC and caseSSCC in DeliveryDocumentLine
      line.setPalletSSCC(isPalletSSCCRequest ? scannedSSCC : null);
      line.setPackSSCC(!isPalletSSCCRequest ? scannedSSCC : null);
      additionalInfo.setGtinList(new ArrayList<>(packGtinSet));
      additionalInfo.setLotList(new ArrayList<>(packLotSet));
      additionalInfo.setUnitGtinList(new ArrayList<>(unitGtinSet));
      additionalInfo.setUnitLotList(new ArrayList<>(unitLotSet));
      additionalInfo.setSerializedInfo(serializedInfo);
      additionalInfo.setAttpQtyInEaches(attpQtyInEaches);
      additionalInfo.setScannedCase(scannedCase);
      line.setAdditionalInfo(additionalInfo);
    } else {
      LOG.info("Pack serialized data is null or empty.");
    }
  }

  private Pack getUnitSerializedData(
      String deliveryNumber,
      String scannedSSCC,
      String scannedGtin,
      String scannedSerial,
      HttpHeaders httpHeaders,
      Boolean unit2DFlow) {
    PackItemResponse packItemResponse = null;
    UnitSerialRequest unitSerialRequest;
    if (!unit2DFlow) {
      unitSerialRequest =
          getUnitSerialRequest(deliveryNumber, scannedSSCC, scannedGtin, scannedSerial);
    } else {
      unitSerialRequest = getUnitSerialRequestForUnit2D(deliveryNumber, scannedSSCC, scannedGtin);
    }
    try {
      packItemResponse = rxDeliveryService.getUnitSerializedInfo(unitSerialRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      LOG.error("Error fetching unit serial detail for sscc {} ", scannedSSCC, receivingException);
    }
    return Objects.nonNull(packItemResponse)
            && !CollectionUtils.isEmpty(packItemResponse.getPacks())
        ? packItemResponse.getPacks().get(0)
        : null;
  }
  private AtomicBoolean checkIfPacksOfPalletsAreReceived(String deliveryNumber, String sscc, HttpHeaders httpHeaders){
    AtomicBoolean casesReceivedBefore = new AtomicBoolean(false);
    SsccScanResponse ssccScanResponse =  deliveryService.getSsccScanDetails(deliveryNumber, sscc,  httpHeaders);
    if(null!= ssccScanResponse && null!= ssccScanResponse.getPacks()){
      ssccScanResponse.getPacks().forEach( pack -> {
                        if (RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
                                pack.getReceivingStatus()) || RxConstants.PARTIALLY_RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
                                pack.getReceivingStatus())) {
                          casesReceivedBefore.set(true);
                        }});
    }
  return casesReceivedBefore;
  }


  private boolean isUnitSerialResponse(List<Pack> packs) {
    return !CollectionUtils.isEmpty(packs.get(0).getItems());
  }

  private boolean isValidSerializedPack(Pack pack, DeliveryDocumentLine line) {
    return isFullCaseCase(pack, line)
        && !pack.isMultiskuPack()
        && !pack.isPartialPack()
        && StringUtils.isNotBlank(pack.getGtin())
        && RxConstants.VALID_ATTP_SERIALIZED_TRACKING_STATUS.equals(pack.getTrackingStatus())
        && !RxConstants.PARTIALLY_RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
            pack.getReceivingStatus())
        && !RxConstants.DECOMISSIONED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
            pack.getReceivingStatus())
        && !RxConstants.RTV_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
            pack.getReceivingStatus());
  }

  private boolean isScannedPack(
      String scannedSSCC, String scannedGtin, String scannedSerial, Pack pack) {
    boolean scannedSSCCMatch =
        StringUtils.isNotBlank(scannedSSCC) && scannedSSCC.equals(pack.getPackNumber());
    boolean gtinAndSerialMatch =
        org.apache.commons.lang3.StringUtils.isNoneBlank(scannedGtin, scannedSerial)
            && scannedGtin.equals(pack.getGtin())
            && scannedSerial.equals(pack.getSerial());
    return scannedSSCCMatch || gtinAndSerialMatch;
  }

  private UnitSerialRequest getUnitSerialRequestForUnit2D(
      String deliveryNumber, String scannedSSCC, String scannedGtin) {
    UnitSerialRequest unitSerialRequest = new UnitSerialRequest();
    unitSerialRequest.setDeliveryNumber(deliveryNumber);
    List<UnitRequestMap> identifier = new ArrayList();
    UnitRequestMap gtinReq = new UnitRequestMap();
    gtinReq.setKey(ReceivingConstants.KEY_UNITGTIN);
    gtinReq.setValue(scannedGtin);
    identifier.add(gtinReq);
    UnitRequestMap ssccReq = new UnitRequestMap();
    ssccReq.setKey(ReceivingConstants.SSCC);
    ssccReq.setValue(scannedSSCC);
    identifier.add(ssccReq);
    unitSerialRequest.setIdentifier(identifier);
    return unitSerialRequest;
  }

  private UnitSerialRequest getUnitSerialRequest(
      String deliveryNumber, String scannedSSCC, String scannedGtin, String scannedSerial) {
    UnitSerialRequest unitSerialRequest = new UnitSerialRequest();
    unitSerialRequest.setDeliveryNumber(deliveryNumber);
    List<UnitRequestMap> identifier = new ArrayList();
    if (StringUtils.isNotBlank(scannedSSCC)) {
      UnitRequestMap unitReqMap = new UnitRequestMap();
      unitReqMap.setKey(ReceivingConstants.SSCC);
      unitReqMap.setValue(scannedSSCC);
      identifier.add(unitReqMap);
      unitSerialRequest.setIdentifier(identifier);
    } else {
      UnitRequestMap gtinReq = new UnitRequestMap();
      gtinReq.setKey(ReceivingConstants.KEY_GTIN);
      gtinReq.setValue(scannedGtin);
      identifier.add(gtinReq);
      UnitRequestMap serialReq = new UnitRequestMap();
      serialReq.setKey(ReceivingConstants.KEY_SERIAL);
      serialReq.setValue(scannedSerial);
      identifier.add(serialReq);
      unitSerialRequest.setIdentifier(identifier);
    }
    return unitSerialRequest;
  }

  private void populateUnitSerializedData(
      Set<String> unitGtinSet,
      Set<String> unitLotSet,
      Pack unitSerialDetailofScannedCase,
      List<ManufactureDetail> unitSerialList,
      ItemData additionalInfo,
      String ndc) {

    if (null != unitSerialDetailofScannedCase) {
      additionalInfo.setScannedCaseAttpQty(unitSerialDetailofScannedCase.getUnitCount().intValue());
      additionalInfo.setScannedCaseAttpQtyUOM(ReceivingConstants.Uom.WHPK);
    }
    if ((!additionalInfo.getSkipEvents() || !additionalInfo.getSkipUnitEvents())
        && Objects.nonNull(unitSerialDetailofScannedCase)) {
      unitSerialDetailofScannedCase
          .getItems()
          .forEach(
              unitSerialDetail -> {
                if (RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
                    unitSerialDetail.getReceivingStatus())) {
                  additionalInfo.setSkipEvents(true);
                  additionalInfo.setSkipUnitEvents(true);
                }
              });
    }

    if (null != unitSerialDetailofScannedCase) {
      unitSerialDetailofScannedCase
          .getItems()
          .stream()
          .filter(
              unitSerialDetail ->
                  RxConstants.VALID_ATTP_SERIALIZED_TRACKING_STATUS.equals(
                      unitSerialDetail.getTrackingStatus()))
          .filter(
              unitSerialDetail ->
                  !RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS.equalsIgnoreCase(
                      unitSerialDetail.getReceivingStatus()))
          .filter(unitSerialDetail -> !checkIfReceived(unitSerialDetail.getSerial(), ndc))
          .forEach(
              unitSerialDetail -> {
                unitSerialList.add(
                    RxUtils.getManufactureDetailByPackItem(
                        unitSerialDetail, unitSerialDetailofScannedCase));
                unitGtinSet.add(StringUtils.trim(unitSerialDetail.getGtin()));
                unitLotSet.add(
                    StringUtils.trim(
                        unitSerialDetail.getManufactureDetails().get(0).getLotNumber()));
              });
    }
  }

  private int convertSerializedQtyToEaches(DeliveryDocumentLine line, Pack pack) {
    return ReceivingUtils.conversionToEaches(
        pack.getUnitCount().intValue(),
        ReceivingConstants.Uom.WHPK,
        line.getVendorPack(),
        line.getWarehousePack());
  }

  private boolean isFullCaseCase(Pack pack, DeliveryDocumentLine line) {
    return Objects.nonNull(pack)
        && pack.getUnitCount().intValue() == (line.getVendorPack() / line.getWarehousePack());
  }

  /**
   * This method will call GDM API to update pack/pallet/eaches status to received
   *
   * @param instructionFromDB
   * @param parentContainer
   * @param httpHeaders
   * @param deliveryDocumentLine
   * @throws ReceivingException
   */
  public void callGdmToUpdatePackStatus(
      Instruction instructionFromDB,
      Container parentContainer,
      HttpHeaders httpHeaders,
      DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    String url;
    String request;
    httpHeaders = getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    ResponseEntity<String> response;
    url = appConfig.getGdmBaseUrl() + GDM_UPDATE_STATUS_URL;
    request =
        createRequestToUpdatePackStatus(instructionFromDB, parentContainer, deliveryDocumentLine);
    LOG.info("callGdmToUpdatePackStatus: For  Request={}, Headers={}", request, httpHeaders);
    response = restUtils.put(url, httpHeaders, new HashMap<>(), request);
    if (OK != response.getStatusCode()) {
      LOG.error(
          "Error calling GDM update status API: url={}  Request={}, response={}, Headers={}",
          url,
          request,
          response,
          httpHeaders);
      throw new ReceivingException(
          GDM_UPDATE_STATUS_API_ERROR,
          response.getStatusCode(),
          GDM_UPDATE_STATUS_API_ERROR_CODE,
          GDM_UPDATE_STATUS_API_ERROR_HEADER);
    }
  }

  /**
   * This method will create request for GDM update status API based on the instruction Type
   *
   * @param instruction
   * @param parentContainer
   * @param deliveryDocumentLine
   * @return
   * @throws ReceivingException
   */
  public String createRequestToUpdatePackStatus(
      Instruction instruction, Container parentContainer, DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    ShipmentInfo shipmentInfo = null;
    ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
    String ndc = deliveryDocumentLine.getNdc();
    Set<Container> childContainers = parentContainer.getChildContainers();

    // Pallet Receiving
    if (isPalletReceiving(instruction.getInstructionCode()) && !additionalInfo.isPalletFlowInMultiSku()) {
      if (instruction.getProjectedReceiveQty() == instruction.getReceivedQuantity()
          && !additionalInfo.getPartialPallet()
          && !moreShipmentPacksPresentInGDMThanFbq(deliveryDocumentLine)) {
        shipmentInfo = constructGDMRequestForAllCases(parentContainer, deliveryDocumentLine);
      } else {
        shipmentInfo =
            constructGDMRequestForCasesAndEaches(
                parentContainer, childContainers, additionalInfo, ndc);
      }
    }

    // Case Receiving
    if (isCaseReceiving(instruction.getInstructionCode()) || (Arrays.asList(
                    RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType())
            .contains(instruction.getInstructionCode())  && additionalInfo.isPalletFlowInMultiSku())) {
      if (instruction.getProjectedReceiveQty() == instruction.getReceivedQuantity()
          && null == additionalInfo.getPalletOfCase()
          && StringUtils.isBlank(instruction.getProblemTagId())
          && Boolean.FALSE.equals(additionalInfo.getPartOfMultiSkuPallet())
          && !moreShipmentPacksPresentInGDMThanFbq(deliveryDocumentLine)) {
        shipmentInfo = constructGDMRequestForAllCases(parentContainer, deliveryDocumentLine);
      } else {
        shipmentInfo =
            constructGDMRequestForCasesAndEaches(
                parentContainer, childContainers, additionalInfo, ndc);
      }
    }

    // Partial Receiving
    if (isPartialReceiving(instruction.getInstructionCode())) {
      // fetch unit container data
      shipmentInfo =
          constructGDMRequestForCasesAndEaches(
              parentContainer, childContainers, additionalInfo, ndc);
    }
    return gson.toJson(ShipmentRequest.builder().shipment(shipmentInfo).build());
  }

  private boolean moreShipmentPacksPresentInGDMThanFbq(DeliveryDocumentLine deliveryDocumentLine) {
    return Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())
        && Objects.nonNull(deliveryDocumentLine.getShippedQty())
        && deliveryDocumentLine.getAdditionalInfo().getAttpQtyInEaches()
            > getShippedQtyInEaches(deliveryDocumentLine);
  }

  private Integer getShippedQtyInEaches(DeliveryDocumentLine deliveryDocumentLine) {
    return ReceivingUtils.calculateQuantityFromSourceUOMToDestinationUOM(
        deliveryDocumentLine.getShippedQty(),
        deliveryDocumentLine.getShippedQtyUom(),
        EA,
        deliveryDocumentLine.getVendorPack(),
        deliveryDocumentLine.getWarehousePack());
  }

  /**
   * returns type of receiving
   *
   * @param instructionCode instruction code
   * @return receiving type
   */
  private static String getReceivingType(String instructionCode) {

    if (isPalletReceiving(instructionCode)) {
      return PALLET;
    }
    if (isCaseReceiving(instructionCode)) {
      return CASE;
    }
    if (isCaseReceiving(instructionCode)) {
      return UNIT;
    }

    return StringUtils.EMPTY;
  }

  /**
   * checks if the receiving type is pallet receiving
   *
   * @param instructionCode instruction code
   * @return true or false
   */
  private static boolean isPalletReceiving(String instructionCode) {
    return Arrays.asList(
            RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType(),
            RxInstructionType.RX_SER_MULTI_SKU_PALLET.getInstructionType())
        .contains(instructionCode);
  }

  /**
   * checks if the receiving type is case receiving
   *
   * @param instructionCode instruction code
   * @return true or false
   */
  private static boolean isCaseReceiving(String instructionCode) {
    return Arrays.asList(
            RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionType(),
            RxInstructionType.RX_SER_CNTR_GTIN_AND_LOT.getInstructionType())
        .contains(instructionCode);
  }

  /**
   * checks if the receiving type is partial receiving
   *
   * @param instructionCode instruction code
   * @return true or false
   */
  private static boolean isPartialReceiving(String instructionCode) {
    return RxInstructionType.RX_SER_BUILD_UNITS_SCAN
        .getInstructionType()
        .equalsIgnoreCase(instructionCode);
  }

  /**
   * This method will add frame request for full pallet/cases receiving
   *
   * @param parentContainer
   * @return
   */
  public ShipmentInfo constructGDMRequestForAllCases(
      Container parentContainer, DeliveryDocumentLine deliveryDocumentLine) {
    ShipmentInfo shipmentInfo;
    String documentId = null;
    String shipmentNumber = null;
    PalletData palletData = new PalletData(deliveryDocumentLine.getPalletSSCC());
    Map<String, Object> parentContainerMiscInfo = parentContainer.getContainerMiscInfo();
    if (null != parentContainerMiscInfo.get(ReceivingConstants.DOCUMENT_ID)) {
      documentId = (String) parentContainerMiscInfo.get(ReceivingConstants.DOCUMENT_ID);
    }
    if (null != parentContainerMiscInfo.get(ReceivingConstants.SHIPMENT_NUMBER)) {
      shipmentNumber = (String) parentContainerMiscInfo.get(ReceivingConstants.SHIPMENT_NUMBER);
    }
    shipmentInfo =
        ShipmentInfo.builder()
            .documentId(documentId)
            .shipmentNumber(shipmentNumber)
            .pallets(Collections.singleton(palletData))
            .documentType(ReceivingConstants.EPCIS)
            .receivingStatus(ReceivingConstants.RECEIVED_STATUS)
            .build();

    return shipmentInfo;
  }

  /**
   * This method will construct payload for GDM Status update api for For Cases And eaches in
   * partial scenarios.
   *
   * @param parentContainer
   * @param childContainers
   * @param additionalInfo
   * @param ndc
   * @return
   * @throws ReceivingException
   */
  public ShipmentInfo constructGDMRequestForCasesAndEaches(
      Container parentContainer,
      Set<Container> childContainers,
      ItemData additionalInfo,
      String ndc)
      throws ReceivingException {
    ShipmentInfo shipmentInfo = null;
    String documentId = null;
    String shipmentNumber = null;
    String documentPackId = null;
    Set<PackData> packDataList = new HashSet<>();
    List<PackItemData> packItemDataList = new ArrayList<>();
    Container child = null;
    Container caseContainer = null;
    Optional<Container> childContainer = parentContainer.getChildContainers().stream().findFirst();

    if (childContainer.isPresent()) {
      child = childContainer.get();
      String instructionCode =
          String.valueOf(parentContainer.getContainerMiscInfo().get(INSTRUCTION_CODE));
      int quantitySum =
          child
              .getContainerItems()
              .stream()
              .map(ContainerItem::getQuantity)
              .reduce(0, Integer::sum);
      if (RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType().equals(instructionCode)
          || (quantitySum < child.getContainerItems().get(0).getVnpkQty()
              && (RxInstructionType.RX_SER_CNTR_CASE_SCAN
                      .getInstructionType()
                      .equalsIgnoreCase(instructionCode)
                  || RxInstructionType.RX_SER_CNTR_GTIN_AND_LOT
                      .getInstructionType()
                      .equalsIgnoreCase(instructionCode)))) {
        LOG.info("Frame GDM request for  all eaches {}", child.getTrackingId());
        caseContainer =
            containerService.getContainerWithChildsByTrackingId(child.getTrackingId(), true);
        for (Container eachesContainer : caseContainer.getChildContainers()) {
          Map<String, Object> containerMiscInfo = eachesContainer.getContainerMiscInfo();
          if (null != containerMiscInfo.get(ReceivingConstants.DOCUMENT_ID)) {
            documentId = (String) containerMiscInfo.get(ReceivingConstants.DOCUMENT_ID);
          }
          if (null != containerMiscInfo.get(ReceivingConstants.DOCUMENT_PACK_ID)) {
            documentPackId = (String) containerMiscInfo.get(ReceivingConstants.DOCUMENT_PACK_ID);
          }
          if (null != containerMiscInfo.get(ReceivingConstants.SHIPMENT_NUMBER)) {
            shipmentNumber = (String) containerMiscInfo.get(ReceivingConstants.SHIPMENT_NUMBER);
          }
          packItemDataList.add(
              PackItemData.builder()
                  .documentPackId(documentPackId)
                  .gtin(eachesContainer.getContainerItems().get(0).getGtin())
                  .serial(eachesContainer.getContainerItems().get(0).getSerial())
                  .receivingStatus(ReceivingConstants.RECEIVED_STATUS)
                  .build());
        }
        packDataList.add(
            PackData.builder()
                .documentId(documentId)
                .receivingStatus(ReceivingConstants.RECEIVED_STATUS)
                .documentPackId(documentPackId)
                .items(packItemDataList)
                .build());
        shipmentInfo =
            ShipmentInfo.builder()
                .documentId(documentId)
                .shipmentNumber(shipmentNumber)
                .documentType(ReceivingConstants.EPCIS)
                .packs(packDataList)
                .build();
      } else {
        additionalInfo
            .getSerializedInfo()
            .stream()
            .filter(info -> info.getReportedUom().equalsIgnoreCase(Uom.EACHES))
            .forEach(
                info -> {
                  childContainers
                      .stream()
                      .filter(
                          child2 ->
                              child2
                                  .getTrackingId()
                                  .equalsIgnoreCase(ndc + "_" + info.getSerial()));
                });
        for (Container childC : childContainers) {
          Map<String, Object> containerMiscInfo = childC.getContainerMiscInfo();
          if (null != containerMiscInfo.get(ReceivingConstants.DOCUMENT_ID)) {
            documentId = (String) containerMiscInfo.get(ReceivingConstants.DOCUMENT_ID);
          }
          if (null != containerMiscInfo.get(ReceivingConstants.DOCUMENT_PACK_ID)) {
            documentPackId = (String) containerMiscInfo.get(ReceivingConstants.DOCUMENT_PACK_ID);
          }
          if (null != containerMiscInfo.get(ReceivingConstants.SHIPMENT_NUMBER)) {
            shipmentNumber = (String) containerMiscInfo.get(ReceivingConstants.SHIPMENT_NUMBER);
          }
          packDataList.add(
              PackData.builder()
                  .documentId(documentId)
                  .receivingStatus(ReceivingConstants.RECEIVED_STATUS)
                  .documentPackId(documentPackId)
                  .build());
        }
        shipmentInfo =
            ShipmentInfo.builder()
                .documentId(documentId)
                .shipmentNumber(shipmentNumber)
                .documentType(ReceivingConstants.EPCIS)
                .packs(packDataList)
                .build();
      }
    }
    return shipmentInfo;
  }

  private boolean checkIfReceived(String serial, String ndc) {
    String trackingId = ndc + "_" + serial;
    Container receivedContainer = containerService.findByTrackingId(trackingId);
    if (Objects.isNull(receivedContainer)) {
      return false;
    } else {
      return true;
    }
  }

  public ReceiveContainersResponseBody mockRDSResponseObj(
      String trackingId, SlotDetails slotDetails) {
    ReceiveContainersResponseBody receiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(trackingId);
    Destination destination = new Destination();
    destination.setSlot(slotDetails.getSlot());
    receivedContainer.setDestinations(Collections.singletonList(destination));
    receiveContainersResponseBody.setReceived(Collections.singletonList(receivedContainer));
    return receiveContainersResponseBody;
  }

  public void verifyScanned2DWithSerializedInfoForMultiSku(
          DeliveryDocumentLine deliveryDocumentLine,
          Map<String, ScannedData> scannedDataMap,
          String scannedUOMClient) {
    Map<ItemData, ManufactureDetail> selectedSerialInfoList = new HashMap<>();
    boolean isValidationSuccess = false;
    if (deliveryDocumentLine.getAdditionalInfo().getIsEpcisEnabledVendor()) {
      ManufactureDetail scannedDetails =
              RxUtils.convertScannedDataToManufactureDetail(scannedDataMap);
      List<Pack> serializedCaseInfoList = deliveryDocumentLine.getPacks();
      for (Pack serializedCaseInfo : serializedCaseInfoList) {
        if (scannedDetails.getGtin().equalsIgnoreCase(serializedCaseInfo.getGtin())
                && scannedDetails.getExpiryDate().equalsIgnoreCase(serializedCaseInfo.getExpiryDate())
                && scannedDetails.getLot().equalsIgnoreCase(serializedCaseInfo.getLotNumber())
                && scannedDetails.getSerial().equalsIgnoreCase(serializedCaseInfo.getSerial())
                && scannedUOMClient.equalsIgnoreCase(serializedCaseInfo.getUom())) {
          isValidationSuccess = true;
          break;
        }
      }
      if (!isValidationSuccess) {
        throw new ReceivingBadDataException(
                ExceptionCodes.SCANNED_DETAILS_DO_NOT_MATCH,
                RxConstants.SCANNED_DETAILS_DO_NOT_MATCH_SERIAL);
      }
    }
  }

  public void updateParentMultiSkuInstruction(Instruction instruction , HttpHeaders httpHeaders) {
    Instruction instructionMultiSku =
            rxInstructionHelperService.fetchMultiSkuInstrDeliveryDocumentForCompleteIns(
                    instruction.getDeliveryNumber(),
                    instruction.getPurchaseReferenceNumber(),
                    httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    if (null != instructionMultiSku) {
      DeliveryDocument multiSkuDeliveryDoc =
              gson.fromJson(instructionMultiSku.getDeliveryDocument(), DeliveryDocument.class);
      List<DeliveryDocumentLine> multiSkuDeliveryDocumentLines = getDeliveryDocumentLines(instruction, multiSkuDeliveryDoc);
      multiSkuDeliveryDoc.setDeliveryDocumentLines(multiSkuDeliveryDocumentLines);
      instructionMultiSku.setDeliveryDocument(gson.toJson(multiSkuDeliveryDoc));
      instructionPersisterService.saveInstruction(instructionMultiSku);
    }
  }

  private List<DeliveryDocumentLine> getDeliveryDocumentLines(Instruction instruction, DeliveryDocument multiSkuDeliveryDoc) {
    List<DeliveryDocumentLine> multiSkuDeliveryDocumentLines =
            multiSkuDeliveryDoc.getDeliveryDocumentLines();
    DeliveryDocument deliveryDocumentOfCurrentIns =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine currDeliveryDocumentLine = deliveryDocumentOfCurrentIns.getDeliveryDocumentLines().get(0);
    multiSkuDeliveryDocumentLines.forEach(
            line -> {
              // Checking if the parent ins is having skipEvents true
              if(line.getAdditionalInfo().getSkipEvents()){
                currDeliveryDocumentLine.getAdditionalInfo().setSkipEvents(true);
              }else {
                line.getAdditionalInfo().setSkipEvents(true);
              }
            });
    deliveryDocumentOfCurrentIns.setDeliveryDocumentLines(Collections.singletonList(currDeliveryDocumentLine));
    instruction.setDeliveryDocument(gson.toJson(deliveryDocumentOfCurrentIns));
    return multiSkuDeliveryDocumentLines;
  }
}
