package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.InstructionUtils.createInstruction;
import static com.walmart.move.nim.receiving.core.common.JacksonParser.convertJsonToObject;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GET_PTAG_ERROR_CODE;
import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.*;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchRequest;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedQuantityResponseFromRDS;
import com.walmart.move.nim.receiving.core.client.orderfulfillment.OrderFulfillmentRestApiClient;
import com.walmart.move.nim.receiving.core.client.orderfulfillment.model.PrintShippingLabelRequest;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.item.rules.HazmatValidateRule;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.fixit.ProblemReponse;
import com.walmart.move.nim.receiving.core.model.fixit.ProblemRequest;
import com.walmart.move.nim.receiving.core.model.fixit.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.client.ngr.NgrRestApiClient;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.rdc.model.*;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.utils.RdcAutoReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.collections.CollectionUtils;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

public class RdcReceiveExceptionHandler implements ReceiveExceptionHandler {
  private static final Logger logger = LoggerFactory.getLogger(RdcReceiveExceptionHandler.class);
  @Autowired private MirageRestApiClient mirageRestApiClient;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcExceptionReceivingService rdcExceptionReceivingService;
  @Autowired private Gson gson;
  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired RegulatedItemService regulatedItemService;
  @Autowired NgrRestApiClient ngrRestApiClient;
  @Autowired private RdcReceiveInstructionHandler rdcReceiveInstructionHandler;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private RdcDaService rdcDaService;
  @Autowired private ContainerService containerService;
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private ProblemServiceFixit fixitPlatformService;
  @Autowired private HazmatValidateRule hazmatValidateRule;
  @Autowired private RdcItemServiceHandler rdcItemServiceHandler;
  @Autowired private RdcAutoReceiveService rdcAutoReceiveService;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private LabelDataService labelDataService;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private OrderFulfillmentRestApiClient orderFulfillmentRestApiClient;
  @Autowired private RdcAutoReceivingUtils rdcAutoReceivingUtils;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Autowired private InventoryRestApiClient inventoryRestApiClient;
  @Autowired private RdcContainerService rdcContainerService;

