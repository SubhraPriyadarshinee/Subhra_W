package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_EXPIRY_DATE_VALIDATION_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MIN_LIFE_EXPECTANCY_V2;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.logging.commons.lang3.tuple.Pair;
import java.util.*;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Primary
@Component(value = ReceivingConstants.DEFAULT_UPDATE_INSTRUCTION_HANDLER)
public class DefaultUpdateInstructionHandler implements UpdateInstructionHandler {

  private static final Logger log = LoggerFactory.getLogger(DefaultUpdateInstructionHandler.class);

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired protected ReceiptService receiptService;

  @Resource(name = ReceivingConstants.FDE_SERVICE)
  private FdeService fdeService;

  @Resource(name = ReceivingConstants.WITRON_DELIVERY_METADATA_SERVICE)
  protected WitronDeliveryMetaDataService witronDeliveryMetaDataService;

  @Autowired protected InstructionPersisterService instructionPersisterService;
  @Autowired private TenantSpecificConfigReader configUtils;

  protected InstructionError instructionError;
  protected GdmError gdmError;

  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private InstructionStateValidator instructionStateValidator;
  @Autowired private PurchaseReferenceValidator purchaseReferenceValidator;
  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;

  @Override
  public InstructionResponse updateInstruction(
      Long instructionId,
      UpdateInstructionRequest instructionUpdateRequestFromClient,
      String parentTrackingId,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    DocumentLine documentLine =
        instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
    Instruction instructionResponse = instructionPersisterService.getInstructionById(instructionId);

    return processUpdateInstructionRequest(
        instructionUpdateRequestFromClient, httpHeaders, documentLine, instructionResponse);
  }

  protected InstructionResponse processUpdateInstructionRequest(
      UpdateInstructionRequest instructionUpdateRequestFromClient,
      HttpHeaders httpHeaders,
      DocumentLine documentLine,
      Instruction instructionResponse)
      throws ReceivingException {
    Integer quantityToBeReceived = documentLine.getQuantity();
    String userId = httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).get(0);
    Integer receivedQuantity = instructionResponse.getReceivedQuantity();
    Integer projectedQuantity = instructionResponse.getProjectedReceiveQty();
    final String deliveryNumber = instructionResponse.getDeliveryNumber().toString();
    final Boolean firstExpiryFirstOut = instructionResponse.getFirstExpiryFirstOut();
    final String purchaseReferenceNumber = instructionResponse.getPurchaseReferenceNumber();
    final Integer totalQuantityValueAfterReceiving = receivedQuantity + quantityToBeReceived;
    final boolean isKotlinEnabled = ReceivingUtils.isKotlinEnabled(httpHeaders, configUtils);
    boolean isNationalPo =
        InstructionUtils.isNationalPO(
            instructionUpdateRequestFromClient
                .getDeliveryDocumentLines()
                .get(0)
                .getPurchaseRefType());

    // PO state validation
    purchaseReferenceValidator.validatePOConfirmation(deliveryNumber, purchaseReferenceNumber);
    // Multi user validations
    ReceivingUtils.verifyUser(instructionResponse, userId, RequestType.UPDATE);
    // Received update for already completed instruction
    instructionStateValidator.validate(instructionResponse);

    // validate quantities for national po receiving
    validateForNationalPo(
        isNationalPo,
        instructionUpdateRequestFromClient,
        documentLine,
        quantityToBeReceived,
        projectedQuantity,
        firstExpiryFirstOut,
        instructionResponse,
        totalQuantityValueAfterReceiving);

    if (isKotlinEnabled) {
      validateWeightThreshold(
          documentLine, receivedQuantity, quantityToBeReceived, documentLine.getQuantityUOM());
      updateTotalReceivedQtyInInstructionDeliveryDoc(
          instructionResponse, documentLine, quantityToBeReceived);
    }

