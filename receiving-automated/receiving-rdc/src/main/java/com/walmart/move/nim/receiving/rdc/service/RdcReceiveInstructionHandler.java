package com.walmart.move.nim.receiving.rdc.service;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.nimrds.AsyncNimRdsRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersRequestBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.move.MoveType;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponseWithRdsResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.CommonLabelDetails;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.rdc.model.ReceivedQuantityByLines;
import com.walmart.move.nim.receiving.rdc.model.VoidLPNRequest;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
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
import org.springframework.transaction.annotation.Transactional;

public class RdcReceiveInstructionHandler implements ReceiveInstructionHandler {

  private static final Logger logger = LoggerFactory.getLogger(RdcReceiveInstructionHandler.class);

  @Autowired private Gson gson;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private InstructionStateValidator instructionStateValidator;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired private PrintJobService printJobService;
  @Autowired private ReceiptService receiptService;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcLpnUtils rdcLpnUtils;
  @Autowired private MirageRestApiClient mirageRestApiClient;
  @Autowired private RdcDeliveryService rdcDeliveryService;
  @Autowired private RdcSlottingUtils rdcSlottingUtils;
  @Autowired private AsyncNimRdsRestApiClient asyncNimRdsRestApiClient;
  @Autowired private NimRDSRestApiClient nimRDSRestApiClient;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @ManagedConfiguration private AppConfig appconfig;
  @Autowired private RdcDaService rdcDaService;
  @Autowired private RdcLabelGenerationService rdcLabelGenerationService;
  @Autowired private RdcSSTKInstructionUtils rdcSSTKInstructionUtils;
  @Autowired private RdcLabelGenerationUtils rdcLabelGenerationUtils;

