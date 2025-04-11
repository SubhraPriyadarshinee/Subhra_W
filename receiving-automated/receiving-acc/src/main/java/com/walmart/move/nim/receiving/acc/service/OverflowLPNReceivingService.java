package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.model.OverflowLPNReceivingRequest;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseOrderInfo;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.utils.constants.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class OverflowLPNReceivingService extends LPNReceivingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(OverflowLPNReceivingService.class);

  @Autowired private ReceiptService receiptService;

  public void receiveByLPN(OverflowLPNReceivingRequest request, HttpHeaders httpHeaders)
      throws ReceivingException {

    String lpn = request.getLpn();
    Long deliveryNumber = request.getDeliveryNumber();
    String location = request.getLocation();
    Optional<Container> existingContainer = getExistingContainer(request);

    // create Http headers from tenant context
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    // Get PO, POLines from LABEL_DATA
    PurchaseOrderInfo purchaseOrderInfo = getPurchaseOrderInfo(lpn, deliveryNumber);

    // set deliveryNumber if it is null in request payload
    if (Objects.isNull(deliveryNumber)) {
      deliveryNumber = purchaseOrderInfo.getDeliveryNumber();
      request.setDeliveryNumber(purchaseOrderInfo.getDeliveryNumber());
    }
    LOGGER.info(
        "OverflowLPNReceivingService: Fetched purchase order info for lpn: {} and delivery: {}. PO Info: {}",
        lpn,
        deliveryNumber,
        purchaseOrderInfo);

    PossibleUPC possibleUPC = validateAndGetPossibleUpc(request, purchaseOrderInfo);

    // Get PO/POL details from GDM (Could be multi PO/POL)
    List<DeliveryDocument> deliveryDocuments =
        fetchDeliveryDocumentsFromGDM(
            purchaseOrderInfo, possibleUPC, deliveryNumber, lpn, httpHeaders);
    checkIfDeliveryStatusReceivable(deliveryNumber, lpn, deliveryDocuments.get(0), httpHeaders);

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL)) {
      InstructionUtils.validateItemXBlocked(
          deliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    }
    Pair<DeliveryDocument, Boolean> selectedDeliveryDocAndIsReceivable =
        selectDeliveryDocAndCheckIfReceivable(
            deliveryDocuments, purchaseOrderInfo, possibleUPC.getOrderableGTIN());
    DeliveryDocument deliveryDocument = selectedDeliveryDocAndIsReceivable.getKey();

    // validate receivedQty
    if (Boolean.FALSE.equals(selectedDeliveryDocAndIsReceivable.getValue())) {
      LOGGER.error(
          "OverflowLPNReceivingService: Received maximum allowable quantity for this item. lpn: {}",
          lpn);
      createOrUpdateExceptionContainer(
          request, deliveryDocument, ContainerException.OVERAGE, existingContainer, httpHeaders);
      throw new ReceivingBadDataException(
          ExceptionCodes.OVERAGE_ERROR,
          String.format(ExceptionDescriptionConstants.OVERAGE_ERROR_MSG, lpn));
    }

    Instruction instruction =
        callOFandGetInstruction(
            request,
            deliveryDocument,
            deliveryDocuments.get(0).getDeliveryStatus().name(),
            existingContainer,
            userId,
            httpHeaders);

    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, ACCConstants.DEFAULT_SECURITY_ID);

    // publish instruction created to WFM
    instructionHelperService.publishInstruction(
        instruction, null, null, null, InstructionStatus.CREATED, httpHeaders);

    // channel type
    String purRefType =
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseRefType(),
            deliveryDocument.getDeliveryDocumentLines().get(0).getActiveChannelMethods());

    // create updateInstruction request
    UpdateInstructionRequest instructionUpdateRequest =
        InstructionUtils.getInstructionUpdateRequestForOnConveyor(
            location, deliveryNumber, purRefType, deliveryDocument);

    // publish updated instruction to WFM
    publishUpdateInstructionToWFM(
        httpHeaders, instruction, instructionUpdateRequest, ACCConstants.AIR_OVF);

    Pair<Container, Instruction> containersAndSavedInstruction =
        getContainersReceiptsAndSavedInstruction(
            instruction, instructionUpdateRequest, existingContainer, httpHeaders);

    Container consolidatedContainer = containersAndSavedInstruction.getKey();
    Set<Container> childContainerList = new HashSet<>();
    consolidatedContainer.setChildContainers(childContainerList);

    publishContainerInfoToDownstream(lpn, httpHeaders, consolidatedContainer);

    instruction = containersAndSavedInstruction.getValue();

    // Publishing Completed instruction to WFM.
    instructionHelperService.publishInstruction(
        instruction, null, null, consolidatedContainer, InstructionStatus.COMPLETED, httpHeaders);

    LOGGER.info(
        "OverflowLPNReceivingService: Received lpn: {}, delivery: {}, location: {}",
        lpn,
        deliveryNumber,
        location);
  }

  /**
   * If container is not present in Receiving, AIR app will ask user to scan the case upc on the
   * case, and we will verify this upc with the possible UPCs fetched from LABEL_DATA table for this
   * lpn. If it doesn't match, that means the lpn was incorrectly applied on the wrong case.
   */
  private PossibleUPC validateAndGetPossibleUpc(
      OverflowLPNReceivingRequest request, PurchaseOrderInfo purchaseOrderInfo) {

    String upc = request.getUpc();
    String lpn = request.getLpn();
    PossibleUPC possibleUPC =
        JacksonParser.convertJsonToObject(purchaseOrderInfo.getPossibleUPC(), PossibleUPC.class);

    if (request.isUpcValidationRequired()
        && Objects.nonNull(upc)
        && !upc.equalsIgnoreCase(possibleUPC.getOrderableGTIN())
        && !upc.equalsIgnoreCase(possibleUPC.getConsumableGTIN())) {
      LOGGER.error(
          "OverflowLPNReceivingService: upc validation failed for lpn: {}. Provided upc: {}",
          lpn,
          upc);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.UPC_VALIDATION_FAILED,
          String.format(ExceptionDescriptionConstants.UPC_VALIDATION_FAILED_ERROR_MSG, lpn, upc));
    }
    return possibleUPC;
  }

  private Optional<Container> getExistingContainer(OverflowLPNReceivingRequest request) {
    String lpn = request.getLpn();
    Optional<Container> existingContainer =
        Optional.ofNullable(containerPersisterService.getContainerDetails(lpn));
    if (request.isVerifyContainerExists() && !existingContainer.isPresent()) {
      LOGGER.error("OverflowLPNReceivingService: Container not found for tracking id: {}", lpn);
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.CONTAINER_NOT_FOUND,
          String.format(ExceptionDescriptionConstants.CONTAINER_NOT_FOUND_ERROR_MSG, lpn));
    }
    return existingContainer;
  }

  /**
   * create OF request payload, call OF and return instruction from OF response payload
   *
   * @param request
   * @param deliveryDocument
   * @param deliveryStatus
   * @param existingContainer
   * @param userId
   * @param httpHeaders
   * @return Instruction
   */
  private Instruction callOFandGetInstruction(
      OverflowLPNReceivingRequest request,
      DeliveryDocument deliveryDocument,
      String deliveryStatus,
      Optional<Container> existingContainer,
      String userId,
      HttpHeaders httpHeaders) {

    // Create InstructionRequest POJO and use that for building OF payload
    InstructionRequest instructionRequest =
        getInstructionRequest(
            request.getLpn(),
            request.getDeliveryNumber(),
            request.getLocation(),
            deliveryDocument,
            deliveryStatus);

    // Create OF request payload
    FdeCreateContainerRequest fdeCreateContainerRequest =
        InstructionUtils.prepareFdeCreateContainerRequestForOnConveyor(
            httpHeaders, instructionRequest, userId);

    // call OF
    String instructionResponse = null;
    try {
      instructionResponse = fdeService.receive(fdeCreateContainerRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      handleFdeServiceError(
          receivingException, request, deliveryDocument, existingContainer, httpHeaders);
    }

    // populate instruction from OF response
    Instruction instruction =
        getInstruction(httpHeaders, deliveryDocument, instructionRequest, instructionResponse);
    instruction.setCreateUserId(userId);
    return instruction;
  }

  /**
   * handle OF error response
   *
   * @param receivingException
   * @param request
   * @param deliveryDocument
   * @param existingContainer
   * @param httpHeaders
   */
  protected void handleFdeServiceError(
      ReceivingException receivingException,
      OverflowLPNReceivingRequest request,
      DeliveryDocument deliveryDocument,
      Optional<Container> existingContainer,
      HttpHeaders httpHeaders) {

    String lpn = request.getLpn();
    LOGGER.error("OverflowLPNReceivingService: OF call failed for lpn: {}", lpn);
    Optional<InstructionError> instructionErrorOptional =
        InstructionUtils.getFdeInstructionError(receivingException);

    if (instructionErrorOptional.isPresent()) {
      InstructionError instructionError = instructionErrorOptional.get();
      switch (instructionError) {
        case NO_ALLOCATION:
          LOGGER.error(
              "OverflowLPNReceivingService: No allocation found, publishing exception container for lpn: {}",
              lpn);
          createOrUpdateExceptionContainer(
              request,
              deliveryDocument,
              ContainerException.NO_ALLOCATION_FOUND,
              existingContainer,
              httpHeaders);
          throw new ReceivingBadDataException(
              ExceptionCodes.NO_ALLOCATION,
              String.format(ExceptionDescriptionConstants.NO_ALLOCATION_ERROR_MSG, lpn));
        case CHANNEL_FLIP:
          LOGGER.error(
              "OverflowLPNReceivingService: Channel has flipped, publishing exception container for lpn: {}",
              lpn);
          createOrUpdateExceptionContainer(
              request,
              deliveryDocument,
              ContainerException.CHANNEL_FLIP,
              existingContainer,
              httpHeaders);
          throw new ReceivingBadDataException(
              ExceptionCodes.CHANNEL_FLIP,
              String.format(ExceptionDescriptionConstants.CHANNEL_FLIP_ERROR_MSG, lpn));
        default:
          // In case the error is a known FDE error, but not handled above, consider a generic error
          if (tenantSpecificConfigReader.isFeatureFlagEnabled(
              ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH)) {
            LOGGER.error(
                "OverflowLPNReceivingService: Known InstructionError: {} in FDE Call: {}, publishing exception container for lpn: {}",
                instructionError,
                receivingException,
                lpn);
            createOrUpdateExceptionContainer(
                request,
                deliveryDocument,
                ContainerException.NO_ALLOCATION_FOUND,
                existingContainer,
                httpHeaders);
          }
          throw new ReceivingInternalException(
              ExceptionCodes.OF_GENERIC_ERROR,
              String.format(ExceptionDescriptionConstants.OF_GENERIC_ERROR_MSG, lpn));
      }
    } else {
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH)) {
        LOGGER.error(
            "OverflowLPNReceivingService: Exception in FDE Call: {}, publishing exception container for lpn: {}",
            receivingException,
            lpn);
        createOrUpdateExceptionContainer(
            request,
            deliveryDocument,
            ContainerException.NO_ALLOCATION_FOUND,
            existingContainer,
            httpHeaders);
      }
      throw new ReceivingInternalException(
          ExceptionCodes.OF_GENERIC_ERROR,
          String.format(ExceptionDescriptionConstants.OF_GENERIC_ERROR_MSG, lpn));
    }
  }

  /**
   * fetch list of POs and PO lines for the given upc and delivery number.
   *
   * @param purchaseOrderInfo
   * @param possibleUPC
   * @param deliveryNumber
   * @param lpn
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @Override
  protected List<DeliveryDocument> fetchDeliveryDocumentsFromGDM(
      PurchaseOrderInfo purchaseOrderInfo,
      PossibleUPC possibleUPC,
      Long deliveryNumber,
      String lpn,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    try {
      return getDeliveryDocuments(purchaseOrderInfo, possibleUPC, httpHeaders);
    } catch (ReceivingDataNotFoundException exc) {
      LOGGER.error(
          "OverflowLPNReceivingService: Delivery docs not found from GDM for delivery: {} possibleUPC: {}",
          deliveryNumber,
          possibleUPC);
      throw exc;
    }
  }

  /**
   * if container exists, update the existing exception container and container item else, create a
   * new exception container and container item
   *
   * @param request
   * @param deliveryDocument
   * @param containerException
   * @param existingContainer
   * @param httpHeaders
   */
  public void createOrUpdateExceptionContainer(
      OverflowLPNReceivingRequest request,
      DeliveryDocument deliveryDocument,
      ContainerException containerException,
      Optional<Container> existingContainer,
      HttpHeaders httpHeaders) {

    if (existingContainer.isPresent()) {
      // container exists, so update the exception container in receiving
      // and publish to inventory
      updateExceptionContainer(
          existingContainer.get(), request, deliveryDocument, containerException, httpHeaders);
    } else {
      // create container and publish to inventory
      publishExceptionContainer(
          request.getLpn(),
          request.getDeliveryNumber(),
          request.getLocation(),
          deliveryDocument,
          containerException,
          httpHeaders);
    }
  }

  /**
   * if container exists, update the existing container and container item else, create a new
   * container and container item
   *
   * @param instruction
   * @param instructionUpdateRequest
   * @param existingContainer
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public Container createOrUpdateContainer(
      Instruction instruction,
      UpdateInstructionRequest instructionUpdateRequest,
      Optional<Container> existingContainer,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    if (existingContainer.isPresent()) {
      return updateContainer(
          existingContainer.get(), instruction, instructionUpdateRequest, httpHeaders);
    } else {
      return containerService.createAndCompleteParentContainer(
          instruction, instructionUpdateRequest, Boolean.TRUE);
    }
  }

  private Pair<Container, Instruction> getContainersReceiptsAndSavedInstruction(
      Instruction instruction,
      UpdateInstructionRequest instructionUpdateRequest,
      Optional<Container> existingContainer,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    Instruction updatedInstruction =
        instructionPersisterService.updateAndSaveInstruction(
            instructionUpdateRequest, userId, instruction);

    Container container =
        createOrUpdateContainer(
            updatedInstruction, instructionUpdateRequest, existingContainer, httpHeaders);

    receiptService.createReceiptsFromInstruction(
        instructionUpdateRequest, updatedInstruction.getProblemTagId(), userId);

    return new Pair<>(container, updatedInstruction);
  }

  public void updateExceptionContainer(
      Container container,
      OverflowLPNReceivingRequest request,
      DeliveryDocument deliveryDocument,
      ContainerException containerException,
      HttpHeaders httpHeaders) {

    Container newContainer =
        prepareExceptionContainer(
            request.getLpn(),
            request.getLocation(),
            request.getDeliveryNumber(),
            deliveryDocument,
            containerException);

    Long existingItem = container.getContainerItems().get(0).getItemNumber();
    Long newItem = newContainer.getContainerItems().get(0).getItemNumber();
    if (!Objects.equals(existingItem, newItem)) {
      throw new ReceivingInternalException(
          ExceptionCodes.ITEM_CHANGED_AFTER_PO_AUTO_SELECTION,
          String.format(
              ExceptionDescriptionConstants.ITEM_CHANGED_AFTER_PO_AUTO_SELECTION_ERROR_MSG,
              container.getTrackingId(),
              existingItem,
              newItem));
    }

    ContainerUtils.copyContainerProperties(container, newContainer);
    ContainerUtils.copyContainerItemProperties(
        container.getContainerItems().get(0), newContainer.getContainerItems().get(0));

    container.setPublishTs(new Date());
    container.setLastChangedTs(new Date());
    container.setLastChangedUser(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    containerPersisterService.saveContainer(container);

    // TODO: check here if child container is becoming null after creating new container

    exceptionContainerHandlerFactory
        .exceptionContainerHandler(containerException)
        .publishException(container);
  }

  public Container updateContainer(
      Container container,
      Instruction instruction,
      UpdateInstructionRequest instructionUpdateRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    Container newContainer =
        containerService.constructContainer(
            instruction,
            instruction.getContainer(),
            Boolean.TRUE,
            Boolean.FALSE,
            instructionUpdateRequest);
    newContainer.setOnConveyor(Boolean.TRUE);
    containerService.setDistributionAndComplete(userId, newContainer);

    Long existingItem = container.getContainerItems().get(0).getItemNumber();
    Long newItem = newContainer.getContainerItems().get(0).getItemNumber();
    if (!Objects.equals(existingItem, newItem)) {
      throw new ReceivingInternalException(
          ExceptionCodes.ITEM_CHANGED_AFTER_PO_AUTO_SELECTION,
          String.format(
              ExceptionDescriptionConstants.ITEM_CHANGED_AFTER_PO_AUTO_SELECTION_ERROR_MSG,
              container.getTrackingId(),
              existingItem,
              newItem));
    }

    ContainerUtils.copyContainerProperties(container, newContainer);
    ContainerUtils.copyContainerItemProperties(
        container.getContainerItems().get(0), newContainer.getContainerItems().get(0));

    container.setPublishTs(new Date());
    container.setLastChangedTs(new Date());
    container.setLastChangedUser(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    containerPersisterService.saveContainer(container);
    return container;
  }

  private PurchaseOrderInfo getPurchaseOrderInfo(String lpn, Long deliveryNumber) {
    if (Objects.isNull(deliveryNumber)) {
      return Optional.ofNullable(labelDataService.getPurchaseOrderInfoFromLabelDataLpn(lpn))
          .orElseThrow(
              () ->
                  new ReceivingDataNotFoundException(
                      ExceptionCodes.LABEL_DATA_NOT_FOUND,
                      String.format(
                          ExceptionDescriptionConstants.LABEL_DATA_NOT_FOUND_ERROR_MSG, lpn)));
    } else {
      return labelDataService.getPurchaseOrderInfo(deliveryNumber, lpn);
    }
  }

  private void checkIfDeliveryStatusReceivable(
      Long deliveryNumber, String lpn, DeliveryDocument deliveryDocument, HttpHeaders httpHeaders)
      throws ReceivingException {

    String deliveryStatus = deliveryDocument.getDeliveryStatus().name();
    String deliveryLegacyStatus = deliveryDocument.getDeliveryLegacyStatus();

    if (ReceivingUtils.checkIfDeliveryWorkingOrOpen(deliveryStatus, deliveryLegacyStatus)) return;
    if (ReceivingUtils.needToCallReopen(deliveryStatus, deliveryLegacyStatus)) {
      instructionHelperService.reopenDeliveryIfNeeded(
          deliveryNumber, deliveryStatus, httpHeaders, deliveryLegacyStatus);
      LOGGER.info(
          "OverflowLPNReceivingService: Reopened delivery: {} for receiving container with lpn: {}",
          deliveryNumber,
          lpn);
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.DELIVERY_NOT_RECEIVABLE,
          String.format(
              ReceivingConstants.DELIVERY_NOT_RECEIVABLE_ERROR_MESSAGE,
              deliveryNumber,
              deliveryStatus),
          deliveryNumber,
          deliveryStatus);
    }
  }
}
