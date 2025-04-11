package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.retrieveUserId;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.stringfyJson;
import static com.walmart.move.nim.receiving.endgame.common.EndGameUtils.getDefaultSellerId;
import static com.walmart.move.nim.receiving.endgame.constants.EndgameConstants.DEFAULT_USER;
import static com.walmart.move.nim.receiving.endgame.constants.EndgameConstants.DIVERT_DESTINATION;
import static com.walmart.move.nim.receiving.endgame.constants.EndgameConstants.SLOTTING_DIVERT_URI;
import static com.walmart.move.nim.receiving.endgame.model.SlotMoveType.PUTAWAY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GSON_UTC_ADAPTER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PURCHASE_ORDER_PARTITION_SIZE_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.STATUS_CANCELLED;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.core.advice.*;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingForwardedException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.framework.expression.StandardExpressionEvaluator;
import com.walmart.move.nim.receiving.core.framework.expression.TenantPlaceholder;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.service.AuditService;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.ItemMDMService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.common.SlottingUtils;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.entity.SlottingDestination;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.endgame.model.*;
import com.walmart.move.nim.receiving.endgame.repositories.SlottingDestinationRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.google.common.collect.Lists;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Endgame's Slotting service responsible for providing implementations for slotting related
 * operations.
 */
public class EndGameSlottingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndGameSlottingService.class);

  @ManagedConfiguration private AppConfig appConfig;

  @ManagedConfiguration private EndgameManagedConfig endgameManagedConfig;

  @ManagedConfiguration private KafkaConfig kafkaConfig;

  @Autowired private IOutboxPublisherService outboxPublisherService;

  @Value("${endgame.diverts.topic}")
  private String hawkeyeDivertsTopic;

  @Value("${endgame.divert.update.topic}")
  private String hawkeyeDivertUpdateTopic;

  @Autowired private Gson gson;

  @Autowired ItemMDMService itemMDMService;

  @Autowired private ReceiptService receiptService;

  @Autowired private SlottingDestinationRepository slottingDestinationRepository;

  @Autowired private AuditService auditService;

  @Autowired private TenantSpecificConfigReader configUtils;

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_SERVICE)
  private DeliveryService endGameDeliveryService;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService endGameDeliveryMetaDataService;

  @SecurePublisher private KafkaTemplate securePublisher;

  @Autowired
  @Qualifier(GSON_UTC_ADAPTER)
  private Gson gsonUTCDateAdapter;

  public EndGameSlottingService() {}

  /**
   * Gets diverts from slotting.
   *
   * @param slottingDivertRequest the slotting divert request
   * @return the diverts
   */
  @Timed(name = "SLT-Get-Divert", level1 = "uwms-receiving", level2 = "SLT-Get-Divert")
  @ExceptionCounted(
      name = "SLT-Get-Divert-Exception",
      level1 = "uwms-receiving",
      level2 = "SLT-Get-Divert-Exception")
  @TimeTracing(
      component = AppComponent.ENDGAME,
      executionFlow = "SLT-Get-Divert",
      type = Type.REST,
      externalCall = true)
  public SlottingDivertResponse getDivertsFromSlotting(
      SlottingDivertRequest slottingDivertRequest) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.add(EndgameConstants.ORG_UNIT_ID, endgameManagedConfig.getOrgUnitId());

    String slottingUrl = appConfig.getSlottingBaseUrl() + SLOTTING_DIVERT_URI;

    ResponseEntity<String> slottingDivertResponseEntity;
    try {
      slottingDivertResponseEntity =
          restConnector.exchange(
              slottingUrl,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(slottingDivertRequest), httpHeaders),
              String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          slottingUrl,
          slottingDivertRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.SLOTTING_INTERNAL_ERROR,
          String.format(
              EndgameConstants.SLOTTING_BAD_RESPONSE_ERROR_MSG,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          slottingUrl,
          slottingDivertRequest,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.SLOTTING_NOT_ACCESSIBLE,
          String.format(EndgameConstants.SLOTTING_RESOURCE_RESPONSE_ERROR_MSG, e.getMessage()));
    }
    if (Objects.isNull(slottingDivertResponseEntity) || !slottingDivertResponseEntity.hasBody()) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_INFO_MESSAGE, slottingUrl, slottingDivertRequest, "");
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SLOTTING_REQ,
          String.format(
              EndgameConstants.SLOTTING_UNABLE_TO_PROCESS_ERROR_MSG, slottingDivertResponseEntity));
    }
    LOGGER.info(
        ReceivingConstants.RESTUTILS_INFO_MESSAGE,
        slottingUrl,
        ReceivingUtils.stringfyJson(slottingDivertRequest),
        ReceivingUtils.stringfyJson(slottingDivertResponseEntity.getBody()));
    return gson.fromJson(slottingDivertResponseEntity.getBody(), SlottingDivertResponse.class);
  }

  /**
   * Save slotting info for an item into database. Mainly for debug ability & fallback if slotting
   * service is down.
   *
   * @param divertDestinationList the end game slotting data
   */
  @Transactional
  @InjectTenantFilter
  public List<SlottingDestination> save(List<DivertDestinationToHawkeye> divertDestinationList) {
    List<Pair<String, String>> sellerIdCaseUPCList = new ArrayList<>();

    divertDestinationList.forEach(
        divertDestination -> {
          Pair<String, String> pair =
              new Pair(divertDestination.getSellerId(), divertDestination.getCaseUPC());
          sellerIdCaseUPCList.add(pair);
        });

    LOGGER.info("No of sellerId<->caseUPC from slotting [size={}]", sellerIdCaseUPCList.size());

    // Batching whole item as SQL server won't support more the 999 in IN clause
    Collection<List<Pair<String, String>>> sellerIdUPCInBatch =
        ReceivingUtils.batchifyCollection(sellerIdCaseUPCList, appConfig.getInSqlBatchSize());

    LOGGER.info(
        "No of sellerId<->caseUPC batch to query into db [size={}]", sellerIdUPCInBatch.size());

    List<SlottingDestination> slottingDestinationList = new ArrayList<>();

    sellerIdUPCInBatch.forEach(
        pairs -> {
          List<String> sellerIdList = new ArrayList<>();
          List<String> caseUPCList = new ArrayList<>();
          pairs.forEach(
              pair -> {
                sellerIdList.add(pair.getKey());
                caseUPCList.add(pair.getValue());
              });
          slottingDestinationList.addAll(
              slottingDestinationRepository.findByCaseUPCInAndSellerIdIn(
                  caseUPCList, sellerIdList));
        });

    LOGGER.info("No of found records on db [size={}]", slottingDestinationList.size());

    Map<String, SlottingDestination> existingRecord = new HashMap<>();
    slottingDestinationList.forEach(
        slottingDestination -> {
          String key =
              new StringBuilder()
                  .append(slottingDestination.getCaseUPC())
                  .append(EndgameConstants.DELIM_DASH)
                  .append(slottingDestination.getSellerId())
                  .toString();
          existingRecord.put(key, slottingDestination);
        });

    divertDestinationList.forEach(
        divertDestination -> {
          String key =
              new StringBuilder()
                  .append(divertDestination.getCaseUPC())
                  .append(EndgameConstants.DELIM_DASH)
                  .append(divertDestination.getSellerId())
                  .toString();
          if (existingRecord.keySet().contains(key)) {
            existingRecord.get(key).setDestination(divertDestination.getDestination());
          } else {
            slottingDestinationList.add(SlottingUtils.populateSlottingEntity(divertDestination));
          }
        });

    LOGGER.info("Saving upc-divert info in the database");

    return slottingDestinationRepository.saveAll(slottingDestinationList);
  }

  /**
   * Send slotting details over Kafka to Hawkeye on pre-label generation.
   *
   * @param endgameSlottingData the endgame slotting data
   * @param deliveryNumber the delivery number
   */
  @Timed(name = "HKW-Divert-Upload", level1 = "uwms-receiving", level2 = "HKW-Divert-Upload")
  @TimeTracing(
      component = AppComponent.ENDGAME,
      executionFlow = "HKW-Divert-Upload",
      type = Type.MESSAGE,
      externalCall = true)
  public void send(EndGameSlottingData endgameSlottingData, Long deliveryNumber) {
    endgameSlottingData = removeSpecialChar(endgameSlottingData);
    LOGGER.info(
        "Sending the slotting diverts for [deliveryNumber={}] to hawkeye [payload={}]",
        deliveryNumber,
        ReceivingUtils.stringfyJson(endgameSlottingData));
    try {
      Integer facilityNum = TenantContext.getFacilityNum();
      String payload = gson.toJson(endgameSlottingData);
      String key = String.valueOf(deliveryNumber);
      if (configUtils.isDivertInfoOutboxKafkaPublishEnabled(facilityNum)) {
        String facilityCountryCode = TenantContext.getFacilityCountryCode();
        String messageId = UUID.randomUUID().toString();
        Map<String, Object> headers =
            EndGameUtils.getHawkeyeHeaderMap(
                hawkeyeDivertsTopic, key, facilityNum, facilityCountryCode, messageId);
        outboxPublisherService.publishToKafka(
            payload, headers, hawkeyeDivertsTopic, facilityNum, facilityCountryCode, key);
      } else {
        Message<String> message =
            EndGameUtils.setDefaultHawkeyeHeaders(
                payload,
                StandardExpressionEvaluator.EVALUATOR.evaluate(
                    hawkeyeDivertsTopic, new TenantPlaceholder(facilityNum)),
                DEFAULT_USER,
                key);

        securePublisher.send(message);
      }
    } catch (Exception exception) {
      LOGGER.error(
          "Unable to send slotting info for [deliveryNumber={}] to Hawkeye [error={}]",
          deliveryNumber,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(
              ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
              EndgameConstants.DIVERT_UPLOAD_FLOW));
    }
    LOGGER.info("Slotting diverts sent for [deliveryNumber={}]", deliveryNumber);
  }

  private EndGameSlottingData removeSpecialChar(EndGameSlottingData endgameSlottingData) {
    List<DivertDestinationToHawkeye> divertDestinationToHawkeyes = new ArrayList<>();
    for (DivertDestinationToHawkeye divertDestinationToHawkeye :
        endgameSlottingData.getDestinations()) {
      List<String> possibleUPCs = new ArrayList<>();
      for (String possibleUPC :
          ReceivingUtils.checkDefaultValue(divertDestinationToHawkeye.getPossibleUPCs())) {
        possibleUPCs.add(possibleUPC.replace(EndgameConstants.AT, ""));
      }
      divertDestinationToHawkeye.setPossibleUPCs(possibleUPCs);
      divertDestinationToHawkeyes.add(divertDestinationToHawkeye);
    }
    endgameSlottingData.setDestinations(divertDestinationToHawkeyes);
    return endgameSlottingData;
  }

  /**
   * Update Hawkeye to change divert for a UPC to the new destination.
   *
   * @param endGameSlottingData the end game slotting data
   */
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.MESSAGE,
      executionFlow = "HKW-Divert-Update",
      externalCall = true)
  private void updateToHawkeye(EndGameSlottingData endGameSlottingData) {
    LOGGER.info(
        "Sending the slotting divert update to hawkeye [payload={}]",
        ReceivingUtils.stringfyJson(endGameSlottingData));

    endGameSlottingData = removeSpecialChar(endGameSlottingData);
    Message<String> message =
        EndGameUtils.setDefaultHawkeyeHeaders(
            gson.toJson(endGameSlottingData),
            StandardExpressionEvaluator.EVALUATOR.evaluate(
                hawkeyeDivertUpdateTopic, new TenantPlaceholder(TenantContext.getFacilityNum())),
            DEFAULT_USER,
            getUPCAsKey(endGameSlottingData));

    try {
      securePublisher.send(message);
    } catch (Exception exception) {
      LOGGER.error(
          "Unable to send divert update to hawkeye [error={}]",
          ExceptionUtils.getStackTrace(exception));

      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(
              ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG,
              EndgameConstants.DIVERT_UPLOAD_FLOW));
    }
    LOGGER.info("Slotting divert update sent");
  }

  private String getUPCAsKey(EndGameSlottingData endGameSlottingData) {
    if (CollectionUtils.isEmpty(endGameSlottingData.getDestinations())) {
      return TenantContext.getCorrelationId();
    }
    return endGameSlottingData.getDestinations().stream().findFirst().get().getCaseUPC();
  }

  /**
   * Change divert destination for a upc. 1. Check whether UPC exists 2. Update new divert
   * destination 3. Send update to hawkeye
   *
   * @param request divert change request
   */
  @Transactional
  @InjectTenantFilter
  public void changeDivertDestination(DivertPriorityChangeRequest request) {
    LOGGER.info("Got slotting priority change [request={}]", ReceivingUtils.stringfyJson(request));
    List<SlottingDestination> slottingDestinationList =
        slottingDestinationRepository.findByCaseUPC(request.getCaseUPC());
    if (CollectionUtils.isEmpty(slottingDestinationList)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.UPC_NOT_FOUND,
          String.format(EndgameConstants.UPC_NOT_FOUND_ERROR_MSG, request.getCaseUPC()));
    }
    Boolean isMultipleSellerId =
        EndGameUtils.isMultipleSellerIdInSlottingDestinations(slottingDestinationList);
    /*
     If a particular UPC is attached to multiple seller id then
     don't update new destination to hawkeye for that UPC
    */
    if (isMultipleSellerId) {
      LOGGER.warn(
          EndgameConstants.MULTIPLE_SELLER_ID_IN_UPC_DESTINATION_TABLE, request.getCaseUPC());
      return;
    }
    SlottingDestination slottingDestination = slottingDestinationList.get(0);
    slottingDestination.setDestination(request.getNewDivert());
    slottingDestinationRepository.save(slottingDestination);
    EndGameSlottingData endGameSlottingData =
        SlottingUtils.populateEndgameSlottingData(slottingDestination, null);
    updateToHawkeye(endGameSlottingData);
    LOGGER.info(
        "Successfully updated to hawkeye for slotting priority change [caseUPC={}]",
        request.getCaseUPC());
  }

  /**
   * This method is responsible for calling item mdm service to get item details, then calling GDM
   * service to get all the Po/PoLine details for that item, then calling slotting service to get
   * the divert destination for that item number, if divert destination is modified after capturing
   * Expiry date or Audit changes audit-flag for an item, then it will update to hawkeye with new
   * divert destination for that item.
   *
   * @param updateAttributesData
   */
  @Transactional
  @InjectTenantFilter
  public void updateDivertForItemAndDelivery(UpdateAttributesData updateAttributesData) {
    LOGGER.info(
        "Update Request for an item in a [delivery={}]",
        ReceivingUtils.stringfyJson(updateAttributesData));
    SearchCriteria searchCriteria = updateAttributesData.getSearchCriteria();
    UpdateAttributes updateAttributes = updateAttributesData.getUpdateAttributes();

    Long itemNumber = Long.valueOf(searchCriteria.getItemNumber());
    String itemUPC = searchCriteria.getItemUPC();
    String caseUPC = searchCriteria.getCaseUPC();
    String baseDivisionCode = searchCriteria.getBaseDivisionCode();
    long deliveryNumber = searchCriteria.getDeliveryNumber();

    Map<String, Object> itemDetails =
        getItemDetailsFromItemMdmByItemNumber(itemNumber, baseDivisionCode);
    Map<String, Map<String, Object>> processedItemMap = new HashMap<>();
    processedItemMap.put(searchCriteria.getItemNumber(), itemDetails);
    Set<Long> itemSet = new HashSet<>();
    itemSet.add(itemNumber);

    if (ObjectUtils.isEmpty(itemUPC)) {
      itemUPC = String.valueOf(itemDetails.get(EndgameConstants.ITEM_MDM_ITEM_UPC));
    }

    if (ObjectUtils.isEmpty(caseUPC)) {
      caseUPC = String.valueOf(itemDetails.get(EndgameConstants.ITEM_MDM_CASE_UPC));
    }

    List<PurchaseOrder> purchaseOrderList = getDeliveryDocument(deliveryNumber, caseUPC);
    EndGameUtils.enrichDefaultSellerIdInPurchaseOrders(
        purchaseOrderList,
        endgameManagedConfig.getWalmartDefaultSellerId(),
        endgameManagedConfig.getSamsDefaultSellerId());
    Boolean isMultipleSellerId = EndGameUtils.isMultipleSellerIdInPurchaseOrders(purchaseOrderList);
    updateOpenQuantityInPurchaseOrders(purchaseOrderList, deliveryNumber);

    Map<String, DivertRequestItem> divertRequestItemMap =
        SlottingUtils.generateUPCDivertRequestMap(
            purchaseOrderList,
            processedItemMap,
            itemSet,
            endgameManagedConfig.getIsNewItemPath(),
            endgameManagedConfig.getAssortmentPath(),
            Collections.singletonMap(itemNumber, updateAttributes));

    SlottingDivertRequest slottingDivertRequest = new SlottingDivertRequest();
    slottingDivertRequest.setMessageId(TenantContext.getCorrelationId());
    slottingDivertRequest.setDivertRequestItems(new ArrayList<>(divertRequestItemMap.values()));

    SlottingDivertResponse slottingDivertResponse = getDivertsFromSlotting(slottingDivertRequest);
    EndGameUtils.enrichDefaultSellerIdInSlottingDivertResponse(
        slottingDivertResponse.getDivertLocations(),
        getDefaultSellerId(
            baseDivisionCode,
            endgameManagedConfig.getWalmartDefaultSellerId(),
            endgameManagedConfig.getSamsDefaultSellerId()));

    Optional<DivertDestinationFromSlotting> divertDestinationFromSlottingOptional =
        slottingDivertResponse
            .getDivertLocations()
            .stream()
            .filter(divert -> divert.getItemNbr().equals(itemNumber))
            .findAny();
    if (!divertDestinationFromSlottingOptional.isPresent()) {
      LOGGER.warn(EndgameConstants.SLOTTING_DIVERT_DESTINATION_NOT_FOUND, itemNumber, caseUPC);
      return;
    }
    DivertDestinationFromSlotting divertDestinationFromSlotting =
        divertDestinationFromSlottingOptional.get();
    LOGGER.info(
        EndgameConstants.LOG_SLOTTING_DIVERT_DESTINATION_LOG,
        itemNumber,
        caseUPC,
        slottingDivertResponse);
    DeliveryMetaData deliveryMetaData =
        endGameDeliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(deliveryNumber))
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
                        String.format(
                            EndgameConstants.DELIVERY_METADATA_NOT_FOUND_ERROR_MSG,
                            deliveryNumber)));

    SlottingDestination slottingDestination =
        slottingDestinationRepository
            .findFirstByCaseUPCAndSellerId(caseUPC, divertDestinationFromSlotting.getSellerId())
            .orElse(
                createSlottingDestinationObject(
                    caseUPC, itemUPC, divertDestinationFromSlotting.getSellerId(), itemDetails));
    String previousDivert =
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, String.valueOf(itemNumber), DIVERT_DESTINATION);
    if (ObjectUtils.isEmpty(previousDivert)) {
      previousDivert = slottingDestination.getDestination();
    }

    endGameDeliveryMetaDataService.updateDeliveryMetaDataForItemOverrides(
        deliveryMetaData,
        String.valueOf(itemNumber),
        updateAttributes.getRotateDate(),
        divertDestinationFromSlotting.getDivertLocation());

    if (divertDestinationFromSlotting.getDivertLocation().equalsIgnoreCase(previousDivert)) {
      LOGGER.info(
          EndgameConstants.LOG_SLOTTING_DIVERT_SAME_AS_PREVIOUS_NOT_UPDATING_TO_HAWKEYE, caseUPC);
      return;
    }

    if (isMultipleSellerId) {
      LOGGER.warn(
          EndgameConstants.MULTIPLE_SELLER_ID_IN_PURCHASE_ORDER,
          caseUPC,
          searchCriteria.getDeliveryNumber());
      return;
    }

    EndGameSlottingData endgameSlottingData =
        SlottingUtils.populateEndgameSlottingData(
            slottingDivertResponse.getDivertLocations(),
            deliveryNumber,
            deliveryMetaData.getDoorNumber(),
            divertRequestItemMap);
    LOGGER.info(
        EndgameConstants.LOG_SLOTTING_DIVERT_UPDATE_TO_HAWKEYE,
        caseUPC,
        previousDivert,
        endgameSlottingData);
    send(endgameSlottingData, deliveryNumber);
  }

  /**
   * This method is responsible for calling item mdm service to get item details, then calling
   * slotting service to get the divert destination for that item number, if divert destination is
   * modified after performing FTS, then it will update to hawkeye with new divert destination for
   * that item.
   *
   * @param updateAttributesData
   */
  @Transactional
  @InjectTenantFilter
  public void updateDivertForItem(UpdateAttributesData updateAttributesData) {
    SearchCriteria searchCriteria = updateAttributesData.getSearchCriteria();
    Long itemNumber = Long.valueOf(searchCriteria.getItemNumber());
    String baseDivisionCode = searchCriteria.getBaseDivisionCode();
    String itemUPC = searchCriteria.getItemUPC();
    String caseUPC = searchCriteria.getCaseUPC();
    Map<String, Object> itemDetails =
        getItemDetailsFromItemMdmByItemNumber(itemNumber, baseDivisionCode);

    if (ObjectUtils.isEmpty(itemUPC)) {
      itemUPC = String.valueOf(itemDetails.get(EndgameConstants.ITEM_MDM_ITEM_UPC));
    }

    if (ObjectUtils.isEmpty(caseUPC)) {
      caseUPC = String.valueOf(itemDetails.get(EndgameConstants.ITEM_MDM_CASE_UPC));
    }

    List<SlottingDestination> slottingDestinationList =
        slottingDestinationRepository.findByCaseUPC(caseUPC);
    SlottingDestination slottingDestination;
    if (CollectionUtils.isEmpty(slottingDestinationList)) {
      slottingDestination =
          createSlottingDestinationObject(
              caseUPC,
              itemUPC,
              getDefaultSellerId(
                  baseDivisionCode,
                  endgameManagedConfig.getWalmartDefaultSellerId(),
                  endgameManagedConfig.getSamsDefaultSellerId()),
              itemDetails);
    } else {
      Boolean isMultipleSellerId =
          EndGameUtils.isMultipleSellerIdInSlottingDestinations(slottingDestinationList);
      if (isMultipleSellerId) {
        LOGGER.warn(EndgameConstants.MULTIPLE_SELLER_ID_IN_UPC_DESTINATION_TABLE, caseUPC);
        return;
      } else {
        slottingDestination = slottingDestinationList.get(0);
      }
    }

    DivertRequestItem divertRequestItem =
        DivertRequestItem.builder()
            .itemNbr(itemNumber)
            .itemDetails(itemDetails)
            .caseUPC(caseUPC)
            .itemUPC(itemUPC)
            .baseDivisionCode(baseDivisionCode)
            .sellerId(slottingDestination.getSellerId())
            .isRotateDateCaptured(Boolean.FALSE)
            .isRotateDateExpired(Boolean.FALSE)
            .build();

    SlottingDivertRequest slottingDivertRequest = new SlottingDivertRequest();
    slottingDivertRequest.setMessageId(TenantContext.getCorrelationId());
    slottingDivertRequest.setDivertRequestItems(Arrays.asList(divertRequestItem));

    SlottingDivertResponse slottingDivertResponse = getDivertsFromSlotting(slottingDivertRequest);
    EndGameUtils.enrichDefaultSellerIdInSlottingDivertResponse(
        slottingDivertResponse.getDivertLocations(),
        getDefaultSellerId(
            baseDivisionCode,
            endgameManagedConfig.getWalmartDefaultSellerId(),
            endgameManagedConfig.getSamsDefaultSellerId()));

    List<DivertDestinationFromSlotting> divertDestinationsFromSlotting =
        slottingDivertResponse.getDivertLocations();

    LOGGER.info(
        EndgameConstants.LOG_SLOTTING_DIVERT_DESTINATION_LOG,
        itemNumber,
        caseUPC,
        slottingDivertResponse);

    List<DivertDestinationFromSlotting> divertDestinationsNeedToBeUpdated = new ArrayList<>();
    for (DivertDestinationFromSlotting divertDestinationFromSlotting :
        divertDestinationsFromSlotting) {
      /*
       Update hawkeye if isFTS = false
      */
      Map<String, String> attributesMap = divertDestinationFromSlotting.getAttributes();
      if (!CollectionUtils.isEmpty(attributesMap)
          && !ObjectUtils.isEmpty(attributesMap.get(EndgameConstants.ATTRIBUTES_FTS))
          && !Boolean.valueOf(attributesMap.get(EndgameConstants.ATTRIBUTES_FTS))) {
        divertDestinationsNeedToBeUpdated.add(divertDestinationFromSlotting);
      }

      if (ObjectUtils.isEmpty(slottingDestination.getDestination())
          || !divertDestinationFromSlotting
              .getDivertLocation()
              .equalsIgnoreCase(slottingDestination.getDestination())) {
        divertDestinationsNeedToBeUpdated.add(divertDestinationFromSlotting);
      }
    }

    for (DivertDestinationFromSlotting divertDestinationFromSlotting :
        divertDestinationsNeedToBeUpdated) {
      slottingDestination.setDestination(divertDestinationFromSlotting.getDivertLocation());
      slottingDestinationRepository.save(slottingDestination);

      EndGameSlottingData endGameSlottingData =
          SlottingUtils.populateEndgameSlottingData(
              slottingDestination,
              SlottingUtils.populateAttributes(
                  divertDestinationFromSlotting.getAttributes(), itemDetails));
      LOGGER.info(
          EndgameConstants.LOG_SLOTTING_DIVERT_UPDATE_TO_HAWKEYE,
          caseUPC,
          slottingDestination.getDestination(),
          endGameSlottingData);
      updateToHawkeye(endGameSlottingData);
    }
  }

  private SlottingDestination createSlottingDestinationObject(
      String caseUPC, String itemUPC, String sellerId, Map<String, Object> itemDetails) {
    SlottingDestination slottingDestination = new SlottingDestination();
    slottingDestination.setCaseUPC(caseUPC);
    slottingDestination.setSellerId(sellerId);
    slottingDestination.setPossibleUPCs(
        String.join(
            ",",
            SlottingUtils.getAllPossibleUPC(
                itemDetails, itemUPC, endgameManagedConfig.getAssortmentPath())));
    return slottingDestination;
  }

  private Map<String, Object> getItemDetailsFromItemMdmByItemNumber(
      Long itemNumber, String baseDivCode) {
    Set<Long> itemSet = new HashSet<>();
    itemSet.add(itemNumber);

    LOGGER.info(EndgameConstants.CALLING_ITEM_MDM_SERVICE, itemSet);
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    Map<String, Object> itemResponse =
        itemMDMService.retrieveItemDetails(
            itemSet, httpHeaders, baseDivCode, Boolean.TRUE, Boolean.FALSE);

    List<Map<String, Object>> foundItems =
        (List<Map<String, Object>>) itemResponse.get(ReceivingConstants.ITEM_FOUND_SUPPLY_ITEM);
    return foundItems
        .stream()
        .filter(
            item ->
                Double.valueOf(item.get(EndgameConstants.ITEM_MDM_ITEM_NUMBER).toString())
                        .longValue()
                    == itemNumber.longValue())
        .findFirst()
        .get();
  }

  public List<PurchaseOrder> getDeliveryDocument(Long deliveryNumber, String caseUPC) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    String deliveryDocument;
    try {
      deliveryDocument =
          endGameDeliveryService.findDeliveryDocument(deliveryNumber, caseUPC, httpHeaders);
    } catch (ReceivingException e) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format(
              "Delivery = %s not found while updating to hawkeye on capturing expiry date",
              deliveryNumber),
          e);
    }
    return Arrays.asList(gson.fromJson(deliveryDocument, PurchaseOrder[].class));
  }

  /**
   * Add open quantity for each line in the PO List by (OrderQty - ReceivedQty).
   *
   * @param purchaseOrderList
   */
  private void updateOpenQuantityInPurchaseOrders(
      List<PurchaseOrder> purchaseOrderList, Long deliveryNumber) {
    List<ReceiptSummaryQtyByPoAndPoLineResponse> qtyByPoAndPoLineList = new ArrayList<>();
    Lists.partition(
            purchaseOrderList,
            configUtils.getPurchaseOrderPartitionSize(PURCHASE_ORDER_PARTITION_SIZE_KEY))
        .stream()
        .map(
            purchaseOrders ->
                receiptService.getReceiptSummaryQtyByPOandPOLineResponse(
                    purchaseOrders, deliveryNumber))
        .forEach(qtyByPoAndPoLineList::addAll);

    Map<String, Long> qtyByPoAndPoLineMap = new HashMap<>();

    for (ReceiptSummaryQtyByPoAndPoLineResponse qtyByPoAndPoLine : qtyByPoAndPoLineList) {
      String key =
          qtyByPoAndPoLine.getPurchaseReferenceNumber()
              + EndgameConstants.DELIM_DASH
              + qtyByPoAndPoLine.getPurchaseReferenceLineNumber();
      qtyByPoAndPoLineMap.put(key, qtyByPoAndPoLine.getReceivedQty());
    }

    purchaseOrderList.forEach(
        purchaseOrder ->
            purchaseOrder
                .getLines()
                .forEach(
                    line -> {
                      String key =
                          purchaseOrder.getPoNumber()
                              + EndgameConstants.DELIM_DASH
                              + line.getPoLineNumber();
                      if (Objects.nonNull(qtyByPoAndPoLineMap.get(key))) {
                        line.setOpenQuantity(
                            line.getOrdered().getQuantity()
                                + line.getOvgThresholdLimit().getQuantity()
                                - qtyByPoAndPoLineMap.get(key).intValue());
                      } else {
                        line.setOpenQuantity(line.getOrdered().getQuantity());
                      }
                    }));
  }

  @Transactional
  @InjectTenantFilter
  public SlottingDestination updateDestination(
      String caseUPC, SlottingDestination requestedSlottingDestination) {
    final SlottingDestination slottingDestination =
        slottingDestinationRepository
            .findFirstByCaseUPCAndSellerId(caseUPC, requestedSlottingDestination.getSellerId())
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.UPC_NOT_FOUND,
                        String.format(EndgameConstants.UPC_NOT_FOUND_ERROR_MSG, caseUPC)));
    slottingDestination.setDestination(requestedSlottingDestination.getDestination());
    return slottingDestinationRepository.save(slottingDestination);
  }

  @Transactional
  @InjectTenantFilter
  public List<SlottingDestination> findByCaseUPC(String caseUPC) {
    return slottingDestinationRepository.findByCaseUPC(caseUPC);
  }

  @Transactional
  @InjectTenantFilter
  public List<SlottingDestination> findByPossibleUPCsContains(String upc) {
    return slottingDestinationRepository.findByPossibleUPCsContains(upc);
  }

  public PalletSlotResponse multipleSlotsFromSlotting(
      PalletSlotRequest slotRequest, Boolean isOverboxingPallet) {
    PalletSlotResponse palletSlotResponse = null;
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    StringBuilder slottingUrlBuilder =
        new StringBuilder(appConfig.getSlottingBaseUrl())
            .append(ReceivingConstants.SLOTTING_GET_SLOT_URL);
    httpHeaders.set(
        ReceivingConstants.HEADER_FEATURE_TYPE, ReceivingConstants.FEATURE_PALLET_RECEIVING);
    httpHeaders.set(
        ReceivingConstants.ACCEPT_PARTIAL_RESERVATION, ReceivingConstants.IS_PARTIAL_RESERVATION);
    httpHeaders.set(ReceivingConstants.OVERBOXING_REQUIRED, String.valueOf(isOverboxingPallet));
    try {
      LOGGER.info(
          "Slotting request for [slot number:{}]", ReceivingUtils.stringfyJson(slotRequest));
      ResponseEntity<PalletSlotResponse> slotResponseResponseEntity =
          restConnector.post(
              slottingUrlBuilder.toString(),
              gsonUTCDateAdapter.toJson(slotRequest),
              httpHeaders,
              PalletSlotResponse.class);
      palletSlotResponse = slotResponseResponseEntity.getBody();
      LOGGER.info(
          ReceivingConstants.RESTUTILS_INFO_MESSAGE,
          slottingUrlBuilder,
          ReceivingUtils.stringfyJson(slotRequest),
          ReceivingUtils.stringfyJson(palletSlotResponse));
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          slottingUrlBuilder,
          slotRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingForwardedException(e, EndgameConstants.NO_SLOTS_AVAILABLE);
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.SLOTTING_NOT_ACCESSIBLE,
          String.format(EndgameConstants.SLOTTING_RESOURCE_RESPONSE_ERROR_MSG, e.getMessage()));
    }
    return palletSlotResponse;
  }

  public void cancelPalletMoves(List<Container> containerList) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    StringBuilder slottingUrlBuilder =
        new StringBuilder(appConfig.getSlottingBaseUrl())
            .append(ReceivingConstants.SLOTTING_CANCEL_PALLET_MOVE_URL);
    PalletMoveRequest request = prepareRequest(containerList);
    try {
      LOGGER.info(
          "[Slotting request for cancelling pallet moves for containers={}]",
          stringfyJson(request.getContainerIds()));
      ResponseEntity slotResponse =
          restConnector.post(
              slottingUrlBuilder.toString(),
              gsonUTCDateAdapter.toJson(request),
              httpHeaders,
              String.class);
      LOGGER.info(
          "[Slotting request for cancelling moves completed with status code= {}]",
          slotResponse.getStatusCode());
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          slottingUrlBuilder,
          request,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.SLOTTING_INTERNAL_ERROR,
          String.format(
              EndgameConstants.SLOTTING_BAD_RESPONSE_ERROR_MSG,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.SLOTTING_NOT_ACCESSIBLE,
          String.format(EndgameConstants.SLOTTING_RESOURCE_RESPONSE_ERROR_MSG, e.getMessage()));
    }
  }

  private PalletMoveRequest prepareRequest(List<Container> containerList) {
    List<String> containerIds =
        containerList
            .stream()
            .map(Container::getTrackingId)
            .distinct()
            .collect(Collectors.toList());
    return PalletMoveRequest.builder()
        .userId(retrieveUserId())
        .containerIds(containerIds)
        .status(STATUS_CANCELLED)
        .moveType(PUTAWAY)
        .nextMove(false)
        .isSkipped(false)
        .build();
  }
}
