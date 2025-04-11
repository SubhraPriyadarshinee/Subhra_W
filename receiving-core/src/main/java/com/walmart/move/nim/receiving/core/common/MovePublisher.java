package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.constants.MoveEvent.CANCEL;
import static com.walmart.move.nim.receiving.utils.constants.MoveEvent.CREATE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_RECEIVING_INSTRUCTS_PUT_AWAY_MOVE_TO_MM;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.FeatureFlag;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaMoveMessagePublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.move.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.MoveEvent;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class MovePublisher {
  private static final Logger log = LoggerFactory.getLogger(MovePublisher.class);
  @Autowired private JmsPublisher jmsPublisher;
  @ManagedConfiguration private AppConfig appConfig;
  @ManagedConfiguration private MaasTopics maasTopics;
  private Gson gson;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  public MovePublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  /**
   * This method is responsible for publishing move information
   *
   * @param moveQty
   * @param fromLocation
   * @param httpHeaders
   * @param moveData
   * @param moveEvent
   */
  // TODO Remove @FeatureFlag after implementing generic processor based strategy
  @FeatureFlag(value = ReceivingConstants.MOVE_PUBLISH_ENABLED)
  public void publishMove(
      int moveQty,
      String fromLocation,
      HttpHeaders httpHeaders,
      LinkedTreeMap<String, Object> moveData,
      String moveEvent) {
    // Move Type.
    Map<String, Object> moveInfo =
        constructMovePayload(moveQty, fromLocation, httpHeaders, moveData, moveEvent);

    publishMoveEvent(httpHeaders, moveInfo);
  }

  /**
   * This method is responsible for publishing move information
   *
   * @param moveQty
   * @param fromLocation
   * @param httpHeaders
   * @param moveData
   * @param moveEvent
   * @param destination
   */
  // TODO Remove @FeatureFlag after implementing generic processor based strategy
  @FeatureFlag(value = ReceivingConstants.MOVE_PUBLISH_ENABLED)
  public void publishMove(
      int moveQty,
      String fromLocation,
      HttpHeaders httpHeaders,
      LinkedTreeMap<String, Object> moveData,
      String moveEvent,
      Integer destination) {
    // Move Type.
    Map<String, Object> moveInfo =
        constructMovePayload(moveQty, fromLocation, httpHeaders, moveData, moveEvent);

    moveInfo.put(ReceivingConstants.MOVE_DEST_BU_NUMBER, destination);

    publishMoveEvent(httpHeaders, moveInfo);
  }

  public void publishMove(
      int moveQty,
      String fromLocation,
      String toLocation,
      String trackingId,
      HttpHeaders httpHeaders) {
    MoveInfo moveInfo =
        MoveInfo.builder()
            .containerTag(trackingId)
            .correlationID(httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY))
            .fromLocation(fromLocation)
            .moveEvent(MoveEvent.CREATE.toString())
            .moveQty(moveQty)
            .moveQtyUOM(appConfig.getMoveQtyUom())
            .moveType(
                MoveType.builder()
                    .code(appConfig.getMoveTypeCode())
                    .desc(appConfig.getMovetypeDesc())
                    .build())
            .priority(appConfig.getMovePriority())
            // TODO: Need to check this number, apparently comes from OF in other markets
            .sequenceNbr(1)
            .toLocation(toLocation)
            .build();

    ReceivingJMSEvent receivingJMSEvent =
        new ReceivingJMSEvent(
            ReceivingUtils.getForwardablHeader(httpHeaders), gson.toJson(moveInfo));

    log.info("Publishing move {}", moveInfo);
    publishMoveMessage(receivingJMSEvent);
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-147. Multi Pallet Receiving - Send data to MM
   *
   * @param moveInfoList list of moveInfo objects that are to be published to queue
   * @param httpHeaders headers for Receiving JMS event
   */
  public void publishMove(List<MoveInfo> moveInfoList, HttpHeaders httpHeaders) {
    Map<String, Object> jmsHeaders = ReceivingUtils.getForwardablHeader(httpHeaders);
    jmsHeaders.put(
        ReceivingConstants.MOVE_TYPE, httpHeaders.getFirst(ReceivingConstants.MOVE_TYPE));
    ReceivingJMSEvent receivingJMSEvent =
        new ReceivingJMSEvent(jmsHeaders, gson.toJson(moveInfoList));
    log.info("Publishing move {}", moveInfoList);
    publishMoveMessage(receivingJMSEvent);
  }

  /** @param receivingJMSEvent */
  private void publishMoveMessage(ReceivingJMSEvent receivingJMSEvent) {
    String moveTopic =
        StringUtils.isNotBlank(maasTopics.getPubMoveTopic())
            ? maasTopics.getPubMoveTopic()
            : ReceivingConstants.PUB_MOVE_TOPIC;

    jmsPublisher.publish(moveTopic, receivingJMSEvent, Boolean.TRUE);
  }

  /**
   * @param moveContainers
   * @param moveTreeMap
   * @param httpHeaders
   *     <p>This method publishes multiple containers to MM with single move - Split pallet
   *     Receiving
   */
  public void publishMove(
      List<MoveContainer> moveContainers,
      LinkedTreeMap<String, Object> moveTreeMap,
      HttpHeaders httpHeaders) {
    MoveInfo moveInfo =
        MoveInfo.builder()
            .correlationID(httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY))
            .fromLocation(String.valueOf(moveTreeMap.get(ReceivingConstants.MOVE_FROM_LOCATION)))
            .moveEvent(MoveEvent.CREATE.getMoveEvent())
            .moveQtyUOM(appConfig.getMoveQtyUom())
            .priority(appConfig.getMovePriority())
            .moveType((MoveType) moveTreeMap.get(ReceivingConstants.MOVE_TYPE))
            .toLocation(String.valueOf(moveTreeMap.get(ReceivingConstants.MOVE_TO_LOCATION)))
            .containerList(moveContainers)
            .build();
    ReceivingJMSEvent receivingJMSEvent =
        new ReceivingJMSEvent(
            ReceivingUtils.getForwardablHeader(httpHeaders), gson.toJson(moveInfo));
    log.info("Publishing move for multiple containers {}", moveInfo);
    publishMoveMessage(receivingJMSEvent);
  }

  public void publishMove(
      int moveQty,
      String fromLocation,
      LinkedTreeMap<String, Object> moveTreeMap,
      HttpHeaders httpHeaders) {
    MoveInfo moveInfo =
        MoveInfo.builder()
            .containerTag(String.valueOf(moveTreeMap.get(ReceivingConstants.MOVE_CONTAINER_TAG)))
            .correlationID(httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY))
            .fromLocation(fromLocation)
            .moveEvent(MoveEvent.CREATE.getMoveEvent())
            .moveQty(moveQty)
            .moveQtyUOM(appConfig.getMoveQtyUom())
            .moveType((MoveType) moveTreeMap.get(ReceivingConstants.MOVE_TYPE))
            .priority(appConfig.getMovePriority())
            .toLocation(String.valueOf(moveTreeMap.get(ReceivingConstants.MOVE_TO_LOCATION)))
            .build();

    ReceivingJMSEvent receivingJMSEvent =
        new ReceivingJMSEvent(
            ReceivingUtils.getForwardablHeader(httpHeaders), gson.toJson(moveInfo));

    log.info("Publishing move {}", moveInfo);
    publishMoveMessage(receivingJMSEvent);
  }

  private void publishMoveEvent(HttpHeaders httpHeaders, Map<String, Object> moveInfo) {
    MessagePublisher messagePublisher =
        tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.MOVE_EVENT_HANDLER,
            MessagePublisher.class);
    MoveData moveData = gson.fromJson(gson.toJson(moveInfo), MoveData.class);
    messagePublisher.publish(moveData, ReceivingUtils.getForwardablHeader(httpHeaders));
  }

  private Map<String, Object> constructMovePayload(
      int moveQty,
      String fromLocation,
      HttpHeaders httpHeaders,
      LinkedTreeMap<String, Object> moveData,
      String moveEvent) {
    HashMap<String, Object> moveType = new HashMap<>();
    // Generating move info.
    Map<String, Object> moveInfo = new HashMap<>();
    moveInfo.put(ReceivingConstants.MOVE_EVENT, moveEvent);
    moveInfo.put(
        ReceivingConstants.MOVE_CONTAINER_TAG, moveData.get(ReceivingConstants.MOVE_CONTAINER_TAG));
    moveInfo.put(ReceivingConstants.MOVE_FROM_LOCATION, fromLocation);
    moveInfo.put(
        ReceivingConstants.MOVE_CORRELATION_ID,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    moveInfo.put(ReceivingConstants.MOVE_QTY, moveQty);
    moveInfo.put(
        ReceivingConstants.MOVE_TO_LOCATION, moveData.get(ReceivingConstants.MOVE_TO_LOCATION));
    moveInfo.put(ReceivingConstants.MOVE_QTY_UOM, appConfig.getMoveQtyUom());
    moveInfo.put(
        ReceivingConstants.MOVE_SEQUENCE_NBR, moveData.get(ReceivingConstants.MOVE_SEQUENCE_NBR));

    Integer movePriorityCode =
        Objects.nonNull(moveData.get(ReceivingConstants.MOVE_PRIORITY))
            ? (Integer) moveData.get(ReceivingConstants.MOVE_PRIORITY)
            : appConfig.getMovePriority();
    moveInfo.put(ReceivingConstants.MOVE_PRIORITY, movePriorityCode);

    Integer moveTypeCode =
        Objects.nonNull(moveData.get(ReceivingConstants.MOVE_TYPE_CODE))
            ? (Integer) moveData.get(ReceivingConstants.MOVE_TYPE_CODE)
            : appConfig.getMoveTypeCode();
    moveType.put(ReceivingConstants.MOVE_TYPE_CODE, moveTypeCode);

    String moveTypeDesc =
        Objects.nonNull(moveData.get(ReceivingConstants.MOVE_TYPE_DESC))
            ? (String) moveData.get(ReceivingConstants.MOVE_TYPE_DESC)
            : appConfig.getMovetypeDesc();
    moveType.put(ReceivingConstants.MOVE_TYPE_DESC, moveTypeDesc);
    moveInfo.put(ReceivingConstants.MOVE_TYPE, moveType);

    return moveInfo;
  }

  /**
   * V2 version of publish move using ccm moveType configuration
   *
   * @param container
   * @param toLocation
   * @param httpHeaders
   */
  public void publishMoveV2(Container container, String toLocation, HttpHeaders httpHeaders) {
    String movePriority =
        tenantSpecificConfigReader.getCcmValue(
            TenantContext.getFacilityNum(), ReceivingConstants.MOVE_TYPE_PRIORITY, "40");
    String moveCode =
        tenantSpecificConfigReader.getCcmValue(
            TenantContext.getFacilityNum(), ReceivingConstants.MOVE_CODE, "5");
    String moveDesc =
        tenantSpecificConfigReader.getCcmValue(
            TenantContext.getFacilityNum(), ReceivingConstants.MOVE_DESC, "PUTAWAY");
    String moveQtyUom =
        tenantSpecificConfigReader.getCcmValue(
            TenantContext.getFacilityNum(), ReceivingConstants.MOVE_QTY_UOM, "EA");
    MessagePublisher messagePublisher =
        tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.MOVE_EVENT_HANDLER,
            ReceivingConstants.JMS_MOVE_PUBLISHER,
            MessagePublisher.class);

    MoveInfo moveInfo =
        MoveInfo.builder()
            .containerTag(container.getTrackingId())
            .correlationID(httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY))
            .fromLocation(container.getLocation())
            .moveEvent(CREATE.getMoveEvent())
            .moveQty(InstructionUtils.getMoveQuantity(container))
            .moveQtyUOM(moveQtyUom)
            .moveType(MoveType.builder().code(Integer.parseInt(moveCode)).desc(moveDesc).build())
            .priority(Integer.parseInt(movePriority))
            .sequenceNbr(1)
            .toLocation(toLocation)
            .vnpkQty(container.getContainerItems().get(0).getVnpkQty())
            .whpkQty(container.getContainerItems().get(0).getWhpkQty())
            .build();

    if (messagePublisher instanceof KafkaMoveMessagePublisher) {
      messagePublisher.publish(moveInfo, ReceivingUtils.getForwardablHeader(httpHeaders));
    } else {
      ReceivingJMSEvent receivingJMSEvent =
          new ReceivingJMSEvent(
              ReceivingUtils.getForwardablHeader(httpHeaders), gson.toJson(moveInfo));
      publishMoveMessage(receivingJMSEvent);
    }

    log.info("Published the V2 version of create move {}", moveInfo);
  }

  /**
   * @param trackingId
   * @param httpHeaders
   */
  public void publishCancelMove(String trackingId, HttpHeaders httpHeaders) {

    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        IS_RECEIVING_INSTRUCTS_PUT_AWAY_MOVE_TO_MM,
        true)) {
      log.info("Instructions to Moves for the putaway cancel is blocked from receiving");
      return;
    }

    String moveCode =
        tenantSpecificConfigReader.getCcmValue(
            TenantContext.getFacilityNum(), ReceivingConstants.MOVE_CODE, "5");
    String moveDesc =
        tenantSpecificConfigReader.getCcmValue(
            TenantContext.getFacilityNum(), ReceivingConstants.MOVE_DESC, "PUTAWAY");
    MessagePublisher messagePublisher =
        tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.MOVE_EVENT_HANDLER,
            ReceivingConstants.JMS_MOVE_PUBLISHER,
            MessagePublisher.class);

    CancelMove cancelMove =
        CancelMove.builder()
            .containerTag(trackingId)
            .moveEvent(CANCEL.getMoveEvent())
            .moveType(MoveType.builder().code(Integer.parseInt(moveCode)).desc(moveDesc).build())
            .build();

    if (messagePublisher instanceof KafkaMoveMessagePublisher) {
      ((KafkaMoveMessagePublisher) messagePublisher)
          .publishCancelMove(cancelMove, ReceivingUtils.getForwardablHeader(httpHeaders));
    } else {
      ReceivingJMSEvent receivingJMSEvent =
          new ReceivingJMSEvent(
              ReceivingUtils.getForwardablHeader(httpHeaders), gson.toJson(cancelMove));

      publishMoveMessage(receivingJMSEvent);
    }
    log.info("Published the cancel move {}", cancelMove);
  }
}
