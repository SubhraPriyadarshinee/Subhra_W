package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.core.common.InventoryLabelType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.MirageExceptionRequest;
import com.walmart.move.nim.receiving.rdc.model.MirageLpnExceptionErrorResponse;
import com.walmart.move.nim.receiving.rdc.model.RdcExceptionMsg;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
public class RdcExceptionReceivingService {
  private static final Logger logger = LoggerFactory.getLogger(RdcExceptionReceivingService.class);
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private RdcReceivingUtils rdcReceivingUtils;
  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private LabelDataService labelDataService;
  @Autowired private ContainerService containerService;
  @Autowired private InstructionService instructionService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private NimRdsService nimRdsService;

  public InstructionResponse processExceptionLabel(String containerLabel) {
    if (containerLabel.length() == PRE_LABEL_FREIGHT_LENGTH_16) {
      return processExceptionForPreLabelFreight(
          containerLabel,
          PRE_LABEL_FREIGHT_DESTINATION_DC_NUMBER_END_INDEX,
          PRE_LABEL_FREIGHT_SOURCE_DC_NUMBER_START_INDEX,
          PRE_LABEL_FREIGHT_SOURCE_DC_NUMBER_END_INDEX);
    } else if (containerLabel.length() == RECEIVED_RDC_LABEL_LENGTH_18) {
      return processExceptionForPreLabelFreight(
          containerLabel,
          RECEIVED_RDC_LABEL_DESTINATION_DC_NUMBER_END_INDEX,
          RECEIVED_RDC_LABEL_SOURCE_DC_NUMBER_START_INDEX,
          RECEIVED_RDC_LABEL_SOURCE_DC_NUMBER_END_INDEX);
    } else if (containerLabel.length() == RECEIVED_RDC_LABEL_LENGTH_25
        && isOfflineLabel(containerLabel)) {
      return buildInstructionResponse(ReceivingConstants.MATCH_FOUND, null);
    }
    return new InstructionResponseImplException();
  }

  /**
   * Processes Exception label for PreLabel (16 digit and 18 digit) freight
   *
   * @param containerLabel
   * @param destinationEndIndex
   * @param sourceStartIndex
   * @param sourceEndIndex
   * @return
   */
  private InstructionResponse processExceptionForPreLabelFreight(
      String containerLabel, int destinationEndIndex, int sourceStartIndex, int sourceEndIndex) {
    int destinationDcNumber = 0;
    int sourceDcNumber = 0;
    destinationDcNumber =
        Integer.parseInt(
            containerLabel.substring(DESTINATION_DC_NUMBER_START_INDEX, destinationEndIndex));
    sourceDcNumber = Integer.parseInt(containerLabel.substring(sourceStartIndex, sourceEndIndex));
    if (rdcManagedConfig
        .getPreLabelFreightSourceSites()
        .stream()
        .anyMatch(Arrays.asList(destinationDcNumber, sourceDcNumber)::contains)) {
      return buildInstructionResponse(ReceivingConstants.MATCH_FOUND, null);
    }
    logger.error(
        ExceptionCodes.MIRAGE_EXCEPTION_ERROR_INVALID_BARCODE,
        ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE);
    throw new ReceivingBadDataException(
        ExceptionCodes.MIRAGE_EXCEPTION_ERROR_INVALID_BARCODE,
        ReceivingConstants.MIRAGE_INVALID_BARCODE_ERROR_MSG);
  }

  /**
   * Checks if the label is Offline label (XDK1, XDK2 labelType)
   *
   * @param lpn
   * @return
   */
  private boolean isOfflineLabel(String lpn) {
    List<String> offlineLabels =
        Arrays.asList(InventoryLabelType.XDK1.name(), InventoryLabelType.XDK2.name());
    LabelData labelData = labelDataService.findByTrackingIdAndLabelIn(lpn, offlineLabels);
    return Objects.nonNull(labelData);
  }

