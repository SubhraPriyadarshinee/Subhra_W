package com.walmart.move.nim.receiving.wfs.service;

import static java.util.Objects.nonNull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.docktag.CreateDockTagRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintableLabelDataRequest;
import com.walmart.move.nim.receiving.core.service.DefaultDeliveryDocumentSelector;
import com.walmart.move.nim.receiving.core.service.FdeService;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.PrintingAndLabellingService;
import com.walmart.move.nim.receiving.core.service.RetryableFDEService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DockTagType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.config.WFSManagedConfig;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import com.walmart.move.nim.receiving.wfs.utils.WFSUtility;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;

public class WFSInstructionService extends InstructionService {
  private static final Logger log = LoggerFactory.getLogger(WFSInstructionService.class);
  @Autowired private Gson gson;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired TenantSpecificConfigReader configUtils;
  @Autowired private DefaultDeliveryDocumentSelector defaultDeliveryDocumentSelector;
  @Autowired private PrintingAndLabellingService printingAndLabellingService;
  @Autowired private WFSInstructionUtils wfsInstructionUtils;
  @Autowired private WFSInstructionHelperService wfsInstructionHelperService;
  @ManagedConfiguration private WFSManagedConfig wfsManagedConfig;

  @Resource(name = ReceivingConstants.FDE_SERVICE)
  private FdeService fdeService;

  @Resource(name = ReceivingConstants.RETRYABLE_FDE_SERVICE)
  private RetryableFDEService retryableFDEService;

  @Override
  public InstructionResponse serveInstructionRequest(
      String instructionRequestString, HttpHeaders httpHeaders) throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(instructionRequestString, InstructionRequest.class);
    log.info("Received InstructionRequest as: {} inside WFSInstructionService", instructionRequest);
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    Instruction instruction = null;

    // New instruction flow

    // Single PO : No vendor compliance
    // 1st API call: Return single delivery document and instruction code: "WFS Quantity Capture"
    // 2nd API call: Client sends quantity. Return instruction code: "WFS Build Container"

    // Single PO : vendor compliance
    // 1st API call: Return delivery documents with isLimitedQtyVerificationRequired,
    // isLithiumIonVerificationRequired set. Can add instruction code
    // 2nd API call: Return single delivery document and instruction code: "WFS Quantity Capture"
    // 3rd API call: Client sends quantity. Return instruction code: "WFS Build Container"

    // Multi PO : No vendor compliance
    // 1st API call: Return all POs, instruction code: "MANUAL_PO_SELECTION"
    // 2nd API call: Return single delivery document and line and instruction code: "WFS Quantity
    // Capture"
    // 3rd API call: Client sends quantity. Return instruction code: "WFS Build Container"

    // Multi PO : Vendor compliance
    // 1st API call: Return all POs, instruction code: "MANUAL_PO_SELECTION"
    // 2nd API call: Return single delivery document and line and with
    // isLimitedQtyVerificationRequired,
    // isLithiumIonVerificationRequired set. Can add instruction code
    // 3rd API call: Return single delivery document and instruction code: WFS Quantity Capture
    // 4th API call: Client sends quantity. Return instruction code: "WFS Build Container"

    if (CollectionUtils.isEmpty(instructionRequest.getDeliveryDocuments())) {

      Timestamp receivingScanTimeStamp = Timestamp.from(Instant.now());

      // If it's a scan-upc flow, then we have to check whether we have any Pending shelf
      // Containers to Receive!
      // (There is no 2D barcode in re-receiving container in pilot.
      // so GS1 ReceivingType is not possible!)
      wfsInstructionHelperService.checkForPendingShelfContainers(instructionRequest, httpHeaders);

      // Get DeliveryDocument from GDM when deliveryDocuments are null
      List<DeliveryDocument> deliveryDocuments_gdm =
          fetchDeliveryDocument(instructionRequest, httpHeaders);
      deliveryDocuments_gdm
          .stream()
          .map(DeliveryDocument::getAdditionalInfo)
          .filter(Objects::nonNull)
          .forEach(
              additionalInfo -> additionalInfo.setReceivingScanTimeStamp(receivingScanTimeStamp));
      deliveryDocuments_gdm =
          configUtils.isFeatureFlagEnabled(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO)
              ? InstructionUtils.filterCancelledPoPoLine(deliveryDocuments_gdm)
              : deliveryDocuments_gdm;

      // Check if Delivery is in receivable state
      if (appConfig.isCheckDeliveryStatusReceivable())
        wfsInstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocuments_gdm.get(0));

