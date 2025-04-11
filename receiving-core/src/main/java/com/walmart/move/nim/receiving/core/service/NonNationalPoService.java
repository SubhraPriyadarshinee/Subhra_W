package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.MoveEvent;
import com.walmart.move.nim.receiving.utils.constants.PoType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** @author pcr000m */
@Service
public class NonNationalPoService {

  private static final Logger log = LoggerFactory.getLogger(NonNationalPoService.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private Gson gson;

  @Resource(name = ReceivingConstants.FDE_SERVICE)
  private FdeService fdeService;

  @Autowired private InstructionPersisterService instructionPersisterService;

  @Autowired private InstructionHelperService instructionHelperService;

  @Autowired private ReceiptService receiptService;

  @Autowired private MovePublisher movePublisher;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryServiceImpl deliveryService;

  protected GdmError gdmError;

  /**
   * This method is used to get DSDC instruction from OF
   *
   * @param instructionRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public InstructionResponse createInstructionForNonNationalPoReceiving(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();

    Integer caseQuantity = instructionRequest.getDeliveryDocuments().get(0).getQuantity();
    if (Objects.isNull(caseQuantity) || caseQuantity == 0) {
      getAverageQtyPerPallet(deliveryDocuments);
    }

    Instruction instruction =
        mapDeliveryDocumentToInstruction(
            deliveryDocuments,
            InstructionUtils.mapHttpHeaderToInstruction(
                httpHeaders, InstructionUtils.createInstruction(instructionRequest)));

    instruction.setOriginalChannel(deliveryDocuments.get(0).getOriginalFreightType());

    FdeCreateContainerRequest fdeCreateContainerRequest =
        InstructionUtils.populateCreateContainerRequestForNonNationalPOs(
            instructionRequest, httpHeaders);
    String instructionResponse;
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
        InstructionUtils.processInstructionResponse(
            instruction, instructionRequest, fdeCreateContainerResponse);
    instructionHelperService.publishInstruction(
        instruction, null, null, null, InstructionStatus.CREATED, httpHeaders);

    return updateAndCompleteInstructionForNonNationalReceiving(
        instruction, instructionRequest, httpHeaders);
  }

  /**
   * This method is used to set PurchaseReference number as -1
   *
   * @param deliveryDocuments
   * @param instruction
   * @return
   */
  private Instruction mapDeliveryDocumentToInstruction(
      List<DeliveryDocument> deliveryDocuments, Instruction instruction) {
    instruction.setPoDcNumber(deliveryDocuments.get(0).getPoDCNumber());
    instruction.setPurchaseReferenceNumber(ReceivingConstants.DUMMY_PURCHASE_REF_NUMBER);
    instruction.setDeliveryDocument(gson.toJson(deliveryDocuments));
    return instruction;
  }

  /**
   * This method is used to update and complete the instruction.
   *
   * @param instruction
   * @param instructionRequestFromClient
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  private InstructionResponse updateAndCompleteInstructionForNonNationalReceiving(
      Instruction instruction,
      InstructionRequest instructionRequestFromClient,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    try {
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      Integer totalQuantityToBeReceived = getTotalQtyToBeReceived(instructionRequestFromClient);
      Integer receivedQuantity = instruction.getReceivedQuantity();

      // process container and add Receipts and complete the instruction
      Map<String, Object> instructionContainerMap =
          instructionPersisterService.createContainersAndReceiptsForNonNationalPos(
              instructionRequestFromClient,
              httpHeaders,
              userId,
              instruction,
              totalQuantityToBeReceived,
              receivedQuantity);

      // publish update instruction to WFM
      instructionHelperService.publishInstruction(
          instruction,
          InstructionUtils.prepareRequestForASNInstructionUpdate(),
          totalQuantityToBeReceived,
          null,
          InstructionStatus.UPDATED,
          httpHeaders);

      instruction = (Instruction) instructionContainerMap.get("instruction");
      Container consolidatedContainer = (Container) instructionContainerMap.get("container");
      // Get consolidated container for  publish receipt
      instructionHelperService.publishConsolidatedContainer(
          consolidatedContainer, httpHeaders, Boolean.TRUE);

      // Publish move.
      if (instruction.getMove() != null && !instruction.getMove().isEmpty()) {
        if (tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.MOVE_DEST_BU_ENABLED)
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

      // Publishing instruction to WFM.
      instructionHelperService.publishInstruction(
          instruction, null, null, consolidatedContainer, InstructionStatus.COMPLETED, httpHeaders);

      if (Objects.nonNull(
              instructionRequestFromClient.getDeliveryDocuments().get(0).getEnteredPalletQty())
          && instructionRequestFromClient.getDeliveryDocuments().get(0).getEnteredPalletQty() > 0) {
        instruction.setReceivedQuantity(-1);
      }

      // Get print job and instruction response
      InstructionResponse instructionResponse =
          instructionHelperService.prepareInstructionResponse(
              instruction, consolidatedContainer, receivedQuantity, null);

      instructionResponse.getInstruction().setReceivedQuantity(totalQuantityToBeReceived);

      return instructionResponse;

    } catch (Exception e) {
      instructionPersisterService.saveInstructionWithInstructionCodeAsNull(instruction);
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

  /**
   * This method will return total quantity to received
   *
   * @param instructionRequestFromClient
   * @return
   */
  private Integer getTotalQtyToBeReceived(InstructionRequest instructionRequestFromClient) {
    List<DeliveryDocument> deliveryDocuments = instructionRequestFromClient.getDeliveryDocuments();

    Integer totalQuantityToBeReceived = 0;

    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      totalQuantityToBeReceived += deliveryDocument.getQuantity();
    }
    return totalQuantityToBeReceived;
  }