    List<ContainerDetails> childContainers = instructionResponse.getChildContainers();
    // getPrint label before creating container and printJob so that if there is any exception while
    // preparing labelData, containers are not created
    Map<String, Object> childCtrLabelsInfo = null;
    List<Map<String, Object>> oldChildCtrLabelsInfo = null;
    if (configUtils.isPrintingAndroidComponentEnabled()) {
      childCtrLabelsInfo =
          instructionPersisterService.getPrintlabeldata(
              instructionResponse, quantityToBeReceived, receivedQuantity, childContainers);
    } else {
      oldChildCtrLabelsInfo =
          instructionPersisterService.getOldPrintlabeldata(
              instructionResponse, quantityToBeReceived, receivedQuantity, childContainers);
    }

    if (configUtils.getConfiguredFeatureFlag(IS_EXPIRY_DATE_VALIDATION_ENABLED)) {
      InstructionUtils.validateExpiryDate(
          instructionUpdateRequestFromClient.getExpiryDate(), instructionResponse);
    }

    // create container and print jobs
    instructionPersisterService.createContainersAndPrintJobs(
        instructionUpdateRequestFromClient,
        httpHeaders,
        userId,
        instructionResponse,
        quantityToBeReceived,
        receivedQuantity,
        childContainers);

    // publish instruction to WFM
    instructionHelperService.publishInstruction(
        instructionResponse,
        instructionUpdateRequestFromClient,
        quantityToBeReceived,
        null,
        InstructionStatus.UPDATED,
        httpHeaders);

    setReceivedQtyInInstruction(isNationalPo, instructionResponse);

