package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.rdc.utils.RdcUtils.isNonConveyableItem;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_REINDUCT_ROUTING_LABEL;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.walmart.move.nim.atlas.platform.policy.commons.Message;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.AsyncNimRdsRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.publisher.JMSSorterPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaAthenaPublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.ei.InventoryDetails;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponseWithRdsResponse;
import com.walmart.move.nim.receiving.core.model.sorter.LabelType;
import com.walmart.move.nim.receiving.core.model.sorter.ProgramSorterTO;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymHawkeyeEventType;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayMessage;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.core.transformer.InventoryTransformer;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.service.NimRdsService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.repositories.PayloadRef;
import com.walmart.platform.service.OutboxEventSinkService;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.google.common.collect.Lists;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
public class RdcReceivingUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcReceivingUtils.class);
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private ReceiptService receiptService;
  @Autowired private DeliveryDocumentHelper deliveryDocumentHelper;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private AsyncNimRdsRestApiClient asyncNimRdsRestApiClient;
  @Autowired private NimRDSRestApiClient nimRDSRestApiClient;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private DeliveryItemOverrideService deliveryItemOverrideService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ProblemRepository problemRepository;
  @Autowired private LabelDataService labelDataService;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private KafkaAthenaPublisher kafkaAthenaPublisher;
  @Autowired private JMSSorterPublisher jmsSorterPublisher;
  @Autowired private Gson gson;
  @Autowired private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Autowired private RdcSlottingUtils rdcSlottingUtils;
  @Autowired private OutboxEventSinkService outboxEventSinkService;
  @Autowired private ContainerTransformer containerTransformer;
  @Autowired private ContainerItemRepository containerItemRepository;
  @Autowired private ContainerRepository containerRepository;
  @Autowired private InventoryTransformer inventoryTransformer;
  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private OutboxConfig outboxConfig;
  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;

  @Autowired(required = false)
  private EIService eiService;

  @Autowired private LocationService locationService;

  /**
   * @param deliveryDocumentLine
   * @throws ReceivingException
   */
  public void isPoAndPoLineInReceivableStatus(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    rdcInstructionUtils.validatePoLineIsCancelledOrClosedOrRejected(deliveryDocumentLine);
    // ToDo: New Item check for DA
    rdcInstructionUtils.validateItemXBlocked(deliveryDocumentLine);
    rdcInstructionUtils.validateItemHandlingMethod(deliveryDocumentLine);
  }

  public Boolean isWhpkReceiving(
      DeliveryDocumentLine deliveryDocumentLine,
      ReceiveInstructionRequest receiveInstructionRequest) {
    Boolean isLessThanCase =
        Objects.nonNull(receiveInstructionRequest)
                && Objects.nonNull(receiveInstructionRequest.getIsLessThanCase())
            ? receiveInstructionRequest.getIsLessThanCase()
            : Boolean.FALSE;
    return Objects.nonNull(deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode())
            && deliveryDocumentLine
                .getAdditionalInfo()
                .getItemPackAndHandlingCode()
                .equals(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE)
        || isLessThanCase;
  }

  /**
   * @param deliveryDocuments
   * @param receiveQty
   * @param httpHeaders
   */
  public void validateOverage(
      List<DeliveryDocument> deliveryDocuments,
      Integer receiveQty,
      HttpHeaders httpHeaders,
      boolean isLessThanCase) {
    Pair<DeliveryDocument, Long> autoSelectedDeliveryDocument =
        rdcInstructionUtils.autoSelectDocumentAndDocumentLine(
            deliveryDocuments,
            receiveQty,
            deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getCaseUpc(),
            httpHeaders);
    if (isLessThanCase) {
      receiveQty = 0;
    }
    Boolean maxOverageReceived =
        checkIfMaxOverageReceived(
            autoSelectedDeliveryDocument.getKey(),
            autoSelectedDeliveryDocument.getValue(),
            receiveQty);
    if (maxOverageReceived) {
      throw new ReceivingBadDataException(
          ExceptionCodes.OVERAGE_ERROR, ReceivingException.OVERAGE_ERROR);
    }
  }

  /**
   * @param deliveryDocument
   * @param totalReceivedQty
   * @param qtyToBeReceived
   * @return
   */
  public Boolean checkIfMaxOverageReceived(
      DeliveryDocument deliveryDocument, Long totalReceivedQty, int qtyToBeReceived) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    int totalReceivedQtyAfterReceiving = totalReceivedQty.intValue() + qtyToBeReceived;
    int maxReceiveQuantity =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();

    if (totalReceivedQtyAfterReceiving > maxReceiveQuantity) {
      LOGGER.info(
          "Received all the order quantity for PO:{} and POL:{}, please report remaining cases as overages.",
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber());
      return Boolean.TRUE;
    }
    return Boolean.FALSE;
  }

  /**
   * @param instructions
   * @param containers
   * @param containerItems
   * @param receipts
   * @param labelDataList
   */
  public void persistReceivedContainerDetails(
      List<Instruction> instructions,
      List<Container> containers,
      List<ContainerItem> containerItems,
      List<Receipt> receipts,
      List<LabelData> labelDataList) {
    if (!CollectionUtils.isEmpty(instructions)) {
      instructionPersisterService.saveAllInstruction(instructions);
    }
    if (!CollectionUtils.isEmpty(containers) && !CollectionUtils.isEmpty(containerItems)) {
      containerPersisterService.saveContainerAndContainerItems(containers, containerItems);
    }
    if (!CollectionUtils.isEmpty((receipts))) {
      receiptService.saveAll(receipts);
    }
    if (!CollectionUtils.isEmpty(labelDataList)) {
      labelDataService.saveAll(labelDataList);
    }
  }

  public void persistOutboxEvents(Collection<OutboxEvent> outboxEvents) {
    if (!CollectionUtils.isEmpty(outboxEvents)) {
      outboxEventSinkService.saveAllEvent(outboxEvents);
    }
  }

  /**
   * This method build outbox events for all external integrations from Receiving (Inventory /
   * Hawkeye / Athena / EI / WFT) Each integration payLoad with headers will be set in the
   * corresponding outbox policy.
   *
   * @param receivedContainers
   * @param httpHeaders
   * @param deliveryDocument,
   * @param instruction
   * @return
   * @throws ReceivingException
   */
  public Collection<OutboxEvent> buildOutboxEvents(
      List<ReceivedContainer> receivedContainers,
      HttpHeaders httpHeaders,
      Instruction instruction,
      DeliveryDocument deliveryDocument)
      throws ReceivingException {
    Collection<OutboxEvent> outboxEvents = null;
    Map<String, List<PayloadRef>> outboxPolicyMap = new HashMap<>();
    for (ReceivedContainer receivedContainer : receivedContainers) {
      if (StringUtils.isBlank(receivedContainer.getParentTrackingId())) {
        Container consolidatedContainer =
            containerPersisterService.getConsolidatedContainerForPublish(
                receivedContainer.getLabelTrackingId());
        buildOutboxEventForInventory(consolidatedContainer, outboxPolicyMap, httpHeaders);
        buildOutboxEventForPutawayRequest(
            consolidatedContainer,
            receivedContainer,
            instruction,
            deliveryDocument,
            outboxPolicyMap,
            httpHeaders);
        buildOutboxEventForSorterDivert(
            consolidatedContainer,
            receivedContainer,
            outboxPolicyMap,
            httpHeaders,
            deliveryDocument);
        buildOutboxPolicyForEI(consolidatedContainer, outboxPolicyMap, httpHeaders);
        outboxEvents = buildOutboxEvent(consolidatedContainer.getTrackingId(), outboxPolicyMap);
      }
    }

    if (!CollectionUtils.isEmpty(outboxEvents)
        && !EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
      outboxEvents.addAll(
          buildOutboxEventForWFT(
              instruction,
              deliveryDocument.getDeliveryDocumentLines().get(0),
              instruction.getReceivedQuantity(),
              httpHeaders,
              false));
    }
    return outboxEvents;
  }

  /**
   * This method build Outbox event for WFT
   *
   * @param instruction
   * @param deliveryDocumentLine
   * @param receiveQty
   * @param httpHeaders
   * @return
   */
  public Collection<OutboxEvent> buildOutboxEventForWFT(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      int receiveQty,
      HttpHeaders httpHeaders,
      Boolean isLessThanACase) {
    return buildOutboxEvent(
        instruction.getMessageId(),
        buildOutboxPolicyForWFT(
            instruction, deliveryDocumentLine, receiveQty, httpHeaders, isLessThanACase));
  }

  /**
   * Build Inventory container create request payLoad for Outbox event
   *
   * @param consolidatedContainer
   * @param outboxPolicyMap
   * @param httpHeaders
   */
  public void buildOutboxEventForInventory(
      Container consolidatedContainer,
      Map<String, List<PayloadRef>> outboxPolicyMap,
      HttpHeaders httpHeaders) {
    PayloadRef inventoryPayLoad = prepareInventoryPayLoad(consolidatedContainer, httpHeaders);
    LOGGER.info(
        "Outbox Event: Inventory : Container create request body:{} with headers :{} for trackingId: {}",
        inventoryPayLoad.getData().getBody(),
        inventoryPayLoad.getData().getHeaders(),
        consolidatedContainer.getTrackingId());
    outboxPolicyMap.put(
        outboxConfig.getKafkaPublisherPolicyInventory(), Arrays.asList(inventoryPayLoad));
  }

  /**
   * Build putway request payLoad fpr Outbox event
   *
   * @param consolidatedContainer
   * @param receivedContainer
   * @param instruction
   * @param deliveryDocument
   * @param outboxPolicyMap
   * @param httpHeaders
   */
  public void buildOutboxEventForPutawayRequest(
      Container consolidatedContainer,
      ReceivedContainer receivedContainer,
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      Map<String, List<PayloadRef>> outboxPolicyMap,
      HttpHeaders httpHeaders) {
    if (receivedContainer.isRoutingLabel()
        || (isOfflineSymLabelType(receivedContainer, deliveryDocument)
            && putawayEnabledForBreakPackItems(receivedContainer, deliveryDocument))) {
      PayloadRef putawayPayLoad =
          prepareHawkeyePutawayPayLoad(
              receivedContainer, instruction, deliveryDocument, httpHeaders);
      LOGGER.info(
          "Outbox Event: Hawkeye : Putaway request body:{} with headers:{} for trackingId: {}",
          putawayPayLoad.getData().getBody(),
          putawayPayLoad.getData().getHeaders(),
          consolidatedContainer.getTrackingId());
      outboxPolicyMap.put(
          outboxConfig.getKafkaPublisherPolicyPutawayHawkeye(), Arrays.asList(putawayPayLoad));
    }
  }

  /**
   * Build Sorter divert payload for Outbox event
   *
   * @param consolidatedContainer
   * @param receivedContainer
   * @param outboxPolicyMap
   * @param httpHeaders
   */
  public void buildOutboxEventForSorterDivert(
      Container consolidatedContainer,
      ReceivedContainer receivedContainer,
      Map<String, List<PayloadRef>> outboxPolicyMap,
      HttpHeaders httpHeaders,
      DeliveryDocument deliveryDocument) {
    if (receivedContainer.isSorterDivertRequired()) {
      PayloadRef sorterPayLoad =
          prepareSorterPayLoad(
              receivedContainer, consolidatedContainer, httpHeaders, deliveryDocument);
      LOGGER.info(
          "Outbox Event: Athena : Sorter divert request body:{} with headers:{} for trackingId: {}",
          sorterPayLoad.getData().getBody(),
          sorterPayLoad.getData().getBody(),
          consolidatedContainer.getTrackingId());
      outboxPolicyMap.put(
          outboxConfig.getKafkaPublisherPolicySorter(), Arrays.asList(sorterPayLoad));
    }
  }

  /**
   * Build WFT PayLoad for Outbox event
   *
   * @param instruction
   * @param deliveryDocumentLine
   * @param receivedQty
   * @param httpHeaders
   * @return
   */
  public Map<String, List<PayloadRef>> buildOutboxPolicyForWFT(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      int receivedQty,
      HttpHeaders httpHeaders,
      Boolean isLessThanACase) {
    Map<String, List<PayloadRef>> wftOutboxPolicyMap = new HashMap<>();
    PayloadRef wftPayLoad =
        prepareWFTPayLoad(
            instruction, deliveryDocumentLine, receivedQty, httpHeaders, isLessThanACase);
    LOGGER.info(
        "Outbox Event: WFT : Publish WFT instruction payLoad body:{} with headers:{} for messageId: {}",
        wftPayLoad.getData().getBody(),
        wftPayLoad.getData().getHeaders(),
        instruction.getMessageId());
    wftOutboxPolicyMap.put(outboxConfig.getKafkaPublisherPolicyWFT(), Arrays.asList(wftPayLoad));
    return wftOutboxPolicyMap;
  }

  /**
   * @param consolidatedContainer
   * @param outboxPolicyMap
   * @param httpHeaders
   */
  protected void buildOutboxPolicyForEI(
      Container consolidatedContainer,
      Map<String, List<PayloadRef>> outboxPolicyMap,
      HttpHeaders httpHeaders) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RdcConstants.IS_EI_INTEGRATION_ENABLED, false)) {
      // if child container exists then publish all child containers to EI with DC_RECEIVE event
      if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(
          consolidatedContainer.getChildContainers())) {
        List<PayloadRef> payloadRefList =
            consolidatedContainer
                .getChildContainers()
                .stream()
                .map(
                    childContainer ->
                        prepareEIPayLoad(
                            childContainer, httpHeaders, ReceivingConstants.EI_DC_RECEIVING_EVENT))
                .collect(Collectors.toList());
        outboxPolicyMap.put(outboxConfig.getKafkaPublisherPolicyEIDCReceiveEvent(), payloadRefList);
      } else {
        InventoryStatus inventoryStatus =
            InventoryStatus.valueOf(consolidatedContainer.getInventoryStatus());
        LOGGER.info(
            "Outbox Event: Publish container to EI for trackingId:{} with inventory status:{}",
            consolidatedContainer.getTrackingId(),
            inventoryStatus);
        switch (inventoryStatus) {
          case PICKED:
            outboxPolicyMap.put(
                outboxConfig.getKafkaPublisherPolicyEIDCReceiveEvent(),
                Arrays.asList(
                    prepareEIPayLoad(
                        consolidatedContainer,
                        httpHeaders,
                        ReceivingConstants.EI_DC_RECEIVING_EVENT)));
            outboxPolicyMap.put(
                outboxConfig.getKafkaPublisherPolicyEIDCPickEvent(),
                Arrays.asList(
                    prepareEIPayLoad(
                        consolidatedContainer,
                        httpHeaders,
                        ReceivingConstants.EI_DC_PICKED_EVENT)));
            break;
          case ALLOCATED:
            if (consolidatedContainer
                .getInventoryStatus()
                .equals(InventoryStatus.ALLOCATED.name())) {
              outboxPolicyMap.put(
                  outboxConfig.getKafkaPublisherPolicyEIDCReceiveEvent(),
                  Arrays.asList(
                      prepareEIPayLoad(
                          consolidatedContainer,
                          httpHeaders,
                          ReceivingConstants.EI_DC_RECEIVING_EVENT)));
            }
            break;
          default:
            break;
        }
      }
    }
  }

  /**
   * @param labelTrackingId
   * @param outboxPolicyMap
   * @return
   */
  public Collection<OutboxEvent> buildOutboxEvent(
      String labelTrackingId, Map<String, List<PayloadRef>> outboxPolicyMap) {
    Collection<OutboxEvent> outboxEvents =
        outboxPolicyMap
            .entrySet()
            .stream()
            .map(
                entry -> {
                  List<OutboxEvent> outboxEventList =
                      entry
                          .getValue()
                          .stream()
                          .map(
                              payloadRef -> {
                                OutboxEvent outboxEvent =
                                    OutboxEvent.builder()
                                        .eventIdentifier(labelTrackingId)
                                        .executionTs(Instant.now())
                                        .metaData(
                                            MetaData.with(ReceivingConstants.KEY, labelTrackingId))
                                        .publisherPolicyId(entry.getKey())
                                        .payloadRef(payloadRef)
                                        .build();
                                return outboxEvent;
                              })
                          .collect(Collectors.toList());
                  return outboxEventList;
                })
            .flatMap(List::stream)
            .collect(Collectors.toList());
    return outboxEvents;
  }

  /**
   * @param container
   * @param headers
   * @return
   */
  private PayloadRef prepareInventoryPayLoad(Container container, HttpHeaders headers) {
    List<ContainerDTO> containers = containerTransformer.transformList(Arrays.asList(container));
    Map<String, Object> httpHeaders = ReceivingUtils.getForwardablHeaderWithTenantData(headers);
    httpHeaders.put(ReceivingConstants.REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    ReceivingUtils.populateFreightTypeInHeader(containers, httpHeaders, tenantSpecificConfigReader);
    String inventoryPayLoad = ReceivingUtils.getGsonBuilderForUTCConverter().toJson(containers);
    return PayloadRef.builder()
        .data(Message.builder().headers(httpHeaders).body(inventoryPayLoad).build())
        .build();
  }

  /**
   * @param receivedContainer
   * @param consolidatedContainer
   * @param headers
   * @return
   */
  private PayloadRef prepareSorterPayLoad(
      ReceivedContainer receivedContainer,
      Container consolidatedContainer,
      HttpHeaders headers,
      DeliveryDocument deliveryDocument) {
    Map<String, Object> httpHeaders = ReceivingUtils.getForwardablHeaderWithTenantData(headers);
    httpHeaders.put(ReceivingConstants.EVENT, ReceivingConstants.LPN_CREATE);
    httpHeaders.put(ReceivingConstants.IS_ATLAS_ITEM, ReceivingConstants.Y);

    String labelType = null;
    if (EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
      labelType =
          getLabelTypeForOfflineRcv(receivedContainer, consolidatedContainer, deliveryDocument);
    } else {
      labelType = getLabelTypeForSorterDivert(receivedContainer, consolidatedContainer);
    }
    ProgramSorterTO programSorterTO =
        kafkaAthenaPublisher.getSorterDivertPayLoadByLabelType(consolidatedContainer, labelType);
    String sorterPayLoad = JacksonParser.writeValueAsString(programSorterTO);
    return PayloadRef.builder()
        .data(Message.builder().headers(httpHeaders).body(sorterPayLoad).build())
        .build();
  }

  private PayloadRef prepareWFTPayLoad(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      Integer receivedQuantity,
      HttpHeaders headers,
      Boolean isLessThanACase) {
    Map<String, Object> httpHeaders = ReceivingUtils.getForwardablHeaderWithTenantData(headers);
    PublishInstructionSummary publishInstructionSummary =
        prepareInstructionMessage(
            instruction, deliveryDocumentLine, receivedQuantity, headers, isLessThanACase);
    String wftPayLoad =
        ReceivingUtils.getGsonBuilderForUTCConverter().toJson(publishInstructionSummary);
    return PayloadRef.builder()
        .data(Message.builder().headers(httpHeaders).body(wftPayLoad).build())
        .build();
  }

  /**
   * @param receivedContainer
   * @param instruction4mDB
   * @param deliveryDocument
   * @param headers
   * @return
   */
  private PayloadRef prepareHawkeyePutawayPayLoad(
      ReceivedContainer receivedContainer,
      Instruction instruction4mDB,
      DeliveryDocument deliveryDocument,
      HttpHeaders headers) {
    ContainerItem containerItem =
        containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                receivedContainer.getLabelTrackingId(),
                receivedContainer.getPoNumber(),
                receivedContainer.getPoLine());

    /**
     * If the Symbotic 3.0 connection is enabled and container is DA, system parameter for putaway
     * message header needs to be picked from CCM Or default to SYM2_5, otherwise pick from
     * ContainerItem.AsrsAlignment
     */
    String alignment = containerItem.getAsrsAlignment();
    if (isSymCutOverEnabled()) {
      alignment =
          tenantSpecificConfigReader.getCcmValue(
              TenantContext.getFacilityNum(),
              ReceivingConstants.SYM_PUTAWAY_SYSTEM_DEFAULT_VALUE,
              ReceivingConstants.SYM_SYSTEM_DEFAULT_VALUE);
    }
    Map<String, Object> symMessageHeader =
        SymboticUtils.getSymPutawayMessageHeader(
            headers, alignment, SymHawkeyeEventType.PUTAWAY_REQUEST.toString());
    SymPutawayMessage symPutawayMessage =
        SymboticUtils.createPutawayAddMessage(
            containerItem, deliveryDocument, instruction4mDB, receivedContainer, SymFreightType.DA);
    String putawayPayLoad = gson.toJson(symPutawayMessage);
    return PayloadRef.builder()
        .data(Message.builder().headers(symMessageHeader).body(putawayPayLoad).build())
        .build();
  }

  /**
   * This method will validate if the Symbotic Structures SYM2 and SYM2_5 are connected. On
   * connection day, this flag ReceivingConstants.IS_SYM_CUTOVER_COMPLETED will be set to true in
   * CCM.
   *
   * @return
   */
  private boolean isSymCutOverEnabled() {
    return tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_SYM_CUTOVER_COMPLETED,
        false);
  }
  /**
   * @param consolidatedContainer
   * @param headers
   * @param transformTypeInput
   * @return
   */
  private PayloadRef prepareEIPayLoad(
      Container consolidatedContainer, HttpHeaders headers, String... transformTypeInput) {
    Map<String, Object> httpHeaders = ReceivingUtils.getForwardablHeaderWithTenantData(headers);
    String eiPayLoad;
    InventoryDetails dcReceivingInventoryDetails = null;
    for (String transformType : transformTypeInput) {
      dcReceivingInventoryDetails =
          inventoryTransformer.transformToInventory(consolidatedContainer, transformType);
    }
    eiService.populateTickTickHeaders(
        httpHeaders, dcReceivingInventoryDetails, transformTypeInput[0]);
    eiPayLoad = gson.toJson(dcReceivingInventoryDetails);
    LOGGER.info(
        "Outbox Event: EI : {} Event PayLoad :{} for trackingId: {}",
        transformTypeInput,
        eiPayLoad,
        consolidatedContainer.getTrackingId());
    return PayloadRef.builder()
        .data(Message.builder().headers(httpHeaders).body(eiPayLoad).build())
        .build();
  }

  public InstructionResponse checkIfVendorComplianceRequired(
      InstructionRequest instructionRequest,
      DeliveryDocument deliveryDocument,
      InstructionResponse instructionResponse) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    if (!instructionRequest.isVendorComplianceValidated()
        && !CollectionUtils.isEmpty(deliveryDocumentLine.getTransportationModes())) {
      if (deliveryDocumentHelper.updateVendorCompliance(deliveryDocumentLine)) {
        deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
        instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
      }
    }
    return instructionResponse;
  }

  /**
   * @param deliveryDocumentLine
   * @param quantityToBeReceived
   * @return
   */
  public int getContainersCountToBeReceived(
      DeliveryDocumentLine deliveryDocumentLine,
      Integer quantityToBeReceived,
      ReceiveInstructionRequest receiveInstructionRequest,
      Boolean isPalletPullByStore) {
    int containerCount = 0;
    boolean isSlottingEnabled =
        Objects.nonNull(receiveInstructionRequest)
            && Objects.nonNull(receiveInstructionRequest.getSlotDetails());
    boolean isAtlasConvertedItem = deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem();
    String itemPackAndHandlingCode =
        deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode();
    boolean isBreakPackNonConItem =
        RdcConstants.DA_BREAK_PACK_NON_CONVEYABLE_ITEM_HANDLING_CODES.contains(
            itemPackAndHandlingCode);
    if (isSlottingEnabled && !(isAtlasConvertedItem && isBreakPackNonConItem)
        || isPalletPullByStore) {
      return RdcConstants.CONTAINER_COUNT_ONE;
    }
    if (Objects.nonNull(receiveInstructionRequest)
        && receiveInstructionRequest.getIsLessThanCase()) {
      containerCount = quantityToBeReceived;
    } else if (itemPackAndHandlingCode.equalsIgnoreCase(
            RdcConstants.DA_CASE_PACK_NONCON_RTS_PUT_ITEM_HANDLING_CODE)
        || (Arrays.asList(RdcConstants.DA_VOICE_PUT_HANDLING_CODS).contains(itemPackAndHandlingCode)
            && !itemPackAndHandlingCode.equalsIgnoreCase(
                RdcConstants.DA_BREAK_PACK_VOICE_PUT_ITEM_HANDLING_CODE))) {
      containerCount = RdcConstants.CONTAINER_COUNT_ONE;
    } else if (itemPackAndHandlingCode.equalsIgnoreCase(
        RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE)) {
      Integer breakPackRatio = RdcUtils.getBreakPackRatio(deliveryDocumentLine);
      containerCount = quantityToBeReceived * breakPackRatio;
    } else if (isAtlasConvertedItem && isBreakPackNonConItem) {
      quantityToBeReceived =
          RdcUtils.getReceiveQtyForSlottingRequest(receiveInstructionRequest, quantityToBeReceived);
      Integer breakPackRatio = RdcUtils.getBreakPackRatio(deliveryDocumentLine);
      containerCount = quantityToBeReceived * breakPackRatio;
    } else {
      containerCount = quantityToBeReceived;
    }

    return containerCount;
  }

  /**
   * Prepare instruction summary message payload to publish to WFT
   *
   * @param instruction
   * @param deliveryDocumentLine
   * @param quantity
   * @param httpHeaders
   * @return PublishInstructionSummary
   */
  public PublishInstructionSummary prepareInstructionMessage(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      Integer quantity,
      HttpHeaders httpHeaders,
      Boolean isLessThanACase) {
    PublishInstructionSummary publishInstructionSummary = new PublishInstructionSummary();
    PublishInstructionSummary.UserInfo userInfo =
        new PublishInstructionSummary.UserInfo(
            httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY),
            httpHeaders.getFirst(ReceivingConstants.SECURITY_HEADER_KEY));
    publishInstructionSummary.setUserInfo(userInfo);
    publishInstructionSummary.setMessageId(instruction.getMessageId());

    publishInstructionSummary.setPrintChildContainerLabels(
        instruction.getPrintChildContainerLabels());
    publishInstructionSummary.setInstructionExecutionTS(new Date());
    publishInstructionSummary.setInstructionStatus(
        InstructionStatus.UPDATED.getInstructionStatus());
    if (Boolean.TRUE.equals(isLessThanACase)) {
      publishInstructionSummary.setUpdatedQty(0);
      publishInstructionSummary.setUpdatedQtyUOM(ReceivingConstants.Uom.WHPK);
    } else {
      publishInstructionSummary.setUpdatedQty(quantity);
      publishInstructionSummary.setUpdatedQtyUOM(ReceivingConstants.Uom.VNPK);
    }
    if (!Objects.isNull(deliveryDocumentLine)) {
      publishInstructionSummary.setVnpkQty(deliveryDocumentLine.getVendorPack());
      publishInstructionSummary.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    }
    if (Objects.nonNull(instruction.getActivityName())
        && instruction
            .getActivityName()
            .equals(WFTInstruction.NON_CON_CASEPACK.getActivityName())) {
      instruction.setActivityName(WFTInstruction.DA.getActivityName());
    }
    publishInstructionSummary.setInstructionCode(instruction.getInstructionCode());
    publishInstructionSummary.setInstructionMsg(instruction.getInstructionMsg());
    publishInstructionSummary.setActivityName(instruction.getActivityName());
    publishInstructionSummary.setLocation(
        new PublishInstructionSummary.Location(
            httpHeaders.getFirst(RdcConstants.WFT_LOCATION_ID),
            httpHeaders.getFirst(RdcConstants.WFT_LOCATION_TYPE),
            httpHeaders.getFirst(RdcConstants.WFT_SCC_CODE)));
    return publishInstructionSummary;
  }

  /**
   * @param instruction
   * @param deliveryDocumentLine
   * @param receivedQuantity
   * @param httpHeaders
   */
  public void publishInstruction(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      Integer receivedQuantity,
      HttpHeaders httpHeaders,
      Boolean isLessThanACase) {
    instructionHelperService.publishInstruction(
        httpHeaders,
        prepareInstructionMessage(
            instruction, deliveryDocumentLine, receivedQuantity, httpHeaders, isLessThanACase));
  }

  public List<DeliveryDocument> filterDADeliveryDocuments(
      List<DeliveryDocument> deliveryDocuments) {
    List<DeliveryDocument> filteredDADocuments =
        deliveryDocuments
            .stream()
            .filter(doc -> rdcInstructionUtils.isDADocument(doc))
            .collect(Collectors.toList());

    return filteredDADocuments;
  }

  /**
   * This method validates if all the POs are fulfilled or not. 1. In case of Mixed POs, if all
   * POs(DA/SSTK) are fulfilled against max overages, the overage instruction will be created
   * against SSTK PO/POLine based on MABD
   *
   * @param instructionRequest
   * @param receivedQuantityResponseFromRDS
   * @return
   */
  public Pair<Boolean, List<DeliveryDocument>> checkAllPOsFulfilled(
      InstructionRequest instructionRequest,
      ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS) {
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    Map<String, Long> receivedQtyMapFromRds =
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine();

    List<DeliveryDocument> availableDeliveryDocumentsIncludingMaxOverages =
        filterAvailableDeliveryDocuments(deliveryDocuments, receivedQtyMapFromRds, true);

    if (CollectionUtils.isEmpty(availableDeliveryDocumentsIncludingMaxOverages)) {
      LOGGER.error(
          "All Documents are fulfilled, creating Fixit ticket for the given delivery:{} and UPC: {}",
          instructionRequest.getDeliveryNumber(),
          instructionRequest.getUpcNumber());
      return new Pair<>(
          Boolean.TRUE,
          Collections.singletonList(
              rdcInstructionUtils.getDeliveryDocumentForOverageReporting(deliveryDocuments)));
    }
    return new Pair<>(Boolean.FALSE, deliveryDocuments);
  }

  /**
   * @param instructionRequest
   * @param receivedQuantityResponseFromRDS
   * @return
   */
  public List<DeliveryDocument> checkIfAllDAPosFulfilled(
      InstructionRequest instructionRequest,
      ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS) {
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    Map<String, Long> receivedQtyMapFromRds =
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine();

    List<DeliveryDocument> filteredDADeliveryDocuments =
        filterDADeliveryDocuments(deliveryDocuments);
    List<DeliveryDocument> availableDADeliveryDocumentsAgainstMaxReceivedQty =
        filterAvailableDeliveryDocuments(filteredDADeliveryDocuments, receivedQtyMapFromRds, true);

    if (CollectionUtils.isEmpty(availableDADeliveryDocumentsAgainstMaxReceivedQty)
        && deliveryDocuments.size() != filteredDADeliveryDocuments.size()) {
      LOGGER.error(
          "Found Non DA PO for the given delivery:{} and UPC:{}",
          instructionRequest.getDeliveryNumber(),
          instructionRequest.getUpcNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.NON_DA_PURCHASE_REF_TYPE, RdcConstants.NON_DA_PURCHASE_REF_TYPE_MSG);
    }
    return deliveryDocuments;
  }

  /**
   * @param deliveryDocuments
   * @param receivedQtyMapFromRds
   * @param includeAllowableOverages
   * @return
   */
  public List<DeliveryDocument> filterAvailableDeliveryDocuments(
      List<DeliveryDocument> deliveryDocuments,
      Map<String, Long> receivedQtyMapFromRds,
      boolean includeAllowableOverages) {
    List<DeliveryDocument> availableDeliveryDocuments = new ArrayList<>();
    boolean isDeliveryDocumentAvailableToReceive = false;
    List<DeliveryDocument> activeDeliveryDocuments =
        deliveryDocuments
            .stream()
            .filter(
                deliveryDocument ->
                    rdcInstructionUtils.filterActivePoLinesFromRDSResponse(
                        deliveryDocument, receivedQtyMapFromRds))
            .collect(Collectors.toList());

    for (DeliveryDocument deliveryDocument : activeDeliveryDocuments) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        String key =
            deliveryDocumentLine.getPurchaseReferenceNumber()
                + ReceivingConstants.DELIM_DASH
                + deliveryDocumentLine.getPurchaseReferenceLineNumber();

        if (Objects.nonNull(receivedQtyMapFromRds.get(key))) {
          int currentReceivedQtyInVnpk = receivedQtyMapFromRds.get(key).intValue();
          if (includeAllowableOverages) {
            int maxReceiveQty =
                deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
            isDeliveryDocumentAvailableToReceive = currentReceivedQtyInVnpk < maxReceiveQty;
          } else {
            isDeliveryDocumentAvailableToReceive =
                currentReceivedQtyInVnpk < deliveryDocumentLine.getTotalOrderQty();
          }
        }
      }

      if (isDeliveryDocumentAvailableToReceive) {
        availableDeliveryDocuments.add(deliveryDocument);
      }
    }

    return availableDeliveryDocuments;
  }

  /**
   * This method converts the GDM order quantities based on UOM
   *
   * @param gdmDeliveryDocumentList
   */
  public void updateQuantitiesBasedOnUOM(List<DeliveryDocument> gdmDeliveryDocumentList) {
    for (DeliveryDocument deliveryDocument : gdmDeliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        if (!deliveryDocumentLine.getQtyUOM().equals(ReceivingConstants.Uom.VNPK)) {
          int totalOrderQty =
              conversionToVendorPack(
                  deliveryDocumentLine.getTotalOrderQty(),
                  deliveryDocumentLine.getQtyUOM(),
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack());
          int overageLimit =
              conversionToVendorPack(
                  deliveryDocumentLine.getOverageQtyLimit(),
                  deliveryDocumentLine.getQtyUOM(),
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack());
          deliveryDocumentLine.setTotalOrderQty(totalOrderQty);
          deliveryDocumentLine.setOverageQtyLimit(overageLimit);
          deliveryDocumentLine.setQtyUOM(ReceivingConstants.Uom.VNPK);
        }
      }
    }
  }

  /**
   * This method validates that if the item is Master Break pack or Break pack conveyable picks
   * eligible item & return instruction.
   *
   * @param instructionRequest
   * @param deliveryDocumentLine
   * @return
   */
  public Instruction getBreakPackInstruction(
      InstructionRequest instructionRequest, DeliveryDocumentLine deliveryDocumentLine) {
    Instruction instruction = null;
    boolean isBreakPackConveyPicks = RdcUtils.isBreakPackConveyPicks(deliveryDocumentLine);
    boolean isMasterBreakPack =
        deliveryDocumentLine
            .getAdditionalInfo()
            .getPackTypeCode()
            .equals(RdcConstants.MASTER_BREAK_PACK_TYPE_CODE);
    if (isBreakPackConveyPicks || isMasterBreakPack) {
      instruction = new Instruction();
      instruction.setDeliveryNumber(Long.valueOf(instructionRequest.getDeliveryNumber()));
      instruction.setInstructionCode(
          RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
      if (isBreakPackConveyPicks) {
        LOGGER.info(
            "ItemNumber : {} is Break Pack Conveyable Pick item. Inner cases need to be labeled separately.",
            deliveryDocumentLine.getItemNbr());
        instruction.setInstructionMsg(
            String.format(
                RdcConstants.BREAK_PACK_CONVEY_PICKS_MESSAGE, deliveryDocumentLine.getItemNbr()));
      } else {
        LOGGER.info(
            "ItemNumber : {} is Master Break Pack item. Inner cases need to be labeled separately.",
            deliveryDocumentLine.getItemNbr());
        instruction.setInstructionMsg(
            String.format(
                RdcConstants.MASTER_BREAK_PACK_MESSAGE, deliveryDocumentLine.getItemNbr()));
      }
      instruction.setProviderId(RdcConstants.PROVIDER_ID);
      instruction.setGtin(instructionRequest.getUpcNumber());
      instruction.setSsccNumber(instructionRequest.getSscc());
    }
    return instruction;
  }

  public Instruction getNonConRtsPutInstruction(
      InstructionRequest instructionRequest,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders,
      DeliveryDocument deliveryDocument) {
    Instruction instruction =
        populateInstructionFields(
            instructionRequest, deliveryDocumentLine, httpHeaders, deliveryDocument);

    instruction.setDeliveryNumber(Long.valueOf(instructionRequest.getDeliveryNumber()));
    instruction.setInstructionCode(
        RdcInstructionType.NON_CON_RTS_PUT_RECEIVING.getInstructionCode());
    instruction.setInstructionMsg(
        String.format(
            RdcInstructionType.NON_CON_RTS_PUT_RECEIVING.getInstructionMsg(),
            deliveryDocumentLine.getItemNbr()));
    instruction.setSsccNumber(instructionRequest.getSscc());
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    Integer totalReceivedQty =
        deliveryDocument.getDeliveryDocumentLines().get(0).getTotalReceivedQty();
    Integer maxReceiveQuantity =
        deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
    Integer projectedReceiveQty = maxReceiveQuantity - totalReceivedQty;
    instruction.setProjectedReceiveQty(projectedReceiveQty);

    return instruction;
  }

  public Instruction populateInstructionFields(
      InstructionRequest instructionRequest,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders,
      DeliveryDocument deliveryDocument) {
    Instruction instruction = new Instruction();
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    instruction.setCreateUserId(userId);
    instruction.setCreateTs(new Date());
    instruction.setPurchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    instruction.setPurchaseReferenceLineNumber(
        deliveryDocumentLine.getPurchaseReferenceLineNumber());
    String itemDescription =
        Objects.nonNull(deliveryDocumentLine.getDescription())
            ? deliveryDocumentLine.getDescription()
            : deliveryDocumentLine.getSecondaryDescription();
    instruction.setItemDescription(itemDescription);
    instruction.setMessageId(instructionRequest.getMessageId());
    instruction.setGtin(instructionRequest.getUpcNumber());
    instruction.setPoDcNumber(deliveryDocument.getPoDCNumber());
    instruction.setLastChangeUserId(userId);
    instruction.setDeliveryNumber(Long.valueOf(instructionRequest.getDeliveryNumber()));

    instruction.setGtin(instructionRequest.getUpcNumber());
    if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      instruction.setSsccNumber(instructionRequest.getSscc());
      if (StringUtils.isBlank(instruction.getGtin())) {
        instruction.setGtin(deliveryDocumentLine.getOrderableGTIN());
      }
    }
    instruction.setPrintChildContainerLabels(false);
    // Client will show DA NON CON based on activity name-> DANonCon
    if (isNonConveyableItem(deliveryDocumentLine)) {
      instruction.setActivityName(WFTInstruction.NON_CON_CASEPACK.getActivityName());
    } else {
      instruction.setActivityName(WFTInstruction.DA.getActivityName());
    }
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);

    return instruction;
  }

  /**
   * Method prepares Break Pack Convey Picks instruction for DA items
   *
   * @param deliveryNumber
   * @param upc
   * @param sscc
   * @return
   */
  public Instruction getMasterBreakPackInstruction(
      String deliveryNumber, String upc, String sscc, Long itemNumber) {
    Instruction instruction = new Instruction();
    instruction.setDeliveryNumber(Long.valueOf(deliveryNumber));
    instruction.setInstructionCode(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    instruction.setInstructionMsg(
        String.format(RdcConstants.BREAK_PACK_CONVEY_PICKS_MESSAGE, itemNumber));
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setGtin(upc);
    instruction.setSsccNumber(sscc);
    return instruction;
  }

  public Instruction getNonConInstruction(
      String deliveryNumber, String upc, String sscc, String handlingCode) {
    Instruction instruction = new Instruction();
    instruction.setDeliveryNumber(Long.valueOf(deliveryNumber));
    instruction.setInstructionCode(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    instruction.setInstructionMsg(
        String.format(
            RdcConstants.NON_CON_HANDLING_CODES_INFO_MESSAGE,
            RdcConstants.DA_NON_CON_HANDLING_CODES_MAP.get(handlingCode)));
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setGtin(upc);
    instruction.setSsccNumber(sscc);
    return instruction;
  }

  /**
   * @param deliveryDocument
   * @param instructionRequest
   * @param instructionResponse
   * @return
   */
  public InstructionResponse validateBreakPackItems(
      DeliveryDocument deliveryDocument,
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    if (Objects.isNull(deliveryDocumentLine.getBreakPackValidationRequired())) {
      Instruction instruction = getBreakPackInstruction(instructionRequest, deliveryDocumentLine);
      if (Objects.nonNull(instruction)) {
        instructionResponse.setInstruction(instruction);
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .setBreakPackValidationRequired(Boolean.TRUE);
        instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
      }
    }
    return instructionResponse;
  }

  /**
   * @param deliveryDocument
   * @param instructionRequest
   * @param instructionResponse
   * @return
   */
  public InstructionResponse validateRtsPutItems(
      DeliveryDocument deliveryDocument,
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse,
      HttpHeaders httpHeaders) {

    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    boolean isScanToPrinEnabled =
        instructionRequest.getFeatureType().equals(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);

    if (deliveryDocumentLine
            .getAdditionalInfo()
            .getHandlingCode()
            .equals(RdcConstants.NON_CON_RTS_PUT_HANDLING_CODE)
        && !(RdcUtils.isBreakPackNonConRtsPutItem(deliveryDocumentLine))) {
      if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(),
          RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
          false)) {
        if (isScanToPrinEnabled) {
          LOGGER.info(
              "ItemNumber: {} is a Non Con RTS PUT freight, not allowed to receive in Workstation",
              deliveryDocumentLine.getItemNbr());
          throw new ReceivingBadDataException(
              ExceptionCodes.DA_RTS_PUT_NOT_ALLOWED_IN_WORKSTATION,
              ReceivingException.DA_RTS_PUT_NOT_ALLOWED_IN_WORKSTATION);
        }
        Instruction instruction =
            getNonConRtsPutInstruction(
                instructionRequest, deliveryDocumentLine, httpHeaders, deliveryDocument);
        if (Objects.nonNull(instruction)) {
          instructionResponse.setInstruction(instruction);
          instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
        }
      } else {
        LOGGER.info(
            "ItemNumber: {} is a Non Con RTS PUT freight, not allowed to receive in Workstation",
            deliveryDocumentLine.getItemNbr());
        throw new ReceivingBadDataException(
            ExceptionCodes.DA_RTS_PUT_NOT_ALLOWED_IN_WORKSTATION,
            ReceivingException.DA_RTS_PUT_NOT_ALLOWED_IN_WORKSTATION);
      }
    }
    return instructionResponse;
  }

  /**
   * @param deliveryDocument
   * @param instructionRequest
   * @param instructionResponse
   * @return
   */
  public InstructionResponse checkIfNonConveyableItem(
      DeliveryDocument deliveryDocument,
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    if (RdcUtils.isNonConveyableItem(deliveryDocumentLine)
        && Objects.isNull(deliveryDocumentLine.getNonConValidationRequired())) {
      LOGGER.info("ItemNumber : {} is Non Conveyable item", deliveryDocumentLine.getItemNbr());
      Instruction instruction =
          getNonConInstruction(
              instructionRequest.getDeliveryNumber(),
              instructionRequest.getUpcNumber(),
              instructionRequest.getSscc(),
              deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
      instructionResponse.setInstruction(instruction);
      deliveryDocument.getDeliveryDocumentLines().get(0).setNonConValidationRequired(Boolean.TRUE);
      instructionResponse.setDeliveryDocuments(Collections.singletonList(deliveryDocument));
    }
    return instructionResponse;
  }

  /**
   * This method overrides item properties pack type, handling code in delivery documentLine
   *
   * @param deliveryDocument
   * @return
   */
  public void overrideItemProperties(DeliveryDocument deliveryDocument) {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_TEMP_ITEM_OVERRIDE_ENABLED,
        false)) {
      Long itemNumber = deliveryDocumentLine.getItemNbr();
      DeliveryItemOverride deliveryItemOverride = null;
      TenantContext.get().setFetchItemOverrideDBCallStart(System.currentTimeMillis());
      boolean isAtlasConvertedItem =
          deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem();
      Optional<DeliveryItemOverride> deliveryItemOverrideOptional;
      if (isAtlasConvertedItem) {
        TenantContext.get()
            .setFetchDeliveryItemByDeliveryNumberAndItemNumberDBCallStart(
                System.currentTimeMillis());
        deliveryItemOverrideOptional =
            deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
                deliveryDocument.getDeliveryNumber(), itemNumber);
        TenantContext.get()
            .setFetchDeliveryItemByDeliveryNumberAndItemNumberDBCallEnd(System.currentTimeMillis());
        if (deliveryItemOverrideOptional.isPresent()) {
          deliveryItemOverride = deliveryItemOverrideOptional.get();
          overridePackTypeAndHandlingCode(deliveryItemOverride, deliveryDocumentLine);
        }
        LOGGER.info(
            "Latency Check: Time taken to fetch atlas converted delivery item override is={}",
            ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getFetchDeliveryItemByDeliveryNumberAndItemNumberDBCallStart(),
                TenantContext.get().getFetchDeliveryItemByDeliveryNumberAndItemNumberDBCallEnd()));
      } else {
        TenantContext.get().setFetchDeliveryItemByItemNumberDBCallStart(System.currentTimeMillis());
        deliveryItemOverrideOptional = deliveryItemOverrideService.findByItemNumber(itemNumber);
        TenantContext.get().setFetchDeliveryItemByItemNumberDBCallEnd(System.currentTimeMillis());
        LOGGER.info(
            "Latency Check: Time taken to fetch non atlas converted delivery item override is={}",
            ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getFetchDeliveryItemByItemNumberDBCallStart(),
                TenantContext.get().getFetchDeliveryItemByItemNumberDBCallEnd()));
        if (deliveryItemOverrideOptional.isPresent()) {
          deliveryItemOverride = deliveryItemOverrideOptional.get();
          if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.ITEM_OVERRIDE_SAME_DAY_ALLOWED,
              false)) {
            String dcTimeZone =
                tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
            ZonedDateTime lastChangedTs =
                convertUTCToZoneDateTime(deliveryItemOverride.getLastChangedTs(), dcTimeZone);
            ZonedDateTime currentTime = getDCDateTime(dcTimeZone);
            if ((Objects.equals(lastChangedTs.getDayOfMonth(), currentTime.getDayOfMonth())
                && Objects.equals(lastChangedTs.getMonthValue(), currentTime.getMonthValue())
                && Objects.equals(lastChangedTs.getYear(), currentTime.getYear()))) {
              overridePackTypeAndHandlingCode(deliveryItemOverride, deliveryDocumentLine);
              LOGGER.info(
                  "Override item properties for itemNumber: {}", deliveryDocumentLine.getItemNbr());
            }
          } else {
            overridePackTypeAndHandlingCode(deliveryItemOverride, deliveryDocumentLine);
          }
        }
      }
      TenantContext.get().setFetchItemOverrideDBCallEnd(System.currentTimeMillis());
      LOGGER.info(
          "Latency Check: Total time taken to override Rdc item started ts={} time & fetchItemOverrideDBCall within={} milliSeconds, correlationId={}",
          TenantContext.get().getFetchItemOverrideDBCallStart(),
          ReceivingUtils.getTimeDifferenceInMillis(
              TenantContext.get().getFetchItemOverrideDBCallStart(),
              TenantContext.get().getFetchItemOverrideDBCallEnd()),
          TenantContext.getCorrelationId());
    }
  }

  /** @param deliveryDocumentLine */
  public void overridePackTypeCodeForBreakPackItem(DeliveryDocumentLine deliveryDocumentLine) {
    // set packType based on break pack ratio
    String packTypeCode =
        RdcUtils.getBreakPackRatio(deliveryDocumentLine) > 1
            ? RdcConstants.BREAK_PACK_TYPE_CODE
            : RdcConstants.CASE_PACK_TYPE_CODE;
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode(packTypeCode);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(
            StringUtils.join(
                deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(),
                deliveryDocumentLine.getAdditionalInfo().getHandlingCode()));
    String itemHandlingMethod =
        PACKTYPE_HANDLINGCODE_MAP.get(
            deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode());
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemHandlingMethod(
            Objects.isNull(itemHandlingMethod)
                ? INVALID_HANDLING_METHOD_OR_PACK_TYPE
                : itemHandlingMethod);
  }

  /**
   * @param deliveryItemOverride
   * @param deliveryDocumentLine
   */
  private void overridePackTypeAndHandlingCode(
      DeliveryItemOverride deliveryItemOverride, DeliveryDocumentLine deliveryDocumentLine) {
    LOGGER.info(
        "Override Item packType and handlingCode for itemNumber:{} ",
        deliveryDocumentLine.getItemNbr());
    Map<String, String> itemMiscInfo = deliveryItemOverride.getItemMiscInfo();
    ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
    if (Objects.isNull(itemMiscInfo)) {
      return;
    }
    String temporaryPackTypeCode = itemMiscInfo.get(ReceivingConstants.TEMPORARY_PACK_TYPE_CODE);
    if (Objects.nonNull(temporaryPackTypeCode)) {
      additionalInfo.setPackTypeCode(
          RdcUtils.getPackTypeCodeByBreakPackRatio(deliveryDocumentLine));
    }
    String temporaryHandlingMethodCode =
        itemMiscInfo.get(ReceivingConstants.TEMPORARY_HANDLING_METHOD_CODE);
    if (Objects.nonNull(temporaryHandlingMethodCode)) {
      additionalInfo.setHandlingCode(temporaryHandlingMethodCode);
    }
    String itemPackAndHandlingCode =
        StringUtils.join(additionalInfo.getPackTypeCode(), additionalInfo.getHandlingCode());
    additionalInfo.setItemPackAndHandlingCode(itemPackAndHandlingCode);
    String itemHandlingMethod = PACKTYPE_HANDLINGCODE_MAP.get(itemPackAndHandlingCode);
    additionalInfo.setItemHandlingMethod(
        Objects.isNull(itemHandlingMethod)
            ? INVALID_HANDLING_METHOD_OR_PACK_TYPE
            : itemHandlingMethod);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
  }

  /**
   * @param containerCount
   * @param instructionRequest
   * @param deliveryDocumentLine
   * @param httpHeaders
   * @param receiveInstructionRequest
   * @return
   * @throws ReceivingException
   * @throws ExecutionException
   * @throws InterruptedException
   */
  public List<ReceivedContainer> receiveContainers(
      Integer containerCount,
      InstructionRequest instructionRequest,
      DeliveryDocumentLine deliveryDocumentLine,
      HttpHeaders httpHeaders,
      ReceiveInstructionRequest receiveInstructionRequest)
      throws ExecutionException, InterruptedException {

    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    Map<String, Object> httpHeadersMap = getForwardablHeaderWithTenantData(httpHeaders);
    if (Objects.nonNull(httpHeaders.getFirst(IS_REINDUCT_ROUTING_LABEL))) {
      httpHeadersMap.put(IS_REINDUCT_ROUTING_LABEL, Boolean.TRUE);
    }
    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            instructionRequest,
            deliveryDocumentLine,
            httpHeaders,
            containerCount,
            receiveInstructionRequest);

    receivedContainers =
        receivePalletInSmartSlotting(
            receiveContainersRequestBody,
            receiveInstructionRequest,
            receivedContainers,
            httpHeaders);
    if (!receivedContainers.isEmpty()) {
      return receivedContainers;
    }

    if (receiveContainersRequestBody.getContainerOrders().size()
            <= RdcConstants.MAX_CONTAINER_CREATION_PER_TRANSACTION_IN_RDS
        || isWhpkReceiving(deliveryDocumentLine, receiveInstructionRequest)) {
      TenantContext.get().setReceiveContainersInRdsStart(System.currentTimeMillis());
      ReceiveContainersResponseBody receiveContainersResponseBody =
          nimRDSRestApiClient.receiveContainers(receiveContainersRequestBody, httpHeadersMap);
      TenantContext.get().setReceiveContainersInRdsEnd(System.currentTimeMillis());
      receivedContainers.addAll(receiveContainersResponseBody.getReceived());
    } else {
      // Async receive request to RDS for more than the max limit
      List<CompletableFuture<ReceiveContainersResponseBody>> completableFutures = new ArrayList<>();
      Collection<List<ContainerOrder>> partitionedContainerOrderList =
          batchifyCollection(
              receiveContainersRequestBody.getContainerOrders(),
              RdcConstants.MAX_CONTAINER_CREATION_PER_TRANSACTION_IN_RDS);

      TenantContext.get().setAsyncNimRdsReceivedContainersCallStart(System.currentTimeMillis());
      for (List<ContainerOrder> containerOrders : partitionedContainerOrderList) {
        ReceiveContainersRequestBody rdsReceiveContainersBody = new ReceiveContainersRequestBody();
        rdsReceiveContainersBody.setContainerOrders(containerOrders);
        CompletableFuture<ReceiveContainersResponseBody> nimrdsReceiveContainerFuture =
            asyncNimRdsRestApiClient.getReceivedContainers(
                rdsReceiveContainersBody, httpHeadersMap);
        completableFutures.add(nimrdsReceiveContainerFuture);
      }

      CompletableFuture.allOf(
              completableFutures.toArray(new CompletableFuture[completableFutures.size()]))
          .join();
      for (CompletableFuture<ReceiveContainersResponseBody> future : completableFutures) {
        ReceiveContainersResponseBody asyncReceiveContainersResponseBody = future.get();
        receivedContainers.addAll(asyncReceiveContainersResponseBody.getReceived());
      }
      TenantContext.get().setAsyncNimRdsReceivedContainersCallEnd(System.currentTimeMillis());
      LOGGER.info(
          "Latency Check: Time taken to receive containers by nimRds asynchronously is={}",
          ReceivingUtils.getTimeDifferenceInMillis(
              TenantContext.get().getAsyncNimRdsReceivedContainersCallStart(),
              TenantContext.get().getAsyncNimRdsReceivedContainersCallEnd()));
    }

    return receivedContainers;
  }

  /**
   * This method populate total order / received qty details for the given problem details
   *
   * @param deliveryDocument
   * @param instruction
   */
  public void populateProblemReceivedQtyDetails(
      DeliveryDocument deliveryDocument, Instruction instruction) {
    ProblemLabel problemLabel =
        problemRepository.findProblemLabelByProblemTagId(instruction.getProblemTagId());
    FitProblemTagResponse fitProblemTagResponse =
        gson.fromJson(problemLabel.getProblemResponse(), FitProblemTagResponse.class);
    Integer resolutionQty = fitProblemTagResponse.getResolutions().get(0).getQuantity();
    Integer totalReceivedProblemQty =
        instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                instruction.getPurchaseReferenceNumber(),
                instruction.getPurchaseReferenceLineNumber(),
                instruction.getProblemTagId())
            .intValue();
    // override total order qty & total receive qty as specific to problem receiving
    deliveryDocument.getDeliveryDocumentLines().get(0).setTotalOrderQty(resolutionQty);
    deliveryDocument.getDeliveryDocumentLines().get(0).setTotalReceivedQty(totalReceivedProblemQty);
  }

  /**
   * This method publishes receipts/purchases to Inventory/DcFin & WFT
   *
   * @param instruction
   * @param deliveryDocument
   * @param receiveQty
   * @param httpHeaders
   * @param isAtlasConvertedItem
   * @param receivedContainers
   */
  public void postReceivingUpdates(
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      int receiveQty,
      HttpHeaders httpHeaders,
      boolean isAtlasConvertedItem,
      List<ReceivedContainer> receivedContainers,
      Boolean isLessThanACase)
      throws ReceivingException {
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    if (isAtlasConvertedItem) {
      for (ReceivedContainer receivedContainer : receivedContainers) {
        if (StringUtils.isBlank(receivedContainer.getParentTrackingId())) {
          Container consolidatedContainer =
              containerPersisterService.getConsolidatedContainerForPublish(
                  receivedContainer.getLabelTrackingId());
          if (Objects.nonNull(receivedContainer.getAsnNumber())) {
            consolidatedContainer.setAsnNumber(receivedContainer.getAsnNumber());
          }
          if (EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
            consolidatedContainer.setOfflineRcv(true);
          }

          if (receivedContainer.isSorterDivertRequired()) {
            String labelType = null;
            if (EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
              labelType =
                  getLabelTypeForOfflineRcv(
                      receivedContainer, consolidatedContainer, deliveryDocument);
            } else {
              labelType = getLabelTypeForSorterDivert(receivedContainer, consolidatedContainer);
            }
            if (Objects.nonNull(consolidatedContainer.getDestination())
                && Objects.nonNull(
                    consolidatedContainer.getDestination().get(ReceivingConstants.BU_NUMBER))) {
              // publish sorter divert message to Athena
              TenantContext.get().setDaCaseReceivingSorterPublishStart(System.currentTimeMillis());
              if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                  TenantContext.getFacilityNum().toString(),
                  RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
                  false)) {
                kafkaAthenaPublisher.publishLabelToSorter(consolidatedContainer, labelType);
              } else {
                jmsSorterPublisher.publishLabelToSorter(consolidatedContainer, labelType);
              }
              TenantContext.get().setDaCaseReceivingAthenaPublishEnd(System.currentTimeMillis());
            }
          }

          // publish consolidated containers to Inventory
          publishConsolidatedContainerToInventory(
              receivedContainer.getLabelTrackingId(), consolidatedContainer, deliveryDocument);

          // publish putaway message
          TenantContext.get().setDaCaseReceivingPutawayPublishStart(System.currentTimeMillis());
          if (receivedContainer.isRoutingLabel()
              || (isOfflineSymLabelType(receivedContainer, deliveryDocument)
                  && putawayEnabledForBreakPackItems(receivedContainer, deliveryDocument))) {
            symboticPutawayPublishHelper.publishPutawayAddMessage(
                receivedContainer, deliveryDocument, instruction, SymFreightType.DA, httpHeaders);
          }
          TenantContext.get().setDaCaseReceivingPutawayPublishEnd(System.currentTimeMillis());

          // publish consolidated or parent containers to EI
          if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              RdcConstants.IS_EI_INTEGRATION_ENABLED,
              false)) {
            TenantContext.get().setPublishEICallStart(System.currentTimeMillis());
            String[] eiEvents =
                consolidatedContainer.getInventoryStatus().equals(InventoryStatus.PICKED.name())
                    ? ReceivingConstants.EI_DC_RECEIVING_AND_PICK_EVENTS
                    : ReceivingConstants.EI_DC_RECEIVING_EVENT;

            /**
             * If it is offline receiving and ei event is DC_RECEIVING, we will not publish it to EI
             */
            if (!EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())
                || EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())
                    && Arrays.equals(eiEvents, ReceivingConstants.EI_DC_RECEIVING_EVENT)) {
              rdcContainerUtils.publishContainerToEI(consolidatedContainer, eiEvents);
            }

            TenantContext.get().setPublishEICallEnd(System.currentTimeMillis());
          }
        }
      }
    }
    if (!EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
      publishInstructionToWft(
          instruction, deliveryDocumentLine, receiveQty, httpHeaders, isLessThanACase);
    }
  }

  /**
   * This method will check whether the split for child containers is enabled or not while
   * publishing to inventory and verifies the child count that needs to be attached for each parent
   * container iteration. If the child split recommended for inventory is not available in ccm then
   * default value is set to 15
   *
   * @param parentTrackingId
   * @param consolidatedContainer
   * @param deliveryDocument
   */
  public void publishConsolidatedContainerToInventory(
      String parentTrackingId, Container consolidatedContainer, DeliveryDocument deliveryDocument) {
    JsonElement childLimitDetails =
        tenantSpecificConfigReader.getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.CHILD_CONTAINER_LIMIT_FOR_INVENTORY);
    boolean isChildContainerSplitEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_CHILD_CONTAINER_SPLIT_ENABLED,
            false);
    Set<Container> childContainerList = consolidatedContainer.getChildContainers();
    int childLimit =
        Objects.nonNull(childLimitDetails)
            ? childLimitDetails.getAsInt()
            : RdcConstants.DEFAULT_CHILD_CONTAINER_LIMIT_FOR_INVENTORY;
    if (ContainerType.PALLET.getText().equalsIgnoreCase(consolidatedContainer.getContainerType())
        && !CollectionUtils.isEmpty(childContainerList)
        && isChildContainerSplitEnabled
        && childContainerList.size() > childLimit
        && !EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
      List<Container> consolidatedContainers =
          splitChildContainersInBatch(
              consolidatedContainer, childContainerList, parentTrackingId, childLimit);
      for (Container container : consolidatedContainers) {
        TenantContext.get().setReceiveInstrPublishReceiptsCallStart(System.currentTimeMillis());
        rdcContainerUtils.publishContainersToInventory(container);
        TenantContext.get().setReceiveInstrPublishReceiptsCallEnd(System.currentTimeMillis());
      }
    } else {
      // publish consolidated or parent containers to Inventory
      TenantContext.get().setReceiveInstrPublishReceiptsCallStart(System.currentTimeMillis());
      rdcContainerUtils.publishContainersToInventory(consolidatedContainer);
      TenantContext.get().setReceiveInstrPublishReceiptsCallEnd(System.currentTimeMillis());
    }
  }

  /**
   * @param parentTrackingId
   * @param consolidatedContainer
   */
  public void publishConsolidatedSplitContainerToInventoryForDsdc(
      String parentTrackingId, Container consolidatedContainer) {
    JsonElement childLimitDetails =
        tenantSpecificConfigReader.getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.CHILD_CONTAINER_LIMIT_FOR_INVENTORY);
    Set<Container> childContainerList = consolidatedContainer.getChildContainers();
    int childLimit =
        Objects.nonNull(childLimitDetails)
            ? childLimitDetails.getAsInt()
            : RdcConstants.DEFAULT_CHILD_CONTAINER_LIMIT_FOR_INVENTORY;
    List<Container> consolidatedContainers =
        splitChildContainersInBatch(
            consolidatedContainer, childContainerList, parentTrackingId, childLimit);
    for (Container container : consolidatedContainers) {
      TenantContext.get().setReceiveInstrPublishReceiptsCallStart(System.currentTimeMillis());
      rdcContainerUtils.publishContainersToInventory(container);
      TenantContext.get().setReceiveInstrPublishReceiptsCallEnd(System.currentTimeMillis());
    }
  }

  /**
   * This method will split the child containers based on a config child container limit as
   * Inventory recommends to attach child containers to the parent with a limit. Parent container
   * will be same but the messageId we keep as different to make sure DcFin accepts different
   * messageId for the transactions. This method returns list of containers which has parent and
   * child containers. We will iterate the containers and publish to inventory.
   *
   * @param trackingId
   * @param childLimit
   * @return ConsolidatedContainer
   * @throws ReceivingException
   */
  public List<Container> splitChildContainersInBatch(
      Container consolidatedContainer,
      Set<Container> childContainerList,
      String trackingId,
      Integer childLimit) {
    LOGGER.info(
        "Entering splitChildContainersInBatch() with trackingId:{} and childLimit:{}",
        trackingId,
        childLimit);
    List<Container> containerListWithSplitChildContainers = new ArrayList<>();
    if (StringUtils.isEmpty(trackingId)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.TRACKING_ID_CANNOT_BE_EMPTY, "TrackingId should not be null");
    }
    if (Objects.isNull(consolidatedContainer)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.CONTAINER_NOT_FOUND, "Container not found");
    }
    List<Container> childList = new ArrayList<>(childContainerList);
    List<List<Container>> childContainerSubLists = Lists.partition(childList, childLimit);

    for (List<Container> childContainerSubList : childContainerSubLists) {
      Container parentContainer = SerializationUtils.clone(consolidatedContainer);
      Set<Container> childContainerSubSet = new HashSet<>(childContainerSubList);
      parentContainer.setChildContainers(childContainerSubSet);
      parentContainer.setHasChildContainers(!CollectionUtils.isEmpty(childContainerSubSet));
      parentContainer.setMessageId(UUID.randomUUID().toString());
      containerListWithSplitChildContainers.add(parentContainer);
    }
    LOGGER.info(
        "Split {} child containers into {} parent container(s)",
        childList.size(),
        containerListWithSplitChildContainers.size());
    return containerListWithSplitChildContainers;
  }

  /**
   * Validating offline label type & if itemHandlingCode is {I,J,C}
   *
   * @param receivedContainer
   * @param deliveryDocument
   * @return
   */
  private boolean isOfflineSymLabelType(
      ReceivedContainer receivedContainer, DeliveryDocument deliveryDocument) {
    String itemHandlingCode =
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getItemHandlingMethod();

    if (Objects.nonNull(itemHandlingCode)
        && (Objects.nonNull(receivedContainer.getLabelType())
            && RdcConstants.OFFLINE_LABEL_TYPE.contains(receivedContainer.getLabelType()))) {
      return RdcConstants.SYM_ELIGIBLE_CON_ITEM_HANDLING_CODES.contains(itemHandlingCode);
    } else return false;
  }

  /**
   * Validate that if offline label type & break pack & repack than putaway should not be send
   *
   * @param receivedContainer
   * @param deliveryDocument
   * @return
   */
  public boolean putawayEnabledForBreakPackItems(
      ReceivedContainer receivedContainer, DeliveryDocument deliveryDocument) {
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_PUTAWAY_ENABLED_FOR_BREAKPACK_ITEMS,
        false)) {

      return !(InventoryLabelType.XDK1.getType().equals(receivedContainer.getLabelType())
          || ReceivingConstants.REPACK.equals(deliveryDocument.getCtrType()));
    } else return true;
  }

  public void publishInstructionToWft(
      Instruction instruction,
      DeliveryDocumentLine deliveryDocumentLine,
      int receiveQty,
      HttpHeaders httpHeaders,
      Boolean isLessThanACase) {
    TenantContext.get().setDaCaseReceivingPublishWFTCallStart(System.currentTimeMillis());
    publishInstruction(instruction, deliveryDocumentLine, receiveQty, httpHeaders, isLessThanACase);
    TenantContext.get().setDaCaseReceivingPublishWFTCallEnd(System.currentTimeMillis());
  }

  /**
   * This method will return the label type based on container & receivedContainer info. if
   * container has childContainers then the labelType is PUT if the labelType is Routing then the
   * labelType can be derived either based on CCM config else SymAsrsSorterMapping Enum , CCM config
   * will be removed in future once we confirm the asrsAlignment values are passed correctly from
   * upstream(orders). CCM will give a flexibility to override the SYM label type (SYM00020 /
   * SYM00025) on demand. If SSCC available on container then its DSDC ways of receiving it.
   *
   * @param receivedContainer
   * @param consolidatedContainer
   * @return
   */
  public String getLabelTypeForSorterDivert(
      ReceivedContainer receivedContainer, Container consolidatedContainer) {
    // ToDo: Modify the label types for PUT label type
    return StringUtils.isNotBlank(consolidatedContainer.getSsccNumber())
        ? LabelType.DSDC.name()
        : consolidatedContainer.isHasChildContainers()
            ? LabelType.PUT.name()
            : receivedContainer.isRoutingLabel()
                ? StringUtils.isNotBlank(rdcManagedConfig.getSymEligibleLabelType())
                    ? rdcManagedConfig.getSymEligibleLabelType()
                    : SymAsrsSorterMapping.valueOf(receivedContainer.getStoreAlignment())
                        .getSymLabelType()
                : LabelType.MFC.name().equals(receivedContainer.getDestType())
                    ? LabelType.MFC.name()
                    : LabelType.STORE.name();
  }

  /**
   * @param receivedContainer
   * @return
   */
  private String getLabelTypeForOfflineRcv(
      ReceivedContainer receivedContainer, Container container, DeliveryDocument deliveryDocument) {
    if (LabelType.XDK1.name().equals(receivedContainer.getLabelType())) {
      return LabelType.PUT.name();
    } else {
      if (LabelType.XDK2.name().equals(receivedContainer.getLabelType())
          && RdcConstants.MANUAL.equalsIgnoreCase(receivedContainer.getStoreAlignment()))
        return LabelType.STORE.name();
      return LabelType.XDK2.name().equals(receivedContainer.getLabelType())
              && isOfflineSymLabelType(receivedContainer, deliveryDocument)
          ? SymAsrsSorterMapping.valueOf(receivedContainer.getStoreAlignment()).getSymLabelType()
          : LabelType.STORE.name();
    }
  }

  /**
   * This method validates whether smart slotting is enabled for DA Items and provide received
   * containers based on the slotting info
   *
   * @param receiveContainersRequestBody
   * @param receiveInstructionRequest
   * @param receivedContainers
   * @param httpHeaders
   */
  public List<ReceivedContainer> receivePalletInSmartSlotting(
      ReceiveContainersRequestBody receiveContainersRequestBody,
      ReceiveInstructionRequest receiveInstructionRequest,
      List<ReceivedContainer> receivedContainers,
      HttpHeaders httpHeaders) {

    if (Objects.nonNull(
            receiveContainersRequestBody.getContainerOrders().get(0).getSlottingOverride())
        && Objects.nonNull(receiveInstructionRequest.getSlotDetails())
        && tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false)) {
      if (Objects.nonNull(receiveInstructionRequest.getDeliveryDocuments())) {
        DeliveryDocument deliveryDocument = receiveInstructionRequest.getDeliveryDocuments().get(0);
        if (Objects.nonNull(deliveryDocument.getDeliveryDocumentLines())) {
          receiveInstructionRequest.setDeliveryDocumentLines(
              deliveryDocument.getDeliveryDocumentLines());
        }
      }
      TenantContext.get().setReceiveInstrGetSlotCallWithRdsPayloadStart(System.currentTimeMillis());
      SlottingPalletResponse slottingPalletResponse =
          rdcSlottingUtils.receiveContainers(
              receiveInstructionRequest, null, httpHeaders, receiveContainersRequestBody);
      TenantContext.get().setReceiveInstrGetSlotCallWithRdsPayloadEnd(System.currentTimeMillis());

      SlottingPalletResponseWithRdsResponse slottingPalletResponseWithRdsResponse =
          (SlottingPalletResponseWithRdsResponse) slottingPalletResponse;
      receivedContainers.addAll(slottingPalletResponseWithRdsResponse.getRds().getReceived());
    }
    return receivedContainers;
  }

  /**
   * Prepare outbox events for cancel container
   *
   * @param container
   * @param httpHeaders
   * @param labelAction
   * @return
   */
  public Collection<OutboxEvent> buildOutboxEventsForCancelContainers(
      Container container, HttpHeaders httpHeaders, LabelAction labelAction) {
    Map<String, List<PayloadRef>> outboxPolicyMap = new HashMap<>();
    buildOutboxEventForCancelContainerHawkeyePutawayRequest(
        container, outboxPolicyMap, httpHeaders);
    buildOutboxEventForEIVoid(container, outboxPolicyMap);
    if (Objects.nonNull(labelAction)) {
      buildOutboxEventForWftCancelContainer(container, labelAction, httpHeaders, outboxPolicyMap);
    }
    Collection<OutboxEvent> outboxEvents =
        buildOutboxEvent(container.getTrackingId(), outboxPolicyMap);
    return outboxEvents;
  }

  /**
   * Build cancel container putaway request payLoad for Outbox event
   *
   * @param container
   * @param outboxPolicyMap
   * @param httpHeaders
   */
  private void buildOutboxEventForCancelContainerHawkeyePutawayRequest(
      Container container, Map<String, List<PayloadRef>> outboxPolicyMap, HttpHeaders httpHeaders) {
    boolean isSymPutawayEligible =
        SymboticUtils.isValidForSymPutaway(
            container.getContainerItems().get(0).getAsrsAlignment(),
            appConfig.getValidSymAsrsAlignmentValues(),
            container.getContainerItems().get(0).getSlotType());
    if (isSymPutawayEligible) {
      PayloadRef putawayPayloadRef =
          prepareCancelContainerHawkeyePutawayPayLoad(container, httpHeaders);
      outboxPolicyMap.put(
          outboxConfig.getKafkaPublisherPolicyPutawayHawkeye(), Arrays.asList(putawayPayloadRef));
    }
  }

  /**
   * Prepare cancel container putaway payload
   *
   * @param container
   * @param httpHeaders
   * @return
   */
  private PayloadRef prepareCancelContainerHawkeyePutawayPayLoad(
      Container container, HttpHeaders httpHeaders) {
    Map<String, Object> symMessageHeader =
        SymboticUtils.getSymPutawayMessageHeader(
            httpHeaders,
            container.getContainerItems().get(0).getAsrsAlignment(),
            SymHawkeyeEventType.PUTAWAY_REQUEST.toString());
    SymPutawayMessage symPutawayMessage =
        SymboticUtils.createSymPutawayDeleteMessage(container.getTrackingId());
    String putawayPayLoad = gson.toJson(symPutawayMessage);
    return PayloadRef.builder()
        .data(Message.builder().headers(symMessageHeader).body(putawayPayLoad).build())
        .build();
  }

  /**
   * Build EI Void request payLoad for Outbox event
   *
   * @param container
   * @param outboxPolicyMap
   */
  @SneakyThrows
  private void buildOutboxEventForEIVoid(
      Container container, Map<String, List<PayloadRef>> outboxPolicyMap) {
    boolean isEIEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false);
    if (isEIEnabled) {
      Container consolidatedContainer =
          containerPersisterService.getConsolidatedContainerForPublish(container.getTrackingId());
      String inboundChannelMethod =
          consolidatedContainer.getContainerItems().get(0).getInboundChannelMethod();
      if (StringUtils.isNotBlank(inboundChannelMethod)
          && ReceivingConstants.DA_CHANNEL_METHODS_FOR_RDC.contains(inboundChannelMethod)) {
        List<PayloadRef> payloadRefList;
        if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(
            container.getChildContainers())) {
          payloadRefList =
              container
                  .getChildContainers()
                  .stream()
                  .map(childContainer -> prepareEIVoidPayLoad(childContainer))
                  .collect(Collectors.toList());
        } else {
          payloadRefList = Arrays.asList(prepareEIVoidPayLoad(container));
        }
        outboxPolicyMap.put(outboxConfig.getKafkaPublisherPolicyEIDCVoidEvent(), payloadRefList);
      }
    }
  }

  /**
   * Prepare EI Void payload
   *
   * @param container
   * @return
   */
  private PayloadRef prepareEIVoidPayLoad(Container container) {
    InventoryDetails inventoryDetails =
        inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_VOID);
    Map<String, Object> httpHeaders = new HashMap<>();
    eiService.populateTickTickHeaders(httpHeaders, inventoryDetails, ReceivingConstants.DC_VOID);
    String eiPayLoad = gson.toJson(inventoryDetails);
    LOGGER.info(
        "Outbox Event: EI Void : {} Event PayLoad :{} for trackingId: {}",
        ReceivingConstants.DC_VOID,
        eiPayLoad,
        container.getTrackingId());
    return PayloadRef.builder()
        .data(Message.builder().headers(httpHeaders).body(eiPayLoad).build())
        .build();
  }

  /**
   * Build Wft request payLoad for Outbox event
   *
   * @param container
   * @param labelAction
   * @param httpHeaders
   * @param outboxPolicyMap
   */
  private void buildOutboxEventForWftCancelContainer(
      Container container,
      LabelAction labelAction,
      HttpHeaders httpHeaders,
      Map<String, List<PayloadRef>> outboxPolicyMap) {
    final Integer currentContainerQtyInVnpk =
        ReceivingUtils.conversionToVendorPack(
            container.getContainerItems().get(0).getQuantity(),
            ReceivingConstants.Uom.EACHES,
            container.getContainerItems().get(0).getVnpkQty(),
            container.getContainerItems().get(0).getWhpkQty());
    if (!ObjectUtils.allNotNull(
        httpHeaders.getFirst(RdcConstants.WFT_LOCATION_ID),
        httpHeaders.getFirst(RdcConstants.WFT_LOCATION_TYPE),
        httpHeaders.getFirst(RdcConstants.WFT_SCC_CODE))) {
      LocationInfo locationInfo =
          locationService.getLocationInfoByIdentifier(container.getLocation(), httpHeaders);
      rdcContainerUtils.setLocationHeaders(httpHeaders, locationInfo);
    }
    Integer newContainerQty = 0;
    Integer differenceInAdjustedQty = currentContainerQtyInVnpk - newContainerQty;
    PublishInstructionSummary publishInstructionSummary =
        rdcInstructionUtils.prepareInstructionMessage(
            container.getContainerItems().get(0),
            labelAction,
            differenceInAdjustedQty,
            httpHeaders);
    String payload = gson.toJson(publishInstructionSummary);
    Map<String, Object> httpHeadersMap =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);
    LOGGER.info(
        "Outbox Event: WFT Event PayLoad :{} for trackingId: {}",
        payload,
        container.getTrackingId());
    PayloadRef payloadRef =
        PayloadRef.builder()
            .data(Message.builder().headers(httpHeadersMap).body(payload).build())
            .build();
    outboxPolicyMap.put(outboxConfig.getKafkaPublisherPolicyWFT(), Arrays.asList(payloadRef));
  }

  /**
   * @param adjustedReceipt
   * @param labelDataList
   * @param container
   * @param instruction
   * @param outboxEvents
   */
  @Transactional
  @InjectTenantFilter
  public void postCancelContainersUpdates(
      Receipt adjustedReceipt,
      List<LabelData> labelDataList,
      Container container,
      Instruction instruction,
      Collection<OutboxEvent> outboxEvents) {
    if (Objects.nonNull(adjustedReceipt)) {
      receiptService.saveReceipt(adjustedReceipt);
    }
    if (Objects.nonNull(container)) {
      containerPersisterService.saveContainer(container);
    }
    if (Objects.nonNull(instruction)) {
      instructionPersisterService.saveInstruction(instruction);
    }
    if (!CollectionUtils.isEmpty(labelDataList)) {
      labelDataService.saveAll(labelDataList);
    }
    if (!CollectionUtils.isEmpty(outboxEvents)) {
      outboxEventSinkService.saveAllEvent(outboxEvents);
    }
  }

  /**
   * This method build outbox events in Automation flow for all external integrations from Receiving
   * (Inventory / Hawkeye / EI / WFT) Each integration payLoad with headers will be set in the
   * corresponding outbox policy. Based on receivedContainer flags, putaway and sorter Divert will
   * be sent isAutoReceivedContainer - For SYM - FLIB only, Automation - true, Exception flow -
   * false isSorterDivertRequired - For DPS - ACL.
   *
   * @param receivedContainers
   * @param httpHeaders
   * @param instruction
   * @param deliveryDocument
   * @param isDAContainer
   * @return
   * @throws ReceivingException
   */
  public Collection<OutboxEvent> automationBuildOutboxEvents(
      List<ReceivedContainer> receivedContainers,
      HttpHeaders httpHeaders,
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      Boolean isDAContainer)
      throws ReceivingException {
    Collection<OutboxEvent> outboxEvents = null;
    Map<String, List<PayloadRef>> outboxPolicyMap = new HashMap<>();
    for (ReceivedContainer receivedContainer : receivedContainers) {
      if (StringUtils.isBlank(receivedContainer.getParentTrackingId())) {
        Container consolidatedContainer =
            containerPersisterService.getConsolidatedContainerForPublish(
                receivedContainer.getLabelTrackingId());
        buildOutboxEventForInventory(consolidatedContainer, outboxPolicyMap, httpHeaders);
        if (Boolean.TRUE.equals(isDAContainer)) {
          // For Flib Containers - If putaway needed for exception container, set this flag to False
          if (!receivedContainer.isAutoReceivedContainer()) {
            buildOutboxEventForPutawayRequest(
                consolidatedContainer,
                receivedContainer,
                instruction,
                deliveryDocument,
                outboxPolicyMap,
                httpHeaders);
          }
          // For DPS - ACL - Sorter Divert is needed. FLIB, this will be false
          if (receivedContainer.isSorterDivertRequired()) {
            buildOutboxEventForSorterDivert(
                consolidatedContainer,
                receivedContainer,
                outboxPolicyMap,
                httpHeaders,
                deliveryDocument);
          }
          buildOutboxPolicyForEI(consolidatedContainer, outboxPolicyMap, httpHeaders);
        }
        outboxEvents = buildOutboxEvent(consolidatedContainer.getTrackingId(), outboxPolicyMap);
      }
    }

    if (!CollectionUtils.isEmpty(outboxEvents)) {
      outboxEvents.addAll(
          buildOutboxEventForWFT(
              instruction,
              deliveryDocument.getDeliveryDocumentLines().get(0),
              instruction.getReceivedQuantity(),
              httpHeaders,
              false));
    }
    return outboxEvents;
  }

  /**
   * This method build Outbox event for Label Data
   *
   * @param httpHeaders
   * @param labelData
   */
  public void publishAutomationOutboxRdcLabelData(
      HttpHeaders httpHeaders,
      com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData labelData) {
    Map<String, List<PayloadRef>> outboxLabelDataMap = new HashMap<>();
    buildAutomationOutboxEventForRdcLabel(labelData, outboxLabelDataMap, httpHeaders);
    Collection<OutboxEvent> buildOutboxEventForLabelData =
        buildOutboxEvent(labelData.getDeliveryNumber(), outboxLabelDataMap);
    persistOutboxEvents(buildOutboxEventForLabelData);
  }

  private void buildAutomationOutboxEventForRdcLabel(
      com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData aclLabelDataTO,
      Map<String, List<PayloadRef>> outboxLabelDataMap,
      HttpHeaders httpHeaders) {
    PayloadRef aclLabelDataTOPayLoad = prepareLabelDataPayLoad(aclLabelDataTO, httpHeaders);
    LOGGER.info(
        "Hawkeye Outbox Event: LabelData : Label data request body:{} with headers :{} ",
        aclLabelDataTOPayLoad.getData().getBody(),
        aclLabelDataTOPayLoad.getData().getHeaders());
    outboxLabelDataMap.put(
        outboxConfig.getKafkaPublisherHawkeyeLabelData(),
        Collections.singletonList(aclLabelDataTOPayLoad));
  }

  private PayloadRef prepareLabelDataPayLoad(
      com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData aclLabelDataTO,
      HttpHeaders headers) {
    Map<String, Object> httpHeaders = ReceivingUtils.getForwardablHeaderWithTenantData(headers);
    httpHeaders.put(ReceivingConstants.REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    String aclLabelDataTOPayLoad = gson.toJson(aclLabelDataTO);
    return PayloadRef.builder()
        .data(Message.builder().headers(httpHeaders).body(aclLabelDataTOPayLoad).build())
        .build();
  }

  /**
   * This method validates delivery/item for controlled Automation pilot, if the config flag is
   * enabled.
   *
   * @param deliveryNumber
   * @param itemNumber
   * @return
   */
  public boolean isPilotDeliveryItemEnabledForAutomation(Long deliveryNumber, Long itemNumber) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        RdcConstants.IS_AUTOMATION_DELIVERY_FILTER_ENABLED,
        false)) {
      deliveryNumber = Objects.nonNull(deliveryNumber) ? deliveryNumber : 0l;
      itemNumber = Objects.nonNull(itemNumber) ? itemNumber : 0l;
      if (!CollectionUtils.isEmpty(rdcManagedConfig.getValidAutomationDeliveries())) {
        if (!CollectionUtils.isEmpty(rdcManagedConfig.getValidAutomationItems()))
          return rdcManagedConfig.getValidAutomationDeliveries().contains(deliveryNumber.toString())
              && rdcManagedConfig.getValidAutomationItems().contains(itemNumber.toString());
        else
          return rdcManagedConfig
              .getValidAutomationDeliveries()
              .contains(deliveryNumber.toString());
      }
    }
    return true;
  }

  /**
   * This method build outbox event for instruction completion for DSDC flow
   *
   * @param headers
   * @param body
   * @param eventIdentifier
   * @param metadata
   * @param publisherPolicyId
   * @param executionTs
   * @return
   */
  public OutboxEvent buildOutboxEvent(
      Map<String, Object> headers,
      String body,
      String eventIdentifier,
      MetaData metadata,
      String publisherPolicyId,
      Instant executionTs) {
    Message message = Message.builder().headers(headers).body(body).build();
    PayloadRef payloadRef = PayloadRef.builder().data(message).build();
    return OutboxEvent.builder()
        .eventIdentifier(eventIdentifier)
        .payloadRef(payloadRef)
        .metaData(metadata)
        .publisherPolicyId(publisherPolicyId)
        .executionTs(executionTs)
        .build();
  }

  /**
   * This method build outbox events for all external integrations from Receiving (Inventory /EI)
   * Each integration payLoad with headers will be set in the corresponding outbox policy.
   *
   * @param receivedContainers
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  public Collection<OutboxEvent> buildOutboxEventsForAsyncFlow(
      List<ReceivedContainer> receivedContainers, HttpHeaders httpHeaders)
      throws ReceivingException {
    Collection<OutboxEvent> outboxEvents = null;
    Map<String, List<PayloadRef>> outboxPolicyMap = new HashMap<>();
    for (ReceivedContainer receivedContainer : receivedContainers) {
      Container consolidatedContainer =
          containerPersisterService.getConsolidatedContainerForPublish(
              receivedContainer.getLabelTrackingId());
      buildOutboxEventForInventory(consolidatedContainer, outboxPolicyMap, httpHeaders);
      buildOutboxPolicyForEI(consolidatedContainer, outboxPolicyMap, httpHeaders);
      outboxEvents = buildOutboxEvent(consolidatedContainer.getTrackingId(), outboxPolicyMap);
    }
    return outboxEvents;
  }

  /**
   * Block flow for non atlas item with feature flag
   *
   * @param deliveryDocumentLine
   */
  public void blockRdsReceivingForNonAtlasItem(DeliveryDocumentLine deliveryDocumentLine) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_RDS_RECEIVING_BLOCKED,
        false)) {
      String itemPackAndHandlingCode =
          deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode();
      Long itemNumber = deliveryDocumentLine.getItemNbr();
      LOGGER.error(
          String.format(
              ReceivingException.GLS_RCV_ITEM_IS_NOT_ATLAS_SUPPORTED,
              itemNumber,
              itemPackAndHandlingCode));
      throw new ReceivingBadDataException(
          String.format(ExceptionCodes.GLS_RCV_ITEM_IS_NOT_ATLAS_SUPPORTED),
          String.format(
              ReceivingException.GLS_RCV_ITEM_IS_NOT_ATLAS_SUPPORTED,
              itemNumber,
              itemPackAndHandlingCode),
          String.valueOf(itemNumber),
          itemPackAndHandlingCode);
    }
  }

  public LabelFormat getLabelFormatForPallet(DeliveryDocumentLine deliveryDocumentLine) {
    LabelFormat labelFormat;
    if (Objects.nonNull(deliveryDocumentLine.getAdditionalInfo())
        && deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem()) {
      labelFormat = LabelFormat.ATLAS_RDC_PALLET;
    } else {
      labelFormat = LabelFormat.LEGACY_SSTK;
    }
    return labelFormat;
  }

  /**
   * Validate and return NGR services enabled or not
   *
   * @return
   */
  public boolean isNGRServicesEnabled() {
    return !tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), RdcConstants.IS_NGR_SERVICES_DISABLED, false);
  }
}