  @Override
  @Transactional(rollbackFor = ReceivingBadDataException.class)
  @InjectTenantFilter
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.INTERNAL,
      executionFlow = "receiveInstruction")
  public InstructionResponse receiveInstruction(
      Long instructionId,
      ReceiveInstructionRequest receiveInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingBadDataException {

    TenantContext.get().setReceiveInstrStart(System.currentTimeMillis());
    logger.info("Receive Instruction for instruction id:{}", instructionId);
    String labelTrackingId = null;
    ReceivedContainer receivedContainer = null;
    boolean rollbackForException = false;
    boolean isAtlasConvertedItem = false;
    String destinationSlot = null;

    try {
      RdcUtils.validateMandatoryRequestHeaders(httpHeaders);
      Instruction instruction4mDB = instructionPersisterService.getInstructionById(instructionId);
      instructionStateValidator.validate(instruction4mDB);
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      ReceivingUtils.verifyUser(instruction4mDB, userId, RequestType.COMPLETE);

      DeliveryDocument deliveryDocument =
          gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class);
      receiveInstructionRequest.setDeliveryDocumentLines(
          deliveryDocument.getDeliveryDocumentLines());
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);

      isAtlasConvertedItem = deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem();

      rdcInstructionUtils.verifyAndPopulateProDateInfo(
          deliveryDocument, instruction4mDB, httpHeaders);

      rdcInstructionUtils.validateOverage(
          receiveInstructionRequest.getDeliveryDocumentLines(),
          receiveInstructionRequest.getQuantity(),
          instruction4mDB,
          httpHeaders);

      if (isAtlasConvertedItem) {
        // get 18 digit LPN to receive atlas converted items in smart slotting
        labelTrackingId = rdcLpnUtils.getLPNs(1, httpHeaders).get(0);
        TenantContext.get().setReceiveInstrGetSlotCallStart(System.currentTimeMillis());
        SlottingPalletResponse slottingPalletResponse =
            rdcSlottingUtils.receiveContainers(
                receiveInstructionRequest, labelTrackingId, httpHeaders, null);
        TenantContext.get().setReceiveInstrGetSlotCallEnd(System.currentTimeMillis());
        RdcUtils.populateSlotInfoInDeliveryDocument(slottingPalletResponse, deliveryDocumentLine);
        receivedContainer = new ReceivedContainer();
        Destination destination = new Destination();
        receivedContainer.setLabelTrackingId(labelTrackingId);
        receivedContainer.setPoNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
        receivedContainer.setPoLine(deliveryDocumentLine.getPurchaseReferenceLineNumber());
        destination.setSlot(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
        destination.setSlot_size(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize());
        receivedContainer.setDestinations(Collections.singletonList(destination));
        destinationSlot = receivedContainer.getDestinations().get(0).getSlot();

        receiveInstructionRequest.setDeliveryDocumentLines(
            Collections.singletonList(deliveryDocumentLine));
        deliveryDocument.setDeliveryDocumentLines(
            receiveInstructionRequest.getDeliveryDocumentLines());
      } else {
        // Non Atlas item - Invoke smart slotting to receive containers in RDS
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false)) {
          Map<Instruction, SlotDetails> instructionSlotDetailsMap = new HashMap<>();
          instruction4mDB.setReceivedQuantity(receiveInstructionRequest.getQuantity());
          instruction4mDB.setReceivedQuantityUOM(receiveInstructionRequest.getQuantityUOM());
          instructionSlotDetailsMap.put(
              instruction4mDB, receiveInstructionRequest.getSlotDetails());

          ReceiveContainersRequestBody receiveContainersRequestBody =
              nimRdsService.getReceiveContainersRequestBody(instructionSlotDetailsMap, userId);

          TenantContext.get()
              .setReceiveInstrGetSlotCallWithRdsPayloadStart(System.currentTimeMillis());
          SlottingPalletResponse slottingPalletResponse =
              rdcSlottingUtils.receiveContainers(
                  receiveInstructionRequest,
                  labelTrackingId,
                  httpHeaders,
                  receiveContainersRequestBody);
          TenantContext.get()
              .setReceiveInstrGetSlotCallWithRdsPayloadEnd(System.currentTimeMillis());

          if (slottingPalletResponse instanceof SlottingPalletResponseWithRdsResponse) {
            SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse =
                (SlottingPalletResponseWithRdsResponse) slottingPalletResponse;
            receivedContainer = slottingPalletResponseWithRdsResponse.getRds().getReceived().get(0);
          }
        } else {
          // Receive containers in RDS
          ReceiveContainersResponseBody receiveContainersResponseBody =
              getReceiveContainersResponseBody(
                  instruction4mDB, receiveInstructionRequest, httpHeaders);
          receivedContainer = receiveContainersResponseBody.getReceived().get(0);
        }
        if (Objects.nonNull(receivedContainer)) {
          labelTrackingId = receivedContainer.getLabelTrackingId();
          destinationSlot = receivedContainer.getDestinations().get(0).getSlot();
        }
      }

      UpdateInstructionRequest updateInstructionRequest =
          getUpdateInstructionRequest(
              receiveInstructionRequest, instruction4mDB.getDeliveryNumber());

      PrintJob printJob =
          createReceiptsAndContainer(
              updateInstructionRequest,
              instruction4mDB,
              deliveryDocument,
              receiveInstructionRequest.getQuantity(),
              userId,
              labelTrackingId,
              destinationSlot);

      TenantContext.get().setReceiveInstrCreateLabelCallStart(System.currentTimeMillis());
      String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());

      String slot =
          Objects.nonNull(receivedContainer.getDestinations().get(0).getSlot())
              ? receivedContainer.getDestinations().get(0).getSlot()
              : StringUtils.EMPTY;
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.ENABLE_SYM3_SLOT_CHANGE_IN_LABEL,
          false)) {
        String handlingMethodCode =
            receiveInstructionRequest
                .getDeliveryDocumentLines()
                .get(0)
                .getAdditionalInfo()
                .getHandlingCode();
        slot = getSlot(slot, handlingMethodCode);
      }
      CommonLabelDetails commonLabelDetails =
          CommonLabelDetails.builder()
              .labelTrackingId(labelTrackingId)
              .slot(slot)
              .slotSize(receivedContainer.getDestinations().get(0).getSlot_size())
              .receiver(receivedContainer.getReceiver())
              .build();
      LabelFormat labelFormat =
          rdcReceivingUtils.getLabelFormatForPallet(
              receiveInstructionRequest.getDeliveryDocumentLines().get(0));
      Map<String, Object> printLabelData =
          LabelGenerator.generatePalletLabels(
              receiveInstructionRequest.getDeliveryDocumentLines().get(0),
              receiveInstructionRequest.getQuantity(),
              commonLabelDetails,
              printJob.getId(),
              httpHeaders,
              ReceivingUtils.getDCDateTime(dcTimeZone),
              false,
              deliveryDocument.getDeliveryNumber(),
              labelFormat);
      TenantContext.get().setReceiveInstrCreateLabelCallEnd(System.currentTimeMillis());

      instruction4mDB.setDeliveryDocument(gson.toJson(deliveryDocument));
      TenantContext.get().setUpdateInstrStart(System.currentTimeMillis());
      updateInstruction(
          instruction4mDB,
          receivedContainer,
          receiveInstructionRequest.getQuantity(),
          printLabelData,
          userId);
      TenantContext.get().setUpdateInstrEnd(System.currentTimeMillis());

      // container lookup will flush all the pending transactions to catch DB exceptions
      Container container =
          containerPersisterService.getConsolidatedContainerForPublish(labelTrackingId);

      if (StringUtils.isNotBlank(instruction4mDB.getProblemTagId())) {
        logger.info(
            "Invoking completeProblem() for problemTagId: {}", instruction4mDB.getProblemTagId());
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.PROBLEM_SERVICE,
                ProblemService.class)
            .completeProblem(instruction4mDB);
      }

      // Publish instruction to WFT
      if (appconfig.isWftPublishEnabled()) {
        TenantContext.get().setReceiveInstrPublishWftCallStart(System.currentTimeMillis());
        logger.info(
            "Publishing instruction message to WFT for labelTrackingId:{}", labelTrackingId);
        publishInstruction(
            instruction4mDB,
            updateInstructionRequest,
            receiveInstructionRequest.getQuantity(),
            httpHeaders);
        TenantContext.get().setReceiveInstrPublishWftCallEnd(System.currentTimeMillis());
      }

      if (isAtlasConvertedItem) {
        // Post receipts to DC Financials
        TenantContext.get().setReceiveInstrPostDcFinReceiptsCallStart(System.currentTimeMillis());
        rdcContainerUtils.postReceiptsToDcFin(
            container, deliveryDocument.getPurchaseReferenceLegacyType());
        TenantContext.get().setReceiveInstrPostDcFinReceiptsCallEnd(System.currentTimeMillis());

        // Publish containers to inventory
        TenantContext.get().setReceiveInstrPublishReceiptsCallStart(System.currentTimeMillis());
        rdcContainerUtils.publishContainersToInventory(container);
        TenantContext.get().setReceiveInstrPublishReceiptsCallEnd(System.currentTimeMillis());

        // Publish create putaway message to Hawkeye
        TenantContext.get().setReceiveInstrPublishSymPutawayCallStart(System.currentTimeMillis());
        rdcContainerUtils.publishPutawayMessageToHawkeye(
            deliveryDocument, receivedContainer, instruction4mDB, httpHeaders);
        TenantContext.get().setReceiveInstrPublishSymPutawayCallEnd(System.currentTimeMillis());

        // Publish move message to MM
        TenantContext.get().setReceiveInstrPublishMoveCallStart(System.currentTimeMillis());
        rdcContainerUtils.publishMove(
            receiveInstructionRequest.getDoorNumber(),
            receiveInstructionRequest.getQuantity(),
            instruction4mDB.getMove(),
            httpHeaders);
        TenantContext.get().setReceiveInstrPublishMoveCallEnd(System.currentTimeMillis());
      }
      updateSSTKPreGenerationLabelsToCancelled(
          deliveryDocument, httpHeaders, receiveInstructionRequest.getQuantity());
      logReceivedQuantityDetails(instruction4mDB, deliveryDocumentLine, deliveryDocument);
      InstructionResponse instructionResponse =
          new InstructionResponseImplNew(null, null, instruction4mDB, printLabelData);
      TenantContext.get().setReceiveInstrEnd(System.currentTimeMillis());
      calculateAndLogElapsedTimeSummary();
      return instructionResponse;
    } catch (ReceivingBadDataException rbde) {
      rollbackForException = true;
      logger.error(
          "{} {}",
          ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(rbde));
      throw rbde;
    } catch (ReceivingException receivingException) {
      rollbackForException = true;
      logger.error(
          "{} {}",
          ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(receivingException));
      throw RdcUtils.convertToReceivingBadDataException(receivingException);
    } catch (Exception e) {
      rollbackForException = true;
      logger.error(
          "{} {}",
          ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVE_INSTRUCTION_INTERNAL_ERROR,
          ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG,
          e);
    } finally {
      if (rollbackForException
          && StringUtils.isNotBlank(labelTrackingId)
          && !isAtlasConvertedItem) {
        logger.info(
            "Error in receiving instruction: {}, invoking nimRDS service to VTR label: {}",
            instructionId,
            labelTrackingId);
        nimRdsService.quantityChange(0, labelTrackingId, httpHeaders);
      }
    }
  }

  /**
   * This method is to void the lpns for SSTK Sym eligible Items during receiving.
   *
   * @param deliveryDocument
   * @param httpHeaders
   * @param quantity
   */
  private void updateSSTKPreGenerationLabelsToCancelled(
      DeliveryDocument deliveryDocument, HttpHeaders httpHeaders, int quantity) {
    if (rdcInstructionUtils.isSSTKDocument(deliveryDocument)) {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      if (rdcInstructionUtils.isAtlasItemSymEligible(deliveryDocumentLine)
          && tenantSpecificConfigReader.getConfiguredFeatureFlag(
              String.valueOf(TenantContext.getFacilityNum()),
              ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
              false)
          && (!rdcLabelGenerationUtils.isSSTKPilotDeliveryEnabled()
              || rdcLabelGenerationUtils.isAtlasSSTKPilotDelivery(
                  Long.valueOf(deliveryDocument.getDeliveryNumber())))) {
        rdcLabelGenerationService.fetchLabelDataAndUpdateLabelStatusToCancelled(
            deliveryDocumentLine, httpHeaders, quantity);
      }
    }
  }

  /**
   * For Symbotic aligned items when smart slotting returns SYMCP and If the handling Code is J then
   * Label printed is MPCIB or else AIB
   *
   * @param handlingMethodCode
   * @param slot
   * @return
   */
  private String getSlot(String slot, String handlingMethodCode) {
    return RdcConstants.SYMCP_SLOT.equalsIgnoreCase(slot)
        ? Objects.nonNull(handlingMethodCode)
            ? RdcConstants.SYM_MANUAL_HANDLING_CODE.equalsIgnoreCase(handlingMethodCode)
                ? RdcConstants.MCPIB_LOCATION
                : RdcConstants.AIB_LOCATION
            : RdcConstants.AIB_LOCATION
        : slot;
  }

  @Transactional(rollbackFor = ReceivingBadDataException.class)
  @InjectTenantFilter
  public InstructionResponse receiveInstruction(
      ReceiveInstructionRequest receiveInstructionRequest, HttpHeaders httpHeaders)
      throws ReceivingBadDataException {
    TenantContext.get().setDaQtyReceivingStart(System.currentTimeMillis());
    RdcUtils.validateMandatoryRequestHeaders(httpHeaders);
    int receiveQty = receiveInstructionRequest.getQuantity();
    List<DeliveryDocument> deliveryDocuments = receiveInstructionRequest.getDeliveryDocuments();
    boolean isLessThanCase =
        Objects.nonNull(receiveInstructionRequest.getIsLessThanCase())
            && Boolean.TRUE.equals(receiveInstructionRequest.getIsLessThanCase());
    rdcReceivingUtils.validateOverage(deliveryDocuments, receiveQty, httpHeaders, isLessThanCase);

    DeliveryDocument deliveryDocument = deliveryDocuments.get(0);
    rdcReceivingUtils.overrideItemProperties(deliveryDocument);

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocument,
            httpHeaders,
            getInstructionRequest(receiveInstructionRequest, httpHeaders),
            receiveQty,
            receiveInstructionRequest);
    TenantContext.get().setDaQtyReceivingEnd(System.currentTimeMillis());

    logger.info(
        "LatencyCheck: Total time taken for DA qty receiving started ts={} time & completed within={} milliSeconds, correlationId={}",
        TenantContext.get().getDaQtyReceivingStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getDaQtyReceivingStart(),
            TenantContext.get().getDaQtyReceivingEnd()),
        TenantContext.getCorrelationId());

    return instructionResponse;
  }

  private InstructionRequest getInstructionRequest(
      ReceiveInstructionRequest receiveInstructionRequest, HttpHeaders httpHeaders) {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setUpcNumber(
        receiveInstructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getCaseUpc());
    Long deliveryNumber =
        Objects.nonNull(receiveInstructionRequest.getDeliveryNumber())
            ? receiveInstructionRequest.getDeliveryNumber()
            : receiveInstructionRequest.getDeliveryDocuments().get(0).getDeliveryNumber();
    instructionRequest.setDeliveryNumber(deliveryNumber.toString());
    instructionRequest.setDoorNumber(receiveInstructionRequest.getDoorNumber());
    String messageId =
        Objects.nonNull(receiveInstructionRequest.getMessageId())
            ? receiveInstructionRequest.getMessageId()
            : httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    instructionRequest.setMessageId(messageId);

    if (StringUtils.isNotBlank(receiveInstructionRequest.getProblemTagId())) {
      instructionRequest.setProblemTagId(receiveInstructionRequest.getProblemTagId());
    }
    return instructionRequest;
  }

  private void logReceivedQuantityDetails(
      Instruction instruction4mDB,
      DeliveryDocumentLine deliveryDocumentLine,
      DeliveryDocument deliveryDocument) {
    InstructionUtils.logReceivedQuantityDetails(
        instruction4mDB,
        deliveryDocumentLine,
        deliveryDocument,
        rdcManagedConfig.isAsnReceivingEnabled());
  }

  private PrintJob createReceiptsAndContainer(
      UpdateInstructionRequest updateInstructionRequest,
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      Integer receivedQuantity,
      String userId,
      String labelTrackingId,
      String slotId) {
    TenantContext.get().setReceiveInstrCreateReceiptsCallStart(System.currentTimeMillis());
    logger.info("Create receipts and containers for labelTrackingId:{}", labelTrackingId);

    if (deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .isAtlasConvertedItem()) {
      receiptService.createReceiptsFromInstructionWithOsdrMasterUpdate(
          deliveryDocument,
          updateInstructionRequest.getDoorNumber(),
          receivedQuantity,
          instruction.getProblemTagId(),
          userId);
    }

    buildContainerAndContainerItem(
        instruction,
        deliveryDocument,
        updateInstructionRequest,
        receivedQuantity,
        userId,
        labelTrackingId,
        slotId);

    PrintJob printJob =
        printJobService.createPrintJob(
            instruction.getDeliveryNumber(),
            instruction.getId(),
            new HashSet<>(Collections.singletonList(labelTrackingId)),
            userId);
    TenantContext.get().setReceiveInstrCreateReceiptsCallEnd(System.currentTimeMillis());
    return printJob;
  }

  private void buildContainerAndContainerItem(
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      UpdateInstructionRequest updateInstructionRequest,
      Integer receivedQuantity,
      String userId,
      String labelTrackingId,
      String slotId) {

    List<ContainerItem> containerItems =
        rdcContainerUtils.buildContainerItem(
            labelTrackingId, deliveryDocument, receivedQuantity, null);
    Container container =
        rdcContainerUtils.buildContainer(
            instruction,
            updateInstructionRequest,
            deliveryDocument,
            userId,
            labelTrackingId,
            slotId,
            null);

    container.setContainerItems(containerItems);
    containerPersisterService.saveContainer(container);
  }

  private void updateInstruction(
      Instruction instruction,
      ReceivedContainer receivedContainer,
      Integer receivedQuantity,
      Map<String, Object> printLabelData,
      String userId) {

    instruction.setContainer(
        rdcContainerUtils.getContainerDetails(
            receivedContainer.getLabelTrackingId(),
            printLabelData,
            ContainerType.PALLET,
            RdcConstants.OUTBOUND_CHANNEL_METHOD_SSTKU));

    LinkedTreeMap<String, Object> moveTreeMap = instruction.getMove();
    if (Objects.nonNull(moveTreeMap) && !moveTreeMap.isEmpty()) {
      moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_BY, userId);
      moveTreeMap.put(ReceivingConstants.MOVE_LAST_CHANGED_ON, new Date());
      moveTreeMap.put(
          ReceivingConstants.MOVE_TO_LOCATION,
          receivedContainer.getDestinations().get(0).getSlot());
      moveTreeMap.put(
          ReceivingConstants.MOVE_CONTAINER_TAG, receivedContainer.getLabelTrackingId());
      MoveType moveType =
          MoveType.builder()
              .code(rdcManagedConfig.getMoveTypeCode())
              .desc(rdcManagedConfig.getMoveTypeDesc())
              .build();
      moveTreeMap.put(ReceivingConstants.MOVE_TYPE, moveType);
    }
    instruction.setMove(moveTreeMap);
    instruction.setReceivedQuantity(receivedQuantity);
    instruction.setLastChangeUserId(userId);
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());
    instructionPersisterService.saveInstruction(instruction);
  }

  private UpdateInstructionRequest getUpdateInstructionRequest(
      ReceiveInstructionRequest receiveInstructionRequest, Long deliveryNumber) {
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDeliveryNumber(deliveryNumber);
    updateInstructionRequest.setDoorNumber(receiveInstructionRequest.getDoorNumber());
    updateInstructionRequest.setDeliveryDocumentLines(
        getDocumentLinesFromInstruction(receiveInstructionRequest));
    return updateInstructionRequest;
  }

  private List<DocumentLine> getDocumentLinesFromInstruction(
      ReceiveInstructionRequest receiveInstructionRequest) {
    List<DocumentLine> documentLines = new ArrayList<>();
    DocumentLine documentLine = new DocumentLine();
    DeliveryDocumentLine deliveryDocumentLine =
        receiveInstructionRequest.getDeliveryDocumentLines().get(0);
    documentLine.setPurchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    documentLine.setPurchaseReferenceLineNumber(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    documentLine.setVnpkQty(deliveryDocumentLine.getVendorPack());
    documentLine.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    documentLine.setQuantityUOM(receiveInstructionRequest.getQuantityUOM());
    documentLine.setQuantity(receiveInstructionRequest.getQuantity());
    documentLines.add(documentLine);
    return documentLines;
  }

  private ReceiveContainersResponseBody getReceiveContainersResponseBody(
      Instruction instruction,
      ReceiveInstructionRequest receiveInstructionRequest,
      HttpHeaders httpHeaders) {

    TenantContext.get().setReceiveInstrNimRdsCallStart(System.currentTimeMillis());
    ReceiveContainersResponseBody receiveContainersResponseBody =
        nimRdsService.getContainerLabelFromRDS(instruction, receiveInstructionRequest, httpHeaders);
    TenantContext.get().setReceiveInstrNimRdsCallEnd(System.currentTimeMillis());

    return receiveContainersResponseBody;
  }

  private void calculateAndLogElapsedTimeSummary() {
    long timeTakeForReceiveInstrCreateLabelCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getReceiveInstrCreateLabelCallStart(),
            TenantContext.get().getReceiveInstrCreateLabelCallEnd());

    long timeTakeForReceiveInstrNimRdsCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getReceiveInstrNimRdsCallStart(),
            TenantContext.get().getReceiveInstrNimRdsCallEnd());

    long timeTakeForReceiveInstrGetReceiptsNimRdsCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getReceiveInstrGetReceiptsNimRdsCallStart(),
            TenantContext.get().getReceiveInstrGetReceiptsNimRdsCallEnd());

    long timeTakeForReceiveInstrCreateReceiptsCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getReceiveInstrCreateReceiptsCallStart(),
            TenantContext.get().getReceiveInstrCreateReceiptsCallEnd());

    long timeTakeForReceiveInstrPublishReceiptsCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getReceiveInstrPublishReceiptsCallStart(),
            TenantContext.get().getReceiveInstrPublishReceiptsCallEnd());

    long timeTakenForPublishingMoveInfoCall =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getReceiveInstrPublishMoveCallStart(),
            TenantContext.get().getReceiveInstrPublishMoveCallEnd());

    long totalTimeTakenForReceiveInstr =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getReceiveInstrStart(), TenantContext.get().getReceiveInstrEnd());

    logger.warn(
        "LatencyCheck ReceiveInstruction at ts={} time in timeTakeForReceiveInstrCreateLabelCall={}, "
            + "timeTakeForReceiveInstrNimRdsCall={}, timeTakeForReceiveInstrGetReceiptsNimRdsCall={}, "
            + "timeTakeForReceiveInstrCreateReceiptsCall={}, timeTakeForReceiveInstrPublishReceiptsCall={}, "
            + "timeTakenForPublishingMoveInfoCall={}, totalTimeTakenForReceiveInstr={}, and correlationId={}",
        TenantContext.get().getReceiveInstrStart(),
        timeTakeForReceiveInstrCreateLabelCall,
        timeTakeForReceiveInstrNimRdsCall,
        timeTakeForReceiveInstrGetReceiptsNimRdsCall,
        timeTakeForReceiveInstrCreateReceiptsCall,
        timeTakeForReceiveInstrPublishReceiptsCall,
        timeTakenForPublishingMoveInfoCall,
        totalTimeTakenForReceiveInstr,
        TenantContext.getCorrelationId());
  }

  private void publishInstruction(
      Instruction instruction,
      UpdateInstructionRequest updateInstructionRequest,
      Integer receivedQuantity,
      HttpHeaders httpHeaders) {
    TenantContext.get().setPublishRdcInstructionCallStart(System.currentTimeMillis());
    instructionHelperService.publishInstruction(
        httpHeaders,
        rdcInstructionUtils.prepareInstructionMessage(
            instruction, updateInstructionRequest, receivedQuantity, httpHeaders));
    TenantContext.get().setPublishRdcInstructionCallEnd(System.currentTimeMillis());
    logger.info(
        "Latency Check: Time taken to publish Rdc instruction is={}",
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getPublishRdcInstructionCallStart(),
            TenantContext.get().getPublishRdcInstructionCallEnd()));
  }

  private void publishVoidLpnToMirage(Instruction instruction4mDB, HttpHeaders httpHeaders) {
    VoidLPNRequest voidLPNRequest = new VoidLPNRequest();
    List<ReceivedQuantityByLines> receivedQuantityByLinesList = new ArrayList<>();

    voidLPNRequest.setDeliveryNumber(String.valueOf(instruction4mDB.getDeliveryNumber()));

    ReceivedQuantityByLines receivedQuantityByLines = new ReceivedQuantityByLines();
    receivedQuantityByLines.setPurchaseReferenceNumber(
        instruction4mDB.getPurchaseReferenceNumber());
    receivedQuantityByLines.setPurchaseReferenceLineNumber(
        instruction4mDB.getPurchaseReferenceLineNumber());
    receivedQuantityByLines.setReceivedQty(instruction4mDB.getReceivedQuantity());

    receivedQuantityByLinesList.add(receivedQuantityByLines);
    voidLPNRequest.setReceivedQuantityByLines(receivedQuantityByLinesList);

    TenantContext.get().setPublishRdcVoidLpnToMirageCallStart(System.currentTimeMillis());
    mirageRestApiClient.voidLPN(voidLPNRequest, httpHeaders);
    TenantContext.get().setPublishRdcVoidLpnToMirageCallEnd(System.currentTimeMillis());
    logger.info(
        "Latency Check: Time taken to publish void lpn to mirage is={}",
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getPublishRdcVoidLpnToMirageCallStart(),
            TenantContext.get().getPublishRdcVoidLpnToMirageCallEnd()));
  }
}