  public List<DeliveryDocument> fetchDeliveryDocumentsFromGDM(
      ReceiveExceptionRequest receiveExceptionRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments =
        deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            receiveExceptionRequest.getDeliveryNumbers().get(0),
            receiveExceptionRequest.getItemNumber(),
            httpHeaders);
    boolean isSSTKAutomationEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
            false);
    if (isSSTKAutomationEnabled) {
      List<DeliveryDocument> filteredSSTKDeliveryDocuments =
          rdcInstructionUtils.filterSSTKDeliveryDocuments(deliveryDocuments);
      if (deliveryDocuments.size() == filteredSSTKDeliveryDocuments.size()) {
        return Collections.emptyList();
      }
    }
    List<DeliveryDocument> filteredDADeliveryDocuments =
        rdcInstructionUtils.getDADeliveryDocumentsFromGDMDeliveryDocuments(deliveryDocuments);
    rdcReceivingUtils.updateQuantitiesBasedOnUOM(filteredDADeliveryDocuments);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
        false)) {
      rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(filteredDADeliveryDocuments);
    } else {
      nimRdsService.updateAdditionalItemDetails(filteredDADeliveryDocuments, httpHeaders);
    }
    rdcReceivingUtils.overridePackTypeCodeForBreakPackItem(
        filteredDADeliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    rdcInstructionUtils.checkAtlasConvertedItemForDa(filteredDADeliveryDocuments, httpHeaders);
    return filteredDADeliveryDocuments;
  }

  public ReceiveInstructionRequest getReceiveInstructionRequest(
      ReceiveExceptionRequest receiveExceptionRequest, List<DeliveryDocument> deliveryDocuments) {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setDeliveryNumber(deliveryDocuments.get(0).getDeliveryNumber());
    receiveInstructionRequest.setDoorNumber(receiveExceptionRequest.getDoorNumber());
    receiveInstructionRequest.setQuantity(EXCEPTION_RECEIVING_CASE_QUANTITY);
    receiveInstructionRequest.setIsLessThanCase(Boolean.FALSE);
    return receiveInstructionRequest;
  }

  public MirageExceptionRequest getMirageExceptionRequest(
      ReceiveExceptionRequest receiveExceptionRequest) {
    return MirageExceptionRequest.builder()
        .aclErrorString(receiveExceptionRequest.getExceptionMessage())
        .lpn(receiveExceptionRequest.getLpns().get(0))
        .itemNbr(
            Objects.nonNull(receiveExceptionRequest.getItemNumber())
                ? String.valueOf(receiveExceptionRequest.getItemNumber())
                : null)
        .groupNbr(
            CollectionUtils.isEmpty(receiveExceptionRequest.getDeliveryNumbers())
                ? null
                : receiveExceptionRequest.getDeliveryNumbers())
        .tokenId(receiveExceptionRequest.getTokenId())
        .printerNbr(receiveExceptionRequest.getPrinterNumber())
        .build();
  }

  public InstructionResponse buildInstructionResponse(
      String instructionType, List<DeliveryDocument> deliveryDocuments) {
    InstructionResponseImplException instructionResponse = new InstructionResponseImplException();

    Instruction instruction = buildInstruction(instructionType);
    logger.info(
        "Exception Received: {} Instruction Code / Message: InstructionCode: {} InstructionMsg: {}",
        instructionType,
        instruction.getInstructionCode(),
        instruction.getInstructionMsg());
    ExceptionInstructionMsg exceptionInstructionMsg = new ExceptionInstructionMsg();
    exceptionInstructionMsg.setDescription(
        RdcExceptionMsg.valueOf(instructionType).getDescription());
    exceptionInstructionMsg.setTitle(RdcExceptionMsg.valueOf(instructionType).getTitle());
    exceptionInstructionMsg.setInfo(RdcExceptionMsg.valueOf(instructionType).getInfo());
    instructionResponse.setInstruction(instruction);
    instructionResponse.setExceptionInstructionMsg(exceptionInstructionMsg);
    logger.info(
        "Exception Received: {} ExceptionInstructionMsg to Client: Title: {} Description: {} Info:{}",
        instructionType,
        exceptionInstructionMsg.getTitle(),
        exceptionInstructionMsg.getDescription(),
        exceptionInstructionMsg.getInfo());

    if (Objects.nonNull(deliveryDocuments)) {
      instructionResponse.setDeliveryDocuments(deliveryDocuments);
    }
    return instructionResponse;
  }

  public InstructionResponse parseMirageExceptionErrorResponse(
      ReceiveExceptionRequest receiveExceptionRequest,
      MirageLpnExceptionErrorResponse mirageLpnExceptionErrorResponse) {
    if (mirageLpnExceptionErrorResponse.getCode().equals(ERROR_INVALID_BARCODE)) {
      logger.error(
          ExceptionCodes.MIRAGE_EXCEPTION_ERROR_INVALID_BARCODE,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE);
      throw new ReceivingBadDataException(
          ExceptionCodes.MIRAGE_EXCEPTION_ERROR_INVALID_BARCODE,
          ReceivingConstants.MIRAGE_INVALID_BARCODE_ERROR_MSG);
    }
    String instructionType = mirageLpnExceptionErrorResponse.getCode();
    if (Objects.nonNull(receiveExceptionRequest.getExceptionMessage())
        && instructionType.equals(ERROR_LPN_NOT_FOUND)) {
      instructionType = EXCEPTION_LPN_NOT_FOUND;
    }
    return buildInstructionResponse(instructionType, null);
  }

  public Instruction buildInstruction(String instructionType) {
    Instruction instruction = new Instruction();
    RdcInstructionType rdcInstructionType = EXCEPTION_INSTRUCTION_TYPE_MAP.get(instructionType);
    instruction.setInstructionCode(rdcInstructionType.getInstructionCode());
    instruction.setInstructionMsg(rdcInstructionType.getInstructionMsg());
    return instruction;
  }

  public InstructionRequest getInstructionRequest(
      ReceiveExceptionRequest receiveExceptionRequest, List<DeliveryDocument> deliveryDocuments) {
    InstructionRequest instructionRequest = new InstructionRequest();
    if (!CollectionUtils.isEmpty(deliveryDocuments)) {
      instructionRequest.setDeliveryStatus(deliveryDocuments.get(0).getDeliveryStatus().toString());
      instructionRequest.setDeliveryNumber(
          String.valueOf(deliveryDocuments.get(0).getDeliveryNumber()));
      instructionRequest.setVendorComplianceValidated(
          receiveExceptionRequest.isVendorComplianceValidated());
      instructionRequest.setDeliveryDocuments(deliveryDocuments);
    }
    instructionRequest.setMessageId(receiveExceptionRequest.getMessageId());
    instructionRequest.setDoorNumber(receiveExceptionRequest.getDoorNumber());
    if (Objects.nonNull(receiveExceptionRequest.getUpcNumber())) {
      instructionRequest.setUpcNumber(receiveExceptionRequest.getUpcNumber());
      instructionRequest.setReceivingType(ReceivingConstants.UPC);
    }
    return instructionRequest;
  }

  /**
   * * This method will fetch all deliverydocuments matching multiple deliveries with UPC for
   * exceptions NO_BARCODE_SEEN, NO_UPC_FOUND as well as Delivery Search API
   *
   * @param deliveryNumbers
   * @param instructionRequest
   * @param httpHeaders
   * @return
   */
  public List<DeliveryDocument> fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
      List<String> deliveryNumbers,
      InstructionRequest instructionRequest,
      HttpHeaders httpHeaders) {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    for (String delivery : deliveryNumbers) {
      instructionRequest.setDeliveryNumber(delivery);
      DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      try {
        List<DeliveryDocument> deliveryDocuments =
            deliveryDocumentsSearchHandler.fetchDeliveryDocument(instructionRequest, httpHeaders);
        if (!CollectionUtils.isEmpty(deliveryDocuments)) {
          deliveryDocuments =
              rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
                  deliveryDocuments, instructionRequest);
        }
        deliveryDocumentList.addAll(deliveryDocuments);
      } catch (Exception e) {
        // Ignore error in case match not found / no active PO lines / delivery status not
        // receivable
        logger.info("Error message {}", e.getMessage());
      }
    }
    if (CollectionUtils.isEmpty(deliveryDocumentList)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.UPC_MATCH_NOT_FOUND,
          String.format(UPC_MATCH_NOT_FOUND, instructionRequest.getUpcNumber()));
    }
    boolean isSSTKAutomationEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
            false);
    if (isSSTKAutomationEnabled) {
      List<DeliveryDocument> filteredSSTKDeliveryDocuments =
          rdcInstructionUtils.filterSSTKDeliveryDocuments(deliveryDocumentList);
      if (deliveryDocumentList.size() == filteredSSTKDeliveryDocuments.size()) {
        return Collections.emptyList();
      }
    }
    List<DeliveryDocument> filteredDADeliveryDocuments =
        rdcInstructionUtils.getDADeliveryDocumentsFromGDMDeliveryDocuments(deliveryDocumentList);
    rdcReceivingUtils.updateQuantitiesBasedOnUOM(filteredDADeliveryDocuments);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
        false)) {
      rdcInstructionUtils.updateAdditionalItemDetailsFromGDM(filteredDADeliveryDocuments);
    } else {
      nimRdsService.updateAdditionalItemDetails(filteredDADeliveryDocuments, httpHeaders);
    }
    rdcReceivingUtils.overridePackTypeCodeForBreakPackItem(
        filteredDADeliveryDocuments.get(0).getDeliveryDocumentLines().get(0));
    try {
      rdcInstructionUtils.checkAtlasConvertedItemForDa(filteredDADeliveryDocuments, httpHeaders);
    } catch (Exception e) {
      // TODO: Need to revisit this exception
      logger.info("Error reaching item-config.. ignoring now");
    }
    rdcInstructionUtils.populateOpenAndReceivedQtyInDeliveryDocuments(
        filteredDADeliveryDocuments, httpHeaders, instructionRequest.getUpcNumber());
    return filteredDADeliveryDocuments;
  }

  public Boolean validateBreakPack(List<DeliveryDocument> deliveryDocuments) {
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    boolean isBreakPackConveyPicks = RdcUtils.isBreakPackConveyPicks(deliveryDocumentLine);
    boolean isBreakPackItem =
        deliveryDocumentLine.getAdditionalInfo().getPackTypeCode().equals("B")
            || (!deliveryDocumentLine
                .getWarehousePack()
                .equals(deliveryDocumentLine.getVendorPack()));
    boolean isMasterBreakPack =
        deliveryDocumentLine
            .getAdditionalInfo()
            .getPackTypeCode()
            .equals(RdcConstants.MASTER_BREAK_PACK_TYPE_CODE);
    return (isBreakPackConveyPicks || isMasterBreakPack || isBreakPackItem);
  }

  /**
   * * This method will update the storeNumber of the instruction and change the container status
   *
   * @param container
   * @param locationName
   * @param userId
   * @return printJob
   */
  @Transactional
  public Map<String, Object> getPrintRequestPayLoadForShippingLabel(
      Container container, String locationName, String userId) throws ReceivingException {
    Map<String, Object> printJob = new HashMap<>();
    Instruction instruction = null;
    if (!CollectionUtils.isEmpty(container.getDestination())
        && Objects.nonNull(container.getDestination().get(ReceivingConstants.BU_NUMBER))) {
      String storeNumber = container.getDestination().get(ReceivingConstants.BU_NUMBER);
      if (Objects.nonNull(container.getInstructionId())) {
        instruction = instructionService.getInstructionById(container.getInstructionId());
        if (Objects.nonNull(instruction)
            && Objects.nonNull(instruction.getContainer())
            && Objects.nonNull(instruction.getContainer().getCtrLabel())) {
          printJob = instruction.getContainer().getCtrLabel();
          List<Map<String, Object>> printLabelRequests =
              (List<Map<String, Object>>) printJob.get(PRINT_REQUEST_KEY);
          updateStoreNumberInPrintRequest(
              printLabelRequests, container.getTrackingId(), storeNumber);
          updateContainerAndInstructionForShippingLabel(
              container, locationName, userId, instruction, printJob);
        }
      }
    }
    return printJob;
  }

  /**
   * This method is to update the store number in printRequest for Routing Label
   *
   * @param printLabelRequests
   * @param trackingId
   * @param storeNumber
   */
  private void updateStoreNumberInPrintRequest(
      List<Map<String, Object>> printLabelRequests, String trackingId, String storeNumber) {
    for (Map<String, Object> printLabelRequest : printLabelRequests) {
      if (trackingId.equals(printLabelRequest.get(PRINT_LABEL_IDENTIFIER))) {
        List<Map<String, Object>> printLabelDataList =
            (List<Map<String, Object>>) printLabelRequest.get(PRINT_DATA);
        for (Map<String, Object> labelData : printLabelDataList) {
          if (LABEL_TYPE_STORE.equalsIgnoreCase(String.valueOf(labelData.get(PRINT_KEY)))) {
            String paddedStoreNumber = String.format("%05d", Integer.parseInt(storeNumber));
            logger.info(
                "LabelData values are key: {}, value: {}",
                labelData.get(PRINT_KEY),
                labelData.get(PRINT_VALUE));
            labelData.replace(PRINT_VALUE, paddedStoreNumber);
            return;
          }
        }
      }
    }
  }

  /**
   * This method is to update printJob with store number, update printRequest and
   * last_changed_user_id in Instruction, Change the inventory_status in container from 'ALLOCATED'
   * to 'PICKED' and Update location and last_changed_user in Container
   *
   * @param container
   * @param locationName
   * @param userId
   * @param instruction
   * @param printJob
   */
  private void updateContainerAndInstructionForShippingLabel(
      Container container,
      String locationName,
      String userId,
      Instruction instruction,
      Map<String, Object> printJob) {
    instruction.getContainer().setCtrLabel(printJob);
    instruction.setLastChangeUserId(userId);
    instructionPersisterService.saveInstruction(instruction);

    container.setInventoryStatus(InventoryStatus.PICKED.name());
    container.setLastChangedUser(userId);
    container.setLocation(locationName);
    containerPersisterService.saveContainer(container);
  }
}
