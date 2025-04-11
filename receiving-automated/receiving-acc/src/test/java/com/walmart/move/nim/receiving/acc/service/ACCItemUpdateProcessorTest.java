package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.message.common.ActiveDeliveryMessage;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateEventType;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.label.RejectReason;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.apache.commons.lang3.SerializationUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ACCItemUpdateProcessorTest extends ReceivingTestBase {
  @InjectMocks private ACCItemUpdateProcessor itemUpdateProcessor;
  @Mock private AppConfig appConfig;
  @Mock private ACCItemUpdateService itemUpdateService;
  @Mock private PreLabelDeliveryService preLabelDeliveryService;
  @Mock private LabelDataService labelDataService;

  private DeliveryDetails deliveryDetails;
  private ItemUpdateMessage itemUpdateMessage;
  private ActiveDeliveryMessage activeDeliveryMessage;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32888);
    TenantContext.setFacilityCountryCode("US");
    activeDeliveryMessage =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769060L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    itemUpdateMessage =
        ItemUpdateMessage.builder()
            .itemNumber(566051127)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .build();
  }

  @AfterMethod
  public void resetMocks() {
    reset(itemUpdateService);
    reset(preLabelDeliveryService);
    reset(appConfig);
    reset(labelDataService);
  }

  @BeforeMethod
  public void beforeMethod() {
    when(appConfig.getHandlingCodesForConveyableIndicator())
        .thenReturn(Arrays.asList("C", "I", "J", "M", "X", "E"));
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
              .getCanonicalPath();
      deliveryDetails =
          JacksonParser.convertJsonToObject(getFileAsString(dataPath), DeliveryDetails.class);
    } catch (IOException e) {
      assert (false);
    }
  }

  @Test
  public void testProcessEvent_ConToNonConEvent() throws ReceivingException {
    itemUpdateMessage.setEventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.name());
    itemUpdateMessage.setFrom("C");
    itemUpdateMessage.setTo("B");
    when(preLabelDeliveryService.fetchDeliveryDetails(any(), any())).thenReturn(deliveryDetails);
    itemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(preLabelDeliveryService, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_NonConToConEvent() throws ReceivingException {
    itemUpdateMessage.setEventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.name());
    itemUpdateMessage.setFrom("B");
    itemUpdateMessage.setTo("C");
    when(preLabelDeliveryService.fetchDeliveryDetails(any(), any())).thenReturn(deliveryDetails);
    itemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(preLabelDeliveryService, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_InvalidHandlingCodeUpdateEvent() throws ReceivingException {
    itemUpdateMessage.setEventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.name());
    itemUpdateMessage.setFrom("C");
    itemUpdateMessage.setTo("X");
    when(preLabelDeliveryService.fetchDeliveryDetails(any(), any())).thenReturn(deliveryDetails);
    itemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(preLabelDeliveryService, times(0)).fetchDeliveryDetails(anyString(), anyLong());
    verify(itemUpdateService, times(0)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_UndoCatalogEvent() throws ReceivingException {
    itemUpdateMessage.setEventType(ItemUpdateEventType.CATALOG_GTIN_UPDATE.name());
    itemUpdateMessage.setFrom("12345678901234");
    itemUpdateMessage.setTo(null);
    when(preLabelDeliveryService.fetchDeliveryDetails(any(), any())).thenReturn(deliveryDetails);
    itemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(preLabelDeliveryService, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_CatalogEvent() throws ReceivingException {
    itemUpdateMessage.setEventType(ItemUpdateEventType.CATALOG_GTIN_UPDATE.name());
    itemUpdateMessage.setFrom("12345678901234");
    itemUpdateMessage.setTo("11111111111111");
    when(preLabelDeliveryService.fetchDeliveryDetails(any(), any())).thenReturn(deliveryDetails);
    itemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(preLabelDeliveryService, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_SSTKUtoCROSSUEvent_NonConItem() throws ReceivingException {
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(Collections.singletonList("CROSSU"));
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(false);
    DeliveryDetails deliveryDetails1 = SerializationUtils.clone(deliveryDetails);
    DeliveryDetails deliveryDetails2 = SerializationUtils.clone(deliveryDetails);
    ActiveDeliveryMessage activeDeliveryMessage1 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769061L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ActiveDeliveryMessage activeDeliveryMessage2 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769062L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ItemUpdateMessage itemUpdateMessage1 =
        ItemUpdateMessage.builder()
            .itemNumber(566051127)
            .activeDeliveries(Arrays.asList(activeDeliveryMessage1, activeDeliveryMessage2))
            .eventType(ItemUpdateEventType.CHANNEL_FLIP.name())
            .from("SSTKU")
            .to("CROSSU")
            .build();

    deliveryDetails1.setDeliveryNumber(94769061L);
    deliveryDetails1.getDeliveryDocuments().get(0).setDeliveryNumber(94769061L);
    deliveryDetails1
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("SSTKU");
    deliveryDetails2.setDeliveryNumber(94769062L);
    deliveryDetails2.getDeliveryDocuments().get(0).setDeliveryNumber(94769062L);
    deliveryDetails2
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("CROSSU");

    LabelData labelData1 =
        LabelData.builder()
            .deliveryNumber(94769061L)
            .rejectReason(RejectReason.NONCON_SSTK)
            .build();

    // if D2 caused CF, it could be possible that by that time Channel was not updated in GDM by OP.
    // so even though D2 was CROSSU, the activeChannelMethod in GDM still might've been SSTKU
    // and labels for D2 were generated with rejectReason as NONCON_SSTK instead of NONCON_DA
    // when GDM publishes CF event to Receiving, we will reprocess labels
    // and set the correct reject reason for these labels
    LabelData labelData2 =
        LabelData.builder()
            .deliveryNumber(94769062L)
            .rejectReason(RejectReason.NONCON_SSTK)
            .build();

    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769061L)))
        .thenReturn(deliveryDetails1);
    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769062L)))
        .thenReturn(deliveryDetails2);
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769061L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData1));
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769062L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData2));

    itemUpdateProcessor.processEvent(itemUpdateMessage1);

    verify(preLabelDeliveryService, times(2)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataService, times(2))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_SSTKUtoCROSSUEvent_ConveyableItem() throws ReceivingException {
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(Collections.singletonList("CROSSU"));
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    DeliveryDetails deliveryDetails1 = SerializationUtils.clone(deliveryDetails);
    DeliveryDetails deliveryDetails2 = SerializationUtils.clone(deliveryDetails);
    ActiveDeliveryMessage activeDeliveryMessage1 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769061L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ActiveDeliveryMessage activeDeliveryMessage2 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769062L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ItemUpdateMessage itemUpdateMessage1 =
        ItemUpdateMessage.builder()
            .itemNumber(566051127)
            .activeDeliveries(Arrays.asList(activeDeliveryMessage1, activeDeliveryMessage2))
            .eventType(ItemUpdateEventType.CHANNEL_FLIP.name())
            .from("SSTKU")
            .to("CROSSU")
            .build();

    deliveryDetails1.setDeliveryNumber(94769061L);
    deliveryDetails1.getDeliveryDocuments().get(0).setDeliveryNumber(94769061L);
    deliveryDetails1
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("SSTKU");
    deliveryDetails2.setDeliveryNumber(94769062L);
    deliveryDetails2.getDeliveryDocuments().get(0).setDeliveryNumber(94769062L);
    deliveryDetails2
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("CROSSU");

    LabelData labelData1 =
        LabelData.builder()
            .deliveryNumber(94769061L)
            .rejectReason(RejectReason.CONVEYABLE_SSTK)
            .build();

    // if D2 caused CF, it could be possible that by that time Channel was not updated in GDM by OP.
    // so even though D2 was CROSSU, the activeChannelMethod in GDM still might've been SSTKU
    // and labels for D2 were generated with rejectReason as CONVEYABLE_SS instead of null
    // when GDM publishes CF event to Receiving, we will reprocess labels
    // and set the correct reject reason for these labels
    LabelData labelData2 =
        LabelData.builder()
            .deliveryNumber(94769062L)
            .rejectReason(RejectReason.CONVEYABLE_SSTK)
            .build();

    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769061L)))
        .thenReturn(deliveryDetails1);
    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769062L)))
        .thenReturn(deliveryDetails2);
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769061L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData1));
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769062L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData2));

    itemUpdateProcessor.processEvent(itemUpdateMessage1);

    verify(preLabelDeliveryService, times(2)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataService, times(2))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_SSTKUtoCROSSUEvent_ConveyableItem_NewDeliveryWithUpdatedChannel()
      throws ReceivingException {

    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(Collections.singletonList("CROSSU"));
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    DeliveryDetails deliveryDetails1 = SerializationUtils.clone(deliveryDetails);
    DeliveryDetails deliveryDetails2 = SerializationUtils.clone(deliveryDetails);
    DeliveryDetails deliveryDetails3 = SerializationUtils.clone(deliveryDetails);
    ActiveDeliveryMessage activeDeliveryMessage1 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769061L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ActiveDeliveryMessage activeDeliveryMessage2 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769062L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ActiveDeliveryMessage activeDeliveryMessage3 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769063L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ItemUpdateMessage itemUpdateMessage1 =
        ItemUpdateMessage.builder()
            .itemNumber(566051127)
            .activeDeliveries(
                Arrays.asList(
                    activeDeliveryMessage1, activeDeliveryMessage2, activeDeliveryMessage3))
            .eventType(ItemUpdateEventType.CHANNEL_FLIP.name())
            .from("SSTKU")
            .to("CROSSU")
            .build();

    deliveryDetails1.setDeliveryNumber(94769061L);
    deliveryDetails1.getDeliveryDocuments().get(0).setDeliveryNumber(94769061L);
    deliveryDetails1
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("SSTKU");
    deliveryDetails2.setDeliveryNumber(94769062L);
    deliveryDetails2.getDeliveryDocuments().get(0).setDeliveryNumber(94769062L);
    deliveryDetails2
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("CROSSU");
    deliveryDetails3.setDeliveryNumber(94769063L);
    deliveryDetails3.getDeliveryDocuments().get(0).setDeliveryNumber(94769063L);
    deliveryDetails3
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("CROSSU");

    LabelData labelData1 =
        LabelData.builder()
            .deliveryNumber(94769061L)
            .rejectReason(RejectReason.CONVEYABLE_SSTK)
            .build();

    // if D2 caused CF, it could be possible that by that time Channel was not updated in GDM by OP.
    // so even though D2 was CROSSU, the activeChannelMethod in GDM still might've been SSTKU
    // and labels for D2 were generated with rejectReason as CONVEYABLE_SS instead of null
    // when GDM publishes CF event to Receiving, we will reprocess labels
    // and set the correct reject reason for these labels
    LabelData labelData2 =
        LabelData.builder()
            .deliveryNumber(94769062L)
            .rejectReason(RejectReason.CONVEYABLE_SSTK)
            .build();

    // by the time CF event came from GDM when door assign happened for D2,
    // new delivery D3 was door assigned as well and D3 was downloaded with update channel method
    // so, receiving generated labels with lpns and rejectReason as null (since it is DACon)
    LabelData labelData3 = LabelData.builder().deliveryNumber(94769063L).rejectReason(null).build();

    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769061L)))
        .thenReturn(deliveryDetails1);
    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769062L)))
        .thenReturn(deliveryDetails2);
    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769063L)))
        .thenReturn(deliveryDetails3);
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769061L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData1));
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769062L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData2));
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769063L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData3));

    itemUpdateProcessor.processEvent(itemUpdateMessage1);

    verify(preLabelDeliveryService, times(3)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataService, times(3))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_CROSSUtoSSTKUEvent_NonConItem() throws ReceivingException {
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(Collections.singletonList("SSTKU"));
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(false);
    DeliveryDetails deliveryDetails1 = SerializationUtils.clone(deliveryDetails);
    DeliveryDetails deliveryDetails2 = SerializationUtils.clone(deliveryDetails);
    ActiveDeliveryMessage activeDeliveryMessage1 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769061L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ActiveDeliveryMessage activeDeliveryMessage2 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769062L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ItemUpdateMessage itemUpdateMessage1 =
        ItemUpdateMessage.builder()
            .itemNumber(566051127)
            .activeDeliveries(Arrays.asList(activeDeliveryMessage1, activeDeliveryMessage2))
            .eventType(ItemUpdateEventType.CHANNEL_FLIP.name())
            .from("CROSSU")
            .to("SSTKU")
            .build();

    deliveryDetails1.setDeliveryNumber(94769061L);
    deliveryDetails1.getDeliveryDocuments().get(0).setDeliveryNumber(94769061L);
    deliveryDetails1
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("CROSSU");
    deliveryDetails2.setDeliveryNumber(94769062L);
    deliveryDetails2.getDeliveryDocuments().get(0).setDeliveryNumber(94769062L);
    deliveryDetails2
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("SSTKU");

    LabelData labelData1 =
        LabelData.builder().deliveryNumber(94769061L).rejectReason(RejectReason.NONCON_DA).build();

    // if D2 caused CF, it could be possible that by that time Channel was not updated in GDM by OP.
    // so even though D2 was SSTKU, the activeChannelMethod in GDM still might've been CROSSU
    // and labels for D2 were generated with rejectReason as NONCON_DA instead of NONCON_SSTK
    // when GDM publishes CF event to Receiving, we will reprocess labels
    // and set the correct reject reason for these labels
    LabelData labelData2 =
        LabelData.builder().deliveryNumber(94769062L).rejectReason(RejectReason.NONCON_DA).build();

    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769061L)))
        .thenReturn(deliveryDetails1);
    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769062L)))
        .thenReturn(deliveryDetails2);
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769061L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData1));
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769062L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData2));

    itemUpdateProcessor.processEvent(itemUpdateMessage1);

    verify(preLabelDeliveryService, times(2)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataService, times(2))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_CROSSUtoSSTKUEvent_ConveyableItem() throws ReceivingException {
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(Collections.singletonList("SSTKU"));
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    DeliveryDetails deliveryDetails1 = SerializationUtils.clone(deliveryDetails);
    DeliveryDetails deliveryDetails2 = SerializationUtils.clone(deliveryDetails);
    ActiveDeliveryMessage activeDeliveryMessage1 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769061L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ActiveDeliveryMessage activeDeliveryMessage2 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769062L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ItemUpdateMessage itemUpdateMessage1 =
        ItemUpdateMessage.builder()
            .itemNumber(566051127)
            .activeDeliveries(Arrays.asList(activeDeliveryMessage1, activeDeliveryMessage2))
            .eventType(ItemUpdateEventType.CHANNEL_FLIP.name())
            .from("CROSSU")
            .to("SSTKU")
            .build();

    deliveryDetails1.setDeliveryNumber(94769061L);
    deliveryDetails1.getDeliveryDocuments().get(0).setDeliveryNumber(94769061L);
    deliveryDetails1
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("CROSSU");
    deliveryDetails2.setDeliveryNumber(94769062L);
    deliveryDetails2.getDeliveryDocuments().get(0).setDeliveryNumber(94769062L);
    deliveryDetails2
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("SSTKU");

    LabelData labelData1 = LabelData.builder().deliveryNumber(94769061L).rejectReason(null).build();

    // if D2 caused CF, it could be possible that by that time Channel was not updated in GDM by OP.
    // so even though D2 was SSTKU, the activeChannelMethod in GDM still might've been CROSSU
    // and labels for D2 were generated with rejectReason as null instead of CONVEYABLE_SSTK
    // when GDM publishes CF event to Receiving, we will reprocess labels
    // and set the correct reject reason for these labels
    LabelData labelData2 = LabelData.builder().deliveryNumber(94769062L).rejectReason(null).build();

    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769061L)))
        .thenReturn(deliveryDetails1);
    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769062L)))
        .thenReturn(deliveryDetails2);
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769061L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData1));
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769062L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData2));

    itemUpdateProcessor.processEvent(itemUpdateMessage1);

    verify(preLabelDeliveryService, times(2)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataService, times(2))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_CROSSUtoSSTKUEvent_ConveyableItem_NewDeliveryWithUpdatedChannel()
      throws ReceivingException {

    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(Collections.singletonList("SSTKU"));
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setIsConveyable(true);
    DeliveryDetails deliveryDetails1 = SerializationUtils.clone(deliveryDetails);
    DeliveryDetails deliveryDetails2 = SerializationUtils.clone(deliveryDetails);
    DeliveryDetails deliveryDetails3 = SerializationUtils.clone(deliveryDetails);
    ActiveDeliveryMessage activeDeliveryMessage1 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769061L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ActiveDeliveryMessage activeDeliveryMessage2 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769062L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ActiveDeliveryMessage activeDeliveryMessage3 =
        ActiveDeliveryMessage.builder()
            .deliveryNumber(94769063L)
            .deliveryStatus("WRK")
            .url("https://test.com")
            .build();
    ItemUpdateMessage itemUpdateMessage1 =
        ItemUpdateMessage.builder()
            .itemNumber(566051127)
            .activeDeliveries(
                Arrays.asList(
                    activeDeliveryMessage1, activeDeliveryMessage2, activeDeliveryMessage3))
            .eventType(ItemUpdateEventType.CHANNEL_FLIP.name())
            .from("CROSSU")
            .to("SSTKU")
            .build();

    deliveryDetails1.setDeliveryNumber(94769061L);
    deliveryDetails1.getDeliveryDocuments().get(0).setDeliveryNumber(94769061L);
    deliveryDetails1
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("CROSSU");
    deliveryDetails2.setDeliveryNumber(94769062L);
    deliveryDetails2.getDeliveryDocuments().get(0).setDeliveryNumber(94769062L);
    deliveryDetails2
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("SSTKU");
    deliveryDetails3.setDeliveryNumber(94769063L);
    deliveryDetails3.getDeliveryDocuments().get(0).setDeliveryNumber(94769063L);
    deliveryDetails3
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType("SSTKU");

    LabelData labelData1 = LabelData.builder().deliveryNumber(94769061L).rejectReason(null).build();

    // if D2 caused CF, it could be possible that by that time Channel was not updated in GDM by OP.
    // so even though D2 was SSTKU, the activeChannelMethod in GDM still might've been CROSSU
    // and labels for D2 were generated with rejectReason as null instead of CONVEYABLE_SSTK
    // when GDM publishes CF event to Receiving, we will reprocess labels
    // and set the correct reject reason for these labels
    LabelData labelData2 = LabelData.builder().deliveryNumber(94769062L).rejectReason(null).build();

    // by the time CF event came from GDM when door assign happened for D2,
    // new delivery D3 was door assigned as well and D3 was downloaded with update channel method
    // so, receiving generated labels with rejectReason as CONVEYABLE_SSTK
    LabelData labelData3 =
        LabelData.builder()
            .deliveryNumber(94769063L)
            .rejectReason(RejectReason.CONVEYABLE_SSTK)
            .build();

    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769061L)))
        .thenReturn(deliveryDetails1);
    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769062L)))
        .thenReturn(deliveryDetails2);
    when(preLabelDeliveryService.fetchDeliveryDetails(anyString(), eq(94769063L)))
        .thenReturn(deliveryDetails3);
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769061L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData1));
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769062L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData2));
    when(labelDataService.findAllLabelDataByDeliveryPOPOL(eq(94769063L), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(labelData3));

    itemUpdateProcessor.processEvent(itemUpdateMessage1);

    verify(preLabelDeliveryService, times(3)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataService, times(3))
        .findAllLabelDataByDeliveryPOPOL(anyLong(), anyString(), anyInt());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_ChannelFlipInvalidEvent() throws ReceivingException {
    itemUpdateMessage.setEventType(ItemUpdateEventType.CHANNEL_FLIP.name());
    itemUpdateMessage.setFrom("xyz");
    itemUpdateMessage.setTo("abc");
    when(preLabelDeliveryService.fetchDeliveryDetails(any(), any())).thenReturn(deliveryDetails);
    itemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(preLabelDeliveryService, times(0)).fetchDeliveryDetails(anyString(), anyLong());
    verify(itemUpdateService, times(0)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_DeliveryIsNull() throws ReceivingException {
    itemUpdateMessage.setEventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.name());
    itemUpdateMessage.setFrom("C");
    itemUpdateMessage.setTo("B");
    when(preLabelDeliveryService.fetchDeliveryDetails(any(), any())).thenReturn(null);
    itemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(preLabelDeliveryService, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(itemUpdateService, times(0)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_PreviousHandlingCodeWasNull_ConToNonCon() throws ReceivingException {
    itemUpdateMessage.setEventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.name());
    itemUpdateMessage.setFrom(null);
    itemUpdateMessage.setTo("NC");
    when(preLabelDeliveryService.fetchDeliveryDetails(any(), any())).thenReturn(deliveryDetails);
    itemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(preLabelDeliveryService, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }

  @Test
  public void testProcessEvent_PreviousHandlingCodeWasNull_NonConToCon() throws ReceivingException {
    itemUpdateMessage.setEventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.name());
    itemUpdateMessage.setFrom(null);
    itemUpdateMessage.setTo("C");
    when(preLabelDeliveryService.fetchDeliveryDetails(any(), any())).thenReturn(deliveryDetails);
    itemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(preLabelDeliveryService, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(itemUpdateService, times(1)).processItemUpdateEvent(any(), any(), any());
  }
}
