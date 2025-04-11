package com.walmart.move.nim.receiving.acc.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.util.ACCUtils;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.contract.prelabel.LabelingService;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDataLpn;
import com.walmart.move.nim.receiving.core.message.common.HawkeyeItemUpdateType;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import com.walmart.move.nim.receiving.core.model.label.acl.ACLLabelDataTO;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.core.service.LPNCacheService;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * @author g0k0072 Generates store friendly label based on delivery event and delivery details
 *     passed
 */
public class GenericLabelGeneratorService {

  private static final Logger LOGGER = LoggerFactory.getLogger(GenericLabelGeneratorService.class);

  @ManagedConfiguration protected ACCManagedConfig accManagedConfig;
  @ManagedConfiguration protected AppConfig appConfig;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired protected LPNCacheService lpnCacheService;

  @Autowired protected LabelDataService labelDataService;

  @Autowired protected LabelingHelperService labelingHelperService;

  @Resource(name = ReceivingConstants.DELIVERY_EVENT_PERSISTER_SERVICE)
  protected DeliveryEventPersisterService deliveryEventPersisterService;

  @Resource(name = ReceivingConstants.GENERIC_LABELING_SERVICE)
  protected LabelingService labelingService;

  @Autowired protected JmsPublisher jmsPublisher;

  @Resource(name = ACCConstants.ACC_ASYNC_LABEL_PERSISTER_SERVICE)
  protected AsyncLabelPersisterService asyncLabelPersisterService;

  @Autowired private Gson gson;

  /**
   * Calculates no. of exception LPNs
   *
   * @param expectedQuantity ordered quantity
   * @param generatedQty already generated quantity
   * @return no. of exception label
   */
  private int calculateExceptionLPNSCount(int expectedQuantity, int generatedQty) {
    int numberOfExceptionLabels =
        Math.round(
            expectedQuantity
                * tenantSpecificConfigReader
                    .getCcmConfigValue(
                        TenantContext.getFacilityNum(), ACCConstants.EXCEPTION_LPN_THRESHOLD)
                    .getAsFloat());
    if (numberOfExceptionLabels == 0) {
      numberOfExceptionLabels = 1;
    }
    return Math.max(numberOfExceptionLabels - generatedQty, 0);
  }

  /**
   * Computes no of exception LPNS count based on PO Line and already generated LPNS count
   *
   * @param deliveryDocumentLine PO Line
   * @param generatedQty already generated quantity
   * @param labelType type of LPN/LABEL ORDERED/OVERAGE/EXCEPTION
   * @return no. of required exception lpns
   */
  private int calculateLPNSCount(
      DeliveryDocumentLine deliveryDocumentLine, int generatedQty, LabelType labelType) {
    int lpnCount;
    switch (labelType) {
      case ORDERED:
        lpnCount = Math.max(deliveryDocumentLine.getExpectedQty() - generatedQty, 0);
        break;
      case OVERAGE:
        lpnCount = Math.max(deliveryDocumentLine.getOverageQtyLimit() - generatedQty, 0);
        break;
      case EXCEPTION:
        lpnCount = calculateExceptionLPNSCount(deliveryDocumentLine.getExpectedQty(), generatedQty);
        break;
      default:
        LOGGER.error("LG: Unknown label type {}", labelType);
        lpnCount = 0;
    }
    return lpnCount;
  }