    // send instruction response
    if (!configUtils.isPrintingAndroidComponentEnabled()) {
      return new InstructionResponseImplOld(null, null, instructionResponse, oldChildCtrLabelsInfo);
    }
    return new InstructionResponseImplNew(null, null, instructionResponse, childCtrLabelsInfo);
  }

  public void updateTotalReceivedQtyInInstructionDeliveryDoc(
      Instruction instruction,
      DocumentLine mappedDocumentLine,
      Integer totalReceivedQtyAfterReceiving) {
    log.debug("updating total received qty not implemented for default update handler");
  }

  public void validateWeightThreshold(
      DocumentLine documentLine,
      Integer quantityAlreadyReceived,
      Integer quantityToBeReceived,
      String quantityUom) {
    log.debug("validate weight threshold not implemented for default update handler");
  }

  private void setReceivedQtyInInstruction(boolean isNationalPo, Instruction instruction) {
    if (!isNationalPo) {
      // In case of POCON (or other non-national PO), set instruction received qty based on po
      // receipts
      Long totalReceivedQty =
          receiptService.getReceivedQtyByPoAndDeliveryNumber(
              instruction.getPurchaseReferenceNumber(), instruction.getDeliveryNumber());
      instruction.setReceivedQuantity(totalReceivedQty.intValue());
    }
  }

  private void validateForNationalPo(
      boolean isNationalPo,
      UpdateInstructionRequest instructionUpdateRequestFromClient,
      DocumentLine documentLine,
      Integer quantityToBeReceived,
      Integer projectedQuantity,
      Boolean firstExpiryFirstOut,
      Instruction currentInstruction,
      Integer totalQuantityValueAfterReceiving)
      throws ReceivingException {

    if (!isNationalPo) {
      DocumentLine poLine4mRequest =
          instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
      log.debug(
          "non national po: {} pol: {} type: {}. skipping validations for update instruction",
          poLine4mRequest.getPurchaseReferenceNumber(),
          poLine4mRequest.getPurchaseReferenceLineNumber(),
          poLine4mRequest.getPurchaseRefType());
      return;
    }

    // Validate item life expectancy against the threshold
    InstructionUtils.validateThresholdForSellByDate(
        configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), MIN_LIFE_EXPECTANCY_V2, false),
        firstExpiryFirstOut,
        documentLine,
        false,
        false);

    // If totalQuantityValueAfterReceiving exceeds limit for PO/Po line
    if (totalQuantityValueAfterReceiving > projectedQuantity) {
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.UPDATE_INSTRUCTION_EXCEEDS_QUANTITY)
              .errorCode(ReceivingException.UPDATE_INSTRUCTION_ERROR_CODE)
              .errorKey(ExceptionCodes.UPDATE_INSTRUCTION_EXCEEDS_QUANTITY)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }

    // In problem flow will fetch currentReceiveQuantity by problemId and
    // maxReceiveQuantity(maxReceiveQty) from UI
    Gson gson = new Gson();
    DeliveryDocument deliveryDocument4mDb =
        gson.fromJson(currentInstruction.getDeliveryDocument(), DeliveryDocument.class);
    Pair<Long, Long> receivedQtyPair =
        getReceivedAndMaxReceiveQty(
            instructionUpdateRequestFromClient.getProblemTagId(),
            deliveryDocument4mDb,
            documentLine);

    Long currentReceiveQuantity = receivedQtyPair.getLeft();
    Long maxReceiveQuantity = receivedQtyPair.getRight();
    final long receivedAndToBeReceivedQty = currentReceiveQuantity + quantityToBeReceived;
    validateUpdateRequestOverageLimit(currentReceiveQuantity, maxReceiveQuantity);
    validateUpdateRequestNearOverageLimit(
        instructionUpdateRequestFromClient.getProblemTagId(),
        currentReceiveQuantity,
        maxReceiveQuantity,
        receivedAndToBeReceivedQty);
  }

  protected Pair<Long, Long> getReceivedAndMaxReceiveQty(
      String problemTagId, DeliveryDocument deliveryDocument, DocumentLine documentLine) {
    Long currentReceiveQuantity;
    Long maxReceiveQuantity;
    if (!StringUtils.isEmpty(problemTagId)) {
      currentReceiveQuantity = receiptService.getReceivedQtyByProblemId(problemTagId);
      maxReceiveQuantity = documentLine.getMaxReceiveQty();
    } else {
      currentReceiveQuantity =
          receiptService.getReceivedQtyByPoAndPoLine(
              documentLine.getPurchaseReferenceNumber(),
              documentLine.getPurchaseReferenceLineNumber());
      maxReceiveQuantity = documentLine.getExpectedQty();
      maxReceiveQuantity += Optional.ofNullable(documentLine.getMaxOverageAcceptQty()).orElse(0L);
    }
    return Pair.of(currentReceiveQuantity, maxReceiveQuantity);
  }

  private static void validateUpdateRequestOverageLimit(
      Long currentReceiveQuantity, Long maxReceiveQuantity) throws ReceivingException {
    // quantity reached allowed limit
    if (Objects.equals(currentReceiveQuantity, maxReceiveQuantity)) {
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.UPDATE_INSTRUCTION_REACHED_MAXIMUM_THRESHOLD)
              .errorCode(ReceivingException.OVERAGE_ERROR_CODE)
              .errorKey(ExceptionCodes.UPDATE_INSTRUCTION_REACHED_MAXIMUM_THRESHOLD)
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
  }

  private static void validateUpdateRequestNearOverageLimit(
      String requestProblemTagId,
      Long currentReceiveQuantity,
      Long maxReceiveQuantity,
      long receivedAndToBeReceivedQty)
      throws ReceivingException {
    Optional<String> requestProblemTagIdOptional =
        Optional.ofNullable(StringUtils.isEmpty(requestProblemTagId) ? null : requestProblemTagId);

    // receivedAndToBeReceivedQty reached near limit
    if (requestProblemTagIdOptional.isPresent()
        && (receivedAndToBeReceivedQty - maxReceiveQuantity > 0)) {
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(
                  String.format( // Near overage limit
                      ReceivingException.UPDATE_INSTRUCTION_NEAR_OVERAGE_LIMIT,
                      Math.abs(maxReceiveQuantity - currentReceiveQuantity)))
              .errorCode(ReceivingException.NEAR_OVERAGE_LIMIT_ERROR_CODE)
              .errorKey(ExceptionCodes.UPDATE_INSTRUCTION_NEAR_OVERAGE_LIMIT)
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
