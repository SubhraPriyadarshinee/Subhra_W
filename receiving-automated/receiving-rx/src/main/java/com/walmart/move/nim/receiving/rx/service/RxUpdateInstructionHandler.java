package com.walmart.move.nim.receiving.rx.service;

import static java.util.Optional.ofNullable;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ContainerItemService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.UpdateInstructionHandler;
import com.walmart.move.nim.receiving.rx.builders.RxContainerItemBuilder;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.service.v2.data.UpdateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.UpdateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

public class RxUpdateInstructionHandler implements UpdateInstructionHandler {

  private static final String DB_PROCESS_TIME_LOG_MESSAGE =
      "Time take to complete update instruction DB Tx is {} ms and correlation id is {}";

  private static final Logger LOG = LoggerFactory.getLogger(RxUpdateInstructionHandler.class);

  @Autowired private InstructionPersisterService instructionPersisterService;

  @Autowired private TenantSpecificConfigReader configUtils;

  @Resource(name = ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
  private LPNCacheService lpnCacheService;

  @Autowired private Gson gson;

  @Autowired private InstructionStateValidator instructionStateValidator;

  @Autowired private EpcisService epcisService;

  @Autowired private ContainerItemService containerItemService;

  @Autowired private ContainerService containerService;

  @Autowired private ReceiptService receiptService;

  @ManagedConfiguration private RxManagedConfig rxManagedConfig;

  @Autowired private RxReceiptsBuilder rxReceiptsBuilder;

  @Autowired private RxInstructionHelperService rxInstructionHelperService;

  @Autowired private RxContainerItemBuilder containerItemBuilder;

  @Autowired private ShipmentSelectorService shipmentSelector;
  @Autowired private UpdateInstructionServiceHelper updateInstructionServiceHelper;

  @Autowired
  UpdateInstructionDataValidator updateInstructionDataValidator;

  @Transactional(readOnly = true)
  public InstructionResponse updateInstruction(
      Long instructionId,
      UpdateInstructionRequest instructionUpdateRequestFromClient,
      String parentTrackingId,
      HttpHeaders httpHeaders)
      throws ReceivingException {

    // Instruction should exist
    Instruction instruction4mDB = instructionPersisterService.getInstructionById(instructionId);

    // Instruction should not be completed state
    // Received update for already completed instruction
    instructionStateValidator.validate(instruction4mDB);

    Long deliveryNumber = instruction4mDB.getDeliveryNumber();
    String purchaseReferenceNumber = instruction4mDB.getPurchaseReferenceNumber();
    Integer purchaseReferenceLineNumber = instruction4mDB.getPurchaseReferenceLineNumber();

    boolean epcisOverridden = false;
    if (StringUtils.isNotBlank(instructionUpdateRequestFromClient.getProblemTagId())
        && configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RxConstants.IS_EPCIS_PROBLEM_FALLBACK_TO_ASN,
            true)
        && configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_GDM_SHIPMENT_V4_ENABLED,
            false)) {
      epcisOverridden = true;
    }

    // Delivery confirmation check → Keep in RX

    // User verification → Keep in Rx
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    ReceivingUtils.verifyUser(instruction4mDB, userId, RequestType.UPDATE);

