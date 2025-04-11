package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.message.common.PackData;
import com.walmart.move.nim.receiving.core.message.common.ShipmentInfo;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaAthenaPublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackRequest;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackResponse;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadChildContainerDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadContainerDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadDistributionsDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadItemDTO;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelGenerator;
import com.walmart.move.nim.receiving.rdc.message.publisher.RdcMessagePublisher;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.repositories.PayloadRef;
import com.walmart.platform.service.OutboxEventSinkService;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
public class RdcAtlasDsdcService implements ReceivePackHandler {
  private static final Logger logger = LoggerFactory.getLogger(RdcAtlasDsdcService.class);
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private PrintJobService printJobService;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;

  @Resource(name = RdcConstants.RDC_INSTRUCTION_SERVICE)
  private RdcInstructionService rdcInstructionService;

  @Autowired private LabelDataService labelDataService;
  @Autowired private AuditLogPersisterService auditLogPersisterService;
  @Autowired private RdcDaService rdcDaService;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private ContainerService containerService;
  @Autowired private ReceiptService receiptService;
  @Autowired private Gson gson;
  @Autowired private KafkaAthenaPublisher kafkaAthenaPublisher;
  @Autowired private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Autowired private RdcDeliveryService rdcDeliveryService;

  @Resource(name = RdcConstants.RDC_OSDR_SERVICE)
  private RdcOsdrService rdcOsdrSummaryService;

