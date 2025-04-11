package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.client.dcfin.DcFinUtil.*;
import static com.walmart.move.nim.receiving.core.common.InstructionUtils.*;
import static com.walmart.move.nim.receiving.core.common.JacksonParser.convertJsonToObject;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_SERVICE_DOWN;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.NO_PO_FOUND;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.OVERAGE_ERROR;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardablHeaderWithTenantData;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.*;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.GLS_RCV_INSTRUCTION_COMPLETED;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.GROCERY_OVERAGE_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.PO_LINE_NOT_FOUND;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.PO_NOT_FOUND;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.DeliveryStatus.getDeliveryStatus;
import static com.walmart.move.nim.receiving.utils.constants.InstructionStatus.COMPLETED;
import static com.walmart.move.nim.receiving.utils.constants.MoveEvent.CREATE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.witron.common.GdcUtil.getTxnId;
import static com.walmart.move.nim.receiving.witron.common.GdcUtil.isAtlasConvertedItem;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.*;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.SAMS;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.WM;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.valueOf;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.dcfin.model.DcFinAdjustRequest;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.model.inventory.*;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DCFinService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.OverrideInfo;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.ReceiveInstructionHandler;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.common.GdcUtil;
import com.walmart.move.nim.receiving.witron.config.WitronManagedConfig;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import com.walmart.move.nim.receiving.witron.helper.GdcManualReceiveHelper;
import com.walmart.move.nim.receiving.witron.helper.LabelPrintingHelper;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

public class GdcReceiveInstructionHandler implements ReceiveInstructionHandler {
  private static final Logger logger = LoggerFactory.getLogger(GdcReceiveInstructionHandler.class);
  public static final String PO_LEVEL_ERROR = "PoLevelError";
  public static final String PO_LINE_LEVEL_ERROR = "PoLineLevelError";
  protected InstructionError instructionError;

  @Autowired private ReceiptService receiptService;
  @Autowired private InstructionService instructionService;
  @Autowired private ContainerService containerService;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private InstructionStateValidator instructionStateValidator;
  @Autowired private PurchaseReferenceValidator purchaseReferenceValidator;
  @Autowired protected InstructionPersisterService instructionPersisterService;
  @Autowired private DeliveryCacheServiceInMemoryImpl deliveryCacheServiceInMemoryImpl;
  @Autowired private ReceiptPublisher receiptPublisher;
  @Autowired private MovePublisher movePublisher;
  @ManagedConfiguration private WitronManagedConfig witronManagedConfig;

  @Autowired private GdcPutawayPublisher gdcPutawayPublisher;

  @Autowired private GdcManualReceiveHelper gdcManualReceiveHelper;
  @Autowired private GDCFlagReader gdcFlagReader;

  @Autowired private LabelPrintingHelper labelPrintingHelper;
  @Autowired private DCFinRestApiClient dcFinRestApiClient;
  @Autowired DeliveryDocumentHelper deliveryDocumentHelper;
  @Autowired private Gson gson;
  @Autowired private GdcInstructionService gdcInstructionService;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Resource(name = "containerTransformer")
  private Transformer<Container, ContainerDTO> transformer;

  @Autowired AsyncPoReceivingProgressPublisher asyncPoReceivingProgressPublisher;

  @Autowired protected InventoryRestApiClient inventoryRestApiClient;