      // update delivery docs
      deliveryDocuments_gdm = deliveryDocumentHelper.updateDeliveryDocuments(deliveryDocuments_gdm);

      // update received qty for each po line
      if (ReceivingUtils.isKotlinEnabled(httpHeaders, configUtils)) {
        if (configUtils.isFeatureFlagEnabled(
            ReceivingConstants.ENABLE_POPULATE_RECEIVED_QTY_PO_MOBILE)) {
          updateOpenQtyForEachPoPoline(deliveryDocuments_gdm, ReceivingConstants.Uom.EACHES);
        }
      } else if (configUtils.isFeatureFlagEnabled(
          ReceivingConstants.ENABLE_POPULATE_RECEIVED_QTY_PO)) {
        updateOpenQtyForEachPoPoline(deliveryDocuments_gdm, ReceivingConstants.Uom.EACHES);
      }

      instruction = new Instruction();

      // Single PO, check and set regulated item, if no vendor compliance required, display quantity
      // page. Send back only selected PO Line in this case
      if (!CollectionUtils.isEmpty(deliveryDocuments_gdm) && deliveryDocuments_gdm.size() == 1) {
        return createInstructionResponseForSingleDeliveryDocument(
            instructionRequest, instructionResponse, instruction, deliveryDocuments_gdm);
      }
      // multi po always directs to po selection page
      else {
        instruction.setInstructionCode(ReceivingConstants.MANUAL_PO_SELECTION);
        instructionResponse.setInstruction(instruction);
        instructionResponse.setDeliveryDocuments(deliveryDocuments_gdm);
      }
    } else {
      DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

      // Check if Delivery is in receivable state
      // TODO remove this after ui migrates to new instruction flow because already checked in 1st
      // API call
      if (!tenantSpecificConfigReader.isFeatureFlagEnabled(
              WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED)
          && appConfig.isCheckDeliveryStatusReceivable()) {
        InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocument);
      }

      // check for weight and cube
      InstructionUtils.checkIfDeliveryDocumentHasWgtCbAdditionalInfo(deliveryDocument);

      // auto select line if there are multiple
      autoSelectDeliveryDocumentLineIfNeeded(instructionRequest, deliveryDocument);

      DeliveryDocumentLine documentLine =
          instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);

      // check overage receiving case
      // feature flags separately for mobile and web application
      if (((!ReceivingUtils.isKotlinEnabled(httpHeaders, configUtils)
                  && tenantSpecificConfigReader.isFeatureFlagEnabled(
                      WFSConstants.WFS_BLOCK_OVERAGE_RECEIVING))
              || (ReceivingUtils.isKotlinEnabled(httpHeaders, configUtils)
                  && tenantSpecificConfigReader.isFeatureFlagEnabled(
                      WFSConstants.WFS_BLOCK_OVERAGE_RECEIVING_MOBILE)))
              && Objects.nonNull(instructionRequest.getEnteredQty())
          && WFSUtility.isExceedingOverageThreshold(
              instructionRequest, ReceivingUtils.isKotlinEnabled(httpHeaders, configUtils))) {
        return WFSUtility.createInstructionResponseForOverageReceiving(instructionRequest);
      }

      // Check vendor compliance. If needed, send back the same delivery documents with the fields
      // limitedQtyVerificationRequired, lithiumIonVerificationRequired set.
      // Here only selected po line is sent back.
      // TODO Remove the flag once ui migrates to new flow, flag is needed because current flow
      // expects regulated item to be checked in ui itself
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED)) {
        if (Objects.isNull(instructionRequest.getEnteredQty())
            && Objects.isNull(documentLine.getEnteredQty())) {
          // below checks if item is regulated
          if (deliveryDocumentHelper.updateVendorCompliance(documentLine)
              && !(Objects.nonNull(instructionRequest.getRegulatedItemType())
                  || Boolean.TRUE.equals(instructionRequest.isVendorComplianceValidated()))) {
            // vendor compliance needed so send back null instruction with fields set
            wfsInstructionUtils.setIsHazmatTrueInDeliveryDocumentLines(
                instructionRequest.getDeliveryDocuments());
            instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
            instructionResponse.setDeliveryStatus(instructionRequest.getDeliveryStatus());
            instructionResponse.setInstruction(null);
            log.info("Returning delivery documents with regulated item fields set");
            return instructionResponse;
          } else {
            // not regulated or already verified, so send qty capture
            instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
            instructionResponse.setDeliveryStatus(instructionRequest.getDeliveryStatus());
            instruction = new Instruction();
            instruction.setInstructionCode(WFSConstants.WFS_QUANTITY_CAPTURE_INSTRUCTION_CODE);
            instructionResponse.setInstruction(instruction);
            log.info(
                "Returning same delivery documents because item is not regulated or already verified");
            return instructionResponse;
          }
        }

        // if lithium or limited qty, populate isHazmat before calling OP
        if (configUtils.isFeatureFlagEnabled(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED)
            && !Boolean.TRUE.equals(documentLine.getIsHazmat())
            && (documentLine.isLimitedQtyVerificationRequired()
                || documentLine.isLithiumIonVerificationRequired())) {
          log.info(
              "Setting isHazmat flag to true by checking limitedQtyVerificationRequired, lithiumIonVerificationRequired");
          documentLine.setIsHazmat(Boolean.TRUE);
        }
      } else {
        // if lithium or limited qty, populate isHazmat before calling OP
        if (configUtils.isFeatureFlagEnabled(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED)
            && !Boolean.TRUE.equals(documentLine.getIsHazmat())
            && (regulatedItemService.isVendorComplianceRequired(documentLine))) {
          log.info("Setting isHazmat flag to true by checking documentLine");
          documentLine.setIsHazmat(Boolean.TRUE);
        }
      }

      setEnteredQtyAndUomInstructionRequest(instructionRequest);

      if (ReceivingConstants.Uom.VNPK.equalsIgnoreCase(instructionRequest.getEnteredQtyUOM())) {
        // In this case, it will be Pallet Receiving Flow
        List<Instruction> instructions =
            createInstructionsForUPCPalletReceiving(instructionRequest, httpHeaders);
        createInstructionResponseForPalletReceiving(instructionResponse, instructions);
        // Answer: No need to complete, we can just manually set the InstructionResponse field and
        // allow it to be returned later.

        // set delivery documents
        instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
      } else {
        // In this case, it will be Normal Item Scan Flow
        instruction = createInstructionForUpcReceiving(instructionRequest, httpHeaders);
        instructionResponse =
            completeInstructionForWFS(instruction, instructionRequest, httpHeaders);
        instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
      }
    }
    instructionResponse.setDeliveryStatus(instructionRequest.getDeliveryStatus());
    return instructionResponse;
  }

  private InstructionResponse createInstructionResponseForSingleDeliveryDocument(
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse,
      Instruction instruction,
      List<DeliveryDocument> deliveryDocuments_gdm) {
    DeliveryDocument deliveryDocument = deliveryDocuments_gdm.get(0);
    instructionRequest.setDeliveryDocuments(Collections.singletonList(deliveryDocument));

    // auto select line
    autoSelectDeliveryDocumentLineIfNeeded(instructionRequest, deliveryDocument);

    DeliveryDocumentLine documentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);

    // Check vendor compliance. If needed, send back the same delivery documents with fields
    // limitedQtyVerificationRequired, lithiumIonVerificationRequired set
    // Here only selected po line is sent back
    if (!deliveryDocumentHelper.updateVendorCompliance(documentLine)) {
      instruction.setInstructionCode(WFSConstants.WFS_QUANTITY_CAPTURE_INSTRUCTION_CODE);
      instructionResponse.setInstruction(instruction);
    } else {
      wfsInstructionUtils.setIsHazmatTrueInDeliveryDocumentLines(
          instructionRequest.getDeliveryDocuments());
      instructionResponse.setInstruction(null);
    }
    instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
    instructionResponse.setDeliveryStatus(instructionRequest.getDeliveryStatus());

    return instructionResponse;
  }

  protected void autoSelectDeliveryDocumentLineIfNeeded(
      InstructionRequest instructionRequest, DeliveryDocument deliveryDocument) {
    if (!CollectionUtils.isEmpty(deliveryDocument.getDeliveryDocumentLines())) {
      if (deliveryDocument.getDeliveryDocumentLines().size() > 1) {
        // Auto select the line
        Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
            defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(
                instructionRequest.getDeliveryDocuments());

        if (Objects.isNull(selectedLine)) {
          selectedLine =
              new Pair<>(deliveryDocument, deliveryDocument.getDeliveryDocumentLines().get(0));
        }

        // Set the selected line for create and complete instruction flow
        DeliveryDocument selectedDeliveryDocument = selectedLine.getKey();
        DeliveryDocumentLine selectedDeliveryDocumentLine = selectedLine.getValue();
        selectedDeliveryDocument.setDeliveryDocumentLines(
            Collections.singletonList(selectedDeliveryDocumentLine));
        instructionRequest.setDeliveryDocuments(
            Collections.singletonList(selectedDeliveryDocument));
      } else {
        // Set the selected line for create and complete instruction flow
        instructionRequest.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
      }
    }
  }

  /**
   * Set fields in <code>InstructionResponse</code> based on requirement in UI to reduce size of the
   * JSON response for > 5 to 7 instructions for Pallet Receiving
   *
   * @param instructionResponse
   * @param instructions
   */
  private void createInstructionResponseForPalletReceiving(
      InstructionResponse instructionResponse, List<Instruction> instructions) {
    instructions.forEach(
        instructionElement -> {
          if (Objects.nonNull(instructionElement.getContainer())) {
            // TODO: find a better way to set all fields except few specific ones of an object to
            // null
            Map<String, String> ctrDestination =
                instructionElement.getContainer().getCtrDestination();
            ctrDestination = wfsInstructionHelperService.mapFCNumberToFCName(ctrDestination);
            ContainerDetails responseContainerDetails = new ContainerDetails();
            responseContainerDetails.setCtrDestination(ctrDestination);
            instructionElement.setContainer(responseContainerDetails);
          }
        });
    instructionResponse.setInstructions(instructions);
    ((InstructionResponseImplNew) instructionResponse).setPrintJob(null);
  }

  private Instruction createWorkStationInstruction(int numCases, String gtin) {
    Instruction workstationInstruction = new Instruction();
    workstationInstruction.setInstructionCode(WFSConstants.WFS_WORKSTATION_INSTRUCTION_CODE);
    workstationInstruction.setProjectedReceiveQty(numCases);
    workstationInstruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    workstationInstruction.setId((long) (1e4 * Math.random())); // random 4 digit instruction id
    workstationInstruction.setGtin(gtin);
    return workstationInstruction;
  }

  public List<Instruction> createInstructionsForUPCPalletReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    // In InstructionRequest, the UoM will be VNPK or ZA -> Pallet receiving flow
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    setOpenQtyInDeliveryDocumentLine(instructionRequest, deliveryDocumentLine);

    // Get Tenant Specific FdeService
    FdeService configuredFdeService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.FDE_SERVICE_BEAN,
            FdeService.class);

    // Setting Threshold for Number of OP calls (number of FC labels) per request
    JsonElement jsonThresholdOPCallsPerRequest =
        configUtils.getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            WFSConstants.MAX_NUMBER_OF_WFS_PALLET_LABELS);

    int thresholdOPCallsPerRequest =
        Objects.nonNull(jsonThresholdOPCallsPerRequest)
            ? jsonThresholdOPCallsPerRequest.getAsInt()
            : WFSConstants.DEFAULT_MAX_NUMBER_OF_WFS_PALLET_LABELS;

    int numberOfOPCalls = 0;
    List<Instruction> instructionList = new ArrayList<>();

    // Now, for looping logic, we will loop until number of cases are zero
    int numCases = instructionRequest.getEnteredQty();
    while (numCases > 0 && numberOfOPCalls < thresholdOPCallsPerRequest) {
      // For numCases, c1, make a call to OP with retry, by creating a container request
      FdeCreateContainerRequest fdeCreateContainerRequest =
          InstructionUtils.createFdeCreateContainerRequestForWFS(
              instructionRequest, deliveryDocument, httpHeaders);

      Instruction instruction =
          InstructionUtils.mapDeliveryDocumentToInstruction(
              deliveryDocument,
              InstructionUtils.mapHttpHeaderToInstruction(
                  httpHeaders, InstructionUtils.createInstruction(instructionRequest)));

      // up to here, instruction, fdeCreateContainerRequest already have messageId
      // (both of which are set from instructionRequest.messageId) (normal item scan flow)
      // (now setting all the messageId and sourceMessageId explicitly, even if already set)

      String newMessageId = UUID.randomUUID().toString();
      instruction.setMessageId(newMessageId);
      instruction.setSourceMessageId(instructionRequest.getMessageId());
      fdeCreateContainerRequest.setMessageId(newMessageId);

      // Set receivingUnit of each item in payload to PALLET
      InstructionUtils.setFdeCreateContainerRequestReceivingUnit(
          fdeCreateContainerRequest, ReceivingConstants.PALLET);

      // Do the call to OP, and process response into Instruction
      String instructionResponse = "";
      try {
        instructionResponse = configuredFdeService.receive(fdeCreateContainerRequest, httpHeaders);
      } catch (ReceivingException receivingException) {
        // TODO: a field in InstructionResponse, to inform the user of number of failed instructions
        // request, and populate that field here
        // in first call if there is exception, then we return singletonList
        if (numberOfOPCalls == 0) {
          log.error(
              "Exception in first FDE Service Call. Exception: {}",
              receivingException.getMessage());
          return Collections.singletonList(
              createWorkStationInstruction(numCases, deliveryDocumentLine.getGtin()));
        } else {
          // in case of at least 1 successful OP call, need to return (at least 1) instructions
          // along with a dummy instruction to build dock tag, with quantity remaining to be
          // allocated and instruction code for WFS Build Dock Tag
          log.info(
              "Exception in FDE Service Call after {} requests. Number of existing instructions: {},  FDE Exception: {}",
              numberOfOPCalls,
              instructionList.size(),
              receivingException.getMessage());
          instructionList.add(
              (createWorkStationInstruction(numCases, instructionList.get(0).getGtin())));
          return instructionList;
        }
      }

      processFDEServiceResponseToInstruction(instructionRequest, instruction, instructionResponse);

      // Add the newest Instruction to instructionsList, and persist it to DB
      instructionList.add(instruction);
      TenantContext.get().setAtlasRcvCompInsSaveStart(System.currentTimeMillis());
      instructionPersisterService.saveInstruction(instruction);
      TenantContext.get().setAtlasRcvCompInsSaveEnd(System.currentTimeMillis());

      numCases = numCases - instruction.getProjectedReceiveQty();
      numberOfOPCalls = numberOfOPCalls + 1;

      // Set the enteredQty to the updated numCases (as it is iteration variable)
      instructionRequest.setEnteredQty(numCases);
    }

    if (numCases > 0 && numberOfOPCalls == thresholdOPCallsPerRequest) {
      instructionList.add((createWorkStationInstruction(numCases, deliveryDocumentLine.getGtin())));
    }
    return instructionList;
  }

  /**
   * Processes FDEService Response string (<code>instructionResponse</code>), and other parameters
   * to set specific fields in the <code>Instruction</code> object (as sent by FDE Service)
   *
   * @param instructionRequest
   * @param instruction
   * @param instructionResponse
   */
  private void processFDEServiceResponseToInstruction(
      InstructionRequest instructionRequest, Instruction instruction, String instructionResponse) {
    FdeCreateContainerResponse fdeCreateContainerResponse =
        gson.fromJson(instructionResponse, FdeCreateContainerResponse.class);
    InstructionUtils.processInstructionResponseForWFS(
        instruction, instructionRequest, fdeCreateContainerResponse);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setProjectedReceiveQty(
        ReceivingUtils.conversionToVendorPack(
            fdeCreateContainerResponse.getProjectedQty(),
            fdeCreateContainerResponse.getProjectedQtyUom(),
            instructionRequest
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getVendorPack(),
            instructionRequest
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getWarehousePack()));
  }

  @Override
  public Instruction createInstructionForUpcReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    setOpenQtyInDeliveryDocumentLine(instructionRequest, deliveryDocumentLine);

    FdeCreateContainerRequest fdeCreateContainerRequest =
        InstructionUtils.createFdeCreateContainerRequestForWFS(
            instructionRequest, deliveryDocument, httpHeaders);
    Instruction instruction =
        InstructionUtils.mapDeliveryDocumentToInstruction(
            deliveryDocument,
            InstructionUtils.mapHttpHeaderToInstruction(
                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));

    instruction.setReceivedQuantity(0);
    instruction.setReceivedQuantityUOM(instructionRequest.getEnteredQtyUOM());
    instruction.setLastChangeUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    String instructionResponse = "";
    try {
      instructionResponse = fdeService.receive(fdeCreateContainerRequest, httpHeaders);
    } catch (ReceivingException receivingException) {
      log.error(
          String.format(
              ReceivingException.FDE_RECEIVE_FDE_CALL_FAILED, receivingException.getMessage()));
      instructionPersisterService.saveInstruction(instruction);
      throw receivingException;
    }

    FdeCreateContainerResponse fdeCreateContainerResponse =
        gson.fromJson(instructionResponse, FdeCreateContainerResponse.class);

    instruction =
        InstructionUtils.processInstructionResponseForWFS(
            instruction, instructionRequest, fdeCreateContainerResponse);
    TenantContext.get().setAtlasRcvCompInsSaveStart(System.currentTimeMillis());
    instruction = instructionPersisterService.saveInstruction(instruction);
    TenantContext.get().setAtlasRcvCompInsSaveEnd(System.currentTimeMillis());

    return instruction;
  }

  /**
   * Sets the <code>OpenQty</code> field in the selected <code>DeliveryDocumentLine</code> Created a
   * method to re-use code as it is being used in UPC Item Scan as well as Pallet Receiving Flow
   *
   * @param instructionRequest
   * @param deliveryDocumentLine
   */
  private void setOpenQtyInDeliveryDocumentLine(
      InstructionRequest instructionRequest, DeliveryDocumentLine deliveryDocumentLine) {
    Pair<Integer, Long> receivedQtyDetails =
        instructionHelperService.getReceivedQtyDetails(
            instructionRequest.getProblemTagId(), deliveryDocumentLine);

    long totalReceivedQty = receivedQtyDetails.getValue();

    deliveryDocumentLine.setOpenQty(
        deliveryDocumentLine.getTotalOrderQty() - (int) totalReceivedQty);
  }

  // TODO Remove this once UI and backend are on same page
  public void setEnteredQtyAndUomInstructionRequest(InstructionRequest instructionRequest) {
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    if (Objects.isNull(instructionRequest.getEnteredQty())) {
      instructionRequest.setEnteredQty(deliveryDocumentLine.getEnteredQty());
    }
    if (Objects.isNull(instructionRequest.getEnteredQtyUOM())) {
      instructionRequest.setEnteredQtyUOM(deliveryDocumentLine.getEnteredQtyUOM());
    }
  }

  /**
   * This method is used to complete the instruction.
   *
   * @param instruction
   * @param instructionRequestFromClient
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public InstructionResponse completeInstructionForWFS(
      Instruction instruction,
      InstructionRequest instructionRequestFromClient,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    try {
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

      instruction.setReceivedQuantity(instruction.getProjectedReceiveQty());

      // process container and add Receipts and complete the instruction
      Map<String, Object> instructionContainerMap =
          wfsInstructionHelperService.createContainersAndReceiptsForWFSPos(
              instructionRequestFromClient, httpHeaders, userId, instruction);

      instruction = (Instruction) instructionContainerMap.get("instruction");
      Container consolidatedContainer = (Container) instructionContainerMap.get("container");

      if (configUtils.isFeatureFlagEnabled(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)) {
        // Create print request
        List<PrintableLabelDataRequest> printableLabelDataRequests = new ArrayList<>();
        List<Map<String, Object>> printRequestList = new ArrayList<>();

        instructionHelperService.createPrintRequestsForParentLabels(instruction, printRequestList);

        // Prepare PrintRequest for label data
        printRequestList.forEach(
            printRequest ->
                printableLabelDataRequests.add(
                    printingAndLabellingService.getPrintableLabelDataRequest(printRequest)));

        // post to labelling
        printingAndLabellingService.postToLabelling(printableLabelDataRequests, httpHeaders);
      }

      // Get consolidated container for  publish receipt
      instructionHelperService.publishConsolidatedContainer(
          consolidatedContainer, httpHeaders, Boolean.TRUE);

      // Get print job and instruction response
      String dcTimeZone = configUtils.getDCTimeZone(TenantContext.getFacilityNum());
      InstructionResponseImplNew instructionResponse =
          (InstructionResponseImplNew)
              wfsInstructionHelperService.prepareWFSInstructionResponse(
                  instruction, consolidatedContainer, dcTimeZone);
      return instructionResponse;

    } catch (Exception e) {
      instructionPersisterService.saveInstructionWithInstructionCodeAsErrorForWFS(instruction);
      log.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE);
    }
  }

  protected List<DeliveryDocument> updateOpenQtyForEachPoPoline(
      List<DeliveryDocument> deliveryDocuments, String uom) {
    Map<String, Long> receivedQtyByPoPolineMap =
        instructionHelperService.getReceivedQtyMapByPOPOL(deliveryDocuments, uom);
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        String key =
            deliveryDocument.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();
        long totalReceivedQty = receivedQtyByPoPolineMap.getOrDefault(key, 0L);
        int openQty = (int) (deliveryDocumentLine.getTotalOrderQty() - totalReceivedQty);
        deliveryDocumentLine.setOpenQty(Math.max(openQty, 0));
        deliveryDocumentLine.setTotalReceivedQty((int) totalReceivedQty);
      }
    }
    return deliveryDocuments;
  }

  /**
   * Prepare and returns create dock tag instruction
   *
   * @return instruction
   */
  public Instruction getDockTagInstruction(
      CreateDockTagRequest createDockTagRequest, String dockTagId, HttpHeaders httpHeaders) {

    Instruction instruction = new Instruction();
    instruction.setMessageId(dockTagId);
    instruction.setDockTagId(dockTagId);
    instruction.setInstructionCode(WFSConstants.WFS_DOCK_TAG_INSTRUCTION_CODE);
    instruction.setInstructionMsg(ReceivingConstants.DOCKTAG_INSTRUCTION_MESSAGE);
    instruction.setProviderId(ReceivingConstants.RECEIVING_PROVIDER_ID);
    instruction.setContainer(
        instructionHelperService.getDockTagContainer(
            createDockTagRequest.getDoorNumber(),
            createDockTagRequest.getDeliveryNumber().toString(),
            dockTagId,
            httpHeaders,
            DockTagType.ATLAS_RECEIVING));

    // passing in labelQty as null, as we don't need QTY label to be touched
    wfsInstructionHelperService.updatePrintJobsInInstructionForWFS(instruction, null);

    instruction.setActivityName(ReceivingConstants.DOCK_TAG);
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setDeliveryNumber(createDockTagRequest.getDeliveryNumber());
    instruction.setPurchaseReferenceNumber("");
    instruction.setPurchaseReferenceLineNumber(null);
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    instruction.setCreateUserId(userId);
    // mark it complete and save
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId(userId);
    return instruction;
  }

  @Override
  public void autoCancelInstruction(Integer facilityNumber) throws ReceivingException {
    JsonElement autoCancelInstructionMinutes =
        tenantSpecificConfigReader.getCcmConfigValue(
            facilityNumber.toString(), ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES);
    int cancelInstructionMinutes =
        nonNull(autoCancelInstructionMinutes)
            ? autoCancelInstructionMinutes.getAsInt()
            : ReceivingConstants.DEFAULT_AUTO_CANCEL_INSTRUCTION_MINUTES;
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.MINUTE, -cancelInstructionMinutes);
    Date fromDate = cal.getTime();
    log.info(
        "AutoCancelInstruction: Auto-cancelling instructions with lastChangeTs After {}", fromDate);
    List<Instruction> instructionList =
        instructionRepository
            .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                fromDate, facilityNumber);
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        WFSConstants.VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID)) {
      // group fetched instructions by sourceMessageId
      Map<String, List<Instruction>> sourceMessageIdInstructionMap =
          instructionList.stream().collect(Collectors.groupingBy(Instruction::getSourceMessageId));
      for (String sourceMessageId : sourceMessageIdInstructionMap.keySet()) {
        // fetch all instructions from db with same sourceMessageId as this.
        List<Instruction> instructionsWithSameSourceMessageId =
            instructionRepository.findAllBySourceMessageId(sourceMessageId);
        if (CollectionUtils.isEmpty(instructionsWithSameSourceMessageId)) {
          log.error("instructionsWithSourceMessageId is empty for {}, continuing", sourceMessageId);
          continue;
        }

        // set correct userId in httpHeaders to send to cancelInstructionsMultiple method
        if (instructionsWithSameSourceMessageId.get(0).getLastChangeUserId() != null) {
          httpHeaders.set(
              ReceivingConstants.USER_ID_HEADER_KEY,
              instructionsWithSameSourceMessageId.get(0).getLastChangeUserId());
        } else {
          httpHeaders.set(
              ReceivingConstants.USER_ID_HEADER_KEY,
              instructionsWithSameSourceMessageId.get(0).getCreateUserId());
        }

        boolean toCancel =
            // if all instructions have null lastChangeTs, then toCancel = True
            (instructionsWithSameSourceMessageId
                    .stream()
                    .allMatch(instruction1 -> Objects.isNull(instruction1.getLastChangeTs()))
                // now, at least one instruction has non-null lastChangeTs
                // so, if all the instructions' lastChangeTs is BEFORE fromDate, then toCancel =
                // True
                || instructionsWithSameSourceMessageId
                    .stream()
                    .filter(instruction -> Objects.nonNull(instruction.getLastChangeTs()))
                    .allMatch(instruction1 -> fromDate.after(instruction1.getLastChangeTs())));

        if (toCancel) {
          MultipleCancelInstructionsRequestBody cancelInstructionsRequestBody =
              new MultipleCancelInstructionsRequestBody();
          cancelInstructionsRequestBody.setInstructionIds(
              sourceMessageIdInstructionMap
                  .get(sourceMessageId)
                  .stream()
                  .map(Instruction::getId)
                  .collect(Collectors.toList()));
          cancelInstructionsMultiple(cancelInstructionsRequestBody, httpHeaders);
        }
      }
    } else {
      // feature flag for validating by sourceMessageId is disabled, so proceed to cancel normally
      for (Instruction instruction : instructionList) {
        if (instruction.getLastChangeUserId() != null) {
          httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, instruction.getLastChangeUserId());
        } else {
          httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, instruction.getCreateUserId());
        }
        cancelInstruction(instruction.getId(), httpHeaders);
      }
    }
  }
}
