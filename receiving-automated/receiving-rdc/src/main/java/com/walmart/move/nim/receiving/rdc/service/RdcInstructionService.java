package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.InstructionUtils.checkIfProblemTagPresent;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GET_PTAG_ERROR_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GDM_SSCC_SCAN_ASN_NOT_FOUND;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedQuantityResponseFromRDS;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.client.ngr.NgrRestApiClient;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.MoveEvent;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

public class RdcInstructionService extends InstructionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcInstructionService.class);

  @ManagedConfiguration private AppConfig appconfig;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private Gson gson;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private InstructionStateValidator instructionStateValidator;
  @Autowired private RdcDeliveryService rdcDeliveryService;
  @Autowired private ProblemReceivingHelper problemReceivingHelper;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ContainerService containerService;
  @Autowired private RdcDaService rdcDaService;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired private NgrRestApiClient ngrRestApiClient;
  @Autowired private RdcReceiptBuilder rdcReceiptBuilder;
  @Autowired private RdcInstructionHelper rdcInstructionHelper;
  @Autowired private RdcDsdcService rdcDsdcService;
  @Autowired private RdcItemServiceHandler rdcItemServiceHandler;
  @Autowired private ASNReceivingAuditLogger asnReceivingAuditLogger;
  @Autowired private RdcAtlasDsdcService rdcAtlasDsdcService;

  public void publishContainerAndMove(
      String dockTagId, Container container, HttpHeaders httpHeaders) {

    TenantContext.get().setCreateDTPublishContainerAndMoveCallStart(System.currentTimeMillis());
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false)
        || tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_PARITY_MCPIB_DOCKTAG_PUBLISH_ENABLED,
            false)) {
      LOGGER.info("Publishing docktag:{} info to Inventory", dockTagId);
      Set<Container> childContainerList = new HashSet<>();
      container.setChildContainers(childContainerList);
      containerService.publishDockTagContainer(container);
    }

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_DOCKTAG_MOVE_PUBLISH_ENABLED,
        false)) {
      LOGGER.debug("Publishing Move for the dockTag:{}", dockTagId);
      movePublisher.publishMove(
          1,
          container.getLocation(),
          httpHeaders,
          getMove(dockTagId, httpHeaders),
          MoveEvent.CREATE.getMoveEvent());
    }
    TenantContext.get().setCreateDTPublishContainerAndMoveCallEnd(System.currentTimeMillis());
  }

  private LinkedTreeMap<String, Object> getMove(String dockTagId, HttpHeaders httpHeaders) {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    String toLocation = rdcManagedConfig.getMoveToLocationForDockTag();
    moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, toLocation);
    moveTreeMap.put(
        ReceivingConstants.MOVE_CORRELATION_ID,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    moveTreeMap.put(ReceivingConstants.MOVE_CONTAINER_TAG, dockTagId);
    moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_ON, new Date());
    moveTreeMap.put(
        ReceivingConstants.MOVE_LAST_CHANGED_BY,
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    moveTreeMap.put(ReceivingConstants.MOVE_TYPE_CODE, rdcManagedConfig.getDockTagMoveTypeCode());
    moveTreeMap.put(ReceivingConstants.MOVE_TYPE_DESC, rdcManagedConfig.getDockTagMoveTypeDesc());
    moveTreeMap.put(
        ReceivingConstants.MOVE_PRIORITY, rdcManagedConfig.getDocktagMovePriorityCode());
    return moveTreeMap;
  }

  @Override
  public InstructionResponse serveInstructionRequest(
      String instructionRequestString, HttpHeaders httpHeaders) throws ReceivingException {
    TenantContext.get().setCreateInstrStart(System.currentTimeMillis());
    httpHeaders = RdcUtils.getForwardableHttpHeadersWithLocationInfo(httpHeaders);
    List<DeliveryDocument> gdmDeliveryDocumentList;
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    InstructionRequest instructionRequest =
        gson.fromJson(instructionRequestString, InstructionRequest.class);
    String featureType = instructionRequest.getFeatureType();
    boolean isUserAllowedToReceiveDA = RdcUtils.isUserAllowedToReceiveDaFreight(httpHeaders);
    boolean isWksAndScanToPrintEnabled =
        RdcUtils.isWorkStationAndScanToPrintReceivingModeEnabled(featureType);

    if (isWksAndScanToPrintEnabled) {
      TenantContext.get().setDaCaseReceivingStart(System.currentTimeMillis());
    }
    validateInstructionRequest(instructionRequest);
    validateFeatureTypes(featureType);
    validateLocationHeadersForWorkStationReceiving(featureType, httpHeaders);

    // check if instruction request is for 3 scan docktag
    if (RdcConstants.THREE_SCAN_DOCKTAG_FEATURE_TYPE.equalsIgnoreCase(
        instructionRequest.getFeatureType())) {
      return createInstructionForThreeScanDockTag(
          instructionRequest, instructionResponse, httpHeaders);
    }

    // DSDC Receiving Flow
    if (Objects.nonNull(instructionRequest.getSscc())
        && isUserAllowedToReceiveDA
        && !isAtlasDsdcReceivingEnabled()) {
      instructionResponse =
          rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);
      if (Objects.nonNull(instructionResponse)
          && Objects.nonNull(instructionResponse.getInstruction())) return instructionResponse;
    }

    // check whether dsdc instruction already received SSCC
    Boolean isDsdcInstruction =
        rdcInstructionUtils.checkIfDsdcInstructionAlreadyExists(instructionRequest);
    // check if an instruction already exists with SSCC
    if (!isDsdcInstruction) {
      InstructionResponse instructionResponseWithExistingInstruction =
          checkIfInstructionExistsWithSscc(instructionRequest, httpHeaders);
      if (!Objects.isNull(instructionResponseWithExistingInstruction)) {
        TenantContext.get().setCreateInstrEnd(System.currentTimeMillis());
        calculateAndLogElapsedTimeSummary4ServeInstruction();
        return instructionResponseWithExistingInstruction;
      }
    }

    /*create instruction for given delivery documents, Freight identification & Regulated item
    verification use cases are eligible in this flow */
    if (!CollectionUtils.isEmpty(instructionRequest.getDeliveryDocuments())) {
      return createInstructionByDeliveryDocument(instructionRequest, httpHeaders);
    }

    // get delivery documents from GDM for problem Tag
    if (StringUtils.isNotBlank(instructionRequest.getProblemTagId())) {
      gdmDeliveryDocumentList =
          getDeliveryDocumentsForProblemTagId(instructionRequest, httpHeaders);
      rdcReceivingUtils.updateQuantitiesBasedOnUOM(gdmDeliveryDocumentList);
    } else {
      LOGGER.info(
          "Going to fetch delivery documents from GDM for delivery:{} and UPC:{}",
          instructionRequest.getDeliveryNumber(),
          instructionRequest.getUpcNumber());
      gdmDeliveryDocumentList = fetchDeliveryDocument(instructionRequest, httpHeaders);
      rdcReceivingUtils.updateQuantitiesBasedOnUOM(gdmDeliveryDocumentList);
    }

    /* DSDC Receiving validations: If the delivery documents belong to DSDC SSCC & if Atlas
    DSDC Receivng enabled (GDM onboarded with the DSDC Pack numbers). If the pilot vendor supports DSDC Pack receiving in Atlas,
    receive the DSDC packs in Atlas receiving else receive the packs in RDS*/
    if (Objects.nonNull(instructionRequest.getSscc())
        && isUserAllowedToReceiveDA
        && isAtlasDsdcReceivingEnabled()) {
      //      DSDC validation for when sscc details are not available in gdm. In this case receive
      // pack in RDS
      if (!CollectionUtils.isEmpty(gdmDeliveryDocumentList)
          && gdmDeliveryDocumentList.get(0).getAsnNumber().equals(GDM_SSCC_SCAN_ASN_NOT_FOUND)) {
        // receive dsdc packs in RDS
        instructionResponse =
            rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);
        if (Objects.nonNull(instructionResponse)
            && Objects.nonNull(instructionResponse.getInstruction())) {
          return instructionResponse;
        }
      } else if (ReceivingUtils.isDsdcDeliveryDocuments(gdmDeliveryDocumentList)) {
        Optional<DeliveryDocument> dsdcAtlasDeliveryDocumentOptional =
            gdmDeliveryDocumentList
                .stream()
                .filter(
                    deliveryDocument ->
                        asnReceivingAuditLogger.isVendorEnabledForAtlasDsdcAsnReceiving(
                            deliveryDocument, instructionRequest))
                .findAny();
        if (dsdcAtlasDeliveryDocumentOptional.isPresent()) {
          return rdcAtlasDsdcService.receiveDsdcPacksInAtlas(
              instructionRequest, httpHeaders, gdmDeliveryDocumentList);
        } else {
          // receive dsdc packs in RDS
          instructionResponse =
              rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);
          if (Objects.nonNull(instructionResponse)
              && Objects.nonNull(instructionResponse.getInstruction())) {
            return instructionResponse;
          }
        }
      }
    }

    gdmDeliveryDocumentList =
        rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            gdmDeliveryDocumentList, instructionRequest);

    if (isWksAndScanToPrintEnabled) {
      gdmDeliveryDocumentList =
          rdcInstructionUtils.filterDADeliveryDocuments(
              gdmDeliveryDocumentList, instructionRequest);
      if (!isUserAllowedToReceiveDA) {
        throw new ReceivingBadDataException(
            ExceptionCodes.DA_PURCHASE_REF_TYPE, RdcConstants.DA_PURCHASE_REF_TYPE_MSG);
      }
    }

    if (!isUserAllowedToReceiveDA) {
      gdmDeliveryDocumentList =
          rdcInstructionUtils.filterNonDADeliveryDocuments(
              gdmDeliveryDocumentList, instructionRequest);
    }

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
        false)) {
      rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(gdmDeliveryDocumentList);
    } else {
      nimRdsService.updateAdditionalItemDetails(gdmDeliveryDocumentList, httpHeaders);
    }

    checkIfAtlasConvertedItem(
        instructionRequest.getFeatureType(), gdmDeliveryDocumentList, httpHeaders);

    // scanned UPC matches more item numbers
    if (rdcInstructionUtils.hasMoreUniqueItems(gdmDeliveryDocumentList)) {
      return freightIdentificationResponse(
          instructionRequest, gdmDeliveryDocumentList, httpHeaders);
    }

    instructionRequest.setDeliveryDocuments(gdmDeliveryDocumentList);
    instructionResponse = generateInstruction(instructionRequest, httpHeaders);
    TenantContext.get().setCreateInstrEnd(System.currentTimeMillis());
    calculateAndLogElapsedTimeSummary4ServeInstruction();
    return instructionResponse;
  }

  private boolean isAtlasDsdcReceivingEnabled() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
        false);
  }

  public void validateLocationHeadersForWorkStationReceiving(
      String featureType, HttpHeaders httpHeaders) {
    if (RdcUtils.isWorkStationAndScanToPrintReceivingModeEnabled(featureType)) {
      RdcUtils.validateMandatoryRequestHeaders(httpHeaders);
    }
  }

  public void validateFeatureTypes(String featureType) {
    if (Objects.nonNull(featureType)
        && !Arrays.asList(RdcConstants.RDC_RECEIVING_FEATURE_TYPES).contains(featureType)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.RDC_INVALID_RECEIVING_FEATURE_TYPE,
          RdcConstants.INVALID_RDC_RECEIVING_FEATURE_TYPES);
    }
  }

  private InstructionResponse createInstructionForThreeScanDockTag(
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    boolean isFreightIdentificationRequest = false;
    // skipping gdm call for dsdc labels
    if (StringUtils.isBlank(instructionRequest.getSscc())) {
      if (CollectionUtils.isEmpty(instructionRequest.getDeliveryDocuments())) {
        List<DeliveryDocument> gdmDeliveryDocuments =
            fetchDeliveryDocument(instructionRequest, httpHeaders);
        instructionRequest.setDeliveryDocuments(gdmDeliveryDocuments);
      } else {
        isFreightIdentificationRequest = true;
      }
    }
    return rdcInstructionUtils.createInstructionForThreeScanDocktag(
        instructionRequest, instructionResponse, isFreightIdentificationRequest, httpHeaders);
  }

  private InstructionResponse createInstructionByDeliveryDocument(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    if (Objects.nonNull(instructionRequest.getRegulatedItemType())) {
      LOGGER.info(
          "Calling GDM to update vendor compliance dates for delivery:{} and upcNumber:{}",
          instructionRequest.getDeliveryNumber(),
          instructionRequest.getUpcNumber());
      String itemNumber =
          instructionRequest
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getItemNbr()
              .toString();
      regulatedItemService.updateVendorComplianceItem(
          instructionRequest.getRegulatedItemType(), itemNumber);
      if (rdcReceivingUtils.isNGRServicesEnabled()) {
        ngrRestApiClient.updateHazmatVerificationTsInItemCache(instructionRequest, httpHeaders);
      }
      instructionRequest.setVendorComplianceValidated(Boolean.TRUE);
      // Update rejectReason in labelData and HE for Automation enabled sites
      ItemOverrideRequest itemOverrideRequest = new ItemOverrideRequest();
      itemOverrideRequest.setItemNumber(Long.valueOf(itemNumber));
      rdcItemServiceHandler.updateItemRejectReason(null, itemOverrideRequest, httpHeaders);
    }
    InstructionResponse instructionResponse4UpcReceiving =
        generateInstruction(instructionRequest, httpHeaders);
    TenantContext.get().setCreateInstrEnd(System.currentTimeMillis());
    calculateAndLogElapsedTimeSummary4ServeInstruction();
    return instructionResponse4UpcReceiving;
  }

  private List<DeliveryDocument> getDeliveryDocumentsForProblemTagId(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    List<DeliveryDocument> gdmDeliveryDocumentList;
    FitProblemTagResponse fitProblemTagResponse =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.PROBLEM_SERVICE,
                ProblemService.class)
            .getProblemDetails(instructionRequest.getProblemTagId());
    if (problemReceivingHelper.isContainerReceivable(fitProblemTagResponse)) {
      Resolution resolution = fitProblemTagResponse.getResolutions().get(0);
      gdmDeliveryDocumentList =
          rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
              instructionRequest.getDeliveryNumber(),
              resolution.getResolutionPoNbr(),
              resolution.getResolutionPoLineNbr(),
              httpHeaders);

      /**
       * Since we are fetching delivery documents by PO and PO line, there may be a chance that user
       * should have scanned an invalid UPC. So need to validate whether the scanned UPC and UPC's
       * available in delivery documents are same or not.
       */
      DeliveryDocumentLine gdmDeliveryDocumentLine =
          gdmDeliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
      if (!rdcInstructionUtils.isSameUpc(
          instructionRequest.getUpcNumber(), gdmDeliveryDocumentLine)) {
        LOGGER.error(
            "Scanned UPC: {} is different from all UPC's available in GDM delivery document response",
            instructionRequest.getUpcNumber());
        gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
        throw new ReceivingBadDataException(gdmError.getErrorCode(), gdmError.getErrorMessage());
      }

      final int remainingQtyToReceive =
          fitProblemTagResponse.getRemainingQty() < resolution.getRemainingQty()
              ? fitProblemTagResponse.getRemainingQty()
              : resolution.getRemainingQty();
      gdmDeliveryDocumentList
          .get(0)
          .getDeliveryDocumentLines()
          .get(0)
          .setOpenQty(remainingQtyToReceive);
    } else {
      LOGGER.error(
          "Problem Tag:[{}] is not ready to receive.", instructionRequest.getProblemTagId());
      throw new ReceivingException(
          ReceivingException.PTAG_NOT_READY_TO_RECEIVE, HttpStatus.CONFLICT, GET_PTAG_ERROR_CODE);
    }
    return gdmDeliveryDocumentList;
  }

  /**
   * This method will check if the item is Atlas or Non Atlas items. We invoke Item config service
   * only if the scanned item is SSTK. For DA, we do not invoke Item config service for the parity
   * features. We will invoke item config service based on DA Atlas item conversion logic.This
   * method overrides item pack & handling codes to fetch the latest handling codes.
   *
   * @param featureType
   * @param gdmDeliveryDocumentList
   * @param httpHeaders
   * @throws ReceivingException
   */
  public void checkIfAtlasConvertedItem(
      String featureType, List<DeliveryDocument> gdmDeliveryDocumentList, HttpHeaders httpHeaders)
      throws ReceivingException {
    if (!CollectionUtils.isEmpty(
            rdcInstructionUtils.filterSSTKDeliveryDocuments(gdmDeliveryDocumentList))
        && Objects.isNull(featureType)
        && rdcInstructionUtils.enableAtlasConvertedItemValidationForSSTKReceiving()) {
      rdcInstructionUtils.validateAtlasConvertedItems(gdmDeliveryDocumentList, httpHeaders);
    } else {
      // override item pack & handling code for each item
      gdmDeliveryDocumentList.forEach(
          deliveryDocument -> {
            deliveryDocument
                .getDeliveryDocumentLines()
                .forEach(
                    deliveryDocumentLine -> {
                      rdcReceivingUtils.overrideItemProperties(deliveryDocument);
                    });
          });

      // validate Atlas vs Non atlas DA items
      isAtlasConvertedDaItem(gdmDeliveryDocumentList, httpHeaders);
    }
  }

  /**
   * This method will check if the given DA item is eligible for Atlas converted items. If item
   * config service enabled to support Atlas DA items then invoke item config service to validate
   * atlas or non atlas items. If single PO & POLine, invoke item config service based on item
   * handling code map (CC), so we can avoid the traffic to item config service for all DA items. If
   * Item config service is not enabled, we have configs basd logic to determine atlas vs non atlas
   * items. The configs are vendor , item & packAndHandlingCode based
   *
   * @param deliveryDocuments
   */
  public void isAtlasConvertedDaItem(
      List<DeliveryDocument> deliveryDocuments, HttpHeaders httpHeaders) throws ReceivingException {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
        false)) {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          RdcConstants.IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS,
          false)) {
        /* to restrict traffic on item config service, added specific item pack handling code validation
        for atlas item conversion on singlePoPoLine. For Multi POs/delivery documents
        it's still fine to hit item config service as the Mutli PO use cases are not so often */
        if (rdcInstructionUtils.isSinglePoAndPoLine(deliveryDocuments)) {
          if (isPackHandlingCodeEligibleForAtlasItemConversion(deliveryDocuments.get(0))) {
            rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocuments, httpHeaders);
          }
        } else {
          rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocuments, httpHeaders);
        }
      } else {
        /* validate item config for certain handling codes (BC, CN)until we ramp all other type of
        handling codes */
        if (rdcInstructionUtils.isValidPackHandlingCodeForItemConfigApi(deliveryDocuments)) {
          rdcInstructionUtils.validateAtlasConvertedItems(deliveryDocuments, httpHeaders);
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

  private boolean isPackHandlingCodeEligibleForAtlasItemConversion(
      DeliveryDocument deliveryDocument) {
    return rdcManagedConfig
        .getDaAtlasItemEnabledPackHandlingCode()
        .contains(
            deliveryDocument
                .getDeliveryDocumentLines()
                .get(0)
                .getAdditionalInfo()
                .getItemPackAndHandlingCode());
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

  private InstructionResponse freightIdentificationResponse(
      InstructionRequest instructionRequest,
      List<DeliveryDocument> gdmDeliveryDocumentList,
      HttpHeaders httpHeaders) {
    InstructionResponse freightIdentificationResponse = new InstructionResponseImplNew();
    LOGGER.info(
        "Scanned UPC:{} matches with more than one item on this delivery:{}",
        instructionRequest.getUpcNumber(),
        instructionRequest.getDeliveryNumber());
    Boolean isAsnMultiSkuReceivingEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false)
        || isAsnMultiSkuReceivingEnabled) {
      rdcInstructionUtils.populateOpenAndReceivedQtyInDeliveryDocuments(
          gdmDeliveryDocumentList, httpHeaders, instructionRequest.getUpcNumber());

      MultiSkuService multiSkuService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.ASN_MULTISKU_HANDLER,
              MultiSkuService.class);
      multiSkuService.handleMultiSku(
          isAsnMultiSkuReceivingEnabled,
          instructionRequest,
          freightIdentificationResponse,
          new Instruction());
      freightIdentificationResponse.setDeliveryDocuments(gdmDeliveryDocumentList);
      TenantContext.get().setCreateInstrEnd(System.currentTimeMillis());
      calculateAndLogElapsedTimeSummary4ServeInstruction();
      return freightIdentificationResponse;
    }
    throw new ReceivingBadDataException(
        ExceptionCodes.MULTI_ITEM_FOUND_BY_UPC,
        String.format(
            ReceivingException.MULTIPLE_ITEM_FOUND_BY_UPC, instructionRequest.getUpcNumber()),
        instructionRequest.getUpcNumber());
  }

  private InstructionResponse checkIfInstructionExistsWithSscc(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
    return rdcInstructionUtils.checkIfInstructionExistsWithSsccAndValidateInstruction(
        instructionRequest, httpHeaders);
  }

  /*
   * This method processes the delivery documents retrieved from GDM and
   * creates instruction based on TiXHi value. If we get more than on PO line or
   * PO for the scanned UPC then auto PO selection logic will get applied.
   *
   * @param instructionRequest
   * @param httpHeaders
   * @return InstructionResponse
   * @throws ReceivingException
   *
   */
  public InstructionResponse generateInstruction(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    List<DeliveryDocument> gdmDeliveryDocumentList = instructionRequest.getDeliveryDocuments();
    boolean isUserAllowedToReceiveDA = RdcUtils.isUserAllowedToReceiveDaFreight(httpHeaders);
    boolean isDaQtyReceiving =
        !RdcUtils.isWorkStationAndScanToPrintReceivingModeEnabled(
            instructionRequest.getFeatureType());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(
            gdmDeliveryDocumentList, httpHeaders, instructionRequest.getUpcNumber());

    if (isUserAllowedToReceiveDA) {
      if (RdcUtils.isWorkStationAndScanToPrintReceivingModeEnabled(
          instructionRequest.getFeatureType())) {
        Pair<DeliveryDocument, Long> autoSelectedDeliveryDocumentPair =
            rdcInstructionUtils.autoSelectPoPoLine(
                gdmDeliveryDocumentList,
                receivedQuantityResponseFromRDS,
                RdcConstants.QTY_TO_RECEIVE,
                instructionRequest.getUpcNumber());
        DeliveryDocument autoSelectedDeliveryDocument = autoSelectedDeliveryDocumentPair.getKey();
        Integer totalReceivedQty = autoSelectedDeliveryDocumentPair.getValue().intValue();
        if (!isDaQtyReceiving && rdcInstructionUtils.isSSTKDocument(autoSelectedDeliveryDocument)) {
          /**
           * The below logic to check if the scanned item is already reached Max overage or not. If
           * the scanned UPC is SSTK & max overage allowed we need to let the user to create problem
           * ticket from WorkStation/Mobile hence return Overage Alert instruction to the users.
           */
          Boolean maxOverageReceived =
              rdcReceivingUtils.checkIfMaxOverageReceived(
                  autoSelectedDeliveryDocument,
                  autoSelectedDeliveryDocumentPair.getValue(),
                  RdcConstants.QTY_TO_RECEIVE);
          if (maxOverageReceived) {
            Instruction instruction =
                rdcInstructionUtils.getOverageAlertInstruction(instructionRequest, httpHeaders);
            InstructionResponse instructionResponse = new InstructionResponseImplNew();
            instructionResponse.setInstruction(instruction);
            populateReceivedQtyDetailsInDeliveryDocument(
                autoSelectedDeliveryDocument, totalReceivedQty);
            instructionResponse.setDeliveryDocuments(
                Collections.singletonList(autoSelectedDeliveryDocument));
            return instructionResponse;
          }
          // block SSTK freights in Workstation / Scan to print receiving
          throw new ReceivingBadDataException(
              ExceptionCodes.NON_DA_PURCHASE_REF_TYPE, RdcConstants.NON_DA_PURCHASE_REF_TYPE_MSG);
        }
        instructionRequest.setDeliveryDocuments(
            Collections.singletonList(autoSelectedDeliveryDocument));
        return rdcDaService.createInstructionForDACaseReceiving(
            instructionRequest, autoSelectedDeliveryDocumentPair.getValue(), httpHeaders);
      } else {
        Pair<DeliveryDocument, Long> autoSelectedDeliveryDocument =
            rdcInstructionUtils.autoSelectPoPoLine(
                gdmDeliveryDocumentList,
                receivedQuantityResponseFromRDS,
                RdcConstants.QTY_TO_RECEIVE,
                instructionRequest.getUpcNumber());
        DeliveryDocument deliveryDocument = autoSelectedDeliveryDocument.getKey();
        instructionRequest.setDeliveryDocuments(
            Collections.singletonList(autoSelectedDeliveryDocument.getKey()));
        // Check if any problem tag exists => throw exception
        if (rdcInstructionUtils.isProblemTagValidationApplicable(
            instructionRequest.getDeliveryDocuments())) {
          checkIfProblemTagPresent(
              instructionRequest.getUpcNumber(),
              instructionRequest.getDeliveryDocuments().get(0),
              appconfig.getProblemTagTypesList());
        }
        if (rdcInstructionUtils.isSSTKDocument(deliveryDocument)) {
          return getSSTKInstruction(
              instructionRequest, httpHeaders, receivedQuantityResponseFromRDS);
        } else if (rdcInstructionUtils.isDADocument(deliveryDocument)) {
          if (rdcInstructionUtils.isSplitPalletInstruction(instructionRequest)) {
            throw new ReceivingBadDataException(
                ExceptionCodes.SPLIT_PALLET_RECEIVING_NOT_SUPPORTED_FOR_DA_FREIGHT,
                RdcConstants.SPLIT_PALLET_RECEIVING_NOT_SUPPORTED_FOR_DA_FREIGHT);
          }
          return rdcDaService.createInstructionForDACaseReceiving(
              instructionRequest, autoSelectedDeliveryDocument.getValue(), httpHeaders);
        } else {
          LOGGER.error(
              "Found DSDC PO for the given delivery:{} and UPC:{}",
              instructionRequest.getDeliveryNumber(),
              instructionRequest.getUpcNumber());
          throw new ReceivingBadDataException(
              ExceptionCodes.DSDC_PURCHASE_REF_TYPE, RdcConstants.DSDC_PURCHASE_REF_TYPE);
        }
      }
    } else {
      return getSSTKInstruction(instructionRequest, httpHeaders, receivedQuantityResponseFromRDS);
    }
  }

  private void populateReceivedQtyDetailsInDeliveryDocument(
      DeliveryDocument deliveryDocument, Integer totalReceivedQty) {
    Integer openQty =
        deliveryDocument.getDeliveryDocumentLines().get(0).getTotalOrderQty() - totalReceivedQty;
    Integer maxReceiveQuantity =
        deliveryDocument.getDeliveryDocumentLines().get(0).getTotalOrderQty()
            + deliveryDocument.getDeliveryDocumentLines().get(0).getOverageQtyLimit();
    deliveryDocument.getDeliveryDocumentLines().get(0).setOpenQty(openQty);
    deliveryDocument.getDeliveryDocumentLines().get(0).setMaxReceiveQty(maxReceiveQuantity);
    deliveryDocument.getDeliveryDocumentLines().get(0).setTotalReceivedQty(totalReceivedQty);
  }
  /**
   * @param instructionRequest
   * @param httpHeaders
   * @param receivedQuantityResponseFromRDS
   * @return
   * @throws ReceivingException
   */
  private InstructionResponse getSSTKInstruction(
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders,
      ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS)
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    List<DeliveryDocument> filteredSSTKDocuments =
        rdcInstructionUtils.filterSSTKDeliveryDocuments(deliveryDocuments);

    if (!CollectionUtils.isEmpty(filteredSSTKDocuments)) {
      // check all SSTK items fulfilled only when multi po/poLines exists
      if (!rdcInstructionUtils.isSinglePoAndPoLine(filteredSSTKDocuments)) {
        filteredSSTKDocuments =
            rdcInstructionUtils.checkAllSSTKPoFulfilled(
                filteredSSTKDocuments, instructionRequest, receivedQuantityResponseFromRDS);
      }
      Pair<DeliveryDocument, Long> autoSelectedSSTKDeliveryDocument =
          rdcInstructionUtils.autoSelectPoPoLine(
              filteredSSTKDocuments,
              receivedQuantityResponseFromRDS,
              RdcConstants.QTY_TO_RECEIVE,
              instructionRequest.getUpcNumber());
      instructionRequest.setDeliveryDocuments(
          Collections.singletonList(autoSelectedSSTKDeliveryDocument.getKey()));
      return rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
          instructionRequest, autoSelectedSSTKDeliveryDocument.getValue(), httpHeaders);
    } else {
      rdcInstructionUtils.filterNonDADeliveryDocuments(deliveryDocuments, instructionRequest);
      LOGGER.error(
          "Received delivery document with invalid channel method for delivery: {} and UPC: {}",
          instructionRequest.getDeliveryNumber(),
          instructionRequest.getUpcNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.UNSUPPORTED_PURCHASE_REF_TYPE_FOR_RDC,
          String.format(
              RdcConstants.UNSUPPORTED_PURCHASE_REF_TYPE_MSG_FOR_RDC,
              instructionRequest.getDeliveryNumber(),
              instructionRequest.getUpcNumber()));
    }
  }

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public InstructionSummary cancelInstruction(Long instructionId, HttpHeaders httpHeaders)
      throws ReceivingException {
    try {
      TenantContext.get().setCancelInstrStart(System.currentTimeMillis());
      Instruction instruction = instructionPersisterService.getInstructionById(instructionId);
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      List<String> labelTrackingIds = new ArrayList<>();
      List<Receipt> receipts = new ArrayList<>();
      if (rdcInstructionUtils.isCancelInstructionAllowed(instruction, userId)) {
        // split pallet
        if (Objects.nonNull(instruction.getInstructionSetId())) {
          // delete containers and container items
          if (Objects.nonNull(instruction.getContainer())
              && instruction.getReceivedQuantity() > 0) {
            List<Container> containerByInstruction =
                containerService.getContainerByInstruction(instructionId);
            for (Container container : containerByInstruction) {
              labelTrackingIds.add(container.getTrackingId());
            }
            if (rdcInstructionUtils.isAtlasConvertedInstruction(instruction)) {
              int backoutQty = instruction.getReceivedQuantity() * -1;
              receipts.add(rdcReceiptBuilder.buildReceipt(instruction, userId, backoutQty));
            }
          }
        }
        // Complete instruction with received quantity as ZERO
        instruction.setReceivedQuantity(0);
        instruction.setCompleteUserId(userId);
        instruction.setCompleteTs(new Date());
        rdcInstructionHelper.persistForCancelInstructions(
            labelTrackingIds, receipts, Arrays.asList(instruction));
        InstructionSummary instructionSummary =
            InstructionUtils.convertToInstructionSummary(instruction);
        TenantContext.get().setCancelInstrEnd(System.currentTimeMillis());
        calculateAndLogElapsedTimeSummary4CancelInstruction();
        return instructionSummary;
      }
    } catch (ReceivingException re) {
      Object errorMessage = re.getErrorResponse().getErrorMessage();
      String errorCode = re.getErrorResponse().getErrorCode();
      String errorHeader = re.getErrorResponse().getErrorHeader();
      throw new ReceivingException(
          Objects.nonNull(errorMessage)
              ? errorMessage
              : ReceivingException.CANCEL_INSTRUCTION_ERROR_MSG,
          HttpStatus.INTERNAL_SERVER_ERROR,
          Objects.nonNull(errorCode) ? errorCode : ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE,
          Objects.nonNull(errorHeader)
              ? errorHeader
              : ReceivingException.CANCEL_INSTRUCTION_ERROR_HEADER);
    } catch (Exception exception) {
      LOGGER.error("{} {}", ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE, exception);
      throw new ReceivingException(
          ReceivingException.CANCEL_INSTRUCTION_ERROR_MSG,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE);
    }
    TenantContext.get().setCancelInstrEnd(System.currentTimeMillis());
    calculateAndLogElapsedTimeSummary4CancelInstruction();
    return null;
  }

  public void validateInstructionRequest(InstructionRequest instructionRequest)
      throws ReceivingException {
    if (StringUtils.isAllEmpty(instructionRequest.getUpcNumber(), instructionRequest.getSscc())) {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.NO_UPC_ERROR);
      LOGGER.error(instructionError.getErrorMessage());
      throw new ReceivingException(
          instructionError.getErrorMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR,
          instructionError.getErrorCode(),
          instructionError.getErrorHeader());
    }
  }

  private void calculateAndLogElapsedTimeSummary4ServeInstruction() {

    long timeTakenForCreateInstrUpdateComplianceDateToGDMCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateInstrUpdateComplianceDateToGDMCallStart(),
            TenantContext.get().getCreateInstrUpdateComplianceDateToGDMCallEnd());

    long timeTakenForCreateInstr4UpcReceivingCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateInstr4UpcReceivingCallStart(),
            TenantContext.get().getCreateInstr4UpcReceivingCallEnd());

    long timeTakenForCreateInstrUpdateItemDetailsNimRdsCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateInstrUpdateItemDetailsNimRdsCallStart(),
            TenantContext.get().getCreateInstrUpdateItemDetailsNimRdsCallEnd());

    long totaltimeTakenForCreateInstr =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateInstrStart(), TenantContext.get().getCreateInstrEnd());

    LOGGER.warn(
        "LatencyCheck CreateInstruction at ts={} time in timeTakenForCreateInstrUpdateComplianceDateToGDMCall={}, "
            + "timeTakenForCreateInstr4UpcReceivingCall={}, timeTakenForCreateInstrUpdateItemDetailsNimRdsCall={}, "
            + "totaltimeTakenForCreateInstr={}, and correlationId={}",
        TenantContext.get().getCreateInstrStart(),
        timeTakenForCreateInstrUpdateComplianceDateToGDMCall,
        timeTakenForCreateInstr4UpcReceivingCall,
        timeTakenForCreateInstrUpdateItemDetailsNimRdsCall,
        totaltimeTakenForCreateInstr,
        TenantContext.getCorrelationId());
  }

  private void calculateAndLogElapsedTimeSummary4CancelInstruction() {
    long totaltimeTakenForCancelInstr =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCancelInstrStart(), TenantContext.get().getCancelInstrEnd());

    LOGGER.warn(
        "LatencyCheck CancelInstruction at ts={} time in totaltimeTakenForCancelInstr={} and correlationId={}",
        TenantContext.get().getCreateInstrStart(),
        totaltimeTakenForCancelInstr,
        TenantContext.getCorrelationId());
  }

  private void calculateAndLogElapsedTimeSummary4RefreshInstruction() {

    long timeTakenForRefreshInstrDBCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getRefreshInstrDBCallStart(),
            TenantContext.get().getRefreshInstrDBCallEnd());

    long timeTakenForRefreshInstrValidateExistringInstrCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getRefreshInstrValidateExistringInstrCallStart(),
            TenantContext.get().getRefreshInstrValidateExistringInstrCallEnd());

    long timeTakenForRefreshInstrAtlasRcvChkNewInstCanBeCreatedCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvChkNewInstCanBeCreatedStart(),
            TenantContext.get().getAtlasRcvChkNewInstCanBeCreatedEnd());

    long timeTakenForRefreshInstrUpdateItemDetailsNimRdsCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getCreateInstrUpdateItemDetailsNimRdsCallStart(),
            TenantContext.get().getCreateInstrUpdateItemDetailsNimRdsCallEnd());

    long totaltimeTakenForRefreshInstr =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getRefreshInstrStart(), TenantContext.get().getRefreshInstrEnd());

    LOGGER.warn(
        "LatencyCheck RefreshInstruction at ts={} time in timeTakenForRefreshInstrDBCall={}, "
            + "timeTakenForRefreshInstrValidateExistringInstrCall={}, timeTakenForRefreshInstrAtlasRcvChkNewInstCanBeCreatedCall={}, "
            + "timeTakenForRefreshInstrUpdateItemDetailsNimRdsCall={}, totaltimeTakenForCreateInstr={}, and correlationId={}",
        TenantContext.get().getCreateInstrStart(),
        timeTakenForRefreshInstrDBCall,
        timeTakenForRefreshInstrValidateExistringInstrCall,
        timeTakenForRefreshInstrAtlasRcvChkNewInstCanBeCreatedCall,
        timeTakenForRefreshInstrUpdateItemDetailsNimRdsCall,
        totaltimeTakenForRefreshInstr,
        TenantContext.getCorrelationId());
  }
}