    DocumentLine documentLine =
        instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);

    // Validate User Entered if isEpcisEnabledVendor is enabled
    ItemData additionalInfo = null;
    if (null != instruction4mDB.getDeliveryDocument()) {
      additionalInfo =
          gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class)
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo();
      if (RxUtils.isEpcisEnabledVendor(additionalInfo)
          && CollectionUtils.isNotEmpty(
              instructionUpdateRequestFromClient.getUserEnteredDataList())) {
        return validateUserEnteredQty(instructionUpdateRequestFromClient, instruction4mDB);
      }
    }

    Optional<FitProblemTagResponse> fitProblemTagResponseOptional =
        rxInstructionHelperService.getFitProblemTagResponse(instruction4mDB.getProblemTagId());

    Boolean epcisEnabled =
        configUtils.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.IS_GDM_SHIPMENT_V4_ENABLED,
                false)
            && additionalInfo.getIsEpcisEnabledVendor()
            && !epcisOverridden
            && !additionalInfo.getAutoSwitchEpcisToAsn()
            && !(fitProblemTagResponseOptional.isPresent()
                && RxUtils.isASNReceivingOverrideEligible(fitProblemTagResponseOptional.get()));

    if (isUpcReceiving(instruction4mDB)) {
      return updateUpcInstruction(instruction4mDB, instructionUpdateRequestFromClient, httpHeaders);
    }

    // Rx detail is not available
    List<ScannedData> scannedDataList = instructionUpdateRequestFromClient.getScannedDataList();
    Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(scannedDataList);
    RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
    DeliveryDocument deliveryDocument4mDB =
        gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine4mDB =
        deliveryDocument4mDB.getDeliveryDocumentLines().get(0);

    if (!configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_GDM_SHIPMENT_V4_ENABLED,
            false)
        || !RxUtils.isEpcisEnabledVendor(additionalInfo)) {
      rxInstructionHelperService.verify2DBarcodeLotWithShipmentLot(
          true, deliveryDocument4mDB, deliveryDocumentLine4mDB, scannedDataMap);
    }

    // TODO need to check this change
    if (fitProblemTagResponseOptional.isPresent()) {
      rxInstructionHelperService.checkIfContainerIsCloseDated(
          fitProblemTagResponseOptional.get(), scannedDataMap);
    } else {
      rxInstructionHelperService.checkIfContainerIsCloseDated(scannedDataMap);
    }

    String scannedCaseTrackingId = null;
    if (Objects.nonNull(scannedDataMap.get(ReceivingConstants.KEY_GTIN))
            && Objects.nonNull(scannedDataMap.get(ReceivingConstants.KEY_SERIAL))) {
      // For checking container exists
      String gtin = scannedDataMap.get(ReceivingConstants.KEY_GTIN).getValue();
      String serial = scannedDataMap.get(ReceivingConstants.KEY_SERIAL).getValue();
      updateInstructionDataValidator.validateContainerDoesNotAlreadyExist(
              deliveryDocument4mDB.getPoTypeCode(),
              gtin,
              serial,
              TenantContext.getFacilityNum(),
              TenantContext.getFacilityCountryCode()
      );
      scannedCaseTrackingId = generateTrackingId(httpHeaders);
    }
    Integer quantityToBeReceivedInVNPK =
        ReceivingUtils.conversionToVendorPack(
            documentLine.getQuantity(),
            documentLine.getQuantityUOM(),
            documentLine.getVnpkQty(),
            documentLine.getWhpkQty());
    Integer quantityToBeReceivedInEaches =
        ReceivingUtils.conversionToEaches(
            documentLine.getQuantity(),
            documentLine.getQuantityUOM(),
            documentLine.getVnpkQty(),
            documentLine.getWhpkQty());

    // For EPCIS serialized flow, validate Scanned 2D . If pass , increment
    // auditCompletedQty .Else raise exception.
    ManufactureDetail selectedPackDetails = null;

    Integer receivedQuantity = instruction4mDB.getReceivedQuantity();
    Integer projectedQuantity = instruction4mDB.getProjectedReceiveQty();

    // This query is executing 2 times in the this update instruction flow.
    // change code to limit to 1
    Long receiveQtySummary =
        receiptService.receivedQtyByDeliveryPoAndPoLineInEaches(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);

    long totalReceivedQty = Objects.isNull(receiveQtySummary) ? 0 : receiveQtySummary;

    ShipmentDetails autoSelectedShipment = autoSelectShipment(deliveryDocumentLine4mDB);
    verifyIfCaseCanBeReceived(
        totalReceivedQty,
        projectedQuantity,
        deliveryDocumentLine4mDB,
        receivedQuantity,
        quantityToBeReceivedInEaches,
        autoSelectedShipment);
    verifySerializedData(
        scannedDataMap, autoSelectedShipment, deliveryDocumentLine4mDB, httpHeaders);

    // Objects filled in this will get persisted into DB
    List<Container> containers = new ArrayList<>();
    List<ContainerItem> containerItems = new ArrayList<>();

    List<Receipt> receipts =
        rxReceiptsBuilder.buildReceipts(
            instruction4mDB,
            instructionUpdateRequestFromClient,
            userId,
            quantityToBeReceivedInEaches,
            quantityToBeReceivedInVNPK,
            autoSelectedShipment.getInboundShipmentDocId());
    instruction4mDB.setReceivedQuantity(
        instruction4mDB.getReceivedQuantity() + quantityToBeReceivedInEaches);
    Container parentContainer = null;
    // Parent container
    if (Objects.isNull(instruction4mDB.getContainer())) {
      String parentContainerTrackingId = generateTrackingId(httpHeaders);
      ContainerDetails parentContainerDetails =
          buildContainerDetails(deliveryDocument4mDB, parentContainerTrackingId);
      instruction4mDB.setContainer(parentContainerDetails);

      // create parent container
      parentContainer =
          containerService.constructContainer(
              instruction4mDB,
              parentContainerDetails,
              Boolean.TRUE,
              Boolean.TRUE,
              instructionUpdateRequestFromClient);
      parentContainer.setLastChangedTs(new Date());
      parentContainer.setLastChangedUser(userId);
      populateInstructionCodeInContainerMiscInfo(
          parentContainer, instruction4mDB, epcisEnabled, selectedPackDetails);
      // add to db list
      containers.add(parentContainer);

      // create parent container item
      ContainerItem parentContainerItem =
          containerItemBuilder.build(
              parentContainerTrackingId,
              instruction4mDB,
              instructionUpdateRequestFromClient,
              Collections.emptyMap());
      parentContainerItem.setRotateDate(updateInstructionServiceHelper.parseAndGetRotateDate());
      parentContainerItem.setQuantity(quantityToBeReceivedInEaches);
      parentContainerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
      // add to db list
      containerItems.add(parentContainerItem);
    } else {
      Integer quantityReceivedInEaches =
          ReceivingUtils.conversionToEaches(
              instruction4mDB.getReceivedQuantity(),
              instruction4mDB.getReceivedQuantityUOM(),
              documentLine.getVnpkQty(),
              documentLine.getWhpkQty());
      Pair<Container, ContainerItem> updatedContainerContainerItem =
          updateContainerQuantity(
              instruction4mDB.getContainer().getTrackingId(), quantityReceivedInEaches, userId);
      // add to db list
      containers.add(updatedContainerContainerItem.getKey());
      containerItems.add(updatedContainerContainerItem.getValue());
    }

    List<ContainerDetails> instructionChildContainers4mDB = instruction4mDB.getChildContainers();
    if (CollectionUtils.isEmpty(instructionChildContainers4mDB)) {
      instructionChildContainers4mDB = new ArrayList<>();
    }
    // Eaches Parent Container
    String caseContainerTrackingId =
        StringUtils.isNotBlank(parentTrackingId) ? parentTrackingId : null;
    if (ReceivingConstants.Uom.EACHES.equalsIgnoreCase(documentLine.getQuantityUOM())) {
      if (StringUtils.isBlank(parentTrackingId)) {
        caseContainerTrackingId = generateTrackingId(httpHeaders);
        ContainerDetails eachesParentContainerDetails =
            buildContainerDetails(deliveryDocument4mDB, caseContainerTrackingId);
        eachesParentContainerDetails.setParentTrackingId(
            instruction4mDB.getContainer().getTrackingId());

        // create parent container
        Container eachesParentContainer =
            containerService.constructContainer(
                instruction4mDB,
                eachesParentContainerDetails,
                Boolean.TRUE,
                Boolean.TRUE,
                instructionUpdateRequestFromClient);
        eachesParentContainer.setParentTrackingId(instruction4mDB.getContainer().getTrackingId());
        eachesParentContainer.setLastChangedTs(new Date());
        eachesParentContainer.setLastChangedUser(userId);
        populateInstructionCodeInContainerMiscInfo(
            eachesParentContainer, instruction4mDB, epcisEnabled, selectedPackDetails);
        // add to db list
        containers.add(eachesParentContainer);

        // create parent container item
        ContainerItem eachesParentContainerItem =
            containerItemBuilder.build(
                caseContainerTrackingId,
                instruction4mDB,
                instructionUpdateRequestFromClient,
                scannedDataMap);
        eachesParentContainerItem.setRotateDate(updateInstructionServiceHelper.parseAndGetRotateDate());
        eachesParentContainerItem.setQuantity(quantityToBeReceivedInEaches);
        eachesParentContainerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

        ManufactureDetail scannedCase = null;

        if (null != deliveryDocumentLine4mDB.getAdditionalInfo().getScannedCase()) {
          scannedCase = deliveryDocumentLine4mDB.getAdditionalInfo().getScannedCase();
        }

        if (Boolean.TRUE.equals(epcisEnabled)
                && (null != scannedCase)
                && (!StringUtils.isAnyBlank(
                scannedCase.getExpiryDate(), scannedCase.getGtin(), scannedCase.getLot(), scannedCase.getSerial()
        ))) {
          eachesParentContainerItem.setGtin(
                  scannedCase.getGtin());
          eachesParentContainerItem.setLotNumber(
                  scannedCase.getLot());
          eachesParentContainerItem.setSerial(
                  scannedCase.getSerial());
          try {
            eachesParentContainerItem.setExpiryDate(
                DateUtils.parseDate(
                        scannedCase.getExpiryDate(),
                    ReceivingConstants.SIMPLE_DATE));
          } catch (ParseException e) {
            throw new ReceivingBadDataException(
                ExceptionCodes.INVALID_SCANNED_DATA_EXPIRY_DATE,
                RxConstants.INVALID_SCANNED_DATA_EXPIRY_DATE);
          }
        }

        // add to db list
        containerItems.add(eachesParentContainerItem);

        caseContainerTrackingId = eachesParentContainerDetails.getTrackingId();
        instruction4mDB
            .getContainer()
            .getContents()
            .get(0)
            .setQty(containerItems.get(0).getQuantity());
      } else {
        instruction4mDB
            .getContainer()
            .getContents()
            .get(0)
            .setQty(containerItems.get(0).getQuantity());
        containerItems.add(
            updateContainerItemQuantity(
                parentTrackingId, instruction4mDB.getContainer().getContents().get(0).getQty()));
      }
    }

    Content content = InstructionUtils.createContent(deliveryDocument4mDB);
    content.setGtin(scannedDataMap.get(ReceivingConstants.KEY_GTIN).getValue());
    content.setLot(scannedDataMap.get(ReceivingConstants.KEY_LOT).getValue());
    content.setSerial(scannedDataMap.get(ReceivingConstants.KEY_SERIAL).getValue());
    content.setRotateDate(DateFormatUtils.format(updateInstructionServiceHelper.parseAndGetRotateDate(), ReceivingConstants.SIMPLE_DATE));

    content.setQty(quantityToBeReceivedInEaches);
    content.setQtyUom(ReceivingConstants.Uom.EACHES);

    ContainerDetails scannedContainerDetails = new ContainerDetails();
    scannedContainerDetails.setTrackingId(scannedCaseTrackingId);
    scannedContainerDetails.setContents(Arrays.asList(content));

    String scannedCaseParentTrackingId =
        StringUtils.isNotEmpty(caseContainerTrackingId)
            ? caseContainerTrackingId
            : instruction4mDB.getContainer().getTrackingId();
    scannedContainerDetails.setParentTrackingId(scannedCaseParentTrackingId);

    instructionChildContainers4mDB.add(scannedContainerDetails);

    Container scannedContainer =
        containerService.constructContainer(
            instruction4mDB,
            scannedContainerDetails,
            Boolean.TRUE,
            Boolean.TRUE,
            instructionUpdateRequestFromClient);
    scannedContainer.setParentTrackingId(scannedCaseParentTrackingId);
    scannedContainer.setLastChangedTs(new Date());
    scannedContainer.setLastChangedUser(userId);
    scannedContainer.setSsccNumber(
        ofNullable(scannedDataMap.get(ApplicationIdentifier.SSCC.getKey()))
            .map(s -> s.getApplicationIdentifier() + s.getValue())
            .orElse(null));
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(
        RxConstants.SHIPMENT_DOCUMENT_ID, autoSelectedShipment.getInboundShipmentDocId());
    containerMiscInfo.put(RxConstants.INSTRUCTION_CODE, instruction4mDB.getInstructionCode());
    containerMiscInfo.put(ReceivingConstants.IS_AUDITED, true);
    scannedContainer.setAudited(true);
    scannedContainer.setContainerMiscInfo(containerMiscInfo);
    populateInstructionCodeInContainerMiscInfo(
        scannedContainer, instruction4mDB, epcisEnabled, selectedPackDetails);
    // add to db list
    containers.add(scannedContainer);

    // create container item
    ContainerItem scannedContainerItem =
        containerItemBuilder.build(
            scannedCaseTrackingId,
            instruction4mDB,
            instructionUpdateRequestFromClient,
            scannedDataMap);
    scannedContainerItem.setQuantity(quantityToBeReceivedInEaches);
    scannedContainerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);

    // add to db list
    containerItems.add(scannedContainerItem);

    instruction4mDB.setChildContainers(instructionChildContainers4mDB);
    instruction4mDB.setLastChangeUserId(userId);
    instruction4mDB.setLastChangeTs(new Date());
    persistForUpdateInstruction(
        instruction4mDB,
        containers,
        containerItems,
        receipts,
        scannedContainerItem.getSerial(),
        scannedContainerItem.getLotNumber());

    if (rxManagedConfig.isTrimUpdateInstructionResponseEnabled()) {
      // This reduces the response payload size
      instruction4mDB.setChildContainers(Collections.emptyList());
    }
    instruction4mDB = RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(instruction4mDB);

    // send instruction response
    if (!configUtils.isPrintingAndroidComponentEnabled()) {
      return new InstructionResponseImplOld(null, null, instruction4mDB, null);
    }
    return new InstructionResponseImplNew(null, null, instruction4mDB, null);
  }

  public InstructionResponse validateUserEnteredQty(
      UpdateInstructionRequest instructionUpdateRequestFromClient, Instruction instruction4mDB) {
    ItemData additionalInfo =
        gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo();

    DocumentLine documentLine =
        instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
    Integer quantityToBeReceivedInVNPK = 0, quantityToBeReceivedInWhpk = 0;

    Integer caseCount = 0, eachCount = 0;
    List<ScannedData> enteredDataList = instructionUpdateRequestFromClient.getUserEnteredDataList();
    Map<String, ScannedData> enteredDataMap = RxUtils.scannedDataMap(enteredDataList);

    if (null != enteredDataMap.get(ReceivingConstants.ZA)) {

      quantityToBeReceivedInVNPK =
          ReceivingUtils.conversionToVendorPack(
              instruction4mDB.getProjectedReceiveQty(),
              instruction4mDB.getProjectedReceiveQtyUOM(),
              documentLine.getVnpkQty(),
              documentLine.getWhpkQty());

      caseCount = Integer.valueOf(enteredDataMap.get(ReceivingConstants.ZA).getValue());
      LOG.info(
          "validateUserEnteredQty ::ZA validation:: quantityToBeReceivedInVNPK {} :: caseCount {}",
          quantityToBeReceivedInVNPK,
          caseCount);

      if (caseCount > 0 && caseCount.equals(quantityToBeReceivedInVNPK) && !additionalInfo.isPalletFlowInMultiSku()) {
        additionalInfo.setAuditQty(
            Integer.valueOf(
                configUtils.getCcmValue(
                    TenantContext.getFacilityNum(),
                    ReceivingConstants.DEFAULT_PALLET_AUDIT_COUNT,
                    ReceivingConstants.ONE.toString())));
      } else {
        additionalInfo.setAuditQty(quantityToBeReceivedInVNPK);
      }
    }

    if (null != enteredDataMap.get(ReceivingConstants.EA)) {

      quantityToBeReceivedInWhpk =
          ReceivingUtils.conversionToWareHousePack(
              additionalInfo.getScannedCaseAttpQty(),
              additionalInfo.getScannedCaseAttpQtyUOM(),
              documentLine.getVnpkQty(),
              documentLine.getWhpkQty());

      eachCount = Integer.valueOf(enteredDataMap.get(ReceivingConstants.EA).getValue());
      LOG.info(
          "validateUserEnteredQty ::EA validation:: quantityToBeReceivedInWhpk {} :: eachCount {}",
          quantityToBeReceivedInWhpk,
          eachCount);
      if (eachCount > 0
          && eachCount.equals(quantityToBeReceivedInWhpk)
          && !instruction4mDB
              .getInstructionCode()
              .equals(RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType())) {
        additionalInfo.setAuditQty(
            Integer.valueOf(
                configUtils.getCcmValue(
                    TenantContext.getFacilityNum(),
                    ReceivingConstants.DEFAULT_CASE_AUDIT_COUNT,
                    ReceivingConstants.ONE.toString())));
      } else {
        additionalInfo.setAuditQty(quantityToBeReceivedInWhpk);
      }
    }
    additionalInfo.setQtyValidationDone(Boolean.TRUE);
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    // deliveryDocument.getDeliveryDocumentLines().set(0, deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    instruction4mDB.setDeliveryDocument(gson.toJson(deliveryDocument));
    rxInstructionHelperService.saveInstruction(instruction4mDB);

    return new InstructionResponseImplNew(null, null, instruction4mDB, null);
  }

  private void persistForUpdateInstruction(
      Instruction instruction4mDB,
      List<Container> containers,
      List<ContainerItem> containerItems,
      List<Receipt> receipts,
      String serial,
      String lotNumber) {
    long startTimeOfDBTx = System.currentTimeMillis();
    try {
      rxInstructionHelperService.persistForUpdateInstruction(
          instruction4mDB, receipts, containers, containerItems);
    } catch (ObjectOptimisticLockingFailureException e) {
      String errorCode = ExceptionCodes.INSTR_OPTIMISTIC_LOCK_GENERIC_ERROR;
      String errorDescription = RxConstants.INSTR_OPTIMISTIC_LOCK_GENERIC_ERROR;
      if (StringUtils.isNotBlank(serial) && StringUtils.isNotBlank(lotNumber)) {
        errorCode = ExceptionCodes.INSTR_OPTIMISTIC_LOCK_ERROR;
        errorDescription =
            String.format(RxConstants.INSTR_OPTIMISTIC_LOCK_ERROR, serial, lotNumber);
      }
      throw new ReceivingConflictException(errorCode, errorDescription, serial, lotNumber);
    } finally {
      long timeTakenInMills =
          ReceivingUtils.getTimeDifferenceInMillis(startTimeOfDBTx, System.currentTimeMillis());
      LOG.warn(DB_PROCESS_TIME_LOG_MESSAGE, timeTakenInMills, TenantContext.getCorrelationId());
    }
  }

  private Pair<Container, ContainerItem> updateContainerQuantity(
      String trackingId, int quantityInEaches, String userId) throws ReceivingException {

    Container container = containerService.getContainerByTrackingId(trackingId);
    if (Objects.isNull(container)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.CONTAINER_NOT_FOUND, ReceivingException.MATCHING_CONTAINER_NOT_FOUND);
    }

    container.setLastChangedTs(new Date());
    container.setLastChangedUser(userId);

    // create parent container item
    ContainerItem containerItem = container.getContainerItems().get(0);
    containerItem.setQuantity(quantityInEaches);
    return new Pair<>(container, containerItem);
  }

  private ContainerItem updateContainerItemQuantity(
      String parentTrackingId, Integer receivedEachQty) {

    ContainerItem parentContainerItem = null;
    List<ContainerItem> containerItems = containerItemService.findByTrackingId(parentTrackingId);
    if (!CollectionUtils.isEmpty(containerItems) && receivedEachQty > 0) {
      parentContainerItem = containerItems.get(0);
      parentContainerItem.setQuantity(receivedEachQty);
    }
    return parentContainerItem;
  }

  private ContainerDetails buildContainerDetails(
      DeliveryDocument deliveryDocument4mDB, String trackingId) {

    Content content = InstructionUtils.createContent(deliveryDocument4mDB);
    content.setQtyUom(ReceivingConstants.Uom.EACHES);
    content.setRotateDate(DateFormatUtils.format(updateInstructionServiceHelper.parseAndGetRotateDate(), ReceivingConstants.SIMPLE_DATE));

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setTrackingId(trackingId);
    containerDetails.setContents(Arrays.asList(content));

    return containerDetails;
  }

  private boolean isUpcReceiving(Instruction instruction4mDB) {
    return StringUtils.isEmpty(instruction4mDB.getSsccNumber())
        && RxUtils.isUpcReceivingInstruction(instruction4mDB.getInstructionCode());
  }

  private void verifySerializedData(
      Map<String, ScannedData> scannedDataMap,
      ShipmentDetails shipmentDetails,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders) {
    epcisService.verifySerializedData(
        scannedDataMap, shipmentDetails, deliveryDocumentLine, httpHeaders);
  }
  private void verifyIfCaseCanBeReceived(
      Long totalReceivedQty4DeliveryPoPoLine,
      Integer projectedQuantity,
      DeliveryDocumentLine deliveryDocumentLine4mDB,
      Integer receivedQuantity,
      Integer quantityToBeReceived,
      ShipmentDetails autoSelectedShipment) {

    // If quantity exceeds limit for SSCC cases in ASN
    Integer totalQuantityValueAfterReceiving =
        totalQuantityValueAfterReceiving(receivedQuantity, quantityToBeReceived);

    if (totalQuantityValueAfterReceiving > projectedQuantity) {
      throw new ReceivingBadDataException(
          ExceptionCodes.ALLOWED_CASES_RECEIVED, RxConstants.UPDATE_INSTRUCTION_EXCEEDS_QUANTITY);
    }

    // Update cannot receive more than cases allowed in ASN per pallet
    if (RxUtils.deriveProjectedReceiveQtyInEaches(
            deliveryDocumentLine4mDB, totalReceivedQty4DeliveryPoPoLine, 0)
        <= 0) {
      throw new ReceivingBadDataException(
          ExceptionCodes.ALL_CASES_RECEIVED,
          RxConstants.UPDATE_INSTRUCTION_EXCEEDS_PALLET_QUANTITY);
    }

    if (CollectionUtils.isEmpty(deliveryDocumentLine4mDB.getShipmentDetailsList())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.SHIPMENT_UNAVAILABLE, RxConstants.SHIPMENT_DETAILS_UNAVAILABLE);
    }
  }

  private Integer totalQuantityValueAfterReceiving(
      Integer receivedQuantity, Integer quantityToBeReceivied) {
    return receivedQuantity + quantityToBeReceivied;
  }

  private InstructionResponse updateUpcInstruction(
      Instruction instruction4mDB,
      UpdateInstructionRequest instructionUpdateRequestFromClient,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    DocumentLine documentLine =
        instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
    DeliveryDocument deliveryDocument4mDB =
        gson.fromJson(instruction4mDB.getDeliveryDocument(), DeliveryDocument.class);
    Integer vendorPackQty =
        ReceivingUtils.conversionToVendorPack(
            documentLine.getQuantity(),
            documentLine.getQuantityUOM(),
            documentLine.getVnpkQty(),
            documentLine.getWhpkQty());
    Integer eachQty =
        ReceivingUtils.conversionToEaches(
            documentLine.getQuantity(),
            documentLine.getQuantityUOM(),
            documentLine.getVnpkQty(),
            documentLine.getWhpkQty());
    Integer projectedQuantity = instruction4mDB.getProjectedReceiveQty();

    if (eachQty > projectedQuantity) {
      throw new ReceivingBadDataException(
          ExceptionCodes.ALLOWED_CASES_RECEIVED, RxConstants.UPDATE_INSTRUCTION_EXCEEDS_QUANTITY);
    }
    List<Receipt> receipts = new ArrayList<>();
    if (instruction4mDB.getReceivedQuantity() > 0) {
      receipts.add(
          rxReceiptsBuilder.buildReceiptToRollbackInEaches(
              instruction4mDB,
              userId,
              ReceivingUtils.conversionToVendorPack(
                  instruction4mDB.getReceivedQuantity(),
                  instruction4mDB.getReceivedQuantityUOM(),
                  documentLine.getVnpkQty(),
                  documentLine.getWhpkQty()),
              instruction4mDB.getReceivedQuantity()));
    }
    instruction4mDB.setReceivedQuantity(eachQty);
    if (ReceivingConstants.Uom.EACHES.equalsIgnoreCase(documentLine.getQuantityUOM())) {
      vendorPackQty = 1;
      instruction4mDB.setReceivedQuantity(documentLine.getQuantity());
      instruction4mDB.setReceivedQuantityUOM(ReceivingConstants.Uom.EACHES);
    }

    List<Container> containers4DB = new ArrayList<>();
    List<ContainerItem> containerItems4DB = new ArrayList<>();
    // Parent container
    if (Objects.isNull(instruction4mDB.getContainer())) {
      String parentContainerTrackingId = generateTrackingId(httpHeaders);
      ContainerDetails parentContainerDetails =
          buildContainerDetails(deliveryDocument4mDB, parentContainerTrackingId);
      instruction4mDB.setContainer(parentContainerDetails);

      // create parent container
      Container parentContainer =
          containerService.constructContainer(
              instruction4mDB,
              parentContainerDetails,
              Boolean.TRUE,
              Boolean.TRUE,
              instructionUpdateRequestFromClient);
      parentContainer.setLastChangedTs(new Date());
      parentContainer.setLastChangedUser(userId);
      Map<String, Object> containerMiscInfo = parentContainer.getContainerMiscInfo();
      if (MapUtils.isEmpty(containerMiscInfo)) {
        containerMiscInfo = new HashMap<>();
      }
      containerMiscInfo.put(RxConstants.INSTRUCTION_CODE, instruction4mDB.getInstructionCode());
      parentContainer.setContainerMiscInfo(containerMiscInfo);

      // add to db list
      containers4DB.add(parentContainer);

      // create parent container item for Exempt Item
      ContainerItem parentContainerItem =
          containerItemBuilder.build(
              parentContainerTrackingId,
              instruction4mDB,
              instructionUpdateRequestFromClient,
              Collections.emptyMap());
      parentContainerItem.setQuantity(eachQty);
      parentContainerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
      // add to db list
      containerItems4DB.add(parentContainerItem);
    } else {
      Integer quantityReceivedInEaches =
          ReceivingUtils.conversionToEaches(
              instruction4mDB.getReceivedQuantity(),
              instruction4mDB.getReceivedQuantityUOM(),
              documentLine.getVnpkQty(),
              documentLine.getWhpkQty());
      Pair<Container, ContainerItem> updatedContainerContainerItem =
          updateContainerQuantity(
              instruction4mDB.getContainer().getTrackingId(), quantityReceivedInEaches, userId);
      // add to db list
      containers4DB.add(updatedContainerContainerItem.getKey());
      containerItems4DB.add(updatedContainerContainerItem.getValue());
    }

    receipts.addAll(
        rxReceiptsBuilder.buildReceipts(
            instruction4mDB,
            instructionUpdateRequestFromClient,
            userId,
            eachQty,
            vendorPackQty,
            null));

    persistForUpdateInstruction(
        instruction4mDB, containers4DB, containerItems4DB, receipts, null, null);
    instruction4mDB = RxUtils.convertQuantityToVnpkOrWhpkBasedOnInstructionType(instruction4mDB);

    if (!configUtils.isPrintingAndroidComponentEnabled()) {
      return new InstructionResponseImplOld(null, null, instruction4mDB, null);
    }
    return new InstructionResponseImplNew(null, null, instruction4mDB, null);
  }

  private String generateTrackingId(HttpHeaders httpHeaders) {
    String trackingId = lpnCacheService.getLPNBasedOnTenant(httpHeaders);
    if (StringUtils.isBlank(trackingId)) {
      throw new ReceivingBadDataException(ExceptionCodes.INVALID_LPN, RxConstants.INVALID_LPN);
    }
    return trackingId;
  }

  private ShipmentDetails autoSelectShipment(DeliveryDocumentLine deliveryDocumentLine) {
    return shipmentSelector.autoSelectShipment(deliveryDocumentLine);
  }

  private void populateInstructionCodeInContainerMiscInfo(
      Container container,
      Instruction instruction,
      Boolean epcisEnabled,
      ManufactureDetail manufactureDetail) {
    Map<String, Object> containerMiscInfo =
        Objects.isNull(container.getContainerMiscInfo())
            ? new HashMap<>()
            : container.getContainerMiscInfo();
    containerMiscInfo.put(RxConstants.INSTRUCTION_CODE, instruction.getInstructionCode());
    containerMiscInfo.put(ReceivingConstants.IS_EPCIS_ENABLED_VENDOR, epcisEnabled);
    if (Objects.nonNull(manufactureDetail)) {
      containerMiscInfo.put(RxConstants.DOCUMENT_ID, manufactureDetail.getDocumentId());
      containerMiscInfo.put(RxConstants.SHIPMENT_NUMBER, manufactureDetail.getShipmentNumber());
      containerMiscInfo.put(RxConstants.DOCUMENT_PACK_ID, manufactureDetail.getDocumentPackId());
    }

    container.setContainerMiscInfo(containerMiscInfo);
  }
}