  private void getAverageQtyPerPallet(List<DeliveryDocument> deliveryDocuments) {
    for (DeliveryDocument deliveryDocument : deliveryDocuments) {
      int averageQty =
          deliveryDocument.getTotalPurchaseReferenceQty()
              / Math.max(deliveryDocument.getPalletQty(), 1);
      averageQty = Math.max(averageQty, 1);
      deliveryDocument.setQuantity(averageQty);
    }
  }

  /**
   * This method is used to serve NonNational request
   *
   * @param instructionRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public InstructionResponse serveNonNationalPoRequest(
      InstructionRequest instructionRequest, HttpHeaders httpHeaders) throws ReceivingException {

    String deliveryDocumentString;
    List<Long> deliveryNumbers = new ArrayList<>();
    List<String> channelType = new ArrayList<>();
    deliveryNumbers.add(Long.parseLong(instructionRequest.getDeliveryNumber()));
    if (Objects.nonNull(instructionRequest.getIsDSDC())
        && Boolean.TRUE.equals(instructionRequest.getIsDSDC())) {
      channelType.add(ReceivingConstants.DSDC_ACTIVITY_NAME);
    } else {
      channelType.add(ReceivingConstants.POCON_ACTIVITY_NAME);
    }
    deliveryDocumentString =
        deliveryService.getDeliveryDocumentByPOChannelType(
            deliveryNumbers, channelType, httpHeaders);

    DeliveryList deliveries = gson.fromJson(deliveryDocumentString, DeliveryList.class);
    if (Objects.nonNull(deliveries.getData()) && deliveries.getData().size() == 0) {
      gdmError = GdmErrorCode.getErrorValue(ReceivingException.PO_POL_NOT_FOUND_ERROR);
      String errorMessage =
          String.format(gdmError.getErrorMessage(), instructionRequest.getDeliveryNumber());
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(errorMessage)
              .errorCode(gdmError.getErrorCode())
              .errorHeader(gdmError.getLocalisedErrorHeader())
              .errorKey(ExceptionCodes.PO_POL_NOT_FOUND_ERROR)
              .values(new Object[] {instructionRequest.getDeliveryNumber()})
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.NOT_FOUND)
          .errorResponse(errorResponse)
          .build();
    }
    return nonNationalPoInstructionResponseToUI(
        deliveries.getData().get(0), instructionRequest, httpHeaders);
  }

  /**
   * This method is used to send DSDC repsonse to UI according v2 contract.
   *
   * @param delivery
   * @param instructionRequest
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public InstructionResponse nonNationalPoInstructionResponseToUI(
      Delivery delivery, InstructionRequest instructionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    InstructionResponseImplNew instructionResponse = new InstructionResponseImplNew();

    if (instructionRequest.getDeliveryStatus().equalsIgnoreCase(DeliveryStatus.OPN.toString()))
      instructionResponse.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    else instructionResponse.setDeliveryStatus(instructionRequest.getDeliveryStatus());

    instructionResponse.setInstruction(null);

    if (instructionRequest.getDeliveryDocuments() == null
        || instructionRequest.getDeliveryDocuments().isEmpty()) {
      instructionResponse.setPrintJob(null);
      instructionResponse.setDeliveryDocuments(getDeliveryDocuments(delivery));
      return instructionResponse;
    }

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED)) {
      // Find out if payload contains delivery documents with received quantities.
      // If not, it means the new flow of the API has been called.
      // This is to ensure backward compatibility with clients running old app
      boolean isPalletQtyAndCaseQtyPresent = true;
      List<DeliveryDocument> deliveryDocumentsFromRequest =
          instructionRequest.getDeliveryDocuments();
      Set<String> purchaseReferenceNumbers =
          deliveryDocumentsFromRequest
              .stream()
              .map(DeliveryDocument::getPurchaseReferenceNumber)
              .collect(Collectors.toSet());
      Map<String, Pair<Long, Long>> poPalletCaseQtyMap =
          receiptService.getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(
              purchaseReferenceNumbers, delivery.getDeliveryNumber());
      for (DeliveryDocument deliveryDocument : deliveryDocumentsFromRequest) {
        if (isPalletQtyAndCaseQtyPresent
            && (Objects.isNull(deliveryDocument.getReceivedPalletCount())
                && Objects.isNull(deliveryDocument.getReceivedCaseCount()))) {
          isPalletQtyAndCaseQtyPresent = false;
          populateReceivedCaseAndPalletQtyInDeliveryDoc(poPalletCaseQtyMap, deliveryDocument, true);
        }
      }

      if (!isPalletQtyAndCaseQtyPresent) {
        instructionResponse.setPrintJob(null);
        instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
        return instructionResponse;
      }
    }

    InstructionRequest v2InstructionRequest = new InstructionRequest();
    Map<String, List<Integer>> selectedPOs = new HashMap<>();
    List<DeliveryDocument> v2DeliveryDocuments = new ArrayList<>();

    for (DeliveryDocument deliveryDocument : instructionRequest.getDeliveryDocuments()) {
      List<Integer> caseAndPalletQty = new ArrayList<>();
      caseAndPalletQty.add(deliveryDocument.getQuantity());
      caseAndPalletQty.add(deliveryDocument.getEnteredPalletQty());
      selectedPOs.put(deliveryDocument.getPurchaseReferenceNumber(), caseAndPalletQty);
    }
    v2InstructionRequest.setMessageId(instructionRequest.getMessageId());

    if (instructionRequest.getDeliveryStatus().equalsIgnoreCase(DeliveryStatus.OPN.toString()))
      v2InstructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    else v2InstructionRequest.setDeliveryStatus(instructionRequest.getDeliveryStatus());

    v2InstructionRequest.setDeliveryNumber(instructionRequest.getDeliveryNumber());
    v2InstructionRequest.setDoorNumber(instructionRequest.getDoorNumber());
    v2InstructionRequest.setNonNationPo(instructionRequest.getNonNationPo());

    // TODO: check if handlingCode exists in API Response, add cxblock check here
    v2InstructionRequest.setDeliveryDocuments(
        mapGDMV3POResponseToV2(delivery, selectedPOs, v2DeliveryDocuments));
    return createInstructionForNonNationalPoReceiving(v2InstructionRequest, httpHeaders);
  }

  private void populateReceivedCaseAndPalletQtyInDeliveryDoc(
      Map<String, Pair<Long, Long>> poPalletCaseQtyMap,
      DeliveryDocument deliveryDocument,
      boolean shouldSetDefaultQty) {
    Pair<Long, Long> receivedQtyAndPalletQty =
        poPalletCaseQtyMap.get(deliveryDocument.getPurchaseReferenceNumber());

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED)
        && !shouldSetDefaultQty
        && Objects.isNull(receivedQtyAndPalletQty)) {
      deliveryDocument.setReceivedCaseCount(null);
      deliveryDocument.setReceivedPalletCount(null);
    } else {
      deliveryDocument.setReceivedCaseCount(
          Objects.nonNull(receivedQtyAndPalletQty)
                  && Objects.nonNull(receivedQtyAndPalletQty.getKey())
              ? receivedQtyAndPalletQty.getKey()
              : 0L);

      int palletQty =
          Objects.nonNull(receivedQtyAndPalletQty)
                  && Objects.nonNull(receivedQtyAndPalletQty.getValue())
              ? receivedQtyAndPalletQty.getValue().intValue()
              : 0;
      deliveryDocument.setReceivedPalletCount(palletQty);
    }
  }

  /**
   * This method is used to return delivery documents
   *
   * @param delivery
   * @return
   * @throws ReceivingException
   */
  private List<DeliveryDocument> getDeliveryDocuments(Delivery delivery) {

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    Set<String> purchaseReferenceNumbers =
        delivery
            .getPurchaseOrders()
            .stream()
            .map(PurchaseOrder::getPoNumber)
            .collect(Collectors.toSet());
    Map<String, Pair<Long, Long>> poPalletCaseQtyMap = new HashMap<>();
    if (!tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED)) {
      poPalletCaseQtyMap =
          receiptService.getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(
              purchaseReferenceNumbers, delivery.getDeliveryNumber());
    }
    for (PurchaseOrder purchaseOrder : delivery.getPurchaseOrders()) {

      DeliveryDocument deliveryDocument = new DeliveryDocument();
      deliveryDocument.setPurchaseReferenceNumber(purchaseOrder.getPoNumber());
      deliveryDocument.setPurchaseReferenceLegacyType(purchaseOrder.getLegacyType());
      deliveryDocument.setPoDCNumber(purchaseOrder.getPoDcNumber());
      deliveryDocument.setPurchaseReferenceStatus(purchaseOrder.getPoStatus());
      deliveryDocument.setDeliveryDocumentLines(new ArrayList<>());
      deliveryDocument.setTotalPurchaseReferenceQty(purchaseOrder.getFreightBillQty());
      deliveryDocument.setTotalBolFbq(purchaseOrder.getFreightBillQty());

      if ((purchaseOrder.getLines().get(0).getChannel())
          .equalsIgnoreCase(ReceivingConstants.POCON_ACTIVITY_NAME)) {
        deliveryDocument.setOriginalFreightType(
            InstructionUtils.getOriginalChannelMethod(
                purchaseOrder.getLines().get(0).getOriginalChannel()));
      } else {
        deliveryDocument.setOriginalFreightType(PoType.CROSSU.getpoType());
      }

      if (!tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED)) {
        populateReceivedCaseAndPalletQtyInDeliveryDoc(poPalletCaseQtyMap, deliveryDocument, false);
      }

