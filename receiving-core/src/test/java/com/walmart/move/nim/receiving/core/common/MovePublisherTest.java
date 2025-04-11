package com.walmart.move.nim.receiving.core.common;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.JMSMovePublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaMoveMessagePublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.model.move.MoveContainer;
import com.walmart.move.nim.receiving.core.model.move.MoveInfo;
import com.walmart.move.nim.receiving.core.model.move.MoveType;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.MoveEvent;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class MovePublisherTest {

  @Mock private AppConfig appConfig;
  @Mock private MaasTopics maasTopics;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private JMSMovePublisher jmsMovePublisher;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private KafkaMoveMessagePublisher kafkaMoveMessagePublisher;
  @InjectMocks private MovePublisher movePublisher;

  private Gson gson;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private HttpHeaders headers;

  private String fromLocation = "100";
  private String toLocation = "200";
  private String trackingId = "lpn123";
  private String moveEvent = MoveEvent.CREATE.getMoveEvent();
  private int moveQty = 100;
  private Integer destinationBuNumber = 6020;
  @Mock private MessagePublisher messagePublisher;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    gson = new Gson();
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);

    ReflectionTestUtils.setField(movePublisher, "gson", gson);
  }

  @BeforeMethod()
  public void before() {
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.MOVE_EVENT_HANDLER), any()))
        .thenReturn(jmsMovePublisher);
    doNothing().when(jmsMovePublisher).publish(any(), any());
  }

  @AfterMethod
  public void shutdownMocks() {
    reset(appConfig, jmsPublisher, jmsMovePublisher, maasTopics, configUtils);
  }

  @Test
  public void testPublishMoveIsSuccessWithOverriddenMethod1() {
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(appConfig.getMoveQtyUom()).thenReturn("PF");

    movePublisher.publishMove(moveQty, fromLocation, headers, getMoveInfo(), moveEvent);

    verify(appConfig, times(1)).getMoveTypeCode();
    verify(appConfig, times(1)).getMovetypeDesc();
    verify(appConfig, times(1)).getMovePriority();
    verify(appConfig, times(1)).getMoveQtyUom();
    verify(jmsMovePublisher, times(1)).publish(any(), any());
  }

  @Test
  public void testPublishMoveIsSuccessWithOverriddenMethod2() {
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMovePriority()).thenReturn(50);
    when(appConfig.getMoveQtyUom()).thenReturn("PF");

    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    movePublisher.publishMove(moveQty, fromLocation, toLocation, trackingId, headers);

    verify(appConfig, times(1)).getMoveTypeCode();
    verify(appConfig, times(1)).getMovetypeDesc();
    verify(appConfig, times(1)).getMovePriority();
    verify(appConfig, times(1)).getMoveQtyUom();
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishMoveIsSuccessWithOverriddenMethod3() {
    MoveInfo moveInfo = new MoveInfo();
    moveInfo.setMoveQty(moveQty);
    moveInfo.setFromLocation(fromLocation);
    moveInfo.setToLocation(toLocation);
    moveInfo.setMoveQtyUOM("PF");

    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    movePublisher.publishMove(Collections.singletonList(moveInfo), headers);

    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishMoveIsSuccessWithOverriddenMethod4() {
    LinkedTreeMap<String, Object> moveInfo = getMoveInfo();
    MoveType moveType = MoveType.builder().code(5).desc("Putaway Move").build();
    moveInfo.put(ReceivingConstants.MOVE_TYPE, moveType);

    when(appConfig.getMovePriority()).thenReturn(50);
    when(appConfig.getMoveQtyUom()).thenReturn("PF");

    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());

    movePublisher.publishMove(moveQty, fromLocation, getMoveInfo(), headers);

    verify(appConfig, times(1)).getMovePriority();
    verify(appConfig, times(1)).getMoveQtyUom();
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishDockTagMove() {
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(appConfig.getMoveTypeCode()).thenReturn(2);
    when(appConfig.getMovetypeDesc()).thenReturn("Haul Move");
    when(appConfig.getMovePriority()).thenReturn(50);

    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    movePublisher.publishMove(1, fromLocation, headers, getDockTagMoveInfo(), moveEvent);

    verify(appConfig, times(0)).getMovePriority();
    verify(appConfig, times(0)).getMovetypeDesc();
    verify(appConfig, times(0)).getMoveTypeCode();
    verify(appConfig, times(1)).getMoveQtyUom();
    verify(jmsMovePublisher, times(1)).publish(any(), any());
  }

  @Test
  public void testPublishMoveIsSuccessWithOverriddenMethod5() {
    LinkedTreeMap<String, Object> moveInfo = getMoveInfo();
    MoveType moveType = MoveType.builder().code(5).desc("Putaway Move").build();
    moveInfo.put(ReceivingConstants.MOVE_TYPE, moveType);

    when(appConfig.getMovePriority()).thenReturn(50);
    when(appConfig.getMoveQtyUom()).thenReturn("PF");

    movePublisher.publishMove(
        moveQty, fromLocation, headers, getMoveInfo(), moveEvent, destinationBuNumber);

    verify(appConfig, times(1)).getMovePriority();
    verify(appConfig, times(1)).getMoveQtyUom();
    verify(jmsMovePublisher, times(1)).publish(any(), any());
  }

  @Test
  public void testPublishMoveWithMultipleContainers_defaultMoveTopic() {
    String slot = "G0324";
    List<MoveContainer> moveContainerList = new ArrayList<>();
    moveContainerList.add(MoveContainer.builder().trackingId("a32343434324344").moveQty(4).build());
    moveContainerList.add(MoveContainer.builder().trackingId("a32343434324345").moveQty(5).build());
    moveContainerList.add(MoveContainer.builder().trackingId("a32343434324346").moveQty(6).build());
    when(appConfig.getMovePriority()).thenReturn(50);
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_FROM_LOCATION, fromLocation);
    moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, toLocation);
    MoveType moveType = MoveType.builder().code(5).desc("PutAway Move").build();
    moveTreeMap.put(ReceivingConstants.MOVE_TYPE, moveType);
    doNothing()
        .when(jmsPublisher)
        .publish(eq(ReceivingConstants.PUB_MOVE_TOPIC), any(ReceivingJMSEvent.class), anyBoolean());

    movePublisher.publishMove(moveContainerList, moveTreeMap, headers);

    verify(appConfig, times(1)).getMovePriority();
    verify(appConfig, times(1)).getMoveQtyUom();
    verify(jmsPublisher, times(1))
        .publish(eq(ReceivingConstants.PUB_MOVE_TOPIC), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishMoveWithMultipleContainers_OverrideMoveTopicFromCCM() {
    String slot = "G0324";
    List<MoveContainer> moveContainerList = new ArrayList<>();
    moveContainerList.add(MoveContainer.builder().trackingId("a32343434324344").moveQty(4).build());
    moveContainerList.add(MoveContainer.builder().trackingId("a32343434324345").moveQty(5).build());
    moveContainerList.add(MoveContainer.builder().trackingId("a32343434324346").moveQty(6).build());
    when(appConfig.getMovePriority()).thenReturn(50);
    when(appConfig.getMoveQtyUom()).thenReturn("PF");
    when(maasTopics.getPubMoveTopic()).thenReturn("TOPIC/WMSMM/AMBIENT/MOVE");
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_FROM_LOCATION, fromLocation);
    moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, toLocation);
    MoveType moveType = MoveType.builder().code(5).desc("PutAway Move").build();
    moveTreeMap.put(ReceivingConstants.MOVE_TYPE, moveType);
    doNothing()
        .when(jmsPublisher)
        .publish(eq("TOPIC/WMSMM/AMBIENT/MOVE"), any(ReceivingJMSEvent.class), anyBoolean());

    movePublisher.publishMove(moveContainerList, moveTreeMap, headers);

    verify(appConfig, times(1)).getMovePriority();
    verify(appConfig, times(1)).getMoveQtyUom();
    verify(jmsPublisher, times(1))
        .publish(eq("TOPIC/WMSMM/AMBIENT/MOVE"), any(ReceivingJMSEvent.class), anyBoolean());
  }

  private LinkedTreeMap<String, Object> getMoveInfo() {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, toLocation);
    moveTreeMap.put(ReceivingConstants.MOVE_QTY, 100);
    moveTreeMap.put(ReceivingConstants.MOVE_CONTAINER_TAG, trackingId);
    moveTreeMap.put(ReceivingConstants.MOVE_SEQUENCE_NBR, 1);
    return moveTreeMap;
  }

  private LinkedTreeMap<String, Object> getDockTagMoveInfo() {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, "STG0");
    moveTreeMap.put(ReceivingConstants.MOVE_QTY, 1);
    moveTreeMap.put(ReceivingConstants.MOVE_CONTAINER_TAG, "b328180000100000001382942");
    moveTreeMap.put(ReceivingConstants.MOVE_TYPE_CODE, 62);
    moveTreeMap.put(ReceivingConstants.MOVE_TYPE_DESC, "DTPUTAWAY");
    moveTreeMap.put(ReceivingConstants.MOVE_PRIORITY, 10);
    return moveTreeMap;
  }

  @Test
  public void testPublishMoveV2() {
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("30")
        .thenReturn("5")
        .thenReturn("PUTAWAY")
        .thenReturn("PF");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.MOVE_EVENT_HANDLER),
            eq(ReceivingConstants.JMS_MOVE_PUBLISHER),
            any()))
        .thenReturn(messagePublisher);
    doNothing().when(messagePublisher).publish(any(), any());

    movePublisher.publishMoveV2(MockContainer.getSSTKContainer(), toLocation, headers);

    verify(configUtils, times(4)).getCcmValue(anyInt(), anyString(), anyString());
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishMoveV2_Kafka() {
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("30")
        .thenReturn("5")
        .thenReturn("PUTAWAY")
        .thenReturn("PF");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.MOVE_EVENT_HANDLER),
            eq(ReceivingConstants.JMS_MOVE_PUBLISHER),
            any()))
        .thenReturn(kafkaMoveMessagePublisher);
    doNothing().when(messagePublisher).publish(any(), any());

    movePublisher.publishMoveV2(MockContainer.getSSTKContainer(), toLocation, headers);

    verify(configUtils, times(4)).getCcmValue(anyInt(), anyString(), anyString());
    verify(kafkaMoveMessagePublisher, times(1)).publish(any(), any());
  }

  @Test
  public void testPublishCancelMove() {
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(true);

    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("5")
        .thenReturn("PUTAWAY");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.MOVE_EVENT_HANDLER),
            eq(ReceivingConstants.JMS_MOVE_PUBLISHER),
            any()))
        .thenReturn(messagePublisher);
    doNothing().when(messagePublisher).publish(any(), any());

    movePublisher.publishCancelMove(MockContainer.getSSTKContainer().getTrackingId(), headers);

    verify(configUtils, times(1)).getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(configUtils, times(2)).getCcmValue(anyInt(), anyString(), anyString());
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }

  @Test
  public void testPublishCancelMove_Kafka() {
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("5")
        .thenReturn("PUTAWAY");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.MOVE_EVENT_HANDLER),
            eq(ReceivingConstants.JMS_MOVE_PUBLISHER),
            any()))
        .thenReturn(kafkaMoveMessagePublisher);
    doNothing().when(messagePublisher).publish(any(), any());

    movePublisher.publishCancelMove(MockContainer.getSSTKContainer().getTrackingId(), headers);

    verify(configUtils, times(1)).getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(configUtils, times(2)).getCcmValue(anyInt(), anyString(), anyString());
    verify(kafkaMoveMessagePublisher, times(1)).publishCancelMove(any(), any());
  }

  @Test
  public void testPublishCancelMove_NoPutawayInstructionToMM() {
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString()))
        .thenReturn("5")
        .thenReturn("PUTAWAY");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.MOVE_EVENT_HANDLER),
            eq(ReceivingConstants.JMS_MOVE_PUBLISHER),
            any()))
        .thenReturn(messagePublisher);
    doNothing().when(messagePublisher).publish(any(), any());

    movePublisher.publishCancelMove(MockContainer.getSSTKContainer().getTrackingId(), headers);

    verify(configUtils, times(1)).getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(configUtils, times(0)).getCcmValue(anyInt(), anyString(), anyString());
    verify(jmsPublisher, times(0)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
  }
}
