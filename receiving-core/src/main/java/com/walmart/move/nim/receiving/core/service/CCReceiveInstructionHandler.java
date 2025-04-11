package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CONTAINER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INSTRUCTION;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.common.validators.WeightThresholdValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.MoveEvent;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import java.util.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public class CCReceiveInstructionHandler implements ReceiveInstructionHandler {
  private static final Logger logger = LoggerFactory.getLogger(CCReceiveInstructionHandler.class);

  protected InstructionError instructionError;

  @Autowired private ReceiptService receiptService;
  @Autowired private ContainerService containerService;

  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private InstructionStateValidator instructionStateValidator;
  @Autowired private PurchaseReferenceValidator purchaseReferenceValidator;
  @Autowired protected InstructionPersisterService instructionPersisterService;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private MovePublisher movePublisher;
  @Autowired private LabelServiceImpl labelServiceImpl;

  @Autowired private WeightThresholdValidator weightThresholdValidator;
  @Autowired private GdcPutawayPublisher gdcPutawayPublisher;
  @Autowired private InstructionService instructionService;
  @Autowired private ImportsInstructionUtils importsInstructionUtils;
  @Autowired private Gson gson;

  @Override
  public InstructionResponse receiveInstruction(
      Long instructionId,
      ReceiveInstructionRequest receiveInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    logger.info(
        "CC implementation of receive instruction for receiveInstructionRequest: {}",
        receiveInstructionRequest);
    Instruction instructionResponse = instructionPersisterService.getInstructionById(instructionId);
    DeliveryDocument deliveryDocument4mDb =
        gson.fromJson(instructionResponse.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine documentLine4mDb = deliveryDocument4mDb.getDeliveryDocumentLines().get(0);

    // Assuming here, that before instruction 'received' for the first time, the quantity field in
    // instruction table delivery document is zero or null. In case of update instruction api called
    // on this,
    // deliveryDoc will have quantity which has been received
    // Currently setting quantity to be received from current delivery doc in table quantity +
    // request quantity
    int alreadyReceivedQty = Optional.ofNullable(documentLine4mDb.getQuantity()).orElse(0);
    Integer quantityToBeReceived = alreadyReceivedQty + receiveInstructionRequest.getQuantity();

    deliveryDocument4mDb.getDeliveryDocumentLines().get(0).setQuantity(quantityToBeReceived);
    final boolean isKotlinEnabled = ReceivingUtils.isKotlinEnabled(httpHeaders, configUtils);
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    // this quantity in instruction may NOT be set in all flows, (eg. update instruction)
    // receivedQuantity should have already received qty added
    // TODO: try to update this qty in every flow
    Integer receivedQuantity = alreadyReceivedQty + instructionResponse.getReceivedQuantity();

    UpdateInstructionRequest updateInstructionRequest =
        InstructionUtils.constructUpdateInstructionRequest(
            instructionResponse, receiveInstructionRequest);

    DocumentLine documentLine = updateInstructionRequest.getDeliveryDocumentLines().get(0);
    boolean isNationalPo = InstructionUtils.isNationalPO(documentLine.getPurchaseRefType());

    validateReceiveRequest(
        instructionResponse, userId, isNationalPo, documentLine, isKotlinEnabled);

    try {
      List<ContainerDetails> childContainers = instructionResponse.getChildContainers();
      // getPrint label before creating container and printJob so that if there is any exception
      // while preparing labelData, containers are not created
      Map<String, Object> caseLabelsInfo =
          instructionPersisterService.getPrintlabeldata(
              instructionResponse, quantityToBeReceived, receivedQuantity, childContainers);

      Map<String, Object> instructionContainerMap;
      // Update + Complete
      logger.info(
          "CC Receive for instructionId: {} ActivityName:{}",
          instructionId,
          instructionResponse.getActivityName());

      if (ReceivingConstants.POCON_ACTIVITY_NAME.equalsIgnoreCase(
          instructionResponse.getActivityName())) {
        instructionContainerMap =
            instructionHelperService.receiveInstructionAndCompleteProblemTagPOConCC(
                instructionResponse,
                updateInstructionRequest,
                quantityToBeReceived,
                httpHeaders,
                childContainers,
                caseLabelsInfo);
      } else {
        instructionContainerMap =
            instructionHelperService.receiveInstructionAndCompleteProblemTag(
                instructionResponse,
                updateInstructionRequest,
                quantityToBeReceived,
                httpHeaders,
                false,
                caseLabelsInfo);
      }

      // publish instruction to WFM Update
      logger.info("CC Receive for instructionId: {} Publishing Update to WFM", instructionId);
      instructionHelperService.publishInstruction(
          instructionResponse,
          updateInstructionRequest,
          quantityToBeReceived,
          null,
          InstructionStatus.UPDATED,
          httpHeaders);

      Instruction instruction = (Instruction) instructionContainerMap.get(INSTRUCTION);
      Container parentContainer = (Container) instructionContainerMap.get(CONTAINER);
      // Getting consolidated container and publish Container to Receipt topic.

      logger.info("CC Receive for instructionId: {} Publishing to SCT & Inventory", instructionId);
      Container consolidatedContainer =
          ReceivingUtils.getConsolidatedContainerAndPublishContainer(
              parentContainer, httpHeaders, Boolean.TRUE, configUtils, containerService);

      // Publishing move.
      if (instruction.getMove() != null && !instruction.getMove().isEmpty()) {
        logger.info("CC Receive for instructionId: {} Publishing to MM", instructionId);
        if (configUtils.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED)
            && Objects.nonNull(consolidatedContainer)
            && Objects.nonNull(consolidatedContainer.getDestination())
            && Objects.nonNull(
                consolidatedContainer.getDestination().get(ReceivingConstants.BU_NUMBER))) {
          movePublisher.publishMove(
              InstructionUtils.getMoveQuantity(consolidatedContainer),
              consolidatedContainer.getLocation(),
              httpHeaders,
              instruction.getMove(),
              MoveEvent.CREATE.getMoveEvent(),
              Integer.parseInt(
                  consolidatedContainer.getDestination().get(ReceivingConstants.BU_NUMBER)));
        } else {
          movePublisher.publishMove(
              InstructionUtils.getMoveQuantity(consolidatedContainer),
              consolidatedContainer.getLocation(),
              httpHeaders,
              instruction.getMove(),
              MoveEvent.CREATE.getMoveEvent());
        }
      }

      Map<String, Object> printJob = instruction.getContainer().getCtrLabel();
      // Disabling printing for configured label formats. This is introduced for CC market.
      printJob = ReceivingUtils.getNewPrintJob(printJob, instruction, configUtils);
      // Publishing instruction. Instruction will be published based on feature flag.
      // complete instruction to WFM
      logger.info("CC Receive for instructionId: {} Publishing Complete to WFM", instructionId);
      instructionHelperService.publishInstruction(
          instruction, null, null, consolidatedContainer, InstructionStatus.COMPLETED, httpHeaders);

      // Send putaway request message
      //      gdcPutawayPublisher.publishMessage(
      //              consolidatedContainer, ReceivingConstants.PUTAWAY_ADD_ACTION, httpHeaders);

      // Post receipts to DCFin backend by persistence
      DCFinService managedDcFinService =
          configUtils.getConfiguredInstance(
              String.valueOf(getFacilityNum()),
              ReceivingConstants.DC_FIN_SERVICE,
              DCFinService.class);
      logger.info("CC Receive for instructionId: {} Post to DC Fin", instructionId);
      managedDcFinService.postReceiptsToDCFin(consolidatedContainer, httpHeaders, true);

      if (configUtils.isFeatureFlagEnabled(
          ReceivingConstants.ENABLE_OFFLINE_DACON_STORE_LABEL_SORTER_DIVERT)) {
        // Publish sorter divert
        if (!consolidatedContainer.getChildContainers().isEmpty()
            && ReceivingConstants.DA_CON_ACTIVITY_NAME.equals(
                consolidatedContainer.getActivityName())) {
          logger.info(
              "CC Receive for instructionId: {} "
                  + "Pallet Label: {} "
                  + "Publishing {} child labels sorter divert.",
              instructionId,
              consolidatedContainer.getTrackingId(),
              consolidatedContainer.getChildContainers().size());
          SorterPublisher sorterPublisher =
              configUtils.getConfiguredInstance(
                  String.valueOf(TenantContext.getFacilityNum()),
                  ReceivingConstants.SORTER_PUBLISHER,
                  SorterPublisher.class);
          // as child containers do not have publishTs, set their publishTs from parent container to
          // populate sorter publish payload
          consolidatedContainer
              .getChildContainers()
              .stream()
              .peek(container -> container.setPublishTs(consolidatedContainer.getPublishTs()))
              .forEach(sorterPublisher::publishStoreLabel);
        }
      }
      return new InstructionResponseImplNew(null, null, instruction, printJob);
    } catch (Exception e) {
      logger.error(
          "{} {}",
          ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.RECEIVE_INSTRUCTION_ERROR_MSG)
              .errorCode(ReceivingException.RECEIVE_INSTRUCTION_ERROR_CODE)
              .errorKey(ExceptionCodes.RECEIVE_INSTRUCTION_ERROR_MSG)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.BAD_REQUEST)
          .errorResponse(errorResponse)
          .build();
    }
    // Complete end
  }

  @Override
  public InstructionResponse receiveInstruction(
      ReceiveInstructionRequest receiveInstructionRequest, HttpHeaders httpHeaders) {
    logger.info(
        "Default implementation of receive instruction for instruction request {}",
        receiveInstructionRequest);
    return null;
  }

  private void validateReceiveRequest(
      Instruction instructionResponse,
      String userId,
      boolean isNationalPo,
      DocumentLine documentLine,
      boolean isKotlinEnabled)
      throws ReceivingException {

    // Multi user validations
    ReceivingUtils.verifyUser(instructionResponse, userId, RequestType.RECEIVE);

    // Received update for already completed instruction
    instructionStateValidator.validate(instructionResponse);

    Integer quantityToBeReceived = documentLine.getQuantity();
    Integer projectedQuantity = instructionResponse.getProjectedReceiveQty();

    final int quantityAlreadyReceived = instructionResponse.getReceivedQuantity();
    final Integer totalQuantityValueAfterReceiving = quantityAlreadyReceived + quantityToBeReceived;

    if (isKotlinEnabled)
      validateWeightThreshold(
          documentLine,
          quantityAlreadyReceived,
          quantityToBeReceived,
          documentLine.getQuantityUOM());

    if (isNationalPo) {
      // If totalQuantityValueAfterReceiving exceeds limit for PO/Po line
      if (totalQuantityValueAfterReceiving > projectedQuantity) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.RECEIVE_INSTRUCTION_EXCEEDS_QUANTITY)
                .errorCode(ReceivingException.RECEIVE_INSTRUCTION_ERROR_CODE)
                .errorKey(ExceptionCodes.RECEIVE_INSTRUCTION_EXCEEDS_QUANTITY)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      }

      // In problem flow will fetch currentReceiveQuantity by problemId and
      // maxReceiveQuantity(maxReceiveQty) from UI
      Long currentReceiveQuantity;
      Long maxReceiveQuantity;
      if (!StringUtils.isEmpty(instructionResponse.getProblemTagId())) {
        currentReceiveQuantity =
            receiptService.getReceivedQtyByProblemId(instructionResponse.getProblemTagId());
        maxReceiveQuantity = documentLine.getMaxReceiveQty();
      } else {
        // TODO check with rajiv -> what happens when resolution qty > fbq
        // need to take min of fbq and resolution qty??
        currentReceiveQuantity =
            receiptService.getReceivedQtyByPoAndPoLine(
                documentLine.getPurchaseReferenceNumber(),
                documentLine.getPurchaseReferenceLineNumber());
        maxReceiveQuantity = documentLine.getExpectedQty();
        if (documentLine.getMaxOverageAcceptQty() != null) {
          maxReceiveQuantity += documentLine.getMaxOverageAcceptQty();
        }
      }

      // quantity reached allowed limit
      if (currentReceiveQuantity.equals(maxReceiveQuantity)) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(ReceivingException.RECEIVE_INSTRUCTION_REACHED_MAXIMUM_THRESHOLD)
                .errorCode(ReceivingException.OVERAGE_ERROR_CODE)
                .errorKey(ExceptionCodes.RECEIVE_INSTRUCTION_REACHED_MAXIMUM_THRESHOLD)
                .build();

        RangeErrorResponse rangeErrorResponse =
            RangeErrorResponse.rangeErrorBuilder()
                .quantityCanBeReceived(0)
                .errorResponse(errorResponse)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(rangeErrorResponse)
            .build();
      }
      final long receivedAndToBeReceivedQty = currentReceiveQuantity + quantityToBeReceived;
      // receivedAndToBeReceivedQty reached near limit
      if ((StringUtils.isEmpty(instructionResponse.getProblemTagId()))
          && (receivedAndToBeReceivedQty > maxReceiveQuantity)) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(
                    String.format( // Near overage limit
                        ReceivingException.RECEIVE_INSTRUCTION_NEAR_OVERAGE_LIMIT,
                        Math.abs(maxReceiveQuantity - currentReceiveQuantity)))
                .errorCode(ReceivingException.NEAR_OVERAGE_LIMIT_ERROR_CODE)
                .errorKey(ExceptionCodes.RECEIVE_INSTRUCTION_NEAR_OVERAGE_LIMIT)
                .values(new Object[] {Math.abs(maxReceiveQuantity - currentReceiveQuantity)})
                .build();

        RangeErrorResponse rangeErrorResponse =
            RangeErrorResponse.rangeErrorBuilder()
                .quantityCanBeReceived((int) Math.abs(maxReceiveQuantity - currentReceiveQuantity))
                .errorResponse(errorResponse)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(rangeErrorResponse)
            .build();
      }
    }
  }

  public void validateWeightThreshold(
      DocumentLine documentLine,
      Integer quantityAlreadyReceived,
      Integer quantityToBeReceived,
      String quantityUom) {
    weightThresholdValidator.validate(
        documentLine, quantityAlreadyReceived, quantityToBeReceived, quantityUom);
  }
}
