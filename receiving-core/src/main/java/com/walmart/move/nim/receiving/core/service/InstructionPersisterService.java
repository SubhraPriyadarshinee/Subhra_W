package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.InstructionUtils.isNationalPO;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * This service is responsible for persisting smaller transaction related to instruction. As in
 * spring transaction boundary is not getting created if caller method and called method are in same
 * class.
 *
 * <p>Reason - The problem here is, that Spring's AOP proxies don't extend but rather wrap your
 * service instance to intercept calls. This has the effect, that any call to "this" from within
 * your service instance is directly invoked on that instance and cannot be intercepted by the
 * wrapping proxy (the proxy is not even aware of any such call).
 */
@Service(ReceivingConstants.INSTRUCTION_PERSISTER_SERVICE)
public class InstructionPersisterService implements Purge {
  private static final Logger LOG = LoggerFactory.getLogger(InstructionPersisterService.class);
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private ContainerService containerService;
  @Autowired private ReceiptService receiptService;
  @Autowired private PrintJobService printJobService;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  private InstructionError instructionError;

  /**
   * Return instruction based on instruction id
   *
   * @param id instruction id
   * @return Instruction
   */
  @Transactional
  @InjectTenantFilter
  public Instruction getInstructionById(Long id) throws ReceivingException {
    Optional<Instruction> instruction = instructionRepository.findById(id);
    if (!instruction.isPresent()) {
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.INSTRUCTION_NOT_FOUND)
              .errorKey(ExceptionCodes.INSTRUCTION_NOT_FOUND)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }
    return instruction.get();
  }

  @Transactional
  @InjectTenantFilter
  public Instruction saveInstruction(Instruction instructionResponse) {
    // Completing Instruction.
    return instructionRepository.save(instructionResponse);
  }

  @Transactional
  @InjectTenantFilter
  public List<Instruction> saveAllInstruction(List<Instruction> instructions) {
    return instructionRepository.saveAll(instructions);
  }

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public Container createContainerForDockTag(
      Instruction dockTagInstruction,
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders,
      boolean markCompleted) {
    return containerService.createDockTagContainer(
        dockTagInstruction, instructionRequest, httpHeaders, markCompleted);
  }

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public void processCreateChildContainers(
      Instruction instruction,
      UpdateInstructionRequest instructionRequest,
      ContainerDetails childContainerDtls,
      ContainerDetails scannedContainer,
      String userId)
      throws ReceivingException {
    containerService.processCreateChildContainers(
        instruction, instructionRequest, childContainerDtls, scannedContainer);

    Integer receivedQuantity = instruction.getReceivedQuantity();
    DocumentLine documentLine = instructionRequest.getDeliveryDocumentLines().get(0);
    Integer quantityToBeReceived = documentLine.getQuantity();
    boolean isNationalPo =
        isNationalPO(instructionRequest.getDeliveryDocumentLines().get(0).getPurchaseRefType());

    if (isNationalPo) {
      List<Receipt> receiptsResponse =
          receiptService.createReceiptsFromInstruction(
              instructionRequest, instruction.getProblemTagId(), userId);
      for (Receipt receipt : receiptsResponse) {
        receivedQuantity += receipt.getQuantity();
      }
    } else {
      receiptService.createReceiptsFromInstructionForPOCON(instructionRequest, userId);
      receivedQuantity += quantityToBeReceived;
    }

    instruction.setLastChangeUserId(userId);
    if (!(ReceivingConstants.Uom.EACHES.equalsIgnoreCase(documentLine.getQuantityUOM())
        && !instructionRequest.isUnitScanCompleted())) {
      instruction.setReceivedQuantity(receivedQuantity);
    }

    saveInstruction(instruction);
  }

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public void createContainersAndPrintJobs(
      UpdateInstructionRequest instructionUpdateRequestFromClient,
      HttpHeaders httpHeaders,
      String userId,
      Instruction instructionResponse,
      Integer quantityToBeReceived,
      Integer receivedQuantity,
      List<ContainerDetails> childContainers)
      throws ReceivingException {
    boolean isNationalPo =
        isNationalPO(
            instructionUpdateRequestFromClient
                .getDeliveryDocumentLines()
                .get(0)
                .getPurchaseRefType());

    containerService.processCreateContainers(
        instructionResponse, instructionUpdateRequestFromClient, httpHeaders);

    List<String> childCtrTrackingIdInfo = null;
    if (!CollectionUtils.isEmpty(childContainers)) {
      childCtrTrackingIdInfo =
          containerService.getCreatedChildContainerTrackingIds(
              childContainers, receivedQuantity, quantityToBeReceived);
      Labels labels = instructionResponse.getLabels();
      childCtrTrackingIdInfo.forEach(
          trackingId -> {
            labels.getAvailableLabels().remove(trackingId);
            labels.getUsedLabels().add(trackingId);
          });
      instructionResponse.setLabels(labels);
      // check why getPrintChildContainerLabels is not validated for true
      if (instructionResponse.getPrintChildContainerLabels() != null) {
        // Persisting print job
        Set<String> printJobLpnSet = new HashSet<>();
        printJobLpnSet.addAll(childCtrTrackingIdInfo);
        printJobService.createPrintJob(
            instructionResponse.getDeliveryNumber(),
            instructionResponse.getId(),
            printJobLpnSet,
            httpHeaders.getFirst(USER_ID_HEADER_KEY));
      }
    }

    if (isNationalPo) {
      List<Receipt> receiptsResponse =
          receiptService.createReceiptsFromInstruction(
              instructionUpdateRequestFromClient, instructionResponse.getProblemTagId(), userId);
      for (Receipt receipt : receiptsResponse) {
        receivedQuantity += receipt.getQuantity();
      }
    } else {
      receiptService.createReceiptsFromInstructionForPOCON(
          instructionUpdateRequestFromClient, userId);
      receivedQuantity += quantityToBeReceived;
    }

    instructionResponse.setLastChangeUserId(userId);
    instructionResponse.setReceivedQuantity(receivedQuantity);

    saveInstruction(instructionResponse);
  }

  /**
   * Instruction's Complete and Create Container, ContainerItem, Receipts will success/fail as
   * automic
   *
   * @param poLineReq
   * @param updateInstructionRequest
   * @param httpHeaders
   * @param instructionResponse
   * @param receivedQuantity
   * @throws ReceivingException
   */
  @InjectTenantFilter
  public void updateInstructionAndCreateContainerAndReceipt(
      PoLine poLineReq,
      UpdateInstructionRequest updateInstructionRequest,
      HttpHeaders httpHeaders,
      Instruction instructionResponse,
      Integer receivedQuantity,
      List<String> trackingIds)
      throws ReceivingException {

    List<Container> containerList = new ArrayList<>();
    List<Receipt> receiptList = new ArrayList<>();

    Receipt masterReceipt =
        receiptService
            .findFirstMasterReceiptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                updateInstructionRequest.getDeliveryNumber(),
                updateInstructionRequest
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getPurchaseReferenceNumber(),
                updateInstructionRequest
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getPurchaseReferenceLineNumber());

    // Calculated qty and qty UOM to document change it
    DocumentLine documentLine = updateInstructionRequest.getDeliveryDocumentLines().get(0);
    String correlationId = String.valueOf(httpHeaders.getFirst(CORRELATION_ID));

    LOG.info("delivery document {} correlation {}", documentLine, correlationId);
    final Integer tiHi = documentLine.getPalletHi() * documentLine.getPalletTi();
    int remainingCasesToReceive = poLineReq.getReceiveQty();
    LOG.info(
        "Calculated TiHi {} and user entered qty {} correlation {}",
        tiHi,
        poLineReq.getReceiveQty(),
        correlationId);

    // Create pallets equals to number of trackingIds fetched from LPN
    for (String trackingId : trackingIds) {
      int receivingQty = 0;
      if (remainingCasesToReceive > tiHi) {
        receivingQty = tiHi;
        remainingCasesToReceive -= tiHi;
      } else {
        receivingQty = remainingCasesToReceive;
      }

      LOG.info(
          "Create Container for pallet Id {} qty {} correlationid {}",
          trackingId,
          receivingQty,
          correlationId);
      documentLine.setQuantity(receivingQty);

      // 1.Container
      instructionResponse.getContainer().setTrackingId(trackingId);
      containerList.add(
          containerService.constructContainerList(instructionResponse, updateInstructionRequest));

      // 2.create Receipt,
      // Master Receipt
      List<Receipt> receiptsResponse =
          receiptService.createReceiptsFromUpdateInstructionRequestWithOsdrMaster(
              updateInstructionRequest, httpHeaders);
      if (isNull(masterReceipt)) {
        LOG.info(
            "No Master Receipt found for delivery number {}, "
                + "purchase reference number {} and purchase reference line number {},so updating master_receipt value",
            updateInstructionRequest.getDeliveryNumber(),
            updateInstructionRequest.getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber(),
            updateInstructionRequest
                .getDeliveryDocumentLines()
                .get(0)
                .getPurchaseReferenceLineNumber());
        receiptsResponse.get(0).setOsdrMaster(OSDR_MASTER_RECORD_VALUE);
        receiptsResponse.get(0).setOrderFilledQuantity(ZERO_QTY);
        masterReceipt = receiptsResponse.get(0);
      }
      receiptList.addAll(receiptsResponse);
      for (Receipt receipt : receiptsResponse) receivedQuantity += receipt.getQuantity();
    }

    // 3. record Rejects if any
    LOG.info(
        "Record Reject(qty={}) IntoOss for deliver={}, po={}, line={}",
        poLineReq.getRejectQty(),
        instructionResponse.getDeliveryNumber(),
        instructionResponse.getPurchaseReferenceNumber(),
        instructionResponse.getPurchaseReferenceLineNumber());
    receiptService.updateRejects(
        poLineReq.getRejectQty(),
        poLineReq.getRejectQtyUOM(),
        poLineReq.getRejectReasonCode(),
        FLOW_RECEIVE_INTO_OSS,
        masterReceipt);

    // 4. record Damage if any
    LOG.info(
        "Record Damage(qty={}) IntoOss for deliver={}, po={}, line={}",
        poLineReq.getDamageQty(),
        instructionResponse.getDeliveryNumber(),
        instructionResponse.getPurchaseReferenceNumber(),
        instructionResponse.getPurchaseReferenceLineNumber());
    receiptService.updateDamages(
        poLineReq.getDamageQty(),
        poLineReq.getDamageQtyUOM(),
        poLineReq.getDamageReasonCode(),
        poLineReq.getDamageClaimType(),
        masterReceipt);

    containerService.saveAll(containerList);
    receiptService.saveAll(receiptList);

    instructionResponse.setLastChangeUserId(httpHeaders.getFirst(USER_ID_HEADER_KEY));
    instructionResponse.setReceivedQuantity(receivedQuantity);
    saveInstruction(instructionResponse);
  }

  /**
   * * Persists container, receipt and instruction in single transaction for ACL and Manual
   * receiving
   *
   * @param instructionUpdateRequest data for update instruction request
   * @param userId user id
   * @param instruction instruction
   * @param onConveyor is on conveyor or not
   * @return pair of container and instruction
   * @throws ReceivingException on rotate date parsing issue
   */
  @Transactional
  @InjectTenantFilter
  public Pair<Container, Instruction> createContainersReceiptsAndSaveInstruction(
      UpdateInstructionRequest instructionUpdateRequest,
      String userId,
      Instruction instruction,
      boolean onConveyor)
      throws ReceivingException {

    Instruction updatedInstruction =
        updateAndSaveInstruction(instructionUpdateRequest, userId, instruction);

    Container parentContainer =
        containerService.createAndCompleteParentContainer(
            updatedInstruction, instructionUpdateRequest, onConveyor);

    receiptService.createReceiptsFromInstruction(
        instructionUpdateRequest, updatedInstruction.getProblemTagId(), userId);

    return new Pair<>(parentContainer, updatedInstruction);
  }

  @Transactional
  @InjectTenantFilter
  public Instruction updateAndSaveInstruction(
      UpdateInstructionRequest instructionUpdateRequest, String userId, Instruction instruction) {

    instruction.setLastChangeUserId(userId);
    instruction.setReceivedQuantity(
        instructionUpdateRequest.getDeliveryDocumentLines().get(0).getQuantity());
    instruction.setCompleteUserId(userId);
    instruction.setCompleteTs(new Date());
    // Saving delivery document for usage after the if block
    String deliveryDocument = instruction.getDeliveryDocument();

    // Setting delivery document field to null, as it is not useful in LPN receiving case
    instruction.setDeliveryDocument(null);
    if (instruction.getManualInstruction()
        || tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.INSTRUCTION_SAVE_ENABLED,
            Boolean.TRUE)) {
      instruction = saveInstruction(instruction);
    }

    instruction.setDeliveryDocument(deliveryDocument);
    return instruction;
  }

  public Map<String, Object> getPrintlabeldata(
      Instruction instructionResponse,
      Integer quantityToBeReceived,
      Integer receivedQuantity,
      List<ContainerDetails> childContainers) {
    Map<String, Object> childCtrLabelsInfo = null;
    if (!CollectionUtils.isEmpty(childContainers)
        && instructionResponse.getPrintChildContainerLabels() != null) {
      childCtrLabelsInfo =
          containerService.getCreatedChildContainerLabels(
              childContainers, receivedQuantity, quantityToBeReceived);
    }
    return childCtrLabelsInfo;
  }

  public List<Map<String, Object>> getOldPrintlabeldata(
      Instruction instructionResponse,
      Integer quantityToBeReceived,
      Integer receivedQuantity,
      List<ContainerDetails> childContainers) {
    List<Map<String, Object>> childCtrLabelsInfo = null;
    if (!CollectionUtils.isEmpty(childContainers)
        && instructionResponse.getPrintChildContainerLabels() != null) {
      childCtrLabelsInfo =
          containerService.getOldFormatCreatedChildContainerLabels(
              childContainers, receivedQuantity, quantityToBeReceived);
    }
    return childCtrLabelsInfo;
  }

  @Transactional
  @InjectTenantFilter
  public Map<String, Object> completeAndCreatePrintJob(
      HttpHeaders httpHeaders, Instruction completedInstruction) throws ReceivingException {
    // Completing Instruction.
    Instruction instruction = instructionRepository.save(completedInstruction);

    // Persisting printJob.
    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add(instruction.getContainer().getTrackingId());
    printJobService.createPrintJob(
        instruction.getDeliveryNumber(),
        instruction.getId(),
        printJobLpnSet,
        httpHeaders.getFirst(USER_ID_HEADER_KEY));

    // Completing container.
    Container container =
        containerService.containerComplete(
            instruction.getContainer().getTrackingId(), httpHeaders.getFirst(USER_ID_HEADER_KEY));

    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instruction);
    map.put("container", container);

    return map;
  }

  @Transactional
  @InjectTenantFilter
  public Map<String, Object> completeInstructionAndContainer(
      HttpHeaders httpHeaders, Instruction completedInstruction) throws ReceivingException {
    // Completing Instruction.
    Instruction instruction = instructionRepository.save(completedInstruction);
    // Completing container.
    List<Container> containers = containerService.getContainerByInstruction(instruction.getId());
    containers =
        containerService.containerListComplete(
            containers, String.valueOf(httpHeaders.getFirst(USER_ID_HEADER_KEY)));

    HashMap<String, Object> map = new HashMap<>();
    map.put(INSTRUCTION, instruction);
    map.put(CONTAINER_LIST, containers);

    return map;
  }

  @Transactional
  @InjectTenantFilter
  public List<Instruction> findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
      Long deliveryNumber) {

    return instructionRepository.findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
        deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<Instruction> findByDeliveryNumberAndInstructionCodeIsNotNull(Long deliveryNumber) {

    return instructionRepository.findByDeliveryNumberAndInstructionCodeIsNotNull(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<String> getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
      Long deliveryNumber, Long instructionSetId) {
    return instructionRepository.getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
        deliveryNumber, instructionSetId);
  }

  /**
   * This method checks if a new instruction can be created for the given item. Prevent creation of
   * new instruction if projectedRecvQty/rcvQty for all instruction created till now is equal to
   * maxReceiveQty
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param openQtyResult
   * @throws ReceivingException
   */
  public void checkIfNewInstructionCanBeCreated(
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber,
      Long deliveryNumber,
      OpenQtyResult openQtyResult,
      boolean isKotlinEnabled)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvChkNewInstCanBeCreatedStart(System.currentTimeMillis());
    int totalReceivedQty = openQtyResult.getTotalReceivedQty();
    int maxReceiveQty = Math.toIntExact(openQtyResult.getMaxReceiveQty());
    Long pendingInstructionsCumulativeProjectedReceivedQty;
    if (Objects.nonNull(openQtyResult.getFlowType())
        && OpenQtyFlowType.FBQ.equals(openQtyResult.getFlowType())) {
      pendingInstructionsCumulativeProjectedReceivedQty =
          Long.valueOf(
              Optional.ofNullable(
                      instructionRepository
                          .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                              deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber))
                  .orElse(0));
    } else {
      pendingInstructionsCumulativeProjectedReceivedQty =
          instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
              purchaseReferenceNumber, purchaseReferenceLineNumber);
    }
    TenantContext.get().setAtlasRcvChkNewInstCanBeCreatedEnd(System.currentTimeMillis());
    boolean isPendingInstructionExist =
        pendingInstructionsCumulativeProjectedReceivedQty != null
            && pendingInstructionsCumulativeProjectedReceivedQty > 0;
    if (isPendingInstructionExist
        && (pendingInstructionsCumulativeProjectedReceivedQty + totalReceivedQty)
            >= maxReceiveQty) {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.MULTI_USER_ERROR);
      LOG.error(instructionError.getErrorMessage());
      ErrorResponse errorResponse;
      if (isKotlinEnabled) {
        errorResponse =
            ErrorResponse.builder()
                .errorMessage(instructionError.getErrorMessage())
                .errorCode(REQUEST_TRANSFTER_INSTR_ERROR_CODE)
                .errorHeader(instructionError.getErrorHeader())
                .errorKey(ExceptionCodes.MULTI_USER_ERROR)
                .build();
      } else {
        errorResponse =
            ErrorResponse.builder()
                .errorMessage(instructionError.getErrorMessage())
                .errorCode(instructionError.getErrorCode())
                .errorHeader(instructionError.getErrorHeader())
                .errorKey(ExceptionCodes.MULTI_USER_ERROR)
                .build();
      }
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }
  }

  /**
   * Method to fetch instruction by id
   *
   * @param instructionRequest
   * @return existingInstruction or null
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Instruction fetchExistingInstructionIfexists(InstructionRequest instructionRequest) {
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    Instruction existingInstructionByMessageId =
        instructionRepository.findByMessageId(instructionRequest.getMessageId());
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    if (existingInstructionByMessageId != null
        && existingInstructionByMessageId.getInstructionMsg() != null) {
      return existingInstructionByMessageId;
    }
    return null;
  }

  /**
   * Method to fetch instruction by SSCC Code
   *
   * @param instructionRequest
   * @return existingInstruction or null
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchInstructionByDeliveryNumberAndSSCCAndUserId(
      InstructionRequest instructionRequest, String userId) {
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    Instruction existingInstructionBySSCC = null;
    if (Objects.nonNull(instructionRequest.getInstructionSetId())) {
      existingInstructionBySSCC =
          instructionRepository
              .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  instructionRequest.getSscc(),
                  userId,
                  userId,
                  instructionRequest.getInstructionSetId());
    } else {
      existingInstructionBySSCC =
          instructionRepository
              .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  instructionRequest.getSscc(),
                  userId,
                  userId);
    }
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    if (existingInstructionBySSCC != null
        && existingInstructionBySSCC.getInstructionMsg() != null
        && isUserEligibleToReceive(userId, existingInstructionBySSCC)) {
      return existingInstructionBySSCC;
    }
    return null;
  }

  /**
   * Non national PO's
   *
   * @param instructionRequestFromClient
   * @param httpHeaders
   * @param userId
   * @param instructionResponse
   * @param quantityToBeReceived
   * @param receivedQuantity
   * @return
   * @throws ReceivingException
   */
  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public Map<String, Object> createContainersAndReceiptsForNonNationalPos(
      InstructionRequest instructionRequestFromClient,
      HttpHeaders httpHeaders,
      String userId,
      Instruction instructionResponse,
      Integer quantityToBeReceived,
      Integer receivedQuantity)
      throws ReceivingException {

    // Updating the instruction
    receivedQuantity += quantityToBeReceived;
    instructionResponse.setLastChangeUserId(userId);
    instructionResponse.setLastChangeTs(new Date());
    instructionResponse.setReceivedQuantity(receivedQuantity);

    // Completing the instruction
    instructionResponse.setCompleteUserId(userId);
    instructionResponse.setCompleteTs(new Date());
    instructionResponse = instructionRepository.save(instructionResponse);

    // persisting the containers
    Container container =
        containerService.processCreateContainersForNonNationalPO(
            instructionResponse, instructionRequestFromClient, httpHeaders);

    // persisting the receipts
    receiptService.createReceiptsFromInstructionForNonNationalPo(
        instructionResponse, instructionRequestFromClient, userId);

    // Persisting printJob.
    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add(instructionResponse.getContainer().getTrackingId());
    printJobService.createPrintJob(
        instructionResponse.getDeliveryNumber(),
        instructionResponse.getId(),
        printJobLpnSet,
        httpHeaders.getFirst(USER_ID_HEADER_KEY));

    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instructionResponse);
    map.put("container", container);
    return map;
  }

  /*
   * This method is used to set instruction code null in case of DSDC flow failed to complete instruction
   */
  @Transactional
  @InjectTenantFilter
  public void saveInstructionWithInstructionCodeAsNull(Instruction instruction) {
    instruction.setInstructionCode(null);
    instructionRepository.save(instruction);
  }

  @Transactional
  @InjectTenantFilter
  public void saveInstructionWithInstructionCodeAsErrorForWFS(Instruction instruction) {
    instruction.setInstructionCode(WFS_INSTRUCTION_ERROR_CODE);
    instructionRepository.save(instruction);
  }

  @Transactional
  @InjectTenantFilter
  public Instruction getDockTagInstructionIfExists(InstructionRequest instructionRequest) {
    List<Instruction> existingDockTagInstructionsForItem =
        instructionRepository.findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
            "Dock Tag", Long.parseLong(instructionRequest.getDeliveryNumber()));
    return selectDockTagInstructionByMessageId(
        instructionRequest.getMessageId(), existingDockTagInstructionsForItem);
  }

  private Instruction selectDockTagInstructionByMessageId(
      String messageId, List<Instruction> existingDockTagInstructionsForItem) {
    List<Instruction> instructionByMessageId =
        existingDockTagInstructionsForItem
            .stream()
            .filter(o -> o.getMessageId().equals(messageId))
            .collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(instructionByMessageId)) {
      LOG.info("Returning dock tag instruction found by message id : {}", messageId);
      return instructionByMessageId.get(0);
    } else {
      return CollectionUtils.isEmpty(existingDockTagInstructionsForItem)
          ? null
          : existingDockTagInstructionsForItem.get(0);
    }
  }

  @Transactional
  @InjectTenantFilter
  public Instruction saveDockTagInstructionAndContainer(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders, Long deliveryNumber) {
    final String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);
    final String facilityNum = httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).get(0);
    Instruction dockTagInstruction =
        saveInstruction(
            instructionHelperService.getDockTagInstruction(
                instructionRequest,
                httpHeaders,
                instructionRequest.getMappedFloorLine(),
                DockTagType.FLOOR_LINE));
    createContainerForDockTag(dockTagInstruction, instructionRequest, httpHeaders, Boolean.FALSE);
    DockTagService dockTagService =
        tenantSpecificConfigReader.getConfiguredInstance(
            facilityNum, ReceivingConstants.DOCK_TAG_SERVICE, DockTagService.class);
    DockTag dockTag =
        dockTagService.createDockTag(
            dockTagInstruction.getDockTagId(), deliveryNumber, userId, DockTagType.FLOOR_LINE);
    dockTagService.saveDockTag(dockTag);
    return dockTagInstruction;
  }

  @Transactional(rollbackFor = ReceivingException.class)
  @InjectTenantFilter
  public Pair<Instruction, Container> saveContainersAndInstructionForNonConDockTag(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders, String mappedPbylArea) {
    final String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);

    if (StringUtils.isEmpty(mappedPbylArea)) {
      LOG.error("Missing PBYL area mapping for door {}", instructionRequest.getDoorNumber());
      mappedPbylArea =
          tenantSpecificConfigReader.getDCSpecificMoveDestinationForNonConDockTag(
              TenantContext.getFacilityNum());
    }

    Instruction dockTagInstruction =
        instructionHelperService.getDockTagInstruction(
            instructionRequest, httpHeaders, mappedPbylArea, DockTagType.NON_CON);
    // mark it complete and save
    dockTagInstruction.setCompleteTs(new Date());
    dockTagInstruction.setCompleteUserId(userId);
    Instruction saveInstruction = saveInstruction(dockTagInstruction);

    // persist container
    Container containerForDockTag =
        createContainerForDockTag(saveInstruction, instructionRequest, httpHeaders, Boolean.TRUE);

    // Persisting printJob.
    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add(saveInstruction.getContainer().getTrackingId());
    printJobService.createPrintJob(
        saveInstruction.getDeliveryNumber(),
        saveInstruction.getId(),
        printJobLpnSet,
        httpHeaders.getFirst(USER_ID_HEADER_KEY));

    // save docktag
    DockTagService dockTagService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DOCK_TAG_SERVICE,
            DockTagService.class);
    DockTag dockTag =
        dockTagService.createDockTag(
            saveInstruction.getDockTagId(),
            Long.parseLong(instructionRequest.getDeliveryNumber()),
            userId,
            DockTagType.NON_CON);
    dockTag.setScannedLocation(mappedPbylArea);
    dockTagService.saveDockTag(dockTag);
    return new Pair<>(saveInstruction, containerForDockTag);
  }

  @Transactional
  @InjectTenantFilter
  public List<WFTResponse> getReceivedQtyAgainstUserNameGivenActivityName(
      String activityName, Date fromDate, Date toDate) {
    return instructionRepository.getReceivedQtyAgainstUserNameGivenActivityName(
        activityName, fromDate, toDate);
  }

  @Transactional
  @InjectTenantFilter
  public List<WFTResponse> getReceivedQtyAgainstActivityNameByTime(Date fromDate, Date toDate) {
    return instructionRepository.getReceivedQtyAgainstActivityNameByTime(fromDate, toDate);
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<Instruction> instructionList =
        instructionRepository.findByIdGreaterThanEqual(purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    instructionList =
        instructionList
            .stream()
            .filter(instruction -> instruction.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(Instruction::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(instructionList)) {
      LOG.info("Purge INSTRUCTION: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = instructionList.get(instructionList.size() - 1).getId();

    LOG.info(
        "Purge INSTRUCTION: {} records : ID {} to {} : START",
        instructionList.size(),
        instructionList.get(0).getId(),
        lastDeletedId);
    instructionRepository.deleteAll(instructionList);
    LOG.info("Purge INSTRUCTION: END");
    return lastDeletedId;
  }

  @Transactional
  @InjectTenantFilter
  public Map<String, Object> completeAndCreatePrintJobForPoCon(
      HttpHeaders httpHeaders, Instruction completedInstruction, Receipt receiptFromDb)
      throws ReceivingException {
    // Completing Instruction.
    Instruction instruction = instructionRepository.save(completedInstruction);

    receiptFromDb.setPalletQty(ReceivingConstants.NON_NATIONAL_PALLET_QTY);
    receiptService.saveReceipt(receiptFromDb);

    // Persisting printJob.
    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add(instruction.getContainer().getTrackingId());
    printJobService.createPrintJob(
        instruction.getDeliveryNumber(),
        instruction.getId(),
        printJobLpnSet,
        httpHeaders.getFirst(USER_ID_HEADER_KEY));

    // Completing container.
    Container container =
        containerService.containerComplete(
            instruction.getContainer().getTrackingId(), httpHeaders.getFirst(USER_ID_HEADER_KEY));

    HashMap<String, Object> map = new HashMap<>();
    map.put("instruction", instruction);
    map.put("container", container);

    return map;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchInstructionByDeliveryNumberSSCCAndUserIdAndProblemTagId(
      InstructionRequest instructionRequest, String userId) {
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    Instruction existingInstructionBySSCC =
        instructionRepository
            .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
                Long.valueOf(instructionRequest.getDeliveryNumber()),
                instructionRequest.getSscc(),
                userId,
                userId,
                instructionRequest.getProblemTagId());
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    if (existingInstructionBySSCC != null
        && existingInstructionBySSCC.getInstructionMsg() != null
        && isUserEligibleToReceive(userId, existingInstructionBySSCC)) {
      return existingInstructionBySSCC;
    }
    return null;
  }

  /**
   * Method that returns the instruction list by po/po line, delivery for the report ui
   *
   * @return List of instructions by po/po line and delivery
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<Instruction> getInstructionByPoPoLineAndDeliveryNumber(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber, Long deliveryNumber) {
    return instructionRepository
        .findByPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndDeliveryNumber(
            purchaseReferenceNumber, purchaseReferenceLineNumber, deliveryNumber);
  }

  /**
   * This method fetches instruction by delivery, gtin and user id
   *
   * @param instructionRequest
   * @param userId
   * @return Instruction
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchInstructionByDeliveryAndGtinAndUserId(
      InstructionRequest instructionRequest, String userId) {
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    List<Instruction> existingInstructionsList =
        instructionRepository.findByDeliveryNumberAndGtinAndCreateUserIdAndCompleteTsIsNull(
            Long.valueOf(instructionRequest.getDeliveryNumber()),
            instructionRequest.getUpcNumber(),
            userId);
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    if (!CollectionUtils.isEmpty(existingInstructionsList)
        && existingInstructionsList.get(0).getInstructionMsg() != null) {
      return existingInstructionsList.get(0);
    }
    return null;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction
      fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
          DeliveryDocument deliveryDocument, InstructionRequest instructionRequest, String userId) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    List<String> possibleUpcList =
        getPossibleUPCs(deliveryDocument, instructionRequest.getUpcNumber());
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());

    List<Instruction> existingInstructionsList;

    String purchaseReferenceType = deliveryDocumentLine.getPurchaseRefType();
    if (!org.apache.commons.lang3.StringUtils.isBlank(purchaseReferenceType)
        && (PurchaseReferenceType.POCON.name().equalsIgnoreCase(purchaseReferenceType))) {
      existingInstructionsList =
          instructionRepository
              .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberIsNullAndInstructionSetIdIsNull(
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  possibleUpcList,
                  userId,
                  deliveryDocumentLine.getPurchaseReferenceNumber());

    } else {
      existingInstructionsList =
          instructionRepository
              .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  possibleUpcList,
                  userId,
                  deliveryDocumentLine.getPurchaseReferenceNumber(),
                  deliveryDocumentLine.getPurchaseReferenceLineNumber());
    }
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    if (!CollectionUtils.isEmpty(existingInstructionsList)
        && existingInstructionsList.get(0).getInstructionMsg() != null) {
      return existingInstructionsList.get(0);
    }
    return null;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction
      fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
          DeliveryDocument deliveryDocument, InstructionRequest instructionRequest, String userId) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    List<String> possibleUpcList =
        getPossibleUPCs(deliveryDocument, instructionRequest.getUpcNumber());
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    List<Instruction> existingInstructionsList =
        instructionRepository
            .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
                Long.valueOf(instructionRequest.getDeliveryNumber()),
                possibleUpcList,
                userId,
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber());
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    if (!CollectionUtils.isEmpty(existingInstructionsList)
        && existingInstructionsList.get(0).getInstructionMsg() != null) {
      return existingInstructionsList.get(0);
    }
    return null;
  }

  /**
   * This method fetches instruction by delivery, gtin and user id
   *
   * @param instructionRequest
   * @param userId
   * @return Instruction
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction
      fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
          InstructionRequest instructionRequest, String userId) {
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    List<Instruction> existingInstructionsList = null;
    if (nonNull(instructionRequest.getInstructionSetId())) {
      existingInstructionsList =
          instructionRepository
              .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  instructionRequest.getUpcNumber(),
                  userId,
                  userId,
                  instructionRequest.getInstructionSetId());
    } else {
      existingInstructionsList =
          instructionRepository
              .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  instructionRequest.getUpcNumber(),
                  userId,
                  userId);
    }
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    if (!CollectionUtils.isEmpty(existingInstructionsList)
        && existingInstructionsList.get(0).getInstructionMsg() != null
        && isUserEligibleToReceive(userId, existingInstructionsList.get(0))) {
      return existingInstructionsList.get(0);
    }
    return null;
  }

  /**
   * This method fetches instruction by delivery, gtin and user id
   *
   * @param instructionRequest
   * @param userId
   * @return Instruction
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId(
      InstructionRequest instructionRequest, String userId, String problemTagId) {
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    List<Instruction> existingInstructionsList =
        instructionRepository
            .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
                Long.valueOf(instructionRequest.getDeliveryNumber()),
                instructionRequest.getUpcNumber(),
                userId,
                userId,
                problemTagId);
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    if (!CollectionUtils.isEmpty(existingInstructionsList)
        && existingInstructionsList.get(0).getInstructionMsg() != null
        && isUserEligibleToReceive(userId, existingInstructionsList.get(0))) {
      return existingInstructionsList.get(0);
    }
    return null;
  }

  /**
   * Fetch instruction by delivery number, gtin, lotNumber and userId
   *
   * @param deliveryNumber
   * @param scannedDataMap
   * @param userId
   * @return Instruction
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
      Long deliveryNumber,
      Map<String, ScannedData> scannedDataMap,
      String userId,
      Long instructionSetId) {
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    String lotNumber = scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue();
    Gson gson = new Gson();
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    List<Instruction> instructionsByDeliveryAndGtinAndLotNumberAndUserId;
    if (Objects.nonNull(instructionSetId)) {
      instructionsByDeliveryAndGtinAndLotNumberAndUserId =
          instructionRepository
              .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
                  deliveryNumber, gtin, userId, userId, instructionSetId);
    } else {
      instructionsByDeliveryAndGtinAndLotNumberAndUserId =
          instructionRepository
              .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
                  deliveryNumber, gtin, userId, userId);
    }
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    Instruction instructionResponse = null;
    if (!CollectionUtils.isEmpty(instructionsByDeliveryAndGtinAndLotNumberAndUserId)) {
      for (Instruction instruction : instructionsByDeliveryAndGtinAndLotNumberAndUserId) {
        DeliveryDocument document =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine documentLine = document.getDeliveryDocumentLines().get(0);
        if (!StringUtils.isEmpty(instruction.getGtin())
            && gtin.equalsIgnoreCase(instruction.getGtin())
            && !StringUtils.isEmpty(documentLine.getLotNumber())
            && lotNumber.equalsIgnoreCase(documentLine.getLotNumber())
            && isUserEligibleToReceive(userId, instruction)) {
          instructionResponse = instruction;
          break;
        }
      }
      if (instructionResponse != null
          && StringUtils.isEmpty(instructionResponse.getInstructionMsg())) {
        instructionResponse = null;
      }
    }

    return instructionResponse;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Instruction> findInstructionByDeliveryAndGtin(Long deliveryNumber) {
    return instructionRepository.findByDeliveryNumberAndCompleteTsIsNullAndProblemTagIdIsNull(
        deliveryNumber);
  }

  /**
   * Fetch instruction by delivery number, gtin, lotNumber, userId and ProblemId
   *
   * @param deliveryNumber
   * @param scannedDataMap
   * @param userId
   * @return Instruction
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdAndProblemTagId(
      Long deliveryNumber,
      Map<String, ScannedData> scannedDataMap,
      String userId,
      String problemTagId) {
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    String lotNumber = scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue();
    Gson gson = new Gson();
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    List<Instruction> instructionsByDeliveryAndGtinAndLotNumberAndUserId =
        instructionRepository
            .findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
                deliveryNumber, gtin, userId, userId, problemTagId);
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    Instruction instructionResponse = null;
    if (!CollectionUtils.isEmpty(instructionsByDeliveryAndGtinAndLotNumberAndUserId)) {
      for (Instruction instruction : instructionsByDeliveryAndGtinAndLotNumberAndUserId) {
        DeliveryDocument document =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine documentLine = document.getDeliveryDocumentLines().get(0);
        if (!StringUtils.isEmpty(instruction.getGtin())
            && gtin.equalsIgnoreCase(instruction.getGtin())
            && !StringUtils.isEmpty(documentLine.getLotNumber())
            && lotNumber.equalsIgnoreCase(documentLine.getLotNumber())
            && isUserEligibleToReceive(userId, instruction)) {
          instructionResponse = instruction;
          break;
        }
      }
      if (instructionResponse != null
          && StringUtils.isEmpty(instructionResponse.getInstructionMsg())) {
        instructionResponse = null;
      }
    }

    return instructionResponse;
  }

  private boolean isUserEligibleToReceive(String requestUserId, Instruction instruction) {
    if (org.apache.commons.lang.StringUtils.isNotBlank(instruction.getLastChangeUserId())) {
      return requestUserId.equals(instruction.getLastChangeUserId());
    } else {
      return requestUserId.equals(instruction.getCreateUserId());
    }
  }

  @Transactional
  @InjectTenantFilter
  public void updateLastChangeUserIdAndLastChangeTs(List<Long> instructionIds, String userId) {
    instructionRepository.updateLastChangeUserIdAndLastChangeTs(instructionIds, userId);
  }

  private List<String> getPossibleUPCs(DeliveryDocument deliveryDocument, String upcNumber) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    List<String> possibleUpcList = new ArrayList<>();
    possibleUpcList.add(deliveryDocumentLine.getItemUpc());
    possibleUpcList.add(deliveryDocumentLine.getCaseUpc());
    possibleUpcList.add(upcNumber);
    if (Objects.nonNull(deliveryDocumentLine.getCatalogGTIN())) {
      possibleUpcList.add(deliveryDocumentLine.getCatalogGTIN());
    }
    LOG.info("Possible UPC list going to be added in search criteria: {}", possibleUpcList);
    return possibleUpcList;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<String> getSlotDetailsByDeliveryNumberAndInstructionSetId(
      String deliveryNumber, Long instructionSetId) {
    return instructionRepository.getOpenInstructionSlotDetailsByDeliveryNumberAndInstructionSetId(
        deliveryNumber, instructionSetId);
  }

  public List<Instruction> checkIfInstructionExistsWithSscc(InstructionRequest instructionRequest) {
    return instructionRepository
        .findByDeliveryNumberAndSsccNumberAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            Long.valueOf(instructionRequest.getDeliveryNumber()), instructionRequest.getSscc());
  }

  public Instruction
      fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
          DeliveryDocument deliveryDocument,
          InstructionRequest instructionRequest,
          String userId,
          Long instructionSetId) {

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    List<String> possibleUpcList =
        getPossibleUPCs(deliveryDocument, instructionRequest.getUpcNumber());
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());

    List<Instruction> existingInstructionsList =
        instructionRepository
            .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
                Long.valueOf(instructionRequest.getDeliveryNumber()),
                possibleUpcList,
                userId,
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                instructionSetId);
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());

    if (!CollectionUtils.isEmpty(existingInstructionsList)
        && existingInstructionsList.get(0).getInstructionMsg() != null) {
      return existingInstructionsList.get(0);
    }
    return null;
  }

  public Instruction
      fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
          DeliveryDocument deliveryDocument, InstructionRequest instructionRequest, String userId) {

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    List<String> possibleUpcList =
        getPossibleUPCs(deliveryDocument, instructionRequest.getUpcNumber());
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());

    List<Instruction> existingInstructionsList =
        instructionRepository
            .findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
                Long.valueOf(instructionRequest.getDeliveryNumber()),
                possibleUpcList,
                userId,
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber());
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());

    if (!CollectionUtils.isEmpty(existingInstructionsList)
        && existingInstructionsList.get(0).getInstructionMsg() != null) {
      return existingInstructionsList.get(0);
    }
    return null;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public int getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
      Long deliveryNumber, String purchaseReferenceNumber, int purchaseReferenceLineNumber) {
    Integer pendingInstructionsCumulativeReceivedQty =
        instructionRepository.getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);
    return Objects.nonNull(pendingInstructionsCumulativeReceivedQty)
        ? pendingInstructionsCumulativeReceivedQty
        : 0;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Long getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
      String purchaseReferenceNumber, int purchaseReferenceLineNumber, String problemTagId) {
    Long totalReceivedQtyByProblemTagIdAndPoPoLine =
        instructionRepository
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                purchaseReferenceNumber, purchaseReferenceLineNumber, problemTagId);
    return Objects.nonNull(totalReceivedQtyByProblemTagIdAndPoPoLine)
        ? totalReceivedQtyByProblemTagIdAndPoPoLine
        : 0;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public int
      getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
          Long deliveryNumber, String purchaseReferenceNumber, int purchaseReferenceLineNumber) {
    Integer pendingInstructionsCumulativeProjectedReceivedQty =
        instructionRepository
            .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
                deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);
    return Objects.isNull(pendingInstructionsCumulativeProjectedReceivedQty)
        ? 0
        : pendingInstructionsCumulativeProjectedReceivedQty;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public int findNonSplitPalletInstructionCount(
      String purchaseReferenceNumber, int purchaseReferenceLineNumber) {
    return instructionRepository
        .countByPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndCompleteTsIsNull(
            purchaseReferenceNumber, purchaseReferenceLineNumber);
  }

  /**
   * @param deliveryDocument
   * @param instructionRequest
   * @param httpHeaders
   * @return Instruction
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Instruction fetchExistingOpenInstruction(
      DeliveryDocument deliveryDocument,
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    // Criteria 1: Match with messageId
    Instruction instruction = fetchExistingInstructionIfexists(instructionRequest);
    if (Objects.nonNull(instruction)) {
      LOG.info(
          "Returning open instruction[{}] for a given messageId: {}",
          instruction.getId(),
          instructionRequest.getMessageId());
      return instruction;
    }

    // Criteria 2: Match with problemTagId
    String userId = httpHeaders.getFirst(USER_ID_HEADER_KEY);
    if (Objects.nonNull(instructionRequest.getProblemTagId())) {
      instruction =
          fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId(
              instructionRequest, userId, instructionRequest.getProblemTagId());
      if (Objects.nonNull(instruction)) {
        LOG.info(
            "Returning open instruction[{}] for a given problemTagId: {}",
            instruction.getId(),
            instructionRequest.getProblemTagId());
        return instruction;
      }
    }

    if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      return fetchExistingOpenInstructionForSscc(instructionRequest, httpHeaders);
    }

    // Criteria 3: Match with GTIN/itemUpc/caseUpc
    return fetchExistingOpenInstructionForGTIN(deliveryDocument, instructionRequest, userId);
  }

  /**
   * @param deliveryDocument
   * @param instructionRequest
   * @param userId
   * @return Instruction
   */
  public Instruction fetchExistingOpenInstructionForGTIN(
      DeliveryDocument deliveryDocument, InstructionRequest instructionRequest, String userId) {
    if (Objects.nonNull(deliveryDocument)
        && !CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())) {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);

      if (ObjectUtils.allNotNull(
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber())) {
        Instruction instruction =
            fetchInstructionByDeliveryAndPossibleGtinsAndUserIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
                deliveryDocument, instructionRequest, userId);

        if (Objects.nonNull(instruction)) {
          LOG.info(
              "Returning open instruction[{}] for a given delivery: {}, GTIN: {}, userId: {}, PO: {} and LINE: {}",
              instruction.getId(),
              instructionRequest.getDeliveryNumber(),
              instructionRequest.getUpcNumber(),
              userId,
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
          return instruction;
        }
      }
    }
    return null;
  }

  public Instruction fetchExistingOpenInstructionForSscc(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    List<Instruction> instructions = checkIfInstructionExistsWithSscc(instructionRequest);
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    Instruction instruction = null;
    if (!CollectionUtils.isEmpty(instructions)) {
      instruction = instructions.get(0);
      if (!userId.equalsIgnoreCase(instruction.getLastChangeUserId())) {
        InstructionError instructionError =
            InstructionErrorCode.getErrorValue(ReceivingException.MULTI_USER_ERROR);
        LOG.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            REQUEST_TRANSFTER_INSTR_ERROR_CODE,
            instructionError.getErrorHeader());
      }
    }
    return instruction;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchMultiSkuInstrDeliveryDocument(
      String instructionCode,
      Long deliveryNumber,
      String ssccNumber,
      String poNumber,
      String userId) {
    return instructionRepository
        .findFirstByInstructionCodeAndDeliveryNumberAndSsccNumberAndPurchaseReferenceNumberAndCompleteUserIdOrderByCreateTsDesc(
            instructionCode, deliveryNumber, ssccNumber, poNumber, userId);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchMultiSkuInstrDeliveryDocumentByDelivery(
      String instructionCode, Long deliveryNumber, String poNumber, String userId) {
    return instructionRepository
        .findFirstByInstructionCodeAndDeliveryNumberAndPurchaseReferenceNumberAndCompleteUserIdAndSsccNumberIsNullOrderByCreateTsDesc(
            instructionCode, deliveryNumber, poNumber, userId);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchMultiSkuInstrByDelivery(
      String instructionCode, Long deliveryNumber, String poNumber, String userId) {
    return instructionRepository
        .findFirstByInstructionCodeAndDeliveryNumberAndPurchaseReferenceNumberAndCompleteUserIdOrderByCreateTsDesc(
            instructionCode, deliveryNumber, poNumber, userId);
  }

  @InjectTenantFilter
  public void cancelOpenInstructionsIfAny(
      Long deliveryNumber, String po, int lineNum, HttpHeaders httpHeaders) {
    final List<Instruction> instructions =
        instructionRepository
            .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndCompleteTsIsNull(
                deliveryNumber, po, lineNum);
    if (isNotEmpty(instructions)) {
      for (Instruction instruction : instructions) {
        instruction.setReceivedQuantity(0);
        instruction.setCompleteUserId(httpHeaders.getFirst(USER_ID_HEADER_KEY));
        instruction.setCompleteTs(new Date());
      }
      instructionRepository.saveAll(instructions);
    }
  }

  public List<Instruction> findInstructionByDeliveryNumberAndSscc(
      InstructionRequest instructionRequest) {
    return instructionRepository.findByDeliveryNumberAndSsccNumberAndCompleteTsIsNotNull(
        Long.valueOf(instructionRequest.getDeliveryNumber()), instructionRequest.getSscc());
  }
}
