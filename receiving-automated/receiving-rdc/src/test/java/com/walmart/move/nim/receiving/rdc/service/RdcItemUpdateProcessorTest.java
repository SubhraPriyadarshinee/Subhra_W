package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeItemUpdateRequest;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.message.common.ActiveDeliveryMessage;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateEventType;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.LabelDownloadEventMiscInfo;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.service.LabelDownloadEventService;
import com.walmart.move.nim.receiving.core.service.VendorBasedDeliveryDocumentsSearchHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.model.LabelDownloadEventStatus;
import com.walmart.move.nim.receiving.rdc.utils.RdcLabelGenerationUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcSSTKLabelGenerationUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class RdcItemUpdateProcessorTest {
  @InjectMocks private RdcItemUpdateProcessor rdcItemUpdateProcessor;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Mock private LabelDownloadEventService labelDownloadEventService;
  @Mock private VendorBasedDeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler;
  @Mock private AppConfig appConfig;
  @Mock RdcManagedConfig rdcManagedConfig;
  @Mock private RdcLabelGenerationService rdcLabelGenerationService;
  @Mock private ItemUpdateUtils itemUpdateUtils;
  @Mock private RdcSSTKLabelGenerationUtils rdcSSTKLabelGenerationUtils;
  @Mock private RdcLabelGenerationUtils rdcLabelGenerationUtils;
  private Gson gson = new Gson();

  @BeforeMethod
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rdcItemUpdateProcessor, "gson", gson);
    TenantContext.setFacilityNum(32679);
  }

  @AfterMethod
  public void cleanup() {
    reset(
        appConfig,
        tenantSpecificConfigReader,
        rdcLabelGenerationService,
        labelDownloadEventService);
  }

  @DataProvider(name = "SymEligibleAndConveyableHandlingCodes")
  private static Object[][] symEligibleAndConveyableHandlingCodes() {
    return new Object[][] {{"C"}, {"I"}, {"J"}};
  }

  @Test
  public void testprocessItemUpdate_NotHandlingCodeUpdateEvent() {
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.CONVEYABLE_TO_NONCON_GLOBAL.toString())
            .build();
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(appConfig, times(0)).getValidItemPackTypeHandlingCodeCombinations();
  }

  @Test
  public void testprocessItemUpdate_CatchesException() {
    ItemUpdateMessage itemUpdateMessage = ItemUpdateMessage.builder().build();
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(appConfig, times(0)).getValidItemPackTypeHandlingCodeCombinations();
  }

  @Test
  public void testprocessItemUpdate_NotValidEvent_NoItemNumber() {
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .to("N")
            .from("C")
            .build();
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(appConfig, times(0)).getValidItemPackTypeHandlingCodeCombinations();
  }

  @Test
  public void testprocessItemUpdate_NotValidEvent_EmptyToValue() {
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .from("C")
            .build();
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(appConfig, times(0)).getValidItemPackTypeHandlingCodeCombinations();
  }

  @Test
  public void testprocessItemUpdate_EmptyActiveDeliveriesValue() {
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .from("C")
            .to("N")
            .build();
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(appConfig, times(0)).getValidItemPackTypeHandlingCodeCombinations();
  }

  @Test
  public void testprocessItemUpdate_EmptyDeliveryNumbersList() {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(null).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("C")
            .to("N")
            .build();
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(appConfig, times(0)).getValidItemPackTypeHandlingCodeCombinations();
  }

  @Test
  public void testprocessItemUpdate_BreakPackItem() {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("I")
            .to("N")
            .vnpk(3)
            .whpk(2)
            .build();
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(appConfig, times(0)).getValidItemPackTypeHandlingCodeCombinations();
  }

  @Test
  public void testprocessItemUpdate_InvalidItemHandlingCodeUpdate_CatchesException() {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("I")
            .to("N")
            .build();
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(appConfig, times(0)).getValidItemPackTypeHandlingCodeCombinations();
  }

  @Test
  public void testprocessItemUpdate_NonConToCon() throws ReceivingException, IOException {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activePOs(Arrays.asList("0133355567", "0133355568"))
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("N")
            .to("C")
            .build();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<LabelDownloadEvent> labelDownloadEventList = getMockLabelDownloadEventsList();
    labelDownloadEventList.get(0).setRejectReason(RejectReason.RDC_NONCON);
    labelDownloadEventList.get(1).setRejectReason(RejectReason.RDC_NONCON);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Collections.singletonList("CC"));
    doNothing()
        .when(rdcLabelGenerationService)
        .republishLabelsToHawkeye(anyMap(), any(ItemUpdateMessage.class), anyBoolean());
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean());
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(rdcLabelGenerationService, times(1))
        .republishLabelsToHawkeye(anyMap(), any(ItemUpdateMessage.class), anyBoolean());
  }

  @Test
  public void testprocessItemUpdate_XBlockToCon() throws ReceivingException, IOException {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activePOs(Arrays.asList("0133355567", "0133355568"))
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("X")
            .to("C")
            .build();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<LabelDownloadEvent> labelDownloadEventList = getMockLabelDownloadEventsList();
    labelDownloadEventList.get(0).setRejectReason(RejectReason.X_BLOCK);
    labelDownloadEventList.get(1).setRejectReason(RejectReason.X_BLOCK);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Collections.singletonList("CC"));
    doNothing()
        .when(rdcLabelGenerationService)
        .republishLabelsToHawkeye(anyMap(), any(ItemUpdateMessage.class), anyBoolean());
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doCallRealMethod()
        .when(rdcLabelGenerationUtils)
        .filterLabelDownloadEventWithPilotDelivery(anyList());
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean());
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(rdcLabelGenerationService, times(2))
        .republishLabelsToHawkeye(anyMap(), any(ItemUpdateMessage.class), anyBoolean());
  }

  @Test
  public void testprocessItemUpdate_XBlockToCon_SSTKAutomationDisabled()
      throws ReceivingException, IOException {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activePOs(Arrays.asList("0133355567", "0133355568"))
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("X")
            .to("C")
            .build();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<LabelDownloadEvent> labelDownloadEventList = getMockLabelDownloadEventsList();
    labelDownloadEventList.get(0).setRejectReason(RejectReason.X_BLOCK);
    labelDownloadEventList.get(1).setRejectReason(RejectReason.X_BLOCK);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Collections.singletonList("CC"));
    doNothing()
        .when(rdcLabelGenerationService)
        .republishLabelsToHawkeye(anyMap(), any(ItemUpdateMessage.class), anyBoolean());
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean());
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(rdcLabelGenerationService, times(1))
        .republishLabelsToHawkeye(anyMap(), any(ItemUpdateMessage.class), anyBoolean());
  }

  @Test
  public void testprocessItemUpdate_ConToNonCon() throws ReceivingException, IOException {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("C")
            .to("N")
            .activePOs(Arrays.asList("0133355567", "0133355568"))
            .httpHeaders(new HttpHeaders())
            .build();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<LabelDownloadEvent> labelDownloadEventList = getMockLabelDownloadEventsList();
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(rdcLabelGenerationService.fetchAllLabelDownloadEvents(
            anyMap(), any(ItemUpdateMessage.class), anyString(), anyBoolean(), eq(false)))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEventsList().get(0)));
    when(rdcLabelGenerationService.fetchAllLabelDownloadEvents(
            anyMap(), any(ItemUpdateMessage.class), anyString(), anyBoolean(), eq(true)))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEventsList().get(1)));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Collections.singletonList("CC"));
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    when(labelDownloadEventService.saveAll(anyList())).thenReturn(getMockLabelDownloadEventsList());
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doCallRealMethod().when(rdcSSTKLabelGenerationUtils).buildPoDeliveryMap(anyList());
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean());
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(rdcLabelGenerationService, times(0))
        .republishLabelsToHawkeye(anyMap(), any(ItemUpdateMessage.class), anyBoolean());
    verify(hawkeyeRestApiClient, times(1))
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(1)).saveAll(anyList());
  }

  @Test
  public void testProcessItemUpdate_ConToXBLOCK_RejectReason()
      throws ReceivingException, IOException {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .activePOs(Arrays.asList("0133355567", "0133355568"))
            .httpHeaders(new HttpHeaders())
            .from("C")
            .to("X")
            .build();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<LabelDownloadEvent> labelDownloadEventList = getMockLabelDownloadEventsList();
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(rdcLabelGenerationService.fetchAllLabelDownloadEvents(
            anyMap(), any(ItemUpdateMessage.class), anyString(), anyBoolean(), eq(false)))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEventsList().get(0)));
    when(rdcLabelGenerationService.fetchAllLabelDownloadEvents(
            anyMap(), any(ItemUpdateMessage.class), anyString(), anyBoolean(), eq(true)))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEventsList().get(1)));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Collections.singletonList("CC"));
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    when(labelDownloadEventService.saveAll(anyList())).thenReturn(getMockLabelDownloadEventsList());
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doCallRealMethod().when(rdcSSTKLabelGenerationUtils).buildPoDeliveryMap(anyList());
    doCallRealMethod()
        .when(rdcLabelGenerationUtils)
        .filterLabelDownloadEventWithPilotDelivery(anyList());
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(rdcLabelGenerationService, times(0))
        .republishLabelsToHawkeye(anyMap(), any(ItemUpdateMessage.class), anyBoolean());
    verify(hawkeyeRestApiClient, times(2))
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
  }

  @Test
  public void testProcessItemUpdate_ConToXBLOCK_SSTKAutomationDisabled()
      throws ReceivingException, IOException {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .activePOs(Arrays.asList("0133355567", "0133355568"))
            .httpHeaders(new HttpHeaders())
            .from("C")
            .to("X")
            .build();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<LabelDownloadEvent> labelDownloadEventList = getMockLabelDownloadEventsList();
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(rdcLabelGenerationService.fetchAllLabelDownloadEvents(
            anyMap(), any(ItemUpdateMessage.class), anyString(), anyBoolean(), eq(false)))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEventsList().get(0)));
    when(rdcLabelGenerationService.fetchAllLabelDownloadEvents(
            anyMap(), any(ItemUpdateMessage.class), anyString(), anyBoolean(), eq(true)))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEventsList().get(1)));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Collections.singletonList("CC"));
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    when(labelDownloadEventService.saveAll(anyList())).thenReturn(getMockLabelDownloadEventsList());
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doCallRealMethod().when(rdcSSTKLabelGenerationUtils).buildPoDeliveryMap(anyList());
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(rdcLabelGenerationService, times(0))
        .republishLabelsToHawkeye(anyMap(), any(ItemUpdateMessage.class), anyBoolean());
    verify(hawkeyeRestApiClient, times(1))
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(1)).saveAll(anyList());
  }

  @Test
  public void testprocessItemUpdate_NonConToSymInEligible() {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("N")
            .to("E")
            .whpk(8)
            .vnpk(8)
            .build();
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Collections.singletonList("CC"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(labelDownloadEventService, times(0)).findByItemNumber(anyLong());
  }

  @Test
  public void testprocessItemUpdate_SymInEligibleToNonCon() {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activePOs(Arrays.asList("0133355567", "0133355568"))
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("E")
            .to("N")
            .whpk(8)
            .vnpk(8)
            .build();
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Collections.singletonList("CC"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(labelDownloadEventService, times(0)).findByItemNumber(anyLong());
  }

  @Test
  public void testprocessItemUpdate_SymInEligibleToXBlock() {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activePOs(Arrays.asList("0133355567", "0133355568"))
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("E")
            .to("X")
            .whpk(8)
            .vnpk(8)
            .httpHeaders(new HttpHeaders())
            .build();
    List<LabelDownloadEvent> labelDownloadEventList = getMockLabelDownloadEventsList();
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Collections.singletonList("CC"));
    when(rdcLabelGenerationService.fetchAllLabelDownloadEvents(
            anyMap(), any(ItemUpdateMessage.class), anyString(), anyBoolean(), eq(false)))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEventsList().get(0)));
    when(rdcLabelGenerationService.fetchAllLabelDownloadEvents(
            anyMap(), any(ItemUpdateMessage.class), anyString(), anyBoolean(), eq(true)))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEventsList().get(1)));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    when(labelDownloadEventService.saveAll(anyList())).thenReturn(getMockLabelDownloadEventsList());
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doCallRealMethod().when(rdcSSTKLabelGenerationUtils).buildPoDeliveryMap(anyList());
    doCallRealMethod()
        .when(rdcLabelGenerationUtils)
        .filterLabelDownloadEventWithPilotDelivery(anyList());
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(hawkeyeRestApiClient, times(2))
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
  }

  @Test
  public void testprocessItemUpdate_XBlockToSymInEligible() {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.HANDLING_CODE_UPDATE.toString())
            .itemNumber(233232323)
            .activePOs(Arrays.asList("0133355567", "0133355568"))
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .from("X")
            .to("E")
            .whpk(8)
            .vnpk(8)
            .httpHeaders(new HttpHeaders())
            .build();
    List<LabelDownloadEvent> labelDownloadEventList = getMockLabelDownloadEventsList();
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(Collections.singletonList("CC"));
    when(rdcLabelGenerationService.fetchAllLabelDownloadEvents(
            anyMap(), any(ItemUpdateMessage.class), anyString(), anyBoolean(), eq(false)))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEventsList().get(0)));
    when(rdcLabelGenerationService.fetchAllLabelDownloadEvents(
            anyMap(), any(ItemUpdateMessage.class), anyString(), anyBoolean(), eq(true)))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEventsList().get(1)));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    when(labelDownloadEventService.saveAll(anyList())).thenReturn(getMockLabelDownloadEventsList());
    doCallRealMethod().when(rdcSSTKLabelGenerationUtils).buildPoDeliveryMap(anyList());
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(hawkeyeRestApiClient, times(1))
        .itemUpdateToHawkeye(any(HawkeyeItemUpdateRequest.class), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(1)).saveAll(anyList());
  }

  @Test
  public void testprocessItemUpdate_CatalogGtinUpdateEvent_HappyPath()
      throws IOException, ReceivingException {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> activePOs = new ArrayList<>();
    activePOs.add("0133355567");
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.CATALOG_GTIN_UPDATE.toString())
            .itemNumber(551705258)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .activePOs(activePOs)
            .to("0323232316623")
            .httpHeaders(MockHttpHeaders.getHeaders())
            .build();
    List<LabelDownloadEvent> labelDownloadEventList = new ArrayList<>();
    labelDownloadEventList.add(getMockLabelDownloadEvent());
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    ArgumentCaptor<HawkeyeItemUpdateRequest> captor =
        ArgumentCaptor.forClass(HawkeyeItemUpdateRequest.class);
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(captor.capture(), any(HttpHeaders.class));
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(hawkeyeRestApiClient, times(1)).itemUpdateToHawkeye(any(), any());
    Assert.assertNotNull(captor.getValue().getCatalogGTIN());
  }

  @Test
  public void testprocessItemUpdate_CatalogGtinUpdateEvent_EmptyTo()
      throws IOException, ReceivingException {
    ActiveDeliveryMessage activeDeliveryMessage =
        ActiveDeliveryMessage.builder().deliveryNumber(23233223L).build();
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> activePOs = new ArrayList<>();
    activePOs.add("0133355567");
    ItemUpdateMessage itemUpdateMessage =
        ItemUpdateMessage.builder()
            .eventType(ItemUpdateEventType.CATALOG_GTIN_UPDATE.toString())
            .itemNumber(551705258)
            .activeDeliveries(Collections.singletonList(activeDeliveryMessage))
            .activePOs(activePOs)
            .to("")
            .httpHeaders(MockHttpHeaders.getHeaders())
            .build();
    List<LabelDownloadEvent> labelDownloadEventList = new ArrayList<>();
    labelDownloadEventList.add(getMockLabelDownloadEvent());
    when(rdcManagedConfig.getItemUpdateHawkeyeDeliveriesDayLimit()).thenReturn(14);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any()))
        .thenReturn(deliveryDocumentsSearchHandler);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
            anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(labelDownloadEventService.findByItemNumber(anyLong())).thenReturn(labelDownloadEventList);
    ArgumentCaptor<HawkeyeItemUpdateRequest> captor =
        ArgumentCaptor.forClass(HawkeyeItemUpdateRequest.class);
    doNothing()
        .when(hawkeyeRestApiClient)
        .itemUpdateToHawkeye(captor.capture(), any(HttpHeaders.class));
    rdcItemUpdateProcessor.processEvent(itemUpdateMessage);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(labelDownloadEventService, times(1)).findByItemNumber(anyLong());
    verify(hawkeyeRestApiClient, times(1)).itemUpdateToHawkeye(any(), any());
    Assert.assertNotNull(captor.getValue().getCatalogGTIN());
  }

  private LabelDownloadEvent getMockLabelDownloadEvent() {
    return LabelDownloadEvent.builder()
        .purchaseReferenceNumber("0133355567")
        .itemNumber(661298341L)
        .deliveryNumber(232323323L)
        .createTs(new Date())
        .status(LabelDownloadEventStatus.PROCESSED.toString())
        .build();
  }

  private List<LabelDownloadEvent> getMockLabelDownloadEventsList() {
    LabelDownloadEventMiscInfo labelDownloadEventMiscInfo1 =
        LabelDownloadEventMiscInfo.builder().labelType("DA").build();
    LabelDownloadEventMiscInfo labelDownloadEventMiscInfo2 =
        LabelDownloadEventMiscInfo.builder().labelType("SSTK").build();
    List<LabelDownloadEvent> downloadEvents = new ArrayList<>();
    downloadEvents.add(
        LabelDownloadEvent.builder()
            .purchaseReferenceNumber("0133355567")
            .itemNumber(661298341L)
            .deliveryNumber(232323323L)
            .createTs(new Date())
            .status(LabelDownloadEventStatus.PROCESSED.toString())
            .miscInfo(gson.toJson(labelDownloadEventMiscInfo1))
            .build());
    downloadEvents.add(
        LabelDownloadEvent.builder()
            .purchaseReferenceNumber("0133355568")
            .itemNumber(661298341L)
            .deliveryNumber(232323323L)
            .createTs(new Date())
            .status(LabelDownloadEventStatus.PROCESSED.toString())
            .miscInfo(gson.toJson(labelDownloadEventMiscInfo2))
            .build());
    return downloadEvents;
  }
}
