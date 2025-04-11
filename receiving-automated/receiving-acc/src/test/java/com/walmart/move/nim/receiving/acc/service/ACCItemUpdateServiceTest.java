package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.message.common.ActiveDeliveryMessage;
import com.walmart.move.nim.receiving.core.message.common.HawkeyeItemUpdateType;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateEventType;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateInstructionMessage;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateMessage;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaItemUpdateInstructionPublisher;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.repositories.ItemCatalogRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ACCItemUpdateServiceTest extends ReceivingTestBase {
  @InjectMocks private ACCItemUpdateService itemUpdateService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private ItemCatalogRepository itemCatalogRepository;
  @Mock private GenericLabelGeneratorService labelGeneratorService;
  @Mock private KafkaItemUpdateInstructionPublisher itemUpdateInstructionPublisher;

  private ItemUpdateMessage itemUpdateMessage;
  private Map<Long, DeliveryDetails> deliveryDetailsMap;
  private DeliveryDetails deliveryDetails;
  private Long deliveryNumber;
  private HawkeyeItemUpdateType eventType;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32888);
    TenantContext.setFacilityCountryCode("US");

    deliveryNumber = 94769060L;
    deliveryDetailsMap = new HashMap<>();
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(deliveryNumber)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    itemUpdateMessage =
        ItemUpdateMessage.builder()
            .itemNumber(566051127)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .build();
    deliveryDetailsMap = new HashMap<>();
  }

  @AfterMethod
  public void resetMocks() {
    reset(tenantSpecificConfigReader);
    reset(itemCatalogRepository);
    reset(labelGeneratorService);
    reset(itemUpdateInstructionPublisher);
  }

  @BeforeMethod
  public void beforeMethod() {
    doReturn(labelGeneratorService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_GENERATOR_SERVICE), any());
    doReturn(itemUpdateInstructionPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_UPDATE_INSTRUCTION_PUBLISHER), any());
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
              .getCanonicalPath();
      deliveryDetails =
          JacksonParser.convertJsonToObject(getFileAsString(dataPath), DeliveryDetails.class);
      deliveryDetails.setDoorNumber("123");
      deliveryDetails.setTrailerId("xyz");
      deliveryDetailsMap.put(deliveryNumber, deliveryDetails);
    } catch (IOException e) {
      assert (false);
    }
  }

  @Test
  public void processItemUpdateEvent_UpcCatalogGlobalEvent() throws ReceivingException {
    eventType = HawkeyeItemUpdateType.UPC_CATALOG_GLOBAL;
    itemUpdateMessage.setEventType(ItemUpdateEventType.CATALOG_GTIN_UPDATE.name());
    itemUpdateMessage.setFrom("1234567890123");
    itemUpdateMessage.setTo("11111111111111");
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(itemCatalogRepository, times(0)).saveAll(anyList());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_UPDATE_INSTRUCTION_PUBLISHER), any());
    verify(itemUpdateInstructionPublisher, times(1))
        .publish(any(ItemUpdateInstructionMessage.class), anyMap());
  }

  @Test
  public void processItemUpdateEvent_UndoCatalogGlobalEvent() throws ReceivingException {
    eventType = HawkeyeItemUpdateType.UNDO_CATALOG_GLOBAL;
    itemUpdateMessage.setEventType(ItemUpdateEventType.CATALOG_GTIN_UPDATE.name());
    itemUpdateMessage.setFrom("1234567890123");
    itemUpdateMessage.setTo(null);
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(itemCatalogRepository, times(1)).save(any(ItemCatalogUpdateLog.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_UPDATE_INSTRUCTION_PUBLISHER), any());
    verify(itemUpdateInstructionPublisher, times(1))
        .publish(any(ItemUpdateInstructionMessage.class), anyMap());
  }

  @Test
  public void processItemUpdateEvent_UpcCatalogDeliveryEvent() throws ReceivingException {
    eventType = HawkeyeItemUpdateType.UPC_CATALOG_DELIVERY;
    itemUpdateMessage.setEventType(ItemUpdateEventType.CATALOG_GTIN_UPDATE.name());
    itemUpdateMessage.setFrom("1234567890123");
    itemUpdateMessage.setTo("11111111111111");
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(itemCatalogRepository, times(0)).saveAll(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_UPDATE_INSTRUCTION_PUBLISHER), any());
    verify(itemUpdateInstructionPublisher, times(0))
        .publish(any(ItemUpdateInstructionMessage.class), anyMap());
  }

  @Test
  public void processItemUpdateEvent_UndoCatalogDeliveryEvent() throws ReceivingException {
    eventType = HawkeyeItemUpdateType.UNDO_CATALOG_DELIVERY;
    itemUpdateMessage.setEventType(ItemUpdateEventType.CATALOG_GTIN_UPDATE.name());
    itemUpdateMessage.setFrom("1234567890123");
    itemUpdateMessage.setTo(null);
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(itemCatalogRepository, times(0)).saveAll(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.ITEM_UPDATE_INSTRUCTION_PUBLISHER), any());
    verify(itemUpdateInstructionPublisher, times(0))
        .publish(any(ItemUpdateInstructionMessage.class), anyMap());
  }

  @Test
  public void processItemUpdateEvent_NonconToConEvent() throws ReceivingException {
    eventType = HawkeyeItemUpdateType.NONCON_TO_CONVEYABLE_GLOBAL;
    doReturn(new HashMap<>())
        .when(labelGeneratorService)
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.LABEL_GENERATOR_SERVICE),
            eq(GenericLabelGeneratorService.class));
    verify(labelGeneratorService, times(1))
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    verify(labelGeneratorService, times(1))
        .publishLabelsToAcl(anyMap(), anyLong(), anyString(), anyString(), anyBoolean());
  }

  @Test
  public void processItemUpdateEvent_ConToNonconEvent() throws ReceivingException {
    eventType = HawkeyeItemUpdateType.CONVEYABLE_TO_NONCON_GLOBAL;
    doReturn(new HashMap<>())
        .when(labelGeneratorService)
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.LABEL_GENERATOR_SERVICE),
            eq(GenericLabelGeneratorService.class));
    verify(labelGeneratorService, times(1))
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    verify(itemUpdateInstructionPublisher, times(1))
        .publish(any(ItemUpdateInstructionMessage.class), anyMap());
  }

  @Test
  public void processItemUpdateEvent_CROSSUtoSSTKUEvent_ItemIsConveyable()
      throws ReceivingException {
    eventType = HawkeyeItemUpdateType.CROSSU_TO_SSTKU_DELIVERY;
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    deliveryDetailsMap.put(deliveryNumber, deliveryDetails);
    doReturn(new HashMap<>())
        .when(labelGeneratorService)
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.LABEL_GENERATOR_SERVICE),
            eq(GenericLabelGeneratorService.class));
    verify(labelGeneratorService, times(1))
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    verify(itemUpdateInstructionPublisher, times(1))
        .publish(any(ItemUpdateInstructionMessage.class), anyMap());
  }

  @Test
  public void processItemUpdateEvent_CROSSUtoSSTKUEvent_ItemIsNonConveyable()
      throws ReceivingException {
    eventType = HawkeyeItemUpdateType.CROSSU_TO_SSTKU_DELIVERY;
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(false);
    deliveryDetailsMap.put(deliveryNumber, deliveryDetails);
    doReturn(new HashMap<>())
        .when(labelGeneratorService)
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.LABEL_GENERATOR_SERVICE),
            eq(GenericLabelGeneratorService.class));
    verify(labelGeneratorService, times(1))
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    verify(itemUpdateInstructionPublisher, times(1))
        .publish(any(ItemUpdateInstructionMessage.class), anyMap());
  }

  @Test
  public void processItemUpdateEvent_SSTKUtoCROSSUEvent_ItemIsConveyable()
      throws ReceivingException {
    eventType = HawkeyeItemUpdateType.SSTKU_TO_CROSSU_DELIVERY;
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    deliveryDetailsMap.put(deliveryNumber, deliveryDetails);
    doReturn(new HashMap<>())
        .when(labelGeneratorService)
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.LABEL_GENERATOR_SERVICE),
            eq(GenericLabelGeneratorService.class));
    verify(labelGeneratorService, times(1))
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    verify(labelGeneratorService, times(1))
        .publishLabelsToAcl(anyMap(), anyLong(), anyString(), anyString(), anyBoolean());
  }

  @Test
  public void processItemUpdateEvent_SSTKUtoCROSSUEvent_ItemIsNonConveyable()
      throws ReceivingException {
    eventType = HawkeyeItemUpdateType.SSTKU_TO_CROSSU_DELIVERY;
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(false);
    deliveryDetailsMap.put(deliveryNumber, deliveryDetails);
    doReturn(new HashMap<>())
        .when(labelGeneratorService)
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.LABEL_GENERATOR_SERVICE),
            eq(GenericLabelGeneratorService.class));
    verify(labelGeneratorService, times(1))
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    verify(itemUpdateInstructionPublisher, times(1))
        .publish(any(ItemUpdateInstructionMessage.class), anyMap());
  }

  @Test
  public void processItemUpdateEvent_InvalidEvent() throws ReceivingException {
    eventType = HawkeyeItemUpdateType.INVALID_ITEM_UPDATE;
    itemUpdateService.processItemUpdateEvent(itemUpdateMessage, deliveryDetailsMap, eventType);
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.LABEL_GENERATOR_SERVICE),
            eq(GenericLabelGeneratorService.class));
    verify(labelGeneratorService, times(0))
        .updateLabelDataForItemUpdate(deliveryDetails, eventType, Boolean.TRUE);
    verify(labelGeneratorService, times(0))
        .publishLabelsToAcl(anyMap(), anyLong(), anyString(), anyString(), anyBoolean());
    verify(itemUpdateInstructionPublisher, times(0))
        .publish(any(ItemUpdateInstructionMessage.class), anyMap());
  }
}