  /**
   * Get LPNs based on the label data(if existing) and order qty
   *
   * @param labelData label data
   * @param deliveryDocumentLine PO Line
   * @param labelType type of LPN/LABEL ORDERED/OVERAGE/EXCEPTION
   * @return return list of LPNS
   */
  private List<String> getLPNS(
      LabelData labelData, DeliveryDocumentLine deliveryDocumentLine, LabelType labelType)
      throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    List<String> lpnList =
        new ArrayList<>(
            Arrays.asList(JacksonParser.convertJsonToObject(labelData.getLpns(), String[].class)));
    int lpnsCount = calculateLPNSCount(deliveryDocumentLine, lpnList.size(), labelType);
    if (lpnsCount > 0) {
      LOGGER.info("LG: Fetching LPNs for a count of {}", lpnsCount);
      List<String> lpns = lpnCacheService.getLPNSBasedOnTenant(lpnsCount, httpHeaders);
      lpnList.addAll(lpns);
      updateLabelDataLpns(labelData, lpns);
    }
    return lpnList;
  }

  /**
   * Build label data or fetch from data base based on @checkIfCreated
   *
   * @param deliveryDocument PO
   * @param deliveryDocumentLine PO Line
   * @param labelType type of LPN/LABEL ORDERED/OVERAGE/EXCEPTION
   * @return label data existing or create one
   */
  private LabelData createLabelData(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      LabelType labelType) {
    LabelData labelData =
        LabelData.builder()
            .deliveryNumber(deliveryDocument.getDeliveryNumber())
            .purchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber())
            .purchaseReferenceLineNumber(deliveryDocumentLine.getPurchaseReferenceLineNumber())
            .itemNumber(deliveryDocumentLine.getItemNbr())
            .mustArriveBeforeDate(deliveryDocument.getPurchaseReferenceMustArriveByDate())
            .isDAConveyable(Boolean.FALSE)
            .labelType(labelType)
            .lpns(JacksonParser.writeValueAsString(new ArrayList<String>()))
            .rejectReason(ACCUtils.getRejectReason(deliveryDocumentLine))
            .build();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .sscc(null)
            .catalogGTIN(deliveryDocumentLine.getVendorUPC())
            .consumableGTIN(deliveryDocumentLine.getItemUPC())
            .orderableGTIN(deliveryDocumentLine.getCaseUPC())
            .build();
    labelData.setPossibleUPC(JacksonParser.writeValueAsString(possibleUPC));
    return labelData;
  }

  /**
   * Build exception label data based on label data created based on ORDERED quantity
   *
   * @param originalLabelData label data created based on ORDERED quantity
   * @return exception label data
   */
  private LabelData createExceptionLabelData(LabelData originalLabelData) {
    return LabelData.builder()
        .deliveryNumber(originalLabelData.getDeliveryNumber())
        .purchaseReferenceNumber(originalLabelData.getPurchaseReferenceNumber())
        .purchaseReferenceLineNumber(originalLabelData.getPurchaseReferenceLineNumber())
        .itemNumber(originalLabelData.getItemNumber())
        .mustArriveBeforeDate(originalLabelData.getMustArriveBeforeDate())
        .isDAConveyable(originalLabelData.getIsDAConveyable())
        .labelType(LabelType.EXCEPTION)
        .possibleUPC(originalLabelData.getPossibleUPC())
        .label(originalLabelData.getLabel())
        .build();
  }

  /**
   * Get the label data if available otherwise create
   *
   * @param checkIfCreated check if exist required or not
   * @param deliveryDocument PO
   * @param deliveryDocumentLine POL
   * @return list of label data
   */
  protected List<LabelData> getOrCreateLabelData(
      boolean checkIfCreated,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine) {
    List<LabelData> labelDataList = new ArrayList<>();
    if (checkIfCreated) {
      labelDataList =
          labelDataService.findAllLabelDataByDeliveryPOPOL(
              deliveryDocument.getDeliveryNumber(),
              deliveryDocumentLine.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber());
    }
    if (labelDataList.isEmpty()) {
      LOGGER.info(
          "LG: Label data was not found for delivery {}, PO {}, PO line {}. Creating new labels",
          deliveryDocument.getDeliveryNumber(),
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      LabelData labelData;
      for (LabelType labelType : LabelType.values()) {
        labelData = createLabelData(deliveryDocument, deliveryDocumentLine, labelType);
        labelDataList.add(labelData);
      }
    }
    return labelDataList;
  }

  /**
   * create or update label data based on active channel of PO/POL
   *
   * @param checkIfCreated checks if label data already exists
   * @param deliveryDocument PO
   * @param deliveryDocumentLine PO Line
   */
  protected List<LabelData> createOrUpdateLabelData(
      boolean checkIfCreated,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      String inboundDoor)
      throws ReceivingException {
    List<LabelData> labelDataList =
        getOrCreateLabelData(checkIfCreated, deliveryDocument, deliveryDocumentLine);
    boolean shouldProcessLabel =
        InstructionUtils.isDAConFreight(
            deliveryDocumentLine.getIsConveyable(),
            deliveryDocumentLine.getPurchaseRefType(),
            deliveryDocumentLine.getActiveChannelMethods());
    if (shouldProcessLabel) {
      LOGGER.info(
          "LG: PO {} Line {} is conveyable",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      String label =
          labelingService.getFormattedLabelData(
              deliveryDocument,
              deliveryDocumentLine,
              ReceivingConstants.PRINT_TYPE_ZEBRA,
              ReceivingConstants.PRINT_MODE_CONTINUOUS,
              inboundDoor);
      setConveyableLinesInLabelData(deliveryDocumentLine, labelDataList, label);
    } else {
      LOGGER.info(
          "LG: PO {} Line {} is not conveyable",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      setNonConLinesInLabelData(deliveryDocumentLine, labelDataList);
    }
    return labelDataList;
  }

  protected void setConveyableLinesInLabelData(
      DeliveryDocumentLine deliveryDocumentLine, List<LabelData> labelDataList, String label)
      throws ReceivingException {
    for (LabelData labelData : labelDataList) {
      List<String> lpns = getLPNS(labelData, deliveryDocumentLine, labelData.getLabelType());
      labelData.setLpns(JacksonParser.writeValueAsString(lpns));
      labelData.setLpnsCount(lpns.size());
      labelData.setIsDAConveyable(true);
      labelData.setLabel(label);
      labelData.setRejectReason(null);
      labelData.setPossibleUPC(
          getUpdatedPossibleUPC(labelData.getPossibleUPC(), deliveryDocumentLine));
    }
  }

  protected void setNonConLinesInLabelData(
      DeliveryDocumentLine deliveryDocumentLine, List<LabelData> labelDataList) {
    for (LabelData labelData : labelDataList) {
      if (Objects.isNull(labelData.getLpns())) {
        labelData.setLpns(JacksonParser.writeValueAsString(new ArrayList<String>()));
        labelData.setLpnsCount(0);
        labelData.setLabel(null);
      }
      labelData.setIsDAConveyable(false);
      labelData.setRejectReason(ACCUtils.getRejectReason(deliveryDocumentLine));
      labelData.setPossibleUPC(
          getUpdatedPossibleUPC(labelData.getPossibleUPC(), deliveryDocumentLine));
    }
  }

  private String getUpdatedPossibleUPC(
      String possibleUPCString, DeliveryDocumentLine deliveryDocumentLine) {
    PossibleUPC possibleUPC =
        StringUtils.hasText(possibleUPCString)
            ? gson.fromJson(possibleUPCString, PossibleUPC.class)
            : null;
    if (Objects.nonNull(possibleUPC)) {
      return gson.toJson(
          PossibleUPC.builder()
              .sscc(null)
              .catalogGTIN(deliveryDocumentLine.getVendorUPC())
              .consumableGTIN(deliveryDocumentLine.getItemUPC())
              .orderableGTIN(deliveryDocumentLine.getCaseUPC())
              .build());
    }
    return null;
  }

  /**
   * Assigns order of consumption
   *
   * @param allLabelDataList label data list
   * @param checkIfCreated if update check is required
   */
  private void assignSequence(
      long deliveryNumber, List<LabelData> allLabelDataList, boolean checkIfCreated) {
    LOGGER.info("LG: Setting sequence for delivery {}", deliveryNumber);
    int sequence = 0;
    if (checkIfCreated) {
      sequence = labelDataService.getMaxSequence(deliveryNumber);
    }
    List<LabelData> labelDataListWithoutSequence =
        allLabelDataList
            .stream()
            .filter(LabelData::sequenceNotAssigned)
            .sorted(
                Comparator.comparing(LabelData::getMustArriveBeforeDate)
                    .thenComparing(LabelData::getLabelType))
            .collect(Collectors.toList());
    for (LabelData labelData : labelDataListWithoutSequence) {
      labelData.setSequenceNo(++sequence);
    }
  }

  /**
   * Iterate through the PO/POL and generate store friendly label data along with list of LPNS
   *
   * @param deliveryDetails delivery details to be processed
   * @param checkIfCreated flag to check if label data already exists. In that case it updates
   *     accordingly
   */
  private Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>>
      generatorGenericLabelForDelivery(
          DeliveryDetails deliveryDetails, DeliveryEvent deliveryEvent, boolean checkIfCreated)
          throws ReceivingException {
    Map<DeliveryDocumentLine, List<LabelData>> aggregatedLabelDataPoLineMap =
        getAggregatedLabelsPerPoLine(deliveryDetails, checkIfCreated);
    List<LabelData> aggregatedLabelDataList =
        aggregatedLabelDataPoLineMap
            .values()
            .stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
    DeliveryEvent deliveryEventRefreshed =
        saveLabelDataAndGetRefreshedDeliveryEvent(
            deliveryDetails, deliveryEvent, checkIfCreated, aggregatedLabelDataList);
    return new Pair<>(deliveryEventRefreshed, aggregatedLabelDataPoLineMap);
  }

  private Map<DeliveryDocumentLine, List<LabelData>> getAggregatedLabelsPerPoLine(
      DeliveryDetails deliveryDetails, boolean checkIfCreated) throws ReceivingException {
    Map<DeliveryDocumentLine, List<LabelData>> aggregatedLabelDataPoLineMap = new HashMap<>();
    List<LabelData> labelDataPerLine;
    for (DeliveryDocument deliveryDocument : deliveryDetails.getDeliveryDocuments()) {
      if (POStatus.CNCL.name().equalsIgnoreCase(deliveryDocument.getPurchaseReferenceStatus())) {
        LOGGER.info(
            "LG: PO {} is cancelled. Skipping label generation",
            deliveryDocument.getPurchaseReferenceNumber());
        continue;
      }
      for (DeliveryDocumentLine line : deliveryDocument.getDeliveryDocumentLines()) {
        if (POLineStatus.CANCELLED.name().equalsIgnoreCase(line.getPurchaseReferenceLineStatus())
            || (!Objects.isNull(line.getOperationalInfo())
                && POLineStatus.REJECTED
                    .name()
                    .equalsIgnoreCase(line.getOperationalInfo().getState()))) {
          LOGGER.info(
              "LG: PO {} line {} is cancelled or rejected. Skipping label generation",
              line.getPurchaseReferenceNumber(),
              line.getPurchaseReferenceLineNumber());
          continue;
        }
        labelDataPerLine =
            createOrUpdateLabelData(
                checkIfCreated, deliveryDocument, line, deliveryDetails.getDoorNumber());
        enrichDocumentLineWithPoDetails(line, deliveryDocument);
        aggregatedLabelDataPoLineMap.put(line, labelDataPerLine);
      }
    }
    return aggregatedLabelDataPoLineMap;
  }

  protected void enrichDocumentLineWithPoDetails(
      DeliveryDocumentLine deliveryDocumentLine, DeliveryDocument deliveryDocument) {
    deliveryDocumentLine.setDepartment(deliveryDocument.getDeptNumber());
    deliveryDocumentLine.setPoDCNumber(Integer.valueOf(deliveryDocument.getPoDCNumber()));
    deliveryDocumentLine.setPurchaseReferenceLegacyType(
        Integer.valueOf(deliveryDocument.getPurchaseReferenceLegacyType()));
    deliveryDocumentLine.setVendorNumber(deliveryDocument.getVendorNumber());
  }

  private DeliveryEvent saveLabelDataAndGetRefreshedDeliveryEvent(
      DeliveryDetails deliveryDetails,
      DeliveryEvent deliveryEvent,
      boolean checkIfCreated,
      List<LabelData> aggregatedLabelDataList) {
    assignSequence(deliveryEvent.getDeliveryNumber(), aggregatedLabelDataList, checkIfCreated);
    DeliveryEvent deliveryEventRefreshed =
        deliveryEventPersisterService.getDeliveryEventById(deliveryEvent.getId());
    if (!deliveryEventRefreshed.getEventStatus().equals(EventTargetStatus.DELETE)) {
      labelDataService.saveAll(aggregatedLabelDataList);
      if (accManagedConfig.isLabelPostEnabled()) {
        asyncLabelPersisterService.publishLabelDataToLabelling(
            deliveryDetails, aggregatedLabelDataList, ReceivingUtils.getHeaders());
      }
    }
    return deliveryEventRefreshed;
  }

  private void saveLabelDataForItemUpdate(
      DeliveryDetails deliveryDetails,
      boolean checkIfCreated,
      List<LabelData> aggregatedLabelDataList) {
    assignSequence(deliveryDetails.getDeliveryNumber(), aggregatedLabelDataList, checkIfCreated);
    labelDataService.saveAll(aggregatedLabelDataList);
    if (accManagedConfig.isLabelPostEnabled()) {
      asyncLabelPersisterService.publishLabelDataToLabelling(
          deliveryDetails, aggregatedLabelDataList, ReceivingUtils.getHeaders());
    }
  }

  /**
   * This method generated store friendly labels and persist them for a delivery event
   *
   * @param deliveryEvent delivery event to be processed
   * @param deliveryDetails delivery details to be processed
   */
  public Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>> generateGenericLabel(
      DeliveryEvent deliveryEvent, DeliveryDetails deliveryDetails) throws ReceivingException {
    switch (deliveryEvent.getEventType()) {
      case ReceivingConstants.EVENT_DOOR_ASSIGNED:
        return generatorGenericLabelForDelivery(deliveryDetails, deliveryEvent, Boolean.FALSE);
      case ReceivingConstants.EVENT_PO_ADDED:
      case ReceivingConstants.EVENT_PO_UPDATED:
      case ReceivingConstants.EVENT_PO_LINE_ADDED:
      case ReceivingConstants.EVENT_PO_LINE_UPDATED:
      case ReceivingConstants.PRE_LABEL_GEN_FALLBACK:
        return generatorGenericLabelForDelivery(deliveryDetails, deliveryEvent, Boolean.TRUE);
      default:
        LOGGER.info(
            "LG: Invalid event received for pre label generation : {}",
            deliveryEvent.getEventType());
    }
    return null;
  }

  public Map<DeliveryDocumentLine, List<LabelData>> updateLabelDataForItemUpdate(
      DeliveryDetails deliveryDetails, HawkeyeItemUpdateType eventType, Boolean checkIfCreated)
      throws ReceivingException {

    Map<DeliveryDocumentLine, List<LabelData>> aggregatedLabelDataPoLineMap =
        getAggregatedLabelsPerPoLine(deliveryDetails, checkIfCreated);
    for (Map.Entry<DeliveryDocumentLine, List<LabelData>> entry :
        aggregatedLabelDataPoLineMap.entrySet()) {
      DeliveryDocumentLine deliveryDocumentLine = entry.getKey();
      List<LabelData> labelDataList = entry.getValue();
      labelDataList.forEach(
          labelData ->
              labelData.setRejectReason(
                  ACCUtils.getRejectReasonForItemUpdate(deliveryDocumentLine, eventType)));
    }
    List<LabelData> aggregatedLabelDataList =
        aggregatedLabelDataPoLineMap
            .values()
            .stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
    saveLabelDataForItemUpdate(deliveryDetails, checkIfCreated, aggregatedLabelDataList);
    return aggregatedLabelDataPoLineMap;
  }

  /**
   * Compile Label data for given delivery number
   *
   * @param aggregatedLabelDataPoLineMap list of label data per po line to be published
   * @return Label data accepted by ACL
   */
  public void publishACLLabelDataPayload(
      Map<DeliveryDocumentLine, List<LabelData>> aggregatedLabelDataPoLineMap,
      Long deliveryNumber,
      String doorNumber,
      String trailer,
      Map<String, Object> headers) {
    List<LabelData> aggregatedLabelDataList =
        aggregatedLabelDataPoLineMap
            .values()
            .stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
    publishLabelDataList(aggregatedLabelDataList, deliveryNumber, headers);
  }

  private void publishLabelDataList(
      List<LabelData> aggregatedLabelDataList, Long deliveryNumber, Map<String, Object> headers) {
    Map<Long, ScanItem> scanItemMap = new HashMap<>(); // 1 scan item per item
    aggregatedLabelDataList.forEach(
        labelData -> {
          ScanItem scanItem = scanItemMap.get(labelData.getItemNumber());
          if (Objects.isNull(scanItem)) {
            // Fresh item found, create new scan item for it and add labels
            scanItem = labelingHelperService.buildScanItemFromLabelData(deliveryNumber, labelData);
            scanItemMap.put(labelData.getItemNumber(), scanItem);
          }
          if (StringUtils.isEmpty(scanItem.getReject()) && labelData.getLpnsCount() > 0) {
            // Add labels only for conveyable freight
            addLabelsToScanItem(
                labelData, scanItem.getExceptionLabels(), scanItem.getLabels(), null);
          } else {
            // clear the exception label url set previously
            scanItem.getExceptionLabels().setPurchaseReferenceNumber("");
            scanItem.getExceptionLabels().setPurchaseReferenceLineNumber(null);
            // scanItem.getExceptionLabels().setLabelData(null);
            scanItem.setExceptionLabelURL("");
          }
        });
    ACLLabelDataTO aclLabelDataTO =
        ACLLabelDataTO.builder()
            .deliveryNumber(deliveryNumber.toString())
            .deliveryNbr(deliveryNumber)
            .scanItems(new ArrayList<>(scanItemMap.values()))
            .build();

    publishACLLabelData(aclLabelDataTO, headers);
  }

  protected void addLabelsToScanItem(
      LabelData labelData,
      FormattedLabels exceptionLabels,
      List<FormattedLabels> labels,
      DeliveryDocumentLine deliveryDocumentLine) {
    if (labelData.getLabelType().equals(LabelType.EXCEPTION)) {
      addExceptionLabelsToScanItem(labelData, exceptionLabels);
    } else {
      addLpnsToTransactionLabels(labelData, labels, deliveryDocumentLine);
    }
  }

  protected void addLpnsToTransactionLabels(
      LabelData labelData,
      List<FormattedLabels> labels,
      DeliveryDocumentLine deliveryDocumentLine) {
    labels.add(labelingHelperService.buildFormattedLabel(labelData));
  }

  private void addExceptionLabelsToScanItem(LabelData labelData, FormattedLabels exceptionLabels) {
    exceptionLabels
        .getLpns()
        .addAll(
            Arrays.asList(JacksonParser.convertJsonToObject(labelData.getLpns(), String[].class)));
  }

  /**
   * Publish labels to ACL based on feature flag
   *
   * @param deliveryNumber delivery number of delivery for which labels are to be published
   * @param poLineLabelDataMap List of labels per po line to be published
   */
  public void publishLabelsToAcl(
      Map<DeliveryDocumentLine, List<LabelData>> poLineLabelDataMap,
      Long deliveryNumber,
      String doorNumber,
      String trailer,
      boolean isPartialLabels) {
    if (accManagedConfig.isLabelDeltaPublishEnabled() && isPartialLabels) {
      LOGGER.info(
          "Delta Publish enabled. Publishing only delta labels to ACL for delivery {} with correlation id {}",
          deliveryNumber,
          TenantContext.getCorrelationId());
      publishACLLabelDataDelta(
          poLineLabelDataMap, deliveryNumber, doorNumber, trailer, ReceivingUtils.getHeaders());
    } else {
      LOGGER.info(
          "Delta Publish disabled and/or DOOR_ASSIGN/FALLBACK event. Publishing all labels to ACL for delivery {} with correlation id {}",
          deliveryNumber,
          TenantContext.getCorrelationId());
      publishACLLabelDataForDelivery(deliveryNumber, ReceivingUtils.getHeaders());
    }
  }

  /**
   * Publish Label data to ACL for whole delivery
   *
   * @param deliveryNumber delivery number of acl label data to be published
   * @param httpHeaders http headers
   */
  public void publishACLLabelDataForDelivery(Long deliveryNumber, HttpHeaders httpHeaders) {
    List<LabelData> labelDataList =
        labelDataService.getLabelDataByDeliveryNumberSortedBySeq(deliveryNumber);
    publishLabelDataList(
        labelDataList,
        deliveryNumber,
        getMessageHeadersForLabelDataComplete(deliveryNumber, httpHeaders));
  }

  public void publishACLLabelDataDelta(
      Map<DeliveryDocumentLine, List<LabelData>> poLineLabelDataMap,
      Long deliveryNumber,
      String doorNumber,
      String trailer,
      HttpHeaders httpHeaders) {
    publishACLLabelDataPayload(
        poLineLabelDataMap,
        deliveryNumber,
        doorNumber,
        trailer,
        getMessageHeadersForLabelDataPartial(deliveryNumber, httpHeaders));
  }

  /**
   * Publish Label data to ACL
   *
   * @param aclLabelDataTO acl label data to be published
   */
  public void publishACLLabelData(
      com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData aclLabelDataTO,
      Map<String, Object> messageHeaders) {
    String aclLabelDataTOJSON = JacksonParser.writeValueAsString(aclLabelDataTO);
    MessagePublisher labelDataPublisher =
        tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.LABEL_DATA_PUBLISHER,
            MessagePublisher.class);
    labelDataPublisher.publish(aclLabelDataTO, messageHeaders);
    LOGGER.info(
        "LG: Successfully published labels to ACL for user {}. Data: {}",
        aclLabelDataTO.getUser(),
        aclLabelDataTOJSON);
  }

  protected Map<String, Object> getMessageHeadersForLabelDataPartial(
      Long deliveryNumber, HttpHeaders httpHeaders) {
    Map<String, Object> messageHeaders = getBaseMessageHeaders(deliveryNumber, httpHeaders);
    messageHeaders.put(
        ACCConstants.ACL_PAYLOAD_TYPE_HEADER_NAME, ACCConstants.ACL_DELTA_PUBLISH_PARTIAL_DELIVERY);
    return messageHeaders;
  }

  protected Map<String, Object> getMessageHeadersForLabelDataComplete(
      Long deliveryNumber, HttpHeaders httpHeaders) {
    Map<String, Object> messageHeaders = getBaseMessageHeaders(deliveryNumber, httpHeaders);
    messageHeaders.put(
        ACCConstants.ACL_PAYLOAD_TYPE_HEADER_NAME,
        ACCConstants.ACL_DELTA_PUBLISH_COMPLETE_DELIVERY);
    return messageHeaders;
  }

  protected Map<String, Object> getBaseMessageHeaders(
      Long deliveryNumber, HttpHeaders httpHeaders) {
    Map<String, Object> messageHeaders = new HashMap<>();
    httpHeaders.forEach(messageHeaders::put);
    messageHeaders.put(
        ReceivingConstants.PRODUCT_NAME_HEADER_KEY, ReceivingConstants.APP_NAME_VALUE);
    messageHeaders.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    messageHeaders.put(
        ReceivingConstants.CONTENT_ENCODING, ReceivingConstants.CONTENT_ENCODING_GZIP);
    return messageHeaders;
  }

  @Transactional
  public FormattedLabels generateExceptionLabel(Long deliveryNumber, String upc)
      throws ReceivingException {
    LabelData labelData =
        labelDataService.findByDeliveryNumberAndUPCAndLabelType(
            deliveryNumber, upc, LabelType.ORDERED);
    if (Objects.isNull(labelData) || !labelData.getIsDAConveyable()) {
      throw new ReceivingBadDataException(
          ExceptionCodes.PRE_GEN_DATA_NOT_FOUND, ReceivingException.PRE_GEN_DATA_NOT_FOUND);
    }
    LabelData exceptionLabelData = createExceptionLabelData(labelData);
    int exceptionLPNSCount = calculateExceptionLPNSCount(labelData.getLpnsCount(), 0);
    LOGGER.info("LG: Fetching Exception LPNs for a count of {}", exceptionLPNSCount);
    List<String> fetchedExceptionLPNList =
        lpnCacheService.getLPNSBasedOnTenant(exceptionLPNSCount, ReceivingUtils.getHeaders());
    exceptionLabelData.setLpns(JacksonParser.writeValueAsString(fetchedExceptionLPNList));
    exceptionLabelData.setLpnsCount(exceptionLPNSCount);
    exceptionLabelData.setSequenceNo(10000000);
    updateLabelDataLpns(exceptionLabelData, fetchedExceptionLPNList);
    labelDataService.save(exceptionLabelData);
    return FormattedLabels.builder()
        .seqNo(exceptionLabelData.getSequenceNo().toString())
        .purchaseReferenceNumber(exceptionLabelData.getPurchaseReferenceNumber())
        .lpns(fetchedExceptionLPNList)
        .labelData(exceptionLabelData.getLabel())
        .build();
  }

  /**
   * create LabelDataLpn entity objects for newly generated lpns to persist in LABEL_DATA_LPN table
   *
   * @param labelData
   * @param lpns list of generated lpns
   */
  private void updateLabelDataLpns(LabelData labelData, List<String> lpns) {
    if (!tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.LABEL_DATA_LPN_INSERTION_ENABLED)) {
      return;
    }
    if (Objects.isNull(labelData.getLabelDataLpnList())) {
      labelData.setLabelDataLpnList(new ArrayList<>());
    }
    labelData
        .getLabelDataLpnList()
        .addAll(lpns.stream().map(LabelDataLpn::from).collect(Collectors.toList()));
  }
}