  @Resource private OutboxEventSinkService outboxEventSinkService;
  @Autowired private RdcMessagePublisher rdcMessagePublisher;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @ManagedConfiguration private OutboxConfig outboxConfig;

  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.INTERNAL,
      executionFlow = "receiveDsdcPacksInAtlas")
  @Transactional(
      rollbackFor = {
        ReceivingException.class,
        ReceivingBadDataException.class,
        ReceivingInternalException.class
      })
  @InjectTenantFilter
  public InstructionResponse receiveDsdcPacksInAtlas(
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders,
      List<DeliveryDocument> deliveryDocumentList)
      throws ReceivingException {
    InstructionResponse instructionResponse = null;
    String sscc = instructionRequest.getSscc();
    logger.info("Create Instruction for Atlas DSDC Pack, SSCC: {}", sscc);

    deliveryDocumentList =
        rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            deliveryDocumentList, instructionRequest);

    // check if Audit required
    boolean isAuditRequiredForDeliveryDocuments = isAuditRequired(deliveryDocumentList.get(0));

    if (isAuditRequiredForDeliveryDocuments) {
      logger.info(
          "Audit tags exist for Atlas DSDC Pack, SSCC: {}, deliveryNumber:{}",
          sscc,
          instructionRequest.getDeliveryNumber());
      TenantContext.get().setReceiveAuditPackCallStart(System.currentTimeMillis());
      instructionResponse = receiveAuditPack(instructionRequest, deliveryDocumentList, httpHeaders);
      TenantContext.get().setReceiveAuditPackCallEnd(System.currentTimeMillis());
      calculateAndLogElapsedTimeSummaryForDSDCPackReceive();
    } else {
      // Receive in Atlas
      ReceivePackRequest receivePackRequest = new ReceivePackRequest();
      receivePackRequest.setPackNumber(instructionRequest.getSscc());
      String asnNumber = deliveryDocumentList.get(0).getAsnNumber();
      receivePackRequest.setAsnNumber(asnNumber);
      logger.info(
          "Receive DSDC Pack in Atlas for SSCC: {}, deliveryNumber:{}, asnNumber:{}",
          sscc,
          instructionRequest.getDeliveryNumber(),
          asnNumber);
      TenantContext.get().setReceiveDsdcCallStart(System.currentTimeMillis());
      instructionResponse =
          receiveDsdcContainer(
              receivePackRequest, instructionRequest, deliveryDocumentList, httpHeaders, null);
      TenantContext.get().setReceiveDsdcCallEnd(System.currentTimeMillis());
    }

    return instructionResponse;
  }

  /**
   * This method will check if there's any audit tag exists on given SSCC/Delivery documents
   *
   * @param deliveryDocument
   * @return
   */
  private boolean isAuditRequired(DeliveryDocument deliveryDocument) {
    boolean isAuditRequired = false;
    if (Objects.nonNull(deliveryDocument.getAuditDetails()) && deliveryDocument.getAuditDetails()) {
      isAuditRequired = true;
    }
    return isAuditRequired;
  }

  /**
   * This method receives DSDC Audit tag and print Audit label
   *
   * @param instructionRequest
   * @param auditPackDeliveryDocuments
   * @param httpHeaders
   * @return
   */
  private InstructionResponse receiveAuditPack(
      InstructionRequest instructionRequest,
      List<DeliveryDocument> auditPackDeliveryDocuments,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    String labelTrackingId = instructionRequest.getSscc();
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    DeliveryDocument deliveryDocument = auditPackDeliveryDocuments.get(0);
    Instruction instruction =
        createInstruction(instructionRequest, deliveryDocument, true, userId, false);

    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    PrintJob printJob =
        printJobService.createPrintJob(
            Long.valueOf(instructionRequest.getDeliveryNumber()),
            instruction.getId(),
            new HashSet<>(Collections.singletonList(labelTrackingId)),
            userId);

    Map<String, Object> printLabelData =
        LabelGenerator.generateAtlasDsdcPackLabel(
            instructionRequest,
            auditPackDeliveryDocuments,
            null,
            true,
            printJob.getId(),
            httpHeaders,
            ReceivingUtils.getDCDateTime(dcTimeZone));
    updateInstruction(instruction, labelTrackingId, userId, printLabelData);

    // build Audit log details
    AuditLogEntity auditLogEntity = saveAuditLogs(instruction, deliveryDocument);

    TenantContext.get().setReceiveAuditPackDBPersistCallStart(System.currentTimeMillis());
    persistContainersInDB(
        instruction,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        auditLogEntity);
    TenantContext.get().setReceiveAuditPackDBPersistCallEnd(System.currentTimeMillis());
    // Call GDM to update Audit status
    TenantContext.get().setUpdateAuditStatusInGdmStart(System.currentTimeMillis());
    updateAuditStatusInGdm(
        AuditStatus.PENDING.ordinal(),
        instruction.getSsccNumber(),
        deliveryDocument.getAsnNumber(),
        httpHeaders);
    TenantContext.get().setUpdateAuditStatusInGdmEnd(System.currentTimeMillis());

    return new InstructionResponseImplNew(
        instructionRequest.getDeliveryStatus(),
        Collections.emptyList(),
        instruction,
        printLabelData);
  }

  /**
   * @param instructionRequest
   * @param deliveryDocument
   * @param isAuditTag
   * @param userId
   * @return
   */
  private Instruction createInstruction(
      InstructionRequest instructionRequest,
      DeliveryDocument deliveryDocument,
      boolean isAuditTag,
      String userId,
      boolean isSsccAlreadyReceived) {
    Instruction instruction = new Instruction();
    instruction.setCreateUserId(userId);
    instruction.setCreateTs(new Date());
    instruction.setPurchaseReferenceNumber(deliveryDocument.getPurchaseReferenceNumber());
    instruction.setMessageId(instructionRequest.getMessageId());
    instruction.setPoDcNumber(TenantContext.getFacilityNum().toString());
    instruction.setLastChangeUserId(userId);
    instruction.setDeliveryNumber(Long.valueOf(instructionRequest.getDeliveryNumber()));

    if (Objects.nonNull(instructionRequest.getProblemTagId())) {
      instruction.setProblemTagId(instructionRequest.getProblemTagId());
    }
    if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      instruction.setSsccNumber(instructionRequest.getSscc());
    }
    instruction.setPrintChildContainerLabels(false);
    if (isAuditTag) {
      instruction.setInstructionMsg(RdcInstructionType.DSDC_AUDIT_REQUIRED.getInstructionMsg());
      instruction.setInstructionCode(RdcInstructionType.DSDC_AUDIT_REQUIRED.getInstructionCode());
    } else {
      instruction.setInstructionMsg(RdcInstructionType.DSDC_RECEIVING.getInstructionMsg());
      instruction.setInstructionCode(RdcInstructionType.DSDC_RECEIVING.getInstructionCode());
      instruction.setProjectedReceiveQty(RdcConstants.RDC_DA_CASE_RECEIVE_QTY);
      instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    }
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setActivityName(WFTInstruction.DSDC.getActivityName());
    if (isSsccAlreadyReceived) {
      return instruction;
    }
    return instructionPersisterService.saveInstruction(instruction);
  }

  private void updateInstruction(
      Instruction instruction,
      String labelTrackingId,
      String userId,
      Map<String, Object> printLabelData) {
    instruction.setReceivedQuantity(
        StringUtils.isEmpty(labelTrackingId)
            ? RdcConstants.RDC_DSDC_AUDIT_CASE_RECEIVE_QTY
            : RdcConstants.RDC_DA_CASE_RECEIVE_QTY);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());
    instruction.setContainer(
        rdcContainerUtils.getContainerDetails(
            labelTrackingId,
            printLabelData,
            ContainerType.CASE,
            RdcConstants.OUTBOUND_CHANNEL_METHOD_CROSSDOCK));
    instruction.setCreateUserId(userId);
    instruction.setCreateTs(new Date());
  }

  /**
   * Receive pack details
   *
   * @param receivePackRequest
   * @return
   */
  @Transactional(
      rollbackFor = {
        ReceivingException.class,
        ReceivingBadDataException.class,
        ReceivingInternalException.class
      })
  @InjectTenantFilter
  public ReceivePackResponse receivePack(
      ReceivePackRequest receivePackRequest, HttpHeaders httpHeaders) throws Exception {
    ReceivePackResponse receivePackResponse = null;
    try {
      TenantContext.get().setAtlasDsdcReceivePackStart(System.currentTimeMillis());
      AuditLogEntity auditLogEntity = fetchPendingAuditPack(receivePackRequest);
      Long deliveryNumber = auditLogEntity.getDeliveryNumber();

      InstructionRequest instructionRequest =
          prepareInstructionRequest(receivePackRequest, deliveryNumber, httpHeaders);
      List<DeliveryDocument> gdmDeliveryDocumentList =
          rdcInstructionService.fetchDeliveryDocument(instructionRequest, httpHeaders);
      gdmDeliveryDocumentList =
          rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
              gdmDeliveryDocumentList, instructionRequest);

      InstructionResponseImplNew response =
          receiveDsdcContainer(
              receivePackRequest,
              instructionRequest,
              gdmDeliveryDocumentList,
              httpHeaders,
              auditLogEntity);

      receivePackResponse =
          ReceivePackResponse.builder()
              .deliveryNumber(instructionRequest.getDeliveryNumber())
              .packNumber(receivePackRequest.getPackNumber())
              .asnNumber(receivePackRequest.getAsnNumber())
              .trackingId(response.getParentTrackingId())
              .auditStatus(AuditStatus.COMPLETED.getStatus())
              .receivingStatus(ReceivingConstants.STATUS_COMPLETE)
              .printJob(response.getPrintJob())
              .build();
      TenantContext.get().setAtlasDsdcReceivePackEnd(System.currentTimeMillis());
      calculateAndLogElapsedTimeSummaryForReceivePack();
      completeDeliveryIfLastAuditTag(deliveryNumber);
    } catch (Exception exception) {
      logger.error("Error occurred while receivePack", exception);
      throw exception;
    }
    return receivePackResponse;
  }

  /**
   * @param receivePackRequest
   * @return
   */
  private AuditLogEntity fetchPendingAuditPack(ReceivePackRequest receivePackRequest) {
    AuditLogEntity auditLogEntity =
        auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
            receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
    if (Objects.isNull(auditLogEntity)) {
      logger.error(
          ReceivingException.DELIVERY_NUMBER_NOT_FOUND.concat(" for asn number {}, pack number {}"),
          receivePackRequest.getAsnNumber(),
          receivePackRequest.getPackNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVE_PACK_INTERNAL_ERROR, ReceivingException.DELIVERY_NUMBER_NOT_FOUND);
    }
    return auditLogEntity;
  }

  /**
   * Preparation of InstructionRequest
   *
   * @param receivePackRequest
   * @param deliveryNumber
   * @return
   */
  private InstructionRequest prepareInstructionRequest(
      ReceivePackRequest receivePackRequest, Long deliveryNumber, HttpHeaders httpHeaders) {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setReceivingType(ReceivingConstants.SSCC);
    instructionRequest.setSscc(receivePackRequest.getPackNumber());
    instructionRequest.setDeliveryNumber(deliveryNumber.toString());
    // TODO: need to work with Audit team to get the exact values.
    instructionRequest.setDoorNumber(ReceivingConstants.DEFAULT_DOOR);
    instructionRequest.setMessageId(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    return instructionRequest;
  }

  /**
   * @param receivedContainers
   * @return
   */
  private ReceivedContainer getParentReceivedContainer(List<ReceivedContainer> receivedContainers) {
    return receivedContainers
        .stream()
        .filter(receivedContainer -> StringUtils.isBlank(receivedContainer.getParentTrackingId()))
        .collect(Collectors.toList())
        .get(0);
  }

  private Long getTotalChildContainersCount(List<ReceivedContainer> receivedContainers) {
    return receivedContainers
        .stream()
        .filter(
            receivedContainer -> StringUtils.isNotBlank(receivedContainer.getParentTrackingId()))
        .count();
  }
  /**
   * @param labelDataList
   * @param gdmDeliveryDocumentList
   * @return
   */
  public List<ReceivedContainer> transformLabelData(
      List<LabelData> labelDataList, List<DeliveryDocument> gdmDeliveryDocumentList) {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    for (LabelData labelData : labelDataList) {
      List<InstructionDownloadChildContainerDTO> childLabelDataContainers =
          labelData.getAllocation().getChildContainers();
      boolean isDsdcParentContainer = !CollectionUtils.isEmpty(childLabelDataContainers);
      if (isDsdcParentContainer) {
        // build child containers
        String parentTrackingId = labelData.getTrackingId();
        childLabelDataContainers.forEach(
            childContainer -> {
              DeliveryDocument deliveryDocument =
                  filterDeliveryDocumentByPoAndPoLineNumber(
                      gdmDeliveryDocumentList,
                      childContainer.getPoNbr(),
                      childContainer.getPoLineNbr());
              if (Objects.nonNull(deliveryDocument)) {
                ReceivedContainer childReceivedContainer =
                    buildDsdcChildContainer(
                        labelData,
                        deliveryDocument,
                        childContainer.getTrackingId(),
                        childContainer.getDistributions(),
                        childContainer.getCtrDestination(),
                        parentTrackingId);
                receivedContainers.add(childReceivedContainer);
              }
            });

        buildParentContainerForDsdc(
            labelData, receivedContainers, parentTrackingId, gdmDeliveryDocumentList.get(0));
      }
    }
    return receivedContainers;
  }

  /**
   * @param labelData
   * @param deliveryDocument
   * @param labelTrackingId
   * @param distributions
   * @param facility
   * @return
   */
  public ReceivedContainer buildDsdcChildContainer(
      LabelData labelData,
      DeliveryDocument deliveryDocument,
      String labelTrackingId,
      List<InstructionDownloadDistributionsDTO> distributions,
      Facility facility,
      String parentLabelTrackingId) {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setFulfillmentMethod(FulfillmentMethodType.DSDC.getType());
    RdcUtils.buildCommonReceivedContainerDetails(
        labelTrackingId, receivedContainer, deliveryDocument);

    InstructionDownloadItemDTO instructionDownloadItemDTO = distributions.get(0).getItem();
    // ToDo: refactor once the distributions info contract is cleaned up on orders end
    receivedContainer.setAisle(instructionDownloadItemDTO.getAisle());
    if (StringUtils.isNotBlank(instructionDownloadItemDTO.getPrintBatch())) {
      receivedContainer.setBatch(Integer.parseInt(instructionDownloadItemDTO.getPrintBatch()));
    }
    if (StringUtils.isNotBlank(instructionDownloadItemDTO.getPickBatch())) {
      receivedContainer.setPickBatch(Integer.parseInt(instructionDownloadItemDTO.getPickBatch()));
    }

    receivedContainer.setShippingLane(instructionDownloadItemDTO.getShipLaneNumber());
    receivedContainer.setDivision(instructionDownloadItemDTO.getDivisionNumber());
    receivedContainer.setStoreAlignment(instructionDownloadItemDTO.getStoreAlignment());
    receivedContainer.setPack(distributions.get(0).getAllocQty());
    receivedContainer.setLabelType(InventoryLabelType.R8002_DSDC.getType());

    // build destination
    Destination destination = new Destination();
    destination.setSlot(RdcConstants.DA_R8002_SLOT);
    if (Objects.nonNull(labelData.getDestStrNbr())) {
      destination.setStore(labelData.getDestStrNbr().toString());
    }
    if (Objects.nonNull(labelData.getAsnNumber())) {
      receivedContainer.setAsnNumber(labelData.getAsnNumber());
    }
    if (Objects.nonNull(labelData.getSscc())) {
      receivedContainer.setSscc(labelData.getSscc());
    }

    receivedContainer.setDestinations(Collections.singletonList(destination));
    receivedContainer.setDistributions(
        RdcUtils.prepareDistributions(distributions, facility.getBuNumber()));

    receivedContainer.setParentTrackingId(parentLabelTrackingId);
    return receivedContainer;
  }

  /**
   * @param labelData
   * @param childReceivedContainers
   * @param parentTrackingId
   * @param deliveryDocument
   */
  public void buildParentContainerForDsdc(
      LabelData labelData,
      List<ReceivedContainer> childReceivedContainers,
      String parentTrackingId,
      DeliveryDocument deliveryDocument) {
    if (!CollectionUtils.isEmpty(childReceivedContainers)) {
      String destinationStoreNumber = null;
      if (Objects.nonNull(labelData.getDestStrNbr())) {
        destinationStoreNumber = labelData.getDestStrNbr().toString();
      }
      List<Destination> destinations = new ArrayList<>();
      ReceivedContainer parentReceivedContainer = new ReceivedContainer();
      RdcUtils.buildCommonReceivedContainerDetails(
          parentTrackingId, parentReceivedContainer, deliveryDocument);
      parentReceivedContainer.setFulfillmentMethod(FulfillmentMethodType.DSDC.getType());
      parentReceivedContainer.setLabelType(InventoryLabelType.R8002_DSDC.getType());
      parentReceivedContainer.setSorterDivertRequired(true);
      InstructionDownloadContainerDTO container = labelData.getAllocation().getContainer();
      Integer shipLaneNumber =
          (Objects.nonNull(container)
                  && Objects.nonNull(container.getFinalDestination())
                  && Objects.nonNull(container.getFinalDestination().getShipLaneNumber()))
              ? container.getFinalDestination().getShipLaneNumber()
              : childReceivedContainers.get(0).getShippingLane();
      parentReceivedContainer.setShippingLane(shipLaneNumber);
      Integer printBatch =
          (Objects.nonNull(container)
                  && Objects.nonNull(container.getFinalDestination())
                  && Objects.nonNull(container.getFinalDestination().getPrintBatch()))
              ? container.getFinalDestination().getPrintBatch()
              : childReceivedContainers.get(0).getBatch();
      parentReceivedContainer.setBatch(printBatch);
      if (!CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())) {
        parentReceivedContainer.setPoevent(
            deliveryDocument.getDeliveryDocumentLines().get(0).getEvent());
      }
      destinations.add(buildDestination(destinationStoreNumber));
      if (Objects.nonNull(labelData.getSscc())) {
        parentReceivedContainer.setSscc(labelData.getSscc());
      }
      parentReceivedContainer.setDestinations(destinations);
      List<Distribution> distributions = new ArrayList<>();
      Distribution distribution = new Distribution();
      distribution.setDestNbr(Integer.parseInt(destinationStoreNumber));
      distributions.add(distribution);
      parentReceivedContainer.setDistributions(distributions);
      childReceivedContainers.add(parentReceivedContainer);
    }
  }

  private Destination buildDestination(String storeNumber) {
    Destination destination = new Destination();
    if (Objects.nonNull(storeNumber)) {
      destination.setStore(storeNumber);
    }
    destination.setSlot(RdcConstants.DA_R8002_SLOT);
    return destination;
  }

  /**
   * Fetches labels based on receivePackRequest details and throws error if no data is avaialable
   * Also verifies that only 1 label is available for the given asn and SSCC combination and if more
   * than one is available then it fetches the latest created label
   *
   * @param receivePackRequest
   */
  private List<LabelData> fetchLabelDataByAsnAndPackNumber(ReceivePackRequest receivePackRequest) {
    List<LabelData> labelDataList =
        labelDataService.findBySsccAndAsnNumber(
            receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());

    if (CollectionUtils.isEmpty(labelDataList)) {
      throw new ReceivingBadDataException(
          String.format(ExceptionCodes.NO_ALLOCATIONS_FOR_DSDC_FREIGHT),
          String.format(
              ReceivingException.NO_ALLOCATIONS_FOR_DSDC_FREIGHT,
              receivePackRequest.getPackNumber(),
              receivePackRequest.getAsnNumber()),
          receivePackRequest.getPackNumber(),
          receivePackRequest.getAsnNumber());
    }

    validateLabelCountForSSCC(
        labelDataList, receivePackRequest.getPackNumber(), receivePackRequest.getAsnNumber());
    return labelDataList;
  }

  /**
   * This method validates the available label count for the scanned SSCC. We are supposed to have 1
   * label (row) in label data per SSCC. If we have more than 1 label for the scanned SSCC then we
   * pick the one which has latest created timestamp
   *
   * @param labelDataList
   * @param sscc
   */
  private void validateLabelCountForSSCC(
      List<LabelData> labelDataList, String sscc, String asnNumber) {
    if (labelDataList.size() > 1) {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          RdcConstants.IS_DUPLICATE_SSCC_BLOCKED_FOR_MULTIPLE_DELIVERIES,
          false)) {
        logger.info("Number of available labels for the scanned SSCC :{} is more than 1", sscc);
        throw new ReceivingBadDataException(
            ExceptionCodes.LABEL_COUNT_EXCEEDED_FOR_DSDC_SSCC,
            String.format(ReceivingException.LABEL_COUNT_EXCEEDED_FOR_DSDC_SSCC, sscc),
            sscc);
      } else {
        logger.info(
            "Found {} labels for the scanned SSCC: {} ASN: {}",
            labelDataList.size(),
            sscc,
            asnNumber);
        List<LabelData> filteredLabelDataBasedOnCreateTs =
            labelDataList
                .stream()
                .sorted(Comparator.comparing(LabelData::getCreateTs).reversed())
                .collect(Collectors.toList());
        logger.info(
            "Filtered labels based on the latest created timestamp with trackingId: {}",
            filteredLabelDataBasedOnCreateTs.get(0).getTrackingId());
        labelDataList.clear();
        labelDataList.addAll(Collections.singletonList(filteredLabelDataBasedOnCreateTs.get(0)));
      }
    }
  }

  /**
   * Filter DeliveryDocument by po and poLine number. The Po/PoLine number we refer from
   * LabelData/received container which will match against the delivery documents
   *
   * @param deliveryDocuments
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  public DeliveryDocument filterDeliveryDocumentByPoAndPoLineNumber(
      List<DeliveryDocument> deliveryDocuments,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber) {
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      List<DeliveryDocumentLine> matchedDeliveryDocumentLines =
          deliveryDocument
              .getDeliveryDocumentLines()
              .stream()
              .filter(
                  line ->
                      StringUtils.equalsIgnoreCase(
                              line.getPurchaseReferenceNumber(), purchaseReferenceNumber)
                          && Objects.nonNull(purchaseReferenceLineNumber)
                          && line.getPurchaseReferenceLineNumber() == purchaseReferenceLineNumber)
              .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(matchedDeliveryDocumentLines)) {
        DeliveryDocument matchedDeliveryDocumentByPoPoLine;
        try {
          matchedDeliveryDocumentByPoPoLine = new DeliveryDocument();
          BeanUtils.copyProperties(matchedDeliveryDocumentByPoPoLine, deliveryDocument);
        } catch (Exception exception) {
          logger.error(
              "Unable to copy the delivery document properties for purchaseReferenceNumber:{}",
              deliveryDocument.getPurchaseReferenceNumber());
          matchedDeliveryDocumentByPoPoLine =
              getDeliveryDocumentForMatchedPoPoLine(deliveryDocument);
        }
        // set atlas converted item for all the items
        matchedDeliveryDocumentLines.forEach(
            documentLine -> {
              if (Objects.isNull(documentLine.getAdditionalInfo())) {
                documentLine.setAdditionalInfo(new ItemData());
              }
              documentLine.getAdditionalInfo().setAtlasConvertedItem(true);
            });
        matchedDeliveryDocumentByPoPoLine.setDeliveryDocumentLines(matchedDeliveryDocumentLines);
        return matchedDeliveryDocumentByPoPoLine;
      }
    }

    logger.error(
        "DeliveryDocument not matched for the po="
            + purchaseReferenceNumber
            + " and po line number="
            + purchaseReferenceLineNumber);
    return null;
  }

  /**
   * This method copy the delivery Document with the mandatory fields required to create container &
   * container item
   *
   * @param originalDeliveryDocument
   * @return
   */
  private DeliveryDocument getDeliveryDocumentForMatchedPoPoLine(
      DeliveryDocument originalDeliveryDocument) {
    DeliveryDocument matchedDeliveryDocumentByPoPoLine = new DeliveryDocument();
    matchedDeliveryDocumentByPoPoLine.setPoTypeCode(originalDeliveryDocument.getPoTypeCode());
    matchedDeliveryDocumentByPoPoLine.setDeptNumber(originalDeliveryDocument.getDeptNumber());
    matchedDeliveryDocumentByPoPoLine.setPoDCNumber(originalDeliveryDocument.getPoDCNumber());
    matchedDeliveryDocumentByPoPoLine.setVendorNbrDeptSeq(
        originalDeliveryDocument.getVendorNbrDeptSeq());
    matchedDeliveryDocumentByPoPoLine.setVendorNumber(originalDeliveryDocument.getVendorNumber());
    matchedDeliveryDocumentByPoPoLine.setPoTypeCode(originalDeliveryDocument.getPoTypeCode());
    matchedDeliveryDocumentByPoPoLine.setBaseDivisionCode(
        originalDeliveryDocument.getBaseDivisionCode());
    matchedDeliveryDocumentByPoPoLine.setFinancialReportingGroup(
        originalDeliveryDocument.getBaseDivisionCode());
    matchedDeliveryDocumentByPoPoLine.setProDate(originalDeliveryDocument.getProDate());
    matchedDeliveryDocumentByPoPoLine.setOriginFacilityNum(
        originalDeliveryDocument.getOriginFacilityNum());
    matchedDeliveryDocumentByPoPoLine.setOriginType(originalDeliveryDocument.getOriginType());
    matchedDeliveryDocumentByPoPoLine.setPurchaseReferenceLegacyType(
        originalDeliveryDocument.getPurchaseReferenceLegacyType());
    matchedDeliveryDocumentByPoPoLine.setChannelMethod(originalDeliveryDocument.getChannelMethod());
    matchedDeliveryDocumentByPoPoLine.setPurchaseCompanyId(
        originalDeliveryDocument.getPurchaseCompanyId());
    return matchedDeliveryDocumentByPoPoLine;
  }
  /**
   * This method publishes receipts/purchases to Inventory/DcFin & WFT
   *
   * @param receivedContainers
   * @throws ReceivingException
   */
  public void postReceivingUpdates(
      List<ReceivedContainer> receivedContainers, Instruction instruction, HttpHeaders httpHeaders)
      throws ReceivingException {
    for (ReceivedContainer receivedContainer : receivedContainers) {
      if (StringUtils.isBlank(receivedContainer.getParentTrackingId())) {
        Container consolidatedContainer =
            containerPersisterService.getConsolidatedContainerForPublish(
                receivedContainer.getLabelTrackingId());
        if (Objects.nonNull(receivedContainer.getAsnNumber())) {
          consolidatedContainer.setAsnNumber(receivedContainer.getAsnNumber());
        }

        if (Objects.nonNull(consolidatedContainer.getDestination())
            && Objects.nonNull(
                consolidatedContainer.getDestination().get(ReceivingConstants.BU_NUMBER))) {
          // publish sorter divert message to Athena
          TenantContext.get().setDaCaseReceivingSorterPublishStart(System.currentTimeMillis());
          kafkaAthenaPublisher.publishLabelToSorter(consolidatedContainer, LabelType.DSDC.name());
          TenantContext.get().setDaCaseReceivingAthenaPublishEnd(System.currentTimeMillis());
        }

        // publish consolidated or parent containers to Inventory
        TenantContext.get().setReceiveInstrPublishReceiptsCallStart(System.currentTimeMillis());
        rdcContainerUtils.publishContainersToInventory(consolidatedContainer);
        TenantContext.get().setReceiveInstrPublishReceiptsCallEnd(System.currentTimeMillis());

        // publish consolidated or parent containers to EI
        if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false)) {
          TenantContext.get().setPublishEICallStart(System.currentTimeMillis());
          String[] eiEvents =
              consolidatedContainer.getInventoryStatus().equals(InventoryStatus.PICKED.name())
                  ? ReceivingConstants.EI_DC_RECEIVING_AND_PICK_EVENTS
                  : ReceivingConstants.EI_DC_RECEIVING_EVENT;
          rdcContainerUtils.publishContainerToEI(consolidatedContainer, eiEvents);
          TenantContext.get().setPublishEICallEnd(System.currentTimeMillis());
        }
      }

      TenantContext.get().setDsdcCaseReceivingPublishWFTCallStart(System.currentTimeMillis());
      rdcReceivingUtils.publishInstruction(
          instruction,
          new DeliveryDocumentLine(),
          RdcConstants.RDC_DA_CASE_RECEIVE_QTY,
          httpHeaders,
          false);
      TenantContext.get().setDaCaseReceivingPublishWFTCallEnd(System.currentTimeMillis());
    }
  }

  /**
   * @param receivePackRequest
   * @param instructionRequest
   * @param gdmDeliveryDocumentList
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  private InstructionResponseImplNew receiveDsdcContainer(
      ReceivePackRequest receivePackRequest,
      InstructionRequest instructionRequest,
      List<DeliveryDocument> gdmDeliveryDocumentList,
      HttpHeaders httpHeaders,
      AuditLogEntity auditLog) {
    List<Container> containers = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    Map<String, Object> printLabelData = null;
    Collection<OutboxEvent> outboxEvents = new ArrayList<>();

    try {
      boolean isSsccAlreadyReceived =
          validateSsccAlreadyReceivedAndBlockRequest(receivePackRequest);
      DeliveryDocument deliveryDocument = gdmDeliveryDocumentList.get(0);
      List<LabelData> labelDataList = fetchLabelDataByAsnAndPackNumber(receivePackRequest);
      List<ReceivedContainer> receivedContainers =
          transformLabelData(labelDataList, gdmDeliveryDocumentList);
      ReceivedContainer parentReceivedContainer = getParentReceivedContainer(receivedContainers);
      String parentTrackingId = parentReceivedContainer.getLabelTrackingId();

      Instruction instruction =
          createInstruction(
              instructionRequest, deliveryDocument, false, userId, isSsccAlreadyReceived);

      PrintJob printJob;
      if (isSsccAlreadyReceived) {
        printJob =
            printJobService.preparePrintJob(
                Long.valueOf(instructionRequest.getDeliveryNumber()),
                instruction.getId(),
                new HashSet<>(Collections.singletonList(parentTrackingId)),
                userId);
      } else {
        printJob =
            printJobService.createPrintJob(
                Long.valueOf(instructionRequest.getDeliveryNumber()),
                instruction.getId(),
                new HashSet<>(Collections.singletonList(parentTrackingId)),
                userId);
      }
      printLabelData =
          LabelGenerator.generateAtlasDsdcPackLabel(
              instructionRequest,
              gdmDeliveryDocumentList,
              parentReceivedContainer,
              false,
              printJob.getId(),
              httpHeaders,
              ReceivingUtils.getDCDateTime(dcTimeZone));
      if (isSsccAlreadyReceived) {
        updateInstruction(instruction, parentTrackingId, userId, printLabelData);
        return new InstructionResponseImplNew(
            instructionRequest.getDeliveryStatus(),
            Collections.singletonList(deliveryDocument),
            instruction,
            printLabelData,
            parentTrackingId);
      }

      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.IS_RAPID_RELAYER_CALL_BACK_ENABLED_FOR_DSDC_RECEIVING,
          false)) {
        long childContainersCount = getTotalChildContainersCount(receivedContainers);
        if (childContainersCount > rdcManagedConfig.getDsdcAsyncFlowChildContainerCount()) {
          labelDataList.forEach(
              labelData -> labelData.setStatus(LabelInstructionStatus.IN_PROGRESS.name()));
          return receiveDsdcPackByRapidRelayerCallback(
              parentReceivedContainer,
              instructionRequest,
              instruction,
              receivePackRequest,
              httpHeaders,
              gdmDeliveryDocumentList,
              printLabelData,
              labelDataList);
        }
      }

      // prepare container and container item
      for (ReceivedContainer receivedContainer : receivedContainers) {
        deliveryDocument =
            filterDeliveryDocumentByPoAndPoLineNumber(
                gdmDeliveryDocumentList,
                receivedContainer.getPoNumber(),
                receivedContainer.getPoLine());
        if (Objects.nonNull(deliveryDocument)) {
          Integer receivedQtyInEaches = receivedContainer.getPack();
          ContainerItem containerItem;
          // build container item for parent container
          if (StringUtils.isBlank(receivedContainer.getParentTrackingId())) {
            containerItem =
                buildParentContainerItemForDsdc(
                    receivedContainer,
                    receivedContainer.getLabelTrackingId(),
                    gdmDeliveryDocumentList.get(0));
          } else {
            containerItem =
                rdcContainerUtils.buildContainerItemDetails(
                    receivedContainer.getLabelTrackingId(),
                    deliveryDocument,
                    receivedQtyInEaches,
                    null,
                    receivedContainer.getStoreAlignment(),
                    receivedContainer.getDistributions(),
                    receivedContainer.getDestType());
          }
          Container container =
              rdcContainerUtils.buildContainer(
                  instructionRequest.getDoorNumber(),
                  instruction.getId(),
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  instructionRequest.getMessageId(),
                  deliveryDocument,
                  userId,
                  receivedContainer,
                  null,
                  null);
          container.setSsccNumber(receivePackRequest.getPackNumber());
          containers.add(container);
          containerItems.add(containerItem);
        }
      }

      updateInstruction(instruction, parentTrackingId, userId, printLabelData);
      labelDataList.forEach(
          labelData -> labelData.setStatus(LabelInstructionStatus.COMPLETE.name()));

      // build Receipts from container items
      List<ContainerItem> containerItemsForReceipts =
          aggregateContainerItemQuantityByPoPoLine(containerItems);
      List<Receipt> receipts =
          receiptService.buildReceiptsFromContainerItems(
              Long.valueOf(instructionRequest.getDeliveryNumber()),
              receivePackRequest.getAsnNumber(),
              instructionRequest.getDoorNumber(),
              receivePackRequest.getPackNumber(),
              userId,
              containerItemsForReceipts);

      if (Objects.nonNull(auditLog)) {
        auditLog.setAuditStatus(AuditStatus.COMPLETED);
        auditLog.setCompletedBy(userId);
        auditLog.setCompletedTs(new Date());
        auditLog.setLastUpdatedTs(new Date());
      }

      TenantContext.get().setAtlasDsdcReceivePackDBPersistStart(System.currentTimeMillis());
      persistContainersInDB(
          instruction, containers, containerItems, receipts, labelDataList, auditLog);
      TenantContext.get().setAtlasDsdcReceivePackDBPersistEnd(System.currentTimeMillis());

      // Update Audit status in GDM
      if (Objects.nonNull(auditLog)) {
        TenantContext.get().setUpdateAuditStatusInGdmStart(System.currentTimeMillis());
        updateAuditStatusInGdm(
            AuditStatus.COMPLETED.ordinal(),
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            httpHeaders);
        TenantContext.get().setUpdateAuditStatusInGdmEnd(System.currentTimeMillis());
      } else {
        TenantContext.get().setUpdateAuditStatusInGdmStart(System.currentTimeMillis());
        updateAuditStatusInGdm(
            AuditStatus.NOT_REQUIRED.ordinal(),
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            httpHeaders);
        TenantContext.get().setUpdateAuditStatusInGdmEnd(System.currentTimeMillis());
      }

      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
          false)) {
        TenantContext.get().setPersistOutboxEventsForDAStart(System.currentTimeMillis());
        outboxEvents =
            rdcReceivingUtils.buildOutboxEvents(
                receivedContainers, httpHeaders, instruction, deliveryDocument);
        rdcReceivingUtils.persistOutboxEvents(outboxEvents);
        TenantContext.get().setPersistOutboxEventsForDAEnd(System.currentTimeMillis());
      } else {
        TenantContext.get()
            .setAtlasDsdcReceivePackPostReceivingUpdatesStart(System.currentTimeMillis());
        postReceivingUpdates(receivedContainers, instruction, httpHeaders);
        TenantContext.get()
            .setAtlasDsdcReceivePackPostReceivingUpdatesEnd(System.currentTimeMillis());
      }

      return new InstructionResponseImplNew(
          instructionRequest.getDeliveryStatus(),
          Collections.singletonList(deliveryDocument),
          instruction,
          printLabelData,
          parentTrackingId);
    } catch (ReceivingBadDataException rbde) {
      throw rbde;
    } catch (Exception e) {
      throw new ReceivingInternalException(
          String.format(
              ExceptionCodes.RECEIVE_DSDC_INSTRUCTION_INTERNAL_ERROR,
              receivePackRequest.getPackNumber()),
          String.format(
              ReceivingException.DSDC_RECEIVE_INSTRUCTION_ERROR_MSG,
              receivePackRequest.getPackNumber()),
          e);
    }
  }

  /**
   * This method build container and container items and updates DB with instruction and container
   * info. It further utilizes outbox pattern to perform DB operations and publish sorter message
   *
   * @param parentReceivedContainer
   * @param instructionRequest
   * @param instruction
   * @param receivePackRequest
   * @param httpHeaders
   * @param gdmDeliveryDocumentList
   * @param printLabelData
   * @return
   * @throws ReceivingException
   */
  private InstructionResponseImplNew receiveDsdcPackByRapidRelayerCallback(
      ReceivedContainer parentReceivedContainer,
      InstructionRequest instructionRequest,
      Instruction instruction,
      ReceivePackRequest receivePackRequest,
      HttpHeaders httpHeaders,
      List<DeliveryDocument> gdmDeliveryDocumentList,
      Map<String, Object> printLabelData,
      List<LabelData> labelDataList)
      throws ReceivingException {
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    String parentTrackingId = parentReceivedContainer.getLabelTrackingId();
    List<ContainerItem> containerItems = new ArrayList<>();
    Container container = null;
    DeliveryDocument deliveryDocument =
        filterDeliveryDocumentByPoAndPoLineNumber(
            gdmDeliveryDocumentList,
            parentReceivedContainer.getPoNumber(),
            parentReceivedContainer.getPoLine());
    if (Objects.nonNull(deliveryDocument)) {
      ContainerItem containerItem;
      // build container item for parent container
      containerItem =
          buildParentContainerItemForDsdc(
              parentReceivedContainer,
              parentReceivedContainer.getLabelTrackingId(),
              gdmDeliveryDocumentList.get(0));
      container =
          rdcContainerUtils.buildContainer(
              instructionRequest.getDoorNumber(),
              instruction.getId(),
              Long.valueOf(instructionRequest.getDeliveryNumber()),
              instructionRequest.getMessageId(),
              deliveryDocument,
              userId,
              parentReceivedContainer,
              null,
              null);
      container.setSsccNumber(receivePackRequest.getPackNumber());
      container.setAsnNumber(receivePackRequest.getAsnNumber());
      containerItems.add(containerItem);
    }

    updateInstruction(instruction, parentTrackingId, userId, printLabelData);

    persistContainersInDB(
        instruction,
        Collections.singletonList(container),
        containerItems,
        Collections.emptyList(),
        labelDataList,
        null);

    if (Objects.nonNull(container)) {
      buildOutboxEventForCallbackToReceiveDsdcPack(container, httpHeaders);
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
          false)) {
        Collection<OutboxEvent> outboxEvents =
            buildOutboxEventForSoterDivertAndWFT(
                parentReceivedContainer, deliveryDocument, instruction, httpHeaders);
        rdcReceivingUtils.persistOutboxEvents(outboxEvents);
      } else {
        if (Objects.nonNull(container.getDestination())
            && Objects.nonNull(container.getDestination().get(ReceivingConstants.BU_NUMBER))) {
          // publish sorter divert message to Athena
          TenantContext.get().setDaCaseReceivingSorterPublishStart(System.currentTimeMillis());
          kafkaAthenaPublisher.publishLabelToSorter(container, LabelType.DSDC.name());
          TenantContext.get().setDaCaseReceivingAthenaPublishEnd(System.currentTimeMillis());
        }

        TenantContext.get().setDsdcCaseReceivingPublishWFTCallStart(System.currentTimeMillis());
        rdcReceivingUtils.publishInstruction(
            instruction,
            new DeliveryDocumentLine(),
            RdcConstants.RDC_DA_CASE_RECEIVE_QTY,
            httpHeaders,
            false);
        TenantContext.get().setDaCaseReceivingPublishWFTCallEnd(System.currentTimeMillis());
      }
    }

    return new InstructionResponseImplNew(
        instructionRequest.getDeliveryStatus(),
        Collections.singletonList(deliveryDocument),
        instruction,
        printLabelData,
        parentTrackingId);
  }
  /**
   * @param instruction
   * @param containers
   * @param containerItems
   * @param receipts
   * @param labelDataList
   * @param auditLogEntity
   */
  private void persistContainersInDB(
      Instruction instruction,
      List<Container> containers,
      List<ContainerItem> containerItems,
      List<Receipt> receipts,
      List<LabelData> labelDataList,
      AuditLogEntity auditLogEntity) {
    if (Objects.nonNull(auditLogEntity)) {
      auditLogPersisterService.saveAuditLogData(auditLogEntity);
    }
    rdcReceivingUtils.persistReceivedContainerDetails(
        Objects.nonNull(instruction) ? Collections.singletonList(instruction) : null,
        containers,
        containerItems,
        receipts,
        labelDataList);
  }

  /**
   * This method update Audit status as Pending in GDM
   *
   * @param auditStatus
   * @param packNumber
   * @param asnNumber
   * @param httpHeaders
   * @throws ReceivingException
   */
  private void updateAuditStatusInGdm(
      Integer auditStatus, String packNumber, String asnNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<ShipmentInfo> shipments = new ArrayList<>();
    Set<PackData> pendingAuditData = new HashSet<>();
    PackData packData;
    if (auditStatus.equals(AuditStatus.NOT_REQUIRED.ordinal())) {
      packData = PackData.builder().packNumber(packNumber).receivingStatus(RECEIVED_STATUS).build();
    } else if (auditStatus.equals(AuditStatus.COMPLETED.ordinal())) {
      packData =
          PackData.builder()
              .packNumber(packNumber)
              .auditStatus(auditStatus)
              .receivingStatus(RECEIVED_STATUS)
              .build();
    } else {
      packData = PackData.builder().packNumber(packNumber).auditStatus(auditStatus).build();
    }
    pendingAuditData.add(packData);
    shipments.add(
        ShipmentInfo.builder()
            .shipmentNumber(asnNumber)
            .documentType(ReceivingConstants.DSDC_ASN)
            .packs(pendingAuditData)
            .build());
    rdcDeliveryService.callGdmToUpdatePackStatus(shipments, httpHeaders);
  }

  /**
   * Parent container could not keep multiple item details, we are setting minimal information for
   * the container item.
   *
   * @param receivedContainer
   * @param labelTrackingId
   * @param deliveryDocument
   * @return
   */
  private ContainerItem buildParentContainerItemForDsdc(
      ReceivedContainer receivedContainer,
      String labelTrackingId,
      DeliveryDocument deliveryDocument) {
    ContainerItem containerItem = new ContainerItem();
    boolean isMultiPO =
        deliveryDocument
                .getDeliveryDocumentLines()
                .stream()
                .map(docLine -> docLine.getPurchaseReferenceNumber())
                .distinct()
                .count()
            > 1;
    containerItem.setTrackingId(labelTrackingId);
    containerItem.setPurchaseReferenceNumber(
        isMultiPO ? LabelConstants.MULTI_PO : receivedContainer.getPoNumber());
    containerItem.setInboundChannelMethod(ReceivingConstants.DSDC_CHANNEL_METHODS_FOR_RDC);
    containerItem.setDescription(ReceivingConstants.DSDC_CHANNEL_METHODS_FOR_RDC);
    containerItem.setPoDCNumber(TenantContext.getFacilityNum().toString());
    containerItem.setDeptNumber(receivedContainer.getDepartment());
    containerItem.setQuantity(RdcConstants.DSDC_SSCC_CASE_RECEIVE_QTY);
    containerItem.setVnpkQty(RdcConstants.DSDC_SSCC_CASE_RECEIVE_QTY);
    containerItem.setWhpkQty(RdcConstants.DSDC_SSCC_CASE_RECEIVE_QTY);
    String financialReportingGroupCode =
        Objects.requireNonNull(TenantContext.getFacilityCountryCode()).toUpperCase();
    containerItem.setFinancialReportingGroupCode(financialReportingGroupCode);
    containerItem.setRotateDate(new Date());
    return containerItem;
  }

  public AuditLogEntity saveAuditLogs(Instruction instruction, DeliveryDocument deliveryDocument) {
    AuditLogEntity auditLogEntity = null;

    AuditLogEntity auditLogEntityAlreadyPresent =
        auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            deliveryDocument.getAsnNumber(),
            String.valueOf(instruction.getSsccNumber()),
            AuditStatus.PENDING);

    if (Objects.isNull(auditLogEntityAlreadyPresent)) {
      auditLogEntity = new AuditLogEntity();
      auditLogEntity.setAuditStatus(AuditStatus.PENDING);
      auditLogEntity.setDeliveryNumber(deliveryDocument.getDeliveryNumber());
      auditLogEntity.setAsnNumber(deliveryDocument.getAsnNumber());
      auditLogEntity.setCreatedBy(instruction.getCreateUserId());
      auditLogEntity.setSsccNumber(instruction.getSsccNumber());
      auditLogEntity.setCreatedTs(new Date());
      auditLogEntity.setLastUpdatedTs(new Date());
    } else {
      auditLogEntityAlreadyPresent.setLastUpdatedTs(new Date());
      auditLogEntityAlreadyPresent.setUpdatedBy(instruction.getCreateUserId());
      return auditLogEntityAlreadyPresent;
    }
    return auditLogEntity;
  }

  /**
   * This method checks if audit tags for a delivery are complete and updates delivery status as
   * complete
   *
   * @param deliveryNumber
   */
  private void completeDeliveryIfLastAuditTag(Long deliveryNumber) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_COMPLETE_DELIVERY_FOR_LAST_AUDIT_TAG_ENABLED,
        false)) {
      List<AuditLogEntity> auditLogEntityList =
          fetchAuditLogsByDeliveryNumberAndPendingAuditStatus(deliveryNumber);
      if (CollectionUtils.isEmpty(auditLogEntityList)) {
        try {
          Map<String, Object> deliveryStatusMessageHeaders =
              RdcDeliveryStatusUtils.getDeliveryStatusMessageHeaders(
                  ReceivingUtils.getHeaders(), deliveryNumber);
          OsdrSummary osdrSummaryResponse =
              rdcOsdrSummaryService.getOsdrSummary(deliveryNumber, ReceivingUtils.getHeaders());
          rdcMessagePublisher.publishDeliveryReceipts(
              osdrSummaryResponse, deliveryStatusMessageHeaders);
        } catch (Exception exception) {
          logger.error("Error occurred while completing delivery for last Audit Tag", exception);
        }
      }
    }
  }

  private List<AuditLogEntity> fetchAuditLogsByDeliveryNumberAndPendingAuditStatus(
      Long deliveryNumber) {
    return auditLogPersisterService.getAuditLogByDeliveryNumberAndStatus(
        deliveryNumber, AuditStatus.valueOfStatus(AuditStatus.PENDING.getStatus()));
  }

  private void calculateAndLogElapsedTimeSummaryForDSDCPackReceive() {
    long timeTakenForInstructionAndAuditDataUpdate =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getReceiveAuditPackDBPersistCallStart(),
            TenantContext.get().getReceiveAuditPackDBPersistCallEnd());
    long timeTakenForUpdateAuditStatusInGDM =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getUpdateAuditStatusInGdmStart(),
            TenantContext.get().getUpdateAuditStatusInGdmEnd());
    long timeTakenToFetchInstruction =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFindInstructionByDeliveryNumberAndSsccStart(),
            TenantContext.get().getFindInstructionByDeliveryNumberAndSsccEnd());
    logger.info(
        "LatencyCheck receiving Audit DSDC Pack at ts ={} timeTakenByGDM={} timeTakenToUpdateInstructionAndAuditInfoInDB ={} timeTakenToFetchInstructionFromDB={}",
        TenantContext.get().getReceiveAuditPackCallStart(),
        timeTakenForUpdateAuditStatusInGDM,
        timeTakenForInstructionAndAuditDataUpdate,
        timeTakenToFetchInstruction);
  }

  private void calculateAndLogElapsedTimeSummaryForReceivePack() {
    long timeTakenByAllDBcalls =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasDsdcReceivePackDBPersistStart(),
            TenantContext.get().getAtlasDsdcReceivePackDBPersistEnd());
    long timeTakenForPostReceivingUpdates =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasDsdcReceivePackPostReceivingUpdatesStart(),
            TenantContext.get().getAtlasDsdcReceivePackPostReceivingUpdatesEnd());
    long timeTakenByGDM =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getUpdateAuditStatusInGdmStart(),
            TenantContext.get().getUpdateAuditStatusInGdmEnd());
    long timeTakenToFetchInstruction =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFindInstructionByDeliveryNumberAndSsccStart(),
            TenantContext.get().getFindInstructionByDeliveryNumberAndSsccEnd());
    logger.info(
        "LatencyCheck receivePack at ts = {}, timeTakenByAllDBcalls = {}, timeTakenByPostReceivingUpdates = {}, timeTakenByGDM = {}, timeTakenToFetchInstructionFromDB={}",
        TenantContext.get().getAtlasDsdcReceivePackStart(),
        timeTakenByAllDBcalls,
        timeTakenForPostReceivingUpdates,
        timeTakenByGDM,
        timeTakenToFetchInstruction);
  }

  /**
   * Receive pack details
   *
   * @param trackingId
   * @param httpHeaders
   * @return
   */
  @Override
  @Transactional(
      rollbackFor = {
        ReceivingException.class,
        ReceivingBadDataException.class,
        ReceivingInternalException.class
      })
  @InjectTenantFilter
  public ReceivePackResponse receiveDsdcPackByTrackingId(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException {
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    ReceivePackResponse receivePackResponse = null;
    try {
      TenantContext.get().setAtlasDsdcAsyncReceivePackStart(System.currentTimeMillis());
      List<LabelData> labelDataList = fetchLabelDataByTrackingId(trackingId);
      Container container = fetchContainerByTrackingId(trackingId);
      Instruction instruction =
          instructionPersisterService.getInstructionById(container.getInstructionId());
      InstructionRequest instructionRequest =
          prepareInstructionRequestByInstructionId(
              container.getDeliveryNumber(), httpHeaders, container.getSsccNumber(), container);
      List<DeliveryDocument> gdmDeliveryDocumentList =
          rdcInstructionService.fetchDeliveryDocument(instructionRequest, httpHeaders);

      ReceivePackRequest receivePackRequest =
          ReceivePackRequest.builder()
              .packNumber(labelDataList.get(0).getSscc())
              .asnNumber(labelDataList.get(0).getAsnNumber())
              .build();

      AuditLogEntity auditLogEntity = fetchPendingAuditPackIfExists(receivePackRequest);
      InstructionResponseImplNew response =
          receiveDsdcContainerByTrackingId(
              labelDataList,
              trackingId,
              instructionRequest,
              auditLogEntity,
              gdmDeliveryDocumentList,
              httpHeaders,
              receivePackRequest,
              instruction);

      receivePackResponse =
          ReceivePackResponse.builder()
              .deliveryNumber(instructionRequest.getDeliveryNumber())
              .trackingId(trackingId)
              .asnNumber(receivePackRequest.getAsnNumber())
              .packNumber(receivePackRequest.getPackNumber())
              .auditStatus(AuditStatus.COMPLETED.getStatus())
              .receivingStatus(ReceivingConstants.STATUS_COMPLETE)
              .printJob(response.getPrintJob())
              .build();
      TenantContext.get().setAtlasDsdcAsyncReceivePackEnd(System.currentTimeMillis());
      calculateAndLogElapsedTimeSummaryForAsyncReceivePack();
    } catch (Exception exception) {
      logger.error("Error occurred while receivePackByTrackingId", exception);
      throw exception;
    }
    return receivePackResponse;
  }

  /**
   * Async DSDC pack receiving using tracking id. This method build received containers and performs
   * DB operations for container, receipts, labels and audit
   *
   * @param labelDataList
   * @param trackingId
   * @param instructionRequest
   * @param auditLog
   * @param gdmDeliveryDocumentList
   * @param httpHeaders
   * @param receivePackRequest
   * @param instruction
   * @return
   */
  private InstructionResponseImplNew receiveDsdcContainerByTrackingId(
      List<LabelData> labelDataList,
      String trackingId,
      InstructionRequest instructionRequest,
      AuditLogEntity auditLog,
      List<DeliveryDocument> gdmDeliveryDocumentList,
      HttpHeaders httpHeaders,
      ReceivePackRequest receivePackRequest,
      Instruction instruction) {
    List<Container> containers = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Collection<OutboxEvent> outboxEvents = new ArrayList<>();
    try {
      boolean isSsccAlreadyReceived =
          validateSsccAlreadyReceivedAndBlockRequest(receivePackRequest);
      DeliveryDocument deliveryDocument = gdmDeliveryDocumentList.get(0);
      List<ReceivedContainer> receivedContainers =
          transformLabelData(labelDataList, gdmDeliveryDocumentList);
      ReceivedContainer parentReceivedContainer = getParentReceivedContainer(receivedContainers);
      String parentTrackingId = parentReceivedContainer.getLabelTrackingId();
      if (isSsccAlreadyReceived) {
        return new InstructionResponseImplNew(
            instructionRequest.getDeliveryStatus(),
            Collections.singletonList(deliveryDocument),
            instruction,
            null,
            parentTrackingId);
      }
      // filter child containers
      receivedContainers =
          receivedContainers
              .stream()
              .filter(
                  receivedContainer ->
                      StringUtils.isNotEmpty(receivedContainer.getParentTrackingId()))
              .collect(Collectors.toList());
      // prepare container and container item
      for (ReceivedContainer receivedContainer : receivedContainers) {
        deliveryDocument =
            filterDeliveryDocumentByPoAndPoLineNumber(
                gdmDeliveryDocumentList,
                receivedContainer.getPoNumber(),
                receivedContainer.getPoLine());
        if (Objects.nonNull(deliveryDocument)) {
          Integer receivedQtyInEaches = receivedContainer.getPack();
          ContainerItem containerItem =
              rdcContainerUtils.buildContainerItemDetails(
                  receivedContainer.getLabelTrackingId(),
                  deliveryDocument,
                  receivedQtyInEaches,
                  null,
                  receivedContainer.getStoreAlignment(),
                  receivedContainer.getDistributions(),
                  receivedContainer.getDestType());
          Container container =
              rdcContainerUtils.buildContainer(
                  instructionRequest.getDoorNumber(),
                  instruction.getId(),
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  instructionRequest.getMessageId(),
                  deliveryDocument,
                  userId,
                  receivedContainer,
                  null,
                  null);
          container.setSsccNumber(receivedContainer.getSscc());
          containers.add(container);
          containerItems.add(containerItem);
        }
      }

      labelDataList.forEach(
          labelData -> labelData.setStatus(LabelInstructionStatus.COMPLETE.name()));

      // build Receipts from container items
      List<ContainerItem> containerItemsForReceipts =
          aggregateContainerItemQuantityByPoPoLine(containerItems);
      List<Receipt> receipts =
          receiptService.buildReceiptsFromContainerItems(
              Long.valueOf(instructionRequest.getDeliveryNumber()),
              labelDataList.get(0).getAsnNumber(),
              instructionRequest.getDoorNumber(),
              labelDataList.get(0).getSscc(),
              userId,
              containerItemsForReceipts);

      // audit log status update
      if (Objects.nonNull(auditLog)) {
        auditLog.setAuditStatus(AuditStatus.COMPLETED);
        auditLog.setCompletedBy(userId);
        auditLog.setCompletedTs(new Date());
        auditLog.setLastUpdatedTs(new Date());
      }

      TenantContext.get().setAtlasDsdcReceivePackDBPersistStart(System.currentTimeMillis());
      persistContainersInDB(null, containers, containerItems, receipts, labelDataList, auditLog);
      TenantContext.get().setAtlasDsdcReceivePackDBPersistEnd(System.currentTimeMillis());

      // Update Audit status in GDM
      if (Objects.nonNull(auditLog)) {
        TenantContext.get().setUpdateAuditStatusInGdmStart(System.currentTimeMillis());
        updateAuditStatusInGdm(
            AuditStatus.COMPLETED.ordinal(),
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            httpHeaders);
        TenantContext.get().setUpdateAuditStatusInGdmEnd(System.currentTimeMillis());
      } else {
        TenantContext.get().setUpdateAuditStatusInGdmStart(System.currentTimeMillis());
        updateAuditStatusInGdm(
            AuditStatus.NOT_REQUIRED.ordinal(),
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            httpHeaders);
        TenantContext.get().setUpdateAuditStatusInGdmEnd(System.currentTimeMillis());
      }

      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC,
          false)) {
        TenantContext.get().setPersistOutboxEventsForDAStart(System.currentTimeMillis());
        outboxEvents =
            rdcReceivingUtils.buildOutboxEventsForAsyncFlow(receivedContainers, httpHeaders);
        rdcReceivingUtils.persistOutboxEvents(outboxEvents);
        TenantContext.get().setPersistOutboxEventsForDAEnd(System.currentTimeMillis());
      } else {
        TenantContext.get()
            .setAtlasDsdcReceivePackPostReceivingUpdatesStart(System.currentTimeMillis());
        postReceivingUpdatesForRapidRelayerCallBack(
            trackingId, receivePackRequest.getAsnNumber(), deliveryDocument);
        TenantContext.get()
            .setAtlasDsdcReceivePackPostReceivingUpdatesEnd(System.currentTimeMillis());
      }
      return new InstructionResponseImplNew(
          instructionRequest.getDeliveryStatus(),
          Collections.singletonList(deliveryDocument),
          instruction,
          null,
          parentTrackingId);
    } catch (ReceivingBadDataException rbde) {
      throw rbde;
    } catch (Exception e) {
      throw new ReceivingInternalException(
          String.format(ExceptionCodes.RECEIVE_DSDC_INSTRUCTION_INTERNAL_ERROR),
          String.format(ReceivingException.DSDC_RECEIVE_INSTRUCTION_ERROR_MSG, trackingId),
          e);
    }
  }

  /**
   * Retrieving label data using tracking id with InProgress status
   *
   * @param trackingId
   * @return
   */
  private List<LabelData> fetchLabelDataByTrackingId(String trackingId) {
    LabelData labelData = labelDataService.findByTrackingId(trackingId);
    if (Objects.isNull(labelData)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.NO_ALLOCATIONS_FOR_DSDC_FREIGHT_BY_LPN,
          ReceivingException.NO_ALLOCATIONS_FOR_DSDC_FREIGHT,
          trackingId);
    }
    return Collections.singletonList(labelData);
  }

  /**
   * Retrieving container using tracking id
   *
   * @param trackingId
   * @return
   */
  private Container fetchContainerByTrackingId(String trackingId) {
    Container container = containerService.findByTrackingId(trackingId);
    if (Objects.isNull(container)) {
      throw new ReceivingBadDataException(
          String.format(ExceptionCodes.CONTAINER_NOT_FOUND),
          String.format(ReceivingException.CONTAINER_NOT_FOUND, trackingId),
          trackingId);
    }
    return container;
  }

  /**
   * Preparation of InstructionRequest
   *
   * @param deliveryNumber
   * @param httpHeaders
   * @param sscc
   * @return
   */
  private InstructionRequest prepareInstructionRequestByInstructionId(
      Long deliveryNumber, HttpHeaders httpHeaders, String sscc, Container container) {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setReceivingType(ReceivingConstants.SSCC);
    instructionRequest.setSscc(sscc);
    instructionRequest.setDeliveryNumber(deliveryNumber.toString());
    instructionRequest.setDoorNumber(container.getLocation());
    instructionRequest.setMessageId(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    return instructionRequest;
  }

  private void buildOutboxEventForCallbackToReceiveDsdcPack(
      Container container, HttpHeaders httpHeaders) {
    Map<String, Object> headers = ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    MetaData metaData =
        MetaData.with(ReceivingConstants.CONTAINER_TRACKING_ID, container.getTrackingId());
    String eventId =
        getFacilityCountryCode()
            + RdcConstants.UNDERSCORE
            + getFacilityNum()
            + RdcConstants.UNDERSCORE
            + container.getTrackingId();
    String outboxPolicy = outboxConfig.getHttpPublisherPolicyForDsdcReceiving();
    OutboxEvent outboxEvent =
        rdcReceivingUtils.buildOutboxEvent(
            headers, null, eventId, metaData, outboxPolicy, Instant.now());
    rdcReceivingUtils.persistOutboxEvents(Collections.singleton(outboxEvent));
  }

  /**
   * This method group container items by PoPoLine and aggregate the received Qty which are in
   * eaches. In case of DSDC, the labels will be sent by orders which has allocatedQty in eaches.
   * For Break Pack cases in DSDC the labels will be generated at inner pick level so the
   * allocatedQty will be in WHPK qty. This aggregation is needed to map the overall allocatedQty
   * from eaches to VNPK
   *
   * @param containerItems
   * @return
   */
  public List<ContainerItem> aggregateContainerItemQuantityByPoPoLine(
      List<ContainerItem> containerItems) {
    // filter only the child container items which has the Po/PoLine/Qty details
    List<ContainerItem> childContainerItems =
        containerItems
            .stream()
            .filter(
                containerItem ->
                    ObjectUtils.allNotNull(
                        containerItem.getPurchaseReferenceNumber(),
                        containerItem.getPurchaseReferenceLineNumber(),
                        containerItem.getQuantity()))
            .collect(Collectors.toList());
    List<ContainerItem> childContainerItemsListGroupedByPoPoLine =
        getContainerItemsGroupedByPoPoLine(childContainerItems);
    List<ContainerItem> aggregatedChildContainerItemsListByPoPoLine = new ArrayList<>();
    for (ContainerItem containerItemByPoPoLine : childContainerItemsListGroupedByPoPoLine) {
      ContainerItem containerItemGroupedByPoPoLine =
          SerializationUtils.clone(containerItemByPoPoLine);
      Integer aggregatedReceivedQtyPoPoLineInEaches =
          childContainerItems
              .stream()
              .filter(
                  containerItem ->
                      containerItem
                              .getPurchaseReferenceNumber()
                              .equals(containerItemByPoPoLine.getPurchaseReferenceNumber())
                          && containerItem
                              .getPurchaseReferenceLineNumber()
                              .equals(containerItemByPoPoLine.getPurchaseReferenceLineNumber())
                          && Objects.nonNull(containerItem.getQuantity()))
              .mapToInt(ContainerItem::getQuantity)
              .sum();
      containerItemGroupedByPoPoLine.setQuantity(aggregatedReceivedQtyPoPoLineInEaches);
      aggregatedChildContainerItemsListByPoPoLine.add(containerItemGroupedByPoPoLine);
    }

    return aggregatedChildContainerItemsListByPoPoLine;
  }

  /**
   * @param childContainerItems
   * @return
   */
  private List<ContainerItem> getContainerItemsGroupedByPoPoLine(
      List<ContainerItem> childContainerItems) {
    // group container items by PoPoLine
    Map<List<Object>, ContainerItem> containerMapValues =
        childContainerItems
            .stream()
            .collect(
                Collectors.groupingBy(
                    ContainerItem ->
                        Arrays.asList(
                            ContainerItem.getPurchaseReferenceNumber(),
                            ContainerItem.getPurchaseReferenceLineNumber()),
                    Collectors.collectingAndThen(Collectors.toList(), list -> list.get(0))));
    return new ArrayList<>(containerMapValues.values());
  }

  /**
   * This method publishes receipts/purchases to Inventory/EI. The sorter integration have published
   * earlier before the Rapid Relayer call back to this method
   *
   * @param parentTrackingId
   * @param asnNumber
   * @param deliveryDocument
   * @throws ReceivingException
   */
  public void postReceivingUpdatesForRapidRelayerCallBack(
      String parentTrackingId, String asnNumber, DeliveryDocument deliveryDocument)
      throws ReceivingException {
    Container consolidatedContainer =
        containerPersisterService.getConsolidatedContainerForPublish(parentTrackingId);
    if (Objects.nonNull(asnNumber)) {
      consolidatedContainer.setAsnNumber(asnNumber);
    }

    if (Objects.nonNull(deliveryDocument)
        && tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CHILD_CONTAINER_SPLIT_ENABLED_FOR_DSDC,
            false)) {
      rdcReceivingUtils.publishConsolidatedSplitContainerToInventoryForDsdc(
          parentTrackingId, consolidatedContainer);
    } else {
      // publish consolidated or parent containers to Inventory
      TenantContext.get().setReceiveInstrPublishReceiptsCallStart(System.currentTimeMillis());
      rdcContainerUtils.publishContainersToInventory(consolidatedContainer);
      TenantContext.get().setReceiveInstrPublishReceiptsCallEnd(System.currentTimeMillis());
    }

    // publish consolidated or parent containers to EI
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RdcConstants.IS_EI_INTEGRATION_ENABLED, false)) {
      TenantContext.get().setPublishEICallStart(System.currentTimeMillis());
      String[] eiEvents =
          consolidatedContainer.getInventoryStatus().equals(InventoryStatus.PICKED.name())
              ? ReceivingConstants.EI_DC_RECEIVING_AND_PICK_EVENTS
              : ReceivingConstants.EI_DC_RECEIVING_EVENT;
      rdcContainerUtils.publishContainerToEI(consolidatedContainer, eiEvents);
      TenantContext.get().setPublishEICallEnd(System.currentTimeMillis());
    }
  }

  /** Async Receiving pack time taken logging */
  private void calculateAndLogElapsedTimeSummaryForAsyncReceivePack() {
    long timeTakenByAllDBcalls =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasDsdcReceivePackDBPersistStart(),
            TenantContext.get().getAtlasDsdcReceivePackDBPersistEnd());
    long timeTakenForPostReceivingUpdates =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasDsdcReceivePackPostReceivingUpdatesStart(),
            TenantContext.get().getAtlasDsdcReceivePackPostReceivingUpdatesEnd());
    long timeTakenByGDM =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getUpdateAuditStatusInGdmStart(),
            TenantContext.get().getUpdateAuditStatusInGdmEnd());
    long timeTakenToFetchInstruction =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getFindInstructionByDeliveryNumberAndSsccStart(),
            TenantContext.get().getFindInstructionByDeliveryNumberAndSsccEnd());
    logger.info(
        "LatencyCheck asyncReceivePack at ts = {}, timeTakenByAllDBcalls = {}, timeTakenByPostReceivingUpdates = {}, timeTakenByGDM = {}, timeTakenToFetchInstructionFromDB={}",
        TenantContext.get().getAtlasDsdcAsyncReceivePackStart(),
        timeTakenByAllDBcalls,
        timeTakenForPostReceivingUpdates,
        timeTakenByGDM,
        timeTakenToFetchInstruction);
  }

  /**
   * This method checks for pending audit pack based on asn and pack number and return audit log if
   * exists
   *
   * @param receivePackRequest
   * @return
   */
  private AuditLogEntity fetchPendingAuditPackIfExists(ReceivePackRequest receivePackRequest) {
    return auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
        receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber(), AuditStatus.PENDING);
  }

  private Collection<OutboxEvent> buildOutboxEventForSoterDivertAndWFT(
      ReceivedContainer receivedContainer,
      DeliveryDocument deliveryDocument,
      Instruction instruction,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    Collection<OutboxEvent> outboxEvents = null;
    Map<String, List<PayloadRef>> outboxPolicyMap = new HashMap<>();
    Container consolidatedContainer =
        containerPersisterService.getConsolidatedContainerForPublish(
            receivedContainer.getLabelTrackingId());
    if (receivedContainer.isSorterDivertRequired()) {
      rdcReceivingUtils.buildOutboxEventForSorterDivert(
          consolidatedContainer, receivedContainer, outboxPolicyMap, httpHeaders, deliveryDocument);
    }
    outboxEvents =
        rdcReceivingUtils.buildOutboxEvent(consolidatedContainer.getTrackingId(), outboxPolicyMap);
    if (!CollectionUtils.isEmpty(outboxEvents)) {
      outboxEvents.addAll(
          rdcReceivingUtils.buildOutboxEventForWFT(
              instruction,
              deliveryDocument.getDeliveryDocumentLines().get(0),
              instruction.getReceivedQuantity(),
              httpHeaders,
              false));
    }

    return outboxEvents;
  }

  /**
   * This method validates if the scanned SSCC already received or not
   *
   * @param receivePackRequest
   */
  private boolean isSsccAlreadyReceived(ReceivePackRequest receivePackRequest) {
    List<LabelData> labelDataList =
        labelDataService.findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name());
    boolean isSsccAlreadyReceived = !CollectionUtils.isEmpty(labelDataList);
    return isSsccAlreadyReceived;
  }

  /**
   * This method validates if the scanned SSCC already received or not
   *
   * <p>If SSCC already validate, validate with CCM config to allow for re print or block request
   *
   * @param receivePackRequest
   */
  private boolean validateSsccAlreadyReceivedAndBlockRequest(
      ReceivePackRequest receivePackRequest) {
    AuditLogEntity auditLogEntity =
        auditLogPersisterService.getAuditDetailsByAsnNumberAndSsccAndStatus(
            receivePackRequest.getAsnNumber(),
            receivePackRequest.getPackNumber(),
            AuditStatus.COMPLETED);
    List<LabelData> labelDataList =
        labelDataService.findBySsccAndAsnNumberAndStatus(
            receivePackRequest.getPackNumber(),
            receivePackRequest.getAsnNumber(),
            LabelInstructionStatus.COMPLETE.name());
    boolean isDsdcReprintBlocked =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC,
            false);
    boolean isSsccAlreadyReceived = !CollectionUtils.isEmpty(labelDataList);
    boolean isAuditCompleted = Objects.nonNull(auditLogEntity);
    boolean isReprintAllowed = false;
    if (isSsccAlreadyReceived) {
      if (isAuditCompleted) {
        if (isDsdcReprintBlocked) {
          throwDsdcSsscAlreadyReceivedException(receivePackRequest, labelDataList);
        } else {
          isReprintAllowed = true;
        }
      } else {
        throwDsdcSsscAlreadyReceivedException(receivePackRequest, labelDataList);
      }
    }
    return isReprintAllowed;
  }

  /**
   * Throw ReceivingBadDataException with DSDC_SSCC_ALREADY_RECEIVED error code
   *
   * @param receivePackRequest
   * @param labelDataList
   */
  private void throwDsdcSsscAlreadyReceivedException(
      ReceivePackRequest receivePackRequest, List<LabelData> labelDataList) {
    String trackingId = StringUtils.trimToEmpty(labelDataList.get(0).getTrackingId());
    logger.error(
        String.format(
            ReceivingException.DSDC_SSCC_ALREADY_RECEIVED,
            receivePackRequest.getPackNumber(),
            trackingId));
    throw new ReceivingBadDataException(
        String.format(ExceptionCodes.DSDC_SSCC_ALREADY_RECEIVED),
        String.format(
            ReceivingException.DSDC_SSCC_ALREADY_RECEIVED,
            receivePackRequest.getPackNumber(),
            trackingId));
  }

  /**
   * Update pack details
   *
   * @param receivePackRequest
   * @return
   */
  @Transactional(
      rollbackFor = {
        ReceivingException.class,
        ReceivingBadDataException.class,
        ReceivingInternalException.class
      })
  @InjectTenantFilter
  public String updatePack(ReceivePackRequest receivePackRequest, HttpHeaders httpHeaders)
      throws Exception {
    String response = null;
    try {
      TenantContext.get().setAtlasDsdcUpdatePackStart(System.currentTimeMillis());
      boolean isSsccAlreadyReceived = isSsccAlreadyReceived(receivePackRequest);
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

      if (isSsccAlreadyReceived) {
        logger.error(
            String.format(
                ReceivingException.SSCC_ALREADY_RECEIVED, receivePackRequest.getPackNumber()));
        throw new ReceivingBadDataException(
            String.format(ExceptionCodes.SSCC_ALREADY_RECEIVED),
            String.format(
                ReceivingException.SSCC_ALREADY_RECEIVED, receivePackRequest.getPackNumber()),
            receivePackRequest.getPackNumber());
      }

      TenantContext.get().setFetchAuditPackCallStart(System.currentTimeMillis());
      AuditLogEntity auditLog = fetchAuditPack(receivePackRequest);
      TenantContext.get().setFetchAuditPackCallEnd(System.currentTimeMillis());

      if (Objects.isNull(auditLog)) {
        logger.error(String.format(ReceivingException.SSCC_NOT_FOUND));
        throw new ReceivingBadDataException(
            String.format(ExceptionCodes.SSCC_NOT_FOUND),
            String.format(ReceivingException.SSCC_NOT_FOUND));
      }

      // Update Audit status in DB
      if (Objects.nonNull(auditLog)
          && AuditStatus.CANCELLED
              .getStatus()
              .equalsIgnoreCase(receivePackRequest.getEventType())) {
        auditLog.setAuditStatus(AuditStatus.CANCELLED);
        auditLog.setCompletedBy(userId);
        auditLog.setCompletedTs(new Date());
        auditLog.setLastUpdatedTs(new Date());

        TenantContext.get().setAtlasDsdcUpdatePackDBPersistStart(System.currentTimeMillis());
        persistContainersInDB(
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            auditLog);
        response =
            String.format(
                RdcConstants.CANCEL_AUDIT_PACK_SUCCESS_MESSAGE, receivePackRequest.getPackNumber());
      }

      TenantContext.get().setAtlasDsdcUpdatePackEnd(System.currentTimeMillis());
      calculateAndLogElapsedTimeSummaryForUpdatePack();
    } catch (Exception exception) {
      logger.error(
          "Error occurred while updating pack: {} and asn: {} - {}",
          receivePackRequest.getPackNumber(),
          receivePackRequest.getAsnNumber(),
          exception);
      throw exception;
    }
    return response;
  }

  private void calculateAndLogElapsedTimeSummaryForUpdatePack() {
    long timeTakenForPackUpdateInDB =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasDsdcUpdatePackDBPersistStart(),
            TenantContext.get().getAtlasDsdcUpdatePackDBPersistEnd());
    long timeTakenForAuditPackStatusUpdate =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasDsdcUpdatePackStart(),
            TenantContext.get().getAtlasDsdcUpdatePackEnd());
    long timeTakenToFetchAuditPack =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasDsdcUpdatePackDBPersistStart(),
            TenantContext.get().getAtlasDsdcUpdatePackDBPersistEnd());
    logger.info(
        "LatencyCheck timeTakenForPackUpdateInDB = {}, timeTakenForAuditPackStatusUpdate = {}, timeTakenToFetchAuditPack = {}",
        timeTakenForPackUpdateInDB,
        timeTakenForAuditPackStatusUpdate,
        timeTakenToFetchAuditPack);
  }

  /**
   * @param receivePackRequest
   * @return
   */
  private AuditLogEntity fetchAuditPack(ReceivePackRequest receivePackRequest) {
    return auditLogPersisterService.getAuditDetailsByAsnNumberAndSscc(
        receivePackRequest.getAsnNumber(), receivePackRequest.getPackNumber());
  }
}
