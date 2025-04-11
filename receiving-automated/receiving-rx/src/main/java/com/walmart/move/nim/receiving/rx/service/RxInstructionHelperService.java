package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FIXIT_ISSUE_TYPE_DI;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.FitProblemTagResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RxInstructionHelperService {
  private static final Logger LOG = LoggerFactory.getLogger(RxInstructionHelperService.class);

  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private PrintJobService printJobService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private ReceiptService receiptService;
  @Autowired private ContainerItemService containerItemService;
  @Autowired private ContainerService containerService;

  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private RxManagedConfig rxManagedConfig;
  @Autowired private Gson gson;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = "containerTransformer")
  private Transformer<Container, ContainerDTO> transformer;

  @Resource(name = ReceivingConstants.DEFAULT_LPN_CACHE_SERVICE)
  private LPNCacheService lpnCacheService;

  @Transactional
  @InjectTenantFilter
  public void persist(Container container, Instruction instruction, String userId) {

    List<Container> childContainers = new ArrayList<>();
    for (Container childContainer : container.getChildContainers()) {
      childContainers.add(childContainer);
    }

    // persisting container & containerItem
    containerPersisterService.saveContainer(container);

    // persisting child containers
    containerPersisterService.saveContainers(childContainers);

    // persisting Instruction.
    instructionPersisterService.saveInstruction(instruction);

    // persisting printJob.
    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add(container.getTrackingId());
    printJobService.createPrintJob(
        instruction.getDeliveryNumber(), instruction.getId(), printJobLpnSet, userId);
  }

  @Transactional
  @InjectTenantFilter
  public void persist(
      List<Container> containers,
      List<ContainerItem> containerItems,
      List<Instruction> instructions,
      String userId) {

    List<Container> childContainers = new ArrayList<>();
    for (Container container : containers) {
      for (Container childContainer : container.getChildContainers()) {
        childContainers.add(childContainer);
      }
    }

    // persisting container & containerItem
    containerPersisterService.saveContainerAndContainerItems(containers, containerItems);

    // persisting child containers
    containerPersisterService.saveContainers(childContainers);

    // persisting Instruction.
    instructionPersisterService.saveAllInstruction(instructions);
  }

  @InjectTenantFilter
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistForUpdateInstruction(
      Instruction instruction,
      List<Receipt> receipts,
      List<Container> containers,
      List<ContainerItem> containerItems) {
    if (Objects.nonNull(instruction)) {
      instructionPersisterService.saveInstruction(instruction);
    }
    if (CollectionUtils.isNotEmpty(receipts)) {
      receiptService.saveAll(receipts);
    }
    if (CollectionUtils.isNotEmpty(containers)) {
      containerPersisterService.saveContainers(containers);
    }
    if (CollectionUtils.isNotEmpty(containerItems)) {
      containerItemService.saveAll(containerItems);
    }
  }

  @InjectTenantFilter
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveInstruction(Instruction instruction) {
    if (Objects.nonNull(instruction)) {
      instructionPersisterService.saveInstruction(instruction);
    }
  }

  public void checkIfContainerIsCloseDated(
      FitProblemTagResponse problemLabelResponse, Map<String, ScannedData> scannedDataMap) {
    if (appConfig.isCloseDateCheckEnabled()) {
      String problemType = "";
      if (Objects.nonNull(problemLabelResponse)
          && Objects.nonNull(problemLabelResponse.getIssue())) {
        problemType = problemLabelResponse.getIssue().getType();
      }
      if (!FIXIT_ISSUE_TYPE_DI.equalsIgnoreCase(problemType)) {
        if (checkIfContainerIsCloseDated(scannedDataMap, appConfig.getCloseDateLimitDays())) {
          throw new ReceivingBadDataException(
              ExceptionCodes.CLOSE_DATED_ITEM, RxConstants.CLOSE_DATED_ITEM);
        }
      } else {
        if (checkIfContainerIsCloseDated(scannedDataMap, 0)) { // If already expired date.
          throw new ReceivingBadDataException(
              ExceptionCodes.EXPIRED_ITEM, RxConstants.EXPIRED_ITEM);
        }
        LOG.info(
            "Close Dated item is being received for Problem Ticket {}, problemType {}",
            problemLabelResponse.getLabel(),
            problemType);
      }
    }
  }

  public void checkIfContainerIsCloseDated(Map<String, ScannedData> scannedDataMap) {
    if (appConfig.isCloseDateCheckEnabled()
        && checkIfContainerIsCloseDated(scannedDataMap, appConfig.getCloseDateLimitDays())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.CLOSE_DATED_ITEM, RxConstants.CLOSE_DATED_ITEM);
    }
  }

  private boolean checkIfContainerIsCloseDated(
      Map<String, ScannedData> scannedDataMap, int closeDateLimitDays) {
    Date thresholdDate =
        Date.from(
            Instant.now().plus(closeDateLimitDays, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS));
    try {
      Date expDate =
          DateUtils.parseDate(
              scannedDataMap.get(ReceivingConstants.KEY_EXPIRY_DATE).getValue(),
              ReceivingConstants.EXPIRY_DATE_FORMAT);
      return expDate.before(thresholdDate);
    } catch (ParseException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SCANNED_DATA, RxConstants.INVALID_SCANNED_DATA_EXPIRY_DATE);
    }
  }

  public Optional<FitProblemTagResponse> getFitProblemTagResponse(String problemTagId) {
    if (StringUtils.isNotBlank(problemTagId)) {
      ProblemService configuredProblemService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.PROBLEM_SERVICE,
              ProblemService.class);
      ProblemLabel problemLabelByProblemTagId =
          configuredProblemService.findProblemLabelByProblemTagId(problemTagId);
      if (Objects.isNull(problemLabelByProblemTagId)
          || Objects.isNull(problemLabelByProblemTagId.getProblemResponse())) {
        throw new ReceivingBadDataException(
            ExceptionCodes.PROBLEM_NOT_FOUND, ReceivingException.PROBLEM_NOT_FOUND);
      }
      return Optional.of(
          gson.fromJson(
              problemLabelByProblemTagId.getProblemResponse(), FitProblemTagResponse.class));
    }
    return Optional.empty();
  }

  public void sameItemOnProblem(
      FitProblemTagResponse problemLabelResponse, DeliveryDocumentLine deliveryDocumentLine) {
    if (!getWhiteListedProblemTypes().contains(problemLabelResponse.getIssue().getType())) {
      if (!Objects.equals(
          deliveryDocumentLine.getItemNbr(), problemLabelResponse.getIssue().getItemNumber())) {
        if (Objects.nonNull(problemLabelResponse.getIssue().getItemNumber())) {
          throw new ReceivingBadDataException(
              ExceptionCodes.PROBLEM_ITEM_DOES_NOT_MATCH,
              ReceivingException.PROBLEM_ITEM_DOES_NOT_MATCH);
        } else {
          throw new ReceivingBadDataException(
              ExceptionCodes.PROBLEM_TICKET_MISSING_ITEM,
              ReceivingException.PROBLEM_TICKET_MISSING_ITEM);
        }
      }
    }
  }

  public Instruction checkIfInstructionExistsBeforeAllowingPartialReceiving(
      String deliveryNumber, String upcNumber, String userId) {
    Instruction existingInstruction = null;
    List<Instruction> instructionsByDeliveryAndGtin =
        instructionPersisterService.findInstructionByDeliveryAndGtin(Long.valueOf(deliveryNumber));
    instructionsByDeliveryAndGtin =
        RxUtils.filterInstructionMatchingGtin(instructionsByDeliveryAndGtin, upcNumber);
    Optional<Instruction> opartialInstruction = Optional.empty();
    if (CollectionUtils.isNotEmpty(instructionsByDeliveryAndGtin)) {
      opartialInstruction =
          instructionsByDeliveryAndGtin
              .stream()
              .filter(instruction -> RxUtils.isPartialInstruction(instruction.getInstructionCode()))
              .findFirst();
    }

    if (opartialInstruction.isPresent()) {
      Instruction partialInstruction = opartialInstruction.get();
      if (userId.equalsIgnoreCase(partialInstruction.getCreateUserId())
          || userId.equalsIgnoreCase(partialInstruction.getLastChangeUserId())) {
        existingInstruction = partialInstruction;
      } else {
        throw new ReceivingBadDataException(
            ExceptionCodes.MULTI_INSTRUCTION_NOT_SUPPORTED,
            RxConstants.REQUEST_TRANSFTER_INSTR_ERROR_CODE);
      }
    } else if (CollectionUtils.isNotEmpty(
        instructionsByDeliveryAndGtin)) { // Regular instruction exists, has to be completed before
      // requesting partial
      // instruction
      throw new ReceivingBadDataException(
          ExceptionCodes.COMPLETE_EXISTING_INSTRUCTION, RxConstants.COMPLETE_EXISTING_INSTRUCTION);
    }
    return existingInstruction;
  }

  private List<String> getWhiteListedProblemTypes() {
    List<String> result = new ArrayList<>();
    String whiteListedProblemTypes = rxManagedConfig.getWhiteListedProblemTypes();
    if (StringUtils.isNotBlank(whiteListedProblemTypes)) {
      return Arrays.asList(StringUtils.split(whiteListedProblemTypes, ","));
    }
    return result;
  }

  public void verify2DBarcodeLotWithShipmentLot(
      boolean is2dBarcodeRequest,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      Map<String, ScannedData> scannedDataMap) {
    boolean isVendorWholesaler =
        RxUtils.isVendorWholesaler(
            deliveryDocument,
            rxManagedConfig.getWholesalerVendors(),
            rxManagedConfig.isWholesalerLotCheckEnabled());
    RxUtils.verify2DBarcodeLotWithShipmentLot(
        is2dBarcodeRequest,
        deliveryDocument,
        deliveryDocumentLine,
        scannedDataMap,
        isVendorWholesaler);
  }

  @Transactional
  @InjectTenantFilter
  public void rollbackContainers(
      List<String> trackingIds, Receipt receipt, Instruction instruction) {
    instructionPersisterService.saveInstruction(instruction);
    containerService.deleteContainersByTrackingIds(trackingIds);
    receiptService.saveReceipt(receipt);
  }

  @Transactional
  @InjectTenantFilter
  public void rollbackContainers(
      List<String> trackingIds, List<Receipt> receipts, Instruction instruction) {
    instructionPersisterService.saveInstruction(instruction);
    containerService.deleteContainersByTrackingIds(trackingIds);
    receiptService.saveAll(receipts);
  }

  @Transactional
  @InjectTenantFilter
  public void rollbackContainers(
      List<String> trackingIds, List<Receipt> receipts, List<Instruction> instructions) {
    if (CollectionUtils.isNotEmpty(instructions)) {
      instructionPersisterService.saveAllInstruction(instructions);
    }
    if (CollectionUtils.isNotEmpty(trackingIds)) {
      containerService.deleteContainersByTrackingIds(trackingIds);
    }
    if (CollectionUtils.isNotEmpty(receipts)) {
      receiptService.saveAll(receipts);
    }
  }

  public void validatePartialsInSplitPallet(
      InstructionRequest instructionRequest, boolean lessThanCase) {
    if (lessThanCase && RxUtils.isSplitPalletInstructionRequest(instructionRequest)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.PARTIALS_NOT_ALLOWED_IN_SPLIT_PALLET,
          RxConstants.PARTIALS_NOT_ALLOWED_IN_SPLIT_PALLET);
    }
  }

  public void updateDeliveryDocumentLineAdditionalInfo(List<DeliveryDocument> deliveryDocuments) {
    if (CollectionUtils.isNotEmpty(deliveryDocuments)) {
      deliveryDocuments.forEach(
          deliveryDocument ->
              addAdditionalInfo(deliveryDocument, deliveryDocument.getDeliveryDocumentLines()));
    }
  }

  private void addAdditionalInfo(
      DeliveryDocument deliveryDocument, List<DeliveryDocumentLine> deliveryDocumentLines) {
    if (CollectionUtils.isNotEmpty(deliveryDocumentLines)) {
      for (DeliveryDocumentLine deliverydocumentline : deliveryDocumentLines) {
        if (RxUtils.isVendorWholesaler(
            deliveryDocument,
            rxManagedConfig.getWholesalerVendors(),
            rxManagedConfig.isWholesalerLotCheckEnabled())) {
          ItemData additionalInfo = deliverydocumentline.getAdditionalInfo();
          if (additionalInfo == null) {
            additionalInfo = new ItemData();
          }
          additionalInfo.setIsWholesaler(true);
          deliverydocumentline.setAdditionalInfo(additionalInfo);
        }
      }
    }
  }

  public void publishContainers(List<Container> containers) {
    if (rxManagedConfig.isPublishContainersToKafkaEnabled()) {
      try {
        containerService.publishMultipleContainersToInventory(
            transformer.transformList(containers));
      } catch (Exception e) {
        LOG.error("Exception while publishing Containers to Kafka.");
      }
    }
  }

  public Instruction fetchMultiSkuInstrDeliveryDocument(
      String deliveryNumber, String ssccNumber, String poNumber, String userId) {
    return instructionPersisterService.fetchMultiSkuInstrDeliveryDocument(
        RxInstructionType.RX_SER_MULTI_SKU_PALLET.getInstructionType(),
        Long.valueOf(deliveryNumber),
        ssccNumber,
        poNumber,
        userId);
  }

  public Instruction fetchMultiSkuInstrDeliveryDocumentByDelivery(
      String deliveryNumber, String poNumber, String userId) {
    return instructionPersisterService.fetchMultiSkuInstrDeliveryDocumentByDelivery(
        RxInstructionType.RX_SER_MULTI_SKU_PALLET.getInstructionType(),
        Long.valueOf(deliveryNumber),
        poNumber,
        userId);
  }

  public Instruction fetchMultiSkuInstrDeliveryDocumentForCompleteIns(
      Long deliveryNumber, String poNumber, String userId) {
    return instructionPersisterService.fetchMultiSkuInstrByDelivery(
        RxInstructionType.RX_SER_MULTI_SKU_PALLET.getInstructionType(),
        deliveryNumber,
        poNumber,
        userId);
  }

  public String generateTrackingId(HttpHeaders httpHeaders) {
    String trackingId = lpnCacheService.getLPNBasedOnTenant(httpHeaders);
    if (org.apache.commons.lang3.StringUtils.isBlank(trackingId)) {
      throw new ReceivingBadDataException(ExceptionCodes.INVALID_LPN, RxConstants.INVALID_LPN);
    }
    return trackingId;
  }
}