  @Override
  public InstructionResponse receiveInstruction(
      Long instructionId,
      ReceiveInstructionRequest receiveInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    logger.info(
        "GDC implementation of receive instructionId={} for receiveInstructionRequest: {}",
        instructionId,
        receiveInstructionRequest);
    // Check is GLS call is made successfully
    boolean isGlsCallSuccess = false;
    Instruction instruction = null;

    try {
      // removes host and other
      httpHeaders = ReceivingUtils.getForwardableHttpHeaders(httpHeaders);

      // Get the right configs from CCM
      DCFinService dcFinService =
          configUtils.getConfiguredInstance(
              String.valueOf(getFacilityNum()), DC_FIN_SERVICE, DCFinService.class);

      // Get instruction details from DB
      instruction = instructionPersisterService.getInstructionById(instructionId);
      DeliveryDocumentLine deliveryDocumentLine =
          InstructionUtils.getDeliveryDocumentLine(instruction);

      final String poNbr = instruction.getPurchaseReferenceNumber();
      final Integer poLineNbr = instruction.getPurchaseReferenceLineNumber();
      final String deliveryNumber = instruction.getDeliveryNumber().toString();
      final Boolean isReceiveAsCorrection = instruction.getIsReceiveCorrection();
      Integer qtyToBeReceived = receiveInstructionRequest.getQuantity();

      // Validate data before start receiving
      dataValidationBeforeReceive(
          instruction, deliveryDocumentLine, httpHeaders, receiveInstructionRequest, instructionId);

      // Prepare updateInstructionRequest from instruction
      UpdateInstructionRequest updateInstructionRequest =
          InstructionUtils.constructUpdateInstructionRequest(
              instruction, receiveInstructionRequest);
      DocumentLine documentLine = updateInstructionRequest.getDeliveryDocumentLines().get(0);

      // Get delivery metadata for ignoring expiry(rotate) date
      boolean isManagerOverrideIgnoreExpiry =
          instructionHelperService.isManagerOverrideIgnoreExpiry(
              deliveryNumber,
              poNbr,
              instruction.getFirstExpiryFirstOut(),
              instruction.getPurchaseReferenceLineNumber());

      // Validate item life expectancy against the threshold
      InstructionUtils.validateThresholdForSellByDate(
          true,
          instruction.getFirstExpiryFirstOut(),
          documentLine,
          isManagerOverrideIgnoreExpiry,
          true);

      // Overage rules
      OverrideInfo overrideInfo = null;
      String problemTagId = instruction.getProblemTagId();
      if (isNotBlank(problemTagId)) {
        logger.info("No overage alert for GDC problem receiving, problemTagId:{}", problemTagId);
      } else {
        // Get delivery metadata for ignoring overages
        overrideInfo =
            validateOverageRules(
                instruction, deliveryDocumentLine, documentLine, receiveInstructionRequest, true);
      }

      // Safeguard user from keying wrong date
      safeguardMaxAllowedWarehouseStorage(receiveInstructionRequest, instruction, documentLine);

      // Get gdmDeliveryDetailsCached by PO/Line
      DeliveryCacheValue gdmDeliveryDetailsCached =
          deliveryCacheServiceInMemoryImpl.getDeliveryDetailsByPoPoLine(
              Long.valueOf(deliveryNumber), poNbr, poLineNbr, httpHeaders);

      if (isNull(gdmDeliveryDetailsCached)) {
        logger.error(
            "Error while fetching DeliveryCacheValue for deliveryNumber :{}, poNbr :{}, poLineNbr :{}",
            deliveryNumber,
            poNbr,
            poLineNbr);
        throw new ReceivingException(
            ReceivingException.GDM_SERVICE_DOWN,
            INTERNAL_SERVER_ERROR,
            ReceivingException.GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE);
      }

      final boolean isManualDc = gdcFlagReader.isManualGdcEnabled();
      final boolean isAutomatedDc = !isManualDc;
      final boolean isOneAtlas = gdcFlagReader.isDCOneAtlasEnabled();
      final boolean isOneAtlasConverted = isOneAtlas && isAtlasConvertedItem(deliveryDocumentLine);

      if (isOneAtlasConverted) {
        //// calling aquire slot acquireSlotManualGdc
        gdcManualReceiveHelper.buildInstructionFromSlotting(
            receiveInstructionRequest, instruction, httpHeaders, updateInstructionRequest);
      } else {
        if (isManualDc) {
          // Call GLS if Manual grocery and set Instruction container and move
          isGlsCallSuccess =
              gdcManualReceiveHelper.buildInstructionFromGls(
                  receiveInstructionRequest, instruction, httpHeaders, overrideInfo);
        }
      }

      logger.info("Prepare Label data for Print Job");
      Map<String, Object> printJob =
          labelPrintingHelper.getLabelData(instruction, receiveInstructionRequest, httpHeaders);

      // Create CONTAINER_ITEM/CONTAINER/RECEIPT/PRINTJOB
      Map<String, Object> responseMap =
          instructionHelperService.receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
              instruction,
              updateInstructionRequest,
              qtyToBeReceived,
              httpHeaders,
              isReceiveAsCorrection,
              isAutomatedDc,
              isOneAtlasConverted);
      Instruction completedInstruction = (Instruction) responseMap.get(INSTRUCTION);
      Container consolidatedContainer = (Container) responseMap.get(CONTAINER);
      Set<Container> childContainerList = new HashSet<>();
      consolidatedContainer.setChildContainers(childContainerList);

      // Publish RTU to Witron
      if (!gdcFlagReader.publishToWitronDisabled())
        gdcPutawayPublisher.publishMessage(consolidatedContainer, PUTAWAY_ADD_ACTION, httpHeaders);

      final ContainerItem containerItem = consolidatedContainer.getContainerItems().get(0);

      // Receive As Correction FLOW
      if (isReceiveAsCorrection) {
        FinalizePORequestBody finalizePORequestBody =
            (FinalizePORequestBody) responseMap.get(OSDR_PAYLOAD);

        // Post receipts to Inventory
        if (isAutomatedDc || isOneAtlasConverted) {
          containerService.postReceiptsReceiveAsCorrection(consolidatedContainer, httpHeaders);
          // Publish receipts to SCT
          receiptPublisher.publishReceiptUpdate(
              consolidatedContainer.getTrackingId(), httpHeaders, TRUE);
        }
        notifyReceivingCorrectionToDcFin(
            isOneAtlas,
            isAtlasConvertedItem(deliveryDocumentLine),
            consolidatedContainer,
            httpHeaders);

        // Post OSDR to GDM
        containerService.postFinalizePoOsdrToGdm(
            httpHeaders,
            consolidatedContainer.getDeliveryNumber(),
            containerItem.getPurchaseReferenceNumber(),
            finalizePORequestBody);
      } else {
        if (isAutomatedDc || isOneAtlasConverted) {
          publishContainerReceipts(consolidatedContainer, gdmDeliveryDetailsCached, httpHeaders);
        }
        // This flag only applicable for full gls site only ex 6097 = true
        if (!gdcFlagReader.isDCFinApiDisabled() && gdcFlagReader.isDCFinHttpReceiptsEnabled()) {
          dcFinService.postReceiptsToDCFin(consolidatedContainer, httpHeaders, null);
        }
      }

      // Publish move to MM
      publishToMove(completedInstruction, consolidatedContainer, deliveryDocumentLine, httpHeaders);

      // Publish instruction to WFT
      // This flag only applicable for full gls site only ex 6097 = true
      if (!gdcFlagReader.publishToWFTDisabled()) {
        instructionHelperService.publishInstruction(
            instruction,
            updateInstructionRequest,
            qtyToBeReceived,
            consolidatedContainer,
            InstructionStatus.UPDATED,
            httpHeaders);
        instructionHelperService.publishInstruction(
            completedInstruction, null, null, consolidatedContainer, COMPLETED, httpHeaders);
      }

      logReceivedQuantityDetails(
          instruction,
          deliveryDocumentLine,
          getDeliveryDocument(instruction),
          witronManagedConfig.isAsnReceivingEnabled());

      // Publish Receive Progress - Async call
      if (gdcFlagReader.isReceivingProgressPubEnabled())
        asyncPoReceivingProgressPublisher.publishPoReceiveProgress(
            Long.parseLong(deliveryNumber), poNbr, httpHeaders);

      return new InstructionResponseImplNew(null, null, completedInstruction, printJob);

    } catch (ReceivingException re) {
      receivingException(receiveInstructionRequest, instruction, httpHeaders, isGlsCallSuccess, re);
    } catch (ReceivingBadDataException rbde) {
      badDataException(receiveInstructionRequest, instruction, httpHeaders, isGlsCallSuccess, rbde);
    } catch (GDMRestApiClientException gdme) {
      apiException(receiveInstructionRequest, instruction, httpHeaders, isGlsCallSuccess, gdme);
    } catch (Exception e) {
      genericException(receiveInstructionRequest, instruction, httpHeaders, isGlsCallSuccess, e);
    }
    return null;
  }

  private void safeguardMaxAllowedWarehouseStorage(
      ReceiveInstructionRequest receiveRequest,
      Instruction instruction,
      DocumentLine documentLine) {
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), SAFEGUARD_MAX_ALLOWED_STORAGE, false)) {
      DeliveryDocument deliveryDocument = InstructionUtils.getDeliveryDocument(instruction);
      ItemData itemData = InstructionUtils.getDeliveryDocumentLine(instruction).getAdditionalInfo();
      // Sanity check before proceed with validations (3:Strict - Rotate by Sell By Date)
      if (itemData != null && THREE.equalsIgnoreCase(itemData.getWarehouseRotationTypeCode())) {
        final Integer maxAllowedStorageDays =
            getMaxAllowedStorageDays(itemData, deliveryDocument.getBaseDivisionCode());
        final Date maxAllowedStorageDate = getMaxAllowedStorageDate(maxAllowedStorageDays);

        if (receiveRequest.isReceiveBeyondThreshold()) {
          // No alert if it's already acknowledged by user, record in container_item table
          documentLine.setMaxAllowedStorageDays(maxAllowedStorageDays);
          documentLine.setMaxAllowedStorageDate(maxAllowedStorageDate);
        } else {
          // If rotationType=3 and rotateDate entered exceeds maxAllowedStorageDate, then show the
          // alert
          if (receiveRequest.getRotateDate().after(maxAllowedStorageDate)) {
            throw new ReceivingBadDataException(
                BEYOND_THRESHOLD_DATE_WARN_ERROR_CODE,
                String.format(
                    BEYOND_THRESHOLD_DATE_WARN_ERROR_MSG_1,
                    receiveRequest
                        .getRotateDate()
                        .toInstant()
                        .atZone(ZoneId.of(UTC_TIME_ZONE))
                        .toLocalDateTime()
                        .format(DateTimeFormatter.ofPattern(PRINT_LABEL_ROTATE_DATE_MM_DD_YYYY)),
                    maxAllowedStorageDays));
          }
        }
      }
    }
  }

  private Integer getMaxAllowedStorageDays(ItemData itemData, String baseDivisionCode) {
    Integer maxAllowedStorageDays = 0;
    if (WM.equalsIgnoreCase(baseDivisionCode)) {
      // Formula for WM item
      maxAllowedStorageDays =
          (itemData.getWarehouseMinLifeRemainingToReceive() * 2)
              + (Objects.isNull(itemData.getAllowedTimeInWarehouseQty())
                  ? 0
                  : itemData.getAllowedTimeInWarehouseQty());
    } else if (SAMS.equalsIgnoreCase(baseDivisionCode)) {
      // Formula for SAMS item
      maxAllowedStorageDays = itemData.getWarehouseMinLifeRemainingToReceive() * 3;
    }

    return maxAllowedStorageDays;
  }

  private Date getMaxAllowedStorageDate(Integer maxAllowedDays) {
    return Date.from(
        Instant.now().plus(maxAllowedDays, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS));
  }

  /**
   * Creates ContainerDTO transforming from Containers and updates DcFin info
   *
   * @param gdmDeliveryDetailsByPoPoLine
   * @param consolidatedContainer
   * @return List<ContainerDTO>
   */
  public List<ContainerDTO> getContainerDTOs(
      DeliveryCacheValue gdmDeliveryDetailsByPoPoLine, Container consolidatedContainer) {
    List<ContainerDTO> containersDtoList = transformer.transformList(asList(consolidatedContainer));
    if (!containersDtoList.isEmpty()) {
      ContainerDTO containerDTO = containersDtoList.get(0);
      containerService.setToBeAuditedTagGDC(containerDTO);

      Map<String, Object> containerMiscInfo = containerDTO.getContainerMiscInfo();
      if (containerMiscInfo == null) {
        containerMiscInfo = new HashMap<>();
        containerDTO.setContainerMiscInfo(containerMiscInfo);
      }

      String scacCode = gdmDeliveryDetailsByPoPoLine.getScacCode();
      if (isNotBlank(scacCode)) {
        containerMiscInfo.put(CARRIER_NAME, scacCode);
      }

      String trailerId = gdmDeliveryDetailsByPoPoLine.getTrailerId();
      if (isNotBlank(trailerId)) {
        containerMiscInfo.put(TRAILER_NBR, trailerId);
      }

      String freightTermCode = gdmDeliveryDetailsByPoPoLine.getFreightTermCode();
      if (isNotBlank(freightTermCode)) {
        containerMiscInfo.put(BILL_CODE, freightTermCode);
      }

      Integer freightBillQty = gdmDeliveryDetailsByPoPoLine.getTotalBolFbq();
      if (freightBillQty != null) {
        containerMiscInfo.put(FREIGHT_BILL_QTY, freightBillQty);
      }
      containerDTO.setLabelPrintInd(ReceivingConstants.Y);
      containerService.addSubcenterInfo(containerDTO);
    }
    return containersDtoList;
  }

  private void notifyReceivingCorrectionToDcFin(
      boolean isOneAtlas, boolean isConvertedItem, Container container, HttpHeaders httpHeaders)
      throws ReceivingException {
    // if OneAtlas and ItemConverted then notify RC to DcFin
    if ((isOneAtlas && !isConvertedItem)
        || configUtils.getConfiguredFeatureFlag(
            String.valueOf(getFacilityNum()), PUBLISH_TO_DCFIN_ADJUSTMENTS_ENABLED, false)) {
      final DcFinAdjustRequest request =
          createDcFinAdjustRequest(
              container,
              httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY),
              INVENTORY_RECEIVING_CORRECTION_REASON_CODE,
              container.getContainerItems().get(0).getQuantity());
      dcFinRestApiClient.adjustOrVtr(request, getForwardablHeaderWithTenantData(httpHeaders));
    }
  }

  @Override
  public InstructionResponse receiveInstruction(
      ReceiveInstructionRequest receiveInstructionRequest, HttpHeaders httpHeaders) {
    logger.info(
        "Default implementation of receive instruction for instruction request {}",
        receiveInstructionRequest);
    return null;
  }

  @Override
  public ReceiveAllResponse receiveAll(
      Long instructionId, ReceiveAllRequest receiveAllRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    logger.info(
        "GDC implementation of receive instructionId={} for receiveInstructionRequest: {}",
        instructionId,
        receiveAllRequest);

    // Check is GLS call is made successfully
    boolean isGlsCallSuccess = false;
    Instruction instruction = null;

    try {
      // removes host and other
      httpHeaders = ReceivingUtils.getForwardableHttpHeaders(httpHeaders);

      // Get the right configs from CCM
      DCFinService dcFinService =
          configUtils.getConfiguredInstance(
              String.valueOf(getFacilityNum()), DC_FIN_SERVICE, DCFinService.class);

      // Get instruction details from DB
      instruction = instructionPersisterService.getInstructionById(instructionId);
      DeliveryDocumentLine deliveryDocumentLine =
          InstructionUtils.getDeliveryDocumentLine(instruction);

      final String poNbr = instruction.getPurchaseReferenceNumber();
      final Integer poLineNbr = instruction.getPurchaseReferenceLineNumber();
      final String deliveryNumber = instruction.getDeliveryNumber().toString();
      final Boolean isReceiveAsCorrection = instruction.getIsReceiveCorrection();
      Integer qtyToBeReceived = receiveAllRequest.getQuantity();

      // Validate data before start receiving
      dataValidationBeforeReceive(
          instruction, deliveryDocumentLine, httpHeaders, receiveAllRequest, instructionId);

      // Prepare updateInstructionRequest from instruction
      UpdateInstructionRequest updateInstructionRequest =
          InstructionUtils.constructUpdateInstructionRequest(instruction, receiveAllRequest);
      DocumentLine documentLine = updateInstructionRequest.getDeliveryDocumentLines().get(0);

      // Get delivery metadata for ignoring expiry(rotate) date
      boolean isManagerOverrideIgnoreExpiry =
          instructionHelperService.isManagerOverrideIgnoreExpiry(
              deliveryNumber,
              poNbr,
              instruction.getFirstExpiryFirstOut(),
              instruction.getPurchaseReferenceLineNumber());

      // Validate item life expectancy against the threshold
      InstructionUtils.validateThresholdForSellByDate(
          true,
          instruction.getFirstExpiryFirstOut(),
          documentLine,
          isManagerOverrideIgnoreExpiry,
          true);

      // Overage rules
      // Get delivery metadata for ignoring overages
      OverrideInfo overrideInfo =
          validateOverageRules(
              instruction, deliveryDocumentLine, documentLine, receiveAllRequest, true);

      // Safeguard user from keying wrong date
      safeguardMaxAllowedWarehouseStorage(receiveAllRequest, instruction, documentLine);

      final boolean isAutomatedDc = gdcFlagReader.isAutomatedDC();
      final boolean isOneAtlas = gdcFlagReader.isDCOneAtlasEnabled();
      final boolean isOneAtlasConverted = isOneAtlas && isAtlasConvertedItem(deliveryDocumentLine);

      if (isOneAtlasConverted) {
        logger.info("Calling slotting for one atlas item");
        gdcManualReceiveHelper.buildInstructionFromSlotting(
            receiveAllRequest, instruction, httpHeaders, updateInstructionRequest);
      } else {
        // Call GLS if Manual grocery and set Instruction container and move
        isGlsCallSuccess =
            gdcManualReceiveHelper.buildInstructionFromGls(
                receiveAllRequest, instruction, httpHeaders, overrideInfo);
      }

      logger.info("Prepare Label data for Print Job");
      Map<String, Object> printJob =
          labelPrintingHelper.getLabelData(instruction, receiveAllRequest, httpHeaders);

      // Create CONTAINER_ITEM/CONTAINER/RECEIPT/PRINTJOB
      Map<String, Object> responseMap =
          instructionHelperService.receiveInstructionAndCompleteProblemTagAndPostInvIfOSS(
              instruction,
              updateInstructionRequest,
              qtyToBeReceived,
              httpHeaders,
              isReceiveAsCorrection,
              isAutomatedDc,
              isOneAtlasConverted);
      Instruction completedInstruction = (Instruction) responseMap.get(INSTRUCTION);
      Container consolidatedContainer = (Container) responseMap.get(CONTAINER);
      Set<Container> childContainerList = new HashSet<>();
      consolidatedContainer.setChildContainers(childContainerList);
      final ContainerItem containerItem = consolidatedContainer.getContainerItems().get(0);

      // Publish RTU to Witron
      if (!gdcFlagReader.publishToWitronDisabled())
        gdcPutawayPublisher.publishMessage(consolidatedContainer, PUTAWAY_ADD_ACTION, httpHeaders);

      if (isAutomatedDc || isOneAtlasConverted) {
        publishContainerReceipts(consolidatedContainer, null, httpHeaders);
      }

      // Publish purchases to DcFin
      // This flag only applicable for full gls site only ex 6097 = true
      if (!gdcFlagReader.isDCFinApiDisabled())
        dcFinService.postReceiptsToDCFin(consolidatedContainer, httpHeaders, null);

      // Publish move to MM
      publishToMove(completedInstruction, consolidatedContainer, deliveryDocumentLine, httpHeaders);

      // This flag only applicable for full gls site only ex 6097 = true
      if (!gdcFlagReader.publishToWFTDisabled()) {
        instructionHelperService.publishInstruction(
            instruction,
            updateInstructionRequest,
            qtyToBeReceived,
            consolidatedContainer,
            InstructionStatus.UPDATED,
            httpHeaders);
        instructionHelperService.publishInstruction(
            completedInstruction, null, null, consolidatedContainer, COMPLETED, httpHeaders);
      }

      logReceivedQuantityDetails(
          instruction,
          deliveryDocumentLine,
          getDeliveryDocument(instruction),
          witronManagedConfig.isAsnReceivingEnabled());

      return new ReceiveAllResponse(
          Long.parseLong(deliveryNumber),
          poNbr,
          poLineNbr,
          deliveryDocumentLine.getItemNbr(),
          printJob);
    } catch (ReceivingException re) {
      receivingException(receiveAllRequest, instruction, httpHeaders, isGlsCallSuccess, re);
    } catch (ReceivingBadDataException rbde) {
      badDataException(receiveAllRequest, instruction, httpHeaders, isGlsCallSuccess, rbde);
    } catch (Exception e) {
      genericException(receiveAllRequest, instruction, httpHeaders, isGlsCallSuccess, e);
    }
    return null;
  }

  // Publish receipts to Inventory and SCT
  // Only if Automated DC, Manual GDC DC with oneAtlasEnabled and item converted
  private void publishContainerReceipts(
      Container consolidatedContainer,
      DeliveryCacheValue gdmDeliveryDetailsCached,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    // to MQ
    if (gdcFlagReader.isMqReceiptsEnabled()) {
      instructionService.publishConsolidatedContainer(consolidatedContainer, httpHeaders, TRUE);
    }
    // to kafka
    if (gdcFlagReader.isKafkaReceiptsEnabled()) {
      final ContainerItem ci = consolidatedContainer.getContainerItems().get(0);
      if (gdcFlagReader.isKafkaReceiptsDcFinValidateEnabled())
        checkQuantityDivisible(ci.getQuantity(), ci.getVnpkQty(), ci.getWhpkQty());

      if (gdmDeliveryDetailsCached == null) {
        gdmDeliveryDetailsCached =
            deliveryCacheServiceInMemoryImpl.getDeliveryDetailsByPoPoLine(
                consolidatedContainer.getDeliveryNumber(),
                ci.getPurchaseReferenceNumber(),
                ci.getPurchaseReferenceLineNumber(),
                httpHeaders);
      }
      containerService.publishMultipleContainersToInventory(
          getContainerDTOs(gdmDeliveryDetailsCached, consolidatedContainer), httpHeaders);
    }
  }

  private void publishToMove(
      Instruction completedInstruction,
      Container consolidatedContainer,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders) {
    if (!gdcFlagReader.isReceivingInstructsPutAwayMoveToMM()) {
      logger.info("Instructions to Moves for the putaway is blocked from receiving");
      return;
    }
    LinkedTreeMap<String, Object> moveData = completedInstruction.getMove();
    if (MapUtils.isEmpty(moveData)) return;

    // Don't publish to move if slot info is empty or SLT NT FND from slotting
    if (Objects.nonNull(moveData.get("toLocation"))
        && GdcConstants.SLOT_NOT_FOUND.equalsIgnoreCase(String.valueOf(moveData.get("toLocation"))))
      return;

    if (gdcFlagReader.isAutomatedDC()) {
      movePublisher.publishMove(
          InstructionUtils.getMoveQuantity(consolidatedContainer),
          consolidatedContainer.getLocation(),
          httpHeaders,
          moveData,
          CREATE.getMoveEvent());
    } else if (gdcFlagReader.isDCOneAtlasEnabled() && isAtlasConvertedItem(deliveryDocumentLine)) {
      movePublisher.publishMoveV2(
          consolidatedContainer,
          (String) moveData.get(ReceivingConstants.MOVE_TO_LOCATION),
          httpHeaders);
    }
  }

  private void dataValidationBeforeReceive(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders,
      ReceiveInstructionRequest receiveInstructionRequest,
      Long instructionId)
      throws ReceivingException {
    // Basic sanity check
    if (isNull(instruction) || isNull(deliveryDocumentLine)) {
      logger.error("Invalid instructionId: {}", instructionId);
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVING_INTERNAL_ERROR,
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG);
    }

    // Check for valid instruction
    if (configUtils.getConfiguredFeatureFlag(
        getFacilityNum().toString(), IS_GDC_CANCEL_INSTRUCTION_ERROR_ENABLED, false)) {
      instructionStateValidator.validate(instruction, GLS_RCV_INSTRUCTION_COMPLETED, BAD_REQUEST);
    } else {
      instructionStateValidator.validate(instruction);
    }

    // Multi user validation
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    ReceivingUtils.verifyUser(instruction, userId, RequestType.COMPLETE);

    InstructionUtils.validatePalletWeight(
        deliveryDocumentLine,
        receiveInstructionRequest,
        configUtils.getWhiteWoodPalletMaxWeight(getFacilityNum(), WHITE_WOOD_MAX_WEIGHT_KEY));

    // Block if the totalQty exceeds projectedReceiveQty for a given instruction
    Integer projectedRcvQty = instruction.getProjectedReceiveQty();
    Integer qtyToBeReceived = receiveInstructionRequest.getQuantity();
    Integer instructionReceivedQty = instruction.getReceivedQuantity();
    Integer totalQtyAfterReceiving = instructionReceivedQty + qtyToBeReceived;
    if (totalQtyAfterReceiving > projectedRcvQty) {
      logger.info(
          "InstructionId:{} ProjectedReceiveQty:{} TotalQtyAfterReceiving:{}",
          instructionId,
          projectedRcvQty,
          totalQtyAfterReceiving);
      throw new ReceivingException(
          ReceivingException.UPDATE_INSTRUCTION_EXCEEDS_QUANTITY,
          INTERNAL_SERVER_ERROR,
          ReceivingException.UPDATE_INSTRUCTION_ERROR_CODE);
    }

    // Validate PO finalization
    final String poNbr = instruction.getPurchaseReferenceNumber();
    final Integer poLineNbr = instruction.getPurchaseReferenceLineNumber();
    final String deliveryNumber = instruction.getDeliveryNumber().toString();
    final Boolean isReceiveAsCorrection = instruction.getIsReceiveCorrection();
    if (!isReceiveAsCorrection) {
      purchaseReferenceValidator.validatePOConfirmation(deliveryNumber, poNbr);
    }
  }

  private OverrideInfo validateOverageRules(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      DocumentLine documentLine,
      ReceiveInstructionRequest receiveInstructionRequest,
      boolean isKotlinEnabled)
      throws ReceivingException {
    OverrideInfo overrideInfo = null;
    // Get delivery metadata for ignoring overages
    boolean isManagerOverrideIgnoreOverage = false;
    if (gdcFlagReader.isGLSApiEnabled()) {
      overrideInfo =
          instructionHelperService.getOverrideInfo(
              instruction.getDeliveryNumber().toString(),
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
      isManagerOverrideIgnoreOverage = nonNull(overrideInfo) && overrideInfo.isOverrideOverage();
    } else {
      isManagerOverrideIgnoreOverage =
          instructionHelperService.isManagerOverrideIgnoreOverage(
              instruction.getDeliveryNumber().toString(),
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
    }

    if (!isManagerOverrideIgnoreOverage) {
      // Fetch currentReceiveQuantity by PO/Line
      Long currentRcvQtyFromAllDeliveries =
          receiptService.getReceivedQtyByPoAndPoLine(
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());

      // The allowed number of cases for this item have been received
      if ((currentRcvQtyFromAllDeliveries.equals(documentLine.getMaxReceiveQty())
          || (currentRcvQtyFromAllDeliveries + receiveInstructionRequest.getQuantity()
              > documentLine.getMaxReceiveQty()))) {
        instructionError = InstructionErrorCode.getErrorValue(OVERAGE_ERROR);
        logger.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            INTERNAL_SERVER_ERROR,
            isKotlinEnabled ? GROCERY_OVERAGE_ERROR_CODE : instructionError.getErrorCode(),
            currentRcvQtyFromAllDeliveries.intValue(),
            documentLine.getMaxReceiveQty().intValue(),
            null);
      }
    }
    return overrideInfo;
  }

  private void receivingException(
      ReceiveInstructionRequest receiveInstructionRequest,
      Instruction instruction,
      HttpHeaders httpHeaders,
      boolean isGlsCallSuccess,
      ReceivingException re)
      throws ReceivingException {
    logger.error(
        "ReceivingException while isGlsCallSuccess and receiveInstruction(): {} {}",
        isGlsCallSuccess,
        ExceptionUtils.getStackTrace(re));
    // VTR/RCV Correction in GLS on exception if GLS receive call was success
    gdcManualReceiveHelper.adjustOrCancel(
        receiveInstructionRequest, instruction, httpHeaders, isGlsCallSuccess);
    throw new ReceivingException(
        re.getErrorResponse().getErrorMessage(),
        re.getHttpStatus(),
        re.getErrorResponse().getErrorCode(),
        re.getErrorResponse().getErrorHeader());
  }

  private void badDataException(
      ReceiveInstructionRequest receiveInstructionRequest,
      Instruction instruction,
      HttpHeaders httpHeaders,
      boolean isGlsCallSuccess,
      ReceivingBadDataException rbde) {
    logger.error(
        "ReceivingBadDataException while isGlsCallSuccess and receiveInstruction(): {} {}",
        isGlsCallSuccess,
        ExceptionUtils.getStackTrace(rbde));
    // VTR/RCV Correction in GLS on exception if GLS receive call was success
    gdcManualReceiveHelper.adjustOrCancel(
        receiveInstructionRequest, instruction, httpHeaders, isGlsCallSuccess);
    throw rbde;
  }

  private void apiException(
      ReceiveInstructionRequest receiveInstructionRequest,
      Instruction instruction,
      HttpHeaders httpHeaders,
      boolean isGlsCallSuccess,
      GDMRestApiClientException gdme)
      throws ReceivingException {
    logger.error(
        "GDMRestApiClientException while isGlsCallSuccess and receiveInstruction(): {} {}",
        isGlsCallSuccess,
        ExceptionUtils.getStackTrace(gdme));
    // VTR/RCV Correction in GLS on exception if GLS receive call was success
    gdcManualReceiveHelper.adjustOrCancel(
        receiveInstructionRequest, instruction, httpHeaders, isGlsCallSuccess);
    throw new ReceivingException(
        COMPLETE_INSTRUCTION_ERROR_MSG, BAD_REQUEST, COMPLETE_INSTRUCTION_ERROR_CODE);
  }

  private void genericException(
      ReceiveInstructionRequest receiveInstructionRequest,
      Instruction instruction,
      HttpHeaders httpHeaders,
      boolean isGlsCallSuccess,
      Exception e) {
    logger.error(
        "Exception while isGlsCallSuccess and receiveInstruction(): {} {}",
        isGlsCallSuccess,
        ExceptionUtils.getStackTrace(e));
    // VTR/RCV Correction in GLS on exception if GLS receive call was success
    gdcManualReceiveHelper.adjustOrCancel(
        receiveInstructionRequest, instruction, httpHeaders, isGlsCallSuccess);
    throw new ReceivingBadDataException(
        ExceptionCodes.RECEIVING_INTERNAL_ERROR,
        ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
        e);
  }

  @Override
  public ReceiveIntoOssResponse receiveIntoOss(
      Long deliveryNumber, ReceiveIntoOssRequest receiveIntoOssRequest, HttpHeaders httpHeaders)
      throws ReceivingException {

    DeliveryService deliveryService =
        configUtils.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class);
    final String gdmDeliveryResponseStr =
        deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, httpHeaders);
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(gdmDeliveryResponseStr, GdmPOLineResponse.class);
    final List<DeliveryDocument> gdmDeliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();
    DeliveryDetails gdmDeliveryDetails =
        convertJsonToObject(gdmDeliveryResponseStr, DeliveryDetails.class);
    gdcInstructionService.enrichDeliveryStatusAndStateReasonCode(
        gdmDeliveryDocuments,
        getDeliveryStatus(gdmDeliveryDetails.getDeliveryStatus()),
        gdmDeliveryDetails.getStateReasonCodes());

    ReceiveIntoOssError error = new ReceiveIntoOssError();
    // Process each Request PO
    Integer txId = 1;
    for (Po poRequest : receiveIntoOssRequest.getPos()) {
      final long poStartTs = currentTimeMillis();
      final String poNum = poRequest.getPoNum();
      logger.info("Start po for deliveryNumber:{} poNum:{}", deliveryNumber, poNum);
      try {
        final DeliveryDocument gdmDelDocForGivenPo =
            gdmDeliveryDocuments
                .stream()
                .filter(doc -> doc.getPurchaseReferenceNumber().equalsIgnoreCase(poNum))
                .findFirst()
                .orElseThrow(
                    () ->
                        new ReceivingDataNotFoundException(
                            PO_NOT_FOUND, String.format(NO_PO_FOUND, poNum)));
        final List<DeliveryDocumentLine> gdmDelDocLines =
            gdmDelDocForGivenPo.getDeliveryDocumentLines();
        DeliveryDocument onePoOneLineDoc =
            gson.fromJson(gson.toJson(gdmDelDocForGivenPo), DeliveryDocument.class);
        final List<PoLine> poLinesReq = poRequest.getLines();
        // Process each poLine in Request
        for (PoLine poLineReq : poLinesReq) {
          final Integer lineNum = poLineReq.getLineNum();
          logger.info(
              "Start poLine for deliveryNumber:{} poNum:{} lineNum:{}",
              deliveryNumber,
              poNum,
              lineNum);
          final long lineStartTs = currentTimeMillis();
          try {
            final DeliveryDocumentLine gdmOneLine =
                gdmDelDocLines
                    .stream()
                    .filter(docLn -> lineNum == docLn.getPurchaseReferenceLineNumber())
                    .findFirst()
                    .orElseThrow(
                        () ->
                            new ReceivingDataNotFoundException(
                                PO_LINE_NOT_FOUND,
                                String.format(NO_PO_LINES_FOUND, poNum + " ln#" + lineNum)));
            onePoOneLineDoc.getDeliveryDocumentLines().clear();
            onePoOneLineDoc.getDeliveryDocumentLines().add(gdmOneLine);
            receiveIntoOssOnePoLine(
                deliveryNumber, httpHeaders, onePoOneLineDoc, poLineReq, txId++);
          } catch (Exception e) {
            logger.error(
                "Error processing line={} for deliveryNumber={}, po={}, errorMessage={}, stack={}",
                lineNum,
                deliveryNumber,
                poNum,
                e.getMessage(),
                ExceptionUtils.getStackTrace(e));
            prepareLineLevelError(error, poNum, lineNum, e);
          }
          logger.info(
              "End poLine for deliveryNumber:{} poNum:{} lineNum:{} in {} ms",
              deliveryNumber,
              poNum,
              lineNum,
              (currentTimeMillis() - lineStartTs));
        }
        // End of all lines if poLine in Request
      } catch (Exception e) {
        logger.error(
            "Error processing po={} for deliveryNumber={}, stack={}",
            poNum,
            deliveryNumber,
            ExceptionUtils.getStackTrace(e));
        preparePoLevelError(error, poNum, e);
      }
      logger.info(
          "End po for deliveryNumber:{} poNum:{} in {}ms",
          deliveryNumber,
          poNum,
          (currentTimeMillis() - poStartTs));
    }
    ReceiveIntoOssResponse receiveIntoOssResponse = new ReceiveIntoOssResponse();
    receiveIntoOssResponse.setError(error);
    return receiveIntoOssResponse;
  }

  /**
   * @param deliveryNumber
   * @param httpHeaders
   * @param onePoOneLineDoc
   * @param poLine
   * @param txId
   * @throws ReceivingException
   */
  private void receiveIntoOssOnePoLine(
      Long deliveryNumber,
      HttpHeaders httpHeaders,
      DeliveryDocument onePoOneLineDoc,
      PoLine poLine,
      Integer txId)
      throws ReceivingException {
    // Ensure OrgUnitId in headers for downstream
    httpHeaders = ReceivingUtils.getForwardableWithOrgUnitId(httpHeaders);
    // poLine validations
    GdcUtil.validateRequestLineIntoOss(poLine);

    final Integer receiveQty = poLine.getReceiveQty();
    final String poNum = onePoOneLineDoc.getPurchaseReferenceNumber();
    final Integer lineNum = poLine.getLineNum();
    // doReceive and/or reject
    if (nonNull(receiveQty) && receiveQty > 0) {
      // CREATE INSTRUCTIONS
      InstructionResponse createInstruction =
          gdcInstructionService.serveInstructionRequestIntoOss(
              deliveryNumber, httpHeaders, asList(onePoOneLineDoc));
      logger.info(
          "Done createInstruction(serveInstructionRequestIntoOss)={} for deliver={}, po={}, line={}",
          createInstruction.getInstruction().getId(),
          deliveryNumber,
          poNum,
          lineNum);

      // RECEIVE INSTRUCTIONS
      receiveInstructionIntoOss(createInstruction, poLine, httpHeaders, txId);
      logger.info(
          "Done receiveInstructionIntoOss={} for deliver={}, po={}, line={}",
          createInstruction.getInstruction().getId(),
          deliveryNumber,
          poNum,
          lineNum);
    } else {
      processRejectIntoOss(poLine, deliveryNumber, httpHeaders, onePoOneLineDoc);
      processDamageIntoOss(poLine, deliveryNumber, httpHeaders, onePoOneLineDoc);
    }
  }

  private void processRejectIntoOss(
      PoLine poLine,
      Long deliveryNumber,
      HttpHeaders httpHeaders,
      DeliveryDocument onePoOneLineDoc) {
    // isRejectOnly as zero receive qty
    final String poNum = onePoOneLineDoc.getPurchaseReferenceNumber();
    final Integer lineNum = poLine.getLineNum();
    logger.info(
        "No Receive(qty={}) and Just Reject(qty={}) IntoOss for deliver={}, po={}, line={}",
        poLine.getReceiveQty(),
        poLine.getRejectQty(),
        deliveryNumber,
        poNum,
        lineNum);
    Receipt masterReceipt =
        receiptService
            .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                deliveryNumber, poNum, lineNum);
    if (nonNull(masterReceipt)) {
      receiptService.updateRejects(
          poLine.getRejectQty(),
          poLine.getRejectQtyUOM(),
          poLine.getRejectReasonCode(),
          FLOW_RECEIVE_INTO_OSS,
          masterReceipt);
      receiptService.saveReceipt(masterReceipt);
    } else {
      logger.info(
          "doReject creating OSDR_MASTER receipt for DELIVERY :{} PO :{} LINE :{}",
          deliveryNumber,
          poNum,
          lineNum);
      Receipt receipt =
          buildReceiptObj(lineNum, deliveryNumber, poNum, onePoOneLineDoc, httpHeaders);
      receiptService.updateRejects(
          poLine.getRejectQty(),
          poLine.getRejectQtyUOM(),
          poLine.getRejectReasonCode(),
          FLOW_RECEIVE_INTO_OSS,
          receipt);
      receiptService.saveReceipt(receipt);
    }
  }

  private void processDamageIntoOss(
      PoLine poLine,
      Long deliveryNumber,
      HttpHeaders httpHeaders,
      DeliveryDocument onePoOneLineDoc) {
    // isDamageOnly as zero receive qty
    final String poNum = onePoOneLineDoc.getPurchaseReferenceNumber();
    final Integer lineNum = poLine.getLineNum();
    logger.info(
        "No Receive(qty={}), Damage(qty={}) IntoOss for deliver={}, po={}, line={}",
        poLine.getReceiveQty(),
        poLine.getDamageQty(),
        deliveryNumber,
        poNum,
        lineNum);
    Receipt masterReceipt =
        receiptService
            .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                deliveryNumber, poNum, lineNum);
    if (nonNull(masterReceipt)) {
      receiptService.updateDamages(
          poLine.getDamageQty(),
          poLine.getDamageQtyUOM(),
          poLine.getDamageReasonCode(),
          poLine.getDamageClaimType(),
          masterReceipt);
      receiptService.saveReceipt(masterReceipt);
    } else {
      logger.info(
          "doDamage creating OSDR_MASTER receipt for DELIVERY :{} PO :{} LINE :{}",
          deliveryNumber,
          poNum,
          lineNum);
      Receipt receipt =
          buildReceiptObj(lineNum, deliveryNumber, poNum, onePoOneLineDoc, httpHeaders);
      receiptService.updateDamages(
          poLine.getDamageQty(),
          poLine.getDamageQtyUOM(),
          poLine.getDamageReasonCode(),
          poLine.getDamageClaimType(),
          receipt);
      receiptService.saveReceipt(receipt);
    }
  }

  private Receipt buildReceiptObj(
      Integer lineNum,
      Long deliveryNumber,
      String poNum,
      DeliveryDocument onePoOneLineDoc,
      HttpHeaders httpHeaders) {
    final DeliveryDocumentLine oneDocLine = onePoOneLineDoc.getDeliveryDocumentLines().get(0);
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setPurchaseReferenceNumber(poNum);
    receipt.setPurchaseReferenceLineNumber(lineNum);
    receipt.setQuantity(0);
    receipt.setEachQty(0);
    receipt.setQuantityUom(VNPK);
    receipt.setVnpkQty(oneDocLine.getVendorPack());
    receipt.setWhpkQty(oneDocLine.getWarehousePack());
    receipt.setCreateTs(Date.from(Instant.now()));
    receipt.setOsdrMaster(1);
    if (isNotBlank(httpHeaders.getFirst(ORG_UNIT_ID_HEADER)))
      receipt.setOrgUnitId(valueOf(httpHeaders.getFirst(ORG_UNIT_ID_HEADER)));
    return receipt;
  }

  @Transactional
  public void receiveInstructionIntoOss(
      InstructionResponse createInstructionResponse,
      PoLine poLineRequest,
      HttpHeaders httpHeaders,
      Integer txId)
      throws ReceivingException {
    logger.info(
        "receiveInstructionIntoOss poLineRequest={}, createInstructionResponse={}",
        poLineRequest,
        createInstructionResponse);

    // Get instruction details from memory
    Instruction instruction = createInstructionResponse.getInstruction();
    final String deliveryNumber = instruction.getDeliveryNumber().toString();
    final String poNum = instruction.getPurchaseReferenceNumber();
    final Integer lineNum = instruction.getPurchaseReferenceLineNumber();

    // Validate data before start receiving
    purchaseReferenceValidator.validatePOConfirmation(deliveryNumber, poNum);

    // Create receiveInstructionRequest
    final ReceiveInstructionRequest receiveInstructionRequest =
        createReceiveInstructionRequestIntoOss(
            createInstructionResponse, poLineRequest, configUtils.getDCTimeZone(getFacilityNum()));

    // Prepare updateInstructionRequest from instruction
    UpdateInstructionRequest updateInstructionRequest =
        InstructionUtils.constructUpdateInstructionRequest(instruction, receiveInstructionRequest);

    // Get gdmDeliveryDetailsCached by PO/Line
    DeliveryCacheValue gdmDeliveryDetailsCached =
        deliveryCacheServiceInMemoryImpl.getDeliveryDetailsByPoPoLine(
            Long.valueOf(deliveryNumber), poNum, lineNum, httpHeaders);
    if (isNull(gdmDeliveryDetailsCached)) {
      logger.error(
          "Error while fetching DeliveryCacheValue for deliveryNumber :{}, poNum :{}, lineNum :{}",
          deliveryNumber,
          poNum,
          lineNum);
      throw new ReceivingException(
          GDM_SERVICE_DOWN, INTERNAL_SERVER_ERROR, GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE);
    }

    // Calculate no of Pallets and create container
    DocumentLine documentLine = updateInstructionRequest.getDeliveryDocumentLines().get(0);
    final Integer tihi = documentLine.getPalletHi() * documentLine.getPalletTi();

    Integer userEnteredQty = poLineRequest.getReceiveQty();
    int totalPallets = (int) Math.ceil((double) userEnteredQty / tihi);

    List<String> trackingIds =
        gdcInstructionService.getMultiplePalletTag(totalPallets, httpHeaders);

    // Create CONTAINER, CONTAINER_ITEM & RECEIPT
    Map<String, Object> responseMap =
        instructionHelperService.completeInstructionAndCreateContainerAndReceiptAndReject(
            poLineRequest, instruction, updateInstructionRequest, httpHeaders, trackingIds);
    List<Container> consolidatedContainerList = (List<Container>) responseMap.get(CONTAINER_LIST);

    String correlationId = String.valueOf(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    int index = txId * 100; // To Maintain unique tnx id for dcFin Processing
    for (Container consolidatedContainer : consolidatedContainerList) {
      txId = index++;
      consolidatedContainer.setChildContainers(new HashSet<Container>());

      // Final ContainerItem containerDetails, Inventory, SCT
      httpHeaders.add(FLOW_DESCRIPTOR, FLOW_RECEIVE_INTO_OSS);
      httpHeaders.set(CORRELATION_ID_HEADER_KEY, getTxnId(correlationId, txId));
      consolidatedContainer.setMessageId(
          String.valueOf(httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY)));
      publishContainerReceipts(consolidatedContainer, gdmDeliveryDetailsCached, httpHeaders);

      // Publish purchases to DcFin
      if (!gdcFlagReader.isDCFinApiDisabled() && gdcFlagReader.isDCFinHttpReceiptsEnabled()) {
        DCFinService dcFinService =
            configUtils.getConfiguredInstance(
                String.valueOf(getFacilityNum()), DC_FIN_SERVICE, DCFinService.class);
        dcFinService.postReceiptsToDCFin(consolidatedContainer, httpHeaders, null);
      }
    }
  }

  private static ReceiveInstructionRequest createReceiveInstructionRequestIntoOss(
      InstructionResponse createInstructionResponse, PoLine poLineRequest, String dcTimeZone) {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setFlowDescriptor(FLOW_RECEIVE_INTO_OSS);
    receiveInstructionRequest.setQuantity(poLineRequest.getReceiveQty());
    receiveInstructionRequest.setQuantityUOM(poLineRequest.getReceiveQtyUOM());
    SimpleDateFormat formatter =
        new SimpleDateFormat(SLOTTING_ROTATE_DATE_FORMAT_YYYY_MM_DD, Locale.ENGLISH);
    try {
      receiveInstructionRequest.setDoorNumber(
          createInstructionResponse.getInstruction().getContainer().getCtrDestination().get(SLOT));
      receiveInstructionRequest.setRotateDate(
          formatter.parse(ReceivingUtils.getDCDateTime(dcTimeZone).toLocalDate().toString()));
    } catch (Exception e) {
      logger.error(
          "Error creating receiveInstructionRequest doorNumber or parsing rotate date, stack= {}",
          ExceptionUtils.getStackTrace(e));
    }

    receiveInstructionRequest.setDeliveryDocuments(
        createInstructionResponse.getDeliveryDocuments());
    receiveInstructionRequest.setDeliveryDocumentLines(
        asList(
            createInstructionResponse
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)));

    receiveInstructionRequest.setQuantityUOM(Uom.VNPK);
    return receiveInstructionRequest;
  }

  private static void preparePoLevelError(ReceiveIntoOssError error, String poNum, Exception e) {
    HashMap<String, Po> errorPos = error.getErrPos();
    if (errorPos == null) {
      HashMap<String, Po> newPoLevelErrorList = new HashMap<>();
      Po errPo = createNewPoLevelError(poNum, e);
      newPoLevelErrorList.put(poNum, errPo);
      error.setErrPos(newPoLevelErrorList);
    } else {
      Po poLevelError = errorPos.get(poNum);
      if (poLevelError == null) {
        Po newPoLevelError = createNewPoLevelError(poNum, e);
        errorPos.put(poNum, newPoLevelError);
      } else {
        poLevelError.setErrorCode(PO_LEVEL_ERROR);
        poLevelError.setErrorMessage(e.getMessage());
      }
    }
  }

  private static void prepareLineLevelError(
      ReceiveIntoOssError error, String poNum, Integer lineNum, Exception e) {
    HashMap<String, Po> errorPos = error.getErrPos();
    if (errorPos == null) {
      createNewErrorPosMapFromLineLevelError(poNum, error, lineNum, e);
    } else {
      updateErrorPosMapFromLineLevelError(poNum, lineNum, e, errorPos);
    }
  }

  private static void createNewErrorPosMapFromLineLevelError(
      String poNum, ReceiveIntoOssError error, Integer lineNum, Exception e) {
    logger.info(
        "createNewErrorPosMapFromLineLevelError for po={}, line={}, error={}",
        poNum,
        lineNum,
        e.getMessage());
    HashMap<String, Po> newErrPo = new HashMap<>();
    Po errPo = new Po();
    PoLine errPoLine = createNewLineLevelError(e);
    HashMap<Integer, PoLine> errorLines = new HashMap<>();
    errorLines.put(lineNum, errPoLine);
    errPo.setErrLines(errorLines);
    newErrPo.put(poNum, errPo);
    error.setErrPos(newErrPo);
  }

  private static void updateErrorPosMapFromLineLevelError(
      String poNum, Integer lineNum, Exception e, HashMap<String, Po> errorPos) {
    logger.info(
        "add updateErrorPosMapFromLineLevelError for po={}, line={} to errorPos={}, error=",
        poNum,
        lineNum,
        errorPos,
        e.getMessage());
    Po errPo = errorPos.get(poNum);
    if (errPo == null) {
      errorPos.put(poNum, new Po());
      errPo = errorPos.get(poNum);
    }
    HashMap<Integer, PoLine> errorLines = errPo.getErrLines();
    if (errorLines == null) {
      errorLines = new HashMap<>();
      errPo.setErrLines(errorLines);
    }
    final PoLine newLineLevelError = createNewLineLevelError(e);
    errorLines.put(lineNum, newLineLevelError);
  }

  private static Po createNewPoLevelError(String poNum, Exception e) {
    Po errPo = new Po();
    errPo.setErrorCode(PO_LEVEL_ERROR);
    errPo.setErrorMessage(e.getMessage());
    return errPo;
  }

  private static PoLine createNewLineLevelError(Exception e) {
    final PoLine poErrLine = new PoLine();
    poErrLine.setErrorCode(PO_LINE_LEVEL_ERROR);
    poErrLine.setErrorMessage(e.getMessage());
    return poErrLine;
  }
}
