package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PURCHASE_ORDER_PARTITION_SIZE;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ItemMDMService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.data.MockItemMdmData;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.DivertStatus;
import com.walmart.move.nim.receiving.endgame.entity.SlottingDestination;
import com.walmart.move.nim.receiving.endgame.mock.data.MockDeliveryMetaData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.endgame.model.*;
import com.walmart.move.nim.receiving.endgame.repositories.SlottingDestinationRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.nio.charset.Charset;
import java.util.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.SettableListenableFuture;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class EndGameSlottingServiceTest extends ReceivingTestBase {

  private Gson gson = new Gson();
  private String dummyTopicName = "dummy_topic";
  private Map<String, Object> itemMdmMockData = null;
  Transformer<Container, ContainerDTO> transformer;

  @InjectMocks private EndGameSlottingService endgameSlottingService;
  @Mock private AppConfig appConfig;
  @Mock private EndgameManagedConfig endgameManagedConfig;
  @Mock private RestConnector restConnector;
  @Mock private KafkaTemplate kafkaTemplate;
  @Mock private SlottingDestinationRepository slottingDestinationRepository;
  @Mock private ItemMDMService itemMDMService;
  @Mock private EndGameDeliveryService endGameDeliveryService;
  @Mock private ReceiptService receiptService;
  @Mock private EndGameDeliveryMetaDataService endGameDeliveryMetaDataService;
  @Mock private IOutboxPublisherService iOutboxPublisherService;
  @Mock private TenantSpecificConfigReader configUtils;

  @BeforeClass
  public void initMocksAndFields() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(endgameSlottingService, "gsonUTCDateAdapter", gson);
    ReflectionTestUtils.setField(endgameSlottingService, "gson", gson);
    ReflectionTestUtils.setField(endgameSlottingService, "hawkeyeDivertsTopic", dummyTopicName);
    ReflectionTestUtils.setField(endgameSlottingService, "hawkeyeDivertsTopic", dummyTopicName);
    ReflectionTestUtils.setField(endgameSlottingService, "securePublisher", kafkaTemplate);
    ReflectionTestUtils.setField(
        endgameSlottingService, "hawkeyeDivertUpdateTopic", "divert-update-topic");
    TenantContext.setFacilityNum(9610);
    TenantContext.setFacilityCountryCode("US");

    itemMdmMockData =
        gson.fromJson(
            MockItemMdmData.getMockItemMdmData(
                "../../receiving-test/src/main/resources/json/item_mdm_response_561298341.json"),
            Map.class);
    this.transformer = new ContainerTransformer();
  }

  @BeforeMethod
  public void resetMocks() {
    reset(itemMDMService);
    reset(slottingDestinationRepository);
    reset(restConnector);
    reset(kafkaTemplate);
    reset(endGameDeliveryService);
    reset(receiptService);
    reset(endGameDeliveryMetaDataService);
    reset(appConfig);
    reset(endgameManagedConfig);
  }

  @Test
  public void testGetDivertsFromSlotting() {
    SlottingDivertResponse slottingDivertResponse =
        EndGameUtilsTestCase.getSlottingDivertResponse();
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(gson.toJson(slottingDivertResponse), HttpStatus.OK));
    assertEquals(
        endgameSlottingService.getDivertsFromSlotting(
            EndGameUtilsTestCase.getSlottingDivertRequest()),
        slottingDivertResponse);
    verify(restConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Client exception from Slotting.*")
  public void testGetDivertsFromSlotting_RestClientResponseException() {
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null));
    endgameSlottingService.getDivertsFromSlotting(EndGameUtilsTestCase.getSlottingDivertRequest());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Resource exception from Slotting.*")
  public void testGetDivertsFromSlotting_ResourceAccessException() {
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new ResourceAccessException("Error"));
    endgameSlottingService.getDivertsFromSlotting(EndGameUtilsTestCase.getSlottingDivertRequest());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Resource exception from Slotting.*")
  public void testGetDivertsFromSlotting_EmptyResponse() {
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(null);
    endgameSlottingService.getDivertsFromSlotting(EndGameUtilsTestCase.getSlottingDivertRequest());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Resource exception from Slotting.*")
  public void testGetDivertsFromSlotting_EmptyResponseBody() {
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    endgameSlottingService.getDivertsFromSlotting(EndGameUtilsTestCase.getSlottingDivertRequest());
  }

  @Test
  public void testSavingUPCDivertInfo() {
    when(appConfig.getInSqlBatchSize()).thenReturn(Integer.valueOf(1).intValue());
    when(slottingDestinationRepository.findByCaseUPCInAndSellerIdIn(anyList(), anyList()))
        .thenReturn(
            EndGameUtilsTestCase.getSingleSlottingDestinationWithMultipleSellerId(
                "20078742229434", DivertStatus.DECANT));
    when(slottingDestinationRepository.saveAll(anyIterable()))
        .thenReturn(
            EndGameUtilsTestCase.getSingleSlottingDestinationWithMultipleSellerId(
                "20078742229434", DivertStatus.DECANT));
    endgameSlottingService.save(
        Collections.singletonList(EndGameUtilsTestCase.getDivertDestinationToHawkeye()));
    verify(slottingDestinationRepository, times(1))
        .findByCaseUPCInAndSellerIdIn(anyList(), anyList());
    verify(slottingDestinationRepository, times(1)).saveAll(anyList());
    verify(appConfig, times(1)).getInSqlBatchSize();
  }

  @Test
  public void testUpdateDivertForItem_WhenNoPreviousDivertAvailableInDB() {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.save(any(SlottingDestination.class)))
        .thenReturn(EndGameUtilsTestCase.getSlottingDestination());
    when(slottingDestinationRepository.findByCaseUPC(anyString()))
        .thenReturn(Collections.emptyList());
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    endgameSlottingService.updateDivertForItem(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.TRUE, Boolean.FALSE));
    verify(itemMDMService, times(1))
        .retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean());
    verify(restConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(slottingDestinationRepository, times(1)).findByCaseUPC(anyString());
    verify(slottingDestinationRepository, times(1)).save(any(SlottingDestination.class));
    verify(kafkaTemplate, times(1)).send(any(Message.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
    verify(endgameManagedConfig, times(2)).getWalmartDefaultSellerId();
    verify(endgameManagedConfig, times(2)).getSamsDefaultSellerId();
  }

  @Test
  public void testUpdateDivertForItem_WhenPreviousDivertAvailableInDB() {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.save(any(SlottingDestination.class)))
        .thenReturn(EndGameUtilsTestCase.getSlottingDestination());
    when(slottingDestinationRepository.findByCaseUPC(anyString()))
        .thenReturn(
            Collections.singletonList(
                EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    endgameSlottingService.updateDivertForItem(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.TRUE, Boolean.FALSE));
    verify(itemMDMService, times(1))
        .retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean());
    verify(restConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(slottingDestinationRepository, times(1)).findByCaseUPC(anyString());
    verify(slottingDestinationRepository, times(1)).save(any(SlottingDestination.class));
    verify(kafkaTemplate, times(1)).send(any(Message.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
    verify(endgameManagedConfig, times(1)).getWalmartDefaultSellerId();
    verify(endgameManagedConfig, times(1)).getSamsDefaultSellerId();
  }

  @Test
  public void testUpdateDivertForItem_WhenItemUPCAndCaseUPCNotAvailableInMessagePayload() {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.save(any(SlottingDestination.class)))
        .thenReturn(EndGameUtilsTestCase.getSlottingDestination());
    when(slottingDestinationRepository.findByCaseUPC(anyString()))
        .thenReturn(
            Collections.singletonList(
                EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    endgameSlottingService.updateDivertForItem(
        MockMessageData.getMockAttributeUpdateListenerDataWithoutItemUPCAndCaseUPC(
            Boolean.TRUE, Boolean.FALSE));
    verify(itemMDMService, times(1))
        .retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean());
    verify(restConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(slottingDestinationRepository, times(1)).findByCaseUPC(anyString());
    verify(slottingDestinationRepository, times(1)).save(any(SlottingDestination.class));
    verify(kafkaTemplate, times(1)).send(any(Message.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
    verify(endgameManagedConfig, times(1)).getWalmartDefaultSellerId();
    verify(endgameManagedConfig, times(1)).getSamsDefaultSellerId();
  }

  @Test
  public void testUpdateDivertForItem_WhenPreviousDivertIsSameAsNewDivert() {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.save(any(SlottingDestination.class)))
        .thenReturn(EndGameUtilsTestCase.getSlottingDestination());
    when(slottingDestinationRepository.findByCaseUPC(anyString()))
        .thenReturn(Collections.singletonList(EndGameUtilsTestCase.getSlottingDestination()));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    endgameSlottingService.updateDivertForItem(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.TRUE, Boolean.FALSE));
    verify(itemMDMService, times(1))
        .retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean());
    verify(restConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(slottingDestinationRepository, times(1)).findByCaseUPC(anyString());
    verify(slottingDestinationRepository, times(0)).save(any(SlottingDestination.class));
    verify(kafkaTemplate, times(0)).send(any(Message.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
    verify(endgameManagedConfig, times(1)).getWalmartDefaultSellerId();
    verify(endgameManagedConfig, times(1)).getSamsDefaultSellerId();
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to access Kafka.*")
  public void testUpdateDivertForItem_ExceptionWhileSendingDivertMessageToHawkeye() {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.save(any(SlottingDestination.class)))
        .thenReturn(EndGameUtilsTestCase.getSlottingDestination());
    when(slottingDestinationRepository.findByCaseUPC(anyString()))
        .thenReturn(
            Collections.singletonList(
                EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(kafkaTemplate.send(any(Message.class)))
        .thenThrow(new RuntimeException("Something went wrong."));
    endgameSlottingService.updateDivertForItem(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.TRUE, Boolean.FALSE));
  }

  @Test
  public void testUpdateDivertForItemOnCapturingExpiryDate_WhenNoPreviousDivertAvailableInDB()
      throws ReceivingException {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummaryQtyByPoAndPoLineResponse());
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails()));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    doNothing()
        .when(endGameDeliveryMetaDataService)
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt()))
            .thenReturn(false);
    endgameSlottingService.updateDivertForItemAndDelivery(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.TRUE));
    verify(itemMDMService, times(1))
        .retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean());
    verify(restConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1))
        .getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong());
    verify(slottingDestinationRepository, times(1))
        .findFirstByCaseUPCAndSellerId(anyString(), anyString());
    verify(endGameDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(kafkaTemplate, times(1)).send(any(Message.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
    verify(endgameManagedConfig, times(2)).getWalmartDefaultSellerId();
    verify(endgameManagedConfig, times(2)).getSamsDefaultSellerId();
    verify(endGameDeliveryMetaDataService, times(1))
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
  }

  @Test
  public void testUpdateDivertForItemOnCapturingExpiryDate_WhenPreviousDivertAvailableInDB()
      throws ReceivingException {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummaryQtyByPoAndPoLineResponse());
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(
            Optional.of(EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails()));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    doNothing()
        .when(endGameDeliveryMetaDataService)
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt()))
            .thenReturn(false);
    endgameSlottingService.updateDivertForItemAndDelivery(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.TRUE));
    verify(itemMDMService, times(1))
        .retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean());
    verify(restConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1))
        .getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong());
    verify(slottingDestinationRepository, times(1))
        .findFirstByCaseUPCAndSellerId(anyString(), anyString());
    verify(endGameDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(kafkaTemplate, times(1)).send(any(Message.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
    verify(endgameManagedConfig, times(2)).getWalmartDefaultSellerId();
    verify(endgameManagedConfig, times(2)).getSamsDefaultSellerId();
    verify(endGameDeliveryMetaDataService, times(1))
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
  }

  @Test
  public void testUpdateDivertForItemOnCapturingExpiryDate_WhenReceiptsDoesNotExists()
      throws ReceivingException {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(
            Optional.of(EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails()));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    doNothing()
        .when(endGameDeliveryMetaDataService)
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt()))
            .thenReturn(false);
    endgameSlottingService.updateDivertForItemAndDelivery(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.TRUE));
    verify(itemMDMService, times(1))
        .retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean());
    verify(restConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1))
        .getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong());
    verify(endGameDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(slottingDestinationRepository, times(1))
        .findFirstByCaseUPCAndSellerId(anyString(), anyString());
    verify(kafkaTemplate, times(1)).send(any(Message.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
    verify(endgameManagedConfig, times(2)).getWalmartDefaultSellerId();
    verify(endgameManagedConfig, times(2)).getSamsDefaultSellerId();
    verify(endGameDeliveryMetaDataService, times(1))
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
  }

  @Test
  public void testUpdateDivertForItemOnCapturingExpiryDate_WhenPreviousDivertIsSameAsNewDivert()
      throws ReceivingException {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummaryQtyByPoAndPoLineResponse());
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(Optional.of(EndGameUtilsTestCase.getSlottingDestination()));
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(
            Optional.of(
                MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails(
                    "2019-02-11T00:00:00.000Z", "DECANT")));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    doNothing()
        .when(endGameDeliveryMetaDataService)
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt()))
            .thenReturn(false);
    endgameSlottingService.updateDivertForItemAndDelivery(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.TRUE));
    verify(itemMDMService, times(1))
        .retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean());
    verify(restConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1))
        .getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong());
    verify(slottingDestinationRepository, times(1))
        .findFirstByCaseUPCAndSellerId(anyString(), anyString());
    verify(endGameDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(kafkaTemplate, times(0)).send(any(Message.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
    verify(endgameManagedConfig, times(2)).getWalmartDefaultSellerId();
    verify(endgameManagedConfig, times(2)).getSamsDefaultSellerId();
    verify(endGameDeliveryMetaDataService, times(1))
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
  }

  @Test
  public void
      testUpdateDivertForItemOnCapturingExpiryDate_WhenItemUPCAndCaseUPCNotAvailableInMessagePayload()
          throws ReceivingException {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummaryQtyByPoAndPoLineResponse());
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(
            Optional.of(EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails()));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    doNothing()
        .when(endGameDeliveryMetaDataService)
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt()))
        .thenReturn(false);
    endgameSlottingService.updateDivertForItemAndDelivery(
        MockMessageData.getMockAttributeUpdateListenerDataWithoutItemUPCAndCaseUPC(
            Boolean.FALSE, Boolean.TRUE));
    verify(itemMDMService, times(1))
        .retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean());
    verify(restConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1))
        .getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong());
    verify(slottingDestinationRepository, times(1))
        .findFirstByCaseUPCAndSellerId(anyString(), anyString());
    verify(endGameDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(kafkaTemplate, times(1)).send(any(Message.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
    verify(endgameManagedConfig, times(2)).getWalmartDefaultSellerId();
    verify(endgameManagedConfig, times(2)).getSamsDefaultSellerId();
    verify(endGameDeliveryMetaDataService, times(1))
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Unable to get deliveryMetaData for deliveryNumber.*")
  public void testUpdateDivertForItemOnCapturingExpiryDate_WhenDeliveryMetaDataDoesNotExists()
      throws ReceivingException {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummaryQtyByPoAndPoLineResponse());
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(
            Optional.of(EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.empty());
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    doNothing()
        .when(endGameDeliveryMetaDataService)
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt()))
            .thenReturn(false);
    endgameSlottingService.updateDivertForItemAndDelivery(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.TRUE));
    verify(itemMDMService, times(1))
        .retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean());
    verify(restConnector, times(0))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1))
        .getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong());
    verify(slottingDestinationRepository, times(1))
        .findFirstByCaseUPCAndSellerId(anyString(), anyString());
    verify(endGameDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(kafkaTemplate, times(0)).send(any(Message.class));
    verify(endgameManagedConfig, times(1)).getOrgUnitId();
    verify(endgameManagedConfig, times(1)).getWalmartDefaultSellerId();
    verify(endgameManagedConfig, times(1)).getSamsDefaultSellerId();
    verify(endGameDeliveryMetaDataService, times(1))
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to access Kafka.*")
  public void
      testUpdateDivertForItemOnCapturingExpiryDate_ExceptionWhileSendingDivertMessageToHawkeye()
          throws ReceivingException {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getWalmartDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummaryQtyByPoAndPoLineResponse());
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(
            Optional.of(EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails()));
    when(kafkaTemplate.send(any(Message.class)))
        .thenThrow(new RuntimeException("Something went wrong."));
    doNothing()
        .when(endGameDeliveryMetaDataService)
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt()))
            .thenReturn(false);
    endgameSlottingService.updateDivertForItemAndDelivery(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.TRUE));
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "Delivery = 12333333 not found while updating to hawkeye on capturing expiry date")
  public void testUpdateDivertForItemOnCapturingExpiryDate_ExceptionWhileCallingGDMService()
      throws ReceivingException {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                ReceivingException.GDM_SERVICE_DOWN,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE));
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummaryQtyByPoAndPoLineResponse());
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(
            Optional.of(EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails()));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    doNothing()
        .when(endGameDeliveryMetaDataService)
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt()))
            .thenReturn(false);
    endgameSlottingService.updateDivertForItemAndDelivery(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.TRUE));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item mdm service returned empty response for itemNumber.*")
  public void testUpdateDivertForItemOnCapturingExpiryDate_ExceptionWhileCallingItemMDMService()
      throws ReceivingException {
    when(endgameManagedConfig.getWalmartDefaultSellerId())
        .thenReturn("F55CDC31AB754BB68FE0B39041159D63");
    when(endgameManagedConfig.getSamsDefaultSellerId()).thenReturn("0");
    when(endgameManagedConfig.getOrgUnitId()).thenReturn(String.valueOf(1));
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenThrow(
            new ReceivingBadDataException(
                ExceptionCodes.ITEM_NOT_FOUND,
                String.format(
                    ExceptionDescriptionConstants.ITEM_MDM_BAD_DATA_ERROR_MSG, 561298341)));
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummaryQtyByPoAndPoLineResponse());
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(EndGameUtilsTestCase.getSlottingDivertResponse()), HttpStatus.OK));
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(
            Optional.of(EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails()));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    doNothing()
        .when(endGameDeliveryMetaDataService)
        .updateDeliveryMetaDataForItemOverrides(
            any(DeliveryMetaData.class), anyString(), anyString(), anyString());
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt()))
            .thenReturn(false);
    endgameSlottingService.updateDivertForItemAndDelivery(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.TRUE));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Client exception from Slotting.*")
  public void testUpdateDivertForItemOnCapturingExpiryDate_ExceptionWhileCallingSlottingService()
      throws ReceivingException {
    when(itemMDMService.retrieveItemDetails(
            anySet(), any(HttpHeaders.class), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(itemMdmMockData);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(configUtils.getPurchaseOrderPartitionSize(anyString()))
        .thenReturn(PURCHASE_ORDER_PARTITION_SIZE);
    when(receiptService.getReceiptSummaryQtyByPOandPOLineResponse(anyList(), anyLong()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummaryQtyByPoAndPoLineResponse());
    when(restConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null));
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(
            Optional.of(EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(MockDeliveryMetaData.getDeliveryMetaData_WithItemDetails()));
    when(kafkaTemplate.send(any(Message.class))).thenReturn(new SettableListenableFuture<>());
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt()))
            .thenReturn(false);
    endgameSlottingService.updateDivertForItemAndDelivery(
        MockMessageData.getMockAttributeUpdateListenerData(Boolean.FALSE, Boolean.TRUE));
  }

  @Test
  public void updateDestinationTest() {
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(
            Optional.of(EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD)));
    when(slottingDestinationRepository.save(any()))
        .thenReturn(EndGameUtilsTestCase.getSlottingDestination(DivertStatus.DECANT));
    SlottingDestination response =
        endgameSlottingService.updateDestination(
            "12333334", EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD));
    assertEquals(
        response.getDestination(),
        EndGameUtilsTestCase.getSlottingDestination(DivertStatus.DECANT).getDestination());
    verify(slottingDestinationRepository, times(1))
        .findFirstByCaseUPCAndSellerId(anyString(), anyString());
    verify(slottingDestinationRepository, times(1)).save(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "UPC = \\d* not found to process Slotting prioritisation")
  public void updateDestinationFailureTest() {
    when(slottingDestinationRepository.findFirstByCaseUPCAndSellerId(anyString(), anyString()))
        .thenReturn(Optional.empty());
    endgameSlottingService.updateDestination(
        "12333334", EndGameUtilsTestCase.getSlottingDestination(DivertStatus.PALLET_BUILD));
  }

  @Test(
      expectedExceptions = ReceivingForwardedException.class,
      expectedExceptionsMessageRegExp = "There are no slots available for some of the pallets*")
  public void testCalculateSlotException_NO_SLOTS_AVAILABLE() {
    when(appConfig.getSlottingBaseUrl()).thenReturn("test");
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")))
        .when(restConnector)
        .post(any(), anyString(), isA(HttpHeaders.class), any());
    PalletSlotRequest palletSlotRequest = new PalletSlotRequest();
    Boolean isOverboxingPallet = Boolean.TRUE;
    endgameSlottingService.multipleSlotsFromSlotting(palletSlotRequest, isOverboxingPallet);
  }

  @Test(
      expectedExceptions = ReceivingForwardedException.class,
      expectedExceptionsMessageRegExp = "There are no slots available for some of the pallets*")
  public void testCalculateSlotException_SLOTTING_BAD_RESPONSE_ERROR_MSG() {
    when(appConfig.getSlottingBaseUrl()).thenReturn("test");
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.NOT_FOUND.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")))
        .when(restConnector)
        .post(any(), anyString(), isA(HttpHeaders.class), any());
    PalletSlotRequest palletSlotRequest = new PalletSlotRequest();
    Boolean isOverboxingPallet = Boolean.TRUE;
    endgameSlottingService.multipleSlotsFromSlotting(palletSlotRequest, isOverboxingPallet);
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Resource exception from Slotting.*")
  public void testCalculateSlotException_SLOTTING_RESOURCE_RESPONSE_ERROR_MSG()
      throws ReceivingInternalException {
    when(appConfig.getSlottingBaseUrl()).thenReturn("test");
    doThrow(new ResourceAccessException("Some error"))
        .when(restConnector)
        .post(any(), anyString(), isA(HttpHeaders.class), any());
    PalletSlotRequest palletSlotRequest = new PalletSlotRequest();
    Boolean isOverboxingPallet = Boolean.TRUE;
    endgameSlottingService.multipleSlotsFromSlotting(palletSlotRequest, isOverboxingPallet);
  }

  @Test
  public void testCalculateSlot() {
    when(appConfig.getSlottingBaseUrl()).thenReturn("test");
    PalletSlotResponse palletSlotResponse = new PalletSlotResponse();
    SlotLocation slotLocation = new SlotLocation();
    slotLocation.setLocation("test");
    slotLocation.setContainerTrackingId("test1");
    slotLocation.setType("test2");
    slotLocation.setMoveRequired(true);
    slotLocation.setMoveType(SlotMoveType.HAUL);
    palletSlotResponse.setLocations(Arrays.asList(slotLocation));
    palletSlotResponse.setMessageId("test4");
    when(restConnector.post(any(), anyString(), any(HttpHeaders.class), any()))
        .thenReturn(new ResponseEntity<>(palletSlotResponse, HttpStatus.OK));
    PalletSlotRequest palletSlotRequest = new PalletSlotRequest();
    Boolean isOverboxingPallet = true;
    assertEquals(
        endgameSlottingService.multipleSlotsFromSlotting(palletSlotRequest, isOverboxingPallet),
        palletSlotResponse);
    verify(restConnector, times(1)).post(any(), anyString(), isA(HttpHeaders.class), any());
  }

  @Test
  public void sendDivertInfoToKafkaWhenOutboxKafkaPublishEnabled() throws ReceivingException {
    EndGameSlottingData endgameSlottingData = getEndGameSlottingData();
    when(configUtils.isDivertInfoOutboxKafkaPublishEnabled(anyInt())).thenReturn(true);
    doNothing().when(iOutboxPublisherService).publishToKafka(
            anyString(), anyMap(), anyString(), anyInt(), anyString(), anyString());

    endgameSlottingService.send(endgameSlottingData, 12345L);

    verify(iOutboxPublisherService, times(1)).publishToKafka(
            anyString(), anyMap(), anyString(), anyInt(), anyString(), anyString());
  }

  private static EndGameSlottingData getEndGameSlottingData() {
    EndGameSlottingData endgameSlottingData = new EndGameSlottingData();
    endgameSlottingData.setDeliveryNumber(30008889L);
    endgameSlottingData.setDoorNumber("123");
    endgameSlottingData.setDestinations(new ArrayList<>());
    return endgameSlottingData;
  }

}