      deliveryDocument.setPalletQty(
          Objects.isNull(purchaseOrder.getPalletQty()) ? 0 : purchaseOrder.getPalletQty());
      deliveryDocument.setDeliveryNumber(delivery.getDeliveryNumber());
      deliveryDocument.setStateReasonCodes(delivery.getStatusInformation().getStatusReasonCode());
      deliveryDocument.setDeliveryStatus(
          DeliveryStatus.valueOf(delivery.getStatusInformation().getStatus()));
      deliveryDocuments.add(deliveryDocument);
    }
    return deliveryDocuments;
  }

  /**
   * This method is used to map GDM V3 properties to v2 properties
   *
   * @param delivery
   * @param selectedPOs
   * @param v2DeliveryDocuments
   * @return
   */
  private List<DeliveryDocument> mapGDMV3POResponseToV2(
      Delivery delivery,
      Map<String, List<Integer>> selectedPOs,
      List<DeliveryDocument> v2DeliveryDocuments)
      throws ReceivingException {
    Set<String> purchaseReferenceNumbers =
        delivery
            .getPurchaseOrders()
            .stream()
            .map(PurchaseOrder::getPoNumber)
            .collect(Collectors.toSet());
    Map<String, Pair<Long, Long>> poPalletCaseQtyMap =
        receiptService.getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(
            purchaseReferenceNumbers, delivery.getDeliveryNumber());
    for (PurchaseOrder purchaseOrder : delivery.getPurchaseOrders()) {

      if (selectedPOs.containsKey(purchaseOrder.getPoNumber())) {
        if (purchaseOrder.getCube() == null
            || purchaseOrder.getWeight() == null
            || purchaseOrder.getCube().getQuantity() == null
            || purchaseOrder.getCube().getUom() == null
            || purchaseOrder.getWeight().getQuantity() == null
            || purchaseOrder.getWeight().getUom() == null) {

          GdmError gdmError =
              GdmErrorCode.getErrorValue(ReceivingException.MISSING_DSDC_INFO_ERROR);
          log.error("CubeQty, weight and UOM are mandatory information for DSDC request");
          throw new ReceivingException(
              gdmError.getErrorMessage(),
              HttpStatus.INTERNAL_SERVER_ERROR,
              gdmError.getErrorCode(),
              gdmError.getErrorHeader());
        }
        DeliveryDocument v2DeliveryDocument = new DeliveryDocument();
        v2DeliveryDocument.setQuantity(selectedPOs.get(purchaseOrder.getPoNumber()).get(0));
        v2DeliveryDocument.setEnteredPalletQty(selectedPOs.get(purchaseOrder.getPoNumber()).get(1));
        v2DeliveryDocument.setPurchaseReferenceNumber(purchaseOrder.getPoNumber());
        v2DeliveryDocument.setFinancialReportingGroup(purchaseOrder.getFinancialGroupCode());
        v2DeliveryDocument.setBaseDivisionCode(purchaseOrder.getBaseDivisionCode());
        if (Objects.nonNull(purchaseOrder.getVendor())) {
          v2DeliveryDocument.setVendorNumber(
              Objects.nonNull(purchaseOrder.getVendor().getNumber())
                  ? purchaseOrder.getVendor().getNumber().toString()
                  : null);
          v2DeliveryDocument.setDeptNumber(
              Objects.nonNull(purchaseOrder.getVendor().getDepartment())
                  ? purchaseOrder.getVendor().getDepartment().toString()
                  : null);
        }
        v2DeliveryDocument.setPurchaseCompanyId(purchaseOrder.getPurchaseCompanyId().toString());
        v2DeliveryDocument.setPurchaseReferenceLegacyType(purchaseOrder.getLegacyType());
        v2DeliveryDocument.setPoDCNumber(purchaseOrder.getPoDcNumber());
        v2DeliveryDocument.setPurchaseReferenceStatus(purchaseOrder.getPoStatus());
        v2DeliveryDocument.setDeliveryDocumentLines(null);
        v2DeliveryDocument.setTotalPurchaseReferenceQty(purchaseOrder.getFreightBillQty());
        v2DeliveryDocument.setWeight(purchaseOrder.getWeight().getQuantity());
        v2DeliveryDocument.setWeightUOM(purchaseOrder.getWeight().getUom());
        v2DeliveryDocument.setCubeQty(purchaseOrder.getCube().getQuantity());
        v2DeliveryDocument.setCubeUOM(purchaseOrder.getCube().getUom());
        v2DeliveryDocument.setFreightTermCode(purchaseOrder.getFreightTermCode());
        v2DeliveryDocument.setTotalBolFbq(purchaseOrder.getTotalBolFbq());
        v2DeliveryDocument.setPoTypeCode(purchaseOrder.getPoTypeCode());
        v2DeliveryDocument.setDeliveryNumber(delivery.getDeliveryNumber());
        if (!Objects.isNull(delivery.getStatusInformation())) {
          if (!StringUtils.isEmpty(delivery.getStatusInformation().getStatus()))
            v2DeliveryDocument.setDeliveryStatus(
                DeliveryStatus.valueOf(delivery.getStatusInformation().getStatus()));
          v2DeliveryDocument.setStateReasonCodes(
              delivery.getStatusInformation().getStatusReasonCode());
        }
        if ((purchaseOrder.getLines().get(0).getChannel())
            .equalsIgnoreCase(ReceivingConstants.POCON_ACTIVITY_NAME)) {
          v2DeliveryDocument.setOriginalFreightType(
              InstructionUtils.getOriginalChannelMethod(
                  purchaseOrder.getLines().get(0).getOriginalChannel()));
        } else {
          v2DeliveryDocument.setOriginalFreightType(PoType.CROSSU.getpoType());
        }

        populateReceivedCaseAndPalletQtyInDeliveryDoc(poPalletCaseQtyMap, v2DeliveryDocument, true);

        // if 1, average qty per pallet becomes = case count
        int expectedPalletQty =
            Objects.isNull(purchaseOrder.getPalletQty()) ? 0 : purchaseOrder.getPalletQty();
        v2DeliveryDocument.setPalletQty(expectedPalletQty);

        v2DeliveryDocuments.add(v2DeliveryDocument);
      }
    }
    return v2DeliveryDocuments;
  }
}