  @Override
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      executionFlow = "receiveException")
  public InstructionResponse receiveException(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {

    logger.info("Receive Exception for request:{}", receiveExceptionRequest);
    InstructionResponse instructionResponse;
    List<DeliveryDocument> deliveryDocuments = receiveExceptionRequest.getDeliveryDocuments();

    // Receive item when delivery documents is present for Hazmat, Limited Item, Breakout
    // and similar exception scenarios
    if (!CollectionUtils.isEmpty(deliveryDocuments)) {
      if (OVERAGE.equals(receiveExceptionRequest.getExceptionMessage())) {
        // Calling FIXIT to create a problem
        return createProblemTicket(deliveryDocuments, receiveExceptionRequest, httpHeaders);
      }
      if (receiveExceptionRequest.getRegulatedItemType() != null) {
        String itemNumber =
            (Objects.nonNull(receiveExceptionRequest.getItemNumber()))
                ? String.valueOf(receiveExceptionRequest.getItemNumber())
                : String.valueOf(
                    receiveExceptionRequest
                        .getDeliveryDocuments()
                        .get(0)
                        .getDeliveryDocumentLines()
                        .get(0)
                        .getItemNbr());
        regulatedItemService.updateVendorComplianceItem(
            VendorCompliance.valueOf(receiveExceptionRequest.getRegulatedItemType()), itemNumber);
        InstructionRequest instructionRequest =
            rdcExceptionReceivingService.getInstructionRequest(
                receiveExceptionRequest, receiveExceptionRequest.getDeliveryDocuments());
        if (rdcReceivingUtils.isNGRServicesEnabled()) {
          ngrRestApiClient.updateHazmatVerificationTsInItemCache(instructionRequest, httpHeaders);
        }
        // Update rejectReason in labelData and HE for Automation enabled sites
        ItemOverrideRequest itemOverrideRequest = new ItemOverrideRequest();
        itemOverrideRequest.setItemNumber(Long.valueOf(itemNumber));
        rdcItemServiceHandler.updateItemRejectReason(null, itemOverrideRequest, httpHeaders);
        receiveExceptionRequest.setVendorComplianceValidated(Boolean.TRUE);
      }
      receiveExceptionRequest.setItemNumber(
          Math.toIntExact(deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemNbr()));
      receiveExceptionRequest.setDeliveryNumbers(
          Collections.singletonList(String.valueOf(deliveryDocuments.get(0).getDeliveryNumber())));
      List<DeliveryDocument> filteredDADeliveryDocuments =
          rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
              receiveExceptionRequest, httpHeaders);
      if (CollectionUtils.isEmpty(filteredDADeliveryDocuments)) {
        return rdcExceptionReceivingService.buildInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
      }
      receiveExceptionRequest.setDeliveryDocuments(filteredDADeliveryDocuments);
      return receiveInstruction(receiveExceptionRequest, httpHeaders);
    }

    if (CollectionUtils.isNotEmpty(receiveExceptionRequest.getLpns())
        && Objects.isNull(receiveExceptionRequest.getExceptionMessage())
        && (receiveExceptionRequest.getLpns().get(0).length() == PRE_LABEL_FREIGHT_LENGTH_16
            || receiveExceptionRequest.getLpns().get(0).length() == RECEIVED_RDC_LABEL_LENGTH_25)) {
      instructionResponse =
          rdcExceptionReceivingService.processExceptionLabel(
              receiveExceptionRequest.getLpns().get(0));
      if (Objects.nonNull(instructionResponse.getInstruction())) {
        return instructionResponse;
      }
    }

    instructionResponse = validateExceptionMessage(receiveExceptionRequest, httpHeaders);
    if (Objects.nonNull(instructionResponse.getInstruction())
        || Objects.nonNull(instructionResponse.getDeliveryDocuments())) {
      return instructionResponse;
    }

    if (!Objects.equals(receiveExceptionRequest.getExceptionMessage(), OVERAGE)) {
      instructionResponse = checkReceivedLabelDetails(receiveExceptionRequest, httpHeaders);
      if (Objects.nonNull(instructionResponse.getInstruction())) {
        return instructionResponse;
      }
    }
    // Receive item
    return receiveInstruction(receiveExceptionRequest, httpHeaders);
  }

  /**
   * If label is available in container then get Atlas response else check in labelData. If
   * labelData has the lpn in AVAILABLE status then receive lpn else process in mirage
   *
   * @param receiveExceptionRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  private InstructionResponse checkReceivedLabelDetails(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplException();
    if (CollectionUtils.isEmpty(receiveExceptionRequest.getLpns())) {
      return instructionResponse;
    }
    Container container =
        containerService.findByTrackingId(receiveExceptionRequest.getLpns().get(0));
    if (Objects.nonNull(container)) {
      instructionResponse =
          getAtlasInstructionResponse(
              container, httpHeaders, receiveExceptionRequest.getDoorNumber());
    } else {
      LabelData labelData = null;
      if (receiveExceptionRequest.getLpns().get(0).length() == RECEIVED_RDC_LABEL_LENGTH_25) {
        labelData =
            labelDataService.findByTrackingIdAndStatus(
                receiveExceptionRequest.getLpns().get(0), LabelInstructionStatus.AVAILABLE.name());
      }
      if (Objects.nonNull(labelData)) {
        if (ReceivingConstants.STAPLE_STOCK_LABEL.equalsIgnoreCase(labelData.getLabel())) {
          return rdcExceptionReceivingService.buildInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
        }
        receiveExceptionRequest.setDeliveryNumbers(
            Collections.singletonList(String.valueOf(labelData.getDeliveryNumber())));
        receiveExceptionRequest.setItemNumber(Math.toIntExact(labelData.getItemNumber()));
        receiveExceptionRequest.setReceiveScannedLpn(true);
        instructionResponse = receiveInstruction(receiveExceptionRequest, httpHeaders);
      } else if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
          getFacilityNum().toString(), IS_ATLAS_EXCEPTION_RECEIVING, false)) {
        instructionResponse = processExceptionInMirage(receiveExceptionRequest, httpHeaders);
      } else {
        instructionResponse =
            rdcExceptionReceivingService.processExceptionLabel(
                receiveExceptionRequest.getLpns().get(0));
      }
    }
    return instructionResponse;
  }

  /**
   * Builds the instruction response for atlas item based on the container status and updates
   * location in inventory
   *
   * @param container
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  private InstructionResponse getAtlasInstructionResponse(
      Container container, HttpHeaders httpHeaders, String locationName) throws ReceivingException {
    if (StringUtils.endsWithIgnoreCase(container.getContainerStatus(), STATUS_BACKOUT)) {
      return rdcExceptionReceivingService.buildInstructionResponse(ERROR_LPN_BACKOUT, null);
    }
    inventoryLocationUpdate(container.getTrackingId(), locationName, httpHeaders);
    if (InventoryStatus.AVAILABLE.name().equalsIgnoreCase(container.getInventoryStatus())) {
      return rdcExceptionReceivingService.buildInstructionResponse(LPN_RECEIVED_SSTK, null);
    }
    Map<String, Object> printJob =
        rdcContainerService.getContainerLabelsByTrackingIds(
            Collections.singletonList(container.getTrackingId()), httpHeaders);
    List<PrintLabelRequest> printLabelRequestList =
        (List<PrintLabelRequest>) printJob.get(PRINT_REQUEST_KEY);
    printJob.replace(
        PRINT_REQUEST_KEY,
        printLabelRequestList
            .stream()
            .filter(
                printLabelRequest ->
                    printLabelRequest.getLabelIdentifier().equals(container.getTrackingId()))
            .collect(Collectors.toList()));
    Instruction instruction = rdcExceptionReceivingService.buildInstruction(EXCEPTION_LPN_RECEIVED);
    // Updating the location in inventory when the label is reprinted
    String slot =
        Objects.nonNull(container.getDestination())
            ? String.valueOf(container.getDestination().get(SLOT))
            : null;
    boolean isAtlasRoutingLabel =
        InventoryStatus.ALLOCATED.name().equalsIgnoreCase(container.getInventoryStatus())
            && Objects.nonNull(slot)
            && DA_R8000_SLOT.equalsIgnoreCase(slot);
    return new InstructionResponseImplException(
        null, null, instruction, printJob, null, false, isAtlasRoutingLabel);
  }

  /**
   * Updates location in inventory for the trackingId
   *
   * @param trackingId
   * @param locationName
   * @param httpHeaders
   */
  private void inventoryLocationUpdate(
      String trackingId, String locationName, HttpHeaders httpHeaders) {
    InventoryLocationUpdateRequest inventoryLocationUpdateRequest =
        InventoryLocationUpdateRequest.builder()
            .trackingIds(Collections.singletonList(trackingId))
            .destinationLocation(DestinationLocation.builder().locationName(locationName).build())
            .build();
    inventoryRestApiClient.updateLocation(inventoryLocationUpdateRequest, httpHeaders);
  }

  private InstructionResponse processExceptionInMirage(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders) {
    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    MirageExceptionRequest mirageExceptionRequest =
        rdcExceptionReceivingService.getMirageExceptionRequest(receiveExceptionRequest);
    // Pre-labelled freight
    if (Objects.isNull(receiveExceptionRequest.getExceptionMessage())
        && receiveExceptionRequest.getLpns().get(0).length() == RECEIVED_RDC_LABEL_LENGTH_18) {
      mirageExceptionRequest.setContainerLabel(mirageExceptionRequest.getLpn());
    }
    try {
      MirageExceptionResponse mirageExceptionResponse =
          mirageRestApiClient.processException(mirageExceptionRequest);
      if (mirageExceptionResponse.isItemReceived()) {
        logger.info("Item {} is already received", receiveExceptionRequest.getItemNumber());
        Map<String, Object> printJob = null;
        printJob =
            LabelGenerator.reprintLabel(
                receiveExceptionRequest,
                mirageExceptionResponse,
                dcTimeZone,
                httpHeaders,
                tenantSpecificConfigReader.isFeatureFlagEnabled(
                    ReceivingConstants.MFC_INDICATOR_FEATURE_FLAG, getFacilityNum()));
        Instruction instruction =
            rdcExceptionReceivingService.buildInstruction(EXCEPTION_LPN_RECEIVED);
        // Updating the location in inventory when the label is reprinted
        if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_ATLAS_PARITY_EXCEPTION_RECEIVING_ENABLED, false)) {
          inventoryLocationUpdate(
              mirageExceptionRequest.getLpn(),
              receiveExceptionRequest.getDoorNumber(),
              httpHeaders);
        }
        return new InstructionResponseImplNew(null, null, instruction, printJob);
      }
      receiveExceptionRequest.setDeliveryNumbers(
          Collections.singletonList(mirageExceptionResponse.getDeliveryNumber()));
      receiveExceptionRequest.setItemNumber((mirageExceptionResponse.getItemNumber()).intValue());
    } catch (RestClientResponseException e) {
      logger.info(
          "Exception e message {} and response body {}",
          e.getMessage(),
          e.getResponseBodyAsString());
      if (ObjectUtils.isNotEmpty(e.getResponseBodyAsString())) {
        MirageLpnExceptionErrorResponse mirageLpnExceptionErrorResponse =
            gson.fromJson(e.getResponseBodyAsString(), MirageLpnExceptionErrorResponse.class);
        return rdcExceptionReceivingService.parseMirageExceptionErrorResponse(
            receiveExceptionRequest, mirageLpnExceptionErrorResponse);
      }
    } catch (ReceivingException receivingException) {
      logger.error(
          "{} {}",
          ReceivingException.RECEIVE_EXCEPTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(receivingException));
    }
    return new InstructionResponseImplNew();
  }

  private InstructionResponse validateExceptionMessage(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    // Exception scan
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    if (Objects.isNull(receiveExceptionRequest.getExceptionMessage())) {
      return instructionResponse;
    }
    switch (receiveExceptionRequest.getExceptionMessage()) {
      case DSDC_AUDIT_LABEL:
      case X_BLOCK:
      case NONCON:
      case INVALID_REQUEST:
      case NO_DATA_ASSOCIATED:
      case SYSTEM_ERROR:
      case RCV_ERROR:
        return rdcExceptionReceivingService.buildInstructionResponse(
            receiveExceptionRequest.getExceptionMessage(), null);
      case SSTK_ATLAS_ITEM:
      case SSTK_INELIGIBLE:
        return rdcExceptionReceivingService.buildInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
      case HAZMAT:
        return handleHazmatException(receiveExceptionRequest, httpHeaders);
      case LITHIUM:
      case LIMITED_ITEM:
        return handleLimitedLithiumException(receiveExceptionRequest, httpHeaders);

      case BREAKOUT:
        List<DeliveryDocument> deliveryDocuments =
            rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
                receiveExceptionRequest, httpHeaders);
        if (CollectionUtils.isEmpty(deliveryDocuments)) {
          return rdcExceptionReceivingService.buildInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
        }
        boolean isBreakPackConveyPicks =
            RdcUtils.isBreakPackConveyPicks(
                deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
        if (!isBreakPackConveyPicks) {
          receiveExceptionRequest.setDeliveryDocuments(deliveryDocuments);
          return receiveInstruction(receiveExceptionRequest, httpHeaders);
        }
        return rdcExceptionReceivingService.buildInstructionResponse(
            receiveExceptionRequest.getExceptionMessage(), deliveryDocuments);
      case NO_BARCODE_SEEN:
      case UPC_NOT_FOUND:
        return processNoUpcException(receiveExceptionRequest, httpHeaders);
      default:
        logger.info(
            "Exception Received: {} with lpn {}",
            receiveExceptionRequest.getExceptionMessage(),
            receiveExceptionRequest.getLpns().get(0));
        return instructionResponse;
    }
  }

  private InstructionResponse handleLimitedLithiumException(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplException();

    List<DeliveryDocument> deliveryDocuments =
        rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
            receiveExceptionRequest, httpHeaders);
    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      return rdcExceptionReceivingService.buildInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
    }
    InstructionRequest instructionRequest =
        rdcExceptionReceivingService.getInstructionRequest(
            receiveExceptionRequest, deliveryDocuments);
    instructionResponse =
        rdcReceivingUtils.checkIfVendorComplianceRequired(
            instructionRequest, deliveryDocuments.get(0), instructionResponse);
    if (ObjectUtils.isEmpty(instructionResponse.getDeliveryDocuments())) {
      receiveExceptionRequest.setDeliveryDocuments(deliveryDocuments);
      return receiveInstruction(receiveExceptionRequest, httpHeaders);
    }
    return instructionResponse;
  }

  private InstructionResponse handleHazmatException(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    InstructionResponse instructionResponse = new InstructionResponseImplException();

    logger.info("fetching the delivery documents from GDM");
    List<DeliveryDocument> deliveryDocuments =
        rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
            receiveExceptionRequest, httpHeaders);
    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      return rdcExceptionReceivingService.buildInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
    }
    boolean isHazmat =
        hazmatValidateRule.validateRule(deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    logger.info(
        "Delivery Details - Delivery Number: {}, Item Number: {}, isHazmat: {}",
        deliveryDocuments.get(0).getDeliveryNumber(),
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemNbr(),
        isHazmat);

    if (!isHazmat) {
      receiveExceptionRequest.setDeliveryDocuments(deliveryDocuments);
      return receiveInstruction(receiveExceptionRequest, httpHeaders);
    }
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setIsHazmat(Boolean.TRUE);
    instructionResponse.setDeliveryDocuments(deliveryDocuments);
    return instructionResponse;
  }

  public InstructionResponse receiveInstruction(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments;
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    if (CollectionUtils.isEmpty(receiveExceptionRequest.getDeliveryDocuments())) {
      deliveryDocuments =
          rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
              receiveExceptionRequest, httpHeaders);
      if (CollectionUtils.isEmpty(deliveryDocuments)) {
        return rdcExceptionReceivingService.buildInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
      }
      receiveExceptionRequest.setDeliveryDocuments(deliveryDocuments);
    } else {
      deliveryDocuments = receiveExceptionRequest.getDeliveryDocuments();
    }

    try {
      AutoReceiveRequest autoReceiveRequest = buildAutoReceiveRequest(receiveExceptionRequest);
      rdcAutoReceivingUtils.validateDeliveryDocuments(
          deliveryDocuments, autoReceiveRequest, httpHeaders);
      rdcInstructionUtils.validateItemXBlocked(
          deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
      autoReceiveRequest.setDeliveryDocuments(deliveryDocuments);
      boolean isBreakPackItem = rdcExceptionReceivingService.validateBreakPack(deliveryDocuments);
      boolean isAtlasConvertedItem =
          deliveryDocuments
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo()
              .isAtlasConvertedItem();
      if (isAtlasConvertedItem && !isBreakPackItem) {
        instructionResponse =
            rdcAutoReceiveService.autoReceiveContainerLpns(autoReceiveRequest, httpHeaders);
      } else {
        if (autoReceiveRequest.isFlibEligible()
            && appConfig
                .getValidItemPackTypeHandlingCodeCombinations()
                .contains(
                    deliveryDocuments
                        .get(0)
                        .getDeliveryDocumentLines()
                        .get(0)
                        .getAdditionalInfo()
                        .getItemPackAndHandlingCode())) {
          httpHeaders.add(IS_REINDUCT_ROUTING_LABEL, String.valueOf(Boolean.TRUE));
        }
        DeliveryDocumentLine deliveryDocumentLine =
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
        String upcNumber = deliveryDocumentLine.getCaseUpc();
        String key =
            deliveryDocumentLine.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
            nimRdsService.getReceivedQtyByDeliveryDocuments(
                deliveryDocuments, httpHeaders, upcNumber);
        ReceiveInstructionRequest receiveInstructionRequest =
            rdcExceptionReceivingService.getReceiveInstructionRequest(
                receiveExceptionRequest, deliveryDocuments);
        receiveInstructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .setTotalReceivedQty(
                receivedQuantityResponseFromRDS
                    .getReceivedQtyMapByPoAndPoLine()
                    .get(key)
                    .intValue());
        receiveInstructionRequest.setMessageId(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
        instructionResponse =
            rdcReceiveInstructionHandler.receiveInstruction(receiveInstructionRequest, httpHeaders);
        // Instruction should be final before assigning it to async function
        InstructionResponse finalInstructionResponse = instructionResponse;
        if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_ATLAS_EXCEPTION_RECEIVING, false)) {
          CompletableFuture.runAsync(
              () -> voidLPNsOnExceptionReceiving(finalInstructionResponse, httpHeaders));
        }
      }
      instructionResponse
          .getInstruction()
          .setInstructionCode(RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionCode());
      instructionResponse
          .getInstruction()
          .setInstructionMsg(RdcInstructionType.RDC_INBOUND_EXCEPTION_RCV.getInstructionMsg());
      return instructionResponse;
    } catch (ReceivingBadDataException receivingBadDataException) {
      if (Objects.equals(receivingBadDataException.getErrorCode(), ExceptionCodes.OVERAGE_ERROR)) {
        return getOverageInstructionResponse(
            deliveryDocuments, receiveExceptionRequest, receivingBadDataException);
      }
      logger.error("Error occurred while receiving exception", receivingBadDataException);
      throw receivingBadDataException;
    }
  }

  public InstructionResponse getOverageInstructionResponse(
      List<DeliveryDocument> deliveryDocuments,
      ReceiveExceptionRequest receiveExceptionRequest,
      ReceivingBadDataException receivingBadDataException)
      throws ReceivingBadDataException {
    boolean ignoreExceptionMessageCheckForOverage =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE, false);
    if (Objects.equals(receivingBadDataException.getErrorCode(), ExceptionCodes.OVERAGE_ERROR)
        && (ignoreExceptionMessageCheckForOverage
            || OVERAGE.equals(receiveExceptionRequest.getExceptionMessage()))) {
      logger.info("Calling getOverageInstructionResponse");
      return rdcExceptionReceivingService.buildInstructionResponse(OVERAGE, deliveryDocuments);
    }
    logger.info(
        "Throwing exception from getOverageInstructionResponse ", receivingBadDataException);
    throw receivingBadDataException;
  }

  public InstructionResponse processNoUpcException(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    if (Objects.isNull(receiveExceptionRequest.getUpcNumber())) {
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_RECEIVE_EXCEPTION_REQUEST, INVALID_UPC);
    }
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    InstructionRequest instructionRequest =
        rdcExceptionReceivingService.getInstructionRequest(receiveExceptionRequest, null);
    List<DeliveryDocument> deliveryDocumentList =
        rdcExceptionReceivingService.fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            receiveExceptionRequest.getDeliveryNumbers(), instructionRequest, httpHeaders);
    if (CollectionUtils.isEmpty(deliveryDocumentList)) {
      return rdcExceptionReceivingService.buildInstructionResponse(LPN_NOT_RECEIVED_SSTK, null);
    }
    boolean multipleDeliveryUpcMatch =
        deliveryDocumentList.stream().map(DeliveryDocument::getDeliveryNumber).distinct().count()
            > 1;

    if (!multipleDeliveryUpcMatch
        && !rdcInstructionUtils.hasMoreUniqueItems(deliveryDocumentList)) {
      // Single document or multiple documents with same items / different PO scenarios
      if (Boolean.TRUE.equals(receiveExceptionRequest.getIsCatalogRequired())) {
        // inner upc scan - Auto select single document and return delivery document for catalog
        Pair<DeliveryDocument, Long> autoSelectedDeliveryDocument =
            rdcInstructionUtils.autoSelectDocumentAndDocumentLine(
                deliveryDocumentList,
                EXCEPTION_RECEIVING_CASE_QUANTITY,
                receiveExceptionRequest.getUpcNumber(),
                httpHeaders);
        instructionResponse.setDeliveryDocuments(
            Collections.singletonList(autoSelectedDeliveryDocument.getKey()));
        return instructionResponse;
      }
      // outer upc scan - Receive the item as cataloging is not required
      receiveExceptionRequest.setDeliveryDocuments(deliveryDocumentList);
      instructionResponse = receiveInstruction(receiveExceptionRequest, httpHeaders);
      return instructionResponse;
    }
    // Return delivery documents for multiple deliveries matched / single delivery multiple item
    // numbers scenarios for both inner upc and outer upc scan
    // Freight Identification scenario
    instructionResponse.setDeliveryDocuments(deliveryDocumentList);
    return instructionResponse;
  }

  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      executionFlow = "getHistoryDeliveriesFromHawkeye")
  public List<String> getHistoryDeliveriesFromHawkeye(
      DeliverySearchRequest deliverySearchRequest, HttpHeaders httpHeaders) {
    logger.info("findDeliveries :{}", deliverySearchRequest);
    Optional<List<String>> deliveryNumbers =
        hawkeyeRestApiClient.getHistoryDeliveriesFromHawkeye(deliverySearchRequest, httpHeaders);
    if (deliveryNumbers.isPresent() && CollectionUtils.isNotEmpty(deliveryNumbers.get())) {
      return deliveryNumbers.get();
    }
    throw new ReceivingBadDataException(
        ExceptionCodes.HAWKEYE_RECEIVE_NO_DELIVERY_FOUND,
        ReceivingConstants.HAWKEYE_RECEIVE_NO_DELIVERY_FOUND_DESCRIPTION);
  }

  @Override
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      executionFlow = "getDeliveryDocumentsForDeliverySearch")
  public List<DeliveryDocument> getDeliveryDocumentsForDeliverySearch(
      DeliverySearchRequest deliverySearchRequest, HttpHeaders httpHeaders) {
    List<String> deliveryNumbers =
        getHistoryDeliveriesFromHawkeye(deliverySearchRequest, httpHeaders);
    logger.info(
        "calling fetchDeliveryDocumentsWithUpcAndMultipleDeliveries to fetch delivery documents with delivery Numbers:{}",
        deliveryNumbers);
    InstructionRequest instructionRequest = getInstructionRequest(deliverySearchRequest);
    List<DeliveryDocument> deliveryDocuments =
        rdcExceptionReceivingService.fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            deliveryNumbers, instructionRequest, httpHeaders);
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocuments.forEach(
        deliveryDocument -> {
          Pair<DeliveryDocument, Long> autoSelectedDeliveryDocument =
              rdcInstructionUtils.autoSelectDocumentAndDocumentLine(
                  Arrays.asList(deliveryDocument),
                  EXCEPTION_RECEIVING_CASE_QUANTITY,
                  instructionRequest.getUpcNumber(),
                  httpHeaders);
          deliveryDocumentList.add(autoSelectedDeliveryDocument.getKey());
        });
    return deliveryDocumentList;
  }

  /**
   * This method is for printing Shipping Label from Routing Label
   *
   * @param trackingId
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @Override
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      executionFlow = "printShippingLabel")
  public Map<String, Object> printShippingLabel(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException {
    Container container = containerService.getContainerByTrackingId(trackingId);
    Map<String, Object> printJob = new HashMap<>();
    if (Objects.nonNull(container)) {
      String locationName = httpHeaders.getFirst(RdcConstants.WFT_LOCATION_NAME);
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      PrintShippingLabelRequest printShippingLabelRequest =
          PrintShippingLabelRequest.builder()
              .routingLabelId(trackingId)
              .stagingLocation(locationName)
              .build();
      TenantContext.get()
          .setOrderFulfillmentShippingLabelFromRoutingLabelStart(System.currentTimeMillis());
      orderFulfillmentRestApiClient.printShippingLabelFromRoutingLabel(
          printShippingLabelRequest, httpHeaders);
      TenantContext.get()
          .setOrderFulfillmentShippingLabelFromRoutingLabelEnd(System.currentTimeMillis());
      calculateAndLogElapsedTimeSummaryForPrintingShippingLabel();
      Map<String, Object> ctrLabel =
          rdcExceptionReceivingService.getPrintRequestPayLoadForShippingLabel(
              container, locationName, userId);
      printJob.put(PRINT_HEADERS_KEY, ctrLabel.get(PRINT_HEADERS_KEY));
      printJob.put(PRINT_CLIENT_ID_KEY, ctrLabel.get(PRINT_CLIENT_ID_KEY));
      for (Map<String, Object> printLabelRequest :
          (List<Map<String, Object>>) ctrLabel.get(PRINT_REQUEST_KEY)) {
        if (trackingId.equals(printLabelRequest.get(PRINT_LABEL_IDENTIFIER))) {
          printJob.put(PRINT_REQUEST_KEY, Collections.singletonList(printLabelRequest));
          return printJob;
        }
      }
    }
    return Collections.emptyMap();
  }

  private void calculateAndLogElapsedTimeSummaryForPrintingShippingLabel() {
    Long timeTakenForOrderFulfillmentShippingLabelFromRoutingLabelEnd =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getOrderFulfillmentShippingLabelFromRoutingLabelStart(),
            TenantContext.get().getOrderFulfillmentShippingLabelFromRoutingLabelEnd());
    logger.warn(
        "LatencyCheck OrderFulfillment Shipping Label from Routing Label at ts={} "
            + "totalOrderFulfillmentShippingLabelFromRoutingLabelEnd={}, and correlationId={}",
        TenantContext.get().getOrderFulfillmentShippingLabelFromRoutingLabelStart(),
        timeTakenForOrderFulfillmentShippingLabelFromRoutingLabelEnd,
        TenantContext.getCorrelationId());
  }

  protected InstructionRequest getInstructionRequest(DeliverySearchRequest deliverySearchRequest) {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId(deliverySearchRequest.getMessageId());
    instructionRequest.setDoorNumber(deliverySearchRequest.getDoorNumber());
    instructionRequest.setUpcNumber(deliverySearchRequest.getUpc());
    instructionRequest.setReceivingType(ReceivingConstants.UPC);
    return instructionRequest;
  }

  public InstructionResponse createProblemTicket(
      List<DeliveryDocument> deliveryDocuments,
      ReceiveExceptionRequest receiveExceptionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    if (Objects.isNull(receiveExceptionRequest.getQuantity())
        || (receiveExceptionRequest.getLpns().size() == 0)) {
      logger.info("Problem Filing case: problem quantity is not valid.");
      throw new ReceivingException(
          ReceivingException.CREATE_PTAG_ERROR_MESSAGE_INVALID_QTY,
          HttpStatus.BAD_REQUEST,
          GET_PTAG_ERROR_CODE);
    }
    String problemRequestString = null;
    String problemTag = null;
    ProblemReponse problemReponse;
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    int problemQty =
        !Objects.isNull(receiveExceptionRequest.getQuantity())
            ? receiveExceptionRequest.getQuantity()
            : receiveExceptionRequest.getLpns().size();

    // Create a problem Request for the problem quantity
    ProblemRequest problemRequest =
        ProblemRequest.builder()
            .apiInvoker(RECEIVING_API_INVOKER)
            .dcNumber(getFacilityNum().toString())
            .deliveryId(String.valueOf(deliveryDocuments.get(0).getDeliveryNumber()))
            .itemNumber(String.valueOf(deliveryDocumentLine.getItemNbr()))
            .itemUPCNumber(String.valueOf(deliveryDocumentLine.getItemUpc()))
            .receivingUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY))
            .palletHi(deliveryDocumentLine.getPalletHigh())
            .palletTi(deliveryDocumentLine.getPalletTie())
            .problemQty(problemQty)
            .timeoutEnabled(Boolean.TRUE)
            .problemType(OVG)
            .purchaseOrderLines(
                Arrays.asList(
                    PurchaseOrderLine.builder()
                        .purchaseReferenceLineNumber(
                            deliveryDocumentLine.getPurchaseReferenceLineNumber())
                        .purchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber())
                        .polineorderqty(deliveryDocumentLine.getOrderableQuantity())
                        .rcvdqtytilldate(deliveryDocumentLine.getTotalReceivedQty())
                        .build()))
            .build();
    // stringify json
    problemRequestString = gson.toJson(problemRequest);
    // call problems function
    problemTag = fixitPlatformService.createProblemTag(problemRequestString);
    logger.debug("ProblemTag: {}", problemTag);
    problemReponse = convertJsonToObject(problemTag, ProblemReponse.class);
    InstructionRequest instructionRequest =
        rdcExceptionReceivingService.getInstructionRequest(
            receiveExceptionRequest, deliveryDocuments);

    Instruction instruction = createInstruction(instructionRequest);

    PrintLabelData printLabelData =
        problemReponse.getProblemFreightSolution().get(0).getPrintDataRequest();

    Map<String, Object> printJob = new HashMap<>();
    if (Objects.nonNull(printLabelData)) {
      printJob.put(PRINT_CLIENT_ID_KEY, printLabelData.getClientId());
      printJob.put(PRINT_HEADERS_KEY, printLabelData.getHeaders());
      printJob.put(PRINT_REQUEST_KEY, printLabelData.getPrintRequests());
    }

    return new InstructionResponseImplNew(
        deliveryDocuments.get(0).getDeliveryStatus().toString(),
        deliveryDocuments,
        instruction,
        printJob);
  }

  /**
   * This will update container in inventory for SSTK received reject cases
   *
   * @param inventoryUpdateRequest
   * @param httpHeaders
   * @throws ReceivingException
   */
  @Override
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      executionFlow = "inventoryContainerUpdate")
  public void inventoryContainerUpdate(
      InventoryUpdateRequest inventoryUpdateRequest, HttpHeaders httpHeaders) {
    logger.info("Inventory Update for request:{}", inventoryUpdateRequest);

    if (!inventoryUpdateRequest.isProcessInLIUI()) {
      inventoryLocationUpdate(
          inventoryUpdateRequest.getTrackingId(), INVENTORY_RE_INDUCT_LOCATION_NAME, httpHeaders);
      return;
    }
    Container container =
        containerPersisterService.getContainerDetails(inventoryUpdateRequest.getTrackingId());
    if (Objects.nonNull(container)) {
      container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
      containerPersisterService.saveContainer(container);
      InventoryPutawayConfirmationRequest inventoryPutawayConfirmationRequest =
          getInventoryPutawayConfirmationRequest(inventoryUpdateRequest.getTrackingId(), container);
      inventoryRestApiClient.sendPutawayConfirmation(inventoryPutawayConfirmationRequest);
      symboticPutawayPublishHelper.publishSymPutawayUpdateOrDeleteMessage(
          container.getTrackingId(),
          container.getContainerItems().get(0),
          PUTAWAY_DELETE_ACTION,
          ReceivingConstants.ZERO_QTY,
          httpHeaders);
    }
  }

  private InventoryPutawayConfirmationRequest getInventoryPutawayConfirmationRequest(
      String trackingId, Container container) {
    InventoryPutawayConfirmationRequest inventoryPutawayConfirmationRequest =
        new InventoryPutawayConfirmationRequest();
    inventoryPutawayConfirmationRequest.setTrackingId(trackingId);
    inventoryPutawayConfirmationRequest.setStatus(COMPLETED_STATUS);
    inventoryPutawayConfirmationRequest.setQuantity(
        container.getContainerItems().get(0).getQuantity());
    inventoryPutawayConfirmationRequest.setQuantityUOM(
        container.getContainerItems().get(0).getQuantityUOM());
    String slot =
        Objects.nonNull(container.getDestination())
            ? String.valueOf(container.getDestination().get(SLOT))
            : null;
    inventoryPutawayConfirmationRequest.setDestinationLocationId(slot);
    inventoryPutawayConfirmationRequest.setItemNumber(
        container.getContainerItems().get(0).getItemNumber());
    inventoryPutawayConfirmationRequest.setForceComplete(true);
    return inventoryPutawayConfirmationRequest;
  }

  private void voidLPNsOnExceptionReceiving(
      InstructionResponse instructionResponse, HttpHeaders httpHeaders) {
    VoidLPNRequest voidLPNRequest = new VoidLPNRequest();
    List<ReceivedQuantityByLines> receivedQuantityByLinesList = new ArrayList<>();
    voidLPNRequest.setDeliveryNumber(
        String.valueOf(instructionResponse.getDeliveryDocuments().get(0).getDeliveryNumber()));
    ReceivedQuantityByLines receivedQuantityByLines =
        ReceivedQuantityByLines.builder()
            .purchaseReferenceNumber(
                instructionResponse.getDeliveryDocuments().get(0).getPurchaseReferenceNumber())
            .purchaseReferenceLineNumber(
                instructionResponse
                    .getDeliveryDocuments()
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getPurchaseReferenceLineNumber())
            .receivedQty(instructionResponse.getInstruction().getReceivedQuantity())
            .build();
    receivedQuantityByLinesList.add(receivedQuantityByLines);
    voidLPNRequest.setReceivedQuantityByLines(receivedQuantityByLinesList);
    mirageRestApiClient.voidLPN(voidLPNRequest, httpHeaders);
  }

  private AutoReceiveRequest buildAutoReceiveRequest(
      ReceiveExceptionRequest receiveExceptionRequest) {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setDeliveryDocuments(receiveExceptionRequest.getDeliveryDocuments());
    autoReceiveRequest.setDeliveryNumber(
        receiveExceptionRequest.getDeliveryDocuments().get(0).getDeliveryNumber());
    autoReceiveRequest.setQuantity(RdcConstants.RDC_AUTO_RECEIVE_QTY);
    autoReceiveRequest.setDoorNumber(receiveExceptionRequest.getDoorNumber());
    autoReceiveRequest.setFeatureType(receiveExceptionRequest.getFeatureType());
    if (receiveExceptionRequest.isReceiveScannedLpn()) {
      autoReceiveRequest.setLpn(receiveExceptionRequest.getLpns().get(0));
    }
    autoReceiveRequest.setFlibEligible(
        isFlibEligible(receiveExceptionRequest.getExceptionMessage()));
    return autoReceiveRequest;
  }

  /**
   * if the exception message is for FLIB InEligible then returns false else true
   *
   * @param exceptionMessage
   * @return
   */
  private boolean isFlibEligible(String exceptionMessage) {
    return CollectionUtils.isNotEmpty(rdcManagedConfig.getFlibInEligibleExceptions())
        && !rdcManagedConfig.getFlibInEligibleExceptions().contains(exceptionMessage);
  }
}
