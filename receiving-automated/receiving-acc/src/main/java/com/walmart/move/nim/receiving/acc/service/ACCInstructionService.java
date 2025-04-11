package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isKotlinEnabled;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.walmart.move.nim.receiving.acc.util.ACCUtils;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.OpenQtyCalculator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.function.Supplier;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

public class ACCInstructionService extends InstructionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ACCInstructionService.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Override
  protected Instruction createInstructionForUpcReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    // set door number for pbyl dock tag receiving
    setDoorForPbylDockTagReceiving(instructionRequest);
    httpHeaders.add(ReceivingConstants.WMT_PRODUCT_NAME, ReceivingConstants.APP_NAME_VALUE);
    final Long deliveryNumber = Long.parseLong(instructionRequest.getDeliveryNumber());
    if (instructionRequest.isOnline()) {
      LOGGER.debug(
          "The isOnline property is true in instruction request, checking for place on conveyor instruction eligibility");
      if (InstructionUtils.isDAConRequest(instructionRequest)) {
        return createPlaceOnConveyorInstruction(instructionRequest, deliveryNumber);
      }
    } else if (!instructionRequest.isOnline()
        && !StringUtils.isEmpty(instructionRequest.getMappedFloorLine())) {
      LOGGER.debug(
          "There is a mapped floor line in the instruction request, checking for create dock tag instruction eligibility");
      if (InstructionUtils.isDAConRequest(instructionRequest)) {
        return createDockTagInstruction(instructionRequest, httpHeaders, deliveryNumber);
      }
    }
    LOGGER.debug(
        "The isOnline property is false and mapped floor line doesn't exist in the instruction request, calling regular instruction service"
            + "to fetch instruction from OF");
    return super.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
  }

  // Writing this method to avoid 2 clients calls for floor line and place on conveyor instructions.
  // PO selection has no purpose in these 2 cases, bypassing will reduce 1 rest call
  @Override
  protected boolean checkForPlaceOnConveyorOrFloorLine(
      List<DeliveryDocument> deliveryDocuments4mGDM,
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse,
      boolean isDaConFreight,
      HttpHeaders headers)
      throws ReceivingException {
    // Check if DA con and if the door is online or mapped to a floor line.
    // If so, method for creating respective instructions
    if (!regulatedItemService.isVendorComplianceRequired(
            deliveryDocuments4mGDM.get(0).getDeliveryDocumentLines().get(0))
        && !(instructionRequest.isManualReceivingEnabled()
            || ReceivingConstants.AUTO_CASE_RECEIVE_FEATURE_TYPE.equals(
                instructionRequest.getFeatureType()))
        && !(instructionRequest.isOverflowReceiving())
        && isDaConFreight
        && ((instructionRequest.isOnline())
            || (!StringUtils.isEmpty(instructionRequest.getMappedFloorLine())))) {

      List<DeliveryDocument> activeDeliveryDocuments =
          tenantSpecificConfigReader.isFeatureFlagEnabled(
                  ReceivingConstants.ENABLE_FILTER_CANCELLED_PO)
              ? InstructionUtils.filterCancelledPoPoLine(deliveryDocuments4mGDM)
              : deliveryDocuments4mGDM;

      DeliveryDocument deliveryDocument = activeDeliveryDocuments.get(0);
      deliveryDocument.setDeliveryDocumentLines(
          Collections.singletonList(deliveryDocument.getDeliveryDocumentLines().get(0)));
      List<DeliveryDocument> deliveryDocumentsForInstruction =
          Collections.singletonList(deliveryDocument);
      instructionRequest.setDeliveryDocuments(deliveryDocumentsForInstruction);
      instructionResponse.setInstruction(
          createInstructionForUpcReceiving(instructionRequest, headers));

      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocumentsForInstruction.get(0).getDeliveryDocumentLines().get(0);

      Pair<Integer, Long> receivedQtyDetails =
          instructionHelperService.getReceivedQtyDetailsAndValidate(
              instructionRequest.getProblemTagId(),
              deliveryDocument,
              instructionRequest.getDeliveryNumber(),
              isKotlinEnabled(headers, tenantSpecificConfigReader),
              false);

      OpenQtyCalculator qtyCalculator =
          tenantSpecificConfigReader.getConfiguredInstance(
              String.valueOf(TenantContext.getFacilityNum()),
              ReceivingConstants.OPEN_QTY_CALCULATOR,
              ReceivingConstants.DEFAULT_OPEN_QTY_CALCULATOR,
              OpenQtyCalculator.class);

      OpenQtyResult openQtyResult =
          qtyCalculator.calculate(
              deliveryDocument.getDeliveryNumber(), deliveryDocument, deliveryDocumentLine);

      long maxReceiveQty = openQtyResult.getMaxReceiveQty();
      int totalReceivedQty = openQtyResult.getTotalReceivedQty();
      int openQty = Math.toIntExact(openQtyResult.getOpenQty());

      deliveryDocumentLine.setOpenQty(openQty);
      deliveryDocumentLine.setTotalReceivedQty(totalReceivedQty);
      instructionResponse.setDeliveryDocuments(deliveryDocumentsForInstruction);
      return true;
    } else {
      return false;
    }
  }

  private void setDoorForPbylDockTagReceiving(InstructionRequest instructionRequest) {

    if (Objects.nonNull(instructionRequest)
        && !StringUtils.isEmpty(instructionRequest.getPbylLocation())) {
      instructionRequest.setDoorNumber(instructionRequest.getPbylLocation());
      return;
    }

    if (Objects.nonNull(instructionRequest)
        && !StringUtils.isEmpty(instructionRequest.getPbylDockTagId())) {
      DockTagService dockTagService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DOCK_TAG_SERVICE,
              DockTagService.class);

      DockTag dockTagById = dockTagService.getDockTagById(instructionRequest.getPbylDockTagId());
      if (Objects.nonNull(dockTagById) && !StringUtils.isEmpty(dockTagById.getScannedLocation())) {
        instructionRequest.setDoorNumber(dockTagById.getScannedLocation());
      }
    }
  }

  private Instruction createPlaceOnConveyorInstruction(
      InstructionRequest instructionRequest, Long deliveryNumber) throws ReceivingException {
    LOGGER.debug(
        "Received instruction request for DA Conveyable freight, sending place on conveyor instruction"
            + "response for delivery:{}, PO:{} and PO line:{}",
        deliveryNumber,
        instructionRequest.getDeliveryDocuments().get(0).getPurchaseReferenceNumber(),
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber());
    return InstructionUtils.getPlaceOnConveyorInstruction(
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getGtin(),
        appConfig.isDeliveryDocInPlaceOnConveyorEnabled()
            ? instructionRequest.getDeliveryDocuments().get(0)
            : null,
        deliveryNumber);
  }

  private Instruction createDockTagInstruction(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders, Long deliveryNumber) {
    LOGGER.debug(
        "Received instruction request for DA Conveyable freight, sending create dock tag instruction"
            + "response for delivery:{}",
        deliveryNumber);
    Instruction existingDockTagInstruction = getDockTagInstructionIfExists(instructionRequest);
    if (Objects.nonNull(existingDockTagInstruction)) {
      LOGGER.debug(
          "Open dock tag instruction already exists for deliveryNumber: {}", deliveryNumber);
      return existingDockTagInstruction;
    } else {
      LOGGER.debug(
          "Open dock tag instruction does not exist for deliveryNumber: {}. Creating a new dock tag and instruction",
          deliveryNumber);
      Instruction dockTagInstruction =
          createNewDockTagInstruction(instructionRequest, httpHeaders, deliveryNumber);
      return dockTagInstruction;
    }
  }

  private Instruction createNewDockTagInstruction(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders, Long deliveryNumber) {
    Instruction dockTagInstruction =
        instructionPersisterService.saveDockTagInstructionAndContainer(
            instructionRequest, httpHeaders, deliveryNumber);
    instructionHelperService.publishInstruction(
        dockTagInstruction, null, null, null, InstructionStatus.CREATED, httpHeaders);
    return dockTagInstruction;
  }

  @Override
  public Instruction getDockTagInstructionIfExists(InstructionRequest instructionRequest) {
    return instructionPersisterService.getDockTagInstructionIfExists(instructionRequest);
  }

  public InstructionResponse createFloorLineDockTag(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    Instruction dockTagInstruction =
        createDockTagInstruction(
            instructionRequest,
            httpHeaders,
            Long.parseLong(instructionRequest.getDeliveryNumber()));
    return completeInstructionForDockTag(dockTagInstruction, httpHeaders);
  }

  /**
   * Creates an open PByL docktag along with instruction and container, and publishes the created
   * instruction to WFM This will be different from existing PByL flow as the instruction will be
   * completed using complete instruction API, and not completed at creation time
   *
   * @param instructionRequest
   * @param httpHeaders
   * @return
   */
  @Override
  public InstructionResponse createPByLDockTagInstructionResponse(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) {
    LOGGER.info(
        "Creating PByL DockTag for location {}, request {}",
        instructionRequest.getPbylLocation(),
        instructionRequest);

    Long deliveryNumber = Long.valueOf(instructionRequest.getDeliveryNumber());
    final String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    final String facilityNum = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);

    String mappedPbylArea = instructionRequest.getPbylLocation();
    if (!StringUtils.hasLength(mappedPbylArea)) {
      LOGGER.error("Missing PByL area mapping for door {}", instructionRequest.getDoorNumber());
      mappedPbylArea =
          tenantSpecificConfigReader.getDCSpecificMoveDestinationForNonConDockTag(
              Integer.valueOf(facilityNum));
    }

    // creating and persist docktag instruction
    // activity name will be Dock Tag, so that it is consistent in downstream systems
    Instruction dockTagInstruction =
        instructionPersisterService.saveInstruction(
            instructionHelperService.getDockTagInstruction(
                instructionRequest, httpHeaders, mappedPbylArea, DockTagType.NON_CON));

    // create and persist pallet for the docktag
    Container dockTagPalletContainer =
        instructionPersisterService.createContainerForDockTag(
            dockTagInstruction, instructionRequest, httpHeaders, Boolean.FALSE);

    DockTagService dockTagService =
        tenantSpecificConfigReader.getConfiguredInstance(
            facilityNum, ReceivingConstants.DOCK_TAG_SERVICE, DockTagService.class);
    DockTag dockTag =
        dockTagService.createDockTag(
            dockTagInstruction.getDockTagId(), deliveryNumber, userId, DockTagType.NON_CON);
    dockTag.setScannedLocation(mappedPbylArea);
    dockTagService.saveDockTag(dockTag);

    LOGGER.info(
        "Created PByL Dock Tag: {} instruction: {}, pallet for delivery: {} and PByL location: {}",
        dockTagInstruction,
        dockTag.getDockTagId(),
        deliveryNumber,
        mappedPbylArea);

    // publish created instruction to WFM
    instructionHelperService.publishInstruction(
        dockTagInstruction, null, null, null, InstructionStatus.CREATED, httpHeaders);

    InstructionResponse pbylDockTagInstructionResponse = new InstructionResponseImplNew();
    pbylDockTagInstructionResponse.setInstruction(dockTagInstruction);
    pbylDockTagInstructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
    // setting PByL docktag activity name to select correct flow in client, does not have any logic
    // associated with it
    pbylDockTagInstructionResponse
        .getInstruction()
        .setActivityName(ReceivingConstants.PBYL_DOCK_TAG_ACTIVITY_NAME);
    return pbylDockTagInstructionResponse;
  }

  public InstructionResponse createNonConDockTag(
      InstructionRequest instructionRequest, HttpHeaders headers, String mappedPbylArea) {

    try {
      Pair<Instruction, Container> instructionContainerPair =
          instructionPersisterService.saveContainersAndInstructionForNonConDockTag(
              instructionRequest, headers, mappedPbylArea);
      Instruction dockTagInstruction = instructionContainerPair.getKey();
      // publish CREATED to WFM
      instructionHelperService.publishInstruction(
          dockTagInstruction, null, null, null, InstructionStatus.CREATED, headers);

      Container container = instructionContainerPair.getValue();
      List<ContainerItem> list = new ArrayList<>();
      container.setContainerItems(list);

      return publishAndGetInstructionResponse(container, dockTagInstruction, headers);
    } catch (ReceivingException e) {
      LOGGER.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_CREATE_DOCK_TAG,
          ReceivingConstants.DOCK_TAG_CREATE_ERROR_MESSAGE);
    }
  }

  @Override
  protected InstructionResponse getUpcReceivingInstructionResponse(
      InstructionRequest instructionRequest,
      Supplier<List<DeliveryDocument>> responseDeliveryDocumentSupplier,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, tenantSpecificConfigReader);
    Instruction upcReceivingInstruction = null;
    InstructionResponse instructionResponse = new InstructionResponseImplNew();

    try {
      upcReceivingInstruction = createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      // TODO: This seems incorrect, as any other instructionError apart from the ones checked
      // in if block
      // will give OF_GENERIC_ERROR, which is wrong for logging. Should implement the solution
      // commented in the method soon for this to be correct.
      InstructionError instructionError =
          InstructionUtils.getFdeInstructionError(receivingException)
              .orElse(InstructionError.OF_GENERIC_ERROR);

      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
              ReceivingConstants.ENABLE_STREAMLINED_PBYL_RECEIVING)
          && instructionError.equals(InstructionError.PBYL_DOCKTAG_NOT_PRINTED)) {
        LOGGER.error(
            "Received {} error from OP - PByL DockTag missing. Creating DockTag and sending to client. request {}",
            receivingException,
            instructionRequest);
        // create PByL DockTag and send the labels as response to mobile
        return createPByLDockTagInstructionResponse(instructionRequest, httpHeaders);
      }

      // throw the exception back if unhandled with response
      throw receivingException;
    }

    if (isKotlinEnabled) {
      if (isBlank(upcReceivingInstruction.getGtin())) {
        LOGGER.info(
            "Setting the GTIN value in the instruction to the scanned UPC value from the request, if GTIN is Null / Empty / Blank ");
        upcReceivingInstruction.setGtin(instructionRequest.getUpcNumber());
      }
      instructionResponse.setDeliveryDocuments(
          getUpdatedDeliveryDocumentsForAllowableOverage(
              upcReceivingInstruction, responseDeliveryDocumentSupplier.get()));
      instructionResponse.setInstruction(upcReceivingInstruction);
      return instructionResponse;
    }

    instructionResponse.setDeliveryDocuments(responseDeliveryDocumentSupplier.get());
    instructionResponse.setInstruction(upcReceivingInstruction);
    return instructionResponse;
  }

  @Override
  protected Instruction getInstructionFromFDE(
      InstructionRequest instructionRequest,
      Instruction instruction,
      FdeCreateContainerRequest fdeCreateContainerRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    String instructionResponse = "";
    // set pbyl fulfillment type based on instruction request
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.ENABLE_STREAMLINED_PBYL_RECEIVING)) {
      ACCUtils.setFulfillmentTypeInFdeRequest(instructionRequest, fdeCreateContainerRequest);
    }

    try {
      instructionResponse = fdeService.receive(fdeCreateContainerRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      LOGGER.error("Call to FDE service failed. errorMessage={}", receivingException.getMessage());
      instructionPersisterService.saveInstruction(instruction);
      throw receivingException;
    }
    FdeCreateContainerResponse fdeCreateContainerResponse =
        gson.fromJson(instructionResponse, FdeCreateContainerResponse.class);
    instruction =
        InstructionUtils.processInstructionResponse(
            instruction, instructionRequest, fdeCreateContainerResponse);
    return instruction;
  }
}
